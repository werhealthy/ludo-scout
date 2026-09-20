package it.vintedaffari.app;

import android.graphics.Rect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VintedCardParser {
    private static final Pattern PRICE_PATTERN = Pattern.compile("(\\d+(?:[.,]\\d{1,2})?)\\s*€");
    private static final Pattern FAVORITES_PATTERN = Pattern.compile("preferiti da\\s+(\\d+)\\s+utent", Pattern.CASE_INSENSITIVE);
    private static final Pattern BRAND_PATTERN = Pattern.compile("(?:^|[,;|])\\s*brand\\s*:\\s*([^,;|]+)",Pattern.CASE_INSENSITIVE);
    private static final Pattern CONDITION_PATTERN = Pattern.compile("(?:^|[,;|])\\s*condizioni\\s*:\\s*([^,;|]+)",Pattern.CASE_INSENSITIVE);
    private static final Pattern SELLER_PATTERN = Pattern.compile("(?:^|[,;|])\\s*(?:venditore|seller|utente|username)\\s*:\s*([^,;|]+)", Pattern.CASE_INSENSITIVE);

    private VintedCardParser() {}

    /** Accept both Vinted's single content-description sentence and a fallback semantic blob
     * assembled from descendant accessibility nodes. */
    public static VintedCard parse(String description, Rect bounds) {
        if (description == null || description.trim().isEmpty()) return null;
        String normalized=description.replace('\n',',').replace('\r',',').replaceAll("\\s+"," ").replaceAll(",\\s*,+",",").trim();
        if (!normalized.contains("€")) return null;

        List<Double> prices = new ArrayList<>();
        Matcher priceMatcher = PRICE_PATTERN.matcher(normalized);
        while (priceMatcher.find()) {try {prices.add(Double.parseDouble(priceMatcher.group(1).replace(',', '.')));} catch (Exception ignored) {}}
        if (prices.isEmpty()) return null;

        String title=extractTitle(normalized);
        if(title.isEmpty())return null;

        String brand=group(BRAND_PATTERN,normalized),condition=group(CONDITION_PATTERN,normalized),seller=group(SELLER_PATTERN,normalized);
        Integer favorites = null;Matcher favMatcher = FAVORITES_PATTERN.matcher(normalized);if (favMatcher.find()) {try {favorites = Integer.parseInt(favMatcher.group(1));} catch (Exception ignored) {}}
        double itemPrice = prices.get(0);Double protectedPrice = prices.size() > 1 ? prices.get(1) : null;
        return new VintedCard(clean(title),clean(brand),clean(condition),itemPrice,protectedPrice,favorites,bounds,normalized,clean(seller));
    }

    public static String formatPrice(double value) {return String.format(Locale.ITALY, "%.2f €", value);}

    /** Vinted accessibility blobs are not guaranteed to start with the title. In particular the
     * protection/price row can be exposed first (e.g. "9,10 € … protezione acquisti"). Never
     * promote that UI copy to a listing title: prefer the clean prefix when present, otherwise
     * scan semantic fragments for the first plausible human listing title. */
    static String extractTitle(String value){
        if(value==null)return "";
        int end=firstLabelOrPrice(value);
        if(end>0){
            String prefix=cleanTitleCandidate(value.substring(0,end));
            if(isPlausibleTitle(prefix))return prefix;
        }
        String[] parts=value.split("(?:;|\\||,\\s+)");
        String best="";int bestScore=Integer.MIN_VALUE;
        for(int i=0;i<parts.length;i++){
            String part=cleanTitleCandidate(parts[i]);
            if(!isPlausibleTitle(part))continue;
            String low=part.toLowerCase(Locale.ROOT);
            int score=100-i;
            int words=part.trim().isEmpty()?0:part.trim().split("\\s+").length;
            score+=Math.min(18,words*3);
            if(low.contains("gioco")||low.contains("game"))score+=4;
            if(score>bestScore){best=part;bestScore=score;}
        }
        return best;
    }

    private static String cleanTitleCandidate(String raw){
        String out=trimTrailingSeparators(clean(raw).replaceFirst("(?i)^(articolo|annuncio)\\s*[:,|-]?\\s*",""));
        out=out.replaceFirst("(?i)^\\d+(?:[.,]\\d{1,2})?\\s*(?:€|euro)\\s*(?:e\\s*\\d+)?\\s*","").trim();
        return trimTrailingSeparators(out);
    }

    private static boolean isPlausibleTitle(String raw){
        if(raw==null)return false;String x=raw.trim();if(x.length()<2||x.length()>180)return false;String n=x.toLowerCase(Locale.ROOT);
        if(n.contains("€")||PRICE_PATTERN.matcher(x).find())return false;
        if(n.matches("^[\\d\\s.,:+-]+$")||n.matches("^\\d+\\s*(?:euro|eur)(?:\\s+e\\s+\\d+)?$"))return false;
        String[] noise={"protezione acquisti","include la protezione","include la","include il","acquisti protetti","spedizione","brand:","condizioni:","venditore:","seller:","preferiti da","pubblicato","caricato","visualizzato","articolo venduto","compra ora"};
        for(String z:noise)if(n.contains(z))return false;
        return true;
    }

    private static int firstLabelOrPrice(String value){
        int best=firstPriceStart(value);for(String label:new String[]{"brand:","condizioni:","protezione acquisti"}){int x=indexOfIgnoreCase(value,label,0);if(x>=0&&(best<0||x<best))best=x;}return best;
    }
    private static String group(Pattern p,String s){Matcher m=p.matcher(s);return m.find()?m.group(1):"";}
    private static int firstPriceStart(String value) {Matcher matcher = PRICE_PATTERN.matcher(value);return matcher.find() ? matcher.start() : -1;}
    private static int indexOfIgnoreCase(String value, String needle, int fromIndex) {return value.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT), fromIndex);}
    private static String trimTrailingSeparators(String value) {String out = value.trim();while(out.endsWith(",")||out.endsWith(";")||out.endsWith("|")||out.endsWith("-"))out=out.substring(0,out.length()-1).trim();return out;}
    private static String clean(String value) {if (value == null) return "";return value.replace('\n', ' ').replace('\r', ' ').trim();}
}
