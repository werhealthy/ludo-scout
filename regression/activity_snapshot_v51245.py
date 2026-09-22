#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
ui=(root/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
overview=ui[ui.index("private void renderEngineOverview()"):ui.index("private View engineCurrentRunHero",ui.index("private void renderEngineOverview()"))]
hero=ui[ui.index("private View engineCurrentRunHero"):ui.index("\n    private ",ui.index("private View engineCurrentRunHero")+30)]
loader=ui[ui.index("private EngineOverviewSnapshot loadEngineOverviewSnapshot()"):ui.index("private void requestEngineOverviewSnapshot()",ui.index("private EngineOverviewSnapshot loadEngineOverviewSnapshot()"))]
snapshot_ctor=loader.index("new EngineOverviewSnapshot")
completion_clock=loader.rfind("System.currentTimeMillis()",0,snapshot_ctor)
last_read=max(loader.rfind("db.",0,snapshot_ctor),loader.rfind("marketStore.",0,snapshot_ctor))
checks=[
 ("Activity overview consumes an async snapshot","EngineOverviewSnapshot snapshot=engineOverviewSnapshot" in overview and "requestEngineOverviewSnapshot()" in overview),
 ("Activity overview performs no SQLite reads on main thread","db." not in overview and "marketStore." not in overview),
 ("Activity hero performs no SQLite reads on main thread","db." not in hero and "marketStore." not in hero),
 ("snapshot is loaded on UI data executor","uiDataIo.execute(()->" in ui and "loadEngineOverviewSnapshot" in ui),
 ("snapshot freshness starts after the database reads",completion_clock>last_read),
 ("stale snapshots remain visible while refreshing","if(snapshot==null){" in overview and "if(now-snapshot.loadedAt>5_000L)requestEngineOverviewSnapshot();" in overview),
]
failed=[n for n,ok in checks if not ok]
for n,ok in checks: print(("PASS " if ok else "FAIL ")+n)
if failed: raise SystemExit("activity snapshot regression failed: "+", ".join(failed))
