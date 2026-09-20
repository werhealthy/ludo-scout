package it.vintedaffari.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * FINAL CONTROLLED TEST.
 *
 * Applies at most a tiny number of very-high-confidence matches from catalogue snapshots that
 * already exist in SQLite. This method performs zero HTTP. It is deliberately stricter than the
 * shadow matcher: exact observed price (<= 1 cent), score >= 160, >= 20 point margin, safety gate,
 * global one-to-one item assignment, and no LIVE/HUNT/MANUAL jobs.
 */
public final class VintedBatchControlledActivation {
    private VintedBatchControlledActivation() {}
    public static final String BUILD="batch-controlled-final-v1";
    private static final int MIN_SCORE=160;
    private static final int MIN_MARGIN=20;
    private static final long SNAPSHOT_MAX_AGE_MS=24L*60L*60_000L;

    private static final class Candidate {
        String id,title,brand,image; int exactPrice=Integer.MIN_VALUE;
        final Set<Integer> priceHints=new HashSet<>();
    }
    private static final class Snapshot { long lastAt; final List<Candidate> candidates=new ArrayList<>(); }
    private static final class Observation {
        long listingId,firstSeen; String canonical,title,brand,signature; int price;
    }
    private static final class Edge { Observation o; Candidate c; double score; int priceDiff; }
    private static final class Winner { Edge edge; String family; }

