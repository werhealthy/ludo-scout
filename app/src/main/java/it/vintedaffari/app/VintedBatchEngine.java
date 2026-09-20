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
 * Production zero-network Vinted batch linker.
 *
 * It consumes catalogue snapshots already captured by the normal public-page resolver and applies
 * only the exact same conservative policy that passed the final shadow/controlled tests:
 *   - BGG rating gate already passed;
 *   - exact observed price (<= 1 cent);
 *   - strong semantic score and wide margin;
 *   - non-game / collision / unexplained-context safety gate;
 *   - one Vinted item id can belong to only one listing;
 *   - LIVE/HUNT/MANUAL work always preempts backlog batching.
 *
 * This class performs no HTTP. Ambiguous rows are left untouched for the existing resolver.
 */
public final class VintedBatchEngine {
    private VintedBatchEngine() {}

    public static final String BUILD = "batch-engine-v2-run-first";
    private static final int MIN_SCORE = 160;
    private static final int MIN_MARGIN = 20;
    private static final long SNAPSHOT_MAX_AGE_MS = 24L * 60L * 60_000L;
    private static final long PASS_THROTTLE_MS = 20_000L;
    private static final int SCAN_LIMIT = 180;

    private static final class Candidate {
        String id, title, brand, image;
        int exactPrice = Integer.MIN_VALUE;
        final Set<Integer> priceHints = new HashSet<>();
    }

    private static final class Snapshot {
        long lastAt;
        final List<Candidate> candidates = new ArrayList<>();
    }

    private static final class Observation {
        long listingId, firstSeen;
        String canonical, title, brand, signature;
        int price;
    }

    private static final class Edge {
        Observation o;
        Candidate c;
        double score;
        int priceDiff;
    }

    private static final class Winner {
        Edge edge;
        String family;
    }

