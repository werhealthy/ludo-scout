#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ui=(ROOT/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

home=ui[ui.index("private void renderDiscover()"):ui.index("private void addBundleEmptyState",ui.index("private void renderDiscover()"))]
section=ui[ui.index("private void sectionHeader"):ui.index("private void openCatalogPreset",ui.index("private void sectionHeader"))]
card=ui[ui.index("private View catalogRowV51"):ui.index("private int photoCount",ui.index("private View catalogRowV51"))]
manual=ui[ui.index("private void showListingActions"):ui.index("private void showExcluded",ui.index("private void showListingActions"))]
tile=ui[ui.index("private View tileCardV51"):ui.index("private View scoreView",ui.index("private View tileCardV51"))]

checks=[
    ("release identity",
     "versionName '5.12." in build and
     "applicationId 'it.vintedaffari.app'" in build and
     "1000000 + ciVersionCode.toInteger()" in build),
    ("Home independently populates offer, BGG and fresh rails",
     "addDiscoverFreshRail(limitDeals(newest,12))" in home and
     "addDiscoverTopRatedRail(limitDeals(topRated,3))" in home and
     "addDiscoverValueRail(limitDeals(value,12))" in home and
     "publicationAgeMinutes(a)" in home and "a.rank" in home),
    ("Home follows the approved reference order",
     home.index("heroOpportunityCard") < home.index("addDiscoverCategories") <
     home.index("addDiscoverValueRail") < home.index("addDiscoverTopRatedRail") <
     home.index("addDiscoverFreshRail")),
    ("section subtitles are actually rendered",
     "if(!TextUtils.isEmpty(sub))" in section and "text(sub,12,MUTED" in section),
    ("Home rail cards are visual first",
     "dealArtworkView(d,dp(170),dp(168))" in tile and
     "new LinearLayout.LayoutParams(dp(186),dp(286))" in tile and
     "materialChip(" not in tile),
    ("Catalog card exposes listing actions",
     'more.setContentDescription("Azioni annuncio")' in card and
     "showListingActions(d)" in card),
    ("manual sold action is explicit and historical",
     '"Segna annuncio come venduto"' in ui and
     '"L’annuncio verrà rimosso dal Mercato attivo. Il gioco e il prezzo osservato resteranno nello storico."' in ui),
    ("manual sold updates both legacy and canonical listing state",
     "db.markSold(sig)" in manual and
     "marketStore.markSold(listingId)" in manual and
     "listingIdForVintedItemId(d.vintedItemId)" in manual and
     "bundleDb.invalidate(d)" in manual),
]

for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("5.12.38 home/manual-sold regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} 5.12.38 home/manual-sold guards")
