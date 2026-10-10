package it.vintedaffari.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.AtomicFile;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Read-only SQLite projection; capped uploads happen separately from Motore/queue writers. */
public final class FirestoreSyncWorker extends Worker {
    private static final int MAX_PER_PASS = 80;
    private static final int MAX_PER_DAY = 2400;
    // All columns are already present in the canonical market_listings and games tables.
    static final String QUERY = "SELECT l.id,l.vinted_item_id,l.vinted_title,l.brand,l.item_condition,"
            + "l.current_price_cents,l.protected_price_cents,l.vinted_url,l.image_url,"
            + "l.listing_photos_csv,l.observed_text,l.category_normalized,l.category_source,"
            + "l.lifecycle,l.enrichment_state,l.match_state,g.bgg_id,l.language_code,"
            + "l.last_seen,l.manual_review_required FROM market_listings l "
            + "LEFT JOIN games g ON g.id=l.game_id ORDER BY l.id";

    public FirestoreSyncWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull @Override public Result doWork() {
        Context context = getApplicationContext();
        if (!FirestoreSync.enabled(context)) return Result.success();
        File database = context.getDatabasePath("vinted_affari.db");
        if (!database.isFile()) return Result.success();
        try {
            SharedPreferences prefs = FirestoreSync.progress(context);
            String device = deviceId(context);
            // An Android restore may bring old sent hashes but never the no-backup device identity.
            if (!device.equals(prefs.getString("device_id", ""))) {
                prefs.edit().clear().putBoolean("enabled", true).putString("device_id", device).commit();
            }
            int day = (int) (System.currentTimeMillis() / 86_400_000L);
            int spent = prefs.getInt("budget_day", -1) == day ? prefs.getInt("budget_writes", 0) : 0;
            if (spent >= MAX_PER_DAY) return Result.success();

            List<Row> pending = new ArrayList<>();
            try (SQLiteDatabase db = SQLiteDatabase.openDatabase(
                    database.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
                 Cursor cursor = db.rawQuery(QUERY, null)) {
                while (cursor.moveToNext() && !isStopped()
                        && pending.size() < Math.min(MAX_PER_PASS, MAX_PER_DAY - spent)) {
                    Row row = project(cursor);
                    if (!row.hash.equals(prefs.getString("sent_" + row.id, ""))) pending.add(row);
                }
            }
            if (pending.isEmpty() || isStopped()) return Result.success();
            FirebaseFirestore firestore = FirestoreSync.store(context);
            if (firestore == null || !FirestoreSync.enabled(context)) return Result.success();
            String owner = BuildConfig.LUDO_SYNC_OWNER_UID;
            WriteBatch batch = firestore.batch();
            for (Row row : pending) {
                DocumentReference doc = firestore.collection("sync_v1").document(owner)
                        .collection("devices").document(device)
                        .collection("listings").document(Long.toString(row.id));
                batch.set(doc, row.fields);
            }
            // A timeout never marks local progress. A later pass may safely repeat the same set.
            Tasks.await(batch.commit(), 35, TimeUnit.SECONDS);
            SharedPreferences.Editor saved = prefs.edit().putInt("budget_day", day)
                    .putInt("budget_writes", spent + pending.size());
            for (Row row : pending) saved.putString("sent_" + row.id, row.hash);
            if (!saved.commit()) return Result.retry();
            return Result.success();
        } catch (Exception failure) {
            // Do not log records, tokens, credentials or seller text.
            return Result.retry();
        }
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
        values.put("price_cents", c.isNull(5) ? null : c.getLong(5));
        values.put("protected_price_cents", c.isNull(6) ? null : c.getLong(6));
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

    private static String deviceId(Context context) throws Exception {
        AtomicFile file = new AtomicFile(new File(context.getNoBackupFilesDir(), "firestore-sync-device"));
        if (file.getBaseFile().exists()) {
            String saved = new String(file.readFully(), StandardCharsets.UTF_8).trim();
            if (saved.matches("[a-z0-9-]{36}")) return saved;
            throw new IllegalStateException("invalid device identity");
        }
        String created = UUID.randomUUID().toString();
        FileOutputStream out = null;
        try {
            out = file.startWrite();
            out.write(created.getBytes(StandardCharsets.UTF_8));
            file.finishWrite(out);
        } catch (Exception error) {
            if (out != null) file.failWrite(out);
            throw error;
        }
        return created;
    }
}
