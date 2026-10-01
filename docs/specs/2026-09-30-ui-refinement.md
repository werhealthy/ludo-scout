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


## 2026-10-01 — Catalog cards delivered (latest)
5.12.102-catalog-cards (1000119), PR111 mergeb757202670b76da576850e9838a1a1e65881ddc5, uploaded/distributed through Firebase App Tester at11:22 Europe/Rome. PR284 and signed beta119 passed checks.
Catalog cards use square BGG cover, publication date, max-two-line title, BGG rating, compact edition, real price and saving only when verified; their action is a48dp target. Search/filters/category active state/order/pagination/detail routes unchanged. No changes to schema, dependencies, queue or navigation structure. Device review pending: density/height with normal and large text; historical date, unknown rating/language, saving/no-saving examples. The Annunci/Bundle/Giochi selector is still unresolved and should not be replaced without a product decision.


## 2026-10-01 — Catalog phone review additions
For the next Catalog refinement, preserve the current card structure and make three targeted changes: (1) top-right ellipsis must remain a48dp accessible target but have a smaller, less prominent visible circle; (2) unknown publication date is displayed as “?” rather than a full “Data pubblicazione n/d” label; (3) discounts need an explicit visual scale. Proposed product rule: 0–19% neutral slate, 20–34% blue, 35–49% teal, 50%+ green as “Offertona”. Only verified saving data receives a color/badge; no inferred original prices.

## 2026-10-01 — Grouped Phase5: market previews and navigation proposal
User requests fewer, broader updates combining related improvements. Group card hierarchy, Bundle previews and Catalog navigation review in one milestone; preserve separate approval for material navigation changes. Future delivery groups: (A) Catalog/market previews and navigation; (B) product details, language/personalization and controlled transitions; (C) Library/shared sheets and accessibility; (D) Ludo mascot once reference is available; (E) Motore stability and motion after resuming it.

Implemented preview scope:
- Home BGG ranking reuses discoverDiscountBadge, hence the same real-saving data and0–19/20–34/35–49/50+ palette. Rating22sp with16sp star, title16sp capped2lines, square68dp artwork and compact rank medal26dp. Price and rating occupy distinct areas; BGG rank stays secondary and voter evidence remains accessible. Narrow/large-font layouts stack values and facts; no fixed text heights.
- Catalog confirmed Bundle cards emphasize actual seller, game count, up to3existing cover fronts and2-line group title, opening existing Bundle detail. Replace the inherited tall Home tile only in the Bundle Catalog list. Do not promote estimated offer totals as an actual combined purchase price. Existing game eligibility, grouping, order, prospect section and detail calculations/routes preserved.

Navigation proposal — NOT APPROVED / NOT IMPLEMENTED:
- Keep Annunci as default Catalog entry for shopping; two equal tabs: Annunci and Giochi.
- Move Bundle from the three-way selector to a distinct compact header action. Bundle is a seller-based shopping group, not a third form of game identity.
- A game detail lists its actual linked offers; an offer has an explicit game link only when linked and can expose same-seller Bundle only when a confirmed group exists. Existing unknown/unlinked offers remain visible and actionable.
- Bundle detail opens each member offer and the seller. Preserve Back position and separate filter/search state for game/listing views.
- Do not redesign bottom navigation, introduce migrations or change eligibility/pricing to implement this relationship.
- Product approval of the proposed two-tab-plus-Bundle-entry navigation is required before replacement of the existing selector.

Delivery: full existing PR regressions/SQLite/Android JUnit/Java/APK, source diff review, signed beta build, separate Firebase upload/distribution confirmation. Visual acceptance on phone remains required for long titles, absent saving/rating, large text,2/3/many-game bundles.

### Verified delivery
5.12.104-market-previews (1000121), PR113 merged3be0adae846f98ed76ccbc5015b61777411738ea. PR286 (36863477090) and signed beta121 (36863954605) passed regressions/SQLite/JUnit/Java/APK. Firebase separately confirmed upload and tester distribution at2026-10-01 12:49:50UTC (14:49 Europe/Rome). No device/emulator visual acceptance claimed. Navigation remains a proposal.

