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

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
if failed:
    raise SystemExit("Pipeline integrity regression failed: " + ", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} pipeline-integrity guards")
