from pathlib import Path
root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding='utf-8')
java='app/src/main/java/it/vintedaffari/app/'
main=read(java+'MainActivity.java')
classifier=read(java+'ListingClassifier.java')
service=read(java+'VintedAccessibilityService.java')
market=read(java+'MarketStore.java')
dealdb=read(java+'DealDatabase.java')
alert=read(java+'DealAlertNotifier.java')
bgg=read(java+'BggEnricher.java')
manifest=read('app/src/main/AndroidManifest.xml')
gradle=read('app/build.gradle')
policy=read(java+'DealPolicy.java')
checks={
 'release version': "versionName '5.11.20-recovery-alerts'" in gradle and 'versionCode 103' in gradle,
 'rating floor unchanged': 'MIN_BGG_RATING = 6.0' in policy,
 'non-game enum': 'NON_GAME' in classifier,
 'shoe rejection': '"scarpe"' in classifier and '"sneakers"' in classifier,
 'glasses rejection': '"occhiali da sole"' in classifier,
 'book rejection': '"libro"' in classifier and '"libri"' in classifier,
 'reject before sighting': service.index('listingNow.type == ListingClassifier.Type.NON_GAME') < service.index('database.recordSighting(card, listingNow, now)'),
 'non-game diagnostics': 'nonGameRejected' in service,
 'product-page rails suppressed': 'productPageDiscoverySuppressed' in service and 'if (product == null) collectCards(root, discovered)' in service,
 'share receiver': 'android.intent.action.SEND' in manifest and 'android:mimeType="text/plain"' in manifest,
 'share pending target': 'PREF_MANUAL_VINTED_SHARE' in main and 'MANUAL_VINTED_SHARE_TTL' in main,
 'share exact link flow': 'Condividi → Ludo Scout' in main and 'handleSharedVintedUrl' in main and 'applySharedVintedLink' in main,
 'no silent page inference': 'getRootInActiveWindow' not in main,
 'seller manual field removed': 'Venditore, se lo conosci' not in main and 'Salva venditore e riprova' not in main,
 'automatic retry remains': 'Riprova automatico' in main,
 'manual BGG Vinted recovery': 'Cerca questo annuncio su Vinted' in main and 'Apri annuncio Vinted' in main,
 'manual non-game delete': "Non è un gioco · elimina dall'app" in main and 'hideGameAsNonGame' in market,
 'smart deal notifier': 'class DealAlertNotifier' in alert and 'MIN_DISCOUNT_PCT=30' in alert,
 'smart deal rating rule': 'DealPolicy.ratingEligible(d.rating)' in alert,
 'smart deal recent-only': 'MAX_DISCOVERY_AGE_MS' in alert,
 'smart deal dedupe': 'REPEAT_GAP_MS' in alert and 'meaningfullyBetter' in alert,
 'smart deal wired on observation': 'DealAlertNotifier.evaluateAndNotify' in service,
 'smart deal wired after BGG': 'DealAlertNotifier.evaluateAndNotify' in bgg and 'getDealsByBggId' in dealdb,
 'hunt BGG link or id': 'Titolo, link BGG o ID' in main and 'bggIdFromInput(raw)' in main,
}
failed=[k for k,v in checks.items() if not v]
for k,v in checks.items(): print(('PASS' if v else 'FAIL'),k)
if failed: raise SystemExit('failed: '+', '.join(failed))
print('PASS recovery/share/non-game/alerts/hunt flow')
