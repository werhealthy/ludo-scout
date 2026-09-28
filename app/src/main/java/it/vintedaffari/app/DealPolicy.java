package it.vintedaffari.app;

/** Product-level rules shared by catalog, bundle and recommendations. */
public final class DealPolicy {
    public static final double MIN_BGG_RATING = 6.0;
    private DealPolicy() {}

    /** Missing ratings stay eligible while BGG enrichment is pending; known ratings below 6 do not. */
    public static boolean ratingEligible(Double rating) {
        return rating == null || rating >= MIN_BGG_RATING;
    }

    public static boolean ratingEligible(DealRecord deal) {
        return deal != null && ratingEligible(deal.rating);
    }

    /** Product preference: Children's Game is not part of the scouting database. Keep raw history
     * for diagnostics, but never ask the user to review or publish these games. */
    public static boolean childrenCategory(String categories) {
        if (categories == null || categories.trim().isEmpty()) return false;
        String n=java.text.Normalizer.normalize(categories,java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+","").toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+"," ").trim();
        return n.contains("children s game") || n.contains("childrens game") ||
                n.contains("children game") || n.contains("gioco per bambini") ||
                n.contains("giochi per bambini");
    }

    /** Authoritative BGG metadata gate used once rating/categories are known. */
    public static boolean scoutEligible(Double rating,String categories) {
        return rating != null && rating >= MIN_BGG_RATING && !childrenCategory(categories);
    }

    /** Queue/local candidates usually have rating but may not yet have full categories. */
    public static boolean queueCandidateEligible(Double rating,String categories) {
        return (rating == null || rating >= MIN_BGG_RATING) && !childrenCategory(categories);
    }
}
