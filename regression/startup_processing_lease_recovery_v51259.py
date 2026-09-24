#!/usr/bin/env python3
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
service = (ROOT / "app/src/main/java/it/vintedaffari/app/QueueKeepAliveService.java").read_text(encoding="utf-8")
market = (ROOT / "app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")

startup = service[service.index("private void initializeBackground()"):service.index("@Override public int onStartCommand")]

# Executable model of a package update: the old process is gone, but its durable lease and
# unrelated queued work remain. Recovery must preserve both rows and all scheduling metadata.
db = sqlite3.connect(":memory:")
db.execute("""CREATE TABLE processing_jobs(
    id INTEGER PRIMARY KEY, state TEXT, attempt INTEGER, priority INTEGER, source TEXT,
    listing_id INTEGER, next_attempt_at INTEGER, updated_at INTEGER,
    processing_started_at INTEGER, last_error TEXT, progress INTEGER
)""")
db.executemany("INSERT INTO processing_jobs VALUES(?,?,?,?,?,?,?,?,?,?,?)", [
    (1, "PROCESSING", 3, 280, "MANUAL_PRIORITY", 55, 0, 9900, 9800, "", 52),
    (2, "PENDING", 0, 80, "DEFERRED_LINK", 56, 0, 9950, 0, "", 6),
])

now = 10_000
db.execute("""UPDATE processing_jobs
              SET state='FAILED_RETRYABLE', next_attempt_at=?, updated_at=?,
                  last_error='process restart', processing_started_at=0
              WHERE state='PROCESSING'""", (now, now))

rows = db.execute("SELECT id,state,attempt,priority,source,listing_id,processing_started_at FROM processing_jobs ORDER BY id").fetchall()
assert rows == [
    (1, "FAILED_RETRYABLE", 3, 280, "MANUAL_PRIORITY", 55, 0),
    (2, "PENDING", 0, 80, "DEFERRED_LINK", 56, 0),
], rows

checks = [
    ("fresh service releases every inherited processing lease before reconciliation",
     "market.resetStaleProcessing();market.reconcileQueue()" in startup),
    ("startup no longer waits fifteen minutes to recognise a dead owner",
     "resetStaleProcessingOlderThan(15*60_000L)" not in startup),
    ("lease recovery is non-destructive and keeps retry metadata",
     "public void resetStaleProcessing()" in market and "state=?" in market[market.index("public void resetStaleProcessing()"):market.index("public Job claimNextVintedJob")]),
    ("runtime watchdog remains active for jobs that stall after startup",
     "deferStuckVintedProcessing(180_000L,10*60_000L)" in service),
]

for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)

failed = [name for name, ok in checks if not ok]
if failed:
    raise SystemExit("Startup processing-lease recovery regression failed: " + ", ".join(failed))

print(f"PASS {len(checks)}/{len(checks)} startup processing-lease recovery guards")
