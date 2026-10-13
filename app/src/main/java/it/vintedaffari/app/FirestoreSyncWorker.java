package it.vintedaffari.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.util.concurrent.TimeUnit;

/** Read-only SQLite projection; capped uploads happen separately from Motore/queue writers. */
public final class FirestoreSyncWorker extends Worker {
    private static final int MAX_PER_PASS = 80;
    private static final int MAX_PER_DAY = 2400;
    private final WorkerParameters workerParameters;
    // All columns are already present in the canonical market_listings and games tables.
    static final String QUERY = "SELECT l.id,l.vinted_item_id,l.vinted_title,l.brand,l.item_condition,"
            + "l.current_price_cents,l.protected_price_cents,l.vinted_url,l.image_url,"
            + "l.listing_photos_csv,l.observed_text,l.category_normalized,l.category_source,"
            + "l.lifecycle,l.enrichment_state,l.match_state,g.bgg_id,l.language_code,"
            + "l.last_seen,l.manual_review_required FROM market_listings l "
            + "LEFT JOIN games g ON g.id=l.game_id ORDER BY l.id";

    public FirestoreSyncWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
        workerParameters = parameters;
    }

    @NonNull @Override public Result doWork() {
        if (isStopped() || !FirestoreSync.enabled(getApplicationContext())) return Result.success();
        return new FirestoreSnapshotWorker(getApplicationContext(), workerParameters).doWork();
    }

    private static final class Row {
        final long id;
        final String hash;
        final Map<String, Object> fields;
        Row(long id, String hash, Map<String, Object> fields) {
            this.id = id; this.hash = hash; this.fields = fields;
        }
    }

    private static Row project(Cursor c) throws Exception {
        long id = c.getLong(0);
        if (id <= 0) throw new IllegalArgumentException("invalid local ID");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("schema_version", 1);
        values.put("source_id", id);
        values.put("item_id", string(c, 1, 80));
        values.put("title", string(c, 2, 400));
        values.put("brand", string(c, 3, 120));
        values.put("condition", string(c, 4, 80));
        values.put("price_cents", nonnegative(c, 5));
        values.put("protected_price_cents", nonnegative(c, 6));
        values.put("item_url", string(c, 7, 1200));
        values.put("photo_urls", photos(string(c, 9, 6000), string(c, 8, 1200)));
        values.put("observed_text", string(c, 10, 2000));
        values.put("category", string(c, 11, 80));
        values.put("category_source", string(c, 12, 80));
        values.put("lifecycle", string(c, 13, 60));
        values.put("enrichment_state", string(c, 14, 60));
        values.put("match_state", string(c, 15, 60));
        values.put("bgg_id", string(c, 16, 80));
        values.put("language", string(c, 17, 40));
        values.put("last_seen", c.isNull(18) ? 0L : c.getLong(18));
        values.put("manual_review", !c.isNull(19) && c.getInt(19) != 0);
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] digest = sha.digest(values.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder fingerprint = new StringBuilder();
        for (byte b : digest) fingerprint.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
        String hash = fingerprint.toString();
        values.put("source_hash", hash);
        values.put("synced_at", FieldValue.serverTimestamp());
        return new Row(id, hash, values);
    }

    private static Long nonnegative(Cursor c, int index) {
        return c.isNull(index) || c.getLong(index) < 0 ? null : c.getLong(index);
    }

    private static String string(Cursor c, int index, int max) {
        if (c.isNull(index)) return "";
        String raw = c.getString(index);
        if (raw == null) return "";
        String clean = raw.trim();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }

    private static List<String> photos(String csv, String primary) {
        List<String> urls = new ArrayList<>();
        if (!primary.isEmpty() && primary.startsWith("https://")) urls.add(primary);
        for (String candidate : csv.split(",")) {
            String url = candidate.trim();
            if (url.startsWith("https://") && url.length() <= 1200
                    && !urls.contains(url)) urls.add(url);
            if (urls.size() == 3) break;
        }
        return urls;
    }

}
