#!/usr/bin/env python3
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
resolver = (ROOT/"app/src/main/java/it/vintedaffari/app/VintedLinkResolver.java").read_text(encoding="utf-8")
store = (ROOT/"app/src/main/java/it/vintedaffari/app/VintedCandidateSnapshotStore.java").read_text(encoding="utf-8")
listing = (ROOT/"app/src/main/java/it/vintedaffari/app/MarketListingRecord.java").read_text(encoding="utf-8")
session = (ROOT/"app/src/main/java/it/vintedaffari/app/VintedPublicSession.java").read_text(encoding="utf-8")
runner = (ROOT/"app/src/main/java/it/vintedaffari/app/QueueJobRunner.java").read_text(encoding="utf-8")
build = (ROOT/"app/build.gradle").read_text(encoding="utf-8")

checks = [
    ("canonical listing carries observation time into resolver",
     "d.firstSeen = firstSeen;" in listing and "d.lastSeen = lastSeen;" in listing),
    ("durable candidate snapshot has a production read path",
     "public static SearchSnapshot recentSearch" in store and
     "last_at>=?" in store and "lastAt+60_000L<observedAt" in store),
    ("resolver consults durable snapshot before public catalog HTTP",
     "searchSnapshotInto" in resolver and
     resolver.index("searchSnapshotInto") < resolver.index("searchInto(d,q,candidates)")),
    ("snapshot reuse keeps exact public item verification",
     "fromDurableSnapshot" in resolver and
     "Candidate verified=verifyPublicItem(best.id)" in resolver),
    ("durable snapshot cannot use catalog structured fast path",
     "!fromDurableSnapshot&&!canonicalOnly&&best.catalogStructured" in resolver),
    ("ambiguous or weak snapshot falls back to fresh catalog search",
     "if(snapshotSelection==null||!snapshotSelection.unique)" in resolver and
     "candidates.clear();" in resolver),
    ("request ledger resets to a fresh efficiency epoch",
     'LEDGER_BUILD="request-ledger-v3-efficiency"' in session),
    ("successful core links are counted directly rather than inferred from active rows",
     "recordResolvedLink" in session and
     "VintedPublicSession.recordResolvedLink" in runner and
     'control(db,LEDGER_PREFIX+"link:resolved")' in session),
    ("reported requests-per-link uses resolved events",
     "linkPhysical/(double)resolvedLinks" in session),
    ("request rate and app identity stay unchanged",
     "PUBLIC_MIN_INTERVAL_MS=55_000L" in session and
     "PUBLIC_HOURLY_BUDGET=60" in session and
     "applicationId 'it.vintedaffari.app'" in build),
]

for name, ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("5.12.64 Vinted request-efficiency regression failed: "+", ".join(failed))

# Executable freshness boundary: a stored search result can save HTTP only when it is recent
# and not materially older than the listing observation it is being used to resolve.
db=sqlite3.connect(":memory:")
db.execute("""CREATE TABLE vinted_shadow_snapshots_v3(
    query_key TEXT PRIMARY KEY, query_text TEXT, first_at INTEGER, last_at INTEGER,
    capture_count INTEGER, candidate_count INTEGER, structured_count INTEGER,
    link_count INTEGER, payload TEXT)""")
db.execute("INSERT INTO vinted_shadow_snapshots_v3 VALUES(?,?,?,?,?,?,?,?,?)",
           ("take time","Take Time",900,1000,1,3,0,3,"[]"))

def eligible(now, observed_at, max_age=86_400_000):
    row=db.execute("SELECT last_at FROM vinted_shadow_snapshots_v3 WHERE query_key=? AND last_at>=?",
                   ("take time", now-max_age)).fetchone()
    return bool(row and row[0]+60_000 >= observed_at)

assert eligible(2_000, 1_010)
assert eligible(2_000, 61_000)
assert not eligible(2_000, 61_001)
assert not eligible(86_402_000, 1_010)

print(f"PASS {len(checks)}/{len(checks)} request-efficiency guards + freshness fixture")
