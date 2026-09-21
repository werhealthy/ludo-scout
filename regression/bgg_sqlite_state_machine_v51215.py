#!/usr/bin/env python3
import re
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
market = (ROOT / "app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")

m = re.search(r"BGG_MATCH_ALGORITHM_VERSION\s*=\s*(\d+)", market)
if not m:
    raise SystemExit("Cannot find BGG_MATCH_ALGORITHM_VERSION")
ALG = int(m.group(1))

# First reproduce the SQLite affinity trap with a TEXT-bound selection argument.
db = sqlite3.connect(":memory:")
assert db.execute("SELECT COALESCE(4,0) < ?", (str(ALG),)).fetchone()[0] == 1, "fixture no longer reproduces SQLite storage-class comparison"
assert db.execute("SELECT COALESCE(4,0) < CAST(? AS INTEGER)", (str(ALG),)).fetchone()[0] == 0, "numeric cast does not fix affinity trap"

db.executescript("""
CREATE TABLE games(
  id INTEGER PRIMARY KEY,
  bgg_id TEXT,
  match_state TEXT NOT NULL,
  match_algorithm_version INTEGER NOT NULL DEFAULT 0,
  database_visible INTEGER NOT NULL DEFAULT 1,
  verification_state TEXT
);
""")
rows = [
    (1, "", "BGG_MATCH_REVIEW", ALG, 1, None),        # current review: must stay out
    (2, "", "BGG_MATCH_REVIEW", ALG - 1, 1, None),    # legacy review: one-time re-evaluate
    (3, "", "BGG_MATCH_REQUIRED", ALG, 1, None),       # unresolved current work
    (4, "1004", "MATCHED", ALG - 1, 1, "AUTO"),       # historical auto match
    (5, "1005", "MATCHED", ALG, 1, "AUTO"),           # already current historical match
    (6, "1006", "MATCHED", ALG - 1, 1, "USER_CONFIRMED"), # protected
]
db.executemany("INSERT INTO games VALUES(?,?,?,?,?,?)", rows)

current_sql = """
SELECT id FROM games
WHERE database_visible=1
  AND (bgg_id IS NULL OR bgg_id='')
  AND (
    match_state='BGG_MATCH_REQUIRED'
    OR (match_state='BGG_MATCH_REVIEW' AND COALESCE(match_algorithm_version,0)<CAST(? AS INTEGER))
  )
ORDER BY id
"""
current = [r[0] for r in db.execute(current_sql, (str(ALG),))]
assert current == [2, 3], f"wrong current candidate set: {current}"

# Simulate the automatic matcher finishing both currently executable rows as review/current-version.
db.execute("UPDATE games SET match_state='BGG_MATCH_REVIEW', match_algorithm_version=? WHERE id IN (2,3)", (ALG,))
current_after = [r[0] for r in db.execute(current_sql, (str(ALG),))]
assert current_after == [], f"current work did not drain after review transition: {current_after}"

historical_sql = """
SELECT id FROM games
WHERE database_visible=1
  AND bgg_id IS NOT NULL AND bgg_id<>''
  AND match_state='MATCHED'
  AND COALESCE(match_algorithm_version,0)<CAST(? AS INTEGER)
  AND COALESCE(verification_state,'')<>'USER_CONFIRMED'
ORDER BY id
"""
historical = [r[0] for r in db.execute(historical_sql, (str(ALG),))]
assert historical == [4], f"wrong historical candidate set: {historical}"

# Simulate a completed historical audit advancing the algorithm version.
db.execute("UPDATE games SET match_algorithm_version=? WHERE id=4", (ALG,))
historical_after = [r[0] for r in db.execute(historical_sql, (str(ALG),))]
assert historical_after == [], f"historical work did not drain after version advance: {historical_after}"

checks = [
    ("production has no uncast current-version predicate", "COALESCE(match_algorithm_version,0)<?" not in market),
    ("production has no uncast qualified-version predicate", "COALESCE(g.match_algorithm_version,0)<?" not in market),
    ("production uses numeric cast for algorithm predicates", market.count("<CAST(? AS INTEGER)") >= 5),
    ("fixture reproduces old affinity bug", True),
    ("current state-machine drains REQUIRED and legacy REVIEW", current == [2,3] and current_after == []),
    ("historical state-machine drains only eligible automatic matches", historical == [4] and historical_after == []),
]

for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
failed = [name for name, ok in checks if not ok]
if failed:
    raise SystemExit("SQLite BGG state-machine integration failed: " + ", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} executable SQLite BGG state-machine checks")
