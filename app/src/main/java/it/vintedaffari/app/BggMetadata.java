package it.vintedaffari.app;

/** Full BGG metadata snapshot persisted once per canonical game. */
public final class BggMetadata {
    public String bggId, name, originalName, alternateNames, description, thumbnailUrl, imageUrl, itemType;
    public String categories, mechanics, designers, artists, publishers, families, expansions, baseGames;
    public Integer year, minPlayers, maxPlayers, playtime, minAge, rank, voters;
    public Integer marketUsedMedianCents, marketUsedMinCents, marketUsedCount;
    public Double weight, rating;
}
