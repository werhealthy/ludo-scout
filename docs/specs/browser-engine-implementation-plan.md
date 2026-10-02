# Browser → Motore completo — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** consegnare insieme la ricerca browser con UI Catalogo, acquisizione persistente per ID, analisi/code reali e stati Motore affidabili.

**Architecture:** tre tabelle additive nel database DealDatabase, writer breve e ACK dopo commit, job locale BROWSER_ANALYSIS nella coda esistente. Runtime JS senza overlay posseduto dall'owner default process, indipendente da Accessibility; riuso delle policy e dell'analisi attuale. Una sola PR/release completa, commit interni separati per verifiche.

**Tech Stack:** Java17, Android SDK35/min28, SQLite/WAL, WebView/JsGameEngine, WorkManager2.9.1, JUnit4 e framework instrumentation Android già nel SDK. Nessuna nuova dipendenza applicativa.

**Spec:** [backend-reliability-recognition.md](backend-reliability-recognition.md), contratto “consegna completa ricerca browser → Motore, feedback139”, approvato in chat2026-10-02 18:05Europe/Rome. Le istruzioni allegate Vibe Coding OS sono lette; il repository resta fonte canonica.

**Baseline:** beta6174d5b2eef80ada6fd836ffde91f42722f99238, frontend140 integrato. Branch backend/browser-complete-intake, PR173. Prima del codice riallineare; prima del merge riallineare nuovamente e verificare.

## Global Constraints

- Identità ID Vinted; prezzo articolo nullable nello staging, prezzo protetto separato; non usare zero per prezzo ignoto.
- Limiti invariati:32elementi/pacchetto,128KiB e500ID/pagina; nessun annuncio perso silenziosamente.
- Origine/frame/URL-ID verificati prima del commit; ACK significa commit riuscito del pacchetto.
- Nessuna nuova fetch del capturer, auto-scroll, auto-paginazione, auto-navigazione, retry di pagine, cookie/token extraction, API privata o bypass.
- Browser-source non genera VINTED/VINTED_DEEP/bundle remoti per campi mancanti, compresi reconcile/recovery.
- Classificazione, BoardGameIntakeGate, pricing BGG_ONLY, fiducia e soglie attuali conservati; nessun nuovo matcher.
- Conservare storico, preferiti BGG, memoria Ludo, libreria, review e registro reset; nessuna pulizia/backfill distruttiva.
- Remus/FontAwesome/palette Ludo; ricerca54dp, target48dp minimo, sezioni16dp e gap8–12dp, font grande/insets.
- Motore senza ricerca/intake conserva card vuota138; conti/liste stessa popolazione per ID, attività globali separate.
- Una sola beta finale; certificato C7DF7C31D0FE0D059307F4DE7B67BE5992DC87EC73E623CC9E8B4E87C63D8710, upload/distribuzione Firebase distinti.
- Non dichiarare accettazione visiva o prestazioni telefono sulla base della sola CI.

## Review Focus

1. Due venditori con titolo/prezzo uguali restano annunci distinti; cambio prezzo non perde preferiti/ID.
2. Chiusura/cambio pagina durante un commit non produce ACK falso né perdita degli elementi già confermati.
3. Revisione nuova durante analisi, retry o lease scaduta non applica un risultato vecchio.
4. Accessibility disabilitata, offline e processo ricreato non lasciano la coda locale dipendente da Vinted pacing.
5. Filtri multipli/font grande/insets e ritorno dal Motore mantengono URL, ricerca e gerarchia dei comandi.

---

## File structure e confini

Nuovi componenti nel package app/src/main/java/it/vintedaffari/app:
- BrowserCandidate.java: modello immutabile e revisione dei dati, nessun Android/UI.
- BrowserCaptureSql.java: schema e query comuni, usate da produzione e fixture SQLite.
- BrowserCaptureStore.java: staging, appartenenza, claim/lease/revisione e snapshot, sullo stesso DealDatabase.
- BrowserAnalysisCommitter.java: commit per ID e riuso della classificazione/persistenza, senza resolver remoto.
- BrowserAnalysisRunner.java: batch8, JsGameEngine senza overlay, callback/timeout/lifecycle e single-flight.
- BrowserLocalDrainWorker.java: recupero locale WorkManager senza vincolo di rete.
- BrowserCapturePresentation.java: gruppi/stati/copy per conti e liste del Motore.
- VintedBrowserControls.java: cornice nativa di ricerca/filtri/navigazione/cattura.

