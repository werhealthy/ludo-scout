from pathlib import Path
root=Path(__file__).resolve().parents[1]
java=root/'app/src/main/java/it/vintedaffari/app'
parser=(java/'VintedCardParser.java').read_text()
classifier=(java/'ListingClassifier.java').read_text()
main=(java/'MainActivity.java').read_text()
service=(java/'VintedAccessibilityService.java').read_text()
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
dealdb=(java/'DealDatabase.java').read_text()
market=(java/'MarketStore.java').read_text()
runner=(java/'QueueJobRunner.java').read_text()
gradle=(root/'app/build.gradle').read_text()
checks={
 'version': "versionName '5.11.22-automation-cleanup'" in gradle and 'versionCode 105' in gradle,
 'price-first title parser': 'extractTitle(normalized)' in parser and 'include la protezione' in parser and 'isPlausibleTitle' in parser,
 'cd filter': 'containsWord(title,"cd")' in classifier,
 'warhammer filter': 'containsWord(title,"warhammer")' in classifier,
 'non-game before components': classifier.index('containsWord(title,"warhammer")') < classifier.index('containsAny(t, COMPONENTS)'),
 'reject before storage': service.index('listingNow.type == ListingClassifier.Type.NON_GAME') < service.index('database.recordSighting(card, listingNow, now)'),
 'old review cleanup': 'autoHideStrongNonGameReviews' in market and 'v51122AutoHidden' in main,
 'bgg icon review': 'ImageView bggIcon' in main and 'bggIcon.setImageResource(R.drawable.provider_bgg_logo)' in main,
 'inline vinted paste': 'input("Incolla link Vinted")' in main and 'button("Collega link",CYAN)' in main,
 'primary vinted search': 'button("Cerca questo annuncio su Vinted",VINTED_BG)' in main and 'search.setEnabled(true)' in main,
 'no share tutorial': 'Dall\'articolo corretto: Condividi' not in main and 'Se trovi l\'articolo giusto, aprilo in Vinted e usa Condividi' not in main,
 'manual-only badge': 'vintedReviewCount()+marketStore.bggMatchReviewCount()' in main and 'badge.setText(String.valueOf(factChecks))' in main,
 'equivalent candidate auto': 'equivalentVintedCandidate' in market and 'vintedEquivalentAutoMatches' in runner and 'needsDeepMetadata=true' in runner,
 'resolved title repairs dirty capture': 'isUsableResolvedTitle(r.matchedTitle)' in market and 'updateVintedTitle' in dealdb and 'updateVintedTitle(legacy.signature,r.matchedTitle)' in runner,
 'manual search fallback': 'preferredVintedSearchTitle' in main,
 'share receiver preserved': 'android.intent.action.SEND' in manifest and 'handleSharedVintedUrl' in main and 'applySharedVintedLink' in main,
 'hunts shared BGG picker preserved': 'addHuntFlow()' in main and 'addBggPicker(box,d,"",this::huntTargetDialog,false)' in main,
}
failed=[k for k,v in checks.items() if not v]
for k,v in checks.items(): print(('PASS' if v else 'FAIL'), k)
if failed: raise SystemExit('failed: '+', '.join(failed))
print('PASS automation cleanup 5.11.22')
