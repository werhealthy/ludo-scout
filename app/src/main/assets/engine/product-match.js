(() => {
  'use strict';
  const runtime=globalThis.VintedPhase5Runtime;
  if(!runtime){globalThis.VintedProductMatcher=null;return;}
  const normalize=globalThis.VintedCatalogMatcher?.normalize||function(s){return String(s||'').normalize('NFKD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/[^a-z0-9]+/g,' ').trim();};
  const fields=runtime.productFields;
  const fi=Object.fromEntries(fields.map((x,i)=>[x,i]));
  const priceFields=runtime.priceFields;
  const pi=Object.fromEntries(priceFields.map((x,i)=>[x,i]));
  const products=runtime.products.map(r=>({
    id:r[fi.productId],bggId:String(r[fi.bggId]),title:r[fi.title]||'',publisher:r[fi.publisher]||'',year:r[fi.year]||null,
    languages:r[fi.languages]||[],gtin:r[fi.gtin]||null,identityStrength:r[fi.identityStrength],identityScore:Number(r[fi.identityScore]||0),
    reviewRequired:!!r[fi.reviewRequired],observationCount:Number(r[fi.observationCount]||0),sourceCount:Number(r[fi.sourceCount]||0),lastSeenAt:r[fi.lastSeenAt]||null,
    price:r[fi.activePrice]?{medianEUR:r[fi.activePrice][pi.medianEUR],minEUR:r[fi.activePrice][pi.minEUR],maxEUR:r[fi.activePrice][pi.maxEUR],sampleSize:r[fi.activePrice][pi.sampleSize],latestAt:r[fi.activePrice][pi.latestAt],sourceUrl:r[fi.activePrice][pi.sourceUrl],retailer:r[fi.activePrice][pi.retailer],quality:r[fi.activePrice][pi.quality]}:null
  }));
  const byId=new Map(products.map(p=>[p.id,p])),byGtin=new Map(),byBgg=new Map();
  for(const p of products){if(p.gtin)byGtin.set(String(p.gtin),p);if(!byBgg.has(p.bggId))byBgg.set(p.bggId,[]);byBgg.get(p.bggId).push(p);}

  const generic=new Set('gioco giochi tavolo board game gioco societa edition edizione versione version nuovo nuova usato usata completo completa ottime ottimo condizioni sealed sigillato scatola box the a an di del della dei da in con per of and'.split(' '));
  const editionWords=new Set('italiano italiana italian ita english inglese eng englisch englische deutsch deutsche german tedesco tedesca french francese francais francaise spanish spagnolo spagnola espanol nederlands dutch olandese portuguese portoghese polski polish second 2nd edition edizione version versione deluxe collector collectors standard revised anniversary'.split(' '));
  const token=s=>normalize(s).split(' ').filter(Boolean);
  const core=s=>token(s).filter(w=>!generic.has(w));
  const titleBase=s=>token(s).filter(w=>!generic.has(w)&&!editionWords.has(w)).join(' ');
  function publisherEqual(a,b){a=normalize(a).replace(/\b(games?|edizioni|editions?|publishing|publisher)\b/g,'').replace(/\s+/g,' ').trim();b=normalize(b).replace(/\b(games?|edizioni|editions?|publishing|publisher)\b/g,'').replace(/\s+/g,' ').trim();return !!a&&!!b&&(a===b||a.includes(b)||b.includes(a));}
  function titleMetrics(query,title){const q=core(query),t=core(title);if(!q.length||!t.length)return {coverage:0,precision:0,exact:false,phrase:false};const coverage=t.filter(w=>q.includes(w)).length/t.length,precision=q.filter(w=>t.includes(w)).length/q.length;const nq=normalize(query),nt=normalize(title);return {coverage,precision,exact:nq===nt,phrase:nq.includes(nt)||nt.includes(nq)};}
  function titleCompatibleWithGame(product,game){if(product.gtin)return true;const pbase=titleBase(product.title);if(!pbase)return false;const aliases=[game?.name,...(game?.aliases||[])].filter(Boolean);for(const a of aliases){const abase=titleBase(a);if(!abase)continue;if(pbase===abase||pbase.includes(abase)||abase.includes(pbase))return true;const pa=pbase.split(' '),aa=abase.split(' ');const cov=aa.filter(w=>pa.includes(w)).length/aa.length,prec=pa.filter(w=>aa.includes(w)).length/pa.length;if(cov>=.8&&prec>=.6)return true;}return false;}

  function digits(s){return String(s||'').replace(/\D/g,'');}
  function validGtin(s){const c=digits(s);if(![8,12,13,14].includes(c.length))return false;const ds=[...c].map(Number),body=ds.slice(0,-1),check=ds.at(-1);let total=0;for(let i=0;i<body.length;i++){const d=body[body.length-1-i];total+=d*(i%2===0?3:1);}return (10-total%10)%10===check;}
  function extractGtins(text){const found=new Set();for(const raw of String(text||'').match(/(?:\d[\s.\-/]?){8,14}/g)||[]){const d=digits(raw);if(validGtin(d))found.add(d);}return [...found];}
  const langPatterns={it:/\b(italiano|italiana|italian|ita)\b/i,en:/\b(inglese|english|eng|englisch\s*\(?eng\.?\)?)\b/i,de:/\b(tedesco|tedesca|deutsch|deutsche|german)\b/i,fr:/\b(francese|french|fran[cç]ais|fran[cç]aise)\b/i,es:/\b(spagnolo|spagnola|spanish|espa[nñ]ol|espa[nñ]ola)\b/i,nl:/\b(olandese|dutch|nederlands|nederlandse)\b/i,pt:/\b(portoghese|portuguese|portugu[eê]s)\b/i,pl:/\b(polacco|polish|polski)\b/i};
  function explicitLanguages(text){return Object.entries(langPatterns).filter(([,re])=>re.test(String(text||''))).map(([code])=>code);}

  function match({game,title='',brand='',text='',manualLanguage=null}={}){
    const joined=[title,text].filter(Boolean).join(' \n ');
    for(const code of extractGtins(joined)){const exact=byGtin.get(code);if(exact&&(!game||String(game.bggId)===exact.bggId))return {status:'matched',product:exact,score:100,method:'gtin',reason:'GTIN/EAN valido presente nell’annuncio: identità prodotto esatta.',candidates:[{product:exact,score:100}]};}
    if(!game)return {status:'none',reason:'Prima serve identificare il gioco.',candidates:[]};
    const candidates=(byBgg.get(String(game.bggId))||[]).filter(p=>!p.reviewRequired&&titleCompatibleWithGame(p,game));
    if(!candidates.length)return {status:'none',reason:'Nessuna edizione canonica sufficientemente sicura per questo gioco.',candidates:[]};
    const langs=manualLanguage?[manualLanguage]:explicitLanguages(joined);
    const ranked=[];
    for(const p of candidates){const m=titleMetrics(title,p.title);let score=p.identityScore*.12+m.coverage*40+m.precision*10+(m.exact?10:m.phrase?5:0);const reasons=[];
      if(m.coverage>=.8)reasons.push('titolo');
      const pub=publisherEqual(brand,p.publisher);if(pub){score+=22;reasons.push('editore');}
      if(langs.length&&p.languages.length){const same=langs.some(x=>p.languages.includes(x));score+=same?16:-28;if(same)reasons.push('lingua');}
      const pbase=titleBase(p.title),gbase=titleBase(game.name);const editionSpecific=pbase!==gbase||p.languages.length>0;
      if(editionSpecific&&m.exact){score+=5;reasons.push('titolo edizione');}
      ranked.push({product:p,score:Math.max(0,Math.min(99,Math.round(score))),publisherMatch:pub,title:m,editionSpecific,reasons});
    }
    ranked.sort((a,b)=>b.score-a.score||b.product.identityScore-a.product.identityScore||a.product.title.localeCompare(b.product.title));
    const top=ranked[0],second=ranked[1],margin=top.score-(second?.score||0);
    const languageUnique=langs.length&&top.product.languages.length&&langs.some(x=>top.product.languages.includes(x))&&!ranked.slice(1).some(c=>c.score>=top.score-4&&c.product.languages.some(x=>langs.includes(x)));
    const strong=(top.publisherMatch&&top.title.coverage>=.75&&top.score>=70&&margin>=6)||(top.editionSpecific&&top.title.exact&&top.score>=72&&margin>=8)||(languageUnique&&top.title.coverage>=.75&&top.score>=72);
    if(strong)return {status:'matched',product:top.product,score:top.score,method:top.publisherMatch?'title_publisher':languageUnique?'title_language':'edition_title',reason:'Edizione identificata con più indizi coerenti: '+top.reasons.join(', ')+'.',candidates:ranked.slice(0,5)};
    return {status:ranked.length?'ambiguous':'none',reason:'Gioco riconosciuto, ma l’edizione specifica non è abbastanza certa.',candidates:ranked.slice(0,5)};
  }

  function languageHint(game,brand){
    if(!game||!brand)return null;const rows=(byBgg.get(String(game.bggId))||[]).filter(p=>!p.reviewRequired&&p.languages.length===1&&publisherEqual(brand,p.publisher)&&titleCompatibleWithGame(p,game));
    const codes=[...new Set(rows.map(p=>p.languages[0]))];if(codes.length!==1)return null;
    const best=rows.sort((a,b)=>b.identityScore-a.identityScore)[0];return {code:codes[0],product:best,evidence:'L’editore/brand corrisponde a prodotti canonici Fase 5 con una sola lingua nota.',source:best.price?.sourceUrl||null};
  }

  function refFromProduct(p,newShipping=0,kind='phase5_product_median'){
    if(!p?.price||!(p.price.medianEUR>0))return null;const ship=Number.isSafeInteger(newShipping)&&newShipping>=0?newShipping:0;const itemCents=Math.round(p.price.medianEUR*100);
    return {name:p.title,cents:itemCents+ship,itemCents,kind,automatic:true,source:p.price.sourceUrl||null,retailer:p.price.retailer||null,at:p.price.latestAt||p.lastSeenAt,productId:p.id,bggId:p.bggId,editionTitle:p.title,publisher:p.publisher,languages:p.languages,gtin:p.gtin,identityStrength:p.identityStrength,priceQuality:p.price.quality,sampleSize:p.price.sampleSize,minCents:Math.round(p.price.minEUR*100),maxCents:Math.round(p.price.maxEUR*100),shippingEstimated:true,shippingCents:ship};
  }

  function reference(game,productMatch,editionLanguage,newShipping=0){
    if(!game)return null;
    if(productMatch?.status==='matched'&&productMatch.product?.price){const p=productMatch.product;if(game.languageDependence==='independent'||!editionLanguage||!p.languages.length||p.languages.includes(editionLanguage))return refFromProduct(p,newShipping,'phase5_exact_product');}
    let candidates=(byBgg.get(String(game.bggId))||[]).filter(p=>!p.reviewRequired&&p.price&&titleCompatibleWithGame(p,game));
    const dependent=game.languageDependence!=='independent';
    if(dependent){if(editionLanguage)candidates=candidates.filter(p=>p.languages.includes(editionLanguage));else candidates=candidates.filter(p=>p.languages.some(l=>l==='it'||l==='en'));}
    if(!candidates.length)return null;
    const pref=p=>editionLanguage&&p.languages.includes(editionLanguage)?0:p.languages.includes('it')?1:p.languages.includes('en')?2:p.languages.length?3:4;
    candidates.sort((a,b)=>pref(a)-pref(b)||(a.price.medianEUR-b.price.medianEUR)||b.identityScore-a.identityScore);
    const bestPref=pref(candidates[0]);const same=candidates.filter(p=>pref(p)===bestPref);const best=same.reduce((a,b)=>a.price.medianEUR<=b.price.medianEUR?a:b);
    return refFromProduct(best,newShipping,'phase5_game_product');
  }

  function snapshot(result){if(result?.status!=='matched'||!result.product)return null;return {productId:result.product.id,bggId:result.product.bggId,score:result.score,method:result.method,at:Date.now()};}
  function restore(saved){if(!saved||Date.now()-(saved.at||0)>30*86400000)return null;const p=byId.get(saved.productId);if(!p||p.bggId!==String(saved.bggId))return null;return {status:'matched',product:p,score:saved.score||0,method:saved.method||'saved',reason:'Edizione identificata in precedenza su questo annuncio.',candidates:[]};}
  function get(id){return byId.get(id)||null;}
  globalThis.VintedProductMatcher={match,reference,languageHint,snapshot,restore,get,productsForGame:game=>(byBgg.get(String(game?.bggId))||[]),extractGtins,validGtin,normalize};
})();
