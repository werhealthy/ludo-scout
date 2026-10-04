import {test} from 'node:test';
import assert from 'node:assert/strict';
import {Miniflare} from 'miniflare';
import {createHash} from 'node:crypto';
import {mkdtemp,rm,readdir} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {DatabaseSync} from 'node:sqlite';
const token='test-device-secret-only',month=new Date().toISOString().slice(0,7);
const rows=[{listing_id:1,title:'Game + playmat',brand:'Maker'}];
const answer=[{listing_id:1,category:'BUNDLE',confidence:90,evidence:'Game plus playmat',needs_review:false,language:'UNKNOWN',bgg_verdict:'UNKNOWN'}];
const hash=x=>createHash('sha256').update(x).digest('hex');
async function setup(options={}){
 const dir=options.dir||await mkdtemp(join(tmpdir(),'ludo-ai-'));let calls=0;
 const mf=new Miniflare({modules:true,modulesRules:[{type:'ESModule',include:['**/*.js']}],scriptPath:new URL('../src/worker.js',import.meta.url).pathname,
 compatibilityDate:'2026-07-30',durableObjects:{BUDGET:{className:'BudgetObject',useSQLite:true}},durableObjectsPersist:dir,
 bindings:{ENABLED:'true',FREE_TIER_VALID_UNTIL:String(Date.now()+3600000),GEMINI_API_KEY:'fake-test-key',
 DEVICE_DIGESTS:JSON.stringify({d1:hash(token)}),SEED_MANIFEST:JSON.stringify({version:1,source_sha256:'a'.repeat(64),benchmark_disabled:true,months:{[month]:{calls_reserved:options.seed??8,reserved_micro:(options.seed??8)*10000}}}),...options.bindings},
 outboundService:async request=>{calls++; if(options.provider)return options.provider(request);
 return Response.json({candidates:[{content:{parts:[{text:JSON.stringify(answer)}]}}],usageMetadata:{promptTokenCount:100,candidatesTokenCount:100}});}});
 const request=async(id='request-0001',r=rows,auth=token)=>mf.dispatchFetch('https://local/v1/classify',{method:'POST',headers:{authorization:'Bearer '+auth,'content-type':'application/json'},body:JSON.stringify({request_id:id,records:r})});
 return {mf,request,calls:()=>calls,dir,async close(){await mf.dispose();if(!options.dir)await rm(dir,{recursive:true,force:true});}};
}
test('manual result stays a proposal and imported reservations survive',async()=>{const x=await setup();try{const r=await x.request();assert.equal(r.status,200);const b=await r.json();assert.equal(b.status,'PROPOSAL');assert.equal(b.budget.calls_reserved,9);assert.equal(b.records[0].apply_authorized,false);assert.equal(x.calls(),1);}finally{await x.close();}});
test('last slot contested by two devices requests permits one fetch',async()=>{const x=await setup({seed:99});try{const a=await Promise.all([x.request('request-0001'),x.request('request-0002',[{...rows[0],title:'Other'}])]);assert.deepEqual(a.map(r=>r.status).sort(),[200,429]);assert.equal(x.calls(),1);}finally{await x.close();}});
test('duplicate and content cache never repeat transport',async()=>{const x=await setup();try{await x.request();assert.equal((await x.request()).status,200);const cached=await(await x.request('request-0002')).json();assert.equal(cached.request_id,'request-0002');assert.equal(x.calls(),1);assert.equal((await x.request('request-0001',[{...rows[0],title:'Edited'}])).status,409);}finally{await x.close();}});
test('duplicate while provider is pending does not retry',async()=>{let release;const pending=new Promise(r=>release=r);const x=await setup({provider:async()=>{await pending;return Response.json({candidates:[{content:{parts:[{text:JSON.stringify(answer)}]}}]});}});let first;try{first=x.request();while(x.calls()===0)await new Promise(r=>setTimeout(r,5));assert.equal((await x.request()).status,202);const second=await Promise.race([x.request('request-0002'),new Promise(r=>setTimeout(()=>r(null),1000))]);assert.ok(second,'same content must not start another pending attempt');assert.equal(second.status,202);assert.equal(x.calls(),1);release();assert.equal((await first).status,200);assert.equal((await x.request('request-0002')).status,200);}finally{release();if(first)await first;await x.close();}});
test('invalid auth input and disabled configuration never fetch',async()=>{for(const bindings of [{ENABLED:'false'},{FREE_TIER_VALID_UNTIL:'0'},{FREE_TIER_VALID_UNTIL:'Infinity'},{FREE_TIER_VALID_UNTIL:String(Date.now()+2*86400000)},{DEVICE_DIGESTS:'{}'},{SEED_MANIFEST:'invalid'}]){const x=await setup({bindings});try{assert.notEqual((await x.request()).status,200);assert.equal(x.calls(),0);}finally{await x.close();}}const x=await setup();try{assert.equal((await x.request('request-0001',rows,'wrong')).status,401);assert.equal((await x.request('request-0001',Array.from({length:9},(_,i)=>({...rows[0],listing_id:i})))).status,400);assert.equal((await x.request('request-0001',[{...rows[0],title:'x'.repeat(5000)}])).status,400);assert.equal(x.calls(),0);}finally{await x.close();}});
test('provider failure keeps reservation and opens circuit without retry',async()=>{const x=await setup({provider:async()=>new Response('secret body',{status:429})});try{const r=await x.request();const body=await r.json();assert.equal(body.status,'FAILED');assert.equal(body.budget.calls_reserved,9);assert.equal(JSON.stringify(body).includes('secret body'),false);await x.request('request-0002');assert.equal(x.calls(),1);}finally{await x.close();}});
test('restart preserves budget and same request answer',async()=>{const dir=await mkdtemp(join(tmpdir(),'ludo-restart-'));let x=await setup({dir});try{await x.request();await x.close();x=await setup({dir});const b=await(await x.request()).json();assert.equal(b.budget.calls_reserved,9);assert.equal(x.calls(),0);}finally{await x.close();await rm(dir,{recursive:true,force:true});}});
test('changed import is blocked after restart instead of resetting budget',async()=>{const dir=await mkdtemp(join(tmpdir(),'ludo-seed-'));let x=await setup({dir});try{await x.request();await x.close();x=await setup({dir,seed:0});assert.equal((await x.request('request-0002')).status,503);assert.equal(x.calls(),0);}finally{await x.close();await rm(dir,{recursive:true,force:true});}});
test('invalid provider schema and output size retain consumed slot',async()=>{for(const provider of [async()=>Response.json({candidates:[{content:{parts:[{text:'[]'}]}}]}),async()=>new Response('x'.repeat(70000)),async()=>{throw Error('network secret');}]){const x=await setup({provider});try{const body=await(await x.request()).json();assert.equal(body.status,'FAILED');assert.equal(body.budget.calls_reserved,9);assert.equal(x.calls(),1);await x.request();assert.equal(x.calls(),1);}finally{await x.close();}}});
test('two distinct device credentials still share one global limit',async()=>{const token2='second-test-device-secret';const x=await setup({seed:99,bindings:{DEVICE_DIGESTS:JSON.stringify({d1:hash(token),d2:hash(token2)})}});try{const r=await Promise.all([x.request(),x.request('request-0002',rows,token2)]);assert.deepEqual(r.map(x=>x.status).sort(),[200,429]);assert.equal(x.calls(),1);}finally{await x.close();}});

