#!/usr/bin/env python3
from pathlib import Path
import sqlite3

ROOT=Path(__file__).resolve().parents[1]
market=(ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
runner=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueJobRunner.java").read_text(encoding="utf-8")

start=market.index("public int materializeBrowserSnapshotMetadataBatch")
end=market.index("/** Persist validated public captures",start)
section=market[start:end]

checks=[
 ("zero-network replay exists","materializeBrowserSnapshotMetadataBatch" in section),
 ("exact item snapshot join","q.name='browser_snapshot:'||l.vinted_item_id" in section),
 ("active listings only","l.lifecycle='ACTIVE'" in section),
 ("bounded local batch","Math.min(500,limit)" in section and "if(changed>=max)break;" in section and "LIMIT ?" not in section),
 ("stage diagnostics exposed",all(token in section for token in ["scanned=", "parsed=", "usable=", "changed=", "browser-snapshot-metadata-v2"])),
 ("existing values win","COALESCE(NULLIF(" in section and ",NULLIF(?,''))" in section),
 ("publication bridges to deals","published_label" in section and 'db.update("deals"' in section),
 ("no network or queue work",all(token not in section for token in [
     "HttpURLConnection","VintedPublicSession","AutoLinkResolver","enqueueListingJob","QueueWorkScheduler"
 ])),
 ("maintenance replays before language/network scheduling",
  "materializeBrowserSnapshotMetadataBatch(200)" in runner and
  runner.index("materializeBrowserSnapshotMetadataBatch(200)") < runner.index("inferDeferredLanguages(120)")),
]

for name,ok in checks:
 print(("PASS " if ok else "FAIL ")+name)
bad=[name for name,ok in checks if not ok]
if bad:
 raise SystemExit("browser snapshot metadata replay regression failed: "+", ".join(bad))

# Semantic guard: a local replay fills gaps but never replaces fresher metadata.
db=sqlite3.connect(":memory:")
db.execute("""CREATE TABLE market_listings(
 id INTEGER PRIMARY KEY,lifecycle TEXT,published_label TEXT,seller_id TEXT,image_url TEXT,language_code TEXT)""")
db.executemany("INSERT INTO market_listings VALUES(?,?,?,?,?,?)",[
 (1,"ACTIVE","","","", ""),
 (2,"ACTIVE","ieri","42","old-photo","it"),
])
sql="""UPDATE market_listings SET
 published_label=COALESCE(NULLIF(published_label,''),NULLIF(?,'')),
 seller_id=COALESCE(NULLIF(seller_id,''),NULLIF(?,'')),
 image_url=COALESCE(NULLIF(image_url,''),NULLIF(?,'')),
 language_code=COALESCE(NULLIF(language_code,''),NULLIF(?,'')) WHERE id=? AND lifecycle='ACTIVE'"""
db.execute(sql,("2 giorni fa","77","new-photo","fr",1))
db.execute(sql,("oggi","99","replacement-photo","de",2))
assert db.execute("SELECT published_label,seller_id,image_url,language_code FROM market_listings WHERE id=1").fetchone()==(
 "2 giorni fa","77","new-photo","fr")
assert db.execute("SELECT published_label,seller_id,image_url,language_code FROM market_listings WHERE id=2").fetchone()==(
 "ieri","42","old-photo","it")
print("PASS local replay fills only missing metadata")
print(f"PASS {len(checks)+1}/{len(checks)+1} browser snapshot metadata guards")