    /** Opportunistic pass used before a network claim. Returns the number of links applied. */
    public static int applyCached(Context rawContext, DealDatabase helper, MarketStore market, int requestedMax, boolean force) {
        if (rawContext == null || helper == null || market == null) return 0;
        Context context = rawContext.getApplicationContext();
        long started = System.currentTimeMillis();
        int max = Math.max(1, Math.min(8, requestedMax));

        try {
            // This pass is zero-network and scoped to the active scroll. Running it first cannot
            // steal a Vinted permit from Live/Hunt/manual work; it can only remove future network work.
            MarketStore.RuntimeStatus prev = market.diagnosticState("batch_engine");
            boolean previousScan=prev.detail!=null&&(prev.detail.contains("state=DONE")||prev.detail.contains("state=NO_SNAPSHOTS"));
            if (!force && previousScan && prev.updatedAt > 0 && started - prev.updatedAt < PASS_THROTTLE_MS) return 0;

            SQLiteDatabase db = helper.getWritableDatabase();
            ensureSnapshotTable(db);
            DealDatabase.ObservationSession activeRun=helper.activeObservationSession();
            if(activeRun==null){store(market,"state=IDLE;build="+BUILD+";zeroNetwork=true;reason=no-active-run");return 0;}
            Set<String> usedIds = loadUsedIds(db);
            LinkedHashMap<String, List<Observation>> families = new LinkedHashMap<>();
            int considered = 0, covered = 0, stale = 0, snapshotsFound = 0, coveredFamilies = 0;

            String q = "SELECT l.id,COALESCE(g.canonical_name,''),COALESCE(l.vinted_title,''),l.current_price_cents," +
                    "COALESCE(l.brand,''),COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint),l.first_seen " +
                    "FROM market_listings l JOIN games g ON g.id=l.game_id " +
                    "WHERE l.lifecycle='ACTIVE' AND (l.vinted_url IS NULL OR l.vinted_url='') " +
                    "AND g.database_visible=1 AND g.rating>=? " +
                    "AND l.enrichment_state IN ('DEFERRED_LINK','PENDING_ENRICHMENT','FAILED_RETRYABLE') " +
                    "AND NOT EXISTS (SELECT 1 FROM processing_jobs j WHERE j.listing_id=l.id " +
                    "AND j.state IN ('PENDING','PROCESSING','FAILED_RETRYABLE') " +
                    "AND j.source IN ('LIVE_DEAL','HUNT_PRIORITY','MANUAL_PRIORITY')) " +
                    "AND COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) IN (SELECT signature FROM observations WHERE observed_at>=? AND observed_at<=?) " +
                    "ORDER BY l.first_seen ASC LIMIT " + SCAN_LIMIT;

            try (Cursor c = db.rawQuery(q, new String[]{String.valueOf(DealPolicy.MIN_BGG_RATING),String.valueOf(activeRun.startAt),String.valueOf(activeRun.endAt)})) {
                while (c.moveToNext()) {
                    considered++;
                    Observation o = new Observation();
                    o.listingId = c.getLong(0);
                    o.canonical = c.getString(1);
                    o.title = c.getString(2);
                    o.price = c.getInt(3);
                    o.brand = c.getString(4);
                    o.signature = c.getString(5);
                    o.firstSeen = c.getLong(6);
                    String key = norm(o.canonical);
                    if(TextUtils.isEmpty(key))continue;
                    List<Observation> list = families.get(key);
                    if (list == null) {
                        list = new ArrayList<>();
                        families.put(key, list);
                    }
                    list.add(o);
                }
            }

            List<Winner> winners = new ArrayList<>();
            int edgeCount = 0, notStrong = 0, claimCollisions = 0;
            for (Map.Entry<String, List<Observation>> fe : families.entrySet()) {
                // Parse one family's compact payload at a time. On a mature backlog this prevents
                // hundreds of catalogue pages from becoming tens of thousands of Java objects at once.
                Snapshot snapshot = loadSnapshot(db,fe.getKey(),started);
                if (snapshot == null) continue;
                snapshotsFound++;
                List<Observation> familyCovered=new ArrayList<>();
                for(Observation o:fe.getValue()){
                    if(snapshot.lastAt<=0||started-snapshot.lastAt>SNAPSHOT_MAX_AGE_MS||snapshot.lastAt+60_000L<o.firstSeen){stale++;continue;}
                    familyCovered.add(o);covered++;
                }
                if(familyCovered.isEmpty())continue;
                coveredFamilies++;
                Map<String, List<Edge>> claims = new HashMap<>();
                for (Observation o : familyCovered) {
                    List<Edge> edges = compatible(o, snapshot.candidates, usedIds);
                    edgeCount += edges.size();
                    if (edges.isEmpty()) {
                        notStrong++;
                        continue;
                    }
                    Edge best = edges.get(0);
                    double second = edges.size() > 1 ? edges.get(1).score : -1;
                    if (best.score < MIN_SCORE || (edges.size() > 1 && best.score - second < MIN_MARGIN)) {
                        notStrong++;
                        continue;
                    }
                    List<Edge> list = claims.get(best.c.id);
                    if (list == null) {
                        list = new ArrayList<>();
                        claims.put(best.c.id, list);
                    }
                    list.add(best);
                }
                for (Map.Entry<String, List<Edge>> ce : claims.entrySet()) {
                    List<Edge> list = ce.getValue();
                    Collections.sort(list, new Comparator<Edge>() {
                        @Override public int compare(Edge a, Edge b) { return Double.compare(b.score, a.score); }
                    });
                    if (list.size() > 1) {
                        double rival = list.get(1).score;
                        if (list.get(0).score - rival < MIN_MARGIN) {
                            claimCollisions += list.size();
                            continue;
                        }
                    }
                    Winner w = new Winner();
                    w.edge = list.get(0);
                    w.family = fe.getKey();
                    winners.add(w);
                }
            }
            Collections.sort(winners, new Comparator<Winner>() {
                @Override public int compare(Winner a, Winner b) { return Double.compare(b.edge.score, a.edge.score); }
            });

            int applied = 0, applyFailed = 0, deepQueued = 0;
            Set<String> newlyUsed = new HashSet<>();
            StringBuilder samples = new StringBuilder();
            for (Winner w : winners) {
                if (applied >= max) break;
                // Keep the whole local pass atomic with respect to its active run. It performs no HTTP.
                Edge e = w.edge;
                if (e == null || e.c == null || TextUtils.isEmpty(e.c.id) || newlyUsed.contains(e.c.id)) continue;
                MarketListingRecord live = market.listing(e.o.listingId);
                if (live == null || !TextUtils.isEmpty(live.url) || !TextUtils.isEmpty(live.vintedItemId)) continue;
                if (itemAlreadyUsed(db, e.c.id, e.o.listingId)) continue;

                String url = "https://www.vinted.it/items/" + e.c.id;
                long canonical = market.applyTrustedVintedLink(0, e.o.listingId, url, e.c.id, null, e.c.image);
                if (canonical <= 0) {
                    applyFailed++;
                    continue;
                }

                if (!TextUtils.isEmpty(e.c.title)) {
                    android.content.ContentValues mv = new android.content.ContentValues();
                    mv.put("vinted_title", e.c.title);
                    db.update("market_listings", mv, "id=?", new String[]{String.valueOf(canonical)});
                }

                // A batch can prove the Vinted item identity, but it cannot prove a BGG edition/variant
                // from a short catalogue slug alone. Keep that distinction explicit until the existing
                // low-priority item-page metadata phase inspects richer public text.
                String variantPendingReason="Batch Vinted verificato; variante BGG da confermare su pagina articolo";
                market.markBggVariantPending(canonical, variantPendingReason);

                if (!TextUtils.isEmpty(e.o.signature)) {
                    helper.applyResolvedLink(e.o.signature, e.c.id, url, e.c.image, 99,
                            "Batch locale automatico · snapshot condivisa", System.currentTimeMillis());
                    helper.markBggVariantPending(e.o.signature,variantPendingReason);
                    if (!TextUtils.isEmpty(e.c.title)) helper.updateVintedTitle(e.o.signature, e.c.title);
                }

                // Publication date remains a required completeness field. This is deliberately a
                // low-priority item-page request and never blocks fresh identity work.
                market.enqueueDeepMetadata(canonical);
                deepQueued++;
                newlyUsed.add(e.c.id);
                applied++;

                if (samples.length() > 0) samples.append(" | ");
                samples.append(clean(e.o.title, 28)).append(" -> ")
                        .append(clean(e.c.title, 28)).append(" #").append(e.c.id)
                        .append(" score=").append(String.format(Locale.US, "%.0f", e.score));
            }

            String state=snapshotsFound==0?"NO_SNAPSHOTS":"DONE";
            String detail = "state="+state+";build=" + BUILD + ";zeroNetwork=true;run="+activeRun.startAt+"-"+activeRun.endAt+";considered=" + considered +
                    ";covered=" + covered + ";eligibleFamilies=" + families.size() + ";coveredFamilies="+coveredFamilies+
                    ";snapshots="+snapshotsFound+";stale=" + stale +
                    ";candidateEdges=" + edgeCount + ";notStrong=" + notStrong +
                    ";claimCollisions=" + claimCollisions + ";eligibleWinners=" + winners.size() +
                    ";applied=" + applied + ";applyFailed=" + applyFailed + ";deepQueued=" + deepQueued +
                    ";elapsedMs=" + (System.currentTimeMillis() - started) + ";matches=" + samples;
            store(market, detail);
            return applied;
        } catch (Throwable t) {
            store(market, "state=ERROR;build=" + BUILD + ";zeroNetwork=true;type=" +
                    t.getClass().getSimpleName() + ";message=" + safe(t.getMessage()));
            return 0;
        }
    }

