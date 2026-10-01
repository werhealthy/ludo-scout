# Ludo Scout — UI refinement, five reviewable steps

User request: 2026-09-30, installed 5.12.87. Validate each delivered step on Firebase App Tester before moving to the next. Automatic verified merge/distribution remains authorized.

## 1. Home — current implementation
One gear opens Motore. Engine ellipsis leads to existing settings/diagnostics. Featured CTA visibly52dp with ripple and14sp text. Flags + edition names and readable independence labels; unknown facts remain explicit, never inferred. All Home covers crop to their frame, including featured and BGG ranking. Section headings use FontAwesome. Remove category Vedi tutte (interpreting the transcription as “eviterei”; all categories remain horizontally accessible). Real same-seller bundle spotlight with2–3covers and direct detail; no invented combined price or explanatory filler. Bundle discovery bounded and cached off main thread.

Also repair the reproduced phase-list bug immediately: Android binds rawQuery selectionArgs as strings; the computed numeric phase has no column affinity and phase=? never matches. Explicit parameter CAST plus fixtures using Android-equivalent string bindings. Do not claim this fixes queue stalls/SQLite locks.

## 2. Motore and stability — next recommended step
Diagnostic reports five post-install crashes and one ANR, SQLiteDatabaseLockedException in UI, snapshot load5751ms, busy_timeout8000ms, a Vinted PROCESSING lease5670595ms, queue heartbeat5732255ms, eight runnable Vinted jobs, and active scroll over five hours. These are stale/blocked activity evidence, not proof of a specific queue-owner root cause. Audit UI synchronous reads, cross-process SQLite and lease/watchdog recovery before adding broad motion.

Rebalance overview to available screen height. Full loading state with animation, no top-line debug text. Keep exact direct phase drilldown. Establish durable delta semantics before implementation: proposal is comparison with last seen counts of the same observation scroll, labeled “Dall’ultima visita”; show green/red deltas throughout the visit rather than disappearing after three seconds; update baseline on leaving, animate once on reentry, and explicitly reset when the scroll changes. This proposal requires product review, not an assumed final decision. Separate actual PROCESSING activity from waiting. Include reproducible real string-bind fixtures and device debug evidence.

## 3. Catalog and details — pending design review
Clickable taxonomy chips in game detail/preview where useful. Prominent meaningful deal label and raw BGG rating; reduce the chip wall. Smaller provider identity, Material-style bottom actions BGG/Vinted. Rethink Annunci/Bundle/Giochi navigation: relationship between catalog game and market listings differs from same-seller bundles. User explicitly has not settled a new navigation model; present concrete alternatives before replacing the selector.

Controlled announcement→game gesture: only at content end, continued intentional finger movement fills a visible circular indicator and a label describing the transition; release before threshold cancels, completion enters preloaded game view without an intermediate loading screen. Keep explicit accessible game navigation, reduced-motion behavior and no accidental transition from normal scrolling. Bundle catalog cards and filler descriptions also reviewed here.

## 4. Ludo — pending
Purple bold minimal pet inspired by the user’s attached reference (purple cloud-like body, expressive eyes). Opening view gives pet most of the screen; scrolling collapses it progressively and reveals useful advice. It is the app mascot, not a ChatGPT Work pet. No analytics/stat clutter. Decide desired visual/motion prototype before replacing all mascot assets.

## 5. Collection and shared interactions — pending
Explore a subtle bookshelf presentation of covers. Personal liking is the leading preview fact (simple0–5stars/hearts); preserve existing stored ratings and clarify conversion from0–10 rather than overwrite data. Remove played/unplayed and aggregate payment/count clutter from primary hierarchy. Sold games remain in Sold with actual sale price; ask sale price on sale and permit adding missing historical price, never invent it from purchase price.

Add-game and other task modals should slide upward from bottom with coherent background scrim; blur only where platform supports it. Review keyboard/insets, dismissal, accessibility and small purposeful animations. Loading states show actual progress only when measurable; otherwise honest indeterminate animation.

## Delivery checks
Every implemented step: current regression workflow + Android/JUnit compilation; code review; signed beta APK; Firebase upload and tester distribution confirmation. Local environment has no working JDK or Android emulator, so device visual/gesture validation comes from App Tester and user feedback.

