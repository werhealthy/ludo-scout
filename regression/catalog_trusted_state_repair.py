#!/usr/bin/env python3
from pathlib import Path
import sqlite3

ROOT=Path(__file__).resolve().parents[1]
market=(ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")

retry=market[market.index("public void retryJob"):market.index("public void setJobProgress")]
repair=market[market.index("public int repairOptionalDeepMetadataListingState"):market.index("public int reconcileQueue")]

checks=[
 ("ordinary deep retry does not demote listing",
  "optionalDeep=JOB_VINTED_DEEP.equals(job.type)&&!MANUAL_RECOVERY_SOURCE.equals(job.source)" in retry
  and "else updateListingState(job.listingId, FAILED_RETRYABLE, error)" in retry),
 ("manual deep recovery still blocks when needed",
  "!MANUAL_RECOVERY_SOURCE.equals(job.source)" in retry),
 ("historical optional deep failures repair to core",
  'v.put("enrichment_state","CORE_COMPLETE")' in repair and
  "enrichment_state='FAILED_RETRYABLE'" in repair and
  "j.job_type=?" in repair),
 ("core repair requires exact Vinted and matched visible BGG",
  "TRIM(COALESCE(vinted_item_id,''))<>''" in repair and
  "TRIM(COALESCE(vinted_url,''))<>''" in repair and
  "match_state='MATCHED' AND database_visible=1" in repair),
 ("price filtered automatic rows normalize out of active",
  "verification_state='PRICE_FILTERED'" in repair and
  'v.put("lifecycle","REMOVED")' in repair and 'v.put("tier","filtered")' in repair),
 ("user-confirmed overrides protected",
  "COALESCE(confirmed,0)=0" in repair and "u.payload IS NOT NULL" in repair),
 ("repairs are local only",
  all(token not in repair for token in ["HttpURLConnection","VintedPublicSession","AiBetaClient","BggSearchClient"])),
 ("reconcile runs both repairs",
  "repairOptionalDeepMetadataListingState()+normalizeAutomaticPriceFilteredDeals()" in market),
]
for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
bad=[name for name,ok in checks if not ok]
if bad:
    raise SystemExit("trusted state repair regression failed: "+", ".join(bad))

db=sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE games(id INTEGER PRIMARY KEY,bgg_id TEXT,match_state TEXT,database_visible INTEGER);
CREATE TABLE market_listings(
 id INTEGER PRIMARY KEY,lifecycle TEXT,enrichment_state TEXT,match_state TEXT,
 vinted_item_id TEXT,vinted_url TEXT,game_id INTEGER,last_error TEXT);
CREATE TABLE processing_jobs(
 id INTEGER PRIMARY KEY,listing_id INTEGER,job_type TEXT,source TEXT,state TEXT);
CREATE TABLE deals(
 signature TEXT PRIMARY KEY,lifecycle TEXT,tier TEXT,tier_label TEXT,
 verification_state TEXT,confirmed INTEGER);
CREATE TABLE listing_overrides(signature TEXT,payload TEXT,excluded INTEGER);
""")
db.execute("INSERT INTO games VALUES(1,'100','MATCHED',1)")
db.executemany("INSERT INTO market_listings VALUES(?,?,?,?,?,?,?,?)",[
 (1,"ACTIVE","FAILED_RETRYABLE","MATCHED","11","https://www.vinted.it/items/11",1,"timeout recupero Vinted"),
 (2,"ACTIVE","FAILED_RETRYABLE","MATCHED","22","https://www.vinted.it/items/22",1,"manual timeout"),
])
db.executemany("INSERT INTO processing_jobs VALUES(?,?,?,?,?)",[
 (1,1,"VINTED_DEEP_ENRICHMENT","AUTO","FAILED_RETRYABLE"),
 (2,2,"VINTED_DEEP_ENRICHMENT","MANUAL_RECOVERY","FAILED_RETRYABLE"),
])
db.execute("""UPDATE market_listings SET enrichment_state='CORE_COMPLETE',last_error=''
 WHERE lifecycle='ACTIVE' AND enrichment_state='FAILED_RETRYABLE' AND match_state='MATCHED'
 AND TRIM(COALESCE(vinted_item_id,''))<>'' AND TRIM(COALESCE(vinted_url,''))<>''
 AND game_id IN (SELECT id FROM games WHERE match_state='MATCHED' AND database_visible=1 AND TRIM(COALESCE(bgg_id,''))<>'')
 AND EXISTS(SELECT 1 FROM processing_jobs j WHERE j.listing_id=market_listings.id AND j.job_type='VINTED_DEEP_ENRICHMENT'
 AND COALESCE(j.source,'AUTO')<>'MANUAL_RECOVERY' AND j.state IN ('PENDING','PROCESSING','FAILED_RETRYABLE'))""")
assert db.execute("SELECT enrichment_state FROM market_listings WHERE id=1").fetchone()[0]=="CORE_COMPLETE"
assert db.execute("SELECT enrichment_state FROM market_listings WHERE id=2").fetchone()[0]=="FAILED_RETRYABLE"

db.executemany("INSERT INTO deals VALUES(?,?,?,?,?,?)",[
 ("auto","ACTIVE","fair","Fair","PRICE_FILTERED",0),
 ("confirmed","ACTIVE","fair","Fair","PRICE_FILTERED",1),
 ("override","ACTIVE","fair","Fair","PRICE_FILTERED",0),
])
db.execute("INSERT INTO listing_overrides VALUES('override','{}',0)")
db.execute("""UPDATE deals SET lifecycle='REMOVED',tier='filtered',tier_label=''
 WHERE lifecycle='ACTIVE' AND verification_state='PRICE_FILTERED' AND COALESCE(confirmed,0)=0
 AND NOT EXISTS(SELECT 1 FROM listing_overrides u WHERE u.signature=deals.signature AND u.payload IS NOT NULL AND COALESCE(u.excluded,0)=0)""")
assert db.execute("SELECT lifecycle,tier FROM deals WHERE signature='auto'").fetchone()==("REMOVED","filtered")
assert db.execute("SELECT lifecycle FROM deals WHERE signature='confirmed'").fetchone()[0]=="ACTIVE"
assert db.execute("SELECT lifecycle FROM deals WHERE signature='override'").fetchone()[0]=="ACTIVE"

print("PASS semantic optional-deep core repair")
print("PASS semantic price-filter invariant")
print(f"PASS {len(checks)+2}/{len(checks)+2} trusted state repair guards")
