package it.vintedaffari.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.util.Log;
import android.text.TextUtils;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * V3.1 smart radar observer.
 *
 * Important invariant: observing Vinted must NOT depend on the BGG runtime being ready.
 * Raw sightings are persisted immediately. Analysis is a second, asynchronous stage.
 */
public final class VintedAccessibilityService extends AccessibilityService {
    private static final String PREF_VINTED_MARKET_SCAN="vinted_market_scan_v1";

    private static final String TAG = "LudoScoutV5";
    private static final String VINTED_PACKAGE = "fr.vinted";
    private static final String PREFS_DIAG = "va_v3_diag";
    private static final long SCAN_DEBOUNCE_MS = 70;
    // Accessibility can emit the same visible Compose cards repeatedly on focus/window changes.
    // Keep a generous in-process guard so returning to Vinted does not manufacture another Motore job
    // from the exact same title/brand/price rows. A changed price changes the signature and is fresh.
    private static final long REANALYZE_SAME_CARD_MS = 10 * 60_000L;
    private static final long RESIGHT_SAME_CARD_MS = 10 * 60_000L;
    private static final Pattern VINTED_ABSOLUTE_ITEM=Pattern.compile("https?://(?:www\\.)?vinted\\.[^\\s/]+/items/(\\d{5,})(?:-[^\\s,;]*)?",Pattern.CASE_INSENSITIVE);
    private static final Pattern VINTED_RELATIVE_ITEM=Pattern.compile("(?:^|[^A-Za-z0-9])/?items/(\\d{5,})(?:[-/?#][^\\s,;]*)?",Pattern.CASE_INSENSITIVE);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Long> recentlyAnalyzed = new LinkedHashMap<>();
    private final Map<String, Long> recentlySighted = new LinkedHashMap<>();
    private final LinkedHashMap<String, VintedCard> pendingForAnalysis = new LinkedHashMap<>();
    private final Set<String> bundleSellersInFlight = new HashSet<>();
    private final LinkedHashMap<String,String[]> contextualSellerHints = new LinkedHashMap<>();
    /** Explicit item id/url hints exposed by the Vinted accessibility tree. They are accepted only
     * when the subtree literally contains /items/<digits>; generic view ids are never treated as
     * listing identities. This can eliminate the later search request without guessing. */
    private final LinkedHashMap<String,String[]> contextualVintedIdentityHints = new LinkedHashMap<>();
    private final LinkedHashMap<String,List<String>> pendingSellerRails = new LinkedHashMap<>();
    /** Diagnostic-only dedupe for the Accessibility identity probe. This never determines listing identity. */
    private final LinkedHashSet<String> accessibilityIdentityProbeSeen = new LinkedHashSet<>();

