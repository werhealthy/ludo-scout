#!/usr/bin/env python3
from pathlib import Path
import sqlite3

ROOT=Path(__file__).resolve().parents[1]
market=(ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")

start=market.index("public int reconcileBggQualityDealMirrors")
end=market.index("public int reconcileQueue",start)
section=market[start:end]

checks=[
 ("children mirror normalized","BGG_CHILDRENS_GAME" in section and "Escluso: categoria BGG Children's Game" in section),
 ("rating mirror normalized","BGG_RATING_BELOW_6" in section and "Escluso: voto BGG sotto 6" in section),
 ("human decisions protected","COALESCE(confirmed,0)=0" in section and "USER_CONFIRMED" in section),
 ("canonical game gate required","database_visible=0" in section and "filter_reason='BGG_CHILDRENS_GAME'" in section and "filter_reason='BGG_RATING_BELOW_6'" in section),
 ("zero network repair",all(token not in section for token in ["HttpURLConnection","VintedPublicSession","AiBetaClient","enqueueListingJob"])),
 ("repair runs in reconcile","enforceGlobalCatalogRatingGate(now)+reconcileBggQualityDealMirrors()" in market),
]
for name,ok in checks:
 print(("PASS " if ok else "FAIL ")+name)
bad=[name for name,ok in checks if not ok]
if bad:
 raise SystemExit("BGG quality deal mirror regression failed: "+", ".join(bad))

db=sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE games(bgg_id TEXT PRIMARY KEY,database_visible INTEGER,filter_reason TEXT);
CREATE TABLE deals(signature TEXT PRIMARY KEY,lifecycle TEXT,verification_state TEXT,verification_reason TEXT,confirmed INTEGER,bgg_id TEXT);
""")
db.executemany("INSERT INTO games VALUES(?,?,?)",[
 ("c",0,"BGG_CHILDRENS_GAME"),
 ("l",0,"BGG_RATING_BELOW_6"),
 ("v",1,NULL)
])
db.executemany("INSERT INTO deals VALUES(?,?,?,?,?,?)",[
 ("stale-child","REMOVED","PRICE_FILTERED","old",0,"c"),
 ("stale-low","ACTIVE","OK",NULL,0,"l"),
 ("human","ACTIVE","USER_CONFIRMED","human",1,"c"),
 ("visible","ACTIVE","OK",NULL,0,"v"),
])
child_reason="Escluso: categoria BGG Children's Game"
db.execute("""UPDATE deals SET lifecycle='REMOVED',verification_state='BGG_CHILDRENS_GAME',verification_reason=?
 WHERE COALESCE(confirmed,0)=0 AND COALESCE(verification_state,'')<>'USER_CONFIRMED'
 AND bgg_id IN (SELECT bgg_id FROM games WHERE database_visible=0 AND filter_reason='BGG_CHILDRENS_GAME')""",(child_reason,))
low_reason="Escluso: voto BGG sotto 6"
db.execute("""UPDATE deals SET lifecycle='REMOVED',verification_state='BGG_RATING_BELOW_6',verification_reason=?
 WHERE COALESCE(confirmed,0)=0 AND COALESCE(verification_state,'')<>'USER_CONFIRMED'
 AND bgg_id IN (SELECT bgg_id FROM games WHERE database_visible=0 AND filter_reason='BGG_RATING_BELOW_6')""",(low_reason,))
assert db.execute("SELECT lifecycle,verification_state FROM deals WHERE signature='stale-child'").fetchone()==("REMOVED","BGG_CHILDRENS_GAME")
assert db.execute("SELECT lifecycle,verification_state FROM deals WHERE signature='stale-low'").fetchone()==("REMOVED","BGG_RATING_BELOW_6")
assert db.execute("SELECT lifecycle,verification_state FROM deals WHERE signature='human'").fetchone()==("ACTIVE","USER_CONFIRMED")
assert db.execute("SELECT lifecycle,verification_state FROM deals WHERE signature='visible'").fetchone()==("ACTIVE","OK")
print("PASS semantic mirror normalization")
print(f"PASS {len(checks)+1}/{len(checks)+1} BGG quality mirror guards")
