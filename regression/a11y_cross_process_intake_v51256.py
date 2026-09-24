#!/usr/bin/env python3
"""Cross-process Accessibility intake telemetry regression for v5.12.56."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
service = (root / "app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
market = (root / "app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")

event_start = service.index("@Override public void onAccessibilityEvent")
queue_start = service.index("private void queueA11yDiagnosticSnapshot")
scan_start = service.index("private void scheduleScan", queue_start)
event_handler = service[event_start:queue_start]
queue_writer = service[queue_start:scan_start]
diagnostics_start = service.index("public static String diagnostics(Context context)")
diagnostics = service[diagnostics_start:]

checks = [
    ("Vinted events queue a cross-process intake snapshot", "queueA11yDiagnosticSnapshot(" in event_handler),
    ("Accessibility callback does not synchronously write SQLite telemetry", "setDiagnosticState(" not in event_handler),
    ("SQLite mirroring is off the Accessibility callback", "diagnosticIo.execute(" in queue_writer),
    ("telemetry writes are coalesced instead of accumulating", "a11yDiagnosticFlushQueued.compareAndSet(false,true)" in queue_writer and "pendingA11yDiagnosticSnapshot" in queue_writer),
    ("the existing SQLite diagnostics channel is used", 'setDiagnosticState("a11y_intake"' in queue_writer and "SharedPreferences are process-local caches" in market),
    ("the snapshot includes event, parse and analysis freshness", all(k in event_handler for k in ("eventAt=", "cardsParsedTotal=", "analysisBatches=", "localAnalysisLastBatchAt="))),
    ("debug reads intake telemetry from SQLite", 'diagnosticState("a11y_intake")' in diagnostics),
    ("debug uses the cross-process card count and event time", 'parseLongField(a11yIntakePayload,"cardsParsedTotal"' in diagnostics and 'parseLongField(a11yIntakePayload,"eventAt"' in diagnostics),
    ("debug exposes source freshness and payload", "a11yIntakeCrossProcess={authoritative=" in diagnostics),
    ("legacy installs retain SharedPreferences fallback", 'parseLongField(a11yIntakePayload,"cardsParsedTotal",p.getLong("cardsParsedTotal",0))' in diagnostics),
]
failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
if failed:
    raise SystemExit("cross-process intake telemetry regression failed: " + ", ".join(failed))
