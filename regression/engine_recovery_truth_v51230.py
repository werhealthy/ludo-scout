#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
market=(ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
runner=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueJobRunner.java").read_text(encoding="utf-8")
deal=(ROOT/"app/src/main/java/it/vintedaffari/app/DealDatabase.java").read_text(encoding="utf-8")
ui=(ROOT/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
radar=(ROOT/"app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
explore=(ROOT/"app/src/main/java/it/vintedaffari/app/BundleExploration.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

transient_start=runner.index("if (isTransientVintedWait(reason))")
transient_end=runner.index("} else if (isDeterministicMiss(reason))",transient_start)
transient_branch=runner[transient_start:transient_end]

checks=[
    ("release identity",
     "5.12.30-engine-recovery-truth" in build),
    ("human recovery is a separate durable source",
     'MANUAL_RECOVERY_SOURCE = "MANUAL_RECOVERY"' in market and
     "enqueueListingJob(db,canonicalId,JOB_VINTED_DEEP,now,260,MANUAL_RECOVERY_SOURCE)" in market),
    ("human repair starts a fresh retry cycle",
     'SET attempt=0 WHERE job_key=?' in market and
     '"vinted-deep:"+canonicalId' in market),
    ("current-run deterministic misses require three attempts",
     "job.attempt>=3" in runner and
     "listingBelongsToActiveRun(job.listingId)" in runner),
    ("transient waits are not manual-review evidence",
     "isTransientVintedWait(reason)" in transient_branch and
     "settleAutomaticAmbiguity" not in transient_branch and
     "next = Math.max(VintedPublicSession.nextAllowedAt(context), resolver.nextAllowedAt(candidate))" in transient_branch),
    ("historical catalog repair is serial and self-cleaning",
     'CATALOG_RECOVERY_SOURCE = "CATALOG_RECOVERY"' in market and
     'source IN (?,?)' in market[market.index("public int enqueueCatalogHealthCheckIfIdle"):market.index("5.12.22 cut-over")] and
     "ARCHIVED_UNRESOLVED" in runner and
     "Catalogo storico: annuncio non identificabile dopo 3 tentativi" in runner),
    ("manual recovery deep work blocks publication until completion",
     "j.source='MANUAL_RECOVERY'" in deal),
    ("Motore completion truth includes held and review terminal states",
     "DealDatabase.engineContentSettled(run)" in ui and
     "day.completeListings+day.reviewListings+day.heldListings>=day.validListings" in ui),
    ("user-facing fake minute ETA is gone",
     "min di corsia" not in ui and
     "verifiche Vinted rimaste" in ui and
     "prossima richiesta" in ui),
    ("recovery station is explicit",
     'renderEngineHeader("Da completare"' in ui and
     '"Manca il collegamento Vinted"' in ui and
     '"Manca il gioco BGG"' in ui),
    ("catalog shows publishable cards and links recovery back to Motore",
     'db.getDeals("trusted_any_price",800)' in ui and
     '"Da completare · "+blockedCount' in ui),
    ("strong red incomplete badge is removed",
     '"Vinted da completare · "' not in ui and
     'warning.setContentDescription("Dati in aggiornamento")' in ui),
    ("bundle page distinguishes real bundles from seller exploration",
     '"Bundle trovati"' in ui and
     '"Venditori da esplorare"' in ui and
     "BundleExploration.begin(this,d)" in ui),
    ("bundle exploration intent is bounded",
     "TTL_MS=10L*60_000L" in explore and
     "bundleExploreHintsThisIntent>=40" in radar),
    ("diagnostics name exact current-run blockers",
     "engineCoreRemaining={" in radar and
     "engineCoreRemainingSummary()" in market),
    ("dead legacy Activity renderer is gone",
     "renderOperationsLegacyPage" not in ui),
]

for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("5.12.30 engine recovery truth regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} 5.12.30 recovery/truth guards")