    public static String run(Context rawContext,int requestedMax){
        Context context=rawContext.getApplicationContext();
        int max=Math.max(1,Math.min(3,requestedMax));
        DealDatabase helper=new DealDatabase(context);MarketStore market=new MarketStore(context,helper);
        long started=System.currentTimeMillis();
        try{
            SQLiteDatabase db=helper.getWritableDatabase();
            Map<String,Snapshot> snapshots=loadSnapshots(db);
            if(snapshots.isEmpty())return store(market,"state=NO_SNAPSHOTS;build="+BUILD+";zeroNetwork=true;applied=0");
            Set<String> usedIds=loadUsedIds(db);
            LinkedHashMap<String,List<Observation>> families=new LinkedHashMap<>();
            int considered=0,covered=0,stale=0,priorityExcluded=0;
            String q="SELECT l.id,COALESCE(g.canonical_name,''),COALESCE(l.vinted_title,''),l.current_price_cents,COALESCE(l.brand,''),"+
                    "COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint),l.first_seen "+
                    "FROM market_listings l JOIN games g ON g.id=l.game_id "+
                    "WHERE l.lifecycle='ACTIVE' AND (l.vinted_url IS NULL OR l.vinted_url='') AND g.database_visible=1 AND g.rating>=? "+
                    "AND l.enrichment_state IN ('DEFERRED_LINK','PENDING_ENRICHMENT','FAILED_RETRYABLE') "+
                    "AND NOT EXISTS (SELECT 1 FROM processing_jobs j WHERE j.listing_id=l.id AND j.state IN ('PENDING','PROCESSING','FAILED_RETRYABLE') AND j.source IN ('LIVE_DEAL','HUNT_PRIORITY','MANUAL_PRIORITY'))";
            try(Cursor c=db.rawQuery(q,new String[]{String.valueOf(DealPolicy.MIN_BGG_RATING)})){
                while(c.moveToNext()){
                    considered++;Observation o=new Observation();o.listingId=c.getLong(0);o.canonical=c.getString(1);o.title=c.getString(2);o.price=c.getInt(3);o.brand=c.getString(4);o.signature=c.getString(5);o.firstSeen=c.getLong(6);
                    String key=norm(o.canonical);Snapshot s=snapshots.get(key);if(s==null)continue;
                    long now=System.currentTimeMillis();
                    // The snapshot must be recent and not clearly older than the observation it is used to resolve.
                    if(s.lastAt<=0||now-s.lastAt>SNAPSHOT_MAX_AGE_MS||s.lastAt+60_000L<o.firstSeen){stale++;continue;}
                    covered++;List<Observation> list=families.get(key);if(list==null){list=new ArrayList<>();families.put(key,list);}list.add(o);
                }
            }

            List<Winner> winners=new ArrayList<>();int edgeCount=0,notStrong=0,claimCollisions=0;
            for(Map.Entry<String,List<Observation>> fe:families.entrySet()){
                Snapshot snapshot=snapshots.get(fe.getKey());if(snapshot==null)continue;
                Map<String,List<Edge>> claims=new HashMap<>();
                for(Observation o:fe.getValue()){
                    List<Edge> edges=compatible(o,snapshot.candidates,usedIds);edgeCount+=edges.size();
                    if(edges.isEmpty()){notStrong++;continue;}
                    Edge best=edges.get(0);double second=edges.size()>1?edges.get(1).score:-1;
                    if(best.score<MIN_SCORE||(edges.size()>1&&best.score-second<MIN_MARGIN)){notStrong++;continue;}
                    List<Edge> list=claims.get(best.c.id);if(list==null){list=new ArrayList<>();claims.put(best.c.id,list);}list.add(best);
                }
                for(Map.Entry<String,List<Edge>> ce:claims.entrySet()){
                    List<Edge> list=ce.getValue();Collections.sort(list,new Comparator<Edge>(){@Override public int compare(Edge a,Edge b){return Double.compare(b.score,a.score);}});
                    if(list.size()>1){double rival=list.get(1).score;if(list.get(0).score-rival<MIN_MARGIN){claimCollisions+=list.size();continue;}}
                    Winner w=new Winner();w.edge=list.get(0);w.family=fe.getKey();winners.add(w);
                }
            }
            Collections.sort(winners,new Comparator<Winner>(){@Override public int compare(Winner a,Winner b){return Double.compare(b.edge.score,a.edge.score);}});

            int applied=0,applyFailed=0;StringBuilder appliedSamples=new StringBuilder();Set<String> newlyUsed=new HashSet<>();
            for(Winner w:winners){
                if(applied>=max)break;Edge e=w.edge;if(e==null||e.c==null||TextUtils.isEmpty(e.c.id)||newlyUsed.contains(e.c.id))continue;
                // Recheck immediately before write: a normal resolver may have linked it since the scan.
                MarketListingRecord live=market.listing(e.o.listingId);if(live==null||!TextUtils.isEmpty(live.url)||!TextUtils.isEmpty(live.vintedItemId))continue;
                if(itemAlreadyUsed(db,e.c.id,e.o.listingId))continue;
                String url="https://www.vinted.it/items/"+e.c.id;String sig=e.o.signature;
                long canonical=market.applyManualVintedLink(0,e.o.listingId,url,e.c.id,null,e.c.image);
                if(canonical<=0){applyFailed++;continue;}
                if(!TextUtils.isEmpty(e.c.title)){android.content.ContentValues mv=new android.content.ContentValues();mv.put("vinted_title",e.c.title);db.update("market_listings",mv,"id=?",new String[]{String.valueOf(canonical)});}
                // Mirror into the legacy deal table because the current UI still reads it in several places.
                if(!TextUtils.isEmpty(sig)){
                    helper.applyResolvedLink(sig,e.c.id,url,e.c.image,99,"Batch locale controllato · test finale",System.currentTimeMillis());
                    if(!TextUtils.isEmpty(e.c.title))helper.updateVintedTitle(sig,e.c.title);
                }
                newlyUsed.add(e.c.id);applied++;
                if(appliedSamples.length()>0)appliedSamples.append(" | ");
                appliedSamples.append(clean(e.o.title,30)).append(" -> ").append(clean(e.c.title,30)).append(" #").append(e.c.id).append(" score=").append(String.format(Locale.US,"%.0f",e.score));
            }
            String detail="state=DONE;build="+BUILD+";zeroNetwork=true;max="+max+";considered="+considered+";covered="+covered+";families="+families.size()+";stale="+stale+";priorityExcluded="+priorityExcluded+";candidateEdges="+edgeCount+";notStrong="+notStrong+";claimCollisions="+claimCollisions+";eligibleWinners="+winners.size()+";applied="+applied+";applyFailed="+applyFailed+";elapsedMs="+(System.currentTimeMillis()-started)+";matches="+appliedSamples;
            return store(market,detail);
        }catch(Throwable t){return store(market,"state=ERROR;build="+BUILD+";zeroNetwork=true;type="+t.getClass().getSimpleName()+";message="+safe(t.getMessage()));}
        finally{try{helper.close();}catch(Throwable ignored){}}
    }

    public static String summary(Context context){
        DealDatabase helper=new DealDatabase(context.getApplicationContext());try{MarketStore market=new MarketStore(context,helper);MarketStore.RuntimeStatus r=market.diagnosticState("batch_controlled_final");if(r.updatedAt<=0)return "build="+BUILD+", state=NOT_RUN";return "ageMs="+Math.max(0L,System.currentTimeMillis()-r.updatedAt)+", "+r.detail;}finally{try{helper.close();}catch(Throwable ignored){}}
    }

    private static String store(MarketStore market,String detail){try{market.setDiagnosticState("batch_controlled_final",1,detail);}catch(Throwable ignored){}return detail;}