    private JsGameEngine engine;
    private DealDatabase database;
    private AutoLinkResolver linkResolver;
    private BggEnricher bggEnricher;
    private MarketStore marketStore;
    private final ExecutorService maintenanceIo=Executors.newSingleThreadExecutor();
    private volatile boolean marketJobInFlight=false;
    private volatile boolean analysisBatchInFlight=false;
    private BundleDatabase bundleDatabase;
    private SellerBundleScanner bundleScanner;
    private boolean scanScheduled = false;
    private volatile long lastVintedEventAt=0L;private long pendingVintedEventDiag=0L,lastVintedEventDiagFlushAt=0L;
    private boolean retryRegistered = false;
    private volatile boolean manualMetadataRefresh=false;
    private volatile String manualRefreshTargetSignature=null;
    private volatile String manualMaintenanceTask="maintenance:missing-data";
    private final Set<String> manualRefreshAttempted=new HashSet<>();
    private final java.util.ArrayDeque<String> manualRefreshQueue=new java.util.ArrayDeque<>();
    private final Set<String> manualRefreshQueued=new HashSet<>();
    private final Map<String,Integer> manualRefreshInitialMissingMask=new LinkedHashMap<>();
    private volatile boolean manualBulkMode=false;
    private volatile int manualBulkTotal=0,manualBulkCompleted=0;
    private volatile long maintenancePausedUntil=0L;
    private volatile String maintenancePauseReason="";
    private static final String MAINTENANCE_TASK="maintenance:missing-data";
    private final Runnable maintenancePump=new Runnable(){@Override public void run(){if(!manualMetadataRefresh)return;sweepCompletedQueuedTargets();if(TextUtils.isEmpty(manualRefreshTargetSignature))startNextManualRefreshTarget();updateMaintenanceTask();enrichBacklog();resolveBacklog();sweepCompletedQueuedTargets();maybeFinishCurrentManualTarget();if(manualMetadataRefresh)handler.postDelayed(this,1500L);}};
    private final BroadcastReceiver retryReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
        if(i!=null&&OperationCenter.REFRESH_MISSING.equals(i.getAction())){
            final String requestedSignature=i.getStringExtra("signature");
            final String requestedTask=i.getStringExtra("task_id");
            OperationCenter.queued(VintedAccessibilityService.this,MAINTENANCE_TASK,OperationCenter.MAINTENANCE,
                    TextUtils.isEmpty(requestedSignature)?"Indicizzo gli elementi incompleti":"Aggiungo l'elemento alla coda persistente");
            maintenanceIo.execute(()->{
                int added=0;
                if(marketStore!=null){
                    if(TextUtils.isEmpty(requestedSignature))added=marketStore.enqueueMissingLegacyDeals();
                    else{DealRecord d=database==null?null:database.findBySignature(requestedSignature);if(d!=null)added=marketStore.enqueueListingFromLegacy(d);}
                }
                final int count=added;handler.post(()->{
                    Log.i(TAG,"persistent refresh scheduled="+count+" signature="+(requestedSignature==null?"all":requestedSignature));
                    OperationCenter.remove(VintedAccessibilityService.this,MAINTENANCE_TASK);
                    updatePersistentMaintenanceUi();scheduleMarketPump(0L);
                    scheduleScan(0);scanBundleBacklog(true);
                });
            });
            return;
        }
        if(!manualMetadataRefresh){resolveBacklog();enrichBacklog();}
        scheduleScan(0);scanBundleBacklog(true);
    }};
    private long lastBacklogAttemptAt = 0;
    private volatile boolean linkNetworkInFlight=false;
    private long lastPriorityLinkAttemptAt=0;
    private static final long PRIORITY_LINK_GAP_MS=45_000L;
    private final Runnable vintedResumeRunnable=new Runnable(){@Override public void run(){
        if(!manualMetadataRefresh||TextUtils.isEmpty(manualRefreshTargetSignature))return;
        DealRecord d=database==null?null:database.findBySignature(manualRefreshTargetSignature);
        long until=authoritativeVintedResumeAt(d);
        if(until>System.currentTimeMillis()){setVintedPause(until,maintenancePauseReason);return;}
        maintenancePausedUntil=0L;maintenancePauseReason="";updateMaintenanceTask();resolveBacklog();
    }};

    /** Durable enrichment pump. One Vinted resolver at a time, BGG independently serialized by BggEnricher. */
    private final Runnable marketPump=new Runnable(){@Override public void run(){
        try{pumpPersistentMarketJobs();}catch(Throwable t){Log.e(TAG,"persistent market pump failed",t);diag().edit().putString("lastError","market-pump: "+String.valueOf(t.getMessage())).apply();}
        finally{scheduleMarketPump(15_000L);}
    }};

    private void scheduleMarketPump(long delayMs){
        handler.removeCallbacks(marketPump);
        handler.postDelayed(marketPump,Math.max(0L,delayMs));
    }

    private final Runnable scanRunnable = () -> {
        scanScheduled = false;
        scanVisibleVintedCards();
        // Keep sampling during a continuous scroll. The old debounce restarted its timer on every
        // event, so a card could enter and leave the viewport before a scan ever ran.
        if(System.currentTimeMillis()-lastVintedEventAt<700L)scheduleScan(160L);
    };

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        QueueKeepAliveService.ensureRunning(this);
        QueueWorkScheduler.ensureRecovery(this);
        QueueWorkScheduler.schedule(this);
        database = new DealDatabase(getApplicationContext());
        marketStore = new MarketStore(getApplicationContext(),database);
        marketStore.resetStaleProcessingOlderThan(15 * 60_000L);
        linkResolver = new AutoLinkResolver(getApplicationContext());
        bggEnricher = new BggEnricher(getApplicationContext(), database, marketStore);
        bundleDatabase = new BundleDatabase(getApplicationContext());
        bundleScanner = new SellerBundleScanner(getApplicationContext());
        // No queued/running operation can survive an AccessibilityService process restart. Close
        // those rows immediately so Activity never shows a ghost task forever.
        OperationCenter.settleStaleActive(this,0L);
        SharedPreferences pipelinePrefs=diag();
        if(pipelinePrefs.getInt("bundlePipelineGeneration",1)<4){
            int reset=bundleDatabase.resetRetryableDiagnostics();bundleDatabase.resetPipelineCounters();
            pipelinePrefs.edit().putInt("bundlePipelineGeneration",4).putInt("bundlePipelineResetRows",reset).apply();
        }
        IntentFilter retryFilter=new IntentFilter(OperationCenter.RETRY);retryFilter.addAction(OperationCenter.REFRESH_MISSING);if(Build.VERSION.SDK_INT>=33)registerReceiver(retryReceiver,retryFilter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(retryReceiver,retryFilter);retryRegistered=true;
        diag().edit()
                .putBoolean("serviceConnected", true)
                .putLong("serviceConnectedAt", System.currentTimeMillis())
                .putBoolean("engineReady", false)
                .putString("lastError", "")
                // v5.11.0 left the old volatile-bulk diagnostics behind even though the
                // actual work had moved to processing_jobs. Clear those stale fields so
                // Radar reports the durable queue rather than a dead v5.10 session.
                .putBoolean("refreshBulkMode", false)
                .putInt("refreshBulkTotal", 0)
                .putInt("refreshBulkCompleted", 0)
                .putInt("refreshBulkQueued", 0)
                .putString("refreshBulkCurrent", "")
                .putLong("refreshPausedUntil", 0L)
                .putString("refreshPauseReason", "")
                .apply();
        // v5.10 used a volatile in-memory bulk queue. If the process died mid-run, migrate that
        // intent to the durable v9 queue instead of reconstructing the old head-of-line queue.
        if(diag().getBoolean("manualBulkRequested",false)){
            diag().edit().putBoolean("manualBulkRequested",false).apply();
            maintenanceIo.execute(()->{int q=marketStore.enqueueMissingLegacyDeals();Log.i(TAG,"recovered legacy bulk into durable queue: "+q);scheduleMarketPump(0L);});
        }
        scheduleMarketPump(1200L);

        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        engine = new JsGameEngine(getApplicationContext(), wm);
        engine.start(new JsGameEngine.ReadyListener() {
            @Override public void onReady(int gameCount) {
                diag().edit()
                        .putBoolean("engineReady", true)
                        .putInt("engineGames", gameCount)
                        .putString("lastError", "")
                        .apply();
                // Persisted MarketStore state owns classifier ordering. Clear stale RAM hints
                // so a newer waiting scroll cannot jump ahead of the oldest active Motore run.
                pendingForAnalysis.clear();
                if(marketStore!=null){for(VintedCard c:marketStore.pendingAnalysisCards(40))pendingForAnalysis.put(DealDatabase.signature(c),c);}
                Log.i(TAG, "Motore pronto: " + gameCount + " giochi. Flush coda attiva=" + pendingForAnalysis.size());
                flushPendingAnalysis();
                rebuildLocalBundles();
                scheduleScan(0);
                handler.postDelayed(VintedAccessibilityService.this::resolveBacklog, 1500);
                handler.postDelayed(VintedAccessibilityService.this::enrichBacklog, 2200);
                handler.postDelayed(() -> scanBundleBacklog(false), 2800);
            }

            @Override public void onError(String message) {
                String safe = message == null ? "errore motore" : message;
                diag().edit().putBoolean("engineReady", false).putString("lastError", safe).apply();
                Log.e(TAG, safe);
            }
        });
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null || !VINTED_PACKAGE.contentEquals(pkg)) return;

        long eventNow=System.currentTimeMillis();pendingVintedEventDiag++;
        if(lastVintedEventDiagFlushAt==0L||eventNow-lastVintedEventDiagFlushAt>=2_000L||pendingVintedEventDiag>=64L){
            SharedPreferences p=diag();long delta=pendingVintedEventDiag;pendingVintedEventDiag=0L;lastVintedEventDiagFlushAt=eventNow;
            p.edit().putLong("vintedEvents",p.getLong("vintedEvents",0)+delta).putLong("lastEventAt",eventNow).putInt("lastEventType",event.getEventType()).apply();
        }

        int type = event.getEventType();lastVintedEventAt=eventNow;
        if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
                type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            scheduleScan(type == AccessibilityEvent.TYPE_VIEW_SCROLLED ? 0 : (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ? 20 : SCAN_DEBOUNCE_MS));
        }
    }

    private void scheduleScan(long delay) {
        // Throttle/coalesce instead of trailing-edge debounce: never postpone an already scheduled
        // scan just because Vinted keeps emitting scroll/content events.
        if (scanScheduled) return;
        scanScheduled = true;
        handler.postDelayed(scanRunnable, Math.max(0L,delay));
    }

    private void scanVisibleVintedCards() {
        if (database == null) return;

        SharedPreferences p = diag();
        p.edit().putLong("scans", p.getLong("scans", 0) + 1).apply();

        AccessibilityNodeInfo root = getRootInActiveWindow();
        CharSequence rootPkg=root==null?null:root.getPackageName();
        if(root==null||rootPkg==null||!VINTED_PACKAGE.contentEquals(rootPkg)){
            AccessibilityNodeInfo fallback=findVisibleVintedRoot();
            if(fallback!=null){root=fallback;rootPkg=root.getPackageName();p.edit().putLong("accessibilityWindowFallbacks",p.getLong("accessibilityWindowFallbacks",0)+1).apply();}
        }
        if (root == null) {p.edit().putString("lastRoot", "null").apply();return;}
        p.edit().putString("lastRoot", rootPkg == null ? "<no package>" : rootPkg.toString()).apply();
        if (rootPkg == null || !VINTED_PACKAGE.contentEquals(rootPkg)) return;

        ProductPage product = ProductPageParser.parse(root);
        DealRecord currentProductDeal=null;
        if (product != null) {
            handleProductPage(product);
            currentProductDeal=product.itemPrice>0?database.findByTitlePrice(product.title,(int)Math.round(product.itemPrice*100.0)):database.findByVintedTitle(product.title);
            if(product.sold&&currentProductDeal!=null){database.markSold(currentProductDeal.signature);bundleDatabase.invalidate(currentProductDeal);OperationCenter.done(this,"sold:"+currentProductDeal.signature,OperationCenter.LINK,"Articolo venduto · rimosso");sendBroadcast(new Intent("it.vintedaffari.app.DEALS_UPDATED").setPackage(getPackageName()));return;}
            // The Vinted item page itself can expose a visible "Articoli dell'utente" rail.
            // Capture that rail as a zero-request seller snapshot only when the source seller is
            // already verified and the section boundary is narrow enough to be unambiguous.
            if(currentProductDeal!=null){
                List<VintedCard> sellerRail=collectSellerRecommendationCards(root);List<String> railSignatures=new ArrayList<>();int hinted=0;
                for(VintedCard card:sellerRail){String sig=DealDatabase.signature(card);if(sig.equals(currentProductDeal.signature))continue;railSignatures.add(sig);if(!TextUtils.isEmpty(currentProductDeal.sellerId)){contextualSellerHints.put(sig,new String[]{currentProductDeal.sellerId,currentProductDeal.sellerName});hinted++;}}
                if(TextUtils.isEmpty(currentProductDeal.sellerId)&&!railSignatures.isEmpty()){pendingSellerRails.put(currentProductDeal.signature,railSignatures);while(pendingSellerRails.size()>120){String first=pendingSellerRails.keySet().iterator().next();pendingSellerRails.remove(first);}}
                while(contextualSellerHints.size()>600){String first=contextualSellerHints.keySet().iterator().next();contextualSellerHints.remove(first);}
                if(!railSignatures.isEmpty())diag().edit().putInt("bundleAccessibilitySnapshot",railSignatures.size()).putString("bundleStrategy",TextUtils.isEmpty(currentProductDeal.sellerId)?"accessibility-seller-rail-pending":"accessibility-seller-rail").apply();
            }
        }

        List<VintedCard> discovered = new ArrayList<>();
        // A Vinted item-detail page contains seller/recommendation rails that may be shoes, books,
        // glasses, etc. The product itself is already handled above by ProductPageParser; those
        // rails must not become new catalog games just because they are visible on the same page.
        if (product == null) collectCards(root, discovered);
        else diag().edit().putLong("productPageDiscoverySuppressed",diag().getLong("productPageDiscoverySuppressed",0)+1).apply();
        p.edit()
                .putInt("lastCardsParsed", discovered.size())
                .putLong("cardsParsedTotal", p.getLong("cardsParsedTotal", 0) + discovered.size())
                .apply();
        if (discovered.isEmpty()) return;
        // Capture the visible listing artwork once, at observation time. This gives the resolver a
        // durable visual fingerprint even if the listing is sold or becomes hard to rediscover later.
        ThumbnailStore.captureMissing(this, discovered);

        long now = System.currentTimeMillis();
        if(now-lastBacklogAttemptAt>30_000L){lastBacklogAttemptAt=now;handler.post(this::resolveBacklog);}
        for (VintedCard card : discovered) {
            String sig = DealDatabase.signature(card);
            ListingClassifier.Result listingNow = ListingClassifier.classify(card);
            // Strong negative evidence is rejected before it can become a provisional BGG game.
            // Unknown proper names still pass through: only category-level non-game evidence is dropped.
            if (listingNow.type == ListingClassifier.Type.NON_GAME) {
                SharedPreferences pp = diag();
                pp.edit().putLong("nonGameRejected", pp.getLong("nonGameRejected", 0) + 1)
                        .putString("lastNonGameRejected", card.title + " · " + listingNow.reason).apply();
                continue;
            }

            Long lastSight = recentlySighted.get(sig);
            if (lastSight == null || now - lastSight >= RESIGHT_SAME_CARD_MS) {
                database.recordSighting(card, listingNow, now);
                if(marketStore!=null)marketStore.recordSighting(card,listingNow,now);
                applyExplicitVintedIdentityHint(card,sig);
                recentlySighted.put(sig, now);
                if (!listingNow.allowPriceModel) {
                    SharedPreferences pp = diag();
                    pp.edit().putLong("classifierBlocked", pp.getLong("classifierBlocked", 0) + 1)
                            .putString("lastClassifierBlock", listingNow.type.name() + ": " + listingNow.reason + " | " + card.title).apply();
                }
            }

            if (listingNow.allowPriceModel) {
                Long lastAnalyzed = recentlyAnalyzed.get(sig);
                boolean analysisDue=lastAnalyzed == null || now - lastAnalyzed >= REANALYZE_SAME_CARD_MS;
                if(analysisDue){
                    // This map is a wake/cache hint only. Actual batch selection below always comes
                    // from MarketStore, which is scoped to the oldest active Motore run.
                    pendingForAnalysis.put(sig, card);
                    if (pendingForAnalysis.size() > 500) {
                        String first = pendingForAnalysis.keySet().iterator().next();
                        pendingForAnalysis.remove(first);
                    }
                }else pendingForAnalysis.remove(sig);
            }

            if (!card.rawDescription.isEmpty()) {
                android.content.SharedPreferences.Editor edit=p.edit().putString("lastCardSample", truncate(card.rawDescription, 280));if(!TextUtils.isEmpty(card.sellerName))edit.putString("lastCardSeller",card.sellerName);edit.apply();
            }
        }

        trimOld(recentlySighted, now, 10 * 60_000L, 1600);
        trimOld(recentlyAnalyzed, now, 10 * 60_000L, 1600);

        if (engine != null && engine.isReady()) continuePersistentAnalysis();
    }

    private void flushPendingAnalysis() {
        if (engine == null || !engine.isReady()) return;
        List<VintedCard> batch = marketStore==null?new ArrayList<>(pendingForAnalysis.values()):marketStore.pendingAnalysisCards(40);
        if(batch.isEmpty())return;
        long now = System.currentTimeMillis();
        for (VintedCard card : batch) {
            String sig=DealDatabase.signature(card);
            pendingForAnalysis.remove(sig);
            recentlyAnalyzed.put(sig, now);
        }
        analyzeBatch(batch);
    }

    private void analyzeBatch(List<VintedCard> cards) {
        if (engine == null || !engine.isReady() || cards.isEmpty()) return;
        if(analysisBatchInFlight){for(VintedCard c:cards){pendingForAnalysis.put(DealDatabase.signature(c),c);if(pendingForAnalysis.size()>500)pendingForAnalysis.remove(pendingForAnalysis.keySet().iterator().next());}return;}
        analysisBatchInFlight=true;
        SharedPreferences p = diag();
        p.edit().putLong("analysisBatches", p.getLong("analysisBatches", 0) + 1).apply();

        engine.analyze(cards, new JsGameEngine.BatchListener() {
            @Override public void onResult(List<GameAnalysis> analyses) {
                long t = System.currentTimeMillis();
                int count = Math.min(cards.size(), analyses.size());
                for (int i = 0; i < count; i++) {
                    VintedCard card = cards.get(i);
                    GameAnalysis ga=analyses.get(i);
                    GameRecord scanTarget=activeMarketScanGame();if(scanTarget!=null&&ga!=null&&!BoardGameIntakeGate.isStrongNonGameText(card.title,card.rawDescription)&&BoardGameIntakeGate.plausibleOverlap(card.title,scanTarget.name))ga=ga.withCanonicalIdentity(scanTarget,"Ricerca prezzi Vinted esplicita");
                    ListingClassifier.Result analyzedListing=ListingClassifier.classify(card);
                    if(analyzedListing.type==ListingClassifier.Type.NON_GAME){
                        DealRecord wrong=database.findByTitlePrice(card.title,(int)Math.round(card.itemPrice*100.0));
                        if(wrong!=null){database.exclude(wrong,"Classificato automaticamente come non gioco da tavolo");if(marketStore!=null)marketStore.setLegacyListingUserHidden(wrong.signature,true);}
                        continue;
                    }
                    // An unresolved title is no longer automatically turned into human BGG work.
                    // It must show positive board-game evidence; otherwise keep it in a reversible
                    // auto-filtered quarantine and let the fast discovery pipeline continue.
                    if(ga==null||!"matched".equals(ga.status)||TextUtils.isEmpty(ga.bggId)){
                        BoardGameIntakeGate.Decision gate=BoardGameIntakeGate.afterAnalysis(card,ga);
                        if(gate.action==BoardGameIntakeGate.Action.QUARANTINE){
                            DealRecord noisy=database.findByTitlePrice(card.title,(int)Math.round(card.itemPrice*100.0));
                            if(noisy!=null)database.exclude(noisy,"Scarto automatico pre-BGG: "+gate.reason);
                            if(marketStore!=null)marketStore.quarantineUnresolvedObservation(card,gate.reason,t);
                            SharedPreferences pp=diag();pp.edit().putLong("bggAutoQuarantined",pp.getLong("bggAutoQuarantined",0)+1).putString("lastBggAutoQuarantine",card.title+" · "+gate.reason).apply();
                            continue;
                        }
                    }else{
                        // Exact-name BGG matches can still be the wrong product type (e.g. the board
                        // game Watergate vs books titled Watergate). For titles with learned/seeded
                        // cross-category collisions require a second board-game signal.
                        boolean collisionRisk=marketStore!=null&&marketStore.isCollisionRiskTitle(card.title);
                        BoardGameIntakeGate.Decision matchedGate=BoardGameIntakeGate.matchedAnalysis(card,ga,collisionRisk);
                        if(matchedGate.action==BoardGameIntakeGate.Action.QUARANTINE){
                            DealRecord noisy=database.findByTitlePrice(card.title,(int)Math.round(card.itemPrice*100.0));
                            if(noisy!=null)database.exclude(noisy,"Scarto automatico post-match: "+matchedGate.reason);
                            if(marketStore!=null)marketStore.quarantineCollisionObservation(card,matchedGate.reason,t);
                            SharedPreferences pp=diag();pp.edit().putLong("matchedCollisionQuarantined",pp.getLong("matchedCollisionQuarantined",0)+1).putString("lastMatchedCollision",card.title+" · "+matchedGate.reason).apply();
                            continue;
                        }
                    }
                    // Once the local database knows another valid listing of the same game, Vinted
                    // becomes the primary used-market benchmark. The current card is excluded.
                    Integer localRef=null;
                    if(marketStore!=null&&ga!=null&&!TextUtils.isEmpty(ga.bggId)){
                        localRef=marketStore.localVintedReferenceCents(ga.bggId,DealDatabase.signature(card),ga.languageCode);
                        if(localRef!=null&&localRef>0)ga=ga.withUsedMarketBenchmark(localRef,"vinted_local_median","Vinted · prezzo tipico tra annunci comparabili");
                    }
                    database.record(card, ga, analyzedListing, t);
                    if(marketStore!=null){marketStore.applyAnalysis(card,ga,analyzedListing,t);if(localRef!=null&&ga!=null&&!TextUtils.isEmpty(ga.bggId))marketStore.refreshLocalVintedBenchmarksForBgg(ga.bggId);}
                    DealRecord stored=database.findByTitlePrice(card.title,(int)Math.round(card.itemPrice*100.0));
                    String[] sellerHint=contextualSellerHints.remove(DealDatabase.signature(card));
                    if(stored!=null&&sellerHint!=null&&sellerHint.length>0&&!TextUtils.isEmpty(sellerHint[0])){
                        database.applySellerHint(stored.signature,sellerHint[0],sellerHint.length>1?sellerHint[1]:null);
                        stored=database.findBySignature(stored.signature);bundleDatabase.increment("accessibilitySellerHints");rebuildLocalBundlesForSeller(stored);maybeScanBundles(stored);
                    }
                    if(stored!=null){boolean huntHit=HuntDatabase.evaluateAndNotify(getApplicationContext(),ga,stored);if(huntHit&&marketStore!=null)marketStore.promoteLegacyListingForHunt(stored.signature);DealAlertNotifier.evaluateAndNotify(getApplicationContext(),stored);}
                    // applyAnalysis already enqueues BGG/Vinted durable jobs. :radar only observes
                    // and produces work; the queue process owns network consumption and can batch BGG.
                    maybeResolveLink(card);
                }
                SharedPreferences p2 = diag();
                p2.edit()
                        .putLong("analysesStored", p2.getLong("analysesStored", 0) + count)
                        .putString("lastError", "")
                        .apply();
                analysisBatchInFlight=false;
                sendBroadcast(new android.content.Intent("it.vintedaffari.app.DEALS_UPDATED").setPackage(getPackageName()));
                handler.postDelayed(VintedAccessibilityService.this::continuePersistentAnalysis,450L);
            }

            @Override public void onError(String message) {
                analysisBatchInFlight=false;
                String safe = message == null ? "Errore analisi" : message;
                diag().edit().putString("lastError", safe).apply();
                Log.e(TAG, safe);
                handler.postDelayed(VintedAccessibilityService.this::continuePersistentAnalysis,2_000L);
            }
        });
    }

    private void continuePersistentAnalysis(){
        if(analysisBatchInFlight||engine==null||!engine.isReady()||marketStore==null)return;
        List<VintedCard> next=marketStore.pendingAnalysisCards(40);
        if(!next.isEmpty()){
            long now=System.currentTimeMillis();
            for(VintedCard card:next){String sig=DealDatabase.signature(card);pendingForAnalysis.remove(sig);recentlyAnalyzed.put(sig,now);}
            analyzeBatch(next);
            return;
        }
        // A newer captured scroll stays waiting until the current run finishes all automatic work.
        // Re-check without requiring the user to revisit Vinted.
        if(database!=null&&database.waitingObservationSessionCount()>0)
            handler.postDelayed(VintedAccessibilityService.this::continuePersistentAnalysis,5_000L);
    }


    private void pumpPersistentMarketJobs(){
        if(marketStore==null)return;
        // v5.11.8: Accessibility is the producer/observer, not another queue consumer.
        // A single queue owner (QueueKeepAliveService, with WorkManager as recovery) avoids three
        // independent processors competing for RAM, Vinted pacing and SQLite write locks.
        updatePersistentMaintenanceUi();
        QueueKeepAliveService.ensureRunning(this);
        QueueWorkScheduler.schedule(this);
    }

    private void updatePersistentMaintenanceUi(){
        if(marketStore==null)return;MarketStore.JobSummary s=marketStore.jobSummary();
        diag().edit().putInt("persistentJobsPending",s.pending).putInt("persistentJobsProcessing",s.processing).putInt("persistentJobsRetryable",s.retryable).putInt("persistentJobsPermanent",s.permanent).apply();
        // Durable Database maintenance has its own single aggregate card in Activity.
        // Do not represent a Vinted cooldown as if the whole application queue were paused.
        if(s.active()==0)diag().edit().putLong("databaseVintedResumeAt",0L).apply();
    }

    private void enrichBacklog(){
        // Network enrichment is owned by QueueKeepAliveService/WorkManager. Keeping Accessibility
        // out of the consumer side prevents per-item BGG calls in :radar from defeating batching.
        if(marketStore==null)return;
        QueueKeepAliveService.ensureRunning(this);
        QueueWorkScheduler.schedule(this);
    }
    private void scanBundleBacklog(){scanBundleBacklog(false);}
    private void scanBundleBacklog(boolean force){
        if(manualBulkMode)return;
        if(marketStore!=null&&marketStore.vintedActiveCount()>20)return;
        if(database==null||bundleDatabase==null)return;Set<String>seen=new HashSet<>();DealRecord best=null;double bestScore=-1;
        for(DealRecord d:database.getDeals("all_with_review",240)){if(d==null||d.sellerId==null||d.sellerId.isEmpty()||!seen.add(d.sellerId)||bundleDatabase.countForSource(d.signature)>0)continue;if(!force&&!networkPriority(d))continue;long last=bundleDatabase.lastAttemptForSeller(d.sellerId);if(!force&&System.currentTimeMillis()-last<2*60*60_000L)continue;double score=bundlePriority(d);if(score>bestScore){bestScore=score;best=d;}}
        if(best!=null)maybeScanBundles(best,force);
    }

    private boolean networkPriority(DealRecord d){if(d==null)return false;if("hot".equals(d.tier))return true;if(d.qualityScore!=null&&d.qualityScore>=78)return true;return d.rating!=null&&d.rating>=7.5&&System.currentTimeMillis()-d.firstSeen<6*60*60_000L;}
    private double bundlePriority(DealRecord d){double p=d.qualityScore==null?50:d.qualityScore;if("hot".equals(d.tier))p+=30;if(d.rating!=null)p+=d.rating*2;long age=Math.max(0,System.currentTimeMillis()-d.firstSeen);p+=Math.max(0,18-age/3_600_000.0);return p;}

    private void resolveBacklog(){
        if(database==null||linkResolver==null||linkNetworkInFlight)return;
        if(!manualMetadataRefresh&&marketStore!=null&&marketStore.jobSummary().active()>0)return;
        long now=System.currentTimeMillis();
        DealRecord chosen=null;
        if(manualMetadataRefresh&&!TextUtils.isEmpty(manualRefreshTargetSignature)){
            DealRecord target=database.findBySignature(manualRefreshTargetSignature);
            if(targetNeedsVinted(target))chosen=target;
            else {maybeFinishCurrentManualTarget();return;}
        }else{
            long gap=PRIORITY_LINK_GAP_MS;if(now-lastPriorityLinkAttemptAt<gap)return;
            for(DealRecord d:database.getDealsNeedingLinkMetadata(12))if(networkPriority(d)){chosen=d;break;}
            if(chosen==null)for(DealRecord d:database.getUnresolvedDeals(20))if(networkPriority(d)){chosen=d;break;}
            if(chosen==null)for(DealRecord d:database.getDealsNeedingPublishedTime(8))if(networkPriority(d)){chosen=d;break;}
        }
        if(chosen==null)return;
        long allowed=Math.max(linkResolver.nextAllowedAt(chosen),lastPriorityLinkAttemptAt+currentLinkGapMs(chosen));
        if(now<allowed){if(manualMetadataRefresh)setVintedPause(allowed,"attesa protettiva Vinted");return;}
        if(manualMetadataRefresh){manualRefreshAttempted.add(chosen.signature);OperationCenter.running(this,manualMaintenanceTask,OperationCenter.MAINTENANCE,refreshTaskDetail(chosen,"Controllo Vinted"));OperationCenter.progress(this,manualMaintenanceTask,refreshSessionProgress(chosen,chosen.signature),0L);updateMasterProgress();}
        resolvePriority(chosen);
    }

    private String maintenanceProgress(String current){
        int remaining=manualRefreshQueue.size()+(TextUtils.isEmpty(manualRefreshTargetSignature)?0:1);
        int total=Math.max(manualBulkTotal,manualBulkCompleted+remaining);
        return manualBulkCompleted+"/"+Math.max(1,total)+" completati · "+remaining+" rimasti · "+current;
    }
    private int[] maintenanceCounts(){
        if(database==null)return new int[]{0,0,0};
        int vinted=0,bgg=0;
        if(!TextUtils.isEmpty(manualRefreshTargetSignature)){
            DealRecord d=database.findBySignature(manualRefreshTargetSignature);if(targetNeedsVinted(d))vinted=1;if(targetNeedsBgg(d)&&!bggEnricher.isDeferred(d.bggId))bgg=1;
        }
        return new int[]{vinted+bgg,vinted,bgg};
    }
    private boolean maintenanceComplete(){
        if(TextUtils.isEmpty(manualRefreshTargetSignature))return manualRefreshQueue.isEmpty()&&!linkNetworkInFlight&&(bggEnricher==null||bggEnricher.scheduledCount()==0);
        DealRecord d=database==null?null:database.findBySignature(manualRefreshTargetSignature);
        return !targetNeedsVinted(d)&&!targetNeedsBgg(d)&&!linkNetworkInFlight&&(bggEnricher==null||bggEnricher.scheduledCount()==0);
    }
    private void updateMaintenanceTask(){
        if(!manualMetadataRefresh)return;
        if(!TextUtils.isEmpty(manualRefreshTargetSignature)){
            DealRecord d=database.findBySignature(manualRefreshTargetSignature);int p=refreshSessionProgress(d,manualRefreshTargetSignature);
            long authoritative=!linkNetworkInFlight&&targetNeedsVinted(d)?authoritativeVintedResumeAt(d):0L;
            if(authoritative>System.currentTimeMillis()){if(maintenancePausedUntil!=authoritative)setVintedPause(authoritative,maintenancePauseReason);else{OperationCenter.paused(this,manualMaintenanceTask,OperationCenter.MAINTENANCE,refreshTaskDetail(d,"Vinted in pausa · riprendo al momento consentito"));OperationCenter.progress(this,manualMaintenanceTask,p,authoritative);updateMasterProgress();}return;}
            maintenancePausedUntil=0L;maintenancePauseReason="";
            String stage=linkNetworkInFlight?"Controllo Vinted":(targetNeedsBgg(d)?"Controllo BGG":"Verifico i dati salvati");
            OperationCenter.running(this,manualMaintenanceTask,OperationCenter.MAINTENANCE,refreshTaskDetail(d,stage));OperationCenter.progress(this,manualMaintenanceTask,p,0L);
        }
        updateMasterProgress();
    }

    private void finishManualTargetIfNeeded(String signature,boolean success,String detail){
        if(!manualMetadataRefresh||TextUtils.isEmpty(manualRefreshTargetSignature)||!manualRefreshTargetSignature.equals(signature))return;
        if(success){maybeFinishCurrentManualTarget();}
        else{OperationCenter.error(this,manualMaintenanceTask,OperationCenter.MAINTENANCE,detail,"Ricontrollo non completato");OperationCenter.progress(this,manualMaintenanceTask,refreshSessionProgress(database.findBySignature(signature),signature),0L);manualBulkCompleted++;manualRefreshInitialMissingMask.remove(signature);manualRefreshTargetSignature=null;manualMaintenanceTask=MAINTENANCE_TASK;manualRefreshAttempted.clear();handler.post(this::startNextManualRefreshTarget);}
    }

    private boolean targetNeedsVinted(DealRecord d){return d!=null&&(TextUtils.isEmpty(d.vintedUrl)||TextUtils.isEmpty(d.sellerId)||TextUtils.isEmpty(d.publishedLabel));}
    private boolean targetNeedsBgg(DealRecord d){return d!=null&&!TextUtils.isEmpty(d.bggId)&&TextUtils.isEmpty(d.bggImageUrl);}
    private int refreshMissingMask(DealRecord d){if(d==null)return 0;int m=0;if(TextUtils.isEmpty(d.vintedUrl))m|=1;if(TextUtils.isEmpty(d.sellerId))m|=2;if(TextUtils.isEmpty(d.publishedLabel))m|=4;if(!TextUtils.isEmpty(d.bggId)&&TextUtils.isEmpty(d.bggImageUrl))m|=8;return m;}
    private int maskBits(int mask){return Integer.bitCount(mask);}
    private void rememberRefreshBaseline(String signature,DealRecord d){if(TextUtils.isEmpty(signature)||manualRefreshInitialMissingMask.containsKey(signature))return;manualRefreshInitialMissingMask.put(signature,refreshMissingMask(d));}
    private int refreshSessionProgress(DealRecord d,String signature){if(d==null||TextUtils.isEmpty(signature))return 0;Integer initial=manualRefreshInitialMissingMask.get(signature);if(initial==null){rememberRefreshBaseline(signature,d);initial=manualRefreshInitialMissingMask.get(signature);}int total=maskBits(initial==null?0:initial);if(total<=0)return 100;int remaining=maskBits((initial==null?0:initial)&refreshMissingMask(d));return (int)Math.round((total-remaining)*100.0/total);}
    private long currentLinkGapMs(){DealRecord d=database==null||TextUtils.isEmpty(manualRefreshTargetSignature)?null:database.findBySignature(manualRefreshTargetSignature);return currentLinkGapMs(d);}
    private long currentLinkGapMs(DealRecord d){if(!manualBulkMode)return PRIORITY_LINK_GAP_MS;int requests=d!=null&&TextUtils.isEmpty(d.vintedUrl)?2:1;return VintedPublicSession.recommendedBulkGapMs(requests);}
    private long authoritativeVintedResumeAt(DealRecord d){long until=0L;if(linkResolver!=null)until=Math.max(until,linkResolver.nextAllowedAt(d));if(lastPriorityLinkAttemptAt>0L)until=Math.max(until,lastPriorityLinkAttemptAt+currentLinkGapMs());return until;}
    private void setVintedPause(long until,String reason){
        if(until<=System.currentTimeMillis())return;maintenancePausedUntil=until;maintenancePauseReason=TextUtils.isEmpty(reason)?"protezione Vinted":reason;
        diag().edit().putLong("refreshVintedAuthoritativeUntil",until).putLong("refreshVintedGapMs",currentLinkGapMs()).putString("refreshPauseReason",maintenancePauseReason).apply();
        if(!TextUtils.isEmpty(manualRefreshTargetSignature)){DealRecord d=database==null?null:database.findBySignature(manualRefreshTargetSignature);OperationCenter.paused(this,manualMaintenanceTask,OperationCenter.MAINTENANCE,refreshTaskDetail(d,"Vinted in pausa · riprendo al momento consentito"));OperationCenter.progress(this,manualMaintenanceTask,refreshSessionProgress(d,manualRefreshTargetSignature),until);}
        updateMasterProgress();handler.removeCallbacks(vintedResumeRunnable);handler.postDelayed(vintedResumeRunnable,Math.max(250L,until-System.currentTimeMillis()+150L));
    }
    private String refreshTaskDetail(DealRecord d,String stage){String name=d==null?"Annuncio":(TextUtils.isEmpty(d.vintedTitle)?d.gameName:d.vintedTitle);return (TextUtils.isEmpty(name)?"Annuncio":name)+" · "+stage;}
    private String itemTaskId(String signature){return "maintenance:item:"+signature;}
    private void enqueueManualRefreshTarget(String signature,String taskId,boolean fromBulk){
        if(TextUtils.isEmpty(signature)||database==null)return;DealRecord d=database.findBySignature(signature);if(d==null)return;
        if(!targetNeedsVinted(d)&&!targetNeedsBgg(d)){OperationCenter.resolveForSignature(this,signature,"Dati già completi");return;}
        rememberRefreshBaseline(signature,d);
        if(signature.equals(manualRefreshTargetSignature))return;if(manualRefreshQueued.contains(signature)){if(!fromBulk){manualRefreshQueue.remove(signature);manualRefreshQueue.addFirst(signature);}return;}
        if(fromBulk)manualRefreshQueue.addLast(signature);else manualRefreshQueue.addFirst(signature);manualRefreshQueued.add(signature);String id=TextUtils.isEmpty(taskId)?itemTaskId(signature):taskId;OperationCenter.reset(this,id,OperationCenter.MAINTENANCE,refreshTaskDetail(d,"In coda"));OperationCenter.progress(this,id,0,0L);
        if(!fromBulk&&!manualBulkMode)manualBulkTotal=Math.max(1,manualBulkTotal+1);
        manualMetadataRefresh=true;
    }
    private void enqueueAllMissingForRefresh(){
        if(database==null)return;LinkedHashSet<String> all=new LinkedHashSet<>();
        for(DealRecord d:database.getDealsNeedingPublishedTime(500))all.add(d.signature);for(DealRecord d:database.getDealsNeedingLinkMetadata(500))all.add(d.signature);for(DealRecord d:database.getUnresolvedDeals(500))all.add(d.signature);for(DealRecord d:database.getDealsMissingBggEnrichment(500))all.add(d.signature);
        manualBulkMode=true;diag().edit().putBoolean("manualBulkRequested",true).apply();int added=0;for(String sig:all){if(sig.equals(manualRefreshTargetSignature)||manualRefreshQueued.contains(sig))continue;enqueueManualRefreshTarget(sig,itemTaskId(sig),true);added++;}
        int active=(TextUtils.isEmpty(manualRefreshTargetSignature)?0:1)+manualRefreshQueue.size();manualBulkTotal=Math.max(manualBulkTotal,manualBulkCompleted+active);manualMetadataRefresh=active>0;
        if(active==0){OperationCenter.done(this,MAINTENANCE_TASK,OperationCenter.MAINTENANCE,"Tutti i dati disponibili sono aggiornati");manualBulkMode=false;manualBulkTotal=manualBulkCompleted=0;diag().edit().putBoolean("manualBulkRequested",false).putBoolean("refreshBulkMode",false).putInt("refreshBulkQueued",0).putString("refreshBulkCurrent","").apply();return;}
        OperationCenter.queued(this,MAINTENANCE_TASK,OperationCenter.MAINTENANCE,"Preparo "+active+" giochi");updateMasterProgress();
    }
    private void sweepCompletedQueuedTargets(){
        if(database==null||manualRefreshQueue.isEmpty())return;List<String> completed=new ArrayList<>();for(String sig:new ArrayList<>(manualRefreshQueue)){DealRecord d=database.findBySignature(sig);if(d==null||(!targetNeedsVinted(d)&&!targetNeedsBgg(d))){completed.add(sig);OperationCenter.progress(this,itemTaskId(sig),100,0L);OperationCenter.done(this,itemTaskId(sig),OperationCenter.MAINTENANCE,d==null?"Elemento non più disponibile":refreshTaskDetail(d,"Completato"));OperationCenter.resolveForSignature(this,sig,"Dati aggiornati");manualBulkCompleted++;manualRefreshInitialMissingMask.remove(sig);}else if(!targetNeedsVinted(d)&&targetNeedsBgg(d)&&bggEnricher!=null&&bggEnricher.isDeferred(d.bggId)){completed.add(sig);OperationCenter.error(this,itemTaskId(sig),OperationCenter.MAINTENANCE,refreshTaskDetail(d,"BGG temporaneamente non disponibile"),"Riproverò al prossimo controllo globale");manualBulkCompleted++;manualRefreshInitialMissingMask.remove(sig);}}
        for(String sig:completed){manualRefreshQueue.remove(sig);manualRefreshQueued.remove(sig);}if(!completed.isEmpty())updateMasterProgress();
    }
    private void startNextManualRefreshTarget(){
        if(!TextUtils.isEmpty(manualRefreshTargetSignature))return;
        while(!manualRefreshQueue.isEmpty()){
            String sig=manualRefreshQueue.poll();manualRefreshQueued.remove(sig);DealRecord d=database.findBySignature(sig);if(d==null||(!targetNeedsVinted(d)&&!targetNeedsBgg(d))){OperationCenter.progress(this,itemTaskId(sig),100,0L);OperationCenter.done(this,itemTaskId(sig),OperationCenter.MAINTENANCE,d==null?"Elemento non più disponibile":"Dati già completi");manualBulkCompleted++;manualRefreshInitialMissingMask.remove(sig);continue;}
            rememberRefreshBaseline(sig,d);manualRefreshTargetSignature=sig;manualMaintenanceTask=itemTaskId(sig);manualRefreshAttempted.clear();maintenancePausedUntil=0L;maintenancePauseReason="";int progress=refreshSessionProgress(d,sig);
            long allowed=targetNeedsVinted(d)?authoritativeVintedResumeAt(d):0L;
            if(allowed>System.currentTimeMillis()){setVintedPause(allowed,"attesa protettiva Vinted");}else{OperationCenter.running(this,manualMaintenanceTask,OperationCenter.MAINTENANCE,refreshTaskDetail(d,"Avvio controllo"));OperationCenter.progress(this,manualMaintenanceTask,progress,0L);updateMasterProgress();}
            return;
        }
        manualMetadataRefresh=false;if(manualBulkMode){OperationCenter.done(this,MAINTENANCE_TASK,OperationCenter.MAINTENANCE,"Aggiornamento terminato · "+manualBulkCompleted+" giochi controllati");OperationCenter.progress(this,MAINTENANCE_TASK,100,0L);diag().edit().putBoolean("manualBulkRequested",false).putBoolean("refreshBulkMode",false).putInt("refreshBulkQueued",0).putString("refreshBulkCurrent","").apply();}manualBulkMode=false;manualBulkTotal=manualBulkCompleted=0;manualRefreshInitialMissingMask.clear();handler.removeCallbacks(vintedResumeRunnable);rebuildLocalBundles();sendBroadcast(new Intent("it.vintedaffari.app.DEALS_UPDATED").setPackage(getPackageName()));
    }
    private void maybeFinishCurrentManualTarget(){
        if(!manualMetadataRefresh||TextUtils.isEmpty(manualRefreshTargetSignature)||database==null)return;DealRecord d=database.findBySignature(manualRefreshTargetSignature);OperationCenter.progress(this,manualMaintenanceTask,refreshSessionProgress(d,manualRefreshTargetSignature),maintenancePausedUntil);
        if(targetNeedsBgg(d)&&bggEnricher!=null&&bggEnricher.isDeferred(d.bggId)&&!targetNeedsVinted(d)&&!linkNetworkInFlight){String failed=manualRefreshTargetSignature;OperationCenter.error(this,manualMaintenanceTask,OperationCenter.MAINTENANCE,refreshTaskDetail(d,"BGG temporaneamente non disponibile"),"Riproverò al prossimo controllo globale");manualBulkCompleted++;manualRefreshInitialMissingMask.remove(failed);manualRefreshTargetSignature=null;manualMaintenanceTask=MAINTENANCE_TASK;manualRefreshAttempted.clear();updateMasterProgress();handler.post(this::startNextManualRefreshTarget);return;}
        if(targetNeedsVinted(d)||targetNeedsBgg(d)||linkNetworkInFlight||(bggEnricher!=null&&bggEnricher.isScheduled(d==null?null:d.bggId)))return;
        String sig=manualRefreshTargetSignature;OperationCenter.progress(this,manualMaintenanceTask,100,0L);OperationCenter.done(this,manualMaintenanceTask,OperationCenter.MAINTENANCE,refreshTaskDetail(d,"Completato"));OperationCenter.resolveForSignature(this,sig,"Dati aggiornati");manualBulkCompleted++;manualRefreshInitialMissingMask.remove(sig);manualRefreshTargetSignature=null;manualMaintenanceTask=MAINTENANCE_TASK;manualRefreshAttempted.clear();maintenancePausedUntil=0L;maintenancePauseReason="";updateMasterProgress();handler.post(this::startNextManualRefreshTarget);
    }
    private void updateMasterProgress(){
        if(!manualBulkMode)return;int remaining=manualRefreshQueue.size()+(TextUtils.isEmpty(manualRefreshTargetSignature)?0:1);int total=Math.max(1,manualBulkTotal);diag().edit().putBoolean("refreshBulkMode",true).putInt("refreshBulkTotal",total).putInt("refreshBulkCompleted",manualBulkCompleted).putInt("refreshBulkQueued",manualRefreshQueue.size()).putString("refreshBulkCurrent",manualRefreshTargetSignature==null?"":manualRefreshTargetSignature).putLong("refreshPausedUntil",maintenancePausedUntil).putString("refreshPauseReason",maintenancePauseReason==null?"":maintenancePauseReason).putLong("refreshVintedAuthoritativeUntil",Math.max(maintenancePausedUntil,VintedPublicSession.nextAllowedAt(this))).putLong("refreshVintedGapMs",currentLinkGapMs(database==null||TextUtils.isEmpty(manualRefreshTargetSignature)?null:database.findBySignature(manualRefreshTargetSignature))).apply();int p=(int)Math.round(Math.min(total,manualBulkCompleted)*100.0/total);String detail=manualBulkCompleted+"/"+total+" giochi controllati · "+remaining+" rimasti";
        if(maintenancePausedUntil>System.currentTimeMillis()){OperationCenter.paused(this,MAINTENANCE_TASK,OperationCenter.MAINTENANCE,detail+" · Vinted in pausa, BGG continua");OperationCenter.progress(this,MAINTENANCE_TASK,p,maintenancePausedUntil);}else{OperationCenter.running(this,MAINTENANCE_TASK,OperationCenter.MAINTENANCE,detail);OperationCenter.progress(this,MAINTENANCE_TASK,p,0L);}
    }

    private void resolvePriority(DealRecord d){
        if(d==null||linkNetworkInFlight)return;linkNetworkInFlight=true;lastPriorityLinkAttemptAt=System.currentTimeMillis();String type=TextUtils.isEmpty(d.vintedUrl)?OperationCenter.LINK:OperationCenter.SELLER;String id=(OperationCenter.LINK.equals(type)?"link:":"seller:")+d.signature;OperationCenter.running(this,id,type,d.vintedTitle);
        linkResolver.resolve(d,new AutoLinkResolver.Callback(){
            @Override public void onResolved(VintedLinkResolver.Result r){linkNetworkInFlight=false;if(r.sold){DealRecord sold=database.findBySignature(r.signature);database.markSold(r.signature);if(sold!=null)bundleDatabase.invalidate(sold);OperationCenter.done(VintedAccessibilityService.this,id,type,"Articolo venduto · rimosso da Ludo Scout");finishManualTargetIfNeeded(r.signature,true,"Articolo venduto rimosso");sendBroadcast(new Intent("it.vintedaffari.app.DEALS_UPDATED").setPackage(getPackageName()));handler.postDelayed(VintedAccessibilityService.this::resolveBacklog,1000L);return;}database.applyResolvedLink(r.signature,r.itemId,r.url,r.imageUrl,r.confidence,r.reason,r.sellerId,r.sellerName,r.photosCsv,System.currentTimeMillis());if(!TextUtils.isEmpty(r.publishedLabel)){database.updatePublishedLabel(r.signature,r.publishedLabel);diag().edit().putString("lastPublishedLabel",r.publishedLabel).putLong("publishedMetadataResolved",diag().getLong("publishedMetadataResolved",0)+1).apply();}finishManualTargetIfNeeded(r.signature,true,"Dati annuncio aggiornati");if(!TextUtils.isEmpty(r.sellerId)){bundleDatabase.setDiagnostic(r.signature,r.sellerId,"SELLER_FOUND",0,0,0,null);bundleDatabase.increment("sellerFound");if(r.sellerSnapshot!=null){bundleDatabase.storeSnapshot(r.sellerId,r.itemId,"item-public-page",r.sellerSnapshot);bundleDatabase.setDiagnostic(r.signature,r.sellerId,r.sellerSnapshot.isEmpty()?"SNAPSHOT_EMPTY":"SNAPSHOT_FOUND",r.sellerSnapshot.size(),0,0,null);if(!TextUtils.isEmpty(r.snapshotParser))diag().edit().putString("bundleParser",r.snapshotParser).apply();}}else bundleDatabase.setDiagnostic(r.signature,null,"SELLER_UNKNOWN",0,0,0,"sellerId assente");OperationCenter.done(VintedAccessibilityService.this,id,type,r.matchedTitle);DealRecord fresh=database.findBySignature(r.signature);applyPendingSellerRail(r.signature,r.sellerId,r.sellerName);rebuildLocalBundlesForSeller(fresh);maybeScanBundles(fresh);if(r.imageUrl!=null&&!r.imageUrl.isEmpty())ThumbnailStore.downloadRemote(getApplicationContext(),r.signature,r.imageUrl);SharedPreferences pp=diag();pp.edit().putLong("linksResolved",pp.getLong("linksResolved",0)+1).putString("lastLinkResolution",r.matchedTitle+" → "+r.url+" ("+r.confidence+")").apply();sendBroadcast(new Intent("it.vintedaffari.app.DEALS_UPDATED").setPackage(getPackageName()));handler.postDelayed(VintedAccessibilityService.this::resolveBacklog,1000L);}
            @Override public void onUnresolved(String signature,String reason){linkNetworkInFlight=false;boolean deferred="cooldown".equals(reason)||isRateLimited(reason);if(deferred){manualRefreshAttempted.remove(signature);long until=authoritativeVintedResumeAt(d);if(until<=System.currentTimeMillis())until=System.currentTimeMillis()+currentLinkGapMs(d);maintenancePauseReason=reason;if("cooldown".equals(reason))bundleDatabase.increment("linkCooldownDeferred");else bundleDatabase.increment("rateLimited");OperationCenter.paused(VintedAccessibilityService.this,id,type,"Vinted in pausa · riprovo al momento consentito");if(manualMetadataRefresh)setVintedPause(until,reason);}else{finishManualTargetIfNeeded(signature,false,"Dati Vinted ancora mancanti · "+reason);OperationCenter.error(VintedAccessibilityService.this,id,type,d.vintedTitle,reason);}SharedPreferences pp=diag();pp.edit().putLong("linkResolveMisses",pp.getLong("linkResolveMisses",0)+1).putString("lastLinkResolveMiss",reason).apply();if(!manualMetadataRefresh)handler.postDelayed(VintedAccessibilityService.this::resolveBacklog,currentLinkGapMs(d));}
        });
    }

    private static boolean isRateLimited(String reason){if(reason==null)return false;String r=reason.toLowerCase(Locale.ROOT);return r.contains("429")||r.contains("403")||r.contains("pausa")||r.contains("sospes")||r.contains("budget pubblico");}

    private void maybeResolveLink(VintedCard card) {
        if(card==null||marketStore==null)return;
        QueueKeepAliveService.ensureRunning(this);
        QueueWorkScheduler.schedule(this);
    }

    private void applyPendingSellerRail(String sourceSignature,String sellerId,String sellerName){
        if(TextUtils.isEmpty(sourceSignature)||TextUtils.isEmpty(sellerId))return;List<String> pending=pendingSellerRails.remove(sourceSignature);if(pending==null)return;int applied=0;
        for(String sig:pending){DealRecord candidate=database.findBySignature(sig);if(candidate==null)continue;database.applySellerHint(sig,sellerId,sellerName);applied++;}
        if(applied>0){bundleDatabase.increment("accessibilitySellerHints",applied);diag().edit().putInt("bundleAccessibilityHintsApplied",applied).apply();DealRecord source=database.findBySignature(sourceSignature);rebuildLocalBundlesForSeller(source);}
    }

    private List<BundleSuggestion> buildLocalBundlesForSource(DealRecord source){
        if(source==null||database==null||TextUtils.isEmpty(source.sellerId))return Collections.emptyList();
        List<DealRecord> local=new ArrayList<>();local.add(source);
        for(DealRecord d:database.getDeals("all_with_review",1200))if(d!=null&&!source.signature.equals(d.signature)&&source.sellerId.equals(d.sellerId))local.add(d);
        return BundlePlanner.forSource(source,local);
    }

    private void rebuildLocalBundlesForSeller(DealRecord source){
        if(source==null||bundleDatabase==null||TextUtils.isEmpty(source.sellerId))return;
        List<DealRecord> same=new ArrayList<>();
        for(DealRecord d:database.getDeals("all_with_review",1200))if(d!=null&&source.sellerId.equals(d.sellerId))same.add(d);
        if(same.size()<2){bundleDatabase.replace(source.signature,source.sellerId,Collections.emptyList());return;}
        for(DealRecord d:same){List<BundleSuggestion> out=BundlePlanner.forSource(d,same);bundleDatabase.replace(d.signature,d.sellerId,out);bundleDatabase.setDiagnostic(d.signature,d.sellerId,out.isEmpty()?"NO_BOARDGAMES":"BUNDLE_READY",same.size(),out.size(),out.size(),out.isEmpty()?"Seller graph senza coppie eleggibili":"Seller graph locale");}
    }

    private void rebuildLocalBundles(){
        if(database==null||bundleDatabase==null)return;
        Map<String,List<DealRecord>> bySeller=new LinkedHashMap<>();
        for(DealRecord d:database.getDeals("all_with_review",1500))if(d!=null&&!TextUtils.isEmpty(d.sellerId))bySeller.computeIfAbsent(d.sellerId,k->new ArrayList<>()).add(d);
        int sellers=0,bundles=0;
        for(List<DealRecord> group:bySeller.values()){
            if(group.size()<2)continue;sellers++;
            for(DealRecord d:group){List<BundleSuggestion> out=BundlePlanner.forSource(d,group);bundleDatabase.replace(d.signature,d.sellerId,out);bundleDatabase.setDiagnostic(d.signature,d.sellerId,out.isEmpty()?"NO_BOARDGAMES":"BUNDLE_READY",group.size(),out.size(),out.size(),out.isEmpty()?"Seller graph senza coppie eleggibili":"Seller graph locale");bundles+=out.size();}
        }
        diag().edit().putInt("bundleLocalGraphSellers",sellers).putInt("bundleLocalGraphSuggestions",bundles).apply();
    }

    private void maybeScanBundles(DealRecord source){maybeScanBundles(source,false);}
    private void maybeScanBundles(DealRecord source,boolean force){
        if(manualBulkMode)return;
        if(source==null||bundleScanner==null||bundleDatabase==null||engine==null||!engine.isReady())return;
        if(TextUtils.isEmpty(source.sellerId)){bundleDatabase.setDiagnostic(source.signature,null,"SELLER_UNKNOWN",0,0,0,"sellerId assente");return;}
        long previousAttempt=bundleDatabase.lastAttemptForSeller(source.sellerId);
        synchronized(bundleSellersInFlight){
            if(bundleSellersInFlight.contains(source.sellerId))return;
            if(!force&&previousAttempt>0&&System.currentTimeMillis()-previousAttempt<90_000L)return;
            bundleSellersInFlight.add(source.sellerId);
        }
        bundleDatabase.setDiagnostic(source.signature,source.sellerId,"SELLER_FOUND",0,0,0,null);
        // First source of truth: items already observed by Ludo for this verified seller. This is
        // zero-request and must run even when the public item/profile snapshot is empty.
        List<BundleSuggestion> cachedLocalBundles=buildLocalBundlesForSource(source);
        if(!cachedLocalBundles.isEmpty()){
            bundleDatabase.replace(source.signature,source.sellerId,cachedLocalBundles);
            bundleDatabase.setDiagnostic(source.signature,source.sellerId,"BUNDLE_READY",0,cachedLocalBundles.size(),cachedLocalBundles.size(),"Bundle costruito dal seller graph locale");
            bundleDatabase.increment("bundleCandidates");bundleDatabase.increment("bundleReady",cachedLocalBundles.size());bundleDatabase.increment("deepScanAvoided");
            OperationCenter.done(this,"bundle:"+source.sellerId,OperationCenter.BUNDLE,cachedLocalBundles.size()+" bundle · dati locali verificati");
            finishBundleSeller(source.sellerId,1000L);sendBroadcast(new Intent("it.vintedaffari.app.DEALS_UPDATED").setPackage(getPackageName()));return;
        }
        // Bundle discovery is opportunistic. Never spend the same public Vinted budget while the
        // core listing-enrichment lane has a meaningful backlog; local seller graph above still works.
        if(marketStore!=null&&marketStore.vintedActiveCount()>20){
            bundleDatabase.setDiagnostic(source.signature,source.sellerId,"RATE_LIMITED",0,0,0,"core Vinted queue has priority");
            finishBundleSeller(source.sellerId,60_000L);
            return;
        }
        String snapTask="snapshot:"+source.sellerId;
        OperationCenter.running(this,snapTask,OperationCenter.SNAPSHOT,"seller "+source.sellerId);
        bundleScanner.snapshot(source,new SellerBundleScanner.SnapshotCallback(){
            @Override public void onSnapshot(String sourceSignature,String sellerId,SellerBundleScanner.Snapshot snapshot){
                List<SellerBundleScanner.SellerItem> light=snapshot==null?Collections.emptyList():snapshot.items;
                bundleDatabase.increment("snapshotAnalyzed");
                if(light.isEmpty()){
                    bundleDatabase.setDiagnostic(sourceSignature,sellerId,"SNAPSHOT_EMPTY",0,0,0,"Snapshot leggero vuoto: seller data selettivo");
                    OperationCenter.done(VintedAccessibilityService.this,snapTask,OperationCenter.SNAPSHOT,"snapshot vuoto · seller data selettivo");
                    if(force||networkPriority(source)){
                        bundleDatabase.increment("emptySnapshotProbes");queueDeepScan(source,sourceSignature,sellerId,0,0);return;
                    }
                    bundleDatabase.increment("deepScanAvoided");finishBundleSeller(sellerId,3000L);return;
                }
                bundleDatabase.setDiagnostic(sourceSignature,sellerId,"SNAPSHOT_FOUND",light.size(),0,0,null);
                OperationCenter.done(VintedAccessibilityService.this,snapTask,OperationCenter.SNAPSHOT,light.size()+" elementi · "+snapshot.source);


                List<SellerBundleScanner.SellerItem> cheap=SellerBundleScanner.prefilter(light);
                if(cheap.isEmpty()){
                    if(force||networkPriority(source)){
                        bundleDatabase.increment("thinSnapshotProbes");queueDeepScan(source,sourceSignature,sellerId,light.size(),0);return;
                    }
                    bundleDatabase.setDiagnostic(sourceSignature,sellerId,"NO_BOARDGAMES",light.size(),0,0,"Prefiltro locale: nessun gioco plausibile");
                    bundleDatabase.increment("deepScanAvoided");finishBundleSeller(sellerId,3000L);return;
                }
                if(cheap.size()>16)cheap=new ArrayList<>(cheap.subList(0,16));
                final List<SellerBundleScanner.SellerItem> snapshotCandidates=cheap;
                String preTask="prefilter:"+sellerId;
                OperationCenter.running(VintedAccessibilityService.this,preTask,OperationCenter.MATCH,snapshotCandidates.size()+" elementi snapshot");
                engine.analyze(SellerBundleScanner.asCards(snapshotCandidates),new JsGameEngine.BatchListener(){
                    @Override public void onResult(List<GameAnalysis> analyses){
                        int confirmed=0;for(GameAnalysis a:analyses)if(a!=null&&"matched".equals(a.status)&&!a.languageBlocked&&DealPolicy.ratingEligible(a.averageRating))confirmed++;
                        OperationCenter.done(VintedAccessibilityService.this,preTask,OperationCenter.MATCH,confirmed+" giochi confermati nello snapshot");
                        if(confirmed==0){
                            // A one-item/low-information snapshot is not evidence that the seller has no games.
                            if((force||networkPriority(source))&&light.size()<=2){bundleDatabase.increment("thinSnapshotProbes");queueDeepScan(source,sourceSignature,sellerId,light.size(),0);return;}
                            bundleDatabase.setDiagnostic(sourceSignature,sellerId,"NO_BOARDGAMES",light.size(),0,0,"Matcher locale: nessun gioco confermato");
                            bundleDatabase.increment("deepScanAvoided");finishBundleSeller(sellerId,3000L);return;
                        }
                        bundleDatabase.setDiagnostic(sourceSignature,sellerId,"BUNDLE_CANDIDATE",light.size(),confirmed,0,null);
                        bundleDatabase.increment("bundleCandidates");queueDeepScan(source,sourceSignature,sellerId,light.size(),confirmed);
                    }
                    @Override public void onError(String message){
                        bundleDatabase.setDiagnostic(sourceSignature,sellerId,"ERROR",light.size(),0,0,message);bundleDatabase.increment("errors");
                        OperationCenter.error(VintedAccessibilityService.this,preTask,OperationCenter.MATCH,source.vintedTitle,message);finishBundleSeller(sellerId,5*60_000L);
                    }
                });
            }
            @Override public void onError(String sig,String reason){
                bundleDatabase.setDiagnostic(sig,source.sellerId,"ERROR",0,0,0,reason);bundleDatabase.increment("errors");
                OperationCenter.error(VintedAccessibilityService.this,snapTask,OperationCenter.SNAPSHOT,source.vintedTitle,reason);finishBundleSeller(source.sellerId,5*60_000L);
            }
        });
    }

    private void queueDeepScan(DealRecord source,String sourceSignature,String sellerId,int snapshotCount,int snapshotMatches){
        String deepTask="deep:"+sellerId;String detail=snapshotMatches>0?"seller "+sellerId+" · "+snapshotMatches+" giochi nello snapshot":"seller "+sellerId+" · seller data selettivo";
        OperationCenter.queued(this,deepTask,OperationCenter.DEEP_SCAN,detail);
        bundleDatabase.setDiagnostic(sourceSignature,sellerId,"DEEP_SCAN_QUEUED",snapshotCount,snapshotMatches,0,snapshotMatches>0?null:"Snapshot insufficiente: seller data selettivo");
        handler.postDelayed(()->startDeepScan(source,sourceSignature,sellerId,snapshotCount,snapshotMatches,deepTask),450L);
    }

    private void startDeepScan(DealRecord source,String sourceSignature,String sellerId,int snapshotCount,int snapshotMatches,String deepTask){
        if(marketStore!=null&&marketStore.vintedActiveCount()>20){OperationCenter.doneIfActive(this,deepTask,OperationCenter.DEEP_SCAN,"rimandato · priorità dati annunci");finishBundleSeller(sellerId,60_000L);return;}
        if(manualBulkMode){OperationCenter.doneIfActive(this,deepTask,OperationCenter.DEEP_SCAN,"rimandato · priorità aggiornamento dati");finishBundleSeller(sellerId,5*60_000L);return;}
        synchronized(bundleSellersInFlight){
            if(!bundleSellersInFlight.contains(sellerId)){
                OperationCenter.doneIfActive(this,deepTask,OperationCenter.DEEP_SCAN,"annullato · seller non più in coda");
                return;
            }
        }
        OperationCenter.running(this,deepTask,OperationCenter.DEEP_SCAN,"seller "+sellerId+" · seller data selettivo");
        bundleDatabase.setDiagnostic(sourceSignature,sellerId,"DEEP_SCAN_RUNNING",snapshotCount,snapshotMatches,0,null);
        bundleDatabase.increment("deepScanExecuted");
        bundleScanner.deepScan(source,new SellerBundleScanner.Callback(){
            @Override public void onItems(String sig,String sid,List<SellerBundleScanner.SellerItem> items){
                sendBroadcast(new Intent("it.vintedaffari.app.DEALS_UPDATED").setPackage(getPackageName()));
                List<SellerBundleScanner.SellerItem> candidates=rankProfileCandidates(items,64);
                OperationCenter.done(VintedAccessibilityService.this,deepTask,OperationCenter.DEEP_SCAN,candidates.size()+" candidati seller");
                if(candidates.isEmpty()){
                    bundleDatabase.setDiagnostic(sig,sid,"SNAPSHOT_EMPTY",0,0,0,"Seller data vuoto/non disponibile: attendo altri annunci locali dello stesso venditore");
                    diag().edit().putString("bundleLast","seller "+sid+": nessun catalogo esposto; continuo con il seller graph locale").apply();
                    finishBundleSeller(sid,3000L);return;
                }
                List<VintedCard> cards=SellerBundleScanner.asCards(candidates);String matchTask="match:"+sid;
                OperationCenter.running(VintedAccessibilityService.this,matchTask,OperationCenter.MATCH,candidates.size()+" titoli · nessuna rete");
                engine.analyze(cards,new JsGameEngine.BatchListener(){
                    @Override public void onResult(List<GameAnalysis> analyses){
                        List<SellerBundleScanner.SellerItem> matchedItems=new ArrayList<>();
                        for(int i=0;i<Math.min(candidates.size(),analyses.size());i++){GameAnalysis a=analyses.get(i);if(a!=null&&"matched".equals(a.status)&&!a.languageBlocked&&DealPolicy.ratingEligible(a.averageRating))matchedItems.add(candidates.get(i));}
                        OperationCenter.done(VintedAccessibilityService.this,matchTask,OperationCenter.MATCH,matchedItems.size()+" candidati BGG");
                        if(matchedItems.isEmpty()){
                            bundleDatabase.setDiagnostic(sig,sid,"NO_BOARDGAMES",items==null?0:items.size(),0,0,"Nessun titolo seller riconosciuto dal matcher locale");finishBundleSeller(sid,3000L);return;
                        }
                        bundleDatabase.setDiagnostic(sig,sid,"BUNDLE_CANDIDATE",items==null?0:items.size(),matchedItems.size(),0,"Ownership da verificare solo sui match BGG");
                        bundleDatabase.increment("bundleCandidates");verifyBundleMatches(source,sig,sid,items==null?0:items.size(),matchedItems);
                    }
                    @Override public void onError(String message){
                        bundleDatabase.setDiagnostic(sig,sid,"ERROR",items==null?0:items.size(),0,0,message);bundleDatabase.increment("errors");
                        OperationCenter.error(VintedAccessibilityService.this,matchTask,OperationCenter.MATCH,source.vintedTitle,message);finishBundleSeller(sid,5*60_000L);
                    }
                });
            }
            @Override public void onDeferred(String sig,String reason){
                bundleDatabase.setDiagnostic(sig,sellerId,"RATE_LIMITED",snapshotCount,snapshotMatches,0,reason);
                OperationCenter.done(VintedAccessibilityService.this,deepTask,OperationCenter.DEEP_SCAN,"rimandato automaticamente · "+reason);
                OperationCenter.done(VintedAccessibilityService.this,"bundle:"+sellerId,OperationCenter.BUNDLE,"in pausa per proteggere Vinted");finishBundleSeller(sellerId,45*60_000L);
            }
            @Override public void onError(String sig,String reason){
                bundleDatabase.setDiagnostic(sig,sellerId,"ERROR",snapshotCount,snapshotMatches,0,reason);bundleDatabase.increment("errors");
                OperationCenter.error(VintedAccessibilityService.this,deepTask,OperationCenter.DEEP_SCAN,source.vintedTitle,reason);finishBundleSeller(sellerId,5*60_000L);
            }
        });
    }

    private List<SellerBundleScanner.SellerItem> rankProfileCandidates(List<SellerBundleScanner.SellerItem> items,int max){
        if(items==null)return new ArrayList<>();List<SellerBundleScanner.SellerItem> out=new ArrayList<>();Set<String> ids=new HashSet<>();
        for(SellerBundleScanner.SellerItem s:items)if(s!=null&&!TextUtils.isEmpty(s.id)&&!TextUtils.isEmpty(s.title)&&ids.add(s.id))out.add(s);
        out.sort((a,b)->{int av=(SellerBundleScanner.likelyBoardgame(a)?3:0)+(a.ownerVerified?1:0),bv=(SellerBundleScanner.likelyBoardgame(b)?3:0)+(b.ownerVerified?1:0);return Integer.compare(bv,av);});
        return out.size()>max?new ArrayList<>(out.subList(0,max)):out;
    }

    private void verifyBundleMatches(DealRecord source,String sig,String sid,int profileCount,List<SellerBundleScanner.SellerItem> matchedItems){
        String verifyTask="verify:"+sid;OperationCenter.running(this,verifyTask,OperationCenter.SELLER,matchedItems.size()+" match BGG · max 3 verifiche pubbliche");
        bundleScanner.verifyMatches(source,matchedItems,3,new SellerBundleScanner.Callback(){
            @Override public void onItems(String signature,String seller,List<SellerBundleScanner.SellerItem> verified){
                if(verified==null||verified.isEmpty()){
                    OperationCenter.done(VintedAccessibilityService.this,verifyTask,OperationCenter.SELLER,"nessun candidato appartiene al seller");
                    bundleDatabase.setDiagnostic(signature,seller,"NO_BOARDGAMES",profileCount,0,0,"Match BGG non confermati come articoli dello stesso seller");finishBundleSeller(seller,3000L);return;
                }
                List<SellerBundleScanner.SellerItem> finalItems=verified.size()>20?new ArrayList<>(verified.subList(0,20)):verified;
                List<VintedCard> finalCards=SellerBundleScanner.asCards(finalItems);OperationCenter.done(VintedAccessibilityService.this,verifyTask,OperationCenter.SELLER,finalItems.size()+" articoli ownership verificata");
                String finalTask="bundle-match:"+seller;OperationCenter.running(VintedAccessibilityService.this,finalTask,OperationCenter.MATCH,finalItems.size()+" articoli verificati");
                engine.analyze(finalCards,new JsGameEngine.BatchListener(){
                    @Override public void onResult(List<GameAnalysis> analyses){
                        List<DealRecord> records=new ArrayList<>();records.add(source);int matched=0;
                        for(int i=0;i<Math.min(finalItems.size(),analyses.size());i++){
                            GameAnalysis a=analyses.get(i);if(a==null||!"matched".equals(a.status)||a.languageBlocked||!DealPolicy.ratingEligible(a.averageRating))continue;matched++;
                            DealRecord d=database.recordSellerItem(finalItems.get(i),seller,finalCards.get(i),a);if(d!=null)records.add(d);
                        }
                        List<BundleSuggestion> out=BundlePlanner.forSource(source,records);bundleDatabase.replace(signature,seller,out);
                        bundleDatabase.setDiagnostic(signature,seller,out.isEmpty()?"NO_BOARDGAMES":"BUNDLE_READY",profileCount,matched,out.size(),out.isEmpty()?"Articoli seller verificati, ma nessun bundle pubblicabile":null);
                        if(!out.isEmpty())bundleDatabase.increment("bundleReady",out.size());
                        OperationCenter.done(VintedAccessibilityService.this,finalTask,OperationCenter.MATCH,matched+" match BGG verificati");
                        OperationCenter.done(VintedAccessibilityService.this,"bundle:"+seller,OperationCenter.BUNDLE,out.size()+" pronti");
                        SharedPreferences pp=diag();pp.edit().putLong("bundleScans",pp.getLong("bundleScans",0)+1).putInt("bundleLastCount",out.size()).putString("bundleLast",out.isEmpty()?"nessun bundle dopo verifica seller":"bundle trovati: "+out.size()).apply();
                        finishBundleSeller(seller,3000L);sendBroadcast(new Intent("it.vintedaffari.app.DEALS_UPDATED").setPackage(getPackageName()));
                    }
                    @Override public void onError(String message){bundleDatabase.setDiagnostic(signature,seller,"ERROR",profileCount,0,0,message);bundleDatabase.increment("errors");OperationCenter.error(VintedAccessibilityService.this,finalTask,OperationCenter.MATCH,source.vintedTitle,message);finishBundleSeller(seller,5*60_000L);}
                });
            }
            @Override public void onDeferred(String signature,String reason){
                bundleDatabase.setDiagnostic(signature,sid,"RATE_LIMITED",profileCount,matchedItems.size(),0,reason);
                OperationCenter.done(VintedAccessibilityService.this,verifyTask,OperationCenter.SELLER,"rimandato automaticamente · "+reason);OperationCenter.done(VintedAccessibilityService.this,"bundle:"+sid,OperationCenter.BUNDLE,"in pausa per proteggere Vinted");finishBundleSeller(sid,45*60_000L);
            }
            @Override public void onError(String signature,String reason){bundleDatabase.setDiagnostic(signature,sid,"ERROR",profileCount,0,0,reason);bundleDatabase.increment("errors");OperationCenter.error(VintedAccessibilityService.this,verifyTask,OperationCenter.SELLER,source.vintedTitle,reason);finishBundleSeller(sid,5*60_000L);}
        });
    }

    private void finishBundleSeller(String sellerId,long nextDelay){
        synchronized(bundleSellersInFlight){bundleSellersInFlight.remove(sellerId);}
        // Every exit path is terminal for the visible queue. This prevents "Bundle scan" rows
        // remaining permanently queued when a seller is skipped/deferred before startDeepScan.
        OperationCenter.doneIfActive(this,"deep:"+sellerId,OperationCenter.DEEP_SCAN,"chiuso · nessuna operazione pendente");
        OperationCenter.doneIfActive(this,"snapshot:"+sellerId,OperationCenter.SNAPSHOT,"chiuso · nessuna operazione pendente");
        if(nextDelay>0)handler.postDelayed(()->scanBundleBacklog(false),nextDelay);
    }

    private void handleProductPage(ProductPage page) {
        if (page == null || database == null) return;
        int priceCents=(int)Math.round(page.itemPrice*100.0);
        Integer ship=page.shippingPrice==null?null:(int)Math.round(page.shippingPrice*100.0);
        database.updateProductContext(page.title,priceCents,ship,page.publishedLabel,System.currentTimeMillis());
        DealRecord d=database.findByTitlePrice(page.title,priceCents);
        String sig=d==null?"":d.signature;
        SharedPreferences.Editor e=diag().edit().putString("lastProductTitle",page.title).putInt("lastProductPriceCents",priceCents);
        if(ship!=null)e.putInt("lastProductShippingCents",ship);else e.remove("lastProductShippingCents");
        if(sig!=null && !sig.isEmpty()){
            e.putString("lastProductSignature",sig);ThumbnailStore.captureProduct(this,page,sig);
            long listingId=marketStore==null?0L:marketStore.listingIdForSignature(sig);
            if(!TextUtils.isEmpty(page.sellerName)){
                database.updateSellerNameHint(sig,page.sellerName);
                if(marketStore!=null&&listingId>0)marketStore.setSellerHint(listingId,page.sellerName);
                e.putString("lastProductSeller",page.sellerName);
            }
            // The opened product page can expose the exact edition/variant even when the feed card
            // only said "Tokaido". Reconcile locally against the bundled BGG index; no HTTP here.
            if(marketStore!=null&&listingId>0&&(!TextUtils.isEmpty(page.detailsText)||!TextUtils.isEmpty(page.title)))
                BggVariantReconciler.reconcile(this,database,marketStore,listingId,sig,page.title,page.detailsText);
        }
        e.apply();
        sendBroadcast(new android.content.Intent("it.vintedaffari.app.DEALS_UPDATED").setPackage(getPackageName()));
    }
    private List<VintedCard> collectSellerRecommendationCards(AccessibilityNodeInfo root){
        AccessibilityNodeInfo heading=findSellerRailHeading(root);if(heading==null)return Collections.emptyList();AccessibilityNodeInfo section=heading.getParent();
        for(int depth=0;section!=null&&depth<4;depth++){
            List<VintedCard> cards=new ArrayList<>();collectCards(section,cards);
            if(!cards.isEmpty()&&cards.size()<=12)return cards;
            if(cards.size()>12)return Collections.emptyList();
            section=section.getParent();
        }
        return Collections.emptyList();
    }
    private AccessibilityNodeInfo findSellerRailHeading(AccessibilityNodeInfo node){
        if(node==null)return null;String text=((node.getText()==null?"":node.getText().toString())+" "+(node.getContentDescription()==null?"":node.getContentDescription().toString())).toLowerCase(Locale.ROOT);
        if(text.contains("articoli dell'utente")||text.contains("articoli del venditore")||text.contains("articles du membre")||text.contains("articles du vendeur")||text.contains("seller's items")||text.contains("member's items"))return node;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);AccessibilityNodeInfo found=findSellerRailHeading(child);if(found!=null)return found;}
        return null;
    }

    private AccessibilityNodeInfo findVisibleVintedRoot(){
        try{for(AccessibilityWindowInfo w:getWindows()){if(w==null)continue;AccessibilityNodeInfo r=w.getRoot();if(r==null)continue;CharSequence pkg=r.getPackageName();if(pkg!=null&&VINTED_PACKAGE.contentEquals(pkg))return r;}}catch(Throwable ignored){}return null;
    }

    private void collectCards(AccessibilityNodeInfo node, List<VintedCard> out) {
        if (node == null) return;
        String id = node.getViewIdResourceName();CharSequence desc=node.getContentDescription();CharSequence text=node.getText();
        boolean knownCardId=id!=null&&(id.endsWith("more_homepage_items_item")||id.contains("items_item"));
        String own=((desc==null?"":desc.toString())+" "+(text==null?"":text.toString())).trim();
        String candidate=own;
        if(knownCardId||node.isClickable()){
            String blob=semanticBlob(node,0,3,1600,new StringBuilder()).toString();
            // Known Vinted item containers are a stronger signal than optional labels such as
            // Brand/Condizioni, which Vinted does not expose consistently for every card.
            if((knownCardId&&blob.contains("€"))||looksLikeCardText(blob))candidate=blob;
        }
        if((knownCardId&&candidate.contains("€"))||looksLikeCardText(candidate)){
            Rect bounds=new Rect();node.getBoundsInScreen(bounds);VintedCard card=VintedCardParser.parse(candidate,bounds);
            if(card!=null&&!containsSignature(out,card)){
                out.add(card);
                String signature=DealDatabase.signature(card);
                // Test 1 conclusively found no usable ID in Vinted's Compose semantics. Keep only a
                // cheap opportunistic explicit-URL check; the old 96-node diagnostic probe was
                // production overhead and could make scrolling visibly heavy.
                String[] identity=explicitVintedIdentityHintFast(node);
                if(identity!=null)contextualVintedIdentityHints.put(signature,identity);
            } else if(card==null&&candidate.length()<700)diag().edit().putString("lastUnparsedCardSample",truncate(candidate,600)).apply();
        }
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null)collectCards(child,out);}
    }

    private void applyExplicitVintedIdentityHint(VintedCard card,String signature){
        if(card==null||TextUtils.isEmpty(signature)||marketStore==null||database==null)return;
        String[] hint=contextualVintedIdentityHints.remove(signature);if(hint==null||hint.length<2||TextUtils.isEmpty(hint[0])||TextUtils.isEmpty(hint[1]))return;
        long listingId=marketStore.listingIdForSignature(signature);if(listingId<=0)return;
        MarketListingRecord existing=marketStore.listing(listingId);if(existing!=null&&!TextUtils.isEmpty(existing.url))return;
        String itemId=hint[0],url=hint[1],source=hint.length>2?hint[2]:"unknown";long now=System.currentTimeMillis();
        database.applyResolvedLink(signature,itemId,url,null,100,"ID/URL esposto direttamente dalla card Vinted",null,card.sellerName,null,now);
        long canonical=marketStore.applyManualVintedLink(0,listingId,url,itemId,card.sellerName,null);
        if(canonical>0)marketStore.enqueueDeepMetadata(canonical);
        SharedPreferences p=diag();SharedPreferences.Editor ed=p.edit()
                .putLong("vintedIdsCapturedFromAccessibility",p.getLong("vintedIdsCapturedFromAccessibility",0L)+1L)
                .putString("lastAccessibilityVintedId",itemId)
                .putString("a11yIdentityLastSource",source);
        if("url_span".equals(source))ed.putLong("a11yIdsFromUrlSpan",p.getLong("a11yIdsFromUrlSpan",0L)+1L);
        else ed.putLong("a11yIdsFromOtherExplicitField",p.getLong("a11yIdsFromOtherExplicitField",0L)+1L);
        ed.apply();
    }

    /** Production fast path: accept only a literal Vinted item URL/ID already exposed in a
     * small nearby accessibility surface. No Compose extra-data requests and no diagnostic tree
     * walk. This preserves the conservative opportunistic shortcut without paying Test-1 costs. */
    private String[] explicitVintedIdentityHintFast(AccessibilityNodeInfo root){
        if(root==null)return null;
        java.util.ArrayDeque<AccessibilityNodeInfo> q=new java.util.ArrayDeque<>();q.add(root);int seen=0;
        while(!q.isEmpty()&&seen++<12){
            AccessibilityNodeInfo n=q.removeFirst();String[] h=identityFromNodeFast(n,"");if(h!=null)return h;
            if(seen<=4)for(int i=0;i<n.getChildCount()&&i<6;i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null)q.addLast(c);}
        }
        AccessibilityNodeInfo parent=null;try{parent=root.getParent();}catch(Throwable ignored){}
        for(int depth=0;parent!=null&&depth<2;depth++){
            String[] h=identityFromNodeFast(parent,"ancestor_");if(h!=null)return h;
            AccessibilityNodeInfo next=null;try{next=parent.getParent();}catch(Throwable ignored){}parent=next;
        }
        return null;
    }

    private String[] identityFromNodeFast(AccessibilityNodeInfo n,String sourcePrefix){
        if(n==null)return null;IdentityProbeStats stats=new IdentityProbeStats();String[] h=identityFromSpans(n,stats);
        if(h!=null&&sourcePrefix.length()>0)h[2]=sourcePrefix+h[2];
        if(h==null)h=identityFromValue(n.getText(),sourcePrefix+"text");
        if(h==null)h=identityFromValue(n.getContentDescription(),sourcePrefix+"content_description");
        if(h==null)h=identityFromValue(n.getViewIdResourceName(),sourcePrefix+"view_id_resource_name");
        if(h==null&&Build.VERSION.SDK_INT>=33)try{h=identityFromValue(n.getUniqueId(),sourcePrefix+"unique_id");}catch(Throwable ignored){}
        if(h==null)try{h=identityFromValue(n.getHintText(),sourcePrefix+"hint_text");}catch(Throwable ignored){}
        if(h==null&&Build.VERSION.SDK_INT>=28)try{h=identityFromValue(n.getTooltipText(),sourcePrefix+"tooltip_text");}catch(Throwable ignored){}
        if(h==null&&Build.VERSION.SDK_INT>=30)try{h=identityFromValue(n.getStateDescription(),sourcePrefix+"state_description");}catch(Throwable ignored){}
        return h;
    }

    /** Look for an explicit /items/<id> token already present in the Accessibility subtree.
     * This is deliberately conservative: numeric view ids, recycler positions and uniqueId values
     * without an item URL are diagnostics only and are never promoted to a Vinted identity.
     *
     * The probe also inspects Android semantic surfaces that were not covered by the original
     * implementation (URLSpan, action labels and semantic text properties). None of those values is
     * trusted unless it literally contains /items/<digits>.
     */
    private String[] explicitVintedIdentityHint(AccessibilityNodeInfo root,boolean uniqueProbeCard){
        if(root==null)return null;
        IdentityProbeStats stats=new IdentityProbeStats();
        java.util.ArrayDeque<AccessibilityNodeInfo> q=new java.util.ArrayDeque<>();q.add(root);int seen=0;
        boolean requestComposeExtraData=uniqueProbeCard&&accessibilityIdentityProbeSeen.size()<=80;
        while(!q.isEmpty()&&seen++<96){
            AccessibilityNodeInfo n=q.removeFirst();stats.nodes++;
            String[] h=identityFromNodeSurfaces(n,stats,"",requestComposeExtraData);
            if(h!=null){recordIdentityProbe(stats,uniqueProbeCard,h[2]);return h;}
            for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null)q.addLast(c);}
        }

        // A Compose leaf may contain the entire card description while its semantic/deep-link
        // metadata lives on a wrapper node. Inspect a few direct ancestors, but DO NOT use an
        // ancestor hit to auto-link during this diagnostic experiment: an ancestor can encompass
        // more than one card. We only report the explicit /items/<id> evidence for validation.
        AccessibilityNodeInfo parent=null;
        try{parent=root.getParent();}catch(Throwable ignored){}
        for(int depth=1;parent!=null&&depth<=4;depth++){
            stats.ancestorNodes++;
            String[] ancestorHit=identityFromNodeSurfaces(parent,stats,"ancestor_",requestComposeExtraData);
            if(ancestorHit!=null){
                stats.ancestorExplicitHits++;
                stats.lastAncestorExplicit=truncate(ancestorHit[2]+":"+ancestorHit[0],180);
                break;
            }
            AccessibilityNodeInfo next=null;try{next=parent.getParent();}catch(Throwable ignored){}
            parent=next;
        }
        recordIdentityProbe(stats,uniqueProbeCard,"");return null;
    }

    private String[] identityFromNodeSurfaces(AccessibilityNodeInfo n,IdentityProbeStats stats,String sourcePrefix,boolean requestComposeExtraData){
        if(n==null)return null;
        String[] h=identityFromSpans(n,stats);
        if(h!=null&&sourcePrefix.length()>0)h[2]=sourcePrefix+h[2];
        if(h==null)h=identityFromValue(n.getText(),sourcePrefix+"text");
        if(h==null)h=identityFromValue(n.getContentDescription(),sourcePrefix+"content_description");
        if(h==null)h=identityFromValue(n.getViewIdResourceName(),sourcePrefix+"view_id_resource_name");
        if(h==null&&Build.VERSION.SDK_INT>=33)try{h=identityFromValue(n.getUniqueId(),sourcePrefix+"unique_id");}catch(Throwable ignored){}
        if(h==null)try{h=identityFromValue(n.getHintText(),sourcePrefix+"hint_text");}catch(Throwable ignored){}
        if(h==null&&Build.VERSION.SDK_INT>=28)try{h=identityFromValue(n.getTooltipText(),sourcePrefix+"tooltip_text");}catch(Throwable ignored){}
        if(h==null&&Build.VERSION.SDK_INT>=28)try{h=identityFromValue(n.getPaneTitle(),sourcePrefix+"pane_title");}catch(Throwable ignored){}
        if(h==null&&Build.VERSION.SDK_INT>=30)try{h=identityFromValue(n.getStateDescription(),sourcePrefix+"state_description");}catch(Throwable ignored){}
        if(h==null&&Build.VERSION.SDK_INT>=34)try{h=identityFromValue(n.getContainerTitle(),sourcePrefix+"container_title");}catch(Throwable ignored){}
        if(h==null)try{
            List<AccessibilityNodeInfo.AccessibilityAction> actions=n.getActionList();
            if(actions!=null)for(AccessibilityNodeInfo.AccessibilityAction action:actions){
                CharSequence label=action==null?null:action.getLabel();
                if(label==null||label.length()==0)continue;stats.actionLabels++;
                h=identityFromValue(label,sourcePrefix+"action_label");if(h!=null)break;
            }
        }catch(Throwable ignored){}
        if(h==null)try{
            android.os.Bundle extras=n.getExtras();
            if(extras!=null)for(String k:extras.keySet()){
                stats.extraKeys++;
                Object v=extras.get(k);
                if(v!=null&&!TextUtils.isEmpty(String.valueOf(v)))stats.lastExtraSample=truncate(k+"="+String.valueOf(v),220);
                h=identityFromValue(k,sourcePrefix+"extra_key");if(h!=null)break;
                h=identityFromValue(v==null?null:String.valueOf(v),sourcePrefix+"extra_value");if(h!=null)break;
            }
        }catch(Throwable ignored){}
        try{
            List<String> available=n.getAvailableExtraData();
            if(available!=null&&!available.isEmpty()){
                stats.availableExtraKeys+=available.size();
                stats.lastAvailableExtra=truncate(TextUtils.join(",",available),180);
                if(h==null)for(String key:available){h=identityFromValue(key,sourcePrefix+"available_extra_key");if(h!=null)break;}
                if(h==null&&requestComposeExtraData&&Build.VERSION.SDK_INT>=26){
                    int requested=0;
                    for(String key:available){
                        if(key==null||!key.toLowerCase(Locale.ROOT).contains("semantics")||requested++>=4)continue;
                        stats.extraRefreshRequests++;
                        try{
                            boolean refreshed=n.refreshWithExtraData(key,new android.os.Bundle());
                            if(!refreshed)continue;
                            Object value=n.getExtras()==null?null:n.getExtras().get(key);
                            if(value!=null&&!TextUtils.isEmpty(String.valueOf(value))){
                                stats.extraRefreshHits++;
                                stats.lastRequestedExtra=truncate(key+"="+String.valueOf(value),220);
                                h=identityFromValue(String.valueOf(value),sourcePrefix+"requested_extra_value");
                                if(h!=null)break;
                            }
                        }catch(Throwable ignored){}
                    }
                }
            }
        }catch(Throwable ignored){}
        return h;
    }

    private static final class IdentityProbeStats{
        int nodes,ancestorNodes,spannedTextNodes,urlSpans,clickableSpans,actionLabels,extraKeys,availableExtraKeys,extraRefreshRequests,extraRefreshHits,ancestorExplicitHits;
        String lastUrlSpan="",lastAvailableExtra="",lastExtraSample="",lastRequestedExtra="",lastAncestorExplicit="";
    }

    private String[] identityFromSpans(AccessibilityNodeInfo node,IdentityProbeStats stats){
        CharSequence raw;
        try{raw=node.getText();}catch(Throwable ignored){return null;}
        if(!(raw instanceof Spanned))return null;
        stats.spannedTextNodes++;
        Spanned sp=(Spanned)raw;
        try{
            ClickableSpan[] clickable=sp.getSpans(0,sp.length(),ClickableSpan.class);
            if(clickable!=null)stats.clickableSpans+=clickable.length;
        }catch(Throwable ignored){}
        try{
            URLSpan[] urls=sp.getSpans(0,sp.length(),URLSpan.class);
            if(urls!=null)for(URLSpan span:urls){
                if(span==null)continue;stats.urlSpans++;
                String url=span.getURL();if(!TextUtils.isEmpty(url))stats.lastUrlSpan=truncate(url,220);
                String[] h=identityFromValue(url,"url_span");if(h!=null)return h;
            }
        }catch(Throwable ignored){}
        return null;
    }

    private void recordIdentityProbe(IdentityProbeStats stats,boolean uniqueProbeCard,String capturedSource){
        SharedPreferences p=diag();SharedPreferences.Editor e=p.edit();
        boolean freshProbe=!"1d".equals(p.getString("a11yProbeVersion",""));
        long runs=freshProbe?0L:p.getLong("a11yProbeRuns",0L);
        long unique=freshProbe?0L:p.getLong("a11yProbeUniqueCards",0L);
        long nodes=freshProbe?0L:p.getLong("a11yProbeNodes",0L);
        long ancestors=freshProbe?0L:p.getLong("a11yProbeAncestorNodes",0L);
        long spanned=freshProbe?0L:p.getLong("a11yProbeSpannedTextNodes",0L);
        long urls=freshProbe?0L:p.getLong("a11yProbeUrlSpans",0L);
        long clicks=freshProbe?0L:p.getLong("a11yProbeClickableSpans",0L);
        long actions=freshProbe?0L:p.getLong("a11yProbeActionLabels",0L);
        long extraKeys=freshProbe?0L:p.getLong("a11yProbeExtraKeys",0L);
        long available=freshProbe?0L:p.getLong("a11yProbeAvailableExtraKeys",0L);
        long refreshReq=freshProbe?0L:p.getLong("a11yProbeExtraRefreshRequests",0L);
        long refreshHits=freshProbe?0L:p.getLong("a11yProbeExtraRefreshHits",0L);
        long ancestorHits=freshProbe?0L:p.getLong("a11yProbeAncestorExplicitHits",0L);
        long captures=freshProbe?0L:p.getLong("a11yProbeCaptures",0L);
        long urlCaptures=freshProbe?0L:p.getLong("a11yProbeUrlCaptures",0L);
        if(freshProbe)e.putString("a11yProbeVersion","1d")
                .remove("a11yProbeLastUrlSpan").remove("a11yProbeLastAvailableExtra")
                .remove("a11yProbeLastExtraSample").remove("a11yProbeLastRequestedExtra")
                .remove("a11yProbeLastAncestorExplicit").remove("a11yProbeLastCaptureSource");
        long totalRuns=runs+1L,totalUnique=unique+(uniqueProbeCard?1L:0L),totalNodes=nodes+stats.nodes,totalAncestors=ancestors+stats.ancestorNodes;
        long totalSpanned=spanned+stats.spannedTextNodes,totalUrls=urls+stats.urlSpans,totalClicks=clicks+stats.clickableSpans,totalActions=actions+stats.actionLabels;
        long totalExtraKeys=extraKeys+stats.extraKeys,totalAvailable=available+stats.availableExtraKeys,totalRefreshReq=refreshReq+stats.extraRefreshRequests,totalRefreshHits=refreshHits+stats.extraRefreshHits,totalAncestorHits=ancestorHits+stats.ancestorExplicitHits;
        long totalCaptures=captures+(TextUtils.isEmpty(capturedSource)?0L:1L),totalUrlCaptures=urlCaptures+(capturedSource!=null&&capturedSource.endsWith("url_span")?1L:0L);
        e.putLong("a11yProbeRuns",totalRuns);
        e.putLong("a11yProbeUniqueCards",totalUnique);
        e.putLong("a11yProbeNodes",totalNodes)
                .putLong("a11yProbeAncestorNodes",totalAncestors)
                .putLong("a11yProbeSpannedTextNodes",totalSpanned)
                .putLong("a11yProbeUrlSpans",totalUrls)
                .putLong("a11yProbeClickableSpans",totalClicks)
                .putLong("a11yProbeActionLabels",totalActions)
                .putLong("a11yProbeExtraKeys",totalExtraKeys)
                .putLong("a11yProbeAvailableExtraKeys",totalAvailable)
                .putLong("a11yProbeExtraRefreshRequests",totalRefreshReq)
                .putLong("a11yProbeExtraRefreshHits",totalRefreshHits)
                .putLong("a11yProbeAncestorExplicitHits",totalAncestorHits)
                .putLong("a11yProbeCaptures",totalCaptures)
                .putLong("a11yProbeUrlCaptures",totalUrlCaptures);
        if(!TextUtils.isEmpty(stats.lastUrlSpan))e.putString("a11yProbeLastUrlSpan",stats.lastUrlSpan);
        if(!TextUtils.isEmpty(stats.lastAvailableExtra))e.putString("a11yProbeLastAvailableExtra",stats.lastAvailableExtra);
        if(!TextUtils.isEmpty(stats.lastExtraSample))e.putString("a11yProbeLastExtraSample",stats.lastExtraSample);
        if(!TextUtils.isEmpty(stats.lastRequestedExtra))e.putString("a11yProbeLastRequestedExtra",stats.lastRequestedExtra);
        if(!TextUtils.isEmpty(stats.lastAncestorExplicit))e.putString("a11yProbeLastAncestorExplicit",stats.lastAncestorExplicit);
        if(!TextUtils.isEmpty(capturedSource))e.putString("a11yProbeLastCaptureSource",capturedSource);
        e.apply();

        // Authoritative cross-process copy. MainActivity runs in :ui while this service runs in
        // :radar; SQLite is already the app's durable coordination channel.
        try{
            JSONObject o=new JSONObject();
            o.put("probe","1d");o.put("uniqueCards",totalUnique);o.put("runs",totalRuns);o.put("nodes",totalNodes);o.put("ancestorNodes",totalAncestors);
            o.put("spannedTextNodes",totalSpanned);o.put("urlSpans",totalUrls);o.put("clickableSpans",totalClicks);o.put("actionLabels",totalActions);
            o.put("extraKeys",totalExtraKeys);o.put("availableExtraKeys",totalAvailable);o.put("extraRefreshRequests",totalRefreshReq);o.put("extraRefreshHits",totalRefreshHits);o.put("ancestorExplicitHits",totalAncestorHits);
            o.put("captures",totalCaptures);o.put("urlSpanCaptures",totalUrlCaptures);
            o.put("lastUrlSpan",!TextUtils.isEmpty(stats.lastUrlSpan)?stats.lastUrlSpan:(freshProbe?"":p.getString("a11yProbeLastUrlSpan","")));
            o.put("lastAvailableExtra",!TextUtils.isEmpty(stats.lastAvailableExtra)?stats.lastAvailableExtra:(freshProbe?"":p.getString("a11yProbeLastAvailableExtra","")));
            o.put("lastExtraSample",!TextUtils.isEmpty(stats.lastExtraSample)?stats.lastExtraSample:(freshProbe?"":p.getString("a11yProbeLastExtraSample","")));
            o.put("lastRequestedExtra",!TextUtils.isEmpty(stats.lastRequestedExtra)?stats.lastRequestedExtra:(freshProbe?"":p.getString("a11yProbeLastRequestedExtra","")));
            o.put("lastAncestorExplicit",!TextUtils.isEmpty(stats.lastAncestorExplicit)?stats.lastAncestorExplicit:(freshProbe?"":p.getString("a11yProbeLastAncestorExplicit","")));
            o.put("lastCaptureSource",!TextUtils.isEmpty(capturedSource)?capturedSource:(freshProbe?"":p.getString("a11yProbeLastCaptureSource","")));
            if(marketStore!=null)marketStore.setDiagnosticState("a11y_probe",totalRuns,o.toString());
        }catch(Throwable t){Log.w(TAG,"cross-process a11y probe snapshot failed",t);}
    }

    private static String[] identityFromValue(CharSequence value){return identityFromValue(value,"explicit_field");}
    private static String[] identityFromValue(CharSequence value,String source){
        if(value==null)return null;String s=value.toString();Matcher a=VINTED_ABSOLUTE_ITEM.matcher(s);if(a.find()){String id=a.group(1);String url=a.group();return new String[]{id,url,source};}
        Matcher r=VINTED_RELATIVE_ITEM.matcher(s);if(r.find()){String id=r.group(1);return new String[]{id,VintedPublicSession.HOST+"/items/"+id,source};}return null;
    }

    private static boolean looksLikeCardText(String s){if(TextUtils.isEmpty(s)||!s.contains("€"))return false;String n=s.toLowerCase(Locale.ROOT);return n.contains("condizioni:")||n.contains("protezione acquisti")||n.contains("brand:");}
    private static StringBuilder semanticBlob(AccessibilityNodeInfo node,int depth,int maxDepth,int maxChars,StringBuilder out){
        if(node==null||depth>maxDepth||out.length()>=maxChars)return out;appendSemantic(out,node.getContentDescription(),maxChars);appendSemantic(out,node.getText(),maxChars);
        for(int i=0;i<node.getChildCount()&&out.length()<maxChars;i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null)semanticBlob(child,depth+1,maxDepth,maxChars,out);}return out;
    }
    private static void appendSemantic(StringBuilder out,CharSequence value,int maxChars){if(value==null)return;String x=value.toString().replace('\n',' ').replace('\r',' ').trim();if(x.isEmpty())return;if(out.indexOf(x)>=0)return;if(out.length()>0)out.append(", ");int room=maxChars-out.length();if(room>0)out.append(x,0,Math.min(room,x.length()));}

    private static boolean containsSignature(List<VintedCard> cards, VintedCard candidate) {
        String sig = DealDatabase.signature(candidate);
        for (VintedCard c : cards) if (sig.equals(DealDatabase.signature(c))) return true;
        return false;
    }

    private GameRecord activeMarketScanGame(){try{SharedPreferences p=getSharedPreferences(PREF_VINTED_MARKET_SCAN,MODE_PRIVATE);long until=p.getLong("until",0);long id=p.getLong("game_id",0);if(id<=0||until<System.currentTimeMillis())return null;return marketStore==null?null:marketStore.gameStats(id);}catch(Throwable ignored){return null;}}

    private SharedPreferences diag() {
        return getSharedPreferences(PREFS_DIAG, MODE_PRIVATE);
    }

    private static <K> void trimOld(Map<K, Long> map, long now, long ttl, int hardMax) {
        if (map.size() <= hardMax) return;
        map.entrySet().removeIf(e -> now - e.getValue() > ttl);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String firstLine(String s){if(s==null)return"";int n=s.indexOf('\n');String x=n>=0?s.substring(0,n):s;return x.length()>240?x.substring(0,240):x;}

    public static String diagnostics(Context context) {
        DealDatabase db = new DealDatabase(context);
        BundleDatabase bundles = new BundleDatabase(context);
        long since = System.currentTimeMillis() - 24 * 60 * 60_000L;
        int hot = db.countDeals("hot");
        int good = db.countDeals("good");
        int all = db.countDeals(null);
        int today = db.countObservationsSince(since);
        int pending = db.countPendingObservationsSince(since);
        MarketStore marketDiag=new MarketStore(context,db);
        MarketStore.JobSummary queueSummary=marketDiag.jobSummary();
        long queueNow=System.currentTimeMillis();
        int queueVintedDue=marketDiag.runnableVintedDueCount(queueNow),queueBggDue=marketDiag.runnableBggDueCount(queueNow);
        int queueDueNow=queueVintedDue+queueBggDue;
        int queueVinted=marketDiag.vintedActiveCount();
        int queueVintedCore=marketDiag.coreVintedActiveCount(),queueVintedDeep=marketDiag.deepMetadataActiveCount(),queueBgg=marketDiag.bggActiveCount(),queueBggBlocked=marketDiag.bggUnrunnableCount(),queueBggMatchRequired=marketDiag.bggMatchRequiredCount(),queueBggMatchReview=marketDiag.bggMatchReviewCount();
        int queueHistorical=marketDiag.historicalActiveCount(),queueMissingVinted=marketDiag.missingVintedCoreCount(),queuePartialVinted=marketDiag.partialVintedMetadataCount();
        long queueNextDue=marketDiag.nextDueAt();
        boolean queueVintedPaused=marketDiag.isVintedPaused(),queueBggPaused=marketDiag.isBggPaused(),queueHistoricalPaused=marketDiag.isHistoricalPaused();
        long vintedLaneHeartbeat=marketDiag.laneHeartbeatAt("vinted"),bggLaneHeartbeat=marketDiag.laneHeartbeatAt("bgg");
        MarketStore.RuntimeStatus vintedLane=marketDiag.laneStatus("vinted"),bggLane=marketDiag.laneStatus("bgg");
        VintedPublicSession.GateState vintedGate=VintedPublicSession.gateState(context);
        MarketStore.RuntimeStatus a11yCross=marketDiag.diagnosticState("a11y_probe");
        long a11yCrossAgeMs=a11yCross.updatedAt<=0?-1L:Math.max(0L,System.currentTimeMillis()-a11yCross.updatedAt);
        String a11yCrossPayload=TextUtils.isEmpty(a11yCross.detail)?"":a11yCross.detail;
        MarketStore.RuntimeStatus priceRefresh=marketDiag.diagnosticState("verified_price_refresh");
        String priceRefreshSummary=priceRefresh.updatedAt<=0?"state=NOT_RUN":("ageMs="+Math.max(0L,System.currentTimeMillis()-priceRefresh.updatedAt)+", "+priceRefresh.detail);
        String bggIdentityTrustSummary=marketDiag.bggIdentityTrustSummary();
        String bggMatchBreakdownSummary=marketDiag.bggMatchRequiredBreakdown();
        long engineEpochForExit=db.engineEpochStart();
        String processCrashSummary=ProcessCrashJournal.fileSummary(context);
        String systemExitSummary=ProcessCrashJournal.systemExitSummary(context,engineEpochForExit);
        MarketStore.RuntimeStatus bggLocalMatch=marketDiag.diagnosticState("bgg_local_match");
        String bggLocalMatchSummary=bggLocalMatch.updatedAt<=0?"state=NOT_RUN":("ageMs="+Math.max(0L,System.currentTimeMillis()-bggLocalMatch.updatedAt)+", "+bggLocalMatch.detail);
        MarketStore.RuntimeStatus bggReviewWrite=marketDiag.diagnosticState("bgg_match_review_write");
        String bggReviewWriteSummary=bggReviewWrite.updatedAt<=0?"state=NOT_RUN":("ageMs="+Math.max(0L,System.currentTimeMillis()-bggReviewWrite.updatedAt)+", "+bggReviewWrite.detail);
        DealDatabase.ObservationSession engineRun=db.activeObservationSession();int engineWaitingRuns=db.waitingObservationSessionCount();
        long engineEpochStart=engineEpochForExit;
        String engineEpochSummary="build=engine-epoch-v1;start="+engineEpochStart+";vintedReview="+marketDiag.vintedReviewCount()+";bggReview="+marketDiag.bggMatchReviewCount();
        String engineRunSummary=engineRun==null?"state=IDLE;waitingRuns=0":("state=ACTIVE;start="+engineRun.startAt+";end="+engineRun.endAt+";observations="+engineRun.observations+";unique="+engineRun.uniqueListings+";games="+engineRun.validListings+";bgg="+engineRun.bggMatchedListings+";vinted="+engineRun.vintedLinkedListings+";ready="+engineRun.completeListings+";review="+engineRun.reviewListings+";analysisPending="+engineRun.analysisPendingListings+";waitingRuns="+engineWaitingRuns);
        DealDatabase.ObservationSession firstWaiting=null;
        if(engineRun!=null&&engineWaitingRuns>0){
            for(DealDatabase.ObservationSession candidate:db.recentObservationSessions(engineEpochStart,20)){
                if(candidate.startAt<=engineRun.startAt)continue;
                if(firstWaiting==null||candidate.startAt<firstWaiting.startAt)firstWaiting=candidate;
            }
        }
        String engineWaitingSummary=firstWaiting==null?"state=NONE":("state=WAITING;start="+firstWaiting.startAt+";end="+firstWaiting.endAt+";observations="+firstWaiting.observations+";unique="+firstWaiting.uniqueListings+";games="+firstWaiting.validListings+";bgg="+firstWaiting.bggMatchedListings+";vinted="+firstWaiting.vintedLinkedListings+";ready="+firstWaiting.completeListings+";review="+firstWaiting.reviewListings+";analysisPending="+firstWaiting.analysisPendingListings);
        db.close();
        int cachedSellerCatalogs=bundles.sellerCacheCount();int cachedSnapshots=bundles.snapshotCacheCount();int uniqueSellers=bundles.uniqueSellerCount();Map<String,Integer> bundleStates=bundles.statusCounts();long snapshotAnalyzed=bundles.counter("snapshotAnalyzed"),deepExecuted=bundles.counter("deepScanExecuted"),deepAvoided=bundles.counter("deepScanAvoided"),bundleCandidates=bundles.counter("bundleCandidates"),bundleReadyEvents=bundles.counter("bundleReady"),bundleErrors=bundles.counter("errors"),rateLimited=bundles.counter("rateLimited"),cacheHitSnapshot=bundles.counter("cacheHitSnapshot"),cacheHitCatalog=bundles.counter("cacheHitCatalog"),emptySnapshotProbes=bundles.counter("emptySnapshotProbes"),thinSnapshotProbes=bundles.counter("thinSnapshotProbes"),candidateVerifyRequests=bundles.counter("candidateVerifyRequests"),candidateVerifyRejected=bundles.counter("candidateVerifyRejected"),sellerDataProbes=bundles.counter("sellerDataProbes"),sellerDataEmpty=bundles.counter("sellerDataEmpty"),sellerDataCandidates=bundles.counter("sellerDataCandidates"),accessibilitySellerHints=bundles.counter("accessibilitySellerHints");int bundleReadyCurrent=bundleStates.containsKey("BUNDLE_READY")?bundleStates.get("BUNDLE_READY"):0;bundles.close();

        SharedPreferences p = context.getSharedPreferences(PREFS_DIAG, MODE_PRIVATE);
        return "LUDO SCOUT V5 — RADAR + BUNDLE\n" +
                "mode=observer-only (no visual overlays)\n" +
                "serviceConnected=" + p.getBoolean("serviceConnected", false) + "\n" +
                "engineReady=" + p.getBoolean("engineReady", false) + "\n" +
                "engineGames=" + p.getInt("engineGames", 0) + "\n" +
                "vintedEvents=" + p.getLong("vintedEvents", 0) + "\n" +
                "scans=" + p.getLong("scans", 0) + "\n" +
                "lastRoot=" + p.getString("lastRoot", "") + "\n" +
                "accessibilityWindowFallbacks=" + p.getLong("accessibilityWindowFallbacks",0) + "\n" +
                "lastUnparsedCardSample=" + p.getString("lastUnparsedCardSample","") + "\n" +
                "lastCardsParsed=" + p.getInt("lastCardsParsed", 0) + "\n" +
                "cardsParsedTotal=" + p.getLong("cardsParsedTotal", 0) + "\n" +
                "analysisBatches=" + p.getLong("analysisBatches", 0) + "\n" +
                "analysesStored=" + p.getLong("analysesStored", 0) + "\n" +
                "classifierBlocked=" + p.getLong("classifierBlocked", 0) + "\n" +
                "lastClassifierBlock=" + p.getString("lastClassifierBlock", "") + "\n" +
                "catalogStored="+p.getInt("catalogStored",-1)+"\n"+"catalogFiltered="+p.getInt("catalogFiltered",-1)+"\n"+"catalogPreset="+p.getString("catalogPreset","")+"\n"+"catalogFeed=" + all + "\n" +
                "hot=" + hot + "\n" +
                "good=" + good + "\n" +
                "observationsLast24h=" + today + "\n" +
                "pendingSightingsLast24h=" + pending + "\n" +
                "lastError=" + p.getString("lastError", "") + "\n" +
                "lastCardSample=" + p.getString("lastCardSample", "") + "\n" +
                "linksResolved=" + p.getLong("linksResolved", 0) + "\n" +
                "linkResolveMisses=" + p.getLong("linkResolveMisses", 0) + "\n" +
                "lastLinkResolution=" + p.getString("lastLinkResolution", "") + "\n" +
                "lastLinkResolveMiss=" + p.getString("lastLinkResolveMiss", "") + "\n" +
                "linkMetadataRefreshed=" + p.getLong("linkMetadataRefreshed", 0) + "\n" +
                "lastLinkMetadata=" + p.getString("lastLinkMetadata", "") + "\n" +
                "lastLinkMetadataMiss=" + p.getString("lastLinkMetadataMiss", "") + "\n" +
                "linkApiCode=" + p.getInt("linkApiCode", -1) + "\n" +
                "linkBroadApiCode=" + p.getInt("linkBroadApiCode", -1) + "\n" +
                "linkHtmlCode=" + p.getInt("linkHtmlCode", -1) + "\n" +
                "linkVerifyCode=" + p.getInt("linkVerifyCode", -1) + "\n" +
                "linkPublicVerifyCode=" + p.getInt("linkPublicVerifyCode", -1) + "\n" +
                "linkVerifyMode=" + p.getString("linkVerifyMode", "") + "\n" +
                "linkStrategy=" + p.getString("linkStrategy", "") + "\n" +
                "linkHtmlLocalCandidates=" + p.getInt("linkHtmlLocalCandidates", 0) + "\n" +
                "linkStructuredCandidates=" + p.getInt("linkStructuredCandidates", 0) + "\n" +
                "vintedCoreFastResolved=" + p.getLong("vintedCoreFastResolved", 0) + "\n" +
                "vintedThumbCaptured=" + p.getLong("vintedThumbCaptured", 0) + "\n" +
                "linkPhotoCompared=" + p.getInt("linkPhotoCompared", 0) + "\n" +
                "linkBestPhotoSimilarity=" + p.getInt("linkBestPhotoSimilarity", -1) + "\n" +
                "lastCardSeller=" + p.getString("lastCardSeller", "") + "\n" +
                "linkCandidateCount=" + p.getInt("linkCandidateCount", 0) + "\n" +
                "linkBestScore=" + p.getInt("linkBestScore", 0) + "\n" +
                "linkSecondScore=" + p.getInt("linkSecondScore", 0) + "\n" +
                "linkBestTitle=" + p.getString("linkBestTitle", "") + "\n" +
                "vintedHtmlLastCode=" + p.getInt("vintedHtmlLastCode", -1) + "\n" +
                "vintedSessionMode=" + p.getString("vintedSessionMode", "") + "\n" +
                "bggImageLastCode=" + p.getInt("bggImageLastCode", -1) + "\n" +
                "bggImageBytes=" + p.getLong("bggImageBytes", 0) + "\n" +
                "bggImageLastError=" + p.getString("bggImageLastError", "") + "\n" +
                "bggLastHttpCode=" + p.getInt("bggLastHttpCode", 0) + "\n" +
                "bggEnriched=" + p.getLong("bggEnriched", 0) + "\n" +
                "bggLastId=" + p.getString("bggLastId", "") + "\n" +
                "bggLastError=" + p.getString("bggLastError", "") + "\n" +
                "linkResolver=priority-public-pages; private API disabled\n" +
                "bggTokenConfigured=" + (!android.text.TextUtils.isEmpty(BuildConfig.BGG_TOKEN) && !"PASTE_YOUR_BGG_TOKEN_HERE".equals(BuildConfig.BGG_TOKEN)) + "\n" +
                "bundleStrategy=" + p.getString("bundleStrategy", "") + "\n" +
                "bundleSellerHttp=" + p.getInt("bundleSellerHttp", -1) + "\n" +
                "bundleSellerPublicHttp=" + p.getInt("bundleSellerPublicHttp", -1) + "\n" +
                "bundleCatalogItems=" + p.getInt("bundleCatalogItems", 0) + "\n" +
                "bundleSellerCatalogCache=" + cachedSellerCatalogs + "\n" +
                "bundleQueueMode=progressive-opportunistic-serial\n" +
                "bundlePipelineGeneration="+p.getInt("bundlePipelineGeneration",1)+"; resetRows="+p.getInt("bundlePipelineResetRows",0)+"\n"+
                "bundleUniqueSellers="+uniqueSellers+"\n"+
                "bundleLocalGraphSellers="+p.getInt("bundleLocalGraphSellers",0)+"\n"+
                "bundleLocalGraphSuggestions="+p.getInt("bundleLocalGraphSuggestions",0)+"\n"+
                "bundleAccessibilitySnapshot="+p.getInt("bundleAccessibilitySnapshot",0)+"\n"+
                "bundleAccessibilitySellerHints="+accessibilitySellerHints+"\n"+
                "bundleSnapshotCache="+cachedSnapshots+"\n"+
                "bundleSnapshotAnalyzed="+snapshotAnalyzed+"\n"+
                "bundleDeepExecuted="+deepExecuted+"\n"+
                "bundleDeepAvoided="+deepAvoided+"\n"+
                "bundleEmptySnapshotProbes="+emptySnapshotProbes+"\n"+
                "bundleThinSnapshotProbes="+thinSnapshotProbes+"\n"+
                "bundleCandidateVerifyRequests="+candidateVerifyRequests+"\n"+
                "bundleCandidateVerifyRejected="+candidateVerifyRejected+"\n"+
                "bundleSellerDataProbes="+sellerDataProbes+"\n"+
                "bundleSellerDataEmpty="+sellerDataEmpty+"\n"+
                "bundleSellerDataCandidates="+sellerDataCandidates+"\n"+
                "bundleCandidates="+bundleCandidates+"\n"+
                "bundleReady="+bundleReadyCurrent+"\n"+
                "bundleReadyEvents="+bundleReadyEvents+"\n"+
                "bundleErrors="+bundleErrors+"\n"+
                "bundleRateLimited="+rateLimited+"\n"+
                "bundleCacheHitSnapshot="+cacheHitSnapshot+"\n"+
                "bundleCacheHitCatalog="+cacheHitCatalog+"\n"+
                "bundleStates="+bundleStates+"\n"+
                "vintedPublicRequestsLocal="+p.getLong("vintedPublicRequests",0)+"\n"+
                "vintedPublicCacheHitsLocal="+p.getLong("vintedPublicCacheHits",0)+"\n"+
                "vintedRequestLedger={"+VintedPublicSession.requestLedgerSummary(context)+"}\n"+
                "engineEpoch={"+engineEpochSummary+"}\n"+
                "engineRun={"+engineRunSummary+"}\n"+
                "engineWaiting={"+engineWaitingSummary+"}\n"+
                "vintedBatchEngine={"+VintedBatchEngine.summary(context)+"}\n"+
                "bggVariantGuard={"+BggVariantReconciler.summary(context)+"}\n"+
                "bggIdentityTrust={"+bggIdentityTrustSummary+"}\n"+
                "bggMatchBreakdown={"+bggMatchBreakdownSummary+"}\n"+
                "bggHistoricalRevalidation={"+marketDiag.historicalBggRevalidationSummary()+"}\n"+
                "bggLocalMatch={"+bggLocalMatchSummary+"}\n"+
                "bggReviewWrite={"+bggReviewWriteSummary+"}\n"+
                "processCrashJournal={"+processCrashSummary+"}\n"+
                "systemExitHistory={"+systemExitSummary+"}\n"+
                "vintedPriceRefresh={"+priceRefreshSummary+"}\n"+
                "vintedCandidateSnapshotStore={"+VintedCandidateSnapshotStore.summary(context)+"}\n"+
                "vintedPublicHourlyBudget="+VintedPublicSession.hourlyBudget()+"\n"+
                "vintedGate={reason="+(TextUtils.isEmpty(vintedGate.reason)?"READY":vintedGate.reason)+", allowedAt="+vintedGate.allowedAt+", used="+vintedGate.used+"/"+VintedPublicSession.hourlyBudget()+", lastAt="+vintedGate.lastRequestAt+", circuitUntil="+vintedGate.circuitUntil+"}\n" +
                "bundleScans=" + p.getLong("bundleScans",0) + "\n" +
                "bundleLastCount=" + p.getInt("bundleLastCount",0) + "\n" +
                "bundleLast=" + p.getString("bundleLast","") + "\n" +
                "bundleLastResponse=" + p.getString("bundleLastResponse","") + "\n" +
                "bundleParser=" + p.getString("bundleParser","") + "\n" +
                "bundleProfileVerified="+p.getInt("bundleProfileVerified",0)+"\n"+
                "bundleProfileProvisional="+p.getInt("bundleProfileProvisional",0)+"\n"+
                "bundleVerifyLast="+p.getInt("bundleVerifyAcceptedLast",0)+" accepted / "+p.getInt("bundleVerifyRejectedLast",0)+" rejected / "+p.getInt("bundleVerifyRequestsLast",0)+" requests\n"+
                "publishedMetadataResolved="+p.getLong("publishedMetadataResolved",0)+"\n"+
                "lastPublishedLabel="+p.getString("lastPublishedLabel","")+"\n"+
                "a11yProbeCrossProcess={authoritative=true, ageMs="+a11yCrossAgeMs+", writes="+a11yCross.value+", payload="+a11yCrossPayload+"}\n"+
                "a11yIdentityProbe={build="+BuildConfig.VERSION_NAME+", probe=fast-explicit-only; historical1dUniqueCards="+p.getLong("a11yProbeUniqueCards",0)+", runs="+p.getLong("a11yProbeRuns",0)+", nodes="+p.getLong("a11yProbeNodes",0)+", ancestorNodes="+p.getLong("a11yProbeAncestorNodes",0)+", spannedTextNodes="+p.getLong("a11yProbeSpannedTextNodes",0)+", urlSpans="+p.getLong("a11yProbeUrlSpans",0)+", clickableSpans="+p.getLong("a11yProbeClickableSpans",0)+", actionLabels="+p.getLong("a11yProbeActionLabels",0)+", extraKeys="+p.getLong("a11yProbeExtraKeys",0)+", availableExtraKeys="+p.getLong("a11yProbeAvailableExtraKeys",0)+", extraRefresh="+p.getLong("a11yProbeExtraRefreshHits",0)+"/"+p.getLong("a11yProbeExtraRefreshRequests",0)+", ancestorExplicitHits="+p.getLong("a11yProbeAncestorExplicitHits",0)+"}\n"+
                "a11yIdentityResult={idsTotal="+p.getLong("vintedIdsCapturedFromAccessibility",0)+", idsFromUrlSpan="+p.getLong("a11yIdsFromUrlSpan",0)+", idsFromOtherExplicitField="+p.getLong("a11yIdsFromOtherExplicitField",0)+", lastSource="+p.getString("a11yIdentityLastSource","")+", lastProbeCaptureSource="+p.getString("a11yProbeLastCaptureSource","")+"}\n"+
                "a11yProbeLastUrlSpan="+p.getString("a11yProbeLastUrlSpan","")+"\n"+
                "a11yProbeLastAvailableExtra="+p.getString("a11yProbeLastAvailableExtra","")+"\n"+
                "a11yProbeLastExtraSample="+p.getString("a11yProbeLastExtraSample","")+"\n"+
                "a11yProbeLastRequestedExtra="+p.getString("a11yProbeLastRequestedExtra","")+"\n"+
                "a11yProbeLastAncestorExplicit="+p.getString("a11yProbeLastAncestorExplicit","")+"\n"+
                "vintedIdsCapturedFromAccessibility="+p.getLong("vintedIdsCapturedFromAccessibility",0)+" / cardsParsed="+p.getLong("cardsParsedTotal",0)+"\n"+
                "lastAccessibilityVintedId="+p.getString("lastAccessibilityVintedId","")+"\n"+
                "libraryLastError="+p.getString("libraryLastError","")+"\n"+
                "refreshBulkRequested="+p.getBoolean("manualBulkRequested",false)+"\n"+
                 "refreshBulkMode="+p.getBoolean("refreshBulkMode",false)+"; total="+p.getInt("refreshBulkTotal",0)+"; completed="+p.getInt("refreshBulkCompleted",0)+"; queued="+p.getInt("refreshBulkQueued",0)+"; current="+p.getString("refreshBulkCurrent","")+"\n"+
                "refreshPausedUntil="+p.getLong("refreshPausedUntil",0)+"; authoritative="+p.getLong("refreshVintedAuthoritativeUntil",0)+"; gapMs="+p.getLong("refreshVintedGapMs",0)+"; reason="+p.getString("refreshPauseReason","")+"\n"+
                "persistentJobs={PENDING="+queueSummary.pending+", PROCESSING="+queueSummary.processing+", FAILED_RETRYABLE="+queueSummary.retryable+", FAILED_PERMANENT="+queueSummary.permanent+"}\n"+
                "queueRunnable={vinted="+queueVintedDue+", bgg="+queueBggDue+", total="+queueDueNow+"}; nextDueAt="+queueNextDue+"; vintedActive="+queueVinted+"; vintedCore="+queueVintedCore+"; vintedDeep="+queueVintedDeep+"; bggActive="+queueBgg+"; bggUnrunnable="+queueBggBlocked+"; bggMatchRequired="+queueBggMatchRequired+"; bggMatchReview="+queueBggMatchReview+"; historicalActive="+queueHistorical+"; vintedPaused="+queueVintedPaused+"; bggPaused="+queueBggPaused+"; historicalPaused="+queueHistoricalPaused+"\n"+
                "vintedData={missingLink="+queueMissingVinted+", partialMetadata="+queuePartialVinted+"}\n"+
                "bggBatch={lastSize="+p.getInt("bggBatchLastSize",0)+", requests="+p.getLong("bggBatchRequests",0)+", items="+p.getLong("bggBatchItems",0)+"}\n"+
                "queueLanes={vinted="+vintedLane.state+", detail="+firstLine(vintedLane.detail)+", heartbeatAgeMs="+(vintedLaneHeartbeat<=0?-1:Math.max(0,queueNow-vintedLaneHeartbeat))+", bgg="+bggLane.state+", detail="+firstLine(bggLane.detail)+", heartbeatAgeMs="+(bggLaneHeartbeat<=0?-1:Math.max(0,queueNow-bggLaneHeartbeat))+"}\n"+
                "uiLastRenderMs="+p.getLong("uiLastRenderMs",0)+"; tab="+p.getString("uiLastRenderTab","")+"\n"+
                "uiLastAction="+p.getString("uiLastAction","")+"; at="+p.getLong("uiLastActionAt",0)+"\n"+
                "lastCrashAt="+p.getLong("lastCrashAt",0)+"; tab="+p.getString("lastCrashTab","")+"; wizard="+p.getString("lastCrashWizard","")+"\n"+
                "lastCrash="+firstLine(p.getString("lastCrash",""))+"\n"+
                "galleryPage="+p.getInt("galleryPage",0)+"/"+p.getInt("galleryPages",0)+"; HTTP="+p.getInt("galleryLastHttp",0)+"; host="+p.getString("galleryLastHost","")+"\n"+
                "privacy=Accessibility reads Vinted UI + seller rails; opportunistic public item pages only; private seller APIs disabled; no cookies/OAuth/bypass; no visual overlays\n";
    }

    @Override public void onInterrupt() { }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if(retryRegistered)try{unregisterReceiver(retryReceiver);}catch(Exception ignored){}
        diag().edit().putBoolean("serviceConnected", false).apply();
        if (engine != null) engine.destroy();
        if (database != null) database.close();
        if (bundleScanner != null) bundleScanner.close();
        if (bundleDatabase != null) bundleDatabase.close();
        maintenanceIo.shutdownNow();
        super.onDestroy();
    }
}
