package it.vintedaffari.app;

/** Canonical Vinted listing, independent from the legacy deal-feed row. */
public final class MarketListingRecord {
    public long id;
    public Long gameId;
    public String tempFingerprint, vintedItemId, title, brand, condition;
    public int currentPriceCents;
    public Integer protectedPriceCents, favorites;
    public String sellerId, sellerName, url, imageUrl, photosCsv, publishedLabel, languageCode;
    public String lifecycle, enrichmentState, matchState, lastError;
    public Double matchConfidence;
    public long firstSeen, lastSeen, enrichedAt;

    /** Adapter for the existing Vinted resolver so its proven matching logic can be reused. */
    public DealRecord asDealRecord(GameRecord game) {
        DealRecord d = new DealRecord();
        d.signature = tempFingerprint;
        d.firstSeen = firstSeen;
        d.lastSeen = lastSeen;
        d.vintedItemId = vintedItemId;
        d.vintedTitle = title;
        d.brand = brand;
        d.condition = condition;
        d.itemPriceCents = currentPriceCents;
        d.protectedPriceCents = protectedPriceCents;
        d.favorites = favorites;
        d.sellerId = sellerId;
        d.sellerName = sellerName;
        d.vintedUrl = url;
        d.imageUrl = imageUrl;
        d.listingPhotosCsv = photosCsv;
        d.publishedLabel = publishedLabel;
        d.languageCode = languageCode;
        if (game != null) {
            d.bggId = game.bggId;
            d.gameName = game.name;
            d.displayName = game.name;
            d.rating = game.rating;
            d.rank = game.rank;
            d.voters = game.voters;
            d.bggImageUrl = game.imageUrl;
            d.minPlayers = game.minPlayers;
            d.maxPlayers = game.maxPlayers;
            d.weight = game.weight;
            d.playtime = game.playtime;
        }
        return d;
    }
}
