from pathlib import Path
svc=Path('app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java').read_text()
store=Path('app/src/main/java/it/vintedaffari/app/MarketStore.java').read_text()
checks={
 'probe_version_1d':'probe=1d-local-cache' in svc and '!"1d".equals' in svc,
 'cross_process_sqlite_writer':'setDiagnosticState("a11y_probe"' in svc,
 'cross_process_sqlite_reader':'diagnosticState("a11y_probe")' in svc,
 'authoritative_line':'a11yProbeCrossProcess={authoritative=true' in svc,
 'sqlite_kv_api':'public void setDiagnosticState' in store and 'public RuntimeStatus diagnosticState' in store,
 'descendant_bfs':'for(int i=0;i<n.getChildCount();i++)' in svc,
 'ancestor_probe':'ancestorNodes++' in svc,
 'compose_extra_request':'refreshWithExtraData' in svc,
 'strict_item_pattern':'VINTED_RELATIVE_ITEM.matcher' in svc and '/items/' in svc,
}
failed=[k for k,v in checks.items() if not v]
for k,v in checks.items(): print(('PASS' if v else 'FAIL'),k)
raise SystemExit(1 if failed else 0)
