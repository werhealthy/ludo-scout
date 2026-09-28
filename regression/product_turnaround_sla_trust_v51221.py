#!/usr/bin/env python3
import sqlite3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
deal=(ROOT/"app/src/main/java/it/vintedaffari/app/DealDatabase.java").read_text(encoding="utf-8")
market=(ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
runner=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueJobRunner.java").read_text(encoding="utf-8")
classifier=(ROOT/"app/src/main/java/it/vintedaffari/app/ListingClassifier.java").read_text(encoding="utf-8")
gate=(ROOT/"app/src/main/java/it/vintedaffari/app/BoardGameIntakeGate.java").read_text(encoding="utf-8")
normalizer=(ROOT/"app/src/main/java/it/vintedaffari/app/BggTitleNormalizer.java").read_text(encoding="utf-8")
hunt=(ROOT/"app/src/main/java/it/vintedaffari/app/HuntDatabase.java").read_text(encoding="utf-8")
radar=(ROOT/"app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
ui=(ROOT/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
startup=(ROOT/"app/src/main/java/it/vintedaffari/app/EngineStartupMaintenance.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

# Product timing model: ten minutes is a target for small scrolls, never a correctness cutoff.
GAP=3*60_000
def engine_done(end_at, now, analysis_pending, valid, ready, review):
    if now-end_at < GAP:
        return False
    return analysis_pending==0 and (valid==0 or ready+review>=valid)

assert not engine_done(100_000, 100_000+9*60_000, 0, 7, 3, 3)
assert not engine_done(100_000, 100_000+60*60_000, 0, 7, 3, 3)
assert engine_done(100_000, 100_000+4*60_000, 0, 7, 4, 3)

# Trusted-home model: a hot deal is not enough. Identity, product type and canonical completion
# all have to agree before a result is public.
db=sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE deals(signature TEXT PRIMARY KEY,lifecycle TEXT,tier TEXT,rating REAL,bgg_id TEXT,
                   vinted_item_id TEXT,vinted_url TEXT,verification_state TEXT,listing_type TEXT);
CREATE TABLE games(id INTEGER PRIMARY KEY,bgg_id TEXT,match_state TEXT);
CREATE TABLE market_listings(id INTEGER PRIMARY KEY,legacy_signature TEXT,vinted_item_id TEXT,
                   lifecycle TEXT,enrichment_state TEXT,match_state TEXT,manual_review_required INTEGER,game_id INTEGER);
CREATE TABLE processing_jobs(id INTEGER PRIMARY KEY,listing_id INTEGER,job_type TEXT,state TEXT);
INSERT INTO games VALUES(1,'100','MATCHED');
INSERT INTO deals VALUES('trusted','ACTIVE','hot',7.5,'100','v1','https://vinted/item/1','OK','BASE_GAME');
INSERT INTO market_listings VALUES(1,'trusted','v1','ACTIVE','CORE_COMPLETE','MATCHED',0,1);
INSERT INTO deals VALUES('unfinished','ACTIVE','hot',8.0,'100',NULL,NULL,'OK','BASE_GAME');
INSERT INTO market_listings VALUES(2,'unfinished',NULL,'ACTIVE','PENDING_ENRICHMENT','MATCHED',0,1);
INSERT INTO deals VALUES('review','ACTIVE','hot',8.0,'100','v3','https://vinted/item/3','MATCH_UNCERTAIN','BASE_GAME');
INSERT INTO market_listings VALUES(3,'review','v3','ACTIVE','COMPLETE','MATCHED',1,1);
INSERT INTO deals VALUES('accessory','ACTIVE','hot',8.0,'100','v4','https://vinted/item/4','OK','ACCESSORY');
INSERT INTO market_listings VALUES(4,'accessory','v4','ACTIVE','COMPLETE','MATCHED',0,1);
""")
trusted=db.execute("""
SELECT d.signature FROM deals d
WHERE d.lifecycle='ACTIVE' AND d.tier IN ('hot','good') AND d.rating>=6
AND d.bgg_id<>'' AND d.vinted_item_id<>'' AND d.vinted_url<>''
AND d.verification_state IN ('OK','USER_CONFIRMED')
AND d.listing_type IN ('BASE_GAME','EXPANSION','GAME')
AND EXISTS(
  SELECT 1 FROM market_listings l JOIN games g ON g.id=l.game_id
  WHERE l.legacy_signature=d.signature
  AND l.lifecycle='ACTIVE' AND l.enrichment_state IN ('COMPLETE','CORE_COMPLETE')
  AND l.match_state='MATCHED' AND l.manual_review_required=0
  AND g.match_state='MATCHED' AND g.bgg_id=d.bgg_id
  AND NOT EXISTS(SELECT 1 FROM processing_jobs j WHERE j.listing_id=l.id
                 AND j.job_type<>'VINTED_DEEP_ENRICHMENT'
                 AND j.state IN ('PENDING','PROCESSING','FAILED_RETRYABLE'))
)
""").fetchall()
assert trusted==[("trusted",)], trusted

checks=[
    ("Motore timing is adaptive with a ten-minute small-scroll target",
     "ENGINE_RUN_TARGET_MIN_MS=10L*60_000L" in deal and "ENGINE_RUN_REMOTE_UNIT_MS=55_000L" in deal and "engineTargetMs" in deal),
    ("elapsed time is telemetry, never an automatic exclusion reason",
     "observeEngineTiming(now)" in market and "nonDestructive=true" in market and "Motore SLA 10 minuti" not in market),
    ("ordinary Vinted ambiguity is not a manual task",
     "settleAutomaticAmbiguity" in runner and "autoExcludeJob(job,reason)" in runner and
     "isExplicitUserPriority(job)" in runner),
    ("explicit Hunt/manual ambiguity can still reach review",
     '"HUNT_PRIORITY".equals(job.source)' in market and '"MANUAL_PRIORITY".equals(job.source)' in market),
    ("optional deep metadata cannot block Motore readiness",
     "l.enrichment_state IN ('COMPLETE','CORE_COMPLETE')" in deal and "j.job_type<>'VINTED_DEEP_ENRICHMENT'" in deal),
    ("deep metadata failure only excludes a genuinely pending BGG variant",
     "isBggVariantPending(job.listingId)" in runner and "if(!market.isBggVariantPending(job.listingId)){market.completeJob(job);return;}" in runner),
    ("old automatic review debt has a non-destructive cutover",
     "archiveAutomaticReviewDebtBefore" in market and "v51221ReviewTurnaroundApplied" in startup and "EPOCH_ARCHIVED_REVIEW" in market),
    ("existing accessory/non-game pollution is re-swept in the background queue owner",
     "autoHideStrongNonGameListings" in market and "ListingClassifier.classify(card)" in market and
     "v51221ProductNoiseSweepApplied" in startup and "SWEEP_EXEC.execute" in startup),
    ("video-game platform signals are filtered before BGG",
     '"ps5"' in classifier and '"nintendo switch"' in classifier and '"xbox series"' in classifier and
     '"ps5"' in gate and '"nintendo switch"' in gate),
    ("component nouns alone no longer reject full games",
     '"solo tessere"' in classifier and '"solo dadi"' in classifier and
     '"dadi", "meeple", "tiles", "tessere"' not in classifier),
    ("BGG review band is narrow",
     "g.searchScore>=820" in gate and "score>=78.0" in gate and
     'if(hasStrongBoardGameCue(title))return new Decision(Action.REVIEW' not in gate),
    ("descriptive Vinted suffix can produce an exact-title variant",
     "descriptiveLead(raw)" in normalizer and 'suffix.contains("gioco ")' in normalizer),
    ("Home is trusted-only",
     'db.getDeals("trusted",320)' in ui and '"trusted".equals(filter)' in deal),
    ("Motore cards open the standard product sheet",
     "openListingProductDetail(item.listingId,item.gameId,item.signature)" in ui and "openDetail(deal)" in ui),
    ("product detail exposes a correction action for the linked game",
     ('text("Gioco sbagliato"' in ui or 'menuAction("Correggi gioco associato"' in ui) and
     "showMatchCorrection(d,detail)" in ui),
    ("Hunts can preserve exact candidates outside resale tiers",
     "wantsCandidate(Context context,String bggId,Integer totalCents)" in hunt and
     "recordHuntCandidate" in deal and "promoteLegacyListingForHunt" in radar and
     'db.getDeals("trusted_any_price",800)' in ui),
    ("diagnostics expose adaptive timing and review rate",
     "targetMs=" in radar and "targetRemainingMs=" in radar and "timingNonDestructive=true" in radar and "reviewPct=" in radar and "engineSla={" in radar),
    ("build invariants preserved",
     "applicationId 'it.vintedaffari.app'" in build and "1000000 + ciVersionCode.toInteger()" in build and
     "versionName '5.12." in build),
]
for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("Product turnaround regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} product-turnaround guards")
