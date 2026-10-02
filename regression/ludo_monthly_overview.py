#!/usr/bin/env python3
"""Real overview SQL fixtures: duplicate listings must never inflate game counts."""
from pathlib import Path
import re, sqlite3
root=Path(__file__).resolve().parents[1]
source=root/"app/src/main/java/it/vintedaffari/app/LudoMonthlyOverview.java"
assert source.exists(), "Monthly overview is missing: cannot distinguish discovered games from the BGG corpus"
text=source.read_text()
sql=re.search(r'STATIC_QUERY\s*=\s*"([^"]+)"',text).group(1)
db=sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE games(id INTEGER,bgg_id TEXT,rating REAL,database_visible INTEGER,match_state TEXT);
CREATE TABLE market_listings(id INTEGER,game_id INTEGER,first_seen INTEGER,lifecycle TEXT);
INSERT INTO games VALUES (1,'a',7.4,1,'MATCHED'),(2,'b',6.0,1,'MATCHED'),(3,'c',NULL,1,'MATCHED'),(4,'d',9.0,0,'MATCHED'),(5,'e',9.0,1,'BGG_MATCH_REVIEW'),(6,'a',7.4,1,'MATCHED'),(7,'f',8.0,1,'MATCHED'),(8,'g',8.0,1,'MATCHED'),(9,'',8.0,1,'MATCHED'),(10,'corpus',8.0,1,'MATCHED');
INSERT INTO market_listings VALUES (1,1,110,'ACTIVE'),(2,1,115,'ACTIVE'),(3,2,120,'ACTIVE'),(4,3,125,'ACTIVE'),(5,4,130,'ACTIVE'),(6,5,130,'ACTIVE'),(7,6,140,'ACTIVE'),(8,7,90,'ACTIVE'),(9,8,210,'ACTIVE'),(10,9,140,'ACTIVE'),(11,7,150,'USER_HIDDEN');
""")
assert db.execute(sql,("100","200")).fetchone()==(3,1), "must dedupe identities, exclude corpus/unmatched/hidden/outside month, keep missing rating distinct"
assert db.execute(sql,("220","230")).fetchone()==(0,0), "empty month must return actual zero counts"
db.execute("INSERT INTO market_listings VALUES(12,7,160,'ACTIVE')")
assert db.execute(sql,("100","200")).fetchone()==(4,2), "an old known game with a new observed listing belongs in this month's overview"
print("PASS real monthly SQL: distinct BGG games, >6 strict, unrated, duplicate identities, hidden/review/corpus, date bounds and empty month")
