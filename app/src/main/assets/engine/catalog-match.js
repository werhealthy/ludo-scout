(() => {
  'use strict';
  const catalog=globalThis.VintedLocalCatalog;
  const officialNames=globalThis.VINTED_AFFARI_BGG_NAMES||{};
  const normalize=s=>String(s||'').normalize('NFKD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/\b([a-z0-9]+)[’']s\b/g,'$1').replace(/[^a-z0-9]+/g,' ').trim();
  const tokens=s=>normalize(s).split(' ').filter(Boolean);

  // Parole molto frequenti negli annunci: non devono pesare quanto il titolo del gioco.
  const fillers=new Set(('gioco giochi giocattolo giocattoli da tavolo tavoli scatola scatole boardgame societa di del della delle dei degli in il lo la le i gli un una uno the of to and a con per vendo vendita vendesi usato usata nuovo nuova nuovi nuove perfetto perfetta perfette ottime ottima ottimo buono buona condizioni completo completa completi sigillato sigillata italiano italiana ita inglese english eng tedesco tedesca deutsch german francese french fr edition edizione versione asmodee mai giocato aperto solo').split(' '));
  for(const word of 'brettspiel gesellschaftsspiel spiel spielzeug deutsch deutsche deutsches deutscher deutschsprachig ausgabe version zustand sehr gut gebraucht neu neuwertig vollstandig ovp verkaufe verpackung jeu jeux de societe plateau en bon etat neuf scelle complet edition francaise francais juego mesa buen estado nuevo completo deutsche englisch englische italiano italiana italian lingua language sprache scatole ottima buono buona ottimo'.split(' '))fillers.add(word);
  for(const word of 'guter guten gutes gutem gute top excellent excellenti excellentissime versiegelt ungeoffnet benutzt unbenutzt beschadigt neuve parfait parfaite tres verkauft wird verkaufe'.split(' '))fillers.add(word);
  for(const word of 'carte carta cartes cartas card cards game games kickstarter ks pledge special speciale'.split(' '))fillers.add(word);
  for(const word of 'bordspel gezelschapsspel gezelschapsspelletje bordspellen spel nieuw nieuwe zo goed als staat compleet ongebruikt ongeopend nederlands nederlandse nederlandstalig taal come pari praticamente ancora confezione confezionato danish sealed portugues portuguesa portuguese pt'.split(' '))fillers.add(word);

  const editionModifiers=new Set('mini pocket travel compact portable deluxe collector collectors anniversary anniversario special speciale classic classics classici family famiglia edition edizione version versione reprint ristampa nuova nuovo kickstarter ks pledge'.split(' '));
  const accessoryRx=/\b(accessoires|accessori|accessorio|organiseur|organizzatore|organizer|organiser|jetons|tokens?|voedseltokens|dobbelhuisje|voedselhuisje|voedselbakjes|inlay|inserto|insert|componenti|ricambio|ricambi|vuot[aeio]|empty|leer|lege|sleeves|bustine|fanmade|playmat|pedine|segnalini|zubehor|ersatzteile|hulle|nintendo|casco|casque)\b/;
  // Guardrail per annunci che contengono il nome del gioco ma vendono solo materiale accessorio.
  // Sono frasi specifiche: evitiamo di escludere giochi reali che hanno parole generiche come “pack” o “pieces” nel titolo.
  const accessoryPhraseRx=/\b(errata\s*(?:pack|kit)?|correction\s*(?:pack|kit)|backup\s+(?:pieces?|parts?|components?|tokens?|cards?)|replacement\s+(?:pieces?|parts?|components?|tokens?|cards?)|spare\s+(?:pieces?|parts?|components?|tokens?|cards?)|pezzi\s+di\s+ricambio|componenti\s+di\s+ricambio|solo\s+(?:scatola|box|manuale|regolamento)|box\s+only|rulebook\s+only|manual\s+only)\b/;
  const bundleRx=/\b(bundle|lotto|lotti|lot|paket)\b/;
  function classifyListing(title){
    const norm=normalize(title);
    if(!norm)return {kind:'unknown',reason:'Titolo vuoto'};
    if(accessoryRx.test(norm)||accessoryPhraseRx.test(norm))return {kind:'accessory',reason:'Accessorio/componente: non confronto il suo prezzo con il gioco completo.'};
    if(bundleRx.test(norm))return {kind:'bundle',reason:'Lotto/bundle: richiede valutazione separata dei singoli giochi.'};
    return {kind:'game_candidate',reason:null};
  }

  const entries=[];
  const searchEntries=[];
  const byWord=new Map();
  const byRaw=new Map();
  const byCore=new Map();
  const tokenGames=new Map();
  const shortNames={'201808':['Clank','Clank!']};

  function meaningful(words){return words.filter(w=>w.length>1&&!fillers.has(w));}
  // Rimuove solo prefissi marketplace descrittivi, senza cancellare parole che possono
  // appartenere davvero al titolo. Esempio: "Gioco da tavolo Micrositi" -> "Micrositi".
  // Un titolo reale come "A la carte" resta intatto perché non inizia con un prefisso generico.
  function stripGenericLead(title){
    let raw=String(title||'').trim();
    const rx=/^(?:(?:gioco(?:\s+da\s+tavolo|\s+di\s+carte|\s+di\s+societa)?|giochi\s+da\s+tavolo|board\s*game|card\s+game|jeu\s+de\s+soci[eé]t[eé]|jeu\s+de\s+cartes|brettspiel|gesellschaftsspiel|juego\s+de\s+mesa)\s*(?:[:\-–—|]\s*)?)+/i;
    for(let i=0;i<3;i++){const next=raw.replace(rx,'').trim();if(next===raw)break;raw=next;}
    return raw||String(title||'').trim();
  }
  function publisherTokens(game){
    const out=new Set();
    for(const e of game.editions||[])for(const w of meaningful(tokens(e.publisher||'')))if(w.length>=3)out.add(w);
    return [...out];
  }
  function makeEntry(game,alias,kind='catalog'){
    const decomposed=String(alias||'').normalize('NFKD').replace(/[\u0300-\u036f]/g,'');
    // Il normalizzatore ASCII perderebbe alfabeti non latini lasciando frammenti ingannevoli
    // (es. un alias greco che finisce solo in “Carcassonne”). Li lasciamo alla Fase 3 online.
    if(kind==='bgg_official_alias'&&[...decomposed].some(ch=>/\p{L}/u.test(ch)&&ch.charCodeAt(0)>127))return null;
    const norm=normalize(alias),words=tokens(alias);if(norm.length<2||!words.length)return null;
    const core=meaningful(words);
    return {game,alias,norm,words,core,coreKey:core.join(' '),kind,publishers:publisherTokens(game)};
  }
  function pushMap(map,key,value){if(!key)return;if(!map.has(key))map.set(key,[]);map.get(key).push(value);}
  function addEntry(game,alias,kind){
    const e=makeEntry(game,alias,kind);if(!e)return;entries.push(e);searchEntries.push(e);pushMap(byRaw,e.norm,e);pushMap(byCore,e.coreKey,e);for(const w of new Set(e.core)){pushMap(byWord,w,e);if(!tokenGames.has(w))tokenGames.set(w,new Set());tokenGames.get(w).add(game.id);}
  }
  for(const game of catalog.games){
    const seen=new Set(),add=(alias,kind)=>{const k=normalize(alias);if(!k||seen.has(k))return;seen.add(k);addEntry(game,alias,kind);};
    add(game.name,'primary');
    for(const alias of shortNames[game.bggId]||[])add(alias,'short');
    for(const alias of game.aliases||[])add(alias,'catalog_alias');
    for(const alias of officialNames[String(game.bggId)]||[])add(alias,'bgg_official_alias');
    for(const alias of (game.editions||[]).filter(e=>e.identityLevel==='ean'||e.identityLevel==='bgg_id').map(e=>e.title).filter(Boolean))add(alias,'edition');
    for(const alias of new Set(game.searchAliases||[])){const e=makeEntry(game,alias,'manual_search');if(e)searchEntries.push(e);}
  }

  const uniqueGameEntries=list=>{
    const m=new Map();for(const e of list||[])if(!m.has(e.game.id))m.set(e.game.id,e);return [...m.values()];
  };
  const boundaryContains=(hay,needle)=>(' '+hay+' ').includes(' '+needle+' ');
  const gameCountForToken=w=>tokenGames.get(w)?.size||0;
  const idf=w=>Math.min(5,1+Math.log((catalog.games.length+1)/(gameCountForToken(w)+1)));
  function editDistance(a,b,max=2){
    if(Math.abs(a.length-b.length)>max)return max+1;
    let prev=Array.from({length:b.length+1},(_,i)=>i);
    for(let i=1;i<=a.length;i++){
      const cur=[i],start=Math.max(1,i-max-1),end=Math.min(b.length,i+max+1);let rowMin=cur[0];
      for(let j=1;j<=b.length;j++){const v=Math.min(cur[j-1]+1,prev[j]+1,prev[j-1]+(a[i-1]===b[j-1]?0:1));cur[j]=v;rowMin=Math.min(rowMin,v);}if(rowMin>max)return max+1;prev=cur;
    }return prev[b.length];
  }
  function contextBoost(e,context){
    let boost=0,why=[];
    const brand=meaningful(tokens(context?.brand||''));
    if(brand.length&&e.publishers.length){const hit=brand.some(w=>e.publishers.includes(w));if(hit){boost+=8;why.push('editore compatibile');}}
    const year=Number(context?.year||0);if(year&&e.game.year){if(year===Number(e.game.year)){boost+=5;why.push('anno compatibile');}else if(Math.abs(year-Number(e.game.year))>1)boost-=5;}
    return {boost,why};
  }
  const sourceBonus=kind=>kind==='primary'?6:kind==='catalog_alias'?3:kind==='edition'?2:kind==='short'?2:kind==='bgg_official_alias'?1:0;
  function resultFor(e,score,reason,extra={}){score+=sourceBonus(e.kind);return {game:e.game,alias:e.alias,score,full:score>=97,reason,source:e.kind,...extra};}
  function match(title,context={}){
    const cleanedTitle=stripGenericLead(title);
    const qNorm=normalize(cleanedTitle),qWords=tokens(cleanedTitle),qCore=meaningful(qWords),qCoreKey=qCore.join(' ');
    if(!qWords.length)return {status:'none',reason:'Titolo vuoto',candidates:[]};
    const listing=classifyListing(title);
    if(listing.kind==='accessory'||listing.kind==='bundle')return {status:'excluded',reason:listing.reason,candidates:[],listingKind:listing.kind};

    // 1) Nome/alias completo esatto. È il segnale più forte. Se il titolo Vinted
    // iniziava con "gioco da tavolo", "board game", ecc. proviamo l'esatto sul titolo pulito.
    let exact=uniqueGameEntries(byRaw.get(qNorm));
    if(exact.length===1){const e=exact[0],ctx=contextBoost(e,context),cleaned=cleanedTitle!==String(title||'').trim();return {status:'matched',game:e.game,alias:e.alias,reason:(cleaned?'Titolo esatto dopo aver rimosso il prefisso generico dell’annuncio':'Titolo/alias completo esatto')+(ctx.why.length?' · '+ctx.why.join(', '):'.'),candidates:[resultFor(e,100+ctx.boost,cleaned?'Titolo esatto dopo prefisso generico':'Titolo/alias completo esatto')]};}
    if(exact.length>1){const cs=exact.map(e=>resultFor(e,96+contextBoost(e,context).boost,'Alias esatto condiviso')).sort((a,b)=>b.score-a.score);if(cs[0].score-cs[1].score>=5)return {status:'matched',game:cs[0].game,alias:cs[0].alias,reason:'Alias esatto risolto dando priorità al nome primario e al contesto.',candidates:cs.slice(0,5)};return {status:'ambiguous',reason:'Lo stesso alias appartiene a più giochi.',candidates:cs.slice(0,5)};}

    // Se, tolte le parole marketplace, non resta nessun termine distintivo, non inventare un gioco.
    // Esempio reale: 'Gioco carte' non deve diventare 'A la carte'. Un titolo esatto come
    // 'A la carte' continua comunque a essere risolto dallo step 1 qui sopra.
    if(!qCore.length)return {status:'none',reason:'Titolo troppo generico: non resta alcun termine distintivo dopo aver ignorato parole marketplace.',candidates:[]};

    // 2) Dopo aver tolto rumore da marketplace: "gioco di carte Bandido" => "Bandido".
    if(qCoreKey){
      exact=uniqueGameEntries(byCore.get(qCoreKey));
      if(exact.length===1){const e=exact[0],ctx=contextBoost(e,context);return {status:'matched',game:e.game,alias:e.alias,reason:'Titolo esatto dopo aver ignorato parole generiche dell’annuncio'+(ctx.why.length?' · '+ctx.why.join(', '):'.'),candidates:[resultFor(e,99+ctx.boost,'Match esatto senza parole generiche')]};}
    }

    const relevant=new Set();
    for(const w of qCore)for(const e of byWord.get(w)||[])relevant.add(e);
    // Recupero refusi semplici solo quando il token è abbastanza distintivo.
    for(const qw of qCore){
      if(qw.length<5||byWord.has(qw))continue;
      for(const [w,list] of byWord){if(w.length<5||w[0]!==qw[0]||Math.abs(w.length-qw.length)>2)continue;const max=qw.length>=9?2:1;if(editDistance(qw,w,max)<=max)for(const e of list)relevant.add(e);}
    }

    const bestByGame=new Map();
    for(const e of relevant){
      const c=[...new Set(e.core.length?e.core:e.words)],q=[...new Set(qCore)];
      if(!c.length)continue;
      let matchedWeight=0,candidateWeight=0,queryWeight=0,matchedExact=0,fuzzyHits=0;
      for(const w of c){const wt=idf(w);candidateWeight+=wt;if(q.includes(w)){matchedWeight+=wt;matchedExact++;continue;}const qf=q.find(qw=>qw.length>=5&&w.length>=5&&qw[0]===w[0]&&editDistance(qw,w,qw.length>=9?2:1)<= (qw.length>=9?2:1));if(qf){matchedWeight+=wt*.72;fuzzyHits++;}}
      for(const w of q)queryWeight+=idf(w);
      const coverage=candidateWeight?matchedWeight/candidateWeight:0,precision=queryWeight?matchedWeight/queryWeight:0;
      const extraQuery=qCore.filter(w=>!c.includes(w));
      const extraCandidate=c.filter(w=>!qCore.includes(w));
      const contained=boundaryContains(qNorm,e.norm);
      const queryContained=boundaryContains(e.norm,qNorm);
      const extrasEdition=extraQuery.length>0&&extraQuery.every(w=>editionModifiers.has(w));
      const first=qCore[0],uniquePrefix=qCore.length===1&&first?.length>=5&&c[0]===first&&gameCountForToken(first)===1;
      let score=coverage*58+precision*32;
      if(contained)score+=18+Math.min(12,e.words.length*2);
      if(queryContained&&qCore.length>=2)score+=4;
      if(extrasEdition&&contained)score+=15;
      if(uniquePrefix)score=Math.max(score,94);
      if(fuzzyHits)score-=4*fuzzyHits;
      if(extraCandidate.length>=3&&coverage<.7)score-=5;
      // Un titolo con più termini distintivi deve rispettare anche il termine più informativo.
      // Evita casi tipo “Captain Wager” -> “Captain Future”: condividere solo “Captain” non basta.
      const anchor=q.filter(w=>w.length>=4).sort((a,b)=>idf(b)-idf(a))[0]||null;
      const anchorHit=anchor&&c.some(w=>w===anchor||(w.length>=5&&anchor.length>=5&&w[0]===anchor[0]&&editDistance(anchor,w,anchor.length>=9?2:1)<= (anchor.length>=9?2:1)));
      const exactDistinct=q.filter(w=>c.includes(w)).length;
      if(q.length>=2&&anchor&&!anchorHit)score=Math.min(score,36);
      if(q.length>=2&&exactDistinct<2&&!contained&&!queryContained)score=Math.min(score,42);
      const ctx=contextBoost(e,context);score+=ctx.boost;
      let reason='Somiglianza pesata tra parole distintive';
      if(extrasEdition&&contained)reason='Titolo base esatto con indicazione di variante/edizione';
      else if(uniquePrefix)reason='Titolo abbreviato ma univoco nel catalogo';
      else if(fuzzyHits)reason='Titolo compatibile con piccolo refuso';
      else if(contained)reason='Nome del gioco contenuto integralmente nel titolo dell’annuncio';
      if(ctx.why.length)reason+=' · '+ctx.why.join(', ');
      // A complete name contained in a longer title is only a candidate when
      // distinctive seller words remain unexplained. A publisher boost or a wide
      // ranking gap cannot turn that partial identity into independent proof.
      const autoMatchEligible=!(contained&&extraQuery.length>0&&!extrasEdition);
      const row=resultFor(e,Math.round(score*10)/10,reason,{coverage,precision,autoMatchEligible});
      const prev=bestByGame.get(e.game.id);if(!prev||prev.score<row.score)bestByGame.set(e.game.id,row);
    }
    const candidates=[...bestByGame.values()].sort((a,b)=>b.score-a.score);
    const top=candidates[0],second=candidates[1];
    if(top){
      const gap=top.score-(second?.score??0);
      const decisive=(top.score>=97&&gap>=5) || (top.score>=92&&gap>=10) || (top.score>=88&&gap>=18);
      if(decisive&&top.autoMatchEligible)return {status:'matched',game:top.game,alias:top.alias,reason:top.reason,candidates:candidates.slice(0,5)};
      if(top.score>=55)return {status:'ambiguous',reason:gap<8?'Più candidati hanno segnali simili.':'Candidato plausibile ma non abbastanza sicuro per l’auto-match.',candidates:candidates.slice(0,5)};
    }
    return {status:'none',reason:'Nessun titolo/alias locale abbastanza vicino.',candidates:candidates.slice(0,5)};
  }

  function search(query,limit=50){
    const q=normalize(query);if(!q)return [];
    const seen=new Map();
    for(const e of searchEntries){const name=e.norm;if(name.includes(q)||q.includes(name)){const score=name===q?0:name.startsWith(q)?1:name.includes(q)?2:3;if(!seen.has(e.game.id)||seen.get(e.game.id).score>score)seen.set(e.game.id,{game:e.game,score});}}
    return [...seen.values()].sort((a,b)=>a.score-b.score||a.game.name.localeCompare(b.game.name)).slice(0,limit).map(e=>e.game);
  }
  globalThis.VintedCatalogMatcher={match,normalize,search,classifyListing,stripGenericLead};
})();
