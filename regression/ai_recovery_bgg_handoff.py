#!/usr/bin/env python3
from pathlib import Path

policy=Path("app/src/main/java/it/vintedaffari/app/AiEnginePolicy.java").read_text(encoding="utf-8")
listings=Path("app/src/main/java/it/vintedaffari/app/AiEngineListings.java").read_text(encoding="utf-8")
market=Path("app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
radar=Path("app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
gradle=Path("app/build.gradle").read_text(encoding="utf-8")

persist=radar[radar.index("private void persistAnalysisResults"):radar.index("private void continuePersistentAnalysis")]
checks={
 "bounce retry is exact": "AUTO_FILTERED_NON_GAME" in policy and "Nessuna prova positiva di prodotto gioco da tavolo" in policy,
 "bounce retry remains filtered-only": "AUTO_FILTERED" in policy and "AUTO_EXCLUDED" in policy,
 "existing BGG still blocks recovery": '!row.optString("bgg_id").isEmpty()' in policy,
 "human protections retained": "engine_manual_review" in policy and "engine_confirmed" in policy and "listing_overrides" in listings,
 "AI marker is one-shot state": 'startsWith("AI_CATEGORY_RECOVERED:")' in market and '"PENDING_ANALYSIS".equals(c.getString(1))' in market,
 "local strong non-game still wins": "isStrongNonGameText(card.title,card.rawDescription)" in persist,
 "recovered uncertain bypasses only product gate": "aiRecoveredProduct" in persist and "ListingClassifier.Type.UNCERTAIN" in persist,
 "BGG analysis still runs normally": "marketStore.applyAnalysis(card,ga,analyzedListing,t)" in persist,
 "AI does not set BGG identity": 'put("bgg_id"' not in listings and 'db.update("games"' not in listings,
 "version":"5.12.199-ai-recovery-bgg-handoff" in gradle,
}
for name,ok in checks.items():
 print(("PASS " if ok else "FAIL ")+name)
if not all(checks.values()):
 raise SystemExit(1)
