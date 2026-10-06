#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/"tools"))

from catalog_full_audit import text_value

audit=(ROOT/"tools/catalog_full_audit.py").read_text(encoding="utf-8")
market=(ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
runner=(ROOT/"app/src/main/java/it/vintedaffari/app/QueueJobRunner.java").read_text(encoding="utf-8")
main=(ROOT/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")

checks=[
    ("null metadata stays missing", text_value(None)=="" and text_value("")=="" and text_value("   ")==""),
    ("real metadata stays present", text_value(" ieri ")=="ieri"),
    ("publication audit is null-safe",
     'publication_value=text_value(publication.get("raw") if isinstance(publication,dict) else "")' in audit),
    ("snapshot replay removed from store", "materializeBrowserSnapshotMetadataBatch" not in market),
    ("snapshot replay removed from queue", "last_browser_snapshot_metadata" not in runner),
    ("snapshot replay removed from UI", "browser_snapshot_ui_replay" not in main),
]

for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)

bad=[name for name,ok in checks if not ok]
if bad:
    raise SystemExit("catalog snapshot null audit regression failed: "+", ".join(bad))

print(f"PASS {len(checks)}/{len(checks)} catalog snapshot null guards")
