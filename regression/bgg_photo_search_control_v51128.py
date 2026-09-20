from pathlib import Path
root=Path(__file__).resolve().parents[1]
java=root/'app/src/main/java/it/vintedaffari/app'
main=(java/'MainActivity.java').read_text()
search=(java/'BggSearchClient.java').read_text()
thumb=(java/'ThumbnailStore.java').read_text()
service=(java/'VintedAccessibilityService.java').read_text()
gradle=(root/'app/build.gradle').read_text()
checks={
    'version': "versionName '5.11.28-bgg-photo-search-control'" in gradle and 'versionCode 111' in gradle,
    'capture at scroll': 'ThumbnailStore.captureMissing(this, discovered);' in service,
    'thumbnail local storage': 'DealDatabase.signature(card)' in thumb and 'takeScreenshot' in thumb,
    'bgg review uses observed photo': 'observedArt=observedListingPhotoView(observed,64,82)' in main,
    'picker shows observed photo': 'Foto originale catturata durante lo scroll' in main and 'observedListingPhotoView(observed,92,116)' in main and 'Usa la foto originale per riconoscere il gioco' in main,
    'manual match no auto search': '}),false);\n        View notGame=' in main,
    'picker never schedules autosearch': 'uiUpdates.postDelayed(run,120)' not in main,
    'search button remains usable': 'search.setEnabled(true);search.setText(id!=null?' in main,
    'typing invalidates stale result': 'Input cambiato: premi Cerca per usare il nuovo valore.' in main and 'request[0]++;' in main,
    'direct id worker': 'directExec=Executors.newSingleThreadExecutor()' in search and 'detailsDirect(String id' in search,
    'link bypasses name queue': 'if(id!=null)bggSearch.detailsDirect(id,false,callback);else bggSearch.searchFast(raw,callback);' in main,
    'direct worker shutdown': 'directExec.shutdownNow()' in search,
}
for name,ok in checks.items():
    if not ok: raise AssertionError(name)
print('PASS BGG photo/search control 5.11.28:',len(checks),'checks')
