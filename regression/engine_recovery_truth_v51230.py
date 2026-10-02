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
    ("recovery release lineage keeps application identity",
     "applicationId 'it.vintedaffari.app'" in build and "1000000 + ciVersionCode.toInteger()" in build),
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
     "In attesa del prossimo controllo Vinted" in ui and
     "verifiche online ancora necessarie" in ui),
    ("recovery station is explicit",
     'renderEngineHeader("Serve il tuo aiuto"' in ui and
     '"Collegamento Vinted"' in ui and
     '"Gioco BGG"' in ui),
    ("catalog shows publishable cards; recovery remains in Motore",
     'db.getDeals("trusted_any_price",800)' in ui and
     'engineAttentionCard(snapshot.recoveryCount)' in ui and
     'navigate("activity")' in ui),
    ("strong red incomplete badge is removed",
     '"Vinted da completare · "' not in ui and
     'View dot=new View(this);dot.setBackground(round(ORANGE,999,0,0))' in ui),
    ("bundle page distinguishes real bundles from seller exploration",
     '"Bundle confermati · "' in ui and
     ('"Da controllare · "' in ui or '"Da esplorare · "' in ui) and
     'openVintedBrowserExperiment(d.vintedUrl,"BUNDLE",d.sellerId)' in ui or 'openVintedBrowserExperiment(d.url,"BUNDLE",d.sellerId)' in ui),
    ("bundle exploration intent is bounded",
     "TTL_MS=10L*60_000L" in explore and
     "bundleExploreHintsThisIntent>=40" in radar),
    ("LOCAL_ONLY observations cannot monopolize old Motore runs",
     "l.enrichment_state IN ('NEEDS_REVIEW','LOCAL_ONLY')" in deal and
     'String coreRemaining=bgg+" AND NOT "+attention+" AND NOT "+trustHold' in deal),
    ("radar diagnostics distinguish new Vinted events from persisted counters",
     "lastVintedEventAgeMs=" in radar and 'p.getLong("lastEventAt",0)' in radar),
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


