from pathlib import Path
root=Path(__file__).resolve().parents[1]
op=(root/'app/src/main/java/it/vintedaffari/app/OperationCenter.java').read_text()
svc=(root/'app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java').read_text()
main=(root/'app/src/main/java/it/vintedaffari/app/MainActivity.java').read_text()
bgg=(root/'app/src/main/java/it/vintedaffari/app/BggEnricher.java').read_text()
checks={
'paused state':'PAUSED="in pausa"' in op,
'active includes paused':'queued+running+paused' in op,
'dedup bgg':'!scheduled.add(bggId)' in bgg,
'bgg failure backoff':'failedUntil.put' in bgg and 'isDeferred' in bgg,
'maintenance pump':'maintenancePump' in svc,
'rate limit pause':'OperationCenter.paused(VintedAccessibilityService.this,id,type' in svc,
'no duplicate global queue':'OperationCenter.active(this,id)' in main,
'paused activity group':'In pausa · riprende automaticamente' in main,
'item status':'Aggiornamento in pausa' in main,
}
for k,v in checks.items(): print(('PASS' if v else 'FAIL'),k)
raise SystemExit(0 if all(checks.values()) else 1)
