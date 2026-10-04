import {DurableObject} from 'cloudflare:workers';
import {active,CONTRACT,digest,json,MODEL,validate} from './protocol.js';
import {generate} from './gemini.js';
export class BudgetObject extends DurableObject{
 constructor(ctx,env){super(ctx,env);this.ctx=ctx;this.env=env;this.sql=ctx.storage.sql;this.sql.exec('CREATE TABLE IF NOT EXISTS state (id TEXT PRIMARY KEY,value TEXT NOT NULL)');}
 get(id){const rows=this.sql.exec('SELECT value FROM state WHERE id=?',id).toArray();return rows.length?JSON.parse(rows[0].value):null;}
 put(id,value){this.sql.exec('INSERT INTO state(id,value) VALUES(?,?) ON CONFLICT(id) DO UPDATE SET value=excluded.value',id,JSON.stringify(value));}
 seed(){const seed=JSON.parse(this.env.SEED_MANIFEST||'null');if(seed?.version!==1||seed.benchmark_disabled!==true||!/^[a-f0-9]{64}$/.test(seed.source_sha256)||!seed.months||typeof seed.months!=='object')throw Error('seed');
  for(const [month,m] of Object.entries(seed.months)){if(!/^\d{4}-\d{2}$/.test(month)||!Number.isInteger(m.calls_reserved)||m.calls_reserved<0||m.calls_reserved>100||!Number.isSafeInteger(m.reserved_micro)||m.reserved_micro<m.calls_reserved*10000||m.reserved_micro>1000000)throw Error('seed');}
  const identity=JSON.stringify(seed);const old=this.get('seed');if(old){if(old!==identity)throw Error('seed conflict');return;}
  if(this.sql.exec('SELECT COUNT(*) AS n FROM state').one().n!==0)throw Error('uninitialized state');
  this.ctx.storage.transactionSync(()=>{this.put('seed',identity);this.put('month-index',Object.fromEntries(Object.keys(seed.months).map(month=>[month,true])));for(const [month,m]of Object.entries(seed.months))this.put('month:'+month,m);});
 }
 budget(){const month=new Date().toISOString().slice(0,7);const index=this.get('month-index');if(!index||typeof index!=='object'||Array.isArray(index)||Object.entries(index).some(([m,v])=>!/^\d{4}-\d{2}$/.test(m)||v!==true))throw Error('month index');const recorded=this.get('month:'+month);if((index[month]&&!recorded)||(!index[month]&&recorded))throw Error('missing month accounting');const m=recorded||{calls_reserved:0,reserved_micro:0};if(!Number.isInteger(m.calls_reserved)||m.calls_reserved<0||m.calls_reserved>100||!Number.isSafeInteger(m.reserved_micro)||m.reserved_micro<m.calls_reserved*10000||m.reserved_micro>1000000)throw Error('budget');return {...m,month,reserved_eur:(m.reserved_micro/1000000).toFixed(2)};}
 cleanup(){for(const row of this.sql.exec("SELECT id,value FROM state WHERE id LIKE 'cache:%' OR id LIKE 'request:%'").toArray()){const value=JSON.parse(row.value);if(value.expires&&value.expires<=Date.now()){if(row.id.startsWith('cache:'))this.sql.exec('DELETE FROM state WHERE id=?',row.id);else this.put(row.id,{content:value.content,result:{status:'EXPIRED'}});}}}
 async fetch(request){try{
  this.seed();this.cleanup();const b=this.budget();const device=request.headers.get('x-device');if(!device)return json({status:'UNAUTHORIZED'},401);
  if(new URL(request.url).pathname==='/v1/status')return json({enabled:active(this.env)&&!this.get('circuit'),budget:b});
  if(!active(this.env)||this.get('circuit'))return json({status:'DISABLED',budget:b},503);
  const input=validate(await request.json());const content=await digest(JSON.stringify([MODEL,CONTRACT,input.records]));const key='request:'+device+':'+input.request_id,cachekey='cache:'+device+':'+content;
  const result=this.ctx.storage.transactionSync(()=>{
   const current=this.budget(),prior=this.get(key);if(prior){if(prior.content!==content)return {status:'CONFLICT',code:409};const resolved=prior.result.status==='IN_FLIGHT'?this.get(cachekey)?.result:null;const answer=resolved||prior.result;return {...answer,request_id:input.request_id,code:answer.status==='IN_FLIGHT'?202:answer.status==='EXPIRED'?410:200,budget:current};}
   const cached=this.get(cachekey);if(cached&&cached.expires>Date.now()){const reused={...cached.result,request_id:input.request_id};this.put(key,{content,result:reused,expires:cached.expires});return {...reused,code:reused.status==='IN_FLIGHT'?202:200,budget:current};}
   if(current.calls_reserved>=100||current.reserved_micro+10000>1000000)return {status:'BUDGET_BLOCKED',code:429,budget:current};
   const next={calls_reserved:current.calls_reserved+1,reserved_micro:current.reserved_micro+10000};const index=this.get('month-index');index[current.month]=true;this.put('month-index',index);this.put('month:'+current.month,next);this.put(key,{content,result:{status:'IN_FLIGHT'},expires:Date.now()+7*86400000});this.put(cachekey,{result:{status:'IN_FLIGHT'},expires:Date.now()+7*86400000});return {send:true};
  });
  if(!result.send){const {code,...body}=result;return json(body,code);}
  const generated=await generate(input.records,this.env);const response={...generated,model:MODEL,contract:CONTRACT,request_id:input.request_id};delete response.circuit;
  this.ctx.storage.transactionSync(()=>{this.put(key,{content,result:response,expires:Date.now()+7*86400000});this.put(cachekey,{result:response,expires:Date.now()+7*86400000});if(generated.circuit)this.put('circuit',true);});
  return json({...response,budget:this.budget()},generated.status==='PROPOSAL'?200:502);
 }catch{return json({status:'STORAGE_OR_CONFIG_BLOCKED'},503);}}
}
