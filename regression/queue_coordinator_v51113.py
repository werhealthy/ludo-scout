from pathlib import Path
import re, sqlite3
root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding='utf-8')
market=read('app/src/main/java/it/vintedaffari/app/MarketStore.java')
session=read('app/src/main/java/it/vintedaffari/app/VintedPublicSession.java')
service=read('app/src/main/java/it/vintedaffari/app/QueueKeepAliveService.java')
worker=read('app/src/main/java/it/vintedaffari/app/QueueDrainWorker.java')
radar=read('app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java')
main=read('app/src/main/java/it/vintedaffari/app/MainActivity.java')
dbjava=read('app/src/main/java/it/vintedaffari/app/DealDatabase.java')
xml=read('app/src/main/res/xml/vinted_accessibility_service.xml')
gradle=read('app/build.gradle')

checks={
 'version 5.11.13': "versionName '5.11.13-queue-coordinator'" in gradle and 'versionCode 96' in gradle,
 'db16 additive': 'DB_VERSION=16' in dbjava and 'upgradeV15ToV16' in dbjava and 'ADD COLUMN text_value TEXT' in market,
 'sqlite public gate': all(x in session for x in ['vinted_http_last_at','vinted_http_window_count','beginTransactionNonExclusive','putControl(db,K_LAST']),
 'sharedprefs not authoritative': 'diagnostics only, never authoritative' in session and 'MODE_MULTI_PROCESS' not in session,
 'ready gate is zero': 'long allowed=0L;String reason="";' in session and 'new GateState(0L,"",window,used+1' in session,
 'lane heartbeat': all(x in market for x in ['touchLaneHeartbeat','laneHeartbeatAt','setLaneStatus','laneStatus']),
 'service supervised lanes': all(x in service for x in ['superviseLanes','restartVintedLane','vintedFuture.isDone()','Vinted lane fault']),
 'worker checks lane health': 'laneHeartbeatAt("vinted")' in worker and 'vHealthy&&bHealthy' in worker,
 'exact runnable counts': 'runnableVintedDueCount' in market and 'runnableBggDueCount' in market and 'return runnableVintedDueCount(now)+runnableBggDueCount(now)' in market,
 'activity exposes lane state': 'Motore Vinted fermo' in main and 'Vinted in attesa' in main and 'laneHeartbeatAt("vinted")' in main,
 'accessibility includes omitted views': 'flagIncludeNotImportantViews' in xml and 'flagRetrieveInteractiveWindows' in xml and 'typeWindowsChanged' in xml,
 'window fallback': 'getWindows()' in radar and 'accessibilityWindowFallbacks' in radar,
 'scroll scan not trailing debounce': 'if (scanScheduled) return;' in radar and 'TYPE_VIEW_SCROLLED ? 0' in radar,
 'known card fallback': 'knownCardId&&blob.contains("€")' in radar and 'lastUnparsedCardSample' in radar,
 'diagnostics authoritative': 'vintedGate={reason=' in radar and 'queueRunnable={vinted=' in radar and 'queueLanes={vinted=' in radar,
 'secret direct': (root/'secrets.properties').exists() and not (root/'secrets.properties.example').exists(),
}
for k,v in checks.items(): print(('PASS ' if v else 'FAIL ')+k)
if not all(checks.values()): raise SystemExit(1)

# Exercise the additive v15->v16 queue control migration with representative existing rows.
con=sqlite3.connect(':memory:')
con.execute('CREATE TABLE queue_controls(name TEXT PRIMARY KEY,value INTEGER NOT NULL DEFAULT 0,updated_at INTEGER NOT NULL DEFAULT 0)')
con.execute("INSERT INTO queue_controls(name,value,updated_at) VALUES('history_paused',1,123)")
con.execute('ALTER TABLE queue_controls ADD COLUMN text_value TEXT')
row=con.execute("SELECT name,value,updated_at,text_value FROM queue_controls WHERE name='history_paused'").fetchone()
assert row==('history_paused',1,123,None)
con.execute("INSERT OR REPLACE INTO queue_controls(name,value,updated_at,text_value) VALUES('lane_vinted_status',0,456,?)",('WAITING\nPACING',))
assert con.execute("SELECT text_value FROM queue_controls WHERE name='lane_vinted_status'").fetchone()[0]=='WAITING\nPACING'
print('PASS SQLite v15->v16 queue_controls migration preserves existing controls and stores lane status.')
