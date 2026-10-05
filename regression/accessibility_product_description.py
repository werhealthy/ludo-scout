from pathlib import Path
root=Path(__file__).resolve().parents[1]
parser=(root/"app/src/main/java/it/vintedaffari/app/ProductPageParser.java").read_text(encoding="utf-8")
service=(root/"app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
market=(root/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
checks=[
 ("bounded product-detail semantic helper","isProductDetailField" in parser and 'lid.contains("item_info")' in parser),
 ("opened page carries details text","page.publishedLabel,page.detailsText,page.itemPrice" in service),
 ("market persists accessibility details",'v.put("observed_text",safe(detailsText.trim()))' in market),
]
bad=[n for n,ok in checks if not ok]
if bad: raise SystemExit("FAIL: "+", ".join(bad))
print("PASS accessibility_product_description")
