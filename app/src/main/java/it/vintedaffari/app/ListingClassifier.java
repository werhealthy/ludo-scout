package it.vintedaffari.app;

import java.text.Normalizer;
import java.util.Locale;

/** Fast, deliberately conservative classifier that runs BEFORE BGG matching. */
public final class ListingClassifier {
    public enum Type { BASE_GAME, EXPANSION, ACCESSORY, COMPONENTS, EMPTY_BOX, BUNDLE, NON_GAME, UNCERTAIN }

    public static final class Result {
        public final Type type;
        public final String reason;
        public final boolean allowPriceModel;
        Result(Type type, String reason, boolean allowPriceModel) {
            this.type = type; this.reason = reason; this.allowPriceModel = allowPriceModel;
        }
    }

    private static final String[] EMPTY_BOX = {
            "scatola vuota", "scatole vuote", "solo scatola", "solo box", "empty box", "box only"
    };
    private static final String[] ACCESSORY = {
            "raccoglitore", "raccoglitori", "organizer", "organiser", "inserto", "insert", "inserti", "inserts",
            "playmat", "tappetino", "porta carte", "portacarte", "divisori", "sleeves", "bustine",
            "deck box", "porta mazzo", "accessori", "accessorio", "upgrade kit", "token holder",
            "dice tray", "porta dadi", "porta token", "porta segnalini"
    };
    /** Component nouns alone are normal board-game descriptions ("piazzamento tessere", "dadi",
     * "miniature"). Only phrases that explicitly say the listing contains separate/replacement
     * components are strong enough to auto-filter. */
    private static final String[] COMPONENTS = {
            "ricambi", "componenti separati", "pezzi di ricambio", "solo componenti", "solo carte",
            "solo token", "solo segnalini", "solo pedine", "solo miniature", "solo dadi", "solo meeple",
            "solo tiles", "solo tessere", "set di token", "set token", "set di segnalini", "set segnalini",
            "tessere ricambio", "token ricambio", "pedine ricambio", "dadi ricambio",
            "carte promo", "promo cards", "manuale di ricambio", "regolamento di ricambio"
    };
    private static final String[] VIDEO_GAME = {
            "videogioco", "videogame", "video game", "playstation 5", "playstation 4", "playstation 3",
            "ps5", "ps4", "ps3", "xbox one", "xbox series", "xbox 360", "nintendo switch",
            "switch lite", "nintendo 3ds", "nintendo ds", "wii u", "per ps5", "per ps4", "per xbox"
    };
    private static final String[] EXPANSION = {
            "espansione", "expansion", "estensione", "add on", "addon", "scenario pack", "campaign expansion"
    };
    private static final String[] BUNDLE = {
            "lotto", "bundle", "stock", "set di giochi", "giochi in blocco", "blocco giochi", "collezione giochi"
    };
    /** Strong negative evidence. Keep this list category-based rather than brand-based: a title such
     * as "Scarpe Adidas" should never become a provisional BGG game, while an obscure board game
     * with an unfamiliar proper name must still be allowed through to the BGG matcher. */
    private static final String[] NON_GAME = {
            "scarpe", "sneakers", "sneaker", "stivali", "sandali", "ciabatte", "mocassini",
            "occhiali da sole", "occhiali", "sunglasses", "montatura", "lenti da sole",
            "libro", "libri", "romanzo", "romanzi", "fumetto", "fumetti", "manga",
            "maglietta", "t shirt", "tshirt", "felpa", "pantaloni", "jeans", "giacca", "cappotto",
            "borsa", "borsetta", "zaino", "portafoglio", "cintura",
            "profumo", "orologio", "collana", "orecchini", "bracciale",
            "dvd", "blu ray", "bluray", "cd musicale", "compact disc", "audio cd", "album cd", "vinile",
            "isbn", "paperback", "hardcover", "copertina rigida", "copertina flessibile", "pagine", "editore", "autore",
            "biografia", "saggio", "enciclopedia", "rivista", "magazine", "literature", "world literature", "letteratura",
            "hi hat", "hihat", "cymbal", "cymbals", "piatto batteria", "piatti batteria", "drum cymbal", "crash cymbal", "ride cymbal", "audiocassetta", "musicassette"
    };

    public static Result classify(VintedCard card) {
        String title = norm(card == null ? "" : card.title);
        String t = norm((card == null ? "" : card.title) + " " + (card == null ? "" : card.rawDescription));
        // Strong category exclusions run before accessory/component classification: a Warhammer
        // miniature or a book containing the word "manuale" must be dropped, not stored as a
        // board-game accessory that later becomes another review.
        if (BoardGameIntakeGate.isStrongNonGameText(card == null ? "" : card.title, card == null ? "" : card.rawDescription) || containsAny(title, NON_GAME) || containsAny(t, VIDEO_GAME) || containsWord(title,"cd")) return new Result(Type.NON_GAME, "Segnali forti di categoria non ludica o videogioco.", false);
        // Product decision: Warhammer marketplace results are overwhelmingly miniatures/parts for
        // this workflow. Treat the brand/name as a strong exclusion signal so it never fills BGG
        // review with modelling products. A future explicit allow-list can re-enable specific games.
        if (containsWord(title,"warhammer")) return new Result(Type.NON_GAME, "Warhammer escluso: forte segnale di modellismo/miniature.", false);
        if (containsAny(t, EMPTY_BOX)) return new Result(Type.EMPTY_BOX, "Annuncio di scatola vuota/sola confezione.", false);
        if (containsAny(t, ACCESSORY)) return new Result(Type.ACCESSORY, "Termini tipici di accessorio/organizer.", false);
        if (containsAny(t, COMPONENTS)) return new Result(Type.COMPONENTS, "L'annuncio sembra riferirsi a componenti separati.", false);
        if (containsAny(t, BUNDLE)) return new Result(Type.BUNDLE, "Possibile lotto/bundle: il prezzo non va confrontato con un singolo gioco.", false);
        if (containsAny(t, EXPANSION)) return new Result(Type.EXPANSION, "Possibile espansione: ammessa, ma con controllo prezzo più prudente.", true);
        // "scatola/scatole" da sole sono abbastanza sospette da non creare una falsa Offertona.
        if (t.contains("scatola") || t.contains("scatole") || t.contains("box "))
            return new Result(Type.UNCERTAIN, "Riferimento a scatola/box: richiede verifica manuale.", false);
        return new Result(Type.BASE_GAME, "Nessun segnale di accessorio/lotto.", true);
    }

    public static boolean isExtremePriceAnomaly(GameAnalysis a) {
        if (a == null || a.totalCents == null || a.benchmarkCents == null || a.benchmarkCents <= 0) return false;
        return a.totalCents <= Math.round(a.benchmarkCents * 0.28);
    }

    private static boolean containsAny(String text, String[] needles) {
        for (String n : needles) if (text.contains(norm(n))) return true;
        return false;
    }

    private static boolean containsWord(String text,String word){
        if(text==null||word==null)return false;String n=" "+norm(text)+" ";String w=" "+norm(word)+" ";return n.contains(w);
    }

    private static String norm(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
