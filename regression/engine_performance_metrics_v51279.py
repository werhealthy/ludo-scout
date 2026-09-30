#!/usr/bin/env python3
"""Guard queue timing and explicit accounting for gaps between samples."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/it/vintedaffari/app"

tracker = APP / "EnginePerformanceMetrics.java"
assert tracker.is_file(), "EnginePerformanceMetrics must exist before timing can be reported"
tracker_source = tracker.read_text(encoding="utf-8")
for field in ("pacingMs", "runnableMs", "processingMs", "firstResult", "completion"):
    assert field in tracker_source, f"timing tracker omits {field}"
for api in ("sample(", "summary(", "serialize(", "restore("):
    assert api in tracker_source, f"timing tracker omits {api}"

queue = (APP / "QueueKeepAliveService.java").read_text(encoding="utf-8")
diagnostics = (APP / "VintedAccessibilityService.java").read_text(encoding="utf-8")
assert "recordEnginePerformance(activeRun,now)" in queue and "enginePerformance.sample(" in queue, \
    "queue timing must sample on the existing supervisor pulse"
assert '"enginePerformance={' in diagnostics, "copied diagnostics must expose queue timing"
assert "MedianMs" in tracker_source and "WorstMs" in tracker_source and "sampleCount" in tracker_source
assert "noProgressWorstMs" in tracker_source, "diagnostics must expose observed time without run progress"
assert "unobservedMs" in tracker_source, "sample gaps outside the measured interval must be reported, not discarded"
assert "build=engine-performance-v2" in tracker_source, "gap accounting must be visible as the updated metric contract"
print("PASS engine timing exposes bounded per-run medians/worst and queue-state durations")
