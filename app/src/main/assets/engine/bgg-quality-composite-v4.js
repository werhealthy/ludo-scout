(() => {
  'use strict';
  // Quality v4: scala assoluta calibrata, non percentile puro.
  // Rank BGG domina; Geek Rating e media restano segnali secondari;
  // il numero di votanti misura la robustezza/validazione del consenso.
  const cfg={
    v:4,
    weights:{rank:0.55,geek:0.20,average:0.10,votes:0.15},
    rankAnchors:[[1,100],[10,98],[25,96],[50,94],[100,92],[250,88],[500,83],[1000,76],[2000,68],[5000,57],[10000,47],[20000,37],[30000,30]],
    geekAnchors:[[5.0,20],[5.5,30],[6.0,45],[6.5,60],[6.8,68],[7.0,73],[7.2,78],[7.5,85],[7.8,92],[8.0,96],[8.4,100]],
    averageAnchors:[[5.0,20],[5.5,30],[6.0,40],[6.5,50],[7.0,62],[7.5,75],[8.0,87],[8.5,96],[9.0,100]],
    voteAnchors:[[30,25],[100,35],[300,45],[1000,58],[3000,70],[10000,82],[30000,92],[100000,100],[200000,100]]
  };
  const catalog=globalThis.VintedLocalCatalog;
  if(!catalog?.games)return;

  function clamp(n,a=0,b=100){return Math.max(a,Math.min(b,n));}
  function interpLinear(x,a){
    x=Number(x);if(!Number.isFinite(x))return null;
    if(x<=a[0][0])return a[0][1];if(x>=a[a.length-1][0])return a[a.length-1][1];
    for(let i=0;i<a.length-1;i++){
      const [x0,y0]=a[i],[x1,y1]=a[i+1];
      if(x>=x0&&x<=x1){const t=(x-x0)/(x1-x0);return y0+t*(y1-y0);}
    }
    return null;
  }
  function interpLog(x,a){
    x=Number(x);if(!Number.isFinite(x)||x<=0)return null;
    if(x<=a[0][0])return a[0][1];if(x>=a[a.length-1][0])return a[a.length-1][1];
    const lx=Math.log10(x);
    for(let i=0;i<a.length-1;i++){
      const [x0,y0]=a[i],[x1,y1]=a[i+1];
      if(x>=x0&&x<=x1){const t=(lx-Math.log10(x0))/(Math.log10(x1)-Math.log10(x0));return y0+t*(y1-y0);}
    }
    return null;
  }
  function meta(game){
    const rank=Number(game?.bggRank),votes=Math.max(0,Number(game?.ratingCount)||0);
    const rankScore=Number.isFinite(rank)&&rank>0?interpLog(rank,cfg.rankAnchors):null;
    const geekScore=interpLinear(game?.geekRating,cfg.geekAnchors);
    const averageScore=interpLinear(game?.averageRating,cfg.averageAnchors);
    const voteScore=interpLog(Math.max(votes,30),cfg.voteAnchors);
    const pieces=[];
    if(rankScore!==null)pieces.push([rankScore,cfg.weights.rank]);
    if(geekScore!==null)pieces.push([geekScore,cfg.weights.geek]);
    if(averageScore!==null)pieces.push([averageScore,cfg.weights.average]);
    if(voteScore!==null)pieces.push([voteScore,cfg.weights.votes]);
    if(!pieces.length)return null;
    const wsum=pieces.reduce((s,p)=>s+p[1],0);
    let score=pieces.reduce((s,p)=>s+p[0]*p[1],0)/wsum;
    // Senza un rank generale BGG evitiamo voti "elite" generati solo da audience selezionate.
    if(rankScore===null)score=Math.min(score,69);
    score=Math.round(clamp(score));
    const confidence=votes>=30000?'molto alta':votes>=10000?'alta':votes>=3000?'buona':votes>=1000?'media':votes>=300?'bassa':'molto bassa';
    return {score,rankScore,geekScore,averageScore,voteScore,confidence,version:cfg.v};
  }
  let enriched=0;
  for(const game of catalog.games){const m=meta(game);if(!m)continue;game.qualityScore=m.score;game.qualityComposite=m;enriched++;}
  globalThis.VintedQualityComposite={version:cfg.v,config:cfg,stats:{enriched},score:meta};
})();
