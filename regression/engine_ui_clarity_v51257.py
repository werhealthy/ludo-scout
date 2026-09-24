#!/usr/bin/env python3
"""Regression guards for the user-facing Motore funnel in v5.12.57."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
main = (root / "app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")

overview_start = main.index("private void renderEngineOverview()")
hero_start = main.index("private View engineCurrentRunHero", overview_start)
step_start = main.index("private View engineStepRow", hero_start)
overview = main[overview_start:hero_start]
hero = main[hero_start:step_start]

checks = [
    ("current scroll exposes a five-stage funnel", all(label in overview for label in (
        "Card Vinted uniche", "Giochi idonei", "BGG confermato · voto 6+",
        "Annunci Vinted collegati", "Nel Catalogo"))),
    ("funnel uses the existing coherent run snapshot", all(field in overview for field in (
        "run.uniqueListings", "run.validListings", "run.bggMatchedListings",
        "run.vintedLinkedListings", "run.completeListings"))),
    ("manual review is described as optional and independent", "Il Motore continua anche senza una tua scelta" in overview),
    ("old copy no longer implies the whole engine is blocked", "una tua scelta può far ripartire Ludo" not in overview),
    ("hero states the outcome instead of an ambiguous processed fraction", "risultati con un esito" in hero and '" elaborati"' not in hero),
    ("overview funnel does not add direct SQLite reads", "marketStore." not in overview and "db." not in overview),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
if failed:
    raise SystemExit("engine UI clarity regression failed: " + ", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} engine UI clarity guards")
