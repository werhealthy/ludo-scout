from pathlib import Path
import sqlite3

root=Path(__file__).resolve().parents[1]
java=root/'app/src/main/java/it/vintedaffari/app'
market=(java/'MarketStore.java').read_text()
main=(java/'MainActivity.java').read_text()
service=(java/'VintedAccessibilityService.java').read_text()
gate=(java/'BoardGameIntakeGate.java').read_text()
queue=(java/'QueueJobRunner.java').read_text()
hunts=(java/'HuntDatabase.java').read_text()
policy=(java/'DealPolicy.java').read_text()
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
gradle=(root/'app/build.gradle').read_text()

checks={
 'version': "versionName '5.11.25-live-stability'" in gradle and 'versionCode 108' in gradle,
 'watergate collision seed': '"watergate"' in gate and 'seededCollisionTitle' in gate,
 'matched BGG still product-gated': 'matchedAnalysis(card,ga,collisionRisk)' in service and 'quarantineCollisionObservation' in service,
 'collision gate uses publisher/brand context': 'analysis.productPublisher' in gate and 'card.brand' in gate,
 'strong book/media signals': '"isbn"' in gate and '"paperback"' in gate and '"autore"' in gate,
 'live lane gate': 'shouldAutoResolveVinted' in market and 'a.averageRating==null' in market and 'DealPolicy.MIN_BGG_RATING' in market,
 'ordinary observations local only': '"LOCAL_ONLY"' in market and 'liveResolve ? "PENDING_ENRICHMENT" : "LOCAL_ONLY"' in market,
 'legacy importer cannot rebuild broad Vinted backlog': 'Only a known, qualified hot deal is allowed into the scarce remote lane' in market and '"hot".equals(tier)' in market and 'DEEP_METADATA' not in market[market.index('public int enqueueMissingLegacyDeals()'):market.index('/** Quiet bounded sweep')],
 'backlog compaction exists': 'compactVintedBacklogToLiveLane' in market and '"MANUAL_PRIORITY","HUNT_PRIORITY"' in market,
 'fast lane newest first': 'CASE WHEN j.priority>=300 THEN j.created_at END DESC' in market,
 'hunt promotion': 'promoteLegacyListingForHunt' in market and 'HUNT_PRIORITY' in market and 'boolean huntHit=HuntDatabase.evaluateAndNotify' in service,
 'deep metadata deferred': 'vintedDeepDeferred' in queue and 'market.enqueueDeepMetadata(canonical)' not in queue,
 'activity separates remote/local/manual': 'Rete "+analysisTotal+" · locali "+marketStore.localOnlyListingCount()+" · manuali ' in main and 'localOnlyListingCount' in market,
 'return from Vinted keeps activity stack': 'FLAG_ACTIVITY_NEW_TASK' not in main[main.index('private void openVinted(DealRecord'):main.index('private void openBgg',main.index('private void openVinted(DealRecord'))],
 'transient UI route persisted': 'PREF_UI_SESSION' in main and 'restoreTransientRoute' in main and 'activeVintedResolutionListingId' in main,
 'tab scroll positions persisted': 'tabScrollPositions' in main and 'scroll_'+'' in main,
 'onResume does not trigger bulk market recalculation': 'refreshMarketReferencesIfStale()' not in main[main.index('@Override protected void onResume'):main.index('@Override protected void onPause')],
 'market recalculation throttled': '6L*60L*60_000L' in main,
 'market benchmark avoids long write scan': 'Do not hold a write transaction while scanning' in market and market.index('for(DealRecord d:deals)') < market.index('SQLiteDatabase db=helper.getWritableDatabase();int changed=0;db.beginTransaction()'),
 'larger bitmap cache': 'LruCache<String,Bitmap>(12*1024*1024)' in main,
 'Hunt evaluator reports hit': 'public static boolean evaluateAndNotify' in hunts,
 'rating floor still six': 'MIN_BGG_RATING = 6.0' in policy,
 'manual Vinted share preserved': 'android.intent.action.SEND' in manifest and 'handleSharedVintedUrl' in main and 'applySharedVintedLink' in main,
 'publication date never replaced by observation date': 'Data pubblicazione n/d' in main and 'Osservato il ' not in main,
 'advanced filter state still persisted': 'catalogMinRatingFilter' in main and 'databaseMinRating' in main and 'databaseMaxPrice' in main,
 'v51124 review quarantine preserved': 'quarantineLegacyBggReviewBacklog' in market and 'v51124ReviewBacklogReset' in main,
}
for name,ok in checks.items():
    if not ok: raise AssertionError(name)

