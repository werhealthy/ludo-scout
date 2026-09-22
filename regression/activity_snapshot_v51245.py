#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
ui=(root/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
diagnostics=(root/"app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
overview=ui[ui.index("private void renderEngineOverview()"):ui.index("private View engineCurrentRunHero",ui.index("private void renderEngineOverview()"))]
hero=ui[ui.index("private View engineCurrentRunHero"):ui.index("\n    private ",ui.index("private View engineCurrentRunHero")+30)]
render=ui[ui.index("private void render()"):ui.index("private void cancelImageRequests",ui.index("private void render()"))]
receiver=ui[ui.index("private final BroadcastReceiver receiver"):ui.index("@Override protected void onCreate",ui.index("private final BroadcastReceiver receiver"))]
loader=ui[ui.index("private EngineOverviewSnapshot loadEngineOverviewSnapshot()"):ui.index("private void requestEngineOverviewSnapshot()",ui.index("private EngineOverviewSnapshot loadEngineOverviewSnapshot()"))]
snapshot_ctor=loader.index("new EngineOverviewSnapshot")
completion_clock=loader.rfind("System.currentTimeMillis()")
last_read=max(loader.rfind("db."),loader.rfind("marketStore."))
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
 ("Activity render skips SQLite-backed indicator while its button is hidden",'if(!"activity".equals(tab))updateActivityIndicator();' in render),
 ("Activity queue broadcasts skip the SQLite-backed indicator",'if(!"activity".equals(tab))updateActivityIndicator();' in receiver),
 ("snapshot failures use bounded backoff","engineOverviewRetryAt" in ui and "activitySnapshotRetry" in ui),
 ("snapshot state is visible in copied diagnostics",'activitySnapshot={state=' in diagnostics and 'activitySnapshotError' in diagnostics),
]
failed=[n for n,ok in checks if not ok]
for n,ok in checks: print(("PASS " if ok else "FAIL ")+n)
if failed: raise SystemExit("activity snapshot regression failed: "+", ".join(failed))
