#!/usr/bin/env python3
from pathlib import Path

policy=Path("app/src/main/java/it/vintedaffari/app/AiEnginePolicy.java").read_text(encoding="utf-8")
listings=Path("app/src/main/java/it/vintedaffari/app/AiEngineListings.java").read_text(encoding="utf-8")
market=Path("app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
radar=Path("app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
evidence=Path("app/src/main/java/it/vintedaffari/app/AiCategoryEvidence.java").read_text(encoding="utf-8")
gradle=Path("app/build.gradle").read_text(encoding="utf-8")

checks={
 "durable independent evidence written":"AiCategoryEvidence.remember" in listings and "queue_controls" in evidence,
 "only known product misses can reopen":"automaticProductMiss" in policy and "RECOVERABLE_SQL" in policy,
 "human protections retained":"engine_manual_review" in policy and "engine_confirmed" in policy and "listing_overrides" in listings,
 "existing BGG still blocks recovery":'!row.optString("bgg_id").isEmpty()' in policy,
 "runtime reads current input fingerprint":"AiCategoryEvidence.has" in market and "AiBetaListings.inputKey" in evidence,
 "unresolved gate accepts recovered product":"aiRecoveredProduct" in radar and "identità BGG ancora da verificare" in radar,
 "matched collision gate accepts recovered product":"identità BGG valutata dal matcher locale" in radar,
 "strong non-game still blocks":"isStrongNonGameText(card.title,card.rawDescription)" in radar,
 "AI never writes BGG identity":'put("bgg_id"' not in listings and 'db.update("games"' not in listings,
 "version":"5.12.202-ai-wait-deadline" in gradle,
}
for name,ok in checks.items():
 print(("PASS " if ok else "FAIL ")+name)
if not all(checks.values()):
 raise SystemExit(1)

