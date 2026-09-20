package it.vintedaffari.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.ArrayList;
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
 * TEST 9: complete batch resolver simulation. Reads only SQLite snapshots and local listing data.
 * It performs zero HTTP and never writes links. The same candidate item id can be assigned at most
 * once. Conservative ties remain unresolved.
 */
public final class VintedBatchResolverShadow {
    private VintedBatchResolverShadow() {}
    private static final String BUILD="batch-resolver-independent-evidence-shadow-v4";

    private static final class Candidate {
        String id,title,brand; int exactPrice=Integer.MIN_VALUE;
        final Set<Integer> priceHints=new HashSet<>();
    }
    private static final class Observation {
        long listingId; String canonical,title,brand,signature,trueId; int price;
    }
    private static final class Edge {
        Observation o; Candidate c; double score,semantic; int priceDiff;
    }
    private static final class Family {
        String key,label; List<Candidate> candidates=new ArrayList<>(); List<Observation> observations=new ArrayList<>();
        int safe,ambiguous,noCandidate,collisions;
    }

    public static String summary(Context context){
        DealDatabase helper=new DealDatabase(context.getApplicationContext());
        try{
            SQLiteDatabase db=helper.getReadableDatabase();
            Map<String,List<Candidate>> snapshots=loadSnapshots(db);
            if(snapshots.isEmpty())return "build="+BUILD+", zeroNetwork=true, snapshots=0, waitingForSnapshots=true";
            Set<String> usedIds=loadUsedIds(db);
            LinkedHashMap<String,Family> families=new LinkedHashMap<>();
            int eligible=0,covered=0,usedExcluded=0;
            String q="SELECT l.id,COALESCE(g.canonical_name,''),COALESCE(l.vinted_title,''),l.current_price_cents,COALESCE(l.brand,''),COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) "+
                    "FROM market_listings l JOIN games g ON g.id=l.game_id WHERE l.lifecycle='ACTIVE' AND (l.vinted_url IS NULL OR l.vinted_url='') "+
                    "AND g.database_visible=1 AND g.rating>=?";
            try(Cursor c=db.rawQuery(q,new String[]{String.valueOf(DealPolicy.MIN_BGG_RATING)})){
                while(c.moveToNext()){
                    eligible++; Observation o=new Observation();o.listingId=c.getLong(0);o.canonical=c.getString(1);o.title=c.getString(2);o.price=c.getInt(3);o.brand=c.getString(4);o.signature=c.getString(5);
                    String key=norm(o.canonical);List<Candidate> raw=snapshots.get(key);if(raw==null)continue;covered++;
                    Family f=families.get(key);if(f==null){f=new Family();f.key=key;f.label=o.canonical;for(Candidate x:raw)if(!usedIds.contains(x.id))f.candidates.add(x);usedExcluded+=raw.size()-f.candidates.size();families.put(key,f);}f.observations.add(o);
                }
            }

            int safeAssignments=0,ambiguousObs=0,noCandidateObs=0,topCollisions=0,coveredCandidates=0;
            int candidateEdgesBeforeSafety=0,candidateEdgesAfterSafety=0,strongNonGameRejected=0,collisionRiskRejected=0,distinctiveCoverageRejected=0,extraContextRejected=0;
            StringBuilder blockedSamples=new StringBuilder();int blockedSampleN=0;
            StringBuilder samples=new StringBuilder();int sampleN=0;
            List<Family> ordered=new ArrayList<>(families.values());
            Collections.sort(ordered,new Comparator<Family>(){@Override public int compare(Family a,Family b){return Integer.compare(b.observations.size(),a.observations.size());}});
            for(Family f:ordered){
                coveredCandidates+=f.candidates.size();
                Map<Long,List<Edge>> edges=new HashMap<>(); Map<String,Integer> topClaims=new HashMap<>();
                for(Observation o:f.observations){
                    SafetyResult sr=compatibleWithSafety(o,f.candidates);List<Edge> es=sr.edges;edges.put(o.listingId,es);
                    candidateEdgesBeforeSafety+=sr.before;candidateEdgesAfterSafety+=es.size();strongNonGameRejected+=sr.strongNonGame;collisionRiskRejected+=sr.collisionRisk;distinctiveCoverageRejected+=sr.distinctiveCoverage;extraContextRejected+=sr.extraContext;
                    if(blockedSampleN<6&&!TextUtils.isEmpty(sr.sample)){if(blockedSamples.length()>0)blockedSamples.append(" | ");blockedSamples.append(sr.sample);blockedSampleN++;}
                    if(es.isEmpty()){f.noCandidate++;noCandidateObs++;continue;}
                    String top=es.get(0).c.id;topClaims.put(top,topClaims.containsKey(top)?topClaims.get(top)+1:1);
                }
                for(Integer n:topClaims.values())if(n!=null&&n>1){topCollisions+=n;f.collisions+=n;}

                Set<Long> assignedObs=new HashSet<>();Set<String> assignedIds=new HashSet<>();
                boolean progress=true;
                while(progress){
                    progress=false;
                    Map<String,List<Edge>> proposals=new HashMap<>();
                    for(Observation o:f.observations){
                        if(assignedObs.contains(o.listingId))continue;List<Edge> all=edges.get(o.listingId);if(all==null||all.isEmpty())continue;
                        List<Edge> avail=new ArrayList<>();for(Edge e:all)if(!assignedIds.contains(e.c.id))avail.add(e);if(avail.isEmpty())continue;
                        Edge best=avail.get(0);double second=avail.size()>1?avail.get(1).score:-1;
                        boolean strong=best.score>=130.0&&(avail.size()==1||best.score-second>=14.0);
                        if(!strong)continue;
                        List<Edge> ps=proposals.get(best.c.id);if(ps==null){ps=new ArrayList<>();proposals.put(best.c.id,ps);}ps.add(best);
                    }
                    for(Map.Entry<String,List<Edge>> pe:proposals.entrySet()){
                        List<Edge> ps=pe.getValue();Collections.sort(ps,new Comparator<Edge>(){@Override public int compare(Edge a,Edge b){return Double.compare(b.score,a.score);}});
                        Edge winner=ps.get(0);double rival=ps.size()>1?ps.get(1).score:-1;
                        if(ps.size()>1&&winner.score-rival<14.0)continue;
                        if(assignedObs.contains(winner.o.listingId)||assignedIds.contains(winner.c.id))continue;
                        assignedObs.add(winner.o.listingId);assignedIds.add(winner.c.id);f.safe++;safeAssignments++;progress=true;
                        if(sampleN<5){if(samples.length()>0)samples.append(" | ");samples.append(clean(winner.o.title,28)).append(" -> ").append(clean(winner.c.title,28)).append(" [score=").append(String.format(Locale.US,"%.0f",winner.score)).append(",priceΔ=").append(winner.priceDiff).append("]");sampleN++;}
                    }
                }
                for(Observation o:f.observations){if(assignedObs.contains(o.listingId))continue;List<Edge> es=edges.get(o.listingId);if(es!=null&&!es.isEmpty()){f.ambiguous++;ambiguousObs++;}}
            }

            // Retrospective correctness check. Only count rows whose known item id is actually present
            // in the current stored snapshot; an absent sold/stale item is not a matcher failure.
            int gtRows=0,gtPresent=0,gtCorrect=0,gtWrong=0,gtUnresolved=0;
            String gt="SELECT COALESCE(g.canonical_name,''),COALESCE(l.vinted_title,''),l.current_price_cents,COALESCE(l.brand,''),COALESCE(l.vinted_item_id,'') "+
                    "FROM market_listings l JOIN games g ON g.id=l.game_id WHERE l.lifecycle='ACTIVE' AND l.vinted_item_id IS NOT NULL AND l.vinted_item_id<>'' AND g.rating>=?";
            try(Cursor c=db.rawQuery(gt,new String[]{String.valueOf(DealPolicy.MIN_BGG_RATING)})){
                while(c.moveToNext()){
                    gtRows++;Observation o=new Observation();o.canonical=c.getString(0);o.title=c.getString(1);o.price=c.getInt(2);o.brand=c.getString(3);o.trueId=c.getString(4);
                    List<Candidate> cs=snapshots.get(norm(o.canonical));if(cs==null)continue;boolean present=false;for(Candidate x:cs)if(o.trueId.equals(x.id)){present=true;break;}if(!present)continue;gtPresent++;
                    List<Edge> es=compatibleWithSafety(o,cs).edges;if(es.isEmpty()){gtUnresolved++;continue;}Edge best=es.get(0);double second=es.size()>1?es.get(1).score:-1;boolean strong=best.score>=130.0&&(es.size()==1||best.score-second>=14.0);if(!strong){gtUnresolved++;continue;}if(o.trueId.equals(best.c.id))gtCorrect++;else gtWrong++;
                }
            }

            int familyCount=families.size(),multiFamilies=0,primaryBefore=0;for(Family f:families.values()){primaryBefore+=f.observations.size();if(f.observations.size()>1)multiFamilies++;}
            int saved=Math.max(0,primaryBefore-familyCount);
            StringBuilder top=new StringBuilder();int shown=0;for(Family f:ordered){if(f.observations.size()<2)continue;if(shown++>=5)break;if(top.length()>0)top.append("; ");top.append(clean(f.label,24)).append(":obs").append(f.observations.size()).append("/safe").append(f.safe).append("/amb").append(f.ambiguous).append("/coll").append(f.collisions);}
            if(top.length()==0)top.append("none");
            String precision=gtCorrect+gtWrong==0?"n/a":String.format(Locale.US,"%.1f%%",100.0*gtCorrect/(gtCorrect+gtWrong));
            return "build="+BUILD+
                    ", zeroNetwork=true, zeroWrites=true"+
                    ", eligible="+eligible+
                    ", covered="+covered+
                    ", families="+familyCount+
                    ", multiFamilies="+multiFamilies+
                    ", candidateRows="+coveredCandidates+
                    ", usedCandidateIdsExcluded="+usedExcluded+
                    ", individualPrimarySearches="+primaryBefore+
                    ", batchPrimarySearches="+familyCount+
                    ", projectedPrimarySaved="+saved+
                    ", projectedSavedPct="+pct(saved,primaryBefore)+
                    ", safeAssignments="+safeAssignments+
                    ", ambiguousAfterBatch="+ambiguousObs+
                    ", noCandidate="+noCandidateObs+
                    ", candidateEdgesBeforeSafety="+candidateEdgesBeforeSafety+
                    ", candidateEdgesAfterSafety="+candidateEdgesAfterSafety+
                    ", strongNonGameRejected="+strongNonGameRejected+
                    ", collisionRiskRejected="+collisionRiskRejected+
                    ", distinctiveCoverageRejected="+distinctiveCoverageRejected+
                    ", extraContextRejected="+extraContextRejected+
                    ", safetyBlockedPct="+pct(candidateEdgesBeforeSafety-candidateEdgesAfterSafety,candidateEdgesBeforeSafety)+
                    ", blockedSamples="+(blockedSamples.length()==0?"none":blockedSamples.toString())+
                    ", topCandidateCollisionClaims="+topCollisions+
                    ", gtLinkedRows="+gtRows+
                    ", gtIdPresentInSnapshot="+gtPresent+
                    ", gtCorrect="+gtCorrect+
                    ", gtWrong="+gtWrong+
                    ", gtUnresolved="+gtUnresolved+
                    ", gtPrecisionWhenCommitted="+precision+
                    ", samples="+samples+
                    ", top="+top;
        }catch(Throwable t){return "build="+BUILD+", error="+t.getClass().getSimpleName()+":"+safe(t.getMessage());}
        finally{try{helper.close();}catch(Throwable ignored){}}
    }

