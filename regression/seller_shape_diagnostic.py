from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
r=(ROOT/"app/src/main/java/it/vintedaffari/app/VintedLinkResolver.java").read_text(encoding="utf-8")
assert "recordSellerShapeDiagnostic(html,id,item)" in r
assert 'putString("sellerShapeDiagnostic"' in r
assert "raw HTML" in r and "raw.length()" in r
assert 'needles={"user_id","seller_id","owner_id"' in r
print("PASS seller_shape_diagnostic")
