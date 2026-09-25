#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
main=(ROOT/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
session=(ROOT/"app/src/main/java/it/vintedaffari/app/VintedPublicSession.java").read_text(encoding="utf-8")
snapshots=(ROOT/"app/src/main/java/it/vintedaffari/app/VintedCandidateSnapshotStore.java").read_text(encoding="utf-8")
database=(ROOT/"app/src/main/java/it/vintedaffari/app/DealDatabase.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

def block(start, end):
    a=main.index(start); b=main.index(end,a+1); return main[a:b]

shared=block("    private void handleSharedVintedUrl","    private String vintedItemId")
manual=block("    private void applyManualVintedChoice","    private void confirmDeleteReviewListing")

checks=[
    ("shared-link lookup leaves the UI thread",
     "uiDataIo.execute" in shared and shared.index("uiDataIo.execute") < shared.index("marketStore.listing")),
    ("shared-link save leaves the UI thread",
     "maintenanceIo.execute" in shared and shared.index("maintenanceIo.execute") < shared.index("marketStore.applyManualVintedLink")),
    ("manual candidate save leaves the UI thread",
     "maintenanceIo.execute" in manual and manual.index("maintenanceIo.execute") < manual.index("marketStore.applyManualVintedLink")),
    ("manual UI callbacks happen after background persistence",
     "runOnUiThread" in shared and "runOnUiThread" in manual),
    ("durable snapshot lookup is read-only",
     "public static SearchSnapshot recentSearch" in snapshots and
     "getReadableDatabase()" in snapshots[snapshots.index("public static SearchSnapshot recentSearch"):snapshots.index("/** Called only after",snapshots.index("public static SearchSnapshot recentSearch"))] and
     "ensure(db)" not in snapshots[snapshots.index("public static SearchSnapshot recentSearch"):snapshots.index("/** Called only after",snapshots.index("public static SearchSnapshot recentSearch"))]),
    ("diagnostic ledger reads do not acquire a writer when already initialized",
     "ledgerReadDb" in session and "getReadableDatabase()" in session[session.index("ledgerReadDb"):session.index("requestLedgerSnapshot")]),
    ("cross-process busy timeout remains available to background writers",
     "PRAGMA busy_timeout=8000" in database),
    ("version bumped to ANR fix",
     "5.12.65-manual-vinted-ui-anr" in build),
]

for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("5.12.65 manual Vinted UI ANR regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} manual-link ANR guards")
