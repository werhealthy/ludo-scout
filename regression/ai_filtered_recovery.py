#!/usr/bin/env python3
from pathlib import Path
import sqlite3

policy=Path("app/src/main/java/it/vintedaffari/app/AiEnginePolicy.java").read_text(encoding="utf-8")
listings=Path("app/src/main/java/it/vintedaffari/app/AiEngineListings.java").read_text(encoding="utf-8")
runner=Path("app/src/main/java/it/vintedaffari/app/AiEngineRunner.java").read_text(encoding="utf-8")
radar=Path("app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
gradle=Path("app/build.gradle").read_text(encoding="utf-8")

recover_block=listings[listings.index("if(AiEnginePolicy.recover"):listings.index("if(!AiEnginePolicy.hold")]
checks={
 "only automatic product misses are selectable":"AiEnginePolicy.RECOVERABLE_SQL" in listings and "automaticProductMiss" in policy,
 "existing BGG identity excluded":"COALESCE(g.bgg_id,'')=''" in listings and '!row.optString("bgg_id").isEmpty()' in policy,
 "human confirmation and overrides protected":"COALESCE(d.confirmed,0)=0" in listings and "USER_CONFIRMED" in listings and "listing_overrides" in listings,
 "local classifier rejects explicit negative types":'Arrays.asList("UNCERTAIN","BASE_GAME")' in policy,
 "AI must say base game":'!"BASE_GAME".equals(answer.optString("proposed_type"))' in policy,
 "visual evidence required":'photos.length()>0' in policy and 'strong>=2' in policy,
 "explicit non-game category cannot recover":"isExplicitNonGameCategory" in policy,
 "recovery re-enters local analysis":'listing.put("lifecycle","ACTIVE")' in recover_block and 'listing.put("enrichment_state","PENDING_ANALYSIS")' in recover_block and 'listing.put("match_state","PENDING_ANALYSIS")' in recover_block,
 "provisional identity detached":'listing.putNull("game_id")' in recover_block and 'listing.putNull("match_confidence")' in recover_block,
 "existing observation reused":'ORDER BY observed_at DESC,id DESC LIMIT 1' in recover_block and 'observation.put("analysis_status","pending")' in recover_block,
 "recovery does not write BGG or deal trust":'db.update("games"' not in recover_block and 'db.update("deals"' not in recover_block,
 "local classifier wake is explicit":"AI_RECOVERY_READY" in runner and "AiEngineRunner.RECOVERY_READY" in radar and "continuePersistentAnalysis()" in radar,
 "version":"5.12.201-ai-category-state" in gradle,
}
for name,ok in checks.items():
 print(("PASS " if ok else "FAIL ")+name)
if not all(checks.values()):
 raise SystemExit(1)

# Executable state-machine fixture for the SQL safety boundary.
db=sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE market_listings(id INTEGER PRIMARY KEY,legacy_signature TEXT,temp_fingerprint TEXT,lifecycle TEXT,enrichment_state TEXT,manual_review_required INTEGER,game_id INTEGER);
CREATE TABLE games(id INTEGER PRIMARY KEY,bgg_id TEXT);
CREATE TABLE deals(signature TEXT PRIMARY KEY,verification_state TEXT,confirmed INTEGER,lifecycle TEXT);
CREATE TABLE listing_overrides(signature TEXT,item_id TEXT);
CREATE TABLE observations(id INTEGER PRIMARY KEY,signature TEXT,observed_at INTEGER,analysis_status TEXT,verification_state TEXT,verification_reason TEXT);
""")
db.execute("INSERT INTO market_listings VALUES(1,'sig1','fp1','AUTO_FILTERED','AUTO_EXCLUDED',0,10)")
db.execute("INSERT INTO games VALUES(10,'')")
db.execute("INSERT INTO deals VALUES('sig1','AUTO_EXCLUDED',0,'USER_HIDDEN')")
db.execute("INSERT INTO observations VALUES(1,'sig1',1234,'done','AUTO_EXCLUDED','old')")
where="""l.id>0 AND COALESCE(l.manual_review_required,0)=0 AND COALESCE(d.confirmed,0)=0
AND COALESCE(d.verification_state,'')<>'USER_CONFIRMED'
AND NOT EXISTS(SELECT 1 FROM listing_overrides u WHERE u.signature=COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint))
AND l.lifecycle='AUTO_FILTERED' AND l.enrichment_state='AUTO_EXCLUDED' AND COALESCE(g.bgg_id,'')=''
AND EXISTS(SELECT 1 FROM observations o WHERE o.signature=COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint))"""
sql="SELECT l.id FROM market_listings l LEFT JOIN games g ON g.id=l.game_id LEFT JOIN deals d ON d.signature=COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) WHERE "+where
assert db.execute(sql).fetchall()==[(1,)]

db.execute("INSERT INTO listing_overrides VALUES('sig1',NULL)")
assert db.execute(sql).fetchall()==[]
db.execute("DELETE FROM listing_overrides")
db.execute("UPDATE deals SET confirmed=1 WHERE signature='sig1'")
assert db.execute(sql).fetchall()==[]
db.execute("UPDATE deals SET confirmed=0 WHERE signature='sig1'")
db.execute("UPDATE games SET bgg_id='123' WHERE id=10")
assert db.execute(sql).fetchall()==[]
db.execute("UPDATE games SET bgg_id='' WHERE id=10")

old_at=db.execute("SELECT observed_at FROM observations WHERE id=1").fetchone()[0]
db.execute("UPDATE market_listings SET lifecycle='ACTIVE',enrichment_state='PENDING_ANALYSIS',game_id=NULL WHERE id=1 AND lifecycle='AUTO_FILTERED' AND enrichment_state='AUTO_EXCLUDED'")
db.execute("UPDATE observations SET analysis_status='pending',verification_state='PENDING_ANALYSIS',verification_reason='AI category recovery' WHERE id=(SELECT id FROM observations WHERE signature='sig1' ORDER BY observed_at DESC,id DESC LIMIT 1)")
assert db.execute("SELECT lifecycle,enrichment_state,game_id FROM market_listings WHERE id=1").fetchone()==("ACTIVE","PENDING_ANALYSIS",None)
assert db.execute("SELECT observed_at,analysis_status,verification_state FROM observations WHERE id=1").fetchone()==(old_at,"pending","PENDING_ANALYSIS")
assert db.execute("SELECT lifecycle,verification_state FROM deals WHERE signature='sig1'").fetchone()==("USER_HIDDEN","AUTO_EXCLUDED")
print("PASS recovery state machine preserves history and leaves deal/BGG trust closed")

