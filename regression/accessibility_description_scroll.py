from pathlib import Path
s=(Path(__file__).resolve().parents[1]/"app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
checks=[("exact missing-details gate",'if(TextUtils.isEmpty(product.detailsText)){' in s),("targets description",'findNodeByViewId(root,"item_description_content")' in s),("scrolls ancestor only",'while(scrollable!=null&&!scrollable.isScrollable())scrollable=scrollable.getParent();' in s),("bounded cadence",'now-lastDescriptionScrollAt<4_000L' in s),("accessibility scroll",'performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)' in s)]
bad=[n for n,v in checks if not v]
if bad: raise SystemExit("FAIL: "+", ".join(bad))
print("PASS accessibility_description_scroll")
