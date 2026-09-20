from pathlib import Path
root=Path(__file__).resolve().parents[1]
main=(root/'app/src/main/java/it/vintedaffari/app/MainActivity.java').read_text()
market=(root/'app/src/main/java/it/vintedaffari/app/MarketStore.java').read_text()
runner=(root/'app/src/main/java/it/vintedaffari/app/QueueJobRunner.java').read_text()
diag=(root/'app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java').read_text()
session=(root/'app/src/main/java/it/vintedaffari/app/VintedPublicSession.java').read_text()
bundle=(root/'app/src/main/java/it/vintedaffari/app/SellerBundleScanner.java').read_text()
checks={
 'settings_action':'Test 2b · misura 1 attività Vinted' in main and 'runTest2bOneShot()' in main,
 'exclusive_lock':'isTest2bExclusiveActive()' in market and 'claimNextVintedJobForTest2b' in market,
 'normal_runner_honors_lock':'if(market.isTest2bExclusiveActive())return false;' in runner,
 'one_shot_runner':'processOneVintedForTest2b' in runner,
 'gate_not_bypassed':'VintedPublicSession.gateState(this)' in main and 'VINTED_PAUSED' in main,
 'ledger_delta':'physicalDelta=' in main and 'catalogDelta=' in main and 'itemDelta=' in main,
 'diagnostic_line':'vintedRequestOneShot=' in diag,
 'fresh_ledger_epoch':'request-ledger-v2b' in session,
 'bundle_defers_during_lock':'test diagnostico Vinted in corso' in bundle,
}
for name,ok in checks.items():
 print(('PASS' if ok else 'FAIL'),name)
 if not ok: raise SystemExit(1)
