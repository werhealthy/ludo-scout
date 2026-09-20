"""Executes the actual new migration SQL against a representative v2 Library row."""
from pathlib import Path
import re,sqlite3
root=Path(__file__).resolve().parents[1]
source=(root/'app/src/main/java/it/vintedaffari/app/LibraryDatabase.java').read_text()
old='CREATE TABLE library_games(id INTEGER PRIMARY KEY AUTOINCREMENT,bgg_id TEXT UNIQUE,name TEXT,image_url TEXT,source TEXT,paid_cents INTEGER,shipping_cents INTEGER,rating REAL,weight REAL,playtime INTEGER,acquired_at INTEGER,added_at INTEGER)'
db=sqlite3.connect(':memory:');db.execute(old)
db.execute("INSERT INTO library_games(id,bgg_id,name,source,paid_cents,shipping_cents,acquired_at,added_at) VALUES(41,'123','Existing game','Vinted',1500,300,123456,789012)")
prior=db.execute('SELECT * FROM library_games').fetchone()
for sql in re.findall(r'db\.execSQL\("(ALTER TABLE library_games ADD COLUMN [^"\n]+)"\)',source):
 if 'acquired_at' not in sql:db.execute(sql)
current=db.execute('SELECT * FROM library_games').fetchone()
assert current[:len(prior)]==prior
assert {'edition_id','edition_label','fee_cents','fee_estimated'} <= {row[1] for row in db.execute('PRAGMA table_info(library_games)')}
cols=[row[1] for row in db.execute('PRAGMA table_info(library_games)')]
assert current[cols.index('fee_estimated')]==0
assert current[cols.index('bundle_purchase')]==0
assert current[cols.index('personal_rating')] is None
assert current[cols.index('bundle_group_id')] is None
assert current[cols.index('bundle_total_cents')] is None
assert current[cols.index('collection_state')]=='owned'
assert current[cols.index('sold_reason')] is None
assert current[cols.index('sold_at')] is None
sql_source=(root/'app/src/main/java/it/vintedaffari/app/DealDatabase.java').read_text()
sql=re.search(r'db\.execSQL\("(CREATE TABLE IF NOT EXISTS listing_overrides[^"\n]+)"\)',sql_source).group(1)
db.execute(sql);db.execute(sql)
db.execute("INSERT INTO listing_overrides(signature,item_id,payload,excluded,reason) VALUES('sample','456',NULL,1,'Inserto')")
assert db.execute('SELECT excluded,reason FROM listing_overrides').fetchone()==(1,'Inserto')
print('PASS: additive Library migration preserves ID, paid/shipping amounts, source and dates; overrides table is idempotent.')
# v5.11.19 games migration is additive and marks historical matcher decisions as version 0.
market_source=(root/'app/src/main/java/it/vintedaffari/app/MarketStore.java').read_text()
gdb=sqlite3.connect(':memory:')
gdb.execute("CREATE TABLE games(id INTEGER PRIMARY KEY,bgg_id TEXT,match_state TEXT)")
for sql in re.findall(r'db\.execSQL\("(ALTER TABLE games ADD COLUMN match_algorithm_version[^"\n]+)"\)',market_source):
 gdb.execute(sql)
cols={row[1]:row for row in gdb.execute('PRAGMA table_info(games)')}
assert 'match_algorithm_version' in cols
assert cols['match_algorithm_version'][4]=='0'
print('PASS: v5.11.19 matcher-version migration is additive.')
