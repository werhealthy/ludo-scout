#!/usr/bin/env python3
import re
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MARKET = (ROOT / "app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
SERVICE = (ROOT / "app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")

match = re.search(
    r'private static final String CATALOG_VISIBILITY_BREAKDOWN_SQL\\s*=\\s*(.*?);\\s*\\n',
    MARKET,
    re.S,
)
if not match:
    raise AssertionError("MarketStore is missing the authoritative catalog visibility query")

parts = re.findall(r'"((?:\\\\.|[^"\\\\])*)"', match.group(1))
sql = "".join(part.replace(r'\\"', '"').replace(r'\\\\', '\\') for part in parts)

db = sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE games(
    id INTEGER PRIMARY KEY,
    bgg_id TEXT,
    rating REAL,
    database_visible INTEGER,
    match_state TEXT
);
CREATE TABLE market_listings(
    id INTEGER PRIMARY KEY,
    game_id INTEGER,
    lifecycle TEXT,
    vinted_item_id TEXT,
    vinted_url TEXT,
    enrichment_state TEXT,
    manual_review_required INTEGER,
    match_state TEXT,
    legacy_signature TEXT
);
CREATE TABLE deals(
    id INTEGER PRIMARY KEY,
    signature TEXT,
    lifecycle TEXT,
    tier TEXT,
    rating REAL,
    bgg_id TEXT,
    vinted_item_id TEXT,
    vinted_url TEXT,
    verification_state TEXT,
    listing_type TEXT
);
CREATE TABLE processing_jobs(
    id INTEGER PRIMARY KEY,
    listing_id INTEGER,
    job_type TEXT,
    source TEXT,
    state TEXT
);
""")

for index in range(1, 10):
    db.execute("INSERT INTO games VALUES(?,?,?,?,?)", (index, str(1000 + index), 7.0, 1, "MATCHED"))
    db.execute(
        "INSERT INTO market_listings VALUES(?,?,?,?,?,?,?,?,?)",
        (index, index, "ACTIVE", str(9000 + index), f"https://www.vinted.it/items/{9000 + index}",
         "CORE_COMPLETE", 0, "MATCHED", f"sig-{index}"),
    )

# 1 has no legacy deal: market core cannot reach the Catalog table.
for index in range(2, 10):
    db.execute(
        "INSERT INTO deals VALUES(?,?,?,?,?,?,?,?,?,?)",
        (index, f"sig-{index}", "ACTIVE", "good", 7.0, str(1000 + index),
         str(9000 + index), f"https://www.vinted.it/items/{9000 + index}", "OK", "BASE_GAME"),
    )

db.execute("UPDATE deals SET lifecycle='REMOVED' WHERE id=2")
db.execute("UPDATE deals SET tier='verify' WHERE id=3")
db.execute("UPDATE deals SET vinted_url='' WHERE id=4")
db.execute("UPDATE deals SET verification_state='EXPANSION_CHECK' WHERE id=5")
db.execute("UPDATE market_listings SET match_state='MATCH_UNCERTAIN' WHERE id=6")
db.execute("UPDATE deals SET bgg_id='different' WHERE id=7")
db.execute("INSERT INTO processing_jobs VALUES(1,8,'VINTED_ENRICHMENT','AUTO','PENDING')")

row = db.execute(sql).fetchone()
expected = (9, 8, 6, 5, 4, 3, 2, 1, 1)
assert row == expected, f"expected={expected} actual={row}"

for field in (
    "marketCore", "legacyBridge", "dealBase", "dealIdentity", "reviewClear",
    "listingMatched", "bggAgreement", "noBlockingJobs", "catalogEligible",
):
    assert field in SERVICE, f"debug output is missing {field}"

print("PASS catalog visibility stages are cumulative and reconcile to the exact UI population")