    private static List<Edge> compatible(Observation o,List<Candidate> candidates,Set<String> usedIds){
        List<Edge> out=new ArrayList<>();String nt=norm(o.title),nc=norm(o.canonical);
        for(Candidate c:candidates){
            if(c==null||TextUtils.isEmpty(c.id)||usedIds.contains(c.id))continue;
            String nx=norm(c.title);double jo=jaccard(nt,nx),jc=jaccard(nc,nx),co=containment(nt,nx),cc=containment(nc,nx);
            if(!(jo>=.62||jc>=.68||co>=.90||cc>=.90))continue;
            int pd=bestPriceDiff(c,o.price);if(pd>1)continue; // exact displayed price for the controlled test
            if(candidateSafetyGate(o,c,jo,jc,co,cc)!=0)continue;
            double sem=Math.max(Math.max(jo,jc),Math.max(co,cc));double score=sem*100.0+30.0;
            if(nt.equals(nx))score+=18.0;if(co>=.99)score+=10.0;if(jo>=.82)score+=10.0;if(jc>=.82)score+=5.0;
            if(!TextUtils.isEmpty(o.brand)&&!TextUtils.isEmpty(c.brand)&&jaccard(norm(o.brand),norm(c.brand))>=.8)score+=5.0;
            Edge e=new Edge();e.o=o;e.c=c;e.score=score;e.priceDiff=pd;out.add(e);
        }
        Collections.sort(out,new Comparator<Edge>(){@Override public int compare(Edge a,Edge b){int x=Double.compare(b.score,a.score);if(x!=0)return x;return a.c.id.compareTo(b.c.id);}});return out;
    }

    /** Same independent-evidence safety policy that passed Test 12. */
    private static int candidateSafetyGate(Observation o,Candidate c,double observedJ,double canonicalJ,double observedContain,double canonicalContain){
        String title=c==null?"":c.title;
        if(BoardGameIntakeGate.isStrongNonGameText(title,title)||hasMarketplaceNonGameCue(title))return 1;
        String canonical=norm(o==null?"":o.canonical),observed=norm(o==null?"":o.title),candidate=norm(title);
        String canonicalSig=significantOnly(canonical), observedSig=significantOnly(observed), candidateSig=significantOnly(candidate);
        int canonicalTokens=tokenCount(canonical),candidateTokens=tokenCount(candidate);
        int canonicalSigTokens=tokenCount(canonicalSig), candidateSigTokens=tokenCount(candidateSig);
        boolean explicitBoardCue=hasMultilingualBoardGameCue(candidate);
        double canonicalSigCoverage=tokenCoverage(canonicalSig,candidateSig);
        double observedSigCoverage=tokenCoverage(observedSig,candidateSig);
        boolean brandSecondSignal=!TextUtils.isEmpty(o==null?"":o.brand)&&!TextUtils.isEmpty(c==null?"":c.brand)&&jaccard(norm(o.brand),norm(c.brand))>=.80;
        boolean observedSecondSignal=observedJ>=.50 || (observedSigCoverage>=.80 && tokenCount(observedSig)>=2);
        boolean compactExact=canonicalContain>=.99 && candidateTokens<=Math.max(3,canonicalTokens+2);
        boolean singleTokenCanonical=canonicalSigTokens<=1;
        boolean seededCollision=BoardGameIntakeGate.seededCollisionTitle(o==null?"":o.canonical);
        if(canonicalSigTokens>=2){double need=canonicalSigTokens<=3?1.0:.80;if(canonicalSigCoverage+1e-9<need)return 3;}
        if((singleTokenCanonical||seededCollision) && !(explicitBoardCue||observedSecondSignal||brandSecondSignal||compactExact))return 2;
        int unexplained=Math.max(0,candidateSigTokens-canonicalSigTokens);
        if(canonicalSigTokens>=2 && unexplained>=2 && !explicitBoardCue && !brandSecondSignal)return 4;
        if(canonicalContain>=.99 && observedJ<.30 && observedContain<.60 && candidateTokens>=tokenCount(canonical)+5 && !explicitBoardCue)return 2;
        return 0;
    }

