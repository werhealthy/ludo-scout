import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
let makeSeed;try{({makeSeed}=await import('../scripts/bootstrap.mjs'));}catch{}
const source={version:1,disabled:false,months:{'2026-10':{calls_reserved:8,reserved_eur:'.08',operations:{a:{calls_reserved:8,reserved_eur:'.08',responses:Array.from({length:8},()=>({status:'VALIDATED'}))}}}}};
test('seed includes every historical reserved call exactly once',()=>{assert.ok(makeSeed);const s=makeSeed(source,true);assert.equal(s.months['2026-10'].calls_reserved,8);assert.equal(s.months['2026-10'].reserved_micro,80000);assert.equal(s.benchmark_disabled,true);assert.equal(makeSeed(source,true).source_sha256,s.source_sha256);});
test('unverified cutover inconsistent counters and invalid money are rejected',()=>{assert.ok(makeSeed);assert.throws(()=>makeSeed(source,false));for(const change of [s=>s.months['2026-10'].calls_reserved=7,s=>s.months['2026-10'].reserved_eur='NaN',s=>s.months['2026-10'].operations.a.responses=[]]){const s=structuredClone(source);change(s);assert.throws(()=>makeSeed(s,true));}});
