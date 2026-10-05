import {digest,json,localWorkerAuthorized,MAX_INPUT_BYTES,serviceActive,validate} from './protocol.js';
export {BudgetObject} from './budget-object.js';

async function boundedJson(request,max=MAX_INPUT_BYTES){
 const reader=request.body?.getReader();if(!reader)throw Error('input');
 let size=0,chunks=[];while(true){const {value,done}=await reader.read();if(done)break;size+=value.length;if(size>max){await reader.cancel();throw Error('input');}chunks.push(value);}
 const bytes=new Uint8Array(size);let offset=0;for(const c of chunks){bytes.set(c,offset);offset+=c.length;}return JSON.parse(new TextDecoder().decode(bytes));
}
export default {async fetch(request,env){try{
 const path=new URL(request.url).pathname;
 const local=path.startsWith('/v1/local/');
 const allowed=local?['/v1/local/heartbeat','/v1/local/claim','/v1/local/result']:['/v1/classify','/v1/status'];
 if(!allowed.includes(path))return json({status:'NOT_FOUND'},404);
 if(request.method!==(path==='/v1/status'?'GET':'POST'))return json({status:'METHOD_NOT_ALLOWED'},405);
 const auth=request.headers.get('authorization')||'';if(!auth.startsWith('Bearer ')||auth.length>512)return json({status:'UNAUTHORIZED'},401);
 const tokenDigest=await digest(auth.slice(7));
 const stub=env.BUDGET.get(env.BUDGET.idFromName('global-ai-beta-v1'));
 if(local){
  if(!localWorkerAuthorized(env,tokenDigest))return json({status:'UNAUTHORIZED'},401);
  let body={};if(path!=='/v1/local/heartbeat'){try{body=await boundedJson(request,65536);}catch{return json({status:'INVALID_INPUT'},400);}}
  return await stub.fetch('https://internal'+path,{method:'POST',headers:{'x-local-worker':'1','content-type':'application/json'},body:JSON.stringify(body)});
 }
 const devices=JSON.parse(env.DEVICE_DIGESTS||'{}');const device=Object.keys(devices).find(k=>/^[a-zA-Z0-9_-]{1,64}$/.test(k)&&devices[k]===tokenDigest);if(!device)return json({status:'UNAUTHORIZED'},401);
 let body;if(path==='/v1/classify'){if(!serviceActive(env))return json({status:'DISABLED'},503);try{body=JSON.stringify(validate(await boundedJson(request)));}catch{return json({status:'INVALID_INPUT'},400);}}
 return await stub.fetch('https://internal'+path,{method:request.method,headers:{'x-device':device,'content-type':'application/json'},body});
 }catch{return json({status:'CONFIG_BLOCKED'},503);}}};
