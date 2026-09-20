# Ludo Scout — AI handoff

## Source of truth
The private GitHub repository `werhealthy/ludo-scout` is now the source of truth.
Do not reconstruct the project from an older ZIP when the repository is available.

## Current baseline
- App: Ludo Scout Android
- Package / applicationId: `it.vintedaffari.app`
- Baseline version: `5.12.18-single-owner-anr-engine-recovery`
- versionCode: `132`
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

## 5.12.18 Single-owner queue + ANR + engine recovery

- Pixel validation of 5.12.17 confirmed the product-facing unique-card count (`20 card Vinted uniche`) but exposed that the visible active job was still the older 5.12.16 run: `analysisPending=7; waitingRuns=1`. The new scroll was correctly queued behind it.
- A fresh current-build ANR was recorded immediately after package update: default process, `No response to onStartJob ... SystemJobService`, with RSS ~536 MB. The same debug also captured handled `SQLiteDatabaseLockedException` in `queue:onStartCommand:maintenance`.
- Deterministic root cause: `QueueWakeReceiver.ACTION_NOW` started `QueueKeepAliveService` and then fell through to `scheduleLocal()`, launching WorkManager as a second same-process queue consumer. WorkManager and the foreground service could therefore race over SQLite and duplicate queue working sets.
- `ACTION_NOW` is now foreground-service-only; `QueueDrainWorker` exits before opening the queue DB whenever the service owns the queue. WorkManager remains recovery for times when the service is absent.
- `QueueKeepAliveService` claims ownership immediately after `startForeground()` and moves DB initialization, reconcile/sweep, lane supervision, notification-state queries and periodic maintenance off the default-process main thread onto one serialized supervisor executor. `onStartCommand()` is now non-blocking.
- `JsGameEngine` readiness is no longer a one-shot `onPageFinished` probe. It retries every 500 ms for up to 30 s, covering slower/restarted WebView initialization so pending analysis cannot remain stuck merely because the bridge became ready after page-finished.
- `MarketStore.pendingAnalysisCards()` now scopes analysis to the active Motore run while one exists. A newer scroll does not consume the JS classifier ahead of the older job that the UI says is active.
- `systemExitHistory` v3 additionally exposes `buildSince` plus `crashBuild/anrBuild/memoryBuild/otherBuild`, separating current-installed-build failures from older epoch history.
- Regression: `regression/queue_single_owner_anr_engine_recovery_v51218.py` executes an active-vs-waiting analysis fixture and statically guards single queue ownership, off-main service lifecycle work, JS readiness retry and build-scoped exit telemetry.
- No schema migration, signing/applicationId, CI versionCode strategy, Firebase distribution, secrets or network-rate changes.

## 5.12.17 Acquisition dedupe + crash stability

- Pixel validation of 5.12.16 exposed two independent correctness/stability signals in the same fresh job: ~20 unique Vinted cards produced 66 raw observation events, and Android recorded a fresh default-process `CRASH` after the engine epoch.
- Accessibility now suppresses identical title/brand/price sightings and re-analysis for 10 minutes. `DealDatabase.recordSighting()` also applies the same SQLite-backed dedupe window so a `:radar` process restart cannot manufacture a second Motore acquisition from the same visible cards.
- Motore product copy now uses `uniqueListings` for “card Vinted” counts in the hero, job header and daily chronology; raw `observations` remains diagnostic telemetry only.
- Crash journaling writes the minimal timestamp/process/error/root header before allocating/formatting a stack trace, improving survival under OOM/heap-pressure failures.
- Android exit-history diagnostics are now scoped to Ludo processes (`it.vintedaffari.app`, `:ui`, `:radar`) instead of UID-wide WebView sandbox exits, are explicitly scoped to the current engine epoch, and expose PSS/RSS plus recent app exits.
- The same Pixel run showed one local BGG fuzzy decision taking ~74 s immediately before the recorded queue-process crash. Queue exact/fuzzy local catalog scans now have a 2.5 s per-search CPU budget. A timed-out/incomplete scan is never auto-trusted; it becomes optional review and reports timeout telemetry.
- Regression: `regression/engine_acquisition_crash_stability_v51217.py` models persisted same-card dedupe and statically guards unique-card UX, crash attribution, OOM-tolerant journaling, and BGG search budgets.
- No schema migration, no signing/applicationId/network-rate/CI-versionCode strategy changes.

