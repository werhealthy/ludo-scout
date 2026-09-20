from pathlib import Path
root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding='utf-8')
market=read('app/src/main/java/it/vintedaffari/app/MarketStore.java')
runner=read('app/src/main/java/it/vintedaffari/app/QueueJobRunner.java')
radar=read('app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java')
sched=read('app/src/main/java/it/vintedaffari/app/QueueWorkScheduler.java')
manifest=read('app/src/main/AndroidManifest.xml')
main=read('app/src/main/java/it/vintedaffari/app/MainActivity.java')
db=read('app/src/main/java/it/vintedaffari/app/DealDatabase.java')
checks={
 'db14':'DB_VERSION=14' in db,
 'progress column':'ADD COLUMN progress' in market and 'progress INTEGER NOT NULL DEFAULT 0' in market,
 'legacy sightings archived':'historical sighting preserved; live lookup deferred until seen again' in market,
 'ambiguous bounded':'isDeterministicMiss' in runner and 'job.attempt >= 2' in runner and 'needsReview' in runner,
 'radar no queue claim':'claimNextVintedJob' not in radar[radar.index('private void pumpPersistentMarketJobs'):radar.index('private void updatePersistentMaintenanceUi')],
 'bundle yields':'core Vinted queue has priority' in radar,
 'multiprocess forwarding':'QueueWakeReceiver' in sched and 'isDefaultProcess' in sched,
 'radar isolated':'android:process=":radar"' in manifest and 'android:process=":ui"' in manifest,
 'card fill':'applyJobCardFill' in main and 'ClipDrawable' in main,
 'completed cards':'recentCompletedJobs' in main,
}
for k,v in checks.items(): print(('PASS' if v else 'FAIL'),k)
raise SystemExit(0 if all(checks.values()) else 1)
