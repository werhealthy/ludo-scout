"""Execute durable counter behavior and current-state capture funnel fixtures."""
from pathlib import Path
import json, re, sqlite3, subprocess, tempfile, sys
root=Path(__file__).resolve().parents[1]
base=root/'app/src/main/java/it/vintedaffari/app'
service=(base/'VintedAccessibilityService.java').read_text()
assert 'persistAnalysisResults' in service, 'Analysis persistence still runs on the WebView/main callback'
callback=service[service.index('@Override public void onResult(List<GameAnalysis> analyses)'):service.index('@Override public void onError(String message)',service.index('@Override public void onResult(List<GameAnalysis> analyses)'))]
assert 'radarPersistence.submit' in callback and 'database.record(' not in callback
assert 'radarCounters' in service and 'initializeRadarCounters' in service
assert 'engineLatestCapture=' in service and 'engineCaptureFunnel=' in service
assert 'radarPersistence.close' in service, 'Lifecycle must drain persistence before closing databases'
sqlsrc=base/'ObservationFunnelSql.java'
sql=json.loads(re.search(r'static String rows\(\)\s*\{\s*return (".*");',sqlsrc.read_text()).group(1))
c=sqlite3.connect(':memory:')
c.executescript('CREATE TABLE observations(signature TEXT,observed_at INTEGER);CREATE TABLE market_listings(id INTEGER,temp_fingerprint TEXT,legacy_signature TEXT,lifecycle TEXT,enrichment_state TEXT,game_id INTEGER);CREATE TABLE games(id INTEGER,match_state TEXT,bgg_id TEXT,rating REAL,database_visible INTEGER);')
c.executemany('INSERT INTO games VALUES(?,?,?,?,?)',[(1,'MATCHED','10',7,1),(2,'MATCHED','20',5,1),(3,'MATCHED','30',None,1),(4,'MATCHED','40',8,0),(5,'BGG_MATCH_REVIEW','',8,1),(6,None,'60',8,1)])
states=[('a','ACTIVE','BLOCKED_CLASSIFIER',None,'CLASSIFIER_BLOCKED'),('b','ACTIVE','AUTO_FILTERED',None,'AUTO_FILTERED'),('c','ACTIVE','PENDING_ANALYSIS',None,'ANALYSIS_PENDING'),('d','SOLD','COMPLETE',1,'INACTIVE'),('e','ACTIVE','COMPLETE',2,'BELOW_RATING'),('f','ACTIVE','COMPLETE',3,'RATING_PENDING'),('g','ACTIVE','COMPLETE',4,'GAME_HIDDEN'),('h','ACTIVE','COMPLETE',5,'BGG_UNRESOLVED'),('i','ACTIVE','COMPLETE',1,'BGG_QUALIFIED'),('j','ACTIVE','COMPLETE',None,'GAME_MISSING')]
for n,(sig,life,state,game,reason) in enumerate(states,1):
 c.execute('INSERT INTO market_listings VALUES(?,?,?,?,?,?)',(n,sig,sig,life,state,game))
 c.execute('INSERT INTO observations VALUES(?,?)',(sig,100))
c.execute("INSERT INTO observations VALUES('i',101)")
c.execute("INSERT INTO observations VALUES('raw',100)")
c.execute("INSERT INTO observations VALUES('outside',99)")
rows=list(c.execute(sql,('100','101')))
assert {r[0]:r[1] for r in rows}==dict((s[0],s[4]) for s in states)|{'raw':'NO_CANONICAL'},rows
assert len(rows)==11,'Repeated observations must not inflate signature counts'
# legacy_signature wins; temp fingerprint is fallback only when legacy is absent.
c.execute("UPDATE market_listings SET legacy_signature='',temp_fingerprint='j' WHERE id=10")
assert dict(c.execute(sql,('100','101')))['j']=='GAME_MISSING'
assert not list(c.execute(sql,('200','201')))
c.execute("INSERT INTO market_listings VALUES(11,'nullstate','nullstate','ACTIVE','COMPLETE',6)")
c.execute("INSERT INTO observations VALUES('nullstate',100)")
assert dict(c.execute(sql,('100','101')))['nullstate']=='BGG_UNRESOLVED', 'Unknown match state must never qualify'
if '--source-eval' not in sys.argv:
 with tempfile.TemporaryDirectory() as out:
  subprocess.run(['javac','-d',out,str(base/'RadarIntakeCounters.java'),str(base/'RadarPersistence.java'),str(root/'regression/RadarReliabilityRegression.java')],check=True)
  subprocess.run(['java','-cp',out,'it.vintedaffari.app.RadarReliabilityRegression'],check=True)
 subprocess.run([sys.executable,str(root/'regression/radar_service_boundary.py')],check=True)
print('PASS radar persistence boundary, lifecycle and signature funnel fixtures')
