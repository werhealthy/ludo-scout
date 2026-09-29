# Ludo Scout — AI handoff

## 5.12.77 — Queue liveness diagnostics
- Copyable diagnostics now expose the queue supervisor heartbeat, the latest WorkManager recovery attempt and decision, and the age of the oldest due Vinted job.
- Instrumentation only; queue thresholds, priorities, publication behavior, and historical data are unchanged.
- Regression: `regression/queue_liveness_diagnostics_v51277.py`.

## 5.12.76 — Discover visual polish
- Scopri now uses generated lavender, cream and apricot artwork behind the featured opportunity; live game title, BGG rating, price and discount remain real listing data.
- The featured offer alone uses a perspective box composed from the BGG cover. Latest listings use full-bleed listing photos with publication age and price underneath; the BGG favorites section is a vertical ranked list.
- Category tiles use the generated illustrated PNG sheet, hide item counts, and open a full category directory. Tapping a category still routes to the verified Database filter.
- The four-item bottom navigation uses a selected lavender pill and rounded heavier labels. Other screens retain their own shell.
- Regression coverage: `regression/discover_visual_polish_v51276.py`, run in PR validation and beta build. Check actual phone layout for image crop, hero text fit, and scroll/nav spacing after Firebase distribution.
- No changes to trusted-surface eligibility, price evaluation, Vinted observation or listing history.

## 5.12.75 — Discover editorial home
- Scopri uses the warm, compact visual language of the supplied Home reference. Other screens keep their current shell.
- Category tiles are aggregated from actual categories on visible, matched BGG games already stored locally; tapping a tile opens Database with that category query.
- Home rails use trusted active listings: latest listing publication labels, highest BGG ratings among those listings, and discoverable value decisions. Featured opportunity is the only faux-3D cover; all other artwork remains flat.
- Price evaluation, trusted-surface eligibility, Vinted observation and history are unchanged.
- Regression coverage is in `ux_system_v1_v51239.py`. Android PR validation must pass before integration; verify the warm system bars, category navigation, empty/loading images and scroll rails on Pixel after build.

## 5.12.68 — Pricing / intake safe mode
- Product decision: local Vinted asking-price history is temporarily evidence-only. It is still collected for charts/audit, but it cannot blend with or replace the BGG used-price reference used by DealEvaluator.
- Fresh analyses no longer call GameAnalysis.withUsedMarketBenchmark from Accessibility. Targeted local-Vinted repricing is disabled, and the periodic deal rebuild uses only fresh BGG market stats or the bundled BGG used-price index.
- Existing rows removed as PRICE_FILTERED are re-evaluated against BGG-only evidence and may be restored when that authoritative benchmark says the price is still eligible. Raw price history is never deleted.
- Known BGG candidates below 6 are removed before automatic match/review. Authoritative BGG metadata also excludes Children's Game entries from Database, review, Vinted linking and active deal surfaces while preserving observations/history. Asmodee or any publisher is not itself an exclusion criterion.
- Vinted Accessibility intake is explicit opt-in per foreground Vinted session. An accessibility overlay appears as “Ludo · OFF”; tapping it switches to “Ludo · SCANSIONE ON”. Leaving Vinted resets OFF. While OFF, ordinary Vinted tree/card parsing and new Motore observations are disabled.
- Diagnostics now expose scanOptIn and pricingSafeMode=BGG_ONLY. Game detail labels local Vinted prices as historical evidence rather than a decision benchmark.
- No new Android overlay permission is required: the control uses TYPE_ACCESSIBILITY_OVERLAY from the already-authorized AccessibilityService.
- Existing Motore queue-stall investigation remains separate: 5.12.68 does not claim to fix the stale-lane/ANR evidence observed on 5.12.67.
- Next product step after Pixel validation: redesign historical listing cards so stale Vinted URLs are not implicitly actionable, preserve/review local photos, and expose explicit association-cleanup actions.

## 5.12.67 — Queue stall recovery
- Field debug after ~3 days without fresh Vinted input showed an active Motore run ~252M ms old with one DEFERRED_LINK remaining, zero runnable Vinted jobs, and Vinted/BGG lane heartbeats ~26M ms stale. The issue was queue liveness, not legitimate processing time.
- Root cause 1: QueueDrainWorker returned immediately whenever QueueKeepAliveService.isRunning() was true, making its later lane-health check unreachable. A living default process could therefore suppress WorkManager recovery even when the service consumer lanes were stalled.
- Root cause 2: WorkManager recovery could claim persisted jobs but never materialised DEFERRED_LINK rows for the active run, so a deferred-only scroll could remain unfinished indefinitely after the foreground lane stopped.
- Recovery now stands down only during service cold start. Once the service reports RUNNING, WorkManager inspects SQLite-backed lane heartbeats and active-run deferred work; stale lanes can be taken over after the bounded heartbeat window.
- Recovery materialises deferred Vinted identities for the current Motore run before attempting a claim. The foreground supervisor also treats active-run deferred rows as liveness demand.
- Vinted pacing, request budget, matching thresholds, publication gates, fairness ownership, schema, signing and applicationId are unchanged.
- Pixel validation: after installing, do not open Vinted. Confirm the existing old scroll starts advancing on its own, queue lane heartbeat age stays under a few minutes, and active/waiting scroll count eventually drains without new Accessibility events.

## 5.12.66 — Motore outcome-oriented redesign
- Motore is now organized around five user questions: what Ludo is doing now, what this scroll already produced, whether the user must intervene, which other scrolls remain unfinished, and recent activity.
- The overview no longer renders a percentage/progress bar or the five-stage technical funnel. Current work is expressed with concrete states such as acquisition, game recognition, Vinted linking, paced waiting, result preparation and completion.
- Ready counts are always scoped explicitly to the current scroll. The UI no longer labels a run-local count as the global Catalog total.
- Actionable manual review is a separate conditional “Serve il tuo aiuto” inbox. Non-actionable held outcomes are described quietly as “non pubblicati automaticamente”.
- Other unfinished runs are exposed with user-facing states “Riprenderà” and “In attesa”; fairness, scheduler, lane and retry internals remain diagnostic concepts.
- The run inspector is outcome-oriented: Tutti / Pronti / In lavorazione. Individual cards use Pronto / In lavorazione / Serve una tua scelta / Non pubblicato automaticamente.
- A dedicated “Lavoro automatico” drill-down explains the three product stages without exposing queue internals.
- Existing asynchronous Activity snapshots remain the UI data boundary; the redesign adds no synchronous SQLite reads on the Android main thread.
- No change to matching thresholds, publication gates, Vinted pacing, fairness ownership, schema, signing, applicationId or CI versionCode strategy.
- Pixel validation required: verify overview hierarchy and navigation during acquisition, Vinted pacing, multi-scroll fairness, manual review, completion and idle states.

