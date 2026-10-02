/* Experimental passive capture: never requests, navigates or scrolls. */
(function () {
 'use strict';
 if (window.LudoCaptureControl || window !== window.top && window.top) return;
 const initial=window.__LudoCaptureInitial||{};
 let enabled=initial.enabled===true,scheduled=false,oneShot=false,observed=false,inFlight=false,activeReads=0,sequence=0,pendingSeq=0,snapshot=[],statsDirty=false,captureToken=0,pageToken=0,observedUrl=pageIdentity();
 let deliveryRetries=0,deliveryStopped=false,ackTimer=null;
 const records=new Map(),sent=new Map();
 let domSeen=new WeakMap(),staleDOM=new WeakMap(),initialDataAllowed=true;
 const stats={dropped:0,unknownShapes:0,readErrors:0,oversized:0,extraRequestsByCapture:0};
 const MAX_BODY=1024*1024,MAX_MESSAGE=128*1024,MAX_IDS=500;
 const text=(v,max=600)=>typeof v==='string'?v.slice(0,max):null;
 const id=v=>typeof v==='number'&&Number.isSafeInteger(v)&&v>0?String(v):typeof v==='string'&&/^[1-9]\d{0,18}$/.test(v)?v:null;
 function pageAllowed(){try{const u=new URL(window.location.href);return u.protocol==='https:'&&['www.vinted.it','vinted.it'].includes(u.hostname)&&(!u.port||u.port==='443')&&/^\/(?:catalog(?:\/|$)|items\/\d+|member\/\d+|members\/\d+|$)/.test(u.pathname);}catch(_){return false;}}
 function url(value){try{const u=new URL(value,window.location.href);return u.protocol==='https:'&&['www.vinted.it','vinted.it'].includes(u.hostname)&&(!u.port||u.port==='443')&&!u.username&&!u.password?u:null;}catch(_){return null;}}
 function endpoint(value){const u=url(value);return u&&/^\/api\/v\d+\/(?:catalog\/items(?:\/|$)|items\/\d+(?:\/|$)|users\/\d+\/items(?:\/|$))/.test(u.pathname);}
 function photo(value){if(typeof value!=="string"||value.length>2048)return null;try{const u=new URL(value);return u.protocol==='https:'&&!u.username&&!u.password?u.href:null;}catch(_){return null;}}
 function cents(value){if(value&&typeof value==='object')value=value.amount;if(typeof value==='number')value=String(value);if(typeof value!=='string'||!/^\d{1,7}(?:[.,]\d{1,2})?$/.test(value.trim()))return null;const n=Math.round(Number(value.trim().replace(',','.'))*100);return Number.isSafeInteger(n)&&n>0?n:null;}
 function normalize(x,source){
  if(!x||typeof x!=='object')return null;
  let u=url(x.url||x.href||''),n=id(x.id)||(u&&id((u.pathname.match(/^\/items\/(\d+)/)||[])[1]));
  const title=text(x.title||x.name);if(!n||!title||((x.url||x.href)&&!u))return null;
  if((x.url||x.href)&&(!/^\/items\/[1-9]\d*(?:-[^/]*)?$/.test(u.pathname)||id((u.pathname.match(/^\/items\/(\d+)/)||[])[1])!==n))return null;
  if(!x.url&&!x.href&&!x.title)return null;
  const seller=id(x.user_id)||id(x.seller_id)||id(x.user&&x.user.id)||id(x.seller&&x.seller.id);
  const published=text(x.published_at)||text(x.datePublished);let publication=published?{raw:published,source:source+':'+(x.published_at?'published_at':'datePublished')}:null;
  const currency=text(x.price&&x.price.currency_code||x.currency_code||x.currency,12);
  const images=Array.isArray(x.photos)?x.photos.slice(0,10).map(p=>photo(p&&p.url)).filter(Boolean):[];
  const image=photo(x.photo&&x.photo.url||x.image||x.image_url);if(image&&!images.includes(image))images.unshift(image);
  return {id:n,url:'https://www.vinted.it/items/'+n,title,priceCents:cents(x.price||x.offers&&x.offers.price),currency,protectedPriceCents:cents(x.total_item_price),sellerId:seller,sellerName:text(x.user&&x.user.login||x.seller&&x.seller.login,100),expectedSellerMatch:initial.expectedSeller? seller===String(initial.expectedSeller):null,photos:images,brand:text(x.brand_title||x.brand&&x.brand.title,120),condition:text(x.status||x.condition,120),description:text(x.description,2000),language:text(x.language,80),publication,source};
 }
 function keep(item){if(!item)return;const old=records.get(item.id);if(!old&&records.size>=MAX_IDS){bump("dropped");return;}if(old&&item.source==='dom'&&old.source!=='dom'){for(const k of Object.keys(old))if(old[k]!=null&&(!Array.isArray(old[k])||old[k].length))item[k]=old[k];}if(old){for(const k of Object.keys(item))if(item[k]==null||Array.isArray(item[k])&&!item[k].length)item[k]=old[k];}records.set(item.id,item);schedule();}
 function readShape(data,source){syncDocument();let count=0,budget=2000;const visit=(x,depth)=>{if(!x||typeof x!=='object'||depth>7||--budget<0)return;if(Array.isArray(x)){for(const v of x.slice(0,600))visit(v,depth+1);return;}const normalized=normalize(x,source);if(normalized){keep(normalized);count++;return;}for(const key of ['items','item','data','catalog','props','pageProps','initialState','catalogItems','product','offers'])if(x[key])visit(x[key],depth+1);};visit(data,0);if(!count)bump("unknownShapes");}
 function bump(key){stats[key]++;statsDirty=true;schedule();}
 function schedule(){if(deliveryStopped||scheduled||!enabled&&!oneShot)return;scheduled=true;setTimeout(flush,15);}
 function flush(){scheduled=false;if(deliveryStopped||inFlight||!enabled&&!oneShot||!pageAllowed())return;const batch=[];for(const item of (oneShot?snapshot:records.values())){const key=JSON.stringify(item);if(sent.get(item.id)===key)continue;batch.push(item);}const deliver=items=>{const seq=++sequence;const message=JSON.stringify({schema:1,seq,captureToken,pageToken,page:pageReport(),items,complete:!items.length&&oneShot,stats:{...stats}});if(new TextEncoder().encode(message).length>MAX_MESSAGE){bump("oversized");return;}try{inFlight=true;pendingSeq=seq;statsDirty=false;for(const x of items)sent.set(x.id,JSON.stringify(x));window.LudoCapture.postMessage(message);if(inFlight&&pendingSeq===seq)ackTimer=setTimeout(()=>deliveryReply('retry',seq),5000);}catch(_){inFlight=false;for(const x of items)sent.delete(x.id);bump("readErrors");}};
  if(batch.length){const chunk=[];for(const item of batch.slice(0,32)){if(new TextEncoder().encode(JSON.stringify({schema:1,items:[...chunk,item],stats})).length>MAX_MESSAGE-8192)break;chunk.push(item);}if(chunk.length)deliver(chunk);else{bump("oversized");sent.set(batch[0].id,JSON.stringify(batch[0]));schedule();}}else if(oneShot){deliver([]);oneShot=false;snapshot=[];}else if(statsDirty)deliver([]);
 }

 function navigationUrl(action,value,base){
  if(!pageAllowed())return null;let u=url(window.location.href);if(!u)return null;if(!/^\/catalog(?:\/|$)/.test(u.pathname)){if(action==='next'||action==='previous')return null;u=url(base||'https://www.vinted.it/catalog/4881-board-games');if(!u||!/^\/catalog(?:\/|$)/.test(u.pathname))return null;}
  const page=Math.max(1,Math.min(10,parseInt(u.searchParams.get('page')||'1',10)||1));
  if(action==='previous'&&page<=1||action==='next'&&page>=10)return null;
  u.pathname='/catalog/4881-board-games';u.hash='';u.searchParams.delete('catalog[]');u.searchParams.delete('catalog_ids[]');u.searchParams.delete('catalog_id');
  if(action==='next'||action==='previous')u.searchParams.set('page',String(page+(action==='next'?1:-1)));
  else{u.searchParams.set('page','1');if(action==='search'){if(typeof value!=='string'||value.length>100)return null;if(value.trim())u.searchParams.set('search_text',value.trim());else u.searchParams.delete('search_text');}
   else if(action==='order'){if(!['newest_first','relevance','price_low_to_high','price_high_to_low'].includes(value))return null;u.searchParams.set('order',value);}
   else if(action==='pricePlus'||action==='priceMinus'){const raw=u.searchParams.get('price_from')||'0';if(!/^\d{1,5}(?:[.,]\d{1,2})?$/.test(raw))return null;const n=Math.max(0,Math.min(9999900,Math.round(Number(raw.replace(',','.'))*100)+(action==='pricePlus'?100:-100)));u.searchParams.set('price_from',String(n/100));u.searchParams.delete('price_to');u.searchParams.set('order','price_low_to_high');}
   else return null;}
  return u.href;
 }
 function pageIdentity(){try{const u=new URL(window.location.href);u.hash='';return u.href;}catch(_){return window.location.href;}}
 function syncDocument(){if(observedUrl===pageIdentity())return;observedUrl=pageIdentity();clearTimeout(ackTimer);ackTimer=null;deliveryRetries=0;deliveryStopped=false;staleDOM=domSeen;domSeen=new WeakMap();initialDataAllowed=false;records.clear();sent.clear();snapshot=[];oneShot=false;inFlight=false;pendingSeq=0;pageToken=0;for(const key of ['dropped','unknownShapes','readErrors','oversized'])stats[key]=0;statsDirty=false;if(pageAllowed())try{window.LudoCapture.postMessage(JSON.stringify({kind:'location',url:observedUrl.slice(0,4096)}));}catch(_){} }
 function pageReport(){let withPrice=0;for(const x of records.values())if(x.priceCents!=null)withPrice++;return {url:window.location.href.slice(0,4096),observed:records.size,withPrice};}
 function domCard(anchor,image){
  const titleAttr=anchor.getAttribute('title')||'',alt=image&&image.getAttribute('alt')||'';
  const labelled=v=>/,\s*(?:brand|marca|marque|condizioni|condition|état|estado|zustand)\s*:/i.test(v);
  const label=labelled(titleAttr)?titleAttr:labelled(alt)?alt:titleAttr||alt||text(anchor.textContent)||'';
  const start=label.search(/,\s*(?:brand|marca|marque|condizioni|condition|état|estado|zustand)\s*:/i);
  const title=start>=0?label.slice(0,start).trim():label;
  const metadata=start>=0?label.slice(start):'';
  const amounts=Array.from(metadata.matchAll(/([0-9][0-9.,\u00a0 ]*)\s*€/g));
  const brand=(metadata.match(/(?:brand|marca|marque)\s*:\s*([^,]+)/i)||[])[1];
  const condition=(metadata.match(/(?:condizioni|condition|état|estado|zustand)\s*:\s*([^,]+)/i)||[])[1];
  return {url:anchor.getAttribute('href'),title,price:amounts[0]&&amounts[0][1],total_item_price:amounts[1]&&amounts[1][1],currency:amounts.length?'EUR':null,brand_title:brand,condition,image:image&&image.getAttribute('src')};
 }

 function dom(){syncDocument();if(!pageAllowed())return;try{for(const anchor of Array.from(document.querySelectorAll('a[href*="/items/"]')).slice(0,600)){const u=url(anchor.getAttribute('href'));if(!u||!/^\/items\/\d+/.test(u.pathname))continue;const image=anchor.querySelector('img');const signature=JSON.stringify([anchor.getAttribute('href'),anchor.getAttribute('title'),image&&image.getAttribute('alt'),image&&image.getAttribute('src'),text(anchor.textContent,2000)]);const stale=staleDOM.get(anchor)===signature;domSeen.set(anchor,signature);if(stale)continue;keep(normalize(domCard(anchor,image),'dom'));}if(initialDataAllowed)for(const node of Array.from(document.querySelectorAll('script[type="application/ld+json"],script#__NEXT_DATA__')).slice(0,10)){if(node.textContent&&node.textContent.length<=MAX_BODY)try{readShape(JSON.parse(node.textContent),'initial');}catch(_){bump("readErrors");}}}catch(_){bump("readErrors");}}
 async function readResponse(response){if(!pageAllowed()||!response||response.status<200||response.status>=300)return;if(activeReads>=4){bump("dropped");return;}activeReads++;try{if(!/json/i.test(response.headers.get('content-type')||''))return;const length=Number(response.headers.get('content-length'));if(length>MAX_BODY){bump("oversized");return;}const copy=response.clone();if(!copy.body||!copy.body.getReader){bump("readErrors");return;}const requestPage=window.location.href,reader=copy.body.getReader(),decoder=new TextDecoder();let body='',bytes=0;try{while(true){const part=await reader.read();if(part.done)break;bytes+=part.value.byteLength;if(bytes>MAX_BODY){bump("oversized");reader.cancel().catch(()=>{});return;}body+=decoder.decode(part.value,{stream:true});}body+=decoder.decode();if(requestPage===window.location.href)readShape(JSON.parse(body),'json');}catch(_){bump("readErrors");}}catch(_){bump("readErrors");}finally{activeReads--;}}
 if(typeof window.fetch==='function'){const original=window.fetch;window.fetch=function(){const value=original.apply(this,arguments);const requestPage=window.location.href,input=arguments[0];let requestUrl=typeof input==='string'?input:input&&input.url;if(endpoint(requestUrl)&&pageAllowed())value.then(response=>{if(requestPage===window.location.href&&(!response.url||endpoint(response.url)))readResponse(response);},()=>{bump("readErrors");});return value;};}
 if(window.XMLHttpRequest){const proto=window.XMLHttpRequest.prototype,open=proto.open,send=proto.send;const target=new WeakMap();proto.open=function(method,value){target.set(this,typeof value==='string'?value:'');return open.apply(this,arguments);};proto.send=function(){const requestPage=window.location.href;if(endpoint(target.get(this))&&pageAllowed())this.addEventListener('load',function(){if(requestPage!==window.location.href||this.status<200||this.status>=300||this.responseURL&&!endpoint(this.responseURL))return;try{if(this.responseType==='json')readShape(this.response,'xhr');else if(this.responseType===''||this.responseType==='text'){if(this.responseText.length<=MAX_BODY)readShape(JSON.parse(this.responseText),'xhr');else bump("oversized");}}catch(_){bump("readErrors");}},{once:true});return send.apply(this,arguments);};}
 function stopDelivery(reason){deliveryStopped=true;oneShot=false;clearTimeout(ackTimer);ackTimer=null;try{window.LudoCapture.postMessage(JSON.stringify({kind:'deliveryError',pageToken,url:pageIdentity(),reason}));}catch(_){}}
 function deliveryReply(kind,seq){if(seq!==pendingSeq)return;clearTimeout(ackTimer);ackTimer=null;pendingSeq=0;inFlight=false;if(kind==='rejected'){stopDelivery('COMMIT_REJECTED');return;}if(kind==='retry'){if(++deliveryRetries>=3){stopDelivery('RETRY_EXHAUSTED');return;}sent.clear();setTimeout(schedule,100);return;}if(kind==='paused'){sent.clear();return;}if(kind==='ready'){deliveryRetries=0;schedule();}}
 if(window.LudoCapture)window.LudoCapture.onmessage=function(event){const parts=String(event.data).split(':');deliveryReply(parts[0],Number(parts[1]));};
 window.LudoCaptureControl={navigationUrl,setPageToken(value){clearTimeout(ackTimer);ackTimer=null;deliveryRetries=0;deliveryStopped=false;pageToken=value;pendingSeq=0;inFlight=false;sent.clear();schedule();},setEnabled(value){clearTimeout(ackTimer);ackTimer=null;if(value===true&&deliveryStopped){deliveryStopped=false;deliveryRetries=0;sent.clear();}pendingSeq=0;inFlight=false;oneShot=false;snapshot=[];if(value===true&&!enabled)sent.clear();enabled=value===true;if(enabled){dom();schedule();}},captureNow(token){clearTimeout(ackTimer);ackTimer=null;deliveryRetries=0;deliveryStopped=false;sent.clear();captureToken=Number.isSafeInteger(token)?token:0;pendingSeq=0;inFlight=false;oneShot=false;dom();snapshot=Array.from(records.values());oneShot=true;schedule();}};
 if(window.history)for(const key of ['pushState','replaceState']){const original=window.history[key];if(typeof original==='function')window.history[key]=function(){const result=original.apply(this,arguments);syncDocument();dom();return result;};}if(window.addEventListener)window.addEventListener('popstate',()=>{syncDocument();dom();});
 function ready(){dom();if(!observed&&window.MutationObserver){observed=true;let pending=false;new MutationObserver(()=>{if(pending)return;pending=true;setTimeout(()=>{pending=false;dom();},200);}).observe(document,{childList:true,subtree:true});}}
 if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',ready,{once:true});else ready();
})();
