package it.vintedaffari.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * TEST 3 only: zero-network shadow analysis of the unresolved Vinted pool.
 *
 * It estimates how many *primary/canonical* catalogue lookups are structurally duplicated today.
 * It never claims jobs, never mutates listings and never performs HTTP. Fallback searches are not
 * counted as savings because those can be observation-specific after a true canonical-query miss.
 */
public final class VintedBatchReuseShadow {
    private VintedBatchReuseShadow() {}

    private static final class Family {
        String key;
        String label;
        int queued;
        int deferredReady;
        int deferredFuture;
        int urgent;
        int total() { return queued + deferredReady + deferredFuture; }
        int readyTotal() { return queued + deferredReady; }
    }

    private static final class Totals {
        int listings;
        int families;
        int multiFamilies;
        int reusableListings;
        int potentialSaved;
    }

    public static String summary(Context context) {
        DealDatabase helper = new DealDatabase(context.getApplicationContext());
        SQLiteDatabase db = null;
        try {
            db = helper.getReadableDatabase();
            long now = System.currentTimeMillis();
            LinkedHashMap<String,Family> families = new LinkedHashMap<>();

            // Actual core link jobs already materialised in the durable queue.
            String queuedSql =
                    "SELECT l.id,COALESCE(l.game_id,0),COALESCE(g.canonical_name,''),"+
                    "COALESCE(l.vinted_title,''),COALESCE(j.source,''),COALESCE(j.priority,0) "+
                    "FROM processing_jobs j JOIN market_listings l ON l.id=j.listing_id "+
                    "LEFT JOIN games g ON g.id=l.game_id "+
                    "WHERE j.job_type=? AND j.state IN (?,?,?) AND l.lifecycle='ACTIVE' "+
                    "AND (l.vinted_url IS NULL OR l.vinted_url='')";
            try (Cursor c = db.rawQuery(queuedSql, new String[]{
                    MarketStore.JOB_VINTED, MarketStore.PENDING, MarketStore.PROCESSING, MarketStore.FAILED_RETRYABLE})) {
                while (c.moveToNext()) {
                    long gameId = c.getLong(1);
                    String canonical = c.getString(2);
                    String title = c.getString(3);
                    String source = c.getString(4);
                    int priority = c.getInt(5);
                    Family f = family(families, gameId, canonical, title);
                    f.queued++;
                    if (priority >= 300 || "LIVE_DEAL".equals(source) || "HUNT_PRIORITY".equals(source) || "MANUAL_PRIORITY".equals(source)) f.urgent++;
                }
            }

            // Listings deliberately kept outside processing_jobs until the Vinted lane has room.
            // Rating/database_visibility mirrors promoteDeferredVintedBatch(). Future-due rows are
            // shown separately so the immediate estimate cannot pretend they are all ready now.
            String deferredSql =
                    "SELECT l.id,COALESCE(l.game_id,0),COALESCE(g.canonical_name,''),"+
                    "COALESCE(l.vinted_title,''),l.deferred_retry_at "+
                    "FROM market_listings l JOIN games g ON g.id=l.game_id "+
                    "WHERE l.lifecycle='ACTIVE' AND l.enrichment_state='DEFERRED_LINK' "+
                    "AND (l.vinted_url IS NULL OR l.vinted_url='') AND g.database_visible=1 AND g.rating>=?";
            try (Cursor c = db.rawQuery(deferredSql, new String[]{String.valueOf(DealPolicy.MIN_BGG_RATING)})) {
                while (c.moveToNext()) {
                    long gameId = c.getLong(1);
                    String canonical = c.getString(2);
                    String title = c.getString(3);
                    long due = c.getLong(4);
                    Family f = family(families, gameId, canonical, title);
                    if (due <= now) f.deferredReady++; else f.deferredFuture++;
                }
            }

            Totals queued = totals(families, 0);
            Totals ready = totals(families, 1);
            Totals all = totals(families, 2);

            List<Family> top = new ArrayList<>(families.values());
            Collections.sort(top, new Comparator<Family>() {
                @Override public int compare(Family a, Family b) {
                    int d = Integer.compare(b.readyTotal(), a.readyTotal());
                    if (d != 0) return d;
                    return a.label.compareToIgnoreCase(b.label);
                }
            });
            StringBuilder topText = new StringBuilder();
            int shown = 0;
            for (Family f : top) {
                if (f.readyTotal() < 2) continue;
                if (shown++ >= 5) break;
                if (topText.length() > 0) topText.append("; ");
                topText.append(cleanLabel(f.label)).append(":").append(f.readyTotal()).append("->1");
                if (f.queued > 0 && f.deferredReady > 0) topText.append("[q").append(f.queued).append("+d").append(f.deferredReady).append("]");
            }
            if (topText.length() == 0) topText.append("none");

            return "build=batch-reuse-shadow-v1"+
                    ", zeroNetwork=true"+
                    ", queued={listings="+queued.listings+", families="+queued.families+", multiFamilies="+queued.multiFamilies+", reusableListings="+queued.reusableListings+", primaryBefore="+queued.listings+", primaryAfter="+queued.families+", potentialSaved="+queued.potentialSaved+"}"+
                    ", readyPool={listings="+ready.listings+", families="+ready.families+", multiFamilies="+ready.multiFamilies+", reusableListings="+ready.reusableListings+", primaryBefore="+ready.listings+", primaryAfter="+ready.families+", potentialSaved="+ready.potentialSaved+", reusePct="+pct(ready.potentialSaved,ready.listings)+"}"+
                    ", deferredFuture="+(all.listings-ready.listings)+
                    ", allEligible={listings="+all.listings+", families="+all.families+", potentialSaved="+all.potentialSaved+"}"+
                    ", urgentQueued="+urgentCount(families)+
                    ", topReady="+topText+
                    ", note=primary-canonical-only;fallbacks-not-counted";
        } catch (Throwable t) {
            return "build=batch-reuse-shadow-v1, error="+t.getClass().getSimpleName()+":"+safe(t.getMessage());
        } finally {
            try { helper.close(); } catch (Throwable ignored) {}
        }
    }

