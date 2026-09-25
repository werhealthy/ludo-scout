#!/usr/bin/env python3
import re
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MARKET = (ROOT / "app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
SERVICE = (ROOT / "app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")

match = re.search(
    r'private static final String CATALOG_VISIBILITY_BREAKDOWN_SQL\s*=\s*(.*?);\s*\n',
    MARKET,
    re.S,
)
if not match:
    raise AssertionError("MarketStore is missing the authoritative catalog visibility query")

parts = re.findall(r'"((?:\\\\.|[^"\\\\])*)"', match.group(1))
sql = "".join(part.replace(r'\\"', '"').replace(r'\\\\', '\\') for part in parts)

db = sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE games(id INTEGER PRIMARY KEY, bgg_id TEXT, rating REAL, database_visible INTEGER, match_state TEXT);
CREATE TABLE market_listings(
    id INTEGER PRIMARY KEY, game_id INTEGER, lifecycle TEXT, vinted_item_id TEXT, vinted_url TEXT,
    enrichment_state TEXT, manual_review_required INTEGER, match_state TEXT, legacy_signature TEXT
);
CREATE TABLE deals(
    id INTEGER PRIMARY KEY, signature TEXT, lifecycle TEXT, tier TEXT, rating REAL, bgg_id TEXT,
    vinted_item_id TEXT, vinted_url TEXT, verification_state TEXT, listing_type TEXT, last_seen INTEGER
);
CREATE TABLE processing_jobs(id INTEGER PRIMARY KEY, listing_id INTEGER, job_type TEXT, source TEXT, state TEXT);
""")

def add_game(game_id, bgg_id):
    db.execute("INSERT INTO games VALUES(?,?,?,?,?)", (game_id, bgg_id, 7.0, 1, "MATCHED"))

def add_listing(listing_id, game_id, signature, item_id=None, url=None, match_state="MATCHED"):
    db.execute(
        "INSERT INTO market_listings VALUES(?,?,?,?,?,?,?,?,?)",
        (listing_id, game_id, "ACTIVE", item_id, url, "CORE_COMPLETE", 0, match_state, signature),
    )

def add_deal(deal_id, signature, bgg_id, item_id, url):
    db.execute(
        "INSERT INTO deals VALUES(?,?,?,?,?,?,?,?,?,?,?)",
        (deal_id, signature, "ACTIVE", "good", 7.0, bgg_id, item_id, url, "OK", "BASE_GAME", 1000 + deal_id),
    )

for index in range(1, 15):
    add_game(index, str(1000 + index))

for index in range(1, 10):
    add_listing(index, index, f"sig-{index}", str(9000 + index), f"https://www.vinted.it/items/{9000 + index}")

# 1 has no legacy deal. 2-9 progressively exercise one Catalog gate each.
for index in range(2, 10):
    add_deal(index, f"sig-{index}", str(1000 + index), str(9000 + index), f"https://www.vinted.it/items/{9000 + index}")

db.execute("UPDATE deals SET lifecycle='REMOVED' WHERE id=2")
db.execute("UPDATE deals SET tier='verify' WHERE id=3")
db.execute("UPDATE deals SET vinted_url='' WHERE id=4")
db.execute("UPDATE deals SET verification_state='EXPANSION_CHECK' WHERE id=5")
db.execute("UPDATE market_listings SET match_state='MATCH_UNCERTAIN' WHERE id=6")
db.execute("UPDATE deals SET bgg_id='different' WHERE id=7")
db.execute("INSERT INTO processing_jobs VALUES(1,8,'VINTED_ENRICHMENT','AUTO','PENDING')")

# 10 is Catalog-eligible through legacy_signature although the market row has no Vinted identity.
add_listing(10, 10, "sig-10", "", "")
add_deal(10, "sig-10", "1010", "9010", "https://www.vinted.it/items/9010")

# 11 has two matching rows. One fails listing match, the other succeeds; the deal counts once.
add_listing(11, 11, "sig-11", "9011", "https://www.vinted.it/items/9011", "MATCH_UNCERTAIN")
add_game(111, "1011")
add_listing(111, 111, "sig-11", "9011", "https://www.vinted.it/items/9011")
add_deal(11, "sig-11", "1011", "9011", "https://www.vinted.it/items/9011")

# Deep automatic work is optional; manual recovery blocks. NULL source follows SQLite's exact predicate.
for index in (12, 13, 14):
    add_listing(index, index, f"sig-{index}", str(9000 + index), f"https://www.vinted.it/items/{9000 + index}")
    add_deal(index, f"sig-{index}", str(1000 + index), str(9000 + index), f"https://www.vinted.it/items/{9000 + index}")
db.execute("INSERT INTO processing_jobs VALUES(2,12,'VINTED_DEEP_ENRICHMENT','AUTO','PENDING')")
db.execute("INSERT INTO processing_jobs VALUES(3,13,'VINTED_DEEP_ENRICHMENT','MANUAL_RECOVERY','PENDING')")
db.execute("INSERT INTO processing_jobs VALUES(4,14,'VINTED_DEEP_ENRICHMENT',NULL,'PENDING')")

authoritative_sql = """
SELECT d.id FROM deals d
WHERE d.lifecycle='ACTIVE'
  AND d.tier IN ('hot','good','offer','fair','insufficient','hunt')
  AND d.rating IS NOT NULL AND d.rating>=6.0
  AND d.bgg_id IS NOT NULL AND d.bgg_id<>''
  AND d.vinted_item_id IS NOT NULL AND d.vinted_item_id<>''
  AND d.vinted_url IS NOT NULL AND d.vinted_url<>''
  AND COALESCE(d.verification_state,'') IN ('OK','USER_CONFIRMED')
  AND COALESCE(d.listing_type,'') IN ('BASE_GAME','EXPANSION','GAME')
  AND EXISTS (
      SELECT 1 FROM market_listings l JOIN games g ON g.id=l.game_id
      WHERE (l.legacy_signature=d.signature OR (d.vinted_item_id IS NOT NULL AND l.vinted_item_id=d.vinted_item_id))
        AND l.lifecycle='ACTIVE'
        AND l.enrichment_state IN ('COMPLETE','CORE_COMPLETE')
        AND l.match_state='MATCHED'
        AND COALESCE(l.manual_review_required,0)=0
        AND g.match_state='MATCHED' AND g.bgg_id=d.bgg_id
        AND g.database_visible=1 AND g.rating IS NOT NULL AND g.rating>=6.0
        AND NOT EXISTS (
            SELECT 1 FROM processing_jobs j
            WHERE j.listing_id=l.id
              AND (j.job_type<>'VINTED_DEEP_ENRICHMENT' OR j.source='MANUAL_RECOVERY')
              AND j.state IN ('PENDING','PROCESSING','FAILED_RETRYABLE')
        )
  )
ORDER BY d.last_seen DESC
LIMIT 800
"""
authoritative_ids = [row[0] for row in db.execute(authoritative_sql)]
assert authoritative_ids == [14, 12, 11, 10, 9], authoritative_ids

row = db.execute(sql).fetchone()
expected = (14, 11, 10, 9, 8, 7, 5, 5)
assert row == expected, f"expected={expected} actual={row}"
assert row[-1] == len(authoritative_ids), f"diagnostic={row[-1]} catalog={len(authoritative_ids)}"

for field in (
    "marketCore", "catalogBase", "dealIdentity", "reviewClear",
    "listingMatched", "bggAgreement", "noBlockingJobs", "catalogEligible",
):
    assert field in MARKET, f"catalog breakdown is missing {field}"
assert '"catalogVisibility={"' in SERVICE
assert "marketDiag.catalogVisibilityBreakdown()" in SERVICE

print("PASS catalog visibility stages match the exact UI population and edge cases")
