#!/usr/bin/env python3
"""Cross-process Accessibility intake telemetry regression for v5.12.56."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
service = (root / "app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
market = (root / "app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")

event_start = service.index("@Override public void onAccessibilityEvent")
snapshot_start = service.index("private void publishA11yDiagnosticSnapshot")
scan_start = service.index("private void scheduleScan", snapshot_start)
event_handler = service[event_start:snapshot_start]
snapshot_writer = service[snapshot_start:scan_start]
diagnostics_start = service.index("public static String diagnostics(Context context)")
diagnostics = service[diagnostics_start:]

scan_method = service.index("private void scanVisibleVintedCards")
scan_counter_write = service.index('.putLong("cardsParsedTotal"', scan_method)
scan_publish = service.index("publishA11yDiagnosticSnapshot();", scan_counter_write)
analysis_result = service.index("@Override public void onResult(List<GameAnalysis> analyses)")
analysis_error = service.index("@Override public void onError(String message)", analysis_result)
analysis_callback = service[analysis_result:analysis_error]

checks = [
    ("Vinted events queue a cross-process intake snapshot", "publishA11yDiagnosticSnapshot();" in event_handler),
    ("Accessibility callback does not synchronously write SQLite telemetry", "setDiagnosticState(" not in event_handler),
    ("SQLite mirroring is off the Accessibility callback", "diagnosticIo.execute(" in snapshot_writer),
    ("telemetry writes are coalesced instead of accumulating", "a11yDiagnosticFlushQueued.compareAndSet(false,true)" in snapshot_writer and "pendingA11yDiagnosticSnapshot" in snapshot_writer),
    ("snapshot writes are time-throttled during rapid scroll events", "A11Y_DIAGNOSTIC_MIN_WRITE_MS=2_000L" in service and "a11yDiagnosticPublishScheduled" in snapshot_writer),
    ("the existing SQLite diagnostics channel is used", 'setDiagnosticState("a11y_intake"' in snapshot_writer and "SharedPreferences are process-local caches" in market),
    ("the snapshot includes event, parse and analysis freshness", all(k in snapshot_writer for k in ("eventAt=", "cardsParsedTotal=", "analysisBatches=", "localAnalysisLastBatchAt="))),
    ("completed Accessibility parses publish updated counters", scan_publish > scan_counter_write),
    ("completed local analyses publish their completion time", "publishA11yDiagnosticSnapshot();" in analysis_callback and "localAnalysisLastBatchAt" in analysis_callback),
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
