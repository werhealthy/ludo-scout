#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
client = (ROOT / "app/src/main/java/it/vintedaffari/app/BggSearchClient.java").read_text(encoding="utf-8")
build = (ROOT / "app/build.gradle").read_text(encoding="utf-8")

byid_start = client.index("public Game localById")
byid_end = client.index("public Integer localMarketReferenceCents", byid_start)
byid = client[byid_start:byid_end]

catalog_start = client.index("private void ensureCatalogIndex")
catalog_end = client.index("public String localIndexSummary", catalog_start)
catalog = client[catalog_start:catalog_end]

checks = [
    ("localById uses retained id index", "localByIdIndex" in byid and ".get(bggId)" in byid),
    ("localById does not rescan compressed catalog", "openSearchIndex()" not in byid and "BufferedReader" not in byid and "GZIPInputStream" not in byid),
    ("id and catalog refs built in one catalog pass", "Map<String,Game> byId" in catalog and "byId.put(g.id,g)" in catalog and "catalog.add(g)" in catalog),
    ("single compressed catalog open during shared index build", catalog.count("openSearchIndex()") == 1),
    ("global all-alias exact map removed", "localExactIndex" not in client and "addExact(" not in client),
    ("id index cleared on shutdown", "localByIdIndex=null" in client and "localCatalogIndex=null" in client),
    ("build invariants preserved", "applicationId 'it.vintedaffari.app'" in build and "1000000 + ciVersionCode.toInteger()" in build),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
if failed:
    raise SystemExit("BGG local index performance regression failed: " + ", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} BGG local-index performance guards")
