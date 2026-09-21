package it.vintedaffari.app;

import java.util.Locale;

/**
 * Structural compatibility between marketplace product evidence and BGG's authoritative item type.
 * A title score can nominate an identity; it can never override this check for automatic publication.
 */
public final class BggProductCompatibility {
    public enum Verdict { COMPATIBLE, INCOMPATIBLE, UNKNOWN }

    private BggProductCompatibility() {}

    public static Verdict validate(ListingClassifier.Type listingType, String bggType) {
        if (listingType == null || listingType == ListingClassifier.Type.UNCERTAIN) return Verdict.UNKNOWN;
        String actual = bggType == null ? "" : bggType.trim().toLowerCase(Locale.ROOT);
        if (!"boardgame".equals(actual) && !"boardgameexpansion".equals(actual)) return Verdict.UNKNOWN;
        if (listingType == ListingClassifier.Type.EXPANSION) {
            return "boardgameexpansion".equals(actual) ? Verdict.COMPATIBLE : Verdict.INCOMPATIBLE;
        }
        if (listingType == ListingClassifier.Type.BASE_GAME) {
            return "boardgame".equals(actual) ? Verdict.COMPATIBLE : Verdict.INCOMPATIBLE;
        }
        return Verdict.INCOMPATIBLE;
    }

    public static boolean canAutoPublish(ListingClassifier.Type listingType, String bggType) {
        return validate(listingType, bggType) == Verdict.COMPATIBLE;
    }
}
