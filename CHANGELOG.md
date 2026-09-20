# Ludo Scout — Changelog

## Git workflow / CI bootstrap
- Published the Git-ready 5.12.3 baseline to private GitHub repository `werhealthy/ludo-scout`.
- Created `beta` from `main` for test-build integration.
- `Android beta` now runs automatically on pushes to `beta` and remains manually triggerable.
- The workflow builds a signed debug APK with the preserved developer signing identity, verifies the certificate fingerprint, uploads the artifact and distributes it through Firebase App Distribution.
- CI requires the documented BGG, signing and Firebase GitHub Secrets; none are stored in the repository.

## 5.12.4 — Performance stability
- Android beta CI now runs on pushes to `beta` as well as manual dispatch, so a merged beta commit is built, signature-checked and distributed automatically.
- Paginated Motore run inspector to 24 rows per page and removed per-row Deal/MarketListing lookups used only for thumbnails.
- Coalesced Motore refreshes and OperationCenter reconciliation to stop repeated full view-tree rebuilds and queued 1,200-row scans.
- Retired the old deep Test-1 Accessibility identity probe from production; kept only a bounded explicit Vinted URL/ID check.
- Batched Accessibility event diagnostic writes instead of persisting SharedPreferences on every Vinted event.
- Serialized screenshot capture, added low-heap guards and RGB_565 remote thumbnail decoding, and reduced the UI bitmap cache.
- Added short active-run caching and lightweight waiting-run counting to reduce repeated SQLite work in queue loops.
- Added `regression/performance_stability_v5124.py` and wired it into the Android beta workflow before the Android build.
- No database/schema migration. Resolver safety, BGG/Vinted identity separation, active-run scoping, BGG <6 filtering and persistent review semantics are unchanged.

## 5.12.3 — Engine correctness
- Dedicated Motore run inspector instead of redirecting run details to Catalogo.
- Active-run scoped Vinted/BGG ordinary processing; newer runs wait.
- Zero-network snapshot batch may continue during HTTP pacing/cooldown.
- Stronger ready/deal/Hunt identity gates.
- Persistent BGG/Vinted review behavior.
- Variant-pending cases that cannot progress automatically become review.
- Thumbnail/UI bitmap memory-pressure safeguards.
- `engineRun` diagnostics.

## Git workflow migration preparation
- Removed tracked/local `secrets.properties` from the Git-ready copy.
- Added `secrets.properties.example` only as documentation.
- `BGG_TOKEN` can now come from Gradle user properties, CI environment, or legacy local file, in that order.
- Expanded `.gitignore` for secrets, signing material, build products and local IDE state.
- Added `AI_HANDOFF.md` as cross-chat technical source of context.