    /** mode: 0 queued only, 1 queued+ready deferred, 2 all eligible deferred. */
    private static Totals totals(Map<String,Family> families, int mode) {
        Totals t = new Totals();
        for (Family f : families.values()) {
            int n = mode == 0 ? f.queued : (mode == 1 ? f.readyTotal() : f.total());
            if (n <= 0) continue;
            t.listings += n;
            t.families++;
            if (n >= 2) {
                t.multiFamilies++;
                t.reusableListings += n;
                t.potentialSaved += n - 1;
            }
        }
        return t;
    }

    private static int urgentCount(Map<String,Family> families) {
        int n=0; for (Family f : families.values()) n += f.urgent; return n;
    }

    private static Family family(Map<String,Family> out,long gameId,String canonical,String title) {
        String label = !TextUtils.isEmpty(canonical) ? canonical.trim() : title.trim();
        String key;
        if (gameId > 0) key = "g:"+gameId; // exact same canonical GameRecord => exact same first resolver query.
        else key = "t:"+norm(label);
        Family f = out.get(key);
        if (f == null) {
            f = new Family(); f.key=key; f.label=TextUtils.isEmpty(label)?"(senza titolo)":label; out.put(key,f);
        }
        return f;
    }

    private static String norm(String s) {
        if (s == null) return "";
        String x = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);
        return x.replaceAll("[^a-z0-9]+"," ").trim().replaceAll("\\s+"," ");
    }

    private static String cleanLabel(String s) {
        if (s == null) return "";
        String x=s.replace('\n',' ').replace('\r',' ').replace(';',',').trim();
        return x.length()>42?x.substring(0,39)+"...":x;
    }

    private static String pct(int a,int b) {
        if (b <= 0) return "0.0%";
        return String.format(Locale.US,"%.1f%%",100.0*a/b);
    }

    private static String safe(String s){return s==null?"":s.replace('\n',' ').replace('\r',' ');}
}
