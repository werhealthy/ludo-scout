#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
deal=(ROOT/"app/src/main/java/it/vintedaffari/app/DealDatabase.java").read_text(encoding="utf-8")
market=(ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
ui=(ROOT/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
radar=(ROOT/"app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

MIN=10*60_000
BASE=60_000
UNIT=55_000
SLICE=90_000

def target(core_work,valid=0,settled=False):
    remote=max(0,core_work)
    if remote<=0 and not settled:
        remote=max(0,valid)
    return max(MIN,BASE+remote*UNIT)

def eta(core_pending,core_remaining=0,analysis_pending=0,unresolved=0):
    if core_pending<=0 and core_remaining<=0 and analysis_pending<=0 and unresolved<=0:
        return 0
    remote=max(max(0,core_pending),max(0,core_remaining))*UNIT
    local=60_000 if analysis_pending>0 or (remote==0 and unresolved>0) else 0
    return remote+local

# Workload model: raw cards do not set the remote budget; core Vinted work does.
assert target(0,valid=5)==MIN
assert target(5)==MIN
assert target(10)==610_000
assert target(20)==1_160_000
assert target(50)==2_810_000
assert target(100)==5_560_000
assert eta(10)==550_000
assert eta(0,3)==165_000
assert eta(2)<eta(10)

# Fairness model: a run only yields after a service slice and only when another run waits.
def next_run(active,unfinished):
    later=sorted(x for x in unfinished if x>active)
    if later:
        return later[0]
    earlier=sorted(x for x in unfinished if x<active)
    return earlier[0] if earlier else None

assert next_run(100,[100]) is None
assert next_run(100,[100,200,300])==200
assert next_run(300,[100,200,300])==100

auto_start=deal.index("public static boolean engineAutomaticDone")
auto_end=deal.index("private static void createOverrides",auto_start)
auto=deal[auto_start:auto_end]
yield_start=market.index("public int yieldOverBudgetEngineRun")
yield_end=market.index("public int observeEngineTiming",yield_start)
yield_block=market[yield_start:yield_end]
timing_start=market.index("public int observeEngineTiming")
timing_end=market.index("public int vintedActiveCount",timing_start)
timing=market[timing_start:timing_end]
claim_start=market.index("private Job claimNextVintedJobInternal")
claim_end=market.index("public Job claimNextBggJob",claim_start)
claim=market[claim_start:claim_end]

checks=[
    ("small run keeps a ten-minute product target",
     "ENGINE_RUN_TARGET_MIN_MS=10L*60_000L" in deal),
    ("target uses measured core remote work rather than raw cards",
     "coreWorkListings" in deal and "corePendingListings" in deal and
     "int remote=Math.max(s.coreWorkListings,s.corePendingListings)" in deal and
     "ENGINE_RUN_TARGET_BASE_MS=60_000L" in deal),
    ("live ETA includes parked and materialised remaining core candidates",
     "public static long engineEtaMs" in deal and "s.corePendingListings" in deal and "s.coreRemainingListings" in deal),
    ("time alone cannot complete a run",
     "engineSlaExpired" not in auto and "engineContentSettled(s)" in auto),
    ("fairness is a lane yield, never a correctness decision",
     "ENGINE_RUN_FAIRNESS_SLICE_MS=90_000L" in deal and
     "nextUnfinishedObservationSessionAfter" in deal and
     "engine_run_cursor_start" in deal and
     "yieldOverBudgetEngineRun(now)" in market),
    ("yield does not hide, exclude, complete or delete listings",
     "AUTO_FILTERED" not in yield_block and "AUTO_EXCLUDED" not in yield_block and
     "USER_HIDDEN" not in yield_block and "processing_jobs" not in yield_block and
     "market_listings" not in yield_block and "delete(" not in yield_block),
    ("timing observer remains non-destructive",
     "AUTO_FILTERED" not in timing and "AUTO_EXCLUDED" not in timing and
     "nonDestructive=true" in timing),
    ("Hunt, manual and repaired cards still preempt ordinary run ownership",
     "j.source IN ('HUNT_PRIORITY','MANUAL_PRIORITY','MANUAL_RECOVERY')" in claim),
    ("Home remains trusted-only",
     'db.getDeals("trusted",320)' in ui and '"trusted".equals(filter)' in deal),
    ("Motore UI exposes remaining online candidates and continuation",
     "verifiche online ancora necessarie" in ui and "In pausa · riprenderà" in ui and
     "il Motore ruota tra i job senza scartare card" in ui),
    ("diagnostics expose ETA, core work and fairness",
     "etaMs=" in radar and "corePending=" in radar and "coreRemaining=" in radar and "engineFairness={" in radar),
    ("build identity and CI versionCode strategy stay unchanged",
     "applicationId 'it.vintedaffari.app'" in build and
     "1000000 + ciVersionCode.toInteger()" in build),
]
for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("5.12.25 adaptive fairness regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} adaptive fairness guards")
