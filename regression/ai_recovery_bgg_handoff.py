#!/usr/bin/env python3
from pathlib import Path
import sqlite3

policy=Path("app/src/main/java/it/vintedaffari/app/AiEnginePolicy.java").read_text(encoding="utf-8")
listings=Path("app/src/main/java/it/vintedaffari/app/AiEngineListings.java").read_text(encoding="utf-8")
market=Path("app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
radar=Path("app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
gradle=Path("app/build.gradle").read_text(encoding="utf-8")

runner=Path("app/src/main/java/it/vintedaffari/app/QueueJobRunner.java").read_text(encoding="utf-8")
ai_runner=Path("app/src/main/java/it/vintedaffari/app/AiEngineRunner.java").read_text(encoding="utf-8")
handoff=market[market.index("public int materializeAiRecoveredBggCandidates"):market.index("/** Zero-network maintenance",market.index("public int materializeAiRecoveredBggCandidates"))]

persist=radar[radar.index("private void persistAnalysisResults"):radar.index("private void continuePersistentAnalysis")]
checks={
 "bounce retry is exact": "AUTO_FILTERED_NON_GAME" in policy and "Nessuna prova positiva di prodotto gioco da tavolo" in policy,
 "bounce retry remains filtered-only": "AUTO_FILTERED" in policy and "AUTO_EXCLUDED" in policy,
 "existing BGG still blocks recovery": '!row.optString("bgg_id").isEmpty()' in policy,
 "human protections retained": "engine_manual_review" in policy and "engine_confirmed" in policy and "listing_overrides" in listings,
 "AI proof survives analysis state changes": "AiCategoryEvidence.has(db,listingId,card)" in market,
 "local strong non-game still wins": "isStrongNonGameText(card.title,card.rawDescription)" in persist,
 "recovered uncertain bypasses only product gate": "aiRecoveredProduct" in persist and "ListingClassifier.Type.UNCERTAIN" in persist,
 "BGG analysis still runs normally": "marketStore.applyAnalysis(card,ga,analyzedListing,t)" in persist,
 "AI does not set BGG identity": 'put("bgg_id"' not in listings and 'db.update("games"' not in listings,
 "version":"5.12.202-ai-wait-deadline" in gradle,
 "AI recovery handoff is strict": "AI_CATEGORY_RECOVERED:%" in handoff and "l.game_id IS NULL" in handoff and "l.match_state='PENDING_ANALYSIS'" in handoff,
 "AI recovery handoff protects users": "listing_overrides" in handoff and "COALESCE(l.manual_review_required,0)=0" in handoff and "AiCategoryEvidence.has(db,listingId)" in handoff,
 "AI recovery creates only provisional BGG work": 'upsertProvisionalGame(db,title,"BGG_MATCH_REQUIRED"' in handoff and "identityOwner=BGG" in handoff,
 "AI recovery exact Vinted identity is core complete": 'exactIdentity?"CORE_COMPLETE":"PENDING_ANALYSIS"' in handoff,
 "AI recovery preserves BGG ambiguity": '"BGG_MATCH_REVIEW".equals(gameState)' in handoff and 'review.put("manual_review_required",1)' in handoff,
 "AI recovery may reopen only automatic quarantine": '"AUTO_QUARANTINED".equals(gameState)' in handoff and 'reopen.put("match_state","BGG_MATCH_REQUIRED")' in handoff,
 "AI recovery handoff is zero network": all(token not in handoff for token in ["HttpURLConnection","VintedPublicSession","AiBetaClient","BggSearchClient"]),
 "queue maintenance materializes AI recoveries": "market.materializeAiRecoveredBggCandidates(40)" in runner,
 "AI recovery wakes queue owner": "QueueKeepAliveService.ensureRunning(app)" in ai_runner and "QueueWorkScheduler.schedule(app)" in ai_runner,
 "AI recovery persists product type not identity": 'observation.put("listing_type","BASE_GAME")' in listings and 'observation.put("verification_state","MATCH_UNCERTAIN")' in listings and 'put("bgg_id"' not in listings[listings.index("if(AiEnginePolicy.recover"):listings.index("continue;",listings.index("if(AiEnginePolicy.recover"))+9],
 "historical AI recovery type repair exists": "public int repairAiRecoveredObservationType" in market and "identityOwner=BGG" in market,
 "historical AI recovery type repair is zero network": all(token not in market[market.index("public int repairAiRecoveredObservationType"):market.index("/** Bridge AI product recovery",market.index("public int repairAiRecoveredObservationType"))] for token in ["HttpURLConnection","VintedPublicSession","AiBetaClient","BggSearchClient"]),
 "changed AI recovery evidence can refresh": "refreshRecoveredProductEvidence" in policy and "engine_latest_ai_recovered" in listings and "AiCategoryEvidence.remember" in listings and "refreshedCount()" in listings,
 "AI refresh still writes no BGG identity": 'put("bgg_id"' not in listings[listings.index("if(AiEnginePolicy.refreshRecoveredProductEvidence"):listings.index("if(!AiEnginePolicy.hold",listings.index("if(AiEnginePolicy.refreshRecoveredProductEvidence"))],
 "AI refresh wakes local bridge": "refreshed=listings.refreshedCount()" in ai_runner and "recovered>0||refreshed>0" in ai_runner,
 "pending AI recovery reschedules itself": '"PENDING_RECOVERY".equals(result.state)' in ai_runner and "QueueWorkScheduler.scheduleAfter" in ai_runner,
}
for name,ok in checks.items():
 print(("PASS " if ok else "FAIL ")+name)
if not all(checks.values()):
 raise SystemExit(1)

# Semantic guard for the durable candidate predicate: only the untouched automatic recovery qualifies.
db=sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE market_listings(
 id INTEGER PRIMARY KEY,vinted_title TEXT,vinted_item_id TEXT,vinted_url TEXT,
 legacy_signature TEXT,temp_fingerprint TEXT,lifecycle TEXT,game_id INTEGER,
 enrichment_state TEXT,match_state TEXT,manual_review_required INTEGER,last_error TEXT,last_seen INTEGER);
CREATE TABLE observations(
 id INTEGER PRIMARY KEY,signature TEXT,observed_at INTEGER,verification_state TEXT,listing_type TEXT,verification_reason TEXT);
CREATE TABLE listing_overrides(signature TEXT,item_id TEXT);
""")
db.executemany("INSERT INTO market_listings VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",[
 (1,"Recovered","11","https://vinted/items/11","s1","s1","ACTIVE",None,"PENDING_ANALYSIS","PENDING_ANALYSIS",0,"AI_CATEGORY_RECOVERED: proof",10),
 (2,"Human override","22","https://vinted/items/22","s2","s2","ACTIVE",None,"PENDING_ANALYSIS","PENDING_ANALYSIS",0,"AI_CATEGORY_RECOVERED: proof",9),
 (3,"Ordinary pending","33","https://vinted/items/33","s3","s3","ACTIVE",None,"PENDING_ANALYSIS","PENDING_ANALYSIS",0,"",8),
])
db.executemany("INSERT INTO observations VALUES(?,?,?,?,?,?)",[
 (1,"s1",10,"PENDING_ANALYSIS","UNCERTAIN",None),
 (2,"s2",9,"PENDING_ANALYSIS","UNCERTAIN",None),
 (3,"s3",8,"PENDING_ANALYSIS","UNCERTAIN",None),
])
db.execute("INSERT INTO listing_overrides VALUES('s2',NULL)")
candidate_sql="""SELECT l.id FROM market_listings l
WHERE l.lifecycle='ACTIVE' AND l.game_id IS NULL
AND l.enrichment_state='PENDING_ANALYSIS' AND l.match_state='PENDING_ANALYSIS'
AND COALESCE(l.manual_review_required,0)=0
AND COALESCE(l.last_error,'') LIKE 'AI_CATEGORY_RECOVERED:%'
AND EXISTS(SELECT 1 FROM observations o WHERE o.id=(SELECT x.id FROM observations x
 WHERE x.signature=COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint)
 ORDER BY x.observed_at DESC,x.id DESC LIMIT 1)
 AND ((o.verification_state='PENDING_ANALYSIS' AND o.listing_type IN ('UNCERTAIN','BASE_GAME'))
 OR (o.verification_state='MATCH_UNCERTAIN' AND o.listing_type='BASE_GAME'
 AND o.verification_reason='AI category recovery: base game visually recognized; BGG pending')))
AND NOT EXISTS(SELECT 1 FROM listing_overrides u WHERE
 u.signature=COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint)
 OR (u.item_id IS NOT NULL AND u.item_id=l.vinted_item_id))
ORDER BY l.last_seen DESC,l.id DESC"""
assert [row[0] for row in db.execute(candidate_sql)]==[1]
print("PASS semantic recovered-candidate predicate")
print(f"PASS {len(checks)+1}/{len(checks)+1} AI recovery BGG handoff guards")

