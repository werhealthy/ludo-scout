#!/usr/bin/env python3
import sqlite3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
service=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueKeepAliveService.java").read_text(encoding="utf-8")
scheduler=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueWorkScheduler.java").read_text(encoding="utf-8")
receiver=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueWakeReceiver.java").read_text(encoding="utf-8")
worker=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueDrainWorker.java").read_text(encoding="utf-8")
deal=(ROOT/"app/src/main/java/it/vintedaffari/app/DealDatabase.java").read_text(encoding="utf-8")
crash=(ROOT/"app/src/main/java/it/vintedaffari/app/ProcessCrashJournal.java").read_text(encoding="utf-8")
diag=(ROOT/"app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

# Real SQLite fixture for the stale duplicate-pending trap observed on Pixel:
# old builds left multiple raw PENDING_ANALYSIS observations for one signature even though the
# canonical market listing had already advanced. Motore must follow canonical listing state.
db=sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE observations(id INTEGER PRIMARY KEY, signature TEXT, observed_at INTEGER, analysis_status TEXT, verification_state TEXT);
CREATE TABLE market_listings(id INTEGER PRIMARY KEY, legacy_signature TEXT, temp_fingerprint TEXT, lifecycle TEXT, enrichment_state TEXT);
INSERT INTO observations VALUES(1,'dup',1000,'pending','PENDING_ANALYSIS');
INSERT INTO observations VALUES(2,'dup',1100,'pending','PENDING_ANALYSIS');
INSERT INTO observations VALUES(3,'dup',1200,'done','OK');
INSERT INTO observations VALUES(4,'live',1300,'pending','PENDING_ANALYSIS');
INSERT INTO market_listings VALUES(10,'dup','dup','ACTIVE','COMPLETE');
INSERT INTO market_listings VALUES(11,'live','live','ACTIVE','PENDING_ANALYSIS');
""")
sql="""SELECT COUNT(DISTINCT l.id)
FROM observations o
JOIN market_listings l ON COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint)=o.signature
WHERE o.observed_at>=? AND o.observed_at<=?
  AND l.lifecycle='ACTIVE' AND l.enrichment_state='PENDING_ANALYSIS'"""
pending=db.execute(sql,(900,1400)).fetchone()[0]
assert pending==1, f"stale raw pending rows leaked into Motore progress: {pending}"
db.execute("UPDATE market_listings SET enrichment_state='COMPLETE' WHERE id=11")
assert db.execute(sql,(900,1400)).fetchone()[0]==0, "completed canonical listings must drain Motore analysis pending"

oncreate=service[service.index("@Override public void onCreate()"):service.index("private void initializeBackground")]
onstart=service[service.index("@Override public int onStartCommand"):service.index("private synchronized void superviseLanes")]
cold_start=worker.index("if(QueueKeepAliveService.isStarting()){")
cold_start_return=worker.index("return Result.success();",cold_start)
work_start=worker.index("market.resetStaleProcessingOlderThan")
checks=[
    ("foreground startup keeps heavy initialization off main callback", "startForeground(" in oncreate and "controlExecutor.execute(this::initializeBackground)" in oncreate and "new DealDatabase" not in oncreate and "reconcileQueue" not in oncreate),
    ("onStartCommand acknowledges before serialized maintenance", "controlExecutor.execute(()->" in onstart and "QueueJobRunner.sweepMissing" in onstart and "return START_STICKY" in onstart),
    ("supervisor pulse runs on control executor", "controlExecutor.schedule(this,8_000L,TimeUnit.MILLISECONDS)" in service and "Handler" not in service and "Looper" not in service),
    ("worker records and then stands down during service cold start", "state=SKIPPED_STARTING" in worker and cold_start < cold_start_return < work_start),
    ("receiver uses goAsync for WorkManager scheduling", "goAsync()" in receiver and "WAKE_EXEC.execute" in receiver and "scheduleLocal(app)" in receiver),
    ("default-process scheduler dispatches main-thread calls", "runLocalOffMain" in scheduler and "Looper.myLooper()==Looper.getMainLooper()" in scheduler and "SCHEDULER_EXEC.execute" in scheduler),
    ("Motore analysis pending follows canonical listing state", "l.enrichment_state='PENDING_ANALYSIS'" in deal and "COUNT(DISTINCT l.id)" in deal),
    ("exit diagnostics expose current-install stability", "build=system-exit-v4" in crash and "installBoundary=" in crash and "crashAfterInstall=" in crash and "anrAfterInstall=" in crash and "memoryAfterInstall=" in crash and "resourceAfterInstall=" in crash),
    ("waiting run telemetry is exposed", "engineWaiting={" in diag and "engineWaitingSummary" in diag),
    ("build invariants preserved", "applicationId 'it.vintedaffari.app'" in build and "1000000 + ciVersionCode.toInteger()" in build),
]
for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("WorkManager/main-thread stability regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} WorkManager/main-thread stability guards")
