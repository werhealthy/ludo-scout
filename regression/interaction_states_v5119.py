from pathlib import Path
root=Path(__file__).resolve().parents[1]
main=(root/'app/src/main/java/it/vintedaffari/app/MainActivity.java').read_text()
store=(root/'app/src/main/java/it/vintedaffari/app/MarketStore.java').read_text()
runner=(root/'app/src/main/java/it/vintedaffari/app/QueueJobRunner.java').read_text()
bgg=(root/'app/src/main/java/it/vintedaffari/app/BggEnricher.java').read_text()
checks={
 'idempotent global': 'unqueuedIncompleteCount()' in main and 'unqueuedIncompleteCount()' in store,
 'priority state': 'promoteLegacyListing' in store and 'Priorità aumentata' in main,
 'single detail action': main.count('Completa dati mancanti')==1,
 'visible card fill': 'Color.argb(118,0,0,0)' in main,
 'vinted real stages': 'resolver returned a response' in runner and 'setJobProgress(job, 62)' in runner,
 'bgg real stages': 'IntConsumer progress' in bgg and 'progress.accept(64)' in bgg,
 'secret exact': (root/'secrets.properties').exists() and not (root/'secrets.properties.example').exists(),
}
for name, ok in checks.items():
    print(('PASS' if ok else 'FAIL'), name)
if not all(checks.values()): raise SystemExit(1)
