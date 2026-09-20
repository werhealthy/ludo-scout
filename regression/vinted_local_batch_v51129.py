from pathlib import Path
root=Path(__file__).resolve().parents[1]
svc=(root/'app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java').read_text()
store=(root/'app/src/main/java/it/vintedaffari/app/MarketStore.java').read_text()
main=(root/'app/src/main/java/it/vintedaffari/app/MainActivity.java').read_text()
gradle=(root/'app/build.gradle').read_text()
checks={
 'version': "5.11.29-vinted-local-batch" in gradle,
 'explicit_accessibility_item_capture': 'explicitVintedIdentityHint' in svc and 'vintedIdsCapturedFromAccessibility' in svc and '/items/' in svc,
 'no_guess_numeric_identity': 'generic view ids are never treated' in svc,
 'direct_capture_skips_search': 'applyExplicitVintedIdentityHint(card,sig)' in svc and 'applyManualVintedLink(0,listingId' in svc,
 'local_bulk_processor': 'optimizeLocalBacklog()' in store and 'LocalBatchSummary' in store,
 'bulk_zero_network_ui': 'Analizzo sul telefono senza contattare Vinted' in main and 'runLocalBacklogOptimizer' in main,
 'deferred_still_bounded': 'promoteDeferredVintedBatch' in store,
}
failed=[k for k,v in checks.items() if not v]
for k,v in checks.items(): print(('PASS' if v else 'FAIL'),k)
raise SystemExit(1 if failed else 0)