Clarification superseding the earlier saving-color wording:50%+ means a green saving badge, not automatic Offertona classification. Existing total-price/used-benchmark evaluation remains separate and unchanged.


## 2026-10-01 — Navigation proposal approved and delivered
5.12.105-catalog-navigation (1000122) uploaded to Firebase at 2026-10-01 14:58:10 UTC and separately distributed to testers/groups at 14:58:10 UTC (16:58 Europe/Rome), confirmed in signed job110429267970 logs. PR114 merged atad475ad702e8618dada5a10e6eb730c2552bfad8. PR validation run293 (36879669999), head26b2053aa413d3b0b9348e7bdebf749b34d3a552, passed full regressions/SQLite, 11 navigation behavior cases, Android JUnit, Java compilation and review APK. Signed beta run122 (36880064442) passed tests/build/signature/upload/distribution.

User's “next” approves the preceding two-tab/Bundle-entry proposal. Implemented as a grouped navigation and connected-detail update.
- Annunci / Giochi are sibling tabs; Bundle is a distinct header entry with Back. Independent search/filter/pagination state and scroll positions retained; delayed restores are scoped to their destination.
- Actual linked game offers open internal announcement detail. A labeled 48dp “Scheda gioco” action opens the canonical linked game; Vinted remains the provider action inside detail.
- Same-seller partner and Bundle member details preserve their source for Back. Category navigation explicitly closes the nested listing/Bundle stack so it cannot cover the Games destination.
- Added 11 JVM behavior scenarios executing production routing/lifecycle methods. Existing glyph-only source guards now recognize the labeled accessible game action. Independent code review caught the nested category issue; correction re-reviewed and approved.
- Approved Home/product visuals, canonical data mapping, eligibility/prices, schema, dependencies and Motore remain unchanged. No device/emulator validation; phone checks: tab search/filter/position, Bundle Back, game→offer→Bundle→member→Back, and member category destination.
Next grouped block: product detail hierarchy, language/personalization and the intentional announcement→game gesture, preserving the approved product artwork. Motore remains on standby.


This approval supersedes earlier statements that the two-tab proposal is pending. Historical pricing and Motore stability requirements remain open.


## 2026-10-01 — Grouped product interaction design approved and delivered
5.12.106-product-interaction (1000123) uploaded to Firebase at2026-10-01 15:30:50 UTC and separately distributed to testers/groups at15:30:51 UTC (17:30 Europe/Rome), explicitly confirmed in signed job110443809254 logs. PR115 merged6abe6c411812f050251cb0641425a9cf5695aacb. PR validation run300 (36883748763), head44bb6c8b76fb5d0e602947351f026510301369c8, passed9gesture scenarios, all existing regressions/SQLite fixtures, Android JUnit, Java compile and review APK. Signed beta run123 (36884375420) passed tests/build/signature/Firebase upload and tester distribution.

User approved the concrete grouped design with “vai” on2026-10-01: raw BGG rating prominent, Ludo secondary; explicit edition/text dependence; existing interest preferences; intentional end-of-content pull and prepared game destination.
- Listing and game details share24sp raw BGG rating with inspectable secondary Ludo Score. Listing price and real saving badge remain distinct; provider identity28dp. Approved dark product artwork/photos preserved.
- Edition name and text dependence are separate readable facts using HomePresentation; correction/help have48dp minimum targets and larger-font stacking. Unknown language is not inferred.
- Mi interessa / Non mi interessa uses the existing game-key Home preferences, toggles back to neutral, and synchronizes attached preserved views. Catalog eligibility and owned/library ratings unchanged.
- Gesture begins counting only at actual content end, requires72dp further movement and full circular progress followed by release. Retreat, final-UP retreat, Android CANCEL, multiple fingers, source closure and unavailable preparation cancel. Explicit Scheda gioco action remains available.
- Source-bound prepared game dialog uses factual background snapshot of rating/similar games/prices/history/active offers and captured deal scores. UI construction stays on main thread; local image decoding on image executor. Pull opens already prepared content without a full-screen loading intermediate, retaining the listing and its scroll for Back. Images may still use their existing asynchronous placeholders; no instant-image guarantee.
- Independent read-only review identified source reprepare-after-close and final-release-coordinate defects; corrected and re-reviewed. Attached-only preference listeners address stale selection on preload/Back. No blockers remained.
- New executable production touch-listener harness covers9scenarios; test-only run294 reproduced6old failures and run297 reproducedfinal-UPretreat before correction. Full existing regression/SQLite/JUnit/compile/APK checks verified below. A compiler API error (LinearLayout minimum height) was corrected before delivery; no failed final checks hidden.
- No device/emulator available. Harness verifies app listener/state effects, not real Android dispatch/interception or visual/gesture smoothness. Phone checks: BGG/language with enlarged text; interest synchronization/neutral; intentional pull, cancellation/retreat and Back/category from prepared game.
Next grouped block: Library previews/personal ratings and sale-price entry, with shared bottom sheets and accessibility. Motore remains on standby.

