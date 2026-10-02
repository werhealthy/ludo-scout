/* Experimental passive capture: never requests, navigates or scrolls. */
(function () {
 'use strict';
 if (window.LudoCaptureControl || window !== window.top && window.top) return;
 const initial=window.__LudoCaptureInitial||{};
 let enabled=initial.enabled===true,scheduled=false,oneShot=false,observed=false;
 const records=new Map(),sent=new Map();
 const stats={dropped:0,unknownShapes:0,readErrors:0,oversized:0,extraRequestsByCapture:0};
 const MAX_BODY=1024*1024,MAX_MESSAGE=128*1024,MAX_IDS=500;
 const text=(v,max=600)=>typeof v==='string'?v.slice(0,max):null;
 const id=v=>typeof v==='number'&&Number.isSafeInteger(v)&&v>0?String(v):typeof v==='string'&&/^[1-9]\d{0,18}$/.test(v)?v:null;
 function pageAllowed(){try{const u=new URL(window.location.href);return u.protocol==='https:'&&['www.vinted.it','vinted.it'].includes(u.hostname)&&(!u.port||u.port==='443')&&/^\/(?:catalog(?:\/|$)|items\/\d+|member\/\d+|members\/\d+|$)/.test(u.pathname);}catch(_){return false;}}
 function url(value){try{const u=new URL(value,window.location.href);return u.protocol==='https:'&&['www.vinted.it','vinted.it'].includes(u.hostname)&&(!u.port||u.port==='443')&&!u.username&&!u.password?u:null;}catch(_){return null;}}
 function endpoint(value){const u=url(value);return u&&/^\/api\/v\d+\/(?:catalog\/items(?:\/|$)|items\/\d+(?:\/|$)|users\/\d+\/items(?:\/|$))/.test(u.pathname);}
 function photo(value){try{const u=new URL(value);return u.protocol==='https:'&&!u.username&&!u.password?u.href:null;}catch(_){return null;}}
 function cents(value){if(value&&typeof value==='object')value=value.amount;if(typeof value==='number')value=String(value);if(typeof value!=='string'||!/^\d{1,7}(?:[.,]\d{1,2})?$/.test(value.trim()))return null;const n=Math.round(Number(value.trim().replace(',','.'))*100);return Number.isSafeInteger(n)&&n>0?n:null;}
 function normalize(x,source){
  if(!x||typeof x!=='object')return null;
  let u=url(x.url||x.href||''),n=id(x.id)||(u&&id((u.pathname.match(/^\/items\/(\d+)/)||[])[1]));
  const title=text(x.title||x.name);if(!n||!title||((x.url||x.href)&&!u))return null;
  if(u&&/^\/items\//.test(u.pathname)&&id((u.pathname.match(/^\/items\/(\d+)/)||[])[1])!==n)return null;
  const seller=id(x.user_id)||id(x.seller_id)||id(x.user&&x.user.id)||id(x.seller&&x.seller.id);
  const published=text(x.published_at)||text(x.datePublished);let publication=published?{raw:published,source:source+':'+(x.published_at?'published_at':'datePublished')}:null;
  const currency=text(x.price&&x.price.currency_code||x.currency_code||x.currency,12);
  const images=Array.isArray(x.photos)?x.photos.slice(0,10).map(p=>photo(p&&p.url)).filter(Boolean):[];
  const image=photo(x.photo&&x.photo.url||x.image||x.image_url);if(image&&!images.includes(image))images.unshift(image);
  return {id:n,url:'https://www.vinted.it/items/'+n,title,priceCents:cents(x.price||x.offers&&x.offers.price),currency,protectedPriceCents:cents(x.total_item_price),sellerId:seller,sellerName:text(x.user&&x.user.login||x.seller&&x.seller.login,100),expectedSellerMatch:initial.expectedSeller? seller===String(initial.expectedSeller):null,photos:images,brand:text(x.brand_title||x.brand&&x.brand.title,120),condition:text(x.status||x.condition,120),description:text(x.description,2000),language:text(x.language,80),publication,source};
 }
 function keep(item){if(!item)return;const old=records.get(item.id);if(!old&&records.size>=MAX_IDS){stats.dropped++;return;}if(old&&item.source==='dom'&&old.source!=='dom'){for(const k of Object.keys(old))if(old[k]!=null&&(!Array.isArray(old[k])||old[k].length))item[k]=old[k];}if(old){for(const k of Object.keys(item))if(item[k]==null||Array.isArray(item[k])&&!item[k].length)item[k]=old[k];}records.set(item.id,item);schedule();}
 function readShape(data,source){let count=0,budget=2000;const visit=(x,depth)=>{if(!x||typeof x!=='object'||depth>7||--budget<0)return;if(Array.isArray(x)){for(const v of x.slice(0,600))visit(v,depth+1);return;}const normalized=normalize(x,source);if(normalized){keep(normalized);count++;return;}for(const key of ['items','item','data','catalog','props','pageProps','initialState','catalogItems','product','offers'])if(x[key])visit(x[key],depth+1);};visit(data,0);if(!count)stats.unknownShapes++;}
 function schedule(){if(scheduled||!enabled&&!oneShot)return;scheduled=true;setTimeout(flush,15);}
 function flush(){scheduled=false;if(!enabled&&!oneShot||!pageAllowed())return;const batch=[];for(const item of records.values()){const key=JSON.stringify(item);if(sent.get(item.id)===key)continue;batch.push(item);}const deliver=items=>{const message=JSON.stringify({schema:1,items,stats:{...stats}});if(new TextEncoder().encode(message).length>MAX_MESSAGE){stats.oversized++;return;}try{window.LudoCapture.postMessage(message);for(const x of items)sent.set(x.id,JSON.stringify(x));}catch(_){stats.readErrors++;}};
  for(let i=0;i<batch.length;i+=32)deliver(batch.slice(i,i+32));if(!batch.length&&oneShot)deliver([]);oneShot=false;
 }
 function dom(){if(!pageAllowed())return;try{for(const anchor of Array.from(document.querySelectorAll('a[href*="/items/"]')).slice(0,600)){const u=url(anchor.getAttribute('href'));if(!u||!/^\/items\/\d+/.test(u.pathname))continue;const image=anchor.querySelector('img');keep(normalize({url:u.href,title:anchor.getAttribute('title')||image&&image.getAttribute('alt')||text(anchor.textContent),image:image&&image.getAttribute('src')},'dom'));}for(const node of Array.from(document.querySelectorAll('script[type="application/ld+json"],script#__NEXT_DATA__')).slice(0,10)){if(node.textContent&&node.textContent.length<=MAX_BODY)try{readShape(JSON.parse(node.textContent),'initial');}catch(_){stats.readErrors++;}}}catch(_){stats.readErrors++;}}
 async function readResponse(response){if(!pageAllowed()||!response||response.status<200||response.status>=300)return;try{if(!/json/i.test(response.headers.get('content-type')||''))return;const length=Number(response.headers.get('content-length'));if(length>MAX_BODY){stats.oversized++;return;}const copy=response.clone();if(!copy.body||!copy.body.getReader){stats.readErrors++;return;}const reader=copy.body.getReader(),decoder=new TextDecoder();let body='',bytes=0;try{while(true){const part=await reader.read();if(part.done)break;bytes+=part.value.byteLength;if(bytes>MAX_BODY){stats.oversized++;reader.cancel().catch(()=>{});return;}body+=decoder.decode(part.value,{stream:true});}body+=decoder.decode();readShape(JSON.parse(body),'json');}catch(_){stats.readErrors++;}}catch(_){stats.readErrors++;}}
 if(typeof window.fetch==='function'){const original=window.fetch;window.fetch=function(){const value=original.apply(this,arguments);const input=arguments[0];let requestUrl=typeof input==='string'?input:input&&input.url;if(endpoint(requestUrl)&&pageAllowed())value.then(response=>{if(!response.url||endpoint(response.url))readResponse(response);},()=>{stats.readErrors++;});return value;};}
 if(window.XMLHttpRequest){const proto=window.XMLHttpRequest.prototype,open=proto.open,send=proto.send;const target=new WeakMap();proto.open=function(method,value){target.set(this,typeof value==='string'?value:'');return open.apply(this,arguments);};proto.send=function(){if(endpoint(target.get(this))&&pageAllowed())this.addEventListener('load',function(){if(this.status<200||this.status>=300||this.responseURL&&!endpoint(this.responseURL))return;try{if(this.responseType==='json')readShape(this.response,'xhr');else if(this.responseType===''||this.responseType==='text'){if(this.responseText.length<=MAX_BODY)readShape(JSON.parse(this.responseText),'xhr');else stats.oversized++;}}catch(_){stats.readErrors++;}},{once:true});return send.apply(this,arguments);};}
 window.LudoCaptureControl={setEnabled(value){enabled=value===true;if(enabled){dom();schedule();}},captureNow(){oneShot=true;dom();schedule();}};
 function ready(){dom();if(!observed&&window.MutationObserver){observed=true;let pending=false;new MutationObserver(()=>{if(pending)return;pending=true;setTimeout(()=>{pending=false;dom();},200);}).observe(document,{childList:true,subtree:true});}}
 if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',ready,{once:true});else ready();
})();
