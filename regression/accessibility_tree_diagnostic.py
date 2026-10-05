from pathlib import Path
root=Path(__file__).resolve().parents[1]
service=(root/"app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
checks=[
 ("tree diagnostic only on exact opened target",'if(exactOpened)diag().edit().putString("lastOpenedProductTree",productTreeDiagnostic(root)).apply();' in service),
 ("tree diagnostic bounded",'return truncate(out.toString(),6000);' in service and 'depth>14||out.length()>=6000' in service),
 ("tree captures ids and visible semantics",'getViewIdResourceName()' in service and 'node.getContentDescription()' in service),
]
bad=[n for n,ok in checks if not ok]
if bad: raise SystemExit("FAIL: "+", ".join(bad))
print("PASS accessibility_tree_diagnostic")
