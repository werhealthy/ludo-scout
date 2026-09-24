#!/usr/bin/env python3
"""Regression guards for the 5.12 pipeline-integrity recovery and runtime unblock.

These checks intentionally combine executable type-contract fixtures with source guards for
Android-bound code. Android compilation remains the integration check; this script makes the
failure modes reproducible without a device or a Vinted session.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/it/vintedaffari/app"
listing = (SRC / "ListingClassifier.java").read_text(encoding="utf-8")
gate = (SRC / "BoardGameIntakeGate.java").read_text(encoding="utf-8")
market = (SRC / "MarketStore.java").read_text(encoding="utf-8")
main = (SRC / "MainActivity.java").read_text(encoding="utf-8")
queue = (SRC / "QueueKeepAliveService.java").read_text(encoding="utf-8")
a11y = (SRC / "VintedAccessibilityService.java").read_text(encoding="utf-8")
thumbs = (SRC / "ThumbnailStore.java").read_text(encoding="utf-8")
page = (SRC / "ProductPage.java").read_text(encoding="utf-8")
parser = (SRC / "ProductPageParser.java").read_text(encoding="utf-8")
normalizer = (SRC / "BggTitleNormalizer.java").read_text(encoding="utf-8")
deal_db = (SRC / "DealDatabase.java").read_text(encoding="utf-8")


# Fixture intent: a candidate title alone is not product evidence.
fixtures = [
    ("Marrakech Music", "no_boardgame_cue", "quarantine"),
    ("Marrakech – gioco da tavolo", "boardgame_cue", "base_candidate"),
    ("Marrakech espansione", "expansion_cue", "filtered_expansion"),
    ("Unknown title", "no_boardgame_cue", "uncertain"),
]
assert len(fixtures) == 4

kick_start = main.index("private void kickActivityQueue()")
kick_end = main.index("private void refreshMarketReferencesAsync()", kick_start)
kick = main[kick_start:kick_end]
catalog_start = main.index("private void renderCatalog()")
catalog_end = main.index("private void loadMoreCatalog()", catalog_start)
catalog = main[catalog_start:catalog_end]
vinted_loop = queue[queue.index("private void vintedLoop()"):queue.index("private void bggLoop()")]
bgg_loop = queue[queue.index("private void bggLoop()"):queue.index("private static String safe", queue.index("private void bggLoop()"))]

checks = [
    ("unknown is not BASE_GAME fallback",
     'return new Result(Type.UNCERTAIN, "Manca un segnale positivo' in listing),
    ("matched BGG requires independent marketplace evidence or exact verification routing",
     "!product.hasPositiveBoardGameEvidence()" in gate and "isExactIdentityCandidate" in gate and "titolo coincide con BGG" in gate),
    ("Marrakech Music is not an exact candidate for Marrakech",
     "observed.equals(expected)" in gate and "isExactIdentityCandidate(title,candidate)" in gate),
    ("exact candidate remains non-published pending category verification",
     "attendo verifica strutturata della categoria Vinted" in gate and "UNCERTAIN listings ineligible" in gate),
    ("Marrakech Music cannot publish from title alone",
     "Nessuna prova positiva di prodotto gioco da tavolo" in gate),
    ("explicit board-game cue remains admissible",
     'return new Result(Type.BASE_GAME, "Segnale esplicito di gioco da tavolo' in listing),
    ("known board-game publisher is positive marketplace evidence",
     "BOARD_GAME_BRANDS" in listing and '"kosmos"' in listing and "Publisher/brand ludico riconoscibile" in listing),
    ("observed Carcassonne publisher is recognized generically",
     '"999 games"' in listing and '"z man games"' in listing and '"just games"' in listing),
    ("new local cards are not blocked by older remote-run ownership",
     "activeObservationSession()" not in market[market.index("public List<VintedCard> pendingAnalysisCards"):market.index("/** Imports current legacy feed rows")] and
     "Math.min(8,limit)" in market and
     "pendingAnalysisCards(8)" in a11y),
    ("fresh remote runs get bounded 90s fairness slice",
     "ENGINE_RUN_FAIRNESS_SLICE_MS=90_000L" in deal_db and
     "nextUnfinishedObservationSessionAfter" in market),
    ("catalog pipeline exposes precise link and BGG stage counts",
     "catalogPipelineFunnel()" in market and "exactVintedLink" in market and
     "catalogPipeline={" in a11y),
    ("daily Activity summary avoids per-session N+1 joins",
     "d.sessions=countObservationBursts(start,end)" in deal_db and
     "List<ObservationSession> sessions=observationSessionsBetween(start,end,100)" not in deal_db),
    ("expansion cue is retained but excluded from automatic catalog/review",
     'return new Result(Type.EXPANSION, "Espansione esplicitamente indicata: esclusa dal catalogo automatico.", false, false)' in listing and
     "return type == Type.BASE_GAME;" in listing),
    ("cross-process SQLite uses bounded busy timeout",
     'PRAGMA busy_timeout=8000' in deal_db),
    ("normalized BGG variants retain semantic expansion terms",
     "espansione" in normalizer.lower() and "expansion" in normalizer.lower()),
    ("type validation precedes BGG publication",
     "BggProductCompatibility.validate(listingType.name(),m.itemType)" in market and "TYPE_MISMATCH" in market),
    ("product-page category has provenance",
     "category_raw" in market and "category_source" in market and "captureCategory" in parser and "categoryConfidence" in page),
    ("category incompatibility beats title match",
     "CATEGORY_INCOMPATIBLE" in market and "isExplicitNonGameCategory" in listing),
    ("catalog count is derived from rendered base query",
     "final int catalogEligible=list.size();" in catalog and 'catalogEligible+" annunci pronti' in catalog),
    ("catalog empty state no longer reports all stored rows",
     'db.countDeals(null)+" annunci salvati' not in catalog),
    ("activity wake has no UI-thread reconciliation",
     "maintenanceIo.execute" in kick and "marketStore.reconcileQueue();" in kick),
    ("queue lanes do not reconcile concurrently",
     "market.reconcileQueue();" not in vinted_loop and "market.reconcileQueue();" not in bgg_loop),
    ("feed scroll performs no full-frame screenshot capture",
     "ThumbnailStore.captureMissing(this, thumbnailCandidates)" not in a11y and "framebuffer" in a11y and "private static final ExecutorService CAPTURES" in thumbs),
]

# Deterministic local stress model: a 200-card feed does no full-frame capture at all.
observed = list(range(200))
checks.append(("200-card stress harness allocates no feed screenshots", len(observed) == 200 and "ThumbnailStore.captureMissing(this, thumbnailCandidates)" not in a11y))

# A successful BGG title match is not enough for an uncertain marketplace product.
# When a user opens that exact listing, an explicit board-game category must unlock a
# reversible revalidation path; one newer uncertain listing must not poison a known base game.
recovery_start = market.index("private void restoreCategoryConfirmedListings")
recovery_end = market.index("public void applyBggMetadata", recovery_start)
recovery = market[recovery_start:recovery_end]
category_update_start = market.index("public boolean updateVintedCategoryEvidence")
category_update_end = market.index("/** One-time UX cut-over", category_update_start)
category_update = market[category_update_start:category_update_end]

checks.extend([
    ("category recovery behavior is covered by executable Android unit tests",
     "CategoryRecoveryPolicyTest" in (ROOT / "app/src/test/java/it/vintedaffari/app/CategoryRecoveryPolicyTest.java").read_text(encoding="utf-8")),
    ("recovery policy waits for a real rating and enforces the six-point gate",
     "if (bggRating == null) return \"PENDING_ANALYSIS\";" in (SRC / "CategoryRecoveryPolicy.java").read_text(encoding="utf-8") and
     "bggRating >= DealPolicy.MIN_BGG_RATING" in (SRC / "CategoryRecoveryPolicy.java").read_text(encoding="utf-8")),
    ("category recovery queries only active listings for the matched game",
     "WHERE game_id=? AND lifecycle='ACTIVE'" in recovery and
     "if(ListingClassifier.isExplicitBoardGameCategory(c.getString(4)))" in recovery),
    ("listing recovery update is scoped to that active listing id",
     'db.update("market_listings",listing,"id=? AND lifecycle=\'ACTIVE\'' in recovery),
    ("deal hold is cleared only for the same listing signature and BGG identity",
     'db.update("deals",deal,"signature=? AND bgg_id=? AND lifecycle=\'ACTIVE\' AND verification_state=\'TYPE_UNVERIFIED\'' in recovery),
    ("positive category evidence reopens only its exact legacy listing",
     "UPDATE_DEAL_LISTING_TYPE_BASE_GAME" in category_update and
     '"id=? AND lifecycle=\'ACTIVE\'"' in category_update and "enqueueGameJob(db" in category_update),
    ("BGG compatibility prefers independent base-game evidence over a newer uncertain listing",
     "ORDER BY CASE listing_type WHEN 'BASE_GAME' THEN 0" in market),
    ("recovery is additive and leaves observations and history intact",
     "DELETE FROM observations" not in category_update),
])

# Activity detail screens must consume IO snapshots; render callbacks may never wait
# for synchronized SQLite reads while Android is dispatching input.
def method_body(source, signature):
    start = source.index(signature)
    end = source.find("\n    private ", start + len(signature))
    return source[start:end if end >= 0 else len(source)]

engine_history_render = method_body(main, "private void renderEngineHistory()")
engine_day_render = method_body(main, "private void renderEngineDay()")
engine_run_render = method_body(main, "private void renderEngineRun()")
engine_history_load = method_body(main, "private void requestEngineHistorySnapshot()")
engine_day_load = method_body(main, "private void requestEngineDaySnapshot(")
engine_run_load = method_body(main, "private void requestEngineRunSnapshot(")
engine_thumbnail = method_body(main, "private View engineRunThumbnailView(")
checks.extend([
    ("Activity history render never performs a synchronous 30-day SQLite scan",
     "db.recentObservationDays(30)" not in engine_history_render and
     "requestEngineHistorySnapshot()" in engine_history_render),
    ("Activity day render never loads sessions or statuses from SQLite on the UI thread",
     all(token not in engine_day_render for token in
         ("db.observationSessionsBetween(", "db.activeObservationSession()",
          "db.isObservationSessionWaiting(", "db.isObservationSessionDeferred(")) and
     "requestEngineDaySnapshot(" in engine_day_render),
    ("Activity run detail renders from an asynchronous snapshot",
     "db.engineRunItems(" not in engine_run_render and
     "engineSelectedRun()" not in engine_run_render and
     "private DealDatabase.ObservationSession engineSelectedRun()" not in main and
     "requestEngineRunSnapshot(" in engine_run_render),
    ("Activity history query runs inside the background executor",
     "uiDataIo.execute" in engine_history_load and "db.recentObservationDays(30)" in engine_history_load),
    ("Activity day queries run inside the background executor",
     "uiDataIo.execute" in engine_day_load and "db.observationSessionsBetween(start,end,50)" in engine_day_load and
     "db.isObservationSessionWaiting(session)" in engine_day_load and "db.isObservationSessionDeferred(session)" in engine_day_load),
    ("Activity run query runs inside the background executor",
     "uiDataIo.execute" in engine_run_load and "db.engineRunItems(run.startAt,run.endAt,filter,220)" in engine_run_load),
    ("Activity run thumbnail rendering does not decode local bitmaps synchronously",
     "decodeLocalBitmap(" not in engine_thumbnail and "loadEngineRunThumbnail(" in engine_thumbnail),
    ("Activity snapshot IO failures schedule a delayed retry",
     "engineHistoryRetryAt-System.currentTimeMillis()" in engine_history_load and
     "engineDayRetryAt-System.currentTimeMillis()" in engine_day_load and
     "engineRunRetryAt-System.currentTimeMillis()" in engine_run_load),
])

# Copying diagnostics is a database workload, so the click handler must never invoke it
# synchronously while the main thread is dispatching input.
settings_start = main.index("private void settings()")
settings_end = main.find("\n    private ", settings_start + len("private void settings()"))
settings_body = main[settings_start:settings_end if settings_end >= 0 else len(main)]
diagnostic_copy = method_body(main, "private void copyDiagnosticsAsync(") if "private void copyDiagnosticsAsync(" in main else ""
checks.extend([
    ("settings diagnostics are generated away from the UI thread",
     "copyDiagnosticsAsync(" in settings_body and
     "VintedAccessibilityService.diagnostics(this)" not in settings_body and
     "diagnosticIo.execute" in diagnostic_copy and
     "VintedAccessibilityService.diagnostics(getApplicationContext())" in diagnostic_copy and
     "runOnUiThread" in diagnostic_copy),
])

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
if failed:
    raise SystemExit("Pipeline integrity regression failed: " + ", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} pipeline-integrity guards")

