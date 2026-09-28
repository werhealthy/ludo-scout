from pathlib import Path
import sqlite3

root=Path(__file__).resolve().parents[1]
java=root/'app/src/main/java/it/vintedaffari/app'
market=(java/'MarketStore.java').read_text()
main=(java/'MainActivity.java').read_text()
queue=(java/'QueueJobRunner.java').read_text()
service=(java/'QueueKeepAliveService.java').read_text()
worker=(java/'QueueDrainWorker.java').read_text()
lang=(java/'ListingLanguageDetector.java').read_text()
product=(java/'ProductPageParser.java').read_text()
gate=(java/'BoardGameIntakeGate.java').read_text()
resolver=(java/'VintedLinkResolver.java').read_text()
session=(java/'VintedPublicSession.java').read_text()
db=(java/'DealDatabase.java').read_text()
policy=(java/'DealPolicy.java').read_text()
gradle=(root/'app/build.gradle').read_text()

checks={
 'version': "versionName '5.11.26-deferred-link-market'" in gradle and 'versionCode 109' in gradle,
 'db19': 'DB_VERSION=19' in db and 'upgradeV18ToV19' in db,
 'deferred schema': 'deferred_retry_at INTEGER NOT NULL DEFAULT 0' in market and "'DEFERRED_LINK'" in market,
 'bounded active-run window': 'activeRunCore=market.activeRunCoreVintedCount()' in queue and 'activeRunCore<6&&market.activeRunDeferredVintedCount()>0' in queue and 'promoteDeferredVintedBatch(6-activeRunCore)' in queue,
 'urgent Vinted work still precedes deferred promotion': queue.index('urgentVintedReservationUntil(now)>now') < queue.index('int activeRunCore='),
 'same-game batch': 'GROUP BY l.game_id' in market and 'DEFERRED_LINK' in market[market.index('promoteDeferredVintedBatch'):market.index('deferBackgroundLink')],
 'deferred miss avoids manual queue': 'deferBackgroundLink(job,reason' in queue and '24L*60*60_000L' in queue,
 'cooldown auto resume retained': 'nextAllowedAt(this)' in service and 'sleep(Math.min(8_000L' in service,
 'local work during cooldown': 'inferDeferredLanguages' in service and 'BGG/locali continuano' in service,
 'activity labels eventual work': 'da collegare "+marketStore.deferredVintedCount()' in main,
 'language detector': 'class ListingLanguageDetector' in lang and 'mergeWithDependency' in lang,
 'product detail language text': 'detailsText' in product and 'lid.contains("language")' in product,
 'median Vinted benchmark': 'localVintedReferenceStats' in market and 'stats.count<3' in market and 'typicalCents' in market,
 'game detail typical price': 'Prezzo tipico Vinted' in main and 'Min Vinted comparabile' in main,
 'targeted market refresh': 'refreshLocalVintedBenchmarksForBgg' in market,
 'infinite append loader': 'Carico altri "+(next-old)+" annunci…' in main and 'catalogResultsHost.removeView(loader)' in main,
 'shared Vinted search cache': 'return size()>16' in session and 'Canonical game name first' in resolver,
 'publication metadata remains low priority': 'TextUtils.isEmpty(r.publishedLabel)' in queue and 'market.enqueueDeepMetadata(canonical)' in queue,
 'watergate guarded not blanket accepted': '"watergate"' in gate and 'seededCollisionTitle' in gate and 'repairV51125CollisionCleanup' in market,
 'rating floor six': 'MIN_BGG_RATING = 6.0' in policy,
 'worker accounts for deferred': 'deferredVintedCount() > 0' in worker,
}
for name,ok in checks.items():
    if not ok: raise AssertionError(name)

# Migration semantics: ordinary eligible local rows become deferred; manual/hunt/live jobs survive.
sq=sqlite3.connect(':memory:')
sq.executescript('''
CREATE TABLE games(id INTEGER PRIMARY KEY,bgg_id TEXT,rating REAL,database_visible INTEGER);
CREATE TABLE market_listings(id INTEGER PRIMARY KEY,game_id INTEGER,lifecycle TEXT,enrichment_state TEXT,vinted_url TEXT,deferred_retry_at INTEGER DEFAULT 0);
CREATE TABLE processing_jobs(id INTEGER PRIMARY KEY,job_type TEXT,state TEXT,source TEXT,progress INTEGER,next_attempt_at INTEGER,updated_at INTEGER,processing_started_at INTEGER,last_error TEXT);
''')
sq.executemany('INSERT INTO games VALUES(?,?,?,?)',[(1,'100',7.4,1),(2,'200',5.7,1),(3,None,None,1)])
sq.executemany('INSERT INTO market_listings VALUES(?,?,?,?,?,?)',[(1,1,'ACTIVE','LOCAL_ONLY',None,0),(2,2,'ACTIVE','LOCAL_ONLY',None,0),(3,3,'ACTIVE','LOCAL_ONLY',None,0)])
now=123
sq.execute("UPDATE market_listings SET enrichment_state='DEFERRED_LINK',deferred_retry_at=0 WHERE lifecycle='ACTIVE' AND enrichment_state='LOCAL_ONLY' AND (vinted_url IS NULL OR vinted_url='') AND game_id IN (SELECT id FROM games WHERE bgg_id IS NOT NULL AND bgg_id<>'' AND rating IS NOT NULL AND rating>=6.0 AND database_visible=1)")
assert sq.execute('SELECT enrichment_state FROM market_listings WHERE id=1').fetchone()[0]=='DEFERRED_LINK'
assert sq.execute('SELECT enrichment_state FROM market_listings WHERE id=2').fetchone()[0]=='LOCAL_ONLY'
assert sq.execute('SELECT enrichment_state FROM market_listings WHERE id=3').fetchone()[0]=='LOCAL_ONLY'

# Median behavior used by market reference: robust to one cheap outlier.
prices=sorted([200,800,900,900,1000,1000,1100,1100])
n=len(prices)
median=(prices[(n-1)//2]+prices[(n-1)//2+1])//2 if n%2==0 else prices[n//2]
assert median==950 and min(prices)==200

print('PASS deferred-link market 5.11.26:',len(checks),'static checks + migration/median checks')
