# Ludo Scout — Current state

## Verified release — 2026-09-30

Version: 5.12.86-unified-ui-engine (1000103). PR94 merged to beta at 1942022fb5bf8e58d00614ae03ff4947987d52ec. Final PR head c9d8edd198a4a0fe0cc877316d7a983b554bb637 passed Android PR run253; Android beta run36747388504 built signed APK and Firebase confirmed upload and distribution to configured testers.

## User delivery preference

Automatically merge verified Ludo Scout updates to beta and publish to Firebase App Tester, without waiting for another merge/distribution request. Confirm actual tester-distribution success before claiming availability. Documentation-only status commits can skip CI to avoid duplicate identical APK releases.

## Completed

- Home/Catalog: taller cover-first cards, a reserved two-line title slot with ellipsis, consistent price/status slots.
- Catalog: search plus circular filters, result-count sorting, no floating engine overlay. Separate edition/text-dependence filters, all supported editions, accurate unknown labels, transactional reset/apply and filter restoration (category/dependence/discount).
- Shared dark palette/Remus typography, navy surfaces, black navigation, rounded controls, sheets/full-screen panels across market/game/detail, Ludo, Library and settings. Ludo compact heading and explicit Motore entry replace floating overlay.
- Motore: circular Osservati → Giochi → Idonei → Verifiche Vinted → Pronti; ordered-list fallback for narrow phones/large fonts; separate attention inbox and three actual recent scrolls. Phase help and ready-listing navigation.
- Exclusive phase counts from current state, scoped to active/last scroll, grouped by known game/BGG identity (unrecognized signatures separate); most advanced usable listing determines phase. Center excludes ready. Publication readiness follows actual Catalog trust/identity/type/rating/lifecycle/pending-job gates; archive/holds/review never inflate ready. All additional reads on uiDataIo; snapshot time starts after reads. No queue/matching/pricing/schema changes.

## Validation and next step

61 local source regressions passed. Production-generated SQLite fixtures and JUnit/Android compilation passed in CI; independent review findings corrected. No local Android device/emulator verification available.

Next: install the release above from App Tester and review equal card heights/two-line truncation, Catalog filter cancel/apply/reset and scrolling, other pages, Ludo→Motore, phase counts and large-font/narrow layouts. Pixel visual balance remains to be confirmed; do not claim pixel-perfect fidelity from CI alone.

## Relevant source

MainActivity.java; HomePresentation.java; EnginePipelineSql.java; DealDatabase.enginePipelineCounts/engineRunItems; HomePresentationTest.java; regression/engine_pipeline_sql_v51286.py; UI regression guards; app/build.gradle; Android PR/beta workflows. Historical details are in AI_HANDOFF.md and UX_SYSTEM_V1.md.
