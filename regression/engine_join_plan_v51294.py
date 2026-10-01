from pathlib import Path
import sqlite3,re
root=Path(__file__).resolve().parents[1]
s=(root/'app/src/main/java/it/vintedaffari/app/EnginePipelineSql.java').read_text()
join=re.search(r'LEFT JOIN market_listings l ON (.*?) "+',s).group(1)
c=sqlite3.connect(':memory:');c.executescript('CREATE TABLE observations(signature TEXT,observed_at INTEGER); CREATE TABLE market_listings(id INTEGER PRIMARY KEY,temp_fingerprint TEXT UNIQUE,legacy_signature TEXT);CREATE INDEX ls ON market_listings(legacy_signature); CREATE INDEX os ON observations(observed_at);')
c.executemany('INSERT INTO observations VALUES(?,1)',[(v,) for v in ['a','b','c','',None]])
c.executemany('INSERT INTO market_listings VALUES(?,?,?)',[(1,'a',None),(2,'b',''),(3,'other','c'),(4,'c','wrong'),(5,'','x')])
old="COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint)=o.signature"
query='SELECT o.rowid,l.id FROM observations o LEFT JOIN market_listings l ON {} WHERE o.observed_at BETWEEN 0 AND 2 ORDER BY o.rowid,l.id'
assert list(c.execute(query.format(join)))==list(c.execute(query.format(old)))
plan=[r[3] for r in c.execute('EXPLAIN QUERY PLAN '+query.format(join))]
assert not any('SCAN l' in r for r in plan),plan
assert sum('SEARCH l' in r for r in plan)>=2,plan
print('PASS equivalent canonical identities with indexed joins instead of listing scans')
