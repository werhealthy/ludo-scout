#!/usr/bin/env python3
import re
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
market = (ROOT / "app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")

claim_start = market.index("private Job claimNextVintedJobInternal")
claim_end = market.index("public Job claimNextBggJob", claim_start)
claim = market[claim_start:claim_end]

gate_match = re.search(r'String runGate=test2bOwner\?"":"([^"]+)";', claim)
order_match = re.search(r'"ORDER BY ([^"]+) LIMIT 1";', claim)
if not gate_match or not order_match:
    raise AssertionError("Cannot extract the production Vinted claim query")

run_gate = gate_match.group(1)
order_by = order_match.group(1)

# The fixture reproduces the phone state: an old active scroll owns ordinary core work,
# while returning from a different exact Vinted page has queued an OPENED_VERIFY job.
db = sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE market_listings(
  id INTEGER PRIMARY KEY,
  lifecycle TEXT NOT NULL,
  legacy_signature TEXT,
  temp_fingerprint TEXT
);
CREATE TABLE processing_jobs(
  id INTEGER PRIMARY KEY,
  job_key TEXT,
  job_type TEXT,
  listing_id INTEGER,
  game_id INTEGER,
  state TEXT,
  attempt INTEGER,
  next_attempt_at INTEGER,
  last_error TEXT,
  priority INTEGER,
  source TEXT,
  created_at INTEGER
);
CREATE TABLE observations(signature TEXT, observed_at INTEGER);
""")
db.executemany(
    "INSERT INTO market_listings(id,lifecycle,legacy_signature,temp_fingerprint) VALUES(?,?,?,?)",
    [
        (1, "ACTIVE", "opened-outside-run", "opened-outside-run"),
        (2, "ACTIVE", "ordinary-current-run", "ordinary-current-run"),
    ],
)
db.executemany(
    "INSERT INTO processing_jobs VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
    [
        (1, "opened:1", "VINTED_DEEP", 1, None, "PENDING", 0, 0, "", 245, "OPENED_VERIFY", 20),
        (2, "ordinary:2", "VINTED", 2, None, "PENDING", 0, 0, "", 80, "DEFERRED_LINK", 10),
    ],
)
db.execute("INSERT INTO observations(signature,observed_at) VALUES(?,?)", ("ordinary-current-run", 150))

sql = (
    "SELECT j.id FROM processing_jobs j JOIN market_listings l ON l.id=j.listing_id "
    "WHERE j.job_type IN (?,?) AND j.state IN (?,?) AND j.next_attempt_at<=? "
    "AND l.lifecycle='ACTIVE' " + run_gate + "ORDER BY " + order_by + " LIMIT 1"
)
args = ["VINTED", "VINTED_DEEP", "PENDING", "FAILED_RETRYABLE", 1000, 100, 100, 100, 200, "VINTED"]
row = db.execute(sql, args).fetchone()

assert row == (1,), (
    "Returning from an exact Vinted page must claim OPENED_VERIFY before unrelated automatic "
    f"run work; production query selected {row}"
)

print("PASS exact opened-page verification preempts unrelated automatic run work")
