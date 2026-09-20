from pathlib import Path
root=Path(__file__).resolve().parents[1]
main=(root/'app/src/main/java/it/vintedaffari/app/MainActivity.java').read_text()
svc=(root/'app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java').read_text()
op=(root/'app/src/main/java/it/vintedaffari/app/OperationCenter.java').read_text()
build=(root/'app/build.gradle').read_text()
checks={
    'version': "versionName '5.10.13-stability-queue'" in build and 'versionCode 84' in build,
    'bulk queue': 'manualRefreshQueue' in svc and 'enqueueAllMissingForRefresh' in svc and 'startNextManualRefreshTarget' in svc,
    'single item priority': 'manualRefreshQueue.addFirst(signature)' in svc,
    'bulk survives service restart': 'manualBulkRequested' in svc and 'enqueueAllMissingForRefresh();if(TextUtils.isEmpty(manualRefreshTargetSignature))startNextManualRefreshTarget()' in svc,
    'BGG continues beside Vinted': '5-bggEnricher.scheduledCount()' in svc and 'for(String sig:new ArrayList<>(manualRefreshQueue))' in svc,
    'factual item progress': 'refreshProgress(DealRecord d)' in svc and 'maintenance:item:' in svc and 'OperationCenter.progress' in svc,
    'no cooldown bypass on poke': 'lastPriorityLinkAttemptAt=0' not in svc[svc.index('private final BroadcastReceiver retryReceiver'):svc.index('private long lastBacklogAttemptAt')],
    'per-item activity cards': 'maintenanceItemCard' in main and 'Riprova questa card' in main,
    'top thin bar removed': 'progressTap' not in main and 'progressBarStyleHorizontal' in main,
    'activity icon state': 'updateActivityIndicator' in main and 's.error>0' in main,
    'operation broadcasts deduped': 'boolean found=false,changed=false' in op and 'if(changed){p.edit().putString("tasks"' in op,
    'library render caches': 'libraryMarketCache' in main and 'prepareLibraryRenderCaches' in main,
    'deferred render': 'scheduleRender' in main and 'renderInProgress' in main,
    'error reconciliation': 'resolveForDeal' in op and 'reconcileDealOperations' in main,
    'wizard tombstone': 'wizardSessionId' in main and 'completeLibraryWizardSession' in main and 'wizardSessionIsLive' in main,
    'crash journal': 'installCrashJournal' in main and 'lastCrashAt' in main,
    'diagnostics expose refresh/ui': 'refreshBulkRequested' in svc and 'uiLastRenderMs' in svc and 'lastCrashAt' in svc,
}
for name, ok in checks.items(): print(('PASS' if ok else 'FAIL'), name)
raise SystemExit(0 if all(checks.values()) else 1)
