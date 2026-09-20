from pathlib import Path
root=Path(__file__).resolve().parents[1]
main=(root/'app/src/main/java/it/vintedaffari/app/MainActivity.java').read_text()
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
checks={
    'old nullable shipping ternary removed': 'ship=online?parseEuro(shipping.getText().toString()):0' not in main,
    'old nullable fee ternary removed': 'actual="Vinted".equals(source)?parseEuro(fee.getText().toString()):0' not in main,
    'catalog detail can add to library': 'Aggiungi alla Libreria' in main and 'gameFromDeal(d)' in main,
    'library text search uses queued fast path': 'LibrarySearchJob job=new LibrarySearchJob' in main and 'bggSearch.searchFast(searchQuery' in main,
    'completed import removes originating card': 'finishActiveLibrarySearchJob()' in main,
    'wizard state saved': 'libraryWizardStep' in main and 'saveUiState(state)' in main,
    'rotation keeps activity state': 'android:configChanges="orientation|screenSize|keyboardHidden"' in manifest,
}
for name,ok in checks.items():
    if not ok: raise SystemExit(f'FAIL: {name}')
print(f'PASS: {len(checks)} library wizard/state guards')
