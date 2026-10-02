"""Execute the production browser DDL/queries in SQLite; not an Android lifecycle test."""
from pathlib import Path
import json, re, sqlite3, unittest

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'app/src/main/java/it/vintedaffari/app/BrowserCaptureSql.java'

def literals():
    text=SRC.read_text() if SRC.exists() else ''
    return {name:json.loads(value) for name,value in re.findall(r'public static final String (\w+)\s*=\s*("(?:\\.|[^"\\])*")\s*;',text)}

class BrowserSqlTest(unittest.TestCase):
    def setUp(self):
        self.db=sqlite3.connect(':memory:')
        self.sql=literals()
        self.db.execute('CREATE TABLE legacy_sentinel(id INTEGER PRIMARY KEY, value TEXT)')
        self.db.execute("INSERT INTO legacy_sentinel VALUES(1,'favorite/reset/history')")
        for name,value in self.sql.items():
            if name.startswith('CREATE_'):self.db.execute(value)

    def test_staging_preserves_legacy_and_unknown_price(self):
        names={r[0] for r in self.db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        self.assertIn('browser_candidates',names,'capture has no durable staging')
        self.assertEqual(self.db.execute('SELECT value FROM legacy_sentinel').fetchone()[0],'favorite/reset/history')
        self.db.execute("INSERT INTO browser_candidates(item_id,url,title,revision,observed_at,state) VALUES('101','https://www.vinted.it/items/101','Azul',1,10,'INCOMPLETE')")
        self.assertIsNone(self.db.execute("SELECT price_cents FROM browser_candidates WHERE item_id='101'").fetchone()[0])

    def test_same_text_distinct_ids_and_membership_dedupe(self):
        self.assertIn('CREATE_CANDIDATES',self.sql,'per-ID staging missing')
        self.db.execute("INSERT INTO browser_captures(id,url,started_at,updated_at,state) VALUES(1,'https://www.vinted.it/catalog',10,10,'CAPTURING')")
        for item in ['101','102']:
            self.db.execute("INSERT INTO browser_candidates(item_id,url,title,price_cents,revision,observed_at,state) VALUES(?,?,?,1000,1,10,'QUEUED')",(item,'https://www.vinted.it/items/'+item,'Azul'))
            self.db.execute(self.sql['UPSERT_MEMBERSHIP'],(1,item,1,10,10,1,'{}'))
            self.db.execute(self.sql['UPSERT_MEMBERSHIP'],(1,item,1,10,20,1,'{}'))
            self.db.execute(self.sql['UPDATE_MEMBERSHIP'],(1,20,1,'{}',1,item))
        self.assertEqual(self.db.execute('SELECT COUNT(*) FROM browser_candidates').fetchone()[0],2)
        self.assertEqual(self.db.execute('SELECT COUNT(*) FROM browser_capture_items').fetchone()[0],2)
        self.assertEqual(self.db.execute('SELECT MAX(last_observed_at) FROM browser_capture_items').fetchone()[0],20)

    def test_job_lease_is_part_of_production_current_guard(self):
        source=(ROOT/'app/src/main/java/it/vintedaffari/app/BrowserCaptureStore.java').read_text()
        body=source.split('public boolean isCurrent(',1)[1].split('public boolean complete(',1)[0]
        query=json.loads(re.search(r'rawQuery\(("(?:\\.|[^"\\])*")',body).group(1))
        self.db.execute("CREATE TABLE processing_jobs(id INTEGER PRIMARY KEY, job_type TEXT, state TEXT, processing_started_at INTEGER)")
        self.db.execute("INSERT INTO processing_jobs VALUES(8,'BROWSER_ANALYSIS','PROCESSING',30)")
        self.db.execute("INSERT INTO browser_candidates(item_id,url,title,revision,claimed_revision,observed_at,state,lease_started_at) VALUES('101','https://www.vinted.it/items/101','Azul',2,2,10,'ANALYZING',30)")
        args=('101','2','2','30','8','30')[:query.count('?')]
        self.assertIsNotNone(self.db.execute(query,args).fetchone())
        self.db.execute("UPDATE processing_jobs SET state='PENDING',processing_started_at=0 WHERE id=8")
        self.assertIsNone(self.db.execute(query,args).fetchone(),'a revoked processing-job lease must invalidate an old result')

    def test_generic_startup_recovery_preserves_local_owner(self):
        source=(ROOT/'app/src/main/java/it/vintedaffari/app/MarketStore.java').read_text()
        body=source.split('if(ageMs==Long.MAX_VALUE) helper.getWritableDatabase().update(',1)[1].split('else helper.',1)[0]
        query=json.loads(re.search(r', v, ("(?:\\.|[^"\\])*")',body).group(1))
        self.db.execute("CREATE TABLE processing_jobs(id INTEGER PRIMARY KEY, job_type TEXT, state TEXT, updated_at INTEGER)")
        self.db.execute("INSERT INTO processing_jobs VALUES(8,'BROWSER_ANALYSIS','PROCESSING',30)")
        self.db.execute("UPDATE processing_jobs SET state='FAILED_RETRYABLE' WHERE "+query,('PROCESSING',))
        self.assertEqual(self.db.execute("SELECT state FROM processing_jobs WHERE id=8").fetchone()[0],'PROCESSING')

    def test_capture_history_counts_membership_not_canonical_or_time_window(self):
        self.assertIn('CAPTURE_HEADERS',self.sql,'history has no per-capture reader')
        for capture in [1,2]:
            self.db.execute("INSERT INTO browser_captures(id,url,started_at,updated_at,state) VALUES(?, 'https://www.vinted.it/catalog',10,10,'CAPTURING')",(capture,))
        self.db.execute("INSERT INTO browser_candidates(item_id,url,title,price_cents,revision,observed_at,state) VALUES('101','https://www.vinted.it/items/101','Azul',1000,1,10,'QUEUED')")
        for capture in [1,2]:self.db.execute(self.sql['UPSERT_MEMBERSHIP'],(capture,'101',1,10,10,1,'{}'))
        rows=self.db.execute(self.sql['CAPTURE_HEADERS'],(50,)).fetchall()
        self.assertEqual(len(rows),2)
        self.assertEqual([r[5] for r in rows],[1,1])
        self.assertEqual([r[6] for r in rows],[1,1])

    def test_other_jobs_exclude_current_capture_but_keep_older_work(self):
        self.assertIn('OTHER_JOB_WHERE',self.sql,'other-job count and list lack a shared scoped predicate')
        self.db.execute("CREATE TABLE processing_jobs(id INTEGER,job_key TEXT,state TEXT,listing_id INTEGER,game_id INTEGER)")
        self.db.execute("CREATE TABLE market_listings(id INTEGER,game_id INTEGER)")
        self.db.execute("INSERT INTO browser_candidates(item_id,url,title,revision,observed_at,state) VALUES('101','https://www.vinted.it/items/101','Azul',1,10,'QUEUED')")
        self.db.execute(self.sql['UPSERT_MEMBERSHIP'],(1,'101',1,10,10,1,'{}'))
        self.db.execute("INSERT INTO processing_jobs VALUES(1,'browser_analysis:101','PENDING',NULL,NULL)")
        self.db.execute("INSERT INTO processing_jobs VALUES(2,'vinted_deep:old','PENDING',12,NULL)")
        self.db.execute("INSERT INTO processing_jobs VALUES(3,'browser_analysis:102','PENDING',NULL,NULL)")
        result=self.db.execute("SELECT j.id FROM processing_jobs j WHERE "+self.sql['OTHER_JOB_WHERE'],(1,)).fetchall()
        self.assertEqual(result,[(2,),(3,)])

    def test_stale_completion_cannot_finish_new_revision_or_lease(self):
        self.assertIn('COMPLETE_CURRENT',self.sql,'revision guard missing')
        self.db.execute("INSERT INTO browser_candidates(item_id,url,title,price_cents,revision,observed_at,state,lease_started_at) VALUES('101','https://www.vinted.it/items/101','Azul',1000,2,10,'ANALYZING',30)")
        self.assertEqual(self.db.execute(self.sql['COMPLETE_CURRENT'],('READY','',1,40,'101',1,30)).rowcount,0)
        self.assertEqual(self.db.execute(self.sql['COMPLETE_CURRENT'],('READY','',2,40,'101',2,20)).rowcount,0)
        self.assertEqual(self.db.execute(self.sql['COMPLETE_CURRENT'],('READY','',2,40,'101',2,30)).rowcount,1)

if __name__=='__main__':unittest.main()