    private static Map<String,Snapshot> loadSnapshots(SQLiteDatabase db){
        Map<String,Snapshot> out=new HashMap<>();try(Cursor c=db.rawQuery("SELECT query_key,last_at,payload FROM vinted_shadow_snapshots_v3",null)){
            while(c.moveToNext()){Snapshot s=new Snapshot();s.lastAt=c.getLong(1);String raw=c.getString(2);try{JSONArray a=new JSONArray(raw);for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;Candidate k=new Candidate();k.id=x.optString("id","");k.title=x.optString("t","");k.brand=x.optString("b","");k.image=x.optString("img","");k.exactPrice=x.has("pc")?x.optInt("pc",Integer.MIN_VALUE):Integer.MIN_VALUE;if(k.exactPrice!=Integer.MIN_VALUE)k.priceHints.add(k.exactPrice);JSONArray ph=x.optJSONArray("ph");if(ph!=null)for(int j=0;j<ph.length();j++){int v=ph.optInt(j,Integer.MIN_VALUE);if(v!=Integer.MIN_VALUE)k.priceHints.add(v);}if(!TextUtils.isEmpty(k.id))s.candidates.add(k);}}catch(Throwable ignored){}out.put(c.getString(0),s);}
        }catch(Throwable ignored){}return out;
    }
    private static Set<String> loadUsedIds(SQLiteDatabase db){Set<String>s=new HashSet<>();try(Cursor c=db.rawQuery("SELECT vinted_item_id FROM market_listings WHERE vinted_item_id IS NOT NULL AND vinted_item_id<>''",null)){while(c.moveToNext())s.add(c.getString(0));}return s;}
    private static boolean itemAlreadyUsed(SQLiteDatabase db,String id,long listingId){try(Cursor c=db.rawQuery("SELECT 1 FROM market_listings WHERE vinted_item_id=? AND id<>? LIMIT 1",new String[]{id,String.valueOf(listingId)})){return c.moveToFirst();}}
    private static int bestPriceDiff(Candidate c,int price){int best=Integer.MAX_VALUE;if(c.exactPrice!=Integer.MIN_VALUE)best=Math.min(best,Math.abs(c.exactPrice-price));for(Integer p:c.priceHints)if(p!=null)best=Math.min(best,Math.abs(p-price));return best;}
    private static boolean hasMarketplaceNonGameCue(String text){String n=" "+norm(text)+" ";String[] cues={" guanti "," glove "," gloves "," karate "," kimono "," judogi "," cintura karate "," scarpa "," scarpe "," sneaker "," sneakers "," giubbotto "," giacca "," maglia "," tshirt "," t shirt "," felpa "," pantaloni "," borsa "," zaino "," profumo "," cosmetico "," action figure "," figurine "," statuetta "," peluche "," manga "," romanzo "," libro "};for(String x:cues)if(n.contains(x))return true;return false;}
    private static boolean hasMultilingualBoardGameCue(String normalized){String n=" "+normalized+" ";String[] cues={" gioco da tavolo "," gioco di societa "," board game "," boardgame "," tabletop game "," jeu de societe "," jeu de plateau "," brettspiel "," gesellschaftsspiel "," juego de mesa "," jogo de tabuleiro "," bordspel "," bradspel "," expansion "," espansione "," erweiterung "};for(String x:cues)if(n.contains(x))return true;return false;}
    private static double tokenCoverage(String required,String candidate){if(TextUtils.isEmpty(required)||TextUtils.isEmpty(candidate))return 0;Set<String>a=new HashSet<>(Arrays.asList(required.split(" +")));Set<String>b=new HashSet<>(Arrays.asList(candidate.split(" +")));if(a.isEmpty())return 0;Set<String>i=new HashSet<>(a);i.retainAll(b);return (double)i.size()/a.size();}
    private static int tokenCount(String n){if(TextUtils.isEmpty(n))return 0;return n.split(" +").length;}
    private static String significantOnly(String normalized){if(TextUtils.isEmpty(normalized))return "";Set<String> filler=new HashSet<>(Arrays.asList("gioco","giochi","tavolo","societa","board","game","games","boardgame","tabletop","jeu","de","societe","plateau","brettspiel","juego","mesa","the","a","an","of","and","e","di","da","del","della","dei","delle","nuovo","nuova","usato","usata","edizione","edition","versione","version"));StringBuilder b=new StringBuilder();for(String x:normalized.split(" +")){if(x.length()<3||filler.contains(x))continue;if(b.length()>0)b.append(' ');b.append(x);}return b.toString();}
    private static String norm(String s){if(s==null)return"";String n=Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}+","");return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim().replaceAll("\\s+"," ");}
    private static double jaccard(String a,String b){if(a.isEmpty()||b.isEmpty())return 0;Set<String>A=new HashSet<>(Arrays.asList(a.split(" ")));Set<String>B=new HashSet<>(Arrays.asList(b.split(" ")));Set<String>I=new HashSet<>(A);I.retainAll(B);Set<String>U=new HashSet<>(A);U.addAll(B);return U.isEmpty()?0:(double)I.size()/U.size();}
    private static double containment(String a,String b){if(a.isEmpty()||b.isEmpty())return 0;Set<String>A=new HashSet<>(Arrays.asList(a.split(" ")));Set<String>B=new HashSet<>(Arrays.asList(b.split(" ")));Set<String>I=new HashSet<>(A);I.retainAll(B);int den=Math.min(A.size(),B.size());return den<=0?0:(double)I.size()/den;}
    private static String clean(String s,int max){if(s==null)return"";String x=s.replace('\n',' ').replace('\r',' ').replace(';',',').replace('|','/').trim();return x.length()>max?x.substring(0,Math.max(1,max-3))+"...":x;}
    private static String safe(String s){return s==null?"":s.replace('\n',' ').replace('\r',' ');}
}
