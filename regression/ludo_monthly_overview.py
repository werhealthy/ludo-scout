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
assert db.execute(sql,("100","200")).fetchone()==(3,), "monthly games must be distinct; redundant >6 metric must be removed"
assert db.execute(sql,("220","230")).fetchone()==(0,), "empty month must return actual zero counts"
db.execute("INSERT INTO market_listings VALUES(12,7,160,'ACTIVE')")
assert db.execute(sql,("100","200")).fetchone()==(4,), "an old known game with a new observed listing belongs in this month's overview"
print("PASS real monthly SQL: distinct BGG games, unrated, duplicate identities, hidden/review/corpus, date bounds and empty month")

# Candidate SQL is read-only and uses the existing trusted card gates. Price quality
# is recalculated by DealEvaluator in the JVM regression, not duplicated in SQL.
candidate=re.search(r'GREAT_BUY_QUERY\s*=\s*"([^"]+)"',text).group(1)
for column in ["legacy_signature TEXT", "vinted_item_id TEXT", "enrichment_state TEXT", "match_state TEXT", "manual_review_required INTEGER"]:
    db.execute("ALTER TABLE market_listings ADD COLUMN "+column)
db.executescript("""
CREATE TABLE processing_jobs(listing_id INTEGER,job_type TEXT,source TEXT,state TEXT);
CREATE TABLE deals(signature TEXT,bgg_id TEXT,vinted_item_id TEXT,vinted_url TEXT,lifecycle TEXT,tier TEXT,rating REAL,verification_state TEXT,listing_type TEXT,item_price_cents INTEGER,total_cents INTEGER,benchmark_cents INTEGER,protected_price_cents INTEGER,shipping_cents INTEGER,shipping_verified_cents INTEGER,offer_cents INTEGER);
UPDATE market_listings SET legacy_signature='s'||id,vinted_item_id='i'||id,enrichment_state='CORE_COMPLETE',match_state='MATCHED',manual_review_required=0;
INSERT INTO deals VALUES('s1','a','i1','https://www.vinted.it/items/1','ACTIVE','hot',7.4,'OK','BASE_GAME',1000,1400,3000,1120,280,NULL,NULL);
INSERT INTO deals SELECT 's2','a','i2','https://www.vinted.it/items/2',lifecycle,tier,rating,verification_state,listing_type,item_price_cents,total_cents,benchmark_cents,protected_price_cents,shipping_cents,shipping_verified_cents,offer_cents FROM deals WHERE signature='s1';
""")
def candidates():return db.execute(candidate,("100","200")).fetchall()
assert len(candidates())==2 and {row[0] for row in candidates()}=={'a'}, "multiple offers stay one game after identity dedupe"
db.execute("UPDATE deals SET tier='good' WHERE signature='s2'")
assert len(candidates())==1, "good prices are not labelled Offertone"
for field,value in [("lifecycle","SOLD"),("verification_state","MATCH_UNCERTAIN"),("listing_type","ACCESSORY"),("vinted_url",""),("vinted_item_id",""),("bgg_id","different")]:
    original=db.execute("SELECT "+field+" FROM deals WHERE signature='s1'").fetchone()[0]
    db.execute("UPDATE deals SET "+field+"=? WHERE signature='s1'",(value,))
    assert not candidates(),field+" must exclude an untrusted offer"
    db.execute("UPDATE deals SET "+field+"=? WHERE signature='s1'",(original,))
for field,value in [("enrichment_state","PENDING"),("match_state","REVIEW"),("manual_review_required",1),("first_seen",90),("lifecycle","USER_HIDDEN")]:
    original=db.execute("SELECT "+field+" FROM market_listings WHERE id=1").fetchone()[0]
    db.execute("UPDATE market_listings SET "+field+"=? WHERE id=1",(value,))
    assert not candidates(),field+" must exclude an incomplete/outside-month listing"
    db.execute("UPDATE market_listings SET "+field+"=? WHERE id=1",(original,))
db.execute("INSERT INTO processing_jobs VALUES(1,'VINTED_ENRICHMENT','AUTO','PENDING')")
assert not candidates(),"pending core work cannot count as available"
db.execute("UPDATE processing_jobs SET job_type='VINTED_DEEP_ENRICHMENT'")
assert len(candidates())==1,"optional deep work does not block the existing ready contract"
db.execute("UPDATE processing_jobs SET source='MANUAL_RECOVERY'")
assert not candidates(),"manual deep recovery still blocks trust"
print("PASS monthly Offertone candidate SQL: same identity, current availability, trust, date and core-job gates")