## 5.12.16 Motore epoch + progress semantics + queue startup stability
- Product reset after real-device evidence: the old Motore timeline/review inbox mixed multiple historical iterations with the current job. A new durable `engine_epoch_start` in SQLite scopes Motore sessions/history/review to the new cycle while retaining all raw observations, prices, matched identities and market history.
- One-time operational cut-over archives old automatic processing jobs (explicit `HUNT_PRIORITY` work is preserved), clears inherited listing review flags, archives legacy deal review states, and hides unresolved provisional BGG review rows as `EPOCH_ARCHIVED_REVIEW`. No observations or price history are deleted.
- If an archived ambiguous provisional title appears again after the cut-over, `upsertProvisionalGame()` revives it as fresh `BGG_MATCH_REQUIRED` work instead of inheriting the old review forever.
- Fixed a core run-lifecycle bug: `fillEngineCounts()` previously counted every `analysis_status='pending'` row as still pending, including `BLOCKED_CLASSIFIER` observations that are intentionally never analyzed. It now counts only `verification_state='PENDING_ANALYSIS'`, matching the session builder and allowing completed runs to close.
- Motore hero is progress-first (`elaborati / totale`, ready, ambiguous, remaining) instead of centering the number of games found. Ambiguous cases are explicitly optional and do not represent blocked automatic work.
- `QueueKeepAliveService` startup is phase-guarded. Resolver/BGG/reconcile/recovery/sweep/lane failures are journaled instead of escaping through Android Service startup and causing a crash loop; critical foreground/database failures stop the service cleanly.
- Crash journal v2 records handled phase plus deepest root cause. Diagnostics add `engineEpoch={...}` for the current product cycle.
- Regression: `regression/engine_epoch_progress_stability_v51216.py` executes a SQLite fixture for epoch cut-over/pending-count semantics and statically guards UI/startup invariants.
- No signing/applicationId/network-rate/CI-versionCode strategy changes.

## 5.12.15 Crash diagnostics + executable SQLite integration
- Strategy change after repeated Pixel iterations: the device is no longer the primary debugger for BGG state-machine correctness. Pixel Test 9 showed the affinity fix worked (`requiredPure=0`, `reviewLegacy=0`, `bggMatchRequired=0`) and historical revalidation progressed from 73 to 55 pending.
- User also reported Android crash notifications every 1-2 minutes. Existing `lastCrashAt` was insufficient because `MainActivity` is in `:ui` while the queue runs in the default process and Accessibility runs in `:radar`; only the UI process installed the legacy crash handler.
- Added `LudoScoutApp` + `ProcessCrashJournal` installed in every app process. Uncaught Java crashes are journaled to one bounded synchronous file per process, avoiding multi-process SharedPreferences cache ambiguity.
- On Android 11+ diagnostics also query `ActivityManager.getHistoricalProcessExitReasons()` and summarize recent `CRASH`, `ANR`, memory-related and other exits with the affected process name. This catches system-observed deaths that never reached the Java uncaught handler.
- Added diagnostics `processCrashJournal={...}` and `systemExitHistory={...}`.
- Added `regression/bgg_sqlite_state_machine_v51215.py`, an executable sqlite3 integration test that reproduces the old TEXT-vs-INTEGER affinity trap, verifies the cast fix, and runs current/historical candidate sets through their drain transitions.
- Added `regression/process_crash_diagnostics_v51215.py` and wired both new checks into PR and beta CI.
- No schema/data reset, network-rate change, signing/applicationId change, or CI versionCode-strategy change.

