# Ludo Scout — Current state

## Verified release

5.12.89-home-personalized (1000106), distributed 2026-10-01 00:36 Europe/Rome (2026-09-30 22:36 UTC). PR98 merged to beta at 120efc03bd30a2c938e83b7d071a2aa75eb37356. Head 6ef8d2d93a59e3c1b8d323a0fc33edca9d279189 passed Android PR run261 (36785959533). Signed beta run106 (36786129627) passed; Firebase logs explicitly confirmed successful release upload and tester/group distribution.

## Current completed refinement — Home

- Per-game interest/dismiss preferences persist on device. Non mi interessa swaps the hero and has Undo. Owned unsold Library games and confirmed non-IT/EN dependent editions are excluded from Home, including bundle partners.
- Featured cover uses square/portrait/landscape compositions, with 52dp CTA and 48dp preference targets. Compact flags/lock icons share readable help; unknown metadata is preserved.
- Actual discount percentages use bands 0–9, 10–29, 30–79, 80+ across featured/product/top-rated cards. Product rails expose two full cards and half a third; publication dates and greeting spacing improved.
- Categories use independent persisted filters, visible numbered filter badges, and free-text search stays separate. Production SQL fixtures cover typed category, free text, eligibility, count and paging parity.
- Normal cold launcher entry defaults Home; explicit intents and saved activity recreation preserve destinations.
- Validation: PR and signed beta succeeded, including Android unit tests and compile, category/pipeline/canonical SQLite fixtures.62 local source/SQL checks passed. Static review caught badge/color gaps; CI caught a local-name collision, fixed before merge. No local device/emulator pixel validation.

## Delivery preference

Automatically merge verified Ludo Scout updates to beta and publish to Firebase App Tester. Do not ask again for merge or distribution approval. Confirm successful tester distribution before claiming availability. Documentation-only status commits may skip CI to avoid duplicate APKs.

## Previous completed step — Home 5.12.88

- One Home gear opens Motore. Engine ellipsis opens existing settings/diagnostics.
- Featured Scopri di più is visibly52dp tall with ripple and larger text.
- Edition flags/names and distinct dependence labels use ordinary Italian, with explicit unknown states and no invented language evidence. Detail language has its own row to preserve BGG visibility.
- Home covers, hero and BGG ranking crop to their frames. Shared section titles use FontAwesome.
- Category Vedi tutte removed, interpreting user transcription as “eviterei”; all categories remain accessible in the horizontal rail.
- Real same-seller bundle spotlight emphasizes2–3covers and direct detail. No invented combined price or explanatory filler. Discovery is bounded40candidates, cached60s and off main thread; rendering receives resolved partners.
- Critical v87 empty-phase-list bug reproduced and fixed: Android rawQuery binds Strings; computed numeric phase has no SQLite column affinity and never equaled the text filter. Phase parameters now explicitly CAST AS INTEGER. Tests bind strings like Android and evaluate actual query methods; count/list parity and trust gates pass.
- Existing dark palette, typography, fixed two-line preview titles, filter functionality, direct phase navigation and eligibility gates preserved.

## Validation

All PR/beta workflows passed, including production Java-generated SQLite fixtures, Android JUnit, Java compilation and signed APK.62 current workflow source/SQL regressions also passed locally; code review caught and verified fixes for bundle UI cost and detail language clipping. No local Android emulator/device visual validation. User reviews this step through App Tester.

## Remaining work — do not claim fixed

Latest user diagnostic on v87 reports five post-install UI crashes and one ANR; SQLiteDatabaseLockedException; busy_timeout8000ms; Activity snapshot load5751ms; Vinted PROCESSING lease around94min; queue heartbeat around95min; eight runnable Vinted jobs; current scroll active over five hours. Root cause of queue stalling and lock contention is not yet established. This Home step does not fix those crashes/ANR or claim to unblock the queue.

User requested ordered, reviewable steps rather than one broad rewrite. See docs/specs/2026-09-30-ui-refinement.md:
1. Home — delivered; validate on phone.
2. Motore/stability — NEXT: UI SQLite work, queue leases/watchdog, truthful loading, balanced layout, clear delta baseline/duration.
3. Catalog/detail — clickable filters, meaningful offer labels, larger BGG rating, Material provider actions, finger-controlled transition and navigation relationship. Replacement of Annunci/Bundle/Giochi selector remains an open product decision.
4. Ludo — minimal purple pet inspired by attached reference; large opening presence, collapses on scroll.
5. Collection/shared interactions — bookshelf exploration, liking0–5 with preserved existing ratings, sale-price entry/backfill, bottom-slide modals and purposeful motion.

Recommended next action: inspect Home on1000106, then address Motore crashes/stalled queue before deeper animations. Application identity, signing, acquisition, queue ownership and publish/readiness/review/price gates unchanged.