## 5.12.65 — Manual Vinted UI ANR
- Field evidence on 5.12.64: `systemExitHistory` reported 6 UI ANRs after the install boundary, all input-dispatch timeouts; `activitySnapshot` took 7.3s and `vintedRequestLedger` hit `SQLiteDatabaseLockedException`.
- Root cause in the manual recovery path: returning from Vinted via Android share and confirming a manual candidate still executed MarketStore/DealDatabase reads and writes synchronously on MainActivity. The DB intentionally has an 8s SQLite busy timeout, longer than Android’s ~5s input-dispatch ANR threshold.
- Manual share lookup and persistence now run on a dedicated `manualLinkIo` executor. UI callbacks only show confirmation/result, dismiss dialogs and schedule refresh/queue work.
- Durable Vinted snapshot reuse no longer calls `getWritableDatabase()+CREATE TABLE IF NOT EXISTS` on every read; missing tables simply fall back to normal HTTP.
- Request-ledger diagnostics are read-mostly and only request a writer when the current ledger epoch has not been initialized.
- No schema migration, request pacing, matching threshold or publication-rule change.
- Pixel validation: complete at least 3 manual Vinted links, including at least one Vinted Share → Ludo round trip. PASS: no UI freeze/ANR and links persist. Then copy diagnostics and confirm `anrAfterInstall=0`.

## 5.12.64 — Vinted request efficiency
- The 5.12.63 field ratio `linkRequestsPerNewLink=15.46` was not a reliable current-throughput measurement: the ledger spanned older builds and inferred successes from the current number of active linked rows, so sold/unavailable cleanup could reduce the denominator.
- The resolver now reuses a durable catalogue snapshot before spending a new catalogue-page request, but only when the snapshot is fresh relative to the listing observation and the same two-query ordering can be proven. Weak or ambiguous snapshots fall back to the existing network path.
- Durable snapshot candidates never use the structured-catalog fast path. Exact public item-page verification remains mandatory for this reuse path, preserving identity and product-safety gates.
- Request ledger v3 starts a fresh epoch and counts actual resolved-link events, including zero-network batch links, so `linkRequestsPerNewLink` is monotonic with respect to sold cleanup.
- Vinted pacing remains 55 seconds and the hourly ceiling remains 60. No matcher threshold, publication gate, database schema or private API policy changes.
- Field validation: after several new resolutions, compare `resolvedLinks`, `linkPhysical`, `catalogPhysical` and `linkRequestsPerNewLink`; confirm some resolver runs report `linkVerifyMode=durable-snapshot+public-page`.

## 5.12.63 — Opened Vinted verification priority
- Field evidence on 5.12.62 showed an exact opened-listing fallback queued for listing 4512 while an unrelated Motore run still owned the ordinary Vinted lane. The queued fallback could therefore wait behind run ownership instead of confirming a sold/unavailable page immediately after the user returned to Ludo.
- `OPENED_VERIFY` is now treated as interactive exact work in the Vinted claim gate and sorts ahead of ordinary automatic run jobs. Existing public-page pacing and hourly budget remain unchanged.
- Existing sold reconciliation remains authoritative: a confirmed sold/gone exact page marks the canonical listing SOLD, completes its jobs, removes it from active product surfaces and preserves historical observations/price evidence.
- Regression: `regression/opened_vinted_verification_priority_v51263.py`, also wired into PR and beta CI.
- Pixel validation required: open a known Catalog listing on Vinted that is sold/unavailable, return to Ludo, wait through at most the next paced request, and confirm the listing disappears from active Catalog while historical market evidence remains.

## 5.12.60 — Authoritative Vinted miss breakdown
- Debug derives a mutually exclusive breakdown for every active listing without a Vinted URL directly from shared SQLite state.
- `notBggQualified` is kept separate from eligible Vinted outcomes; eligible categories reconcile exactly to queued, awaiting attempt, no candidate, ambiguous, weak match, unavailable, throttled, verification failed and other.
- This is diagnostic-only: no matcher threshold, queue behavior, network budget or publication gate changes.

## 5.12.59 — Startup processing-lease recovery
- A freshly created QueueKeepAliveService now reopens every inherited PROCESSING lease before reconciliation. No in-memory worker from the previous killed process can still own those rows.
- Recovery preserves attempts, source, priority, listing identity and backlog; rows become FAILED_RETRYABLE instead of being deleted.
- The existing three-minute runtime watchdog remains responsible for work that stalls after startup.

## 5.12.58 — Alternate-title Vinted photo proof
- The linker records whether title confidence came from the observed Vinted title or an exact canonical BGG title after removing the known brand.
- Canonical-only matches require the observed exact price and thumbnail similarity of at least 0.84, and they cannot use the catalogue fast path: the public item page is still verified.
- Photo evidence now runs for a single strong candidate as well as ambiguous candidate sets.
- No BGG rating, price-opportunity or catalog-publication threshold changed.

## 5.12.57 — Engine UI clarity
- Motore overview now exposes the current scroll as a five-stage funnel: unique Vinted cards, eligible games, BGG-confirmed games rated 6+, exact Vinted links and Catalog-ready results.
- The hero uses a descriptive working/completed state instead of the ambiguous processed fraction. Cyan means automatic work is still progressing; orange is reserved for human decisions.
- Manual-review cases are explicitly separate from automatic processing and do not imply that the whole engine is blocked.
- The funnel reuses EngineOverviewSnapshot fields already loaded on uiDataIo; it adds no direct SQLite reads to rendering.
- This change improves truth and comprehension only. It does not loosen identity, rating, price or publication gates. Pixel visual validation is required.

