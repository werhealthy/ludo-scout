#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ui=(ROOT/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
hunts=(ROOT/"app/src/main/java/it/vintedaffari/app/HuntDatabase.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

discover=ui[ui.index("private View discoverHeader()"):ui.index("private void addDiscoverCategories",ui.index("private View discoverHeader()"))]
catalog=ui[ui.index("private View catalogRowV51"):ui.index("private int photoCount",ui.index("private View catalogRowV51"))]
library=ui[ui.index("private void renderLibrary()"):ui.index("private View libraryRow",ui.index("private void renderLibrary()"))]
companion=ui[ui.index("private void renderCompanion()"):ui.index("private void renderLibraryInsights",ui.index("private void renderCompanion()"))]
huntrow=ui[ui.index("private View huntRow"):ui.index("private void addHuntFlow",ui.index("private View huntRow"))]

checks=[
    ("phase2 release identity",
     "versionName '5.12." in build and
     "applicationId 'it.vintedaffari.app'" in build and
     "1000000 + ciVersionCode.toInteger()" in build),
    ("Home uses the updated Discover editorial composition",
     "private void renderDiscover()" in ui and
     '"Scopri"' in discover and
     "addDiscoverFreshRail" in ui and
     "addDiscoverTopRatedRail" in ui),
    ("Catalog preview is a true horizontal listing row",
     "new LinearLayout(this)" in catalog and
     "setOrientation(LinearLayout.HORIZONTAL)" in catalog and
     "row.addView(info,new LinearLayout.LayoutParams(0,-2,1))" in catalog and
     "LinearLayout c=verticalCard()" not in catalog),
    ("Library is collection-first with summary search and history scope",
     "La tua collezione, non un altro catalogo." in library and
     "librarySummaryCard(owned)" in library and
     "librarySearchBar()" in library and
     "libraryScopeTabs(owned.size(),sold.size())" in library),
    ("Ludo has explicit Per me Cacce Profilo spaces",
     'addCompanionTab(tabs,"Per me","for_you")' in companion and
     'addCompanionTab(tabs,"Cacce","hunts")' in companion and
     'addCompanionTab(tabs,"Profilo","profile")' in companion),
    ("Ludo root page no longer exposes a back affordance",
     'TextView back=text("‹"' not in ui[ui.index("private View companionHeader"):ui.index("private boolean hasGreatDeal")]),
    ("Hunt rows are horizontal",
     "setOrientation(LinearLayout.HORIZONTAL)" in huntrow and "verticalCard()" not in huntrow),
    ("Adding a game fulfills its active Hunt",
     "huntDb.removeByBggId(g.id)" in ui and
     "public synchronized void removeByBggId" in hunts),
]

for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("5.12.37 UX phase 2 regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} 5.12.37 UX phase 2 guards")
