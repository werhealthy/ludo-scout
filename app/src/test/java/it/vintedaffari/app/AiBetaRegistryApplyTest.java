package it.vintedaffari.app;

import android.app.Application;
import android.database.sqlite.SQLiteDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 34)
public final class AiBetaRegistryApplyTest {
    private SQLiteDatabase db;

    @Before public void setUp() {
        db = SQLiteDatabase.create(null);
        db.execSQL("CREATE TABLE games(id INTEGER PRIMARY KEY,bgg_id TEXT)");
        db.execSQL("CREATE TABLE market_listings(" +
                "id INTEGER PRIMARY KEY,vinted_item_id TEXT UNIQUE,vinted_title TEXT,brand TEXT," +
                "category_normalized TEXT,category_source TEXT,language_code TEXT,lifecycle TEXT," +
                "enrichment_state TEXT,match_state TEXT,observed_text TEXT,listing_photos_csv TEXT," +
                "image_url TEXT,game_id INTEGER,manual_review_required INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("INSERT INTO games(id,bgg_id) VALUES(1,'BGG-1')");
        db.execSQL("INSERT INTO market_listings(id,vinted_item_id,vinted_title,brand,category_normalized," +
                "category_source,language_code,lifecycle,enrichment_state,match_state,observed_text," +
                "listing_photos_csv,image_url,game_id,manual_review_required) VALUES(" +
                "1,'vinted-current-1','Board game listing','Brand','BASE_GAME','RULES','EN','ACTIVE'," +
                "'COMPLETE','MATCHED','observed copy','https://www.vinted.com/photo.jpg'," +
                "'https://www.vinted.com/cover.jpg',1,0)");
        db.execSQL("INSERT INTO market_listings(id,vinted_item_id,vinted_title,brand,category_normalized," +
                "category_source,language_code,lifecycle,enrichment_state,match_state,observed_text," +
                "listing_photos_csv,image_url,game_id,manual_review_required) VALUES(" +
                "2,'vinted-current-2','Unaffected listing','Other','EXPANSION','RULES','FR','ACTIVE'," +
                "'COMPLETE','MATCHED','other copy','','',1,0)");
    }

    @After public void tearDown() {
        if (db != null && db.isOpen()) db.close();
    }

    @Test public void appliesExactlyOneFieldAndRestoresTheBeforeValue() throws Exception {
        AiBetaRegistryDraft.Draft staged = draft(AiBetaRegistryDraft.FIELD_LANGUAGE, "EN", "IT");
        String originalHash = staged.currentHash;
        AtomicReference<AiBetaRegistryDraft.Draft> journal = new AtomicReference<>();

        AiBetaRegistryDraft.Draft applied = AiBetaRegistryApply.apply(db, staged, journal::set);
        assertEquals(AiBetaRegistryDraft.APPLIED, applied.state);
        assertNotNull(applied.appliedHash);
        assertNotEquals(originalHash, applied.appliedHash);
        assertEquals("IT", scalar("SELECT language_code FROM market_listings WHERE id=1"));
        assertEquals("BASE_GAME", scalar("SELECT category_normalized FROM market_listings WHERE id=1"));
        assertEquals("FR", scalar("SELECT language_code FROM market_listings WHERE id=2"));

        AiBetaRegistryDraft.Draft restored = AiBetaRegistryApply.restore(db, applied, journal::set);
        assertEquals(AiBetaRegistryDraft.RESTORED, restored.state);
        assertEquals("EN", scalar("SELECT language_code FROM market_listings WHERE id=1"));
        assertEquals(originalHash, restored.restoredHash);
        assertEquals("BASE_GAME", scalar("SELECT category_normalized FROM market_listings WHERE id=1"));
    }

    @Test public void compositeLanguageApplyAndRestorePreserveDependencyMarker() throws Exception {
        db.execSQL("UPDATE market_listings SET language_code='?|DEP' WHERE id=1");
        AiBetaRegistryDraft.Draft staged = draft(AiBetaRegistryDraft.FIELD_LANGUAGE, "?|DEP", "IT|DEP");
        AtomicReference<AiBetaRegistryDraft.Draft> journal = new AtomicReference<>();

        AiBetaRegistryDraft.Draft applied = AiBetaRegistryApply.apply(db, staged, journal::set);
        assertEquals(AiBetaRegistryDraft.APPLIED, applied.state);
        assertEquals("IT|DEP", scalar("SELECT language_code FROM market_listings WHERE id=1"));
        assertEquals("?|DEP", applied.beforeValue);
        assertEquals("IT|DEP", applied.afterValue);

        AiBetaRegistryDraft.Draft restored = AiBetaRegistryApply.restore(db, applied, journal::set);
        assertEquals(AiBetaRegistryDraft.RESTORED, restored.state);
        assertEquals("?|DEP", scalar("SELECT language_code FROM market_listings WHERE id=1"));
    }

    @Test public void journalReconcilesItsOwnAppliedHashButDetectsLaterContentConflict() throws Exception {
        AiBetaRegistryDraft.Draft staged = draft(AiBetaRegistryDraft.FIELD_LANGUAGE, "EN", "IT");
        AiBetaRegistryDraft.Draft applied = AiBetaRegistryApply.apply(db, staged, value -> { });
        assertNotEquals(staged.currentHash, applied.appliedHash);

        AiBetaRegistryDraft.Draft pendingRecovery = applied.transition(
                AiBetaRegistryDraft.APPLY_PENDING, applied.appliedHash, null);
        AiBetaRegistryApply.Current afterOwnWrite = AiBetaRegistryApply.readCurrent(db, 1);
        assertEquals(AiBetaRegistryDraft.APPLIED,
                AiBetaRegistryApply.reconcile(pendingRecovery, afterOwnWrite).state);
        assertEquals(AiBetaRegistryDraft.APPLIED,
                AiBetaRegistryApply.reconcile(applied, afterOwnWrite).state);

        db.execSQL("UPDATE market_listings SET observed_text='later edit' WHERE id=1");
        AiBetaRegistryApply.Current afterLaterEdit = AiBetaRegistryApply.readCurrent(db, 1);
        assertEquals(AiBetaRegistryDraft.CONFLICT,
                AiBetaRegistryApply.reconcile(pendingRecovery, afterLaterEdit).state);
        assertEquals(AiBetaRegistryDraft.CONFLICT,
                AiBetaRegistryApply.reconcile(applied, afterLaterEdit).state);
    }

    @Test public void encryptedDraftPayloadCarriesReviewBeforeAfterAndCurrentHash() throws Exception {
        AiBetaRegistryDraft.Draft basic = draft(AiBetaRegistryDraft.FIELD_LANGUAGE, "EN", "IT");
        AiBetaRegistryDraft.Draft reviewed = new AiBetaRegistryDraft.Draft(basic.registryId,
                basic.snapshotId, basic.listingId, basic.itemId, basic.sourceItemId, basic.localId,
                basic.title, basic.field, basic.source, basic.provenance, basic.observedCategory,
                basic.observedLanguage, basic.beforeValue, basic.afterValue, basic.currentHash,
                null, null, Collections.singletonList("LANGUAGE_REANALYSIS"), true, true,
                AiBetaRegistryDraft.STAGED);
        AiBetaRegistryDraft.Draft restored = AiBetaRegistryDraft.Draft.fromJson(reviewed.toJson());
        assertEquals(reviewed.itemId, restored.itemId);
        assertEquals(reviewed.sourceItemId, restored.sourceItemId);
        assertEquals(reviewed.currentHash, restored.currentHash);
        assertEquals("EN", restored.beforeValue);
        assertEquals("IT", restored.afterValue);
        assertTrue(restored.reviewRequired);
        assertTrue(restored.reviewed);
        assertEquals(Collections.singletonList("LANGUAGE_REANALYSIS"), restored.reviewFlags);
    }

    @Test public void selectedCardReadLoadsOneRowWithoutRecomputingTheFullCatalog() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        File file = app.getDatabasePath("vinted_affari.db");
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) assertTrue(parent.mkdirs());
        if (file.exists()) assertTrue(file.delete());
        try (SQLiteDatabase onDisk = SQLiteDatabase.openOrCreateDatabase(file, null)) {
            createSchema(onDisk);
            insertListings(onDisk);
        }

