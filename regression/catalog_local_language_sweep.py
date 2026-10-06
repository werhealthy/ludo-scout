#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
runner=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueJobRunner.java").read_text(encoding="utf-8")
market=(ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
detector=(ROOT/"app/src/main/java/it/vintedaffari/app/ListingLanguageDetector.java").read_text(encoding="utf-8")

checks=[
 ("automatic sweep covers full current active catalog","market.inferDeferredLanguages(750);" in runner),
 ("language inference stays local","HttpURLConnection" not in detector and "Vinted" not in detector and "BggSearchClient" not in detector),
 ("weak evidence stays unknown",'return "";' in detector),
 ("supported hints remain bounded",all(code in detector for code in ['return "IT"','return "EN"','return "DE"','return "FR"','return "ES"','return "NL"','return "PT"'])),
 ("dependency markers preserved","mergeWithDependency" in detector and '"|DEP"' in detector and '"|IND"' in detector),
 ("market inference scans active missing language only","lifecycle='ACTIVE'" in market and "language_code IS NULL OR language_code='' OR language_code LIKE '?%'" in market),
]
for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
bad=[name for name,ok in checks if not ok]
if bad:
    raise SystemExit("language sweep regression failed: "+", ".join(bad))
print(f"PASS {len(checks)}/{len(checks)} local language sweep guards")
