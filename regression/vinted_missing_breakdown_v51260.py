#!/usr/bin/env python3
import re
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MARKET = (ROOT / "app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")

match = re.search(
    r'private static final String VINTED_MISSING_BREAKDOWN_SQL\s*=\s*(.*?);\s*\n',
    MARKET,
    re.S,
)
if not match:
    raise AssertionError("MarketStore is missing the authoritative Vinted breakdown query")

parts = re.findall(r'"((?:\\\\.|[^"\\\\])*)"', match.group(1))
sql = "".join(part.replace(r'\\"', '"').replace(r'\\\\', '\\') for part in parts)

db = sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE games(id INTEGER PRIMARY KEY, bgg_id TEXT, rating REAL, database_visible INTEGER, match_state TEXT);
CREATE TABLE market_listings(id INTEGER PRIMARY KEY, game_id INTEGER, lifecycle TEXT, vinted_url TEXT, enrichment_state TEXT, last_error TEXT);
CREATE TABLE processing_jobs(id INTEGER PRIMARY KEY, listing_id INTEGER, job_type TEXT, state TEXT, last_error TEXT, updated_at INTEGER);
""")

reasons = [
    (False, "", "PENDING_ANALYSIS", "", ""),
    (True, "PENDING", "PENDING_ENRICHMENT", "", ""),
    (True, "FAILED_RETRYABLE", "FAILED_RETRYABLE", "Nessun annuncio compatibile trovato nella ricerca Vinted", ""),
    (True, "FAILED_RETRYABLE", "FAILED_RETRYABLE", "Più annunci compatibili: non posso scegliere quello giusto", ""),
    (True, "FAILED_RETRYABLE", "FAILED_RETRYABLE", "Titolo e prezzo non corrispondono abbastanza", ""),
    (True, "FAILED_RETRYABLE", "FAILED_RETRYABLE", "Pagina Vinted non disponibile (404)", ""),
    (True, "FAILED_RETRYABLE", "FAILED_RETRYABLE", "Vinted ha limitato temporaneamente la verifica (HTTP 429)", ""),
    (True, "FAILED_RETRYABLE", "FAILED_RETRYABLE", "Il candidato trovato non supera la verifica della pagina Vinted", ""),
    (True, "", "DEFERRED_LINK", "", ""),
    (True, "FAILED_RETRYABLE", "FAILED_RETRYABLE", "errore sconosciuto", ""),
]

for index, (eligible, job_state, listing_state, job_error, listing_error) in enumerate(reasons, 1):
    db.execute(
        "INSERT INTO games VALUES(?,?,?,?,?)",
        (index, str(1000 + index) if eligible else "", 7.0 if eligible else None, 1, "MATCHED" if eligible else "BGG_UNMATCHED"),
    )
    db.execute(
        "INSERT INTO market_listings VALUES(?,?,?,?,?,?)",
        (index, index, "ACTIVE", "", listing_state, listing_error),
    )
    if job_state:
        db.execute(
            "INSERT INTO processing_jobs VALUES(?,?,?,?,?,?)",
            (index, index, "VINTED_ENRICHMENT", job_state, job_error, 1000 + index),
        )

row = db.execute(sql).fetchone()
expected = (10, 1, 9, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0)
assert row == expected, f"expected={expected} actual={row}"
assert row[2] == sum(row[3:12]), f"eligible outcomes do not reconcile: {row}"
print("PASS Vinted missing-link outcomes are exclusive and reconcile to total")

