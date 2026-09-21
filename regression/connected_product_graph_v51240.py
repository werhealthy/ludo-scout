#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ui=(ROOT/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
market=(ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
system=(ROOT/"UX_SYSTEM_V2_CONNECTED_GRAPH.md").read_text(encoding="utf-8")

detail=ui[ui.index("private void openDetail(DealRecord d)"):ui.index("private TextView detailSecondaryAction",ui.index("private void openDetail(DealRecord d)"))]
game=ui[ui.index("private void renderDatabaseDetail()"):ui.index("private void compareOnVinted",ui.index("private void renderDatabaseDetail()"))]
filters=ui[ui.index("private void showFilterSheet()"):ui.index("private void renderBundles()",ui.index("private void showFilterSheet()"))]
db=ui[ui.index("private void renderDatabase()"):ui.index("private String databaseSortLabel",ui.index("private void renderDatabase()"))]

checks=[
    ("release identity",
     "versionName '5.12.40-connected-product-graph'" in build and
     "applicationId 'it.vintedaffari.app'" in build),
    ("connected product UX contract exists",
     "listings, games and tags form a connected graph" in system and
     "Other listings of the same game" in system),
    ("listing detail separates metadata into distinct visual objects",
     'scorePill("Ludo "+scoreLabel(d),LIME)' in detail and
     "langChip(d.languageCode)" in detail and
     'publicationText(d,12,Typeface.NORMAL)' in detail and
     '" · "+languageShort(d.languageCode)+" · "+publicationDisplay(d)' not in detail),
    ("listing detail exposes canonical game navigation",
     "entityLinkCard(game)" in detail and
     "openDatabaseGame(game.id,tab)" in detail),
    ("listing detail keeps provider identity",
     'provider_vinted_logo,"Vinted"' in detail and
     'provider_bgg_logo,"BoardGameGeek"' in detail),
    ("correction affordance is immediate",
     'roundIconButton("?",CYAN)' in detail and
     "showMatchCorrection(d,dialog)" in detail),
    ("Ludo Score is inspectable and composite",
     "showLudoScoreInfo" in ui and
     "QualityComposite.score(g.rank,null,g.rating,g.voters)" in ui and
     "rank 55% · geek 20% · votanti 15% · media 10%" in ui),
    ("tags navigate into game database",
     "openGameTag(String tag)" in ui and
     'addLinkedMetaSection("Categorie"' in game and
     'addLinkedMetaSection("Meccaniche"' in game),
    ("game search includes taxonomy and creators",
     "LOWER(COALESCE(g.categories,'')) LIKE ?" in market and
     "LOWER(COALESCE(g.mechanics,'')) LIKE ?" in market and
     "LOWER(COALESCE(g.designers,'')) LIKE ?" in market and
     "LOWER(COALESCE(g.publishers,'')) LIKE ?" in market),
    ("game detail owns same-game listings",
     '"Annunci di questo gioco"' in game and
     "marketStore.listingsForGame(g.id,true,80)" in game),
    ("game detail surfaces similar games",
     "similarGames(g,8)" in game and "similarGameCard" in ui),
    ("advanced filters are full-screen tasks",
     'fullScreenPanel("Filtri annunci")' in filters and
     'fullScreenPanel("Filtri giochi")' in ui),
    ("game database shell has no quick-filter wall",
     "marketToolbarButton(databaseAdvancedFilterCount()" in db and
     'materialChip(("review".equals(databaseScope)' not in db),
]

for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("5.12.40 connected product graph regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} connected product graph guards")
