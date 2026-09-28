#!/usr/bin/env python3
"""Regression guards for v5.12.44 queue ownership and Activity render cadence."""
from pathlib import Path

root=Path(__file__).resolve().parents[1]
ui=(root/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
service=(root/"app/src/main/java/it/vintedaffari/app/QueueKeepAliveService.java").read_text(encoding="utf-8")

maintenance=ui[ui.index("private void startPostCreateMaintenance()"):ui.index("@Override protected void onResume()")]
overview=ui[ui.index("private void renderEngineOverview()"):ui.index("private View engineCurrentRunHero",ui.index("private void renderEngineOverview()"))]
pulse=ui[ui.index("private final Runnable activityStatusPulse"):ui.index("private static final class PhotoMatch",ui.index("private final Runnable activityStatusPulse"))]

checks=[
    ("UI process does not reconcile the shared queue", "marketStore.reconcileQueue();" not in maintenance),
    ("Activity overview does not force a full render every six seconds", "uiUpdates.postDelayed(activityStatusPulse,6_000L)" not in overview and "scheduleRender(0)" not in pulse),
    ("queue service owns reconciliation on its serial control lane", "controlExecutor.schedule(this,8_000L" in service and "market.reconcileQueue();" in service),
]
failed=[name for name,ok in checks if not ok]
for name,ok in checks: print(("PASS " if ok else "FAIL ")+name)
if failed: raise SystemExit("runtime ownership regression failed: "+", ".join(failed))
