# Ludo Scout — AI handoff

## Source of truth
The private GitHub repository `werhealthy/ludo-scout` is now the source of truth.
Do not reconstruct the project from an older ZIP when the repository is available.

## Current baseline
- App: Ludo Scout Android
- Package / applicationId: `it.vintedaffari.app`
- Baseline version: `5.12.11-bgg-state-monotonicity`
- versionCode: `125`
- compileSdk / targetSdk: 35
- minSdk: 28
- Java: 17
- Gradle wrapper: 8.9
- Android Gradle Plugin: 8.7.3
- Current development stage: correctness turnaround first; BGG identity provenance containment is active, historical revalidation and identity-model hardening follow before throughput/UX redesign.

## Git workflow
- Repository: `werhealthy/ludo-scout` (private)
- `main`: user-verified stable baseline.
- `beta`: integration branch used for test builds delivered to the Pixel.
- Significant work should start from `beta` on `work/<task-name>`.
- Do not overwrite another chat's active work branch.
- Every significant change must update this file and `CHANGELOG.md`.
- CI workflow: `.github/workflows/android-beta.yml`.
- Pull requests targeting `beta` are pre-merge validated by `.github/workflows/android-pr.yml` (static regressions + Java compile only; no signing/Firebase distribution).
- `Android beta` runs on pushes to `beta` and can also be triggered manually (`workflow_dispatch`). It builds the signed debug APK, verifies the preserved signing certificate, uploads the APK artifact and distributes it through Firebase App Distribution.
- CI versionCode remains `1,000,000 + github.run_number`; do not change the signing key, package/applicationId or versionCode strategy without explicit user approval.

## Current engine invariants
- Vinted Accessibility observation remains the core UX: user scrolls Vinted normally.
- No anti-bot bypass, CAPTCHA bypass, cookie stealing, private abusive API use or artificial request-rate increase.
- Prefer local filtering, caching, batching and snapshot reuse before public-page requests.
- BGG rating below 6 is filtered before ordinary Vinted linking where possible.
- Exact Vinted identity and exact BGG identity are separate facts.
- One ordinary scroll/run owns the automatic Vinted/BGG backlog lane at a time.
- Newer ordinary scrolls are captured but wait while the older run is active.
- LIVE_DEAL, HUNT_PRIORITY and explicit manual work can preempt ordinary backlog work.
- Manual review is persistent: opening/exploring a case must not dismiss it. It disappears only after explicit resolution/archive/removal or a successful authoritative automatic resolution.
- A ready card requires confirmed BGG, exact Vinted item/url, complete normal enrichment, no open review and no open automatic job.
- Publication date, language and price should be retained when technically available; do not silently drop required data just to make a card look complete.

## 5.12.3 correctness changes
- Motore run detail uses a dedicated observations -> market_listings -> games view rather than Catalogo.
- Run inspector includes incomplete cards and shows their actual stage.
- Zero-network snapshot batch is scoped to the active run and may operate during HTTP pacing/cooldown.
- Ordinary BGG/Vinted work from later scrolls waits for the active run; manual/Hunt remain priority exceptions.
- Deal/Hunt notifications require exact/strong identity and reject accessories, components, bundles, non-games and provisional BGG variants.
- Unresolvable `BGG_VARIANT_PENDING` is promoted to persistent review.
- Thumbnail capture/cache memory pressure protections are enabled.
- Diagnostics include `engineRun={...}`.