    private static Map<String,List<Candidate>> loadSnapshots(SQLiteDatabase db){
        Map<String,List<Candidate>> out=new HashMap<>();
        try(Cursor c=db.rawQuery("SELECT query_key,payload FROM vinted_shadow_snapshots_v3",null)){
            while(c.moveToNext()){List<Candidate> cs=decode(c.getString(1));if(!cs.isEmpty())out.put(c.getString(0),cs);}
        }catch(Throwable ignored){}
        return out;
    }
    private static Set<String> loadUsedIds(SQLiteDatabase db){
        Set<String> out=new HashSet<>();try(Cursor c=db.rawQuery("SELECT vinted_item_id FROM market_listings WHERE vinted_item_id IS NOT NULL AND vinted_item_id<>''",null)){while(c.moveToNext())out.add(c.getString(0));}catch(Throwable ignored){}return out;
    }
    private static List<Candidate> decode(String raw){
        List<Candidate> out=new ArrayList<>();if(TextUtils.isEmpty(raw))return out;try{JSONArray a=new JSONArray(raw);for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;Candidate c=new Candidate();c.id=o.optString("id","");c.title=o.optString("t","");c.brand=o.optString("b","");if(o.has("pc")){c.exactPrice=o.optInt("pc",Integer.MIN_VALUE);if(c.exactPrice!=Integer.MIN_VALUE)c.priceHints.add(c.exactPrice);}JSONArray ph=o.optJSONArray("ph");if(ph!=null)for(int k=0;k<ph.length();k++){int v=ph.optInt(k,Integer.MIN_VALUE);if(v!=Integer.MIN_VALUE)c.priceHints.add(v);}if(!TextUtils.isEmpty(c.id))out.add(c);}}catch(Throwable ignored){}return out;
    }
    private static final class SafetyResult {
        List<Edge> edges=new ArrayList<>(); int before,strongNonGame,collisionRisk,distinctiveCoverage,extraContext; String sample="";
    }

    private static SafetyResult compatibleWithSafety(Observation o,List<Candidate> cs){
        SafetyResult r=new SafetyResult();String nt=norm(o.title),nc=norm(o.canonical);
        for(Candidate c:cs){
            String nx=norm(c.title);double jo=jaccard(nt,nx),jc=jaccard(nc,nx),co=containment(nt,nx),cc=containment(nc,nx);
            boolean semantic=jo>=.62||jc>=.68||co>=.90||cc>=.90;if(!semantic)continue;
            int pd=bestPriceDiff(c,o.price);if(pd>5)continue;r.before++;
            int gate=candidateSafetyGate(o,c,jo,jc,co,cc);
            if(gate!=0){
                if(gate==1)r.strongNonGame++;else if(gate==2)r.collisionRisk++;else if(gate==3)r.distinctiveCoverage++;else r.extraContext++;
                if(TextUtils.isEmpty(r.sample)){String why=gate==1?"non-game":gate==2?"collision":gate==3?"distinctive":"extra-context";r.sample=clean(o.title,22)+" X "+clean(c.title,28)+" ["+why+"]";}
                continue;
            }
            double sem=Math.max(Math.max(jo,jc),Math.max(co,cc));double score=sem*100.0+(pd<=1?30.0:25.0);
            if(nt.equals(nx))score+=18.0;if(co>=.99)score+=10.0;if(jo>=.82)score+=10.0;if(jc>=.82)score+=5.0;
            if(!TextUtils.isEmpty(o.brand)&&!TextUtils.isEmpty(c.brand)&&jaccard(norm(o.brand),norm(c.brand))>=.8)score+=5.0;
            Edge e=new Edge();e.o=o;e.c=c;e.score=score;e.semantic=sem;e.priceDiff=pd;r.edges.add(e);
        }
        Collections.sort(r.edges,new Comparator<Edge>(){@Override public int compare(Edge a,Edge b){int x=Double.compare(b.score,a.score);if(x!=0)return x;return a.c.id.compareTo(b.c.id);}});return r;
    }

    /** Returns 0=allow, 1=strong non-game, 2=single-token/collision risk,
     *  3=missing distinctive game words, 4=too much unexplained candidate context. */
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

        // For multiword games, a shared generic word is never enough. Two-word games must
        // carry both distinctive words; longer names may miss at most a small fraction.
        if(canonicalSigTokens>=2){
            double need=canonicalSigTokens<=3?1.0:.80;
            if(canonicalSigCoverage+1e-9<need)return 3;
        }

        if((singleTokenCanonical||seededCollision) && !(explicitBoardCue||observedSecondSignal||brandSecondSignal||compactExact))return 2;

        // Even full canonical coverage can be accidental in collectibles/merch. If the candidate
        // adds several unexplained distinctive words, demand a genuinely independent signal.
        int unexplained=Math.max(0,candidateSigTokens-canonicalSigTokens);
        // Lexical similarity to the observed title is not independent evidence: it often repeats
        // the same game words (e.g. "Boardgame Carnival Zombie"). If a candidate adds multiple
        // unexplained distinctive tokens, require a truly independent signal from the candidate
        // itself (explicit board-game cue) or a matching brand. Otherwise leave it unresolved.
        if(canonicalSigTokens>=2 && unexplained>=2 && !explicitBoardCue && !brandSecondSignal)return 4;

        // Keep the older long-title guard as an additional conservative backstop.
        if(canonicalContain>=.99 && observedJ<.30 && observedContain<.60 && candidateTokens>=tokenCount(canonical)+5 && !explicitBoardCue)return 2;
        return 0;
    }

    private static boolean hasMarketplaceNonGameCue(String text){
        String n=" "+norm(text)+" ";
        String[] cues={" guanti "," glove "," gloves "," karate "," kimono "," judogi "," cintura karate "," scarpa "," scarpe "," sneaker "," sneakers "," giubbotto "," giacca "," maglia "," tshirt "," t shirt "," felpa "," pantaloni "," borsa "," zaino "," profumo "," cosmetico "," action figure "," figurine "," statuetta "," peluche "," manga "," romanzo "," libro "};
        for(String x:cues)if(n.contains(x))return true;return false;
    }

    private static boolean hasMultilingualBoardGameCue(String normalized){
        String n=" "+normalized+" ";
        String[] cues={" gioco da tavolo "," gioco di societa "," board game "," boardgame "," tabletop game "," jeu de societe "," jeu de plateau "," brettspiel "," gesellschaftsspiel "," juego de mesa "," jogo de tabuleiro "," bordspel "," bradspel "," expansion "," espansione "," erweiterung "};
        for(String x:cues)if(n.contains(x))return true;return false;
    }


    private static double tokenCoverage(String required,String candidate){
        if(TextUtils.isEmpty(required)||TextUtils.isEmpty(candidate))return 0;
        Set<String> a=new HashSet<>(java.util.Arrays.asList(required.split(" +")));
        Set<String> b=new HashSet<>(java.util.Arrays.asList(candidate.split(" +")));
        if(a.isEmpty())return 0;Set<String> i=new HashSet<>(a);i.retainAll(b);return (double)i.size()/a.size();
    }
    private static int tokenCount(String n){if(TextUtils.isEmpty(n))return 0;return n.split(" +").length;}
    private static String significantOnly(String normalized){
        if(TextUtils.isEmpty(normalized))return "";Set<String> filler=new HashSet<>(java.util.Arrays.asList("gioco","giochi","tavolo","societa","board","game","games","boardgame","tabletop","jeu","de","societe","plateau","brettspiel","juego","mesa","the","a","an","of","and","e","di","da","del","della","dei","delle","nuovo","nuova","usato","usata","edizione","edition","versione","version"));
        StringBuilder b=new StringBuilder();for(String x:normalized.split(" +")){if(x.length()<3||filler.contains(x))continue;if(b.length()>0)b.append(' ');b.append(x);}return b.toString();
    }

    private static int bestPriceDiff(Candidate c,int price){int best=Integer.MAX_VALUE;if(c.exactPrice!=Integer.MIN_VALUE)best=Math.min(best,Math.abs(c.exactPrice-price));for(Integer p:c.priceHints)if(p!=null)best=Math.min(best,Math.abs(p-price));return best;}
    private static String norm(String s){if(s==null)return"";String n=Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}+","");return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim().replaceAll("\\s+"," ");}
    private static double jaccard(String a,String b){if(a.isEmpty()||b.isEmpty())return 0;Set<String>A=new HashSet<>(java.util.Arrays.asList(a.split(" ")));Set<String>B=new HashSet<>(java.util.Arrays.asList(b.split(" ")));Set<String>I=new HashSet<>(A);I.retainAll(B);Set<String>U=new HashSet<>(A);U.addAll(B);return U.isEmpty()?0:(double)I.size()/U.size();}
    private static double containment(String a,String b){if(a.isEmpty()||b.isEmpty())return 0;Set<String>A=new HashSet<>(java.util.Arrays.asList(a.split(" ")));Set<String>B=new HashSet<>(java.util.Arrays.asList(b.split(" ")));Set<String>I=new HashSet<>(A);I.retainAll(B);int d=Math.min(A.size(),B.size());return d<=0?0:(double)I.size()/d;}
    private static String pct(int a,int b){return b<=0?"0.0%":String.format(Locale.US,"%.1f%%",100.0*a/b);}
    private static String clean(String s,int max){if(s==null)return"";String x=s.replace('\n',' ').replace('\r',' ').replace(';',',').replace('|','/').trim();return x.length()>max?x.substring(0,Math.max(1,max-3))+"...":x;}
    private static String safe(String s){return s==null?"":s.replace('\n',' ').replace('\r',' ');}
}