    public static String summary(Context context) {
        DealDatabase helper = new DealDatabase(context.getApplicationContext());
        try {
            MarketStore market = new MarketStore(context, helper);
            MarketStore.RuntimeStatus r = market.diagnosticState("batch_engine");
            if (r.updatedAt <= 0) return "build=" + BUILD + ", state=NOT_RUN";
            return "ageMs=" + Math.max(0L, System.currentTimeMillis() - r.updatedAt) + ", " + r.detail;
        } finally {
            try { helper.close(); } catch (Throwable ignored) {}
        }
    }

    private static void store(MarketStore market, String detail) {
        try { market.setDiagnosticState("batch_engine", 1, detail); } catch (Throwable ignored) {}
    }

    private static List<Edge> compatible(Observation o, List<Candidate> candidates, Set<String> usedIds) {
        List<Edge> out = new ArrayList<>();
        String nt = norm(o.title), nc = norm(o.canonical);
        for (Candidate c : candidates) {
            if (c == null || TextUtils.isEmpty(c.id) || usedIds.contains(c.id)) continue;
            String nx = norm(c.title);
            double jo = jaccard(nt, nx), jc = jaccard(nc, nx), co = containment(nt, nx), cc = containment(nc, nx);
            if (!(jo >= .62 || jc >= .68 || co >= .90 || cc >= .90)) continue;
            int pd = bestPriceDiff(c, o.price);
            if (pd > 1) continue;
            if (candidateSafetyGate(o, c, jo, jc, co, cc) != 0) continue;
            double sem = Math.max(Math.max(jo, jc), Math.max(co, cc));
            double score = sem * 100.0 + 30.0;
            if (nt.equals(nx)) score += 18.0;
            if (co >= .99) score += 10.0;
            if (jo >= .82) score += 10.0;
            if (jc >= .82) score += 5.0;
            if (!TextUtils.isEmpty(o.brand) && !TextUtils.isEmpty(c.brand) && jaccard(norm(o.brand), norm(c.brand)) >= .8) score += 5.0;
            Edge e = new Edge();
            e.o = o; e.c = c; e.score = score; e.priceDiff = pd;
            out.add(e);
        }
        Collections.sort(out, new Comparator<Edge>() {
            @Override public int compare(Edge a, Edge b) {
                int x = Double.compare(b.score, a.score);
                if (x != 0) return x;
                return a.c.id.compareTo(b.c.id);
            }
        });
        return out;
    }

