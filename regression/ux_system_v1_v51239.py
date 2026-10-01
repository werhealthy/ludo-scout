#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ui=(ROOT/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
system=(ROOT/"UX_SYSTEM_V1.md").read_text(encoding="utf-8")

home=ui[ui.index("private void renderDiscover()"):ui.index("private void addBundleEmptyState",ui.index("private void renderDiscover()"))]
header=ui[ui.index("private View discoverHeader"):ui.index("private String discoverGreetingName",ui.index("private View discoverHeader"))]
categories=ui[ui.index("private void addDiscoverCategories()"):ui.index("private GameRecord discoverGame",ui.index("private void addDiscoverCategories()"))]
product=ui[ui.index("private View discoverProductCard"):ui.index("private void addDiscoverTopRatedRail",ui.index("private View discoverProductCard"))]
top=ui[ui.index("private View discoverTopRatedCard"):ui.index("private View heroOpportunityCard",ui.index("private View discoverTopRatedCard"))]
opportunity=ui[ui.index("private View heroOpportunityCard"):ui.index("private View discoverFlatArtwork",ui.index("private View heroOpportunityCard"))]
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
    ("Home categories use the shared product clusters",
     "DiscoverCategories.labels()" in ui and
     "openDiscoverCluster" in categories),
    ("fresh rail leads with listing publication time",
     "publicationDisplay(d)" in product),
    ("newest rail sorts by Vinted publication time",
     "publicationAgeMinutes(a)" in home and
     "publicationAgeMinutes(b)" in home),
    ("BGG rail exposes rank, votes and rating",
     "homeScore(d,18)" in top and "d.voters" in top and "d.rank" in top and
     '" BGG"' in top),
    ("featured opportunity uses canonical BGG content",
     "FeaturedBoxView box=new FeaturedBoxView(true,false)" in opportunity and
     "card.setBackground(premiumSurface(true))" in opportunity and
     "setFeaturedArtwork(image,placeholder,d" in opportunity and
     "discoverGameDescription(d)" in opportunity and
     "name(d)" in opportunity and "total(d)" in opportunity and "discoverDiscountBadge(d" in opportunity),
    ("featured opportunity renders the BGG cover as an adaptive 2.5D box",
     "setPolyToPoly" in ui and "path(topPath,geometry.top)" in ui and "path(sidePath,geometry.side)" in ui and "sampleEdge(bitmap)" in ui),
    ("Discover greeting matches the approved Home reference",
     '"Bentornato,"' in header and
     "discoverGreetingName()" in header),
    ("section headers rely on typography instead of icon tiles",
     "TextView ic=text(icon" not in section and "text(title,21,TEXT" in section),
    ("Catalog has search with adjacent filters and result-count sorting",
     'filters.setOnClickListener(v->showFilterSheet())' in market and
     "sortControl.setOnClickListener(v->showSortMenu(sortControl))" in market and
     "addCatalogToggleChip(quick" not in market),
    ("Catalog uses the shared product grammar in a two-column grid",
     "appendCatalogCards(results,list" in market and "col<2" in market and "catalogProductCard(deals.get(index))" in market),
    ("Listing detail has one dominant Vinted provider action",
     'productPrimaryButton(hasVinted?"Apri su Vinted"' in detail and
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
