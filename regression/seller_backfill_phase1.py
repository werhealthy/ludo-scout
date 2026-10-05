from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
market=(ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
session=(ROOT/"app/src/main/java/it/vintedaffari/app/VintedPublicSession.java").read_text(encoding="utf-8")
checks=[
 ("dedicated source",'SELLER_BACKFILL_SOURCE = "SELLER_BACKFILL"' in market),
 ("missing seller only","(l.seller_id IS NULL OR l.seller_id='')" in market),
 ("exact identity only","l.vinted_item_id IS NOT NULL" in market and "l.vinted_url IS NOT NULL" in market),
 ("one shot marker","SELLER_BACKFILL_MARKER_PREFIX" in market and "state=QUEUED;source=" in market),
 ("serial owner","source IN (?,?,?)" in market and "SELLER_BACKFILL_SOURCE" in market),
 ("same paced lane","JOB_VINTED_DEEP" in market and "PUBLIC_MIN_INTERVAL_MS=55_000L" in session and "PUBLIC_HOURLY_BUDGET=60" in session),
 ("motore idle","activeObservationSession()!=null" in market),
]
bad=[name for name,ok in checks if not ok]
if bad: raise SystemExit("FAIL: "+", ".join(bad))
print("PASS seller_backfill_phase1")
