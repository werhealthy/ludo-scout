# Ludo Scout — Changelog

## Git workflow / CI bootstrap
- Published the Git-ready 5.12.3 baseline to private GitHub repository `werhealthy/ludo-scout`.
- Created `beta` from `main` for test-build integration.
- `Android beta` now runs automatically on pushes to `beta` and remains manually triggerable.
- The workflow builds a signed debug APK with the preserved developer signing identity, verifies the certificate fingerprint, uploads the artifact and distributes it through Firebase App Distribution.
- CI requires the documented BGG, signing and Firebase GitHub Secrets; none are stored in the repository.

## 5.12.9 — Historical BGG drain scheduling
- Fixed the second bottleneck revealed by Pixel testing after the local-index optimization: historical revalidation was artificially limited to tiny slices and could run before current BGG work.
- Current BGG identity and enrichment now always precede historical cleanup; historical revalidation runs only when the current BGG lane has no runnable work.
- Historical cleanup now drains bounded 24-game bursts with a short yield, while the revalidator hard-caps callers at 32 games.
- Added historical pending work to foreground-service liveness, BGG lane supervision and WorkManager recovery/rescheduling so the one-shot audit cannot silently stop while rows remain.
- Canonical pending accounting now requires an active listing, matching the actual candidate query.
- Coalesced historical queue notifications to one broadcast per slice instead of one per game, avoiding UI/update storms while increasing local throughput.
- Added `regression/historical_bgg_drain_scheduling_v5129.py` and updated the historical safety regression to verify priority semantics rather than obsolete batch constants.
- No schema/data reset and no signing, applicationId, CI versionCode strategy, Vinted request rate or BGG network-rate changes.

## 5.12.8 — BGG local-index performance
- Fixed the root cause of historical BGG revalidation starvation: `localById()` no longer reopens/decompresses/scans the entire ~31k-game local catalog once per game.
- The local BGG catalog is now indexed once per queue-process `BggSearchClient`, producing both exact title/alias lookup and direct BGG-id lookup from the same parsed `Game` objects.
- Subsequent historical BGG id resolution is O(1), while audit batch sizes and network pacing remain unchanged.
- Added `regression/bgg_local_index_performance_v5128.py` and wired it into PR validation and Android beta CI.
- No schema/data reset and no signing, applicationId, CI versionCode strategy, Vinted request rate or BGG network-rate changes.

## 5.12.7 — Revalidation accounting fix
- Aligned historical revalidation diagnostics with execution: `USER_CONFIRMED` identities are excluded from `pending`/`matchedToRevalidate`, so completion can truthfully reach zero.
- Mixed games with at least one historical listing requiring review are persistently counted as review even if another listing independently confirms the canonical BGG identity.
- No data, identity, queue, network, signing or schema behavior changed beyond these accounting semantics.

## 5.12.6 — Historical BGG revalidation
- Added a restart-safe, zero-network one-shot audit for pre-v4 automatic BGG identities after Pixel diagnostics reported 143 historical `MATCHED` games still needing revalidation.
- Manual/user-confirmed identities are excluded from automatic audit.
- Historical seller titles must resolve exactly and uniquely to the stored BGG id to preserve automatic trust; weaker, conflicting, ambiguous, accessory, bundle and non-game evidence is moved to persistent review instead of being guessed.
- Revalidation never deletes or silently reassigns historical BGG ids. It flags individual listings, preserving recovery and allowing later manual/authoritative correction.
- Legacy deal rows associated with flagged listings become `MATCH_UNCERTAIN`, keeping suspect historical identities out of the ready/deal path.
- Added durable per-game `bgg_revalidation_v1:<gameId>` markers so the audit is one-shot even across process restarts.
- Added `bggHistoricalRevalidation` diagnostics and `regression/historical_bgg_revalidation_v5126.py`; both PR validation and Android beta CI run the guard.
- No schema migration/reset and no Vinted request-rate, signing, applicationId or CI versionCode strategy changes.

## 5.12.5 — BGG identity provenance firewall
- Stopped seller-authored Vinted aliases from acting as authoritative learned BGG identities in the zero-network matcher.
- Kept Vinted titles as non-authoritative evidence while limiting identity shortcuts to BGG primary/original/alternate aliases, curated BGG aliases, canonical auto-match names and explicit manual BGG choices.
- Bumped the BGG match algorithm version to 4 so older unresolved review cases can be reconsidered under the stricter trust rule.
- Added a read-only contamination audit (`bggIdentityTrust`) including the count of seller aliases and already-matched games that remain candidates for a later controlled revalidation pass.
- Added `regression/bgg_identity_provenance_v5125.py` and wired it into Android beta CI.
- Added non-distributive `Android PR validation` for pull requests targeting `beta`: regressions plus Java compile before merge, without Firebase or signing-secret use.
- Existing matched rows are preserved in this step: no destructive reset and no schema migration. Signing, applicationId, CI versionCode strategy and Vinted request pacing are unchanged.

## 5.12.4 — Performance stability
- Android beta CI now runs on pushes to `beta` as well as manual dispatch, so a merged beta commit is built, signature-checked and distributed automatically.
- Paginated Motore run inspector to 24 rows per page and removed per-row Deal/MarketListing lookups used only for thumbnails.
- Coalesced Motore refreshes and OperationCenter reconciliation to stop repeated full view-tree rebuilds and queued 1,200-row scans.
- Retired the old deep Test-1 Accessibility identity probe from production; kept only a bounded explicit Vinted URL/ID check.
- Batched Accessibility event diagnostic writes instead of persisting SharedPreferences on every Vinted event.
- Serialized screenshot capture, added low-heap guards and RGB_565 remote thumbnail decoding, and reduced the UI bitmap cache.
- Added short active-run caching and lightweight waiting-run counting to reduce repeated SQLite work in queue loops.
- Added `regression/performance_stability_v5124.py` and wired it into the Android beta workflow before the Android build.
- No database/schema migration. Resolver safety, BGG/Vinted identity separation, active-run scoping, BGG <6 filtering and persistent review semantics are unchanged.

## 5.12.3 — Engine correctness
- Dedicated Motore run inspector instead of redirecting run details to Catalogo.
- Active-run scoped Vinted/BGG ordinary processing; newer runs wait.
- Zero-network snapshot batch may continue during HTTP pacing/cooldown.
- Stronger ready/deal/Hunt identity gates.
- Persistent BGG/Vinted review behavior.
- Variant-pending cases that cannot progress automatically become review.
- Thumbnail/UI bitmap memory-pressure safeguards.
- `engineRun` diagnostics.

## Git workflow migration preparation
- Removed tracked/local `secrets.properties` from the Git-ready copy.
- Added `secrets.properties.example` only as documentation.
- `BGG_TOKEN` can now come from Gradle user properties, CI environment, or legacy local file, in that order.
- Expanded `.gitignore` for secrets, signing material, build products and local IDE state.
- Added `AI_HANDOFF.md` as cross-chat technical source of context.
