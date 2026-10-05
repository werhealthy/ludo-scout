from pathlib import Path

root = Path(__file__).resolve().parents[1]
market = (root / "app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
runner = (root / "app/src/main/java/it/vintedaffari/app/QueueJobRunner.java").read_text(encoding="utf-8")

checks = [
    ("exact-page description is persisted", 'v.put("observed_text",safe(r.detailsText.trim()))' in market),
    ("description persistence is gated by non-empty evidence", 'if(!TextUtils.isEmpty(r.detailsText))' in market),
    ("existing resolver evidence remains reused locally", 'BggVariantReconciler.reconcile(context,db,market,canonical,r.signature,r.matchedTitle,r.detailsText)' in runner),
]
failed = [name for name, ok in checks if not ok]
if failed:
    raise SystemExit("FAIL: " + ", ".join(failed))
print("PASS public_page_evidence")
