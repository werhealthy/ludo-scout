from pathlib import Path
import gzip

root=Path(__file__).resolve().parents[1]
app=root/'app/src/main/java/it/vintedaffari/app'
engine=(app/'VintedBatchEngine.java').read_text()
variant=(app/'BggVariantReconciler.java').read_text()
runner=(app/'QueueJobRunner.java').read_text()
store=(app/'MarketStore.java').read_text()
resolver=(app/'VintedLinkResolver.java').read_text()
svc=(app/'VintedAccessibilityService.java').read_text()
main=(app/'MainActivity.java').read_text()
db=(app/'DealDatabase.java').read_text()
gradle=(root/'app/build.gradle').read_text()
snapshot=(app/'VintedCandidateSnapshotStore.java').read_text()

idx=gzip.decompress((root/'app/src/main/res/raw/bgg_search_index_gz').read_bytes()).decode('utf-8','replace')

checks={
    'version_5_12_1_ux': "versionCode 116" in gradle and "5.12.1-ux-engine-run" in gradle,
    'production_batch_exists': 'class VintedBatchEngine' in engine and 'BUILD = "batch-engine-v1"' in engine,
    'batch_has_no_http': all(x not in engine for x in ['HttpURLConnection','java.net.URL','getPublic(']),
    'batch_exact_price': 'if (pd > 1) continue;' in engine,
    'batch_one_to_one': 'itemAlreadyUsed' in engine and 'newlyUsed' in engine and 'claims.get(best.c.id)' in engine,
    'batch_safety_gate': 'candidateSafetyGate' in engine and 'hasMarketplaceNonGameCue' in engine and 'unexplained >= 2' in engine,
    'batch_rating_gate': 'g.database_visible=1 AND g.rating>=?' in engine,
    'batch_urgent_preemption': 'urgentVintedWorkCount' in engine and 'urgentVintedWorkCount' in runner and 'urgentVintedDueCount' in runner,
    'batch_before_network': runner.index('VintedBatchEngine.applyCached(context,db,market,6,false)') < runner.index('VintedPublicSession.nextAllowedAt(context)'),
    'batch_after_network': 'VintedBatchEngine.applyCached(context,db,market,6,true)' in runner,
    'batch_memory_bounded': 'SCAN_LIMIT = 180' in engine and 'loadSnapshot(db,fe.getKey(),started)' in engine and 'ensureSnapshotTable(db)' in engine,
    'snapshot_is_persistent_store': 'vinted_shadow_snapshots_v3' in snapshot and 'Production VintedBatchEngine may consume these snapshots' in snapshot,
    'variant_guard_exists': 'class BggVariantReconciler' in variant and 'localCandidates' in variant,
    'variant_requires_more_specific': 'tokenCount(cs) <= tokenCount(currentSig)' in variant and 'covers(cs, currentSig)' in variant,
    'variant_reads_page_text': 'pageTitle' in variant and 'detailsText' in variant,
    'variant_accessibility_hook': 'BggVariantReconciler.reconcile(this,database,marketStore' in svc,
    'variant_resolver_hook': 'BggVariantReconciler.reconcile(context,db,market,canonical' in runner,
    'resolver_carries_description': 'detailsText' in resolver and 'optString("description"' in resolver,
    'verified_price_only_from_exact_page': 'exactPagePrice' in resolver and 'applyExactPagePrice(r,verified)' in resolver and 'applyExactPagePrice(r,c)' in resolver,
    'verified_price_history_preserved': 'updateVerifiedCurrentPrice' in store and '"vinted-item-page"' in store and 'price_observations' in store,
    'verified_price_legacy_refresh': 'updateVerifiedCurrentPrice' in db and 'r.exactPagePrice' in runner and 'vintedVerifiedPriceUpdates' in runner,
    'variant_single_listing_move': 'reassignListingToBggVariant' in store and 'db.update("market_listings",l,"id=?"' in store,
    'variant_legacy_state_sync': all(x in db for x in ['markBggVariantPending','confirmBggVariant','flagBggVariantReview','AUTO_VARIANT_VERIFIED']),
    'variant_invalidates_old_benchmark': 'Variante aggiornata · riferimento da ricalcolare' in db and 'v.putNull("benchmark_cents")' in db,
    'tokaido_duo_in_local_index': '363183' in idx and 'Tokaido Duo' in idx and '123540' in idx,
    'production_diagnostics': 'vintedBatchEngine={' in svc and 'bggVariantGuard={' in svc,
    'test_buttons_removed': 'Test finale · applica max 3 batch sicuri' not in main and 'Test 2b · misura 1 attività Vinted' not in main,
    'settings_still_has_diagnostics': 'Copia diagnostica' in main,
}

failed=[k for k,v in checks.items() if not v]
for k,v in checks.items():
    print(('PASS' if v else 'FAIL'),k)
raise SystemExit(1 if failed else 0)
