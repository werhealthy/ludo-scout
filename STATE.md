# Ludo Scout — Current state

## Verified release — 2026-09-30

Version: 5.12.87-catalog-engine-motion (1000104). PR95 merged to beta at c4014b0ca019008e8b463fa6c446ef5c20efc98f. Final PR head 2fccc53e026cde7fdf0e264dc171ecea0b568750 passed Android PR run255 (36755674527). Android beta run36756069912 built signed APK and Firebase confirmed successful upload and distribution to configured testers at 18:09 UTC.

## User delivery preference

Automatically merge verified Ludo Scout updates to beta and publish to Firebase App Tester, without waiting for another merge/distribution request. Confirm actual tester-distribution success before claiming availability. Documentation-only status commits can skip CI to avoid duplicate identical APK releases.

## Current UI

- Catalog Giochi: two cover-first columns, roomy portrait covers, fixed two-line ellipsized titles, raw BGG rating, player/time facts, current price and availability. Historical market details remain in game detail. Four advanced filters and sorting remain functional; pill search and circular filter control replace the clipped old toolbar.
- Ludo: redundant metric summary and Motore button removed, tabs/modules spaced, recommendation cards use calm cover-first composition and raw BGG rating.
- Motore opens through Home; the overview has the exclusive five-phase wheel and conditional actionable attention. No work hero, other-scroll cards or recent-scroll list in overview.
- Each phase opens its exact grouped elements directly without an intermediate modal. Central number opens phase<4 work elements and current work status. Zero/empty phases also open directly. Lists load on the data executor, show observation titles for unidentified elements, and keep known game detail navigation.
- Shared read-only SQL produces counts and phase membership with unchanged identity/readiness/review/price gates. Recognized game identities are deduplicated into their most advanced usable phase.
- Same-scroll visual baseline persists. On fresh return/change, numbers interpolate once with a small zoom and green/red delta, then settle. Same-snapshot rerenders do not replay. Actual PROCESSING listing/game jobs pulse the corresponding occupied phase; optional automatic deep jobs are excluded. Motion respects disabled system animators and foreground lifecycle.
- FontAwesome language/text-dependence indicators distinguish edition, independence, dependence and unknown from exact existing facts. No invented game-wide edition language.

## Validation

All current PR and beta workflow regressions passed, including production Java-generated SQL fixtures for count/list parity, identity/trust holds and actual processing ownership. Android JUnit (including motion state tests), Java compilation and signed APK build passed. No Android emulator/device visual verification was available locally; the delivered App Tester build is the device review surface.

## Retained product behavior

Application identity, signing/versionCode strategy, queue ownership, acquisition/reconciliation, catalog publication eligibility and trust/price gates unchanged. Previous Home adaptive cover layout, taller shared previews, category-colored outlines, transactional filters and dark Remus typography remain.

See AI_HANDOFF.md for implementation continuity and UX_SYSTEM files for the established product context.
