#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
radar=(ROOT/"app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
market=(ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

scan=radar[radar.index("private boolean motoreOwnsPublicVintedLane"):radar.index("private boolean networkPriority")]
resolve=radar[radar.index("private void resolveBacklog"):radar.index("private String maintenanceProgress")]
bundle=radar[radar.index("private void maybeScanBundles"):radar.index("private void finishBundleSeller")]
maybe=bundle[:bundle.index("private void startDeepScan")]
deep=bundle[bundle.index("private void startDeepScan"):bundle.index("private List<SellerBundleScanner.SellerItem> rankProfileCandidates")]
verify=bundle[bundle.index("private void verifyBundleMatches"):]

checks=[
    ("active Motore ownership is the network-priority signal",
     "marketStore.hasActiveObservationRun()" in scan),
    ("bundle backlog does not start during an active Motore run",
     "manualBulkMode||motoreOwnsPublicVintedLane()" in scan),
    ("legacy metadata resolver cannot steal a slot from deferred Motore work",
     "marketStore.jobSummary().active()>0||marketStore.hasActiveObservationRun()" in resolve),
    ("zero-network seller graph remains available before the network guard",
     maybe.index("buildLocalBundlesForSource(source)") < maybe.index("motoreOwnsPublicVintedLane()")),
    ("bundle snapshot/public-page discovery defers to Motore",
     "if(motoreOwnsPublicVintedLane())" in maybe and
     "Motore ha priorità sulla corsia Vinted" in maybe),
    ("already queued deep scan rechecks Motore ownership before network",
     "if(motoreOwnsPublicVintedLane())" in deep and
     "rimandato · Motore ha priorità" in deep),
    ("bundle ownership verification rechecks Motore before public requests",
     "if(motoreOwnsPublicVintedLane())" in verify and
     "Motore ha priorità sulla verifica bundle" in verify),
    ("diagnostic counter exposes how often bundle work yielded",
     'increment("bundleDeferredForMotore")' in radar),
    ("5.12.28 catalog health remains in place",
     "enqueueCatalogHealthCheckIfIdle" in market and "CATALOG_HEALTH_SOURCE" in market),
    ("request-rate strategy is unchanged",
     "PUBLIC_MIN_INTERVAL_MS=55_000L" in (ROOT/"app/src/main/java/it/vintedaffari/app/VintedPublicSession.java").read_text(encoding="utf-8")),
    ("build identity and CI version strategy remain unchanged",
     "applicationId 'it.vintedaffari.app'" in build and
     "1000000 + ciVersionCode.toInteger()" in build),
]
for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("5.12.29 Motore network priority regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} Motore network-priority guards")
