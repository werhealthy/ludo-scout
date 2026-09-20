from pathlib import Path
root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding='utf-8')
java='app/src/main/java/it/vintedaffari/app/'
main=read(java+'MainActivity.java'); market=read(java+'MarketStore.java'); service=read(java+'VintedAccessibilityService.java')
classifier=read(java+'ListingClassifier.java'); alerts=read(java+'DealAlertNotifier.java'); enricher=read(java+'BggEnricher.java')
dealdb=read(java+'DealDatabase.java'); policy=read(java+'DealPolicy.java'); runner=read(java+'QueueJobRunner.java'); queue=read(java+'QueueKeepAliveService.java')
bridge=read('app/src/main/assets/engine/android-bridge.js'); engine=read('app/src/main/assets/engine/deal-engine.js'); manifest=read('app/src/main/AndroidManifest.xml')
checks={
 'rating floor 6': 'MIN_BGG_RATING = 6.0' in policy,
 'BGG review retry': 'BGG_MATCH_ALGORITHM_VERSION = 2' in market and "match_state='BGG_MATCH_REVIEW'" in market,
 'learned BGG aliases': 'learnedBggIdForTitle' in market and 'learnedBggIdForTitle' in runner,
 'BGG queue fairness': 'resolveLocalBggMatches(8)' in queue,
 'used-only deal ref': 'return marketUsedRef(game);' in bridge,
 'BGG used Q25': 'p.usedQ25EUR * 100' in bridge,
 'low-used thresholds': "reference?.kind==='used_market_low'" in engine,
 'local Vinted min': 'SELECT MIN(l.current_price_cents)' in market,
 'exclude self market ref': "COALESCE(l.legacy_signature,l.temp_fingerprint)<>?" in market,
 'language dependent IT': 'lc.contains("DEP")' in market and "LIKE 'IT%'" in market,
 'manual hiding reprices': 'setLegacyListingUserHidden' in market and 'refreshMarketReferencesAsync' in main,
 'non-game classifier': 'NON_GAME' in classifier and '"scarpe"' in classifier and '"occhiali da sole"' in classifier and '"libri"' in classifier,
 'reject non-game before storage': service.index('listingNow.type == ListingClassifier.Type.NON_GAME') < service.index('database.recordSighting(card, listingNow, now)'),
 'product rails suppressed': 'productPageDiscoverySuppressed' in service and 'if (product == null) collectCards(root, discovered)' in service,
 'Vinted share receiver': 'android.intent.action.SEND' in manifest and 'handleSharedVintedUrl' in main and 'Condividi → Ludo Scout' in main,
 'non-game delete': "Non è un gioco · elimina dall'app" in main and 'hideGameAsNonGame' in market,
 'smart alerts': 'MIN_DISCOUNT_PCT=30' in alerts and 'DealPolicy.ratingEligible(d.rating)' in alerts and 'DealAlertNotifier.evaluateAndNotify' in service and 'DealAlertNotifier.evaluateAndNotify' in enricher,
 'hunts use shared title/link/id picker': 'addHuntFlow()' in main and 'addBggPicker(box,d,"",this::huntTargetDialog,false)' in main and 'Inserisci titolo, link o ID BGG' in main,
 'legacy deals by BGG retained': 'getDealsByBggId' in dealdb,
}
failed=[k for k,v in checks.items() if not v]
for k,v in checks.items(): print(('PASS' if v else 'FAIL'),k)
if failed: raise SystemExit('failed: '+', '.join(failed))
print('PASS 5.11.19/5.11.20 feature preservation')
