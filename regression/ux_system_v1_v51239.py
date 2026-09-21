#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ui=(ROOT/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
system=(ROOT/"UX_SYSTEM_V1.md").read_text(encoding="utf-8")

home=ui[ui.index("private void renderDiscover()"):ui.index("private void addBundleEmptyState",ui.index("private void renderDiscover()"))]
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
    ("Home uses multiple presentation grammars",
     all(x in home for x in ["addUrgentList","addDealRail","addRankedRatingList","addDiscountRail","addRecentTimeline"]) and
     "addBundleSpotlight" in home),
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
     '"Contesto prezzo"' in detail and
     '"Scheda gioco"' in ui and
     '"Dettagli gioco e costi  ⌄"' not in detail),
]

for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("5.12.39 UX system regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} UX system guards")
