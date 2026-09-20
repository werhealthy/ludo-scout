# Ludo Scout — Changelog

## Git workflow / CI bootstrap
- Published the Git-ready 5.12.3 baseline to private GitHub repository `werhealthy/ludo-scout`.
- Created `beta` from `main` for test-build integration.
- `Android beta` now runs automatically on pushes to `beta` and remains manually triggerable.
- The workflow builds a signed debug APK with the preserved developer signing identity, verifies the certificate fingerprint, uploads the artifact and distributes it through Firebase App Distribution.
- CI requires the documented BGG, signing and Firebase GitHub Secrets; none are stored in the repository.

## 5.12.27 — Queue liveness and Catalog truth
- Fixed a Vinted-lane starvation bug exposed by the 5.12.26 Pixel debug: runnable Motore jobs could coexist with an idle lane because any future LIVE/HUNT/MANUAL retry blocked ordinary work until its retry time.
- Urgent Vinted work now reserves at most one public-page slot when it is within 65 seconds of becoming runnable. Far-future urgent retries no longer freeze current Motore progress.
- Existing priority ordering is preserved: once urgent work is due it still preempts ordinary work.
- The queue lane now reports near-due urgent reservation as an explicit WAITING state instead of “nessuna attività rivendicabile”.
- Diagnostics add `vintedUrgent={active,due,nextDueAt,reserveUntil}`.
- Fixed Catalog `Vinted da completare` count/filter mismatch. The badge and filter now agree on missing URL, publication label, or seller id.
- The red Vinted core warning remains specific to a missing exact page/link; metadata-only incompleteness remains a softer state.
- Added `regression/queue_liveness_catalog_truth_v51227.py` to PR and beta CI.
- No schema migration, request-rate increase, signing, applicationId, Firebase, secrets or CI versionCode-strategy change.

## 5.12.26 — Target-only recovery, truthful review and early price gate
- Manual “Cerca questo annuncio su Vinted” is now a target-only recovery mode. Search-result cards seen during that flow no longer become ordinary Motore observations/jobs; explicit market-price scans remain unchanged.
- Recovery copy now explains that one visible result is not enough for an exact automatic link when Vinted does not expose the item URL/id; the exact item must be opened/shared or its link pasted.
- Split user-actionable review from non-actionable trust/history holds. Historical `MATCH_UNCERTAIN` rows remain excluded from trusted surfaces but no longer inflate the “da controllare” count or lead to an empty inbox.
- Held rows are considered settled automatic work, so they do not keep a run alive forever; Motore labels them as non-published with no action required.
- Added a conservative pre-network price gate: ordinary unlinked listings are removed from automatic Vinted work only when seller ask is at least 2× and €25 above an existing used-market reference. Hunt/manual work is exempt and raw/game history is preserved.
- Applied the price gate immediately before deferred Vinted promotion so it can actually save the scarce public-page request.
- Moved queue reconciliation and repeated noise maintenance off Activity startup’s UI thread to reduce the observed SQLite contention/input-ANR path.
- Diagnostics add manual recovery suppression/state, early price filtering and Motore held counts.
- Added `regression/recovery_review_price_gates_v51226.py` to PR and beta CI.
- No schema migration, request-rate increase, signing, applicationId, Firebase, secrets or CI versionCode-strategy change.

## 5.12.25 — Adaptive Motore fairness and real-work ETA
- Closed the remaining head-of-line gap left after 5.12.24: the oldest unfinished scroll can no longer monopolize the ordinary automatic lane indefinitely.
- Motore timing now models core Vinted identity work rather than raw valid-card count. The target is a 10-minute floor, otherwise roughly 1 minute of local/setup allowance plus 55 seconds per core remote candidate.
- Added a live ETA based on core Vinted candidates still pending; it shrinks as online identity work completes and does not include optional deep metadata.
- Added non-destructive round-robin continuation: when another unfinished scroll exists, an ordinary run yields after a 10-minute service slice. Its listings/jobs remain untouched and resume on a later turn.
- HUNT_PRIORITY and MANUAL_PRIORITY keep their existing preemption; trusted-only Home/Scopri rules are unchanged.
- Motore UI now reports remaining online verifications, uses `In pausa · riprenderà` for yielded work, and explains that acquired scrolls rotate without discarding cards.
- Diagnostics add `engineFairness`, `etaMs`, `coreWork` and `corePending`.
- Added `regression/engine_adaptive_fairness_v51225.py` and updated the 5.12.24 timing regression to the real-work model.
- No schema migration, request-rate increase, signing, applicationId, Firebase, secrets or CI versionCode-strategy change.

