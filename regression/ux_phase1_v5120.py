from pathlib import Path
root=Path(__file__).resolve().parents[1]
main=(root/'app/src/main/java/it/vintedaffari/app/MainActivity.java').read_text()
db=(root/'app/src/main/java/it/vintedaffari/app/DealDatabase.java').read_text()
gradle=(root/'app/build.gradle').read_text()
checks={
 'version': "versionCode 115" in gradle and "5.12.0-ux-engine-dashboard" in gradle,
 'motore nav': 'R.drawable.ic_nav_engine,"Motore","activity"' in main,
 'database removed from nav': 'R.drawable.ic_store,"Database","database"' not in main,
 'catalog games entry': 'materialChip("Giochi"' in main and 'navigate("database")' in main,
 'engine overview': 'private void renderEngineOverview()' in main,
 'separate review': 'private void renderEngineReview()' in main,
 'session history': 'private void renderEngineHistory()' in main,
 'session click to catalog': 'openObservationSession' in main and 'observationSignatures' in main,
 'session grouping local': 'recentObservationSessions' in db and '25L*60_000L' in db,
 'no network in session code': 'recentObservationSessions' in db and 'Http' not in db[db.find('recentObservationSessions'):db.find('recentObservationSessions')+2600],
 'activity floating hidden': 'activityButton.setVisibility(View.GONE)' in main,
 'diagnostics in settings': 'Copia diagnostica tecnica' in main,
}
for k,v in checks.items(): print(('PASS' if v else 'FAIL')+' '+k)
raise SystemExit(0 if all(checks.values()) else 1)