## 5.12.4 performance/stability changes
- Motore run inspector is paginated at 24 rows per page instead of inflating up to 220 thumbnail-heavy cards at once.
- Run-list rendering no longer performs per-row Deal/MarketListing lookups just to obtain a thumbnail.
- Motore run subpages are not force-rebuilt every few seconds; queue-driven refresh is coalesced and slower while a run inspector is open.
- OperationCenter/deal reconciliation is single-flight + coalesced instead of allowing repeated 1,200-row scans to queue.
- The old Test-1 deep Accessibility identity diagnostic probe is retired from production observation. Production keeps only a small explicit `/items/<digits>` opportunistic check and never requests Compose extra-data for this purpose.
- Accessibility event diagnostics are batched rather than writing SharedPreferences on every event.
- Screenshot capture is single-flight; remote thumbnail decoding uses RGB_565 and skips under low heap headroom.
- Active-run lookup has a short cache and waiting-run counting avoids recomputing full BGG/Vinted status for every later session.
- UI bitmap cache reduced to 4 MiB.
- Static regression guard: `regression/performance_stability_v5124.py`; Android beta CI runs it before the Android build.
- No database/schema migration in 5.12.4.
- Remaining open performance question: current request efficiency is much better than the historical resolver (~6 physical link requests per newly linked row in the latest field diagnostic), but active-run completion time still needs fresh Pixel measurement after these CPU/memory fixes. Do not increase Vinted request rate; future throughput work must reduce requests via shared family discovery/snapshot reuse/fewer fallbacks.

## 5.12.5 BGG identity provenance firewall
- Automatic zero-network BGG memory reuse now trusts only authoritative BGG metadata/aliases, the canonical game name, an auto-selected canonical BGG name, or an explicit manual BGG choice.
- Seller-authored Vinted titles (`VINTED`, `VINTED_VARIANT`) remain stored as useful observational/search evidence but can no longer, by themselves, bootstrap a future automatic BGG identity at 99.5 confidence.
- BGG match algorithm version is `4`, so older unresolved review cases may be retried under the stricter provenance rule.
- Existing `MATCHED` identities are intentionally not rewritten or deleted by this task. Diagnostics now expose `bggIdentityTrust={... matchedToRevalidate=...}` so the next correctness task can target historical revalidation without an indiscriminate reset.
- Static regression guard: `regression/bgg_identity_provenance_v5125.py`; Android beta CI runs it before the Android build.
- No database/schema migration, no signing/applicationId/versionCode-strategy change, and no request-rate change.

## 5.12.6 Historical BGG revalidation
- Pixel Test 1 on 5.12.5 measured 2,247 seller aliases, 1,886 seller-only aliases and 143 pre-v4 `MATCHED` games eligible for audit. This justified controlled revalidation rather than a blanket data reset.
- `BggHistoricalRevalidator` is a zero-network, one-shot-per-game audit for old automatic matches. Explicit `MANUAL_BGG` and legacy `USER_CONFIRMED` identities are excluded.
- Historical identity is preserved. A listing is independently verified only when its seller title, after conservative marketplace cleanup, resolves exactly and uniquely to the stored BGG id in the bundled local BGG index.
- Non-game/accessory/component/empty-box/bundle cases, conflicting exact BGG identities, ambiguous aliases and merely fuzzy/plausible titles become persistent listing review. No row or BGG id is deleted or silently reassigned.
- Review also marks any legacy deal `MATCH_UNCERTAIN`, preventing an unverified historical identity from remaining in the ready/deal path.
- Progress is persisted in `queue_controls` with `bgg_revalidation_v1:<gameId>` markers. This makes the pass one-shot and restart-safe; it cannot repeat automatically for the same game.
- The BGG foreground lane advances only 2 historical games per loop; WorkManager recovery advances 4, preserving current-run responsiveness. No network calls are made by the audit.
- Diagnostics expose `bggHistoricalRevalidation={processed, verifiedGames, reviewGames, pending}`; `bggIdentityTrust.matchedToRevalidate` now counts only unprocessed eligible games.
- 5.12.7 tightens accounting: legacy `USER_CONFIRMED` games are excluded from pending metrics as well as from execution, and any game with at least one flagged listing remains counted in `reviewGames` even when another listing independently verifies the canonical BGG identity.
- Static regression guard: `regression/historical_bgg_revalidation_v5126.py`.
- No schema migration, no data deletion/reset, no signing/applicationId/versionCode-strategy change, and no Vinted request-rate change.

