#!/usr/bin/env python3
import re
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MARKET = (ROOT / "app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
RUNNER = (ROOT / "app/src/main/java/it/vintedaffari/app/QueueJobRunner.java").read_text(encoding="utf-8")

match = re.search(r'private static final String CATALOG_BRIDGE_BACKFILL_SQL\s*=\s*(.*?);\s*\n', MARKET, re.S)
if not match:
    raise AssertionError("Existing complete canonical rows still have no bounded Catalog recovery")
parts = re.findall(r'"((?:\\\\.|[^"\\\\])*)"', match.group(1))
sql = "".join(part.replace(r'\\"', '"').replace(r'\\\\', '\\') for part in parts)

db = sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE games(id INTEGER PRIMARY KEY,bgg_id TEXT,rating REAL,database_visible INTEGER,match_state TEXT);
CREATE TABLE market_listings(id INTEGER PRIMARY KEY,game_id INTEGER,legacy_signature TEXT,temp_fingerprint TEXT,lifecycle TEXT,vinted_item_id TEXT,vinted_url TEXT,enrichment_state TEXT,match_state TEXT,manual_review_required INTEGER,last_seen INTEGER);
CREATE TABLE deals(id INTEGER PRIMARY KEY,signature TEXT,vinted_item_id TEXT);
CREATE TABLE processing_jobs(id INTEGER PRIMARY KEY,listing_id INTEGER,job_type TEXT,source TEXT,state TEXT);
CREATE TABLE queue_controls(name TEXT PRIMARY KEY,value INTEGER,updated_at INTEGER,text_value TEXT);
INSERT INTO games VALUES(1,'101',7.2,1,'MATCHED'),(2,'102',5.9,1,'MATCHED'),(3,'103',7.0,1,'MATCHED'),(4,'104',7.0,1,'MATCHED'),(5,'105',7.0,1,'MATCHED');
INSERT INTO market_listings VALUES
 (1,1,'sig-1','tmp-1','ACTIVE','901','https://www.vinted.it/items/901','COMPLETE','MATCHED',0,100),
 (2,2,'sig-2','tmp-2','ACTIVE','902','https://www.vinted.it/items/902','COMPLETE','MATCHED',0,200),
 (3,3,'sig-3','tmp-3','ACTIVE','903','https://www.vinted.it/items/903','COMPLETE','MATCHED',1,300),
 (4,4,'sig-4','tmp-4','ACTIVE','904','https://www.vinted.it/items/904','CORE_COMPLETE','MATCHED',0,400),
 (5,5,'sig-5','tmp-5','ACTIVE','905','https://www.vinted.it/items/905','COMPLETE','MATCHED',0,500);
INSERT INTO deals VALUES(1,'sig-4','904');
INSERT INTO processing_jobs VALUES(1,5,'VINTED_ENRICHMENT','AUTO','PENDING');
""")

assert [row[0] for row in db.execute(sql, (20,))] == [1]

# The query is intentionally bounded and newest-first.
db.execute("DELETE FROM processing_jobs")
assert [row[0] for row in db.execute(sql, (1,))] == [5]
db.execute("INSERT INTO queue_controls VALUES('catalog_bridge_seen:5',5,0,'PRICE_REJECTED')")
assert [row[0] for row in db.execute(sql, (1,))] == [1]

assert "materializeCanonicalCatalogBatch" in MARKET
assert "materializeCanonicalCatalogBatch" in RUNNER
assert "last_catalog_bridge_v51262" in RUNNER

print("PASS existing canonical rows recover locally in a bounded, idempotent batch")