## 5.12.55 — Diagnostic funnel and process-exit reasons
- The 5.12.54 Pixel sample proved fresh accessibility intake: Vinted events, parsed cards and stored analyses increased. The legacy funnel did not identify the reasons new active listings failed later gates.
- `catalogPipeline` now adds first-seen active listing counts for the last 24 hours across pending analysis, classifier blocks, uncertain product type, ambiguous or absent BGG identity, rating, visibility, exact Vinted identity, review and core publication eligibility. These are listing/fingerprint counts, not unique games, and later-stage counts can overlap.
- Android exit diagnostics now separate `REASON_LOW_MEMORY` from `REASON_EXCESSIVE_RESOURCE_USAGE`. Previous `memory*` counters combined both causes, so historical values included CPU-resource terminations.
- Diagnostic-only change; it does not relax product, rating, identity, pricing or publication gates. Pixel validation is still required.

## 5.12.51 — Remote fairness and conversion funnel
- 5.12.50 user debug showed oldest active run ~44 hours old with 14 waiting runs and ~24 core remaining; fairness was only 27s into a ten-minute slice after process restart. All 12,816 parsed cards had zero explicit Vinted IDs captured by Accessibility. Request ledger roughly 12.86 physical requests per linked item. Thus exact-link lookup is a hard publish bottleneck.
- Important timing distinction: `lastVintedEventAgeMs` ~6,141,848 ms on a 5.12.50 build with `radarService.ageMs` ~747,194 ms means the reported counters predated that installation. No new Vinted scroll after install was established.
- 5.12.51 reduces remote run fairness slice from ten minutes to 90s only when another session is waiting. Serial public-page budget and job durability remain unchanged.
- `catalogPipeline={active=...;pendingAnalysis=...;bggQualified=...;localOnly=...;deferredLink=...;exactVintedLink=...;coreQualified=...}` counts each block independently, not disjoint buckets. Compare only after fresh install and new Vinted scroll.
- Field-validate whether run cursor rotates and whether coreQualified/new catalog deals increase. If not, focus exact link verification throughput; do not loosen verification or invent links.


## 5.12.50 local intake and summary query (pending Pixel validation)
- 5.12.49 field debug: 2 post-install UI ANRs, 1 memory exit, excessive UI CPU. Activity indicator was async (371 ms), so this change focuses on the day-summary N+1 query path (snapshot ~5907 ms).
- `recentObservationDays()` now counts temporal observation bursts directly; it no longer constructs up to 100 detailed session snapshots per day just to count them. Detailed session history remains available separately.
- `pendingAnalysisCards()` no longer requires the owner of the remote Vinted run: local BGG/JS classifier processes newer captured observations in newest-first 8-card batches every 1.5s. No new Vinted network requests or bypass of serialized public pacing.
- Diagnostic `localAnalysisLastBatchAgeMs; size=` tracks local intake. `classifierBlocked` still counts all UNCERTAIN or otherwise unpriced sightings; positive publisher evidence now includes 999 Games, Z-Man Games, Keymaster Games and Just Games. Publication still needs product/type/identity verification.
- Confirm that `analysisBatches` and `analysesStored` rise with fresh `cardsParsedTotal` while older remote runs progress separately. Track `anrAfterInstall`, memory and Activity snapshot elapsed times.


## 5.12.49 UI indicator ANR guard (pending Pixel validation)
- 5.12.48 field debug proves LOCAL_ONLY run unblocking worked: active Motore run moved forward from the Marracash run to start=1790003455573.
- The same debug recorded 3 post-install UI ANRs. `activitySnapshot` took 6685 ms but already executes on `uiDataIo`, so that number is background latency rather than direct proof of main-thread blocking.
- A separate synchronous path remained: `updateActivityIndicator()` called `jobSummary`, review counts, pause state and priority count on the main thread. It is invoked during `makeActivityButton()` at startup and on navigation to Activity.
- 5.12.49 replaces those synchronous DB reads with a cached `ActivityIndicatorSnapshot` loaded on `uiDataIo`. Initial/stale UI paints immediately from cache/default state; DB completion updates the badge later.
- Diagnostics expose `activityIndicatorState`; regression prevents SQLite-backed MarketStore reads from re-entering the main-thread indicator method.


## 5.12.48 local-only session recovery (pending Pixel validation)
- Root cause from 5.12.47 field debug: the active Motore scroll contained four `LOCAL_ONLY` Marracash listings, which are explicitly not runnable remote jobs; the engineRangeCounts query nevertheless counted them as outstanding Vinted identity work, trapping the oldest run while ten later scrolls waited.
- A `LOCAL_ONLY` listing is now a non-published, non-actionable hold for Motore progress. It remains in market history and can be re-projected to `DEFERRED_LINK` if eligibility changes during later analysis. `DEFERRED_LINK` remains active remote work, never time-completed.
- Radar diagnostics expose age of last actual Vinted Accessibility event. No new `cardsParsedTotal` and no fresh event after installing a build means the classification changes were not tested.
- Regression: `regression/engine_recovery_truth_v51230.py` protects LOCAL_ONLY completion and event telemetry.

## 5.12.47 stability + intake recovery (pending Pixel validation)
- Cross-process SQLite contention is expected between :radar, default queue and :ui. DealDatabase configures an 8-second busy timeout; the Vinted lane treats SQLiteDatabaseLockedException as bounded backpressure instead of a FAULT state.
- ListingClassifier accepts a narrow allow-list of known board-game publishers/brands as positive marketplace evidence. This only opens the existing identity/verification pipeline; BGG identity and structured marketplace checks still gate publication.
- Explicit expansions are retained as observations but are not identity/price/publication candidates and must not create human review work.
- Regression: pipeline_integrity_v51242.py guards publisher evidence, expansion suppression and SQLite busy timeout.
- Pixel validation should compare queueLanes.vinted, processCrashJournal SQLite locks, catalog growth after a controlled scroll, review composition and systemExitHistory ANR/memory counters.


## Source of truth
The private GitHub repository `werhealthy/ludo-scout` is now the source of truth.
Do not reconstruct the project from an older ZIP when the repository is available.

