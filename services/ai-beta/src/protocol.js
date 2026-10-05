export const MODEL='ludo-hybrid-v1',CONTRACT='listing-evidence-v2',GEMINI_MODEL='gemini-3.1-flash-lite';
export const MAX_INPUT_BYTES=32768,LOCAL_HEARTBEAT_TTL_MS=20000,LOCAL_LEASE_MS=10*60*1000;
export const json=(body,status=200)=>Response.json(body,{status,headers:{'cache-control':'no-store'}});
export async function digest(value){return Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(value))),b=>b.toString(16).padStart(2,'0')).join('');}
const photoHostOk=value=>{try{const u=new URL(value);const h=u.hostname.toLowerCase();return u.protocol==='https:'&&(h==='vinted.net'||h.endsWith('.vinted.net')||h==='vinted.com'||h.endsWith('.vinted.com'));}catch{return false;}};
export function validate(input){
 if(!input||Object.keys(input).some(k=>!['request_id','records'].includes(k))||!/^[-a-zA-Z0-9]{8,80}$/.test(input.request_id||'')||!Array.isArray(input.records)||input.records.length<1||input.records.length>8)throw Error('input');
 const ids=new Set();for(const r of input.records){
  if(Object.keys(r).some(k=>!['listing_id','title','brand','source_text','photos'].includes(k))||!Number.isSafeInteger(r.listing_id)||ids.has(r.listing_id)||typeof r.title!=='string'||!r.title.trim()||r.title.length>180||typeof r.brand!=='string'||r.brand.length>160)throw Error('input');
  if(typeof r.source_text!=='string'||r.source_text.length>2000||!Array.isArray(r.photos)||r.photos.length>4||r.photos.some(p=>typeof p!=='string'||p.length>2048||!photoHostOk(p)))throw Error('input');
  ids.add(r.listing_id);
 }
 return input;
}
const geminiWindowActive=env=>{const until=Number(env.FREE_TIER_VALID_UNTIL),now=Date.now();return Number.isFinite(until)&&until>now&&until<=now+86400000;};
export function serviceActive(env){return env.ENABLED==='true'&&(!!env.LOCAL_WORKER_DIGEST||geminiWindowActive(env)&&!!env.GEMINI_API_KEY);}
export function geminiActive(env){return env.ENABLED==='true'&&geminiWindowActive(env)&&!!env.GEMINI_API_KEY;}
export function localWorkerAuthorized(env,tokenDigest){return typeof env.LOCAL_WORKER_DIGEST==='string'&&/^[a-f0-9]{64}$/.test(env.LOCAL_WORKER_DIGEST)&&tokenDigest===env.LOCAL_WORKER_DIGEST;}