Modifiche necessarie: DealDatabase, MarketStore, VintedCard, JsGameEngine, VintedBrowserActivity, VintedAccessibilityService solo confine condiviso, QueueKeepAliveService, QueueWorkScheduler, MainActivity per snapshot/presentazione browser, LudoScoutApp per suffissi WebView, captureJS per ACK/errori. EngineOverviewPresentation140 si modifica solo se il nuovo snapshot lo richiede, conservando policy/movimento.

Test: nuovi JUnit puri per modello/presentazione; harness JVM di parser/protocollo/runner; SQLite Python usa schema/query reali; instrumentation senza librerie nuove per store/commit/runtime; fixture JS e workflow esistenti estesi. Shared files e motivazione dichiarati nella PR.

### Task 1: staging additivo e revisione atomica

**Files:** Create BrowserCandidate.java, BrowserCaptureSql.java, BrowserCaptureStore.java; Modify DealDatabase.java DB_VERSION21→22/onCreate/onUpgrade; Test app/src/test/java/it/vintedaffari/app/BrowserCandidateTest.java, regression/browser_capture_sql.py, app/src/androidTest/java/it/vintedaffari/app/BrowserCaptureStoreTest.java.

**Interfaces:** BrowserCandidate fields itemId,url,title,Integer priceCents,protectedPriceCents, metadata/provenance; BrowserCaptureStore(DealDatabase helper). Methods long beginCapture(String url,long now), CommitReceipt commit(long captureId,int page,List<BrowserCandidate> candidates,long now), List<Claim> claim(int limit,long now), boolean complete(Claim claim,String outcome,String reason,long now), void fail(Claim claim,String reason,long retryAt), Snapshot snapshot(long captureId). Receipt confirmedIds/updatedIds/incompleteIds; Claim jobId/itemId/revision/leaseStartedAt/candidate; Snapshot captureId and current rows. Internal helpers accept SQLiteDatabase; no nested waits.

- [ ] Write tests duplicateSameId, identicalTextDifferentIds, nullPrice, nullDoesNotErase, revisionIgnoresObservationTime, staleRevisionDoesNotComplete, migrate21PreservesData. Assert2IDs→2rows; sameID changedprice→1row/revision+1; null price→INCOMPLETE and0jobs; repeated unchangeddata→same revision/job; schema retains legacy rows/reset record.
- [ ] Run JUnit with ./gradlew --no-daemon :app:testDebugUnitTest; Python python3 regression/browser_capture_sql.py; instrumentation on emulator via :app:connectedDebugAndroidTest. Confirm new contract assertions fail on21/missing store.
- [ ] Implement tables browser_captures, browser_candidates, browser_capture_items and indexes for membership/state/job lookup. Keep metadata payload bounded; persist source per field. processing_jobs key browser_analysis:<itemId>, type BROWSER_ANALYSIS; candidate/job revision bookkeeping in staging, optional listing_id until materialized. Commit upsert+membership+job together.
- [ ] Claim at most8 in one short transaction; compare revision/lease at result commit. If candidate changed during processing keep one pending current revision; stale writer cannot finish/release a newer lease. Retry technical failures, no INCOMPLETE retry loop.
- [ ] Run same tests GREEN plus startup_reset_disabled.py; commit “feat: persist browser captures and local analysis jobs”.

### Task 2: exact-ID materialization and passive provenance

**Files:** Modify VintedCard.java, DealDatabase.java signature/record methods, MarketStore.java fingerprint/listing lookup/applyAnalysis/enqueue/reconcile/health/recovery; Create BrowserAnalysisCommitter.java; Test app/src/androidTest/java/it/vintedaffari/app/BrowserAnalysisCommitterTest.java, regression/browser_capture_provenance.py.

**Interfaces:** VintedCard adds itemId,itemUrl,captureSource with legacy defaults empty. BrowserAnalysisCommitter(DealDatabase db,MarketStore market,BrowserCaptureStore captures); boolean commit(Claim claim,GameAnalysis analysis,long now). Preserve current public legacy APIs; add package-private SQLiteDatabase overloads where atomic commit needs them. BrowserCaptureStore complete must share the same transaction as materialization.

