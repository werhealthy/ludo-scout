# Ludo Scout — Current state

## Verified release

5.12.95-home-scrolls (1000112), Firebase upload and tester distribution explicitly confirmed 2026-10-01 08:19 Europe/Rome (06:19 UTC). PR104 merged at cd656b1ba47ff2d50505111fe0332734f3268060. Head40992671e3a5be259e6625e2fa00eb79f3b9ceb9 passed PR run272 (36823496955): full regressions, Java-generated SQLite, Android JUnit/compile and review APK. Signed beta run112 (36823791143) succeeded with verified Firebase distribution.

## Pending step 5.12.96 — Featured hero with supplied pedestal

- User approved hero-only implementation after read-only audit. Keep Motore/Catalogo/other cards and selection/preferences unchanged. Motore header/job spacing and grouping remain next separate step; current upper body/header padding4dp creates cramped top and disconnected waiting rows.
- Hero now has discrete pill, larger2-line title, description up to3lines for all modes, rating/language, unstruck single price,52dp white CTA. One geometry/renderer and PNG for square/tall/wide; wide/narrow/large-font layout fallback retained. Renderer remains isolated from hero content and layout rebuild remains posted, never in layout callback.
- Supplied1000362898.png was actually JPEG/RGB without alpha. Built-in imagegen background-extraction produced real RGBA PNG2172x724 at app/src/main/res/drawable-nodpi/featured_game_pedestal.png. Prompt: remove only black background, preserve purple elliptical cylinder, dark front, violet rim and controlled glow; no new objects/box/text/redesign, transparent outside silhouette/glow. Generated variant is for user visual validation; do not claim exact pixel preservation.
- Featured bitmap/pedestal decode uses image executor; PNG optional resource lookup once, ARGB_8888/inScaled=false/downsampled<=1200width. Cover cache/remote fallback preserved; placeholders remain until valid bitmap. Missing/rejected pedestal or invalid geometry yields flat real cover. No new dependency, software blur, frame bitmap allocations or invented side text/art.
- Geometry uniformly scales the PNG and positions box/contact shadow at40% asset height (top ellipse surface); front remains original BGG artwork with narrow7.5%depth, dark muted side withcharcoal fallback, small top and edge highlights. PNG supplies ambient glow; procedural contact shadow only.
- Validation so far:67 local source/SQLite scripts passed, RGBA/transparent corner PNG regression passed, independent read-only review foundno blockers andnumeric sweep confirmedbounds over ratios0.15..6. New JUnit covers square/tall/wide fitting, uniformPNG ratio, surfaceanchor andinvalidpedestal fallback. Full remote CI/Java/JUnit/APK and Firebase distribution still required.
- Device approval needed: square/tall/wide, front readability, depth/color, actual contact withbase, description/CTA/clipping, noblankhero/flicker, scroll smoothness. No device/emulator visual verification here.

## Previous step 5.12.95 — Home reference and waiting scroll row

- User selected compact waiting-scroll row instead of intake circle. Count/list share lightweight temporal grouping after current owner's end, no joined enrichment. Dedicated background list opens an observation-backed phase=-3 list, including raw cards without price/BGG. Review caught that old run inspector requires canonical game joins; it is deliberately bypassed here. Global pending analysis remains a separate compact action; never sum announcement count with scrolls/games.
- Hero removes visible dismissal action; personalization still available on catalog previews and unchanged. Artwork background transparent; title/rating/language/description/real used-market benchmark/CTA in copy column for square/tall, wide/narrow/large-font stacked fallback. Renderer remains isolated; no realism claim. Pedestal asset not supplied yet, await user asset.
- Device v94 snapshot 67ms; intake opt-in OFF ~13h, queue heartbeat recent and processing lease35s. Old run remains ACTIVE with unresolved/retry work; do not claim stalled runs or SQLite crash root fixed. v94 install has0 post-install crashes/ANRs; older radar SQLiteBusy crash predates install.
- Validation:66 local regression scripts passed, extracted production SQLite query fixture confirms missing BGG/price does not hide raw cards and repeat signatures deduplicate. Independent read-only review passed after raw route fix. Full PR/beta checks, Java/JUnit/compile/APK and Firebase distribution succeeded. Device visual verification still pending.

## Previous Motore/UI step — 5.12.94

- Separate Da analizzare circle is GLOBAL announcement intake, not another active-scroll game phase. Shows canonical active PENDING_ANALYSIS listings plus latest raw pending sightings without any canonical listing. Raw signatures deduplicate and completion wins by (observed_at,id). Count/list use same SQL; direct phase=-2 route works even with no pipelineRun. Data remains truthful while queue is paused. Count changes animate once; throttled background arrivals request fresh snapshots. Existing 5-phase scroll logic and publish/trust gates preserved; do not sum global announcement intake with scoped game phases.
- Query plan reproduced full SCAN market_listings for COALESCE(NULLIF(legacy_signature,''),temp_fingerprint) joins. Equivalent indexed OR joins now use existing legacy/fingerprint indexes in DealDatabase and EnginePipelineSql. No schema migration, new index, queue ownership or data changes. Parity test covers null/empty/overridden legacy identity; phase SQL fixtures retain all trust/readiness gates.
- Main Motore initial load is blank page with centered animation/activity text; icon38 inside52dp and spinner144dp in168dp frame leave clear spacing. Stale data remains visible while refreshing.
- Hero large interest buttons removed. Single muted Non mi interessa action retains48dp hit target, announcement tap and game exclusion via long-press popup. Catalog announcement ellipsis now overlays top-right artwork; text offer labels removed from catalog previews.
- Validation:65 local source/SQL tests passed; full remote PR/beta tests/compile/APK passed. Static review caught latest raw same-timestamp completion and fix is in production fixture. User/device validation pending: incoming paused intake, direct list, count drop after analysis, load time, spacing and preview actions.
- User rejected apparent flat framed box on device and explicitly paused box work; do not claim renderer realism or revisit before other UX tasks. Shared SQLite crashes/ANR and stalled processing lease are NOT established fixed. Indexed query correction addresses demonstrated cost, not all lock/queue behavior.
- Remaining steps: rich phase cards with available BGG/Vinted data; investigate latest device timing/queue stall; finish catalog/detail actions and controlled transition, Ludo purple pet, collection ratings/sold prices and shared bottom sheets.

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
