package it.vintedaffari.app;

/**
 * Central product decision for a listing.
 *
 * <p>The engine keeps item price and real purchase total separate. A listing is only filtered out
 * when the seller's ask is itself materially above the used-market reference and a plausible offer
 * cannot repair it. High shipping alone should not make a potentially useful bundle lead disappear.</p>
 */
public final class DealEvaluator {
    public enum Decision {
        GREAT_BUY,
        GOOD_PRICE,
        OFFER,
        FAIR,
        INSUFFICIENT_DATA,
        REJECT
    }

    public static final class Evaluation {
        public final Decision decision;
        public final String label;
        public final String reason;
        public final Integer suggestedOfferCents;
        public final Integer currentTotalCents;
        public final Integer benchmarkCents;

        Evaluation(Decision decision, String label, String reason, Integer suggestedOfferCents,
                   Integer currentTotalCents, Integer benchmarkCents) {
            this.decision = decision;
            this.label = label;
            this.reason = reason;
            this.suggestedOfferCents = suggestedOfferCents;
            this.currentTotalCents = currentTotalCents;
            this.benchmarkCents = benchmarkCents;
        }

        public boolean visible() {
            return decision != Decision.REJECT;
        }

        public String storageTier() {
            switch (decision) {
                case GREAT_BUY: return "hot";
                case GOOD_PRICE: return "good";
                case OFFER: return "offer";
                case FAIR: return "fair";
                case INSUFFICIENT_DATA: return "insufficient";
                default: return "filtered";
            }
        }
    }

    private DealEvaluator() {}

    public static Evaluation evaluate(VintedCard card, GameAnalysis analysis) {
        if (card == null || analysis == null) {
            return insufficient(null, null);
        }
        int item = Math.max(0, (int) Math.round(card.itemPrice * 100.0));
        return evaluate(item, analysis.totalCents, analysis.benchmarkCents, analysis.marketQ25Cents,
                analysis.marketAllowHot, analysis.offerCents, analysis.afterOfferCents, analysis.shippingCents);
    }

    public static Evaluation evaluate(DealRecord deal) {
        if (deal == null) return insufficient(null, null);
        Integer currentTotal = effectiveTotal(deal);
        Integer shipping = deal.shippingVerifiedCents != null ? deal.shippingVerifiedCents : deal.shippingCents;
        Integer offerTotal = offerTotal(deal.offerCents, shipping);
        // DealRecord does not yet persist quartiles, so only a previously validated hot row may
        // retain the strongest category. Recalculation paths with fresh market stats pass Q25.
        return evaluate(Math.max(0, deal.itemPriceCents), currentTotal, deal.benchmarkCents, null,
                "hot".equals(deal.tier), deal.offerCents, offerTotal, shipping);
    }

    public static Evaluation evaluate(int itemCents, Integer currentTotalCents, Integer benchmarkCents,
                                      Integer offerCents, Integer afterOfferCents, Integer shippingCents) {
        return evaluate(itemCents, currentTotalCents, benchmarkCents, null, false,
                offerCents, afterOfferCents, shippingCents);
    }

