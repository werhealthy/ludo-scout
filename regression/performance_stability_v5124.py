#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
main = (ROOT / "app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
a11y = (ROOT / "app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
thumbs = (ROOT / "app/src/main/java/it/vintedaffari/app/ThumbnailStore.java").read_text(encoding="utf-8")
db = (ROOT / "app/src/main/java/it/vintedaffari/app/DealDatabase.java").read_text(encoding="utf-8")

checks = [
    ("run inspector paginated", "final int pageSize=24" in main and "engineRunPage" in main),
    ("run inspector avoids per-card deal lookup", "private View engineRunItemCard" in main and "db.findBySignature(item.signature)" not in main[main.index("private View engineRunItemCard"):main.index("private void openEngineRunItem")]),
    ("run page auto refresh bounded", '"run".equals(engineSection)?12_000L:6_000L' in main),
    ("operation reconciliation coalesced", "operationReconcileInFlight" in main and "lastOperationReconcileAt" in main and "maintenanceIo.execute" in main),
    ("UI bitmap cache reduced", "LruCache<String,Bitmap>(4*1024*1024)" in main),
    ("production accessibility uses fast explicit probe", "explicitVintedIdentityHintFast(node)" in a11y and "probe=fast-explicit-only" in a11y),
    ("deep probe not called from card collection", "explicitVintedIdentityHint(node,uniqueProbeCard)" not in a11y),
    ("accessibility event diagnostics throttled", "pendingVintedEventDiag" in a11y and "lastVintedEventDiagFlushAt" in a11y),
    ("screenshots serialized", "SCREENSHOT_IN_FLIGHT" in thumbs and "compareAndSet(false,true)" in thumbs),
    ("remote thumbnail decode uses RGB565", "opts.inPreferredConfig=Bitmap.Config.RGB_565" in thumbs),
    ("active run state cached", "ACTIVE_RUN_CACHE_MS" in db and "cachedActiveRunAt" in db),
    ("waiting runs use lightweight timestamp query", 'SELECT observed_at FROM observations WHERE observed_at>?' in db),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
if failed:
    raise SystemExit("Performance/stability regression failed: " + ", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} performance/stability guards")
