import {active,digest,json,validate} from './protocol.js';
export {BudgetObject} from './budget-object.js';
export default {async fetch(request,env){try{
 const path=new URL(request.url).pathname;if(!['/v1/classify','/v1/status'].includes(path))return json({status:'NOT_FOUND'},404);
 if(request.method!==(path==='/v1/status'?'GET':'POST'))return json({status:'METHOD_NOT_ALLOWED'},405);
 const auth=request.headers.get('authorization')||'';if(!auth.startsWith('Bearer ')||auth.length>512)return json({status:'UNAUTHORIZED'},401);
 const value=await digest(auth.slice(7));const devices=JSON.parse(env.DEVICE_DIGESTS||'{}');const device=Object.keys(devices).find(k=>/^[a-zA-Z0-9_-]{1,64}$/.test(k)&&devices[k]===value);if(!device)return json({status:'UNAUTHORIZED'},401);
 let body;if(path==='/v1/classify'){if(!active(env))return json({status:'DISABLED'},503);const reader=request.body?.getReader();if(!reader)return json({status:'INVALID_INPUT'},400);let size=0,chunks=[];while(true){const {value,done}=await reader.read();if(done)break;size+=value.length;if(size>4096){await reader.cancel();return json({status:'INVALID_INPUT'},400);}chunks.push(value);}const bytes=new Uint8Array(size);let offset=0;for(const c of chunks){bytes.set(c,offset);offset+=c.length;}try{body=JSON.stringify(validate(JSON.parse(new TextDecoder().decode(bytes))));}catch{return json({status:'INVALID_INPUT'},400);}}
 const stub=env.BUDGET.get(env.BUDGET.idFromName('global-ai-beta-v1'));
 return await stub.fetch('https://internal'+path,{method:request.method,headers:{'x-device':device,'content-type':'application/json'},body});
 }catch{return json({status:'CONFIG_BLOCKED'},503);}}};
