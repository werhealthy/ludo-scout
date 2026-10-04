import {createHash,randomBytes} from 'node:crypto';
import {readFile,writeFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import {makeSeed} from './bootstrap.mjs';
const endpoint='https://ludo-ai-beta.havas-html-to-figma.workers.dev/v1/status';
export function buildProvision(ledger,o){
 if(o.benchmarkEnabled!=='false'||o.freeConfirmed!=='true'||typeof o.geminiKey!=='string'||!o.geminiKey.trim()||!/^[a-f0-9]{64}$/.test(o.deviceDigest||'')||!o.probeToken)throw Error('Provision prerequisites missing');
 const seed=makeSeed(ledger,true),phone={'francesco-phone-1':o.deviceDigest};
 return {secrets:{GEMINI_API_KEY:o.geminiKey,SEED_MANIFEST:JSON.stringify(seed),DEVICE_DIGESTS:JSON.stringify({...phone,'provision-check':createHash('sha256').update(o.probeToken).digest('hex')})},cleanup:{DEVICE_DIGESTS:JSON.stringify(phone)}};
}
export function verifyStatus(status,seed,month){
 const expected=seed.months[month];
 if(!expected||status?.enabled!==false||status.budget?.month!==month||status.budget.calls_reserved!==expected.calls_reserved||status.budget.reserved_micro!==expected.reserved_micro)throw Error('Disabled status/accounting verification failed');
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
 try{
  const [mode,ledgerFile,dir]=process.argv.slice(2);
  if(!ledgerFile||!dir||!['prepare','verify','revoked'].includes(mode))throw Error('Usage');
  const ledger=JSON.parse(await readFile(ledgerFile,'utf8'));
  if(mode==='prepare'){
   const token=randomBytes(32).toString('base64url');
   const p=buildProvision(ledger,{benchmarkEnabled:process.env.AI_BENCHMARK_ENABLED,freeConfirmed:process.env.FREE_PROJECTS_CONFIRMED,geminiKey:process.env.GEMINI_API_KEY,deviceDigest:process.env.DEVICE_DIGEST,probeToken:token});
   for(const [name,value]of [['secrets.json',JSON.stringify(p.secrets)],['cleanup.json',JSON.stringify(p.cleanup)],['probe-token',token]])
    await writeFile(dir+'/'+name,value,{flag:'wx',mode:0o600});
  }else{
   const token=await readFile(dir+'/probe-token','utf8');
   const response=await fetch(endpoint,{headers:{authorization:'Bearer '+token},signal:AbortSignal.timeout(15000)});
   if(mode==='revoked'){
    if(response.status!==401)throw Error('Verification credential not revoked');
    console.log('Temporary verification credential confirmed revoked.');
   }else{
   if(response.status!==200)throw Error('Status failed');
   const status=await response.json();
   verifyStatus(status,makeSeed(ledger,true),new Date().toISOString().slice(0,7));
   console.log('Authenticated status verified: AI disabled; imported historical accounting matches.');
   }
  }
 }catch{console.error('Private provisioning failed; details and credentials redacted.');process.exitCode=1;}
}
