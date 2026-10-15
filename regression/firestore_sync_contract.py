#!/usr/bin/env python3
"""Offline source/SQL contract checks; no Firebase traffic or Android SDK required."""
import re
import sqlite3
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/it/vintedaffari/app"
WORKER = (SRC / "FirestoreSnapshotWorker.java").read_text(encoding="utf-8")
SNAPSHOT_WORKER = WORKER
SNAPSHOT_STORE = (SRC / "FirestoreSnapshotStore.java").read_text(encoding="utf-8")
SYNC = (SRC / "FirestoreSync.java").read_text(encoding="utf-8")
OPT_IN_POLICY = (SRC / "FirestoreOptInPolicy.java").read_text(encoding="utf-8")
RULES = (ROOT / "firestore.rules").read_text(encoding="utf-8")
MAIN = (SRC / "MainActivity.java").read_text(encoding="utf-8")
APP = (SRC / "LudoScoutApp.java").read_text(encoding="utf-8")
MANIFEST = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
APP_GRADLE = (ROOT / "app/build.gradle").read_text(encoding="utf-8")


def sql_projection():
    definition = WORKER.split("static final String QUERY =", 1)[1].split(";", 1)[0]
    return "".join(re.findall(r'"([^"]*)"', definition))


class FirestoreSyncContractTest(unittest.TestCase):
    def test_read_only_query_against_canonical_fixture(self):
        db = sqlite3.connect(":memory:")
        try:
            db.executescript("""
                CREATE TABLE games(id INTEGER PRIMARY KEY, bgg_id TEXT);
                CREATE TABLE market_listings(
                    id INTEGER PRIMARY KEY, game_id INTEGER, vinted_title TEXT, brand TEXT,
                    category_normalized TEXT, category_source TEXT, language_code TEXT,
                    lifecycle TEXT, enrichment_state TEXT, match_state TEXT,
                    observed_text TEXT, listing_photos_csv TEXT, image_url TEXT
                );
                INSERT INTO games VALUES(1,'12345');
                INSERT INTO market_listings(id,game_id,vinted_title,brand,category_normalized,category_source,language_code,lifecycle,enrichment_state,match_state)
                    VALUES(21,1,'Gioco','Hasbro','board_game','manual','it','ACTIVE','ENRICHED','BGG_MATCHED');
                INSERT INTO market_listings(id,vinted_title,brand,category_normalized,category_source,language_code,lifecycle,enrichment_state,match_state)
                    VALUES(22,'Incerto','',NULL,'','xx','ACTIVE','PENDING_ANALYSIS','BGG_MATCH_REQUIRED');
            """)
            before = db.total_changes
            query = sql_projection()
            self.assertTrue(query.strip().startswith("SELECT "))
            self.assertEqual(2, len(db.execute(query).fetchall()))
            rows = db.execute(query).fetchall()
            self.assertEqual(13, len(rows[0]))
            self.assertEqual((21, "it", "Gioco", "Hasbro", "board_game", "manual", "12345", "ACTIVE", "ENRICHED", "BGG_MATCHED", None, None, None), rows[0])
            self.assertEqual((22, "xx", "Incerto", "", None, "", None, "ACTIVE", "PENDING_ANALYSIS", "BGG_MATCH_REQUIRED", None, None, None), rows[1])
            self.assertEqual(before, db.total_changes)
        finally:
            db.close()

    def test_projection_matches_exact_firestone_allowlist(self):
        projection = WORKER.split("private static Row project(", 1)[1].split("static String normalizedLanguage", 1)[0]
        produced = re.findall(r'v[.]put[(]"([^"]+)"', projection)
        produced.insert(1, "snapshot_id")  # added only when the row is published
        expected = []
        v3_rules = RULES.split("function validV3Listing", 1)[1].split("match /sync_v3", 1)[0]
        for action in ("hasAll", "hasOnly"):
            start = v3_rules.index("d.keys()." + action + "([")
            fragment = v3_rules[start:].split("])", 1)[0]
            fields = re.findall(r"'([^']+)'", fragment)
            if action == "hasOnly":
                fields = [field for field in fields if field not in {"observed_text", "photo_urls"}]
            expected.append(fields)
        expected[0].extend(["observed_text", "photo_urls"])
        expected[0].sort(key=produced.index)
        expected[1].extend(["observed_text", "photo_urls"])
        expected[1].sort(key=produced.index)
        self.assertEqual(produced, expected[0])
        self.assertEqual(produced, expected[1])
        for forbidden in ("seller_name", "seller_id", "favorites", "password",
                          "token", "listing_overrides", "condition", "price_cents",
                          "protected_price_cents", "item_url", "author"):
            self.assertNotIn(forbidden, produced)
        self.assertIn("observed_text", produced)
        self.assertIn("photo_urls", produced)

    def test_evidence_projection_and_document_limit_are_gated_before_materialization_complete(self):
        self.assertIn("MAX_EVIDENCE_TEXT = 2_000", WORKER)
        self.assertIn("MAX_PHOTO_URLS = 4", WORKER)
        self.assertIn("MAX_DOCUMENT_BYTES = 12_000", WORKER)
        self.assertIn('v.put("observed_text",boundedCodePoints', WORKER)
        self.assertIn('v.put("photo_urls",photos', WORKER)
        materialize = WORKER.split("private static void materialize", 1)[1].split("private static String sourceHash", 1)[0]
        self.assertLess(materialize.index("payload.getBytes"), materialize.index("store.insertRow"))
        self.assertLess(materialize.index("store.insertRow"), materialize.index("store.finishMaterialization"))
        self.assertIn('"snapshot_document_limit"', materialize)
        migration = WORKER.split("if(state!=null&&state.protocolVersion!=4)", 1)[1].split('if(state!=null&&"CLEAN_OLD"', 1)[0]
        self.assertIn("store.markAborted()", migration)
        self.assertIn('"PUBLISH".equals(state.state)', migration)

    def test_owner_scope_deny_all_and_off_by_default(self):
        self.assertIn("request.auth.uid == uid", RULES)
        self.assertIn("FxAKNm8aK6amBPoHZcXNOtSLv3t2", RULES)
        self.assertIn("match /sync_v1/{uid}/devices/{deviceId}/listings/{listingId}", RULES)
        self.assertIn("allow delete: if false", RULES)
        self.assertIn("allow list: if false", RULES)
        self.assertIn("allow read, write: if false", RULES)
        self.assertIn("function validV3Listing", RULES)
        self.assertIn("FirestoreSyncDialog.show(this)", MAIN)
        for key in ("LUDO_FIREBASE_APP_ID", "LUDO_FIREBASE_API_KEY",
                    "LUDO_FIREBASE_PROJECT_ID", "LUDO_SYNC_OWNER_UID"):
            self.assertIn("BuildConfig." + key, SYNC)
        self.assertIn('FirestoreOptInPolicy.accepts(p.getBoolean("enabled", false),', SYNC)
        self.assertIn('return enabled && (version == null || version.isEmpty() || CURRENT_VERSION.equals(version));', OPT_IN_POLICY)
        self.assertIn('WorkQuery.fromUniqueWorkNames(java.util.Arrays.asList(WORK, V2_WORK, LEGACY_WORK))', SYNC)
        self.assertIn('OPT_IN_VERSION.equals(preferences.getString("opt_in_version", ""))', SYNC)
        self.assertIn("MAX_PER_PASS = 80", SNAPSHOT_WORKER)
        self.assertIn("MAX_PER_DAY = 2400", SNAPSHOT_WORKER)
        self.assertIn("SQLiteDatabase.OPEN_READONLY", SNAPSHOT_WORKER)
        self.assertIn("waitForPendingWrites()", SNAPSHOT_WORKER)

    def test_sync_scheduling_uses_the_multi_process_workmanager_api(self):
        self.assertIn('android:process=":ui"', MANIFEST)
        self.assertIn("Configuration.Provider", APP)
        self.assertIn("setDefaultProcessName(getPackageName())", APP)
        self.assertIn("if(getPackageName().equals(Application.getProcessName()))", APP)
        self.assertIn("WorkManager.initialize(this,getWorkManagerConfiguration())", APP)
        self.assertIn('androidx.work.WorkManagerInitializer', MANIFEST)
        self.assertIn('tools:node="remove"', MANIFEST)
        self.assertIn("androidx.work:work-multiprocess:2.9.1", APP_GRADLE)
        self.assertIn("RemoteWorkManager.getInstance(appContext)", SYNC)
        self.assertNotRegex(SYNC, r"(?<!Remote)WorkManager\.getInstance")

    def test_first_device_identity_initialization_preserves_opt_in_and_continues(self):
        identity = SNAPSHOT_WORKER.split("boolean identityChanged=", 1)[1].split("int day=", 1)[0]
        self.assertNotIn(".clear()", identity)
        self.assertNotIn("return Result.success()", identity)
        self.assertIn('putString("device_id",device)', identity)
        self.assertIn('putInt("budget_day",-1)', identity)
        self.assertIn('putInt("budget_writes",0)', identity)
        self.assertIn("store=FirestoreSnapshotStore.open(context);if(identityChanged)store.resetIdle()", SNAPSHOT_WORKER)
        self.assertIn('FirestoreOptInPolicy.accepts(p.getBoolean("enabled", false),', SYNC)

    def test_sync_device_identity_is_shared_across_worker_passes(self):
        sync_source = (SRC / "FirestoreSync.java").read_text(encoding="utf-8")
        self.assertIn("FirestoreSync.deviceId(context)", SNAPSHOT_WORKER)
        self.assertIn("static String deviceId(Context context)", sync_source)
        self.assertNotIn("private static String deviceId(Context context)", WORKER)

    def test_firestore_projection_is_not_misrepresented_as_complete_snapshot(self):
        docs = (ROOT / "docs/firestore-sync-v1.md").read_text(encoding="utf-8").lower()
        self.assertIn("non viene effettuata la rimozione automatica", docs)
        self.assertIn("implementata la riconciliazione/tombstone", docs)
        self.assertNotIn('"has_author"', WORKER)
        self.assertIn('manifest.put("author_coverage_available",false)', WORKER)

    def test_v2_snapshot_worker_is_isolated_bounded_and_owner_scoped(self):
        self.assertIn("SQLiteDatabase.OPEN_READONLY", SNAPSHOT_WORKER)
        self.assertIn("getNoBackupFilesDir()", SNAPSHOT_STORE)
        self.assertIn("MAX_BYTES = 32L * 1024L * 1024L", SNAPSHOT_STORE)
        self.assertIn("MAX_DATABASE_BYTES = MAX_BYTES - AUXILIARY_RESERVE_BYTES", SNAPSHOT_STORE)
        self.assertIn("sidecarBytes(databaseFile)", SNAPSHOT_STORE)
        self.assertIn("MAX_ROWS = 100_000", SNAPSHOT_STORE)
        self.assertIn("MAX_PER_PASS = 80", SNAPSHOT_WORKER)
        self.assertIn("MAX_PER_DAY = 2400", SNAPSHOT_WORKER)
        self.assertIn("sync_v3", SNAPSHOT_WORKER)
        self.assertIn('store.state("MANIFEST")', SNAPSHOT_WORKER)
        self.assertIn('store.state("PUBLISH")', SNAPSHOT_WORKER)
        self.assertIn('manifest.put("schema_version",3)', SNAPSHOT_WORKER)
        self.assertIn("state.contentHash.equals(sourceHash(catalog))", SNAPSHOT_WORKER)
        self.assertIn('snapshotId != v3CurrentSnapshotId(uid, deviceId)', RULES)
        self.assertIn('FirestoreSnapshotWorker.class', SYNC)
        self.assertIn('cancelUniqueWork(V2_WORK)', SYNC)
        self.assertIn('cancelUniqueWork(LEGACY_WORK)', SYNC)
        v2_rules = RULES.split('match /sync_v2/', 1)[1].split('function v3CurrentSnapshotId', 1)[0]
        self.assertIn('allow create, update: if owner(uid)', v2_rules)
        self.assertIn('allow create: if owner(uid)', v2_rules)


if __name__ == "__main__":
    unittest.main()
