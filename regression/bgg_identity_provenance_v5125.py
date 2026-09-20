#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
market = (ROOT / "app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
queue = (ROOT / "app/src/main/java/it/vintedaffari/app/QueueJobRunner.java").read_text(encoding="utf-8")
a11y = (ROOT / "app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")

start = market.index("public String learnedBggIdForTitle")
end = market.index("public String bggIdentityTrustSummary", start)
learned = market[start:end]

trusted_sources = [
    "BGG_PRIMARY",
    "BGG_ORIGINAL",
    "BGG_ALTERNATE",
    "BGG_ALIAS",
    "AUTO_LOCAL_BGG",
    "MANUAL_BGG",
]

checks = [
    ("matcher algorithm version bumped", "BGG_MATCH_ALGORITHM_VERSION = 4" in market),
    ("all authoritative alias sources admitted", all(src in learned for src in trusted_sources)),
    ("seller Vinted aliases excluded from authoritative lookup", "'VINTED'" not in learned and "VINTED_VARIANT" not in learned),
    ("seller aliases remain stored as non-authoritative evidence", 'addAlias(db, gameId, card.title, "VINTED")' in market and '"VINTED_VARIANT"' in market),
    ("queue uses provenance-aware learned lookup", "market.learnedBggIdForTitle(q)" in queue),
    ("historical matched identities are auditable", "matchedToRevalidate" in market and "COALESCE(g.match_algorithm_version,0)<CAST(? AS INTEGER)" in market),
    ("diagnostics expose trust audit", "bggIdentityTrust={" in a11y and "bggIdentityTrustSummary" in a11y),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
if failed:
    raise SystemExit("BGG identity provenance regression failed: " + ", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} BGG identity provenance guards")
