import {DurableObject} from 'cloudflare:workers';
import {CONTRACT,digest,geminiActive,json,LOCAL_HEARTBEAT_TTL_MS,LOCAL_LEASE_MS,MODEL,serviceActive,validate} from './protocol.js';
import {generate} from './gemini.js';

const LANGUAGES=new Set(['IT','EN','DE','FR','ES','PT','NL','MULTI','OTHER','UNKNOWN']);
const TYPES=new Set(['BASE_GAME','EXPANSION','BUNDLE','ACCESSORY_COMPONENT','NON_GAME','UNKNOWN']);

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
 localOnline(now=Date.now()){const h=this.get('local-heartbeat');return !!h&&Number.isFinite(h.at)&&now-h.at>=0&&now-h.at<=LOCAL_HEARTBEAT_TTL_MS;}
 touchLocal(body={}){const model=typeof body.model==='string'?body.model.slice(0,120):'';this.put('local-heartbeat',{at:Date.now(),model});}
 reserveGemini(key,cachekey,base,current){
  if(current.calls_reserved>=100||current.reserved_micro+10000>1000000)return {status:'BUDGET_BLOCKED',code:429,budget:current};
  const next={calls_reserved:current.calls_reserved+1,reserved_micro:current.reserved_micro+10000};const index=this.get('month-index');index[current.month]=true;this.put('month-index',index);this.put('month:'+current.month,next);
  const inflight={status:'IN_FLIGHT'};const value={...base,result:inflight};this.put(key,value);this.put(cachekey,{result:inflight,expires:base.expires});return {send:true};
 }
 normalizeLocal(input,body){
  if(!body||typeof body!=='object'||Object.keys(body).some(k=>!['job_id','worker_model','records'].includes(k))||typeof body.worker_model!=='string'||body.worker_model.length<1||body.worker_model.length>120||!Array.isArray(body.records)||body.records.length!==input.records.length)throw Error('local output');
  const ids=new Set(input.records.map(r=>r.listing_id)),seen=new Set();const out=[];
  for(const a of body.records){
   if(!a||Object.keys(a).some(k=>!['listing_id','proposed_type','confidence','evidence','language','product_title'].includes(k))||!ids.has(a.listing_id)||seen.has(a.listing_id)||!TYPES.has(a.proposed_type)||!LANGUAGES.has(a.language)||!(a.confidence===null||typeof a.confidence==='number'&&Number.isFinite(a.confidence)&&a.confidence>=0&&a.confidence<=100)||typeof a.evidence!=='string'||!a.evidence.trim()||a.evidence.length>500||typeof a.product_title!=='string'||a.product_title.length>180)throw Error('local output');
   seen.add(a.listing_id);out.push({listing_id:a.listing_id,proposed_type:a.proposed_type,confidence:a.confidence,evidence:a.evidence,needs_review:true,apply_authorized:false,language:a.language,bgg_verified:false,product_title:a.product_title});
  }
  return out;
 }
 async fetch(request){try{
  this.seed();this.cleanup();const path=new URL(request.url).pathname,b=this.budget();
  if(path==='/v1/local/heartbeat'){
   if(!request.headers.get('x-local-worker'))return json({status:'UNAUTHORIZED'},401);
   const body=await request.json().catch(()=>({}));this.touchLocal(body);return json({status:'OK'});
  }
  if(path==='/v1/local/claim'){
   if(!request.headers.get('x-local-worker'))return json({status:'UNAUTHORIZED'},401);
   const body=await request.json().catch(()=>({}));this.touchLocal(body);
   const now=Date.now();let chosen=null;
   for(const row of this.sql.exec("SELECT id,value FROM state WHERE id LIKE 'request:%'").toArray()){
    const value=JSON.parse(row.value),s=value.result?.status;
    if(value.expires<=now)continue;
    if(s==='LOCAL_PENDING'||(s==='LOCAL_CLAIMED'&&value.result.lease_until<=now)){if(!chosen||(value.created_at||0)<(chosen.value.created_at||0))chosen={id:row.id,value};}
   }
   if(!chosen)return json({status:'NO_JOB'},204);
   const claimed={status:'LOCAL_CLAIMED',lease_until:now+LOCAL_LEASE_MS};chosen.value.result=claimed;this.put(chosen.id,chosen.value);this.put(chosen.value.cache_key,{result:claimed,expires:chosen.value.expires});
   return json({status:'JOB',job_id:chosen.id,request_id:chosen.value.request_id,contract:CONTRACT,records:chosen.value.records});
  }
  if(path==='/v1/local/result'){
   if(!request.headers.get('x-local-worker'))return json({status:'UNAUTHORIZED'},401);
   const body=await request.json();this.touchLocal(body);const value=this.get(body.job_id);
   if(!value||!value.records||value.expires<=Date.now())return json({status:'JOB_GONE'},410);
   if(!['LOCAL_PENDING','LOCAL_CLAIMED'].includes(value.result?.status))return json({status:'JOB_SETTLED'},409);
   const input=validate({request_id:value.request_id,records:value.records}),records=this.normalizeLocal(input,body);
   const response={status:'PROPOSAL',records,model:MODEL,contract:CONTRACT,provider:'LOCAL',worker_model:body.worker_model,request_id:value.request_id};
   this.ctx.storage.transactionSync(()=>{this.put(body.job_id,{...value,result:response});this.put(value.cache_key,{result:response,expires:value.expires});});
   return json({status:'ACCEPTED'});
  }
  const device=request.headers.get('x-device');if(!device)return json({status:'UNAUTHORIZED'},401);
  const circuit=!!this.get('circuit'),local=this.localOnline();
  if(path==='/v1/status')return json({enabled:serviceActive(this.env)&&(local||geminiActive(this.env)&&!circuit),local_online:local,gemini_available:geminiActive(this.env)&&!circuit,budget:b});
  if(!serviceActive(this.env))return json({status:'DISABLED',budget:b},503);
  const input=validate(await request.json());const content=await digest(JSON.stringify([MODEL,CONTRACT,input.records]));const key='request:'+device+':'+input.request_id,cachekey='cache:'+device+':'+content,now=Date.now(),expires=now+7*86400000;
  const result=this.ctx.storage.transactionSync(()=>{
   const current=this.budget(),prior=this.get(key);
   const canGemini=geminiActive(this.env)&&!this.get('circuit');
   if(prior){
    if(prior.content!==content)return {status:'CONFLICT',code:409};
    const resolved=this.get(cachekey)?.result;if(resolved&&resolved.status==='PROPOSAL')return {...resolved,request_id:input.request_id,code:200,budget:current};
    const status=prior.result?.status;
    if(['LOCAL_PENDING','LOCAL_CLAIMED'].includes(status)){
     if(this.localOnline(now))return {...prior.result,status:'LOCAL_PENDING',request_id:input.request_id,code:202,budget:current};
     if(!canGemini)return {status:'LOCAL_UNAVAILABLE',request_id:input.request_id,code:503,budget:current};
     return this.reserveGemini(key,cachekey,prior,current);
    }
    const answer=resolved||prior.result;return {...answer,request_id:input.request_id,code:answer.status==='IN_FLIGHT'?202:answer.status==='EXPIRED'?410:200,budget:current};
   }
   const cached=this.get(cachekey);
   if(cached&&cached.expires>now){
    if(cached.result.status==='PROPOSAL'){const reused={...cached.result,request_id:input.request_id};this.put(key,{content,records:input.records,request_id:input.request_id,cache_key:cachekey,result:reused,created_at:now,expires:cached.expires});return {...reused,code:200,budget:current};}
    if(['LOCAL_PENDING','LOCAL_CLAIMED'].includes(cached.result.status)&&this.localOnline(now)){const pending={content,records:input.records,request_id:input.request_id,cache_key:cachekey,result:{status:'LOCAL_PENDING'},created_at:now,expires:cached.expires};this.put(key,pending);return {status:'LOCAL_PENDING',request_id:input.request_id,code:202,budget:current};}
    if(cached.result.status==='IN_FLIGHT')return {status:'IN_FLIGHT',request_id:input.request_id,code:202,budget:current};
   }
   const base={content,records:input.records,request_id:input.request_id,cache_key:cachekey,created_at:now,expires};
   if(this.localOnline(now)){const pending={...base,result:{status:'LOCAL_PENDING'}};this.put(key,pending);this.put(cachekey,{result:{status:'LOCAL_PENDING'},expires});return {status:'LOCAL_PENDING',request_id:input.request_id,code:202,budget:current};}
   if(!canGemini)return {status:'LOCAL_UNAVAILABLE',request_id:input.request_id,code:503,budget:current};
   return this.reserveGemini(key,cachekey,base,current);
  });
  if(!result.send){const {code,...body}=result;return json(body,code);}
  const generated=await generate(input.records,this.env);const response={...generated,model:MODEL,contract:CONTRACT,provider:'GEMINI',request_id:input.request_id};delete response.circuit;
  this.ctx.storage.transactionSync(()=>{const current=this.get(key)||{content,records:input.records,request_id:input.request_id,cache_key:cachekey,created_at:now,expires};this.put(key,{...current,result:response});this.put(cachekey,{result:response,expires});if(generated.circuit)this.put('circuit',true);});
  return json({...response,budget:this.budget()},generated.status==='PROPOSAL'?200:502);
 }catch{return json({status:'STORAGE_OR_CONFIG_BLOCKED'},503);}}
}
