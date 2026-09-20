from pathlib import Path
src=Path('app/src/main/java/it/vintedaffari/app/VintedCandidateSnapshotShadow.java').read_text()
checks={
'build_v3':'candidate-local-price-shadow-v3' in src,
'new_table':'vinted_shadow_snapshots_v3' in src,
'zero_network':'getPublic(' not in src and 'HttpURLConnection' not in src,
'local_midpoint':'nearest DIFFERENT item' in src,
'avg_hints':'avgHintsPerPricedCandidate' in src,
'pair_unique':'pairUnique' in src,
'strict_unique':'strictUnique' in src,
}
for k,v in checks.items(): print(('PASS' if v else 'FAIL'),k)
raise SystemExit(0 if all(checks.values()) else 1)
