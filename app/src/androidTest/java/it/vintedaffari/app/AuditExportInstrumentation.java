package it.vintedaffari.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;

/** Android SQLite/ZIP behavior; independent fixture, no Vinted or AI clients. */
public final class AuditExportInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        File dir = new File(getTargetContext().getCacheDir(), "audit-fixture-" + System.nanoTime());
        dir.mkdirs();
        File database = new File(dir, "fixture.db");
        try {
            try (SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(database, null)) {
                String[][] sections = sections();
                for (String[] section : sections) {
                    String sql = section[1];
                    String names = sql.substring(7, sql.indexOf(" FROM "));
                    String table = sql.substring(sql.indexOf(" FROM ") + 6).split(" ")[0];
                    String[] fields = names.split(",");
                    StringBuilder create = new StringBuilder("CREATE TABLE ").append(table).append("(");
                    for (int i = 0; i < fields.length; i++) {
                        if (i > 0) create.append(",");
                        create.append(fields[i]).append(fields[i].equals("id") || fields[i].equals("updated_at") ? " INTEGER" : " TEXT");
                    }
                    db.execSQL(create.append(")").toString());
                }
                db.execSQL("INSERT INTO market_listings(id,vinted_title,lifecycle) VALUES(1,?,'ACTIVE')", new Object[]{"Gioco café\nSolo carte?"});
                db.execSQL("INSERT INTO market_listings(id,lifecycle) VALUES(2,'RESET_LEGACY')");
                db.execSQL("INSERT INTO observations(id,signature,listing_type) VALUES(1,'repeat','BUNDLE')");
                db.execSQL("INSERT INTO observations(id,signature,listing_type) VALUES(2,'repeat','BUNDLE')");
                db.execSQL("INSERT INTO queue_controls(name,updated_at,text_value) VALUES('browser_snapshot:123',1,?)",
                        new Object[]{"{\"id\":\"123\",\"title\":\"Senza prezzo\",\"seller\":{\"name\":\"Private seller\"},\"photos\":[\"https://example.test/box.jpg\"]}"});
                db.execSQL("INSERT INTO queue_controls(name,updated_at,text_value) VALUES('browser_snapshot:124',2,'invalid')");
                db.execSQL("INSERT INTO queue_controls(name,text_value) VALUES('secret-control','NEVER EXPORT')");
            }
            File archive = ClassificationAuditExport.create(database, dir, "fixture");
            try (ZipFile zip = new ZipFile(archive)) {
                JSONObject manifest = new JSONObject(read(zip, "manifest.json"));
                check(manifest.getBoolean("read_only"), "manifest not read-only");
                check(!manifest.getBoolean("snapshot_atomic"), "incorrect atomicity claim");
                check(manifest.getInt("ai_calls") == 0 && manifest.getInt("network_requests") == 0, "remote work");
                check(manifest.getJSONObject("counts").getInt("listings") == 2, "historical listing lost");
                check(manifest.getJSONObject("counts").getInt("observations") == 2, "observations deduplicated");
                String[] listings = read(zip, "listings.jsonl").trim().split("\n");
                JSONObject first = new JSONObject(listings[0]);
                check(first.getLong("id") == 1, "integer corrupted");
                check(first.getString("vinted_title").equals("Gioco café\nSolo carte?"), "text corrupted");
                check(first.isNull("observed_text"), "null invented");
                String snapshots = read(zip, "browser_snapshots.jsonl");
                check(!snapshots.contains("Private seller") && !snapshots.contains("NEVER EXPORT"), "unrelated data leaked");
                check(new JSONObject(snapshots.trim().split("\n")[1]).getBoolean("parse_error"), "invalid snapshot hidden");
            }
            try (SQLiteDatabase ro = SQLiteDatabase.openDatabase(database.getPath(), null, SQLiteDatabase.OPEN_READONLY);
                 android.database.Cursor c = ro.rawQuery("SELECT COUNT(*) FROM market_listings", null)) {
                check(c.moveToFirst() && c.getInt(0) == 2, "archive mutated");
            }
            try (SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(database, null)) { db.execSQL("DROP TABLE games"); }
            boolean failed = false;
            try { ClassificationAuditExport.create(database, dir, "fixture"); }
            catch (Exception expected) { failed = true; }
            check(failed, "missing section silently accepted");
            for (File f : dir.listFiles()) check(!f.getName().endsWith(".partial"), "partial export retained");
            result.putString("result", "Audit export Android SQLite and ZIP passed");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            android.util.Log.e("LudoAudit", "FAIL", failure);
            result.putString("error", failure.toString());
            finish(Activity.RESULT_CANCELED, result);
        } finally {
            for (File f : dir.listFiles()) f.delete();
            dir.delete();
        }
    }
    private String[][] sections() throws Exception {
        java.lang.reflect.Field field = ClassificationAuditExport.class.getDeclaredField("SECTIONS");
        field.setAccessible(true);
        return (String[][]) field.get(null);
    }
    private String read(ZipFile zip, String name) throws Exception {
        try (java.io.InputStream input = zip.getInputStream(zip.getEntry(name));
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int n;
            while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
