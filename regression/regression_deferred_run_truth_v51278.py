#!/usr/bin/env python3
"""Protect current-run deferred work counts and promotion from review/held rows."""
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
market = (ROOT / "app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
deal = (ROOT / "app/src/main/java/it/vintedaffari/app/DealDatabase.java").read_text(encoding="utf-8")

def method(source, start, next_method):
    begin = source.index(start)
    end = source.index(next_method, begin + len(start))
    return source[begin:end]

deferred_count = method(market, "public int activeRunDeferredVintedCount()", "public boolean listingBelongsToActiveRun")
deferred_promote = method(market, "public int promoteDeferredVintedBatch(", "public int ")
engine_truth = method(deal, "private int[] engineRangeCounts(", "private void fillEngineCounts(")

guards = [
    "g.bgg_id IS NOT NULL",
    "g.match_state='MATCHED'",
    "COALESCE(l.manual_review_required,0)=0",
    "l.match_state<>'BGG_VARIANT_REVIEW'",
    "COALESCE(d.verification_state,'') NOT IN",
]

for name, source in (("active-run deferred count", deferred_count), ("deferred promotion", deferred_promote)):
    missing = [guard for guard in guards if guard not in source]
    assert not missing, f"{name} lacks engine truth guards: {missing}"

assert "BGG_VARIANT_REVIEW" in engine_truth and "MATCH_UNCERTAIN" in engine_truth and "manual_review_required" in engine_truth

# Executable fixture: only the ordinary matched listing is actionable. Manual review,
# BGG-variant review, uncertain identity, and below-threshold rows must not enter the lane.
db = sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE games(id INTEGER PRIMARY KEY, database_visible INTEGER, rating REAL, bgg_id TEXT, match_state TEXT);
CREATE TABLE market_listings(id INTEGER PRIMARY KEY, game_id INTEGER, lifecycle TEXT, enrichment_state TEXT,
    vinted_url TEXT, vinted_item_id TEXT, legacy_signature TEXT, temp_fingerprint TEXT,
    manual_review_required INTEGER, match_state TEXT, first_seen INTEGER);
CREATE TABLE deals(signature TEXT, verification_state TEXT);
CREATE TABLE observations(signature TEXT, observed_at INTEGER);
INSERT INTO games VALUES
 (1,1,7.0,'1001','MATCHED'), (2,1,7.0,'1002','MATCHED'),
 (3,1,7.0,'1003','MATCHED'), (4,1,7.0,'1004','MATCHED'),
 (5,1,5.9,'1005','MATCHED');
INSERT INTO market_listings VALUES
 (1,1,'ACTIVE','DEFERRED_LINK',NULL,NULL,'s1','s1',0,'MATCHED',1),
 (2,2,'ACTIVE','DEFERRED_LINK',NULL,NULL,'s2','s2',1,'MATCHED',2),
 (3,3,'ACTIVE','DEFERRED_LINK',NULL,NULL,'s3','s3',0,'BGG_VARIANT_REVIEW',3),
 (4,4,'ACTIVE','DEFERRED_LINK',NULL,NULL,'s4','s4',0,'MATCHED',4),
 (5,5,'ACTIVE','DEFERRED_LINK',NULL,NULL,'s5','s5',0,'MATCHED',5);
INSERT INTO deals VALUES ('s4','MATCH_UNCERTAIN');
INSERT INTO observations VALUES ('s1',100),('s2',100),('s3',100),('s4',100),('s5',100);
""")

actionable = db.execute("""
SELECT l.id FROM market_listings l JOIN games g ON g.id=l.game_id
LEFT JOIN deals d ON d.signature=COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint)
WHERE l.lifecycle='ACTIVE' AND l.enrichment_state='DEFERRED_LINK'
  AND (l.vinted_url IS NULL OR l.vinted_url='')
  AND g.database_visible=1 AND g.rating>=6.0
  AND g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND g.match_state='MATCHED'
  AND COALESCE(l.manual_review_required,0)=0
  AND l.match_state<>'BGG_VARIANT_REVIEW'
  AND COALESCE(d.verification_state,'') NOT IN ('BGG_VARIANT_REVIEW','MATCH_UNCERTAIN','PRICE_ANOMALY','EXPANSION_CHECK')
  AND COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) IN
      (SELECT signature FROM observations WHERE observed_at>=0 AND observed_at<=200)
ORDER BY l.first_seen
""").fetchall()
assert actionable == [(1,)], actionable

print("PASS deferred counts and promotion share engine review/identity guards")
print("PASS SQLite fixture excludes manual, variant, uncertain, and below-rating rows")
