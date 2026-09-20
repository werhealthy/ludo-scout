#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
deal=(ROOT/"app/src/main/java/it/vintedaffari/app/DealDatabase.java").read_text(encoding="utf-8")
market=(ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
radar=(ROOT/"app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
ui=(ROOT/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

MIN=10*60_000
BASE=5*60_000
UNIT=55_000
def target(valid):
    return max(MIN,BASE+max(0,valid)*UNIT)

assert target(0)==10*60_000
assert target(5)==10*60_000
assert target(10)==850_000
assert target(20)==1_400_000
assert target(100)==5_800_000

auto_start=deal.index("public static boolean engineAutomaticDone")
auto_end=deal.index("private static void createOverrides",auto_start)
auto=deal[auto_start:auto_end]
observe_start=market.index("public int observeEngineTiming")
observe_end=market.index("public int vintedActiveCount",observe_start)
observe=market[observe_start:observe_end]

checks=[
    ("small-scroll target remains ten minutes", "ENGINE_RUN_TARGET_MIN_MS=10L*60_000L" in deal),
    ("timing scales with eligible workload", "ENGINE_RUN_TARGET_BASE_MS=5L*60_000L" in deal and "ENGINE_RUN_REMOTE_UNIT_MS=55_000L" in deal and "engineTargetMs" in deal),
    ("time alone cannot complete a run", "engineSlaExpired" not in auto and "engineContentSettled(s)" in auto),
    ("timing observer is non-destructive", "AUTO_FILTERED" not in observe and "USER_HIDDEN" not in observe and "nonDestructive=true" in observe),
    ("diagnostics expose target instead of fixed cutoff", "targetMs=" in radar and "targetRemainingMs=" in radar and "timingNonDestructive=true" in radar),
    ("UI calls timing an estimate", 'state=automaticRemaining+" ancora in automatico · stima "+mins+" min"' in ui),
    ("UI no longer promises a ten-minute hard cutoff", "chiusura entro" not in ui and "non può bloccarli oltre 10 minuti" not in ui),
    ("build invariants preserved", "applicationId 'it.vintedaffari.app'" in build and "1000000 + ciVersionCode.toInteger()" in build),
]
for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("5.12.24 adaptive timing regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} adaptive timing guards")
