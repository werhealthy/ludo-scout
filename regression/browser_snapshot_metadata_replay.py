#!/usr/bin/env python3
"""Static regression for zero-network browser snapshot metadata replay."""
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
market=(ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
runner=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueJobRunner.java").read_text(encoding="utf-8")
gradle=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

start=market.index("public int materializeBrowserSnapshotMetadataBatch")
end=market.index("/** Persist validated public captures",start)
replay=market[start:end]

checks={
    "replay method present":"browser_snapshot:'||l.vinted_item_id" in replay,
    "active only":"l.lifecycle='ACTIVE'" in replay,
    "bounded batch":"Math.min(1000,limit)" in replay and "LIMIT ?" in replay,
    "empty-only image":"TextUtils.isEmpty(c.getString(2))" in replay and 'v.put("image_url",image)' in replay,
    "empty-only photos":"TextUtils.isEmpty(c.getString(3))" in replay and 'v.put("listing_photos_csv",photos)' in replay,
    "empty-only seller":"TextUtils.isEmpty(c.getString(4))" in replay and 'v.put("seller_id",sellerId)' in replay and "TextUtils.isEmpty(c.getString(5))" in replay,
    "empty-only publication":"TextUtils.isEmpty(c.getString(6))" in replay and 'v.put("published_label",safe(published))' in replay,
    "language normalized locally":"ListingLanguageDetector.detect(snapshot.optString(\"language\"))" in replay,
    "photos validated":"BrowserCapturePolicy.photo" in replay,
    "deal sync preserves existing values":"CASE WHEN COALESCE(TRIM(published_label),'')=''" in replay and "CASE WHEN COALESCE(TRIM(seller_name),'')=''" in replay,
    "zero network":all(token not in replay for token in ("VintedPublicSession","HttpURLConnection","AiBetaClient","BggSearchClient","AiEngine")),
    "scheduled in local sweep":"market.materializeBrowserSnapshotMetadataBatch(750)" in runner,
    "runs before language inference":runner.index("market.materializeBrowserSnapshotMetadataBatch(750)") < runner.index("market.inferDeferredLanguages(750)"),
    "diagnostic is explicit":"network=0;ai=0" in replay,
    "version":"5.12.203-catalog-metadata-replay" in gradle,
}
for name,ok in checks.items():
    print(("PASS " if ok else "FAIL ")+name)
if not all(checks.values()):
    raise SystemExit(1)
print(f"PASS {len(checks)}/{len(checks)} browser snapshot metadata replay guards")