# SQL-level behavior for the one-time backlog compaction. This mirrors the predicates used by
# MarketStore.compactVintedBacklogToLiveLane and catches the dangerous case where an old sweep
# leaves ordinary observations in the remote queue.
db=sqlite3.connect(':memory:')
db.executescript('''
CREATE TABLE deals(signature TEXT PRIMARY KEY,lifecycle TEXT,tier TEXT,rating REAL);
CREATE TABLE market_listings(id INTEGER PRIMARY KEY,legacy_signature TEXT,lifecycle TEXT,vinted_url TEXT,enrichment_state TEXT);
CREATE TABLE processing_jobs(id INTEGER PRIMARY KEY,job_type TEXT,listing_id INTEGER,state TEXT,source TEXT,priority INTEGER,next_attempt_at INTEGER,updated_at INTEGER,processing_started_at INTEGER,last_error TEXT,progress INTEGER,created_at INTEGER);
''')
P='PENDING'; R='FAILED_RETRYABLE'; X='PROCESSING'; F='FAILED_PERMANENT'; C='COMPLETE'
JOB='VINTED_ENRICHMENT'; DEEP='VINTED_DEEP_ENRICHMENT'
rows=[
 # id, tier, rating, job source, type
 (1,'normal',7.5,'AUTO_MISSING',JOB),
 (2,'hot',7.5,'AUTO_MISSING',JOB),
 (3,'hot',None,'AUTO_MISSING',JOB),
 (4,'hot',5.8,'AUTO_MISSING',JOB),
 (5,'normal',7.5,'MANUAL_PRIORITY',JOB),
 (6,'normal',7.5,'HUNT_PRIORITY',JOB),
 (7,'hot',7.5,'AUTO_MISSING',DEEP),
]
for i,tier,rating,source,typ in rows:
    sig=f's{i}'
    db.execute('INSERT INTO deals VALUES(?,?,?,?)',(sig,'ACTIVE',tier,rating))
    db.execute('INSERT INTO market_listings VALUES(?,?,?,?,?)',(i,sig,'ACTIVE',None,'PENDING_ENRICHMENT'))
    db.execute('INSERT INTO processing_jobs VALUES(?,?,?,?,?,?,?,?,?,?,?,?)',(i,typ,i,P,source,100,0,0,0,'',0,i))

done=(C,100,0,123,0,'local-only: remote Vinted non necessario')
db.execute('''UPDATE processing_jobs SET state=?,progress=?,next_attempt_at=?,updated_at=?,processing_started_at=?,last_error=?
 WHERE job_type IN (?,?) AND state IN (?,?,?,?) AND source NOT IN (?,?)
 AND NOT EXISTS (SELECT 1 FROM market_listings l LEFT JOIN deals d ON d.signature=l.legacy_signature
 WHERE l.id=processing_jobs.listing_id AND d.lifecycle='ACTIVE' AND d.tier='hot' AND d.rating IS NOT NULL AND d.rating>=?)''',
 (*done,JOB,DEEP,P,R,X,F,'MANUAL_PRIORITY','HUNT_PRIORITY',6.0))
db.execute('''UPDATE processing_jobs SET state=?,progress=?,next_attempt_at=?,updated_at=?,processing_started_at=?,last_error=?
 WHERE job_type=? AND state IN (?,?,?,?) AND source NOT IN (?,?)''',(*done,DEEP,P,R,X,F,'MANUAL_PRIORITY','HUNT_PRIORITY'))
db.execute('''UPDATE processing_jobs SET priority=320,source='LIVE_DEAL',next_attempt_at=0,updated_at=123
 WHERE job_type=? AND state IN (?,?) AND listing_id IN
 (SELECT l.id FROM market_listings l JOIN deals d ON d.signature=l.legacy_signature
 WHERE d.lifecycle='ACTIVE' AND d.tier='hot' AND d.rating>=?)''',(JOB,P,R,6.0))
state={i:db.execute('SELECT state,source,priority FROM processing_jobs WHERE id=?',(i,)).fetchone() for i in range(1,8)}
assert state[1][0]==C, state
assert state[2]==(P,'LIVE_DEAL',320), state
assert state[3][0]==C and state[4][0]==C, state
assert state[5][0]==P and state[5][1]=='MANUAL_PRIORITY', state
assert state[6][0]==P and state[6][1]=='HUNT_PRIORITY', state
assert state[7][0]==C, state

# Newest-first only inside high-priority live lane; manual/hunt/hot work beats old generic work.
db2=sqlite3.connect(':memory:')
db2.execute('CREATE TABLE j(id INT,priority INT,created_at INT,next_attempt_at INT)')
for r in [(1,320,10,0),(2,320,20,0),(3,100,1,0),(4,340,5,0)]: db2.execute('INSERT INTO j VALUES(?,?,?,?)',r)
order=[x[0] for x in db2.execute('SELECT id FROM j ORDER BY priority DESC, CASE WHEN priority>=300 THEN created_at END DESC,next_attempt_at ASC,created_at ASC')]
assert order==[4,2,1,3],order

print('PASS live stability 5.11.25:',len(checks),'static checks + SQL compaction/priority checks')
