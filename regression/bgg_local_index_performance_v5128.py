#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
client = (ROOT / "app/src/main/java/it/vintedaffari/app/BggSearchClient.java").read_text(encoding="utf-8")
build = (ROOT / "app/build.gradle").read_text(encoding="utf-8")

byid_start = client.index("public Game localById")
byid_end = client.index("public Integer localMarketReferenceCents", byid_start)
byid = client[byid_start:byid_end]

exact_start = client.index("private Map<String,List<Game>> exactIndex")
exact_end = client.index("private static void addExact", exact_start)
exact = client[exact_start:exact_end]

checks = [
    ("localById uses retained id index", "localByIdIndex" in byid and ".get(bggId)" in byid),
    ("localById does not rescan compressed catalog", "openSearchIndex()" not in byid and "BufferedReader" not in byid and "GZIPInputStream" not in byid),
    ("id and exact indexes built in one catalog pass", "Map<String,Game> byId" in exact and "byId.put(g.id,g)" in exact and "addExact(exact" in exact),
    ("single compressed catalog open during index build", exact.count("openSearchIndex()") == 1),
    ("id index cleared on shutdown", "localByIdIndex=null" in client),
    ("beta version bumped", "5.12.8-bgg-local-index-performance" in build),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
if failed:
    raise SystemExit("BGG local index performance regression failed: " + ", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} BGG local-index performance guards")
