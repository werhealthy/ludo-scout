#!/usr/bin/env python3
"""Offline source/SQL contract checks; no Firebase traffic or Android SDK required."""
import re
import sqlite3
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/it/vintedaffari/app"
WORKER = (SRC / "FirestoreSyncWorker.java").read_text(encoding="utf-8")
SYNC = (SRC / "FirestoreSync.java").read_text(encoding="utf-8")
RULES = (ROOT / "firestore.rules").read_text(encoding="utf-8")
MAIN = (SRC / "MainActivity.java").read_text(encoding="utf-8")


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
                    id INTEGER PRIMARY KEY, game_id INTEGER,
                    vinted_item_id TEXT, vinted_title TEXT, brand TEXT,
                    item_condition TEXT, current_price_cents INTEGER,
                    protected_price_cents INTEGER, vinted_url TEXT,
                    image_url TEXT, listing_photos_csv TEXT, observed_text TEXT,
                    category_normalized TEXT, category_source TEXT,
                    lifecycle TEXT, enrichment_state TEXT, match_state TEXT,
                    language_code TEXT, last_seen INTEGER,
                    manual_review_required INTEGER
                );
                INSERT INTO games VALUES(1,'12345');
                INSERT INTO market_listings(
                    id,game_id,vinted_item_id,vinted_title,current_price_cents,
                    lifecycle,enrichment_state,match_state,manual_review_required
                ) VALUES(21,1,'v-1','Gioco',1500,'ACTIVE','COMPLETE','MATCHED',0);
                INSERT INTO market_listings(
                    id,vinted_item_id,vinted_title,lifecycle,
                    enrichment_state,match_state,manual_review_required
                ) VALUES(22,'v-2','Incerto','AUTO_FILTERED','REVIEW','UNKNOWN',1);
            """)
            before = db.total_changes
            query = sql_projection()
            self.assertTrue(query.strip().startswith("SELECT "))
            self.assertEqual(2, len(db.execute(query).fetchall()))
            rows = db.execute(query).fetchall()
            self.assertEqual(20, len(rows[0]))
            self.assertEqual((21, "v-1", "Gioco"), rows[0][:3])
            self.assertEqual("12345", rows[0][16])
            self.assertIsNone(rows[1][16])
            self.assertEqual("AUTO_FILTERED", rows[1][13])
            self.assertEqual(1, rows[1][19])
            self.assertEqual(before, db.total_changes)
        finally:
            db.close()

    def test_projection_matches_exact_firestone_allowlist(self):
        produced = re.findall(r'values[.]put[(]"([^"]+)"', WORKER)
        expected = []
        for action in ("hasAll", "hasOnly"):
            start = RULES.index("data.keys()." + action + "([")
            fragment = RULES[start:].split("])", 1)[0]
            fields = re.findall(r"'([^']+)'", fragment)
            expected.append(fields)
        self.assertEqual(produced, expected[0])
        self.assertEqual(produced, expected[1])
        for forbidden in ("seller_name", "seller_id", "favorites", "password",
                          "token", "listing_overrides"):
            self.assertNotIn(forbidden, produced)

    def test_owner_scope_deny_all_and_off_by_default(self):
        self.assertIn("request.auth.uid == uid", RULES)
        self.assertIn("FxAKNm8aK6amBPoHZcXNOtSLv3t2", RULES)
        self.assertIn("match /sync_v1/{uid}/devices/{deviceId}/listings/{listingId}", RULES)
        self.assertIn("allow delete: if false", RULES)
        self.assertIn("allow read, write: if false", RULES)
        self.assertIn("&& validListing()", RULES)
        self.assertIn("FirestoreSyncDialog.show(this)", MAIN)
        for key in ("LUDO_FIREBASE_APP_ID", "LUDO_FIREBASE_API_KEY",
                    "LUDO_FIREBASE_PROJECT_ID", "LUDO_SYNC_OWNER_UID"):
            self.assertIn("BuildConfig." + key, SYNC)
        self.assertIn('prefs(context).getBoolean("enabled", false) && authorized(context)', SYNC)
        self.assertIn("MAX_PER_PASS = 80", WORKER)
        self.assertIn("MAX_PER_DAY = 2400", WORKER)
        self.assertIn("SQLiteDatabase.OPEN_READONLY", WORKER)
        self.assertIn("waitForPendingWrites()", WORKER)


if __name__ == "__main__":
    unittest.main()
