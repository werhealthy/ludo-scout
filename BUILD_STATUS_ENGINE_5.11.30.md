# Ludo Scout 5.11.30 — Engine consolidated

Status: **production engine integration complete in source; full Android compile must be run in Android Studio.**

## What changed

- The validated Vinted batch matcher is now part of the normal queue flow (`VintedBatchEngine`), not a test button.
- It performs **zero-network** linking from fresh persistent catalogue snapshots and only commits high-confidence matches.
- A Vinted item ID is assigned to at most one local listing. Ambiguous cases stay unresolved and fall back to the existing resolver.
- BGG rating eligibility remains before automatic Vinted backlog work.
- LIVE, Hunt and manual work preempt the backlog, including while urgent work is temporarily waiting for its next allowed network slot.
- A fresh catalogue response is immediately reused for sibling observations before the next network claim.
- Batch scanning is memory-bounded: at most 180 observations are considered in a pass and one family snapshot is decoded at a time.
- Persistent candidate snapshots are now a production store (`VintedCandidateSnapshotStore`); its diagnostics are lightweight and do not replay the entire backlog.
- Exact Vinted identity and exact BGG variant are treated as separate facts. Batch-linked rows enter `BGG_VARIANT_PENDING` until richer item-page text is inspected.
- `BggVariantReconciler` can move one listing to a more specific local BGG title such as **Tokaido Duo**, without moving sibling Tokaido listings. Ambiguous variant evidence is sent to review instead of being guessed.
- Exact public item-page price is authoritative for the **current** price. When Vinted drops a price (for example 10 € → 6 €), Ludo appends a `price_observations` history row and updates the current card price instead of overwriting the old observation.
- Test-only activation/one-shot buttons were removed from Settings. Production diagnostics now expose `vintedBatchEngine`, `bggVariantGuard`, `vintedPriceRefresh` and `vintedCandidateSnapshotStore`.

## Safety / request policy

The new production batch engine performs no HTTP. It consumes pages Ludo already obtained through the existing public-page flow. It does not use cookies, OAuth, private Vinted APIs, CAPTCHA/rate-limit bypasses, or security-check evasion. Optional exact item-page metadata remains low priority and is preempted by LIVE/Hunt/manual work.

## Validation performed here

`regression/engine_batch_v51130.py` passes all current engine checks, including production batch integration, exact-price gate, one-to-one identity, safety gates, BGG ≥6 gate, urgent preemption, memory-bounded snapshot processing, variant reconciliation, exact-page price refresh, removal of test buttons and production diagnostics.

A Java lexical/balance scan passed on all changed Java files. A `javac` parse attempt produced only the expected missing Android / `org.json` dependency errors and no syntax-pattern errors.

A full Gradle Android compile could not be completed in this environment because the Gradle 8.9 wrapper distribution is not cached and outbound download is unavailable (`UnknownHostException: services.gradle.org`). Open the project in Android Studio and run the normal build there before installing. If Android Studio reports a compiler error, paste the first error block back into the chat.
