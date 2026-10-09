#!/usr/bin/env python3
from pathlib import Path
import sqlite3

ROOT=Path(__file__).resolve().parents[1]
market=(ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
radar=(ROOT/"app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

start=market.index("public int enforceGlobalCatalogRatingGate")
end=market.index("public int reconcileQueue()",start)
sweep=market[start:end]

checks=[
    ("release identity","applicationId 'it.vintedaffari.app'" in build and "1000000 + ciVersionCode.toInteger()" in build and "versionCode ciVersionCode ? (ciBaseVersionCode + 2034) : 1002034" in build),
    ("sweep is global and threshold-driven",
     "rating IS NOT NULL AND rating<?" in sweep and "DealPolicy.MIN_BGG_RATING" in sweep),
    ("sweep contains no game-name special cases",
     "marracash" not in sweep.lower() and "marrakech" not in sweep.lower()),
    ("existing low-rated canonical games are hidden",
     'hide.put("database_visible",0)' in sweep and '"BGG_RATING_BELOW_6"' in sweep),
    ("only rating-gate hides are automatically restored",
     "filter_reason='BGG_RATING_BELOW_6'" in sweep and "rating>=?" in sweep),
    ("legacy deal ratings sync from canonical BGG truth",
     "UPDATE deals SET rating=(SELECT g.rating FROM games g WHERE g.bgg_id=deals.bgg_id)" in sweep),
    ("jobs for below-six games are closed",
     '"skipped: BGG rating below 6"' in sweep and "processing_jobs" in sweep),
    ("sweep is one-time and idempotent",
     "CATALOG_RATING_SWEEP_V51234" in sweep and "queue_controls" in sweep),
    ("sweep runs as normal queue maintenance",
     "enforceGlobalCatalogRatingGate(now)+clearHistoricalManualReviewDebt" in market),
    ("diagnostic exposes sweep result",
     '"catalogRatingSweep={"' in radar and 'diagnosticState("catalog_rating_sweep")' in radar),
]

for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("5.12.34 source guards failed: "+", ".join(failed))

# Executable model: names are arbitrary on purpose. The outcome depends only on numeric BGG rating.
db=sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE games(id INTEGER PRIMARY KEY,bgg_id TEXT,rating REAL,voters INTEGER,bgg_rank INTEGER,database_visible INTEGER,filter_reason TEXT);
CREATE TABLE deals(signature TEXT PRIMARY KEY,bgg_id TEXT,rating REAL,voters INTEGER,bgg_rank INTEGER,lifecycle TEXT);
CREATE TABLE market_listings(id INTEGER PRIMARY KEY,game_id INTEGER,lifecycle TEXT);
CREATE TABLE processing_jobs(id INTEGER PRIMARY KEY,game_id INTEGER,listing_id INTEGER,state TEXT,progress INTEGER,next_attempt_at INTEGER,updated_at INTEGER,processing_started_at INTEGER,last_error TEXT);
""")
games=[
 (1,"a",5.90,100,300,1,None),
 (2,"b",5.99,200,200,1,None),
 (3,"c",6.00,300,100,1,None),
 (4,"d",7.20,400,50,0,"BGG_RATING_BELOW_6"),
 (5,"e",4.80,500,20,0,"USER_NOT_A_BOARD_GAME"),
]
db.executemany("INSERT INTO games VALUES(?,?,?,?,?,?,?)",games)
db.executemany("INSERT INTO deals VALUES(?,?,?,?,?,?)",[
 ("totally-unrelated-title-1","a",6.4,1,1,"ACTIVE"),
 ("another-random-name","b",7.1,1,1,"ACTIVE"),
 ("third-game","c",5.5,1,1,"ACTIVE"),
 ("fourth-game","d",5.0,1,1,"ACTIVE"),
])
db.executemany("INSERT INTO market_listings VALUES(?,?,?)",[(10,1,"ACTIVE"),(11,2,"ACTIVE"),(12,3,"ACTIVE"),(13,4,"ACTIVE")])
db.executemany("INSERT INTO processing_jobs VALUES(?,?,?,?,?,?,?,?,?)",[
 (20,1,None,"PENDING",0,0,0,0,""),
 (21,None,11,"FAILED_RETRYABLE",0,0,0,0,""),
 (22,3,None,"PENDING",0,0,0,0,""),
])

threshold=6.0
db.execute("UPDATE games SET database_visible=0,filter_reason='BGG_RATING_BELOW_6' WHERE rating IS NOT NULL AND rating<? AND database_visible<>0",(threshold,))
db.execute("UPDATE games SET database_visible=1,filter_reason=NULL WHERE rating IS NOT NULL AND rating>=? AND database_visible=0 AND filter_reason='BGG_RATING_BELOW_6'",(threshold,))
db.execute("""UPDATE deals SET
 rating=(SELECT g.rating FROM games g WHERE g.bgg_id=deals.bgg_id),
 voters=(SELECT g.voters FROM games g WHERE g.bgg_id=deals.bgg_id),
 bgg_rank=(SELECT g.bgg_rank FROM games g WHERE g.bgg_id=deals.bgg_id)
 WHERE bgg_id IS NOT NULL AND bgg_id<>'' AND EXISTS(SELECT 1 FROM games g WHERE g.bgg_id=deals.bgg_id AND g.rating IS NOT NULL)""")
db.execute("""UPDATE processing_jobs SET state='COMPLETE',progress=100,last_error='skipped: BGG rating below 6'
 WHERE state IN ('PENDING','PROCESSING','FAILED_RETRYABLE') AND
 (game_id IN (SELECT id FROM games WHERE rating IS NOT NULL AND rating<?)
 OR listing_id IN (SELECT l.id FROM market_listings l JOIN games g ON g.id=l.game_id WHERE g.rating IS NOT NULL AND g.rating<?))""",(threshold,threshold))

visible=dict(db.execute("SELECT id,database_visible FROM games"))
assert visible[1]==0 and visible[2]==0, visible
assert visible[3]==1 and visible[4]==1, visible
# User/non-game hidden reason is not overridden by the rating gate.
assert db.execute("SELECT database_visible,filter_reason FROM games WHERE id=5").fetchone()==(0,"USER_NOT_A_BOARD_GAME")
ratings=dict(db.execute("SELECT signature,rating FROM deals"))
assert abs(ratings["totally-unrelated-title-1"]-5.90)<1e-9
assert abs(ratings["another-random-name"]-5.99)<1e-9
assert abs(ratings["third-game"]-6.00)<1e-9
assert abs(ratings["fourth-game"]-7.20)<1e-9
states=dict(db.execute("SELECT id,state FROM processing_jobs"))
assert states[20]=="COMPLETE" and states[21]=="COMPLETE" and states[22]=="PENDING", states
print("PASS executable global rating sweep is name-independent and repairs existing rows")
print(f"PASS {len(checks)+1}/{len(checks)+1} 5.12.34 global rating sweep guards")