## Current baseline
- App: Ludo Scout Android
- Package / applicationId: `it.vintedaffari.app`
- Baseline version: `5.12.68-safe-mode`
- versionCode: `170`
- compileSdk / targetSdk: 35
- minSdk: 28
- Java: 17
- Gradle wrapper: 8.9
- Android Gradle Plugin: 8.7.3
- Current development stage: product turnaround. The core contract is scroll → trustworthy output in minutes. Ten minutes is a small-scroll target and, under contention, a fairness service slice; it is never a correctness deadline.

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

## 5.12.46 Activity ready-state recovery (pending Pixel validation)
- A database snapshot is fresh from completion, never from query start. Slow SQLite reads must not arrive already expired and recursively trigger another placeholder/load cycle.
- Activity uses stale-while-revalidate: once a coherent snapshot exists, background refresh never replaces it with an empty loading page.
- The overview fetches only the three history days it renders. Full history remains a separate explicit surface.
- The Activity route must skip the SQLite-backed global indicator because the indicator button is hidden there; queue broadcasts follow the same rule.
- Snapshot failures back off for five seconds, retain an explicit error state, and surface `activitySnapshot={state,error}` in copied diagnostics.
- Regression: `regression/activity_snapshot_v51245.py` covers transitive main-thread ownership, completion-time freshness, stale-snapshot visibility, bounded retry and diagnostics.

## 5.12.42 pipeline integrity recovery (pending beta validation)
- Repository baseline inspected for this recovery: main and beta had the same source tree at 5.12.41. The field build 5.12.37-ux-library-ludo-cardfix predates this branch and must not be used as proof that later GitHub UX changes fixed a pipeline defect.
- Product type, BGG identity, exact Vinted identity and publication eligibility are now distinct gates. A title match only nominates BGG identity; automatic publication additionally requires positive marketplace game evidence and compatible authoritative BGG item type.
- ListingClassifier is fail-closed: unknown marketplace text is UNCERTAIN, may retain bounded identity evidence, but cannot enter price/publication as an implicit base game. This removes cross-category collision dependence on title blacklists.
- Product-page category/catalog accessibility semantics are retained as raw/normalized/source/confidence/timestamp evidence when Vinted exposes them. Feed cards remain category-unknown. A structured non-game category reversibly auto-filters the exact canonical listing.
- BGG XML metadata now carries BGG type; base game ↔ boardgame and expansion ↔ boardgameexpansion are validated before catalog readiness. Mismatch is retained as TYPE_MISMATCH, not silently converted.
- Catalog semantics are explicit: storedAll is every active legacy row; eligible is exactly getDeals("trusted_any_price"); visible is eligible after the current query/preset/filters. Empty state and count are derived from eligible, never from storedAll.
- Reconciliation ownership is serialized through the foreground control lane. UI activity wake and Vinted/BGG consumer loops no longer each invoke the expensive global reconcile. Screenshot crops/file writes move off accessibility callbacks and feed capture is capped at eight viable candidates.
- DB migration 21 is additive only: category provenance columns on market_listings; no reset or history deletion. New regression: regression/pipeline_integrity_v51242.py plus executable BGG compatibility fixtures.
- Pixel validation still required after beta build: compare uiLastRenderMs, queue SQLITE_BUSY/ANR/memory exits, catalogEligible vs catalogVisible, category capture rate, and 150–250-card scroll behavior. Do not change Vinted public-hour budget.

## 5.12.35 pricing / bundle product invariants
- User-facing deal decisions are centralized in `DealEvaluator`: Offertona, Buon prezzo, Prova un'offerta, Prezzo giusto and Pochi dati. `REJECT` is internal only; it removes the listing from product surfaces while preserving market history.
- “Typical used price” means median. Q25 is a separate low-market band. The compact BGG used-price fallback reads its median column.
- 5.12.68 safe mode supersedes local repricing: Vinted asking prices are history/audit only. Deal decisions use BGG used-price evidence until identity cleanup is explicitly validated and local pricing is deliberately re-enabled.
- Offer targets solve for a good all-in total and must remain within a plausible 5–15% reduction.
- Discover is selective; Catalog may retain fair/insufficient-data rows. Explicit Hunt intent remains separate from normal deal filtering.
- Bundle prospecting may start from a strong game even when the single purchase is not itself a bargain. Existing BundleExploration intent is reused; do not add aggressive seller scraping.
- A bundle with unknown package shipping must not show an exact total or all-in saving.
- Notifications remain stricter than cards: exact identities, existing safety gates, GREAT_BUY, and the legacy 30% all-in saving threshold.

