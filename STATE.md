# Ludo Scout — Current state

## Verified release

5.12.90-featured-box-poc (1000107), distributed 2026-10-01 00:40 Europe/Rome (2026-09-30 22:40 UTC). PR99 merged to beta at 8039f8ed81e69c350413552f809fb8cb9177ff30. Head 53502a2b3eeae414f9a9ff6906a58545887150f1 passed Android PR run262 (36786299279). Signed beta run107 (36786535211) passed; Firebase confirmed successful new release upload and tester/group distribution.

## Delivery preference

Automatically merge verified Ludo Scout updates to beta and publish to Firebase App Tester. Do not ask again for merge or distribution approval. Confirm successful tester distribution before claiming availability. Documentation-only status commits may skip CI to avoid duplicate APKs.

## Previous completed step — Home 5.12.88

- One Home gear opens Motore. Engine ellipsis opens existing settings/diagnostics.
- Featured hero proof of concept uses the real BGG cover in an adaptive 2.5D box renderer: perspective-mapped front, separate top/side planes, cover-derived side tone, restrained front light, and layered contact/ambient shadows. Personalized Mi interessa / Non mi interessa behavior from v89 is preserved; no new dependency was added.
- Featured Scopri di più is visibly52dp tall with ripple and larger text.
- Edition flags/names and distinct dependence labels use ordinary Italian, with explicit unknown states and no invented language evidence. Detail language has its own row to preserve BGG visibility.
- Home covers, hero and BGG ranking crop to their frames. Shared section titles use FontAwesome.
- Category Vedi tutte removed, interpreting user transcription as “eviterei”; all categories remain accessible in the horizontal rail.
- Real same-seller bundle spotlight emphasizes2–3covers and direct detail. No invented combined price or explanatory filler. Discovery is bounded40candidates, cached60s and off main thread; rendering receives resolved partners.
- Critical v87 empty-phase-list bug reproduced and fixed: Android rawQuery binds Strings; computed numeric phase has no SQLite column affinity and never equaled the text filter. Phase parameters now explicitly CAST AS INTEGER. Tests bind strings like Android and evaluate actual query methods; count/list parity and trust gates pass.
- Existing dark palette, typography, fixed two-line preview titles, filter functionality, direct phase navigation and eligibility gates preserved.

## Validation

PR99 Android validation passed all regressions and Java compilation. Signed beta run107 passed, uploaded 5.12.90-featured-box-poc (1000107) to Firebase, and Firebase reported distribution to testers/groups successful. No emulator/device visual validation was performed: the renderer's realism, shadow quality, and behavior across real wide/tall/square BGG covers must be judged on the user's phone before treating this visual direction as approved.

## Remaining work — do not claim fixed

Latest user diagnostic on v87 reports five post-install UI crashes and one ANR; SQLiteDatabaseLockedException; busy_timeout8000ms; Activity snapshot load5751ms; Vinted PROCESSING lease around94min; queue heartbeat around95min; eight runnable Vinted jobs; current scroll active over five hours. Root cause of queue stalling and lock contention is not yet established. This Home step does not fix those crashes/ANR or claim to unblock the queue.

User requested ordered, reviewable steps rather than one broad rewrite. See docs/specs/2026-09-30-ui-refinement.md:
1. Home — delivered; validate on phone.
2. Motore/stability — NEXT: UI SQLite work, queue leases/watchdog, truthful loading, balanced layout, clear delta baseline/duration.
3. Catalog/detail — clickable filters, meaningful offer labels, larger BGG rating, Material provider actions, finger-controlled transition and navigation relationship. Replacement of Annunci/Bundle/Giochi selector remains an open product decision.
4. Ludo — minimal purple pet inspired by attached reference; large opening presence, collapses on scroll.
5. Collection/shared interactions — bookshelf exploration, liking0–5 with preserved existing ratings, sale-price entry/backfill, bottom-slide modals and purposeful motion.

Recommended next action: inspect the featured card on 5.12.90-featured-box-poc with several real BGG covers; decide whether the 2.5D renderer is visually credible before refining it or extending it elsewhere.
