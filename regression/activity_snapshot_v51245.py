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
null_guard=overview.index("if(snapshot==null){") if "if(snapshot==null){" in overview else -1
null_return=overview.index("return;",null_guard) if null_guard>=0 else -1
background_refresh=overview.index("requestEngineOverviewSnapshot();",null_return+1) if null_return>=0 else -1
checks=[
 ("Activity overview consumes an async snapshot","EngineOverviewSnapshot snapshot=engineOverviewSnapshot" in overview and "requestEngineOverviewSnapshot()" in overview),
 ("Activity overview performs no SQLite reads on main thread","db." not in overview and "marketStore." not in overview),
 ("Activity hero performs no SQLite reads on main thread","db." not in hero and "marketStore." not in hero),
 ("snapshot is loaded on UI data executor","uiDataIo.execute(()->" in ui and "loadEngineOverviewSnapshot" in ui),
 ("snapshot freshness starts after the database reads",completion_clock>last_read),
 ("stale snapshots remain visible while refreshing",null_guard>=0 and null_return>null_guard and background_refresh>null_return),
]
failed=[n for n,ok in checks if not ok]
for n,ok in checks: print(("PASS " if ok else "FAIL ")+n)
if failed: raise SystemExit("activity snapshot regression failed: "+", ".join(failed))
