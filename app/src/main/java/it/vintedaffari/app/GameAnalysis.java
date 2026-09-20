package it.vintedaffari.app;

import org.json.JSONObject;

public final class GameAnalysis {
    public final String status;
    public final String reason;
    public final String gameName;
    public final String matchedAlias;
    public final String displayName;
    public final String bggId;
    public final Double averageRating;
    public final Double geekRating;
    public final Integer rank;
    public final Integer voters;
    public final Integer qualityScore;
    public final String tier;
    public final String tierLabel;
    public final Integer totalCents;
    public final Integer benchmarkCents;
    public final Integer offerCents;
    public final Integer afterOfferCents;
    public final Integer feeCents;
    public final Integer shippingCents;
    public final Integer savingsCents;
    public final Double discount;
    public final String languageCode;
    public final boolean languageBlocked;
    public final String referenceKind;
    public final String referenceSource;
    public final String productTitle;
    public final String productPublisher;
    public final Integer productScore;
    public final String matchReason;
    /** Best unresolved BGG candidate, when the matcher found something plausible but not decisive. */
    public final String candidateName;
    public final String candidateBggId;
    /** Raw confidence score emitted by the embedded catalog matcher (0..100 when available). */
    public final Double matchConfidence;

    private GameAnalysis(String status, String reason, String gameName, String matchedAlias, String displayName,
                         String bggId, Double averageRating, Double geekRating, Integer rank, Integer voters,
                         Integer qualityScore, String tier, String tierLabel, Integer totalCents,
                         Integer benchmarkCents, Integer offerCents, Integer afterOfferCents, Integer feeCents,
                         Integer shippingCents, Integer savingsCents, Double discount, String languageCode,
                         boolean languageBlocked, String referenceKind, String referenceSource,
                         String productTitle, String productPublisher, Integer productScore, String matchReason,
                         String candidateName, String candidateBggId, Double matchConfidence) {
        this.status = status;
        this.reason = reason;
        this.gameName = gameName;
        this.matchedAlias = matchedAlias;
        this.displayName = displayName;
        this.bggId = bggId;
        this.averageRating = averageRating;
        this.geekRating = geekRating;
        this.rank = rank;
        this.voters = voters;
        this.qualityScore = qualityScore;
        this.tier = tier;
        this.tierLabel = tierLabel;
        this.totalCents = totalCents;
        this.benchmarkCents = benchmarkCents;
        this.offerCents = offerCents;
        this.afterOfferCents = afterOfferCents;
        this.feeCents = feeCents;
        this.shippingCents = shippingCents;
        this.savingsCents = savingsCents;
        this.discount = discount;
        this.languageCode = languageCode;
        this.languageBlocked = languageBlocked;
        this.referenceKind = referenceKind;
        this.referenceSource = referenceSource;
        this.productTitle = productTitle;
        this.productPublisher = productPublisher;
        this.productScore = productScore;
        this.matchReason = matchReason;
        this.candidateName = candidateName;
        this.candidateBggId = candidateBggId;
        this.matchConfidence = matchConfidence;
    }

    /** Re-evaluate only the price decision against a better used-market reference. Identity, BGG
     * quality and language evidence remain untouched. This is used when Ludo Scout has learned a
     * stronger local Vinted baseline than the bundled BGG fallback. */
    public GameAnalysis withUsedMarketBenchmark(int referenceCents, String kind, String source) {
        if (referenceCents <= 0) return this;
        Integer total = totalCents;
        Double pct = total == null ? null : ((referenceCents - total) * 100.0 / referenceCents);
        Integer savings = total == null ? null : referenceCents - total;
        String nextTier = tier; String nextLabel = tierLabel;
        if (total != null) {
            if (total <= Math.floor(referenceCents * 0.80)) { nextTier = "hot"; nextLabel = "Offertona"; }
            else if (total < referenceCents) { nextTier = "good"; nextLabel = "Buon prezzo"; }
            else { nextTier = "normal"; nextLabel = "Prezzo di mercato"; }
        }
        return new GameAnalysis(status, reason, gameName, matchedAlias, displayName, bggId, averageRating, geekRating,
                rank, voters, qualityScore, nextTier, nextLabel, totalCents, referenceCents, null, null, feeCents,
                shippingCents, savings, pct, languageCode, languageBlocked, kind, source, productTitle,
                productPublisher, productScore, matchReason, candidateName, candidateBggId, matchConfidence);
    }