        AiBetaRegistryView.CurrentTargetRead target = AiBetaRegistryView.readCurrentTarget(app, 1);
        assertEquals(AiBetaRegistryView.CatalogState.AVAILABLE, target.catalog.state);
        assertEquals(1, target.catalog.byLocalId.size());
        assertEquals("vinted-current-1", target.catalog.byLocalId.get(1L).itemId);
        assertTrue(target.uniqueCurrentItemId);
        assertTrue(target.active);
        assertFalse(target.manualReviewRequired);
        file.delete();
        new File(file.getPath() + "-wal").delete();
        new File(file.getPath() + "-shm").delete();
    }

    @Test public void groupedCurrentReadLoadsRequestedRowsAndChecksCurrentItemIdUniqueness() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        File file = app.getDatabasePath("vinted_affari.db");
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) assertTrue(parent.mkdirs());
        if (file.exists()) assertTrue(file.delete());
        try {
            try (SQLiteDatabase onDisk = SQLiteDatabase.openOrCreateDatabase(file, null)) {
                createSchema(onDisk);
                insertListings(onDisk);
                onDisk.execSQL("UPDATE market_listings SET language_code='?|DEP' WHERE id=1");
                onDisk.execSQL("INSERT INTO market_listings(id,vinted_item_id,vinted_title,brand,category_normalized," +
                        "category_source,language_code,lifecycle,enrichment_state,match_state,observed_text," +
                        "listing_photos_csv,image_url,game_id,manual_review_required) VALUES(" +
                        "3,'vinted-current-1','Duplicate ID row','Other','BASE_GAME','RULES','EN','ACTIVE'," +
                        "'COMPLETE','MATCHED','duplicate','','',1,0)");
            }

            Map<Long, AiBetaRegistryView.CurrentTargetRead> reads =
                    AiBetaRegistryView.readCurrentTargets(app, Arrays.asList(1L, 2L, 99L, 1L));
            assertEquals(3, reads.size());
            assertEquals(AiBetaRegistryView.CatalogState.AVAILABLE, reads.get(1L).catalog.state);
            assertEquals(2, reads.get(1L).catalog.byLocalId.size());
            assertEquals("?|DEP", reads.get(1L).catalog.byLocalId.get(1L).language);
            assertEquals("vinted-current-1", reads.get(1L).catalog.byLocalId.get(1L).itemId);
            assertNotNull(reads.get(1L).catalog.byLocalId.get(1L).sourceHash);
            assertFalse(reads.get(1L).uniqueCurrentItemId);
            assertTrue(reads.get(1L).active);
            assertFalse(reads.get(1L).manualReviewRequired);
            assertTrue(reads.get(2L).uniqueCurrentItemId);
            assertTrue(reads.get(2L).active);
            assertFalse(reads.get(1L).catalog.byLocalId.containsKey(99L));
        } finally {
            file.delete();
            new File(file.getPath() + "-wal").delete();
            new File(file.getPath() + "-shm").delete();
        }
    }

    @Test public void contentModificationAfterStagingBlocksApply() throws Exception {
        AiBetaRegistryDraft.Draft staged = draft(AiBetaRegistryDraft.FIELD_LANGUAGE, "EN", "IT");
        db.execSQL("UPDATE market_listings SET observed_text='concurrent edit' WHERE id=1");
        AtomicReference<AiBetaRegistryDraft.Draft> journal = new AtomicReference<>();

        try {
            AiBetaRegistryApply.apply(db, staged, journal::set);
            fail("stale draft applied");
        } catch (AiBetaRegistryApply.ConflictException expected) {
            assertTrue(expected.getMessage().contains("hash"));
        }
        assertEquals("EN", scalar("SELECT language_code FROM market_listings WHERE id=1"));
        assertEquals(AiBetaRegistryDraft.CONFLICT, journal.get().state);
    }

    @Test public void currentVintedIdChangeBlocksEvenWhenProjectionHashDoesNotIncludeThatId() throws Exception {
        AiBetaRegistryDraft.Draft staged = draft(AiBetaRegistryDraft.FIELD_CATEGORY, "BASE_GAME", "EXPANSION");
        db.execSQL("UPDATE market_listings SET vinted_item_id='vinted-replaced' WHERE id=1");
        AtomicReference<AiBetaRegistryDraft.Draft> journal = new AtomicReference<>();

        try {
            AiBetaRegistryApply.apply(db, staged, journal::set);
            fail("changed current identity applied");
        } catch (AiBetaRegistryApply.ConflictException expected) {
            assertTrue(expected.getMessage().contains("Identità Vinted corrente"));
        }
        assertEquals("BASE_GAME", scalar("SELECT category_normalized FROM market_listings WHERE id=1"));
    }

    @Test public void modificationAfterApplyBlocksUndoWithoutOverwritingIt() throws Exception {
        AtomicReference<AiBetaRegistryDraft.Draft> journal = new AtomicReference<>();
        AiBetaRegistryDraft.Draft applied = AiBetaRegistryApply.apply(
                db, draft(AiBetaRegistryDraft.FIELD_LANGUAGE, "EN", "IT"), journal::set);
        db.execSQL("UPDATE market_listings SET observed_text='later edit' WHERE id=1");

        try {
            AiBetaRegistryApply.restore(db, applied, journal::set);
            fail("stale undo applied");
        } catch (AiBetaRegistryApply.ConflictException expected) {
            assertTrue(expected.getMessage().contains("ripristino bloccato"));
        }
        assertEquals("IT", scalar("SELECT language_code FROM market_listings WHERE id=1"));
        assertEquals(AiBetaRegistryDraft.CONFLICT, journal.get().state);
    }

    private AiBetaRegistryDraft.Draft draft(String field, String before, String after) throws Exception {
        AiBetaRegistryApply.Current current = AiBetaRegistryApply.readCurrent(db, 1);
        AiBetaRegistryView.LocalListing row = current.listing;
        return new AiBetaRegistryDraft.Draft("registry-test", "snapshot-test", 1,
                row.itemId, "historic-item-id", row.localId, row.title, field,
                AiBetaRegistryDraft.SOURCE_CORRECTED, "reviewed-test-source", row.category,
                row.language, before, after, row.sourceHash, null, null, Collections.emptyList(),
                false, false, AiBetaRegistryDraft.STAGED);
    }

    private static void createSchema(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE games(id INTEGER PRIMARY KEY,bgg_id TEXT)");
        db.execSQL("CREATE TABLE market_listings(" +
                "id INTEGER PRIMARY KEY,vinted_item_id TEXT,vinted_title TEXT,brand TEXT," +
                "category_normalized TEXT,category_source TEXT,language_code TEXT,lifecycle TEXT," +
                "enrichment_state TEXT,match_state TEXT,observed_text TEXT,listing_photos_csv TEXT," +
                "image_url TEXT,game_id INTEGER,manual_review_required INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("INSERT INTO games(id,bgg_id) VALUES(1,'BGG-1')");
    }

    private static void insertListings(SQLiteDatabase db) {
        db.execSQL("INSERT INTO market_listings(id,vinted_item_id,vinted_title,brand,category_normalized," +
                "category_source,language_code,lifecycle,enrichment_state,match_state,observed_text," +
                "listing_photos_csv,image_url,game_id,manual_review_required) VALUES(" +
                "1,'vinted-current-1','Board game listing','Brand','BASE_GAME','RULES','EN','ACTIVE'," +
                "'COMPLETE','MATCHED','observed copy','https://www.vinted.com/photo.jpg'," +
                "'https://www.vinted.com/cover.jpg',1,0)");
        db.execSQL("INSERT INTO market_listings(id,vinted_item_id,vinted_title,brand,category_normalized," +
                "category_source,language_code,lifecycle,enrichment_state,match_state,observed_text," +
                "listing_photos_csv,image_url,game_id,manual_review_required) VALUES(" +
                "2,'vinted-current-2','Unaffected listing','Other','EXPANSION','RULES','FR','ACTIVE'," +
                "'COMPLETE','MATCHED','other copy','','',1,0)");
    }

    private String scalar(String sql) {
        try (android.database.Cursor cursor = db.rawQuery(sql, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getString(0);
        }
    }
}
