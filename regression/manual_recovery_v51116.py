from pathlib import Path
root=Path(__file__).resolve().parents[1]
java=root/'app/src/main/java/it/vintedaffari/app'
main=(java/'MainActivity.java').read_text()
market=(java/'MarketStore.java').read_text()
dealdb=(java/'DealDatabase.java').read_text()
resolver=(java/'VintedLinkResolver.java').read_text()
runner=(java/'QueueJobRunner.java').read_text()
service=(java/'QueueKeepAliveService.java').read_text()
bgg=(java/'BggSearchClient.java').read_text()
gradle=(root/'app/build.gradle').read_text()
checks={
'version': "versionName '5.11.16-manual-recovery'" in gradle,
'candidate callback': 'onCandidates(String signature,List<CandidateOption> candidates)' in resolver,
'candidate durability': 'saveVintedCandidates(long listingId' in market and 'vinted_candidates:' in market,
'manual URL fallback': 'Collega questo annuncio' in main and 'applyManualVintedLink' in market,
'manual seller hint': 'Salva venditore e riprova' in main and 'setSellerHint' in market,
'candidate chooser': 'È questo' in main and 'Candidati trovati' in main,
'seller affects ranking': 'd.sellerName' in resolver and 's+=24' in resolver,
'providers use logos': 'R.drawable.provider_vinted_logo' in main and 'R.drawable.provider_bgg_logo' in main,
'compact lane panels': 'addQueueControlPanels();' in main and 'queueControlPanel(boolean vinted)' in main,
'bgg identity matcher': 'matchBggIdentities' in runner and 'provisionalGamesForMatching' in market,
'bgg local exact index': 'localExactCandidates' in bgg and 'localExactIndex' in bgg,
'bgg lane invokes matcher': 'resolveLocalBggMatches(20)' in service,
'bgg active cards': 'activeBggJobs(6)' in main,
'bgg review cards': 'bggMatchReviewGames(4)' in main and 'Verifica match' in main,
'no schema bump': 'DB_VERSION=17' in dealdb.replace(' ',''),
'secrets real name': (root/'secrets.properties').exists() and not (root/'secrets.properties.example').exists(),
}
failed=[k for k,v in checks.items() if not v]
if failed:
    print('FAIL:', ', '.join(failed)); raise SystemExit(1)
print('PASS: manual Vinted recovery, candidate review, seller hints, provider-logo lane controls, and local BGG identity matching are wired.')