- [ ] Write tests exactIdOnly, distinctIdsSameTitlePrice, updatesRetainIdentity, quarantineRetainsRaw, staleResultNoMaterialization, passiveReconcileNoRemoteJobs, existingHiddenListingNotReactivated. Assert distinct listing/observation signatures; IDexact reuses row; no signature-only promotion; gates unchanged; hidden/favorite/user states persist.
- [ ] Execute instrumentation/real SQL and JVM provenance harness RED. Verify expected enqueue counts0 for VINTED/VINTED_DEEP/bundle across direct apply, reconciliation, catalog health and recovery.
- [ ] Implement canonical vinted:<ID> for new signatures/fingerprints; exact-existing-ID rows retain their legacy references. Copy source/title/price/fotos/metadata without replacing unknown fields. Never reactivate SOLD/REMOVED/USER_HIDDEN on capture alone. Price history has explicit browser source.
- [ ] Reuse ListingClassifier and BoardGameIntakeGate before materializing trusted results; extract their persistence boundary from radar only as needed. Persist raw filtered outcomes without generating trusted deals. Preserve existing BGG enrichment; ambiguity/review/technical errors distinct.
- [ ] Persist browser origin via staging/listing ID relationship and enforce it centrally in automatic Vinted enqueue and at consumer claim/recovery so alternate code paths cannot bypass it. Existing explicitly requested manual operations keep their user-authorized semantics; the capture itself never creates one.
- [ ] Run GREEN and existing classification/identity/catalog regressions; commit “feat: materialize browser candidates by exact ID”.

### Task 3: local runtime and resumable queue without Accessibility

**Files:** Create BrowserAnalysisRunner.java, BrowserLocalDrainWorker.java; Modify JsGameEngine.java, QueueKeepAliveService.java, QueueWorkScheduler.java, LudoScoutApp.java; Test regression/browser_analysis_runner.py, app/src/androidTest/java/it/vintedaffari/app/BrowserRuntimeTest.java.

**Interfaces:** JsGameEngine adds RuntimeHost {EXISTING_UI,HEADLESS}; overload constructor(Context,WindowManager,RuntimeHost), existing constructor behavior preserved. BrowserAnalysisRunner(Context,DealDatabase,MarketStore,BrowserCaptureStore) implements AutoCloseable; boolean drainOnce(int limit,long now) on worker; void close(); callbacks remain main-thread. QueueWorkScheduler.scheduleLocalBrowser(Context), ensureLocalBrowserRecovery(Context) forward from :ui to default through existing QueueWakeReceiver (modify receiver for these two actions).

- [ ] Write tests offAccessibilityStillDrains, offlineStillDrains, serviceWorkerSingleFlight, engineNotReadyRetry, engineTimeoutNoHumanReview, changedRevisionWhileAnalyzing, serviceDestroyReleasesRuntime. Assert8max; real headless engine loads31181 games on current asset and can analyze; no WindowManager.addView; no MAIN-thread database wait.
- [ ] Run runtime instrumentation and harness RED. A detached-WebView failure blocks task completion; do not fall back to Accessibility overlay/permission or title-only BGG matcher.
- [ ] HEADLESS mode uses local asset/runtime, no overlay attachment and no external page HTTP; lazily initialize on Android main. Keep current30s readiness timeout, bounded60s batch wait, teardown on idle/service destruction. Tests verify callbacks and cancellation do not commit late/stale results.
- [ ] Add local lane before remote work in service control pulse, own single executor/batch and heartbeat. Worker recovery uses same runner/SQLite leases; no duplicate owner when service healthy, takeover when local heartbeat stale. Stale leases use existing15min recovery convention, independent of Vinted gate.
- [ ] Schedule unique local wake and15min recovery without NetworkType constraint; preserve existing network-constrained jobs and priority lanes. Default process has its own WebView directory suffix initialized before any WebView; :ui ludo-ui and radar current runtime remain isolated.
- [ ] Run GREEN and queue/runtime/workmanager regressions; commit “feat: drain browser analysis independently of accessibility”.

### Task 4: capture protocol confirms durable writes

**Files:** Modify VintedBrowserActivity.java, app/src/main/assets/browser/vinted-capture.js; Test regression/vinted_browser_capture.test.js, regression/browser_capture_protocol.py.

