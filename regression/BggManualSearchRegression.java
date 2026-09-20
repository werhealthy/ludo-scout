import it.vintedaffari.app.BggManualSearchRanking;

public final class BggManualSearchRegression {
    private static int score(String query, String candidate) {
        return BggManualSearchRanking.score(BggManualSearchRanking.prepare(query), candidate, false);
    }
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        require(score("Carcassone", "Carcassonne") >= 900, "single typo should be recoverable");
        require(score("Wingspann", "Wingspan") >= 900, "extra letter should be recoverable");
        require(score("white caslte", "The White Castle") >= 800, "transposition + article should be recoverable");
        require(score("gioco Azul italiano", "Azul") >= 1500, "marketplace filler should not hide exact game");
        require(score("7 wonder duel", "7 Wonders Duel") > score("7 wonder duel", "7 Wonders"), "specific title must beat the base family");
        require(score("Wingspan: Espansione Oceania", "Wingspan: Espansione Oceania") > score("Wingspan: Espansione Oceania", "Wingspan"), "localized expansion title must beat its base game");
        require(score("scarpe adidas", "Azul") == 0, "unrelated products must not become plausible BGG results");
        System.out.println("PASS forgiving manual BGG search ranking");
    }
}
