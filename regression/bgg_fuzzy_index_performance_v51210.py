#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
client = (ROOT / "app/src/main/java/it/vintedaffari/app/BggSearchClient.java").read_text(encoding="utf-8")
runner = (ROOT / "app/src/main/java/it/vintedaffari/app/QueueJobRunner.java").read_text(encoding="utf-8")
diag = (ROOT / "app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
build = (ROOT / "app/build.gradle").read_text(encoding="utf-8")

indexed_start = client.index("public List<Game> localCandidatesIndexed")
indexed_end = client.index("/** Exact/alias lookup", indexed_start)
indexed = client[indexed_start:indexed_end]

stream_start = client.index("private List<Game> localSearch")
stream_end = client.index("public List<Game> localCandidates(String query)", stream_start)
stream = client[stream_start:stream_end]

catalog_start = client.index("private void ensureCatalogIndex")
catalog_end = client.index("public String localIndexSummary", catalog_start)
catalog = client[catalog_start:catalog_end]

checks = [
    ("queue uses indexed fuzzy path", "matcher.localCandidatesIndexed(q)" in runner and "matcher.localCandidates(q)" not in runner),
    ("indexed fuzzy path avoids compressed catalog IO", "openSearchIndex()" not in indexed and "GZIPInputStream" not in indexed and "BufferedReader" not in indexed),
    ("short-lived/manual path remains streaming", "openSearchIndex()" in stream and "GZIPInputStream" in stream),
    ("catalog refs built in shared index pass", "catalog.add(g)" in catalog and "byId.put(g.id,g)" in catalog),
    ("no second full Game object graph for queue catalog", "copySearchGame" in client and "catalog.add(g)" in catalog),
    ("fuzzy candidate heap is bounded", "PriorityQueue<RankedLocal> top=new PriorityQueue<>(16" in indexed and "top.size()<16" in indexed),
    ("queue fuzzy cache is bounded", "queueFuzzyCache" in client and "return size()>32" in client),
    ("queue fuzzy cache cleared on shutdown", "queueFuzzyCache.clear()" in client and "localCatalogIndex=null" in client),
    ("matcher timing diagnostic emitted", 'setDiagnosticState("bgg_local_match"' in runner and "elapsedMs=" in runner),
    ("matcher timing diagnostic exposed", "bggLocalMatch={" in diag),
    ("build invariants preserved", "applicationId 'it.vintedaffari.app'" in build and "1000000 + ciVersionCode.toInteger()" in build),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
if failed:
    raise SystemExit("BGG fuzzy index performance regression failed: " + ", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} BGG fuzzy-index performance guards")
