#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
activity = (ROOT / "app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
service = (ROOT / "app/src/main/java/it/vintedaffari/app/QueueKeepAliveService.java").read_text(encoding="utf-8")
maintenance_path = ROOT / "app/src/main/java/it/vintedaffari/app/EngineStartupMaintenance.java"
maintenance = maintenance_path.read_text(encoding="utf-8") if maintenance_path.exists() else ""

on_create = activity[activity.index("@Override protected void onCreate"):activity.index("@Override protected void onNewIntent")]
checks = [
    ("Activity opens its shell without running engine startup cutovers", all(name not in on_create for name in (
        "applyUxFreshStartIfNeeded", "applyOperationalEpochIfNeeded",
        "applyTurnaroundReviewCutoverIfNeeded", "quarantineLegacyBggReviewBacklog",
        "repairV51125CollisionCleanup", "compactVintedBacklogToLiveLane"))),
    ("legacy startup-cutover helpers no longer live in the Activity", all(name not in activity for name in (
        "private void applyUxFreshStartIfNeeded", "private void applyOperationalEpochIfNeeded",
        "private void applyTurnaroundReviewCutoverIfNeeded"))),
    ("queue foreground service owns startup maintenance", "EngineStartupMaintenance.run(this,market)" in service),
    ("startup cutovers are grouped behind one queue-process entry point", "static void run(Context context, MarketStore market)" in maintenance),
    ("existing first-install archive remains restart-safe", "v5121FreshStartApplied" in maintenance and "freshStartLegacyBacklog" in maintenance),
    ("operational epoch remains persisted and idempotent", "startOperationalEpochIfMissing" in maintenance and "v51216OperationalEpochApplied" in maintenance),
    ("review archive and product sweep remain restart-safe", "archiveAutomaticReviewDebtBefore" in maintenance and "v51221ProductNoiseSweepApplied" in maintenance),
    ("legacy one-time queue repairs remain accounted for", all(name in maintenance for name in (
        "quarantineLegacyBggReviewBacklog", "repairV51125CollisionCleanup",
        "compactVintedBacklogToLiveLane", "v51268SafeModePricing"))),
]
for label, ok in checks:
    print(("PASS " if ok else "FAIL ") + label)
failed = [label for label, ok in checks if not ok]
if failed:
    raise SystemExit("Single-owner startup-maintenance regression failed: " + ", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} single-owner startup-maintenance guards")
