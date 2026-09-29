#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
queue = (ROOT / "app/src/main/java/it/vintedaffari/app/QueueKeepAliveService.java").read_text(encoding="utf-8")
build = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
notification = queue[queue.index("private Notification notification(){"):queue.index("private static String waitText", queue.index("private Notification notification(){"))]

checks = [
    ("build version advances beyond the notification clarity fix baseline",
     "versionName '5.12.75-discover-editorial-home'" not in build),
    ("queued work is described as queued, not active",
     '" attività in coda"' in notification and '"coda attiva"' not in notification),
    ("a missing activity label still says work is being processed",
     'if(processing>0)text=TextUtils.isEmpty(current)?"Elaborazione in corso"' in notification),
    ("only a real processing lease shows a progress bar",
     'if(processing>0)b.setProgress(100,' in notification and
     'else b.setProgress(0,0,false)' in notification and
     'setProgress(0,0,true)' not in notification),
    ("waiting states keep their specific pacing message",
     'if(wait>now&&(vDue>0||deferred>0))text=waitText(reason,wait-now)' in notification),
]

for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
failed = [name for name, ok in checks if not ok]
if failed:
    raise SystemExit("queue notification truth regression failed: " + ", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} queue notification truth guards")