## 5.12.24 — Adaptive, non-destructive Motore timing
- Replaced the universal 10-minute Motore correctness cutoff with workload-aware timing. Ten minutes remains the product target for a small/ordinary scroll.
- The timing estimate is based on actual eligible listings and the deliberately conservative Vinted public-page pace: minimum 10 minutes, otherwise roughly 5 minutes base plus 55 seconds per eligible listing.
- This was the first non-destructive timing model; 5.12.25 supersedes its raw eligible-listing estimate with measured core Vinted work and adds fair run rotation.
- Elapsed time alone no longer auto-excludes listings, hides deals or completes a run. A run finishes only when its automatic content is actually settled.
- Timing overrun is now diagnostics-only via `engine-timing-v2`; existing job watchdogs/retries and deterministic classification/matching outcomes remain responsible for real failure handling.
- Motore UI now labels remaining time as an estimate instead of promising a hard deadline; waiting-scroll copy no longer claims a fixed ten-minute release.
- Updated the 5.12.21 product regression to the superseding timing contract and added `regression/engine_adaptive_timing_v51224.py`.
- No schema migration, request-rate increase, signing, applicationId or CI versionCode-strategy change.

## 5.12.23 — Review truth, bounded fuzzy BGG and idle queue cleanup
- Pixel validation of 5.12.22 confirmed the exact-title BGG path is fixed: exact lookup produced zero timeouts, BGG review was zero and the current install had zero crash/ANR/memory exits.
- Replaced full-catalog fuzzy rescoring with a compact token-postings index and a bounded candidate pool selected from rare query tokens.
- Historical BGG revalidation no longer creates Motore human-review work. Existing historical review flags are cleared once while the corresponding legacy deals remain `MATCH_UNCERTAIN` and excluded from trusted surfaces.
- Review diagnostics now distinguish explicit/manual, BGG variant, historical inbox debt, historical held debt, other Vinted review, BGG technical and BGG genuine review.
- Fixed idle Vinted queue truth: runnable counts and next-due timing now use the same source gate as the actual job claimer.
- Ordinary Vinted jobs left materialized after Motore becomes idle are parked back into deferred state, and the maintenance sweep no longer recreates ordinary network jobs while no scroll owns them.
- Added diagnostics for historical-review cleanup, idle-job parking, token-index load time and fuzzy candidate counts.
- Added `regression/review_fuzzy_queue_truth_v51223.py` and wired it into PR/beta CI.
- No schema migration, request-rate increase, signing, applicationId or CI versionCode-strategy change.

## 5.12.22 — Compact BGG exact index and no technical timeout review
- First field diagnostic after 5.12.21 confirmed Motore now closes cleanly at the product SLA: no active/waiting run remained and five unresolved ordinary rows were expired at the 10-minute ceiling, with no current-install crash/ANR/memory exit.
- The same run exposed 29 exact BGG scans and 29 exact-scan timeouts; those technical timeouts were being written as BGG review.
- Replaced repeated 31k-game exact scans with a compact primitive hash index built once with the queue-process catalog; candidate hash collisions are verified against original names/aliases before acceptance.
- Excluded one-time catalog/index bootstrap time from the per-query fuzzy safety timer.
- BGG matcher timeouts and technical exceptions no longer create human review; they are automatically quarantined from trusted surfaces and retained in diagnostics.
- Added a one-time recovery for 5.12.21 timeout/error review rows so they are reopened under the new exact-index path instead of remaining permanent review debt.
- Added `reviewBreakdown` diagnostics and timestamp/build metadata for `lastClassifierBlock`, preventing stale pre-update classifier evidence from being mistaken for current behavior.
- Added `regression/bgg_exact_index_no_timeout_review_v51222.py` and wired it into PR/beta CI.
- No schema migration, request-rate increase, signing, applicationId or CI versionCode-strategy change.

