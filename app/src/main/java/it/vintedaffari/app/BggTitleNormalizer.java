package it.vintedaffari.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Conservative normalisation for marketplace titles before local BGG matching.
 * It removes only packaging/marketplace words that are not part of a game's identity.
 * Semantic words such as "espansione", "expansion", sequel numbers and subtitles are kept.
 */
public final class BggTitleNormalizer {
    private BggTitleNormalizer() {}

    public static List<String> variants(String raw) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String base = clean(raw);
        if (!base.isEmpty()) out.add(base);
        String stripped = stripMarketplaceNoise(base);
        if (!stripped.isEmpty()) out.add(stripped);
        String lead=descriptiveLead(raw);
        if(!lead.isEmpty()){
            String cleanLead=clean(lead);if(!cleanLead.isEmpty())out.add(cleanLead);
            String compact=stripMarketplaceNoise(cleanLead);if(!compact.isEmpty())out.add(compact);
        }
        return new ArrayList<>(out);
    }

    public static String clean(String raw) {
        if (raw == null) return "";
        String s = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return s.toLowerCase(Locale.ROOT)
                .replace('&', ' ')
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Sellers often write "Fyfe – gioco astratto di piazzamento tessere (...)". The
     * descriptive suffix is useful for product typing but harmful to identity matching. Only split
     * when the suffix itself looks descriptive, never on an arbitrary subtitle dash. */
    private static String descriptiveLead(String raw){
        if(raw==null)return "";
        String[] separators={" – "," — "," - "};
        int cut=-1;
        for(String sep:separators){int i=raw.indexOf(sep);if(i>1&&(cut<0||i<cut))cut=i;}
        if(cut<0)return "";
        String suffix=clean(raw.substring(cut+1));
        boolean descriptive=suffix.contains("gioco ")||suffix.contains(" gioco")||suffix.contains("board game")||
                suffix.contains("edizione")||suffix.contains("versione")||suffix.contains("marca ")||
                suffix.contains("condizioni")||suffix.contains("completo")||suffix.contains("sigillato");
        return descriptive?raw.substring(0,cut).trim():"";
    }

    public static String stripMarketplaceNoise(String raw) {
        String s = clean(raw);
        if (s.isEmpty()) return s;

        // Generic category prefixes. Deliberately do not remove "espansione/expansion".
        s = s.replaceFirst("^(?:il |lo |la |un |una )?(?:gioco da tavolo|gioco di societa|board game|tabletop game)\\s+", "");
        s = s.replaceFirst("^(?:vendo gioco|vendesi|vendo|gioco)\\s+", "");

        // Language/edition wording is useful for edition selection but should not prevent
        // identifying the canonical BGG game. Only remove it at the edges.
        String lang = "(?:edizione\\s+)?(?:italiano|italiana|italian|inglese|english|francese|french|tedesco|tedesca|german|deutsch|spagnolo|spagnola|spanish|olandese|dutch|portoghese|portuguese)";
        s = s.replaceFirst("^" + lang + "\\s+", "");
        s = s.replaceFirst("\\s+" + lang + "$", "");

        // Condition/sale adjectives at the edges only.
        String noise = "(?:completo di tutto|ottime condizioni|perfette condizioni|come nuovo|come nuova|sigillato|sigillata|sealed|completo|completa|nuovo|nuova|new|usato|usata|used)";
        boolean changed;
        do {
            String before = s;
            s = s.replaceFirst("^" + noise + "\\s+", "");
            s = s.replaceFirst("\\s+" + noise + "$", "");
            changed = !before.equals(s);
        } while (changed && !s.isEmpty());

        return s.replaceAll("\\s+", " ").trim();
    }
}
