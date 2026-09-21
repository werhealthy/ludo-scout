#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
deal=(ROOT/"app/src/main/java/it/vintedaffari/app/DealDatabase.java").read_text(encoding="utf-8")
market=(ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
runner=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueJobRunner.java").read_text(encoding="utf-8")
ui=(ROOT/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
radar=(ROOT/"app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
bundles=(ROOT/"app/src/main/java/it/vintedaffari/app/BundleDatabase.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

UNIT=55_000

def eta(core_pending,core_remaining,analysis_pending=0):
    remote=max(max(0,core_pending),max(0,core_remaining))*UNIT
    local=60_000 if analysis_pending>0 else 0
    return remote+local

# Field case from 5.12.27: 9 valid, 6 exact Vinted identities, 5 ready, 1 hold.
# Three BGG-ready listings still need Vinted identity even if no durable job is materialised.
assert eta(0,3)==165_000
assert eta(6,6)==330_000
assert eta(0,0,1)==60_000

promote=market[market.index("public int promoteDeferredVintedBatch"):market.index("public void deferBackgroundLink")]
health=market[market.index("public int enqueueCatalogHealthCheckIfIdle"):market.index("public int reopenTechnicalBggReviewsForExactIndex")]
product_scan=radar[radar.index("ProductPage product = ProductPageParser.parse"):radar.index("List<VintedCard> discovered",radar.index("ProductPage product = ProductPageParser.parse"))]
bundle_rebuild=radar[radar.index("private void rebuildLocalBundlesForSeller"):radar.index("private void maybeScanBundles",radar.index("private void rebuildLocalBundlesForSeller"))]
manual_counts=market[market.index("public int prioritizeIncompleteListings"):market.index("/** Active job for one legacy/feed card",market.index("public int prioritizeIncompleteListings"))]

checks=[
    ("ETA counts parked product work, not only processing_jobs",
     "coreRemainingListings" in deal and
     "Math.max(Math.max(0,s.corePendingListings),Math.max(0,s.coreRemainingListings))" in deal),
    ("diagnostics expose core remaining",
     "coreRemaining=" in radar),
    ("active owner reactivates its parked links regardless of background retry timestamp",
     "deferred_retry_at<=?" not in promote and
     "SELECT signature FROM observations WHERE observed_at>=? AND observed_at<=?" in promote),
    ("Motore UI shows factual remaining work instead of a minute forecast",
     "min di corsia" not in ui and
     '" verifiche Vinted rimaste"' in ui and
     '" · prossima richiesta "+retryCountdown(wait)' in ui),
    ("catalog health is idle-only, serial, and can repair both exact and missing-link history",
     'CATALOG_HEALTH_SOURCE = "CATALOG_HEALTH"' in market and
     'CATALOG_RECOVERY_SOURCE = "CATALOG_RECOVERY"' in market and
     "if(helper.activeObservationSession()!=null)return 0" in health and
     "l.vinted_item_id IS NOT NULL" in health and "l.vinted_url IS NOT NULL" in health and
     "source IN (?,?)" in health and
     "enqueueListingJob(db,listingId,JOB_VINTED_DEEP,now,5,CATALOG_HEALTH_SOURCE)" in health and
     "enqueueListingJob(db,recovery,JOB_VINTED,now,4,CATALOG_RECOVERY_SOURCE)" in health),
    ("catalog health uses existing paced resolver rather than new networking",
     "CATALOG_HEALTH_SOURCE.equals(job.source)" in runner and
     "VintedPublicSession" not in health and "HttpURLConnection" not in health),
    ("exact-open provenance is separate from search recovery",
     'OPENED_VINTED_TARGET = "opened_vinted_target"' in market and
     'MANUAL_VINTED_RECOVERY = "manual_vinted_recovery"' in market and
     "beginOpenedVintedTarget" in ui and "activeOpenedVintedTarget" in product_scan),
    ("opened sold page updates exact legacy and canonical listing",
     "database.markSold(currentProductDeal.signature)" in product_scan and
     "marketStore.markSold(exactListingId)" in product_scan and
     "bundleDatabase.invalidate(currentProductDeal)" in product_scan),
    ("exact opened product page refreshes publication seller and price",
     "handleProductPage(product,currentProductDeal,exactListingId)" in product_scan and
     "updateExactProductMetadata" in radar and
     "updateVerifiedCurrentPrice" in radar),
    ("exact catalog 404 retires item without human review",
     "CATALOG_HEALTH_SOURCE.equals(job.source)&&isGoneVintedPage(reason)" in runner and
     "market.markUnavailable(job.listingId,reason)" in runner and
     "db.markUnavailable(candidate.signature,reason)" in runner and
     "needsReview(job" not in runner[runner.index("if(MarketStore.CATALOG_HEALTH_SOURCE.equals(job.source)&&isGoneVintedPage(reason))"):runner.index("// Deep metadata is optional",runner.index("if(MarketStore.CATALOG_HEALTH_SOURCE.equals(job.source)&&isGoneVintedPage(reason))"))]),
    ("bundle invalidation is seller-wide and clears stale caches",
     'db.delete("bundle_suggestions","seller_id=? OR source_signature=? OR item_id=?"' in bundles and
     'db.delete("seller_catalog_cache","seller_id=?"' in bundles and
     'db.delete("seller_snapshot_cache","seller_id=?"' in bundles),
    ("seller graph is pruned below two active games",
     "bundleDatabase.clearSellerGraph(source.sellerId)" in bundle_rebuild and
     "bundleDatabase.clearSellerGraph(entry.getKey())" in bundle_rebuild),
    ("Catalog bundle badge and filters require a live two-game bundle",
     "private boolean hasLiveBundle" in ui and
     "if(hasLiveBundle(d))" in ui and
     'else if("bundle".equals(catalogPreset))l.removeIf(d->!hasLiveBundle(d))' in ui and
     "if(filterBundle)l.removeIf(d->!hasLiveBundle(d))" in ui),
    ("manual recheck sees missing publication and seller metadata",
     "published_label IS NULL OR l.published_label=''" in manual_counts and
     "seller_id IS NULL OR l.seller_id=''" in manual_counts and
     "TextUtils.isEmpty(d.publishedLabel)" in ui and "TextUtils.isEmpty(d.sellerId)" in ui),
    ("automatic catalog health stays behind active Motore ownership",
     "run==null" in market and "CATALOG_HEALTH" in market and
     "activeObservationSession()!=null" in health),
    ("trusted Home contract remains unchanged",
     'db.getDeals("trusted",320)' in ui and '"trusted".equals(filter)' in deal),
    ("build identity and CI version strategy remain unchanged",
     "applicationId 'it.vintedaffari.app'" in build and
     "1000000 + ciVersionCode.toInteger()" in build),
]

for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("5.12.28 catalog health regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} catalog health / bundle / ETA guards")
