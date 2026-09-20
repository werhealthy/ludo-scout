from pathlib import Path
r=Path(__file__).resolve().parents[1]
main=(r/'app/src/main/java/it/vintedaffari/app/MainActivity.java').read_text()
market=(r/'app/src/main/java/it/vintedaffari/app/MarketStore.java').read_text()
runner=(r/'app/src/main/java/it/vintedaffari/app/QueueJobRunner.java').read_text()
resolver=(r/'app/src/main/java/it/vintedaffari/app/VintedLinkResolver.java').read_text()
db=(r/'app/src/main/java/it/vintedaffari/app/DealDatabase.java').read_text()
checks={
 'db15':'DB_VERSION=15' in db and 'upgradeV14ToV15' in db,
 'start timestamp':'processing_started_at' in market,
 '145s bounded resolver':'latch.await(145, TimeUnit.SECONDS)' in runner,
 'real resolver milestones':'onProgress(d.signature,68,"verifico annuncio")' in resolver,
 'estimated fill capped':'Math.min(88,estimated)' in market,
 'priority after processing':"CASE j.state WHEN 'PROCESSING' THEN 0 ELSE 1 END,j.priority DESC" in market,
 'data action':'Dati  ↑' in main and 'star_big_on' not in main[main.find('// ---------- DETAIL ----------'):main.find('private void requestDealRefresh')],
 'completed collapsed':'Completate · ' in main and 'if(showActivityHistory)' in main,
 'secret direct':(r/'secrets.properties').exists(),
}
for k,v in checks.items(): print(('PASS ' if v else 'FAIL ')+k)
raise SystemExit(0 if all(checks.values()) else 1)
