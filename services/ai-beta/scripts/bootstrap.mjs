import {createHash} from 'node:crypto';
import {readFile,writeFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
function micro(value){if(typeof value!=='string'||!/^\d*(?:\.\d{1,6})?$/.test(value)||!/[0-9]/.test(value))throw Error('invalid reservation');const [whole,fraction='']=value.split('.');const n=Number(whole||0)*1000000+Number(fraction.padEnd(6,'0'));if(!Number.isSafeInteger(n))throw Error('invalid reservation');return n;}
export function makeSeed(ledger,benchmarkDisabled){
 if(benchmarkDisabled!==true||ledger.version!==1||typeof ledger.disabled!=='boolean'||!ledger.months||typeof ledger.months!=='object')throw Error('cutover/ledger not verified');
 const months={};for(const [month,m]of Object.entries(ledger.months)){
  if(!/^\d{4}-\d{2}$/.test(month)||!Number.isInteger(m.calls_reserved)||m.calls_reserved<0||m.calls_reserved>100||!m.operations)throw Error('invalid month');
  let calls=0,money=0;for(const op of Object.values(m.operations)){if(!Number.isInteger(op.calls_reserved)||op.calls_reserved<0||!Array.isArray(op.responses)||op.responses.length!==op.calls_reserved)throw Error('invalid operation');const reserved=micro(op.reserved_eur);if(reserved<op.calls_reserved*10000)throw Error('invalid operation reservation');calls+=op.calls_reserved;money+=reserved;}
  const reserved=micro(m.reserved_eur);if(calls!==m.calls_reserved||money!==reserved||reserved<calls*10000||reserved>1000000)throw Error('inconsistent accounting');months[month]={calls_reserved:calls,reserved_micro:reserved};
 }
 return {version:1,source_sha256:createHash('sha256').update(JSON.stringify(ledger)).digest('hex'),benchmark_disabled:true,months};
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
 if(process.argv.length!==5||process.argv[4]!=='--benchmark-disabled')throw Error('usage: node scripts/bootstrap.mjs LEDGER OUTPUT --benchmark-disabled');
 const seed=makeSeed(JSON.parse(await readFile(process.argv[2],'utf8')),true);await writeFile(process.argv[3],JSON.stringify(seed),{flag:'wx',mode:0o600});
}
