from pathlib import Path
root=Path(__file__).resolve().parents[1]
shadow=(root/'app/src/main/java/it/vintedaffari/app/VintedBatchReuseShadow.java').read_text()
diag=(root/'app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java').read_text()
checks={
 'zero_network_class': 'getPublic(' not in shadow and 'HttpURLConnection' not in shadow,
 'does_not_mutate_jobs': all(x not in shadow for x in ['UPDATE processing_jobs','DELETE FROM processing_jobs','INSERT INTO processing_jobs','claimNextVintedJob']),
 'queued_core_only': "j.job_type=? AND j.state IN (?,?,?)" in shadow,
 'deferred_gate': "l.enrichment_state='DEFERRED_LINK'" in shadow and 'g.database_visible=1 AND g.rating>=?' in shadow,
 'canonical_family': 'key = "g:"+gameId' in shadow,
 'fallback_excluded': 'fallbacks-not-counted' in shadow,
 'diagnostic_line': 'vintedBatchReuseShadow={' in diag,
 'top_groups': 'topReady=' in shadow,
}
failed=[]
for name,ok in checks.items():
    print(('PASS' if ok else 'FAIL'),name)
    if not ok: failed.append(name)
if failed: raise SystemExit(1)
