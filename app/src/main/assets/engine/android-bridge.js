(() => {
  'use strict';

  const matcher = globalThis.VintedCatalogMatcher;
  const PM = globalThis.VintedProductMatcher;
  const D = globalThis.VintedDeals;
  const L = globalThis.VintedLanguage;
  const Q = globalThis.VintedQualityComposite;

  function marketUsedRef(game) {
    const p = game?.bggPriceGlobal;
    if (!p || p.conflict || p.usedMode < 1 || !(p.usedMedianEUR > 0) || !(p.usedQ25EUR > 0) || p.usedN < 3) return null;
    return {
      name: game.name,
      // "Typical used price" is the observed median. Q25 remains a separate lower
      // market band for exceptional-deal detection; it must never masquerade as the median.
      cents: Math.round(p.usedMedianEUR * 100),
      kind: 'used_market',
      automatic: true,
      marketMedianCents: Math.round(p.usedMedianEUR * 100),
      marketObservedMedianCents: Math.round(p.usedMedianEUR * 100),
      marketQ25Cents: Math.round(p.usedQ25EUR * 100),
      marketQ75Cents: Math.round((p.usedQ75EUR || p.usedMedianEUR) * 100),
      marketN: p.usedN,
      confidence: p.usedModeLabel,
      score: p.usedScore || 0,
      dispersion: p.usedDispersion,
      latest: p.usedLatest || null,
      marketNewCents: p.newMedianEUR ? Math.round(p.newMedianEUR * 100) : null,
      marketNewN: p.newN || 0,
      allowHot: p.allowHotUsed,
      sourceLabel: 'BGG GeekMarket · mercato usato globale normalizzato'
    };
  }

  function marketNewRef(game) {
    const p = game?.bggPriceGlobal;
    if (!p || p.conflict || !p.newStrong || !(p.newMedianEUR > 0) || p.newN < 3) return null;
    return {
      name: game.name,
      cents: Math.round(p.newMedianEUR * 100),
      kind: 'market_new',
      automatic: true,
      marketNewCents: Math.round(p.newMedianEUR * 100),
      marketNewQ25Cents: p.newQ25EUR ? Math.round(p.newQ25EUR * 100) : null,
      marketNewQ75Cents: p.newQ75EUR ? Math.round(p.newQ75EUR * 100) : null,
      marketNewN: p.newN,
      confidence: p.allowHotNew ? 'premium' : 'standard',
      allowHot: p.allowHotNew,
      sourceLabel: 'BGG GeekMarket · annunci NEW globali normalizzati'
    };
  }

  function currentRef(game, productMatch, languageState) {
    // Deal percentages are used-vs-used only. Retail/MSRP and marketplace NEW data may still
    // enrich product details, but they are never allowed to create a Vinted "discount".
    return marketUsedRef(game);
  }

  function quality(game) {
    const m = game?.qualityComposite || Q?.score?.(game) || null;
    return Number.isFinite(m?.score) ? m.score : (Number.isFinite(game?.qualityScore) ? Math.round(game.qualityScore) : null);
  }

  function tierLabel(tier) {
    if (tier === 'hot') return 'Offertona';
    if (tier === 'good') return 'Buon prezzo';
    if (tier === 'normal') return 'Prezzo giusto';
    if (tier === 'poor') return null;
    return null;
  }

  function languageCode(state) {
    if (!state) return '?';
    if (state.independent) return 'IND';
    if (state.edition?.code) return String(state.edition.code).toUpperCase();
    if (state.dependent) return '?DEP';
    return '?';
  }

  function compactCandidate(c) {
    return {
      name: c?.game?.name || null,
      alias: c?.alias || null,
      bggId: c?.game?.bggId || null,
      score: Number.isFinite(c?.score) ? c.score : null,
      reason: c?.reason || null
    };
  }

  function analyze(input) {
    try {
      const title = String(input?.title || '').trim();
      const brand = String(input?.brand || '').trim();
      const match = matcher?.match(title, {brand});

      if (!match || match.status !== 'matched' || !match.game) {
        const lead = match?.candidates?.[0] || null;
        return {
          status: match?.status || 'none',
          title,
          reason: match?.reason || 'Nessuna corrispondenza locale.',
          matchScore: Number.isFinite(lead?.score) ? lead.score : null,
          candidate: lead ? {
            name: lead?.alias || lead?.game?.name || null,
            bggId: lead?.game?.bggId || null
          } : null,
          candidates: (match?.candidates || []).slice(0, 3).map(compactCandidate)
        };
      }

      const game = match.game;
      const product = PM?.match({game, title, brand, text: title});
      const language = L?.assess(game, title, null, product);
      const reference = currentRef(game, product, language);

      const priceCents = Math.round(Number(input.itemPrice || 0) * 100);
      let protectedCents = Number(input.protectedPrice) > 0 ? Math.round(Number(input.protectedPrice) * 100) : null;
      if (!Number.isSafeInteger(protectedCents) || protectedCents < priceCents) {
        const fee = D?.protectionFeeFor({priceCents}, priceCents);
        protectedCents = Number.isSafeInteger(fee) ? priceCents + fee : priceCents;
      }

      const shippingCents = Number.isFinite(Number(input?.shippingCents)) ? Math.max(0, Math.round(Number(input.shippingCents))) : 450;
      const shipping = {cents: shippingCents, estimated: true, source: 'Stima standard Android'};
      const negotiation = reference && D ? D.negotiate({priceCents, protectedCents}, reference, shipping, 40) : null;
      const result = negotiation?.r || null;

      return {
        status: 'matched',
        title,
        matchReason: match.reason || null,
        matchScore: Number.isFinite(match?.candidates?.[0]?.score) ? match.candidates[0].score : null,
        game: {
          name: game.name || null,
          matchedAlias: match.alias || null,
          displayName: match.alias || game.name || null,
          bggId: game.bggId || null,
          averageRating: Number.isFinite(Number(game.averageRating)) ? Number(game.averageRating) : null,
          geekRating: Number.isFinite(Number(game.geekRating)) ? Number(game.geekRating) : null,
          rank: Number.isFinite(Number(game.bggRank)) ? Number(game.bggRank) : null,
          voters: Number.isFinite(Number(game.ratingCount)) ? Number(game.ratingCount) : null,
          qualityScore: quality(game)
        },
        product: product?.status === 'matched' ? {
          status: 'matched',
          title: product.product?.title || null,
          publisher: product.product?.publisher || null,
          score: Number.isFinite(product.score) ? product.score : null,
          method: product.method || null
        } : {status: product?.status || 'none'},
        language: {
          code: languageCode(language),
          independent: !!language?.independent,
          dependent: !!language?.dependent,
          blocked: !!language?.blocked,
          editionCode: language?.edition?.code || null,
          dependence: language?.dependence || null
        },
        reference: reference ? {
          kind: reference.kind || null,
          cents: Number.isFinite(reference.cents) ? reference.cents : null,
          marketMedianCents: Number.isFinite(reference.marketMedianCents) ? reference.marketMedianCents : null,
          marketQ25Cents: Number.isFinite(reference.marketQ25Cents) ? reference.marketQ25Cents : null,
          marketN: Number.isFinite(reference.marketN) ? reference.marketN : null,
          confidence: reference.confidence || null,
          allowHot: reference.allowHot !== false,
          sourceLabel: reference.sourceLabel || reference.source || null
        } : null,
        deal: negotiation ? {
          tier: negotiation.tier || null,
          label: tierLabel(negotiation.tier),
          totalCents: Number.isFinite(result?.total) ? result.total : null,
          feeCents: Number.isFinite(result?.fee) ? result.fee : null,
          shippingCents: shipping.cents,
          benchmarkCents: Number.isFinite(result?.benchmark) ? result.benchmark : null,
          savingsCents: Number.isFinite(result?.savings) ? result.savings : null,
          discount: Number.isFinite(result?.discount) ? result.discount : null,
          offerCents: Number.isFinite(negotiation.offer) ? negotiation.offer : null,
          afterOfferCents: Number.isFinite(negotiation.afterOffer) ? negotiation.afterOffer : null,
          missing: result?.missing || null
        } : null
      };
    } catch (error) {
      return {status: 'error', reason: String(error?.message || error)};
    }
  }

  function analyzeBatch(items) {
    return (Array.isArray(items) ? items : []).map(analyze);
  }

  globalThis.VintedAffariAndroidBridge = {
    version: '1.0-mobile-beta',
    ready: !!(matcher && D && globalThis.VintedLocalCatalog?.games?.length),
    gameCount: globalThis.VintedLocalCatalog?.games?.length || 0,
    analyze,
    analyzeBatch
  };
})();
