#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
client = (ROOT / "app/src/main/java/it/vintedaffari/app/BggSearchClient.java").read_text(encoding="utf-8")
runner = (ROOT / "app/src/main/java/it/vintedaffari/app/QueueJobRunner.java").read_text(encoding="utf-8")
build = (ROOT / "app/build.gradle").read_text(encoding="utf-8")

exact_start = client.index("public List<Game> localExactCandidates")
exact_end = client.index("private void ensureCatalogIndex", exact_start)
exact = client[exact_start:exact_end]

catalog_start = client.index("private void ensureCatalogIndex")
catalog_end = client.index("public String localIndexSummary", catalog_start)
catalog = client[catalog_start:catalog_end]

match_start = runner.index("public static int matchBggIdentities")
match_end = runner.index("public static boolean processOneBgg", match_start)
match = runner[match_start:match_end]

checks = [
    ("no retained global alias map", "localExactIndex" not in client and "addExact(" not in client),
    ("exact lookup uses bounded lazy cache", "queueExactCache" in client and "return size()>64" in client and "localExactCandidates" in client),
    ("exact lookup reuses loaded catalog without gzip io", "ensureCatalogIndex()" in exact and "openSearchIndex()" not in exact and "GZIPInputStream" not in exact),
    ("shared catalog loader parses gzip once", catalog.count("openSearchIndex()") == 1 and "catalog.add(g)" in catalog and "byId.put(g.id,g)" in catalog),
    ("local id lookup remains O1 after load", ".get(bggId)" in client[client.index("public Game localById"):client.index("public Integer localMarketReferenceCents")]),
    ("matcher single-flight guard exists", "BGG_IDENTITY_RUNNING" in runner and "compareAndSet(false,true)" in match and "finally{BGG_IDENTITY_RUNNING.set(false);}" in match),
    ("busy matcher reports rather than duplicates", "state=BUSY" in match and "singleFlight=true" in match),
    ("completed matcher reports cold-index telemetry", ("build=bgg-local-match-v3" in match or "build=bgg-local-match-v4" in match) and "localIndexSummary()" in match),
    ("local index summary exposes load cost", "loadMs=" in client and "exactScans=" in client and "exactCacheHits=" in client),
    ("build invariants preserved", "applicationId 'it.vintedaffari.app'" in build and "1000000 + ciVersionCode.toInteger()" in build),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
if failed:
    raise SystemExit("BGG cold-index single-flight regression failed: " + ", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} BGG cold-index single-flight guards")
