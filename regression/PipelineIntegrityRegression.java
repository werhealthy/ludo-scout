import it.vintedaffari.app.BggProductCompatibility;

public final class PipelineIntegrityRegression {
    private static void expect(BggProductCompatibility.Verdict actual, BggProductCompatibility.Verdict expected, String label) {
        if (actual != expected) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        System.out.println("PASS " + label);
    }

    public static void main(String[] args) {
        expect(BggProductCompatibility.validate("BASE_GAME", "boardgame"), BggProductCompatibility.Verdict.COMPATIBLE, "base game accepts boardgame");
        expect(BggProductCompatibility.validate("BASE_GAME", "boardgameexpansion"), BggProductCompatibility.Verdict.INCOMPATIBLE, "base game rejects expansion candidate");
        expect(BggProductCompatibility.validate("EXPANSION", "boardgameexpansion"), BggProductCompatibility.Verdict.COMPATIBLE, "expansion accepts expansion candidate");
        expect(BggProductCompatibility.validate("EXPANSION", "boardgame"), BggProductCompatibility.Verdict.INCOMPATIBLE, "expansion rejects base candidate");
        expect(BggProductCompatibility.validate("UNCERTAIN", "boardgame"), BggProductCompatibility.Verdict.UNKNOWN, "uncertain product never auto-publishes");
        expect(BggProductCompatibility.validate("BASE_GAME", "thing"), BggProductCompatibility.Verdict.UNKNOWN, "unknown BGG item type stays unpublishable");
    }
}