## 5.12.8 BGG local index performance
- Pixel Test 2 showed historical BGG revalidation advancing only 20 games in 8 minutes even though the audit is zero-network.
- Root cause: `BggSearchClient.localById()` reopened, decompressed and linearly scanned the full ~31k-game gzip search index for every historical game; `localExactCandidates()` then maintained a separate exact-name index.
- `localById()` now shares the same one-time queue-process catalog load used by the exact-name/alias matcher. That single pass builds both `normalized title/alias -> candidates` and `BGG id -> game`; subsequent ID lookups are O(1) and do not reopen the gzip file.
- The retained id map reuses the same `Game` objects already held by the exact index, adding map references rather than duplicating the full catalog objects. It is cleared on `shutdown()`.
- Historical audit scheduling remains deliberately bounded (2 games/service loop, 4/recovery worker); this change removes wasted CPU/I/O instead of increasing network rate or priority.
- Regression: `regression/bgg_local_index_performance_v5128.py` protects against restoring per-ID full catalog scans.
- No schema/data reset, no Vinted/BGG request-rate change, no signing/applicationId/versionCode-strategy change.

## 5.12.9 Historical BGG drain scheduling
- Pixel Test 3 on 5.12.8 improved revalidation from 20 to 47 processed games, proving the per-game full-index scan was removed, but 96 eligible historical games remained after five minutes.
- Root cause after the index fix was scheduling, not matching cost: the foreground BGG lane still processed only two historical games per loop, ran historical cleanup before current BGG identity work, and service/recovery liveness did not consider historical pending rows.
- Priority is now explicit: current BGG identity matching -> current BGG enrichment -> historical revalidation. Historical work runs only when no current BGG work is runnable.
- Foreground historical cleanup drains up to 24 games per burst and yields 350 ms between bursts. The revalidator itself caps any caller at 32 games; no network calls are introduced.
- `historicalBggRevalidationPendingCount()` is now the canonical executable pending metric and requires an ACTIVE listing, so liveness/diagnostics cannot be held open by archived-only games.
- Foreground-service liveness and BGG lane supervision include historical pending work. WorkManager recovery also includes historical pending work in health/rescheduling decisions, preventing the audit from silently stopping when it is the only remaining task.
- Bulk revalidation suppresses per-game queue broadcasts and emits one coalesced update per slice, preventing faster cleanup from creating an OperationCenter/UI rebuild storm.
- Regression: `regression/historical_bgg_drain_scheduling_v5129.py` protects current-before-history priority, liveness, bounded bursts and notification coalescing.
- No schema/data reset, no Vinted/BGG request-rate change, no signing/applicationId/versionCode-strategy change.

## 5.12.11 BGG state monotonicity
- Pixel Test 5 proved the fuzzy performance fix: 3 current fuzzy matches completed in 122 ms. However, all 3 were reported as moved to review while `bggMatchRequired` stayed at 3 and the historical audit remained blocked at 73 pending.
- Root cause: `applyAnalysis()`/`upsertProvisionalGame()` could overwrite a later BGG decision. Re-analysis of an existing provisional title unconditionally reset the game to `BGG_MATCH_REQUIRED`, made it visible, cleared its filter reason, and wrote the listing back to `BGG_MATCH_REQUIRED`.
- BGG identity states are now monotonic: only unresolved states (`PENDING_ANALYSIS`/`BGG_MATCH_REQUIRED`) may be refreshed by another analysis pass. `BGG_MATCH_REVIEW` and `AUTO_QUARANTINED` cannot be downgraded by stale or repeated analysis, including a late `status=matched` result from an older analysis batch.
- `markBggMatchReview()` now updates the canonical game and active linked listings transactionally, so UI/listing state cannot diverge from the game state.
- `applyAnalysis()` ignores inactive listings and refilters stale work that points at an already quarantined game.
- `reconcileQueue()` idempotently repairs legacy cross-table drift: active listings linked to review games become `BGG_MATCH_REVIEW`; active listings linked to `AUTO_QUARANTINED` games are returned to `AUTO_FILTERED`.
- This is not a data reset and deletes nothing. It preserves existing review/quarantine decisions and only repairs state invariants.
- Regression: `regression/bgg_state_monotonicity_v51211.py`; PR and Android beta CI run it before compilation/build.
- No schema change, network-rate change, signing/applicationId change, or CI versionCode-strategy change.