## Current engine invariants
- Vinted Accessibility intake is explicit opt-in: opening Vinted shows a small Ludo control OFF by default; only SCANSIONE ON may create ordinary observations/Motore scrolls, and leaving Vinted resets OFF.
- No anti-bot bypass, CAPTCHA bypass, cookie stealing, private abusive API use or artificial request-rate increase.
- Prefer local filtering, caching, batching and snapshot reuse before public-page requests.
- Known BGG rating below 6 is filtered before match/review/linking; authoritative BGG Children's Game metadata is also outside the scouting database. Publisher alone is never a quality exclusion.
- Exact Vinted identity and exact BGG identity are separate facts.
- Elapsed time alone must never classify, hide, exclude, complete or discard a listing. Correctness comes only from matching/classification/trust state.
- Motore timing is workload-aware: the target uses core Vinted identity work, while the live ETA counts exact Vinted identities still missing even when their durable job is temporarily parked. The displayed `~N min di corsia` is service-lane work, not a wall-clock promise when fairness rotates multiple scrolls.
- One ordinary run owns the automatic Vinted/BGG lane at a time. If another unfinished scroll is waiting, the owner yields after a 10-minute service slice; unfinished listings remain intact and the run resumes in round-robin continuation. With no competing run, it simply keeps working past ten minutes.
- LIVE_DEAL, HUNT_PRIORITY and explicit manual work preempt ordinary backlog work when runnable. A future urgent retry may reserve at most one public-page slot (~65 s); it must never freeze already-runnable Motore work for minutes.
- Manual review is reserved for cases with an actual user action available. Historical/trust holds stay non-publishable but are not counted or labelled as actionable review.
- Manual Vinted recovery is target-only: search-result cards visible during the recovery flow must not create ordinary observations/jobs. Exact Vinted identity still requires an exact item URL/id; a single visible search result is not sufficient evidence by itself.
- Clearly overpriced ordinary listings may be filtered before Vinted network identity only under a conservative ask-price gate (at least 2× and €25 above an existing used-market reference). HUNT_PRIORITY and MANUAL_PRIORITY are exempt.
- A ready card requires confirmed BGG, exact Vinted item/url, core identity enrichment, no open review and no open core automatic job. Optional deep metadata must not block readiness.
- Publication date, language and price should be retained when technically available; do not silently drop required data just to make a card look complete.
- Catalog filter badges and their result predicates must describe the same rows. `Vinted da completare` means missing exact Vinted URL, publication label, or seller id; the red core warning remains specifically for a missing page/link.
- Catalog health is maintenance, not discovery: when no Motore scroll owns the Vinted lane, at most one exact already-linked catalog item is rechecked through the existing paced public-page lane. Sold/404 items leave the active catalog while history remains.
- Opening a known Vinted item from a Ludo card creates short-lived exact provenance separate from manual search recovery. The opened product page may safely update that exact listing's sold state, publication metadata and price.
- Bundle labels represent current seller inventory, not historical suggestions. A sold/hidden/corrected member invalidates the seller graph; a seller with fewer than two active eligible games must expose no bundle.
- Public Vinted network ownership is strict: while any Motore observation run is unfinished, opportunistic Catalog metadata maintenance and Bundle snapshot/deep/ownership verification must not start. Zero-network local Bundle reconstruction remains allowed.

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
- Non-game/accessory/component/empty-box/bundle cases, conflicting exact BGG identities, ambiguous aliases and merely fuzzy/plausible titles are held out of trusted product surfaces. Historical audit uncertainty is not a current human-review task. No row or BGG id is deleted or silently reassigned.
- Historical holds mark the legacy deal `MATCH_UNCERTAIN`, preventing an unverified identity from remaining in the ready/deal path while keeping Motore review nonblocking.
- Progress is persisted in `queue_controls` with `bgg_revalidation_v1:<gameId>` markers. This makes the pass one-shot and restart-safe; it cannot repeat automatically for the same game.
- The BGG foreground lane advances only 2 historical games per loop; WorkManager recovery advances 4, preserving current-run responsiveness. No network calls are made by the audit.
- Diagnostics expose `bggHistoricalRevalidation={processed, verifiedGames, heldGames, pending}`; `bggIdentityTrust.matchedToRevalidate` counts only unprocessed eligible games.
- 5.12.7 tightens accounting: legacy `USER_CONFIRMED` games are excluded from pending metrics as well as from execution, and any game with at least one flagged listing remains counted in the historical held set even when another listing independently verifies the canonical BGG identity.
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

## 5.12.29 Motore network priority
- Field debug on 5.12.27 showed `vintedActive=20` together with `DEEP_SCAN_RUNNING=1`: the old Bundle guard deferred network work only above 20 active Vinted jobs, so Bundle could still consume the same paced public-page lane while a Motore run was unfinished.
- 5.12.28 fixed ETA accounting and Catalog health, but the legacy Accessibility metadata resolver and Bundle pipeline still used queue-size heuristics rather than Motore ownership.
- An unfinished Motore run is now the authoritative network-priority signal. Automatic legacy metadata maintenance returns immediately while a run exists; Bundle backlog, snapshot discovery, queued deep scan, and ownership verification all re-check Motore ownership before starting public requests.
- Zero-network seller-graph rebuilding remains allowed during Motore work so stale Bundle labels can disappear immediately without costing a Vinted request.
- Manual user-requested metadata refresh keeps its explicit behavior; the new guard targets opportunistic background work.
- Diagnostic counter `bundleDeferredForMotore` records Bundle work yielded for Motore.
- Regression: `regression/motore_network_priority_v51229.py`.
- No schema migration, request-rate increase, signing/applicationId/Firebase/secrets/CI-versionCode-strategy change.

## 5.12.28 Catalog freshness, bundle health and Motore ETA truth

- Pixel/debug evidence on 5.12.27 showed a run with 9 valid games, 6 exact Vinted identities, 5 ready and 1 hold but `corePending=0` / `etaMs=60000`. Three BGG-ready listings still needed exact Vinted identity; they were parked outside `processing_jobs`, so the old ETA mistook queue materialisation for product completion.
- `coreRemainingListings` now counts BGG-ready listings whose exact Vinted item/url is still missing independently of durable-job state. ETA uses the larger of materialised pending work and product work still required.
- A yielded/deferred listing's `deferred_retry_at` remains a background throttle only. When its original scroll owns Motore again, unresolved rows can be rematerialised immediately instead of waiting hours for a retry timestamp assigned under a different owner.
- Motore copy reports `~N min di corsia` and explicitly says it may alternate with other scrolls. This is a service-work estimate, not a wall-clock completion promise.
- Added an idle-only Catalog health pass. With no active Motore run, one already-linked BGG-valid listing older than 24 hours may enter low-priority exact Vinted metadata verification through the existing paced public-page lane. Missing publication/seller metadata is prioritized. Request rate is unchanged.
- Exact health checks retire sold or HTTP-404 listings from the active catalog without creating human review; raw/history state is preserved. Successful checks refresh metadata and current price when available.
- Opening a known Vinted item from Ludo records a two-minute exact outbound target. Accessibility consumes that target before title/price heuristics, so a visible sold page such as Mysterium can update the exact canonical/legacy card and invalidate its seller bundle.
- Bundle invalidation is seller-wide: suggestions, diagnostics and stale seller caches are removed when a represented item is sold/hidden/corrected. Local bundle rebuild clears seller graphs that fall below two active games. Catalog bundle chips/presets now require a live two-game bundle, not a stale persisted row.
- Manual `Ricontrolla dati` now sees the same missing Vinted publication/seller metadata surfaced by Catalog.
- Diagnostics add `coreRemaining`, `catalogHealth`, `openedVintedTarget` and `lastOpenedVintedReconcile`.
- Regression: `regression/catalog_freshness_bundle_health_eta_v51228.py` plus updated 5.12.24/5.12.25 timing guards.
- No schema migration, request-rate increase, signing/applicationId/Firebase/secrets/CI-versionCode-strategy change.

## 5.12.27 Queue liveness and Catalog truth