## 2026-10-01 — First Motore layout milestone delivered
5.12.98-engine-layout: header/page spacing, bars/cutout helper, one grouped surface for current scroll/state, independent waiting-scroll/global-intake actions and unchanged five-phase graph. Progressive phase cards use actual available BGG and legacy Vinted fields, with background enrichment/artwork decode. Raw rows remain visible; exact phase membership/publication gates and schema unchanged. Shared game/announcement detail padding is consistent. CI/regression/SQLite/JUnit/compile/APK/signing and Firebase upload/distribution confirmed; device visual approval pending.

This delivery does not close the stability or motion requirements in section2. Recorded PROCESSING activity is not proof of a healthy lease. Current deltas compare successive fresh snapshots of the same scroll and fade after~3seconds; the older “Dall’ultima visita” proposal still requires a dedicated decision. User chose compact waiting-scroll actions, superseding the extra “Da analizzare” circle. No navigation selector, mascot, Library or queue rewrite was included.


## 2026-10-01 — Product box stage delivered (latest decision)
User put Motore on standby and requested foreground product artwork throughout product details.5.12.99-product-box-stage (1000116), PR108 merge1c3152ecce6e3d94728be055700cb8b89f10c584, is uploaded and distributed through Firebase App Tester at09:38 Europe/Rome. PR279 and signed beta116 passed checks.
Visible cover front shares pedestal center; side depth13%, colors derived from real cover with preserved hue/saturation, stronger contact shadow. Existing PNG preserved. Home square stage now82% available width capped240dp, two-line description. Catalog game, game overlay, listing and Library details use the same transparent stage and continuous page background. Real listing photos remain available in separate existing gallery; unknown-BGG listings retain photos. No changes to navigation, dependency, schema, publication gates or queue ownership. Decode and metadata fallback remain off UI thread.
Automated verification includes the centering test failing before correction then passing, existing regressions/SQLite fixtures, Android JUnit, Java compilation and signed APK. No device/emulator available. Pending visual review: square/tall/wide cover integrity, bright-front/pedestal alignment, contact shadow and depth/colors, compact Home balance, and listing photo access. Next milestone is phone visual approval/refinement of this shared stage; Motore stability/motion and other historical tasks remain pending.


## 2026-10-01 — Home light scene and product refinement (latest phone feedback)
5.12.100-home-light (1000117), PR109 merge3e828d3792bfd0a2978665238f9303b4d61968fa, uploaded/distributed to Firebase testers at10:36 Europe/Rome. PR282 and signed beta117 checks succeeded.
Phone feedback supersedes the13% box depth: use8.5% width with darker top/side tones derived from the actual cover. Preserve front/pedestal centering, complete cover and existing PNG/contact shadows. Listing photos are clickable64dp thumbnails below artwork, excluding BGG cover and keeping real gallery access; no text photo button. BGG pill logo is20dp bounded.
Home now uses a pale continuous full-width greeting/product scene, no featured card boundary, dark readable copy and CTA. A144dp content-relative angular purple/blue light transition blends into dark category/offer sections. Cached shaders/paths avoid draw allocations; enlarged copy moves the transition with actual content. Home status-bar appearance is light scoped to Home. Category order, preferences, publication gates and navigation remain. No assets/dependencies/schema/engine changes; Motore remains on standby.
Automated checks passed and336geometry cases stayed within bounds. Visual review remains pending on device: light-to-dark composition, subtler darker side, thumbnail/full-gallery behavior, BGG icon and enlarged text. No emulator/device available here. The next single step is phone visual approval/refinement of this direction.


## 2026-10-01 — Light Home rejected; product page approved (latest decision)
User explicitly requested return to black/dark Home and liked the product page. This supersedes the preceding light scene/transition direction.5.12.101-home-dark (1000118), PR110 mergeb4bfef8cc956cdb2883d7b6effaf4b56214cedbe, uploaded/distributed to Firebase App Tester at10:59 Europe/Rome. PR283 and signed beta118 passed all existing checks.
Restore5.12.99 dark Home composition/header/featured card/status-bar appearance. Preserve5.12.100 shared product stage (8.5% depth, darker sides, contact shadow), listing photo thumbnails/gallery and bounded20dp BGG logo; direct source comparison confirms preservation. Product-page visual direction approved by user. No device/emulator available here; verify restored Home on phone. Motore stays on standby. Next milestone: Catalog preview hierarchy/layout with existing navigation and filters preserved.
