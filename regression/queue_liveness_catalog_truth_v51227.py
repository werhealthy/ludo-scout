#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
market=(ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
runner=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueJobRunner.java").read_text(encoding="utf-8")
service=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueKeepAliveService.java").read_text(encoding="utf-8")
ui=(ROOT/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
deal=(ROOT/"app/src/main/java/it/vintedaffari/app/DealDatabase.java").read_text(encoding="utf-8")
radar=(ROOT/"app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

RESERVE=65_000

def reserve(now,urgent_active,urgent_due,next_due):
    if urgent_active<=0 or urgent_due>0:
        return 0
    if next_due<=now or next_due-now>RESERVE:
        return 0
    return next_due

# A near-due Hunt/manual/live item may hold one request slot.
assert reserve(100_000,1,0,150_000)==150_000
# But a retry minutes away must not freeze runnable Motore work.
assert reserve(100_000,1,0,700_000)==0
# An urgent item that is already due does not need a reservation; priority ordering claims it.
assert reserve(100_000,1,1,90_000)==0
assert reserve(100_000,0,0,0)==0

urgent_block=market[market.index("private static final long URGENT_VINTED_RESERVE_MS"):market.index("/** A strong Vinted batch link",market.index("private static final long URGENT_VINTED_RESERVE_MS"))]
runner_block=runner[runner.index("private static boolean processOneVintedInternal"):runner.index("public static void sweepMissing")]
lane_block=service[service.index("private void vintedLoop"):service.index("private void bggLoop")]
catalog_block=ui[ui.index("private void applyCatalogFilter"):ui.index("private String sortLabel")]
count_line=deal[deal.index("public synchronized int countVintedIncomplete"):deal.index("public synchronized void updateUserFields")]

checks=[
    ("urgent reservation is bounded to one public-page slot",
     "URGENT_VINTED_RESERVE_MS=65_000L" in urgent_block and
     "next-now>URGENT_VINTED_RESERVE_MS" in urgent_block),
    ("future urgent retry no longer globally blocks Motore",
     "urgentVintedWorkCount(now)>0 && market.urgentVintedDueCount(now)==0" not in runner_block and
     "urgentVintedReservationUntil(now)>now" in runner_block),
    ("near-due urgent work still preempts ordinary work",
     "urgentVintedReservationUntil" in urgent_block and
     "CASE WHEN j.job_type=? THEN 0 ELSE 1 END,j.priority DESC" in market and
     "CASE WHEN j.source='OPENED_VERIFY' THEN 0 ELSE 1 END" in market),
    ("lane reports intentional reservation instead of false idle",
     'urgentVintedReservationUntil(now)' in lane_block and
     '"WAITING","priorità Vinted tra "' in lane_block),
    ("catalog incomplete predicate includes url publication and seller",
     "TextUtils.isEmpty(d.vintedUrl)" in catalog_block and
     "TextUtils.isEmpty(d.publishedLabel)" in catalog_block and
     "TextUtils.isEmpty(d.sellerId)" in catalog_block),
    ("catalog incomplete badge counts the same field family",
     "vinted_url IS NULL OR vinted_url=''" in count_line and
     "published_label IS NULL OR published_label=''" in count_line and
     "seller_id IS NULL OR seller_id=''" in count_line),
    ("core-link warning remains specifically about the missing page",
     'private boolean vintedCoreMissing(DealRecord d){return d==null||TextUtils.isEmpty(d.vintedUrl);}' in ui),
    ("diagnostics expose urgent queue truth",
     "vintedUrgent={active=" in radar and "reserveUntil=" in radar),
    ("trusted Home contract remains unchanged",
     'db.getDeals("trusted",320)' in ui and '"trusted".equals(filter)' in deal),
    ("build identity and CI version strategy remain unchanged",
     "applicationId 'it.vintedaffari.app'" in build and
     "1000000 + ciVersionCode.toInteger()" in build),
]

for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("5.12.27 queue/catalog regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} queue/catalog truth guards")
