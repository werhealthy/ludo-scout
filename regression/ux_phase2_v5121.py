from pathlib import Path
root=Path(__file__).resolve().parents[1]
app=root/'app/src/main/java/it/vintedaffari/app'
main=(app/'MainActivity.java').read_text()
db=(app/'DealDatabase.java').read_text()
store=(app/'MarketStore.java').read_text()
svc=(app/'QueueKeepAliveService.java').read_text()
gradle=(root/'app/build.gradle').read_text()
checks={
 'version': "versionCode 116" in gradle and "5.12.1-ux-engine-run" in gradle,
 'single hero': 'engineCurrentRunHero' in main and 'Cosa sta succedendo' in main,
 'human hero metrics': 'giochi validi' in main and 'card Vinted osservate' in main,
 'ready CTA': 'Apri le '+'' in main and 'card pronte' in main,
 'daily history': 'recentObservationDays' in db and 'L\'altro ieri' in main,
 'sessions hidden in day detail': 'renderEngineDay()' in main and 'Tutti gli scroll di questa giornata' in main,
 'short session gap': 'final long gap=3L*60_000L' in db,
 'fresh start once': 'v5121FreshStartApplied' in main and 'freshStartLegacyBacklog' in store,
 'fresh start archives incomplete': 'RESET_LEGACY' in store and 'published_label IS NOT NULL' in store,
 'fresh start preserves complete': "enrichment_state='COMPLETE'" in store and "g.match_state='MATCHED'" in store,
 'old jobs deleted': 'db.delete("processing_jobs",null,null)' in store,
 'archive label': 'materialChip("Archivio"' in main and 'text("Archivio",30' in main,
 'no missing check drawable': 'R.drawable.ic_check' not in main,
 'completion notification': 'ENGINE_CHANNEL' in svc and 'puoi fare un nuovo scroll' in svc and 'open_engine' in svc,
 'review not green-complete': 'ULTIMO SCROLL · DA VERIFICARE' in main,
 'engine notification opens engine': 'getBooleanExtra("open_engine"' in main,
 'no network added to history': 'HttpURLConnection' not in db and 'java.net.URL' not in db,
}
failed=[k for k,v in checks.items() if not v]
for k,v in checks.items(): print(('PASS' if v else 'FAIL'),k)
raise SystemExit(1 if failed else 0)
