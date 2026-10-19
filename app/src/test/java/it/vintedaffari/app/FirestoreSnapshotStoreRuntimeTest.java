package it.vintedaffari.app;

import android.app.Application;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.File;
import static org.junit.Assert.*;

/** Exercises the real Android SQLite sidecar without installing an APK or contacting Firebase. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 34)
public final class FirestoreSnapshotStoreRuntimeTest {
    private Context context;
    private File sidecar;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        sidecar = new File(context.getNoBackupFilesDir(), "ludo_firestore_snapshot.db");
        deleteSidecar();
        FirestoreSync.progress(context).edit().remove("evidence_enabled")
                .remove("evidence_opt_in_version").remove("evidence_upload_mode")
                .remove("snapshot_error").commit();
    }

    @After public void tearDown() {
        deleteSidecar();
        FirestoreSync.progress(context).edit().remove("evidence_enabled")
                .remove("evidence_opt_in_version").remove("evidence_upload_mode")
                .remove("snapshot_error").commit();
    }

    @Test public void legacyPartialSnapshotSurvivesOpenAndIsClearedBeforeV5Generation() {
        seedLegacyV4Upload();
        try (FirestoreSnapshotStore store = FirestoreSnapshotStore.open(context)) {
            FirestoreSnapshotStore.Snapshot opened = store.snapshot();
            assertNotNull(opened);
            assertEquals("legacy-snapshot", opened.id);
            assertEquals("UPLOADING", opened.state);
            assertEquals(4, opened.protocolVersion);
            try (Cursor row = store.nextRows(10)) {
                assertTrue(row.moveToFirst());
                assertEquals(42L, row.getLong(0));
            }

            store.markAborted();
            assertEquals("ABORTING", store.snapshot().state);
            store.resetIdle(); // Worker calls this only after remote abort cleanup finishes.
            assertNull(store.snapshot());

            store.begin("fresh-v5-snapshot", "", false);
            FirestoreSnapshotStore.Snapshot fresh = store.snapshot();
            assertNotNull(fresh);
            assertEquals("fresh-v5-snapshot", fresh.id);
            assertEquals("MATERIALIZING", fresh.state);
            assertEquals(6, fresh.protocolVersion);
            assertFalse(fresh.includesEvidence);
        }
    }

    @Test public void crashAfterPointerWriteResumesCleanupOfOnlyExpiredThirdGeneration() {
        try (FirestoreSnapshotStore store = FirestoreSnapshotStore.open(context)) {
            // Before publish: A is current, B is the older rollback; C is fully staged.
            store.begin("generation-C", "generation-A", false, "generation-B");
            store.state("PUBLISH");

            // Simulate restart after Firestore accepted current=C, rollback=A,
            // but before the worker persisted CLEAN_OLD locally.
            store.markCleanupPending("generation-A");
            FirestoreSnapshotStore.Snapshot recovered = store.snapshot();
            assertEquals("CLEAN_OLD", recovered.state);
            assertEquals("generation-A", recovered.previousId);
            assertEquals("generation-B", FirestoreSnapshotWorker.expiredGenerationId(recovered));
        }
    }

    @Test public void revocationScrubsEvidenceRetainsAbortIdentityAndSkipsBatchSubmission() throws Exception {
        try (FirestoreSnapshotStore store = FirestoreSnapshotStore.open(context)) {
            store.begin("evidence-snapshot", "previous-snapshot", true);
            store.state("UPLOADING");
            String payload = "{\"schema_version\":3,\"snapshot_id\":\"evidence-snapshot\","
                    + "\"source_id\":42,\"title\":\"Game\",\"observed_text\":\"private description\","
                    + "\"photo_urls\":[\"https://images.vinted.net/a.jpg\"],\"source_hash\":\"old-hash\"}";
            store.insertRow(42L, payload, "old-hash");
            store.markUploadAttempted(new long[]{42L});
            FirestoreSnapshotStore.Snapshot beforeRevocation = store.snapshot();
            assertTrue(beforeRevocation.includesEvidence);

            store.prepareEvidenceRevocation();
            FirestoreSnapshotStore.Snapshot revoked = store.snapshot();
            assertNotNull(revoked);
            assertEquals("evidence-snapshot", revoked.id);
            assertEquals("ABORTING", revoked.state);
            assertFalse(revoked.evidenceEnabled);
            assertFalse(revoked.includesEvidence);

            try (Cursor row = store.nextRows(1)) {
                assertTrue(row.moveToFirst());
                JSONObject scrubbed = new JSONObject(row.getString(1));
                assertFalse(scrubbed.has("observed_text"));
                assertFalse(scrubbed.has("photo_urls"));
            }
            assertUploadAttemptedZero();

            final int[] submitted = {0};
            String task = FirestoreSnapshotWorker.commitIfEvidenceModeMatches(
                    true, false, beforeRevocation, () -> { submitted[0]++; return "submitted"; });
            assertNull(task);
            assertEquals("revocation must prevent batch submission", 0, submitted[0]);
        }

        try (FirestoreSnapshotStore reopened = FirestoreSnapshotStore.open(context)) {
            assertEquals("ABORTING", reopened.snapshot().state);
            try (Cursor row = reopened.nextRows(1)) {
                assertTrue(row.moveToFirst());
                String payload = row.getString(1);
                assertFalse(payload.contains("observed_text"));
                assertFalse(payload.contains("photo_urls"));
            }
        }
    }

    @Test public void revocationPreservesPublishCheckpointForPointerRaceReconciliation() {
        try (FirestoreSnapshotStore store = FirestoreSnapshotStore.open(context)) {
            store.begin("publishing-evidence-snapshot", "previous", true);
            store.state("PUBLISH");
            store.prepareEvidenceRevocation();
            FirestoreSnapshotStore.Snapshot revoked = store.snapshot();
            assertEquals("PUBLISH", revoked.state);
            assertFalse(revoked.evidenceEnabled);
        }
    }

    @Test public void revocationRemainsOffAndMarkedForRetryWhenLocalScrubFails() {
        android.content.SharedPreferences preferences = FirestoreSync.progress(context);
        preferences.edit().putBoolean("evidence_enabled", true)
                .putString("evidence_opt_in_version", FirestoreEvidenceConsentPolicy.CURRENT_VERSION)
                .putString("evidence_upload_mode", FirestoreEvidenceConsentPolicy.CURRENT_VERSION).commit();

        assertTrue(FirestoreSync.commitEvidenceConsent(preferences, false, false));

        assertFalse(FirestoreSync.evidenceConsented(context));
        assertTrue(FirestoreSync.evidenceCleanupPending(context));
        assertEquals("evidence_revoke_local_scrub_failed",
                preferences.getString("snapshot_error", ""));
    }

    @Test public void successfulRevocationMarksLocalEvidenceModeAsScrubbed() {
        android.content.SharedPreferences preferences = FirestoreSync.progress(context);
        preferences.edit().putBoolean("evidence_enabled", true)
                .putString("evidence_opt_in_version", FirestoreEvidenceConsentPolicy.CURRENT_VERSION)
                .putString("evidence_upload_mode", FirestoreEvidenceConsentPolicy.CURRENT_VERSION)
                .putString("snapshot_error", "evidence_revoke_local_scrub_failed").commit();

        assertTrue(FirestoreSync.commitEvidenceConsent(preferences, false, true));

        assertFalse(FirestoreSync.evidenceConsented(context));
        assertFalse(FirestoreSync.evidenceCleanupPending(context));
        assertEquals("", preferences.getString("snapshot_error", ""));
    }

    private void assertUploadAttemptedZero() {
        SQLiteDatabase db = SQLiteDatabase.openDatabase(sidecar.getPath(), null, SQLiteDatabase.OPEN_READONLY);
        try (Cursor row = db.rawQuery("SELECT upload_attempted FROM snapshot_rows WHERE local_id=42", null)) {
            assertTrue(row.moveToFirst());
            assertEquals(0, row.getInt(0));
        } finally { db.close(); }
    }

    private void seedLegacyV4Upload() {
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(sidecar, null);
        try {
            db.execSQL("CREATE TABLE snapshot_meta (singleton INTEGER PRIMARY KEY CHECK(singleton=1), snapshot_id TEXT NOT NULL, state TEXT NOT NULL, row_count INTEGER NOT NULL, content_hash TEXT NOT NULL, uploaded_count INTEGER NOT NULL, previous_id TEXT NOT NULL, previous_cursor TEXT NOT NULL, protocol_version INTEGER NOT NULL DEFAULT 2)");
            db.execSQL("INSERT INTO snapshot_meta VALUES(1,'legacy-snapshot','UPLOADING',1,'legacy-hash',0,'','',4)");
            db.execSQL("CREATE TABLE snapshot_rows (local_id INTEGER PRIMARY KEY, payload TEXT NOT NULL, source_hash TEXT NOT NULL, uploaded INTEGER NOT NULL DEFAULT 0, cleaned INTEGER NOT NULL DEFAULT 0)");
            db.execSQL("INSERT INTO snapshot_rows(local_id,payload,source_hash) VALUES(?,?,?)",
                    new Object[]{42L, "{\"title\":\"legacy row\"}", "legacy-hash"});
        } finally { db.close(); }
    }

    private void deleteSidecar() {
        for (String suffix : new String[]{"", "-journal", "-wal", "-shm"}) {
            File file = suffix.isEmpty() ? sidecar : new File(sidecar.getPath() + suffix);
            if (file.exists()) assertTrue("could not delete test database", file.delete());
        }
    }
}