async function mutateState(dir,change){
 const files=await readdir(dir,{recursive:true});let found=false;
 for(const file of files.filter(f=>f.endsWith('.sqlite'))){const db=new DatabaseSync(join(dir,file));try{
  const tables=db.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='state'").all();
  if(tables.length){change(db);found=true;}
 }finally{db.close();}}
 assert.ok(found,'must mutate the real durable SQLite state');
}
test('corrupt durable budget blocks provider after restart',async()=>{const dir=await mkdtemp(join(tmpdir(),'ludo-corrupt-'));let x=await setup({dir});try{
 await x.request();await x.close();await mutateState(dir,db=>db.prepare('UPDATE state SET value=? WHERE id=?').run('invalid', 'month:'+month));
 x=await setup({dir});assert.equal((await x.request('request-0002')).status,503);assert.equal(x.calls(),0);
 }finally{await x.close();await rm(dir,{recursive:true,force:true});}});
test('expired response leaves request tombstone and never repeats that request',async()=>{const dir=await mkdtemp(join(tmpdir(),'ludo-expiry-'));let x=await setup({dir});try{
 await x.request();await x.close();await mutateState(dir,db=>{for(const row of db.prepare("SELECT id,value FROM state WHERE id LIKE 'request:%' OR id LIKE 'cache:%'").all()){const v=JSON.parse(row.value);v.expires=Date.now()-1;db.prepare('UPDATE state SET value=? WHERE id=?').run(JSON.stringify(v),row.id);}});
 x=await setup({dir});assert.equal((await x.request()).status,410);assert.equal(x.calls(),0);
 }finally{await x.close();await rm(dir,{recursive:true,force:true});}});
