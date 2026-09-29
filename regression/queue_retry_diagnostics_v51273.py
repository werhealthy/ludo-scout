#!/usr/bin/env python3
from pathlib import Path

source=(Path(__file__).resolve().parents[1]/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
start=source.index("public String engineCoreRemainingSummary(){")
end=source.index("public int missingVintedCoreCount()",start)
summary=source[start:end]

checks=[
    ("core summary selects retry type, source and next-attempt time",
     "COALESCE(j.job_type,'')" in summary and
     "COALESCE(j.source,'')" in summary and
     "COALESCE(j.next_attempt_at,0)" in summary),
    ("core summary reports retry timing and job origin",
     'append(";type=")' in summary and
     'append(";source=")' in summary and
     'append(";nextAttemptInMs=")' in summary),
    ("core summary preserves the last failure reason",
     'append(";why=")' in summary),
]

for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("queue retry diagnostics regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} queue retry diagnostic guards")
