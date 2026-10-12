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
    static final int MAX_ROWS = 100_000;
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
        Snapshot(String id, String state, long count, String contentHash,
                 long uploaded, String previousId, String previousCursor) {
            this.id=id; this.state=state; this.count=count; this.contentHash=contentHash;
            this.uploaded=uploaded; this.previousId=previousId; this.previousCursor=previousCursor;
        }
    }

    private FirestoreSnapshotStore(Context context) {
        File file = new File(context.getNoBackupFilesDir(), DB_NAME);
        db = SQLiteDatabase.openOrCreateDatabase(file, null);
        db.setMaximumSize(MAX_BYTES);
        db.execSQL("CREATE TABLE IF NOT EXISTS snapshot_meta (singleton INTEGER PRIMARY KEY CHECK(singleton=1), snapshot_id TEXT NOT NULL, state TEXT NOT NULL, row_count INTEGER NOT NULL, content_hash TEXT NOT NULL, uploaded_count INTEGER NOT NULL, previous_id TEXT NOT NULL, previous_cursor TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS snapshot_rows (local_id INTEGER PRIMARY KEY, payload TEXT NOT NULL, source_hash TEXT NOT NULL, uploaded INTEGER NOT NULL DEFAULT 0, cleaned INTEGER NOT NULL DEFAULT 0)");
    }

    static FirestoreSnapshotStore open(Context context) {
        return new FirestoreSnapshotStore(context.getApplicationContext());
    }

    Snapshot snapshot() {
        try (Cursor c = db.rawQuery("SELECT snapshot_id,state,row_count,content_hash,uploaded_count,previous_id,previous_cursor FROM snapshot_meta WHERE singleton=1", null)) {
            if (!c.moveToFirst()) return null;
            return new Snapshot(c.getString(0), c.getString(1), c.getLong(2), c.getString(3),
                    c.getLong(4), c.getString(5), c.getString(6));
        }
    }

    void begin(String id, String previousId) {
        db.beginTransaction();
        try {
            db.delete("snapshot_rows", null, null);
            db.delete("snapshot_meta", null, null);
            db.execSQL("INSERT INTO snapshot_meta VALUES(1,?,'MATERIALIZING',0,'',0,?,'')",
                    new Object[]{id, previousId == null ? "" : previousId});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    void insertRow(long id, String payload, String hash) {
        db.execSQL("INSERT INTO snapshot_rows(local_id,payload,source_hash,uploaded,cleaned) VALUES(?,?,?,0,0)",
                new Object[]{id, payload, hash});
    }

    void finishMaterialization(long count, String hash) {
        if (count > MAX_ROWS || new File(db.getPath()).length() > MAX_BYTES) throw new IllegalStateException("snapshot_limit_exceeded");
        db.execSQL("UPDATE snapshot_meta SET state='UPLOADING',row_count=?,content_hash=? WHERE singleton=1",
                new Object[]{count, hash});
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
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
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
        return db.rawQuery("SELECT local_id FROM snapshot_rows WHERE cleaned=0 ORDER BY local_id LIMIT ?",
                new String[]{Integer.toString(limit)});
    }

    void markCleaned(long[] ids) {
        db.beginTransaction();
        try {
            SQLiteStatement statement=db.compileStatement("UPDATE snapshot_rows SET cleaned=1 WHERE local_id=?");
            for(long id:ids){statement.bindLong(1,id);statement.executeUpdateDelete();}
            statement.close();
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    void clearAfterPublish(String publishedId, String contentHash) {
        db.beginTransaction();
        try {
            db.delete("snapshot_rows",null,null);
            db.delete("snapshot_meta",null,null);
            db.execSQL("INSERT INTO snapshot_meta VALUES(1,?,'PUBLISHED',0,?,0,'','')",
                    new Object[]{publishedId, contentHash});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    void resetIdle() {
        db.beginTransaction();
        try { db.delete("snapshot_rows",null,null); db.delete("snapshot_meta",null,null); db.setTransactionSuccessful(); }
        finally { db.endTransaction(); }
    }

    void markCleanupPending(String oldId) {
        db.execSQL("UPDATE snapshot_meta SET state='CLEAN_OLD',previous_id=?,previous_cursor='' WHERE singleton=1",
                new Object[]{oldId == null ? "" : oldId});
    }

    void markAborted() { state("ABORTING"); }

    static String newId() { return UUID.randomUUID().toString(); }

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