    /** Same independent-evidence gate that passed Test 12. */
    private static int candidateSafetyGate(Observation o, Candidate c, double observedJ, double canonicalJ,
                                           double observedContain, double canonicalContain) {
        String title = c == null ? "" : c.title;
        if (BoardGameIntakeGate.isStrongNonGameText(title, title) || hasMarketplaceNonGameCue(title)) return 1;
        String canonical = norm(o == null ? "" : o.canonical), observed = norm(o == null ? "" : o.title), candidate = norm(title);
        String canonicalSig = significantOnly(canonical), observedSig = significantOnly(observed), candidateSig = significantOnly(candidate);
        int canonicalTokens = tokenCount(canonical), candidateTokens = tokenCount(candidate);
        int canonicalSigTokens = tokenCount(canonicalSig), candidateSigTokens = tokenCount(candidateSig);
        boolean explicitBoardCue = hasMultilingualBoardGameCue(candidate);
        double canonicalSigCoverage = tokenCoverage(canonicalSig, candidateSig);
        double observedSigCoverage = tokenCoverage(observedSig, candidateSig);
        boolean brandSecondSignal = !TextUtils.isEmpty(o == null ? "" : o.brand) && !TextUtils.isEmpty(c == null ? "" : c.brand)
                && jaccard(norm(o.brand), norm(c.brand)) >= .80;
        boolean observedSecondSignal = observedJ >= .50 || (observedSigCoverage >= .80 && tokenCount(observedSig) >= 2);
        boolean compactExact = canonicalContain >= .99 && candidateTokens <= Math.max(3, canonicalTokens + 2);
        boolean singleTokenCanonical = canonicalSigTokens <= 1;
        boolean seededCollision = BoardGameIntakeGate.seededCollisionTitle(o == null ? "" : o.canonical);

        if (canonicalSigTokens >= 2) {
            double need = canonicalSigTokens <= 3 ? 1.0 : .80;
            if (canonicalSigCoverage + 1e-9 < need) return 3;
        }
        if ((singleTokenCanonical || seededCollision) && !(explicitBoardCue || observedSecondSignal || brandSecondSignal || compactExact)) return 2;

        int unexplained = Math.max(0, candidateSigTokens - canonicalSigTokens);
        if (canonicalSigTokens >= 2 && unexplained >= 2 && !explicitBoardCue && !brandSecondSignal) return 4;
        if (canonicalContain >= .99 && observedJ < .30 && observedContain < .60 &&
                candidateTokens >= tokenCount(canonical) + 5 && !explicitBoardCue) return 2;
        return 0;
    }