**Interfaces:** receive parses an immutable packet with captureId/pageGeneration/captureGeneration/URL/main-frame origin copied on UI. parse returns CommitReceipt or classified failure; ready:<sequence> only after Store.commit; retry:<sequence> bounded transient failure; rejected final with reason for malformed packets. Capture scope differs from pageGeneration.

- [ ] Write tests ackOnlyAfterCommit, commitFailsNoAcquiredIncrement, closeDuringCommit, oldPagePacketRejected, duplicateAfterLostAck, metadataRetained, metadataLimits, cap500AndBatch32, oversize128KiB. Assert commit-before-ACK order, idempotent membership/jobs, consistent confirmed count, photos/description/sellerName retained under validated limits:12public image URLs maximum,2048chars per URL,2000chars description,100chars sellerName,600chars title; reject invalid URLs individually and report truncation/overflow explicitly.
- [ ] Run node --test regression/vinted_browser_capture.test.js and python3 regression/browser_capture_protocol.py RED. Use actual Activity parser/protocol methods in the Java harness, no shadow implementation.
- [ ] Persist in existing bounded serial ingest executor. New search/filter starts capture; page change keeps capture; pause/one-shot/generation invalidation retained. Pause stops new admissions, does not erase committed rows; destruction lets accepted persistence complete with no dead Activity callback.
- [ ] Keep malformed/rejected/overflow/database failures distinct. No ACK after failure; bounded protocol retry has terminal visible failure instead of indefinite duplicate flooding. Counters/diagnostics read committed data and distinguish confirmed/pending; wake local queue after successful commit.
- [ ] Run GREEN including existing40browser fixtures; commit “feat: acknowledge browser items after durable intake”.

### Task 5: Catalog-style browser controls

**Files:** Create VintedBrowserControls.java, BrowserSearchNavigation.java; Modify VintedBrowserActivity.java; Test app/src/test/java/it/vintedaffari/app/BrowserSearchNavigationTest.java, app/src/androidTest/java/it/vintedaffari/app/BrowserControlsTest.java, regression/browser_controls.py.

**Interfaces:** VintedBrowserControls(Activity,Listener); Listener onNavigate(String url), onCapture(), onToggle(), onOpenEngine(long captureId), onClose(); void render(State state). State has permittedURL/query/order/min/max/saved searches/page/loading/capture flag/committed counts. BrowserSearchNavigation.build(String currentUrl,String query,String order,Integer minCents,Integer maxCents,int page) returns String permittedURL; pureJava and tested; site routes/orders use current implementation values.

- [ ] Write tests multipleFiltersPreserved, removingMinKeepsMax, filterChangeResetsPage1, savedSearchExplicitNavigation, pageBounds1to10, narrowFontLargeLayout, oneGestureOneNavigation, noPresetGameTags. Assert unchanged unrelated query params, no automatic web.loadUrl/scroll, distinct contentDescriptions/touch targets.
- [ ] Run pure JUnit/navigation harness and instrumentation RED; use existing Catalog token/layout reference renderCatalog/showFilterSheet and screenshots as comparison input.
- [ ] Implement search54dp, filter54dp with badge, active chip row, sort control, panel draft/apply; category4881-board-games and Vinted orders unchanged. Validate min/max in integer cents; resetpage1, conserve other permitted filters. Nessun suggerimento di giochi preimpostato (Catan/Azul rimossi). Solo chip di filtri selezionati; eventuali ricerche salvate dall'utente nel pannello, non in file di tag.
- [ ] Header close/title/menu, separate prev/page/next and capture controls; diagnostics in menu, compact status/CTA Motore. Apply safe WindowInsets, adaptive heights, keyboard/back dismissal and single-line vs wrap consciously; no broad shared-Catalog rewrite.
- [ ] Run GREEN, capture screenshots emulator narrow/default and font1.5x, inspect spacing/icons/overlap. Record emulator screenshots as verification only, not user phone acceptance; commit “feat: align Vinted search controls with Catalog”.

### Task 6: persistent capture presentation and Motore integration

**Files:** Create BrowserCapturePresentation.java; Modify MainActivity.java snapshot/readers/render/drilldown/history/navigation, EngineOverviewPresentation.java only needed additions, BrowserCaptureStore.java snapshot query; Test app/src/test/java/it/vintedaffari/app/BrowserCapturePresentationTest.java, regression/browser_engine_presentation.py, app/src/androidTest/java/it/vintedaffari/app/BrowserEnginePipelineTest.java.

