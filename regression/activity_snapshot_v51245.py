#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
ui=(root/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
overview=ui[ui.index("private void renderEngineOverview()"):ui.index("private View engineCurrentRunHero",ui.index("private void renderEngineOverview()"))]
hero=ui[ui.index("private View engineCurrentRunHero"):ui.index("\n    private ",ui.index("private View engineCurrentRunHero")+30)]
checks=[
 ("Activity overview consumes an async snapshot","EngineOverviewSnapshot snapshot=engineOverviewSnapshot" in overview and "requestEngineOverviewSnapshot()" in overview),
 ("Activity overview performs no SQLite reads on main thread","db." not in overview and "marketStore." not in overview),
 ("Activity hero performs no SQLite reads on main thread","db." not in hero and "marketStore." not in hero),
 ("snapshot is loaded on UI data executor","uiDataIo.execute(()->" in ui and "loadEngineOverviewSnapshot" in ui),
]
failed=[n for n,ok in checks if not ok]
for n,ok in checks: print(("PASS " if ok else "FAIL ")+n)
if failed: raise SystemExit("activity snapshot regression failed: "+", ".join(failed))