    private static void ensureSnapshotTable(SQLiteDatabase db){
        if(db==null)return;
        db.execSQL("CREATE TABLE IF NOT EXISTS vinted_shadow_snapshots_v3(query_key TEXT PRIMARY KEY,query_text TEXT NOT NULL,first_at INTEGER NOT NULL,last_at INTEGER NOT NULL,capture_count INTEGER NOT NULL DEFAULT 1,candidate_count INTEGER NOT NULL DEFAULT 0,structured_count INTEGER NOT NULL DEFAULT 0,link_count INTEGER NOT NULL DEFAULT 0,payload TEXT NOT NULL)");
    }

    private static Snapshot loadSnapshot(SQLiteDatabase db,String key,long now) {
        if(db==null||TextUtils.isEmpty(key))return null;
        try {
            try(Cursor c=db.rawQuery("SELECT last_at,payload FROM vinted_shadow_snapshots_v3 WHERE query_key=? AND last_at>=? LIMIT 1",new String[]{key,String.valueOf(now-SNAPSHOT_MAX_AGE_MS)})){
                if(!c.moveToFirst())return null;
                Snapshot s=new Snapshot();s.lastAt=c.getLong(0);String raw=c.getString(1);
                try{
                    JSONArray a=new JSONArray(raw);
                    for(int i=0;i<a.length();i++){
                        JSONObject x=a.optJSONObject(i);if(x==null)continue;Candidate k=new Candidate();
                        k.id=x.optString("id","");k.title=x.optString("t","");k.brand=x.optString("b","");k.image=x.optString("img","");
                        k.exactPrice=x.has("pc")?x.optInt("pc",Integer.MIN_VALUE):Integer.MIN_VALUE;if(k.exactPrice!=Integer.MIN_VALUE)k.priceHints.add(k.exactPrice);
                        JSONArray ph=x.optJSONArray("ph");if(ph!=null)for(int j=0;j<ph.length();j++){int v=ph.optInt(j,Integer.MIN_VALUE);if(v!=Integer.MIN_VALUE)k.priceHints.add(v);}
                        if(!TextUtils.isEmpty(k.id))s.candidates.add(k);
                    }
                }catch(Throwable ignored){}
                return s.candidates.isEmpty()?null:s;
            }
        }catch(Throwable ignored){return null;}
    }

    private static Set<String> loadUsedIds(SQLiteDatabase db) {
        Set<String> s = new HashSet<>();
        try (Cursor c = db.rawQuery("SELECT vinted_item_id FROM market_listings WHERE vinted_item_id IS NOT NULL AND vinted_item_id<>''", null)) {
            while (c.moveToNext()) s.add(c.getString(0));
        }
        return s;
    }

    private static boolean itemAlreadyUsed(SQLiteDatabase db, String id, long listingId) {
        try (Cursor c = db.rawQuery("SELECT 1 FROM market_listings WHERE vinted_item_id=? AND id<>? LIMIT 1",
                new String[]{id, String.valueOf(listingId)})) {
            return c.moveToFirst();
        }
    }

