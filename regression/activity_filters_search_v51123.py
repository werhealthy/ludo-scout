from pathlib import Path
import gzip
root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding='utf-8')
main=read('app/src/main/java/it/vintedaffari/app/MainActivity.java')
market=read('app/src/main/java/it/vintedaffari/app/MarketStore.java')
bgg=read('app/src/main/java/it/vintedaffari/app/BggSearchClient.java')
gradle=read('app/build.gradle')
checks={
 'version': "versionName '5.11.23-activity-filters-search'" in gradle and 'versionCode 106' in gradle,
 'no observed-date fallback': 'Osservato il ' not in main and 'dateShort(l.lastSeen)' not in main and 'Data pubblicazione n/d' in main,
 'activity transparent counts': 'Annunci":"Giochi' in main and 'da analizzare' in main and 'manuali ' in main,
 'direct Vinted trash': 'confirmDeleteReviewListing(job)' in main and 'Elimina annuncio' in main,
 'direct BGG trash': 'Non è un gioco: elimina' in main and 'excludeGameAsNonGame(game,null)' in main,
 'database sort separate': 'showDatabaseSortMenu()' in main and 'Filtri avanzati' in main,
 'database combinable filters': all(x in main for x in ['databaseActiveOnly','databaseMinRating','databaseMaxPrice','databaseScope']),
 'database advanced SQL': all(x in market for x in ['searchGamesAdvanced','g.rating>=?','current_price_cents<=?']),
 'catalog sort separate': 'showSortMenu(sortControl)' in main and 'Filtri catalogo' in main,
 'catalog rating filter': 'catalogMinRatingFilter' in main and 'Voto BGG minimo' in main,
 'filter state persisted': all(x in main for x in ['putDouble("catalogMinRatingFilter"','putString("databaseScope"','putString("databaseSort"','putString("databaseQuery"']),
 'unified manual BGG search': 'public void search(String query,Callback cb){searchFast(query,cb);}' in bgg,
 'BGG alternate names preserved': 'public final List<String> aliases' in bgg and '"alternate".equals(nt)' in bgg and 'manualSearchScore' in bgg,
 'BGG expansions searched online': 'search?type=boardgame,boardgameexpansion&query=' in bgg,
}
idx=root/'app/src/main/res/raw/bgg_search_index_gz'
rows=aliases=0
localized=False
with gzip.open(idx,'rt',encoding='utf-8') as f:
    for line in f:
        c=line.rstrip('\n').split('\t')
        rows+=1
        if len(c)>=8 and c[7]:
            vals=c[7].split('\x1f'); aliases+=len(vals)
            if c[0]=='4390' and 'Carcassonne: Cacciatori e Raccoglitori' in vals: localized=True
checks['expanded local BGG aliases']=rows>30000 and aliases>20000
checks['localized alias sample']=localized
failed=[k for k,v in checks.items() if not v]
for k,v in checks.items(): print(('PASS' if v else 'FAIL'),k)
print('BGG index:',rows,'rows,',aliases,'aliases')
if failed: raise SystemExit('failed: '+', '.join(failed))
print('PASS activity/filters/search 5.11.23')
