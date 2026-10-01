from pathlib import Path
root=Path(__file__).resolve().parents[1]
ui=(root/'app/src/main/java/it/vintedaffari/app/MainActivity.java').read_text()
db=(root/'app/src/main/java/it/vintedaffari/app/DealDatabase.java').read_text()
hero=ui.split('private View heroOpportunityCard')[1].split('private View discoverFlatArtwork')[0]
assert 'Non mi interessa' not in hero, 'Hero must not contain dismissal text'
assert 'benchmarkCents' in hero and 'STRIKE_THRU_TEXT_FLAG' in hero, 'Comparison must use real benchmark'
assert 'Scroll in attesa' in ui and 'renderEngineWaiting' in ui, 'Waiting scrolls need a direct list'
assert 'waitingObservationSessions().size()' in db, 'Count and waiting list must share membership'
assert 'engineIntakeItems()' in ui, 'Pending raw announcements must remain reachable'
assert 'db.waitingScrollItems(start,end)' in ui, 'Waiting drilldown must include observations before BGG recognition'
query=db.split('public synchronized List<PipelineItem> waitingScrollItems')[1].split('public synchronized boolean isObservationSessionWaiting')[0]
assert 'FROM observations' in query and 'JOIN' not in query, 'Raw waiting cards cannot require a canonical game'
print('PASS Home reference and waiting scroll integration')

import sqlite3,re,json
sql=json.loads(re.search(r'rawQuery\(("(?:[^"\\]|\\.)*")',query).group(1))
conn=sqlite3.connect(':memory:')
conn.execute('CREATE TABLE observations(signature TEXT,vinted_title TEXT,observed_at INTEGER)')
conn.executemany('INSERT INTO observations VALUES(?,?,?)',[('raw','Senza BGG',10),('raw','Senza BGG',11),('second','Senza prezzo',12),('old','Altro scroll',2)])
assert conn.execute(sql,('10','12')).fetchall()==[('second','Senza prezzo'),('raw','Senza BGG')], 'Missing game and price must not hide or duplicate acquired cards'
print('PASS raw waiting scroll SQLite fixture')
