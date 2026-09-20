from pathlib import Path
root=Path(__file__).resolve().parents[1]
svc=(root/'app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java').read_text()
session=(root/'app/src/main/java/it/vintedaffari/app/VintedPublicSession.java').read_text()
resolver=(root/'app/src/main/java/it/vintedaffari/app/VintedLinkResolver.java').read_text()
main=(root/'app/src/main/java/it/vintedaffari/app/MainActivity.java').read_text()
build=(root/'app/build.gradle').read_text()
checks={
 'version': "versionName '5.10.14-cooldown-pacing'" in build and 'versionCode 85' in build,
 'public next allowed': 'nextAllowedAt(Context context)' in session and 'vintedPublicCircuitUntil' in session,
 'per listing cooldown exposed': 'nextAllowedAt(DealRecord d)' in resolver and 'last+RETRY_MS' in resolver,
 'bulk pacing': 'recommendedBulkGapMs(int expectedRequests)' in session and 'currentLinkGapMs(DealRecord d)' in svc and 'expectedRequests' in session,
 'authoritative pause': 'authoritativeVintedResumeAt' in svc and 'setVintedPause' in svc,
 'no 45s forced cooldown retry': 'maintenancePausedUntil=System.currentTimeMillis()+PRIORITY_LINK_GAP_MS' not in svc,
 'bundle network yields to bulk': 'if(manualBulkMode)return;' in svc and 'priorità aggiornamento dati' in svc,
 'queued starts zero': 'OperationCenter.progress(this,id,0,0L)' in svc,
 'session progress': 'manualRefreshInitialMissingMask' in svc and 'refreshSessionProgress' in svc,
 'master only completed': 'manualBulkCompleted)*100.0/total' in svc,
 'queued UI no fake percent': 'OperationCenter.QUEUED.equals(t.state)||t.progress<=0' in main,
 'live countdown': 'retryCountdownView' in main and 'postDelayed(this,1000L)' in main,
 'diagnostics authoritative': 'refreshVintedAuthoritativeUntil' in svc and 'refreshVintedGapMs' in svc,
}
for k,v in checks.items(): print(('PASS' if v else 'FAIL'),k)
raise SystemExit(0 if all(checks.values()) else 1)
