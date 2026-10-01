#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ui=(ROOT/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
system=(ROOT/"UX_SYSTEM_V3_INVISIBLE_INTERACTION.md").read_text(encoding="utf-8")

def production_method(signature):
    start=ui.index(signature);brace=ui.index("{",start);depth=0
    for i in range(brace,len(ui)):
        if ui[i]=="{": depth+=1
        elif ui[i]=="}":
            depth-=1
            if depth==0:return ui[start:i+1]
    raise ValueError(signature)
detail=production_method("private void openDetail(DealRecord d,boolean preserveParent)")
filters=ui[ui.index("private void showFilterSheet()"):ui.index("private void renderBundles()",ui.index("private void showFilterSheet()"))]
catalog=ui[ui.index("private View catalogRowV51"):ui.index("private int photoCount",ui.index("private View catalogRowV51"))]
overlay=ui[ui.index("private void openGameDetailOverlay"):ui.index("private View marketListingCard",ui.index("private void openGameDetailOverlay"))]
panel=ui[ui.index("private Dialog fullScreenPanel(String title)"):ui.index("private TextView filterIntro",ui.index("private Dialog fullScreenPanel(String title)"))]
indicator=ui[ui.index("private void updateActivityIndicator(View target)"):ui.index("private String compactCount",ui.index("private void updateActivityIndicator(View target)"))]
indicator_loader=ui[ui.index("private void requestActivityIndicatorSnapshot()"):ui.index("private void updateActivityIndicator(View target)",ui.index("private void requestActivityIndicatorSnapshot()"))]

checks=[
    ("release identity",
     "versionName '5.12." in build and "applicationId 'it.vintedaffari.app'" in build),
    ("invisible interaction contract exists",
     "The interface should explain itself by shape, placement and behavior" in system and
     "Never place all chips, checkboxes and inputs on the same filter screen" in system),
    ("listing detail has no instructional tag heading",
     '"Esplora per tag"' not in detail and
     "linkedDealTagStrip(d,game)" in detail),
    ("listing detail does not duplicate game identity card",
     "entityLinkCard(game)" not in detail and
     'text("Scheda gioco",14,TEXT,Typeface.BOLD)' in detail and
     "openGameDetailOverlay(game.id)" in detail),
    ("listing detail removes market history",
     '"Contesto prezzo"' not in detail and
     "localVintedReferenceStats" not in detail and
     '"Annunci di questo gioco"' not in detail),
    ("listing detail keeps distinct signals",
     'productRatingRow(d.rating,scoreLabel(d)' in detail and
     "productLanguagePanel(d.languageCode" in detail and
     "productRatingRow(d.rating" in detail and "openBgg(d.bggId)" in detail and
     "publicationText(d,12,Typeface.NORMAL)" in detail),
    ("listing can transition to game by overscroll",
     "installPullToGame(sc,pullHint,game.id,dialog)" in detail and
     '"Rilascia per aprire il gioco"' in ui),
    ("game detail opens directly without catalog routing",
     "uiDataIo.execute" in overlay and
     'tab="database"' not in overlay and
     "renderDatabaseDetailInto(host,ready,dialog::dismiss)" in overlay),
    ("UI data has dedicated executor",
     "ExecutorService uiDataIo=Executors.newSingleThreadExecutor()" in ui and
     "uiDataIo.shutdownNow()" in ui),
    ("Activity indicator never queries SQLite on the main thread",
     "marketStore.jobSummary()" not in indicator and
     "marketStore.vintedReviewCount()" not in indicator and
     "requestActivityIndicatorSnapshot()" in indicator and
     "uiDataIo.execute" in indicator_loader),
    ("filters use progressive rows",
     'filterRow("Voto BGG"' in filters and
     'filterRow("Lingua"' in filters and
     "showChoicePage" in filters and
     "choiceChips(" not in filters),
    ("full-screen filters respect insets",
     "setOnApplyWindowInsetsListener" in panel and
     "getSystemWindowInsetTop()" in panel and
     "getSystemWindowInsetBottom()" in panel),
    ("catalog metadata is not serialized into one sentence",
     "languageCompact(d.languageCode)" in catalog and
     "publicationText(d,11,Typeface.NORMAL)" in catalog and
     'languageShort(d.languageCode)+" · "+publicationDisplay(d)' not in catalog),
    ("Library action is contextual",
     'menuAction("Aggiungi alla Libreria",TEXT)' in ui and
     '"＋ Libreria"' not in detail),
]

for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("Invisible interaction regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} invisible-interaction guards")
