from pathlib import Path
root=Path(__file__).resolve().parents[1]
service=(root/"app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
checks=[
 ("only exact opened listing expands",'if(exactOpened){' in service and 'maybeExpandProductDescription(root)' in service),
 ("only when details are missing",'TextUtils.isEmpty(product.detailsText)&&maybeExpandProductDescription(root)' in service),
 ("uses accessibility click only",'performAction(AccessibilityNodeInfo.ACTION_CLICK)' in service),
 ("bounded description section",'lid.contains("item_description_content")' in service),
 ("repeat click throttled",'now-lastDescriptionExpandAt<2_000L' in service),
]
bad=[n for n,ok in checks if not ok]
if bad: raise SystemExit("FAIL: "+", ".join(bad))
print("PASS accessibility_description_expand")
