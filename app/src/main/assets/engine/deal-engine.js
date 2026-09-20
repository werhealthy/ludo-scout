(() => {
  'use strict';
  const valid = n => Number.isSafeInteger(n) && n >= 0;
  function catalogReference(game,newShipping=0,now=Date.now()) {
    if(!game)return null;
    const offers=[],lists=[];
    for(const edition of game.editions||[]){
      // Il riferimento predefinito è italiano. Altre lingue solo senza testo.
      const languages=edition.languages||[];
      const compatible=languages.includes('it')||languages.includes('en')||game.languageDependence==='independent';
      for(const p of edition.priceObservations||[]){
        const age=now-Date.parse(p.retrievedAt||'');
        if(!compatible||p.availability!=='InStock'||p.currency!=='EUR'||p.condition!=='new'||!Number.isFinite(age)||age<0||age>90*86400000||!(p.itemPriceEUR>0))continue;
        const delivered=typeof p.deliveredPriceEUR==='number'&&p.deliveredPriceEUR>0;
        const knownShipping=typeof p.shippingToItalyEUR==='number'&&p.shippingToItalyEUR>=0;
        const cents=Math.round(delivered?p.deliveredPriceEUR*100:p.itemPriceEUR*100+(knownShipping?p.shippingToItalyEUR*100:newShipping));
        offers.push({name:game.name,cents,kind:'retail',automatic:true,itemCents:Math.round(p.itemPriceEUR*100),source:p.sourceUrl||edition.sourceUrl,at:p.retrievedAt,editionId:edition.id,languages,shippingEstimated:!delivered&&!knownShipping,shippingCents:delivered?null:knownShipping?Math.round(p.shippingToItalyEUR*100):newShipping});
      }
      // Il listino rimane un riferimento indicativo, mai una prova di affare.
      if(edition.msrpEUR>0)lists.push({name:game.name,cents:Math.round(edition.msrpEUR*100),kind:'msrp',automatic:true,source:edition.sourceUrl,at:edition.retrievedAt,editionId:edition.id,languages});
    }
    return offers.sort((a,b)=>a.cents-b.cents)[0]||lists.sort((a,b)=>a.cents-b.cents)[0]||null;
  }
  function protectionFeeFor(data,priceCents) {
    if(!valid(priceCents))return null;
    const currentFee=valid(data?.priceCents)&&valid(data?.protectedCents)&&data.protectedCents>=data.priceCents?data.protectedCents-data.priceCents:null;
    // Quando il costo letto dalla pagina coincide con la tariffa standard osservata su Vinted Italia,
    // ricalcoliamo la protezione sul prezzo offerto. Se la pagina usa una tariffa diversa,
    // manteniamo prudenzialmente l'importo letto invece di inventare una formula.
    const standardFor=price=>70+Math.round(price*.05);
    if(currentFee!==null&&Math.abs(currentFee-standardFor(data.priceCents))<=2)return standardFor(priceCents);
    return currentFee!==null?currentFee:standardFor(priceCents);
  }
  function totalForPrice(data,priceCents,shipping) {
    const fee=protectionFeeFor(data,priceCents);
    if(fee===null||!shipping||!valid(shipping.cents))return null;
    return {priceCents,fee,total:priceCents+fee+shipping.cents,shippingCents:shipping.cents};
  }
  function referenceThresholds(reference,percent=40){
    if(reference?.kind==='used_market'||reference?.kind==='used_market_low'){
      const median=valid(reference.marketMedianCents)?reference.marketMedianCents:reference.cents;
      const q25=valid(reference.marketQ25Cents)?reference.marketQ25Cents:Math.round(median*.85);
      const good=Math.max(1,q25);
      // Price Model v2: Offertona solo con benchmark premium. I campioni standard possono
      // generare Buon prezzo / Da negoziare / Prezzo alto, ma non il badge viola.
      const hot=reference.allowHot===false?0:Math.max(1,Math.round(good*.80));
      return {benchmark:median,hot,good,poor:median};
    }
    const benchmark=reference?.cents;
    const hot=reference?.allowHot===false?0:Math.floor(benchmark*percent/100);
    return {benchmark,hot,good:Math.floor(benchmark*.5),poor:benchmark};
  }
  function calculate(data,reference,shipping,percent=40) {
    if (!reference || !valid(reference.cents) || reference.cents === 0) return {missing:'reference'};
    if (!valid(data.priceCents) || !valid(data.protectedCents) || data.protectedCents < data.priceCents) return {missing:'protection'};
    if (!shipping || !valid(shipping.cents)) return {missing:'shipping'};
    const fee=data.protectedCents-data.priceCents;
    const total=data.protectedCents+shipping.cents;
    const t=referenceThresholds(reference,percent),threshold=t.hot,savings=t.benchmark-total;
    const budget=threshold-shipping.cents-fee;
    const maxOffer=budget > 0 ? Math.min(data.priceCents,budget) : null;
    const ratio=t.benchmark>0?total/t.benchmark:null;
    return {total,fee,threshold,goodThreshold:t.good,benchmark:t.benchmark,savings,discount:t.benchmark>0?Math.round(savings/t.benchmark*100):null,maxOffer,
      dealScore:reference.kind==='msrp'||ratio===null?null:Math.max(0,Math.min(100,Math.round((1-ratio)/.6*100))),
      tier:total<=t.hot?'hot':total<=t.good?'good':total<t.poor?'normal':'poor',
      estimated:shipping.estimated, shipping};
  }
  function shippingFor(item,observed,standard=450) {
    if(valid(item?.shipping))return {cents:item.shipping,estimated:true,source:'Inserita da te'};
    // La cache di spedizione vale 24 ore: promozioni e destinazioni possono cambiare.
    if(observed && Date.now()-observed.at<86400000 && valid(observed.cents)) {
      if(observed.minimum)return {cents:Math.max(standard,observed.cents),estimated:true,source:`Stima; Vinted mostra ${observed.raw}`};
      return {cents:observed.cents,estimated:true,source:`Letta nell’annuncio: ${observed.raw}`};
    }
    return {cents:standard,estimated:true,source:'Stima standard modificabile'};
  }
  function negotiate(data,reference,shipping,percent=40){
    const r=calculate(data,reference,shipping,percent);
    if(r.missing)return {r,tier:null,offer:null};
    const goodThreshold=r.goodThreshold,minimumOffer=Math.ceil(data.priceCents*.6);
    const step=data.priceCents<1000?10:50;
    const floorStep=value=>Math.floor(value/step)*step;
    const clampOffer=value=>Math.max(minimumOffer,Math.min(data.priceCents-step,floorStep(value)));
    const totalAt=price=>totalForPrice(data,price,shipping)?.total??Infinity;
    const offerForTarget=targetTotal=>{
      if(!(targetTotal>0))return null;
      for(let offer=floorStep(data.priceCents-step);offer>=minimumOffer;offer-=step){if(totalAt(offer)<=targetTotal)return offer;}
      return null;
    };
    let offer=null,target='discount';
    if(r.tier==='hot'){
      // Anche su un'Offertona proponiamo un ribasso prudente: l'utente vuole sempre una base da cui trattare.
      const reduction=Math.max(step,Math.round(data.priceCents*.10/step)*step);
      offer=clampOffer(data.priceCents-reduction);target='discount';
    }else if(r.tier==='good'){
      // Se possibile proviamo a trasformare un buon prezzo in Offertona; altrimenti ~10% sotto richiesta.
      offer=r.threshold>0?offerForTarget(r.threshold):null;
      if(offer!==null)target='hot';
      else {const reduction=Math.max(step,Math.round(data.priceCents*.10/step)*step);offer=clampOffer(data.priceCents-reduction);target='discount';}
    }else if(r.tier==='normal'){
      // La priorità è entrare almeno nella fascia Buon prezzo.
      offer=offerForTarget(goodThreshold);
      if(offer!==null)target='good';
      else {offer=minimumOffer;target='aggressive';}
    }else{
      // Prezzo alto: suggeriamo comunque la massima trattativa compatibile con il limite usato dall'app.
      offer=offerForTarget(goodThreshold);
      if(offer!==null)target='good';
      else {offer=minimumOffer;target='aggressive';}
    }
    if(!(offer>=minimumOffer&&offer<data.priceCents))offer=Math.max(minimumOffer,data.priceCents-step);
    if(!(offer<data.priceCents))offer=null;
    const after=offer!==null?totalForPrice(data,offer,shipping):null;
    const afterTier=after?calculate({...data,priceCents:offer,protectedCents:offer+after.fee},reference,shipping,percent).tier:null;
    return {r,tier:r.tier,offer,goodThreshold,minimumOffer,target,afterOffer:after?.total??null,afterTier};
  }
  function bundle(items,shippingCents,feesCents=null,discount=0,percent=40){
    if(items.length<2||!valid(shippingCents)||!Number.isFinite(discount)||discount<0||discount>100)return null;
    if(items.some(i=>!valid(i.data.priceCents)||!valid(i.data.protectedCents)||i.data.protectedCents<i.data.priceCents||!i.ref||!valid(i.ref.cents)))return null;
    const sum=items.reduce((n,i)=>n+i.data.priceCents,0),priceCents=Math.round(sum*(1-discount/100));
    const fee=feesCents==null?items.reduce((n,i)=>n+i.data.protectedCents-i.data.priceCents,0):feesCents;if(!valid(fee))return null;
    const allUsed=items.every(i=>i.ref.kind==='used_market'||i.ref.kind==='used_market_low');
    const reference=allUsed?{
      cents:items.reduce((n,i)=>n+(i.ref.marketMedianCents||i.ref.cents),0),
      kind:'used_market',
      marketMedianCents:items.reduce((n,i)=>n+(i.ref.marketMedianCents||i.ref.cents),0),
      marketQ25Cents:items.reduce((n,i)=>n+(i.ref.marketQ25Cents||i.ref.cents),0),
      allowHot:items.every(i=>i.ref.allowHot!==false)
    }:{cents:items.reduce((n,i)=>n+i.ref.cents,0),allowHot:items.every(i=>i.ref.allowHot!==false)};
    return {...negotiate({priceCents,protectedCents:priceCents+fee},reference,{cents:shippingCents},percent),priceCents,fees:fee,reference,shippingCents,estimatedFees:feesCents==null};
  }
  globalThis.VintedDeals={calculate,shippingFor,catalogReference,negotiate,bundle,protectionFeeFor,totalForPrice,referenceThresholds};
})();
