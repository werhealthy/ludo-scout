"""Execute production SQL gates against SQLite; no Vinted traffic."""
from pathlib import Path
import re, sqlite3
s=(Path(__file__).resolve().parents[1]/'app/src/main/java/it/vintedaffari/app/MarketStore.java').read_text(encoding='utf-8')
db=sqlite3.connect(':memory:')
db.executescript("CREATE TABLE processing_jobs(job_type,state,source); INSERT INTO processing_jobs VALUES('VINTED_DEEP_ENRICHMENT','PENDING','SELLER_BACKFILL');")
parking=re.search(r'String automatic="([^"]+)";',s).group(1)
assert db.execute('SELECT COUNT(*) FROM processing_jobs WHERE '+parking,('VINTED_LINK','VINTED_DEEP_ENRICHMENT','PENDING','FAILED_RETRYABLE','HISTORICAL')).fetchone()[0]==0, 'seller job must survive idle parking'
gate=re.search(r'String runGate=test2bOwner\?"":"([^"]+)";',s).group(1)
# The idle arm is the real source allow-list in the production claim query.
idle=re.search(r'\(\? = 0 AND (j.source IN \([^)]+\))\)',gate).group(1)
assert db.execute('SELECT COUNT(*) FROM processing_jobs j WHERE '+idle).fetchone()[0]==1, 'idle seller job must be claimable'
for source in ('AUTO','DEEP_METADATA','DEFERRED_LINK'):
 db.execute('UPDATE processing_jobs SET source=?',(source,))
 assert db.execute('SELECT COUNT(*) FROM processing_jobs j WHERE '+idle).fetchone()[0]==0
 assert db.execute('SELECT COUNT(*) FROM processing_jobs WHERE '+parking,('VINTED_LINK','VINTED_DEEP_ENRICHMENT','PENDING','FAILED_RETRYABLE','HISTORICAL')).fetchone()[0]==1
print('PASS seller idle SQL gates and ordinary parking')

# Active Motore must never claim backfill, even for an observation in the run.
assert "? > 0 AND j.source<>'SELLER_BACKFILL'" in gate
for method in ('runnableVintedDueCount','oldestRunnableVintedAgeMs','nextRunnableVintedDueAt'):
 section=(Path(__file__).resolve().parents[1]/'app/src/main/java/it/vintedaffari/app/MarketStore.java').read_text(encoding='utf-8').split(method+'(',1)[1].split('\n    }',1)[0]
 assert "'CATALOG_HEALTH','SELLER_BACKFILL')" in section
# Execute the exact unpark selection: previous network attempts/other reasons stay terminal.
source=(Path(__file__).resolve().parents[1]/'app/src/main/java/it/vintedaffari/app/MarketStore.java').read_text(encoding='utf-8')
where=re.search(r'db.update\("processing_jobs",unparkSeller,"([^"]+)"',source).group(1)
x=sqlite3.connect(':memory:')
x.executescript("CREATE TABLE market_listings(id,lifecycle,seller_id); INSERT INTO market_listings VALUES(1,'ACTIVE',''); CREATE TABLE processing_jobs(id,listing_id,source,state,attempt,last_error,updated_at); INSERT INTO processing_jobs VALUES(1,1,'SELLER_BACKFILL','COMPLETE',0,'parked: nessuno scroll attivo',0),(2,1,'SELLER_BACKFILL','COMPLETE',1,'parked: nessuno scroll attivo',0),(3,1,'SELLER_BACKFILL','COMPLETE',0,'done',0),(4,1,'AUTO','COMPLETE',0,'parked: nessuno scroll attivo',0);")
assert x.execute('SELECT id FROM processing_jobs WHERE '+where,('SELLER_BACKFILL','COMPLETE','parked: nessuno scroll attivo')).fetchall()==[(1,)]
print('PASS never-attempted recovery; attempted/other jobs preserved')

x.execute("INSERT INTO processing_jobs VALUES(5,1,'SELLER_BACKFILL','PENDING',0,'',0)")
assert x.execute('SELECT id FROM processing_jobs WHERE '+where,('SELLER_BACKFILL','COMPLETE','parked: nessuno scroll attivo')).fetchall()==[]
print('PASS recovery respects serial maintenance owner')