test('unknown attempt persisted before restart is never sent again',async()=>{const dir=await mkdtemp(join(tmpdir(),'ludo-unknown-'));let x=await setup({dir});try{
 await x.request();await x.close();await mutateState(dir,db=>{for(const row of db.prepare("SELECT id,value FROM state WHERE id LIKE 'request:%' OR id LIKE 'cache:%'").all()){const v=JSON.parse(row.value);v.result={status:'IN_FLIGHT'};db.prepare('UPDATE state SET value=? WHERE id=?').run(JSON.stringify(v),row.id);}});
 x=await setup({dir});const body=await(await x.request()).json();assert.equal(body.status,'IN_FLIGHT');assert.equal(body.budget.calls_reserved,9);assert.equal(x.calls(),0);
 }finally{await x.close();await rm(dir,{recursive:true,force:true});}});
test('reservation write failure rolls back and blocks transport',async()=>{const dir=await mkdtemp(join(tmpdir(),'ludo-write-'));let x=await setup({dir});try{
 await x.request();await x.close();await mutateState(dir,db=>db.exec("CREATE TRIGGER reject_request BEFORE INSERT ON state WHEN NEW.id LIKE 'request:%' BEGIN SELECT RAISE(ABORT,'test write failure'); END"));
 x=await setup({dir});assert.equal((await x.request('request-0002',[{...rows[0],title:'Another game'}])).status,503);assert.equal(x.calls(),0);
 const status=await(await x.mf.dispatchFetch('https://local/v1/status',{headers:{authorization:'Bearer '+token}})).json();assert.equal(status.budget.calls_reserved,9);
 }finally{await x.close();await rm(dir,{recursive:true,force:true});}});
test('missing recorded month cannot silently reset the global quota',async()=>{const dir=await mkdtemp(join(tmpdir(),'ludo-missing-'));let x=await setup({dir,seed:99});try{
 await x.request();await x.close();await mutateState(dir,db=>db.prepare('DELETE FROM state WHERE id=?').run('month:'+month));
 x=await setup({dir,seed:99});assert.equal((await x.request('request-0002',[{...rows[0],title:'Another game'}])).status,503);assert.equal(x.calls(),0);
 }finally{await x.close();await rm(dir,{recursive:true,force:true});}});
test('a legitimate new UTC month starts once and survives restart',async()=>{const dir=await mkdtemp(join(tmpdir(),'ludo-rollover-'));const seed=JSON.stringify({version:1,source_sha256:'a'.repeat(64),benchmark_disabled:true,months:{'2020-01':{calls_reserved:8,reserved_micro:80000}}});let x=await setup({dir,bindings:{SEED_MANIFEST:seed}});try{const body=await(await x.request()).json();assert.equal(body.budget.calls_reserved,1);await x.close();x=await setup({dir,bindings:{SEED_MANIFEST:seed}});assert.equal((await(await x.request()).json()).budget.calls_reserved,1);assert.equal(x.calls(),0);}finally{await x.close();await rm(dir,{recursive:true,force:true});}});