- Pixel/debug evidence on 5.12.26 showed a real liveness contradiction: the active run still had 6 core Vinted candidates pending and diagnostics reported 7 runnable Vinted jobs, while the Vinted lane repeatedly said `IDLE · nessuna attività rivendicabile`. The public-page gate was READY and no Vinted job was PROCESSING.
- Root cause was an over-broad urgent-preemption guard: any LIVE/HUNT/MANUAL row whose retry time was still in the future blocked all ordinary Vinted work, even when that retry was many minutes away.
- Urgent work now reserves only the next public-page slot when its retry is within 65 seconds. If the urgent retry is farther away, already-runnable Motore work continues; when urgent work becomes due, existing priority ordering still claims it first.
- The Vinted lane reports this bounded reservation as `WAITING · priorità Vinted tra … s` instead of the misleading `IDLE · nessuna attività rivendicabile`.
- Diagnostics add `vintedUrgent={active,due,nextDueAt,reserveUntil}` so a future queue stall can be distinguished from intentional one-slot reservation.
- Catalog `Vinted da completare` had a count/filter mismatch: the badge counted missing URL, publication label, or seller id, but the filter only displayed missing URLs. The predicate now uses all three fields and the badge count uses the same active-catalog universe.
- The core red Vinted warning remains specifically about a missing exact Vinted page/link; publication/seller-only incompleteness stays the softer secondary state.
- Regression: `regression/queue_liveness_catalog_truth_v51227.py`.
- No schema migration, request-rate increase, signing/applicationId/Firebase/secrets/CI-versionCode-strategy change.

## 5.12.26 Recovery scope, review truth and early price gate

- Pixel/debug evidence on 5.12.25 showed three deterministic UX/cost problems: manual Vinted recovery polluted Motore with the surrounding Vinted search results; Motore reported two “ambiguous” rows while the actionable inbox was empty (`historicalHeld=2`); and 18 of 21 valid games were still candidates for core Vinted identity even though some listings were economically hopeless.
- Manual Vinted recovery is now stored in SQLite as a cross-process target-only scope. Accessibility can still use an explicit exact item-id hint for the target if Vinted exposes one, but all surrounding search-result cards are discarded before observation/thumbnail/job persistence. Returning to Ludo closes the recovery scope. Explicit “Confronta prezzi su Vinted” market-scan behavior is unchanged.
- Recovery UI explicitly explains that even one visible search result is not enough for an exact link when Vinted does not expose the item URL/id in Accessibility; the user opens/shares the exact item or pastes its URL.
- Motore now separates `reviewListings` (a real inbox choice) from `heldListings` (non-publishable trust/history state with no user action). Historical `MATCH_UNCERTAIN` no longer creates a phantom “Ambigui” count. Held rows count as automatically settled but remain excluded from trusted Home/Scopri.
- Added a conservative zero-network price gate before deferred Vinted promotion: an ordinary unlinked listing with an existing used reference is filtered from the automatic product path only when the seller ask is at least 2× the reference and at least €25 above it. Raw/game history remains; Hunt/manual intent bypasses the optimization.
- Queue reconciliation and repeated matched-noise maintenance now run through the existing maintenance executor after the shell is built, instead of synchronously in Activity startup. This addresses the observed UI ANR/SQLite contention path without changing queue ownership or request pacing.
- Diagnostics add `manualRecovery`, `manualRecoveryState`, `earlyPriceFilter`, and Motore `held`.
- Regression: `regression/recovery_review_price_gates_v51226.py`.
- No schema migration, request-rate increase, signing/applicationId/Firebase/secrets/CI-versionCode-strategy change.

## 5.12.25 Adaptive Motore fairness

- 5.12.24 removed the destructive timeout but still let the oldest unfinished run own the ordinary lane indefinitely. A very large scroll could therefore protect its own cards correctly while still delaying every later scroll.
- Timing now follows the expensive work that actually matters: core Vinted identity candidates. The product target is `max(10 min, ~1 min local/setup + core-work × 55 s)`; the live ETA uses only core candidates still pending and shrinks as they settle.
- Ten minutes is also the fairness service slice only when another unfinished scroll exists. After one slice the current run yields the automatic lane to the next unfinished run; after later runs settle or yield, earlier unfinished work resumes. Yield never changes listing/job correctness state.
- HUNT_PRIORITY and MANUAL_PRIORITY continue to preempt ordinary ownership. Optional deep Vinted metadata still does not block card readiness.
- Motore now exposes remaining online verifications and realistic ETA. Historical run rows distinguish `In attesa` from `In pausa · riprenderà`, and the overview explains that jobs rotate without discarding cards.
- Diagnostics add `engineFairness`, `etaMs`, `coreWork` and `corePending`; timing stays explicitly `nonDestructive=true`.
- Regressions: updated `engine_adaptive_timing_v51224.py` plus new `engine_adaptive_fairness_v51225.py`, including non-destructive yield, priority preservation and trusted-only Home guards.
- No schema migration, request-rate increase, signing/applicationId/Firebase/CI-versionCode-strategy change.

## 5.12.24 Adaptive Motore timing

- Product correction from Pixel/UX review: 10 minutes is a target for a small scroll, not a universal correctness deadline.
- Current Vinted public-page pacing is intentionally conservative at roughly one request every 55 seconds with a 60/hour budget. Therefore a run containing many genuinely eligible listings can require materially more than 10 minutes even when healthy.
- Motore now computes a workload-aware timing estimate: minimum 10 minutes; otherwise approximately 5 minutes base + 55 seconds per eligible listing. This is deliberately conservative and improves automatically when local/batch resolution reduces remote work.
- Elapsed time alone can no longer mark a listing `AUTO_EXCLUDED`, hide a deal, or declare a run complete. A run finishes only when its automatic content is actually settled.
- The timing observer is diagnostics-only (`engine-timing-v2`). It reports target/over-target state but never mutates listing correctness.
- UI copy says `stima` instead of promising `chiusura entro`, and waiting-scroll copy no longer claims a hard 10-minute release.
- Existing per-job watchdogs, retry rules, technical quarantine, classifier filtering and trust gates remain responsible for real failures; timing is not evidence.
- Regression: `regression/engine_adaptive_timing_v51224.py`.
- No schema migration, request-rate increase, signing/applicationId/CI-versionCode-strategy change.

## 5.12.23 Review/fuzzy/queue truth

