#!/usr/bin/env python3
import sqlite3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
receiver=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueWakeReceiver.java").read_text(encoding="utf-8")
worker=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueDrainWorker.java").read_text(encoding="utf-8")
market=(ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
radar=(ROOT/"app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
engine=(ROOT/"app/src/main/java/it/vintedaffari/app/JsGameEngine.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

# Executable model: a newer scroll can already be persisted while an older Motore run is active,
# but classifier work must stay on the oldest active run until it settles.
db=sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE observations(signature TEXT, observed_at INTEGER);
CREATE TABLE market_listings(
  id INTEGER PRIMARY KEY, legacy_signature TEXT, temp_fingerprint TEXT,
  lifecycle TEXT, enrichment_state TEXT, last_seen INTEGER
);
INSERT INTO observations VALUES('old-a',1000);
INSERT INTO observations VALUES('old-b',1100);
INSERT INTO observations VALUES('new-a',5000);
INSERT INTO observations VALUES('new-b',5100);
INSERT INTO market_listings VALUES(1,'old-a','old-a','ACTIVE','PENDING_ANALYSIS',1000);
INSERT INTO market_listings VALUES(2,'old-b','old-b','ACTIVE','PENDING_ANALYSIS',1100);
INSERT INTO market_listings VALUES(3,'new-a','new-a','ACTIVE','PENDING_ANALYSIS',5000);
INSERT INTO market_listings VALUES(4,'new-b','new-b','ACTIVE','PENDING_ANALYSIS',5100);
""")
active=(900,1200)
rows=db.execute("""
SELECT COALESCE(NULLIF(legacy_signature,''),temp_fingerprint)
FROM market_listings
WHERE lifecycle='ACTIVE' AND enrichment_state='PENDING_ANALYSIS'
  AND COALESCE(NULLIF(legacy_signature,''),temp_fingerprint) IN
      (SELECT signature FROM observations WHERE observed_at>=? AND observed_at<=?)
ORDER BY last_seen DESC
""",active).fetchall()
assert [x[0] for x in rows]==["old-b","old-a"], rows

action_start=receiver.index("if (QueueWorkScheduler.ACTION_NOW.equals(action))")
action_end=receiver.index("final BroadcastReceiver.PendingResult pending",action_start)
action_now=receiver[action_start:action_end]
worker_head=worker[worker.index("Context context = getApplicationContext()"):worker.index("DealDatabase db = new DealDatabase(context)")+50]
pending_start=market.index("public List<VintedCard> pendingAnalysisCards")
pending_end=market.index("/** Imports current legacy feed rows",pending_start)
pending=market[pending_start:pending_end]
scan_start=radar.index("private void scanVisibleVintedCards")
scan_end=radar.index("private void analyzeBatch",scan_start)
scan=radar[scan_start:scan_end]
continue_start=radar.index("private void continuePersistentAnalysis")
continue_end=radar.index("private void pumpPersistentMarketJobs",continue_start)
continuation=radar[continue_start:continue_end]

checks=[
    ("ACTION_NOW has foreground owner only",
     "QueueKeepAliveService.ensureRunning(app)" in action_now and "return;" in action_now and "scheduleLocal" not in action_now),
    ("WorkManager stands down before opening DB under service owner",
     "isStarting()||QueueKeepAliveService.isRunning()" in worker_head and worker_head.index("isRunning()") < worker_head.index("new DealDatabase")),
    ("pending classifier query is scoped to active Motore run",
     "DealDatabase.ObservationSession active=helper.activeObservationSession()" in pending and "SELECT signature FROM observations WHERE observed_at>=? AND observed_at<=?" in pending),
    ("RAM hints cannot directly bypass active-run ordering",
     "freshForAnalysis" not in scan and "marketStore.pendingAnalysisCards(40)" in scan and "new ArrayList<>(pendingForAnalysis.values())" in scan),
    ("waiting scroll is rechecked without another Vinted visit",
     "waitingObservationSessionCount()>0" in continuation and "postDelayed(VintedAccessibilityService.this::continuePersistentAnalysis,5_000L)" in continuation),
    ("WebView readiness has bounded retry window",
     "READY_RETRY_MS = 500L" in engine and "READY_TIMEOUT_MS = 30_000L" in engine and "retryVerifyOrFail" in engine),
    ("WebView readiness retry chain is single-flight",
     "verifyInFlight" in engine and "verifyRetryScheduled" in engine and "scheduleVerifyRetry" in engine),
    ("build invariants preserved",
     "applicationId 'it.vintedaffari.app'" in build and "1000000 + ciVersionCode.toInteger()" in build),
]
for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("Queue single-owner/engine-order regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} queue single-owner/engine-order guards")
