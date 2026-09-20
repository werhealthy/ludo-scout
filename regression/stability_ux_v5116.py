from pathlib import Path
import gzip

root=Path(__file__).resolve().parents[1]
main=(root/'app/src/main/java/it/vintedaffari/app/MainActivity.java').read_text()
bgg=(root/'app/src/main/java/it/vintedaffari/app/BggSearchClient.java').read_text()
fgs=(root/'app/src/main/java/it/vintedaffari/app/QueueKeepAliveService.java').read_text()
runner=(root/'app/src/main/java/it/vintedaffari/app/QueueJobRunner.java').read_text()
market=(root/'app/src/main/java/it/vintedaffari/app/MarketStore.java').read_text()
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()

assert 'BGG ≥' not in main and 'ordine alfabetico' not in main
assert 'queueLaneChip("Vinted"' in main and 'queueLaneChip("Database"' in main
assert 'historicalJobs(0,databaseJobsVisible)' in main and 'databaseJobsVisible=10' in main
assert 'requestNotificationPermission();Toast.makeText(this,"Controllo avviato"' in main
assert 'catalog-data.js' not in bgg.replace('// 18 MB catalog-data.js into a retained JSONArray in every Activity process, duplicating the\n // hidden JS engine\'s catalog and consuming a large fraction of the Android heap.','')
assert 'localCatalog' not in bgg
assert 'size()>24' in bgg and 'shutdownNow()' in bgg
assert 'QueueJobRunner.processOneVinted' in fgs and 'QueueJobRunner.processOneBgg' in fgs
assert '.setProgress(' in fgs
assert 'android:stopWithTask="false"' in manifest
assert 'enqueueIncompleteListingsBackground(120)' in runner
assert 'source<>?' in market and 'HISTORICAL_SOURCE' in market
assert 'decodeLocalBitmap(tmp,520,720)' in main
assert 'ByteArrayOutputStream' not in main

search=root/'app/src/main/res/raw/bgg_search_index_gz'
price=root/'app/src/main/res/raw/bgg_price_index_gz'
assert search.exists() and price.exists()
with gzip.open(search,'rt',encoding='utf-8') as f:
    assert sum(1 for _ in f)==31181
with gzip.open(price,'rt',encoding='utf-8') as f:
    assert sum(1 for _ in f)>=7000
print('PASS: v5.11.6 stability/UX guards')
