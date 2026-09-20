#!/usr/bin/env python3
import sqlite3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
radar=(ROOT/"app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
engine=(ROOT/"app/src/main/java/it/vintedaffari/app/JsGameEngine.java").read_text(encoding="utf-8")
market=(ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

# Model the exact failure mode from Pixel validation: :ui cached engineReady=false while :radar
# later became READY. SQLite is shared between processes and must win over process-local prefs.
db=sqlite3.connect(":memory:")
db.execute("CREATE TABLE queue_controls(name TEXT PRIMARY KEY,value INTEGER,updated_at INTEGER,text_value TEXT)")
db.execute("INSERT INTO queue_controls VALUES('diag:engine_runtime',31181,2000,'build=engine-runtime-v1;state=READY;games=31181')")
prefs_engine_ready=False
row=db.execute("SELECT value,updated_at,text_value FROM queue_controls WHERE name='diag:engine_runtime'").fetchone()
authoritative_ready=(row[0]>0) if row else prefs_engine_ready
assert authoritative_ready is True
assert row[0]==31181

checks=[
    ("SQLite diagnostics channel exists for cross-process telemetry",
     'setDiagnosticState(String name,long value,String text)' in market and 'diagnosticState(String name)' in market),
    ("radar publishes engine bootstrap state to SQLite",
     'setDiagnosticState("engine_runtime",0' in radar and 'onState(String state,String detail)' in radar),
    ("ready state publishes authoritative game count",
     'setDiagnosticState("engine_runtime",Math.max(1,gameCount)' in radar),
    ("engine error publishes authoritative failure",
     'setDiagnosticState("engine_runtime",-1' in radar),
    ("radar lifecycle is cross-process authoritative",
     'setDiagnosticState("radar_service",1' in radar and 'setDiagnosticState("radar_service",0' in radar),
    ("diagnostics prefer SQLite engine state over SharedPreferences cache",
     'engineReadyAuthoritative=engineRuntime.updatedAt>0?engineRuntime.value>0' in radar and
     'engineGamesAuthoritative=engineRuntime.value>0' in radar and
     'engineRuntime={authoritative=' in radar),
    ("bootstrap snapshot exposes document bridge and catalog state",
     'bridge:!!globalThis.VintedAffariAndroidBridge' in engine and
     'catalog:!!globalThis.VintedLocalCatalog' in engine and
     'doc:document.readyState' in engine),
    ("bootstrap errors expose console diagnostics",
     'WebChromeClient' in engine and 'CONSOLE_ERROR' in engine and 'lastConsoleError' in engine),
    ("bootstrap retries remain bounded and diagnostic writes are sampled",
     'READY_TIMEOUT_MS = 30_000L' in engine and 'READY_RETRY_MS = 500L' in engine and
     'traceAttempt(int attempt)' in engine and 'attempt%10==0' in engine),
    ("build invariants preserved",
     "applicationId 'it.vintedaffari.app'" in build and "1000000 + ciVersionCode.toInteger()" in build),
]
for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("Engine cross-process telemetry regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} engine cross-process telemetry guards")
