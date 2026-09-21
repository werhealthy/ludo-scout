#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ui=(ROOT/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
market=(ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
runner=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueJobRunner.java").read_text(encoding="utf-8")
radar=(ROOT/"app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
parser=(ROOT/"app/src/main/java/it/vintedaffari/app/ProductPageParser.java").read_text(encoding="utf-8")
queue=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueKeepAliveService.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

sold_branch=runner[runner.index("if (r.sold) {"):runner.index("} else if (legacy != null)",runner.index("if (r.sold) {"))]
core_summary=market[market.index("public String engineCoreRemainingSummary"):market.index("public int missingVintedCoreCount")]
resume=ui[ui.index("@Override protected void onResume"):ui.index("@Override protected void onPause")]

checks=[
    ("sold/next-action release lineage keeps app identity",
     "applicationId 'it.vintedaffari.app'" in build and "1000000 + ciVersionCode.toInteger()" in build),
    ("sold parser recognizes sold and unavailable wording",
     "venduto|venduta|sold" in parser and "non più disponibile" in parser and "item unavailable" in parser),
    ("accessibility sold reconciliation does not require legacy mirror",
     "currentProductDeal==null&&exactListing!=null" in radar and
     "product.sold&&(currentProductDeal!=null||exactListingId>0)" in radar and
     'lastOpenedVintedPage' in radar),
    ("sold listing is terminal in canonical queue",
     'v.put("lifecycle","SOLD")' in market and
     'j.put("state",COMPLETE)' in market[market.index("public void markSold"):market.index("public void markUnavailable")] and
     'db.delete("queue_controls","name=? AND value=?"' in market[market.index("public void markSold"):market.index("public void markUnavailable")]),
    ("sold resolver exits before price/variant/alert work",
     "market.clearVintedCandidates(job.listingId);" in sold_branch and "return;" in sold_branch),
    ("return from Vinted queues exact paced fallback",
     "activeOpenedVintedTarget(now)" in resume and
     "enqueueOpenedListingVerification(opened.listingId)" in resume and
     'OPENED_VERIFY_SOURCE = "OPENED_VERIFY"' in market and
     "enqueueListingJob(db,listingId,JOB_VINTED_DEEP,now,245,OPENED_VERIFY_SOURCE)" in market),
    ("gone exact opened page retires listing automatically",
     "MarketStore.OPENED_VERIFY_SOURCE.equals(job.source)&&isGoneVintedPage(reason)" in runner and
     'setDiagnosticState("opened_vinted_verify",2,"state=REMOVED' in runner),
    ("next-action notification is quiet and contextual",
     'NEXT_ACTION_CHANNEL="ludo_next_action_v1"' in queue and
     'NotificationManager.IMPORTANCE_LOW' in queue and
     'title="Puoi fare un nuovo scroll"' in queue and
     '" giochi da confermare"' in queue and
     'open.putExtra("open_engine_review",true)' in queue and
     ".setSilent(true)" in queue),
    ("review notification deep-links to Da completare",
     'getBooleanExtra("open_engine_review",false)' in ui and 'engineSection="review"' in ui),
    ("notification completion uses full settled truth including holds",
     "DealDatabase.engineContentSettled(run)" in queue),
    ("core blocker diagnostic mirrors held/review exclusions",
     "LEFT JOIN deals d" in core_summary and
     "BGG_VARIANT_REVIEW" in core_summary and
     "MATCH_UNCERTAIN" in core_summary and
     "PRICE_ANOMALY" in core_summary),
    ("sold diagnostics expose direct and fallback paths",
     '"openedVintedVerify={"' in radar and
     '"lastOpenedVintedPage="' in radar and
     '"lastOpenedVintedReconcile="' in radar),
]

for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("5.12.32 sold/next-action regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} 5.12.32 sold/next-action guards")
