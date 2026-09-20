from pathlib import Path
root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding='utf-8')
java='app/src/main/java/it/vintedaffari/app/'
main=read(java+'MainActivity.java')
market=read(java+'MarketStore.java')
bgg=read(java+'BggSearchClient.java')
rank=read(java+'BggManualSearchRanking.java')
gradle=read('app/build.gradle')
checks={
 'release version': "versionName '5.11.21-database-search-refactor'" in gradle and 'versionCode 104' in gradle,
 'database hides review by default': 'databaseShowReview=false' in main,
 'database review toggle': '"Da verificare"' in main and 'databaseShowReview=!databaseShowReview' in main,
 'verified-only SQL': "g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND g.match_state='MATCHED'" in market,
 'database alphabetical': 'ORDER BY g.normalized_name COLLATE NOCASE ASC,g.canonical_name COLLATE NOCASE ASC' in market,
 'database filters': all(x in main for x in ['"Con annunci"','"BGG 7+"','"BGG 8+"','"≤ 20 €"']),
 'database filter SQL': all(x in market for x in ['"active".equals(filter)','"rating7".equals(filter)','"rating8".equals(filter)','"cheap".equals(filter)']),
 'automatic database pagination': 'setOnScrollChangeListener' in main and 'loadMoreDatabase()' in main and 'databaseVisible+24' in main,
 'old 12 button removed': 'Mostra altri 12' not in main,
 'shared BGG picker': main.count('addBggPicker(') >= 4 and 'lookupBgg(' in main,
 'database change-game action': 'button("È un altro gioco"' in main and 'chooseManualBggMatch(g)' in main,
 'identity correction split': "L'annuncio è giusto · cambia gioco" in main and 'Il gioco è giusto · cambia annuncio' in main,
 'direct non-game removal': "Non è un gioco · elimina dall'app" in main and 'excludeDealAsNonGame' in main,
 'BGG link/id everywhere': 'Inserisci titolo, link o ID BGG' in main and 'bggIdFromInput(raw)' in main,
 'manual picker uses forgiving fast search': 'else bggSearch.searchFast(raw,cb)' in main,
 'offline BGG id fallback': 'Game local=localById(id)' in bgg and 'Scheda caricata dal catalogo locale' in bgg,
 'manual rank separate': 'Human-facing BGG search ranking' in rank and 'BggManualSearchRanking.score' in bgg,
 'automatic rank untouched': 'SearchRanking.score' in bgg,
 'new BGG price informational source': 'localNewMarketCents' in bgg and 'R.raw.bgg_price_index_gz' in bgg,
 'market detail current vinted': 'Min Vinted attuale' in main,
 'market detail historical vinted': 'Min Vinted storico' in main,
 'market detail used BGG': 'Riferimento usato BGG' in main or 'Rif. usato BGG' in main,
 'market detail new BGG': 'Prezzo nuovo BGG' in main or 'Nuovo BGG' in main,
 'protection removed from deep detail': 'details.addView(kv("Con protezione"' not in main,
 'publication fallback is observation': 'Osservato il ' in main and 'return "Data da aggiornare"' not in main,
 'refresh state no seller/date gate': 'private boolean vintedDataIncomplete(DealRecord d){return d==null||TextUtils.isEmpty(d.vintedUrl);}' in main,
}
failed=[k for k,v in checks.items() if not v]
for k,v in checks.items(): print(('PASS' if v else 'FAIL'),k)
if failed: raise SystemExit('failed: '+', '.join(failed))
print('PASS database/search/refactor 5.11.21')
