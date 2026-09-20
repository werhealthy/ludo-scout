#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
service=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueKeepAliveService.java").read_text(encoding="utf-8")
crash=(ROOT/"app/src/main/java/it/vintedaffari/app/ProcessCrashJournal.java").read_text(encoding="utf-8")
engine=(ROOT/"app/src/main/java/it/vintedaffari/app/JsGameEngine.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

oncreate=service[service.index("@Override public void onCreate()"):service.index("private void initializeOffMainThread")]
onstart=service[service.index("@Override public int onStartCommand"):service.index("private synchronized void superviseLanes")]
pulse=service[service.index("private final Runnable notificationPulse"):service.index("public static boolean isRunning")]
init=service[service.index("private void initializeOffMainThread"):service.index("@Override public int onStartCommand")]
supervisor=service[service.index("private void runSupervisorPass"):service.index("public static boolean isRunning")]

checks=[
    ("foreground service main-thread onCreate is DB-free",
     "new DealDatabase" not in oncreate and "reconcileQueue" not in oncreate and "sweepMissing" not in oncreate and "supervisorExecutor.execute(this::initializeOffMainThread)" in oncreate),
    ("Service.onStartCommand is DB-free",
     "sweepMissing" not in onstart and "touchProcessorHeartbeat" not in onstart and "requestSupervisorPass(true)" in onstart),
    ("periodic notification pulse delegates instead of querying SQLite",
     "requestSupervisorPass(false)" in pulse and "jobSummary()" not in pulse and "reconcileQueue()" not in pulse),
    ("queue initialization is serialized off main",
     "new DealDatabase(this)" in init and "market.reconcileQueue()" in init and "QueueJobRunner.sweepMissing" in init),
    ("supervisor pass owns recurring DB maintenance",
     "market.touchProcessorHeartbeat()" in supervisor and "market.reconcileQueue()" in supervisor and "maybeNotifyNextRunComplete" in supervisor),
    ("supervisor is single-flight",
     "AtomicBoolean supervisorPassQueued" in service and "compareAndSet(false,true)" in service and "supervisorPassQueued.set(false)" in service),
    ("ANR diagnostics capture bounded main-thread trace",
     "getTraceInputStream()" in crash and "latestMainTrace=" in crash and "mainTraceSnippet" in crash and "scanned++<1200" in crash),
    ("cold WebView engine readiness retries are bounded",
     "MAX_VERIFY_ATTEMPTS = 45" in engine and "VERIFY_RETRY_MS = 2_000L" in engine and "verifyInFlight" in engine and "postDelayed(this::verifyEngine" in engine),
    ("engine readiness watchdog cannot duplicate retry chains",
     "ready || verifyInFlight" in engine and "verifyInFlight=true" in engine and "verifyInFlight=false" in engine),
    ("build invariants preserved",
     "applicationId 'it.vintedaffari.app'" in build and "1000000 + ciVersionCode.toInteger()" in build),
]

failed=[name for name,ok in checks if not ok]
for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
if failed:
    raise SystemExit("Default-process ANR stability regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} default-process ANR stability guards")
