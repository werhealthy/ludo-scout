package it.vintedaffari.app;

import android.content.Context;
import android.text.TextUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Zero-network second look at the exact BGG identity once richer Vinted item-page text is known.
 *
 * The first card-level match intentionally stays fast. This pass only changes a listing when a
 * strictly more specific local BGG title is explicitly present in the Vinted title/description
 * (e.g. "Tokaido Duo" while the card had initially been grouped as "Tokaido").
 */
public final class BggVariantReconciler {
    private BggVariantReconciler() {}
    public static final String BUILD = "bgg-variant-guard-v1";

    private static final Map<String,List<BggSearchClient.Game>> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String,List<BggSearchClient.Game>>(24,.75f,true){
                @Override protected boolean removeEldestEntry(Map.Entry<String,List<BggSearchClient.Game>> e){return size()>24;}
            });

    public static void reconcile(Context rawContext, DealDatabase helper, MarketStore market,
                                 long listingId, String signature, String pageTitle, String detailsText) {
        if (rawContext == null || helper == null || market == null || listingId <= 0) return;
        try {
            MarketListingRecord listing = market.listing(listingId);
            GameRecord current = market.gameForListing(listing);
            if (listing == null || current == null || TextUtils.isEmpty(current.bggId) || TextUtils.isEmpty(current.name)) return;

            String title = norm(pageTitle);
            String details = norm(detailsText);
            if (title.isEmpty() && details.isEmpty()) return;
            if (details.length() > 2500) details = details.substring(0,2500);

            String currentNorm = norm(current.name);
            String currentSig = significantOnly(currentNorm);
            List<BggSearchClient.Game> candidates = localCandidates(rawContext.getApplicationContext(), current.name);
            List<BggSearchClient.Game> explicit = new ArrayList<>();

            for (BggSearchClient.Game g : candidates) {
                if (g == null || TextUtils.isEmpty(g.id) || TextUtils.isEmpty(g.name) || g.id.equals(current.bggId)) continue;
                String cn = norm(g.name);
                String cs = significantOnly(cn);
                if (tokenCount(cs) <= tokenCount(currentSig)) continue; // must truly be more specific
                if (!covers(cs, currentSig)) continue;                  // must still be the same title family
                boolean inTitle = phrase(title, cn);
                boolean inDetails = phrase(details, cn);
                if (!inTitle && !inDetails) continue;
                // Long unrelated titles can share a base word. Keep only compact extensions of the
                // current identity (Tokaido -> Tokaido Duo, not an arbitrary sentence containing it).
                if (tokenCount(cn) > tokenCount(currentNorm) + 4) continue;
                explicit.add(g);
            }

            if (!explicit.isEmpty()) {
                Collections.sort(explicit,new Comparator<BggSearchClient.Game>(){
                    @Override public int compare(BggSearchClient.Game a,BggSearchClient.Game b){
                        int ta=tokenCount(significantOnly(norm(a.name))),tb=tokenCount(significantOnly(norm(b.name)));
                        if(ta!=tb)return Integer.compare(tb,ta);
                        return Integer.compare(b.searchScore,a.searchScore);
                    }
                });
                BggSearchClient.Game best=explicit.get(0);
                int bestTokens=tokenCount(significantOnly(norm(best.name)));
                if(explicit.size()>1){
                    BggSearchClient.Game second=explicit.get(1);
                    int secondTokens=tokenCount(significantOnly(norm(second.name)));
                    if(secondTokens==bestTokens && !second.id.equals(best.id)){
                        String reason="Più varianti BGG esplicite nella pagina Vinted: "+best.name+" / "+second.name;
                        market.flagBggVariantReview(listingId,reason);
                        if(!TextUtils.isEmpty(signature))helper.flagBggVariantReview(signature,reason);
                        return;
                    }
                }
                String reason="Pagina Vinted: variante esplicita '"+best.name+"'";
                long target=market.reassignListingToBggVariant(listingId,best,98.0,reason);
                if(target>0 && !TextUtils.isEmpty(signature))helper.applyAutoBggVariantCorrection(signature,best,reason);
                return;
            }

            // If the page explicitly looks like a variant but the local catalogue could not resolve
            // it uniquely, keep the listing out of automatic price references rather than confirming
            // the base game by absence of evidence.
            if (mentionsVariantMarker(currentNorm,title,details)) {
                String reason="Possibile variante/edizione diversa indicata nella pagina Vinted";
                market.flagBggVariantReview(listingId,reason);
                if(!TextUtils.isEmpty(signature))helper.flagBggVariantReview(signature,reason);
                return;
            }

            // A real description was inspected and no more-specific local identity is present.
            // This is enough to clear the temporary pending flag created by batch linking.
            if (!details.isEmpty()) {
                String reason="pagina Vinted controllata; nessuna variante più specifica trovata";
                market.confirmBggVariant(listingId,reason);
                if(!TextUtils.isEmpty(signature))helper.confirmBggVariant(signature,reason);
            }
        } catch (Throwable t) {
            try { market.setDiagnosticState("bgg_variant_guard",4,"state=ERROR;build="+BUILD+";type="+t.getClass().getSimpleName()+";message="+safe(t.getMessage())); } catch(Throwable ignored) {}
        }
    }

    public static String summary(Context context){
        DealDatabase helper=new DealDatabase(context.getApplicationContext());
        try{
            MarketStore market=new MarketStore(context,helper);MarketStore.RuntimeStatus r=market.diagnosticState("bgg_variant_guard");
            int pending=0,review=0;
            try(android.database.Cursor c=helper.getReadableDatabase().rawQuery("SELECT match_state,COUNT(*) FROM market_listings WHERE match_state IN ('BGG_VARIANT_PENDING','BGG_VARIANT_REVIEW') GROUP BY match_state",null)){
                while(c.moveToNext()){if("BGG_VARIANT_PENDING".equals(c.getString(0)))pending=c.getInt(1);else if("BGG_VARIANT_REVIEW".equals(c.getString(0)))review=c.getInt(1);}
            }catch(Throwable ignored){}
            if(r.updatedAt<=0)return "build="+BUILD+", state=NOT_RUN, pending="+pending+", review="+review;
            return "ageMs="+Math.max(0L,System.currentTimeMillis()-r.updatedAt)+", "+r.detail+", pending="+pending+", review="+review;
        }finally{try{helper.close();}catch(Throwable ignored){}}
    }

    private static List<BggSearchClient.Game> localCandidates(Context context,String currentName){
        String key=norm(currentName);List<BggSearchClient.Game> cached=CACHE.get(key);if(cached!=null)return new ArrayList<>(cached);
        BggSearchClient client=new BggSearchClient(context);try{List<BggSearchClient.Game> out=client.localCandidates(currentName);CACHE.put(key,new ArrayList<>(out));return out;}finally{client.shutdown();}
    }

    private static boolean mentionsVariantMarker(String current,String title,String details){
        String all=" "+title+" "+details+" ";String[] markers={" duo "," junior "," legacy "," duel "," big box "," pocket "," mini "," travel "," viaggio "," espansione "," expansion "," erweiterung "," standalone "," sequel "};
        boolean marker=false;for(String m:markers)if(all.contains(m)){marker=true;break;}if(!marker)return false;
        String[] base=significantOnly(current).split(" +");for(String t:base)if(t.length()>=3 && all.contains(" "+t+" "))return true;return false;
    }

    private static boolean phrase(String haystack,String needle){return !TextUtils.isEmpty(haystack)&&!TextUtils.isEmpty(needle)&&(" "+haystack+" ").contains(" "+needle+" ");}
    private static boolean covers(String candidateSig,String currentSig){if(TextUtils.isEmpty(candidateSig)||TextUtils.isEmpty(currentSig))return false;Set<String>a=new HashSet<>(Arrays.asList(candidateSig.split(" +")));for(String x:currentSig.split(" +"))if(!a.contains(x))return false;return true;}
    private static int tokenCount(String n){return TextUtils.isEmpty(n)?0:n.split(" +").length;}
    private static String significantOnly(String normalized){if(TextUtils.isEmpty(normalized))return "";Set<String> filler=new HashSet<>(Arrays.asList("gioco","giochi","tavolo","societa","board","game","games","boardgame","tabletop","jeu","de","societe","plateau","brettspiel","juego","mesa","the","a","an","of","and","e","di","da","del","della","dei","delle","nuovo","nuova","usato","usata","edizione","edition","versione","version"));StringBuilder b=new StringBuilder();for(String x:normalized.split(" +")){if(x.length()<3||filler.contains(x))continue;if(b.length()>0)b.append(' ');b.append(x);}return b.toString();}
    private static String norm(String s){if(s==null)return"";String n=Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}+","");return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim().replaceAll("\\s+"," ");}
    private static String safe(String s){return s==null?"":s.replace('\n',' ').replace('\r',' ');}
}
