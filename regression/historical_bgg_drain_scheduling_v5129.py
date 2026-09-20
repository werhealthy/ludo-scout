#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
service = (ROOT / "app/src/main/java/it/vintedaffari/app/QueueKeepAliveService.java").read_text(encoding="utf-8")
worker = (ROOT / "app/src/main/java/it/vintedaffari/app/QueueDrainWorker.java").read_text(encoding="utf-8")
market = (ROOT / "app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
reval = (ROOT / "app/src/main/java/it/vintedaffari/app/BggHistoricalRevalidator.java").read_text(encoding="utf-8")
build = (ROOT / "app/build.gradle").read_text(encoding="utf-8")

loop_start = service.index("private void bggLoop()")
loop_end = service.index("private int resolveLocalBggMatches", loop_start)
loop = service[loop_start:loop_end]

checks = [
    ("service liveness counts historical pending", "historicalBggRevalidationPendingCount()" in service[service.index("notificationPulse"):service.index("public static boolean isRunning")]),
    ("BGG supervisor restarts for historical pending", "bggNeeds=market.runnableBggDueCount(now)>0||market.bggMatchRequiredCount()>0||market.historicalBggRevalidationPendingCount()>0" in service),
    ("current identity work precedes historical", loop.index("resolveLocalBggMatches(8)") < loop.index("BggHistoricalRevalidator.runSlice")),
    ("current enrichment work precedes historical", loop.index("QueueJobRunner.processBggBatch") < loop.index("BggHistoricalRevalidator.runSlice")),
    ("historical burst requires no current match work", "remainingCurrent<=0&&historicalPending>0" in loop),
    ("historical burst remains bounded", "BggHistoricalRevalidator.runSlice(market,bggMatcher,24)" in loop and "Math.min(32,limit)" in reval),
    ("historical loop yields between bursts", 'sleep(350L);continue;' in loop),
    ("WorkManager health includes historical lane", "hp=market.historicalBggRevalidationPendingCount()" in worker and "(bd<=0&&hp<=0)" in worker),
    ("WorkManager reschedules while history remains", "market.historicalBggRevalidationPendingCount() > 0" in worker),
    ("per-game broadcasts suppressed in bulk", "independentlyVerified,false" in reval and "notifyHistoricalBggRevalidationChanged()" in reval),
    ("one coalesced notification API exists", "public void notifyHistoricalBggRevalidationChanged(){notifyQueueChanged();}" in market),
    ("pending count matches executable active listings", "public int historicalBggRevalidationPendingCount()" in market and "l.lifecycle='ACTIVE'" in market[market.index("public int historicalBggRevalidationPendingCount()"):market.index("public String historicalBggRevalidationSummary()")]),
    ("beta version bumped", "5.12.9-bgg-historical-drain" in build),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
if failed:
    raise SystemExit("Historical BGG drain scheduling regression failed: " + ", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} historical BGG drain scheduling guards")