## 5.12.10 BGG fuzzy index performance
- Pixel Test 4 on 5.12.9 still left 73 historical revalidations pending. Diagnostics showed the BGG lane stuck in `MATCHING` with three current identities, and the lane heartbeat aged ~19 s while processing one title.
- Root cause: the current local matcher’s fuzzy fallback still called `BggSearchClient.localCandidates()`, which reopened/decompressed/scanned the full ~31k-game gzip catalog for every fuzzy query.
- Added a queue-only `localCandidatesIndexed()` path. The long-lived queue client reuses the same parsed `Game` objects already owned by the exact/id indexes and performs in-memory scoring without reopening the gzip catalog.
- The short-lived/manual `localCandidates()` path intentionally remains streaming from disk. This avoids retaining the 31k-game catalog in UI/manual clients and preserves the earlier heap/OOM protection.
- Indexed fuzzy ranking keeps only the best 16 candidates in a bounded priority queue and uses a 32-query LRU cache. Returned candidates are copies so queue ranking cannot mutate shared index objects.
- Added cross-process `bggLocalMatch={handled,fuzzy,matched,review,quarantined,elapsedMs}` diagnostics so Pixel tests can measure local matcher latency directly.
- Regression: `regression/bgg_fuzzy_index_performance_v51210.py` protects the queue-only indexed path, bounded candidate/cache sizes, UI streaming path, shared-object reuse and telemetry.
- No schema/data reset, no Vinted/BGG network-rate change, no signing/applicationId/versionCode-strategy change.

## Known UX direction
The next major phase is a full Motore redesign. Avoid treating all information as equal cards.
The intended hierarchy is run-oriented:
1. current/active scroll job;
2. explicit pipeline stages and current stage;
3. ready results / unresolved review;
4. newer runs waiting;
5. history grouped by day, with individual scrolls only inside a day.
Use concrete language (`card Vinted osservate`, `giochi trovati`, `BGG riconosciuto`, `Vinted collegato`, `card pronta`) rather than ambiguous labels such as `gioco valido`.

## Secrets
Never commit secrets, signing keys or local SDK paths.
- `secrets.properties` is ignored and should not normally be needed.
- Local BGG token should live in `%USERPROFILE%\\.gradle\\gradle.properties` as `BGG_TOKEN=...`.
- CI expects GitHub Actions secret `BGG_TOKEN`.
- CI expects GitHub Actions secret `ANDROID_DEBUG_KEYSTORE_BASE64`, containing the existing developer-machine `%USERPROFILE%\\.android\\debug.keystore` encoded as Base64.
- Signing material must stay outside Git.

## Android signing warning
The baseline has no custom `signingConfig`; Android Studio debug builds therefore normally use the developer machine's debug keystore (`%USERPROFILE%\\.android\\debug.keystore`).
The GitHub workflow restores that exact keystore to `$HOME/.android/debug.keystore` before `assembleDebug`.
Do not replace it with a newly generated key: the first cloud APK must update the existing Pixel installation without uninstalling or losing local app data.

## Required completion note after every task
State explicitly:
- files changed;
- behavior changed;
- database/schema migration, if any;
- automated checks run and results;
- one simple manual test for the user, including exact pass/fail criteria;
- remaining risks/open questions.