## 5.12.21 — Product UX turnaround: 10-minute Motore SLA and trusted results
- Added a hard 10-minute ownership ceiling for ordinary Motore runs. Incomplete automatic rows are parked reversibly so one difficult listing cannot hold later scrolls for hours.
- Automatic Vinted misses and unresolved automatic BGG variants no longer become routine manual-review work. Explicit Hunt/manual requests keep the recovery path; ordinary ambiguity is auto-excluded.
- Optional deep Vinted metadata no longer blocks a card whose BGG and exact Vinted identity are already complete.
- Added a one-time non-destructive cleanup for automatic review debt created by older builds while preserving explicit Hunt/manual intent.
- Tightened the BGG review band and added conservative seller-title suffix normalization for obvious descriptive titles.
- Added explicit videogame/platform exclusions and narrowed accessory/component detection so words such as “tessere” or “dadi” in a full-game description do not cause false component filtering.
- Scopri and companion recommendations now use trusted-only results: fully matched identity, no review and no open core job. Hunts use the same trust checks without requiring resale-tier pricing.
- Motore cards now open the standard product detail instead of a special two-button modal. The product detail exposes the unified BGG/Vinted/correction actions, including “Gioco sbagliato”.
- Hunts can promote exact watched games even when the price is merely average; an explicit max price is still respected.
- Diagnostics add current-run age, SLA time remaining, review percentage and SLA-expiry telemetry.
- Added `regression/product_turnaround_sla_trust_v51221.py` and wired it into PR/beta CI.
- No schema migration, request-rate, signing, applicationId or CI versionCode-strategy changes.

## 5.12.20 — Engine runtime cross-process telemetry
- Pixel validation of 5.12.19 showed no current-install crash/ANR/memory exits and confirmed the stale Motore analysis-pending count is fixed.
- Replaced process-local engine/service readiness diagnostics with authoritative SQLite-backed `queue_controls` state so `:ui` cannot report a stale `:radar` SharedPreferences cache.
- Top-level diagnostics now derive `serviceConnected`, `engineReady` and `engineGames` from cross-process runtime state when available.
- Added bounded JsGameEngine bootstrap tracing: WebView creation/attach/page completion, sampled verify snapshots, ready/timeout state and JavaScript console errors.
- Verify snapshots report document readiness plus bridge/catalog presence and catalog game count, making the next Pixel result sufficient to distinguish a real engine failure from stale telemetry.
- Added `regression/engine_runtime_cross_process_v51220.py` and wired it into PR/beta CI.
- No queue semantics, database schema, signing, applicationId, request-rate or CI versionCode-strategy changes.

## 5.12.19 — Queue single owner and Motore analysis ordering
- ACTION_NOW now starts only the foreground queue owner and no longer also enqueues one-shot WorkManager work.
- QueueDrainWorker stands down before opening SQLite whenever the foreground queue is starting or running; WorkManager is recovery-only.
- Pending classifier batches are scoped to the oldest active Motore observation run, preventing a newer waiting scroll from consuming JS analysis ahead of current work.
- RAM Accessibility hints no longer bypass persisted current-run ordering; waiting scrolls are rechecked every 5 seconds and can advance without another Vinted visit.
- JsGameEngine readiness retries in a bounded, single-chain loop every 500 ms for up to 30 seconds instead of failing after one cold-WebView check.
- Added `regression/queue_single_owner_engine_order_v51219.py` and wired it into PR/beta CI.
- No schema migration, signing, applicationId, request-rate or CI versionCode-strategy changes.

