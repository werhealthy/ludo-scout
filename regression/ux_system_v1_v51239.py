#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ui=(ROOT/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
system=(ROOT/"UX_SYSTEM_V1.md").read_text(encoding="utf-8")

home=ui[ui.index("private void renderDiscover()"):ui.index("private void addBundleEmptyState",ui.index("private void renderDiscover()"))]
header=ui[ui.index("private View discoverHeader"):ui.index("private View discoverActionButton",ui.index("private View discoverHeader"))]
categories=home
fresh=ui[ui.index("private View discoverFreshCard"):ui.index("private void addDiscoverTopRatedRail",ui.index("private View discoverFreshCard"))]
top=ui[ui.index("private View discoverTopRatedCard"):ui.index("private void addDiscoverValueRail",ui.index("private View discoverTopRatedCard"))]
opportunity=ui[ui.index("private View heroOpportunityCard"):ui.index("private View discoverFlatArtwork",ui.index("private View heroOpportunityCard"))]
flat=ui[ui.index("private View discoverFlatArtwork"):ui.index("private void addDiscoverBundleSpotlight",ui.index("private View discoverFlatArtwork"))]
section=ui[ui.index("private void sectionHeader"):ui.index("private void openCatalogPreset",ui.index("private void sectionHeader"))]
market=ui[ui.index("private void renderCatalog()"):ui.index("private void loadMoreCatalog",ui.index("private void renderCatalog()"))]
row=ui[ui.index("private View catalogRowV51"):ui.index("private int photoCount",ui.index("private View catalogRowV51"))]
detail=ui[ui.index("private void openDetail(DealRecord d)"):ui.index("private void addRelatedListings",ui.index("private void openDetail(DealRecord d)"))]
menu=ui[ui.index("private void showDetailActions"):ui.index("private void addRelatedListings",ui.index("private void showDetailActions"))]

checks=[
    ("release identity",
     "versionName '5.12." in build and
     "applicationId 'it.vintedaffari.app'" in build),
    ("design system contract exists",
     "quiet, premium, data-smart" in system and
     "Context / corrective actions" in system and
     "Do not reuse the same card grammar" in system),
    ("Discover uses distinct, data-backed editorial rails",
     all(x in home for x in ["addDiscoverCategories","addDiscoverFreshRail","addDiscoverTopRatedRail","addDiscoverValueRail"]) and
     "heroOpportunityCard" in home),
    ("BGG categories come from the canonical visible game catalog",
     "marketStore.popularCategories(" in categories and
     'databaseQuery=category;' in categories and
     "FROM games" in (ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")),
    ("fresh rail leads with the listing publication time",
     "publicationDisplay(d)" in fresh and "firstSeen" not in fresh),
    ("newest rail sorts by Vinted publication time",
     "publicationAgeMinutes(a)" in home and
     "publicationAgeMinutes(b)" in home),
    ("top-rated rail makes BGG rating the primary signal",
     "d.rating" in top and "d.voters" in top),
    ("faux 3D box is reserved for the featured opportunity",
     "discoverBoxArtwork" in opportunity and
     "discoverBoxArtwork" not in flat and
     "discoverFlatArtwork" in flat),
    ("Discover uses compact screen title",
     'text("Scopri",26' in header and
     "Scopri il tuo prossimo gioco da tavolo" not in header),
    ("section headers rely on typography instead of icon tiles",
     "TextView ic=text(icon" not in section and "text(title,21,TEXT" in section),
    ("Market has one search and two explicit utility controls",
     'marketToolbarButton(activeFilterCount()>0?"Filtri' in market and
     "marketToolbarButton(sortLabel()" in market and
     "addCatalogToggleChip(quick" not in market),
    ("Market result is a list row rather than a dashboard card",
     "setBackgroundColor(Color.TRANSPARENT)" in row and
     "View divider=new View(this)" in row and
     "decision=text(" not in row),
    ("Listing detail has one dominant Vinted provider action",
     'providerLinkCard(R.drawable.provider_vinted_logo,"Vinted"' in detail and
     'if(hasVinted)openVinted(d);else openVintedRecoveryForDeal(d,dialog);' in detail and
     "providerAction(" not in detail),
    ("Listing detail moves corrective actions into contextual menu",
     'more.setContentDescription("Altre azioni")' in detail and
     'menuAction("Segna annuncio come venduto",ORANGE)' in menu and
     'menuAction("Nascondi annuncio",RED)' in menu and
     'TextView soldAction=' not in detail),
    ("Listing detail contextualizes secondary information",
     '"Contesto prezzo"' not in detail and
     'linkedDealTagStrip(d,game)' in detail and
     '"Dettagli gioco e costi  ⌄"' not in detail),
]

for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("5.12.39 UX system regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} UX system guards")
