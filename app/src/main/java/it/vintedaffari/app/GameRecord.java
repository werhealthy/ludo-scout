package it.vintedaffari.app;

/** Canonical game row used by the local market database. */
public final class GameRecord {
    public long id;
    public String bggId, provisionalKey, name, originalName, alternateNames, description;
    public String thumbnailUrl, imageUrl, bggUrl;
    public Integer year, minPlayers, maxPlayers, playtime, minAge, rank, voters;
    public Double weight, rating, matchConfidence;
    public String categories, mechanics, designers, artists, publishers, families, expansions, baseGames;
    public String matchState;
    public long firstSeen, lastSeen, metadataUpdatedAt;

    // Aggregate market fields populated by MarketStore search/detail queries.
    public int listingCount, activeListingCount, observationCount;
    public Integer currentMinPriceCents, historicalMinPriceCents, historicalMaxPriceCents, recentAveragePriceCents;
    public Double historicalAveragePriceCents, historicalMedianPriceCents, q1PriceCents, q3PriceCents;
}
