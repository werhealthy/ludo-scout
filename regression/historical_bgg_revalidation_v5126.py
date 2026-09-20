#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
market = (ROOT / "app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
reval = (ROOT / "app/src/main/java/it/vintedaffari/app/BggHistoricalRevalidator.java").read_text(encoding="utf-8")
service = (ROOT / "app/src/main/java/it/vintedaffari/app/QueueKeepAliveService.java").read_text(encoding="utf-8")
worker = (ROOT / "app/src/main/java/it/vintedaffari/app/QueueDrainWorker.java").read_text(encoding="utf-8")
diag = (ROOT / "app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
build = (ROOT / "app/build.gradle").read_text(encoding="utf-8")

candidates_block = market[market.index("public List<HistoricalBggCandidate> historicalBggRevalidationCandidates"):market.index("public void flagHistoricalBggReview")]
pending_block = market[market.index("public int historicalBggRevalidationPendingCount()"):market.index("public String historicalBggRevalidationSummary()")]

checks = [
    ("manual identities excluded", "a.source='MANUAL_BGG'" in market and "d.verification_state='USER_CONFIRMED'" in market),
    ("per-game one-shot marker", 'BGG_REVALIDATION_PREFIX = "bgg_revalidation_v1:"' in market and "queue_controls q" in market),
    ("user-confirmed rows excluded from candidates and pending metrics", "d.verification_state='USER_CONFIRMED'" in candidates_block and "d.verification_state='USER_CONFIRMED'" in pending_block),
    ("mixed games stay review in persistent accounting", 'q.put("value","VERIFIED".equals(state)?1:2)' in market),
    ("zero-network exact-only revalidation", "localExactCandidates" in reval and "localCandidates(" not in reval and "searchFast(" not in reval),
    ("weak evidence becomes review", "plausibile ma non esatto/univoco" in reval and "manual_review_required" in market),
    ("review preserves historical identity", "flagHistoricalBggReview" in market and "db.delete" not in market[market.index("public void flagHistoricalBggReview"):market.index("public void completeHistoricalBggRevalidation")]),
    ("legacy feed blocked on review", 'verification_state","MATCH_UNCERTAIN"' in market),
    ("bounded historical slices", "Math.min(32,limit)" in reval and "BggHistoricalRevalidator.runSlice(market,bggMatcher,24)" in service),
    ("recovery worker advances audit only after current BGG", "market.bggMatchRequiredCount()==0&&market.runnableBggDueCount(now)==0" in worker and "BggHistoricalRevalidator.runSlice(market,bggMatcher,Math.min(24,MAX_ITEMS-processed))" in worker),
    ("diagnostics expose revalidation", "bggHistoricalRevalidation={" in diag),
    ("build invariants preserved", "applicationId 'it.vintedaffari.app'" in build and "1000000 + ciVersionCode.toInteger()" in build),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
if failed:
    raise SystemExit("Historical BGG revalidation regression failed: " + ", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} historical BGG revalidation guards")
