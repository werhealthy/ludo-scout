(() => {
  const names={it:'Italiano',en:'Inglese',de:'Tedesco',fr:'Francese',es:'Spagnolo',pt:'Portoghese',nl:'Olandese',pl:'Polacco',ru:'Russo',ja:'Giapponese'};
  const words={it:'italiano|italiana|italian|ita',en:'inglese|english|eng|englisch|englische',de:'tedesco|tedesca|deutsch|deutsche|deutsches|german',fr:'francese|french|français|française',es:'spagnolo|spagnola|español|española|spanish',pt:'portoghese|português|portuguese',nl:'olandese|dutch|nederlands|nederlandse|nederlandstalig',pl:'polacco|polish|polski',ru:'russo|russian',ja:'giapponese|japanese'};
  function detect(text,gameName=''){
    let source=String(text||'');if(gameName)source=source.replace(new RegExp(gameName.replace(/[.*+?^${}()|[\]\\]/g,'\\$&'),'ig'),'');
    const found=[];
    for(const clause of source.split(/[.;,\n!?]+/))for(const [code,list]of Object.entries(words)){
      const re=new RegExp('\\b('+list+')\\b','ig');
      for(const m of clause.matchAll(re)){
        const before=clause.slice(0,m.index);
        if(/\b(non|not|no|senza|without)\s+(?:(?:in|è|e|is|the|di|versione|edizione|lingua)\s+){0,5}$/i.test(before))continue;
        if(/\b(regolamento|manuale|rules|traduzion\w*|translation|parlo|speak|spedizione|shipping)\b[^.;,]*$/i.test(before))continue;
        found.push({code,text:clause.trim().slice(0,160)});
      }
    }
    const distinct=[...new Map(found.map(x=>[x.code,x])).values()];
    if(distinct.length===1)return {code:distinct[0].code,evidence:'Testo visibile: «'+distinct[0].text+'». Indizio sull’edizione, non verifica della scatola.'};
    return {code:null,evidence:distinct.length?'Più lingue citate: edizione non univoca.':'Nessuna lingua dell’edizione esplicita nel testo letto.'};
  }
  function publisherEdition(game,brand){
    const phase5=globalThis.VintedProductMatcher?.languageHint?.(game,brand);
    if(phase5)return {code:phase5.code,inferred:true,evidence:phase5.evidence,source:phase5.source,productId:phase5.product?.id,phase5:true,at:Date.now()};
    const norm=s=>String(s||'').toLowerCase().replace(/[^a-z0-9]/g,'');
    if(!game||!brand)return null;
    const editions=[...(game.editions||[]),...(globalThis.VintedEditionHints||[]).filter(e=>String(e.bggId)===String(game.bggId))].filter(e=>norm(e.publisher)===norm(brand)&&e.languages?.length===1);
    const codes=[...new Set(editions.map(e=>e.languages[0]))];
    if(codes.length!==1)return null;
    return {code:codes[0],inferred:true,evidence:'Edizione probabile: il brand «'+brand+'» corrisponde a un’edizione documentata di questo gioco. Il brand del venditore può essere inesatto.',source:editions[0].sourceUrl,at:Date.now()};
  }
  function assess(game,title,observed,productMatch){
    const direct=detect(title,game?.name);let edition=direct;
    if(observed&&Date.now()-observed.at<30*86400000&&observed.code){
      edition=!observed.manual&&direct.code&&direct.code!==observed.code?{code:null,evidence:'Titolo e descrizione indicano lingue diverse.'}:observed;
    }
    const product=productMatch?.status==='matched'?productMatch.product:null;
    if(product?.languages?.length===1&&!observed?.manual){
      const code=product.languages[0];
      if(direct.code&&direct.code!==code)edition={code:null,evidence:'Il testo dell’annuncio e il prodotto Fase 5 indicano lingue diverse: verifica la scatola.',conflict:true};
      else edition={code,inferred:productMatch.method!=='gtin',productMatched:true,productId:product.id,productMethod:productMatch.method,productScore:productMatch.score||0,source:product.price?.sourceUrl||null,at:Date.now(),evidence:(productMatch.method==='gtin'?'EAN/GTIN valido identifica ':'Titolo/editore identificano probabilmente ')+'l’edizione «'+product.title+'»'+(product.publisher?' · '+product.publisher:'')+'.'};
    }
    if(!edition.code&&!edition.conflict){const pub=publisherEdition(game,product?.publisher||null);if(pub)edition=pub;}
    let dependence=game?.languageDependence||null;
    const evidence=game?.languageEvidence||[];
    if(!dependence&&evidence.length&&evidence.every(e=>['low','moderate','high'].includes(e.value)))dependence='dependent';
    const independent=dependence==='independent',dependent=['low','moderate','high','dependent'].includes(dependence);
    // Fase 5: per un gioco dipendente la sola lingua sicuramente compatibile è l'italiano.
    // Inglese/FR/DE/... restano informative, ma vengono marcate rosse perché richiedono di giocare in quella lingua.
    const accepted=edition.code==='it';
    return {edition,dependence,independent,dependent,blocked:!!edition.code&&!accepted&&dependent,
      uncertain:!independent&&!edition.code,
      label:edition.code?names[edition.code]:null,
      name:names[edition.code]||'Edizione incerta',evidence};
  }
  function badge(state,announcement){
    // La lingua della DESCRIZIONE non viene mai promossa a lingua dell'EDIZIONE.
    // Vinted può tradurre automaticamente il testo e questo era la principale fonte dei falsi 🇮🇹.
    if(state.independent)return {code:'independent',name:'Indipendente',level:'independent',label:'Gioco indipendente dalla lingua'};
    if(state.blocked)return {code:state.edition.code||'unknown',name:names[state.edition.code]||'Lingua non italiana',level:'blocked',label:'Gioco dipendente dalla lingua · edizione non italiana'};
    if(state.edition.code&&state.edition.productMatched){
      const exact=state.edition.productMethod==='gtin'||state.edition.productMethod==='manual'||(state.edition.productScore||0)>=96;
      return {code:state.edition.code,name:names[state.edition.code],level:exact?'edition_exact':'edition_probable',label:exact?'Lingua edizione verificata da un indizio forte':'Lingua edizione probabile'};
    }
    if(state.edition.code&&!state.edition.inferred)return {code:state.edition.code,name:names[state.edition.code],level:'edition_exact',label:'Lingua edizione dichiarata esplicitamente'};
    if(state.dependent)return {code:'unknown',name:'Lingua ?',level:'unknown',label:'Gioco dipendente dalla lingua · edizione non determinata'};
    if(state.edition.code)return {code:state.edition.code,name:names[state.edition.code],level:'edition_probable',label:'Lingua edizione probabile · dipendenza non classificata'};
    return {code:'unknown',name:'Lingua ?',level:'unknown',label:'Lingua dell’edizione non determinata'};
  }
  function announcementHint(text){
    const t=String(text||'').normalize('NFKD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
    const phrases={it:/\b(gioc[oh]i? da tavol[oi]|gioco da tavol[oi]|gioco di societa|in ottime condizioni)\b/,fr:/\b(jeu[x]? de societe|jeu de plateau|en tres bon etat)\b/,de:/\b(brettspiel|gesellschaftsspiel|sehr guter zustand)\b/,nl:/\b(bordspel|gezelschapsspel|zo goed als nieuw)\b/,en:/\b(board game|brand new sealed|excellent condition)\b/,es:/\b(juego de mesa|en buen estado)\b/};
    const matches=Object.entries(phrases).filter(([,r])=>r.test(t));return matches.length===1?matches[0][0]:null;
  }
  function translationSource(text){
    const match=String(text||'').match(/(?:tradott[oa]|traduci|traduzione)\s+(?:dal|da|dall['’])\s*(\w+)|translated?\s+from\s+(\w+)/i);
    return match?detect(match[1]||match[2]).code:null;
  }
  function textHint(text){
    const phrase=announcementHint(text);if(phrase)return phrase;
    const raw=String(text||'').normalize('NFKD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
    const terms=new Set(raw.match(/[a-z]+/g)||[]);
    const vocab={
      fr:'je le la les un une et de du des avec dans pour vendu vendre boite tres etat jamais complet livraison pieces jeu',
      it:'il lo la i gli le un una e di da del della con per che non mai vendo venduto gioco scatola condizioni completo completa spedizione usato usata nuovo nuova',
      de:'ich der die das ein eine und mit spiel zustand verkaufe verkauft vollstandig versand wurde neu gebraucht',
      nl:'een de het voor en van met spel nieuw compleet onderdelen verkoop verkocht gebruikt doos',
      en:'the a an and of with this game condition complete played selling shipping box new used never',
      es:'el la los las un una y de con para juego vendo vendido estado completo envio caja piezas nuevo usado'
    };
    const scores=Object.entries(vocab).map(([code,list])=>({code,n:list.split(' ').filter(w=>terms.has(w)).length})).sort((a,b)=>b.n-a.n);
    const min=raw.length>=70?2:raw.length>=30?2:3;
    return scores[0].n>=min&&scores[0].n-scores[1].n>=1?scores[0].code:null;
  }
  async function detectAnnouncement(text){
    let reason='Rilevatore del browser non disponibile';
    if(globalThis.chrome?.i18n?.detectLanguage)try{const result=await chrome.i18n.detectLanguage(text),best=result.languages?.[0];if(result.isReliable&&best?.percentage>=80&&names[best.language])return {code:best.language,reason:'Rilevata dal browser nel testo originale'};reason='Il browser non identifica il testo con sufficiente affidabilità';}catch(e){reason='Rilevatore: '+e.message;}
    const code=textHint(text);return {code,reason:code?'Indizio lessicale nel testo originale':reason};
  }
  globalThis.VintedLanguage={detect,assess,publisherEdition,names,badge,announcementHint,translationSource,textHint,detectAnnouncement};
})();
