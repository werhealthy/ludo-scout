import test from 'node:test';
import assert from 'node:assert/strict';
import {buildProvision,verifyStatus} from '../scripts/provision.mjs';
const ledger={version:1,disabled:false,months:{'2026-10':{calls_reserved:8,reserved_eur:'.08',operations:{old:{calls_reserved:8,reserved_eur:'.08',responses:Array(8).fill({})}}}}};
const options={benchmarkEnabled:'false',freeConfirmed:'true',geminiKey:'test-key',deviceDigest:'a'.repeat(64),probeToken:'test-private-probe'};
test('keeps historical reservations and separate device/probe auth',()=>{
 const result=buildProvision(ledger,options);
 assert.equal(JSON.parse(result.secrets.SEED_MANIFEST).months['2026-10'].calls_reserved,8);
 assert.equal(JSON.parse(result.secrets.SEED_MANIFEST).months['2026-10'].reserved_micro,80000);
 assert.equal(result.secrets.GEMINI_API_KEY,'test-key');
 assert.equal(JSON.parse(result.secrets.DEVICE_DIGESTS)['francesco-phone-1'],'a'.repeat(64));
 assert.ok(JSON.parse(result.secrets.DEVICE_DIGESTS)['provision-check']);
 assert.deepEqual(JSON.parse(result.cleanup.DEVICE_DIGESTS),{'francesco-phone-1':'a'.repeat(64)});
 assert.ok(!JSON.stringify(result).includes('test-private-probe'));
});
test('blocks absent cutover, free declaration, key and malformed auth before upload',()=>{
 for(const patch of [{benchmarkEnabled:'true'},{freeConfirmed:'false'},{geminiKey:''},{deviceDigest:'bad'},{probeToken:''}])
  assert.throws(()=>buildProvision(ledger,{...options,...patch}));
 const wrong=structuredClone(ledger);wrong.months['2026-10'].calls_reserved=0;
 assert.throws(()=>buildProvision(wrong,options));
});
test('requires disabled authenticated status with exact imported accounting',()=>{
 const seed=JSON.parse(buildProvision(ledger,options).secrets.SEED_MANIFEST);
 const status={enabled:false,budget:{month:'2026-10',calls_reserved:8,reserved_micro:80000,reserved_eur:'0.08'}};
 verifyStatus(status,seed,'2026-10');
 for(const patch of [{enabled:true},{budget:{...status.budget,calls_reserved:0}},{budget:{...status.budget,reserved_micro:0}},{budget:{...status.budget,month:'2026-11'}}])
  assert.throws(()=>verifyStatus({...status,...patch},seed,'2026-10'));
});
