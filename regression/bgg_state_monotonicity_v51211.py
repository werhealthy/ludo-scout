#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
market = (ROOT / "app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
build = (ROOT / "app/build.gradle").read_text(encoding="utf-8")

apply_start = market.index("public void applyAnalysis")
apply_end = market.index("private static boolean shouldAutoResolveVinted", apply_start)
apply_block = market[apply_start:apply_end]

review_start = market.index("public void markBggMatchReview")
review_end = market.index("public int bggMatchReviewCount", review_start)
review_block = market[review_start:review_end]

upsert_start = market.index("private long upsertProvisionalGame")
upsert_end = market.index("private void deleteOrphanProvisional", upsert_start)
upsert_block = market[upsert_start:upsert_end]

reconcile_start = market.index("public int reconcileQueue()")
reconcile_end = market.index("public int vintedActiveCount()", reconcile_start)
reconcile_block = market[reconcile_start:reconcile_end]

checks = [
    ("stale analysis cannot mutate inactive listing", "listingLifecycle" in apply_block and '!"ACTIVE".equals(listingLifecycle)' in apply_block),
    ("late matched analysis cannot override review/quarantine", "oldGameState" in apply_block and '"BGG_MATCH_REVIEW".equals(oldGameState)' in apply_block and '"AUTO_QUARANTINED".equals(oldGameState)' in apply_block and apply_block.index("oldGameState") < apply_block.index('if ("matched".equals(analysis.status)')),
    ("listing state follows persisted game state", 'SELECT match_state FROM games WHERE id=?' in apply_block and 'matchState=TextUtils.isEmpty(persisted)?' in apply_block),
    ("quarantined game refilters stale listing", '"AUTO_QUARANTINED".equals(matchState)' in apply_block and '"AUTO_FILTERED_NON_GAME"' in apply_block),
    ("review transition updates game and listings", '"BGG_MATCH_REVIEW"' in review_block and 'db.update("market_listings"' in review_block),
    ("review transition is transactional", "db.beginTransaction()" in review_block and "db.setTransactionSuccessful()" in review_block),
    ("provisional refresh reads existing state", 'SELECT id,COALESCE(match_state' in upsert_block and "currentState" in upsert_block),
    ("review and quarantine are monotonic", '"PENDING_ANALYSIS".equals(currentState)||"BGG_MATCH_REQUIRED".equals(currentState)' in upsert_block and '"AUTO_QUARANTINED"' not in upsert_block.split("if(TextUtils.isEmpty(currentState)",1)[1].split("db.update",1)[0]),
    ("provisional refresh no longer unconditionally clears decisions", 'v.putNull("filter_reason")' in upsert_block and upsert_block.count('v.put("match_state",state)') == 2),
    ("reconcile repairs review drift", "reviewListing" in reconcile_block and "games WHERE match_state='BGG_MATCH_REVIEW'" in reconcile_block),
    ("reconcile repairs quarantine drift", "quarantinedListing" in reconcile_block and "games WHERE match_state='AUTO_QUARANTINED'" in reconcile_block),
    ("build invariants preserved", "applicationId 'it.vintedaffari.app'" in build and "1000000 + ciVersionCode.toInteger()" in build),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
if failed:
    raise SystemExit("BGG state monotonicity regression failed: " + ", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} BGG state monotonicity guards")
