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

}
