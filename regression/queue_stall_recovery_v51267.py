#!/usr/bin/env python3
"""Regression guards for stalled queue recovery in v5.12.67."""
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
worker=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueDrainWorker.java").read_text(encoding="utf-8")
service=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueKeepAliveService.java").read_text(encoding="utf-8")
market=(ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

# Model the service-health decision that caused the field stall: a running process is not healthy
# when active Motore work exists and the corresponding lane heartbeat has been stale for hours.
def healthy(service_running, needs, gate_future, heartbeat_age_ms, processing):
    if not service_running:
        return False
    if not needs:
        return True
    if gate_future:
        return True
    if heartbeat_age_ms < 45_000:
        return True
    if processing and heartbeat_age_ms < 180_000:
        return True
    return False

assert healthy(True, True, False, 20_000, False)
assert healthy(True, True, True, 7*60*60_000, False)
assert healthy(True, True, False, 120_000, True)
assert not healthy(True, True, False, 7*60*60_000, False)
assert not healthy(True, True, False, 10*60_000, True)
assert healthy(True, False, False, 7*60*60_000, False)

worker_head=worker[worker.index("Context context = getApplicationContext()"):worker.index("market.resetStaleProcessingOlderThan")]
worker_loop=worker[worker.index("while (!isStopped()"):worker.index("} catch (Throwable t)",worker.index("while (!isStopped()"))]
supervise=service[service.index("private synchronized void superviseLanes"):service.index("private synchronized void restartVintedLane")]
active_deferred=market[market.index("public int activeRunDeferredVintedCount"):market.index("public boolean listingBelongsToActiveRun")]

checks=[
    ("diagnostics identify active queue job types and the age of their oldest lease",
     "public String processingLeaseSummary(long now)" in market and
     "processing_started_at>0 THEN processing_started_at" in market and
     "oldestAgeMs=" in market and
     '"processingLeases={"+marketDiag.processingLeaseSummary(queueNow)+"}"' in service),
    ("foreground watchdog applies a bounded stale-work lease to BGG",
     "market.deferStuckBggProcessing(180_000L,10*60_000L)" in service),
    ("BGG recovery only releases rows whose processing lease exceeded the threshold",
     "public int deferStuckBggProcessing(long maxAgeMs,long retryDelayMs)" in market and
     "job_type=? AND state=? AND processing_started_at>0 AND processing_started_at<?" in market and
     "new String[]{JOB_BGG,PROCESSING,String.valueOf(cutoff)}" in market),
    ("cold-start ownership still prevents duplicate worker startup",
     "if(QueueKeepAliveService.isStarting())return Result.success();" in worker_head),
    ("running service no longer causes unconditional WorkManager exit",
     "isStarting()||QueueKeepAliveService.isRunning()" not in worker_head and
     "if (QueueKeepAliveService.isRunning())" in worker_head),
    ("worker health uses lane heartbeat and active deferred Motore work",
     "activeRunDeferredVintedCount()" in worker_head and
     "vAge<45_000L" in worker_head and "vAge<180_000L" in worker_head and
     'setDiagnosticState("queue_recovery"' in worker_head),
    ("stale processing cannot mask a dead lane forever",
     "market.processingVintedCount()>0&&vAge<180_000L" in worker_head and
     "market.processingCount(MarketStore.JOB_BGG)>0&&bAge<180_000L" in worker_head),
    ("recovery worker materialises deferred active-run identities before claiming",
     "activeRunDeferredVintedCount()>0" in worker_loop and
     "promoteDeferredVintedBatch(6-activeRunCore)" in worker_loop and
     worker_loop.index("promoteDeferredVintedBatch") < worker_loop.index("processOneVinted")),
    ("foreground supervisor also treats active-run deferred work as liveness demand",
     "activeRunDeferredVintedCount()>0" in supervise),
    ("active deferred count is scoped to current observation session",
     "activeObservationSession()" in active_deferred and
     "observed_at>=? AND observed_at<=?" in active_deferred and
     "enrichment_state='DEFERRED_LINK'" in active_deferred),
    ("build identity and CI version strategy remain intact",
     "applicationId 'it.vintedaffari.app'" in build and
     "1000000 + ciVersionCode.toInteger()" in build),
]

for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("5.12.67 queue stall recovery regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} queue stall recovery guards")
