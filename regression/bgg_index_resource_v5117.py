from pathlib import Path
import gzip, sys
root=Path(__file__).resolve().parents[1]
java=(root/'app/src/main/java/it/vintedaffari/app/BggSearchClient.java').read_text()
search=root/'app/src/main/res/raw/bgg_search_index_gz'
price=root/'app/src/main/res/raw/bgg_used_price_index_gz'
assert search.exists() and search.stat().st_size > 500_000, search
assert price.exists() and price.stat().st_size > 10_000, price
assert not (root/'app/src/main/assets/engine/bgg-search-index.tsv.gz').exists()
assert 'R.raw.bgg_search_index_gz' in java
assert 'R.raw.bgg_used_price_index_gz' in java
assert 'getAssets().open("engine/bgg-search-index.tsv.gz")' not in java
with gzip.open(search,'rt',encoding='utf-8') as f:
    row=next(f).rstrip('\n').split('\t')
    assert len(row)>=7 and row[0].isdigit() and row[1]
with gzip.open(price,'rt',encoding='utf-8') as f:
    row=next(f).rstrip('\n').split('\t')
    assert len(row)==5 and row[0].isdigit() and row[1].isdigit() and row[2].isdigit()
print('PASS bgg_index_resource_v5117')