- Pixel validation of 5.12.22 confirmed the exact BGG fix: `exactTimeouts=0`, `bggReview=0`, `bggTechnical=0`, and no crash/ANR/memory exit after the current install.
- The same diagnostic exposed three deterministic follow-ups:
  - fuzzy BGG still scanned too broadly (`fuzzyTimeouts=14`; latest 3/3 fuzzy attempts timed out and were quarantined);
  - 15 Vinted review rows were all non-explicit/non-variant, traced to historical BGG revalidation still setting `manual_review_required`;
  - `queueRunnable=33` disagreed with the Vinted lane saying no job was claimable because idle diagnostics and claim logic used different source gates.
- Queue-process fuzzy BGG matching now builds a compact primitive token-postings index lazily. Each query ranks at most a bounded candidate pool gathered from its rarest tokens instead of rescoring all 31k games.
- Historical BGG revalidation remains a trust firewall but no longer creates human review. Existing historical manual-review flags are cleared once; affected deals remain `MATCH_UNCERTAIN` and therefore stay out of trusted Home/Scopri.
- Idle ordinary Vinted jobs are parked back into the deferred pool. The 30-minute maintenance sweep no longer materializes ordinary network work when no Motore run is active.
- `runnableVintedDueCount()` and `nextRunnableVintedDueAt()` now use the same idle source policy as the real claimer: only LIVE/HUNT/MANUAL work is runnable without an active scroll.
- Review diagnostics v2 separate explicit, variant, historical inbox debt, historical held debt, other Vinted review, BGG technical and BGG genuine review.
- Additional diagnostics expose historical-review cleanup and idle-job parking.
- Regression: `regression/review_fuzzy_queue_truth_v51223.py`.
- No schema migration, request-rate increase, signing/applicationId/CI-versionCode-strategy change.

## 5.12.22 BGG exact-index turnaround follow-up

- First 5.12.21 Pixel validation confirms the product-level SLA works: Motore was IDLE with no waiting run, while `engineSla` reported five ordinary rows expired at the 10-minute ceiling; current-install crash/ANR/memory counts remained zero.
- The same diagnostic exposed the next bottleneck precisely: `bggLocalMatch` had `exactScans=29` and `exactTimeouts=29`; the matcher was turning its own CPU safety timeout into `BGG_MATCH_REVIEW`. This created review for infrastructure latency rather than genuine ambiguity.
- Exact BGG lookup now uses a compact primitive hash index built alongside the 31k-game catalog. It avoids normalizing/scanning all titles and aliases on every query while retaining collision verification against the original strings.
- Cold catalog/index creation is excluded from the per-query fuzzy scan timer. The one-time bootstrap cost may be a few seconds but cannot manufacture a timeout-review decision.
- BGG exact/fuzzy technical timeouts and matcher exceptions no longer become human review. The provisional game is automatically quarantined from trusted surfaces and the technical fault remains diagnostic.
- Existing 5.12.21 BGG review rows whose reason is specifically a local-match timeout/error are reopened once, not discarded, so the new exact index gets a clean chance to resolve them.
- Review telemetry now includes `reviewBreakdown` (Vinted explicit/variant/other and BGG technical/other) so the next Pixel cycle can distinguish real ambiguity from system debt.
- Classifier-block diagnostics now include timestamp + build. This is important because the previous cumulative `lastClassifierBlock` could survive an app update and make an old Fyfe false-positive look current.
- Regression: `regression/bgg_exact_index_no_timeout_review_v51222.py`.
- No schema migration, request-rate increase, signing/applicationId/CI-versionCode-strategy change.

## 5.12.21 Product UX turnaround — 10-minute SLA, trust, low-review pipeline

- Product decision: an ordinary Vinted scroll must normally yield useful results within 1–3 minutes and must stop owning Motore no later than 10 minutes after the last captured card. A hard `ENGINE_RUN_SLA_MS=10m` prevents one unresolved listing from blocking later scrolls.
- At SLA expiry unresolved ordinary rows are parked as reversible `AUTO_EXCLUDED`; raw observations/prices remain stored. Explicit Hunt/manual priority is never auto-dismissed by this rule.
- Automatic Vinted ambiguity no longer becomes a manual-review task after routine deterministic misses. Ordinary misses are auto-excluded; only explicit Hunt/manual intent retains the human-review recovery path.
- Optional Vinted deep metadata (`CORE_COMPLETE`) no longer blocks Motore readiness or trusted publication when exact Vinted + BGG identity is already complete.
- Review debt generated by pre-turnaround automatic behavior is archived once on first 5.12.21 UI start. It is not deleted and can be reactivated by a fresh sighting; explicit Hunt/manual work is preserved.
- BGG review is narrowed: board-game wording alone is no longer enough to create review; fuzzy review requires strong lexical evidence. Exact ambiguity still goes to review.
- Vinted title normalization now creates an identity variant for descriptive seller suffixes such as “Fyfe – gioco astratto…”, improving obvious exact matches without making fuzzy matching more permissive.
- Classifier adds explicit videogame/platform exclusions (PS5/PS4/Xbox/Switch etc.) and stops treating bare component nouns such as “tessere”, “dadi”, “meeple” or “miniature” as proof that a full game is only components.
- `Scopri` and companion recommendations now read only a strict trusted surface: matched BGG identity, exact Vinted identity, no review, no open core job, and base-game/expansion product type. Incomplete/suspect rows stay in Motore.
- Motore cards now open the same full product detail used elsewhere. The standard detail retains Vinted/BGG actions and exposes “Gioco sbagliato”, accessory/non-game and link correction from one place.
- Hunts are intent-first rather than resale-first. An exact hunted BGG game can be retained/promoted even at an average price; an explicit user max price is still respected. Hunt results use a trusted any-price surface while ordinary Home recommendations remain deal-focused.
- Diagnostics now include run age, time remaining before the SLA, per-run review percentage and `engineSla` expiry telemetry.
- Regression: `regression/product_turnaround_sla_trust_v51221.py` contains executable SLA/trusted-surface fixtures and guards review, classifier, Hunt and unified-detail behavior.
- No schema migration, signing/applicationId/request-rate/CI-versionCode-strategy change.

## 5.12.20 Engine runtime cross-process telemetry