    /** Explicit Vinted market-scan context: keep price/language evidence from the observation but
     * pin identity to the game the user deliberately searched for. Benchmark is reset until local
     * comparable evidence or BGG refresh supplies the correct market reference. */
    public GameAnalysis withCanonicalIdentity(GameRecord game,String why){if(game==null||game.bggId==null||game.bggId.isEmpty())return this;Integer q=QualityComposite.score(game.rank,null,game.rating,game.voters);return new GameAnalysis("matched",why,game.name,game.name,game.name,game.bggId,game.rating,null,game.rank,game.voters,q,"normal","Prezzo da confrontare",totalCents,null,null,null,feeCents,shippingCents,null,null,languageCode,languageBlocked,"vinted_scan","Ricerca Vinted avviata dalla scheda gioco",productTitle,productPublisher,productScore,why,game.name,game.bggId,100.0);}

    public static GameAnalysis fromJson(JSONObject json) {
        String status = optString(json, "status");
        String reason = optString(json, "reason");
        String matchReason = optString(json, "matchReason");
        JSONObject game = json.optJSONObject("game");
        JSONObject deal = json.optJSONObject("deal");
        JSONObject language = json.optJSONObject("language");
        JSONObject reference = json.optJSONObject("reference");
        JSONObject product = json.optJSONObject("product");
        JSONObject candidate = json.optJSONObject("candidate");

        return new GameAnalysis(
                status, reason,
                game == null ? null : optString(game, "name"),
                game == null ? null : optString(game, "matchedAlias"),
                game == null ? null : optString(game, "displayName"),
                game == null ? null : optString(game, "bggId"),
                game == null ? null : optDouble(game, "averageRating"),
                game == null ? null : optDouble(game, "geekRating"),
                game == null ? null : optInt(game, "rank"),
                game == null ? null : optInt(game, "voters"),
                game == null ? null : optInt(game, "qualityScore"),
                deal == null ? null : optString(deal, "tier"),
                deal == null ? null : optString(deal, "label"),
                deal == null ? null : optInt(deal, "totalCents"),
                deal == null ? null : optInt(deal, "benchmarkCents"),
                deal == null ? null : optInt(deal, "offerCents"),
                deal == null ? null : optInt(deal, "afterOfferCents"),
                deal == null ? null : optInt(deal, "feeCents"),
                deal == null ? null : optInt(deal, "shippingCents"),
                deal == null ? null : optInt(deal, "savingsCents"),
                deal == null ? null : optDouble(deal, "discount"),
                language == null ? null : optString(language, "code"),
                language != null && language.optBoolean("blocked", false),
                reference == null ? null : optString(reference, "kind"),
                reference == null ? null : optString(reference, "sourceLabel"),
                product == null ? null : optString(product, "title"),
                product == null ? null : optString(product, "publisher"),
                product == null ? null : optInt(product, "score"),
                matchReason,
                candidate == null ? null : optString(candidate, "name"),
                candidate == null ? null : optString(candidate, "bggId"),
                optDouble(json, "matchScore")
        );
    }

    private static String optString(JSONObject json, String key) {
        if (json == null || !json.has(key) || json.isNull(key)) return null;
        String value = json.optString(key, null);
        return value == null || value.isEmpty() ? null : value;
    }
    private static Integer optInt(JSONObject json, String key) {
        if (json == null || !json.has(key) || json.isNull(key)) return null;
        Object value = json.opt(key);
        if (!(value instanceof Number)) return null;
        return ((Number) value).intValue();
    }
    private static Double optDouble(JSONObject json, String key) {
        if (json == null || !json.has(key) || json.isNull(key)) return null;
        Object value = json.opt(key);
        if (!(value instanceof Number)) return null;
        return ((Number) value).doubleValue();
    }
}
