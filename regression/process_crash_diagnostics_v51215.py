#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
app = (ROOT / "app/src/main/java/it/vintedaffari/app/LudoScoutApp.java").read_text(encoding="utf-8")
journal = (ROOT / "app/src/main/java/it/vintedaffari/app/ProcessCrashJournal.java").read_text(encoding="utf-8")
diag = (ROOT / "app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")

checks = [
    ("Application bootstrap installed", 'android:name=".LudoScoutApp"' in manifest and "ProcessCrashJournal.install(this)" in app),
    ("uncaught handler covers every app process", "Thread.setDefaultUncaughtExceptionHandler" in journal and "Application.getProcessName()" in journal),
    ("journal uses one file per process", "process_crash_journal" in journal and '"crash-"+process+".txt"' in journal),
    ("crash write is bounded and synchronous", "stack.length()>7000" in journal and "fos.getFD().sync()" in journal),
    ("Android exit history queried on API 30+", "getHistoricalProcessExitReasons" in journal and "Build.VERSION.SDK_INT<30" in journal),
    ("exit summary distinguishes crash/anr/memory", "crash24h=" in journal and "anr24h=" in journal and "memory24h=" in journal),
    ("diagnostics expose process journal", "processCrashJournal={" in diag),
    ("diagnostics expose system exit history", "systemExitHistory={" in diag),
]
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
failed = [name for name, ok in checks if not ok]
if failed:
    raise SystemExit("Process crash diagnostics regression failed: " + ", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} process crash diagnostics guards")