    private static int bestPriceDiff(Candidate c, int price) {
        int best = Integer.MAX_VALUE;
        if (c.exactPrice != Integer.MIN_VALUE) best = Math.min(best, Math.abs(c.exactPrice - price));
        for (Integer p : c.priceHints) if (p != null) best = Math.min(best, Math.abs(p - price));
        return best;
    }

    private static boolean hasMarketplaceNonGameCue(String text) {
        String n = " " + norm(text) + " ";
        String[] cues = {" guanti ", " glove ", " gloves ", " karate ", " kimono ", " judogi ", " cintura karate ",
                " scarpa ", " scarpe ", " sneaker ", " sneakers ", " giubbotto ", " giacca ", " maglia ",
                " tshirt ", " t shirt ", " felpa ", " pantaloni ", " borsa ", " zaino ", " profumo ",
                " cosmetico ", " action figure ", " figurine ", " statuetta ", " peluche ", " manga ", " romanzo ", " libro "};
        for (String x : cues) if (n.contains(x)) return true;
        return false;
    }

    private static boolean hasMultilingualBoardGameCue(String normalized) {
        String n = " " + normalized + " ";
        String[] cues = {" gioco da tavolo ", " gioco di societa ", " board game ", " boardgame ", " tabletop game ",
                " jeu de societe ", " jeu de plateau ", " brettspiel ", " gesellschaftsspiel ", " juego de mesa ",
                " jogo de tabuleiro ", " bordspel ", " bradspel ", " expansion ", " espansione ", " erweiterung "};
        for (String x : cues) if (n.contains(x)) return true;
        return false;
    }

    private static double tokenCoverage(String required, String candidate) {
        if (TextUtils.isEmpty(required) || TextUtils.isEmpty(candidate)) return 0;
        Set<String> a = new HashSet<>(Arrays.asList(required.split(" +")));
        Set<String> b = new HashSet<>(Arrays.asList(candidate.split(" +")));
        if (a.isEmpty()) return 0;
        Set<String> i = new HashSet<>(a); i.retainAll(b);
        return (double) i.size() / a.size();
    }

    private static int tokenCount(String n) { return TextUtils.isEmpty(n) ? 0 : n.split(" +").length; }

    private static String significantOnly(String normalized) {
        if (TextUtils.isEmpty(normalized)) return "";
        Set<String> filler = new HashSet<>(Arrays.asList("gioco", "giochi", "tavolo", "societa", "board", "game", "games",
                "boardgame", "tabletop", "jeu", "de", "societe", "plateau", "brettspiel", "juego", "mesa", "the", "a", "an",
                "of", "and", "e", "di", "da", "del", "della", "dei", "delle", "nuovo", "nuova", "usato", "usata",
                "edizione", "edition", "versione", "version"));
        StringBuilder b = new StringBuilder();
        for (String x : normalized.split(" +")) {
            if (x.length() < 3 || filler.contains(x)) continue;
            if (b.length() > 0) b.append(' ');
            b.append(x);
        }
        return b.toString();
    }

    private static String norm(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private static double jaccard(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> A = new HashSet<>(Arrays.asList(a.split(" "))), B = new HashSet<>(Arrays.asList(b.split(" ")));
        Set<String> I = new HashSet<>(A); I.retainAll(B);
        Set<String> U = new HashSet<>(A); U.addAll(B);
        return U.isEmpty() ? 0 : (double) I.size() / U.size();
    }

    private static double containment(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> A = new HashSet<>(Arrays.asList(a.split(" "))), B = new HashSet<>(Arrays.asList(b.split(" ")));
        Set<String> I = new HashSet<>(A); I.retainAll(B);
        int den = Math.min(A.size(), B.size());
        return den <= 0 ? 0 : (double) I.size() / den;
    }

    private static String clean(String s, int max) {
        if (s == null) return "";
        String x = s.replace('\n', ' ').replace('\r', ' ').replace(';', ',').replace('|', '/').trim();
        return x.length() > max ? x.substring(0, Math.max(1, max - 3)) + "..." : x;
    }

    private static String safe(String s) { return s == null ? "" : s.replace('\n', ' ').replace('\r', ' '); }
}