The concrete in-chat design was presented and explicitly approved before implementation. Intentional pull requirements in section3 are implemented for prepared linked-game destinations; device gesture/visual acceptance remains pending. Earlier Home/Motore/Library historical requirements are not automatically closed.


## 2026-10-01 — Grouped Library design and schema approved; delivered
5.12.107-library-interactions (1000124) uploaded to Firebase at2026-10-01 16:19:41 UTC and separately distributed to testers/groups at16:19:42 UTC (18:19 Europe/Rome), explicitly confirmed in signed job110465000317 logs. PR116 merged39913c18530f96759e38038c3acb983a02146233. Final head3942962c5a2ed1c57370ca6f25d26bbc8ca19ed5 passed PR run305 (36890098114): all regressions/SQLite fixtures, Android JUnit, Java compilation and review APK. Signed beta run124 (36890615976) passed regressions/tests, APK build, expected-certificate verification and Firebase upload/tester distribution.

User explicitly approved the presented grouped Library design and nullable actual-sale-price schema with “vai” on2026-10-01.
- Library previews lead with actual covers and personal taste; aggregate payment summary and filler leave the primary hierarchy. Search, owned/Sold scopes and existing product artwork remain.
- Personal rating display uses0–5stars. Stored0–10values remain exact without bulk rewrite; legacy7 displays3.5/5, zero is meaningful and distinct from missing. Rating removal storesnull. Whole-star choices write corresponding even legacy values.
- Approved SQLite version8 adds nullable sale_price_cents. Sale entry permits unknownblank or actualzero, validates exact decimal cents/negative/precision/overflow. Historical backfill changes only actual proceeds on Sold, keeping original sale date/reason and purchase/rating. Restore-owned explicitly confirms clearing sale fields. Sold purchase editing is omitted to avoid the existing acquisition path silently resetting sale history.
- Library mutations run off UI thread; failures keep inputs for retry. Sheet-scoped single-flight guard rejects rapid second taps and dismissed sheets; successful writes return to the active originating detail and scroll. Leaving during a write does not reopen it.
- Shared bottom sheets use bounded wrap-content height, upward220ms entrance/160ms exit, safe insets/keyboard resizing and disabled-animation fallback. No new dependencies. No emulator/device visual or keyboard validation performed.
- Executable production-Java/SQLite tests cover migration fixtures1–7, nullable fresh schema, preserved ratings7/10/null/zero, actual sale/backfill/restore/owned guard, five-star display and12euro-input boundaries. Async production-method harness covers double submission, failure/retry and leaving during save.
- Test-only run301 reproduced15pre-implementation data/display failures; run304 reproduced2writes from duplicate taps before the single-flight fix. Legacy UX phase2 guard was intentionally updated for approved rows/search/history without the primary payment summary. Final independent review of3942962 found no Critical/Important issues.
Phone checks pending: long titles/enlarged text, legacy3.5stars/zero/no vote, actual/blank/zero sale and historical price, restore confirmation, keyboard/navigation bars, cancellation and disabled animations.
Next grouped block: Ludo app mascot presence/collapse/motion. Present concrete visual prototype before replacing mascot assets; use the user's supplied reference when available in this conversation. Motore stability/motion remains on standby.

This supersedes the pending implementation status of section5 for Library covers/taste, sale price and shared sheets. No full bookshelf redesign, keyboard/device visual acceptance or Ludo mascot replacement is claimed.
