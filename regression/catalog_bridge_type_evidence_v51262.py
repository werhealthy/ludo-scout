#!/usr/bin/env python3
import re
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MARKET = (ROOT / "app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")

match = re.search(r'private static final String BGG_PRODUCT_TYPE_EVIDENCE_SQL\s*=\s*(.*?);\s*\n', MARKET, re.S)
if not match:
    raise AssertionError("BGG product compatibility still ignores canonical observations without a deal row")
parts = re.findall(r'"((?:\\\\.|[^"\\\\])*)"', match.group(1))
sql = "".join(part.replace(r'\\"', '"').replace(r'\\\\', '\\') for part in parts)

db = sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE market_listings(id INTEGER PRIMARY KEY, game_id INTEGER, legacy_signature TEXT, temp_fingerprint TEXT, lifecycle TEXT);
CREATE TABLE observations(id INTEGER PRIMARY KEY, signature TEXT, observed_at INTEGER, listing_type TEXT);
INSERT INTO market_listings VALUES(1,42,'sig-base','tmp-base','ACTIVE');
INSERT INTO observations VALUES(1,'sig-base',100,'UNCERTAIN');
INSERT INTO observations VALUES(2,'sig-base',200,'BASE_GAME');
INSERT INTO market_listings VALUES(2,42,'sig-exp','tmp-exp','ACTIVE');
INSERT INTO observations VALUES(3,'sig-exp',300,'EXPANSION');
""")

# BASE_GAME wins over expansion and over older/uncertain evidence, matching the existing deal policy.
row = db.execute(sql, (42,)).fetchone()
assert row == ("BASE_GAME",), row

db.execute("DELETE FROM observations WHERE listing_type='BASE_GAME'")
row = db.execute(sql, (42,)).fetchone()
assert row == ("EXPANSION",), row

print("PASS BGG product type can use the latest canonical observation without a legacy deal")
