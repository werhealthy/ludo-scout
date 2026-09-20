(() => {
  'use strict';
  const runtime=globalThis.VINTED_AFFARI_BGG_OFFICIAL_CORE;
  const catalog=globalThis.VintedLocalCatalog;
  if(!runtime?.g||!Array.isArray(runtime.fields)||!catalog?.games)return;

  const ix=Object.fromEntries(runtime.fields.map((name,i)=>[name,i]));
  const langMap=runtime.lang||{};
  const confMap=runtime.confBand||{};
  let enriched=0, officialPoll=0, hardLanguage=0, fallbackLanguage=0;

  for(const game of catalog.games){
    const row=runtime.g[String(game.bggId)];
    if(!row)continue;
    enriched++;

    const n=(field)=>row[ix[field]];
    const avg=n('avg'),bayes=n('bayes'),voters=n('voters'),rank=n('rank'),weight=n('weight');
    if(Number.isFinite(avg))game.averageRating=avg;
    if(Number.isFinite(bayes)){
      game.geekRating=bayes;
      game.qualityScore=Math.max(0,Math.min(100,Math.round(bayes*10)));
    }
    if(Number.isFinite(voters))game.ratingCount=voters;
    if(Number.isFinite(rank)&&rank>0)game.bggRank=rank;
    if(Number.isFinite(weight)&&weight>0)game.bggWeight=weight;

    const lang=langMap[String(n('lang'))]||'unknown';
    const confBand=confMap[String(n('confBand'))]||'none';
    const langConf=Number(n('langConf'))||0;
    const langVotes=Number(n('langVotes'))||0;
    const langWeighted=n('langWeighted');
    const hard=!!n('hardLang');
    const globeOk=!!n('globeOk');
    if(langVotes>0)officialPoll++;

    const legacyDependence=game.languageDependence||null;
    let effective=legacyDependence;
    let authority='legacy';
    // Fase 6.6: le poll sparse sono evidenza di supporto, non una certezza operativa.
    // Il globo richiede esplicitamente globeOk; i livelli dipendenti diventano gate solo con hardLang.
    if(lang==='independent'&&globeOk){effective='independent';authority='bgg_official';hardLanguage++;}
    else if(['low','moderate','high'].includes(lang)&&hard){effective=lang;authority='bgg_official';hardLanguage++;}
    else if(!legacyDependence&&lang!=='unknown'){effective=null;authority='bgg_supporting';}
    else if(legacyDependence)fallbackLanguage++;

    game.languageDependence=effective;
    game.bggOfficial={
      version:runtime.v||2,
      averageRating:avg,
      geekRating:bayes,
      voters,
      rank,
      weight,
      language:lang,
      confidenceBand:confBand,
      confidence:langConf,
      languageVotes:langVotes,
      weightedLevel:langWeighted,
      hardLanguage:hard,
      globeOk,
      effectiveLanguageDependence:effective,
      authority,
      legacyLanguageDependence:legacyDependence
    };
  }

  catalog.coverage={...(catalog.coverage||{}),bggOfficialCore:enriched,bggOfficialPoll:officialPoll,bggOfficialHardLanguage:hardLanguage};
  globalThis.VintedBGGOfficial={
    version:runtime.v||2,
    fields:runtime.fields,
    stats:{enriched,officialPoll,hardLanguage,fallbackLanguage},
    get(gameOrId){
      const game=typeof gameOrId==='object'?gameOrId:null;
      if(game?.bggOfficial)return game.bggOfficial;
      const id=String(gameOrId?.bggId??gameOrId??'');
      const row=runtime.g[id];if(!row)return null;
      const n=(field)=>row[ix[field]];
      return {averageRating:n('avg'),geekRating:n('bayes'),voters:n('voters'),rank:n('rank'),weight:n('weight'),language:langMap[String(n('lang'))]||'unknown',confidenceBand:confMap[String(n('confBand'))]||'none',confidence:Number(n('langConf'))||0,languageVotes:Number(n('langVotes'))||0,weightedLevel:n('langWeighted'),hardLanguage:!!n('hardLang'),globeOk:!!n('globeOk')};
    }
  };
})();