- Pixel validation of 5.12.19 passed the two targeted correctness/stability checks: current-install `crashAfterInstall=0; anrAfterInstall=0; memoryAfterInstall=0`, and the stale old-run `analysisPending=7` collapsed to canonical `analysisPending=0`.
- The remaining `engineReady=false` line was not trustworthy because it was written in the Accessibility `:radar` process and read in `:ui` through process-local SharedPreferences caching. This build makes runtime state authoritative through the existing SQLite `queue_controls` diagnostics channel.
- `radar_service` and `engine_runtime` are written from `:radar` and read from any process. Top-level diagnostics now derive `serviceConnected`, `engineReady` and `engineGames` from SQLite whenever an authoritative row exists.
- `JsGameEngine` emits bounded bootstrap stages (create/attach/page finished/verify/ready/timeout) and captures WebView console errors. Verify snapshots expose `document.readyState`, bridge presence, catalog presence and catalog game count.
- Bootstrap diagnostic writes are sampled (attempts 1, 2, then every 10th) so the new observability does not create SQLite write pressure.
- Regression: `regression/engine_runtime_cross_process_v51220.py` models the exact stale-SharedPreferences case and guarantees SQLite wins.
- No queue semantics, schema migration, signing/applicationId/network-rate/CI-versionCode strategy changes.

## 5.12.19 Queue single-owner + current-run engine ordering

- 5.12.17 Pixel validation confirmed the acquisition fix at product level (20 unique cards) but also showed a new scroll waiting behind the older active run and a fresh default-process WorkManager ANR after install.
- 5.12.18 moved heavy Service/WorkManager operations off the Android main thread and made Motore pending counts canonical. A further deterministic review of current beta found two remaining ownership gaps that are fixed here before another Pixel cycle:
  - `QueueWakeReceiver.ACTION_NOW` started the foreground queue owner and then still fell through to one-shot WorkManager enqueue. ACTION_NOW is now foreground-service-only.
  - `QueueDrainWorker` now exits before opening SQLite whenever the foreground queue is STARTING or RUNNING. WorkManager is recovery only.
- Classifier/JS analysis now follows the same Motore ordering as Vinted/BGG durable jobs. `MarketStore.pendingAnalysisCards()` scopes pending analysis to the oldest active observation run; RAM hints can no longer let a newer waiting scroll jump ahead.
- A waiting scroll is rechecked every 5 seconds after the current classifier batch drains, so it can advance automatically without requiring another Vinted visit.
- `JsGameEngine` no longer treats one early cold-WebView readiness check as terminal. Readiness retries are single-chain/single-flight every 500 ms for up to 30 seconds before exposing an error.
- Regression: `regression/queue_single_owner_engine_order_v51219.py` includes an executable active-vs-waiting SQLite fixture plus guards for single-owner wake/recovery, classifier ordering and WebView readiness recovery.
- No schema migration, no signing/applicationId/network-rate/CI-versionCode strategy changes.

## 5.12.18 WorkManager/main-thread stability

- Pixel validation of 5.12.17 confirmed the acquisition dedupe: the Motore returned to ~20 unique Vinted cards instead of 35/58/66 repeated Accessibility events.
- The same install exposed a fresh default-process ANR 47.8s after the APK update: `No response to onStartJob ... SystemJobService`. The queue foreground service was still doing SQLite/reconcile/scheduler/sweep work synchronously from Android Service callbacks, and a handled `SQLiteDatabaseLockedException` had already shown queue-owner contention.
- `QueueKeepAliveService` now calls `startForeground()` immediately and moves database construction, epoch/reconcile, WorkManager recovery registration, sweep, lane supervision, periodic queue maintenance and notification state calculation onto a dedicated serialized control executor. `onStartCommand()` is acknowledgement-only on the Android main thread.
- `QueueWakeReceiver` uses `goAsync()` plus a single background executor for WorkManager enqueue calls. `QueueWorkScheduler` additionally dispatches default-process calls off the main looper as a safety net.
- WorkManager recovery does not open/compete for SQLite while the foreground owner is still in cold-start initialization.
- Motore `analysisPending` now follows canonical `market_listings.enrichment_state='PENDING_ANALYSIS'` rather than stale raw observation rows. This fixes old jobs that stayed active because pre-5.12.17 duplicate observation rows were still marked pending after the canonical listing had already advanced.
- Crash diagnostics v3 derive a current-install boundary from Android `PACKAGE_UPDATED` exits and report `crashAfterInstall/anrAfterInstall/memoryAfterInstall` separately from epoch/24h history.
- Diagnostics now expose the first waiting Motore run (`engineWaiting`) so a newer scroll can be inspected without hiding behind the current oldest job.
- Regression: `regression/workmanager_mainthread_stability_v51218.py` includes an executable SQLite fixture for the stale duplicate-pending trap plus static guards for main-thread scheduling/WorkManager ownership.
- No schema migration, no signing/applicationId/network-rate/CI-versionCode strategy changes.

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


## 5.12.43 runtime catalog unblock (2026-09-22)

Follow-up della 5.12.42. Il gate post-match non deve confondere “nessun cue nel testo feed” con “non inviare alla verifica categoria”. Solo uguaglianza esatta normalizzata titolo osservato/candidato BGG, e mai titolo collision-prone, può creare un candidato UNCERTAIN instradato alla lane Vinted; nessuna card Catalogo è pubblicabile da questo passaggio. La categoria strutturata Vinted e la compatibilità BGG rimangono i requisiti di promozione. Le catture screenshot full-frame del feed sono disabilitate per ridurre il rischio OOM/ANR su scroll 150–250 card.


## 5.12.44 queue ownership stability (2026-09-22)

Il processo UI non deve invocare MarketStore.reconcileQueue né sweep/cleanup sul database condiviso. QueueKeepAliveService è l'unico owner della manutenzione seriale; Radar resta produttore di osservazioni. La pagina Attività non deve ricostruire tutta la gerarchia a timer: render solo su navigazione o evento semantico.


## 5.12.45 Activity snapshot recovery (2026-09-22)

Invariante UI: renderEngineOverview ed engineCurrentRunHero non devono accedere direttamente a DealDatabase o MarketStore. Tutte le letture per Attività passano da EngineOverviewSnapshot caricato su uiDataIo con single-flight; il main thread mostra un placeholder e renderizza solo dati già pronti.
