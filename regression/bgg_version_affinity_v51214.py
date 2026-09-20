#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
market = (ROOT / "app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
diag = (ROOT / "app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
build = (ROOT / "app/build.gradle").read_text(encoding="utf-8")

checks = [
    ("all BGG algorithm comparisons cast parameter numerically", "COALESCE(match_algorithm_version,0)<?" not in market and "COALESCE(g.match_algorithm_version,0)<?" not in market),
    ("unqualified version comparisons use numeric cast", market.count("COALESCE(match_algorithm_version,0)<CAST(? AS INTEGER)") >= 3),
    ("qualified version comparisons use numeric cast", market.count("COALESCE(g.match_algorithm_version,0)<CAST(? AS INTEGER)") >= 2),
    ("required count uses cast", "bggMatchRequiredCount()" in market and "COALESCE(match_algorithm_version,0)<CAST(? AS INTEGER)" in market[market.index("public int bggMatchRequiredCount()"):market.index("public int userVisibleActiveCount()")]),
    ("candidate query uses cast", "provisionalGamesForMatching" in market and "COALESCE(match_algorithm_version,0)<CAST(? AS INTEGER)" in market[market.index("provisionalGamesForMatching"):market.index("historicalBggRevalidationCandidates")]),
    ("historical candidates use cast", "COALESCE(g.match_algorithm_version,0)<CAST(? AS INTEGER)" in market[market.index("historicalBggRevalidationCandidates"):market.index("flagHistoricalBggReview")]),
    ("historical pending uses cast", "COALESCE(g.match_algorithm_version,0)<CAST(? AS INTEGER)" in market[market.index("historicalBggRevalidationPendingCount"):market.index("historicalBggRevalidationSummary")]),
    ("breakdown distinguishes pure required from legacy/current review", "bggMatchRequiredBreakdown" in market and "requiredPure=" in market and "reviewLegacy=" in market and "reviewCurrent=" in market),
    ("breakdown exposed to Pixel diagnostic", "bggMatchBreakdown={" in diag),
    ("build invariants preserved", "applicationId 'it.vintedaffari.app'" in build and "1000000 + ciVersionCode.toInteger()" in build),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
if failed:
    raise SystemExit("BGG version affinity regression failed: " + ", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} BGG version-affinity guards")
