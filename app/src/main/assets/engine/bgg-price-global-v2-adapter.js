(() => {
  'use strict';
  const runtime=globalThis.VINTED_AFFARI_PRICE_MODEL_V2;
  const catalog=globalThis.VintedLocalCatalog;
  if(!runtime?.g||!catalog?.games)return;
  const ix=Object.fromEntries(runtime.fields.map((name,i)=>[name,i]));
  let enriched=0,usedStandard=0,usedPremium=0,newStrong=0;
  const conf=runtime.confidence||{};
  for(const game of catalog.games){
    const row=runtime.g[String(game.bggId)];
    if(!row)continue;
    const n=f=>row[ix[f]];
    const usedMode=Number(n('usedMode'))||0;
    const model={
      version:runtime.v||2,
      source:runtime.source,
      usedMedianEUR:Number(n('usedMedian'))||null,
      usedQ25EUR:Number(n('usedQ25'))||null,
      usedQ75EUR:Number(n('usedQ75'))||null,
      usedN:Number(n('usedN'))||0,
      usedScore:Number(n('usedScore'))||0,
      usedConfidence:conf[String(n('usedConfidence'))]||'none',
      usedDispersion:Number.isFinite(Number(n('usedDispersion')))?Number(n('usedDispersion')):null,
      usedMode,
      usedModeLabel:usedMode===2?'premium':usedMode===1?'standard':'none',
      allowHotUsed:usedMode===2,
      newMedianEUR:Number(n('newMedian'))||null,
      newQ25EUR:Number(n('newQ25'))||null,
      newQ75EUR:Number(n('newQ75'))||null,
      newN:Number(n('newN'))||0,
      newStrong:Boolean(n('newStrong')),
      allowHotNew:Boolean(n('newStrong'))&&Number(n('newN'))>=5,
      conflict:Boolean(n('conflict')),
      usedLatest:n('usedLatest')||null
    };
    game.bggPriceGlobal=model;
    enriched++;if(usedMode>=1)usedStandard++;if(usedMode===2)usedPremium++;if(model.newStrong)newStrong++;
  }
  globalThis.VintedBGGPriceGlobal={
    version:runtime.v||2,
    policy:runtime.policy||{},
    runtimeStats:runtime.stats||{},
    stats:{enriched,usedStandard,usedPremium,newStrong},
    get(gameOrId){
      const game=typeof gameOrId==='object'?gameOrId:catalog.games.find(g=>String(g.bggId)===String(gameOrId));
      return game?.bggPriceGlobal||null;
    }
  };
})();
