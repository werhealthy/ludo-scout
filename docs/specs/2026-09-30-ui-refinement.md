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
