#!/usr/bin/env python3
import sqlite3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
receiver=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueWakeReceiver.java").read_text(encoding="utf-8")
worker=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueDrainWorker.java").read_text(encoding="utf-8")
service=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueKeepAliveService.java").read_text(encoding="utf-8")
engine=(ROOT/"app/src/main/java/it/vintedaffari/app/JsGameEngine.java").read_text(encoding="utf-8")
market=(ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
radar=(ROOT/"app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
crash=(ROOT/"app/src/main/java/it/vintedaffari/app/ProcessCrashJournal.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

# Executable model: while an older Motore run is active, newer pending listings must not consume
# the analysis engine. This is the state observed on Pixel (old run pending + one waiting scroll).
db=sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE observations(signature TEXT, observed_at INTEGER);
CREATE TABLE market_listings(
  id INTEGER PRIMARY KEY, legacy_signature TEXT, temp_fingerprint TEXT,
  enrichment_state TEXT, last_seen INTEGER,
  vinted_title TEXT, brand TEXT, item_condition TEXT,
  current_price_cents INTEGER, protected_price_cents INTEGER,
  favorites INTEGER, observed_text TEXT
);
""")
db.executemany("INSERT INTO observations VALUES(?,?)",[
    ("old-a",1000),("old-b",1100),("new-a",5000),("new-b",5100)
])
db.executemany("INSERT INTO market_listings VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",[
    (1,"old-a","old-a","PENDING_ANALYSIS",1000,"old a","","",1000,None,None,""),
    (2,"old-b","old-b","PENDING_ANALYSIS",1100,"old b","","",1000,None,None,""),
    (3,"new-a","new-a","PENDING_ANALYSIS",5000,"new a","","",1000,None,None,""),
    (4,"new-b","new-b","PENDING_ANALYSIS",5100,"new b","","",1000,None,None,""),
])
active=(900,1200)
rows=db.execute("""
SELECT legacy_signature FROM market_listings
WHERE enrichment_state='PENDING_ANALYSIS'
AND COALESCE(NULLIF(legacy_signature,''),temp_fingerprint) IN
 (SELECT signature FROM observations WHERE observed_at>=? AND observed_at<=?)
ORDER BY last_seen DESC LIMIT ?
""",(active[0],active[1],40)).fetchall()
assert [r[0] for r in rows]==["old-b","old-a"], rows

oncreate=service[service.index("@Override public void onCreate()"):service.index("@Override public int onStartCommand")]
onstart=service[service.index("@Override public int onStartCommand"):service.index("private synchronized void superviseLanes")]
pulse=service[service.index("private final Runnable notificationPulse"):service.index("private void runSupervisorPulse")]
action_now=receiver[receiver.index("ACTION_NOW.equals"):receiver.index("ACTION_RECOVERY.equals")]
worker_head=worker[worker.index("Context context = getApplicationContext()"):worker.index("market.resetStaleProcessingOlderThan")]

checks=[
    ("ACTION_NOW does not fall through to WorkManager", "QueueKeepAliveService.ensureRunning(context);" in action_now and "return;" in action_now and "scheduleLocal" not in action_now),
    ("Worker stands down before opening queue DB when service owns it", "if (QueueKeepAliveService.isRunning()) return Result.success();" in worker_head and worker_head.index("isRunning") < worker_head.index("new DealDatabase")),
    ("service claims ownership before async DB startup", "RUNNING=true" in oncreate and "supervisorExecutor.execute(this::initializeOwner)" in oncreate and "new DealDatabase" not in oncreate),
    ("onStartCommand performs no direct SQLite maintenance", "scheduleOwnerMaintenance" in onstart and "sweepMissing" not in onstart and "touchProcessorHeartbeat" not in onstart),
    ("main-thread pulse only dispatches supervisor work", "supervisorExecutor.execute" in pulse and "market." not in pulse),
    ("JS engine retries readiness instead of one-shot failure", "READY_TIMEOUT_MS = 30_000L" in engine and "READY_RETRY_MS = 500L" in engine and "main.postDelayed(this::verifyEngine" in engine and "retryVerifyOrFail" in engine),
    ("active Motore run owns pending analysis", "DealDatabase.ObservationSession active=helper.activeObservationSession()" in market and "SELECT signature FROM observations WHERE observed_at>=? AND observed_at<=?" in market),
    ("RAM queue cannot bypass active-run classifier ownership", "marketStore.pendingAnalysisCards(40)" in radar and "new ArrayList<>(pendingForAnalysis.values())" in radar and "freshForAnalysis.add(card)" not in radar and "waitingObservationSessionCount()>0" in radar),
    ("exit diagnostics distinguish current installed build", "build=system-exit-v3" in crash and "lastUpdateTime" in crash and "crashBuild=" in crash and "anrBuild=" in crash),
    ("build invariants preserved", "applicationId 'it.vintedaffari.app'" in build and "1000000 + ciVersionCode.toInteger()" in build),
]
for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("Queue owner/ANR/engine recovery regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} queue owner/ANR/engine recovery guards")