**Interfaces:** Presentation.groups(Snapshot) returns disjoint rows INCOMPLETE,QUEUED,ANALYZING,FILTERED,REVIEW,READY,TECHNICAL_ERROR and counts. Store listCaptures(int limit), snapshot(long captureId) feed engineUiIo snapshot. Browser CTA selects captureId and opens activity; run UI carries browser scope distinct from time-only legacy scope.

- [ ] Write tests allIdsAccountedExactlyOnce, captureExistsNoFalseEmpty, globalJobNotCapture, sameIdTwoSearchesNoDoubleCanonical, pendingBggNotReady, returnKeepsScopePosition, snapshotErrorNotEmpty. For156synthetic IDs/96priced before drain assert156persisted,60incomplete,96queued; after drain assert sum groups156 and0newVinted jobs. No fixture claims a live page always has156.
- [ ] Run JUnit/SQL/instrumentation and engine harness RED.
- [ ] Merge browser capture scope into overview/day/history/detail on IO snapshot; both counts and row lists use the same query/status projection. Incomplete records visible before canonical listing exists; explicit capture membership avoids timestamp collision with concurrent radar captures.
- [ ] READY requires current classifier/BGG/catalog eligibility checks; source exactURL alone cannot mark ready. BGG waiting and technical states stay visible. Preserve140 motion policy, inactive Pronti, system animation/focus gates, explicit pauses and existing manual-review flows.
- [ ] Preserve empty138 and separate global-job row; show stale/error/loading explicitly. Return from browser/run/detail preserves scope and scroll; diagnostics report durablecapture/job/funnel counts and local lane, not in-memory inferred work.
- [ ] Run GREEN all existing engine/navigation regressions; commit “feat: show durable browser searches in Motore”.

### Task 7: full verification, alignment and one beta delivery

**Files:** Modify .github/workflows/android-pr.yml, android-beta.yml, app/build.gradle; update existing specs and STATE only at checkpoint/merge. No new parallel product backlog.

- [ ] Wire new executable tests into both workflows, including SDK instrumentation runner android.test.InstrumentationTestRunner (defaultConfig) and a bounded API35 emulator smoke job using SDK tools, no new app dependencies. Compile androidTest APK; run real store/commit/runtime/protocol/UI pipeline tests, preserve existing regression expectations.
- [ ] Execute full PR workflow: existing regressions, new Python/JVM/JS, :app:testDebugUnitTest, :app:compileDebugJavaWithJavac, :app:assembleDebug and connectedDebugAndroidTest. Compare before/after network spy and identity fixture output. Any required unavailable check is a reported blocker, not PASS.
- [ ] Re-read beta/current frontend/shared files and realign preserving140+successive changes. Run pertinent/full workflow again only when alignment changes source. Request independent whole-branch review; fix material issues and rerun relevant checks.
- [ ] Assign next free5.12 version/localCode after checking beta, not hardcode141 if another agent has shipped it. PR description rewritten for final implementation, user approval/time and shared-file scope explicit; merge only verified head.
- [ ] Run signed beta workflow; verify certificate, upload and separate tester distribution. Filter logs to avoid exposing signed/token download URLs. No intermediate APK release for a partial task.
- [ ] Update STATE/specs with actual CI/run/merge/distribution evidence, limitations and remaining groups backend5/frontend7 until substantive acceptance closes one. Phone test once complete:2manualpages, browser/Motore screenshots and two diagnostics before/after reopening; incomplete reasons, identity/no-duplicates, retained state, no induced Vinted requests.

## Plan self-review and handoff

Coverage: staging/revision Task1; identity/classifier/provenance Task2; queue/runtime/offline Task3; ACK/generation/metadata Task4; UI/filter/insets Task5; scopes/counts/states/navigation Task6; CI/review/beta/phone evidence Task7. Five Review Focus conditions each have named test assertions above. No paid dependency, filter threshold change, destructive migration or unapproved scope added.

Execution recommendation: Native, same session implements each task, then independent whole-branch review. The tasks share exact-ID/revision interfaces and the same SQLite helper; this minimizes repeated context while retaining mandatory verification. User reviews this written plan and chooses/approves execution method before product code. Approval already granted for the spec/schema; do not ask for those again.