## 5.12.18 — WorkManager/main-thread stability
- Pixel validation confirmed unique-card acquisition counts, but Android recorded a fresh default-process ANR 47.8 seconds after the 5.12.17 package update: `No response to onStartJob ... SystemJobService`.
- Moved queue-service database/reconcile/sweep/supervisor work off Android Service callbacks onto a serialized control executor; `startForeground()` remains immediate and `onStartCommand()` now returns without synchronous SQLite work.
- Moved default-process WorkManager enqueue operations out of `BroadcastReceiver.onReceive()` via `goAsync()`, with an additional main-looper dispatch guard in `QueueWorkScheduler`.
- WorkManager recovery now stands down while the foreground queue owner is still cold-starting, reducing SQLite startup contention.
- Motore analysis progress now uses canonical market-listing state rather than stale raw duplicate observation rows, preventing already-analysed cards from keeping an older job alive.
- System-exit diagnostics v3 report crash/ANR/memory counts since the current APK install boundary, while retaining epoch and 24-hour history.
- Added `engineWaiting` diagnostics for the first queued scroll behind the active job.
- Added `regression/workmanager_mainthread_stability_v51218.py` with an executable SQLite stale-pending fixture and main-thread ownership guards; wired it into PR and beta CI.
- No database/schema migration, signing, applicationId, request-rate or CI versionCode-strategy changes.

## 5.12.17 — Acquisition dedupe and crash stability
- Fixed Motore card-count inflation caused by repeated Accessibility renders of the same visible Vinted cards. Same title/brand/price sightings and re-analysis are suppressed for 10 minutes, with the observation dedupe persisted in SQLite so it survives `:radar` restarts.
- Motore now reports unique Vinted cards in the hero, job detail header and daily chronology; raw observation events remain available only in diagnostics.
- Hardened crash journaling so the minimal crash header is flushed before stack formatting, improving evidence retention when the fatal condition is memory pressure.
- System exit-history diagnostics now ignore WebView sandbox processes, scope fresh crash/ANR/memory counts to the current engine epoch, and expose Ludo-process PSS/RSS plus a compact recent-exit history.
- Added a hard 2.5 s budget to queue-process local BGG exact/fuzzy scans. An incomplete timed-out scan cannot produce an automatic match; it becomes optional review instead.
- Added `regression/engine_acquisition_crash_stability_v51217.py` and wired it into PR and beta CI.
- No schema migration, request-rate, signing, applicationId or CI versionCode-strategy changes.

## 5.12.16 — Motore epoch, progress semantics, queue startup stability
- Added a non-destructive Motore operational epoch so old timeline/review debt no longer appears as current work; observations, prices and authoritative matched data remain stored.
- Old automatic queue jobs are completed at cut-over while explicit Hunt work is preserved. Legacy review flags and unresolved provisional BGG review rows are archived from the active workflow.
- Fresh sightings can revive an archived provisional BGG title and re-evaluate it with current logic.
- Fixed stuck jobs caused by counting BLOCKED_CLASSIFIER observations as analysis still pending.
- Reworked Motore around job progress instead of games found; ambiguous cases are optional and non-blocking.
- Hardened QueueKeepAliveService startup against crash loops and extended crash diagnostics with startup phase plus root cause.
- Added engineEpoch diagnostics and regression/engine_epoch_progress_stability_v51216.py.
- No raw observation deletion, network-rate change, signing/applicationId change, or CI versionCode-strategy change.

## 5.12.15 — Crash diagnostics + executable SQLite integration
- Added application-wide crash journaling so UI, radar and queue/default processes are all covered instead of only MainActivity.
- Added Android system exit-history diagnostics on API 30+ to identify actual process deaths as CRASH, ANR, memory-related or other exit reasons.
- Added `processCrashJournal` and `systemExitHistory` to the technical diagnostic output.
- Added an executable sqlite3 BGG state-machine integration test that reproduces the previously missed affinity bug and verifies current/historical work drains after transitions.
- Added a dedicated regression for multi-process crash diagnostics and wired both new tests into PR and beta CI.
- Shifted Pixel usage to final real-device validation rather than repeated debugging of deterministic SQLite behavior.
- No schema/data reset, request-rate, signing, applicationId or CI versionCode-strategy changes.

