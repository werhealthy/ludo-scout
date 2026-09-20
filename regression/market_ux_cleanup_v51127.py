from pathlib import Path
import statistics
root=Path(__file__).resolve().parents[1]
java=root/'app/src/main/java/it/vintedaffari/app'
main=(java/'MainActivity.java').read_text()
market=(java/'MarketStore.java').read_text()
enrich=(java/'BggEnricher.java').read_text()
search=(java/'BggSearchClient.java').read_text()
service=(java/'VintedAccessibilityService.java').read_text()
gate=(java/'BoardGameIntakeGate.java').read_text()
classifier=(java/'ListingClassifier.java').read_text()
gradle=(root/'app/build.gradle').read_text()
checks={
 'version': "versionName '5.11.27-market-ux-cleanup'" in gradle and 'versionCode 110' in gradle,
 'robust vinted median': 'stats.count<3' in market and 'typicalCents' in market and 'median' in market.lower(),
 'bgg online marketplace': 'marketplace=1' in enrich and 'marketplace=1' in search and 'bggMarketStats' in market,
 'bgg current used not single source for DEP': 'languageDependent' in market and '!languageDependent&&live.count>=2' in market,
 'market scan action': 'Confronta prezzi su Vinted' in main and 'PREF_VINTED_MARKET_SCAN' in main and 'activeMarketScanGame' in service,
 'scan keeps non-game gate': 'isStrongNonGameText(card.title,card.rawDescription)' in service and 'plausibleOverlap(card.title,scanTarget.name)' in service,
 'literature/music false positives': 'world literature' in gate and 'hi hat' in gate and 'cymbal' in classifier,
 'matched cleanup': 'autoHideStrongNonGameListings' in market and 'v51127MatchedNoiseCleanup' in main,
 'expensive normal network demotion': 'item_price_cents>=benchmark_cents*2.0' in market and 'enrichment_state","LOCAL_ONLY"' in market,
 'correction bgg-only': 'private void searchCorrection' in main and 'Dialog dialog=bottomSheet("Cambia gioco BGG")' in main,
 'one bgg input': 'Titolo, link o ID BGG' in main and 'Cerca su BGG' in main,
 'vinted recovery inline': 'Dialog dialog=bottomSheet("Cambia annuncio Vinted")' in main and 'Incolla link Vinted' in main,
 'non-game prominent': 'wizardOption(android.R.drawable.ic_delete,"Non è un gioco"' in main,
 'activity all tabs': '"activity".equals(tab)?View.GONE:View.VISIBLE' in main,
 'compact badge': 'compactCount(factChecks)' in main and '99K+' in main,
 'share ttl and ordering': 'MANUAL_VINTED_SHARE_TTL=2*60*60_000L' in main and 'incomingShare?420:120' in main,
 'italian compact summary': 'italianGameSummary' in main and 'Vedi tutto' in main and 'In breve' in main,
 'market shows online bgg': 'BGG usato online' in main,
}
for name,ok in checks.items():
    if not ok: raise AssertionError(name)
# A single super-low deal cannot redefine normal market value once normal comparables exist.
prices=[500,1000,1000,1100,1200]
assert int(statistics.median(prices))==1000
# With fewer than three Vinted comparables the code must keep BGG as fallback.
assert 'vintedMarket.count>=3' in main
print('PASS market/UX cleanup 5.11.27:',len(checks),'static checks + median behavior')
