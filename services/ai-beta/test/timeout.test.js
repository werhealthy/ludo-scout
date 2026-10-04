import {test} from 'node:test';
import assert from 'node:assert/strict';
import {Miniflare} from 'miniflare';
import {createHash} from 'node:crypto';
test('provider timeout retains one reservation and never retries',{timeout:45000},async()=>{
 let calls=0,release;const pending=new Promise(r=>release=r);const token='timeout-test-token';const month=new Date().toISOString().slice(0,7);
 const mf=new Miniflare({modules:true,modulesRules:[{type:'ESModule',include:['**/*.js']}],scriptPath:new URL('../src/worker.js',import.meta.url).pathname,compatibilityDate:'2026-07-30',durableObjects:{BUDGET:{className:'BudgetObject',useSQLite:true}},bindings:{ENABLED:'true',FREE_TIER_VALID_UNTIL:String(Date.now()+3600000),GEMINI_API_KEY:'fake',DEVICE_DIGESTS:JSON.stringify({timeout:createHash('sha256').update(token).digest('hex')}),SEED_MANIFEST:JSON.stringify({version:1,source_sha256:'a'.repeat(64),benchmark_disabled:true,months:{[month]:{calls_reserved:8,reserved_micro:80000}}})},outboundService:async()=>{calls++;await pending;return new Response('late');}});
 const request=()=>mf.dispatchFetch('https://local/v1/classify',{method:'POST',headers:{authorization:'Bearer '+token,'content-type':'application/json'},body:JSON.stringify({request_id:'timeout-0001',records:[{listing_id:1,title:'Game',brand:'Maker'}]})});
 try{const body=await(await request()).json();assert.equal(body.status,'FAILED');assert.equal(body.budget.calls_reserved,9);assert.equal(calls,1);assert.equal((await(await request()).json()).status,'FAILED');assert.equal(calls,1);}finally{release();await mf.dispose();}
});
