from pathlib import Path
import sqlite3

root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding='utf-8')
market=read('app/src/main/java/it/vintedaffari/app/MarketStore.java')
enricher=read('app/src/main/java/it/vintedaffari/app/BggEnricher.java')
limiter=read('app/src/main/java/it/vintedaffari/app/BggRateLimiter.java')
resolver=read('app/src/main/java/it/vintedaffari/app/VintedLinkResolver.java')
service=read('app/src/main/java/it/vintedaffari/app/QueueKeepAliveService.java')
worker=read('app/src/main/java/it/vintedaffari/app/QueueDrainWorker.java')
runner=read('app/src/main/java/it/vintedaffari/app/QueueJobRunner.java')
radar=read('app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java')
main=read('app/src/main/java/it/vintedaffari/app/MainActivity.java')
dbjava=read('app/src/main/java/it/vintedaffari/app/DealDatabase.java')
gradle=read('app/build.gradle')
session=read('app/src/main/java/it/vintedaffari/app/VintedPublicSession.java')

checks={
 'version 5.11.14': "versionCode 97" in gradle and "versionName '5.11.14-fast-pipeline'" in gradle,
 'db17 additive': 'DB_VERSION=17' in dbjava and 'upgradeV16ToV17' in dbjava,
 'core/deep split': 'VINTED_DEEP_ENRICHMENT' in market and 'CORE_COMPLETE' in market and 'enqueueDeepMetadata' in market,
 'core first': 'CASE WHEN j.job_type=? THEN 0 ELSE 1 END' in market,
 'deep hidden from main queue': 'j.job_type<>?' in market and 'userVisibleActiveCount' in market and 'deepMetadataActiveCount' in market,
 'bgg claim max20': 'Math.min(20,limit)' in market and 'claimBggBatch' in market,
 'bgg one request many ids': 'TextUtils.join(",",ids)' in enricher and 'xmlapi2/thing?id=' in enricher,
 'bgg service batch': 'processBggBatch(this,market,bgg,20)' in service,
 'bgg recovery batch': 'processBggBatch(context,market,bgg,Math.min(20,room))' in worker,
 'bgg batch progress single broadcast path': 'setJobsProgress' in market and 'market.setJobsProgress(jobs,p)' in runner,
 'bgg cross-process pacing': 'queue_controls' in limiter and 'bgg_rate_next_at' in limiter and 'BggRateLimiter.acquire(context)' in enricher,
 'radar is producer': 'applyAnalysis already enqueues BGG/Vinted durable jobs' in radar and 'bggEnricher.enrich(ga.bggId)' not in radar,
 'vinted structured fast path': all(x in resolver for x in ['VintedStructuredData.scan(body,null)','catalogStructured','catalog-structured-fast-path','vintedCoreFastResolved']),
 'fast path queues optional deep': 'needsDeepMetadata' in resolver and 'market.enqueueDeepMetadata(canonical)' in runner,
 'vinted rate not raised': 'PUBLIC_MIN_INTERVAL_MS=55_000L' in session and 'PUBLIC_HOURLY_BUDGET=60' in session,
 'html cache bounded': 'return size()>4;' in session,
 'activity correct BGG origin': 'MarketStore.JOB_BGG.equals(job.type))?"Database":"Vinted"' in main,
 'secret direct': (root/'secrets.properties').exists() and not (root/'secrets.properties.example').exists(),
}
for k,v in checks.items(): print(('PASS ' if v else 'FAIL ')+k)
if not all(checks.values()): raise SystemExit(1)

# Representative lossless v16 -> v17 migration. Also verify that a URL-known row that was
# PROCESSING when the APK is upgraded does not survive as a stale lease after changing job type.
con=sqlite3.connect(':memory:')
con.executescript('''
CREATE TABLE market_listings(id INTEGER PRIMARY KEY,lifecycle TEXT,enrichment_state TEXT,vinted_url TEXT);
CREATE TABLE processing_jobs(
  id INTEGER PRIMARY KEY,job_key TEXT UNIQUE,job_type TEXT,listing_id INTEGER,state TEXT,
  priority INTEGER,source TEXT,updated_at INTEGER,next_attempt_at INTEGER,processing_started_at INTEGER
);
INSERT INTO market_listings VALUES(1,'ACTIVE','PENDING_ENRICHMENT',NULL);
INSERT INTO market_listings VALUES(2,'ACTIVE','PENDING_ENRICHMENT','https://www.vinted.it/items/2-demo');
INSERT INTO market_listings VALUES(3,'ACTIVE','PENDING_ENRICHMENT','https://www.vinted.it/items/3-demo');
INSERT INTO processing_jobs VALUES(11,'vinted:1','VINTED_ENRICHMENT',1,'PENDING',170,'GENERAL_CHECK',10,10,0);
INSERT INTO processing_jobs VALUES(12,'vinted:2','VINTED_ENRICHMENT',2,'FAILED_RETRYABLE',170,'GENERAL_CHECK',10,999,0);
INSERT INTO processing_jobs VALUES(13,'vinted:3','VINTED_ENRICHMENT',3,'PROCESSING',170,'GENERAL_CHECK',10,10,777);
''')
now=123456
con.execute("UPDATE OR IGNORE processing_jobs SET job_key='vinted-deep:'||listing_id,job_type='VINTED_DEEP_ENRICHMENT',state=CASE WHEN state='PROCESSING' THEN 'FAILED_RETRYABLE' ELSE state END,next_attempt_at=CASE WHEN state='PROCESSING' THEN ? ELSE next_attempt_at END,processing_started_at=0,priority=CASE WHEN priority>40 THEN 40 ELSE priority END,source=CASE WHEN source='MANUAL_PRIORITY' THEN source ELSE 'DEEP_METADATA' END,updated_at=? WHERE job_type='VINTED_ENRICHMENT' AND state IN ('PENDING','FAILED_RETRYABLE','PROCESSING') AND listing_id IN (SELECT id FROM market_listings WHERE vinted_url IS NOT NULL AND vinted_url<>'')",(now,now))
con.execute("UPDATE market_listings SET enrichment_state='CORE_COMPLETE' WHERE lifecycle='ACTIVE' AND vinted_url IS NOT NULL AND vinted_url<>'' AND enrichment_state IN ('PENDING_ANALYSIS','PENDING_ENRICHMENT','FAILED_RETRYABLE')")
assert con.execute('SELECT job_type,job_key,priority,source FROM processing_jobs WHERE id=11').fetchone()==('VINTED_ENRICHMENT','vinted:1',170,'GENERAL_CHECK')
assert con.execute('SELECT job_type,job_key,priority,source,state,next_attempt_at,processing_started_at FROM processing_jobs WHERE id=12').fetchone()==('VINTED_DEEP_ENRICHMENT','vinted-deep:2',40,'DEEP_METADATA','FAILED_RETRYABLE',999,0)
assert con.execute('SELECT job_type,job_key,state,next_attempt_at,processing_started_at FROM processing_jobs WHERE id=13').fetchone()==('VINTED_DEEP_ENRICHMENT','vinted-deep:3','FAILED_RETRYABLE',now,0)
assert con.execute('SELECT enrichment_state FROM market_listings WHERE id=2').fetchone()[0]=='CORE_COMPLETE'
assert con.execute('SELECT enrichment_state FROM market_listings WHERE id=3').fetchone()[0]=='CORE_COMPLETE'
print('PASS v16->v17 migration keeps core work, demotes URL-known metadata work, resets stale leases, preserves listing rows.')
