#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
market = (ROOT / "app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
method = market[market.index("public String catalogPipelineFunnel()"):market.index("public int pendingAnalysisCount()")]

checks = [
    ("24h funnel uses first-seen listing rows, not repeat observations", "l.first_seen>=" in method and "firstSeenListings24h=" in method),
    ("funnel separates analysis pending from unmatched BGG identity", "firstSeenPendingAnalysis24h=" in method and "firstSeenBggUnmatched24h=" in method),
    ("funnel distinguishes missing, low, and hidden BGG ratings", "firstSeenBggRatingPending24h=" in method and "firstSeenBggBelow6_24h=" in method and "firstSeenBggHidden24h=" in method),
    ("funnel exposes exact-link and publication outcomes", "firstSeenVintedLinkPending24h=" in method and "firstSeenExactVintedLink24h=" in method and "firstSeenCoreQualified24h=" in method),
    ("funnel marks review and local/deferred holds", "firstSeenManualReview24h=" in method and "firstSeenLocalOnly24h=" in method and "firstSeenDeferredLink24h=" in method),
    ("funnel is read-only and remains one aggregate query", "rawQuery(sql,null)" in method and "db.update(" not in method and "db.insert(" not in method and "db.delete(" not in method),
]
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
failed = [name for name, ok in checks if not ok]
if failed:
    raise SystemExit("Catalog pipeline diagnostics regression failed: " + ", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} catalog pipeline diagnostics guards")
