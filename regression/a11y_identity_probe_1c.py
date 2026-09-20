from pathlib import Path

src = Path('app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java').read_text()
checks = {
    'probe_version_visible': 'probe=1c' in src,
    'ancestor_probe': 'ancestorNodes++' in src and 'ancestorExplicitHits++' in src,
    'ancestor_not_autolinked': 'recordIdentityProbe(stats,uniqueProbeCard,"");return null;' in src,
    'compose_extra_requested': 'refreshWithExtraData' in src and 'requested_extra_value' in src,
    'strict_item_path_preserved': 'VINTED_RELATIVE_ITEM.matcher' in src and '/items/' in src,
    'probe_counters_reset_on_version': 'a11yProbeVersion' in src and '!"1c".equals' in src,
    'requested_extra_visible': 'a11yProbeLastRequestedExtra=' in src,
    'ancestor_hit_visible': 'a11yProbeLastAncestorExplicit=' in src,
}
failed = [k for k,v in checks.items() if not v]
for k,v in checks.items():
    print(('PASS' if v else 'FAIL'), k)
raise SystemExit(1 if failed else 0)
