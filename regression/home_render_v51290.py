from pathlib import Path
ui=(Path(__file__).resolve().parents[1]/'app/src/main/java/it/vintedaffari/app/MainActivity.java').read_text()
hero=ui[ui.index('private View heroOpportunityCard'):ui.index('private View discoverFlatArtwork')]
loader=ui[ui.index('private EngineOverviewSnapshot loadEngineOverviewSnapshot'):ui.index('private void requestEngineOverviewSnapshot')]
database=ui[ui.index('private void renderDatabase()'):ui.index('private String databaseSortLabel')]
checks={
 'hero defers hierarchy changes beyond Android layout traversal':'content.post(arrange)' in hero and '->arrange.run());arrange.run()' not in hero,
 'category filter has its own stable search-row sibling':'searchBox.addView(filter,' not in database and 'searchRow.addView(filter,' in database,
 'overview does not rebuild unused historical sections':'recentObservationDays(' not in loader and 'recentObservationSessions(' not in loader,
}
for name,ok in checks.items(): print(('PASS ' if ok else 'FAIL ')+name)
assert all(checks.values())
