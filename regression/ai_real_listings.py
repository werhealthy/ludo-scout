"""Execute the production read-only selection SQL against adversarial SQLite data."""
import pathlib, re, sqlite3
p=pathlib.Path('app/src/main/java/it/vintedaffari/app/AiBetaListings.java')
s=p.read_text()
sql=re.search(r'SELECTION_SQL\s*=\s*"([^"]+)"',s).group(1)
db=sqlite3.connect(':memory:')
db.executescript('CREATE TABLE market_listings(id INTEGER PRIMARY KEY,vinted_title TEXT,brand TEXT,item_condition TEXT,current_price_cents INTEGER,observed_text TEXT,game_id INTEGER,last_seen INTEGER,lifecycle TEXT,match_state TEXT); CREATE TABLE games(id INTEGER PRIMARY KEY,bgg_id TEXT,canonical_name TEXT,match_state TEXT);')
db.execute("INSERT INTO games VALUES(1,'123','Known game','MATCHED')")
for i in range(1,13):
 db.execute('INSERT INTO market_listings VALUES(?,?,?,?,?,?,?,?,?,?)',(i,'Organizer base + expansions' if i==12 else 'Game '+str(i),'Brand',None,0,'',1 if i==12 else None,100,'AUTO_FILTERED' if i==12 else 'ACTIVE','MATCHED'))
db.execute("INSERT INTO market_listings VALUES(13,'  ',NULL,NULL,0,NULL,NULL,999,'ACTIVE','')")
before=list(db.iterdump())
rows=db.execute(sql).fetchall()
assert [r[0] for r in rows]==list(range(12,4,-1)),rows
assert rows[0][-3:]==('123','Known game','MATCHED'),rows[0]
assert rows[1][-3:]==('','',''),rows[1]
assert list(db.iterdump())==before
db.execute('DELETE FROM market_listings')
assert db.execute(sql).fetchall()==[]
assert 'OPEN_READONLY' in s and 'getReadableDatabase' not in s and 'getWritableDatabase' not in s
print('Real AI selection: eight recent canonical IDs, stable ties, filtered/partial rows, optional BGG, blank titles, empty DB and unchanged SQL data passed')

