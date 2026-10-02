"""Execute production SQL: local holds must not masquerade as remote work."""
import json
import re
import sqlite3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/it/vintedaffari/app/MarketStore.java').read_text()
def sql_literals(text):
    return ''.join(json.loads(x) for x in re.findall(r'"(?:[^"\\]|\\.)*"', text))
summary = sql_literals(source.split('public String engineCoreRemainingSummary()', 1)[1].split('String sql=', 1)[1].split(';\n', 1)[0])
breakdown = sql_literals(source.split('private static final String VINTED_MISSING_BREAKDOWN_SQL =', 1)[1].split(';\n', 1)[0])
db = sqlite3.connect(':memory:')
db.executescript('''
CREATE TABLE games(id INTEGER,bgg_id TEXT,match_state TEXT,rating REAL,database_visible INTEGER,canonical_name TEXT);
CREATE TABLE market_listings(id INTEGER,game_id INTEGER,lifecycle TEXT,enrichment_state TEXT,vinted_url TEXT,vinted_item_id TEXT,manual_review_required INTEGER,match_state TEXT,legacy_signature TEXT,temp_fingerprint TEXT,last_error TEXT,vinted_title TEXT,last_seen INTEGER);
CREATE TABLE deals(signature TEXT,verification_state TEXT);
CREATE TABLE observations(signature TEXT,observed_at INTEGER);
CREATE TABLE processing_jobs(id INTEGER,listing_id INTEGER,job_type TEXT,state TEXT,last_error TEXT,updated_at INTEGER,attempt INTEGER,source TEXT,next_attempt_at INTEGER);
INSERT INTO games VALUES(1,'123','MATCHED',7,1,'Test');
''')
for i, state in enumerate(['LOCAL_ONLY', 'DEFERRED_LINK', 'PENDING_ENRICHMENT', 'NEEDS_REVIEW', 'AUTO_EXCLUDED', 'AUTO_FILTERED'], 1):
    db.execute('INSERT INTO market_listings VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)', (i,1,'ACTIVE',state,None,None,0,'MATCHED',str(i),str(i),'','Test',i))
    db.execute('INSERT INTO observations VALUES(?,?)', (str(i),150))
assert [r[0] for r in db.execute(summary, ('VINTED_ENRICHMENT','6','100','200'))] == [2,3], 'local/trust holds reported as remote work'
db.execute('DELETE FROM market_listings WHERE id>3')
row = db.execute(breakdown).fetchone()
assert row == (3,0,2,0,2,0,0,0,0,0,0,0,1), row
assert row[0] == row[1]+row[2]+row[12]
assert row[2] == sum(row[3:12])
print('PASS production diagnostics separate local holds from remote work; global outcomes reconcile')

local = sql_literals(source.split('public String engineLocalOnlySummary()',1)[1].split('String sql=',1)[1].split(';\n',1)[0])
assert db.execute(local, ('6','100','200')).fetchone() == (1,)
assert db.execute(local, ('6','201','300')).fetchone() == (0,)
db.execute("INSERT INTO observations VALUES('1',160)")
assert db.execute(local, ('6','100','200')).fetchone() == (1,), 'repeated sightings must not multiply local holds'
print('PASS current-run local holds use inclusive period and deduplicate observations')
