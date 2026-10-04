import test from 'node:test';
import assert from 'node:assert/strict';
import {buildResume,verifyBudget,verifyUnchanged} from '../scripts/resume.mjs';
const status={enabled:false,budget:{month:'2026-10',calls_reserved:9,reserved_micro:90000,reserved_eur:'0.09'}};
test('resume only rotates the temporary probe and preserves phone enrollment',()=>{const r=buildResume('a'.repeat(64),'temporary-random-token');assert.deepEqual(Object.keys(r.auth),['DEVICE_DIGESTS']);assert.deepEqual(Object.keys(r.cleanup),['DEVICE_DIGESTS']);assert.equal(JSON.parse(r.auth.DEVICE_DIGESTS)['francesco-phone-1'],'a'.repeat(64));assert.deepEqual(JSON.parse(r.cleanup.DEVICE_DIGESTS),{'francesco-phone-1':'a'.repeat(64)});assert.ok(!JSON.stringify(r).includes('temporary-random-token'));});
test('resume rejects invalid enrollment',()=>assert.throws(()=>buildResume('wrong','probe')));
test('live ninth call is valid without reimport of eight-call seed',()=>assert.deepEqual(verifyBudget(status,'2026-10',false),status.budget));
test('enabled resume preserves exact current accounting',()=>assert.doesNotThrow(()=>verifyUnchanged({...status,enabled:true},status.budget,'2026-10')));
test('changed/missing accounting or wrong enabled state is rejected',()=>{for(const value of [null,{...status,enabled:true},{...status,budget:{...status.budget,calls_reserved:101}},{...status,budget:{...status.budget,reserved_micro:80000}},{...status,budget:{...status.budget,month:'2026-09'}},{...status,budget:{...status.budget,reserved_eur:'0.08'}}])assert.throws(()=>verifyBudget(value,'2026-10',false));assert.throws(()=>verifyUnchanged({...status,enabled:true,budget:{...status.budget,calls_reserved:10,reserved_micro:100000,reserved_eur:'0.10'}},status.budget,'2026-10'));});
