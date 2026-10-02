"""Read production evidence SQL without changing retained or archived rows."""
import json
import re
import sqlite3
from pathlib import Path

source=(Path(__file__).resolve().parents[1]/'app/src/main/java/it/vintedaffari/app/MarketStore.java').read_text()
def sql(name):
    m=re.search(r'private static final String '+name+r'\s*=\s*(.*?);\s*\n',source,re.S)
    assert m, 'missing read-only reset evidence query: '+name
    return ''.join(json.loads(x) for x in re.findall(r'"(?:[^"\\]|\\.)*"',m.group(1)))
listing_sql=sql('RESET_LISTING_EVIDENCE_SQL')
observation_sql=sql('RESET_OBSERVATION_EVIDENCE_SQL')
db=sqlite3.connect(':memory:')
db.executescript('''CREATE TABLE market_listings(lifecycle TEXT,vinted_url TEXT); CREATE TABLE observations(observed_at INTEGER);
INSERT INTO market_listings VALUES('ACTIVE','https://vinted.it/items/1'),('ACTIVE',''),('RESET_LEGACY',NULL),('RESET_LEGACY',''),('AUTO_FILTERED',NULL),(NULL,NULL);
INSERT INTO observations VALUES(99),(100),(200),(200);
''')
before=db.total_changes
rows=db.execute(listing_sql).fetchall()
assert rows==[('ACTIVE',2,1),('AUTO_FILTERED',1,1),('RESET_LEGACY',2,2),('UNKNOWN_STATE',1,1)],rows
assert db.execute(observation_sql,('100',)).fetchone()==(4,99,200,3)
assert db.total_changes==before, 'diagnostics must not write'
db.execute('DELETE FROM observations')
assert db.execute(observation_sql,('100',)).fetchone()==(0,None,None,None)
db.execute('DELETE FROM market_listings')
assert db.execute(listing_sql).fetchall()==[]
print('PASS reset evidence: retained/archived/missing URL, inclusive observation bounds, empty DB, read-only')
marker_sql=sql('RESET_MARKER_EVIDENCE_SQL')
db.execute('CREATE TABLE queue_controls(name TEXT,value INTEGER,updated_at INTEGER,text_value TEXT)')
assert db.execute(marker_sql).fetchall()==[], 'absent record must remain absent'
db.execute('INSERT INTO queue_controls VALUES(?,?,?,?)',('diag:fresh_start_reset',7,300,'jobs=1, observations=2, listings=4'))
changes=db.total_changes
assert db.execute(marker_sql).fetchone()==(7,300,'jobs=1, observations=2, listings=4')
assert db.total_changes==changes
print('PASS SQLite reset marker absent/present, original timestamp and mixed units preserved')