## 5.12.14 — BGG algorithm-version numeric affinity
- Fixed a SQLite comparison bug that could keep review rows permanently eligible for re-matching even after `match_algorithm_version` was updated to the current algorithm.
- All version predicates now cast the bound parameter to INTEGER explicitly, including current matcher selection/counting and historical revalidation selection/counting.
- Added `bggMatchBreakdown` diagnostics separating pure required identities, legacy review rows and current review rows.
- Added `regression/bgg_version_affinity_v51214.py` and wired it into PR/beta CI.
- No schema/data reset, request-rate, signing, applicationId or CI versionCode-strategy changes.

## 5.12.13 — BGG cold-index + single-flight
- Removed the queue-process global all-alias exact-name map that could dominate BGG matcher cold start after an APK/process restart.
- The local catalog is now parsed once into shared game objects plus a direct BGG-id map; exact title/alias lookups are lazy in-memory scans cached in a bounded 64-query LRU.
- Preserved queue fuzzy matching on the same shared catalog and the disk-streaming manual/UI path.
- Added single-flight protection so QueueKeepAliveService and WorkManager cannot execute duplicate local BGG identity batches concurrently.
- `bggLocalMatch` v3 now reports index load time and exact-cache telemetry for Pixel validation.
- Added `regression/bgg_cold_index_singleflight_v51213.py` and updated older index regressions for the new architecture.
- No schema/data reset, request-rate, signing, applicationId or CI versionCode-strategy changes.

## 5.12.12 — BGG review-write accountability
- Added transactional, directly observable accounting for the three provisional BGG rows that still appeared stuck after the state-monotonicity fix.
- `markBggMatchReview()` now validates the current row inside its write transaction, protects a concurrent authoritative match, returns the actual write count, and keeps linked listing review state in the same transaction.
- `bggLocalMatch` now distinguishes review decisions from successful writes and reports remaining required identities after each batch.
- Added `bggReviewWrite` diagnostics with before/after state, BGG id, visibility and changed-row counts.
- Added `regression/bgg_review_write_accountability_v51212.py` and wired it into PR and beta CI.
- No schema/data reset, request-rate, signing, applicationId or CI versionCode-strategy changes.

## 5.12.11 — BGG state monotonicity
- Fixed a state-race exposed by Pixel Test 5: repeated/stale analysis could reset a game already moved to BGG review back to `BGG_MATCH_REQUIRED`, which permanently blocked historical revalidation behind the same three current titles.
- Provisional re-analysis may now refresh only unresolved states. `BGG_MATCH_REVIEW` and `AUTO_QUARANTINED` are monotonic and cannot be silently reopened, even by a late `status=matched` analysis result.
- `markBggMatchReview()` now updates canonical game state and active linked listing state in one SQLite transaction.
- Stale analysis results no longer mutate inactive listings; a listing tied to an already quarantined game is refiltered rather than resurrected.
- `reconcileQueue()` repairs legacy game/listing drift for review and auto-quarantine states without deleting records.
- Added `regression/bgg_state_monotonicity_v51211.py` and wired it into PR validation and Android beta CI.
- No schema/data reset, no Vinted/BGG rate change, and no signing/applicationId/CI versionCode-strategy change.

## 5.12.10 — BGG fuzzy index performance
- Fixed the remaining local BGG matcher full-scan path: queue fuzzy matching no longer reopens/decompresses/scans the ~31k-game gzip catalog once per query.
- Added a queue-only in-memory fuzzy scorer over the catalog objects already retained by exact/id indexes, with a bounded top-16 heap and 32-query LRU cache.
- Preserved the existing disk-streaming fuzzy path for short-lived/manual clients to avoid reintroducing a large retained catalog into UI processes.
- Added `bggLocalMatch` diagnostics with batch counts and elapsed time so local matcher stalls can be measured on Pixel.
- Added `regression/bgg_fuzzy_index_performance_v51210.py` and wired it into PR and Android beta CI.
- No schema/data reset and no signing, applicationId, CI versionCode strategy, Vinted request rate or BGG network-rate changes.

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
