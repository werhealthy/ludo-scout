package it.vintedaffari.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import android.database.sqlite.SQLiteStatement;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;

/** Bounded, resumable materialization kept separate from the authoritative catalog DB. */
final class FirestoreSnapshotStore implements AutoCloseable {
    static final long MAX_BYTES = 32L * 1024L * 1024L;
    // Keep headroom for one bounded rollback-journal batch while the DB file is capped.
    static final long AUXILIARY_RESERVE_BYTES = 2L * 1024L * 1024L;
    static final long MAX_DATABASE_BYTES = MAX_BYTES - AUXILIARY_RESERVE_BYTES;
    static final int MAX_ROWS = 100_000;
    private static final int DELETE_BATCH_ROWS = 16;
    private static final String DB_NAME = "ludo_firestore_snapshot.db";
    private final SQLiteDatabase db;

    static final class Snapshot {
        final String id;
        final String state;
        final long count;
        final String contentHash;
        final long uploaded;
        final String previousId;
        final String previousCursor;
        final int protocolVersion;
        Snapshot(String id, String state, long count, String contentHash,
                 long uploaded, String previousId, String previousCursor, int protocolVersion) {
            this.id=id; this.state=state; this.count=count; this.contentHash=contentHash;
            this.uploaded=uploaded; this.previousId=previousId; this.previousCursor=previousCursor;
            this.protocolVersion=protocolVersion;
        }
    }

    private FirestoreSnapshotStore(Context context) {
        File file = new File(context.getNoBackupFilesDir(), DB_NAME);
        db = SQLiteDatabase.openOrCreateDatabase(file, null);
        long configuredMaximum = db.setMaximumSize(MAX_DATABASE_BYTES);
        if (configuredMaximum > MAX_DATABASE_BYTES) throw new IllegalStateException("snapshot_database_limit_unavailable");
        db.execSQL("CREATE TABLE IF NOT EXISTS snapshot_meta (singleton INTEGER PRIMARY KEY CHECK(singleton=1), snapshot_id TEXT NOT NULL, state TEXT NOT NULL, row_count INTEGER NOT NULL, content_hash TEXT NOT NULL, uploaded_count INTEGER NOT NULL, previous_id TEXT NOT NULL, previous_cursor TEXT NOT NULL)");
        boolean hasProtocolVersion = false;
        try (Cursor columns = db.rawQuery("PRAGMA table_info(snapshot_meta)", null)) {
            int nameColumn = columns.getColumnIndex("name");
            while (columns.moveToNext()) if ("protocol_version".equals(columns.getString(nameColumn))) hasProtocolVersion = true;
        }
        if (!hasProtocolVersion) db.execSQL("ALTER TABLE snapshot_meta ADD COLUMN protocol_version INTEGER NOT NULL DEFAULT 2");
        db.execSQL("CREATE TABLE IF NOT EXISTS snapshot_rows (local_id INTEGER PRIMARY KEY, payload TEXT NOT NULL, source_hash TEXT NOT NULL, uploaded INTEGER NOT NULL DEFAULT 0, upload_attempted INTEGER NOT NULL DEFAULT 0, cleaned INTEGER NOT NULL DEFAULT 0)");
        verifySidecarBounds();
    }

    static FirestoreSnapshotStore open(Context context) {
        return new FirestoreSnapshotStore(context.getApplicationContext());
    }

    Snapshot snapshot() {
        try (Cursor c = db.rawQuery("SELECT snapshot_id,state,row_count,content_hash,uploaded_count,previous_id,previous_cursor,protocol_version FROM snapshot_meta WHERE singleton=1", null)) {
            if (!c.moveToFirst()) return null;
            return new Snapshot(c.getString(0), c.getString(1), c.getLong(2), c.getString(3),
                    c.getLong(4), c.getString(5), c.getString(6), c.getInt(7));
        }
    }

