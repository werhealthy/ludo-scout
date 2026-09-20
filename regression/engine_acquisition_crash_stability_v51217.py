#!/usr/bin/env python3
import sqlite3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
radar=(ROOT/"app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
deal=(ROOT/"app/src/main/java/it/vintedaffari/app/DealDatabase.java").read_text(encoding="utf-8")
ui=(ROOT/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
crash=(ROOT/"app/src/main/java/it/vintedaffari/app/ProcessCrashJournal.java").read_text(encoding="utf-8")
bgg=(ROOT/"app/src/main/java/it/vintedaffari/app/BggSearchClient.java").read_text(encoding="utf-8")
runner=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueJobRunner.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

# Executable model of the persisted duplicate-sighting guard. The same signature may be rendered
# many times by Accessibility, but only one observation belongs to a short Motore acquisition.
db=sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE observations(id INTEGER PRIMARY KEY AUTOINCREMENT,signature TEXT,observed_at INTEGER);
CREATE INDEX idx_observations_sig_time ON observations(signature,observed_at);
""")
gap=10*60_000
def sight(sig,at):
    cutoff=max(0,at-gap)
    if db.execute("SELECT 1 FROM observations WHERE signature=? AND observed_at>=? LIMIT 1",(sig,cutoff)).fetchone():
        return False
    db.execute("INSERT INTO observations(signature,observed_at) VALUES(?,?)",(sig,at))
    return True

assert sight("same-card",1_000)
assert not sight("same-card",5_000)
assert not sight("same-card",300_000)
assert sight("same-card",700_001)
assert db.execute("SELECT COUNT(*) FROM observations WHERE signature='same-card'").fetchone()[0]==2

checks=[
    ("Accessibility same-card re-observation is bounded", "REANALYZE_SAME_CARD_MS = 10 * 60_000L" in radar and "RESIGHT_SAME_CARD_MS = 10 * 60_000L" in radar),
    ("SQLite persists dedupe across radar restarts", "ENGINE_DUPLICATE_SIGHTING_MS=10L*60_000L" in deal and "SELECT 1 FROM observations WHERE signature=? AND observed_at>=? LIMIT 1" in deal),
    ("Motore reports unique cards instead of raw observation events", 'run.uniqueListings+" card Vinted uniche"' in ui and 'day.uniqueListings+" card uniche"' in ui and 'run.observations+" card Vinted lette"' not in ui),
    ("uncaught journal persists header before stack formatting", crash.index("fos.write(header.getBytes") < crash.index("StringWriter sw=new StringWriter")),
    ("system exit diagnostics are epoch scoped", "systemExitSummary(Context context,long since)" in crash and "crashSince=" in crash and "since=" in crash),
    ("system exit diagnostics exclude WebView/sandbox processes", 'pn.equals(pkg)||pn.startsWith(pkg+":")' in crash),
    ("system exit diagnostics expose memory footprint", "latestPssKb=" in crash and "latestRssKb=" in crash),
    ("queue BGG search has a hard CPU budget", "QUEUE_SEARCH_BUDGET_MS=2_500L" in bgg and "queueSearchTimedOut=true" in bgg and "localFuzzyTimeouts" in bgg and "localExactTimeouts" in bgg),
    ("timed-out BGG search is never auto-trusted", "if(chosen!=null&&!searchTimedOut)" in runner and "else if(searchTimedOut)" in runner and "markBggMatchReview" in runner),
    ("matcher telemetry exposes timeouts", '";timedOut="+timedOut' in runner and "searchBudgetMs=" in bgg),
    ("build invariants preserved", "applicationId 'it.vintedaffari.app'" in build and "1000000 + ciVersionCode.toInteger()" in build),
]
for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("Engine acquisition/crash stability regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} acquisition/crash stability guards")
