#!/usr/bin/env python3
"""Execute production phase SQL against adversarial SQLite identities/trust states."""
import ast,pathlib,sqlite3,subprocess,tempfile,sys
root=pathlib.Path(__file__).resolve().parents[1]
source=root/'app/src/main/java/it/vintedaffari/app/EnginePipelineSql.java'
if '--source-eval' in sys.argv:
    import re
    values={}
    def literal(node):
        if isinstance(node,ast.Constant) and isinstance(node.value,str):return node.value
        if isinstance(node,ast.Name):return values[node.id]
        if isinstance(node,ast.BinOp) and isinstance(node.op,ast.Add):return literal(node.left)+literal(node.right)
        raise ValueError('Unexpected Java query expression')
    for name,expression in re.findall(r'String (\w+)=(.*?);\n',source.read_text(),re.S):values[name]=literal(ast.parse('('+expression+')',mode='eval').body)
    expression=source.read_text().split('        return ',1)[1].split(';\n',1)[0]
    sql=literal(ast.parse('('+expression+')',mode='eval').body)
else:
    with tempfile.TemporaryDirectory() as temp:
        runner=pathlib.Path(temp)/'PrintPipeline.java'
        runner.write_text('package it.vintedaffari.app; public class PrintPipeline {public static void main(String[] args){System.out.print(EnginePipelineSql.query());}}')
        subprocess.run(['javac','-d',temp,str(source),str(runner)],check=True)
        sql=subprocess.check_output(['java','-cp',temp,'it.vintedaffari.app.PrintPipeline'],text=True)
db=sqlite3.connect(':memory:')
db.executescript('''
CREATE TABLE observations(signature TEXT,observed_at INTEGER);
CREATE TABLE games(id INTEGER PRIMARY KEY,bgg_id TEXT,match_state TEXT,rating REAL,database_visible INTEGER);
CREATE TABLE market_listings(id INTEGER PRIMARY KEY,game_id INTEGER,legacy_signature TEXT,temp_fingerprint TEXT,lifecycle TEXT,enrichment_state TEXT,manual_review_required INTEGER,match_state TEXT,vinted_item_id TEXT,vinted_url TEXT);
CREATE TABLE deals(signature TEXT,verification_state TEXT,lifecycle TEXT,listing_type TEXT,tier TEXT,rating REAL,bgg_id TEXT,vinted_item_id TEXT,vinted_url TEXT);
CREATE TABLE processing_jobs(listing_id INTEGER,job_type TEXT,source TEXT,state TEXT);
''')
def add(sig,game=None,bgg=None,rating=None,gs='PENDING',state='PENDING_ANALYSIS',linked=False,review=0,visible=1):
    lid=db.execute('SELECT COALESCE(MAX(id),0)+1 FROM market_listings').fetchone()[0]
    if game is not None:db.execute('INSERT OR IGNORE INTO games VALUES(?,?,?,?,?)',(game,bgg,gs,rating,visible))
    db.execute('INSERT INTO observations VALUES(?,100)',(sig,))
    db.execute('INSERT INTO market_listings VALUES(?,?,?,?,?,?,?,?,?,?)',(lid,game,sig,sig,'ACTIVE',state,review,'MATCHED' if gs=='MATCHED' else gs,str(lid) if linked else '', 'url' if linked else ''))
    db.execute("INSERT INTO deals VALUES(?,'OK','ACTIVE','GAME','good',?,?,?,?)",(sig,rating,bgg,str(lid) if linked else '','url' if linked else ''))
    return lid
def counts():return dict(db.execute(sql,(0,200)))
db.execute("INSERT INTO observations VALUES('raw',100)")
add('pending',1)
add('analyzing',2,state='ANALYZED')
add('eligible',3,'3',7,'MATCHED','ANALYZED')
vinted=add('vinted',4,'4',7,'MATCHED','PENDING_ENRICHMENT');db.execute("INSERT INTO processing_jobs VALUES(?,'VINTED_ENRICHMENT','AUTO','PENDING')",(vinted,))
ready=add('ready',5,'5',7,'MATCHED','CORE_COMPLETE',True)
assert counts()=={0:2,1:1,2:1,3:1,4:1},counts()
# Repeated observations and duplicate canonical/BGG identities don't inflate any phase.
db.execute("INSERT INTO observations VALUES('ready',100)")
add('ready-copy',6,'5',7,'MATCHED','COMPLETE',True)
assert counts()=={0:2,1:1,2:1,3:1,4:1},counts()
# A game's most advanced usable listing determines its one phase.
add('eligible-copy',7,'3',7,'MATCHED','COMPLETE',True)
assert counts()=={0:2,1:1,3:1,4:2},counts()
for sig,kind in [('price','PRICE_ANOMALY'),('identity','MATCH_UNCERTAIN'),('expansion','EXPANSION_CHECK'),('variant','BGG_VARIANT_REVIEW')]:
    add(sig,20+len(sig),sig,8,'MATCHED','COMPLETE',True);db.execute('UPDATE deals SET verification_state=? WHERE signature=?',(kind,sig))
add('manual',50,'50',8,'MATCHED','COMPLETE',True,review=1)
add('hidden',51,'51',8,'MATCHED','COMPLETE',True,visible=0)
add('low',52,'52',5,'MATCHED','COMPLETE',True)
add('hold',53,'53',8,'MATCHED','LOCAL_ONLY',True)
assert counts()=={0:2,1:1,3:1,4:2},counts()
# Automatic deep enrichment doesn't unpublish core readiness; manual deep recovery does.
db.execute("INSERT INTO processing_jobs VALUES(?,'VINTED_DEEP_ENRICHMENT','AUTO','PENDING')",(ready,))
assert counts().get(4)==2
# Remove other ready listing so the single pending manual recovery can demote this identity.
db.execute("UPDATE market_listings SET lifecycle='SOLD' WHERE legacy_signature='ready-copy'")
db.execute("UPDATE processing_jobs SET source='MANUAL_RECOVERY' WHERE listing_id=?",(ready,))
assert counts().get(4)==1 and counts().get(3)==2,counts()
# Archived legacy listings cannot masquerade as ready despite stale canonical lifecycle.
add('sold',70,'70',8,'MATCHED','COMPLETE',True)
db.execute("UPDATE deals SET lifecycle='SOLD' WHERE signature='sold'")
assert counts().get(4)==1
# Missing trust evidence, unsupported type and incomplete legacy identity are not publishable.
for sig in ['untrusted','unsupported','missing-id']:
    add(sig,80+len(sig),sig,8,'MATCHED','COMPLETE',True)
db.execute("UPDATE deals SET verification_state=NULL WHERE signature='untrusted'")
db.execute("UPDATE deals SET listing_type='ACCESSORY' WHERE signature='unsupported'")
db.execute("UPDATE deals SET vinted_item_id='' WHERE signature='missing-id'")
assert counts().get(4)==1
assert not dict(db.execute(sql,(201,300)))
print('PASS production SQLite phase occupancy, identities, review/price holds, pending/manual/deep jobs and time scope')
