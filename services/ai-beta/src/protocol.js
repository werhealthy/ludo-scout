export const MODEL='gemini-3.1-flash-lite',CONTRACT='title-brand-beta-v1';
export const json=(body,status=200)=>Response.json(body,{status,headers:{'cache-control':'no-store'}});
export async function digest(value){return Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(value))),b=>b.toString(16).padStart(2,'0')).join('');}
export function validate(input){
 if(!input||Object.keys(input).some(k=>!['request_id','records'].includes(k))||!/^[-a-zA-Z0-9]{8,80}$/.test(input.request_id||'')||!Array.isArray(input.records)||input.records.length<1||input.records.length>8)throw Error('input');
 const ids=new Set();for(const r of input.records){if(Object.keys(r).some(k=>!['listing_id','title','brand'].includes(k))||!Number.isSafeInteger(r.listing_id)||ids.has(r.listing_id)||typeof r.title!=='string'||!r.title.trim()||typeof r.brand!=='string')throw Error('input');ids.add(r.listing_id);}
 return input;
}
export function active(env){const until=Number(env.FREE_TIER_VALID_UNTIL),now=Date.now();return env.ENABLED==='true'&&Number.isFinite(until)&&until>now&&until<=now+86400000&&!!env.GEMINI_API_KEY;}
