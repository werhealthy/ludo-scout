"""Execute the app's actual selection SQL with a read-only database.

Protects against selecting deleted/hidden listings, unstable ties, unbounded
archive reads and writes while preparing a manual AI comparison.
"""
import json, re, sqlite3, tempfile
from pathlib import Path
root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/it/vintedaffari/app/AiBetaRealListings.java'
assert source.exists(), 'Real announcement selection is missing'
sql = re.search(r'SELECT_RECENT\s*=\s*("(?:[^"\\]|\\.)*")', source.read_text())
assert sql, 'Selection SQL is missing'
query = json.loads(sql.group(1))
with tempfile.TemporaryDirectory() as tmp:
    path = Path(tmp) / 'archive.db'
    db = sqlite3.connect(path)
    schema = (root / 'app/src/main/java/it/vintedaffari/app/MarketStore.java').read_text()
    expression = re.search(r'db.execSQL\(("CREATE TABLE IF NOT EXISTS market_listings.*?\));', schema, re.S).group(1)
    db.execute(''.join(json.loads(s) for s in re.findall(r'"(?:[^"\\]|\\.)*"', expression)))
    for ident, state, title, stamp in [(1,'ACTIVE','Catan',10),(2,'AUTO_FILTERED','Organizer Catan',10),(3,'SOLD','Sold',99),(4,'REMOVED','Removed',99),(5,'USER_HIDDEN','Hidden',99),(6,'RESET_LEGACY','Reset',99),(7,'ACTIVE','   ',100)]:
        db.execute('INSERT INTO market_listings(id,temp_fingerprint,vinted_title,brand,observed_text,current_price_cents,lifecycle,first_seen,last_seen) VALUES(?,?,?,?,?,100,?,0,?)', (ident,str(ident),title,'Kosmos','Descrizione locale',state,stamp))
    for ident in range(10,60):
        db.execute('INSERT INTO market_listings(id,temp_fingerprint,vinted_title,current_price_cents,lifecycle,first_seen,last_seen) VALUES(?,?,?,100,?,0,0)', (ident,str(ident),'Gioco '+str(ident),'ACTIVE'))
    db.commit(); before = '\n'.join(db.iterdump()); db.close()
    ro = sqlite3.connect(f'file:{path}?mode=ro', uri=True)
    rows = ro.execute(query).fetchall()
    assert [row[0] for row in rows[:2]] == [2,1], 'Recent ordering / tie-break lost'
    assert len(rows) == 32, 'Candidate read is not bounded'
    assert not {3,4,5,6,7}.intersection(row[0] for row in rows), 'Unavailable / blank rows selected'
    assert rows[0][1:4] == ('Organizer Catan','Kosmos','Descrizione locale'), 'Local evidence lost'
    assert '\n'.join(ro.iterdump()) == before, 'Preparation changed the catalog'
print('Real listings: lifecycle, stable order, bounded read, local context and unchanged archive passed')