    public static Evaluation evaluate(int itemCents, Integer currentTotalCents, Integer benchmarkCents,
                                      Integer lowerQuartileCents, boolean allowGreat,
                                      Integer offerCents, Integer afterOfferCents, Integer shippingCents) {
        if (benchmarkCents == null || benchmarkCents <= 0 || itemCents <= 0) {
            return insufficient(currentTotalCents, benchmarkCents);
        }

        int benchmark = benchmarkCents;
        int smallMargin = Math.max(300, (int) Math.round(benchmark * 0.08));
        int strongMargin = Math.max(500, (int) Math.round(benchmark * 0.15));
        int goodCeiling = benchmark - smallMargin;
        int greatCeiling = benchmark - strongMargin;
        int fairItemCeiling = benchmark + smallMargin;

        if (currentTotalCents != null && currentTotalCents > 0) {
            int greatItemCeiling = lowerQuartileCents != null && lowerQuartileCents > 0
                    ? Math.min(lowerQuartileCents, goodCeiling) : goodCeiling;
            if (allowGreat && currentTotalCents <= greatCeiling && itemCents <= greatItemCeiling) {
                return new Evaluation(Decision.GREAT_BUY, "Offertona",
                        euroBelow(benchmark - currentTotalCents) + " sotto l'usato tipico",
                        null, currentTotalCents, benchmark);
            }
            if (currentTotalCents <= goodCeiling) {
                return new Evaluation(Decision.GOOD_PRICE, "Buon prezzo",
                        euroBelow(benchmark - currentTotalCents) + " sotto l'usato tipico",
                        null, currentTotalCents, benchmark);
            }
        }

        // Prefer the price that mathematically makes the purchase good. Legacy engine offers are
        // only a fallback when shipping is not known well enough to solve the target total.
        Integer suggestedOffer = shippingCents != null && shippingCents >= 0
                ? PurchaseMath.maxItemForTotal(goodCeiling, shippingCents)
                : offerCents;
        Integer plausibleOfferTotal = offerTotal(suggestedOffer, shippingCents);
        if ((plausibleOfferTotal == null || plausibleOfferTotal <= 0) && suggestedOffer != null
                && suggestedOffer.equals(offerCents)) {
            plausibleOfferTotal = afterOfferCents;
        }
        if (suggestedOffer != null && suggestedOffer > 0 && suggestedOffer < itemCents) {
            double cut = (itemCents - suggestedOffer) / (double) itemCents;
            if (cut >= 0.05 && cut <= 0.18 && plausibleOfferTotal != null && plausibleOfferTotal <= goodCeiling) {
                return new Evaluation(Decision.OFFER, "Prova un’offerta",
                        "A " + money(suggestedOffer) + " diventerebbe un buon prezzo",
                        suggestedOffer, currentTotalCents, benchmark);
            }
        }

        // Keep a fair seller ask even when shipping makes the single purchase mediocre:
        // that is exactly the kind of lead that can become useful in a bundle.
        if (itemCents <= fairItemCeiling) {
            String reason;
            if (currentTotalCents != null && currentTotalCents > benchmark + smallMargin) {
                reason = "Il prezzo del gioco è sensato; la spedizione pesa sul singolo acquisto";
            } else {
                reason = "In linea con l'usato tipico";
            }
            return new Evaluation(Decision.FAIR, "Prezzo giusto", reason,
                    null, currentTotalCents, benchmark);
        }

        return new Evaluation(Decision.REJECT, "", "Prezzo sopra il mercato usato",
                null, currentTotalCents, benchmark);
    }

    public static boolean isBundleProspect(DealRecord deal) {
        if (deal == null || !"ACTIVE".equals(deal.lifecycle) || "verify".equals(deal.tier)) return false;
        if (!DealPolicy.ratingEligible(deal) || isEmpty(deal.sellerId) || isEmpty(deal.vintedUrl) || isEmpty(deal.bggId)) return false;
        Evaluation e = evaluate(deal);
        if (!e.visible()) return false;
        boolean strongGame = (deal.qualityScore != null && deal.qualityScore >= 74)
                || (deal.rating != null && deal.rating >= 7.4);
        boolean economicLead = e.decision == Decision.GREAT_BUY
                || e.decision == Decision.GOOD_PRICE
                || e.decision == Decision.OFFER;
        return strongGame || economicLead;
    }

    public static double bundleProspectScore(DealRecord deal) {
        if (!isBundleProspect(deal)) return -1;
        Evaluation e = evaluate(deal);
        double score = deal.qualityScore == null ? (deal.rating == null ? 55 : deal.rating * 10) : deal.qualityScore;
        switch (e.decision) {
            case GREAT_BUY: score += 28; break;
            case GOOD_PRICE: score += 18; break;
            case OFFER: score += 12; break;
            case FAIR: score += 4; break;
            default: break;
        }
        Integer total = effectiveTotal(deal);
        if (deal.benchmarkCents != null && total != null && total > deal.benchmarkCents
                && deal.itemPriceCents <= deal.benchmarkCents) {
            score += 8; // shipping-friction lead: bundle can genuinely help.
        }
        return score;
    }

    private static Evaluation insufficient(Integer total, Integer benchmark) {
        return new Evaluation(Decision.INSUFFICIENT_DATA, "Pochi dati",
                "Riferimento usato non ancora abbastanza solido", null, total, benchmark);
    }

    private static Integer effectiveTotal(DealRecord d) {
        if (d.shippingVerifiedCents != null) {
            int base = d.protectedPriceCents != null ? d.protectedPriceCents : d.itemPriceCents + PurchaseMath.vintedFee(d.itemPriceCents);
            return base + d.shippingVerifiedCents;
        }
        if (d.totalCents != null && d.totalCents > 0) return d.totalCents;
        if (d.protectedPriceCents != null && d.protectedPriceCents > 0) return d.protectedPriceCents;
        return null;
    }

    private static Integer offerTotal(Integer offerCents, Integer shippingCents) {
        if (offerCents == null || offerCents <= 0 || shippingCents == null || shippingCents < 0) return null;
        return PurchaseMath.estimatedTotal(offerCents, shippingCents);
    }

    private static String euroBelow(int cents) {
        return money(Math.max(0, cents));
    }

    private static String money(int cents) {
        int euros = Math.abs(cents) / 100;
        int rem = Math.abs(cents) % 100;
        return rem == 0 ? euros + " €" : String.format(java.util.Locale.ITALY, "%.2f €", cents / 100.0);
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
