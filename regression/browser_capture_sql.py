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
        self.assertEqual(self.db.execute('SELECT COUNT(*) FROM browser_candidates').fetchone()[0],2)
        self.assertEqual(self.db.execute('SELECT COUNT(*) FROM browser_capture_items').fetchone()[0],2)

    def test_stale_completion_cannot_finish_new_revision_or_lease(self):
        self.assertIn('COMPLETE_CURRENT',self.sql,'revision guard missing')
        self.db.execute("INSERT INTO browser_candidates(item_id,url,title,price_cents,revision,observed_at,state,lease_started_at) VALUES('101','https://www.vinted.it/items/101','Azul',1000,2,10,'ANALYZING',30)")
        self.assertEqual(self.db.execute(self.sql['COMPLETE_CURRENT'],('READY','',1,40,'101',1,30)).rowcount,0)
        self.assertEqual(self.db.execute(self.sql['COMPLETE_CURRENT'],('READY','',2,40,'101',2,20)).rowcount,0)
        self.assertEqual(self.db.execute(self.sql['COMPLETE_CURRENT'],('READY','',2,40,'101',2,30)).rowcount,1)

if __name__=='__main__':unittest.main()
