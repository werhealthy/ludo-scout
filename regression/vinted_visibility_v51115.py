from pathlib import Path
import sys
root=Path(__file__).resolve().parents[1]

def read(rel): return (root/rel).read_text(encoding='utf-8')

def check(name, cond):
    print(('PASS' if cond else 'FAIL'), name)
    return bool(cond)

main=read('app/src/main/java/it/vintedaffari/app/MainActivity.java')
market=read('app/src/main/java/it/vintedaffari/app/MarketStore.java')
resolver=read('app/src/main/java/it/vintedaffari/app/VintedLinkResolver.java')
runner=read('app/src/main/java/it/vintedaffari/app/QueueJobRunner.java')
diag=read('app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java')
deals=read('app/src/main/java/it/vintedaffari/app/DealDatabase.java')
gradle=read('app/build.gradle')
checks=[]
checks.append(check('version 5.11.15', "versionName '5.11.15-vinted-visibility'" in gradle))
checks.append(check('retry reason visible', 'friendlyJobError(job.lastError)' in main and 'Riprovo ' in main))
checks.append(check('manual unresolved review', 'Da verificare · Vinted' in main and 'Non più su Vinted' in main and 'Riprova ora' in main))
checks.append(check('archive preserves history', 'lifecycle","REMOVED"' in market and 'Prezzi e storico restano salvati.' in main and 'markUnavailable' in deals))
checks.append(check('catalog vinted indicator', 'provider_vinted_logo' in main and 'Vinted da completare' in main and 'vintedDataIncomplete' in main))
checks.append(check('catalog incomplete filter', 'new String[]{"all","complete","incomplete","pending"}' in main))
checks.append(check('source aware database back', 'databaseDetailReturnTab' in main and 'openDatabaseGame(gameId,tab)' in main and 'closeDatabaseGame()' in main))
checks.append(check('separate lane cards', 'Vinted · in corso' in main and 'Database · BGG in corso' in main and 'addQueueLaneStatusCards' in main))
checks.append(check('database toggle is bgg', 'marketStore.isBggPaused()' in main and 'marketStore.setBggPaused' in main))
checks.append(check('phantom bgg reconciled', 'BGG match necessario; enrichment rinviato' in market and 'bggMatchRequiredCount' in market))
checks.append(check('resolver reasons classified', 'explainUnresolved' in resolver and 'Più annunci compatibili' in resolver and 'Pagina Vinted non disponibile (404)' in resolver))
checks.append(check('resolver diagnostics reset per attempt', 'putInt("linkPublicVerifyCode",0)' in resolver))
checks.append(check('legacy 8m per-item guard removed', 'RETRY_MS=60_000L' in resolver and '8*60_000L' not in resolver))
checks.append(check('deterministic retry bounded 5m', '5 * 60_000L' in runner and 'isGoneVintedPage' in runner))
checks.append(check('diagnostics expose lanes', 'bggMatchRequired=' in diag and 'vintedData={missingLink=' in diag and 'bggPaused=' in diag))
checks.append(check('secret exact', (root/'secrets.properties').exists() and not (root/'secrets.properties.example').exists()))
sys.exit(0 if all(checks) else 1)
