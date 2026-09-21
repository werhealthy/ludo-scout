package it.vintedaffari.app;

import java.util.Locale;

/**
 * Structural compatibility between marketplace product evidence and BGG's authoritative item type.
 * A title score can nominate an identity; it can never override this check for automatic publication.
 *
 * This class intentionally has no Android or classifier dependency so its product contract is
 * executable in the lightweight regression suite.
 */
public final class BggProductCompatibility {
    public enum Verdict { COMPATIBLE, INCOMPATIBLE, UNKNOWN }

    private BggProductCompatibility() {}

    public static Verdict validate(String listingType, String bggType) {
        String expected=listingType==null?"":listingType.trim().toUpperCase(Locale.ROOT);
        if ("UNCERTAIN".equals(expected) || expected.isEmpty()) return Verdict.UNKNOWN;
        String actual = bggType == null ? "" : bggType.trim().toLowerCase(Locale.ROOT);
        if (!"boardgame".equals(actual) && !"boardgameexpansion".equals(actual)) return Verdict.UNKNOWN;
        if ("EXPANSION".equals(expected)) return "boardgameexpansion".equals(actual) ? Verdict.COMPATIBLE : Verdict.INCOMPATIBLE;
        if ("BASE_GAME".equals(expected)) return "boardgame".equals(actual) ? Verdict.COMPATIBLE : Verdict.INCOMPATIBLE;
        return Verdict.INCOMPATIBLE;
    }

    public static boolean canAutoPublish(String listingType, String bggType) {
        return validate(listingType, bggType) == Verdict.COMPATIBLE;
    }
}
