from pathlib import Path
import gzip,re
root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding='utf-8')
market=read('app/src/main/java/it/vintedaffari/app/MarketStore.java')
runner=read('app/src/main/java/it/vintedaffari/app/QueueJobRunner.java')
queue=read('app/src/main/java/it/vintedaffari/app/QueueKeepAliveService.java')
client=read('app/src/main/java/it/vintedaffari/app/BggSearchClient.java')
main=read('app/src/main/java/it/vintedaffari/app/MainActivity.java')
bridge=read('app/src/main/assets/engine/android-bridge.js')
deal_engine=read('app/src/main/assets/engine/deal-engine.js')
db=read('app/src/main/java/it/vintedaffari/app/DealDatabase.java')
policy=read('app/src/main/java/it/vintedaffari/app/DealPolicy.java')
gradle=read('app/build.gradle')
checks={
 'release version': "versionName '5.11.19-bgg-market-reference'" in gradle,
 'db18': 'DB_VERSION=18' in db.replace(' ',''),
 'matcher version': 'BGG_MATCH_ALGORITHM_VERSION = 2' in market,
 'old reviews retry once': "match_state='BGG_MATCH_REVIEW'" in market and 'match_algorithm_version' in market,
 'learned local aliases': 'learnedBggIdForTitle' in market and 'learnedBggIdForTitle' in runner,
 'title cleanup': 'BggTitleNormalizer.variants' in runner,
 'queue matching fairness': 'resolveLocalBggMatches(8)' in queue and queue.index('resolveLocalBggMatches(8)') < queue.index('processBggBatch(this,market,bgg,20)'),
 'rating floor preserved': 'MIN_BGG_RATING = 6.0' in policy,
 'used only JS reference': re.search(r'function currentRef\([^)]*\)\s*\{[^}]*return marketUsedRef\(game\);',bridge,re.S) is not None,
 'BGG Q25': 'p.usedQ25EUR * 100' in bridge,
 'low used kind keeps used thresholds': "reference?.kind==='used_market_low'" in deal_engine,
 'local vinted min': 'SELECT MIN(l.current_price_cents)' in market,
 'exclude self': "COALESCE(l.legacy_signature,l.temp_fingerprint)<>?" in market,
 'dependent language IT': "lc.contains(\"DEP\")" in market and "LIKE 'IT%'" in market,
 'user hidden excluded': 'setLegacyListingUserHidden' in market and 'setLegacyListingUserHidden' in main,
 'reprice after corrections': 'refreshMarketReferencesAsync' in main and 'refreshLegacyDealBenchmarks' in market,
 'new BGG used resource': 'R.raw.bgg_used_price_index_gz' in client,
}
for k,v in checks.items(): print(('PASS' if v else 'FAIL'),k)
if not all(checks.values()): raise SystemExit(1)
price=root/'app/src/main/res/raw/bgg_used_price_index_gz'
assert price.exists() and price.stat().st_size>10000
with gzip.open(price,'rt',encoding='utf-8') as f:
    rows=[next(f).rstrip('\n').split('\t') for _ in range(10)]
assert all(len(r)==5 and r[0].isdigit() and r[1].isdigit() and r[2].isdigit() for r in rows)
print('PASS used price resource')
