#!/usr/bin/env python3
"""Guards for copyable queue liveness diagnostics added in v5.12.77."""
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
market=(ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
service=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueKeepAliveService.java").read_text(encoding="utf-8")
worker=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueDrainWorker.java").read_text(encoding="utf-8")
diagnostics=(ROOT/"app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

supervisor=service[service.index("private synchronized void superviseLanes"):service.index("private synchronized void restartVintedLane")]
checks=[
    ("SQLite stores an independent queue supervisor heartbeat",
     'v.put("name","supervisor_heartbeat")' in market and "supervisorHeartbeatAt()" in market),
    ("heartbeat advances after the lane supervisor finishes its checks",
     "market.touchSupervisorHeartbeat();" in supervisor),
    ("worker records start, skip reasons, takeover and completion",
     all(x in worker for x in ["state=STARTED","state=SKIPPED_STARTING","state=SKIPPED_SERVICE_HEALTHY","state=TAKEOVER","state=FINISHED"])),
    ("skip diagnostics say whether work, gate or heartbeats made the service healthy",
     'vReason=!vNeeds?"NO_WORK":gateUntil>now?"GATE":"HEARTBEAT"' in worker and
     'bReason=!bNeeds?"NO_WORK":"HEARTBEAT"' in worker),
    ("copyable diagnostics show supervisor, WorkManager recovery and oldest due Vinted age",
     "queueSupervisor={" in diagnostics and "oldestRunnableVintedAgeMs=" in diagnostics and
     "marketDiag.supervisorHeartbeatAt()" in diagnostics and
     'marketDiag.diagnosticState("queue_recovery")' in diagnostics),
    ("oldest-job age uses the due state and active-listing filters",
     "oldestRunnableVintedAgeMs(long now)" in market and
     "j.state IN (?,?) AND j.next_attempt_at<=? AND l.lifecycle='ACTIVE'" in market),
    ("beta build identity advances for App Tester validation",
     "versionName '5.12.83-engine-sampling-health'" in build),
]
for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("queue liveness diagnostics regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} queue liveness diagnostic guards")
