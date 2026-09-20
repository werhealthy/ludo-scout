from pathlib import Path
root=Path(__file__).resolve().parents[1]
svc=(root/'app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java').read_text()
checks={
 'urlspan_inspected': 'URLSpan[] urls=sp.getSpans' in svc and 'span.getURL()' in svc,
 'clickablespan_diagnostic': 'ClickableSpan[] clickable=sp.getSpans' in svc,
 'actions_inspected': 'getActionList()' in svc and 'action_label' in svc,
 'semantic_fields_inspected': all(x in svc for x in ['hint_text','tooltip_text','pane_title','state_description','container_title']),
 'strict_item_pattern_preserved': 'VINTED_RELATIVE_ITEM' in svc and '/items/' in svc and 'generic view ids are never treated' in svc,
 'urlspan_source_metric': 'a11yIdsFromUrlSpan' in svc and 'a11yProbeUrlSpans' in svc,
 'designer_visible_diagnostic': 'a11yIdentityProbe={' in svc and 'a11yIdentityResult={' in svc,
 'unique_card_probe_metric': 'a11yProbeUniqueCards' in svc,
}
failed=[k for k,v in checks.items() if not v]
for k,v in checks.items(): print(('PASS' if v else 'FAIL'),k)
raise SystemExit(1 if failed else 0)
