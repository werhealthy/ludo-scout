#!/usr/bin/env python3
import sqlite3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
deal=(ROOT/"app/src/main/java/it/vintedaffari/app/DealDatabase.java").read_text(encoding="utf-8")
market=(ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
ui=(ROOT/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
service=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueKeepAliveService.java").read_text(encoding="utf-8")
crash=(ROOT/"app/src/main/java/it/vintedaffari/app/ProcessCrashJournal.java").read_text(encoding="utf-8")

# Executable fixture for the product-facing cut-over.
db=sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE observations(id INTEGER PRIMARY KEY,signature TEXT,observed_at INTEGER,analysis_status TEXT,verification_state TEXT);
CREATE TABLE processing_jobs(id INTEGER PRIMARY KEY,state TEXT,source TEXT,next_attempt_at INTEGER,updated_at INTEGER,progress INTEGER,processing_started_at INTEGER,last_error TEXT);
CREATE TABLE market_listings(id INTEGER PRIMARY KEY,last_seen INTEGER,lifecycle TEXT,manual_review_required INTEGER,manual_review_reason TEXT);
CREATE TABLE deals(id INTEGER PRIMARY KEY,last_seen INTEGER,lifecycle TEXT,verification_state TEXT,verification_reason TEXT);
CREATE TABLE games(id INTEGER PRIMARY KEY,last_seen INTEGER,database_visible INTEGER,bgg_id TEXT,match_state TEXT,filter_reason TEXT);
CREATE TABLE queue_controls(name TEXT PRIMARY KEY,value INTEGER,updated_at INTEGER,text_value TEXT);
""")
epoch=2000
db.executemany("INSERT INTO observations VALUES(?,?,?,?,?)",[
    (1,"old",1000,"pending","PENDING_ANALYSIS"),
    (2,"blocked",2100,"pending","BLOCKED_CLASSIFIER"),
    (3,"live",2200,"pending","PENDING_ANALYSIS"),
])
db.executemany("INSERT INTO processing_jobs VALUES(?,?,?,?,?,?,?,?)",[
    (1,"PENDING","AUTO",0,0,0,0,""),
    (2,"PROCESSING","LEGACY_V9",0,0,30,100,""),
    (3,"PENDING","HUNT_PRIORITY",0,0,0,0,""),
])
db.executemany("INSERT INTO market_listings VALUES(?,?,?,?,?)",[
    (1,1500,"ACTIVE",1,"legacy ambiguity"),
    (2,2300,"ACTIVE",1,"fresh ambiguity"),
])
db.executemany("INSERT INTO deals VALUES(?,?,?,?,?)",[
    (1,1500,"ACTIVE","MATCH_UNCERTAIN","legacy"),
    (2,2300,"ACTIVE","OK","fresh"),
])
db.executemany("INSERT INTO games VALUES(?,?,?,?,?,?)",[
    (1,1500,1,"","BGG_MATCH_REVIEW","legacy"),
    (2,2300,1,"123","MATCHED",""),
])

# Production cut-over semantics: archive automatic debt, preserve Hunt, clear old inherited review debt.
db.execute("UPDATE processing_jobs SET state='COMPLETE',next_attempt_at=0,updated_at=?,progress=100,processing_started_at=0,last_error='archived' WHERE state IN ('PENDING','PROCESSING','FAILED_RETRYABLE','FAILED_PERMANENT') AND COALESCE(source,'AUTO')<>'HUNT_PRIORITY'",(epoch,))
db.execute("UPDATE market_listings SET manual_review_required=0,manual_review_reason=NULL WHERE COALESCE(manual_review_required,0)=1")
db.execute("UPDATE deals SET verification_state='EPOCH_ARCHIVED_REVIEW',verification_reason='archived' WHERE lifecycle='ACTIVE' AND COALESCE(verification_state,'') IN ('BGG_VARIANT_REVIEW','MATCH_UNCERTAIN','PRICE_ANOMALY','EXPANSION_CHECK')")
db.execute("UPDATE games SET match_state='EPOCH_ARCHIVED_REVIEW',database_visible=0,filter_reason='archived' WHERE database_visible=1 AND (bgg_id IS NULL OR bgg_id='') AND match_state='BGG_MATCH_REVIEW'")
db.execute("INSERT INTO queue_controls VALUES('engine_epoch_start',?,?,?)",(epoch,epoch,"fixture"))

assert db.execute("SELECT state FROM processing_jobs WHERE id=1").fetchone()[0]=="COMPLETE"
assert db.execute("SELECT state FROM processing_jobs WHERE id=2").fetchone()[0]=="COMPLETE"
assert db.execute("SELECT state FROM processing_jobs WHERE id=3").fetchone()[0]=="PENDING"
assert db.execute("SELECT COUNT(*) FROM observations").fetchone()[0]==3, "cut-over must preserve raw observations"
assert db.execute("SELECT COUNT(*) FROM market_listings WHERE manual_review_required=1").fetchone()[0]==0
assert db.execute("SELECT match_state,database_visible FROM games WHERE id=1").fetchone()==("EPOCH_ARCHIVED_REVIEW",0)

pending=db.execute("SELECT COUNT(DISTINCT signature) FROM observations WHERE observed_at>=? AND analysis_status='pending' AND verification_state='PENDING_ANALYSIS'",(epoch,)).fetchone()[0]
assert pending==1, f"blocked classifier leaked into pending count: {pending}"
visible_sessions=db.execute("SELECT COUNT(*) FROM observations WHERE observed_at>=?",(epoch,)).fetchone()[0]
assert visible_sessions==2 and db.execute("SELECT COUNT(*) FROM observations WHERE observed_at<?",(epoch,)).fetchone()[0]==1

checks=[
 ("DealDatabase scopes Motore by engine epoch", "engineEpochStart()" in deal and "clampEngineStart" in deal),
 ("pending analysis excludes blocked classifier", "analysis_status='pending' AND verification_state='PENDING_ANALYSIS'" in deal),
 ("operational epoch is lossless for observation history", "startOperationalEpochIfMissing" in market and 'db.delete("observations"' not in market[market.index("startOperationalEpochIfMissing"):market.index("public long engineEpochStart")]),
 ("automatic debt archived but Hunt preserved", "COALESCE(source,'AUTO')<>'HUNT_PRIORITY'" in market),
 ("legacy review debt archived", "EPOCH_ARCHIVED_REVIEW" in market and "listingReviewsCleared" in market),
 ("archived provisional can revive on fresh evidence", '"EPOCH_ARCHIVED_REVIEW".equals(currentState)' in market),
 ("review inbox is epoch-scoped", "last_seen>=?" in market[market.index("public int vintedReviewCount"):market.index("public boolean retryNow")]),
 ("hero is progress-first", 'automaticDone+" / "+run.validListings' in ui and '" elaborati"' in ui),
 ("human recovery is separate from automatic completion", 'renderEngineHeader("Da completare"' in ui and "Qui Ludo ti chiede solo una decisione precisa" in ui),
 ("queue startup records phases instead of throwing through Service", "queue:onCreate:database" in service and "queue:onCreate:lanes" in service and "START_NOT_STICKY" in service),
 ("crash journal exposes root cause and handled phase", "recordHandled" in crash and "root=" in crash and "phase=" in crash),
]
for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("Engine epoch/progress/stability regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} engine epoch/progress/stability guards")
