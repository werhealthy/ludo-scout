from pathlib import Path
import sqlite3,subprocess,tempfile,sys,json,re
root=Path(__file__).resolve().parents[1];src=root/'app/src/main/java/it/vintedaffari/app/EngineIntakeSql.java'
if '--source-eval' in sys.argv:
 rows=json.loads(re.search(r'static String rows\(\)\{return (".*");\}',src.read_text()).group(1));count='SELECT COUNT(*) FROM ('+rows+')'
else:
 with tempfile.TemporaryDirectory() as tmp:
  runner=Path(tmp)/'Intake.java';runner.write_text('package it.vintedaffari.app;public class Intake{public static void main(String[]a){System.out.print(EngineIntakeSql.rows()+"\\n---\\n"+EngineIntakeSql.count());}}')
  subprocess.run(['javac','-d',tmp,str(src),str(runner)],check=True)
  rows,count=subprocess.check_output(['java','-cp',tmp,'it.vintedaffari.app.Intake'],text=True).split('\n---\n')
c=sqlite3.connect(':memory:');c.executescript('CREATE TABLE market_listings(id INTEGER PRIMARY KEY,temp_fingerprint TEXT UNIQUE,legacy_signature TEXT,vinted_title TEXT,lifecycle TEXT,enrichment_state TEXT);CREATE INDEX ls ON market_listings(legacy_signature);CREATE TABLE observations(id INTEGER PRIMARY KEY,signature TEXT,vinted_title TEXT,observed_at INTEGER,analysis_status TEXT,verification_state TEXT);CREATE INDEX os ON observations(signature,observed_at);')
c.executemany('INSERT INTO market_listings VALUES(?,?,?,?,?,?)',[(1,'a','a','first','ACTIVE','PENDING_ANALYSIS'),(2,'b','b','done','ACTIVE','COMPLETE'),(3,'c','c','blocked','ACTIVE','BLOCKED_CLASSIFIER'),(4,'d','d','sold','SOLD','PENDING_ANALYSIS'),(5,'e','', 'fallback-key','ACTIVE','PENDING_ANALYSIS')])
def observe(sig,at,status='pending',verify='PENDING_ANALYSIS'):
 c.execute('INSERT INTO observations(signature,vinted_title,observed_at,analysis_status,verification_state) VALUES(?,?,?,?,?)',(sig,sig,at,status,verify))
for sig in ['a','b','c','d','e','raw','raw']:observe(sig,100)
observe('old',100);observe('old',101,'matched','OK');observe('tie',100);observe('tie',100,'matched','OK');observe('blockedraw',100,'pending','BLOCKED_CLASSIFIER')
def check(expected):
 result=list(c.execute(rows));assert c.execute(count).fetchone()[0]==len(result)==len(expected),(result,expected)
 assert {r[1] for r in result}==set(expected),(result,expected)
check(['a','e','raw'])
# No dependency on processing job state or active-scroll range: new intake remains visible while paused.
observe('new',1000);check(['a','e','raw','new'])
c.execute("UPDATE market_listings SET enrichment_state='COMPLETE' WHERE id=1");observe('raw',1001,'matched','OK');check(['e','new'])
print('PASS global current intake, duplicates, latest/tied completion, classifier/sold exclusions and count/list parity')
