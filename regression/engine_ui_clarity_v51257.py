#!/usr/bin/env python3
"""Regression guards for the outcome-oriented Motore information architecture."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
main = (root / "app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
db = (root / "app/src/main/java/it/vintedaffari/app/DealDatabase.java").read_text(encoding="utf-8")

overview_start = main.index("private void renderEngineOverview()")
overview_end = main.index("private View enginePipelineCard", overview_start)
hero_start = main.index("private View engineCurrentRunHero", overview_start)
results_start = main.index("private View engineCurrentResultsCard", hero_start)
funnel_start = main.index("private View engineFunnelRow", results_start)
run_start = main.index("private void renderEngineRun()")
thumb_start = main.index("private View engineRunThumbnailView", run_start)

overview = main[overview_start:overview_end]
hero = main[hero_start:results_start]
components = main[results_start:funnel_start]
run = main[run_start:thumb_start]

checks = [
    ("overview has only pipeline and actionable attention",
     "enginePipelineCard" in overview and "engineAttentionCard" in overview and
     all(token not in overview for token in ("engineCurrentRunHero", "engineWorkQueueCard", "Scroll recenti"))),
    ("phase and central clicks open direct exact data",
     "openEnginePhase(-1,snapshot)" in main and "EnginePipelineSql.items()" in db and
     "private void showEnginePhase" not in main),
    ("overview no longer renders the technical five-stage funnel",
     "engineFunnelRow(" not in overview and "Percorso di questo scroll" not in overview),
    ("hero has concrete states and no percentage progress bar",
     all(copy in hero for copy in (
         "STO RACCOGLIENDO", "Sto riconoscendo i giochi",
         "Sto collegando gli annunci", "In attesa del prossimo controllo Vinted",
         "Sto preparando i risultati")) and "ProgressBar" not in hero),
    ("ready results are explicitly scoped to the current scroll",
     "Risultati di questo scroll" in components and
     "risultati pronti" in components and
     "da questo scroll" in components and
     "nel Catalogo" not in components),
    ("human attention is a separate conditional inbox",
     "Richiedono attenzione" in components and
     "elementi richiedono" in components and
     'engineSection="review"' in components),
    ("unfinished scrolls use user-facing waiting states",
     "Altri scroll" in components and "Riprenderà" in components and "In attesa" in components),
    ("run inspector filters by outcome instead of provider lane",
     '{"all","Tutti"},{"ready","Pronti"},{"working","In lavorazione"}' in run and
     '{"all","Tutti"},{"bgg","BGG"},{"vinted","Vinted"},{"ready","Pronte"}' not in run),
    ("working filter excludes terminal outcomes",
     '"working".equals(mode)&&!x.complete&&!x.review&&!x.held' in db),
    ("overview rendering stays snapshot-only",
     "marketStore." not in overview and "db." not in overview),
]

for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
failed = [name for name, ok in checks if not ok]
if failed:
    raise SystemExit("Motore UI clarity regression failed: " + ", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} Motore UI clarity guards")

