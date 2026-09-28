#!/usr/bin/env python3
"""Regression guards for safe-mode pricing, quality gates and opt-in Vinted capture."""
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
market=(ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
policy=(ROOT/"app/src/main/java/it/vintedaffari/app/DealPolicy.java").read_text(encoding="utf-8")
runner=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueJobRunner.java").read_text(encoding="utf-8")
radar=(ROOT/"app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
main=(ROOT/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
service=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueKeepAliveService.java").read_text(encoding="utf-8")
manifest=(ROOT/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

analysis_block=radar[radar.index("private void analyzeBatch"):radar.index("private void continuePersistentAnalysis")]
schedule_block=radar[radar.index("private void scheduleScan"):radar.index("private void scanVisibleVintedCards")]
metadata_block=market[market.index("public void applyBggMetadata"):market.index("public List<GameRecord> searchGames")]
repricing=market[market.index("static Integer resolveVintedReference"):market.index("/** Game-level comparison policy")]
legacy_refresh=market[market.index("public int refreshLegacyDealBenchmarks"):market.index("/** If price evidence changes")]

checks=[
    ("radar no longer overrides BGG benchmark with local Vinted median",
     "withUsedMarketBenchmark" not in analysis_block and "localVintedReferenceCents" not in analysis_block),
    ("central reference resolver is BGG-prior only",
     "return priorCents!=null&&priorCents>0?priorCents:null;" in repricing),
    ("local targeted repricing is disabled",
     "public int refreshLocalVintedBenchmarksForBgg" in market and
     "Safe mode: keep local observations for charts/audit" in market),
    ("old price-filtered rows can be restored only from BGG reference",
     "restorePriceFilteredFromBgg" in legacy_refresh and
     "verification_state='PRICE_FILTERED'" in legacy_refresh and
     "Ricalcolato in safe mode con riferimento BGG" in legacy_refresh),
    ("known sub-6 queue candidates cannot become match/review",
     "queueCandidateEligible" in policy and "qualityCandidates(" in runner and
     "qualityCandidates(matcher.localExactCandidates(q))" in runner and
     "qualityCandidates(matcher.localCandidatesIndexed(q))" in runner),
    ("Children's Game is an authoritative scouting exclusion",
     "childrenCategory" in policy and "BGG_CHILDRENS_GAME" in metadata_block and
     "qualityRejected=childrenGame||ratingRejected" in metadata_block),
    ("manual BGG picker applies the same quality gate",
     "DealPolicy.queueCandidateEligible(candidate)" in main and
     "BGG 6+ e non Children's Game" in main),
    ("existing low-quality rows receive one-time background cutover",
     "applySafeModeQualityCutover" in market and "safe_mode_quality_v51268" in market and
     "market.applySafeModeQualityCutover()" in service),
    ("Vinted capture is off by default and gated before scanning",
     "private boolean scanEnabled=false;" in radar and
     "if(!scanEnabled)return;" in schedule_block and
     "if (!scanEnabled || database == null) return;" in radar),
    ("diagnostics expose safe-mode state cross-process",
     'diagnosticState("scan_opt_in")' in radar and
     "scanOptIn={authoritative=" in radar and
     "pricingSafeMode=BGG_ONLY; localVinted=history-only" in radar and
     "publishScanOptInDiagnostic" in radar),
    ("Accessibility overlay controls explicit capture without draw-over-apps permission",
     "TYPE_ACCESSIBILITY_OVERLAY" in radar and "Ludo · SCANSIONE ON" in radar and "Ludo · OFF" in radar and
     "SYSTEM_ALERT_WINDOW" not in manifest),
    ("leaving Vinted turns capture off",
     "scanOverlayWatch" in radar and "setScanEnabled(false);hideScanOverlay();" in radar),
    ("release identity intact",
     "applicationId 'it.vintedaffari.app'" in build and "1000000 + ciVersionCode.toInteger()" in build),
]

for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("5.12.68 safe-mode regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} safe-mode guards")
