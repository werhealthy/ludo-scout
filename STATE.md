# Ludo Scout — Current state

## Verified release

5.12.93-featured-box-refinement (1000110), Firebase upload/distribution confirmed 2026-10-01 07:31 Europe/Rome (05:31 UTC). PR102 merged at 5db9ef233f72956703bedfd044d5ad46e8c14947. Head ca0e6060fee6c1b0245c1b26f5bf2c3b3d84f0b3 passed PR run269 (36819619132): full regressions, Android unit/compile and review APK build. Signed beta run110 (36819845258) succeeded and Firebase logs explicitly confirmed release upload and distribution to testers/groups.

## Current step — isolated featured artwork

- FeaturedBoxGeometry prepares reusable square/tall/wide front, top and side coordinates. Front uses full BGG bitmap, thin side is cover-derived color without invented artwork. Top extends above front, shadow space is reserved.
- FeaturedBoxView caches Paths/Matrix/gradients on bitmap/bounds changes, reuses Paints and geometry, and uses translucent ambient/contact shadow ovals without blur or software layer. Invalid geometry or drawing runtime errors use FIT_CENTER flat bitmap fallback; missing image keeps existing placeholder.
- Hero layout, preferences, selection/ranking and all other cards unchanged. Posting layout reconstruction after traversal is preserved.
- Geometry JUnit covers shape bounds/ratios, top direction and invalid values.63 local guards and full remote PR/beta checks passed; static review checked Canvas state, fallback and shared bitmap handling. PR APK uses a temporary review-only signing key; Firebase beta uses existing signing key.
- No device/emulator visual or frame-time verification. Do not claim realistic rendering from successful build. User must check cover/content visibility, proportions, side/top, contact/ambient shadows, distortions, square/tall/wide behavior and scrolling/flicker on phone.
- Next UI feedback remains pending: keep only Non mi interessa in hero, announcement ellipsis top-right, remove textual Offertona badge, full-page centered Motore loading with more icon/ring spacing and specific action text.

## Delivery preference

Automatically merge verified Ludo Scout updates to beta and publish to Firebase App Tester. Do not ask again for merge or distribution approval. Confirm successful tester distribution before claiming availability. Documentation-only status commits may skip CI to avoid duplicate APKs.

## Previous completed step — Home 5.12.88

- One Home gear opens Motore. Engine ellipsis opens existing settings/diagnostics.
- Featured hero proof of concept uses the real BGG cover in an adaptive 2.5D box renderer: perspective-mapped front, separate top/side planes, cover-derived side tone, restrained front light, and layered contact/ambient shadows. Personalized Mi interessa / Non mi interessa behavior from v89 is preserved; no new dependency was added.
- v90 device screenshot exposed a blank featured card. Root cause was the renderer wrapper being coupled to the hero's dynamic layout rebuild. v91 restores the known-good personalized hero layout and isolates the renderer inside the artwork layer; title, price, CTA and preference controls render independently of the 2.5D box.
- Featured Scopri di più is visibly52dp tall with ripple and larger text.
- Edition flags/names and distinct dependence labels use ordinary Italian, with explicit unknown states and no invented language evidence. Detail language has its own row to preserve BGG visibility.
- Home covers, hero and BGG ranking crop to their frames. Shared section titles use FontAwesome.
- Category Vedi tutte removed, interpreting user transcription as “eviterei”; all categories remain accessible in the horizontal rail.
- Real same-seller bundle spotlight emphasizes2–3covers and direct detail. No invented combined price or explanatory filler. Discovery is bounded40candidates, cached60s and off main thread; rendering receives resolved partners.
- Critical v87 empty-phase-list bug reproduced and fixed: Android rawQuery binds Strings; computed numeric phase has no SQLite column affinity and never equaled the text filter. Phase parameters now explicitly CAST AS INTEGER. Tests bind strings like Android and evaluate actual query methods; count/list parity and trust gates pass.
- Existing dark palette, typography, fixed two-line preview titles, filter functionality, direct phase navigation and eligibility gates preserved.

## Validation

PR100 Android validation passed all regressions and Java compilation. Signed beta run108 passed and Firebase uploaded 5.12.91-featured-box-layout-fix (1000108), added release notes, and distributed to testers/groups successfully. Device screenshot from v90 was used to identify the blank-card regression. v91 still requires user visual validation on device for both content visibility and the actual realism of the 2.5D box.

## Remaining work — do not claim fixed

Latest user diagnostic on v87 reports five post-install UI crashes and one ANR; SQLiteDatabaseLockedException; busy_timeout8000ms; Activity snapshot load5751ms; Vinted PROCESSING lease around94min; queue heartbeat around95min; eight runnable Vinted jobs; current scroll active over five hours. Root cause of queue stalling and lock contention is not yet established. This Home step does not fix those crashes/ANR or claim to unblock the queue.

User requested ordered, reviewable steps rather than one broad rewrite. See docs/specs/2026-09-30-ui-refinement.md:
1. Home — delivered; validate on phone.
2. Motore/stability — NEXT: UI SQLite work, queue leases/watchdog, truthful loading, balanced layout, clear delta baseline/duration.
3. Catalog/detail — clickable filters, meaningful offer labels, larger BGG rating, Material provider actions, finger-controlled transition and navigation relationship. Replacement of Annunci/Bundle/Giochi selector remains an open product decision.
4. Ludo — minimal purple pet inspired by attached reference; large opening presence, collapses on scroll.
5. Collection/shared interactions — bookshelf exploration, liking0–5 with preserved existing ratings, sale-price entry/backfill, bottom-slide modals and purposeful motion.

Recommended next action: install 5.12.91-featured-box-layout-fix and verify that the featured card content is visible again; only then judge whether the 2.5D box itself is visually credible.
