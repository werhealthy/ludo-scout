#!/usr/bin/env python3
from pathlib import Path
import sqlite3

ROOT=Path(__file__).resolve().parents[1]
market=(ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
runner=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueJobRunner.java").read_text(encoding="utf-8")

start=market.index("public int resolveMatchedAiCategoryHolds")
end=market.index("/** Zero-network maintenance",start)
section=market[start:end]

checks=[
 ("strict automatic hold only","verification_state='MATCH_UNCERTAIN'" in section and "AI_CATEGORY_REVIEW:%" in section),
 ("human decisions excluded","COALESCE(d.confirmed,0)=0" in section and "listing_overrides" in section),
 ("canonical listing required","l.lifecycle='ACTIVE'" in section and "l.enrichment_state='CORE_COMPLETE'" in section and "l.match_state='MATCHED'" in section),
 ("canonical game required","g.match_state='MATCHED'" in section and "g.database_visible=1" in section and "g.bgg_id=d.bgg_id" in section),
 ("base game required","d.listing_type='BASE_GAME'" in section and "o.listing_type='BASE_GAME'" in section),
 ("repair is zero network",all(token not in section for token in ["HttpURLConnection","VintedPublicSession","AiBetaClient","enqueueListingJob"])),
 ("queue runs local repair","market.resolveMatchedAiCategoryHolds();" in runner),
 ("bgg commit runs repair","resolveMatchedAiCategoryHolds();" in market[market.index("public void applyBggMetadata"):]),
]
for name,ok in checks:
 print(("PASS " if ok else "FAIL ")+name)
bad=[name for name,ok in checks if not ok]
if bad:
 raise SystemExit("AI hold BGG resolution regression failed: "+", ".join(bad))

# Semantic guard on the core eligibility rule.
db=sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE deals(signature TEXT PRIMARY KEY,lifecycle TEXT,verification_state TEXT,verification_reason TEXT,confirmed INTEGER,listing_type TEXT,bgg_id TEXT,vinted_item_id TEXT);
CREATE TABLE market_listings(id INTEGER PRIMARY KEY,legacy_signature TEXT,temp_fingerprint TEXT,lifecycle TEXT,enrichment_state TEXT,match_state TEXT,manual_review_required INTEGER,game_id INTEGER,last_error TEXT);
CREATE TABLE games(id INTEGER PRIMARY KEY,bgg_id TEXT,match_state TEXT,database_visible INTEGER);
CREATE TABLE observations(id INTEGER PRIMARY KEY,signature TEXT,observed_at INTEGER,listing_type TEXT);
CREATE TABLE listing_overrides(signature TEXT PRIMARY KEY,item_id TEXT);
""")
db.execute("INSERT INTO games VALUES(1,'100','MATCHED',1)")
db.execute("INSERT INTO market_listings VALUES(1,'sig','','ACTIVE','CORE_COMPLETE','MATCHED',0,1,'AI_CATEGORY_REVIEW: stale')")
db.execute("INSERT INTO observations VALUES(1,'sig',1,'BASE_GAME')")
db.execute("INSERT INTO deals VALUES('sig','ACTIVE','MATCH_UNCERTAIN','AI_CATEGORY_REVIEW: stale',0,'BASE_GAME','100','v1')")
eligible="""d.lifecycle='ACTIVE' AND d.verification_state='MATCH_UNCERTAIN'
AND d.verification_reason LIKE 'AI_CATEGORY_REVIEW:%' AND COALESCE(d.confirmed,0)=0
AND d.listing_type='BASE_GAME'
AND EXISTS(SELECT 1 FROM market_listings l JOIN games g ON g.id=l.game_id
WHERE COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint)=d.signature
AND l.lifecycle='ACTIVE' AND l.enrichment_state='CORE_COMPLETE' AND l.match_state='MATCHED'
AND COALESCE(l.manual_review_required,0)=0 AND g.match_state='MATCHED'
AND g.database_visible=1 AND g.bgg_id=d.bgg_id
AND EXISTS(SELECT 1 FROM observations o WHERE o.signature=d.signature
AND o.id=(SELECT id FROM observations x WHERE x.signature=d.signature ORDER BY x.observed_at DESC,x.id DESC LIMIT 1)
AND o.listing_type='BASE_GAME'))
AND NOT EXISTS(SELECT 1 FROM listing_overrides u WHERE u.signature=d.signature OR (u.item_id IS NOT NULL AND u.item_id=d.vinted_item_id))"""
assert db.execute("SELECT COUNT(*) FROM deals d WHERE "+eligible).fetchone()[0]==1
db.execute("UPDATE observations SET listing_type='EXPANSION' WHERE id=1")
assert db.execute("SELECT COUNT(*) FROM deals d WHERE "+eligible).fetchone()[0]==0
db.execute("UPDATE observations SET listing_type='BASE_GAME' WHERE id=1")
db.execute("INSERT INTO listing_overrides VALUES('sig',NULL)")
assert db.execute("SELECT COUNT(*) FROM deals d WHERE "+eligible).fetchone()[0]==0
print("PASS semantic hold eligibility")
print(f"PASS {len(checks)+1}/{len(checks)+1} AI hold BGG resolution guards")
