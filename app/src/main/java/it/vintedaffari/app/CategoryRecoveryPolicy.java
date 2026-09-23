package it.vintedaffari.app;

/** Pure publication decisions for listings recovered from explicit Vinted category evidence. */
public final class CategoryRecoveryPolicy {
    private CategoryRecoveryPolicy() {}

    public static String enrichmentState(boolean explicitBoardGameCategory, Double bggRating, boolean exactVintedIdentity) {
        if (!explicitBoardGameCategory) return null;
        if (bggRating == null) return "PENDING_ANALYSIS";
        if (bggRating < DealPolicy.MIN_BGG_RATING) return "LOCAL_ONLY";
        return exactVintedIdentity ? "CORE_COMPLETE" : "DEFERRED_LINK";
    }

    public static boolean mayClearTypeHold(boolean explicitBoardGameCategory, Double bggRating) {
        return explicitBoardGameCategory && bggRating != null && bggRating >= DealPolicy.MIN_BGG_RATING;
    }
}