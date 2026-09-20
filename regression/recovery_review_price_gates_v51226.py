#!/usr/bin/env python3
import sqlite3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
deal=(ROOT/"app/src/main/java/it/vintedaffari/app/DealDatabase.java").read_text(encoding="utf-8")
market=(ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
ui=(ROOT/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
radar=(ROOT/"app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

# Product semantics: an actionable review is a real inbox choice. A historical trust hold is settled
# but remains non-publishable and must not be presented as something the user can "check".
def settled(valid_count, ready, actionable, held, analysis_pending=0):
    return analysis_pending == 0 and (valid_count == 0 or ready + actionable + held >= valid_count)

assert settled(4, 2, 0, 2)
assert not settled(4, 2, 0, 1)
assert settled(4, 2, 1, 1)

# Executable miniature of the early price guard. It uses seller ask, not shipping, and only removes
# obviously bad ordinary automatic work. Explicit Hunt/manual work is exempt.
db=sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE candidate(id INTEGER PRIMARY KEY,item INTEGER,benchmark INTEGER,source TEXT);
INSERT INTO candidate VALUES(1,6500,3000,'AUTO');
INSERT INTO candidate VALUES(2,5200,3000,'AUTO');
INSERT INTO candidate VALUES(3,6500,3000,'HUNT_PRIORITY');
INSERT INTO candidate VALUES(4,6500,3000,'MANUAL_PRIORITY');
INSERT INTO candidate VALUES(5,9000,6000,'AUTO');
""")
filtered=db.execute("""
SELECT id FROM candidate
WHERE item>=benchmark*2.0 AND item-benchmark>=2500
AND source NOT IN ('HUNT_PRIORITY','MANUAL_PRIORITY')
ORDER BY id
""").fetchall()
assert filtered==[(1,)], filtered

oncreate=ui[ui.index("@Override protected void onCreate"):ui.index("@Override protected void onNewIntent")]
scan=radar[radar.index("private void scanVisibleVintedCards"):radar.index("private void flushPendingAnalysis")]
range_block=deal[deal.index("private int[] engineRangeCounts"):deal.index("private void fillEngineCounts")]
promotion=market[market.index("public int promoteDeferredVintedBatch"):market.index("public void deferBackgroundLink")]

checks=[
    ("manual recovery is cross-process and target scoped",
     "MANUAL_VINTED_RECOVERY" in market and "beginManualVintedRecovery" in market and
     "activeManualVintedRecovery" in market and "scope=target-only" in market),
    ("manual search starts recovery before launching Vinted",
     "marketStore.beginManualVintedRecovery" in ui and
     ui.index("marketStore.beginManualVintedRecovery") < ui.index("launchVintedSearch(searchTitle)")),
    ("recovery search cards do not become ordinary observations",
     "manualRecoverySuppressedCards" in scan and "discovered.clear()" in scan and
     scan.index("discovered.clear()") < scan.index("ThumbnailStore.captureMissing")),
    ("explicit price-comparison mode still exists",
     "activeMarketScanGame()" in radar and "Ricerca prezzi Vinted esplicita" in radar),
    ("single-result recovery still requires exact URL",
     "Anche se ne vedi uno solo" in ui and "serve l’URL esatto" in ui),
    ("review count is aligned to actionable inbox",
     "String attention=" in range_block and "manual_review_required" in range_block and
     "MATCH_UNCERTAIN" not in range_block[range_block.index("String attention="):range_block.index("String trustHold=")]),
    ("historical uncertainty is a hold, not a fake question",
     "String trustHold=" in range_block and "MATCH_UNCERTAIN" in range_block and
     "heldListings" in deal and "s.completeListings+s.reviewListings+s.heldListings" in deal),
    ("Motore UI separates actions from held results",
     "Da controllare · facoltativo" in ui and "nessuna azione richiesta" in ui and
     "Non pubblicata automaticamente" in ui),
    ("extreme price filter runs before deferred Vinted promotion",
     "filterClearlyOverpricedAutomaticListings(now);" in promotion and
     "d.item_price_cents>=d.benchmark_cents*2.0" in market and
     "d.item_price_cents-d.benchmark_cents>=2500" in market),
    ("price optimization never sacrifices Hunt or manual intent",
     "p.source IN ('HUNT_PRIORITY','MANUAL_PRIORITY')" in market and
     "COALESCE(source,'AUTO') NOT IN ('HUNT_PRIORITY','MANUAL_PRIORITY')" in market),
    ("price-filtered rows keep history but leave the automatic product path",
     '"PRICE_FILTERED"' in market and '"AUTO_FILTERED"' in market and
     "verifica Vinted evitata" in market),
    ("queue reconciliation is no longer synchronous in Activity onCreate",
     "marketStore.reconcileQueue()" not in oncreate and "buildShell();startPostCreateMaintenance()" in oncreate and
     "maintenanceIo.execute" in ui[ui.index("private void startPostCreateMaintenance"):ui.index("private void applyUxFreshStartIfNeeded")]),
    ("Home remains trusted-only",
     'db.getDeals("trusted",320)' in ui and '"trusted".equals(filter)' in deal),
    ("diagnostics expose recovery, held and early price filtering",
     "manualRecovery={" in radar and "manualRecoveryState={" in radar and
     "earlyPriceFilter={" in radar and ";held=" in radar),
    ("build identity and CI versionCode strategy remain unchanged",
     "applicationId 'it.vintedaffari.app'" in build and
     "1000000 + ciVersionCode.toInteger()" in build),
]

for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("5.12.26 recovery/review/price regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} recovery/review/price guards")
