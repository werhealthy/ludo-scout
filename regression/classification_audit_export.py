"""Execute production export selections against the real schema and a read-only fixture."""
import json, re, sqlite3, tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
source=root/'app/src/main/java/it/vintedaffari/app/ClassificationAuditExport.java'
assert source.exists(), 'Missing read-only archive exporter'
s=source.read_text()
queries=re.findall(r'"(SELECT [^"\n]+)"',s)
assert len(queries)>=6, 'Missing audit source namespaces'
with tempfile.TemporaryDirectory() as tmp:
    path=Path(tmp)/'audit.db'
    db=sqlite3.connect(path)
    for name in ['DealDatabase.java','MarketStore.java']:
        text=(root/'app/src/main/java/it/vintedaffari/app'/name).read_text()
        for expression in re.findall(r'db.execSQL\(("CREATE TABLE .*?)\);',text,re.S):
            sql=''.join(json.loads(x) for x in re.findall(r'"(?:[^"\\]|\\.)*"',expression))
            try: db.execute(sql)
            except sqlite3.OperationalError as e:
                if 'already exists' not in str(e): raise
    for state in ['ACTIVE','AUTO_FILTERED','REMOVED','RESET_LEGACY','SOLD','UNKNOWN','USER_HIDDEN']:
        db.execute('INSERT INTO market_listings(temp_fingerprint,vinted_title,current_price_cents,lifecycle,first_seen,last_seen) VALUES(?,?,?,?,0,0)',(state,'Annuncio '+state,100,state))
    db.execute("INSERT INTO queue_controls(name,text_value) VALUES('browser_snapshot:123','{\"id\":\"123\",\"title\":\"Senza prezzo\"}')")
    db.execute("INSERT INTO queue_controls(name,text_value) VALUES('unrelated-secret','must-not-export')")
    db.commit();before='\n'.join(db.iterdump());db.close()
    ro=sqlite3.connect(f'file:{path}?mode=ro',uri=True)
    results=[ro.execute(q).fetchall() for q in queries]
    assert len(results[0])==7, 'Filtered lifecycle lost'
    snapshot_query=next(q for q in queries if 'browser_snapshot:' in q)
    assert len(ro.execute(snapshot_query).fetchall())==1, 'Snapshot scope leaked queue controls'
    assert '\n'.join(ro.iterdump())==before, 'Audit modified the archive'
    ro.close()
assert 'OPEN_READONLY' in s and 'openInputStream' not in s
assert 'captureBrowserItem' not in s and 'applyAnalysis' not in s
print('Audit export: all lifecycle states, partial browser snapshots, queue scope and read-only SQL passed')