    void begin(String id, String previousId) {
        deleteRowsInBatches();
        db.beginTransaction();
        try {
            db.delete("snapshot_meta", null, null);
            db.execSQL("INSERT INTO snapshot_meta(singleton,snapshot_id,state,row_count,content_hash,uploaded_count,previous_id,previous_cursor,protocol_version) VALUES(1,?,'MATERIALIZING',0,'',0,?,'',3)",
                    new Object[]{id, previousId == null ? "" : previousId});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        verifySidecarBounds();
    }

    void insertRow(long id, String payload, String hash) {
        verifySidecarBounds();
        db.execSQL("INSERT INTO snapshot_rows(local_id,payload,source_hash,uploaded,cleaned) VALUES(?,?,?,0,0)",
                new Object[]{id, payload, hash});
        verifySidecarBounds();
    }

    void finishMaterialization(long count, String hash) {
        if (count > MAX_ROWS) throw new IllegalStateException("snapshot_limit_exceeded");
        verifySidecarBounds();
        db.execSQL("UPDATE snapshot_meta SET state='STAGE_MANIFEST',row_count=?,content_hash=? WHERE singleton=1",
                new Object[]{count, hash});
        verifySidecarBounds();
    }

    Cursor nextRows(int limit) {
        return db.rawQuery("SELECT local_id,payload,source_hash FROM snapshot_rows WHERE uploaded=0 ORDER BY local_id LIMIT ?",
                new String[]{Integer.toString(limit)});
    }

    void markUploaded(long[] ids) {
        db.beginTransaction();
        try {
            SQLiteStatement statement=db.compileStatement("UPDATE snapshot_rows SET uploaded=1 WHERE local_id=?");
            for (long id:ids) { statement.bindLong(1,id); statement.executeUpdateDelete(); }
            statement.close();
            db.execSQL("UPDATE snapshot_meta SET uploaded_count=(SELECT COUNT(*) FROM snapshot_rows WHERE uploaded=1) WHERE singleton=1");
            verifySidecarBounds();
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    void markUploadAttempted(long[] ids) {
        db.beginTransaction();
        try {
            SQLiteStatement statement=db.compileStatement("UPDATE snapshot_rows SET upload_attempted=1 WHERE local_id=?");
            for(long id:ids){statement.bindLong(1,id);statement.executeUpdateDelete();}
            statement.close();
            verifySidecarBounds();
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        verifySidecarBounds();
    }

    void state(String state) {
        db.execSQL("UPDATE snapshot_meta SET state=? WHERE singleton=1", new Object[]{state});
    }

    void previousCursor(String cursor) {
        db.execSQL("UPDATE snapshot_meta SET previous_cursor=? WHERE singleton=1", new Object[]{cursor});
    }

    void setPreviousId(String id) {
        db.execSQL("UPDATE snapshot_meta SET previous_id=? WHERE singleton=1", new Object[]{id == null ? "" : id});
    }

    Cursor cleanupRows(String snapshotId, int limit) {
        return db.rawQuery("SELECT local_id FROM snapshot_rows WHERE upload_attempted=1 AND cleaned=0 ORDER BY local_id LIMIT ?",
                new String[]{Integer.toString(limit)});
    }

    void markCleaned(long[] ids) {
        db.beginTransaction();
        try {
            SQLiteStatement statement=db.compileStatement("UPDATE snapshot_rows SET cleaned=1 WHERE local_id=?");
            for(long id:ids){statement.bindLong(1,id);statement.executeUpdateDelete();}
            statement.close();
            verifySidecarBounds();
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    void clearAfterPublish(String publishedId, String contentHash) {
        deleteRowsInBatches();
        db.beginTransaction();
        try {
            db.delete("snapshot_meta",null,null);
            db.execSQL("INSERT INTO snapshot_meta(singleton,snapshot_id,state,row_count,content_hash,uploaded_count,previous_id,previous_cursor,protocol_version) VALUES(1,?,'PUBLISHED',0,?,0,'','',3)",
                    new Object[]{publishedId, contentHash});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        verifySidecarBounds();
    }

    void resetIdle() {
        deleteRowsInBatches();
        db.beginTransaction();
        try { db.delete("snapshot_meta",null,null); db.setTransactionSuccessful(); }
        finally { db.endTransaction(); }
        verifySidecarBounds();
    }

    void markCleanupPending(String oldId) {
        db.execSQL("UPDATE snapshot_meta SET state='CLEAN_OLD',previous_id=?,previous_cursor='' WHERE singleton=1",
                new Object[]{oldId == null ? "" : oldId});
    }

    void markAborted() { state("ABORTING"); }

    static String newId() { return UUID.randomUUID().toString(); }

    static long sidecarBytes(File databaseFile) {
        long total = 0;
        for (String suffix : new String[]{"", "-journal", "-wal", "-shm"}) {
            File part = new File(databaseFile.getPath() + suffix);
            if (part.isFile()) total += part.length();
        }
        return total;
    }

    private void verifySidecarBounds() {
        File databaseFile = new File(db.getPath());
        if (databaseFile.length() > MAX_DATABASE_BYTES || sidecarBytes(databaseFile) > MAX_BYTES) {
            throw new IllegalStateException("snapshot_limit_exceeded");
        }
    }

    private void deleteRowsInBatches() {
        SQLiteStatement delete = db.compileStatement(
                "DELETE FROM snapshot_rows WHERE local_id IN "
                        + "(SELECT local_id FROM snapshot_rows ORDER BY local_id LIMIT " + DELETE_BATCH_ROWS + ")");
        try {
            int removed;
            do {
                db.beginTransaction();
                try {
                    removed = delete.executeUpdateDelete();
                    verifySidecarBounds();
                    db.setTransactionSuccessful();
                } finally { db.endTransaction(); }
            } while (removed > 0);
        } finally { delete.close(); }
    }

    static String hex(byte[] bytes) {
        StringBuilder out=new StringBuilder(bytes.length*2);
        for(byte b:bytes) out.append(String.format(Locale.ROOT,"%02x",b&0xff));
        return out.toString();
    }

    static MessageDigest sha256() throws Exception { return MessageDigest.getInstance("SHA-256"); }
    static void updateRoot(MessageDigest digest,long id,String rowHash) {
        digest.update((Long.toString(id)+":"+rowHash+"\n").getBytes(StandardCharsets.UTF_8));
    }

    @Override public void close() { db.close(); }
}