## 5.12.14 BGG algorithm-version numeric affinity
- Pixel Test 8 proved cold-start performance is fixed: shared catalog load 767 ms, full local batch 185 ms, single-flight active, no review write misses. Yet `remainingRequired=3` persisted after three successful review writes.
- Root cause: Android `rawQuery` selection arguments are strings while queries compared them against `COALESCE(match_algorithm_version,0)`. In SQLite, `COALESCE(...)` is an expression with no column affinity, so numeric stored values can be compared by storage class against the TEXT parameter instead of numerically. This made already-current review rows continue to satisfy the legacy `< ?` predicate.
- Every BGG algorithm-version comparison now explicitly uses `CAST(? AS INTEGER)`: current match-required count, current candidate selection, historical candidate selection and historical pending count.
- Added `bggMatchBreakdown={requiredPure,reviewLegacy,reviewCurrent,algorithm}` diagnostics to distinguish unresolved current work from already-current human-review work.
- Regression: `regression/bgg_version_affinity_v51214.py`; wired into PR and beta CI.
- No schema/data reset, network-rate change, signing/applicationId change, or CI versionCode-strategy change.

## 5.12.13 BGG cold-index + single-flight
- Pixel Test 7 proved review persistence itself works: `bggReviewWrite` showed `BGG_MATCH_REVIEW -> BGG_MATCH_REVIEW`, `gameChanged=1`, `listingChanged=1`. The fresh write appeared ~12 s old while `bggLocalMatch` was still the stale v1 summary from ~172 s earlier, showing the new batch had not completed.
- Root cause moved to cold-start architecture: the queue client still built a global all-alias exact-name HashMap before answering exact lookups. On a fresh process after APK update this can dominate startup CPU/heap, despite warm subsequent fuzzy batches being fast.
- Removed the retained global alias map. The queue process now parses the gzip catalog once into shared `Game` objects plus `BGG id -> Game`; exact title/alias lookup scans that in-memory catalog lazily and caches only the most recent 64 exact queries.
- Queue fuzzy lookup continues to reuse the same catalog and retains only its bounded top-16 heap + 32-query cache.
- Added process-level single-flight to `QueueJobRunner.matchBggIdentities()` so foreground service and WorkManager cannot duplicate the same local identity batch concurrently. A second caller reports `state=BUSY` instead of doing duplicate work.
- `bggLocalMatch` v3 now includes local-index telemetry: catalog loaded flag, game count, cold load milliseconds, exact scan/cache-hit counts and cache sizes.
- Regression: `regression/bgg_cold_index_singleflight_v51213.py`; existing 5.12.8/5.12.10 guards were updated to validate the lighter shared-catalog architecture instead of the removed global alias map.
- No schema/data reset, no network-rate change, no signing/applicationId/versionCode-strategy change.

## 5.12.12 BGG review-write accountability
- Pixel Test 6 still showed `handled=3`, `review=3`, but `bggMatchRequired=3` and `bggMatchReview=15` unchanged. Fuzzy matching itself was fast (63 ms), so the remaining uncertainty is the persistence transition itself.
- `markBggMatchReview()` now reads the canonical game row inside the same SQLite write transaction, refuses to overwrite a concurrently authoritative BGG identity, updates by exact game id, and returns the actual canonical-row write count.
- Queue matcher diagnostics now separate `reviewDecisions`, successful `reviewWrites`, and `reviewWriteMisses`, then re-read and report `remainingRequired` after the batch.
- A dedicated `bggReviewWrite={...}` diagnostic records game id, before/after state, current BGG id, visibility, changed game/listing row counts and reason. This turns the remaining Pixel test from inference into direct evidence.
- Linked active listings still transition to `BGG_MATCH_REVIEW`/`NEEDS_REVIEW` in the same transaction as the canonical game row.
- Regression: `regression/bgg_review_write_accountability_v51212.py`; PR and beta CI run it.
- No data reset/schema/network-rate/signing/applicationId/versionCode-strategy changes.

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
