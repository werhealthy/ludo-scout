package it.vintedaffari.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Explicit local export only. Opens the existing archive without helper upgrades or writes.
 * Sections are individual read snapshots, not a transaction across sections. No remote clients.
 */
public final class ClassificationAuditExport {
    private ClassificationAuditExport() {}
    private static final String[][] SECTIONS = {
        {"listings", "SELECT id,temp_fingerprint,legacy_signature,vinted_item_id,game_id,vinted_title,brand,item_condition,current_price_cents,protected_price_cents,vinted_url,image_url,listing_photos_csv,published_label,language_code,observed_text,category_raw,category_normalized,category_source,category_confidence,lifecycle,enrichment_state,match_state,match_confidence,first_seen,last_seen,manual_review_required,manual_review_reason,last_error FROM market_listings ORDER BY id"},
        {"legacy_deals", "SELECT id,signature,vinted_title,brand,item_condition,listing_type,verification_state,verification_reason,analysis_status,bgg_id,game_name,language_code,vinted_item_id,vinted_url,image_url,listing_photos_csv,item_price_cents,match_reason,lifecycle,first_seen,last_seen FROM deals ORDER BY id"},
        {"observations", "SELECT id,signature,observed_at,vinted_title,brand,item_condition,listing_type,verification_state,verification_reason,analysis_status,bgg_id,game_name,language_code,match_reason FROM observations ORDER BY id"},
        {"games", "SELECT id,bgg_id,canonical_name,original_name,alternate_names,expansions,base_games,publishers,categories,match_state,match_confidence,filter_reason FROM games ORDER BY id"},
        {"aliases", "SELECT id,game_id,alias,normalized_alias,source FROM game_aliases ORDER BY id"},
        {"overrides", "SELECT signature,item_id,excluded,reason FROM listing_overrides ORDER BY signature"},
        {"browser_snapshots", "SELECT name,updated_at,text_value FROM queue_controls WHERE name GLOB 'browser_snapshot:*' ORDER BY name"}
    };
    private static final String[] SNAPSHOT_FIELDS = {
        "id","url","title","description","brand","condition","priceCents","currency",
        "protectedPriceCents","photos","language","category","publication","source"
    };

    public static File create(File database, File directory, String version) throws Exception {
        if (!database.isFile()) throw new IOException("Archivio locale assente");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cartella non disponibile");
        File partial = File.createTempFile("ludo-audit-", ".partial", directory);
        File complete = new File(directory, partial.getName().replace(".partial", ".zip"));
        boolean success = false;
        long started = System.currentTimeMillis();
        Map<String,Long> counts = new LinkedHashMap<>();
        try {
            try (SQLiteDatabase db = SQLiteDatabase.openDatabase(database.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
                 ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(partial))) {
                for (String[] section : SECTIONS) counts.put(section[0], writeSection(db, zip, section));
                JSONObject manifest = new JSONObject();
                manifest.put("format", "ludo-classification-audit-v1");
                manifest.put("app_version", version);
                manifest.put("started_at", started);
                manifest.put("completed_at", System.currentTimeMillis());
                manifest.put("read_only", true);
                manifest.put("network_requests", 0);
                manifest.put("ai_calls", 0);
                manifest.put("ai_cost_eur", 0);
                manifest.put("snapshot_atomic", false);
                manifest.put("consistency", "Each section is a separate read snapshot; collection may continue.");
                manifest.put("counts", new JSONObject(counts));
                manifest.put("count_units", "Rows per source, not unique announcements. Reconcile by item id/signature.");
                manifest.put("images", "Saved references only; availability and cached image bytes are not checked.");
                manifest.put("classification", "Existing listing_type in legacy_deals/observations; match_confidence is identity confidence, not calibrated type confidence.");
                zip.putNextEntry(new ZipEntry("manifest.json"));
                zip.write(manifest.toString(2).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            if (!partial.renameTo(complete)) throw new IOException("Finalizzazione non riuscita");
            success = true;
            return complete;
        } finally {
            if (!success) { partial.delete(); complete.delete(); }
        }
    }

    private static long writeSection(SQLiteDatabase db, ZipOutputStream zip, String[] section) throws Exception {
        long count = 0;
        zip.putNextEntry(new ZipEntry(section[0] + ".jsonl"));
        try (Cursor cursor = db.rawQuery(section[1], null)) {
            while (cursor.moveToNext()) {
                JSONObject row = new JSONObject();
                if ("browser_snapshots".equals(section[0])) {
                    row.put("snapshot_key", cursor.getString(0));
                    row.put("observed_at", cursor.getLong(1));
                    String raw = cursor.getString(2);
                    try {
                        JSONObject saved = new JSONObject(raw == null ? "" : raw);
                        JSONObject filtered = new JSONObject();
                        for (String field : SNAPSHOT_FIELDS) if (saved.has(field)) filtered.put(field, saved.get(field));
                        row.put("item", filtered);
                    } catch (org.json.JSONException invalid) {
                        row.put("parse_error", true);
                        row.put("needs_review", true);
                    }
                } else {
                    for (int i = 0; i < cursor.getColumnCount(); i++) {
                        Object value;
                        switch (cursor.getType(i)) {
                            case Cursor.FIELD_TYPE_NULL: value = JSONObject.NULL; break;
                            case Cursor.FIELD_TYPE_INTEGER: value = cursor.getLong(i); break;
                            case Cursor.FIELD_TYPE_FLOAT: value = cursor.getDouble(i); break;
                            default: value = cursor.getString(i);
                        }
                        row.put(cursor.getColumnName(i), value);
                    }
                }
                zip.write(row.toString().getBytes(StandardCharsets.UTF_8));
                zip.write('\n');
                count++;
            }
        }
        zip.closeEntry();
        return count;
    }
}
