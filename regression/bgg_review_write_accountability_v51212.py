#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
market = (ROOT / "app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
runner = (ROOT / "app/src/main/java/it/vintedaffari/app/QueueJobRunner.java").read_text(encoding="utf-8")
diag = (ROOT / "app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
build = (ROOT / "app/build.gradle").read_text(encoding="utf-8")

review_start = market.index("public int markBggMatchReview")
review_end = market.index("public int bggMatchReviewCount", review_start)
review = market[review_start:review_end]

match_start = runner.index("public static int matchBggIdentities")
match_end = runner.index("public static boolean processOneBgg", match_start)
match = runner[match_start:match_end]

checks = [
    ("review method returns actual write count", "public int markBggMatchReview" in market and "return changed;" in review),
    ("canonical row read occurs inside write transaction", review.index("db.beginTransaction()") < review.index("SELECT COALESCE(match_state") < review.index('db.update("games"')),
    ("concurrent authoritative match is protected", 'if(!TextUtils.isEmpty(before)&&TextUtils.isEmpty(bgg))' in review),
    ("review update is by exact game id after transactional validation", 'db.update("games",v,"id=?"' in review),
    ("linked active listings follow canonical review", 'db.update("market_listings"' in review and '"NEEDS_REVIEW"' in review),
    ("write diagnostic includes before after and changed", 'setDiagnosticState("bgg_match_review_write"' in review and "before=" in review and "after=" in review and "gameChanged=" in review),
    ("matcher separates review decisions from successful writes", "reviewDecisions" in match and "reviewWrites" in match and "reviewWriteMisses" in match),
    ("matcher reports remaining required after batch", "remainingRequired=market.bggMatchRequiredCount()" in match and "remainingRequired=" in match),
    ("local matcher diagnostic version advanced", "build=bgg-local-match-v2" in match),
    ("review-write diagnostic exposed to Pixel", "bggReviewWrite={" in diag),
    ("build invariants preserved", "applicationId 'it.vintedaffari.app'" in build and "1000000 + ciVersionCode.toInteger()" in build),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
if failed:
    raise SystemExit("BGG review-write accountability regression failed: " + ", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} BGG review-write accountability guards")