# Execute the diagnostic partition against mutually exclusive exclusion fixtures.
section=source.split('private void recordSellerBackfillExclusions',1)[1]
query=re.search(r'String sql="([^"]+)"',section).group(1)
z=sqlite3.connect(':memory:')
z.executescript("CREATE TABLE market_listings(id,lifecycle,seller_id,vinted_item_id,vinted_url); CREATE TABLE queue_controls(name,value); CREATE TABLE processing_jobs(listing_id,state); INSERT INTO market_listings VALUES(1,'ACTIVE','123','1','url'),(2,'ACTIVE','','',''),(3,'ACTIVE','','3','url'),(4,'ACTIVE','','4','url'),(5,'ACTIVE','','5','url'),(6,'ACTIVE','','6','url'); INSERT INTO queue_controls VALUES('browser_listing:3',1),('seller_backfill_once:4',1); INSERT INTO processing_jobs VALUES(5,'PENDING');")
assert dict(z.execute(query))==dict(HAS_SELLER=1,NO_IDENTITY=1,ONCE_MARKER=1,ACTIVE_JOB=1,ELIGIBLE=2)
print('PASS scheduling exclusions SQLite partition')

# Browser provenance must not suppress seller recovery. Execute the exact production candidate query.
candidate=re.search(r'Long sellerBackfill=scalarLong\(db,\s*"(SELECT l.id.*?LIMIT 1)",\s*new String\[\]\{SELLER_BACKFILL_MARKER_PREFIX,PENDING,PROCESSING,FAILED_RETRYABLE\}',source,re.S).group(1)
candidate=re.sub(r'//[^\n]*','',candidate)
candidate=re.sub(r'"\s*\+\s*"','',candidate)
b=sqlite3.connect(':memory:')
b.executescript("CREATE TABLE market_listings(id,lifecycle,seller_id,vinted_item_id,vinted_url,last_seen); CREATE TABLE queue_controls(name,value); CREATE TABLE processing_jobs(listing_id,state); INSERT INTO market_listings VALUES(71,'ACTIVE','','71','url',1),(72,'ACTIVE','','72','url',2); INSERT INTO queue_controls VALUES('browser_listing:72',1);")
assert b.execute(candidate,('seller_backfill_once:','PENDING','PROCESSING','FAILED_RETRYABLE')).fetchall()==[(72,)]
# Other maintenance still owns its browser gate.
health=source.split('long cutoff=',1)[1].split('if(listingId!=null)',1)[0]
assert "browser_listing:'||l.id" in health
print('PASS browser-owned ACTIVE listing is eligible only for seller backfill')

# The serial claim guard must not be held forever by legacy PROCESSING rows that lack
# processing_started_at. Execute the exact production watchdog WHERE clause.
watchdog_where=re.search(r'deferStuckVintedProcessing.*?update\("processing_jobs",v,"([^\"]+)"',source,re.S).group(1)
w=sqlite3.connect(':memory:')
w.executescript("CREATE TABLE processing_jobs(id,job_type,state,processing_started_at,updated_at); INSERT INTO processing_jobs VALUES(1,'VINTED_DEEP_ENRICHMENT','PROCESSING',0,100),(2,'VINTED_DEEP_ENRICHMENT','PROCESSING',0,900),(3,'VINTED_LINK','PROCESSING',100,900),(4,'VINTED_LINK','PROCESSING',900,100);")
rows=w.execute('SELECT id FROM processing_jobs WHERE '+watchdog_where,('VINTED_LINK','VINTED_DEEP_ENRICHMENT','PROCESSING','500')).fetchall()
assert rows==[(1,),(3,)], rows
print('PASS stale Vinted watchdog recovers missing-start leases without touching fresh work')

import sys,io,contextlib
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from seller_runtime_audit import report
z=sqlite3.connect(':memory:')
z.executescript("CREATE TABLE market_listings(id,lifecycle,seller_id,seller_name,legacy_signature,temp_fingerprint); INSERT INTO market_listings VALUES(1,'ACTIVE','10','PRIVATE_USER','a','a'),(2,'ACTIVE','10','PRIVATE_USER','b','b'),(3,'ACTIVE','','','c','c'); CREATE TABLE processing_jobs(id,listing_id,job_type,source,state,attempt,next_attempt_at,last_error,updated_at,processing_started_at); CREATE TABLE queue_controls(name,value,updated_at,text_value);")
output=io.StringIO()
with contextlib.redirect_stdout(output): report(z)
assert 'seller_coverage=2/3' in output.getvalue() and 'same_seller_pairs=1' in output.getvalue() and 'PRIVATE_USER' not in output.getvalue()
print('PASS audit counts and seller-value privacy')
