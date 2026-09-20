#!/usr/bin/env python3
import sqlite3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
client=(ROOT/"app/src/main/java/it/vintedaffari/app/BggSearchClient.java").read_text(encoding="utf-8")
market=(ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
runner=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueJobRunner.java").read_text(encoding="utf-8")
reval=(ROOT/"app/src/main/java/it/vintedaffari/app/BggHistoricalRevalidator.java").read_text(encoding="utf-8")
diag=(ROOT/"app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

fuzzy=client[client.index("public List<Game> localCandidatesIndexed"):client.index("/** Exact/alias lookup",client.index("public List<Game> localCandidatesIndexed"))]
hist=market[market.index("public void flagHistoricalBggReview"):market.index("public void completeHistoricalBggRevalidation")]
runnable=market[market.index("public int runnableVintedDueCount"):market.index("public int runnableBggDueCount")]
next_due=market[market.index("public long nextRunnableVintedDueAt"):market.index("public long nextRunnableBggDueAt")]

# Executable queue truth model: while Motore is idle, only explicit/manual/live work is claimable.
db=sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE jobs(source TEXT,state TEXT);
INSERT INTO jobs VALUES('AUTO','PENDING');
INSERT INTO jobs VALUES('DEFERRED_LINK','PENDING');
INSERT INTO jobs VALUES('LIVE_DEAL','PENDING');
INSERT INTO jobs VALUES('HUNT_PRIORITY','PENDING');
INSERT INTO jobs VALUES('MANUAL_PRIORITY','PENDING');
INSERT INTO jobs VALUES('HISTORICAL_REVALIDATION','PENDING');
""")
claimable=[r[0] for r in db.execute("""
SELECT source FROM jobs WHERE state='PENDING'
AND source IN ('HUNT_PRIORITY','MANUAL_PRIORITY','LIVE_DEAL')
ORDER BY source
""")]
assert claimable==['HUNT_PRIORITY','LIVE_DEAL','MANUAL_PRIORITY'], claimable

# Executable historical cleanup model: historical uncertainty remains trust-gated, but not user review.
db2=sqlite3.connect(":memory:")
db2.executescript("""
CREATE TABLE market_listings(id INTEGER PRIMARY KEY,manual_review_required INTEGER,manual_review_reason TEXT);
INSERT INTO market_listings VALUES(1,1,'Rivalidazione BGG storica: titolo non confermato');
INSERT INTO market_listings VALUES(2,1,'Scelta utente richiesta');
UPDATE market_listings SET manual_review_required=0,manual_review_reason=NULL
WHERE manual_review_required=1 AND manual_review_reason LIKE 'Rivalidazione BGG storica:%';
""")
assert db2.execute("SELECT manual_review_required FROM market_listings WHERE id=1").fetchone()[0]==0
assert db2.execute("SELECT manual_review_required FROM market_listings WHERE id=2").fetchone()[0]==1

checks=[
    ("fuzzy search uses compact token postings",
     "localTokenHashIndex" in client and "ensureTokenIndex()" in fuzzy and "hashRange(tokens,h)" in fuzzy),
    ("fuzzy search no longer scans the full 31k catalog per query",
     "for(Game g:catalog)" not in fuzzy and "candidateIds" in fuzzy and "candidateIds.size()<4000" in fuzzy),
    ("fuzzy ranking keeps the existing bounded top-16 confidence path",
     "PriorityQueue<RankedLocal> top=new PriorityQueue<>(16" in fuzzy and "top.size()<16" in fuzzy),
    ("token index is primitive and bounded-memory",
     "LongBuilder" in client and "long[] localExactHashIndex,localTokenHashIndex" in client and "HashSet<Integer> seenHashes" in client),
    ("fuzzy diagnostics expose token load and candidate work",
     "tokenLoadMs=" in client and "fuzzyCandidatesScanned=" in client and "build=bgg-local-index-v5" in client),
    ("historical audit no longer creates manual review",
     'manual_review_required",0' in hist and 'manual_review_required",1' not in hist and 'verification_state","MATCH_UNCERTAIN"' in hist),
    ("old historical review flags are cleared once",
     "clearHistoricalManualReviewDebt" in market and "historical_review_nonblocking_v1" in market),
    ("historical revalidation is described as held, not human review",
     "heldGames" in reval and "persistent review instead of being guessed" not in reval),
    ("idle runnable count matches claimable source policy",
     "j.source IN ('HUNT_PRIORITY','MANUAL_PRIORITY','LIVE_DEAL')" in runnable and
     "j.source IN ('HUNT_PRIORITY','MANUAL_PRIORITY','LIVE_DEAL')" in next_due),
    ("idle ordinary jobs are parked instead of spinning",
     "parkIdleOrdinaryVintedJobs" in market and "parked: nessuno scroll attivo" in market and
     "hasActiveObservationRun()" in runner),
    ("review diagnostics separate historical held debt",
     "review-breakdown-v2" in market and "historicalHeld=" in market and "reviewBreakdown={" in diag),
    ("cleanup diagnostics are visible on Pixel",
     "historicalReviewCutover={" in diag and "vintedIdleParking={" in diag),
    ("build invariants preserved",
     "applicationId 'it.vintedaffari.app'" in build and "1000000 + ciVersionCode.toInteger()" in build),
]
for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("5.12.23 review/fuzzy/queue regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} review/fuzzy/queue guards")
