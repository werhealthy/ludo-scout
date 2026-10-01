package it.vintedaffari.app;

import android.app.*;
import android.animation.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.*;

/** Ludo Scout v5 — branded dashboard/catalog/library shell over the stable V4 observer stack. */
public final class MainActivity extends Activity {
    private static final int SHEET_ID=0x00530053;
    private static final int BG=Color.rgb(10,11,20),SURFACE=Color.rgb(22,26,47),SURFACE2=Color.rgb(29,32,57),TEXT=Color.WHITE,MUTED=Color.rgb(142,148,177),LIME=Color.rgb(164,151,255),PINK=Color.rgb(205,104,225),CYAN=Color.rgb(164,151,255),TEAL=Color.rgb(54,201,176),PURPLE=Color.rgb(151,113,224),ORANGE=Color.rgb(255,96,26),YELLOW=Color.rgb(255,214,49),RED=Color.rgb(231,79,79),OUTLINE=Color.rgb(43,51,100),VINTED_BG=Color.rgb(8,128,139),BGG_BG=Color.rgb(75,70,105);
    private static final int DISCOVER_BG=Color.rgb(10,11,20),DISCOVER_SURFACE=Color.rgb(22,26,47),DISCOVER_TEXT=Color.WHITE,DISCOVER_MUTED=Color.rgb(142,148,177),DISCOVER_YELLOW=Color.rgb(240,180,0),DISCOVER_PINK=Color.rgb(205,104,225),DISCOVER_ORANGE=Color.rgb(194,130,95),DISCOVER_MINT=Color.rgb(0,208,151),DISCOVER_LAVENDER=Color.rgb(124,113,255),DISCOVER_BOX_SIDE=Color.rgb(128,80,59),DISCOVER_NAV_ACTIVE=Color.rgb(25,25,25),DISCOVER_OUTLINE=Color.rgb(43,51,100);
    private static final Pattern VINTED_URL=Pattern.compile("https?://(?:www\\.)?vinted\\.[^\\s/]+/items/[^\\s]+",Pattern.CASE_INSENSITIVE);
    private static final String PREF_MANUAL_VINTED_SHARE="manual_vinted_share_v1";
    private static final long MANUAL_VINTED_SHARE_TTL=2*60*60_000L;
    private static final long MANUAL_VINTED_RECOVERY_TTL=20*60_000L;
    private static final String PREF_VINTED_MARKET_SCAN="vinted_market_scan_v1";
    private static final long VINTED_MARKET_SCAN_TTL=20*60_000L;
    private static final String PREF_UI_SESSION="ui_session_v51125";
    private static final long UI_SESSION_TTL=2*60*60_000L;
    private DealDatabase db;private final Map<Integer,Bitmap> discoverCategoryIcons=new HashMap<>();private Typeface discoverTypefaceBase;private final Map<Integer,Typeface> discoverTypefaceWeights=new HashMap<>();private MarketStore marketStore;private BundleDatabase bundleDb;private LibraryDatabase libraryDb;private AccessoryDatabase accessoryDb;private HuntDatabase huntDb;private BggSearchClient bggSearch;
    private String pendingLibraryQuery="";private List<BggSearchClient.Game> pendingLibraryResults=new ArrayList<>();private boolean pendingLibraryRunning=false;private int catalogVisible=24,databaseVisible=24,databaseJobsVisible=6,databaseCategory=-1;private boolean databaseLoadingMore=false,catalogLoadingMore=false;private String databaseScope="verified",databaseSort="alpha";private boolean databaseActiveOnly=false;private Double databaseMinRating=null;private Integer databaseMaxPrice=null;private LinearLayout databaseResultsHost,catalogResultsHost;private List<DealRecord> catalogResultSnapshot=new ArrayList<>();private final Handler uiUpdates=new Handler(Looper.getMainLooper());private final Runnable refreshData=()->{if(!isDestroyed())render();};private final Runnable deferredRender=()->{if(!isDestroyed())render();};private boolean renderInProgress=false,openingPreset=false,showActivityHistory=false,showBundleDebug=false;private String engineSection="overview";private long engineDayStart=0L,engineDayEnd=0L,engineRunStart=0L,engineRunEnd=0L;private String engineRunFilter="all";private Set<String> catalogBatchSignatures=null;private String catalogBatchLabel="";private Dialog libraryWizardDialog;private String libraryWizardStep="",libraryWizardSource="",libraryWizardQuery="",wizardPaidText="",wizardShippingText="",wizardFeeText="",wizardDateText="",wizardSessionId="";private BggSearchClient.Game libraryWizardGame;private boolean restoringLibraryWizard=false;private int restoredScrollY=0;private LinearLayout body,nav;private ScrollView scroll;private PullRefreshScrollView refreshHost;private View activityButton,companionFab;private TextView globalProgressLabel;private String tab="discover",returnTab="discover",sort="relevance",query="",filterMode="all",catalogPreset="all",bundleSort="deal",databaseQuery="",databaseDetailReturnTab="",libraryScope="owned",libraryQuery="",companionSection="for_you";private long selectedGameId=0L;private boolean receiverRegistered,gateVisible;private volatile boolean marketReferenceRefreshInFlight;private long lastMarketReferenceRefreshAt=0L;private final ArrayDeque<String> tabHistory=new ArrayDeque<>();private final Map<String,Integer> tabScrollPositions=new HashMap<>();private String activeDealSignature="";private long activeVintedResolutionListingId=0L;private final ArrayList<Dialog> marketDetailDialogs=new ArrayList<>();private Dialog activeDetailDialog,activeResolutionDialog,activeGameOverlay;private boolean suppressDetailDismissState=false,suppressResolutionDismissState=false;private final Map<String,Integer> libraryMarketCache=new HashMap<>();private final Map<String,Double> libraryScoreCache=new HashMap<>();private Thread.UncaughtExceptionHandler previousCrashHandler,installedCrashHandler;private long lastQueueUiRenderAt=0L,activityWakeRequestedAt=0L;private int engineRunPage=0;private volatile boolean operationReconcileInFlight=false,operationReconcilePending=false;private volatile long lastOperationReconcileAt=0L;private boolean engineUiResumed;private final Runnable activityStatusPulse=()->{if("activity".equals(tab)&&"overview".equals(engineSection)&&engineUiResumed&&!isDestroyed())requestEngineOverviewSnapshot();};
    private final EngineMotionState intakeMotion=new EngineMotionState();private long engineEnteredAt;private final EngineMotionState engineMotion=new EngineMotionState();private int enginePhase=-1;private List<DealDatabase.PipelineItem> enginePhaseItems;private Map<Long,GameRecord> enginePhaseGames=new HashMap<>();private Map<String,DealRecord> enginePhaseDeals=new HashMap<>();private int phaseRequestId;private int enginePhaseVisible=24;

    private static final class EngineOverviewSnapshot {
        final long loadedAt; final DealDatabase.ObservationSession run; final int waitingRuns,recoveryCount;
        final List<DealDatabase.ObservationDay> days;
        DealDatabase.ObservationSession pipelineRun;int[] phases=new int[5];int activeMask,intakeCount;boolean bggPaused,vintedPaused;long vintedWaitUntil;List<DealDatabase.ObservationSession> recentRuns=Collections.emptyList();
        final List<DealDatabase.ObservationSession> unfinishedRuns;
        EngineOverviewSnapshot(long at,DealDatabase.ObservationSession r,int w,int recovery,List<DealDatabase.ObservationDay> d,List<DealDatabase.ObservationSession> unfinished){
            loadedAt=at;run=r;waitingRuns=w;recoveryCount=recovery;days=d==null?Collections.emptyList():d;unfinishedRuns=unfinished==null?Collections.emptyList():unfinished;
        }
    }
    private volatile EngineOverviewSnapshot engineOverviewSnapshot;
    private final AtomicBoolean engineOverviewLoading=new AtomicBoolean(false);
    private volatile long engineOverviewRetryAt=0L;
    private volatile String engineOverviewLoadError="";
    private static final class EngineDaySession {final DealDatabase.ObservationSession session;final boolean waiting,deferred,active;EngineDaySession(DealDatabase.ObservationSession s,boolean w,boolean d,boolean a){session=s;waiting=w;deferred=d;active=a;}}
    private static final class EngineDaySnapshot {final long startAt,endAt,loadedAt;final List<EngineDaySession> sessions;EngineDaySnapshot(long a,long b,List<EngineDaySession> s){startAt=a;endAt=b;loadedAt=System.currentTimeMillis();sessions=s==null?Collections.emptyList():s;}}
    private String lastHomeDismissKey="",lastHomeDismissTitle="";private int lastHomeDismissValue;private Map<String,Double> homeScores=Collections.emptyMap();
    private static final class DiscoverBundleSnapshot {final long loadedAt;final DealRecord source;final List<DealRecord> games;DiscoverBundleSnapshot(DealRecord source,List<DealRecord> games){this.source=source;this.games=games;loadedAt=System.currentTimeMillis();}}
    private volatile DiscoverBundleSnapshot discoverBundleSnapshot;private final AtomicBoolean discoverBundleLoading=new AtomicBoolean();
    private static final class EngineRunSnapshot {final long startAt,endAt,loadedAt;final String filter;final DealDatabase.ObservationSession run;final List<DealDatabase.EngineRunItem> items;EngineRunSnapshot(long a,long b,String f,DealDatabase.ObservationSession r,List<DealDatabase.EngineRunItem> i){startAt=a;endAt=b;loadedAt=System.currentTimeMillis();filter=f;run=r;items=i==null?Collections.emptyList():i;}}
    private volatile List<DealDatabase.ObservationDay> engineHistorySnapshot;
    private volatile EngineDaySnapshot engineDaySnapshot;
    private volatile EngineRunSnapshot engineRunSnapshot;
    private final AtomicBoolean engineHistoryLoading=new AtomicBoolean(false),engineDayLoading=new AtomicBoolean(false),engineRunLoading=new AtomicBoolean(false);
    private volatile long engineHistoryRetryAt=0L,engineHistoryLoadedAt=0L,engineDayRetryAt=0L,engineRunRetryAt=0L,engineRunRequestedStart=0L,engineRunRequestedEnd=0L;
    private volatile String engineRunRequestedFilter="";
    private static final class ActivityIndicatorSnapshot {
        final boolean active,onlyPaused; final int factChecks; final long loadedAt;
        ActivityIndicatorSnapshot(boolean a,boolean p,int f,long at){active=a;onlyPaused=p;factChecks=f;loadedAt=at;}
    }
    private volatile ActivityIndicatorSnapshot activityIndicatorSnapshot;
    private final AtomicBoolean activityIndicatorLoading=new AtomicBoolean(false);
    private static final class PhotoMatch {BggSearchClient.Game game;double visual,text,score;PhotoMatch(BggSearchClient.Game g,double v,double t,double s){game=g;visual=v;text=t;score=s;}}
    private final Runnable activitySnapshotRetry=()->{if(!isDestroyed()&&"activity".equals(tab)&&"overview".equals(engineSection))scheduleRender(0);};
    private static final class LibrarySearchJob {String id,label;Uri uri;boolean running=true;String error="";final List<BggSearchClient.Game> results=new ArrayList<>();LibrarySearchJob(String i,String l,Uri u){id=i;label=l;uri=u;}}
    private static final List<LibrarySearchJob> librarySearchJobs=Collections.synchronizedList(new ArrayList<>());private String activeLibrarySearchJobId="";private final List<BggSearchClient.Game> wizardBundleGames=new ArrayList<>();private final Set<String> libraryBackfillInFlight=Collections.synchronizedSet(new HashSet<>());private final Set<String> libraryBackfillAttempted=Collections.synchronizedSet(new HashSet<>());
    private static final ExecutorService LIBRARY_SEARCH_NET=Executors.newFixedThreadPool(2);
    private static final class BundleGroup {String sellerId;final List<DealRecord> deals=new ArrayList<>();}
    private static final class BundlePlan {int askSubtotal,offerSubtotal,feeCents,shippingCents,totalCents,benchmarkCents;boolean offerEstimated,shippingEstimated;Integer savingPct,savingCents;}
    private static final class FilterDraft {Double rating;Integer discount,maxPrice;String language,link,type,dependence="all";int category=-1;boolean shipping,bundle,verify;FilterDraft(Double r,Integer d,String l,String k,String t,Integer p,boolean s,boolean b,boolean v){rating=r;discount=d;language=l;link=k;type=t;maxPrice=p;shipping=s;bundle=b;verify=v;}int extras(){return(shipping?1:0)+(bundle?1:0)+(verify?1:0);}void reset(){rating=null;discount=null;maxPrice=null;language="all";link="all";type="all";dependence="all";category=-1;shipping=bundle=verify=false;}}
    private static final class GameFilterDraft {int category=-1;String scope;Double rating;boolean active;Integer maxPrice;GameFilterDraft(String s,Double r,boolean a,Integer p){scope=s;rating=r;active=a;maxPrice=p;}void reset(){category=-1;scope="verified";rating=null;active=false;maxPrice=null;}}
    private final Set<String> detailRefreshes=Collections.synchronizedSet(new HashSet<>());
    private final Set<String> photoRefreshes=Collections.synchronizedSet(new HashSet<>());
    private final Map<Long,Integer> jobProgressMemory=new HashMap<>();
    private final LocalIntelligenceBackend intelligence=new LocalIntelligenceBackend.Rules();
    private final ExecutorService net=Executors.newFixedThreadPool(2);private final ExecutorService maintenanceIo=Executors.newSingleThreadExecutor();private final ExecutorService uiDataIo=Executors.newSingleThreadExecutor();private final ExecutorService engineUiIo=Executors.newSingleThreadExecutor();private final ExecutorService manualLinkIo=Executors.newSingleThreadExecutor();private static final ExecutorService diagnosticIo=Executors.newSingleThreadExecutor();private final ExecutorService galleryNet=Executors.newSingleThreadExecutor();private final android.util.LruCache<String,Bitmap> imageCache=new android.util.LruCache<String,Bitmap>(4*1024*1024){protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
    private final BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
        if(!"activity".equals(tab))updateActivityIndicator();
        long now=System.currentTimeMillis();
        if(OperationCenter.CHANGED.equals(i.getAction())){
            // Queue updates can be very frequent. Keep the overview alive, but do not rebuild a
            // thumbnail-heavy run inspector every couple of seconds.
            long minUiGap="run".equals(engineSection)?12_000L:6_000L;
            if("activity".equals(tab)&&now-lastQueueUiRenderAt>=minUiGap){if("overview".equals(engineSection))requestEngineOverviewSnapshot();lastQueueUiRenderAt=now;uiUpdates.removeCallbacks(refreshData);uiUpdates.postDelayed(refreshData,220);}
            return;
        }
        reconcileResolvedOperationErrorsAsync();
        long minUiGap="run".equals(engineSection)?12_000L:6_000L;
        if("activity".equals(tab)&&now-lastQueueUiRenderAt>=minUiGap){if("overview".equals(engineSection))requestEngineOverviewSnapshot();lastQueueUiRenderAt=now;uiUpdates.removeCallbacks(refreshData);uiUpdates.postDelayed(refreshData,280);}
        // Database ordering/content does not need to repaint for each background job completion.
    }};

    @Override protected void onCreate(Bundle b){super.onCreate(b);installCrashJournal();restoreUiState(b);if(b==null){restoreTransientUiSession();if(Intent.ACTION_MAIN.equals(getIntent().getAction())&&!getIntent().getBooleanExtra("open_engine",false)&&!getIntent().getBooleanExtra("open_engine_review",false)&&!getIntent().getBooleanExtra("open_hunts",false)&&TextUtils.isEmpty(libraryWizardStep)){tab="discover";selectedGameId=0;activeDealSignature="";activeVintedResolutionListingId=0;databaseDetailReturnTab="";restoredScrollY=0;}}db=new DealDatabase(this);marketStore=new MarketStore(this,db);bundleDb=new BundleDatabase(this);libraryDb=new LibraryDatabase(this);accessoryDb=new AccessoryDatabase(this);huntDb=new HuntDatabase(this);bggSearch=new BggSearchClient(this);upgradeOperationLedger();buildShell();startPostCreateMaintenance();refreshMarketReferencesIfStale();uiUpdates.postDelayed(this::requestNotificationPermission,1400);if(getIntent().getBooleanExtra("open_engine_review",false)){engineSection="review";tab="activity";}else if(getIntent().getBooleanExtra("open_engine",false)){tab="activity";}else if(getIntent().getBooleanExtra("open_hunts",false)){tab="companion";}if(!TextUtils.isEmpty(libraryWizardStep))uiUpdates.postDelayed(this::restoreLibraryWizard,260);if(restoredScrollY>0)uiUpdates.postDelayed(()->scroll.scrollTo(0,restoredScrollY),180);boolean incomingShare=Intent.ACTION_SEND.equals(getIntent().getAction());uiUpdates.postDelayed(this::restoreTransientRoute,incomingShare?120:220);uiUpdates.postDelayed(()->handleExternalIntent(getIntent()),incomingShare?420:120);}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);if(i!=null&&i.getBooleanExtra("open_engine_review",false)){engineSection="review";navigate("activity");}else if(i!=null&&i.getBooleanExtra("open_engine",false))navigate("activity");else if(i!=null&&i.getBooleanExtra("open_hunts",false))navigate("companion");uiUpdates.post(()->handleExternalIntent(i));}
    @Override public void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);if(body!=null)render();}
    /** SQLite reconciliation can contend with :radar. Keep it off the UI thread so a queue pulse
     * cannot turn app launch/navigation into an input-dispatch ANR. */
    private void startPostCreateMaintenance(){
        // The foreground queue process owns reconciliation and maintenance writes. Running the
        // same sweeps from :ui competed with :radar/:queue for the single SQLite writer.
        QueueKeepAliveService.ensureRunning(this);
    }

    @Override protected void onResume(){super.onResume();petResumed=true;petLoadedAt=0;if(petView!=null)petView.setResumed(activePetPanel==null);engineUiResumed=true;if("activity".equals(tab)){engineEnteredAt=System.currentTimeMillis();requestEngineOverviewSnapshot();}if(marketStore!=null)maintenanceIo.execute(()->{try{long now=System.currentTimeMillis();MarketStore.ManualVintedRecovery opened=marketStore.activeOpenedVintedTarget(now);if(opened!=null&&opened.active(now)&&opened.listingId>0)marketStore.enqueueOpenedListingVerification(opened.listingId);marketStore.clearManualVintedRecovery(0L);marketStore.clearOpenedVintedTarget(0L);}catch(Throwable ignored){}});if(!receiverRegistered){IntentFilter f=new IntentFilter("it.vintedaffari.app.DEALS_UPDATED");f.addAction(OperationCenter.CHANGED);if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(receiver,f);receiverRegistered=true;}if((activeDetailDialog!=null&&activeDetailDialog.isShowing())||(activeResolutionDialog!=null&&activeResolutionDialog.isShowing()))updateActivityIndicator();else scheduleRender(0);}
    @Override protected void onPause(){petResumed=false;if(petView!=null)petView.setResumed(false);engineUiResumed=false;uiUpdates.removeCallbacks(activityStatusPulse);persistTransientUiSession();super.onPause();}
    @Override public void onTrimMemory(int level){
        super.onTrimMemory(level);
        if(level>=TRIM_MEMORY_RUNNING_LOW){
            imageCache.evictAll();
            libraryMarketCache.clear();
            libraryScoreCache.clear();
        }
        if(level>=TRIM_MEMORY_BACKGROUND){
            cancelImageRequests(body);
            pendingLibraryResults.clear();
        }
    }

    @Override protected void onDestroy(){phaseRequestId++;engineUiResumed=false;uiUpdates.removeCallbacksAndMessages(null);if(receiverRegistered)try{unregisterReceiver(receiver);}catch(Exception ignored){}if(Thread.getDefaultUncaughtExceptionHandler()==installedCrashHandler)Thread.setDefaultUncaughtExceptionHandler(previousCrashHandler);if(db!=null)db.close();if(bundleDb!=null)bundleDb.close();if(libraryDb!=null)libraryDb.close();if(accessoryDb!=null)accessoryDb.close();if(huntDb!=null)huntDb.close();if(bggSearch!=null)bggSearch.shutdown();net.shutdownNow();maintenanceIo.shutdownNow();uiDataIo.shutdownNow();engineUiIo.shutdownNow();manualLinkIo.shutdownNow();galleryNet.shutdownNow();imageCache.evictAll();super.onDestroy();}

private void buildShell(){
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(BG);
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);root.addView(shell,new FrameLayout.LayoutParams(-1,-1));
        refreshHost=new PullRefreshScrollView(this);scroll=refreshHost.scroll();refreshHost.setOnRefresh(this::refreshCurrent);scroll.setOnScrollChangeListener((v,sx,sy,ox,oy)->{View content=scroll.getChildAt(0);if(content==null)return;int remaining=content.getHeight()-(scroll.getHeight()+sy);if(remaining>=dp(520))return;if("database".equals(tab)&&selectedGameId==0&&databaseResultsHost!=null&&!databaseLoadingMore)loadMoreDatabase();else if("catalog".equals(tab)&&catalogResultsHost!=null&&!catalogLoadingMore)loadMoreCatalog();});
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setClipToPadding(false);body.setPadding(dp(18),dp(4),dp(18),dp(40));
        refreshHost.setContent(body);shell.addView(refreshHost,new LinearLayout.LayoutParams(-1,0,1));
        nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setPadding(dp(8),dp(7),dp(8),dp(7));nav.setBackgroundColor(SURFACE);nav.setElevation(dp(10));
        shell.addView(nav,new LinearLayout.LayoutParams(-1,dp(78)));
        companionFab=makeCompanionFab();FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(dp(70),dp(70),Gravity.END|Gravity.BOTTOM);fp.rightMargin=dp(18);fp.bottomMargin=dp(94);root.addView(companionFab,fp);
        activityButton=makeActivityButton();globalProgressLabel=text("",1,MUTED,Typeface.NORMAL);globalProgressLabel.setVisibility(View.GONE);FrameLayout.LayoutParams abp=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.END|Gravity.TOP);abp.rightMargin=dp(18);abp.topMargin=dp(10);root.addView(activityButton,abp);
        root.setOnApplyWindowInsetsListener((v,in)->{int top,bottom;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets x=in.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());top=x.top;bottom=x.bottom;}else{top=in.getSystemWindowInsetTop();bottom=in.getSystemWindowInsetBottom();}Rect safe=contentSafeInsets(in);shell.setPadding(safe.left,safe.top,safe.right,safe.bottom);FrameLayout.LayoutParams q=(FrameLayout.LayoutParams)companionFab.getLayoutParams();q.bottomMargin=dp(94)+bottom;companionFab.setLayoutParams(q);FrameLayout.LayoutParams a=(FrameLayout.LayoutParams)activityButton.getLayoutParams();a.topMargin=top+dp(10);activityButton.setLayoutParams(a);return in;});
        setContentView(root);root.requestApplyInsets();scheduleRender(0);
    }

    /** System bars/cutout are window offsets, separate from page spacing. */
    private Rect contentSafeInsets(WindowInsets in){
        if(Build.VERSION.SDK_INT>=30){android.graphics.Insets safe=in.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());return new Rect(safe.left,safe.top,safe.right,safe.bottom);}
        Rect safe=new Rect(in.getSystemWindowInsetLeft(),in.getSystemWindowInsetTop(),in.getSystemWindowInsetRight(),in.getSystemWindowInsetBottom());android.view.DisplayCutout cutout=in.getDisplayCutout();if(cutout!=null){safe.left=Math.max(safe.left,cutout.getSafeInsetLeft());safe.top=Math.max(safe.top,cutout.getSafeInsetTop());safe.right=Math.max(safe.right,cutout.getSafeInsetRight());safe.bottom=Math.max(safe.bottom,cutout.getSafeInsetBottom());}return safe;
    }

    private View makeActivityButton(){
        FrameLayout f=new FrameLayout(this);f.setBackground(round(SURFACE2,999,0,0));f.setElevation(dp(8));
        TextView icon=appIcon(LudoIcons.HISTORY,18,CYAN);icon.setId(SHEET_ID+2);FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(dp(26),dp(26),Gravity.CENTER);f.addView(icon,ip);
        TextView status=new TextView(this);status.setId(SHEET_ID+3);status.setBackground(round(CYAN,999,0,0));FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(dp(10),dp(10),Gravity.START|Gravity.BOTTOM);sp.leftMargin=dp(3);sp.bottomMargin=dp(3);f.addView(status,sp);
        TextView count=text("",11,BG,Typeface.BOLD);count.setId(SHEET_ID+1);count.setGravity(Gravity.CENTER);count.setBackground(round(PINK,999,0,0));FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(dp(20),dp(20),Gravity.END|Gravity.TOP);cp.rightMargin=dp(-2);cp.topMargin=dp(-2);f.addView(count,cp);
        f.setContentDescription("Apri Motore e attività");f.setOnClickListener(x->navigate("activity"));updateActivityIndicator(f);return f;
    }
private void refreshCurrent(){if("activity".equals(tab)){kickActivityQueue();return;}if(!"discover".equals(tab)&&!"catalog".equals(tab)){refreshHost.finish();return;}String id="manual:"+System.currentTimeMillis();OperationCenter.queued(this,id,OperationCenter.LINK,"Aggiornamento "+("discover".equals(tab)?"Scopri":"Catalogo"));sendBroadcast(new Intent(OperationCenter.RETRY).setPackage(getPackageName()).putExtra("type","all"));new Handler(Looper.getMainLooper()).postDelayed(()->{OperationCenter.done(this,id,OperationCenter.LINK,"Vista aggiornata");render();refreshHost.finish();},700);}

private void kickActivityQueue(){activityWakeRequestedAt=System.currentTimeMillis();maintenanceIo.execute(()->{try{marketStore.reconcileQueue();QueueKeepAliveService.ensureRunning(getApplicationContext());QueueWorkScheduler.schedule(getApplicationContext());}catch(Throwable ignored){}});uiUpdates.postDelayed(()->{if("activity".equals(tab))scheduleRender(0);if(refreshHost!=null)refreshHost.finish();},900L);}

private void refreshMarketReferencesAsync(){if(marketReferenceRefreshInFlight||marketStore==null||bggSearch==null)return;marketReferenceRefreshInFlight=true;maintenanceIo.execute(()->{int changed=0;try{changed=marketStore.refreshLegacyDealBenchmarks(bggSearch);lastMarketReferenceRefreshAt=System.currentTimeMillis();getSharedPreferences("va_v3_diag",MODE_PRIVATE).edit().putLong("marketReferenceRefreshAt",lastMarketReferenceRefreshAt).apply();}catch(Throwable ignored){}finally{marketReferenceRefreshInFlight=false;final int c=changed;runOnUiThread(()->{if(!isDestroyed()&&c>0&&activeDetailDialog==null)scheduleRender(0);});}});}private void refreshMarketReferencesIfStale(){long now=System.currentTimeMillis();long persisted=getSharedPreferences("va_v3_diag",MODE_PRIVATE).getLong("marketReferenceRefreshAt",0L);lastMarketReferenceRefreshAt=Math.max(lastMarketReferenceRefreshAt,persisted);if(now-lastMarketReferenceRefreshAt<6L*60L*60_000L)return;refreshMarketReferencesAsync();}

private View makeCompanionFab(){
        FrameLayout f=new FrameLayout(this);f.setBackground(round(SURFACE,999,3,LIME));f.setElevation(dp(16));f.setClipToOutline(true);
        ImageView ludo=new ImageView(this);ludo.setImageResource(R.drawable.ludo_tracking);ludo.setScaleType(ImageView.ScaleType.FIT_CENTER);ludo.setPadding(dp(3),dp(3),dp(3),dp(3));f.addView(ludo,new FrameLayout.LayoutParams(-1,-1));
        TextView dot=new TextView(this);dot.setBackground(round(PINK,999,0,0));FrameLayout.LayoutParams dpv=new FrameLayout.LayoutParams(dp(12),dp(12),Gravity.END|Gravity.TOP);dpv.rightMargin=dp(4);dpv.topMargin=dp(4);f.addView(dot,dpv);
        f.setContentDescription("Apri Ludo, il tuo companion");f.setOnClickListener(v->navigate("companion"));return f;
    }

    private void render(){
        if(body==null||renderInProgress)return;if(activePetPanel!=null&&activePetPanel.isShowing()){refreshPetPanel();return;}renderInProgress=true;long started=System.currentTimeMillis();
        try{applyDiscoverChrome();if(!"activity".equals(tab))updateActivityIndicator();renderNav();cancelImageRequests(body);body.removeAllViews();body.setPadding(dp("discover".equals(tab)?20:18),dp("discover".equals(tab)?7:"activity".equals(tab)?16:4),dp("discover".equals(tab)?20:18),"discover".equals(tab)?dp(32):dp(24));if(activityButton!=null)activityButton.setVisibility(View.GONE);if(companionFab!=null)companionFab.setVisibility(View.GONE);if("discover".equals(tab))renderDiscover();else if("catalog".equals(tab))renderCatalog();else if("bundles".equals(tab))renderBundles();else if("database".equals(tab)){if(selectedGameId>0)renderDatabaseDetail();else renderDatabase();}else if("companion".equals(tab))renderCompanion();else if("activity".equals(tab))renderOperationsPage();else renderLibrary();}
        finally{renderInProgress=false;long elapsed=System.currentTimeMillis()-started;getSharedPreferences("va_v3_diag",MODE_PRIVATE).edit().putLong("uiLastRenderMs",elapsed).putString("uiLastRenderTab",tab).apply();}
    }
    private void cancelImageRequests(View view){if(view==null)return;if(view instanceof ImageView)view.setTag(new Object());if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)cancelImageRequests(group.getChildAt(i));}}
    private boolean isMarketTab(){return "catalog".equals(tab)||"database".equals(tab)||"bundles".equals(tab);}
    private void renderNav(){
        boolean home=true;nav.setBackgroundColor(home?Color.BLACK:SURFACE);nav.removeAllViews();
        nav.addView(navItem(LudoIcons.HOUSE,"Home","discover"),navLp());
        nav.addView(navItem(LudoIcons.SEARCH,"Catalogo","catalog"),navLp());
        nav.addView(navItem(LudoIcons.STAR,"Ludo","companion"),navLp());
        nav.addView(navItem(LudoIcons.BOOK_OPEN,"Libreria","library"),navLp());
    }
    private View navItem(String glyph,String label,String value){
        boolean on=value.equals(tab)||("catalog".equals(value)&&isMarketTab()),home=true;
        int active=home?Color.WHITE:LIME,inactive=home?Color.WHITE:MUTED;
        LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setGravity(Gravity.CENTER);x.setPadding(dp(3),dp(5),dp(3),dp(3));
        FrameLayout pill=new FrameLayout(this);pill.setBackground(round(on?(home?DISCOVER_NAV_ACTIVE:SURFACE2):Color.TRANSPARENT,14,on&&home?1:0,Color.rgb(43,43,43)));
        TextView icon=appIcon(glyph,22,on?active:inactive);pill.addView(icon,new FrameLayout.LayoutParams(-1,-1));x.addView(pill,new LinearLayout.LayoutParams(dp(70),dp(35)));
        TextView l=discoverText(label,10,on?active:inactive,Typeface.BOLD);l.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-1,-2);tlp.topMargin=dp(4);x.addView(l,tlp);x.setOnClickListener(v->navigate(value));return x;
    }

    private void addMarketHeader(String active){
        if("bundles".equals(active)){LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(0,dp(12),0,dp(18));TextView back=appIcon(LudoIcons.CHEVRON_LEFT,22,TEXT);back.setContentDescription("Torna alla schermata precedente");back.setOnClickListener(v->onBackPressed());head.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));head.addView(discoverTextWeight("Bundle",30,DISCOVER_TEXT,700),new LinearLayout.LayoutParams(0,-2,1));body.addView(head,new LinearLayout.LayoutParams(-1,-2));return;}
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setOrientation(getResources().getConfiguration().fontScale>1.15f?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);head.setPadding(0,dp(12),0,dp(16));TextView title=discoverTextWeight("Catalogo",32,DISCOVER_TEXT,700);head.addView(title,head.getOrientation()==LinearLayout.VERTICAL?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1));
        TextView bundles=discoverTextWeight("Bundle",13,DISCOVER_TEXT,600);bundles.setGravity(Gravity.CENTER);bundles.setCompoundDrawablesWithIntrinsicBounds(iconDrawable(LudoIcons.GIFT,DISCOVER_TEXT,16),null,null,null);bundles.setCompoundDrawablePadding(dp(7));bundles.setPadding(dp(12),0,dp(12),0);bundles.setBackground(round(SURFACE2,12,1,DISCOVER_OUTLINE));bundles.setContentDescription("Apri i bundle dello stesso venditore");bundles.setOnClickListener(v->navigate("bundles"));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,dp(48));if(head.getOrientation()==LinearLayout.VERTICAL)bp.topMargin=dp(8);else bp.leftMargin=dp(10);head.addView(bundles,bp);body.addView(head,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout tabs=new LinearLayout(this);tabs.setPadding(dp(4),dp(4),dp(4),dp(4));tabs.setBackground(round(DISCOVER_SURFACE,18,1,DISCOVER_OUTLINE));addMarketTab(tabs,"Annunci","catalog",active);addMarketTab(tabs,"Giochi","database",active);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,dp(56));tp.bottomMargin=dp(14);body.addView(tabs,tp);
    }
    private void addMarketTab(LinearLayout row,String label,String target,String active){boolean on=target.equals(active);TextView item=discoverTextWeight(label,14,on?DISCOVER_TEXT:DISCOVER_MUTED,500);item.setGravity(Gravity.CENTER);item.setSelected(on);item.setContentDescription(label+(on?", selezionato":""));item.setBackground(round(on?Color.rgb(61,42,90):Color.TRANSPARENT,14,0,0));item.setOnClickListener(v->openMarketTab(target));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);if(row.getChildCount()>0)lp.leftMargin=dp(4);row.addView(item,lp);}
    private void openMarketTab(String target){if(TextUtils.isEmpty(target)||target.equals(tab))return;if(scroll!=null)tabScrollPositions.put(tab,scroll.getScrollY());selectedGameId=0;databaseDetailReturnTab="";tab=target;final int savedY=tabScrollPositions.getOrDefault(target,0);persistTransientUiSession();renderNav();scheduleRender(0);uiUpdates.postDelayed(()->{if(scroll!=null&&target.equals(tab)&&selectedGameId==0)scroll.scrollTo(0,savedY);},35);}
    private void navigate(String value){
        if("database".equals(value)&&"database".equals(tab)&&selectedGameId>0){closeDatabaseGame();return;}
        if("database".equals(value)&&!"database".equals(tab)){selectedGameId=0;databaseDetailReturnTab="";}
        if("catalog".equals(value)&&!"catalog".equals(tab)&&!openingPreset)catalogVisible=Math.max(24,catalogVisible);
        if(TextUtils.isEmpty(value)||value.equals(tab))return;if("activity".equals(value)){engineSection="overview";engineEnteredAt=System.currentTimeMillis();requestEngineOverviewSnapshot();}recordAction("navigate:"+tab+"->"+value);if(scroll!=null)tabScrollPositions.put(tab,scroll.getScrollY());tabHistory.push(tab);returnTab=tab;tab=value;persistTransientUiSession();renderNav();updateActivityIndicator();final int savedY=tabScrollPositions.getOrDefault(value,0);scheduleRender(1);uiUpdates.postDelayed(()->{if(scroll!=null&&value.equals(tab)&&(!"database".equals(value)||selectedGameId==0))scroll.scrollTo(0,savedY);},40);
    }
    private void openDatabaseGame(long gameId,String sourceTab){if(gameId<=0)return;if(scroll!=null)tabScrollPositions.put(tab,scroll.getScrollY());selectedGameId=gameId;databaseDetailReturnTab=!TextUtils.isEmpty(sourceTab)&&!"database".equals(sourceTab)?sourceTab:"";if(!"database".equals(tab))tab="database";persistTransientUiSession();renderNav();scheduleRender(1);uiUpdates.postDelayed(()->{if(scroll!=null)scroll.scrollTo(0,0);},30);}
    private void closeDatabaseGame(){selectedGameId=0;if(!TextUtils.isEmpty(databaseDetailReturnTab)){String target=databaseDetailReturnTab;databaseDetailReturnTab="";tab=target;renderNav();}final String destination=tab;final int savedY=tabScrollPositions.getOrDefault(destination,0);persistTransientUiSession();scheduleRender(1);uiUpdates.postDelayed(()->{if(scroll!=null&&destination.equals(tab)&&selectedGameId==0)scroll.scrollTo(0,savedY);},35);}
    @Override public void onBackPressed(){if("activity".equals(tab)&&!"overview".equals(engineSection)){if("run".equals(engineSection)&&engineDayStart>0){engineSection="day";}else engineSection="overview";scheduleRender(0);if(scroll!=null)scroll.scrollTo(0,0);return;}if("database".equals(tab)&&selectedGameId>0){closeDatabaseGame();return;}if(scroll!=null)tabScrollPositions.put(tab,scroll.getScrollY());if(!tabHistory.isEmpty()){tab=tabHistory.pop();final String destination=tab;final int savedY=tabScrollPositions.getOrDefault(destination,0);renderNav();persistTransientUiSession();scheduleRender(1);uiUpdates.postDelayed(()->{if(scroll!=null&&destination.equals(tab))scroll.scrollTo(0,savedY);},35);return;}if(!"discover".equals(tab)){tab="discover";renderNav();scheduleRender(1);uiUpdates.postDelayed(()->{if(scroll!=null)scroll.scrollTo(0,tabScrollPositions.getOrDefault(tab,0));},35);return;}if(scroll!=null)scroll.smoothScrollTo(0,0);}
    private void scheduleRender(long delayMs){uiUpdates.removeCallbacks(deferredRender);uiUpdates.postDelayed(deferredRender,Math.max(0,delayMs));}
    private void updateActivityIndicator(){updateActivityIndicator(activityButton);}
    private void requestActivityIndicatorSnapshot(){
        if(marketStore==null||!activityIndicatorLoading.compareAndSet(false,true))return;
        final long started=System.currentTimeMillis();
        uiDataIo.execute(()->{
            ActivityIndicatorSnapshot ready=null;
            try{
                OperationCenter.Summary s=OperationCenter.userSummary(this);
                MarketStore.JobSummary q=marketStore.jobSummary();
                int durableActive=q==null?0:q.active();
                int factChecks=marketStore.vintedReviewCount()+marketStore.bggMatchReviewCount();
                boolean historyPaused=marketStore.isHistoricalPaused();
                boolean active=s.active()>0||durableActive>0;
                boolean onlyPaused=active&&historyPaused&&s.running==0&&(q==null||q.processing==0)&&marketStore.priorityActiveCount()==0;
                ready=new ActivityIndicatorSnapshot(active,onlyPaused,factChecks,System.currentTimeMillis());
            }catch(Throwable t){
                getSharedPreferences("va_v3_diag",MODE_PRIVATE).edit().putString("activityIndicatorState","ERROR;elapsedMs="+(System.currentTimeMillis()-started)+";error="+String.valueOf(t)).apply();
            }
            final ActivityIndicatorSnapshot result=ready;
            runOnUiThread(()->{
                activityIndicatorLoading.set(false);
                if(result!=null){
                    activityIndicatorSnapshot=result;
                    getSharedPreferences("va_v3_diag",MODE_PRIVATE).edit().putString("activityIndicatorState","READY;elapsedMs="+(System.currentTimeMillis()-started)).apply();
                }
                if(!isDestroyed()&&activityButton!=null)applyActivityIndicatorSnapshot(activityButton,result!=null?result:activityIndicatorSnapshot);
            });
        });
    }
    private void updateActivityIndicator(View target){
        if(target==null)return;
        ActivityIndicatorSnapshot snapshot=activityIndicatorSnapshot;
        applyActivityIndicatorSnapshot(target,snapshot);
        long age=snapshot==null?Long.MAX_VALUE:System.currentTimeMillis()-snapshot.loadedAt;
        if(age>5_000L)requestActivityIndicatorSnapshot();
    }
    private void applyActivityIndicatorSnapshot(View target,ActivityIndicatorSnapshot snapshot){
        if(target==null)return;
        boolean active=snapshot!=null&&snapshot.active,onlyPaused=snapshot!=null&&snapshot.onlyPaused;int factChecks=snapshot==null?0:snapshot.factChecks;
        TextView icon=target.findViewById(SHEET_ID+2);TextView dot=target.findViewById(SHEET_ID+3);TextView badge=target.findViewById(SHEET_ID+1);
        if(icon!=null)icon.setTextColor(onlyPaused?ORANGE:active?CYAN:MUTED);
        if(dot!=null){dot.setVisibility(active?View.VISIBLE:View.GONE);dot.setBackground(round(onlyPaused?YELLOW:CYAN,999,0,0));}
        if(badge!=null){String shown=compactCount(factChecks);badge.setText(shown);badge.setVisibility(factChecks>0?View.VISIBLE:View.GONE);FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)badge.getLayoutParams();int w=shown.length()<=2?dp(20):shown.length()<=4?dp(29):dp(36);lp.width=w;badge.setLayoutParams(lp);}
        target.setContentDescription(factChecks>0?"Attività: "+factChecks+" fact-check da risolvere":active?(onlyPaused?"Completamento Database in pausa":"Elaborazioni in corso in background"):"Attività");
    }
    private String compactCount(int n){if(n<1000)return String.valueOf(Math.max(0,n));if(n<10_000)return String.format(Locale.ITALY,"%.1fK",n/1000.0).replace(",0K","K");if(n<100_000)return Math.round(n/1000f)+"K";return "99K+";}
    private void installCrashJournal(){previousCrashHandler=Thread.getDefaultUncaughtExceptionHandler();installedCrashHandler=(thread,error)->{try{StringWriter sw=new StringWriter();error.printStackTrace(new PrintWriter(sw));String stack=sw.toString();if(stack.length()>5000)stack=stack.substring(0,5000);getSharedPreferences("va_v3_diag",MODE_PRIVATE).edit().putLong("lastCrashAt",System.currentTimeMillis()).putString("lastCrashTab",tab).putString("lastCrashWizard",libraryWizardStep).putString("lastCrash",stack).commit();}catch(Throwable ignored){}if(previousCrashHandler!=null)previousCrashHandler.uncaughtException(thread,error);};Thread.setDefaultUncaughtExceptionHandler(installedCrashHandler);}
    private void recordAction(String action){getSharedPreferences("va_v3_diag",MODE_PRIVATE).edit().putString("uiLastAction",action==null?"":action).putLong("uiLastActionAt",System.currentTimeMillis()).apply();}
    private synchronized void reconcileResolvedOperationErrorsAsync(){
        long now=System.currentTimeMillis();
        if(operationReconcileInFlight){operationReconcilePending=true;return;}
        long since=now-lastOperationReconcileAt;
        if(since<20_000L){
            if(!operationReconcilePending){
                operationReconcilePending=true;
                uiUpdates.postDelayed(()->{operationReconcilePending=false;reconcileResolvedOperationErrorsAsync();},Math.max(1_000L,20_000L-since));
            }
            return;
        }
        operationReconcileInFlight=true;operationReconcilePending=false;lastOperationReconcileAt=now;
        maintenanceIo.execute(()->{
            try{
                if(db!=null)for(DealRecord d:db.getDeals("all_with_review",1200))
                    if(!needsRefreshForQueue(d))OperationCenter.resolveForDeal(MainActivity.this,d.signature,d.vintedTitle,d.gameName,d.displayName);
            }catch(Throwable ignored){}
            finally{
                operationReconcileInFlight=false;
                if(operationReconcilePending&&!isDestroyed())runOnUiThread(()->{operationReconcilePending=false;reconcileResolvedOperationErrorsAsync();});
            }
        });
    }
    private boolean needsRefreshForQueue(DealRecord d){return d!=null&&(TextUtils.isEmpty(d.vintedUrl)||TextUtils.isEmpty(d.bggId)||d.rating==null||(!TextUtils.isEmpty(d.bggId)&&TextUtils.isEmpty(d.bggImageUrl)));}
    private void reconcileDealOperations(DealRecord d){if(d==null)return;if(!needsRefreshForQueue(d))OperationCenter.resolveForDeal(this,d.signature,d.vintedTitle,d.gameName,d.displayName);else requestDealRefresh(d);}


    private void upgradeOperationLedger(){
        SharedPreferences p=getSharedPreferences("ludo_ui_migrations",MODE_PRIVATE);
        if(!p.getBoolean("v59_progressive_bundle",false)){
            for(OperationCenter.Task task:OperationCenter.tasks(this)){
                if((OperationCenter.QUEUED.equals(task.state)||OperationCenter.RUNNING.equals(task.state))
                        &&(OperationCenter.LINK.equals(task.type)||OperationCenter.SELLER.equals(task.type)||OperationCenter.CATALOG.equals(task.type)||OperationCenter.BUNDLE.equals(task.type))){
                    OperationCenter.error(this,task.id,task.type,"Operazione precedente archiviata","La nuova coda progressiva ripartirà solo dagli annunci prioritari.");
                }
            }
            OperationCenter.clearFailures(this,OperationCenter.CATALOG);OperationCenter.clearFailures(this,OperationCenter.SNAPSHOT);OperationCenter.clearFailures(this,OperationCenter.DEEP_SCAN);OperationCenter.clearFailures(this,OperationCenter.BUNDLE);
            p.edit().putBoolean("v59_progressive_bundle",true).apply();
        }
        if(!p.getBoolean("v594_hide_bundle_plumbing",false)){
            OperationCenter.clearFailures(this,OperationCenter.CATALOG);OperationCenter.clearFailures(this,OperationCenter.SNAPSHOT);OperationCenter.clearFailures(this,OperationCenter.DEEP_SCAN);OperationCenter.clearFailures(this,OperationCenter.BUNDLE);
            p.edit().putBoolean("v594_hide_bundle_plumbing",true).apply();
        }
    }
    private LinearLayout.LayoutParams navLp(){return new LinearLayout.LayoutParams(0,-1,1);}

    // ---------- SCOPRI: editorial dashboard ----------
private void applyDiscoverChrome(){
        boolean home=true;int barColor=home?DISCOVER_BG:BG;
        getWindow().setStatusBarColor(barColor);getWindow().setNavigationBarColor(home?Color.BLACK:barColor);
        getWindow().getDecorView().setSystemUiVisibility(0);
        if(refreshHost!=null)refreshHost.setBackgroundColor(barColor);
        if(body!=null)body.setBackground(home?discoverHomeBackground():new ColorDrawable(barColor));
        if(nav!=null)nav.setBackgroundColor(home?Color.BLACK:SURFACE);
    }
    private Drawable discoverHomeBackground(){
        return new Drawable(){
            private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override public void draw(Canvas canvas){
                Rect b=getBounds();if(b.width()<=0||b.height()<=0)return;
                RadialGradient shader=new RadialGradient(b.width()*.5008f,b.width()*.3118f,b.width()*.7489f,
                        new int[]{Color.rgb(27,33,67),Color.rgb(27,33,67),Color.BLACK},new float[]{0f,.4f,1f},Shader.TileMode.CLAMP);
                Matrix scale=new Matrix();scale.setScale(1f,b.height()/(float)b.width());scale.postTranslate(b.left,b.top);shader.setLocalMatrix(scale);
                paint.setShader(shader);canvas.drawRect(b,paint);
            }
            @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
            @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
            @Override public int getOpacity(){return PixelFormat.OPAQUE;}
        };
    }

    private int homeInterest(DealRecord d){SharedPreferences prefs=getSharedPreferences("ludo_home_interest_v2",MODE_PRIVATE);return HomeDiscoveryPolicy.interest(prefs.getInt(HomeDiscoveryPolicy.listingKey(d.signature),0),prefs.getInt(HomeDiscoveryPolicy.key(d.bggId,d.signature),0));}
    private void setHomeInterest(DealRecord d,int vote){setHomeInterest(d,vote,false);}
    private void setHomeInterest(DealRecord d,int vote,boolean wholeGame){String key=vote<0&&!wholeGame?HomeDiscoveryPolicy.listingKey(d.signature):HomeDiscoveryPolicy.key(d.bggId,d.signature);SharedPreferences prefs=getSharedPreferences("ludo_home_interest_v2",MODE_PRIVATE);if(vote<0){lastHomeDismissKey=key;lastHomeDismissTitle=wholeGame?name(d):"annuncio · "+name(d);lastHomeDismissValue=prefs.getInt(key,0);}prefs.edit().putInt(key,vote).apply();discoverBundleSnapshot=null;scheduleRender(0);}
    private boolean showHomeDismissMenu(View anchor,DealRecord d){PopupMenu menu=new PopupMenu(this,anchor,Gravity.END);menu.getMenu().add("Non mi interessa il gioco").setOnMenuItemClickListener(item->{setHomeInterest(d,-1,true);return true;});menu.show();return true;}
    private double homeScore(DealRecord d){return homeScores.getOrDefault(d.signature,0d);}
    private void renderDiscover(){
        List<DealRecord> deals=db.getDeals("trusted",320);
        Set<String> owned=new HashSet<>();for(LibraryGame game:libraryDb.all())if(!"sold".equals(game.collectionState)&&game.bggId!=null)owned.add(game.bggId.trim());
        deals.removeIf(d->!HomeDiscoveryPolicy.eligible(d.bggId,d.signature,d.languageCode,owned,homeInterest(d)));Map<String,Double> scores=new HashMap<>();for(DealRecord d:deals)scores.put(d.signature,relevance(d)+(homeInterest(d)>0?25:0));homeScores=scores;
        deals.sort((a,b)->Double.compare(homeScore(b),homeScore(a)));
        body.addView(discoverHeader());

        DealRecord best=bestStrongOffer(deals);
        if(best!=null)body.addView(heroOpportunityCard(best));

        if(!lastHomeDismissKey.isEmpty()){TextView undo=text("Escluso "+lastHomeDismissTitle+" · Annulla",12,CYAN,Typeface.NORMAL);undo.setMinHeight(dp(48));undo.setGravity(Gravity.CENTER_VERTICAL);undo.setOnClickListener(v->{getSharedPreferences("ludo_home_interest_v2",MODE_PRIVATE).edit().putInt(lastHomeDismissKey,lastHomeDismissValue).apply();lastHomeDismissKey="";discoverBundleSnapshot=null;render();});body.addView(undo);}
        addDiscoverCategories();
        if(deals.isEmpty()){addDiscoverEmptyState();return;}

        List<DealRecord> value=new ArrayList<>(deals);
        value.removeIf(d->!DealEvaluator.evaluate(d).discoverable());
        value.sort((a,b)->{
            int decision=Integer.compare(decisionPriority(b),decisionPriority(a));
            if(decision!=0)return decision;
            int discount=Integer.compare(nz(saving(b),-1),nz(saving(a),-1));
            return discount!=0?discount:Double.compare(homeScore(b),homeScore(a));
        });
        addDiscoverValueRail(limitDeals(value,12));

        List<DealRecord> topRated=uniqueDiscoverGames(deals,deals.size());
        topRated.sort((a,b)->{
            int ar=a.rank==null?Integer.MAX_VALUE:a.rank,br=b.rank==null?Integer.MAX_VALUE:b.rank;
            int rankCompare=Integer.compare(ar,br);
            if(rankCompare!=0)return rankCompare;
            int ratingCompare=Double.compare(b.rating==null?0:b.rating,a.rating==null?0:a.rating);
            return ratingCompare!=0?ratingCompare:Integer.compare(nz(b.voters,0),nz(a.voters,0));
        });
        addDiscoverTopRatedRail(limitDeals(topRated,3));

        List<DealRecord> newest=new ArrayList<>(deals);
        newest.sort((a,b)->Long.compare(publicationAgeMinutes(a),publicationAgeMinutes(b)));
        addDiscoverFreshRail(limitDeals(newest,12));

        requestDiscoverBundleSpotlight(deals,owned);DiscoverBundleSnapshot bundle=discoverBundleSnapshot;if(bundle!=null&&bundle.source!=null&&bundle.games.stream().allMatch(d->HomeDiscoveryPolicy.eligible(d.bggId,d.signature,d.languageCode,owned,homeInterest(d))))addDiscoverBundleSpotlight(bundle.source,bundle.games);
        List<DealRecord> urgent=urgentDeals(deals);lastUrgentIds=new HashSet<>();
        for(DealRecord item:urgent)if(item!=null&&!TextUtils.isEmpty(item.signature))lastUrgentIds.add(item.signature);
    }

    

    private List<DealRecord> limitDeals(List<DealRecord> input,int max){
        if(input==null||input.isEmpty()||max<=0)return new ArrayList<>();
        return new ArrayList<>(input.subList(0,Math.min(max,input.size())));
    }

    private long publicationAgeMinutes(DealRecord d){
        String label=ageLabel(d);
        if(TextUtils.isEmpty(label))return d.firstSeen>0?Math.max(0,System.currentTimeMillis()-d.firstSeen)/60_000L:Long.MAX_VALUE;
        String raw=label.trim().toLowerCase(Locale.ROOT);
        if("ora".equals(raw))return 0L;
        String[] parts=raw.split("\\s+");
        if(parts.length>=2)try{
            long count=Long.parseLong(parts[0]);String unit=parts[1];
            if(unit.startsWith("min"))return count;
            if("h".equals(unit)||unit.startsWith("or"))return count*60L;
            if("g".equals(unit)||unit.startsWith("gior"))return count*24L*60L;
            if(unit.startsWith("settiman"))return count*7L*24L*60L;
            if(unit.startsWith("mes"))return count*30L*24L*60L;
            if(unit.startsWith("ann"))return count*365L*24L*60L;
        }catch(NumberFormatException ignored){}
        if(raw.matches("\\d{2}/\\d{2}"))try{
            int year=Calendar.getInstance().get(Calendar.YEAR);SimpleDateFormat f=new SimpleDateFormat("dd/MM/yyyy",Locale.ITALY);f.setLenient(false);
            Date date=f.parse(raw+"/"+year);long now=System.currentTimeMillis();if(date!=null&&date.getTime()>now+24L*60L*60_000L)date=f.parse(raw+"/"+(year-1));
            if(date!=null)return Math.max(0,now-date.getTime())/60_000L;
        }catch(Exception ignored){}
        return d.firstSeen>0?Math.max(0,System.currentTimeMillis()-d.firstSeen)/60_000L:Long.MAX_VALUE;
    }

    private List<DealRecord> uniqueDiscoverGames(List<DealRecord> input,int max){
        List<DealRecord> out=new ArrayList<>();Set<String> seen=new HashSet<>();
        if(input!=null)for(DealRecord d:input){
            String key=!TextUtils.isEmpty(d.bggId)?d.bggId:name(d).toLowerCase(Locale.ROOT);
            if(seen.add(key))out.add(d);
            if(out.size()>=max)break;
        }
        return out;
    }

    private void addDiscoverSectionHeading(String title,String action,Runnable onAction){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        String icon="Categorie".equals(title)?LudoIcons.GAMEPAD:"Le migliori offerte".equals(title)?LudoIcons.TAG:"I migliori su BGG".equals(title)?LudoIcons.STAR:"Appena pubblicati".equals(title)?LudoIcons.CLOCK:LudoIcons.GIFT;
        row.addView(appIcon(icon,22,DISCOVER_LAVENDER),new LinearLayout.LayoutParams(dp(34),dp(32)));
        TextView heading=discoverTextWeight(title,18,DISCOVER_TEXT,700);
        heading.setLetterSpacing(-.019f);
        row.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        if(!TextUtils.isEmpty(action)){
            TextView link=discoverTextWeight(action+" →",11,DISCOVER_TEXT,400);
            link.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);link.setMinHeight(dp(48));
            link.setOnClickListener(v->{if(onAction!=null)onAction.run();});
            row.addView(link);
        }
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(27);lp.bottomMargin=dp(12);body.addView(row,lp);
    }

    private View discoverHeader(){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(discoverTextWeight("Bentornato,",21,DISCOVER_TEXT,300));
        TextView name=discoverTextWeight(discoverGreetingName(),37,DISCOVER_TEXT,700);name.setLetterSpacing(-.025f);copy.addView(name);
        row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        TextView engine=appIcon(LudoIcons.GEAR,22,DISCOVER_TEXT);engine.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(50,255,255,255)),round(SURFACE,999,1,DISCOVER_OUTLINE),null));engine.setContentDescription("Apri Motore");engine.setOnClickListener(v->navigate("activity"));row.addView(engine,new LinearLayout.LayoutParams(dp(52),dp(52)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(22);lp.bottomMargin=dp(34);row.setLayoutParams(lp);return row;
    }

    private String discoverGreetingName(){
        return getSharedPreferences("ludo_profile",MODE_PRIVATE).getString("first_name","Checco");
    }

    private Typeface discoverTypeface(int weight){
        int w=Math.max(100,Math.min(900,weight));Typeface cached=discoverTypefaceWeights.get(w);if(cached!=null)return cached;
        try{
            if(discoverTypefaceBase==null)discoverTypefaceBase=Typeface.createFromAsset(getAssets(),"fonts/remus-variable.ttf");
            Typeface face=Typeface.create(discoverTypefaceBase,w,false);discoverTypefaceWeights.put(w,face);return face;
        }catch(Throwable ignored){
            Typeface face=Typeface.create(Typeface.create("sans-serif",Typeface.NORMAL),w,false);discoverTypefaceWeights.put(w,face);return face;
        }
    }

    private TextView discoverTextWeight(String value,float sp,int color,int weight){
        TextView view=new TextView(this);view.setText(value);view.setTextSize(sp);view.setTextColor(color);
        view.setTypeface(discoverTypeface(weight));view.setIncludeFontPadding(false);view.setLineSpacing(0,1.0f);
        return view;
    }

    private TextView discoverText(String value,float sp,int color,int style){
        return discoverTextWeight(value,sp,color,style==Typeface.BOLD?700:400);
    }

    private TextView appIcon(String glyph,float sp,int color){return LudoIcons.view(this,glyph,sp,color);}
    private TextView appIconRegular(String glyph,float sp,int color){return LudoIcons.regularView(this,glyph,sp,color);}
    private Drawable iconDrawable(String glyph,int color,float sizeDp){return LudoIcons.drawable(this,glyph,color,dp(sizeDp));}

    private String[] discoverClusterLabels(){return DiscoverCategories.labels();}
    private int discoverCategoryResource(int index){
        int[] ids={R.drawable.discover_strategy,R.drawable.discover_family,R.drawable.discover_party,R.drawable.discover_fantasy,
                R.drawable.discover_cards,R.drawable.discover_scifi,R.drawable.discover_mystery,R.drawable.discover_war};
        return ids[Math.max(0,Math.min(index,ids.length-1))];
    }
    private Bitmap discoverCategoryIcon(int index){
        Bitmap cached=discoverCategoryIcons.get(index);if(cached!=null)return cached;
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inScaled=false;opts.inSampleSize=8;
        Bitmap bitmap=BitmapFactory.decodeResource(getResources(),discoverCategoryResource(index),opts);
        if(bitmap!=null)discoverCategoryIcons.put(index,bitmap);return bitmap;
    }

    private void addDiscoverCategories(){
        addDiscoverSectionHeading("Categorie",null,null);
        HorizontalScrollView rail=new HorizontalScrollView(this);rail.setHorizontalScrollBarEnabled(false);rail.setClipToPadding(false);
        LinearLayout tiles=new LinearLayout(this);tiles.setPadding(0,0,dp(12),0);
        String[] labels=discoverClusterLabels();for(int i=0;i<labels.length;i++)tiles.addView(discoverCategoryTile(labels[i],i));
        rail.addView(tiles);body.addView(rail);
    }

    private void showDiscoverCategoryDirectory(){
        String[] labels=discoverClusterLabels();
        new AlertDialog.Builder(this).setTitle("Categorie").setItems(labels,(d,which)->openDiscoverCluster(which)).setNegativeButton("Chiudi",null).show();
    }

    private void openDiscoverCluster(int index){databaseCategory=index;openDiscoverCategory(DiscoverCategories.query(index));}

    private void openDiscoverCategory(String category){
        databaseCategory=DiscoverCategories.index(category);databaseQuery="";databaseScope="verified";databaseActiveOnly=false;databaseMinRating=null;databaseMaxPrice=null;databaseSort="alpha";databaseVisible=24;navigate("database");
    }

    private View discoverCategoryTile(String label,int index){
        int[] colors={0xff302d64,0xff361d27,0xff102c25,0xff302a18,0xff443d80,0xff14274a,0xff3b3415,0xff10313e};
        LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(8),dp(10),dp(8),dp(10));tile.setBackground(round(colors[index],13,1,new int[]{0xff7770c6,0xffa45e78,0xff43866c,0xff9c8844,0xff8e82d1,0xff517bb2,0xff9d9047,0xff468794}[index]));
        ImageView icon=new ImageView(this);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setImageBitmap(discoverCategoryIcon(index));
        tile.addView(icon,new LinearLayout.LayoutParams(dp(44),dp(44)));
        TextView labelView=discoverTextWeight(label,12,DISCOVER_TEXT,400);labelView.setGravity(Gravity.CENTER);labelView.setSingleLine(true);
        LinearLayout.LayoutParams textLp=new LinearLayout.LayoutParams(-1,-2);textLp.topMargin=dp(5);tile.addView(labelView,textLp);
        tile.setContentDescription(label);tile.setOnClickListener(v->openDiscoverCluster(index));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(90),dp(94));lp.rightMargin=dp(8);tile.setLayoutParams(lp);return tile;
    }

    private GameRecord discoverGame(DealRecord d){
        if(d==null||TextUtils.isEmpty(d.bggId))return null;
        try{return marketStore.gameStatsByBggId(d.bggId);}catch(Throwable ignored){return null;}
    }

    private String discoverGameDescription(DealRecord d){
        GameRecord game=discoverGame(d);String raw=game==null?"":game.description;
        if(TextUtils.isEmpty(raw))return"Descrizione BGG non disponibile.";
        try{
            String clean=Html.fromHtml(raw,Html.FROM_HTML_MODE_LEGACY).toString().replaceAll("\\s+"," ").trim();
            return TextUtils.isEmpty(clean)?"Descrizione BGG non disponibile.":clean;
        }catch(Throwable ignored){return raw.replaceAll("<[^>]+>"," ").replaceAll("\\s+"," ").trim();}
    }

    private String discoverRankCategory(DealRecord d){
        String cats=d==null?"":d.bggCategories;GameRecord game=discoverGame(d);if(TextUtils.isEmpty(cats)&&game!=null)cats=game.categories;
        String c=cats==null?"":cats.toLowerCase(Locale.ROOT);
        if(c.contains("children"))return"Children";
        if(c.contains("party game")||c.contains("humor")||c.contains("bluffing"))return"Party";
        if(c.contains("wargame")||c.contains("war game"))return"War";
        if(c.contains("abstract strategy"))return"Abstract";
        if(c.contains("economic")||c.contains("civilization")||c.contains("industry")||c.contains("territory building")||(d!=null&&d.weight!=null&&d.weight>=3.2))return"Strategy";
        if(c.contains("fantasy")||c.contains("adventure")||c.contains("horror")||c.contains("science fiction"))return"Thematic";
        return"Family";
    }

    private void setDiscoverBggArtwork(ImageView image,TextView placeholder,DealRecord d){setDiscoverBggArtwork(image,placeholder,d,null);}
    private void setDiscoverBggArtwork(ImageView image,TextView placeholder,DealRecord d,Runnable loaded){
        image.setImageDrawable(null);image.setBackgroundColor(Color.TRANSPARENT);if(d==null)return;
        File local=TextUtils.isEmpty(d.bggId)?null:ArtworkStore.bggFile(this,d.bggId);
        if(local!=null&&local.exists()&&local.length()>1024){
            Bitmap bitmap=decodeLocalBitmap(local,420,520);if(bitmap!=null){image.setImageBitmap(bitmap);if(placeholder!=null)placeholder.setVisibility(View.GONE);if(loaded!=null)image.post(loaded);return;}
        }
        String url=d.bggImageUrl;GameRecord game=null;
        if(TextUtils.isEmpty(url)){game=discoverGame(d);if(game!=null)url=!TextUtils.isEmpty(game.imageUrl)?game.imageUrl:game.thumbnailUrl;}
        if(TextUtils.isEmpty(url))return;
        if(!TextUtils.isEmpty(d.bggId))ArtworkStore.downloadBgg(this,d.bggId,url);
        final TextView ph=placeholder;loadFirstRemote(image,Collections.singletonList(url),()->{if(ph!=null)ph.setVisibility(View.GONE);if(loaded!=null)image.post(loaded);},null);
    }

    private View discoverBggCover(DealRecord d,int width,int height,float radius){return discoverBggCover(d,width,height,radius,false);}

    private View discoverBggCover(DealRecord d,int width,int height,float radius,boolean fit){
        FrameLayout picture=new FrameLayout(this);picture.setBackground(round(DISCOVER_SURFACE,radius,0,0));picture.setClipToOutline(true);
        TextView placeholder=discoverTextWeight(coverPlaceholder(d),9,DISCOVER_MUTED,700);placeholder.setGravity(Gravity.CENTER);placeholder.setPadding(dp(4),dp(4),dp(4),dp(4));picture.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
        ImageView image=new ImageView(this);image.setScaleType(fit?ImageView.ScaleType.FIT_CENTER:ImageView.ScaleType.CENTER_CROP);picture.addView(image,new FrameLayout.LayoutParams(-1,-1));setDiscoverBggArtwork(image,placeholder,d);
        picture.setLayoutParams(new LinearLayout.LayoutParams(width,height));return picture;
    }

    private TextView discoverDiscountBadge(DealRecord d,float textSp){
        Integer saved=saving(d);if(saved==null||saved<=0)return null;
        TextView badge=discoverTextWeight("−"+saved+"%",textSp,DISCOVER_TEXT,400);badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(9),dp(4),dp(9),dp(4));int[] palette={Color.rgb(61,70,90),Color.rgb(49,80,138),Color.rgb(16,96,110),Color.rgb(12,108,60)};int color=palette[HomeDiscoveryPolicy.discountBand(saved)];badge.setBackground(round(color,7,1,color));return badge;
    }

    private void addDiscoverFreshRail(List<DealRecord> deals){
        if(deals==null||deals.isEmpty())return;
        addDiscoverSectionHeading("Appena pubblicati","Vedi tutte",()->openCatalogPreset("recent"));
        HorizontalScrollView rail=new HorizontalScrollView(this);rail.setHorizontalScrollBarEnabled(false);rail.setClipToPadding(false);
        LinearLayout cards=new LinearLayout(this);cards.setPadding(0,0,dp(12),0);
        for(DealRecord d:deals)cards.addView(discoverProductCard(d,true));
        rail.addView(cards);body.addView(rail);
    }

    private void addDiscoverValueRail(List<DealRecord> deals){
        if(deals==null||deals.isEmpty())return;
        addDiscoverSectionHeading("Le migliori offerte","Vedi tutte",()->openCatalogPreset("deal"));
        HorizontalScrollView rail=new HorizontalScrollView(this);rail.setHorizontalScrollBarEnabled(false);rail.setClipToPadding(false);
        LinearLayout cards=new LinearLayout(this);cards.setPadding(0,0,dp(12),0);
        for(DealRecord d:deals)cards.addView(discoverProductCard(d,false));
        rail.addView(cards);body.addView(rail);
    }

    private View discoverFreshCard(DealRecord d){return discoverProductCard(d,true);}
    private View discoverValueCard(DealRecord d){return discoverProductCard(d,false);}

    private View languageIndicators(String value){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(5),0,dp(5));String flag=HomePresentation.editionFlag(value),code=HomePresentation.languageLabel(value).split(" · ")[0];
        if(!flag.isEmpty()){TextView emblem=text(flag,17,TEXT,Typeface.NORMAL);row.addView(emblem,new LinearLayout.LayoutParams(dp(25),dp(26)));}else row.addView(appIcon("\uf1ab",13,MUTED),new LinearLayout.LayoutParams(dp(22),dp(26)));
        row.addView(text("?".equals(code)?"n.d.":code,11,TEXT,Typeface.BOLD));boolean independent=HomePresentation.matchesDependence(value,"IND"),dependent=HomePresentation.matchesDependence(value,"DEP");TextView lock=appIcon(independent?"\uf3c1":dependent?"\uf023":LudoIcons.INFO,12,independent?TEAL:dependent?TEXT:MUTED);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(24),dp(26));ip.leftMargin=dp(3);row.addView(lock,ip);row.setContentDescription("Edizione: "+HomePresentation.editionName(value)+". "+HomePresentation.dependenceLabel(value)+". Tocca per informazioni");return row;
    }
    private View discoverLanguageIndicator(DealRecord d){View row=languageIndicators(d.languageCode);row.setOnClickListener(v->showLanguageHelp());return row;}

    private View discoverProductCard(DealRecord d,boolean fresh){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(10),dp(10),dp(10),dp(10));
        card.setBackground(round(DISCOVER_SURFACE,14,1,DISCOVER_OUTLINE));
        View cover=discoverBggCover(d,-1,dp(180),10,false);card.addView(cover,new LinearLayout.LayoutParams(-1,dp(180)));
        card.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int size=r-l-card.getPaddingLeft()-card.getPaddingRight();if(size>0&&cover.getLayoutParams().height!=size)cover.post(()->{cover.getLayoutParams().height=size;cover.requestLayout();});});
        TextView title=discoverTextWeight(name(d),17,DISCOVER_TEXT,700);title.setLines(2);title.setEllipsize(TextUtils.TruncateAt.END);title.setPadding(0,dp(9),0,dp(4));card.addView(title);
        if(fresh){TextView age=discoverTextWeight(publicationDisplay(d),12,DISCOVER_TEXT,500);age.setSingleLine(true);age.setEllipsize(TextUtils.TruncateAt.END);age.setMinHeight(dp(28));age.setGravity(Gravity.CENTER_VERTICAL);age.setPadding(dp(5),0,dp(5),0);age.setBackground(round(SURFACE2,6,0,0));card.addView(age);}
        LinearLayout rating=new LinearLayout(this);rating.setGravity(Gravity.CENTER_VERTICAL);
        rating.addView(appIcon(LudoIcons.STAR,14,DISCOVER_YELLOW),new LinearLayout.LayoutParams(dp(21),dp(24)));
        rating.addView(discoverTextWeight(d.rating==null?"—":String.format(Locale.ITALY,"%.1f",d.rating),16,DISCOVER_TEXT,500));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(7);rating.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));rating.addView(discoverLanguageIndicator(d));card.addView(rating,rp);
        LinearLayout bottom=new LinearLayout(this);bottom.setGravity(Gravity.CENTER_VERTICAL);
        TextView price=discoverTextWeight(total(d),24,DISCOVER_TEXT,700);price.setSingleLine(true);price.setMinHeight(Math.round(34*getResources().getDisplayMetrics().scaledDensity));price.setAutoSizeTextTypeUniformWithConfiguration(14,24,1,android.util.TypedValue.COMPLEX_UNIT_SP);bottom.addView(price,new LinearLayout.LayoutParams(0,-2,1));
        TextView badge=discoverDiscountBadge(d,11);if(badge!=null)bottom.addView(badge);card.addView(bottom);
        card.setContentDescription(name(d)+", "+total(d));card.setOnClickListener(v->openDetail(d));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(HomeDiscoveryPolicy.railWidth(getResources().getDisplayMetrics().widthPixels-dp(40),dp(8)),-2);lp.rightMargin=dp(8);card.setLayoutParams(lp);return card;
    }

    private void addDiscoverTopRatedRail(List<DealRecord> deals){
        if(deals==null||deals.isEmpty())return;
        addDiscoverSectionHeading("I migliori su BGG","Vedi tutte",()->openCatalogPreset("top_rated"));
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);
        for(int i=0;i<Math.min(3,deals.size());i++)list.addView(discoverTopRatedCard(deals.get(i),i+1));
        body.addView(list);
    }

    private View discoverTopRatedCard(DealRecord d,int rank){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(12),dp(12),dp(12));
        row.setBackground(round(DISCOVER_SURFACE,14,1,DISCOVER_OUTLINE));
        int medal=rank==1?Color.rgb(255,214,118):rank==2?Color.rgb(143,148,157):DISCOVER_ORANGE;
        TextView position=discoverTextWeight(String.valueOf(rank),13,Color.BLACK,700);position.setGravity(Gravity.CENTER);position.setBackground(round(medal,999,0,0));position.setContentDescription("Posizione "+rank+" nella selezione");row.addView(position,new LinearLayout.LayoutParams(dp(26),dp(26)));
        View cover=discoverBggCover(d,dp(68),dp(68),9,false);LinearLayout.LayoutParams coverLp=new LinearLayout.LayoutParams(dp(68),dp(68));coverLp.leftMargin=dp(10);row.addView(cover,coverLp);
        LinearLayout center=new LinearLayout(this);center.setOrientation(LinearLayout.VERTICAL);center.setPadding(dp(12),0,0,0);
        TextView title=discoverTextWeight(name(d),16,DISCOVER_TEXT,700);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);center.addView(title);
        boolean stacked=getResources().getConfiguration().fontScale>1.3f||getResources().getDisplayMetrics().widthPixels<dp(360);LinearLayout values=new LinearLayout(this);values.setOrientation(stacked?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);values.setGravity(Gravity.CENTER_VERTICAL);values.setPadding(0,dp(5),0,0);
        LinearLayout score=new LinearLayout(this);score.setGravity(Gravity.CENTER_VERTICAL);score.addView(appIcon(LudoIcons.STAR,16,DISCOVER_YELLOW),new LinearLayout.LayoutParams(dp(22),dp(28)));
        score.addView(discoverTextWeight(d.rating==null?"—":String.format(Locale.ITALY,"%.1f",d.rating),22,DISCOVER_TEXT,700));score.setContentDescription(d.rating==null?"Voto BGG non disponibile":String.format(Locale.ITALY,"Voto BGG %.1f su 10",d.rating));values.addView(score);
        TextView price=discoverTextWeight(total(d),17,DISCOVER_TEXT,600);price.setSingleLine(true);price.setGravity((stacked?Gravity.START:Gravity.END)|Gravity.CENTER_VERTICAL);price.setPadding(stacked?0:dp(8),stacked?dp(3):0,0,0);price.setAutoSizeTextTypeUniformWithConfiguration(12,17,1,android.util.TypedValue.COMPLEX_UNIT_SP);values.addView(price,stacked?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1));center.addView(values,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout facts=new LinearLayout(this);facts.setOrientation(stacked?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);facts.setGravity(stacked?Gravity.START:Gravity.CENTER_VERTICAL);facts.setPadding(0,dp(5),0,0);
        String meta=d.rank==null?"BGG":("#"+d.rank+" BGG");TextView proof=discoverTextWeight(meta,10,DISCOVER_MUTED,400);proof.setSingleLine(true);proof.setEllipsize(TextUtils.TruncateAt.END);proof.setContentDescription((d.rank==null?"Classifica BGG non disponibile":"Classifica BGG: "+d.rank)+(d.voters==null?". Numero di voti non disponibile":String.format(Locale.ITALY,". %,d voti",d.voters)));facts.addView(proof,stacked?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1));
        TextView savingBadge=discoverDiscountBadge(d,10);if(savingBadge!=null){LinearLayout.LayoutParams badgeLp=new LinearLayout.LayoutParams(-2,-2);badgeLp.leftMargin=stacked?0:dp(6);badgeLp.topMargin=stacked?dp(4):0;facts.addView(savingBadge,badgeLp);}center.addView(facts,new LinearLayout.LayoutParams(-1,-2));
        row.addView(center,new LinearLayout.LayoutParams(0,-2,1));row.setOnClickListener(v->openDetail(d));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(8);row.setLayoutParams(lp);return row;
    }

    private View heroOpportunityCard(DealRecord d){
        FrameLayout card=new FrameLayout(this);card.setClipToOutline(true);GradientDrawable background=new GradientDrawable();background.setColors(new int[]{Color.rgb(48,22,78),Color.rgb(12,7,35)});background.setGradientType(GradientDrawable.RADIAL_GRADIENT);background.setGradientCenter(.28f,.30f);background.setGradientRadius(dp(310));background.setCornerRadius(dp(16));background.setStroke(dp(1),Color.rgb(106,48,131));GradientDrawable glow=new GradientDrawable();glow.setColors(new int[]{Color.argb(110,151,63,204),Color.TRANSPARENT});glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);glow.setGradientCenter(.12f,.18f);glow.setGradientRadius(dp(190));glow.setCornerRadius(dp(16));card.setBackground(new android.graphics.drawable.LayerDrawable(new Drawable[]{background,glow}));
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),dp(16),dp(16),dp(16));card.addView(content,new FrameLayout.LayoutParams(-1,-2));
        FrameLayout art=new FrameLayout(this);art.setBackgroundColor(Color.TRANSPARENT);art.setClipToOutline(true);FeaturedBoxView box=new FeaturedBoxView();art.addView(box,new FrameLayout.LayoutParams(-1,-1));TextView placeholder=discoverTextWeight(coverPlaceholder(d),11,DISCOVER_MUTED,400);placeholder.setGravity(Gravity.CENTER);art.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));ImageView image=new ImageView(this);image.setVisibility(View.INVISIBLE);art.addView(image,new FrameLayout.LayoutParams(dp(1),dp(1),Gravity.TOP|Gravity.START));TextView badge=discoverDiscountBadge(d,14);if(badge!=null){FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END);bp.setMargins(dp(8),dp(8),dp(8),0);art.addView(badge,bp);}
        String summary=discoverGameDescription(d);final int[] previous={-1,-1};Runnable arrange=()->{
            Drawable cover=image.getDrawable();int width=cover==null?1:cover.getIntrinsicWidth(),height=cover==null?1:cover.getIntrinsicHeight();int mode=HomePresentation.coverMode(width,height);int available=content.getWidth()-content.getPaddingLeft()-content.getPaddingRight();if(available<=0)available=getResources().getDisplayMetrics().widthPixels-dp(72);if(previous[0]==mode&&previous[1]==available)return;previous[0]=mode;previous[1]=available;
            if(art.getParent()!=null)((ViewGroup)art.getParent()).removeView(art);content.removeAllViews();
            LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);TextView title=discoverTextWeight(name(d),mode==HomePresentation.SQUARE?27:28,DISCOVER_TEXT,700);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);copy.addView(title);LinearLayout facts=new LinearLayout(this);facts.setGravity(Gravity.CENTER_VERTICAL);facts.addView(appIcon(LudoIcons.STAR,15,DISCOVER_YELLOW),new LinearLayout.LayoutParams(dp(23),dp(26)));facts.addView(discoverTextWeight(d.rating==null?"—":String.format(Locale.ITALY,"%.1f",d.rating),17,TEXT,700));copy.addView(facts);copy.addView(discoverLanguageIndicator(d));TextView price=discoverTextWeight(total(d),27,DISCOVER_TEXT,700);price.setPadding(0,dp(3),0,0);price.setGravity(Gravity.CENTER_VERTICAL);price.setSingleLine(true);price.setAutoSizeTextTypeUniformWithConfiguration(16,27,1,android.util.TypedValue.COMPLEX_UNIT_SP);
            TextView eyebrow=discoverTextWeight("★ Gioco in evidenza",12,Color.rgb(242,231,255),600);eyebrow.setPadding(dp(8),dp(5),dp(8),dp(5));eyebrow.setBackground(round(Color.rgb(73,38,99),8,1,Color.rgb(156,94,190)));LinearLayout.LayoutParams labelParams=new LinearLayout.LayoutParams(-2,-2);labelParams.bottomMargin=dp(8);copy.addView(eyebrow,0,labelParams);
            if(!TextUtils.isEmpty(summary)&&!summary.equals("Descrizione BGG non disponibile.")){TextView description=discoverTextWeight(summary,12,DISCOVER_MUTED,400);description.setMaxLines(2);description.setEllipsize(TextUtils.TruncateAt.END);description.setPadding(0,dp(8),0,dp(8));copy.addView(description);}
            LinearLayout prices=new LinearLayout(this);prices.setGravity(Gravity.CENTER_VERTICAL);prices.addView(price,new LinearLayout.LayoutParams(0,dp(44),1));copy.addView(prices);
            Button action=button("Scopri di più",Color.WHITE);action.setCompoundDrawables(null,null,iconDrawable(LudoIcons.CHEVRON_RIGHT,Color.BLACK,16),null);action.setCompoundDrawablePadding(dp(10));action.setTextColor(Color.BLACK);action.setTextSize(14);action.setPadding(dp(12),0,dp(12),0);action.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(40,0,0,0)),round(Color.WHITE,999,0,0),null));action.setOnClickListener(v->openDetail(d));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(52));ap.topMargin=dp(6);copy.addView(action,ap);
            if(mode==HomePresentation.SQUARE||mode==HomePresentation.WIDE||available<dp(290)||getResources().getConfiguration().fontScale>1.15f){int h=mode==HomePresentation.SQUARE?Math.min(dp(240),Math.round(available*.82f)):Math.max(dp(120),Math.min(dp(260),Math.round(available*height/(float)Math.max(1,width))));content.addView(art,new LinearLayout.LayoutParams(-1,h));copy.setPadding(0,dp(12),0,0);content.addView(copy,new LinearLayout.LayoutParams(-1,-2));}
            else{LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);float imageWeight=mode==HomePresentation.TALL?.52f:.52f;int imageWidth=Math.round(available*imageWeight);int h=mode==HomePresentation.TALL?Math.max(dp(210),Math.min(dp(300),Math.round(imageWidth*height/(float)Math.max(1,width)))):Math.max(dp(230),imageWidth);row.addView(art,new LinearLayout.LayoutParams(0,h,imageWeight));copy.setPadding(dp(14),0,0,0);row.addView(copy,new LinearLayout.LayoutParams(0,-2,1-imageWeight));content.addView(row,new LinearLayout.LayoutParams(-1,-2));}

        };
        content.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol)content.post(arrange);});arrange.run();setFeaturedArtwork(image,placeholder,d,()->{Drawable drawable=image.getDrawable();if(drawable instanceof android.graphics.drawable.BitmapDrawable){Bitmap bitmap=((android.graphics.drawable.BitmapDrawable)drawable).getBitmap();if(bitmap!=null&&!bitmap.isRecycled()){box.setCover(bitmap,featuredPedestal);placeholder.setVisibility(View.GONE);}}content.post(arrange);});card.setOnClickListener(v->openDetail(d));card.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));return card;
    }

    private Bitmap featuredPedestal;
    private boolean featuredPedestalChecked;private final Object featuredPedestalLock=new Object();
    /** Home and detail stages share cache, off-thread decoding and the same PNG. */
    private void setFeaturedArtwork(ImageView image,TextView placeholder,DealRecord d,Runnable loaded){
        setProductArtwork(image,placeholder,d.bggId,d.bggImageUrl,d.signature,loaded);
    }
    private void setProductArtwork(ImageView image,TextView placeholder,String bggId,String sourceUrl,String identity,Runnable loaded){
        placeholder.setVisibility(View.VISIBLE);image.setImageDrawable(null);image.setTag(identity);
        galleryNet.execute(()->{
            synchronized(featuredPedestalLock){if(!featuredPedestalChecked){featuredPedestalChecked=true;try{int id=getResources().getIdentifier("featured_game_pedestal","drawable",getPackageName());if(id!=0){BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;options.inPreferredConfig=Bitmap.Config.ARGB_8888;BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeResource(getResources(),id,bounds);options.inSampleSize=1;while(bounds.outWidth/options.inSampleSize>1200)options.inSampleSize*=2;Bitmap candidate=BitmapFactory.decodeResource(getResources(),id,options);if(candidate!=null&&candidate.hasAlpha()&&Color.alpha(candidate.getPixel(0,0))==0)featuredPedestal=candidate;}}catch(RuntimeException|OutOfMemoryError ignored){featuredPedestal=null;}}}
            File file=TextUtils.isEmpty(bggId)?null:ArtworkStore.bggFile(this,bggId);Bitmap bitmap=file!=null&&file.exists()?decodeLocalBitmap(file,600,700):null;
            String remote=sourceUrl;if(TextUtils.isEmpty(remote)&&!TextUtils.isEmpty(bggId)){try{GameRecord game=marketStore.gameStatsByBggId(bggId);if(game!=null)remote=TextUtils.isEmpty(game.imageUrl)?game.thumbnailUrl:game.imageUrl;}catch(RuntimeException ignored){}}
            final String url=remote;
            runOnUiThread(()->{if(isDestroyed()||isFinishing()||!java.util.Objects.equals(identity,image.getTag()))return;if(bitmap!=null&&!bitmap.isRecycled()){image.setImageBitmap(bitmap);loaded.run();return;}
                if(TextUtils.isEmpty(url))return;if(!TextUtils.isEmpty(bggId))ArtworkStore.downloadBgg(this,bggId,url);loadFirstRemote(image,Collections.singletonList(url),loaded,null);
            });
        });
    }
    /** Transparent foreground artwork: never owns title, price, photos or routing. */
    private FrameLayout productBoxArtwork(String bggId,String url,String name){
        FrameLayout stage=new FrameLayout(this);stage.setBackgroundColor(Color.TRANSPARENT);FeaturedBoxView box=new FeaturedBoxView();box.setContentDescription("Scatola di "+(TextUtils.isEmpty(name)?"questo gioco":name));stage.addView(box,new FrameLayout.LayoutParams(-1,-1));
        TextView placeholder=text("Copertina non disponibile",12,MUTED,Typeface.NORMAL);placeholder.setGravity(Gravity.CENTER);stage.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));ImageView image=new ImageView(this);image.setVisibility(View.INVISIBLE);stage.addView(image,new FrameLayout.LayoutParams(dp(1),dp(1)));
        setProductArtwork(image,placeholder,bggId,url,"product:"+bggId+":"+url,()->{Drawable drawable=image.getDrawable();if(drawable instanceof android.graphics.drawable.BitmapDrawable){Bitmap bitmap=((android.graphics.drawable.BitmapDrawable)drawable).getBitmap();if(bitmap!=null&&!bitmap.isRecycled()){box.setCover(bitmap,featuredPedestal);placeholder.setVisibility(View.GONE);}}});return stage;
    }
    private int productArtworkHeight(){int width=getResources().getDisplayMetrics().widthPixels-dp(40);return Math.max(dp(220),Math.min(dp(300),Math.round(width*.90f)));}
    private Drawable productPageBackground(){return new Drawable(){
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override protected void onBoundsChange(Rect bounds){paint.setShader(new RadialGradient(bounds.centerX(),bounds.top+dp(180),Math.max(dp(430),bounds.width()*.85f),new int[]{Color.rgb(48,24,70),Color.rgb(23,17,43),BG},new float[]{0,.52f,1},Shader.TileMode.CLAMP));}
        @Override public void draw(Canvas canvas){canvas.drawRect(getBounds(),paint);}
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
        @Override public int getOpacity(){return PixelFormat.OPAQUE;}
    };}
    private View dealProductArtwork(DealRecord d){return TextUtils.isEmpty(d.bggId)?galleryView(d,0,true):productBoxArtwork(d.bggId,d.bggImageUrl,name(d));}

    private View discoverFlatArtwork(DealRecord d,int width,int height){
        return discoverBggCover(d,width,height,14);
    }

    

    private final class FeaturedBoxView extends View{
        private final Paint bitmapPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG),fillPaint=new Paint(Paint.ANTI_ALIAS_FLAG),edgePaint=new Paint(Paint.ANTI_ALIAS_FLAG),shadowPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix coverMatrix=new Matrix();private final Path frontPath=new Path(),topPath=new Path(),sidePath=new Path();private final FeaturedBoxGeometry geometry=new FeaturedBoxGeometry();private final RectF flatBounds=new RectF(),ambient=new RectF(),contact=new RectF(),pedestalBounds=new RectF();private final float[] source=new float[8];
        private Bitmap cover,pedestal;private int edgeColor;private boolean prepared,failed;private LinearGradient sideLight,topLight,frontLight;private android.graphics.RadialGradient contactLight,ambientLight;
        FeaturedBoxView(){super(MainActivity.this);setContentDescription("Scatola del gioco in prospettiva");}
        void setCover(Bitmap bitmap,Bitmap base){cover=bitmap;pedestal=base;failed=false;try{edgeColor=sampleEdge(bitmap);}catch(RuntimeException e){failed=true;}prepare();invalidate();}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);prepare();}
        private void path(Path path,float[] p){path.reset();path.moveTo(p[0],p[1]);for(int i=2;i<8;i+=2)path.lineTo(p[i],p[i+1]);path.close();}
        private void prepare(){
            prepared=false;if(cover==null||cover.isRecycled()||getWidth()<=0||getHeight()<=0)return;
            float ratio=cover.getWidth()/(float)cover.getHeight(),w=Math.min(getWidth(),getHeight()*ratio),h=w/ratio;flatBounds.set((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);
            if(failed||pedestal==null||pedestal.isRecycled())return;
            try{
                if(!geometry.update(getWidth(),getHeight(),cover.getWidth(),cover.getHeight(),getResources().getDisplayMetrics().density,pedestal.getWidth()/(float)pedestal.getHeight()))return;
                source[0]=0;source[1]=0;source[2]=cover.getWidth();source[3]=0;source[4]=cover.getWidth();source[5]=cover.getHeight();source[6]=0;source[7]=cover.getHeight();coverMatrix.reset();if(!coverMatrix.setPolyToPoly(source,0,geometry.front,0,4))return;
                path(frontPath,geometry.front);path(topPath,geometry.top);path(sidePath,geometry.side);
                float l=geometry.left,r=l+geometry.width,b=geometry.bottom,d=geometry.depth;pedestalBounds.set(geometry.pedestalLeft,geometry.pedestalTop,geometry.pedestalLeft+geometry.pedestalWidth,geometry.pedestalTop+geometry.pedestalHeight);
                topLight=new LinearGradient(l,geometry.top[3],r,b,shade(edgeColor,.80f),shade(edgeColor,.58f),Shader.TileMode.CLAMP);
                sideLight=new LinearGradient(r,0,r+d,0,shade(edgeColor,.64f),shade(edgeColor,.42f),Shader.TileMode.CLAMP);
                frontLight=new LinearGradient(l,0,r,b,new int[]{Color.argb(12,255,255,255),Color.TRANSPARENT,Color.argb(16,0,0,0)},new float[]{0,.6f,1},Shader.TileMode.CLAMP);
                ambient.set(l-geometry.width*.12f,b-geometry.height*.04f,r+d+geometry.width*.12f,b+geometry.height*.12f);contact.set(l-geometry.width*.02f,b-geometry.height*.012f,r+d+geometry.width*.02f,b+geometry.height*.05f);contactLight=new android.graphics.RadialGradient(0,0,1,new int[]{Color.argb(245,0,0,0),Color.argb(165,0,0,0),Color.TRANSPARENT},new float[]{0,.55f,1},Shader.TileMode.CLAMP);ambientLight=new android.graphics.RadialGradient(0,0,1,new int[]{Color.argb(100,0,0,0),Color.TRANSPARENT},null,Shader.TileMode.CLAMP);prepared=true;
            }catch(RuntimeException e){failed=true;}
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);if(cover==null||cover.isRecycled())return;
            if(!prepared){canvas.drawBitmap(cover,null,flatBounds,bitmapPaint);return;}
            int save=canvas.save();try{
                canvas.drawBitmap(pedestal,null,pedestalBounds,bitmapPaint);
                // Cached radial shaders produce soft elliptical shadows without bitmap blur.
                int ambientSave=canvas.save();canvas.translate(ambient.centerX(),ambient.centerY());canvas.scale(ambient.width()/2,ambient.height()/2);shadowPaint.setShader(ambientLight);canvas.drawCircle(0,0,1,shadowPaint);canvas.restoreToCount(ambientSave);
                int contactSave=canvas.save();canvas.translate(contact.centerX(),contact.centerY());canvas.scale(contact.width()/2,contact.height()/2);shadowPaint.setShader(contactLight);canvas.drawCircle(0,0,1,shadowPaint);canvas.restoreToCount(contactSave);shadowPaint.setShader(null);
                fillPaint.setShader(topLight);canvas.drawPath(topPath,fillPaint);fillPaint.setShader(sideLight);canvas.drawPath(sidePath,fillPaint);canvas.drawBitmap(cover,coverMatrix,bitmapPaint);
                int frontSave=canvas.save();canvas.clipPath(frontPath);fillPaint.setShader(frontLight);canvas.drawPaint(fillPaint);canvas.restoreToCount(frontSave);
                edgePaint.setStyle(Paint.Style.STROKE);edgePaint.setStrokeWidth(Math.max(1,getResources().getDisplayMetrics().density*.5f));edgePaint.setColor(Color.argb(45,255,255,255));canvas.drawPath(topPath,edgePaint);edgePaint.setColor(Color.argb(50,0,0,0));canvas.drawLine(geometry.front[2],geometry.front[3],geometry.front[4],geometry.front[5],edgePaint);
            }catch(RuntimeException e){failed=true;prepared=false;canvas.restoreToCount(save);save=canvas.save();canvas.drawBitmap(cover,null,flatBounds,bitmapPaint);}finally{canvas.restoreToCount(save);}
        }
        private int sampleEdge(Bitmap bitmap){
            if(bitmap==null||bitmap.getWidth()<2||bitmap.getHeight()<2)return Color.rgb(58,50,76);
            long rr=0,gg=0,bb=0;int count=0,x=Math.max(0,Math.min(bitmap.getWidth()-1,(int)(bitmap.getWidth()*.90f)));
            for(int i=1;i<=7;i++){int y=Math.min(bitmap.getHeight()-1,(int)(bitmap.getHeight()*(i/8f)));int c=bitmap.getPixel(x,y);rr+=Color.red(c);gg+=Color.green(c);bb+=Color.blue(c);count++;}
            int base=Color.rgb((int)(rr/count),(int)(gg/count),(int)(bb/count));float[] hsv=new float[3];Color.colorToHSV(base,hsv);hsv[1]=Math.min(.72f,hsv[1]*.85f);hsv[2]=Math.max(.10f,Math.min(.52f,hsv[2]*.56f));return Color.HSVToColor(hsv);
        }
        private int shade(int c,float f){return Color.rgb(Math.min(255,(int)(Color.red(c)*f)),Math.min(255,(int)(Color.green(c)*f)),Math.min(255,(int)(Color.blue(c)*f)));}
    }

    private View discoverBoxArtwork(DealRecord d,int width,int height){
        FrameLayout box=new FrameLayout(this);box.setClipChildren(false);box.setClipToPadding(false);
        View shadow=new View(this);shadow.setBackground(round(Color.argb(50,40,30,24),9,0,0));FrameLayout.LayoutParams shadowLp=new FrameLayout.LayoutParams(width-dp(3),height-dp(1),Gravity.RIGHT|Gravity.BOTTOM);shadowLp.rightMargin=dp(1);box.addView(shadow,shadowLp);
        View spine=new View(this);GradientDrawable spinePaint=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.rgb(115,68,50),DISCOVER_BOX_SIDE});spinePaint.setCornerRadii(new float[]{dp(3),dp(3),dp(7),dp(7),dp(7),dp(7),dp(3),dp(3)});spine.setBackground(spinePaint);FrameLayout.LayoutParams sideLp=new FrameLayout.LayoutParams(dp(16),height-dp(5),Gravity.RIGHT|Gravity.CENTER_VERTICAL);sideLp.rightMargin=dp(1);box.addView(spine,sideLp);
        FrameLayout front=new FrameLayout(this);front.setBackground(round(DISCOVER_SURFACE,6,0,0));front.setClipToOutline(true);ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);front.addView(cover,new FrameLayout.LayoutParams(-1,-1));TextView placeholder=discoverText(coverPlaceholder(d),9,DISCOVER_MUTED,Typeface.BOLD);placeholder.setGravity(Gravity.CENTER);front.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));setDealArtwork(cover,placeholder,d);front.setElevation(dp(6));front.setRotationY(-8f);front.setCameraDistance(dp(900));FrameLayout.LayoutParams frontLp=new FrameLayout.LayoutParams(width-dp(17),height-dp(3),Gravity.LEFT|Gravity.CENTER_VERTICAL);box.addView(front,frontLp);
        box.setContentDescription(name(d)+" · copertina BGG");return box;
    }

    private void requestDiscoverBundleSpotlight(List<DealRecord> deals,Set<String> owned){
        DiscoverBundleSnapshot cached=discoverBundleSnapshot;if(cached!=null&&System.currentTimeMillis()-cached.loadedAt<60_000L||!discoverBundleLoading.compareAndSet(false,true))return;
        List<DealRecord> candidates=limitDeals(deals,40);
        uiDataIo.execute(()->{DealRecord chosen=null;List<DealRecord> games=Collections.emptyList();try{for(DealRecord source:uniqueBundleSources(candidates)){List<DealRecord> partners=bundleDealsForSource(source);partners.removeIf(d->!HomeDiscoveryPolicy.eligible(d.bggId,d.signature,d.languageCode,owned,homeInterest(d)));if(partners.size()>=2){chosen=source;games=partners;break;}}}catch(Throwable ignored){}final DiscoverBundleSnapshot ready=new DiscoverBundleSnapshot(chosen,games);runOnUiThread(()->{discoverBundleLoading.set(false);if(isDestroyed()||isFinishing())return;DiscoverBundleSnapshot old=discoverBundleSnapshot;discoverBundleSnapshot=ready;if("discover".equals(tab)&&(ready.source!=null||old!=null&&old.source!=null))scheduleRender(0);});});
    }
    private void addDiscoverBundleSpotlight(DealRecord d,List<DealRecord> games){
        if(games.size()<2)return;
        addDiscoverSectionHeading("Dallo stesso venditore","Apri",()->openBundleDetail(d));
        LinearLayout card=verticalCard();card.setPadding(dp(14),dp(14),dp(14),dp(16));LinearLayout covers=new LinearLayout(this);
        for(int i=0;i<Math.min(3,games.size());i++){LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(140),1);if(i>0)cp.leftMargin=dp(8);covers.addView(discoverBggCover(games.get(i),-1,dp(140),10,false),cp);}card.addView(covers);
        LinearLayout bottom=new LinearLayout(this);bottom.setGravity(Gravity.CENTER_VERTICAL);bottom.setPadding(0,dp(14),0,0);LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);TextView title=text(bundleTitle(games),18,TEXT,Typeface.BOLD);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);copy.addView(title);copy.addView(text(games.size()+" giochi · stesso venditore",12,MUTED,Typeface.NORMAL));bottom.addView(copy,new LinearLayout.LayoutParams(0,-2,1));bottom.addView(appIcon(LudoIcons.CHEVRON_RIGHT,17,TEXT),new LinearLayout.LayoutParams(dp(44),dp(48)));card.addView(bottom);card.setOnClickListener(v->openBundleDetail(d));body.addView(card);
    }

    private View discoverStaleHint(){
        TextView hint=text("Il radar non vede nuovi annunci da qualche giorno.",12,DISCOVER_MUTED,Typeface.NORMAL);
        hint.setPadding(dp(12),dp(10),dp(12),dp(10));hint.setBackground(round(DISCOVER_SURFACE,14,0,0));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(18);hint.setLayoutParams(lp);return hint;
    }

    private void addDiscoverEmptyState(){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(15),dp(16),dp(15));card.setBackground(round(DISCOVER_SURFACE,18,0,0));
        card.addView(text("Ancora nessun annuncio pronto",17,DISCOVER_TEXT,Typeface.BOLD));
        card.addView(text("Quando Ludo conferma gioco, annuncio e dati principali, le occasioni appariranno qui.",13,DISCOVER_MUTED,Typeface.NORMAL));
        TextView action=text("Apri Motore  ›",13,DISCOVER_TEXT,Typeface.BOLD);action.setPadding(0,dp(10),0,0);action.setOnClickListener(v->navigate("activity"));card.addView(action);body.addView(card);
    }

    private DealRecord bestStrongOffer(List<DealRecord> deals){
        DealRecord best=null;double score=-1e9;
        for(DealRecord d:deals){if(!personalDealEligible(d))continue;DealEvaluator.Evaluation e=DealEvaluator.evaluate(d);if(!e.discoverable())continue;double value=homeScore(d)+(ageMinutes(d)<=30?7:0);if(value>score){score=value;best=d;}}
        return best;
    }

    private long ageMinutes(DealRecord d){return Math.max(0,System.currentTimeMillis()-d.firstSeen)/60000L;}

    private void addBundleEmptyState(){sectionHeader("▣","Bundle","","Vedi tutti","bundle");LinearLayout c=new LinearLayout(this);c.setGravity(Gravity.CENTER_VERTICAL);c.setPadding(dp(14),dp(10),dp(14),dp(10));c.setBackground(round(SURFACE,22,1,OUTLINE));ImageView im=new ImageView(this);im.setImageResource(R.drawable.ludo_bundle_gift);im.setScaleType(ImageView.ScaleType.FIT_CENTER);c.addView(im,new LinearLayout.LayoutParams(dp(92),dp(96)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);tx.setPadding(dp(10),0,0,0);tx.addView(text("Ancora niente qui",19,TEXT,Typeface.BOLD));tx.addView(text("Ludo controlla i seller promettenti in automatico.",13,MUTED,Typeface.NORMAL));TextView activity=text("Vedi attività  ›",13,CYAN,Typeface.BOLD);activity.setMinHeight(dp(38));activity.setGravity(Gravity.CENTER_VERTICAL);activity.setOnClickListener(v->navigate("activity"));tx.addView(activity);c.addView(tx,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(122));cp.bottomMargin=dp(6);body.addView(c,cp);}

private View heroDealVNext(DealRecord d){DealEvaluator.Evaluation evaluation=DealEvaluator.evaluate(d);LinearLayout card=verticalCard();card.setPadding(dp(14),dp(14),dp(14),dp(14));card.setBackground(round(SURFACE,22,1,dealAccent(d)));LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.addView(text("MIGLIORE OCCASIONE",11,LIME,Typeface.BOLD));top.addView(new Space(this),new LinearLayout.LayoutParams(0,1,1));top.addView(publicationText(d,12,Typeface.BOLD));card.addView(top);LinearLayout main=new LinearLayout(this);main.setGravity(Gravity.CENTER_VERTICAL);main.setPadding(0,dp(12),0,0);main.addView(dealArtworkView(d,dp(102),dp(132)),new LinearLayout.LayoutParams(dp(102),dp(132)));LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(14),0,0,0);TextView title=text(name(d),22,TEXT,Typeface.BOLD);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);info.addView(title);String bgg=d.rating==null?"BGG n/d":"★ "+String.format(Locale.ITALY,"%.1f",d.rating)+" BGG";info.addView(text(bgg+" · "+languageShort(d.languageCode),13,MUTED,Typeface.BOLD));TextView price=text(total(d),29,LIME,Typeface.BOLD);price.setPadding(0,dp(10),0,0);info.addView(price);Integer sv=saving(d);if(sv!=null){String ref=d.benchmarkCents==null?"":" · rif. "+money(d.benchmarkCents);info.addView(text("−"+sv+"%"+ref,13,PINK,Typeface.BOLD));}main.addView(info,new LinearLayout.LayoutParams(0,-2,1));card.addView(main);TextView why=text(evaluation.reason,13,TEXT,Typeface.NORMAL);why.setLineSpacing(0,1.08f);why.setPadding(0,dp(12),0,0);card.addView(why);card.setOnClickListener(v->openDetail(d));return card;}

private View heroDealV51(DealRecord d,String icon,String label){
        DealEvaluator.Evaluation evaluation=DealEvaluator.evaluate(d);FrameLayout frame=new FrameLayout(this);int heroAccent=dealAccent(d);frame.setBackground(round(detailSurface(heroAccent),28,2,heroAccent));frame.setElevation(dp(3));frame.setClipToOutline(true);
        ImageView scenic=new ImageView(this);scenic.setImageResource(R.drawable.ludo_bg_forest_wide);scenic.setScaleType(ImageView.ScaleType.CENTER_CROP);scenic.setAlpha(.17f);frame.addView(scenic,new FrameLayout.LayoutParams(-1,-1));
        View veil=new View(this);veil.setBackground(verticalGradient(Color.argb(28,7,19,25),Color.argb(220,7,19,25)));frame.addView(veil,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(18),dp(16),dp(18),dp(16));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);String chipLabel=(TextUtils.isEmpty(icon)?"":icon+"  ")+evaluation.label.toUpperCase(Locale.ITALY);TextView kind=materialChip(chipLabel,heroAccent,heroAccent==ORANGE||heroAccent==YELLOW||heroAccent==TEAL?BG:TEXT,true);top.addView(kind,new LinearLayout.LayoutParams(-2,dp(40)));top.addView(new Space(this),new LinearLayout.LayoutParams(0,1,1));TextView age=publicationText(d,15,Typeface.BOLD);age.setBackground(round(SURFACE2,999,0,0));age.setPadding(dp(14),0,dp(14),0);age.setGravity(Gravity.CENTER);top.addView(age,new LinearLayout.LayoutParams(-2,dp(40)));card.addView(top);
        LinearLayout main=new LinearLayout(this);main.setGravity(Gravity.CENTER_VERTICAL);main.setPadding(0,dp(15),0,0);main.addView(dealArtworkView(d,dp(132),dp(174)),new LinearLayout.LayoutParams(dp(132),dp(174)));
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(16),0,0,0);TextView game=text(name(d),26,TEXT,Typeface.BOLD);game.setSingleLine(true);game.setEllipsize(TextUtils.TruncateAt.END);info.addView(game);
        LinearLayout meta=new LinearLayout(this);meta.setGravity(Gravity.CENTER_VERTICAL);meta.setPadding(0,dp(5),0,0);meta.addView(text("★  "+scoreLabel(d),15,YELLOW,Typeface.BOLD));LinearLayout.LayoutParams lg=new LinearLayout.LayoutParams(-2,-2);lg.leftMargin=dp(8);View lang=langChip(d.languageCode);lang.setOnClickListener(v->editListingInfo(d,null));meta.addView(lang,lg);info.addView(meta);
        TextView price=text(total(d),34,LIME,Typeface.BOLD);price.setPadding(0,dp(10),0,0);info.addView(price);TextView reason=text(evaluation.reason,12,MUTED,Typeface.BOLD);reason.setMaxLines(2);reason.setPadding(0,dp(4),0,0);info.addView(reason);
        main.addView(info,new LinearLayout.LayoutParams(0,-2,1));card.addView(main);
        String action=evaluation.decision==DealEvaluator.Decision.OFFER?"Vedi offerta":"Apri annuncio";Button open=button(action,LIME);open.setEnabled(!TextUtils.isEmpty(d.vintedUrl));open.setAlpha(open.isEnabled()?1f:.45f);open.setOnClickListener(v->openDetail(d));LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,dp(54));op.topMargin=dp(12);card.addView(open,op);
        frame.addView(card,new FrameLayout.LayoutParams(-1,-1));frame.setOnClickListener(v->openDetail(d));LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(dp(364),dp(350));fp.rightMargin=dp(12);frame.setLayoutParams(fp);return frame;
    }

private View heroBundleV51(DealRecord d){
        List<BundleSuggestion>b=bundleDb.forSource(d.signature,1);List<DealRecord> games=bundleDealsForSource(d);if(b.isEmpty()||games.size()<2)return heroDealV51(d,"▣","BUNDLE");BundleSuggestion s=b.get(0);BundlePlan plan=bundlePlan(games);
        FrameLayout frame=new FrameLayout(this);int heroAccent=dealAccent(d);frame.setBackground(round(detailSurface(heroAccent),28,2,heroAccent));frame.setElevation(dp(3));frame.setClipToOutline(true);
        ImageView scenic=new ImageView(this);scenic.setImageResource(R.drawable.ludo_bg_bundle_glow);scenic.setScaleType(ImageView.ScaleType.CENTER_CROP);scenic.setAlpha(.24f);frame.addView(scenic,new FrameLayout.LayoutParams(-1,-1));
        View veil=new View(this);veil.setBackground(verticalGradient(Color.argb(18,7,19,25),Color.argb(230,7,19,25)));frame.addView(veil,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(18),dp(16),dp(18),dp(16));LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.addView(materialChip("BUNDLE",PINK,TEXT,true),new LinearLayout.LayoutParams(-2,dp(40)));top.addView(new Space(this),new LinearLayout.LayoutParams(0,1,1));top.addView(materialChip("spedizione da verificare",SURFACE2,TEXT,false),new LinearLayout.LayoutParams(-2,dp(38)));card.addView(top);
        LinearLayout main=new LinearLayout(this);main.setGravity(Gravity.CENTER_VERTICAL);main.setPadding(0,dp(15),0,0);main.addView(bundleMiniFan(games),new LinearLayout.LayoutParams(dp(124),dp(168)));LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(14),0,0,0);TextView bundleName=text(bundleTitle(games),21,TEXT,Typeface.BOLD);bundleName.setMaxLines(2);bundleName.setEllipsize(TextUtils.TruncateAt.END);info.addView(bundleName);TextView offer=text("Offerta bundle · "+money(plan.offerSubtotal),18,LIME,Typeface.BOLD);offer.setPadding(0,dp(8),0,0);info.addView(offer);TextView total=text("Totale da verificare su Vinted",14,TEXT,Typeface.BOLD);info.addView(total);main.addView(info,new LinearLayout.LayoutParams(0,-2,1));card.addView(main);TextView hint=text("Il totale finale dipende dalla spedizione del bundle calcolata da Vinted.",13,MUTED,Typeface.BOLD);hint.setPadding(0,dp(9),0,0);card.addView(hint);
        frame.addView(card,new FrameLayout.LayoutParams(-1,-1));frame.setOnClickListener(v->openBundleDetail(d));LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(dp(364),dp(350));fp.rightMargin=dp(12);frame.setLayoutParams(fp);return frame;
    }

    private void addDealRail(String icon,String title,String sub,String preset,List<DealRecord> list,int max){if(list==null||list.isEmpty())return;sectionHeader(icon,title,sub,"Vedi tutti",preset);HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(this);row.setPadding(0,dp(2),dp(18),dp(6));for(int i=0;i<Math.min(max,list.size());i++)row.addView(tileCardV51(list.get(i)));hs.addView(row);body.addView(hs);}
    private void addBundleRail(String icon,String title,String sub,String preset,List<DealRecord> list,int max){if(list==null||list.isEmpty())return;sectionHeader(icon,title,sub,"Vedi tutti",preset);HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(this);row.setPadding(0,dp(2),dp(18),dp(6));for(int i=0;i<Math.min(max,list.size());i++)row.addView(bundleTileV51(list.get(i)));hs.addView(row);body.addView(hs);}
    private View tileCardV51(DealRecord d){
        LinearLayout c=verticalCard();c.setPadding(dp(8),dp(8),dp(8),dp(10));c.setBackground(round(SURFACE,18,0,0));c.setElevation(0);
        c.addView(dealArtworkView(d,dp(170),dp(168)),new LinearLayout.LayoutParams(-1,dp(168)));
        TextView n=text(name(d),16,TEXT,Typeface.BOLD);n.setMaxLines(2);n.setEllipsize(TextUtils.TruncateAt.END);n.setPadding(dp(2),dp(9),dp(2),0);c.addView(n,new LinearLayout.LayoutParams(-1,dp(50)));
        String meta=(d.rating==null?"BGG n/d":"★ "+String.format(Locale.ITALY,"%.1f",d.rating))+" · "+languageShort(d.languageCode);TextView m=text(meta,11,MUTED,Typeface.NORMAL);m.setSingleLine(true);m.setEllipsize(TextUtils.TruncateAt.END);m.setPadding(dp(2),0,dp(2),0);c.addView(m);
        LinearLayout price=new LinearLayout(this);price.setGravity(Gravity.CENTER_VERTICAL);price.setPadding(dp(2),dp(6),dp(2),0);price.addView(text(total(d),20,TEXT,Typeface.BOLD));Integer sv=saving(d);if(sv!=null)price.addView(text("  −"+sv+"%",12,LIME,Typeface.BOLD));c.addView(price);
        c.setOnClickListener(v->openDetail(d));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(186),dp(286));lp.rightMargin=dp(10);c.setLayoutParams(lp);return c;
    }
    private View scoreView(DealRecord d){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);TextView art=appIcon(LudoIcons.STAR,16,YELLOW);art.setGravity(Gravity.CENTER);r.addView(art,new LinearLayout.LayoutParams(dp(26),dp(40)));r.addView(text(scoreLabel(d),15,TEXT,Typeface.BOLD),new LinearLayout.LayoutParams(0,-2,1));TextView info=appIcon(LudoIcons.INFO,18,CYAN);info.setGravity(Gravity.CENTER);info.setContentDescription("Dettagli valutazione e dati BGG");info.setOnClickListener(v->showScoreInfo(d));r.addView(info,new LinearLayout.LayoutParams(dp(44),dp(44)));return r;}
    private void showScoreInfo(DealRecord d){Dialog dialog=bottomSheet("Valutazione e fonti");LinearLayout box=dialog.findViewById(SHEET_ID);box.addView(text("Valutazione complessiva: "+scoreLabel(d),19,TEXT,Typeface.BOLD));box.addView(text("Formula originale: classifica BGG 55%, Geek Rating 20%, media BGG 10%, numero di votanti 15%. I segnali sono calibrati; con dati mancanti i pesi vengono ridistribuiti. Senza classifica il punteggio è limitato a 6,9/10. Prezzo e convenienza restano separati.",14,MUTED,Typeface.NORMAL));box.addView(kv("Media BGG",d.rating==null?"Non disponibile":String.format(Locale.ITALY,"%.2f / 10",d.rating)));box.addView(kv("Votanti",d.voters==null?"Non disponibili":String.format(Locale.ITALY,"%,d",d.voters)));box.addView(kv("Classifica BGG",d.rank==null?"Non disponibile":"#"+d.rank));box.addView(text(whyLine(d),14,MUTED,Typeface.NORMAL));dialog.show();}
    private View bundleTileV51(DealRecord d){List<BundleSuggestion> items=bundleDb.forSource(d.signature,60);List<DealRecord> games=bundleDealsForSource(d);if(items.isEmpty()||games.size()<2)return tileCardV51(d);LinearLayout card=verticalCard();int accent=bundleAccent(games);BundlePlan plan=bundlePlan(games);card.setBackground(round(detailSurface(accent),22,1,accent));card.setPadding(dp(12),dp(12),dp(12),dp(12));card.addView(bundleStack(d,items),new LinearLayout.LayoutParams(-1,dp(160)));TextView title=text(bundleTitle(games),20,TEXT,Typeface.BOLD);title.setMaxLines(2);card.addView(title);TextView offer=text("Offerta da fare · "+money(plan.offerSubtotal),16,LIME,Typeface.BOLD);offer.setPadding(0,dp(10),0,0);setStartIcon(offer,R.drawable.ic_deal_bolt,LIME,18);card.addView(offer);TextView total=text("Totale da verificare",12,TEXT,Typeface.BOLD);setStartIcon(total,R.drawable.ic_nav_bundle,TEXT,14);card.addView(total);card.setOnClickListener(v->openBundleDetail(d));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(230),-2);lp.rightMargin=dp(12);card.setLayoutParams(lp);return card;}
    private View bundleStack(DealRecord source,List<BundleSuggestion> items){FrameLayout stack=new FrameLayout(this);stack.setClipChildren(false);stack.setClipToPadding(false);int shown=Math.min(3,items.size()+1);for(int i=shown-1;i>=0;i--){ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.FIT_CENTER);cover.setAdjustViewBounds(true);cover.setElevation(dp(2+i));if(i==0)setBggArtwork(cover,source);else{BundleSuggestion item=items.get(i-1);File cached=TextUtils.isEmpty(item.bggId)?null:ArtworkStore.bggFile(this,item.bggId);if(cached!=null&&cached.exists())cover.setImageBitmap(decodeLocalBitmap(cached,220,320));else if(!TextUtils.isEmpty(item.imageUrl))loadRemote(cover,item.imageUrl);}FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(92),dp(138),Gravity.CENTER);lp.leftMargin=dp((i-1)*34);lp.topMargin=dp(Math.abs(i-1)*5);cover.setRotation((i-1)*11);stack.addView(cover,lp);}return stack;}
private void sectionHeader(String icon,String title,String sub,String action,String preset){
        LinearLayout outer=new LinearLayout(this);outer.setOrientation(LinearLayout.VERTICAL);outer.setPadding(0,dp(30),0,dp(10));LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.BOTTOM);
        LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);tx.addView(text(title,21,TEXT,Typeface.BOLD));if(!TextUtils.isEmpty(sub)){TextView s=text(sub,12,MUTED,Typeface.NORMAL);s.setMaxLines(2);s.setPadding(0,dp(3),0,0);tx.addView(s);}row.addView(tx,new LinearLayout.LayoutParams(0,-2,1));
        if(action!=null){TextView a=text(action,12,CYAN,Typeface.BOLD);a.setGravity(Gravity.BOTTOM|Gravity.END);a.setPadding(dp(12),dp(10),0,dp(2));a.setOnClickListener(v->{if("bundle".equals(preset))navigate("bundles");else openCatalogPreset(preset);});row.addView(a);}
        outer.addView(row);body.addView(outer);
    }
    private void openCatalogPreset(String preset){String requested=TextUtils.isEmpty(preset)?"all":preset;urgentSnapshot=null;if("recent_hot".equals(requested)){urgentSnapshot=lastUrgentIds==null?new HashSet<>():new HashSet<>(lastUrgentIds);if(lastUrgentIds==null)for(DealRecord d:urgentDeals(db.getDeals("all",320)))urgentSnapshot.add(d.signature);}catalogPreset="recent_hot".equals(requested)?"recent_hot":"bundle".equals(requested)?"bundle":"all";filterMode="all";languageFilter=linkFilter=typeFilter="all";catalogTextDependence="all";catalogCategory=-1;catalogMinRatingFilter=null;catalogMinDiscountFilter=null;maxPriceFilter=null;filterShipping=filterBundle=filterVerify=false;query="";if("recent_hot".equals(requested)||"recent".equals(requested))sort="recent";else if("top_rated".equals(requested))sort="bgg";else if("cheap".equals(requested))sort="price";else if("deal".equals(requested))sort="deal";else sort="relevance";openingPreset=true;try{navigate("catalog");}finally{openingPreset=false;}}


    // ---------- CATALOGO: utility-first ----------
private void renderDatabase(){
        addMarketHeader("database");
        LinearLayout searchBox=new LinearLayout(this);searchBox.setGravity(Gravity.CENTER_VERTICAL);searchBox.setPadding(dp(14),0,dp(8),0);searchBox.setBackground(round(DISCOVER_SURFACE,999,1,DISCOVER_OUTLINE));TextView si=appIcon(LudoIcons.SEARCH,15,MUTED);searchBox.addView(si,new LinearLayout.LayoutParams(dp(34),dp(34)));EditText search=new EditText(this);search.setSingleLine(true);search.setHint("Cerca titolo, tag, meccanica…");search.setHintTextColor(MUTED);search.setTextColor(TEXT);search.setTextSize(16);search.setText(databaseQuery);search.setSelection(search.length());search.setBackgroundColor(Color.TRANSPARENT);search.setPadding(dp(6),0,dp(4),0);searchBox.addView(search,new LinearLayout.LayoutParams(0,-1,1));TextView clear=appIcon(LudoIcons.XMARK,15,MUTED);clear.setGravity(Gravity.CENTER);clear.setVisibility(databaseQuery.isEmpty()?View.GONE:View.VISIBLE);clear.setOnClickListener(v->{databaseQuery="";databaseVisible=24;render();});searchBox.addView(clear,new LinearLayout.LayoutParams(dp(38),dp(38)));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(54));sp.topMargin=dp(8);sp.bottomMargin=dp(12);

        TextView advanced=appIcon(LudoIcons.SLIDERS,19,TEXT);advanced.setBackground(round(SURFACE,999,1,OUTLINE));advanced.setContentDescription("Filtri giochi · "+databaseAdvancedFilterCount()+" attivi");advanced.setOnClickListener(v->showDatabaseFilterSheet());FrameLayout filter=new FrameLayout(this);filter.addView(advanced,new FrameLayout.LayoutParams(-1,-1));int activeFilters=databaseAdvancedFilterCount();if(activeFilters>0){TextView count=text(String.valueOf(activeFilters),10,BG,Typeface.BOLD);count.setGravity(Gravity.CENTER);count.setBackground(round(CYAN,999,0,0));filter.addView(count,new FrameLayout.LayoutParams(dp(18),dp(18),Gravity.TOP|Gravity.END));}filter.setContentDescription("Filtri giochi · "+activeFilters+" attivi");filter.setOnClickListener(v->showDatabaseFilterSheet());LinearLayout searchRow=new LinearLayout(this);searchRow.setGravity(Gravity.CENTER_VERTICAL);searchRow.addView(searchBox,new LinearLayout.LayoutParams(0,-1,1));LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(dp(54),dp(54));fp.leftMargin=dp(10);searchRow.addView(filter,fp);body.addView(searchRow,sp);

        LinearLayout selected=new LinearLayout(this);selected.setGravity(Gravity.CENTER_VERTICAL);if(databaseCategory>=0)addFilterChip(selected,DiscoverCategories.labels()[databaseCategory],()->databaseCategory=-1);if(!"verified".equals(databaseScope))addFilterChip(selected,"review".equals(databaseScope)?"Da verificare":"Tutti",()->databaseScope="verified");if(databaseActiveOnly)addFilterChip(selected,"Con annunci",()->databaseActiveOnly=false);if(databaseMinRating!=null)addFilterChip(selected,"BGG "+databaseMinRating.intValue()+"+",()->databaseMinRating=null);if(databaseMaxPrice!=null)addFilterChip(selected,"≤ "+money(databaseMaxPrice),()->databaseMaxPrice=null);if(selected.getChildCount()>0){HorizontalScrollView active=new HorizontalScrollView(this);active.setHorizontalScrollBarEnabled(false);active.addView(selected);LinearLayout.LayoutParams alp=new LinearLayout.LayoutParams(-1,dp(48));alp.topMargin=dp(8);body.addView(active,alp);}

        LinearLayout results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);databaseResultsHost=results;LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(18);body.addView(results,rp);renderDatabaseResults(results,databaseQuery);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){}public void afterTextChanged(Editable e){databaseQuery=e.toString();databaseVisible=24;String requested=databaseQuery;uiUpdates.removeCallbacks(deferredRender);uiUpdates.postDelayed(()->{if(requested.equals(databaseQuery)&&results.getParent()!=null)renderDatabaseResults(results,requested);},180);}});
    }

    private String databaseSortLabel(){if("rating".equals(databaseSort))return"Voto BGG";if("price".equals(databaseSort))return"Prezzo più basso";if("recent".equals(databaseSort))return"Ultimi trovati";return"A–Z";}
    private int databaseAdvancedFilterCount(){int n=databaseCategory>=0?1:0;if(!"verified".equals(databaseScope))n++;if(databaseActiveOnly)n++;if(databaseMinRating!=null)n++;if(databaseMaxPrice!=null)n++;return n;}
    private void showDatabaseSortMenu(){Dialog dialog=bottomSheet("Ordina giochi");LinearLayout box=dialog.findViewById(SHEET_ID);String[] labels={"A–Z","Voto BGG","Prezzo più basso","Ultimi trovati"};String[] keys={"alpha","rating","price","recent"};for(int i=0;i<labels.length;i++){final String key=keys[i];TextView row=text((key.equals(databaseSort)?"✓  ":"")+labels[i],16,key.equals(databaseSort)?CYAN:TEXT,Typeface.BOLD);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinHeight(dp(54));row.setOnClickListener(v->{databaseSort=key;databaseVisible=24;dialog.dismiss();render();});box.addView(row);}dialog.show();}
    private void showDatabaseFilterSheet(){
        GameFilterDraft draft=new GameFilterDraft(databaseScope,databaseMinRating,databaseActiveOnly,databaseMaxPrice);draft.category=databaseCategory;Dialog dialog=fullScreenPanel("Filtri giochi","Azzera",()->{});LinearLayout box=dialog.findViewById(SHEET_ID);final Runnable[] refresh={null};
        refresh[0]=()->{box.removeAllViews();
            String[] categoryLabels=new String[9],categoryKeys=new String[9];categoryLabels[0]="Tutte";categoryKeys[0]="-1";for(int i=0;i<8;i++){categoryLabels[i+1]=DiscoverCategories.labels()[i];categoryKeys[i+1]=String.valueOf(i);}
            box.addView(filterRow("Categoria",draft.category<0?"Tutte":DiscoverCategories.labels()[draft.category],()->showChoicePage("Categoria",categoryLabels,categoryKeys,String.valueOf(draft.category),v->{draft.category=Integer.parseInt(v);refresh[0].run();})));
            box.addView(filterRow("Stato",filterGameScopeSummary(draft.scope),()->showChoicePage("Stato",new String[]{"Verificati","Da verificare","Tutti"},new String[]{"verified","review","all"},draft.scope,v->{draft.scope=v;refresh[0].run();})));
            box.addView(filterRow("Voto BGG",filterRatingSummary(draft.rating),()->showChoicePage("Voto BGG",new String[]{"Qualsiasi","6+","7+","8+","9+"},new String[]{"all","6","7","8","9"},draft.rating==null?"all":String.valueOf(draft.rating.intValue()),v->{draft.rating="all".equals(v)?null:Double.valueOf(v);refresh[0].run();})));
            box.addView(filterRow("Annunci",draft.active?"Solo con annunci attivi":"Qualsiasi",()->showChoicePage("Annunci",new String[]{"Qualsiasi","Solo con annunci attivi"},new String[]{"all","active"},draft.active?"active":"all",v->{draft.active="active".equals(v);refresh[0].run();})));
            box.addView(filterRow("Prezzo",draft.maxPrice==null?"Qualsiasi":"Fino a "+money(draft.maxPrice),()->showPricePage("Prezzo massimo Vinted",draft.maxPrice,v->{draft.maxPrice=v;refresh[0].run();})));
            LinearLayout footer=new LinearLayout(this);footer.setPadding(0,dp(26),0,dp(12));Button apply=button("Mostra risultati",LIME);apply.setOnClickListener(v->{databaseCategory=draft.category;databaseScope=draft.scope;databaseMinRating=draft.rating;databaseActiveOnly=draft.active;databaseMaxPrice=draft.maxPrice;databaseVisible=24;dialog.dismiss();render();});footer.addView(apply,new LinearLayout.LayoutParams(-1,dp(52)));box.addView(footer);
        };
        refresh[0].run();setFullScreenPanelAction(dialog,()->{draft.reset();refresh[0].run();});dialog.show();
    }

    private void loadMoreDatabase(){
        if(databaseResultsHost==null||databaseLoadingMore)return;int total=marketStore.countVisibleGamesAdvanced(databaseQuery,databaseScope,databaseActiveOnly,databaseMinRating,databaseMaxPrice,databaseCategory);if(databaseVisible>=total)return;databaseLoadingMore=true;int old=databaseVisible,next=Math.min(total,old+24);View loader=loadingMoreView("Carico altri "+(next-old)+" giochi…");loader.setTag("load-more-footer");removeLoadMoreHint(databaseResultsHost);databaseResultsHost.addView(loader);
        uiUpdates.postDelayed(()->{try{if(databaseResultsHost==null||loader.getParent()!=databaseResultsHost)return;List<GameRecord> games=marketStore.searchGamesAdvanced(databaseQuery,next,databaseScope,databaseActiveOnly,databaseMinRating,databaseMaxPrice,databaseSort,databaseCategory);databaseResultsHost.removeView(loader);addDatabaseGrid(databaseResultsHost,games,old);databaseVisible=next;if(next<total)addLoadMoreHint(databaseResultsHost,"Scorri per caricare altri "+(total-next)+" giochi…");}finally{databaseLoadingMore=false;}},80);
    }

    private View loadingMoreView(String label){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);row.setPadding(0,dp(16),0,dp(18));ProgressBar p=new ProgressBar(this);p.setIndeterminate(true);row.addView(p,new LinearLayout.LayoutParams(dp(28),dp(28)));TextView t=text(label,12,MUTED,Typeface.NORMAL);t.setPadding(dp(10),0,0,0);row.addView(t);return row;}
    private void addLoadMoreHint(LinearLayout host,String label){TextView more=text(label,12,MUTED,Typeface.NORMAL);more.setTag("load-more-hint");more.setGravity(Gravity.CENTER);more.setPadding(0,dp(12),0,dp(20));host.addView(more);}
    private void removeLoadMoreHint(LinearLayout host){if(host==null)return;for(int i=host.getChildCount()-1;i>=0;i--){View v=host.getChildAt(i);if("load-more-hint".equals(v.getTag())||"load-more-footer".equals(v.getTag()))host.removeViewAt(i);}}

    private void renderDatabaseResults(LinearLayout host,String q){
        host.removeAllViews();
        try{
            int total=marketStore.countVisibleGamesAdvanced(q,databaseScope,databaseActiveOnly,databaseMinRating,databaseMaxPrice,databaseCategory);List<GameRecord> games=marketStore.searchGamesAdvanced(q,databaseVisible,databaseScope,databaseActiveOnly,databaseMinRating,databaseMaxPrice,databaseSort,databaseCategory);
            if(games.isEmpty()){host.addView(emptyState(TextUtils.isEmpty(q)?"Nessun gioco in questa vista":"Nessun risultato",TextUtils.isEmpty(q)?("review".equals(databaseScope)?"Non ci sono giochi da verificare con questi filtri.":"Prova a modificare i filtri avanzati."):"Prova un alias o un titolo diverso.",R.drawable.ludo_value_book));return;}
            String scopeLabel="review".equals(databaseScope)?"da verificare":"all".equals(databaseScope)?"totali":"verificati";TextView count=text(total+" "+(total==1?"gioco":"giochi")+" · "+scopeLabel,12,MUTED,Typeface.BOLD);count.setPadding(0,0,0,dp(8));LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.addView(count,new LinearLayout.LayoutParams(0,dp(44),1));TextView order=text(databaseSortLabel()+"  ↓",12,TEXT,Typeface.NORMAL);order.setMinHeight(dp(44));order.setGravity(Gravity.CENTER);order.setOnClickListener(v->showDatabaseSortMenu());header.addView(order);host.addView(header);
            addDatabaseGrid(host,games,0);
            if(games.size()<total)addLoadMoreHint(host,"Scorri per caricare altri "+(total-games.size())+" giochi…");
        }catch(Throwable t){
            String detail=t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage());getSharedPreferences("va_v3_diag",MODE_PRIVATE).edit().putString("databaseLastError",detail).apply();
            host.addView(emptyState("Database non disponibile","Riprova tra poco.",R.drawable.ludo_refresh));
        }
    }

    private void addDatabaseGrid(LinearLayout host,List<GameRecord> games,int start){
        for(int i=start;i<games.size();i+=2){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.TOP);row.addView(databaseGameCard(games.get(i)),new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams second=new LinearLayout.LayoutParams(0,-2,1);second.leftMargin=dp(14);row.addView(i+1<games.size()?databaseGameCard(games.get(i+1)):new Space(this),second);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=dp(14);host.addView(row,rp);}
    }
    private View databaseGameCard(GameRecord g){
        LinearLayout card=verticalCard();card.setPadding(dp(11),dp(11),dp(11),dp(14));
        ImageView art=new ImageView(this);art.setScaleType(ImageView.ScaleType.FIT_CENTER);art.setBackground(round(SURFACE2,12,0,0));setGameArtwork(art,g);int width=(getResources().getDisplayMetrics().widthPixels-dp(50))/2-dp(22);card.addView(art,new LinearLayout.LayoutParams(-1,(int)(width*1.18f)));
        TextView title=text(g.name,17,TEXT,Typeface.BOLD);title.setLines(2);title.setEllipsize(TextUtils.TruncateAt.END);title.setPadding(0,dp(10),0,0);card.addView(title);
        TextView rating=text(g.rating==null?"BGG da verificare":"★ "+String.format(Locale.ITALY,"%.1f",g.rating)+"  BGG",13,g.rating==null?MUTED:YELLOW,Typeface.BOLD);rating.setPadding(0,dp(7),0,0);card.addView(rating);
        String players=g.minPlayers==null?"Giocatori n/d":g.minPlayers+(g.maxPlayers!=null&&!g.maxPlayers.equals(g.minPlayers)?"–"+g.maxPlayers:"")+" gioc.";TextView facts=text(players+(g.playtime==null?"":" · "+g.playtime+" min"),11,MUTED,Typeface.NORMAL);facts.setSingleLine(true);facts.setEllipsize(TextUtils.TruncateAt.END);facts.setPadding(0,dp(5),0,0);card.addView(facts);
        TextView price=text(g.currentMinPriceCents==null?"Nessun annuncio":"da "+money(g.currentMinPriceCents),g.currentMinPriceCents==null?13:19,g.currentMinPriceCents==null?MUTED:TEXT,Typeface.BOLD);price.setMinHeight(dp(38));price.setGravity(Gravity.CENTER_VERTICAL);card.addView(price);
        TextView availability=text(g.activeListingCount>0?g.activeListingCount+" annunci attivi":"Scopri il gioco",11,g.activeListingCount>0?TEAL:MUTED,Typeface.NORMAL);availability.setSingleLine(true);card.addView(availability);card.setOnClickListener(v->openDatabaseGame(g.id,null));return card;
    }

    private void renderDatabaseDetail(){
        GameRecord g=marketStore.gameStats(selectedGameId);if(g==null){selectedGameId=0;renderDatabase();return;}body.setBackground(productPageBackground());renderDatabaseDetailInto(body,g,this::closeDatabaseGame);
    }

    private void renderDatabaseDetailInto(LinearLayout host,GameRecord g,Runnable backAction){renderDatabaseDetailInto(host,g,backAction,null);}
    private void renderDatabaseDetailInto(LinearLayout host,GameRecord g,Runnable backAction,GameDetailData snapshot){
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);TextView back=appIcon(LudoIcons.CHEVRON_LEFT,24,TEXT);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->backAction.run());toolbar.addView(back,new LinearLayout.LayoutParams(dp(44),dp(52)));toolbar.addView(text("Gioco",17,TEXT,Typeface.BOLD),new LinearLayout.LayoutParams(0,-2,1));TextView help=roundIconButton("?",CYAN);help.setContentDescription("Correggi associazione BGG");help.setOnClickListener(v->chooseManualBggMatch(g));toolbar.addView(help,new LinearLayout.LayoutParams(dp(40),dp(40)));host.addView(toolbar);

        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);hero.setPadding(0,dp(8),0,dp(8));hero.addView(productBoxArtwork(g.bggId,TextUtils.isEmpty(g.imageUrl)?g.thumbnailUrl:g.imageUrl,g.name),new LinearLayout.LayoutParams(-1,productArtworkHeight()));LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(0,dp(12),0,0);TextView name=text(g.name,27,TEXT,Typeface.BOLD);name.setMaxLines(3);copy.addView(name);
        Integer composite=snapshot==null?gameQualityScore(g):snapshot.quality;copy.addView(productRatingRow(g.rating,composite==null?"":String.format(Locale.ITALY,"%.1f",composite/10.0),()->{if(!TextUtils.isEmpty(g.bggId))openBgg(g.bggId);},()->{if(composite!=null)showGameScoreInfo(g,composite);}));
        String rawMeta=(g.year==null?"Anno n/d":String.valueOf(g.year))+(g.rank==null||g.rank<=0?"":" · #"+g.rank+" BGG");TextView meta=text(rawMeta,12,MUTED,Typeface.NORMAL);meta.setPadding(0,dp(7),0,0);copy.addView(meta);
        if(!TextUtils.isEmpty(g.bggId)){DealRecord preference=new DealRecord();preference.bggId=g.bggId;preference.gameName=g.name;preference.signature="game:"+g.bggId;copy.addView(productInterestControls(preference));}
        if(!TextUtils.isEmpty(g.bggId)){View bgg=providerLinkCard(R.drawable.provider_bgg_logo,"BoardGameGeek","Apri scheda",BGG_BG,false);bgg.setOnClickListener(v->openBgg(g.bggId));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=dp(12);copy.addView(bgg,bp);}hero.addView(copy,new LinearLayout.LayoutParams(-1,-2));host.addView(hero);

        LinearLayout quick=gameFactChips(g);if(quick.getChildCount()>0){quick.setPadding(0,dp(12),0,0);host.addView(quick);}
        addLinkedMetaSection(host,"Categorie",g.categories,10);addLinkedMetaSection(host,"Meccaniche",g.mechanics,12);
        String summary=italianGameSummary(g);if(!TextUtils.isEmpty(summary)){TextView section=text("In breve",19,TEXT,Typeface.BOLD);section.setPadding(0,dp(26),0,dp(9));host.addView(section);TextView desc=text(summary,14,MUTED,Typeface.NORMAL);desc.setLineSpacing(0,1.14f);host.addView(desc);}
        if(!TextUtils.isEmpty(g.designers))addLinkedMetaSection(host,"Designer",g.designers,6);if(!TextUtils.isEmpty(g.publishers))addLinkedMetaSection(host,"Editori",g.publishers,6);

        List<GameRecord> similar=snapshot==null?similarGames(g,8):snapshot.similar;if(!similar.isEmpty()){TextView st=text("Giochi simili",20,TEXT,Typeface.BOLD);st.setPadding(0,dp(30),0,dp(10));host.addView(st);TextView ss=text("Dal database Ludo, in base a categorie e meccaniche condivise.",12,MUTED,Typeface.NORMAL);ss.setPadding(0,0,0,dp(10));host.addView(ss);HorizontalScrollView sh=new HorizontalScrollView(this);sh.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(this);row.setPadding(0,0,dp(16),dp(4));for(GameRecord x:similar)row.addView(similarGameCard(x));sh.addView(row);host.addView(sh);}

        TextView pricesTitle=text("Mercato",21,TEXT,Typeface.BOLD);pricesTitle.setPadding(0,dp(30),0,dp(10));host.addView(pricesTitle);Integer bggUsed=snapshot==null?(TextUtils.isEmpty(g.bggId)?null:bggSearch.localMarketReferenceCents(g.bggId)):snapshot.bggUsed;Integer bggNew=snapshot==null?(TextUtils.isEmpty(g.bggId)?null:bggSearch.localNewMarketCents(g.bggId)):snapshot.bggNew;MarketStore.BggMarketStats bggOnline=snapshot==null?(TextUtils.isEmpty(g.bggId)?new MarketStore.BggMarketStats(0,null,null,0):marketStore.bggMarketStats(g.bggId)):snapshot.bggOnline;MarketStore.MarketReferenceStats vintedMarket=snapshot==null?(TextUtils.isEmpty(g.bggId)?new MarketStore.MarketReferenceStats(0,null,null):marketStore.localVintedReferenceStats(g.bggId,null,marketStore.marketLanguagePolicyForGame(g.id))):snapshot.vintedMarket;Integer typicalVinted=vintedMarket.count>=3?vintedMarket.typicalCents:null;LinearLayout stats=new LinearLayout(this);stats.setWeightSum(2);stats.addView(marketStat("Storico Vinted",priceOrDash(typicalVinted)),new LinearLayout.LayoutParams(0,dp(88),1));LinearLayout.LayoutParams histLp=new LinearLayout.LayoutParams(0,dp(88),1);histLp.leftMargin=dp(8);stats.addView(marketStat("Min osservato",priceOrDash(vintedMarket.minCents)),histLp);host.addView(stats);TextView safeMode=text("I prezzi Vinted restano storico osservato. Per ora non decidono se un annuncio è conveniente.",12,MUTED,Typeface.NORMAL);safeMode.setPadding(0,dp(8),0,0);host.addView(safeMode);LinearLayout stats2=new LinearLayout(this);stats2.setWeightSum(2);stats2.setPadding(0,dp(8),0,0);Integer currentBgg=bggOnline.fresh(System.currentTimeMillis())&&bggOnline.count>0?bggOnline.typicalCents:bggUsed;stats2.addView(marketStat("Usato BGG",priceOrDash(currentBgg)),new LinearLayout.LayoutParams(0,dp(88),1));LinearLayout.LayoutParams newLp=new LinearLayout.LayoutParams(0,dp(88),1);newLp.leftMargin=dp(8);stats2.addView(marketStat("Nuovo BGG",priceOrDash(bggNew)),newLp);host.addView(stats2);
        View compare=providerLinkCard(R.drawable.provider_vinted_logo,"Vinted","Confronta prezzi",VINTED_BG,true);compare.setOnClickListener(v->compareOnVinted(g));LinearLayout.LayoutParams cmp=new LinearLayout.LayoutParams(-1,dp(62));cmp.topMargin=dp(12);host.addView(compare,cmp);
        TextView hist=text(g.observationCount+" osservazioni · "+g.listingCount+" annunci incontrati",12,MUTED,Typeface.NORMAL);hist.setPadding(0,dp(10),0,0);host.addView(hist);
        List<MarketStore.PricePoint> series=snapshot==null?marketStore.priceHistory(g.id,90):snapshot.series;if(series.size()>=2){TextView chartTitle=text("Storico prezzi",17,TEXT,Typeface.BOLD);chartTitle.setPadding(0,dp(20),0,dp(8));host.addView(chartTitle);PriceHistoryView graph=new PriceHistoryView(this,series);graph.setBackground(round(SURFACE,18,1,OUTLINE));host.addView(graph,new LinearLayout.LayoutParams(-1,dp(180)));}

        List<MarketListingRecord> active=snapshot==null?marketStore.listingsForGame(g.id,true,80):snapshot.active;LinearLayout listHead=new LinearLayout(this);listHead.setGravity(Gravity.CENTER_VERTICAL);listHead.setPadding(0,dp(30),0,dp(10));listHead.addView(text("Annunci di questo gioco",20,TEXT,Typeface.BOLD),new LinearLayout.LayoutParams(0,-2,1));listHead.addView(text(active.size()+" attivi",12,MUTED,Typeface.NORMAL));host.addView(listHead);
        if(active.isEmpty()){LinearLayout empty=verticalCard();empty.setPadding(dp(16),dp(16),dp(16),dp(16));empty.addView(text("Nessun annuncio attivo osservato",16,TEXT,Typeface.BOLD));empty.addView(text("La scheda gioco resta disponibile con storico, tag e giochi simili.",13,MUTED,Typeface.NORMAL));host.addView(empty);}else for(MarketListingRecord l:active)host.addView(snapshot==null?marketListingCard(g,l):marketListingCard(g,l,snapshot.listingScores.get(l)));
    }


    private void openGameDetailOverlay(long gameId){
        if(gameId<=0)return;if(activeGameOverlay!=null&&activeGameOverlay.isShowing())activeGameOverlay.dismiss();Dialog dialog=new Dialog(this,android.R.style.Theme_Material_NoActionBar);activeGameOverlay=dialog;ScrollView sc=new ScrollView(this);sc.setBackground(productPageBackground());LinearLayout host=new LinearLayout(this);host.setOrientation(LinearLayout.VERTICAL);host.setPadding(dp(20),dp(6),dp(20),dp(28));sc.addView(host);
        LinearLayout loading=new LinearLayout(this);loading.setGravity(Gravity.CENTER_VERTICAL);ProgressBar p=new ProgressBar(this);loading.addView(p,new LinearLayout.LayoutParams(dp(28),dp(28)));TextView t=text("Apro il gioco…",14,MUTED,Typeface.BOLD);t.setPadding(dp(12),0,0,0);loading.addView(t);host.addView(loading,new LinearLayout.LayoutParams(-1,dp(72)));
        sc.setOnApplyWindowInsetsListener((view,insets)->{Rect safe=contentSafeInsets(insets);host.setPadding(dp(20)+safe.left,dp(16)+safe.top,dp(20)+safe.right,dp(24)+safe.bottom);return insets;});dialog.setOnDismissListener(v->{if(activeGameOverlay==dialog)activeGameOverlay=null;});dialog.setContentView(sc);dialog.show();sc.requestApplyInsets();Window w=dialog.getWindow();if(w!=null){w.setLayout(-1,-1);w.setStatusBarColor(BG);w.setNavigationBarColor(BG);}
        uiDataIo.execute(()->{GameRecord g=null;try{g=marketStore.gameStats(gameId);}catch(Throwable ignored){}final GameRecord ready=g;runOnUiThread(()->{if(!dialog.isShowing())return;host.removeAllViews();if(ready==null){TextView error=text("Gioco non disponibile",18,TEXT,Typeface.BOLD);host.addView(error);return;}renderDatabaseDetailInto(host,ready,dialog::dismiss);sc.post(()->sc.scrollTo(0,0));});});
    }

    private View marketListingCard(GameRecord g,MarketListingRecord l){return marketListingCard(g,l,marketStore.dealScore(g.id,l.currentPriceCents));}
    private View marketListingCard(GameRecord g,MarketListingRecord l,Integer preparedScore){LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(LinearLayout.VERTICAL);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(11),0,dp(11));ImageView im=new ImageView(this);im.setScaleType(ImageView.ScaleType.CENTER_CROP);im.setBackground(round(SURFACE2,12,0,0));if(!TextUtils.isEmpty(l.imageUrl))loadRemote(im,l.imageUrl);else setGameArtwork(im,g);row.addView(im,new LinearLayout.LayoutParams(dp(66),dp(82)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);tx.setPadding(dp(12),0,dp(8),0);TextView title=text(TextUtils.isEmpty(l.title)?g.name:l.title,15,TEXT,Typeface.BOLD);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);tx.addView(title);String meta=marketPublicationDisplay(l)+(TextUtils.isEmpty(l.languageCode)?"":" · "+languageShort(l.languageCode));tx.addView(text(meta,11,MUTED,Typeface.NORMAL));int score=preparedScore==null?0:preparedScore;String signal=dealScoreLabel(score);TextView deal=text(signal,11,score>=82?LIME:score>=68?CYAN:MUTED,Typeface.BOLD);deal.setPadding(0,dp(4),0,0);tx.addView(deal);row.addView(tx,new LinearLayout.LayoutParams(0,-2,1));LinearLayout end=new LinearLayout(this);end.setOrientation(LinearLayout.VERTICAL);end.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);TextView price=text(money(l.currentPriceCents),19,TEXT,Typeface.BOLD);price.setGravity(Gravity.END);end.addView(price);TextView chevron=appIcon(LudoIcons.CHEVRON_RIGHT,14,MUTED);chevron.setGravity(Gravity.END);end.addView(chevron);row.addView(end,new LinearLayout.LayoutParams(dp(86),-2));row.setOnClickListener(v->openDetail(dealForListing(l,g==null?0:g.id),true));wrap.addView(row,new LinearLayout.LayoutParams(-1,dp(104)));View divider=new View(this);divider.setBackgroundColor(OUTLINE);LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-1,dp(1));dl.leftMargin=dp(78);wrap.addView(divider,dl);return wrap;}

    private void compareOnVinted(GameRecord g){if(g==null||TextUtils.isEmpty(g.name))return;getSharedPreferences(PREF_VINTED_MARKET_SCAN,MODE_PRIVATE).edit().putLong("game_id",g.id).putString("bgg_id",g.bggId==null?"":g.bggId).putString("game_name",g.name).putLong("until",System.currentTimeMillis()+VINTED_MARKET_SCAN_TTL).apply();launchVintedSearch(g.name);}
    private String italianGameSummary(GameRecord g){if(g==null)return"";StringBuilder x=new StringBuilder();x.append(g.name);if(g.year!=null)x.append(" (").append(g.year).append(")");x.append(" è un gioco da tavolo");if(g.minPlayers!=null){x.append(" per ").append(g.minPlayers);if(g.maxPlayers!=null&&!g.maxPlayers.equals(g.minPlayers))x.append("–").append(g.maxPlayers);x.append(" giocatori");}if(g.playtime!=null)x.append(", circa ").append(g.playtime).append(" minuti");if(g.weight!=null)x.append(", complessità BGG ").append(String.format(Locale.ITALY,"%.1f/5",g.weight));x.append(". ");if(!TextUtils.isEmpty(g.baseGames))x.append("È un'espansione collegata a ").append(g.baseGames).append(". ");if(!TextUtils.isEmpty(g.categories))x.append("Categorie: ").append(g.categories).append(". ");if(!TextUtils.isEmpty(g.designers))x.append("Designer: ").append(g.designers).append(". ");if(!TextUtils.isEmpty(g.publishers))x.append("Editori: ").append(g.publishers).append(".");return x.toString().trim();}

    private View marketStat(String label,String value){LinearLayout c=verticalCard();c.setGravity(Gravity.CENTER);c.addView(text(value,20,TEXT,Typeface.BOLD));c.addView(text(label,11,MUTED,Typeface.BOLD));return c;}
    private void addDatabaseMeta(String label,String value){TextView t=text(label.toUpperCase(Locale.ITALY)+"  ·  "+value,12,MUTED,Typeface.NORMAL);t.setPadding(0,dp(9),0,0);body.addView(t);}
    private void setGameArtwork(ImageView im,GameRecord g){if(im==null||g==null)return;String identity="game-art:"+g.id+":"+g.bggId;im.setTag(identity);im.setImageResource(R.drawable.ludo_value_book);galleryNet.execute(()->{File f=TextUtils.isEmpty(g.bggId)?null:ArtworkStore.bggFile(this,g.bggId);Bitmap bitmap=f!=null&&f.exists()?decodeLocalBitmap(f,220,300):null;runOnUiThread(()->{if(isDestroyed()||isFinishing()||!java.util.Objects.equals(identity,im.getTag()))return;if(bitmap!=null){im.setImageBitmap(bitmap);return;}if(!TextUtils.isEmpty(g.thumbnailUrl))loadRemote(im,g.thumbnailUrl);else if(!TextUtils.isEmpty(g.imageUrl))loadRemote(im,g.imageUrl);});});}
    private String priceOrDash(Integer cents){return cents==null?"—":money(cents);}
    private String dealScoreLabel(int score){return score>=92?"Occasione rarissima":score>=82?"Ottimo prezzo":score>=68?"Buon prezzo":score<=25?"Caro rispetto allo storico":"Prezzo normale";}
    private String dateShort(long when){if(when<=0)return"n/d";return android.text.format.DateFormat.format("dd/MM/yy",when).toString();}
    private String cleanBggDescription(String d){if(TextUtils.isEmpty(d))return"";String s=android.text.Html.fromHtml(d,android.text.Html.FROM_HTML_MODE_LEGACY).toString().replaceAll("\\n{3,}","\\n\\n").trim();return s.length()>1800?s.substring(0,1800)+"…":s;}
    private void openMarketListing(MarketListingRecord l){if(l==null||TextUtils.isEmpty(l.url)){Toast.makeText(this,"Link Vinted ancora in arricchimento.",Toast.LENGTH_SHORT).show();return;}String sig=marketStore.signatureForListing(l.id);marketStore.beginOpenedVintedTarget(l.id,sig,l.title,2L*60_000L);try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(l.url)));}catch(Exception e){marketStore.clearOpenedVintedTarget(l.id);Toast.makeText(this,"Impossibile aprire Vinted.",Toast.LENGTH_SHORT).show();}}

    private DealRecord representativeDealForGame(GameRecord game){
        if(game==null||game.id<=0)return null;List<MarketListingRecord> rows=marketStore.listingsForGame(game.id,false,8);if(rows==null||rows.isEmpty())return null;MarketListingRecord chosen=rows.get(0);
        for(MarketListingRecord row:rows){String sig=marketStore.signatureForListing(row.id);if(TextUtils.isEmpty(sig))sig=row.tempFingerprint;File thumb=ThumbnailStore.fileFor(this,sig);if(thumb.exists()&&thumb.length()>1024){chosen=row;break;}}
        DealRecord d=chosen.asDealRecord(game);String sig=marketStore.signatureForListing(chosen.id);if(!TextUtils.isEmpty(sig))d.signature=sig;return d;
    }

    private DealRecord dealForListing(MarketListingRecord listing,long gameId){
        if(listing==null)return null;GameRecord game=gameId>0?marketStore.gameStats(gameId):null;DealRecord d=listing.asDealRecord(game);String sig=marketStore.signatureForListing(listing.id);if(!TextUtils.isEmpty(sig))d.signature=sig;return d;
    }

    private View observedListingPhotoView(DealRecord d,int w,int h){
        FrameLayout frame=new FrameLayout(this);frame.setBackground(round(SURFACE2,14,1,OUTLINE));frame.setClipToOutline(true);ImageView im=new ImageView(this);im.setScaleType(ImageView.ScaleType.CENTER_CROP);frame.addView(im,new FrameLayout.LayoutParams(-1,-1));boolean loaded=false;if(d!=null&&!TextUtils.isEmpty(d.signature)){File local=ThumbnailStore.fileFor(this,d.signature);if(local.exists()&&local.length()>1024){Bitmap b=decodeLocalBitmap(local,Math.max(w,320),Math.max(h,420));if(b!=null){im.setImageBitmap(b);loaded=true;}}}if(!loaded&&d!=null&&!TextUtils.isEmpty(d.imageUrl)){loadRemote(im,d.imageUrl);loaded=true;}if(!loaded){im.setImageDrawable(iconDrawable(LudoIcons.IMAGE,MUTED,24));}frame.setContentDescription("Foto osservata dell'annuncio Vinted");return frame;
    }

    private void addBggPicker(LinearLayout box,Dialog dialog,String initial,java.util.function.Consumer<BggSearchClient.Game> selected,boolean autoSearch){
        TextView help=text("Scrivi un nome oppure incolla link/ID BGG. La ricerca parte solo quando premi Cerca.",13,MUTED,Typeface.NORMAL);help.setPadding(0,dp(16),0,dp(8));box.addView(help);
        EditText query=input("Titolo, link o ID BGG");query.setText(initial==null?"":initial);query.setSelectAllOnFocus(false);box.addView(query,new LinearLayout.LayoutParams(-1,dp(56)));
        TextView state=text("",13,MUTED,Typeface.NORMAL);state.setPadding(0,dp(8),0,0);box.addView(state);ProgressBar busy=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);busy.setIndeterminate(true);busy.setVisibility(View.GONE);box.addView(busy,new LinearLayout.LayoutParams(-1,dp(4)));
        LinearLayout results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);Button search=button("Cerca su BGG",CYAN);final int[] request={0};final boolean[] running={false};
        Runnable run=()->{String raw=query.getText().toString().trim();if(raw.isEmpty()){query.setError("Inserisci titolo, link o ID BGG");return;}String id=bggIdFromInput(raw);int ticket=++request[0];running[0]=true;results.removeAllViews();busy.setVisibility(View.VISIBLE);search.setEnabled(true);search.setText(id!=null?"Apri ID / link BGG":"Cerca su BGG");state.setText(id!=null?"Apro subito il gioco indicato…":"Cerco anche titoli simili…");BggSearchClient.Callback callback=new BggSearchClient.Callback(){public void ok(List<BggSearchClient.Game> games){runOnUiThread(()->{if(!dialog.isShowing()||ticket!=request[0])return;running[0]=false;busy.setVisibility(View.GONE);search.setEnabled(true);search.setText("Cerca su BGG");List<BggSearchClient.Game> eligible=new ArrayList<>();if(games!=null)for(BggSearchClient.Game candidate:games)if(candidate!=null&&DealPolicy.queueCandidateEligible(candidate.rating,candidate.categories))eligible.add(candidate);state.setText(eligible.isEmpty()?"Nessun gioco compatibile con i filtri Ludo (BGG 6+ e non Children's Game).":"Scegli il gioco corretto.");for(BggSearchClient.Game candidate:eligible){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);results.addView(gameChoiceRow(candidate,()->{dialog.dismiss();selected.accept(candidate);}),lp);}});}public void error(String e){runOnUiThread(()->{if(dialog.isShowing()&&ticket==request[0]){running[0]=false;busy.setVisibility(View.GONE);search.setEnabled(true);search.setText("Cerca su BGG");state.setText("Nessun risultato disponibile. Puoi incollare il link o l’ID BGG esatto.");}});}};if(id!=null)bggSearch.detailsDirect(id,false,callback);else bggSearch.searchFast(raw,callback);};
        query.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){if(running[0]){request[0]++;running[0]=false;busy.setVisibility(View.GONE);state.setText("Input cambiato: premi Cerca per usare il nuovo valore.");}String id=bggIdFromInput(s==null?"":s.toString());search.setText(id!=null?"Apri ID / link BGG":"Cerca su BGG");search.setEnabled(true);}public void afterTextChanged(android.text.Editable e){}});
        search.setOnClickListener(v->run.run());box.addView(search,new LinearLayout.LayoutParams(-1,dp(48)));box.addView(results);
    }

    private void chooseManualBggMatch(GameRecord game){
        if(game==null)return;Dialog dialog=bottomSheet(TextUtils.isEmpty(game.bggId)?"Collega il gioco BGG":"Cambia gioco BGG");LinearLayout box=dialog.findViewById(SHEET_ID);
        DealRecord observed=representativeDealForGame(game);if(observed!=null){LinearLayout preview=new LinearLayout(this);preview.setGravity(Gravity.CENTER_VERTICAL);preview.setPadding(0,0,0,dp(8));preview.addView(observedListingPhotoView(observed,92,116),new LinearLayout.LayoutParams(dp(92),dp(116)));LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(12),0,0,0);TextView raw=text(TextUtils.isEmpty(observed.vintedTitle)?game.name:observed.vintedTitle,16,TEXT,Typeface.BOLD);raw.setMaxLines(3);raw.setEllipsize(TextUtils.TruncateAt.END);copy.addView(raw);if(observed.itemPriceCents>0)copy.addView(text("Vinted · "+money(observed.itemPriceCents),13,MUTED,Typeface.BOLD));copy.addView(text("Foto originale catturata durante lo scroll",12,MUTED,Typeface.NORMAL));preview.addView(copy,new LinearLayout.LayoutParams(0,-2,1));box.addView(preview);}
        addBggPicker(box,dialog,game.name,candidate->maintenanceIo.execute(()->{List<String> legacySignatures=new ArrayList<>();for(MarketListingRecord row:marketStore.listingsForGame(game.id,false,200)){String sig=marketStore.signatureForListing(row.id);if(!TextUtils.isEmpty(sig)&&!legacySignatures.contains(sig))legacySignatures.add(sig);}long canonical=marketStore.assignManualBggMatch(game.id,candidate);for(String sig:legacySignatures){DealRecord legacy=db.findBySignature(sig);if(legacy!=null){db.correctMatch(legacy,candidate);DealRecord corrected=db.findBySignature(sig);if(corrected!=null)marketStore.syncLegacyCorrection(corrected);}}if(candidate.marketUsedCount!=null&&candidate.marketUsedCount>0)marketStore.saveBggMarketStats(candidate.id,candidate.marketUsedMedianCents,candidate.marketUsedMinCents,candidate.marketUsedCount,System.currentTimeMillis());marketStore.refreshLegacyDealBenchmarks(bggSearch);runOnUiThread(()->{selectedGameId=canonical;Toast.makeText(this,"BGG collegato · Ludo riprende la card",Toast.LENGTH_SHORT).show();sendBroadcast(new Intent(OperationCenter.REFRESH_MISSING).setPackage(getPackageName()));kickActivityQueue();scheduleRender(40);});}),false);
        View notGame=wizardOption(LudoIcons.TRASH,"Non è un gioco","Eliminalo da Ludo: non influenzerà più prezzi, review o notifiche.",RED,()->excludeGameAsNonGame(game,dialog));LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=dp(14);box.addView(notGame,np);dialog.show();
    }

    private final class PriceHistoryView extends View{private final List<MarketStore.PricePoint> points;private final Paint line=new Paint(Paint.ANTI_ALIAS_FLAG),grid=new Paint(Paint.ANTI_ALIAS_FLAG),label=new Paint(Paint.ANTI_ALIAS_FLAG);PriceHistoryView(Context c,List<MarketStore.PricePoint> p){super(c);points=p;line.setColor(CYAN);line.setStrokeWidth(dp(3));line.setStyle(Paint.Style.STROKE);grid.setColor(OUTLINE);grid.setStrokeWidth(dp(1));label.setColor(MUTED);label.setTextSize(dp(11));setPadding(dp(12),dp(14),dp(12),dp(18));}protected void onDraw(Canvas c){super.onDraw(c);if(points==null||points.size()<2)return;int l=getPaddingLeft(),r=getWidth()-getPaddingRight(),t=getPaddingTop(),b=getHeight()-getPaddingBottom();int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;for(MarketStore.PricePoint p:points){min=Math.min(min,p.priceCents);max=Math.max(max,p.priceCents);}if(max==min){max+=100;min=Math.max(0,min-100);}for(int i=0;i<=3;i++){float y=t+(b-t)*i/3f;c.drawLine(l,y,r,y,grid);}Path path=new Path();for(int i=0;i<points.size();i++){float x=l+(r-l)*(i/(float)(points.size()-1));float y=b-(points.get(i).priceCents-min)/(float)(max-min)*(b-t);if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}c.drawPath(path,line);c.drawText(money(max),l,t+dp(11),label);c.drawText(money(min),l,b,label);}}

private int catalogCategory=-1;private String catalogTextDependence="all";
private void renderCatalog(){
        addMarketHeader("catalog");
        LinearLayout searchBox=new LinearLayout(this);searchBox.setGravity(Gravity.CENTER_VERTICAL);searchBox.setPadding(dp(14),0,dp(8),0);searchBox.setBackground(round(DISCOVER_SURFACE,999,1,DISCOVER_OUTLINE));TextView si=appIcon(LudoIcons.SEARCH,15,MUTED);searchBox.addView(si,new LinearLayout.LayoutParams(dp(34),dp(34)));EditText search=new EditText(this);search.setSingleLine(true);search.setHint("Cerca un gioco");search.setHintTextColor(MUTED);search.setTextColor(TEXT);search.setText(query);search.setTextSize(16);search.setBackgroundColor(Color.TRANSPARENT);search.setPadding(dp(6),0,dp(4),0);search.setOnEditorActionListener((v,a,e)->{query=v.getText().toString().trim();catalogVisible=24;render();return true;});searchBox.addView(search,new LinearLayout.LayoutParams(0,-1,1));TextView clear=appIcon(LudoIcons.XMARK,15,MUTED);clear.setGravity(Gravity.CENTER);clear.setVisibility(query.isEmpty()?View.GONE:View.VISIBLE);clear.setOnClickListener(v->{query="";render();});searchBox.addView(clear,new LinearLayout.LayoutParams(dp(38),dp(38)));LinearLayout searchRow=new LinearLayout(this);searchRow.setGravity(Gravity.CENTER_VERTICAL);searchRow.addView(searchBox,new LinearLayout.LayoutParams(0,dp(54),1));TextView filters=appIcon(LudoIcons.SLIDERS,20,TEXT);filters.setGravity(Gravity.CENTER);filters.setBackground(round(SURFACE,999,1,OUTLINE));filters.setContentDescription("Filtri catalogo · "+activeFilterCount()+" attivi");filters.setOnClickListener(v->showFilterSheet());LinearLayout.LayoutParams flp=new LinearLayout.LayoutParams(dp(54),dp(54));flp.leftMargin=dp(10);FrameLayout filterButton=new FrameLayout(this);filterButton.addView(filters,new FrameLayout.LayoutParams(-1,-1));int filterTotal=activeFilterCount();if(filterTotal>0){TextView number=discoverTextWeight(String.valueOf(filterTotal),10,Color.BLACK,700);number.setGravity(Gravity.CENTER);number.setBackground(round(CYAN,999,0,0));filterButton.addView(number,new FrameLayout.LayoutParams(dp(18),dp(18),Gravity.TOP|Gravity.END));}filterButton.setContentDescription("Filtri catalogo · "+filterTotal+" attivi");filterButton.setOnClickListener(v->showFilterSheet());searchRow.addView(filterButton,flp);body.addView(searchRow);

        HorizontalScrollView categories=new HorizontalScrollView(this);categories.setHorizontalScrollBarEnabled(false);LinearLayout tiles=new LinearLayout(this);String[] labels=DiscoverCategories.labels();
        for(int i=0;i<labels.length;i++){final int index=i;View tile=discoverCategoryTile(labels[i],i);tile.setSelected(catalogCategory==i);tile.setAlpha(catalogCategory<0||catalogCategory==i?1f:.6f);tile.setOnClickListener(v->{catalogCategory=catalogCategory==index?-1:index;catalogVisible=24;render();});tiles.addView(tile);}categories.addView(tiles);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(16);cp.bottomMargin=dp(6);body.addView(categories,cp);


        LinearLayout selectedRow=new LinearLayout(this);selectedRow.setGravity(Gravity.CENTER_VERTICAL);if(catalogCategory>=0)addFilterChip(selectedRow,DiscoverCategories.labels()[catalogCategory],()->catalogCategory=-1);if(!"all".equals(catalogTextDependence))addFilterChip(selectedRow,"IND".equals(catalogTextDependence)?"Indipendente":"DEP".equals(catalogTextDependence)?"Dipendente":"Testo non verificato",()->catalogTextDependence="all");if(catalogBatchSignatures!=null&&!catalogBatchSignatures.isEmpty()){addFilterChip(selectedRow,catalogBatchLabel,()->{catalogBatchSignatures=null;catalogBatchLabel="";});}if(!"all".equals(catalogPreset))addFilterChip(selectedRow,catalogPresetLabel(),()->{catalogPreset="all";urgentSnapshot=null;});if(catalogMinRatingFilter!=null)addFilterChip(selectedRow,"BGG "+catalogMinRatingFilter.intValue()+"+",()->catalogMinRatingFilter=null);if(catalogMinDiscountFilter!=null)addFilterChip(selectedRow,"−"+catalogMinDiscountFilter+"%+",()->catalogMinDiscountFilter=null);if(!"all".equals(languageFilter))addFilterChip(selectedRow,filterLanguageSummary(languageFilter),()->languageFilter="all");if(maxPriceFilter!=null)addFilterChip(selectedRow,"≤ "+money(maxPriceFilter),()->maxPriceFilter=null);if(filterBundle)addFilterChip(selectedRow,"Bundle",()->filterBundle=false);if(filterShipping)addFilterChip(selectedRow,"Spedizione ≤ 3 €",()->filterShipping=false);if(filterVerify)addFilterChip(selectedRow,"Da verificare",()->filterVerify=false);if(!"all".equals(linkFilter))addFilterChip(selectedRow,"Dati Vinted",()->linkFilter="all");if(!"all".equals(typeFilter))addFilterChip(selectedRow,"base".equals(typeFilter)?"Base":"Espansione",()->typeFilter="all");if(selectedRow.getChildCount()>0){HorizontalScrollView activeFilters=new HorizontalScrollView(this);activeFilters.setHorizontalScrollBarEnabled(false);activeFilters.addView(selectedRow);LinearLayout.LayoutParams afp=new LinearLayout.LayoutParams(-1,dp(48));afp.topMargin=dp(8);body.addView(activeFilters,afp);}

        // Catalog population contract: "eligible" is exactly the base query used to render this
        // surface. "visible" is that same population after the user's current view constraints.
        List<DealRecord> list=db.getDeals("trusted_any_price",800);
        final int catalogEligible=list.size();
        final int catalogStoredAll=db.countDeals(null);
        applyCatalogPreset(list);applyCatalogFilter(list);if(catalogCategory>=0)list.removeIf(d->!DiscoverCategories.matches(catalogCategory,d.bggCategories));if(catalogBatchSignatures!=null&&!catalogBatchSignatures.isEmpty())list.removeIf(d->d==null||!catalogBatchSignatures.contains(d.signature));if(!query.isEmpty()){String q=query.toLowerCase(Locale.ROOT);list.removeIf(d->!(name(d).toLowerCase(Locale.ROOT).contains(q)||(d.brand!=null&&d.brand.toLowerCase(Locale.ROOT).contains(q))));}sortDeals(list);
        getSharedPreferences("va_v3_diag",MODE_PRIVATE).edit().putInt("catalogStoredAll",catalogStoredAll).putInt("catalogEligible",catalogEligible).putInt("catalogVisible",list.size()).putString("catalogPreset",catalogPreset).apply();
        LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);titleRow.setPadding(0,dp(18),0,dp(10));titleRow.addView(text(list.size()+" annunci",15,TEXT,Typeface.BOLD),new LinearLayout.LayoutParams(0,-2,1));TextView count=text(list.size()+" di "+catalogEligible+" pronti",12,MUTED,Typeface.NORMAL);count.setGravity(Gravity.END);TextView sortControl=discoverTextWeight(sortLabel()+" ↓",12,TEXT,400);sortControl.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);sortControl.setMinHeight(dp(48));sortControl.setPadding(dp(12),0,0,0);sortControl.setOnClickListener(v->showSortMenu(sortControl));titleRow.addView(sortControl);body.addView(titleRow);
        if(list.isEmpty()){String detail=catalogEligible>0?catalogEligible+" annunci pronti nel Catalogo; questa ricerca o questi filtri non ne trovano nessuno.":"Nessun annuncio è ancora pronto per il Catalogo.";body.addView(emptyState("Nessun annuncio in questa vista",detail,R.drawable.ludo_refresh));TextView reset=secondaryTextAction("Azzera filtri");reset.setOnClickListener(v->{catalogPreset=filterMode=languageFilter=linkFilter=typeFilter="all";maxPriceFilter=null;catalogMinRatingFilter=null;catalogMinDiscountFilter=null;filterShipping=filterBundle=filterVerify=false;catalogTextDependence="all";catalogCategory=-1;query="";urgentSnapshot=null;render();});body.addView(reset);return;}
        catalogResultSnapshot=new ArrayList<>(list);LinearLayout results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);catalogResultsHost=results;body.addView(results,new LinearLayout.LayoutParams(-1,-2));appendCatalogCards(results,list,0,Math.min(catalogVisible,list.size()));if(list.size()>catalogVisible)addLoadMoreHint(results,"Scorri per caricare altri "+(list.size()-catalogVisible)+" annunci…");
    }
    private View catalogProductCard(DealRecord d){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(10),dp(10),dp(10),dp(10));card.setBackground(round(DISCOVER_SURFACE,16,1,DISCOVER_OUTLINE));card.setClipToOutline(true);
        FrameLayout artwork=(FrameLayout)discoverBggCover(d,-1,dp(148),11,false);card.addView(artwork,new LinearLayout.LayoutParams(-1,dp(148)));
        card.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int side=r-l-card.getPaddingLeft()-card.getPaddingRight();if(side>0&&artwork.getLayoutParams().height!=side)artwork.post(()->{artwork.getLayoutParams().height=side;artwork.requestLayout();});});
        String published=TextUtils.isEmpty(ageLabel(d))?"?":publicationDisplay(d);TextView date=discoverTextWeight(published,10,TEXT,600);date.setSingleLine(true);date.setEllipsize(TextUtils.TruncateAt.END);date.setGravity(Gravity.CENTER);date.setPadding(dp(8),0,dp(8),0);date.setBackground(round(Color.argb(205,10,11,20),999,0,0));date.setContentDescription("?".equals(published)?"Data di pubblicazione sconosciuta":"Pubblicato "+published);FrameLayout.LayoutParams dlp=new FrameLayout.LayoutParams(-2,dp(28),Gravity.TOP|Gravity.START);dlp.topMargin=dp(7);dlp.leftMargin=dp(7);artwork.addView(date,dlp);
        FrameLayout more=new FrameLayout(this);TextView dots=appIcon(LudoIcons.ELLIPSIS_VERTICAL,14,TEXT);dots.setGravity(Gravity.CENTER);dots.setBackground(round(Color.argb(185,10,11,20),999,0,0));dots.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);more.addView(dots,new FrameLayout.LayoutParams(dp(28),dp(28),Gravity.CENTER));more.setContentDescription("Azioni annuncio");more.setFocusable(true);more.setOnClickListener(v->showListingActions(d));FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.END);mp.topMargin=dp(1);mp.rightMargin=dp(1);artwork.addView(more,mp);
        TextView title=discoverTextWeight(name(d),16,DISCOVER_TEXT,700);title.setLines(2);title.setEllipsize(TextUtils.TruncateAt.END);title.setGravity(Gravity.TOP);title.setPadding(0,dp(10),0,0);title.setMinHeight(dp(48));card.addView(title,new LinearLayout.LayoutParams(-1,dp(48)));
        LinearLayout meta=new LinearLayout(this);meta.setGravity(Gravity.CENTER_VERTICAL);meta.setPadding(0,dp(3),0,0);
        meta.addView(appIcon(LudoIcons.STAR,13,DISCOVER_YELLOW),new LinearLayout.LayoutParams(dp(18),dp(24)));TextView rating=discoverTextWeight(d.rating==null?"BGG n/d":String.format(Locale.ITALY,"%.1f",d.rating),13,DISCOVER_TEXT,600);rating.setSingleLine(true);meta.addView(rating);
        TextView edition=discoverTextWeight(languageCompact(d.languageCode),11,DISCOVER_MUTED,600);edition.setSingleLine(true);edition.setGravity(Gravity.CENTER);edition.setPadding(dp(7),0,dp(7),0);edition.setBackground(round(SURFACE2,999,0,0));LinearLayout.LayoutParams elp=new LinearLayout.LayoutParams(-2,dp(24));elp.leftMargin=dp(6);meta.addView(edition,elp);card.addView(meta,new LinearLayout.LayoutParams(-1,dp(27)));
        LinearLayout priceRow=new LinearLayout(this);priceRow.setGravity(Gravity.CENTER_VERTICAL);priceRow.setPadding(0,dp(6),0,0);TextView price=discoverTextWeight(total(d),21,DISCOVER_TEXT,700);price.setSingleLine(true);price.setAutoSizeTextTypeUniformWithConfiguration(13,21,1,android.util.TypedValue.COMPLEX_UNIT_SP);priceRow.addView(price,new LinearLayout.LayoutParams(0,dp(34),1));
        TextView saving=discoverDiscountBadge(d,10);if(saving!=null)priceRow.addView(saving,new LinearLayout.LayoutParams(-2,dp(28)));card.addView(priceRow,new LinearLayout.LayoutParams(-1,dp(40)));
        card.setContentDescription(name(d)+", "+total(d)+", "+publicationDisplay(d));card.setOnClickListener(v->openDetail(d));return card;
    }
        private void appendCatalogCards(LinearLayout host,List<DealRecord> deals,int start,int end){
        for(int i=start;i<end;i+=2){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.TOP);
            for(int col=0;col<2;col++){int index=i+col;View card=index<end?catalogProductCard(deals.get(index)):new Space(this);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(col==1)lp.leftMargin=dp(12);row.addView(card,lp);}
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=dp(14);host.addView(row,rp);
        }
    }
    private void loadMoreCatalog(){
        if(catalogResultsHost==null||catalogLoadingMore||catalogResultSnapshot==null||catalogVisible>=catalogResultSnapshot.size())return;catalogLoadingMore=true;int old=catalogVisible,next=Math.min(catalogResultSnapshot.size(),old+24);View loader=loadingMoreView("Carico altri "+(next-old)+" annunci…");loader.setTag("load-more-footer");removeLoadMoreHint(catalogResultsHost);catalogResultsHost.addView(loader);
        uiUpdates.postDelayed(()->{try{if(catalogResultsHost==null||loader.getParent()!=catalogResultsHost)return;catalogResultsHost.removeView(loader);appendCatalogCards(catalogResultsHost,catalogResultSnapshot,old,next);catalogVisible=next;if(next<catalogResultSnapshot.size())addLoadMoreHint(catalogResultsHost,"Scorri per caricare altri "+(catalogResultSnapshot.size()-next)+" annunci…");}finally{catalogLoadingMore=false;}},80);
    }

    private void addCatalogToggleChip(LinearLayout row,String label,boolean on,Runnable toggle){TextView chip=materialChip((on?"✓ ":"")+label,on?Color.rgb(46,66,86):SURFACE2,on?TEXT:MUTED,on);chip.setBackground(round(on?Color.rgb(46,66,86):SURFACE2,999,1,on?CYAN:OUTLINE));chip.setOnClickListener(v->{toggle.run();catalogVisible=24;render();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(36));if(row.getChildCount()>0)lp.leftMargin=dp(8);row.addView(chip,lp);}
    private void addCatalogPresetChip(LinearLayout row,String label,String key){TextView chip=materialChip(label,key.equals(catalogPreset)?Color.rgb(46,66,86):SURFACE2,key.equals(catalogPreset)?TEXT:MUTED,key.equals(catalogPreset));chip.setBackground(round(key.equals(catalogPreset)?Color.rgb(46,66,86):SURFACE2,999,1,key.equals(catalogPreset)?CYAN:OUTLINE));chip.setOnClickListener(v->{catalogPreset=key;catalogVisible=24;render();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(36));if(row.getChildCount()>0)lp.leftMargin=dp(8);row.addView(chip,lp);}
    private String catalogPresetSubtitle(){if("recent_hot".equals(catalogPreset))return"Affari forti comparsi da poco.";if("bundle".equals(catalogPreset))return"Solo opportunità bundle già verificate.";if("top_rated".equals(catalogPreset))return"I giochi con i voti migliori.";if("cheap".equals(catalogPreset))return"Prima i prezzi più piccoli.";return"Tutto quello che Ludo ha riconosciuto.";}
    private String catalogPresetLabel(){if("recent_hot".equals(catalogPreset))return"Da non perdere ora";if("bundle".equals(catalogPreset))return"Bundle reali";if("top_rated".equals(catalogPreset))return"Più amati";if("cheap".equals(catalogPreset))return"Piccoli prezzi";return"Tutto";}
    private Set<String> urgentSnapshot,lastUrgentIds;
    private List<DealRecord> urgentDeals(List<DealRecord> input){List<DealRecord> result=new ArrayList<>();for(DealRecord d:input){if(!personalDealEligible(d)||!DealEvaluator.evaluate(d).discoverable())continue;if(ageMinutes(d)<=60)result.add(d);}result.sort((a,b)->Double.compare(relevance(b),relevance(a)));if(result.isEmpty()){for(DealRecord d:input)if(personalDealEligible(d)&&DealEvaluator.evaluate(d).discoverable())result.add(d);result.sort((a,b)->Double.compare(relevance(b),relevance(a)));if(result.size()>8)result=new ArrayList<>(result.subList(0,8));}return result;}
    private void applyCatalogPreset(List<DealRecord>l){if("recent_hot".equals(catalogPreset)){if(urgentSnapshot!=null)l.removeIf(d->!urgentSnapshot.contains(d.signature));else{List<DealRecord> selected=urgentDeals(l);l.clear();l.addAll(selected);}}else if("bundle".equals(catalogPreset))l.removeIf(d->!hasLiveBundle(d));}
    private void applyCatalogFilter(List<DealRecord>l){if(catalogMinRatingFilter!=null)l.removeIf(d->d.rating==null||d.rating<catalogMinRatingFilter);if(catalogMinDiscountFilter!=null)l.removeIf(d->{Integer sv=saving(d);return sv==null||sv<catalogMinDiscountFilter;});if(filterShipping)l.removeIf(d->d.shippingVerifiedCents==null||d.shippingVerifiedCents>300);if(filterBundle)l.removeIf(d->!hasLiveBundle(d));if(filterVerify)l.removeIf(d->!"verify".equals(d.tier));if(maxPriceFilter!=null)l.removeIf(d->{Integer total=effectiveTotal(d);return total==null||total>maxPriceFilter;});if(!"all".equals(languageFilter))l.removeIf(d->!HomePresentation.matchesLanguage(d.languageCode,languageFilter));if(!"all".equals(catalogTextDependence))l.removeIf(d->!HomePresentation.matchesDependence(d.languageCode,catalogTextDependence));if("linked".equals(linkFilter))l.removeIf(d->TextUtils.isEmpty(d.vintedUrl));else if("pending".equals(linkFilter))l.removeIf(d->!TextUtils.isEmpty(d.vintedUrl));else if("incomplete".equals(linkFilter))l.removeIf(d->!vintedDataIncomplete(d));else if("complete".equals(linkFilter))l.removeIf(this::vintedDataIncomplete);if(!"all".equals(typeFilter))l.removeIf(d->{String t=d.listingType==null?"":d.listingType.toLowerCase(Locale.ROOT);boolean expansion=t.contains("expan");return "expansion".equals(typeFilter)?!expansion:expansion;});}
    private boolean vintedDataIncomplete(DealRecord d){return d==null||TextUtils.isEmpty(d.vintedUrl)||TextUtils.isEmpty(d.publishedLabel)||TextUtils.isEmpty(d.sellerId);}
    private boolean vintedCoreMissing(DealRecord d){return d==null||TextUtils.isEmpty(d.vintedUrl);}
private void addFilterChip(LinearLayout row,String label,Runnable remove){TextView chip=materialChip(label+" ×",SURFACE2,TEXT,false);chip.setOnClickListener(v->{remove.run();render();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(38));lp.leftMargin=dp(6);row.addView(chip,lp);}
    private TextView marketToolbarButton(String label,int icon){TextView v=discoverTextWeight(label,14,TEXT,500);v.setGravity(Gravity.CENTER);v.setCompoundDrawablesWithIntrinsicBounds(icon,0,0,0);v.setCompoundDrawablePadding(dp(8));v.setBackground(round(SURFACE2,14,0,0));return v;}
    private TextView marketToolbarButton(String label,String glyph){TextView v=discoverTextWeight(label,14,TEXT,500);v.setGravity(Gravity.CENTER);v.setCompoundDrawables(iconDrawable(glyph,TEXT,19),null,null,null);v.setCompoundDrawablePadding(dp(8));v.setBackground(round(SURFACE2,14,0,0));return v;}
    private TextView secondaryTextAction(String label){TextView v=text(label,14,CYAN,Typeface.BOLD);v.setGravity(Gravity.CENTER);v.setMinHeight(dp(48));return v;}
    private String sortLabel(){switch(sort){case "recent":return "Ultimi scoperti";case "deal":return "Convenienza";case "rating":return "Valutazione";case "price":return "Prezzo crescente";case "price_desc":return "Prezzo decrescente";case "bgg":return "Voto BGG";default:return "Rilevanza";}}
    private int activeFilterCount(){int n=catalogCategory>=0?1:0;if(!"all".equals(catalogTextDependence))n++;if(!"all".equals(catalogPreset))n++;if(catalogMinRatingFilter!=null)n++;if(catalogMinDiscountFilter!=null)n++;if(!"all".equals(languageFilter))n++;if(maxPriceFilter!=null)n++;if(!"all".equals(linkFilter))n++;if(!"all".equals(typeFilter))n++;if(filterShipping)n++;if(filterBundle)n++;if(filterVerify)n++;return n;}
    private void showSortMenu(View anchor){Dialog dialog=bottomSheet("Ordina catalogo");LinearLayout box=dialog.findViewById(SHEET_ID);box.addView(text("Cambia solo l’ordine dei risultati: non nasconde nessun annuncio.",13,MUTED,Typeface.NORMAL));String[] labels={"Rilevanza Ludo","Più recenti","Miglior affare","Punteggio Ludo","Prezzo: basso → alto","Prezzo: alto → basso","Voto BGG"};String[] sub={"Equilibrio tra qualità, prezzo, freschezza e attrito","Prima gli annunci appena trovati","Prima il risparmio maggiore","Il nostro punteggio composito, non la sola media BGG","Prima i totali più bassi","Prima i totali più alti","Solo la media utenti di BoardGameGeek"};String[] keys={"relevance","recent","deal","rating","price","price_desc","bgg"};String[] icons={LudoIcons.COMPASS,LudoIcons.HISTORY,LudoIcons.ARROW_DOWN,LudoIcons.STAR,LudoIcons.SORT,LudoIcons.SORT,LudoIcons.STAR};for(int i=0;i<labels.length;i++){final int k=i;LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(10),dp(12),dp(10));row.setBackground(round(keys[i].equals(sort)?Color.rgb(61,42,90):SURFACE,16,keys[i].equals(sort)?1:0,CYAN));TextView ic=appIcon(icons[i],15,keys[i].equals(sort)?CYAN:MUTED);row.addView(ic,new LinearLayout.LayoutParams(dp(28),dp(28)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);tx.setPadding(dp(12),0,0,0);tx.addView(text(labels[i],16,TEXT,Typeface.BOLD));tx.addView(text(sub[i],12,MUTED,Typeface.NORMAL));row.addView(tx,new LinearLayout.LayoutParams(0,-2,1));row.setOnClickListener(v->{sort=keys[k];dialog.dismiss();render();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);box.addView(row,lp);}dialog.show();}
    private String languageFilter="all",linkFilter="all",typeFilter="all";private boolean filterShipping,filterBundle,filterVerify;private Integer maxPriceFilter=null,catalogMinDiscountFilter=null;private Double catalogMinRatingFilter=null;
    private void showLanguageMenu(View anchor){PopupMenu m=new PopupMenu(this,anchor);String[] labels={"Tutte le lingue","Italiano","Indipendente","Francese","Inglese","Tedesco","Lingua non indicata"};String[] keys={"all","IT","IND","FR","EN","DE","unknown"};for(int i=0;i<labels.length;i++)m.getMenu().add(0,i,i,labels[i]);m.setOnMenuItemClickListener(x->{languageFilter=keys[x.getItemId()];render();return true;});m.show();}
    private View sortChip(String label,String value){boolean on=value.equals(sort);TextView v=materialChip(label,on?CYAN:SURFACE2,on?BG:TEXT,on);String icon="recent".equals(value)?LudoIcons.HISTORY:"rating".equals(value)?LudoIcons.STAR:"deal".equals(value)?LudoIcons.ARROW_DOWN:LudoIcons.SORT;v.setCompoundDrawables(iconDrawable(icon,on?BG:TEXT,17),null,null,null);v.setCompoundDrawablePadding(dp(7));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(48));lp.rightMargin=dp(9);v.setLayoutParams(lp);v.setOnClickListener(x->{sort=value;render();});return v;}
    private void sortDeals(List<DealRecord>l){if("recent".equals(sort))l.sort((a,b)->Long.compare(b.firstSeen,a.firstSeen));else if("deal".equals(sort))l.sort((a,b)->{int x=Integer.compare(decisionPriority(b),decisionPriority(a));return x!=0?x:Integer.compare(nz(saving(b),-999),nz(saving(a),-999));});else if("rating".equals(sort))l.sort((a,b)->Integer.compare(nz(b.qualityScore,-1),nz(a.qualityScore,-1)));else if("bgg".equals(sort))l.sort((a,b)->Double.compare(b.rating==null?0:b.rating,a.rating==null?0:a.rating));else if("price_desc".equals(sort))l.sort((a,b)->Integer.compare(nz(effectiveTotal(b),-1),nz(effectiveTotal(a),-1)));else if("price".equals(sort))l.sort(Comparator.comparingInt(d->effectiveTotal(d)==null?Integer.MAX_VALUE:effectiveTotal(d)));else l.sort((a,b)->Double.compare(relevance(b),relevance(a)));}
private View catalogRowV51(DealRecord d){
        DealEvaluator.Evaluation evaluation=DealEvaluator.evaluate(d);LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(10),0,dp(10));row.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout artWrap=new FrameLayout(this);artWrap.addView(dealArtworkView(d,dp(78),dp(104)),new FrameLayout.LayoutParams(dp(78),dp(104)));if(vintedDataIncomplete(d)){View dot=new View(this);dot.setBackground(round(ORANGE,999,0,0));FrameLayout.LayoutParams dp1=new FrameLayout.LayoutParams(dp(8),dp(8),Gravity.END|Gravity.TOP);dp1.rightMargin=dp(2);dp1.topMargin=dp(2);artWrap.addView(dot,dp1);}row.addView(artWrap,new LinearLayout.LayoutParams(dp(78),dp(104)));
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(12),0,dp(6),0);LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.TOP);TextView title=text(name(d),17,TEXT,Typeface.BOLD);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView more=text("⋯",22,MUTED,Typeface.BOLD);more.setGravity(Gravity.CENTER);more.setContentDescription("Azioni annuncio");more.setOnClickListener(v->showListingActions(d));top.addView(more,new LinearLayout.LayoutParams(dp(34),dp(34)));info.addView(top);
        LinearLayout meta=new LinearLayout(this);meta.setGravity(Gravity.CENTER_VERTICAL);meta.setPadding(0,dp(4),0,0);TextView rating=text(d.rating==null?"BGG n/d":"BGG "+String.format(Locale.ITALY,"%.1f",d.rating),11,MUTED,Typeface.BOLD);meta.addView(rating);TextView lang=text(languageCompact(d.languageCode),11,languageMetaColor(d.languageCode),Typeface.NORMAL);LinearLayout.LayoutParams lpLang=new LinearLayout.LayoutParams(-2,-2);lpLang.leftMargin=dp(10);meta.addView(lang,lpLang);meta.addView(new Space(this),new LinearLayout.LayoutParams(0,1,1));meta.addView(publicationText(d,11,Typeface.NORMAL));info.addView(meta);
        LinearLayout priceLine=new LinearLayout(this);priceLine.setGravity(Gravity.CENTER_VERTICAL);priceLine.setPadding(0,dp(7),0,0);priceLine.addView(text(total(d),21,TEXT,Typeface.BOLD));Integer sv=saving(d);if(sv!=null)priceLine.addView(text("  −"+sv+"%",13,LIME,Typeface.BOLD));info.addView(priceLine);
        String secondary=evaluation.label+(hasLiveBundle(d)?" · Bundle":"");TextView signal=text(secondary,11,dealAccent(d),Typeface.NORMAL);signal.setSingleLine(true);signal.setEllipsize(TextUtils.TruncateAt.END);signal.setPadding(0,dp(2),0,0);info.addView(signal);row.addView(info,new LinearLayout.LayoutParams(0,-2,1));row.setOnClickListener(v->openDetail(d));
        LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(LinearLayout.VERTICAL);wrap.addView(row,new LinearLayout.LayoutParams(-1,dp(124)));View divider=new View(this);divider.setBackgroundColor(OUTLINE);LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-1,dp(1));dl.leftMargin=dp(90);wrap.addView(divider,dl);return wrap;
    }

    private int photoCount(DealRecord d){int n=0;if(!TextUtils.isEmpty(d.listingPhotosCsv))for(String u:d.listingPhotosCsv.split(","))if(!u.trim().isEmpty())n++;File local=ThumbnailStore.fileFor(this,d.signature);if(local.exists())n=Math.max(n,1);return n;}
private String filterLabel(){if("it".equals(filterMode))return"Solo italiano";if("ind".equals(filterMode))return"Indipendente";if("ship".equals(filterMode))return"Spedizione ≤ 3 €";if("bundle".equals(filterMode))return"Bundle disponibili";if("verify".equals(filterMode))return"Da verificare";return"Tutti";}
private void showFilterSheet(){
        FilterDraft draft=new FilterDraft(catalogMinRatingFilter,catalogMinDiscountFilter,languageFilter,linkFilter,typeFilter,maxPriceFilter,filterShipping,filterBundle,filterVerify);
        draft.category=catalogCategory;draft.dependence=catalogTextDependence;if("IND".equals(draft.language)){draft.language="all";draft.dependence="IND";}Dialog dialog=fullScreenPanel("Filtri","Azzera",()->{});LinearLayout box=dialog.findViewById(SHEET_ID);final Runnable[] refresh={null};
        refresh[0]=()->{box.removeAllViews();
            box.addView(filterRow("Voto BGG",filterRatingSummary(draft.rating),()->showChoicePage("Voto BGG",new String[]{"Qualsiasi","6+","7+","8+","9+"},new String[]{"all","6","7","8","9"},draft.rating==null?"all":String.valueOf(draft.rating.intValue()),v->{draft.rating="all".equals(v)?null:Double.valueOf(v);refresh[0].run();})));
            box.addView(filterRow("Sconto",filterDiscountSummary(draft.discount),()->showChoicePage("Sconto minimo",new String[]{"Qualsiasi","20%+","30%+","40%+","50%+"},new String[]{"all","20","30","40","50"},draft.discount==null?"all":String.valueOf(draft.discount),v->{draft.discount="all".equals(v)?null:Integer.valueOf(v);refresh[0].run();})));
            box.addView(filterRow("Lingua",filterLanguageSummary(draft.language),()->showChoicePage("Lingua",new String[]{"Tutte","Italiano","Inglese","Francese","Tedesco","Spagnolo","Olandese","Portoghese","Non verificata"},new String[]{"all","IT","EN","FR","DE","ES","NL","PT","unknown"},draft.language,v->{draft.language=v;refresh[0].run();})));
            box.addView(filterRow("Testo nel gioco","all".equals(draft.dependence)?"Qualsiasi":"IND".equals(draft.dependence)?"Indipendente dalla lingua":"DEP".equals(draft.dependence)?"Dipendente dalla lingua":"Non verificata",()->showChoicePage("Dipendenza dalla lingua",new String[]{"Qualsiasi","Indipendente","Dipendente","Non verificata"},new String[]{"all","IND","DEP","unknown"},draft.dependence,v->{draft.dependence=v;refresh[0].run();})));
            box.addView(filterRow("Prezzo",draft.maxPrice==null?"Qualsiasi":"Fino a "+money(draft.maxPrice),()->showPricePage("Prezzo massimo",draft.maxPrice,v->{draft.maxPrice=v;refresh[0].run();})));
            box.addView(filterRow("Tipo",filterTypeSummary(draft.type),()->showChoicePage("Tipo",new String[]{"Tutti","Gioco base","Espansione"},new String[]{"all","base","expansion"},draft.type,v->{draft.type=v;refresh[0].run();})));
            box.addView(filterRow("Dati Vinted",filterLinkSummary(draft.link),()->showChoicePage("Dati Vinted",new String[]{"Qualsiasi","Completi","Da completare","Link mancante"},new String[]{"all","complete","incomplete","pending"},draft.link,v->{draft.link=v;refresh[0].run();})));
            box.addView(filterRow("Altri filtri",draft.extras()==0?"Nessuno":draft.extras()+" attivi",()->showOtherFiltersPage(draft,refresh[0])));
            LinearLayout footer=new LinearLayout(this);footer.setPadding(0,dp(26),0,dp(12));Button apply=button("Mostra risultati",LIME);apply.setOnClickListener(v->{catalogMinRatingFilter=draft.rating;catalogMinDiscountFilter=draft.discount;languageFilter=draft.language;catalogTextDependence=draft.dependence;catalogCategory=draft.category;linkFilter=draft.link;typeFilter=draft.type;maxPriceFilter=draft.maxPrice;filterShipping=draft.shipping;filterBundle=draft.bundle;filterVerify=draft.verify;filterMode="all";catalogVisible=24;dialog.dismiss();render();});footer.addView(apply,new LinearLayout.LayoutParams(-1,dp(52)));box.addView(footer);
        };
        refresh[0].run();
        setFullScreenPanelAction(dialog,()->{draft.reset();refresh[0].run();});dialog.show();
    }

    private void renderBundles(){
        addMarketHeader("bundles");
        List<DealRecord> allBundleDeals=db.getDeals("trusted_any_price",900);List<DealRecord> sources=uniqueBundleSources(allBundleDeals);sources.removeIf(d->bundleDealsForSource(d).size()<2);sortBundleSources(sources);List<DealRecord> prospects=bundleProspects(allBundleDeals,sources);
        if(!sources.isEmpty()){
            sectionTitle("Bundle confermati · "+sources.size(),null);TextView confirmedHint=text("Questi sono bundle reali: Ludo ha almeno due giochi attivi dello stesso venditore.",13,TEAL,Typeface.BOLD);confirmedHint.setPadding(0,0,0,dp(8));body.addView(confirmedHint);
            HorizontalScrollView bundleFilterScroll=new HorizontalScrollView(this);bundleFilterScroll.setHorizontalScrollBarEnabled(false);LinearLayout chips=new LinearLayout(this);chips.setPadding(0,dp(2),0,dp(10));addBundleSortChip(chips,"Convenienza","deal");addBundleSortChip(chips,"Prezzo","price");addBundleSortChip(chips,"Più giochi","size");bundleFilterScroll.addView(chips);body.addView(bundleFilterScroll,new LinearLayout.LayoutParams(-1,dp(50)));
            for(DealRecord d:sources)body.addView(bundleStrip(d));
        }
        if(!prospects.isEmpty()){TextView divider=text("Da controllare · "+prospects.size(),15,MUTED,Typeface.BOLD);divider.setPadding(2,sources.isEmpty()?dp(8):dp(24),0,dp(4));body.addView(divider);TextView hint=text("Non sono bundle confermati: sono venditori che vale la pena controllare manualmente.",12,MUTED,Typeface.NORMAL);hint.setPadding(0,0,0,dp(10));body.addView(hint);for(DealRecord d:prospects)body.addView(bundleProspectCard(d));}
        if(sources.isEmpty()&&prospects.isEmpty()){
            LinearLayout empty=verticalCard();empty.setGravity(Gravity.CENTER);empty.setPadding(dp(18),dp(14),dp(18),dp(14));ImageView art2=new ImageView(this);art2.setImageResource(R.drawable.ludo_bundle_gift);art2.setScaleType(ImageView.ScaleType.FIT_CENTER);empty.addView(art2,new LinearLayout.LayoutParams(-1,dp(150)));TextView title=text("Ancora niente qui",21,TEXT,Typeface.BOLD);title.setGravity(Gravity.CENTER);empty.addView(title);TextView sub=text("Ludo mostrerà sia bundle reali sia venditori interessanti da esplorare, senza inventare combinazioni.",13,MUTED,Typeface.NORMAL);sub.setGravity(Gravity.CENTER);empty.addView(sub);body.addView(empty,new LinearLayout.LayoutParams(-1,-2));
        }
    }

    private List<DealRecord> bundleProspects(List<DealRecord> deals,List<DealRecord> confirmed){
        Set<String> confirmedSellers=new HashSet<>();if(confirmed!=null)for(DealRecord d:confirmed)if(!TextUtils.isEmpty(d.sellerId))confirmedSellers.add(d.sellerId);
        Map<String,DealRecord> best=new LinkedHashMap<>();if(deals!=null)for(DealRecord d:deals){
            if(d==null||confirmedSellers.contains(d.sellerId)||BundleExploration.wasExplored(this,d.sellerId)||!DealEvaluator.isBundleProspect(d)||!bundleGameEligible(d))continue;
            DealRecord old=best.get(d.sellerId);if(old==null||DealEvaluator.bundleProspectScore(d)>DealEvaluator.bundleProspectScore(old))best.put(d.sellerId,d);
        }
        List<DealRecord> out=new ArrayList<>(best.values());out.sort((a,b)->Double.compare(DealEvaluator.bundleProspectScore(b),DealEvaluator.bundleProspectScore(a)));return out.size()>8?new ArrayList<>(out.subList(0,8)):out;
    }

    private View bundleProspectCard(DealRecord d){
        DealEvaluator.Evaluation evaluation=DealEvaluator.evaluate(d);boolean strongGame=(d.qualityScore!=null&&d.qualityScore>=74)||(d.rating!=null&&d.rating>=7.4);String signal=strongGame&&!evaluation.discoverable()?"Gioco forte":evaluation.label;String prospectReason=strongGame&&!evaluation.discoverable()?"Un gioco molto interessante può essere un buon punto di partenza per esplorare questo venditore.":evaluation.reason;LinearLayout card=verticalCard();card.setPadding(dp(12),dp(12),dp(12),dp(12));LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(dealArtworkView(d,dp(78),dp(102)),new LinearLayout.LayoutParams(dp(78),dp(102)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(14),0,dp(8),0);copy.addView(materialChip("ESPLORA BUNDLE",SURFACE2,CYAN,true),new LinearLayout.LayoutParams(-2,dp(32)));TextView title=text(name(d),17,TEXT,Typeface.BOLD);title.setMaxLines(2);copy.addView(title);String seller=TextUtils.isEmpty(d.sellerName)?"Venditore Vinted":"@"+d.sellerName;copy.addView(text(seller+" · "+signal,12,MUTED,Typeface.BOLD));TextView reason=text(prospectReason,12,MUTED,Typeface.NORMAL);reason.setMaxLines(2);copy.addView(reason);row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));TextView action=text("Vinted  ›",14,CYAN,Typeface.BOLD);action.setGravity(Gravity.CENTER);row.addView(action,new LinearLayout.LayoutParams(dp(78),dp(48)));card.addView(row);card.setOnClickListener(v->openBundleProspect(d));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(10);card.setLayoutParams(lp);return card;
    }

    private void openBundleProspect(DealRecord d){if(d==null)return;BundleExploration.begin(this,d);getSharedPreferences("va_v3_diag",MODE_PRIVATE).edit().putString("bundleExploreSource",d.signature==null?"":d.signature).putString("bundleExploreSeller",d.sellerId==null?"":d.sellerId).putLong("bundleExploreStartedAt",System.currentTimeMillis()).apply();recordAction("bundle:explore:"+d.signature);openVinted(d);}


    private void sortBundleSources(List<DealRecord> sources){
        if(sources==null)return;
        if("price".equals(bundleSort))sources.sort(Comparator.comparingInt(d->bundlePlan(bundleDealsForSource(d)).offerSubtotal));
        else if("size".equals(bundleSort))sources.sort((a,b)->Integer.compare(bundleDealsForSource(b).size(),bundleDealsForSource(a).size()));
        else sources.sort((a,b)->{
            List<DealRecord> gb=bundleDealsForSource(b),ga=bundleDealsForSource(a);BundlePlan pb=bundlePlan(gb),pa=bundlePlan(ga);
            double sb=bundleQuality(gb)*12+(pb.shippingEstimated||pb.savingPct==null?0:Math.max(-20,Math.min(60,pb.savingPct))*.55)-bundleLanguagePenalty(gb);
            double sa=bundleQuality(ga)*12+(pa.shippingEstimated||pa.savingPct==null?0:Math.max(-20,Math.min(60,pa.savingPct))*.55)-bundleLanguagePenalty(ga);
            return Double.compare(sb,sa);
        });
    }
    private int bundleLanguagePenalty(List<DealRecord> games){int penalty=0;if(games==null)return 0;for(DealRecord d:games){String lc=d.languageCode==null?"":d.languageCode.toUpperCase(Locale.ROOT);if(isForeignLanguage(lc)&&lc.contains("DEP"))penalty+=45;else if(isForeignLanguage(lc)&&!lc.contains("IND"))penalty+=18;else if(TextUtils.isEmpty(lc)||(!lc.contains("IND")&&!lc.contains("DEP")))penalty+=5;}return penalty;}
    private void addBundleSortChip(LinearLayout row,String label,String key){TextView chip=materialChip(label,key.equals(bundleSort)?Color.rgb(46,66,86):SURFACE2,key.equals(bundleSort)?TEXT:MUTED,key.equals(bundleSort));chip.setBackground(round(key.equals(bundleSort)?Color.rgb(46,66,86):SURFACE2,999,1,key.equals(bundleSort)?CYAN:OUTLINE));chip.setOnClickListener(v->{bundleSort=key;render();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(38));if(row.getChildCount()>0)lp.leftMargin=dp(8);row.addView(chip,lp);}



    // ---------- OCCHIO DI LUDO: local companion ----------
    private final LudoPetState petState=new LudoPetState();
    private LudoPetView petView;private boolean petResumed;private Dialog activePetPanel;private boolean petPanelProfile;
    private LocalScoutBrain.Snapshot petSnapshot;private List<DealRecord> petHuntDeals=Collections.emptyList();private List<LibraryGame> petLibrary=Collections.emptyList();
    private final Map<String,Long> petGameIds=new HashMap<>();private boolean petLoading;private long petLoadedAt;private String petFailure;
    private void requestPetSnapshot(){
        if(petLoading||(petSnapshot!=null||petFailure!=null)&&System.currentTimeMillis()-petLoadedAt<15000)return;petLoading=true;
        uiDataIo.execute(()->{LocalScoutBrain.Snapshot snapshot=null;List<DealRecord> hunts=Collections.emptyList();List<LibraryGame> library=Collections.emptyList();Map<String,Long> games=new java.util.LinkedHashMap<>();String failure=null;
            try{List<DealRecord> deals=db.getDeals("trusted",600);hunts=db.getDeals("trusted_any_price",800);library=libraryDb.all();snapshot=intelligence.analyze(deals,bundleDb,library);for(DealRecord pick:snapshot.picks)if(!TextUtils.isEmpty(pick.vintedUrl)&&!TextUtils.isEmpty(pick.bggId)&&!TextUtils.isEmpty(pick.signature)){GameRecord game=marketStore.gameStatsByBggId(pick.bggId);if(game!=null)games.put(pick.signature,game.id);}}
            catch(RuntimeException error){failure="Non riesco a leggere i consigli. Riproviamo?";}
            final LocalScoutBrain.Snapshot ready=snapshot;final List<DealRecord> readyHunts=hunts;final List<LibraryGame> readyLibrary=library;final String error=failure;
            runOnUiThread(()->{petLoading=false;if(isFinishing()||isDestroyed())return;petFailure=error;petLoadedAt=System.currentTimeMillis();if(error==null){petSnapshot=ready;petHuntDeals=readyHunts;petLibrary=readyLibrary;petGameIds.clear();petGameIds.putAll(games);petState.refresh(games.keySet().toArray(new String[0]));}else{petSnapshot=null;petGameIds.clear();petState.refresh(new String[0]);}if("companion".equals(tab))scheduleRender(0);});
        });
    }
    private DealRecord petSuggestion(){if(petSnapshot==null||petState.selected()==null)return null;for(DealRecord d:petSnapshot.picks)if(petState.selected().equals(d.signature))return d;return null;}
    private void renderCompanion(){
        requestPetSnapshot();body.setPadding(dp(18),dp(16),dp(18),dp(16));body.setBackgroundColor(BG);
        body.addView(text("Ludo",28,TEXT,Typeface.BOLD));
        final DealRecord chosen=petSuggestion();
        LinearLayout scene=new LinearLayout(this);scene.setOrientation(LinearLayout.VERTICAL);scene.setPadding(dp(12),dp(12),dp(12),dp(16));scene.setBackground(round(Color.rgb(27,18,44),26,1,Color.rgb(58,40,82)));
        String phrase=petLoading&&petSnapshot==null?"Sto cercando tra i tuoi giochi…":petFailure!=null?petFailure:chosen!=null?"Ecco la mia proposta.":petSnapshot!=null&&petGameIds.isEmpty()?"Nessun consiglio convincente adesso. Avviamo una caccia?":"Cerchiamo il prossimo gioco?";
        TextView speech=text(phrase,18,Color.rgb(40,22,61),Typeface.NORMAL);speech.setPadding(dp(16),dp(12),dp(16),dp(12));speech.setBackground(round(Color.rgb(239,228,255),20,0,0));speech.setGravity(Gravity.CENTER);scene.addView(speech,new LinearLayout.LayoutParams(-1,-2));
        FrameLayout stage=new FrameLayout(this);stage.setBackground(new Drawable(){private final Paint p=new Paint(3);public void draw(Canvas c){Rect b=getBounds();p.setColor(Color.rgb(52,33,76));c.drawOval(b.left-b.width()*.3f,b.top+b.height()*.72f,b.right+b.width()*.3f,b.bottom+b.height()*.3f,p);}public void setAlpha(int a){}public void setColorFilter(android.graphics.ColorFilter f){}public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}});
        LinearLayout stageContent=new LinearLayout(this);stageContent.setOrientation(LinearLayout.VERTICAL);
        LinearLayout controls=new LinearLayout(this);controls.setPadding(0,dp(12),0,dp(8));controls.setGravity(Gravity.CENTER_VERTICAL);
        Button hunts=petSideButton("Cacce",LudoIcons.SEARCH,()->openPetSpace(false));controls.addView(hunts,new LinearLayout.LayoutParams(0,-2,1));
        controls.addView(new Space(this),new LinearLayout.LayoutParams(dp(32),1));
        Button tastes=petSideButton("Gusti",LudoIcons.HEART,()->openPetSpace(true));controls.addView(tastes,new LinearLayout.LayoutParams(0,-2,1));stageContent.addView(controls,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout actors=new LinearLayout(this);actors.setGravity(Gravity.CENTER_VERTICAL);if(chosen==null)actors.addView(new Space(this),new LinearLayout.LayoutParams(0,1,.22f));petView=new LudoPetView(this);petView.lookAtGame(chosen!=null);petView.setResumed(petResumed);actors.addView(petView,new LinearLayout.LayoutParams(0,-1,.56f));if(chosen==null)actors.addView(new Space(this),new LinearLayout.LayoutParams(0,1,.22f));
        if(chosen!=null){View box=productBoxArtwork(chosen.bggId,chosen.bggImageUrl,name(chosen));actors.addView(box,new LinearLayout.LayoutParams(0,-1,.44f));}
        stageContent.addView(actors,new LinearLayout.LayoutParams(-1,0,1));stage.addView(stageContent,new FrameLayout.LayoutParams(-1,-1));
        int available=getResources().getDisplayMetrics().heightPixels;int stageHeight=Math.max(dp(260),Math.min(dp(350),available/2-dp(75)));scene.addView(stage,new LinearLayout.LayoutParams(-1,stageHeight));
        if(chosen!=null){TextView title=text(name(chosen),18,TEXT,Typeface.BOLD);title.setGravity(Gravity.CENTER);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);scene.addView(title);String reason=petSnapshot.pickReasons.get(chosen.signature);if(!TextUtils.isEmpty(reason)){TextView why=text(reason,13,Color.rgb(211,194,233),Typeface.NORMAL);why.setGravity(Gravity.CENTER);why.setPadding(0,dp(6),0,dp(8));scene.addView(why);}}
        String action=petFailure!=null?"Riprova":chosen!=null?"Apri il gioco":petSnapshot!=null&&petGameIds.isEmpty()?"Avvia una caccia":"Consigliami";
        Button primary=button(action,Color.rgb(121,58,207));primary.setTextColor(Color.WHITE);primary.setMinHeight(dp(56));primary.setEnabled(!petLoading||petSnapshot!=null);primary.setOnClickListener(v->{if(petFailure!=null){petLoadedAt=0;requestPetSnapshot();render();return;}if(chosen!=null){Long id=petGameIds.get(chosen.signature);if(id!=null)openGameDetailOverlay(id);return;}if(petGameIds.isEmpty()){openPetSpace(false);return;}petState.suggest();render();if(petView!=null)petView.react();});scene.addView(primary,new LinearLayout.LayoutParams(-1,-2));
        if(chosen!=null){TextView again=secondaryTextAction("Un altro consiglio");again.setMinHeight(dp(48));again.setGravity(Gravity.CENTER);again.setOnClickListener(v->{petState.suggest();render();});scene.addView(again);}
        LinearLayout.LayoutParams sceneLp=new LinearLayout.LayoutParams(-1,-2);sceneLp.topMargin=dp(16);body.addView(scene,sceneLp);
    }
    private Button petSideButton(String label,String icon,Runnable action){Button b=button(label,Color.rgb(72,46,103));b.setTextColor(Color.WHITE);b.setTextSize(14);b.setMinHeight(dp(56));b.setMinWidth(dp(110));b.setPadding(dp(12),dp(8),dp(12),dp(8));b.setCompoundDrawablesWithIntrinsicBounds(iconDrawable(icon,Color.WHITE,18),null,null,null);b.setCompoundDrawablePadding(dp(8));b.setContentDescription("Apri "+label);b.setOnClickListener(v->action.run());return b;}
    private void refreshPetPanel(){
        Dialog panel=activePetPanel;if(panel==null||!panel.isShowing())return;LinearLayout host=panel.findViewById(SHEET_ID);if(host==null)return;
        LinearLayout previous=body;try{body=host;host.removeAllViews();if(petPanelProfile){renderLibraryInsights(petSnapshot,petLibrary);body.addView(companionProfileTrustCard(petLibrary));body.addView(languageStats(petSnapshot));}else renderHunts(petHuntDeals);}finally{body=previous;}
    }
    private void openPetSpace(boolean profile){
        if(petSnapshot==null){Toast.makeText(this,"Attendi il caricamento, poi riprova.",Toast.LENGTH_SHORT).show();return;}
        Dialog panel=fullScreenPanel(profile?"I miei gusti":"Le mie cacce");activePetPanel=panel;petPanelProfile=profile;
        if(petView!=null)petView.setResumed(false);
        panel.setOnDismissListener(d->{if(activePetPanel==panel)activePetPanel=null;petLoadedAt=0;if(petView!=null&&"companion".equals(tab)){petView.setResumed(petResumed);scheduleRender(0);}});
        panel.show();refreshPetPanel();
    }
    private View companionTabs(){LinearLayout tabs=new LinearLayout(this);tabs.setPadding(dp(4),dp(4),dp(4),dp(4));tabs.setBackground(round(SURFACE2,18,1,OUTLINE));addCompanionTab(tabs,"Per me","for_you");addCompanionTab(tabs,"Cacce","hunts");addCompanionTab(tabs,"Profilo","profile");return tabs;}
    private void addCompanionTab(LinearLayout tabs,String label,String value){boolean on=value.equals(companionSection);TextView t=text(label,13,on?TEXT:MUTED,Typeface.BOLD);t.setGravity(Gravity.CENTER);t.setBackground(round(on?Color.rgb(61,42,90):Color.TRANSPARENT,13,0,0));t.setOnClickListener(v->{companionSection=value;render();if(scroll!=null)scroll.scrollTo(0,0);});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);if(tabs.getChildCount()>0)lp.leftMargin=dp(4);tabs.addView(t,lp);}
    private View companionProfileTrustCard(List<LibraryGame> library){int rated=0;for(LibraryGame g:library)if(!"sold".equals(g.collectionState)&&g.personalRating!=null)rated++;LinearLayout c=verticalCard();c.setPadding(dp(16),dp(14),dp(16),dp(14));c.addView(text("Come Ludo usa il tuo profilo",17,TEXT,Typeface.BOLD));TextView p=text("Le raccomandazioni partono sempre da qualità BGG e prezzo. La Libreria e i tuoi "+rated+" voti servono per capire quali giochi hanno più senso per te, non per cambiare i dati del mercato.",13,MUTED,Typeface.NORMAL);p.setLineSpacing(0,1.08f);p.setPadding(0,dp(7),0,0);c.addView(p);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(14);c.setLayoutParams(lp);return c;}

    private void renderLibraryInsights(LocalScoutBrain.Snapshot snap,List<LibraryGame> library){
        sectionHeader("✦","Cosa leggo nella tua Libreria",null,null,null);LinearLayout card=verticalCard();card.setPadding(dp(16),dp(14),dp(16),dp(14));
        if(snap.libraryInsights.isEmpty())card.addView(text("Aggiungi qualche gioco e qualche voto: qui Ludo costruirà un quadro della collezione senza sostituire BGG.",14,MUTED,Typeface.NORMAL));
        else for(int i=0;i<snap.libraryInsights.size();i++){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.TOP);TextView icon=text(i==0?"◎":"·",18,i==0?LIME:CYAN,Typeface.BOLD);row.addView(icon,new LinearLayout.LayoutParams(dp(28),-2));TextView copy=text(snap.libraryInsights.get(i),14,TEXT,Typeface.NORMAL);copy.setLineSpacing(0,1.08f);row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);if(i>0)rp.topMargin=dp(10);card.addView(row,rp);}
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(6);body.addView(card,cp);
    }
private void renderAdvice(LocalScoutBrain.Snapshot snap){
        sectionHeader("◎","Ludo ti segnala","BGG prima, Libreria come contesto.",null,null);int count=0;
        for(DealRecord d:snap.picks){if(TextUtils.isEmpty(d.vintedUrl)||count++>=3)continue;
            LinearLayout article=verticalCard();article.setPadding(0,0,0,dp(14));article.setClipChildren(true);
            LinearLayout hero=new LinearLayout(this);hero.setGravity(Gravity.CENTER_VERTICAL);hero.setPadding(dp(16),dp(16),dp(16),0);ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.FIT_CENTER);setDiscoverBggArtwork(cover,null,d,()->{});hero.addView(cover,new LinearLayout.LayoutParams(dp(104),dp(136)));LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(16),0,0,0);TextView title=text(name(d),21,TEXT,Typeface.BOLD);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);info.addView(title);info.addView(text(d.rating==null?"BGG n/d":"★ "+String.format(Locale.ITALY,"%.1f",d.rating)+" BGG",13,YELLOW,Typeface.BOLD));info.addView(discoverLanguageIndicator(d));String label=snap.pickLabels.get(d.signature);if(!TextUtils.isEmpty(label)){TextView note=text(label,10,CYAN,Typeface.BOLD);note.setMaxLines(2);info.addView(note);}hero.addView(info,new LinearLayout.LayoutParams(0,-2,1));article.addView(hero);
            LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(16),dp(13),dp(16),0);String reason=snap.pickReasons.get(d.signature);if(TextUtils.isEmpty(reason))reason=decisionReason(d);TextView why=text(reason,14,TEXT,Typeface.NORMAL);why.setLineSpacing(0,1.08f);copy.addView(why);LinearLayout price=new LinearLayout(this);price.setGravity(Gravity.CENTER_VERTICAL);price.setPadding(0,dp(12),0,0);price.addView(text(total(d),20,TEXT,Typeface.BOLD));TextView decision=materialChip(DealEvaluator.evaluate(d).label,SURFACE2,dealAccent(d),true);LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-2,dp(34));slp.leftMargin=dp(9);price.addView(decision,slp);price.addView(new Space(this),new LinearLayout.LayoutParams(0,1,1));TextView open=text("Apri  ›",14,CYAN,Typeface.BOLD);open.setGravity(Gravity.CENTER);open.setMinWidth(dp(82));open.setOnClickListener(v->openDetail(d));price.addView(open,new LinearLayout.LayoutParams(dp(86),dp(42)));copy.addView(price);article.addView(copy);article.setOnClickListener(v->openDetail(d));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(12);body.addView(article,lp);
        }
        if(count==0){LinearLayout empty=verticalCard();empty.setPadding(dp(16),dp(14),dp(16),dp(14));empty.addView(text("Niente da spingere adesso",18,TEXT,Typeface.BOLD));empty.addView(text("Meglio nessun consiglio che un acquisto mediocre: Ludo aspetta un gioco solido e un'offerta sensata.",13,MUTED,Typeface.NORMAL));body.addView(empty);}
    }
    private String decisionReason(DealRecord d){DealEvaluator.Evaluation evaluation=DealEvaluator.evaluate(d);StringBuilder why=new StringBuilder(evaluation.reason==null?"":evaluation.reason);int other=0,cheaper=0;Integer cost=effectiveTotal(d);for(DealRecord x:db.getDeals("all_with_review",800))if(d.bggId!=null&&d.bggId.equals(x.bggId)&&!d.signature.equals(x.signature)&&"ACTIVE".equals(x.lifecycle)){other++;if(cost!=null&&effectiveTotal(x)!=null&&effectiveTotal(x)<cost)cheaper++;}if(cheaper>0)why.append(". Ci sono ").append(cheaper).append(" annunci dello stesso gioco con un totale più basso");else if(other>0)why.append(". Ci sono altri ").append(other).append(" annunci dello stesso gioco da confrontare");return why.toString();}
    private View companionHeader(LocalScoutBrain.Snapshot snap){
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(0,dp(12),0,dp(22));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.addView(text("Ludo",34,TEXT,Typeface.BOLD));TextView phrase=text(snap.headline,14,MUTED,Typeface.NORMAL);phrase.setMaxLines(2);phrase.setEllipsize(TextUtils.TruncateAt.END);phrase.setPadding(0,dp(7),0,0);copy.addView(phrase);header.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        ImageView mascot=new ImageView(this);mascot.setImageResource(snap.totalDeals==0?R.drawable.ludo_no_results:hasGreatDeal(snap)?R.drawable.ludo_great_deal:R.drawable.ludo_searching_animation);mascot.setScaleType(ImageView.ScaleType.FIT_CENTER);header.addView(mascot,new LinearLayout.LayoutParams(dp(94),dp(110)));return header;
    }
    private boolean hasGreatDeal(LocalScoutBrain.Snapshot snap){if(snap==null||snap.picks==null)return false;for(DealRecord d:snap.picks)if(DealEvaluator.evaluate(d).decision==DealEvaluator.Decision.GREAT_BUY)return true;return false;}
private View statCard(String icon,String value,String label){LinearLayout c=verticalCard();c.setGravity(Gravity.CENTER);c.setPadding(dp(10),dp(10),dp(10),dp(10));TextView i=text(icon,24,LIME,Typeface.BOLD);i.setGravity(Gravity.CENTER);c.addView(i);TextView v=text(value,22,TEXT,Typeface.BOLD);v.setGravity(Gravity.CENTER);c.addView(v);TextView l=text(label,11,MUTED,Typeface.BOLD);l.setGravity(Gravity.CENTER);c.addView(l);return c;}
    private void companionSection(String icon,String title,String sub,List<DealRecord> deals,String tag){sectionHeader(icon,title,sub,null,null);HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(this);row.setPadding(0,0,dp(16),dp(6));for(int i=0;i<Math.min(5,deals.size());i++)row.addView(companionDealCard(deals.get(i),tag));hs.addView(row);body.addView(hs);}
    private void renderHunts(List<DealRecord> deals){sectionHeader("⌖","Le cacce",null,null,null);Button add=button("Aggiungi una caccia",CYAN);add.setCompoundDrawables(iconDrawable(LudoIcons.SEARCH,BG,18),null,null,null);add.setCompoundDrawablePadding(dp(8));add.setOnClickListener(v->addHuntFlow());body.addView(add,new LinearLayout.LayoutParams(-1,dp(50)));List<HuntDatabase.Hunt> hunts=huntDb.all();if(hunts.isEmpty()){LinearLayout e=new LinearLayout(this);e.setGravity(Gravity.CENTER_VERTICAL);e.setPadding(dp(12),dp(10),dp(12),dp(10));ImageView im=new ImageView(this);im.setImageResource(R.drawable.ludo_tracking);im.setScaleType(ImageView.ScaleType.FIT_CENTER);e.addView(im,new LinearLayout.LayoutParams(dp(88),dp(88)));e.addView(text("Indica un gioco e il prezzo giusto. Ludo controllerà i nuovi annunci.",14,MUTED,Typeface.NORMAL),new LinearLayout.LayoutParams(0,-2,1));body.addView(e);}else for(HuntDatabase.Hunt h:hunts)body.addView(huntRow(h,deals));}
    private View huntRow(HuntDatabase.Hunt h,List<DealRecord> deals){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setBackground(round(SURFACE,20,0,0));row.setElevation(dp(1));row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(10),dp(12),dp(10));ImageView im=new ImageView(this);im.setScaleType(ImageView.ScaleType.FIT_CENTER);File f=ArtworkStore.bggFile(this,h.bggId);if(f.exists())im.setImageBitmap(decodeLocalBitmap(f,240,320));else if(!TextUtils.isEmpty(h.imageUrl))loadRemote(im,h.imageUrl);row.addView(im,new LinearLayout.LayoutParams(dp(64),dp(76)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);tx.setPadding(dp(12),0,0,0);tx.addView(text(h.name,17,TEXT,Typeface.BOLD));int matches=0;for(DealRecord d:deals)if(h.bggId.equals(d.bggId)&&(h.targetCents==null||effectiveTotal(d)!=null&&effectiveTotal(d)<=h.targetCents))matches++;tx.addView(text((h.targetCents==null?"Qualsiasi prezzo · priorità al gioco":"Avvisami entro "+money(h.targetCents))+"  ·  "+matches+" trovati",12,matches>0?CYAN:MUTED,Typeface.BOLD));row.addView(tx,new LinearLayout.LayoutParams(0,-2,1));TextView del=appIcon(LudoIcons.XMARK,16,MUTED);del.setGravity(Gravity.CENTER);del.setContentDescription("Elimina caccia");del.setOnClickListener(v->{huntDb.remove(h.id);render();});row.addView(del,new LinearLayout.LayoutParams(dp(40),dp(40)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(98));lp.topMargin=dp(8);row.setLayoutParams(lp);return row;}
    private void addHuntFlow(){Dialog d=bottomSheet("Nuova caccia");LinearLayout box=d.findViewById(SHEET_ID);box.addView(text("Trova il gioco con lo stesso selettore usato nelle correzioni: nome, link o ID BGG.",13,MUTED,Typeface.NORMAL));addBggPicker(box,d,"",this::huntTargetDialog,false);d.show();}
    private void chooseHuntGame(List<BggSearchClient.Game> games){chooseGames("Scegli il gioco",games,this::huntTargetDialog);}

    private void huntTargetDialog(BggSearchClient.Game g){Dialog d=bottomSheet(g.name);LinearLayout box=d.findViewById(SHEET_ID);ImageView art=new ImageView(this);art.setImageResource(R.drawable.ludo_hunt_deal);art.setScaleType(ImageView.ScaleType.FIT_CENTER);box.addView(art,new LinearLayout.LayoutParams(-1,dp(150)));TextView hint=text("Senza un limite di prezzo, la Caccia conserva anche offerte a prezzo medio: qui conta trovare il gioco, non solo il margine di rivendita.",13,MUTED,Typeface.NORMAL);hint.setPadding(0,0,0,dp(10));box.addView(hint);EditText price=input("Prezzo massimo, opzionale (€)");price.setInputType(2|8192);box.addView(price,new LinearLayout.LayoutParams(-1,dp(56)));Button save=button("Avvia la caccia",PINK);save.setOnClickListener(v->{huntDb.add(g,parseEuro(price.getText().toString()));if(!TextUtils.isEmpty(g.imageUrl))ArtworkStore.downloadBgg(this,g.id,g.imageUrl);requestNotificationPermission();d.dismiss();render();});LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(52));sp.topMargin=dp(12);box.addView(save,sp);d.show();}
    private void requestNotificationPermission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},540);}
    private View companionDealCard(DealRecord d,String tag){DealEvaluator.Evaluation evaluation=DealEvaluator.evaluate(d);LinearLayout c=verticalCard();c.setPadding(dp(10),dp(10),dp(10),dp(10));c.addView(dealArtworkView(d,dp(146),dp(104)),new LinearLayout.LayoutParams(-1,dp(104)));TextView tg=pill(tag.toUpperCase(Locale.ITALY),"bundle reale".equals(tag)?PINK:Color.rgb(31,104,109),TEXT);LinearLayout.LayoutParams tgp=new LinearLayout.LayoutParams(-2,dp(30));tgp.topMargin=dp(7);c.addView(tg,tgp);TextView n=text(name(d),15,TEXT,Typeface.BOLD);n.setSingleLine(true);n.setEllipsize(TextUtils.TruncateAt.END);c.addView(n);LinearLayout p=new LinearLayout(this);p.setGravity(Gravity.CENTER_VERTICAL);p.addView(text(total(d),17,TEXT,Typeface.BOLD));p.addView(new Space(this),new LinearLayout.LayoutParams(0,1,1));p.addView(text(evaluation.label,12,dealAccent(d),Typeface.BOLD));c.addView(p);TextView why=text(evaluation.reason,11,MUTED,Typeface.NORMAL);why.setMaxLines(2);c.addView(why);c.setOnClickListener(v->openDetail(d));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(188),dp(242));lp.rightMargin=dp(10);c.setLayoutParams(lp);return c;}
    private String whyLine(DealRecord d){List<String>x=new ArrayList<>();if(d.rating!=null)x.add(String.format(Locale.ITALY,"BGG %.1f",d.rating));if(d.rank!=null)x.add("rank #"+d.rank);if(!TextUtils.isEmpty(d.bggCategories)){String c=d.bggCategories.split(" · ")[0];x.add(c);}if(d.minPlayers!=null)x.add(d.minPlayers+(d.maxPlayers!=null&&!d.maxPlayers.equals(d.minPlayers)?"–"+d.maxPlayers:"")+" giocatori");if(d.playtime!=null)x.add(d.playtime+" min");if(d.weight!=null)x.add(String.format(Locale.ITALY,"peso %.1f",d.weight));Integer sv=saving(d);if(sv!=null&&sv>0)x.add("risparmio "+sv+"%");x.add(publicationDisplay(d));return TextUtils.join(" · ",x);}
    private View languageStats(LocalScoutBrain.Snapshot s){LinearLayout c=verticalCard();c.setPadding(dp(14),dp(12),dp(14),dp(12));TextView h=text("Lingue che trovi oggi",17,TEXT,Typeface.BOLD);c.addView(h);int max=Math.max(1,Math.max(s.italianDeals,s.independentDeals));c.addView(languageBar("🇮🇹 IT",s.italianDeals,max));c.addView(languageBar("🌐 IND",s.independentDeals,max));c.addView(languageBar("ALTRE",Math.max(0,s.totalDeals-s.italianDeals-s.independentDeals),max));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(20);c.setLayoutParams(lp);return c;}
    private View languageBar(String label,int value,int max){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(0,dp(8),0,dp(3));r.addView(text(label,12,TEXT,Typeface.BOLD),new LinearLayout.LayoutParams(dp(72),-2));FrameLayout track=new FrameLayout(this);track.setBackground(round(SURFACE2,999,0,0));View fill=new View(this);fill.setBackground(round(LIME,999,0,0));int w=Math.max(dp(8),(int)(dp(150)*(value/(double)Math.max(1,max))));track.addView(fill,new FrameLayout.LayoutParams(w,dp(8)));r.addView(track,new LinearLayout.LayoutParams(0,dp(8),1));TextView n=text(String.valueOf(value),12,MUTED,Typeface.BOLD);n.setGravity(Gravity.END);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(dp(40),-2);np.leftMargin=dp(8);r.addView(n,np);return r;}

    private String libraryPurchaseQuality(LibraryGame g){
        if(g==null||g.paidCents==null||"Regalo".equalsIgnoreCase(g.source))return"";
        Integer total=PurchaseMath.total(g.paidCents,g.shippingCents,g.feeCents);if(total==null)total=g.paidCents;
        Integer ref=libraryMarketReference(g);if(ref==null||ref<=0)return"Prezzo da verificare";
        int pct=Math.round((ref-total)*100f/ref);if(pct>=35)return"Offertona";if(pct>=20)return"Buon prezzo";if(pct>=5)return"Buon prezzo";return"In linea col mercato";
    }
    private View librarySearchJobCard(LibrarySearchJob job){
        LinearLayout c=new LinearLayout(this);c.setGravity(Gravity.CENTER_VERTICAL);c.setPadding(dp(14),dp(14),dp(14),dp(14));c.setBackground(round(SURFACE,20,1,job.running?CYAN:(TextUtils.isEmpty(job.error)?TEAL:RED)));
        ImageView im=new ImageView(this);im.setScaleType(ImageView.ScaleType.CENTER_CROP);im.setBackground(round(SURFACE2,14,0,0));try{if(job.uri!=null)im.setImageURI(job.uri);else{im.setImageDrawable(iconDrawable(LudoIcons.SEARCH,CYAN,24));im.setPadding(dp(20),dp(20),dp(20),dp(20));}}catch(Exception ignored){}c.addView(im,new LinearLayout.LayoutParams(dp(90),dp(94)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(12),0,dp(8),0);copy.addView(text(job.running?"Sto riconoscendo il gioco":(!TextUtils.isEmpty(job.error)?"Serve il tuo aiuto":"Gioco da confermare"),17,TEXT,Typeface.BOLD));copy.addView(text(job.label,13,MUTED,Typeface.NORMAL));
        if(job.running){ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setIndeterminate(true);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(4));bp.topMargin=dp(9);copy.addView(bar,bp);}else if(!TextUtils.isEmpty(job.error))copy.addView(text(job.error,12,ORANGE,Typeface.NORMAL));else copy.addView(text(job.results.size()+" possibili risultati",12,TEAL,Typeface.BOLD));c.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        if(!job.running&&TextUtils.isEmpty(job.error)){TextView go=appIcon(LudoIcons.CHEVRON_RIGHT,17,CYAN);go.setGravity(Gravity.CENTER);go.setOnClickListener(v->{activeLibrarySearchJobId=job.id;chooseLibraryGame(new ArrayList<>(job.results));});c.addView(go,new LinearLayout.LayoutParams(dp(42),dp(54)));c.setOnClickListener(v->{activeLibrarySearchJobId=job.id;chooseLibraryGame(new ArrayList<>(job.results));});}
        else if(!job.running){c.setOnClickListener(v->librarySearchHelp(job));}
        TextView del=appIcon(LudoIcons.XMARK,16,MUTED);del.setGravity(Gravity.CENTER);del.setContentDescription("Elimina questa ricerca");del.setOnClickListener(v->{librarySearchJobs.remove(job);render();});c.addView(del,new LinearLayout.LayoutParams(dp(38),dp(54)));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(10);c.setLayoutParams(cp);return c;
    }
    private View pendingLibrarySearchCard(){LinearLayout c=verticalCard();c.setPadding(dp(16),dp(14),dp(16),dp(14));c.setBackground(round(SURFACE,20,1,pendingLibraryRunning?CYAN:TEAL));LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView icon=text(pendingLibraryRunning?"⌕":"✓",22,pendingLibraryRunning?CYAN:TEAL,Typeface.BOLD);top.addView(icon,new LinearLayout.LayoutParams(dp(36),-2));LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.addView(text(pendingLibraryRunning?"Sto cercando su BGG":"Risultati pronti",17,TEXT,Typeface.BOLD));copy.addView(text(pendingLibraryQuery,13,MUTED,Typeface.NORMAL));top.addView(copy,new LinearLayout.LayoutParams(0,-2,1));c.addView(top);if(pendingLibraryRunning){ProgressBar p=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);p.setIndeterminate(true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(4));lp.topMargin=dp(12);c.addView(p,lp);}else{TextView action=text("Conferma il gioco  ›",14,TEAL,Typeface.BOLD);action.setGravity(Gravity.CENTER_VERTICAL);action.setOnClickListener(v->chooseLibraryGame(new ArrayList<>(pendingLibraryResults)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(44));lp.topMargin=dp(8);c.addView(action,lp);}LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(14);cp.bottomMargin=dp(14);c.setLayoutParams(cp);return c;}
    private Double libraryLudoScore(LibraryGame g){if(g==null)return null;if(g.qualityScore!=null)return g.qualityScore/10.0;if(!TextUtils.isEmpty(g.bggId)&&libraryScoreCache.containsKey(g.bggId))return libraryScoreCache.get(g.bggId);return g.rating;}
    private Integer libraryMarketReference(LibraryGame g){if(g==null||TextUtils.isEmpty(g.bggId))return null;if(libraryMarketCache.containsKey(g.bggId)){int x=libraryMarketCache.get(g.bggId);return x<=0?null:x;}Integer catalogRef=bggSearch==null?null:bggSearch.localMarketReferenceCents(g.bggId);if(catalogRef!=null&&catalogRef>0){libraryMarketCache.put(g.bggId,catalogRef);return catalogRef;}libraryMarketCache.put(g.bggId,-1);return null;}
    private void prepareLibraryRenderCaches(List<LibraryGame> games){libraryMarketCache.clear();libraryScoreCache.clear();Map<String,List<Integer>> bench=new HashMap<>(),observed=new HashMap<>(),scores=new HashMap<>();for(DealRecord d:db.getDeals("all_with_review",1200)){if(TextUtils.isEmpty(d.bggId))continue;if(d.benchmarkCents!=null&&d.benchmarkCents>0)bench.computeIfAbsent(d.bggId,k->new ArrayList<>()).add(d.benchmarkCents);Integer total=effectiveTotal(d);if(total!=null&&total>0)observed.computeIfAbsent(d.bggId,k->new ArrayList<>()).add(total);if(d.qualityScore!=null)scores.computeIfAbsent(d.bggId,k->new ArrayList<>()).add(d.qualityScore);}for(LibraryGame g:games){if(TextUtils.isEmpty(g.bggId))continue;List<Integer> q=scores.get(g.bggId);if(q!=null&&!q.isEmpty()){Collections.sort(q);libraryScoreCache.put(g.bggId,q.get(q.size()/2)/10.0);}else if(g.rating!=null)libraryScoreCache.put(g.bggId,g.rating);List<Integer> b=bench.get(g.bggId);if(b!=null&&!b.isEmpty()){Collections.sort(b);libraryMarketCache.put(g.bggId,b.get(b.size()/2));continue;}Integer local=bggSearch==null?null:bggSearch.localMarketReferenceCents(g.bggId);if(local!=null&&local>0){libraryMarketCache.put(g.bggId,local);continue;}List<Integer> o=observed.get(g.bggId);if(o!=null&&!o.isEmpty()){Collections.sort(o);libraryMarketCache.put(g.bggId,o.get(o.size()/2));}else libraryMarketCache.put(g.bggId,-1);}}

    private void enrichLibraryBggIfNeeded(LibraryGame g){if(g==null)return;backfillLibraryBgg(Collections.singletonList(g));}
    private boolean libraryGameNeedsBgg(LibraryGame g){return g!=null&&!TextUtils.isEmpty(g.bggId)&&(g.rating==null||g.rank==null||g.qualityScore==null||TextUtils.isEmpty(g.categories)||g.minPlayers==null||g.maxPlayers==null||g.playtime==null||g.weight==null);}
    private BggSearchClient.Game bggFromDeal(DealRecord d){BggSearchClient.Game g=new BggSearchClient.Game();g.id=d.bggId;g.name=name(d);g.imageUrl=d.bggImageUrl;g.rating=d.rating;g.rank=d.rank;g.voters=d.voters;g.qualityScore=d.qualityScore;g.categories=d.bggCategories;g.minPlayers=d.minPlayers;g.maxPlayers=d.maxPlayers;g.playtime=d.playtime;g.weight=d.weight;return g;}
    private void backfillLibraryBgg(List<LibraryGame> games){
        if(games==null||games.isEmpty())return;List<LibraryGame> snapshot=new ArrayList<>(games);LIBRARY_SEARCH_NET.execute(()->{try{Map<String,DealRecord> known=new HashMap<>();for(DealRecord d:db.getDeals("all_with_review",1600))if(!TextUtils.isEmpty(d.bggId)&&(!known.containsKey(d.bggId)||nz(d.qualityScore,0)>nz(known.get(d.bggId).qualityScore,0)))known.put(d.bggId,d);for(LibraryGame g:snapshot)if(libraryGameNeedsBgg(g)&&known.containsKey(g.bggId))libraryDb.updateBgg(bggFromDeal(known.get(g.bggId)));if(!bggSearch.configured())return;List<LibraryGame> fresh=libraryDb.all();List<String> todo=new ArrayList<>();for(LibraryGame g:fresh)if(libraryGameNeedsBgg(g)&&!libraryBackfillInFlight.contains(g.bggId)&&!libraryBackfillAttempted.contains(g.bggId)){todo.add(g.bggId);libraryBackfillInFlight.add(g.bggId);libraryBackfillAttempted.add(g.bggId);}for(int start=0;start<todo.size();start+=20){List<String> batch=new ArrayList<>(todo.subList(start,Math.min(todo.size(),start+20)));bggSearch.detailsMany(batch,new BggSearchClient.Callback(){public void ok(List<BggSearchClient.Game> result){if(result!=null)for(BggSearchClient.Game full:result)libraryDb.updateBgg(full);libraryBackfillInFlight.removeAll(batch);runOnUiThread(()->{if("library".equals(tab))scheduleRender(80);});}public void error(String e){libraryBackfillInFlight.removeAll(batch);}});}}catch(Throwable ignored){}});
    }
    private String playerLabel(LibraryGame g){if(g.minPlayers==null&&g.maxPlayers==null)return"";if(g.minPlayers!=null&&g.maxPlayers!=null)return g.minPlayers.equals(g.maxPlayers)?String.valueOf(g.minPlayers):g.minPlayers+"–"+g.maxPlayers;return String.valueOf(g.minPlayers!=null?g.minPlayers:g.maxPlayers);}
    private String shortCategories(String raw){if(TextUtils.isEmpty(raw))return"";String clean=raw.replace("[","").replace("]","").replace("\"","").replace("|",",");String[] p=clean.split(",");StringBuilder out=new StringBuilder();for(String x:p){x=x.trim();if(x.isEmpty())continue;if(out.length()>0)out.append(" · ");out.append(x);if(out.length()>55)break;}return out.toString();}
    private static String libraryPersonalRatingLabel(Integer value){return value==null?"Da valutare":String.format(Locale.ITALY,"★ %.1f /5",value/2.0);}
    private static Integer librarySalePrice(String raw){
        String value=raw==null?"":raw.trim();if(value.isEmpty())return null;
        if(!value.matches("[0-9]+([.,][0-9]{1,2})?"))throw new IllegalArgumentException("Importo non valido");
        try{return new java.math.BigDecimal(value.replace(',','.')).movePointRight(2).intValueExact();}catch(ArithmeticException|NumberFormatException error){throw new IllegalArgumentException("Importo troppo grande",error);}
    }
    private final Set<Dialog> librarySaveInFlight=new HashSet<>();
    private void saveLibraryChange(LibraryGame game,Runnable change,Dialog sheet,Dialog parent,View save){
        if(!sheet.isShowing()||!librarySaveInFlight.add(sheet))return;
        save.setEnabled(false);int position=0;if(parent!=null){ScrollView previous=parent.findViewById(SHEET_ID+110);if(previous!=null)position=previous.getScrollY();}final int savedY=position;
        uiDataIo.execute(()->{LibraryGame updated=null;String failure=null;try{change.run();for(LibraryGame item:libraryDb.all())if(java.util.Objects.equals(game.bggId,item.bggId)){updated=item;break;}}catch(RuntimeException error){failure="Salvataggio non riuscito. Riprova.";}final LibraryGame fresh=updated;final String error=failure;
            runOnUiThread(()->{librarySaveInFlight.remove(sheet);if(isFinishing()||isDestroyed())return;if(error!=null){save.setEnabled(true);if(sheet.isShowing())Toast.makeText(this,error,Toast.LENGTH_LONG).show();return;}boolean returnToDetail=parent!=null&&parent.isShowing()&&sheet.isShowing();sheet.dismiss();if(returnToDetail)parent.dismiss();if("library".equals(tab))scheduleRender(0);if(returnToDetail&&fresh!=null)openLibraryDetail(fresh,savedY);});
        });
    }
    private void editLibrarySale(LibraryGame g,Dialog parent,boolean markSold){
        Dialog sheet=bottomSheet(markSold?"Segna come venduto":"Prezzo di vendita");LinearLayout box=sheet.findViewById(SHEET_ID);
        box.addView(text(g.name,18,TEXT,Typeface.BOLD));TextView description=text("Prezzo effettivo del gioco, spedizione esclusa. Puoi lasciarlo vuoto e aggiungerlo dopo.",14,MUTED,Typeface.NORMAL);description.setPadding(0,dp(8),0,dp(14));box.addView(description);
        EditText value=input("Prezzo di vendita (€)");value.setContentDescription("Prezzo di vendita in euro, facoltativo");value.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);if(g.salePriceCents!=null)value.setText(String.format(Locale.ITALY,"%.2f",g.salePriceCents/100.0));box.addView(value,new LinearLayout.LayoutParams(-1,dp(56)));
        Spinner reason=new Spinner(this);String[] reasons={"Motivo (facoltativo)","Non mi piaceva","Non lo giocavo","Troppo simile ad altri","Altro"};if(markSold){reason.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,reasons));reason.setContentDescription("Motivo della vendita, facoltativo");LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(54));rp.topMargin=dp(12);box.addView(reason,rp);}
        Button save=button(markSold?"Conferma vendita":"Salva prezzo",LIME);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(52));sp.topMargin=dp(18);box.addView(save,sp);
        save.setOnClickListener(v->{Integer cents;try{cents=librarySalePrice(value.getText().toString());}catch(IllegalArgumentException error){value.setError("Inserisci un importo valido, con massimo due decimali");return;}String why=markSold&&reason.getSelectedItemPosition()>0?reasons[reason.getSelectedItemPosition()]:null;saveLibraryChange(g,()->{if(markSold)libraryDb.markSold(g.bggId,why,cents);else if(!libraryDb.setSalePrice(g.bggId,cents))throw new IllegalStateException("Gioco non venduto");},sheet,parent,save);});
        TextView cancel=menuAction("Annulla",MUTED);cancel.setGravity(Gravity.CENTER);cancel.setOnClickListener(v->sheet.dismiss());box.addView(cancel);sheet.show();
    }
    private View libraryPersonalRatingCard(LibraryGame g,Dialog parent){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(14),dp(12),dp(14),dp(12));card.setBackground(round(SURFACE,16,1,OUTLINE));card.addView(text("Il tuo gusto",12,MUTED,Typeface.BOLD));TextView score=text(libraryPersonalRatingLabel(g.personalRating),22,g.personalRating==null?MUTED:PINK,Typeface.BOLD);score.setPadding(0,dp(4),0,dp(8));card.addView(score);TextView edit=detailSecondaryAction(g.personalRating==null?"Valuta il gioco":"Modifica il voto");edit.setMinHeight(dp(48));edit.setOnClickListener(v->rateLibraryGame(g,parent));card.addView(edit,new LinearLayout.LayoutParams(-1,-2));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(12);card.setLayoutParams(lp);return card;
    }
    private ImageView libraryCover(LibraryGame g){
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImageResource(R.drawable.ludo_value_book);String key="library-cover:"+g.bggId+":"+g.imageUrl;image.setTag(key);galleryNet.execute(()->{File file=TextUtils.isEmpty(g.bggId)?null:ArtworkStore.bggFile(this,g.bggId);Bitmap bitmap=file!=null&&file.exists()?decodeLocalBitmap(file,240,320):null;runOnUiThread(()->{if(isDestroyed()||isFinishing()||!java.util.Objects.equals(key,image.getTag()))return;if(bitmap!=null)image.setImageBitmap(bitmap);else if(!TextUtils.isEmpty(g.imageUrl))loadRemote(image,g.imageUrl);});});return image;
    }
    private void rateLibraryGame(LibraryGame g,Dialog parent){
        Dialog sheet=bottomSheet("Il tuo voto");LinearLayout box=sheet.findViewById(SHEET_ID);box.addView(text(g.name,18,TEXT,Typeface.BOLD));TextView current=text(libraryPersonalRatingLabel(g.personalRating),22,PINK,Typeface.BOLD);current.setPadding(0,dp(10),0,dp(10));box.addView(current);box.addView(text("Da zero a cinque stelle. Un voto già presente mantiene anche le mezze stelle.",13,MUTED,Typeface.NORMAL));
        for(int start=0;start<6;start+=3){LinearLayout row=new LinearLayout(this);for(int n=start;n<start+3;n++){final int stored=n*2;TextView option=text(n+" ★",17,g.personalRating!=null&&g.personalRating==stored?BG:TEXT,Typeface.BOLD);option.setGravity(Gravity.CENTER);option.setMinHeight(dp(48));option.setBackground(round(g.personalRating!=null&&g.personalRating==stored?LIME:SURFACE2,12,0,0));option.setSelected(g.personalRating!=null&&g.personalRating==stored);option.setContentDescription(n+" stelle su 5");option.setOnClickListener(v->saveLibraryChange(g,()->libraryDb.setPersonalRating(g.bggId,stored),sheet,parent,row));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(n>start)lp.leftMargin=dp(8);row.addView(option,lp);}LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(10);box.addView(row,rp);}
        TextView clear=menuAction("Rimuovi il voto",MUTED);clear.setGravity(Gravity.CENTER);clear.setOnClickListener(v->saveLibraryChange(g,()->libraryDb.setPersonalRating(g.bggId,null),sheet,parent,box));box.addView(clear);sheet.show();
    }
    private void markLibraryGameSold(LibraryGame g,Dialog parent){editLibrarySale(g,parent,true);}
    private void confirmDeleteLibraryGame(LibraryGame g,Dialog parent){new AlertDialog.Builder(this).setTitle("Eliminare "+g.name+"?").setMessage("Elimina il record dalla Libreria e dalla memoria di Ludo. Se lo hai venduto, usa invece ‘Segna come venduto’. ").setNegativeButton("Annulla",null).setPositiveButton("Elimina",(x,w)->{libraryDb.deleteGame(g.bggId);if(parent!=null)parent.dismiss();render();}).show();}
    private void openLibraryDetail(LibraryGame g){openLibraryDetail(g,0);}
    private void openLibraryDetail(LibraryGame g,int restoreY){
        enrichLibraryBggIfNeeded(g);
        Dialog d=new Dialog(this,android.R.style.Theme_Material_NoActionBar);ScrollView sc=new ScrollView(this);sc.setId(SHEET_ID+110);sc.setBackground(productPageBackground());LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(12),dp(20),dp(36));sc.addView(box);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView back=appIcon(LudoIcons.CHEVRON_LEFT,25,TEXT);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->d.dismiss());head.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));head.addView(text("Gioco",20,TEXT,Typeface.BOLD),new LinearLayout.LayoutParams(0,-2,1));box.addView(head);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,productArtworkHeight());ip.topMargin=dp(8);box.addView(productBoxArtwork(g.bggId,g.imageUrl,g.name),ip);
        String purchaseQuality=libraryPurchaseQuality(g);Double score=libraryLudoScore(g);LinearLayout chips=new LinearLayout(this);chips.setGravity(Gravity.CENTER_VERTICAL);chips.setPadding(0,dp(14),0,dp(8));if(score!=null){TextView s=materialChip("★ "+String.format(Locale.ITALY,"%.1f",score),Color.rgb(55,53,31),YELLOW,true);chips.addView(s,new LinearLayout.LayoutParams(-2,dp(40)));}if(!TextUtils.isEmpty(purchaseQuality)){int qc=purchaseQuality.startsWith("Super")?LIME:purchaseQuality.startsWith("Ottimo")?ORANGE:purchaseQuality.startsWith("Buon")?TEAL:RED;TextView q=materialChip(purchaseQuality.toUpperCase(Locale.ITALY),Color.argb(80,Color.red(qc),Color.green(qc),Color.blue(qc)),qc,true);LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(-2,dp(40));qp.leftMargin=dp(8);chips.addView(q,qp);}if(g.bundlePurchase){TextView b=materialChip("Bundle",Color.rgb(69,31,55),PINK,true);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,dp(40));bp.leftMargin=dp(8);chips.addView(b,bp);}if("sold".equals(g.collectionState)){TextView soldChip=materialChip("Venduto",Color.rgb(55,32,20),ORANGE,true);LinearLayout.LayoutParams spc=new LinearLayout.LayoutParams(-2,dp(40));spc.leftMargin=dp(8);chips.addView(soldChip,spc);}box.addView(chips);
        box.addView(text(g.name,30,TEXT,Typeface.BOLD));box.addView(libraryPersonalRatingCard(g,d));
        if("sold".equals(g.collectionState)){LinearLayout sale=verticalCard();sale.setPadding(dp(14),dp(12),dp(14),dp(12));sale.addView(text("La tua vendita",13,MUTED,Typeface.BOLD));sale.addView(text(g.salePriceCents==null?"Prezzo da inserire":money(g.salePriceCents),24,ORANGE,Typeface.BOLD));if(g.soldAt!=null)sale.addView(text(new SimpleDateFormat("dd/MM/yyyy",Locale.ITALY).format(new Date(g.soldAt)),12,MUTED,Typeface.NORMAL));if(!TextUtils.isEmpty(g.soldReason))sale.addView(text(g.soldReason,13,MUTED,Typeface.NORMAL));TextView priceAction=detailSecondaryAction(g.salePriceCents==null?"Aggiungi prezzo di vendita":"Modifica prezzo di vendita");priceAction.setMinHeight(dp(48));priceAction.setOnClickListener(v->editLibrarySale(g,d,false));sale.addView(priceAction,new LinearLayout.LayoutParams(-1,-2));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(12);box.addView(sale,sp);}
        LinearLayout bggFacts=new LinearLayout(this);bggFacts.setOrientation(LinearLayout.VERTICAL);bggFacts.setPadding(dp(14),dp(12),dp(14),dp(12));bggFacts.setBackground(round(SURFACE2,18,1,OUTLINE));LinearLayout factsTop=new LinearLayout(this);factsTop.setGravity(Gravity.CENTER_VERTICAL);if(g.rating!=null)factsTop.addView(text("BGG ★ "+String.format(Locale.ITALY,"%.1f",g.rating),15,YELLOW,Typeface.BOLD),new LinearLayout.LayoutParams(0,-2,1));if(g.rank!=null)factsTop.addView(text("#"+g.rank+" BGG",14,TEXT,Typeface.BOLD));bggFacts.addView(factsTop);String cats=shortCategories(g.categories);if(!cats.isEmpty()){TextView ct=text(cats,13,TEXT,Typeface.NORMAL);ct.setPadding(0,dp(8),0,0);bggFacts.addView(ct);}StringBuilder quick=new StringBuilder();String pl=playerLabel(g);if(!pl.isEmpty())quick.append("👥 ").append(pl);if(g.playtime!=null){if(quick.length()>0)quick.append("   ");quick.append("◷ ").append(g.playtime).append(" min");}if(g.weight!=null){if(quick.length()>0)quick.append("   ");quick.append("⚙ ").append(String.format(Locale.ITALY,"%.1f",g.weight)).append("/5");}if(quick.length()>0){TextView qt=text(quick.toString(),13,MUTED,Typeface.BOLD);qt.setPadding(0,dp(8),0,0);bggFacts.addView(qt);}LinearLayout.LayoutParams bfp=new LinearLayout.LayoutParams(-1,-2);bfp.topMargin=dp(12);box.addView(bggFacts,bfp);
        Integer total=g.paidCents==null?null:PurchaseMath.total(g.paidCents,g.shippingCents,g.feeCents);if(total==null)total=g.paidCents;Integer market=libraryMarketReference(g);LinearLayout purchase=verticalCard();purchase.setPadding(dp(14),dp(12),dp(14),dp(12));purchase.addView(text("Il tuo acquisto",13,MUTED,Typeface.BOLD));if(total!=null)purchase.addView(text(money(total)+(TextUtils.isEmpty(purchaseQuality)?"":" · "+purchaseQuality),25,LIME,Typeface.BOLD));if(market!=null&&total!=null){int delta=Math.round((market-total)*100f/market);purchase.addView(text("Riferimento mercato "+money(market)+" · "+(delta>=0?"hai risparmiato circa "+delta+"%":"circa "+Math.abs(delta)+"% sopra il riferimento"),13,delta>=0?TEAL:ORANGE,Typeface.BOLD));}if(g.bundlePurchase&&!TextUtils.isEmpty(g.bundleLabel))purchase.addView(text(g.bundleLabel+(g.bundleTotalCents==null?"":" · totale acquisto "+money(g.bundleTotalCents)),13,MUTED,Typeface.BOLD));if(!TextUtils.isEmpty(g.source))purchase.addView(text("Comprato su "+g.source,13,MUTED,Typeface.NORMAL));LinearLayout.LayoutParams pup=new LinearLayout.LayoutParams(-1,-2);pup.topMargin=dp(12);box.addView(purchase,pup);
        LinearLayout actions=new LinearLayout(this);actions.addView(providerAction(R.drawable.provider_bgg_logo,"BGG","Scheda gioco",BGG_BG,!TextUtils.isEmpty(g.bggId),()->openBgg(g.bggId)),new LinearLayout.LayoutParams(0,dp(72),1));Button edit=button("Modifica acquisto",SURFACE2);edit.setTextColor(CYAN);edit.setOnClickListener(v->{d.dismiss();BggSearchClient.Game game=new BggSearchClient.Game();game.id=g.bggId;game.name=g.name;game.imageUrl=g.imageUrl;game.rating=g.rating;game.rank=g.rank;game.voters=g.voters;game.qualityScore=g.qualityScore;game.weight=g.weight;game.playtime=g.playtime;game.minPlayers=g.minPlayers;game.maxPlayers=g.maxPlayers;game.categories=g.categories;game.editionId=g.editionId;game.editionName=g.editionLabel;purchaseSource(game);});LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(0,dp(72),1);ep.leftMargin=dp(10);if(!"sold".equals(g.collectionState))actions.addView(edit,ep);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(72));ap.topMargin=dp(16);box.addView(actions,ap);Button stateAction=button("sold".equals(g.collectionState)?"Rimetti in Libreria":"Segna come venduto",SURFACE2);stateAction.setTextColor("sold".equals(g.collectionState)?TEAL:ORANGE);stateAction.setOnClickListener(v->{if("sold".equals(g.collectionState)){Dialog confirm=bottomSheet("Rimetti in Libreria");LinearLayout confirmBox=confirm.findViewById(SHEET_ID);confirmBox.addView(text("Il gioco torna nella collezione. Data, motivo e prezzo di questa vendita vengono rimossi.",14,MUTED,Typeface.NORMAL));Button restore=button("Conferma",LIME);restore.setOnClickListener(x->saveLibraryChange(g,()->libraryDb.restoreOwned(g.bggId),confirm,d,restore));confirmBox.addView(restore,new LinearLayout.LayoutParams(-1,dp(52)));confirm.show();}else markLibraryGameSold(g,d);});LinearLayout.LayoutParams sap=new LinearLayout.LayoutParams(-1,dp(52));sap.topMargin=dp(12);box.addView(stateAction,sap);TextView delete=text("Elimina definitivamente",14,RED,Typeface.BOLD);delete.setGravity(Gravity.CENTER);delete.setMinHeight(dp(52));delete.setOnClickListener(v->confirmDeleteLibraryGame(g,d));box.addView(delete);
        sc.setOnApplyWindowInsetsListener((view,insets)->{Rect safe=contentSafeInsets(insets);box.setPadding(dp(20)+safe.left,dp(16)+safe.top,dp(20)+safe.right,dp(24)+safe.bottom);return insets;});d.setContentView(sc);d.show();sc.requestApplyInsets();sc.post(()->sc.scrollTo(0,restoreY));Window w=d.getWindow();if(w!=null){w.setLayout(-1,-1);w.setStatusBarColor(BG);w.setNavigationBarColor(BG);}
    }
    // ---------- LIBRERIA ----------
    private void renderLibrary(){
        List<LibraryGame> allGames=libraryDb.all();prepareLibraryRenderCaches(allGames);backfillLibraryBgg(allGames);List<LibraryGame> owned=new ArrayList<>(),sold=new ArrayList<>();for(LibraryGame g:allGames){if("sold".equals(g.collectionState))sold.add(g);else owned.add(g);}
        LinearLayout h=new LinearLayout(this);h.setGravity(Gravity.CENTER_VERTICAL);h.setPadding(0,dp(12),0,dp(12));LinearLayout title=new LinearLayout(this);title.setOrientation(LinearLayout.VERTICAL);title.addView(text("Libreria",30,TEXT,Typeface.BOLD));h.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView add=iconBadge(LudoIcons.PLUS,LIME);add.setTextColor(BG);add.setContentDescription("Aggiungi gioco alla Libreria");add.setOnClickListener(v->addLibraryGame());h.addView(add,new LinearLayout.LayoutParams(dp(48),dp(48)));body.addView(h);
        for(LibrarySearchJob job:new ArrayList<>(librarySearchJobs))body.addView(librarySearchJobCard(job));
        if(allGames.isEmpty()){body.addView(emptyState("La tua tana è vuota","Aggiungi i giochi che possiedi: da qui Ludo impara cosa ti piace.",R.drawable.ludo_value_book));return;}
        
        body.addView(librarySearchBar(),new LinearLayout.LayoutParams(-1,dp(54)));body.addView(libraryScopeTabs(owned.size(),sold.size()),new LinearLayout.LayoutParams(-1,dp(52)));
        List<LibraryGame> shown=new ArrayList<>("sold".equals(libraryScope)?sold:owned);if(!libraryQuery.trim().isEmpty()){String q=libraryQuery.trim().toLowerCase(Locale.ROOT);shown.removeIf(g->g==null||TextUtils.isEmpty(g.name)||!g.name.toLowerCase(Locale.ROOT).contains(q));}
        TextView section=text(("sold".equals(libraryScope)?"Venduti":"Collezione")+" · "+shown.size(),15,MUTED,Typeface.BOLD);section.setPadding(0,dp(12),0,dp(9));body.addView(section);
        if(shown.isEmpty()){LinearLayout e=verticalCard();e.setPadding(dp(16),dp(14),dp(16),dp(14));e.addView(text(libraryQuery.trim().isEmpty()?"Niente qui per ora":"Nessun gioco corrisponde alla ricerca",15,MUTED,Typeface.BOLD));body.addView(e);return;}
        for(LibraryGame g:shown)body.addView(libraryRow(g));
    }
    private View librarySummaryCard(List<LibraryGame> games){
        int spent=0,rated=0,market=0,marketKnown=0;for(LibraryGame g:games){if(g.personalRating!=null)rated++;if(g.paidCents!=null){Integer total=PurchaseMath.total(g.paidCents,g.shippingCents,g.feeCents);spent+=total==null?g.paidCents:total;}Integer ref=libraryMarketReference(g);if(ref!=null&&ref>0){market+=ref;marketKnown++;}}
        LinearLayout card=verticalCard();card.setPadding(dp(16),dp(14),dp(16),dp(14));LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.addView(text(games.size()+" giochi nella tua collezione",20,TEXT,Typeface.BOLD));copy.addView(text(rated+" valutati · "+Math.max(0,games.size()-rated)+" da votare",13,rated<games.size()?PINK:MUTED,Typeface.BOLD));top.addView(copy,new LinearLayout.LayoutParams(0,-2,1));ImageView ludo=new ImageView(this);ludo.setImageResource(R.drawable.ludo_value_book);ludo.setScaleType(ImageView.ScaleType.FIT_CENTER);top.addView(ludo,new LinearLayout.LayoutParams(dp(72),dp(72)));card.addView(top);
        LinearLayout stats=new LinearLayout(this);stats.setPadding(0,dp(12),0,0);stats.addView(libraryMetric(money(spent),"spesi"),new LinearLayout.LayoutParams(0,dp(66),1));LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,dp(66),1);mp.leftMargin=dp(8);stats.addView(libraryMetric(marketKnown==0?"n/d":money(market),"valore mercato · "+marketKnown+"/"+games.size()),mp);card.addView(stats);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(14);lp.bottomMargin=dp(12);card.setLayoutParams(lp);return card;
    }
    private View libraryMetric(String value,String label){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);box.setBackground(round(SURFACE2,15,0,0));TextView v=text(value,18,TEXT,Typeface.BOLD);v.setGravity(Gravity.CENTER);box.addView(v);TextView l=text(label,10,MUTED,Typeface.BOLD);l.setGravity(Gravity.CENTER);box.addView(l);return box;}
    private View librarySearchBar(){LinearLayout box=new LinearLayout(this);box.setGravity(Gravity.CENTER_VERTICAL);box.setPadding(dp(14),0,dp(8),0);box.setBackground(round(SURFACE2,18,1,OUTLINE));TextView icon=appIcon(LudoIcons.SEARCH,14,MUTED);box.addView(icon,new LinearLayout.LayoutParams(dp(32),dp(32)));EditText q=new EditText(this);q.setSingleLine(true);q.setHint("Cerca nella tua collezione");q.setHintTextColor(MUTED);q.setTextColor(TEXT);q.setTextSize(15);q.setText(libraryQuery);q.setBackgroundColor(Color.TRANSPARENT);q.setPadding(dp(6),0,dp(4),0);q.setOnEditorActionListener((v,a,e)->{libraryQuery=v.getText().toString();render();return true;});box.addView(q,new LinearLayout.LayoutParams(0,-1,1));TextView clear=appIcon(LudoIcons.XMARK,14,MUTED);clear.setGravity(Gravity.CENTER);clear.setVisibility(TextUtils.isEmpty(libraryQuery)?View.GONE:View.VISIBLE);clear.setOnClickListener(v->{libraryQuery="";render();});box.addView(clear,new LinearLayout.LayoutParams(dp(36),dp(36)));return box;}
    private View libraryScopeTabs(int owned,int sold){LinearLayout tabs=new LinearLayout(this);tabs.setPadding(dp(4),dp(4),dp(4),dp(4));tabs.setBackground(round(SURFACE2,16,1,OUTLINE));addLibraryScopeTab(tabs,"Collezione · "+owned,"owned");addLibraryScopeTab(tabs,"Venduti · "+sold,"sold");return tabs;}
    private void addLibraryScopeTab(LinearLayout tabs,String label,String value){boolean on=value.equals(libraryScope);TextView t=text(label,13,on?TEXT:MUTED,Typeface.BOLD);t.setGravity(Gravity.CENTER);t.setBackground(round(on?LIME:Color.TRANSPARENT,12,0,0));t.setOnClickListener(v->{libraryScope=value;render();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);if(tabs.getChildCount()>0)lp.leftMargin=dp(4);tabs.addView(t,lp);}

private View libraryRow(LibraryGame g){
        LinearLayout row=new LinearLayout(this);row.setPadding(dp(12),dp(14),dp(12),dp(14));row.setGravity(Gravity.CENTER_VERTICAL);row.setBackground(round(SURFACE,18,1,OUTLINE));row.addView(libraryCover(g),new LinearLayout.LayoutParams(dp(94),dp(112)));LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(14),0,0,0);TextView title=text(g.name,18,TEXT,Typeface.BOLD);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);copy.addView(title);
        TextView personal=text(libraryPersonalRatingLabel(g.personalRating),17,g.personalRating==null?MUTED:PINK,Typeface.BOLD);personal.setPadding(0,dp(7),0,dp(5));copy.addView(personal);if(g.rating!=null)copy.addView(text("BGG "+String.format(Locale.ITALY,"%.1f",g.rating)+(g.rank==null?"":" · #"+g.rank),12,MUTED,Typeface.NORMAL));
        if("sold".equals(g.collectionState)){TextView sale=text(g.salePriceCents==null?"Venduto · prezzo da inserire":"Venduto · "+money(g.salePriceCents),14,ORANGE,Typeface.BOLD);sale.setPadding(0,dp(6),0,0);copy.addView(sale);}
        row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));row.setContentDescription(g.name+". Il tuo voto: "+libraryPersonalRatingLabel(g.personalRating));row.setOnClickListener(v->openLibraryDetail(g));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(10);row.setLayoutParams(lp);return row;
    }
    private String parseBggId(String raw){if(TextUtils.isEmpty(raw))return"";String x=raw.trim();if(x.matches("[0-9]+"))return x;Matcher m=Pattern.compile("(?:boardgame/|bgg\\.cc/(?:boardgame/)?)([0-9]+)",Pattern.CASE_INSENSITIVE).matcher(x);if(m.find())return m.group(1);Matcher any=Pattern.compile("(?:^|/)([0-9]{2,})(?:/|$|[?#])").matcher(x);String last="";while(any.find())last=any.group(1);return last;}
    private void fetchBggDirect(String raw,java.util.function.Consumer<BggSearchClient.Game> done){String id=parseBggId(raw);if(TextUtils.isEmpty(id)){Toast.makeText(this,"Incolla un link BGG o un ID valido.",Toast.LENGTH_LONG).show();return;}BggSearchClient.Game local=bggSearch.localById(id);if(!bggSearch.configured()){if(local!=null){done.accept(local);return;}Toast.makeText(this,"Per questo ID serve la connessione BGG.",Toast.LENGTH_LONG).show();return;}OperationCenter.running(this,"library-direct:"+id,OperationCenter.MATCH,"Apro BGG #"+id);bggSearch.details(id,false,new BggSearchClient.Callback(){public void ok(List<BggSearchClient.Game> games){runOnUiThread(()->{OperationCenter.done(MainActivity.this,"library-direct:"+id,OperationCenter.MATCH,"Gioco BGG trovato");if(games!=null&&!games.isEmpty())done.accept(games.get(0));else if(local!=null)done.accept(local);else Toast.makeText(MainActivity.this,"BGG non ha restituito il gioco.",Toast.LENGTH_LONG).show();});}public void error(String e){runOnUiThread(()->{OperationCenter.error(MainActivity.this,"library-direct:"+id,OperationCenter.MATCH,"BGG #"+id,e);if(local!=null)done.accept(local);else Toast.makeText(MainActivity.this,e,Toast.LENGTH_LONG).show();});}});}
    private void addLibraryGame(){addLibraryGame("");}
    private void addLibraryGame(String initial){
        ensureLibraryWizardSession();libraryWizardStep="search";libraryWizardQuery=initial==null?"":initial;libraryWizardGame=null;libraryWizardSource="";wizardBundleGames.clear();
        Dialog d=wizardSheet("Aggiungi alla Libreria");libraryWizardDialog=d;LinearLayout box=d.findViewById(SHEET_ID);
        TextView intro=text("Il link BGG è il percorso più veloce. In alternativa usa foto o titolo.",15,MUTED,Typeface.NORMAL);intro.setPadding(0,dp(8),0,dp(14));box.addView(intro);
        EditText link=input("Link BGG oppure ID BGG");box.addView(link,new LinearLayout.LayoutParams(-1,dp(58)));Button open=button("Collega da BGG",CYAN);open.setOnClickListener(v->{String raw=link.getText().toString().trim();if(raw.isEmpty()){link.setError("Incolla un link o un ID BGG");return;}fetchBggDirect(raw,g->{d.dismiss();chooseEdition(g,this::libraryPurchaseDialog);});});LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,dp(52));op.topMargin=dp(8);box.addView(open,op);
        TextView or1=text("OPPURE",12,MUTED,Typeface.BOLD);or1.setGravity(Gravity.CENTER);or1.setPadding(0,dp(18),0,dp(10));box.addView(or1);
        View photo=wizardOption(R.drawable.ic_photo,"Da una foto","Crea una card e riconosce il gioco in background.",CYAN,()->{completeLibraryWizardSession();clearLibraryWizardState();d.dismiss();photoSearchOptions();});box.addView(photo);
        TextView or=text("OPPURE CERCA PER TITOLO",12,MUTED,Typeface.BOLD);or.setPadding(0,dp(22),0,dp(10));box.addView(or);
        EditText q=input("Titolo del gioco");q.setText(libraryWizardQuery);box.addView(q,new LinearLayout.LayoutParams(-1,dp(58)));
        Button go=button("Aggiungi alla coda",LIME);go.setOnClickListener(v->{String searchQuery=q.getText().toString().trim();if(searchQuery.isEmpty()){q.setError("Scrivi un titolo");return;}String id="text-search:"+System.nanoTime();LibrarySearchJob job=new LibrarySearchJob(id,searchQuery,null);librarySearchJobs.add(0,job);OperationCenter.queued(this,id,OperationCenter.MATCH,"Cerco "+searchQuery);completeLibraryWizardSession();clearLibraryWizardState();d.dismiss();tab="library";render();LIBRARY_SEARCH_NET.execute(()->{OperationCenter.running(MainActivity.this,id,OperationCenter.MATCH,"Cerco "+searchQuery);bggSearch.searchFast(searchQuery,new BggSearchClient.Callback(){public void ok(List<BggSearchClient.Game> games){runOnUiThread(()->{job.running=false;job.results.clear();if(games!=null)for(BggSearchClient.Game candidate:games)if(DealPolicy.ratingEligible(candidate.rating))job.results.add(candidate);if(job.results.isEmpty()){job.error="Non ho trovato un risultato sicuro";OperationCenter.error(MainActivity.this,id,OperationCenter.MATCH,searchQuery,"Serve una conferma manuale");}else OperationCenter.done(MainActivity.this,id,OperationCenter.MATCH,"Ho trovato "+job.results.size()+" possibili giochi");render();});}public void error(String e){runOnUiThread(()->{job.running=false;job.error="Ricerca non riuscita";OperationCenter.error(MainActivity.this,id,OperationCenter.MATCH,searchQuery,"Ricerca non riuscita");render();});}});});});LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,dp(54));gp.topMargin=dp(18);box.addView(go,gp);d.show();
    }
    private void chooseLibraryGame(List<BggSearchClient.Game> games){ensureLibraryWizardSession();List<BggSearchClient.Game> eligible=new ArrayList<>();if(games!=null)for(BggSearchClient.Game g:games)if(DealPolicy.ratingEligible(g.rating))eligible.add(g);games=eligible;if(games.isEmpty()){Toast.makeText(this,"Nessun risultato valido. I giochi con rating BGG noto sotto 6 non vengono proposti.",Toast.LENGTH_LONG).show();return;}Dialog dialog=wizardSheet("Scegli il gioco");libraryWizardDialog=dialog;libraryWizardStep="search";LinearLayout box=dialog.findViewById(SHEET_ID);TextView hint=text("Tocca il risultato corretto. I dettagli BGG verranno caricati solo dopo la scelta.",13,MUTED,Typeface.NORMAL);hint.setPadding(0,dp(8),0,dp(14));box.addView(hint);for(BggSearchClient.Game g:games){LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=dp(10);View row=gameChoiceRow(g,()->{dialog.dismiss();chooseEdition(g,this::libraryPurchaseDialog);});box.addView(row,rp);}dialog.show();}

    private void libraryPurchaseDialog(BggSearchClient.Game g){purchaseSource(g);}


    // ---------- DETAIL ----------
private void openDetail(DealRecord d){openDetail(d,false);}
    private void openDetail(DealRecord d,boolean preserveParent){
        if(d==null)return;final Dialog parent=preserveParent&&activeDetailDialog!=null&&activeDetailDialog.isShowing()?activeDetailDialog:null;final String parentSignature=parent==null?"":activeDealSignature;activeDealSignature=d.signature==null?"":d.signature;if(parent==null&&activeDetailDialog!=null&&activeDetailDialog.isShowing()){suppressDetailDismissState=true;activeDetailDialog.dismiss();suppressDetailDismissState=false;}Dialog dialog=new Dialog(this,android.R.style.Theme_Material_NoActionBar);activeDetailDialog=dialog;marketDetailDialogs.add(dialog);ScrollView sc=new ScrollView(this);sc.setBackground(productPageBackground());LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(6),dp(20),dp(28));sc.addView(box);
        MarketStore.Job refreshJob=marketStore.activeJobForLegacySignature(d.signature);boolean missingNow=needsDataRefresh(d);GameRecord game=TextUtils.isEmpty(d.bggId)?null:marketStore.gameStatsByBggId(d.bggId);

        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);TextView back=appIcon(LudoIcons.CHEVRON_LEFT,24,TEXT);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->dialog.dismiss());toolbar.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));toolbar.addView(text("Annuncio",17,TEXT,Typeface.BOLD),new LinearLayout.LayoutParams(0,-2,1));
        TextView correct=roundIconButton("?",CYAN);correct.setContentDescription("Correggi il gioco associato");correct.setOnClickListener(v->showMatchCorrection(d,dialog));toolbar.addView(correct,new LinearLayout.LayoutParams(dp(38),dp(38)));
        TextView more=roundIconButton("⋯",TEXT);more.setContentDescription("Altre azioni");more.setOnClickListener(v->showDetailActions(d,dialog,missingNow,refreshJob));LinearLayout.LayoutParams mlp=new LinearLayout.LayoutParams(dp(38),dp(38));mlp.leftMargin=dp(6);toolbar.addView(more,mlp);box.addView(toolbar);

        FrameLayout hero=new FrameLayout(this);hero.addView(dealProductArtwork(d),new FrameLayout.LayoutParams(-1,-1));LinearLayout.LayoutParams hlp=new LinearLayout.LayoutParams(-1,productArtworkHeight());hlp.topMargin=dp(8);box.addView(hero,hlp);
        View photos=listingPhotoThumbnails(d);if(photos!=null){LinearLayout.LayoutParams photosParams=new LinearLayout.LayoutParams(-1,dp(72));photosParams.topMargin=dp(8);box.addView(photos,photosParams);}


        LinearLayout signalRow=new LinearLayout(this);signalRow.setGravity(Gravity.CENTER_VERTICAL);signalRow.setPadding(0,dp(16),0,0);TextView decision=text(DealEvaluator.evaluate(d).label.toUpperCase(Locale.ITALY),13,dealAccent(d),Typeface.BOLD);decision.setMaxLines(2);signalRow.setOrientation(getResources().getConfiguration().fontScale>1.2f?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);signalRow.addView(decision);signalRow.addView(new Space(this),new LinearLayout.LayoutParams(0,1,1));signalRow.addView(publicationText(d,12,Typeface.NORMAL));box.addView(signalRow);

        LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);TextView title=text(name(d),29,TEXT,Typeface.BOLD);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);titleRow.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams trp=new LinearLayout.LayoutParams(-1,-2);trp.topMargin=dp(6);box.addView(titleRow,trp);
        if(game!=null){TextView gameLink=text("Scheda gioco",14,TEXT,Typeface.BOLD);gameLink.setGravity(Gravity.CENTER_VERTICAL);gameLink.setCompoundDrawablesWithIntrinsicBounds(iconDrawable(LudoIcons.BOOK_OPEN,TEXT,16),null,iconDrawable(LudoIcons.CHEVRON_RIGHT,MUTED,14),null);gameLink.setCompoundDrawablePadding(dp(8));gameLink.setPadding(dp(12),0,dp(12),0);gameLink.setBackground(round(SURFACE2,12,0,0));gameLink.setContentDescription("Apri la scheda del gioco "+game.name);gameLink.setOnClickListener(v->openGameDetailOverlay(game.id));LinearLayout.LayoutParams gameLp=new LinearLayout.LayoutParams(-1,dp(48));gameLp.topMargin=dp(10);box.addView(gameLink,gameLp);}

        box.addView(productRatingRow(d.rating,scoreLabel(d),()->{if(!TextUtils.isEmpty(d.bggId))openBgg(d.bggId);},()->showLudoScoreInfo(d)));
        box.addView(productLanguagePanel(d.languageCode,()->editListingInfo(d,dialog)));
        if(!TextUtils.isEmpty(d.bggId))box.addView(productInterestControls(d));

        View tags=linkedDealTagStrip(d,game);if(tags!=null){LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,dp(38));tp.topMargin=dp(13);box.addView(tags,tp);}

        LinearLayout moneyRow=new LinearLayout(this);moneyRow.setGravity(Gravity.BOTTOM);moneyRow.setOrientation(getResources().getConfiguration().fontScale>1.2f?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);moneyRow.setPadding(0,dp(20),0,0);LinearLayout priceCol=new LinearLayout(this);priceCol.setOrientation(LinearLayout.VERTICAL);priceCol.addView(text(total(d),39,LIME,Typeface.BOLD));TextView savingBadge=discoverDiscountBadge(d,12);if(savingBadge!=null)priceCol.addView(savingBadge);moneyRow.addView(priceCol,getResources().getConfiguration().fontScale>1.2f?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1));if(d.offerCents!=null){LinearLayout offer=new LinearLayout(this);offer.setOrientation(LinearLayout.VERTICAL);offer.setPadding(dp(12),dp(8),dp(12),dp(8));offer.setBackground(round(SURFACE2,13,0,0));offer.addView(text("Offerta",10,MUTED,Typeface.BOLD));offer.addView(text(money(d.offerCents),19,TEXT,Typeface.BOLD));moneyRow.addView(offer);}box.addView(moneyRow);

        boolean hasVinted=!TextUtils.isEmpty(d.vintedUrl);View vintedLink=providerLinkCard(R.drawable.provider_vinted_logo,"Vinted",hasVinted?"Apri annuncio":"Collega annuncio",VINTED_BG,false);vintedLink.setOnClickListener(v->{if(hasVinted)openVinted(d);else openVintedRecoveryForDeal(d,dialog);});LinearLayout.LayoutParams vlp=new LinearLayout.LayoutParams(-1,-2);vlp.topMargin=dp(14);box.addView(vintedLink,vlp);

        if(missingNow){LinearLayout missing=new LinearLayout(this);missing.setGravity(Gravity.CENTER_VERTICAL);missing.setPadding(dp(12),dp(10),dp(12),dp(10));missing.setBackground(round(SURFACE2,13,0,0));LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.addView(text(refreshJob==null?"Dati incompleti":MarketStore.PROCESSING.equals(refreshJob.state)?"Aggiornamento in corso":"Aggiornamento in coda",12,TEXT,Typeface.BOLD));copy.addView(text(refreshJob!=null?jobStage(refreshJob):missingDataSummary(d),11,MUTED,Typeface.NORMAL));missing.addView(copy,new LinearLayout.LayoutParams(0,-2,1));TextView refresh=text(refreshJob==null?"Aggiorna":"Priorità",12,CYAN,Typeface.BOLD);refresh.setGravity(Gravity.CENTER);refresh.setOnClickListener(v->requestDealRefresh(d));missing.addView(refresh,new LinearLayout.LayoutParams(dp(74),dp(38)));LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);mp.topMargin=dp(12);box.addView(missing,mp);}

        String edition=db.edition(d.signature);LinearLayout facts=new LinearLayout(this);facts.setOrientation(LinearLayout.VERTICAL);if(!TextUtils.isEmpty(edition))facts.addView(infoRow("Edizione",edition));if(d.shippingVerifiedCents!=null)facts.addView(infoRow("Spedizione",money(d.shippingVerifiedCents)));if(facts.getChildCount()>0){LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,-2);fp.topMargin=dp(14);box.addView(facts,fp);}

        List<BundleSuggestion> bundles=bundleDb.forSource(d.signature,12);if(!bundles.isEmpty()&&bundleDealsForSource(d).size()>=2){TextView seller=text("Dallo stesso venditore",17,TEXT,Typeface.BOLD);seller.setPadding(0,dp(22),0,dp(8));box.addView(seller);box.addView(bundleModule(d,bundles,dialog));}
        addSavedAccessories(box,d);

        TextView photoStatus=text("",11,MUTED,Typeface.NORMAL);photoStatus.setPadding(0,dp(14),0,0);box.addView(photoStatus);refreshDetailPhotos(d,hero,photoStatus);
        LinearLayout refreshHost=new LinearLayout(this);refreshHost.setVisibility(View.GONE);TextView refreshStatus=text("",11,MUTED,Typeface.NORMAL);refreshDetailBgg(d,refreshHost,refreshStatus,false,hero);

        if(game!=null){TextView pullHint=text("La scheda gioco si sta preparando",12,MUTED,Typeface.BOLD);pullHint.setGravity(Gravity.CENTER_VERTICAL);pullHint.setMinHeight(dp(56));GamePullProgress progress=new GamePullProgress();progress.setBounds(0,0,dp(32),dp(32));pullHint.setCompoundDrawables(progress,null,null,null);pullHint.setCompoundDrawablePadding(dp(12));LinearLayout.LayoutParams php=new LinearLayout.LayoutParams(-1,-2);php.topMargin=dp(16);box.addView(pullHint,php);installPullToGame(sc,pullHint,game.id,dialog);sc.post(()->prepareGameTransition(dialog,game.id,pullHint));}

        sc.setOnApplyWindowInsetsListener((view,insets)->{Rect safe=contentSafeInsets(insets);box.setPadding(dp(20)+safe.left,dp(16)+safe.top,dp(20)+safe.right,dp(24)+safe.bottom);return insets;});dialog.setOnDismissListener(x->finishListingDetail(dialog,parent,parentSignature));dialog.setContentView(sc);sc.requestApplyInsets();dialog.show();Window w=dialog.getWindow();if(w!=null){w.setLayout(-1,-1);w.setStatusBarColor(BG);w.setNavigationBarColor(BG);}
    }

    private void finishListingDetail(Dialog dialog,Dialog parent,String parentSignature){
        marketDetailDialogs.remove(dialog);preparedGameOverlays.remove(dialog);
        if(activeDetailDialog!=dialog)return;
        activeDetailDialog=!suppressDetailDismissState&&parent!=null&&parent.isShowing()?parent:null;
        if(!suppressDetailDismissState&&activeResolutionDialog==null)activeDealSignature=activeDetailDialog==null?"":parentSignature;
        persistTransientUiSession();
    }

    private TextView bggPill(DealRecord d){TextView v=softPill("BGG "+String.format(Locale.ITALY,"%.1f",d.rating));Drawable logo=getDrawable(R.drawable.provider_bgg_logo).mutate();logo.setBounds(0,0,dp(20),dp(20));v.setCompoundDrawables(logo,null,null,null);v.setCompoundDrawablePadding(dp(6));return v;}
    private String languageCompact(String code){String x=code==null?"":code.toUpperCase(Locale.ROOT);if(x.startsWith("IT"))return"IT";if(x.startsWith("EN"))return"EN";if(x.startsWith("FR"))return"FR";if(x.startsWith("DE"))return"DE";if(x.startsWith("ES"))return"ES";if(x.contains("IND"))return"Indipendente";if(x.contains("DEP"))return"Lingua ?";return"Lingua ?";}
    private int languageMetaColor(String code){String x=code==null?"":code.toUpperCase(Locale.ROOT);return x.contains("DEP")||TextUtils.isEmpty(x)?ORANGE:MUTED;}
    private String tagVisualLabel(String raw){String t=raw==null?"":raw.trim();String l=t.toLowerCase(Locale.ROOT);if(l.contains("economic"))return"€  "+t;if(l.contains("farm"))return"◒  "+t;if(l.contains("industry")||l.contains("manufact"))return"⚙  "+t;if(l.endsWith("giocatori")||l.endsWith("gioc."))return"♙  "+t;if(l.endsWith(" min"))return"◷  "+t;return"◇  "+t;}
    private View linkedDealTagStrip(DealRecord d,GameRecord game){ArrayList<String> tags=new ArrayList<>();if(d!=null&&!TextUtils.isEmpty(d.bggCategories))for(String x:d.bggCategories.split(" · "))if(!TextUtils.isEmpty(x.trim()))tags.add(x.trim());if(d!=null&&d.minPlayers!=null)tags.add(d.minPlayers+(d.maxPlayers!=null&&!d.maxPlayers.equals(d.minPlayers)?"–"+d.maxPlayers:"")+" gioc.");if(d!=null&&d.playtime!=null)tags.add(d.playtime+" min");if(tags.isEmpty())return null;HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);for(int i=0;i<Math.min(6,tags.size());i++){String raw=tags.get(i);TextView chip=linkedTag(tagVisualLabel(raw));boolean navigable=!raw.endsWith("gioc.")&&!raw.endsWith(" min");if(navigable)chip.setOnClickListener(v->openGameTag(raw));else chip.setAlpha(.82f);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(34));if(i>0)lp.leftMargin=dp(7);row.addView(chip,lp);}hs.addView(row);return hs;}
    private static final class GamePullGesture {
        private float anchor,progress;private boolean touching,anchored;
        void down(float y,boolean atBottom){touching=true;anchored=atBottom;anchor=y;progress=0;}
        float move(float y,boolean atBottom,float distance){if(!touching)return 0;if(!atBottom){anchored=false;progress=0;return 0;}if(!anchored){anchor=y;anchored=true;progress=0;return 0;}progress=Math.max(0,Math.min(1,(anchor-y)/distance));return progress;}
        boolean release(float y,float distance,boolean ready){boolean complete=touching&&anchored&&progress>=1&&anchor-y>=distance&&ready;cancel();return complete;}
        void cancel(){touching=false;anchored=false;progress=0;}
    }
    private void installPullToGame(ScrollView sc,TextView hint,long gameId,Dialog detail){
        GamePullGesture gesture=new GamePullGesture();
        sc.setOnTouchListener((v,e)->{
            int action=e.getActionMasked();boolean ready=readyGameTransition(detail,gameId);
            boolean bottom=!sc.canScrollVertically(1);
            if(action==android.view.MotionEvent.ACTION_CANCEL||e.getPointerCount()!=1||!detail.isShowing()){gesture.cancel();updateGamePullHint(hint,0,ready);return false;}
            if(action==android.view.MotionEvent.ACTION_DOWN){gesture.down(e.getY(),bottom);updateGamePullHint(hint,0,ready);}
            else if(action==android.view.MotionEvent.ACTION_MOVE){float progress=gesture.move(e.getY(),bottom,dp(72f));updateGamePullHint(hint,ready?progress:0,ready);}
            else if(action==android.view.MotionEvent.ACTION_UP){boolean go=gesture.release(e.getY(),dp(72f),ready&&bottom);updateGamePullHint(hint,0,ready);if(go)showPreparedGameTransition(detail,gameId);}
            return false;
        });
    }
    private final class GamePullProgress extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF circle=new RectF();private float progress;
        void setProgress(float value){progress=value;invalidateSelf();}
        @Override public void draw(Canvas canvas){Rect r=getBounds();circle.set(r.left+dp(3),r.top+dp(3),r.right-dp(3),r.bottom-dp(3));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(3));paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(OUTLINE);canvas.drawOval(circle,paint);paint.setColor(CYAN);canvas.drawArc(circle,-90,360*progress,false,paint);}
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private void updateGamePullHint(TextView hint,float progress,boolean ready){
        Drawable[] drawables=hint.getCompoundDrawables();if(drawables[0] instanceof GamePullProgress)((GamePullProgress)drawables[0]).setProgress(progress);
        String label=!ready?"La scheda gioco si sta preparando":progress>=1?"Rilascia per aprire il gioco":progress>0?"Continua per aprire il gioco":"Continua verso l’alto per aprire il gioco";
        if(!label.contentEquals(hint.getText()))hint.setText(label);
        hint.setAlpha(ready?1f:.65f);hint.setContentDescription(label+(progress>0?" · "+Math.round(progress*100)+"%":""));
    }
    private static final class PreparedGameOverlay {final long gameId;Dialog dialog;PreparedGameOverlay(long id){gameId=id;}}
    private final Map<Dialog,PreparedGameOverlay> preparedGameOverlays=new HashMap<>();
    private boolean readyGameTransition(Dialog source,long gameId){PreparedGameOverlay ready=preparedGameOverlays.get(source);return !isFinishing()&&!isDestroyed()&&source.isShowing()&&activeDetailDialog==source&&(activeGameOverlay==null||!activeGameOverlay.isShowing())&&ready!=null&&ready.gameId==gameId&&ready.dialog!=null;}
    private void showPreparedGameTransition(Dialog source,long gameId){if(!readyGameTransition(source,gameId))return;PreparedGameOverlay ready=preparedGameOverlays.get(source);Dialog target=ready.dialog;ready.dialog=null;activeGameOverlay=target;showGameOverlayWindow(target);}
    private void prepareGameTransition(Dialog source,long gameId,TextView hint){
        if(isFinishing()||isDestroyed()||!source.isShowing())return;
        PreparedGameOverlay request=new PreparedGameOverlay(gameId);preparedGameOverlays.put(source,request);
        uiDataIo.execute(()->{GameRecord game=null;GameDetailData data=null;try{game=marketStore.gameStats(gameId);if(game!=null)data=loadGameDetailData(game);}catch(RuntimeException ignored){}final GameRecord g=game;final GameDetailData snapshot=data;
            runOnUiThread(()->{if(isFinishing()||isDestroyed()||!source.isShowing()||preparedGameOverlays.get(source)!=request)return;if(g==null||snapshot==null){hint.setText("Usa Scheda gioco per aprire il gioco");return;}request.dialog=buildPreparedGameOverlay(g,snapshot,()->prepareGameTransition(source,gameId,hint));updateGamePullHint(hint,0,true);});
        });
    }
    private Dialog buildPreparedGameOverlay(GameRecord game,GameDetailData snapshot,Runnable onClosed){
        Dialog dialog=new Dialog(this,android.R.style.Theme_Material_NoActionBar);ScrollView sc=new ScrollView(this);sc.setBackground(productPageBackground());LinearLayout host=new LinearLayout(this);host.setOrientation(LinearLayout.VERTICAL);host.setPadding(dp(20),dp(16),dp(20),dp(24));sc.addView(host);
        renderDatabaseDetailInto(host,game,dialog::dismiss,snapshot);
        sc.setOnApplyWindowInsetsListener((view,insets)->{Rect safe=contentSafeInsets(insets);host.setPadding(dp(20)+safe.left,dp(16)+safe.top,dp(20)+safe.right,dp(24)+safe.bottom);return insets;});
        dialog.setOnDismissListener(v->{if(activeGameOverlay==dialog)activeGameOverlay=null;if(onClosed!=null)onClosed.run();});dialog.setContentView(sc);return dialog;
    }
    private void showGameOverlayWindow(Dialog dialog){dialog.show();Window w=dialog.getWindow();if(w!=null){w.setLayout(-1,-1);w.setStatusBarColor(BG);w.setNavigationBarColor(BG);w.getDecorView().requestApplyInsets();}}
    private static final class GameDetailData {
        Integer quality,bggUsed,bggNew;MarketStore.BggMarketStats bggOnline;MarketStore.MarketReferenceStats vintedMarket;
        List<GameRecord> similar;List<MarketStore.PricePoint> series;List<MarketListingRecord> active;Map<MarketListingRecord,Integer> listingScores=new HashMap<>();
    }
    private GameDetailData loadGameDetailData(GameRecord g){
        GameDetailData data=new GameDetailData();data.quality=gameQualityScore(g);data.similar=similarGames(g,8);
        data.bggUsed=TextUtils.isEmpty(g.bggId)?null:bggSearch.localMarketReferenceCents(g.bggId);data.bggNew=TextUtils.isEmpty(g.bggId)?null:bggSearch.localNewMarketCents(g.bggId);
        data.bggOnline=TextUtils.isEmpty(g.bggId)?new MarketStore.BggMarketStats(0,null,null,0):marketStore.bggMarketStats(g.bggId);
        String policy=marketStore.marketLanguagePolicyForGame(g.id);data.vintedMarket=TextUtils.isEmpty(g.bggId)?new MarketStore.MarketReferenceStats(0,null,null):marketStore.localVintedReferenceStats(g.bggId,null,policy);
        data.series=marketStore.priceHistory(g.id,90);data.active=marketStore.listingsForGame(g.id,true,80);for(MarketListingRecord listing:data.active)data.listingScores.put(listing,marketStore.dealScore(g.id,listing.currentPriceCents));return data;
    }
    private View productRatingRow(Double rating,String quality,Runnable bggAction,Runnable qualityAction){
        LinearLayout row=new LinearLayout(this);boolean stacked=getResources().getConfiguration().fontScale>1.2f;row.setOrientation(stacked?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(12),0,dp(4));
        LinearLayout bgg=new LinearLayout(this);bgg.setGravity(Gravity.CENTER_VERTICAL);bgg.setMinimumHeight(dp(48));bgg.addView(appIcon(LudoIcons.STAR,18,YELLOW),new LinearLayout.LayoutParams(dp(28),dp(32)));bgg.addView(text(rating==null?"—":String.format(Locale.ITALY,"%.1f",rating),24,TEXT,Typeface.BOLD));TextView label=text("BGG",12,MUTED,Typeface.BOLD);label.setPadding(dp(8),0,dp(10),0);bgg.addView(label);bgg.setContentDescription(rating==null?"Voto BGG non disponibile":String.format(Locale.ITALY,"Voto BGG %.1f su 10. Apri BoardGameGeek",rating));bgg.setOnClickListener(v->bggAction.run());row.addView(bgg,stacked?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1));
        if(!TextUtils.isEmpty(quality)&&!"n/d".equals(quality)){TextView secondary=detailSecondaryAction("Ludo "+quality+"  ⓘ");secondary.setMinHeight(dp(48));secondary.setPadding(dp(12),0,dp(12),0);secondary.setContentDescription("Ludo Score "+quality+". Come viene calcolato");secondary.setOnClickListener(v->qualityAction.run());row.addView(secondary);}return row;
    }
    private View productLanguagePanel(String code,Runnable edit){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(12),dp(10),dp(12),dp(8));box.setBackground(round(SURFACE2,14,0,0));box.addView(text("Edizione · "+HomePresentation.editionName(code),14,TEXT,Typeface.BOLD));TextView dependence=text(HomePresentation.dependenceLabel(code),13,MUTED,Typeface.NORMAL);dependence.setPadding(0,dp(4),0,0);box.addView(dependence);
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(getResources().getConfiguration().fontScale>1.2f?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);TextView info=detailSecondaryAction("Lingua e testo nel gioco");info.setMinHeight(dp(48));info.setOnClickListener(v->showLanguageHelp());actions.addView(info,new LinearLayout.LayoutParams(getResources().getConfiguration().fontScale>1.2f?-1:0,-2,getResources().getConfiguration().fontScale>1.2f?0:1));TextView correct=detailSecondaryAction("Correggi edizione");correct.setMinHeight(dp(48));correct.setOnClickListener(v->edit.run());actions.addView(correct,new LinearLayout.LayoutParams(getResources().getConfiguration().fontScale>1.2f?-1:0,-2,getResources().getConfiguration().fontScale>1.2f?0:1));box.addView(actions);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);box.setLayoutParams(lp);return box;
    }
    private View productInterestControls(DealRecord d){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(0,dp(14),0,0);box.addView(text("Per i tuoi consigli",12,MUTED,Typeface.BOLD));LinearLayout row=new LinearLayout(this);boolean stacked=getResources().getConfiguration().fontScale>1.2f;row.setOrientation(stacked?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
        String key=HomeDiscoveryPolicy.key(d.bggId,d.signature);SharedPreferences prefs=getSharedPreferences("ludo_home_interest_v2",MODE_PRIVATE);TextView yes=detailSecondaryAction("Mi interessa"),no=detailSecondaryAction("Non mi interessa");
        Runnable update=()->{int vote=prefs.getInt(key,0);yes.setSelected(vote>0);no.setSelected(vote<0);yes.setText(vote>0?"✓ Mi interessa":"Mi interessa");no.setText(vote<0?"✓ Non mi interessa":"Non mi interessa");yes.setBackground(round(vote>0?OUTLINE:SURFACE2,12,0,0));no.setBackground(round(vote<0?OUTLINE:SURFACE2,12,0,0));};
        SharedPreferences.OnSharedPreferenceChangeListener changed=(preferences,changedKey)->{if(key.equals(changedKey))runOnUiThread(update);};box.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){prefs.registerOnSharedPreferenceChangeListener(changed);update.run();}public void onViewDetachedFromWindow(View v){prefs.unregisterOnSharedPreferenceChangeListener(changed);}});
        yes.setMinHeight(dp(48));no.setMinHeight(dp(48));yes.setOnClickListener(v->{int next=prefs.getInt(key,0)>0?0:1;setHomeInterest(d,next,true);update.run();});no.setOnClickListener(v->{int next=prefs.getInt(key,0)<0?0:-1;setHomeInterest(d,next,true);update.run();});update.run();
        row.addView(yes,stacked?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams lp=stacked?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=stacked?0:dp(8);lp.topMargin=stacked?dp(6):0;row.addView(no,lp);box.addView(row);return box;
    }
    private TextView detailSecondaryAction(String label){TextView v=text(label,13,CYAN,Typeface.BOLD);v.setGravity(Gravity.CENTER);v.setBackground(round(SURFACE2,14,0,0));return v;}
    private void showDetailActions(DealRecord d,Dialog detail,boolean missingNow,MarketStore.Job refreshJob){
        Dialog menu=bottomSheet("Azioni annuncio");LinearLayout box=menu.findViewById(SHEET_ID);
        if(missingNow){TextView refresh=menuAction(refreshJob==null?"Aggiorna dati":"Dai priorità ai dati",CYAN);refresh.setOnClickListener(v->{menu.dismiss();requestDealRefresh(d);});box.addView(refresh);}
        TextView edit=menuAction("Modifica dati",TEXT);edit.setOnClickListener(v->{menu.dismiss();editListingInfo(d,detail);});box.addView(edit);
        TextView wrong=menuAction("Correggi gioco associato",TEXT);wrong.setOnClickListener(v->{menu.dismiss();showMatchCorrection(d,detail);});box.addView(wrong);
        if(!isOwned(d)){TextView library=menuAction("Aggiungi alla Libreria",TEXT);library.setOnClickListener(v->{menu.dismiss();if(detail!=null&&detail.isShowing())detail.dismiss();BggSearchClient.Game game=gameFromDeal(d);if(game!=null&&!TextUtils.isEmpty(game.id))chooseEdition(game,this::libraryPurchaseDialog);else addLibraryGame(name(d));});box.addView(library);}
        TextView sold=menuAction("Segna annuncio come venduto",ORANGE);sold.setOnClickListener(v->{menu.dismiss();confirmManualSold(d,detail);});box.addView(sold);
        TextView hide=menuAction("Nascondi annuncio",RED);hide.setOnClickListener(v->{menu.dismiss();hideDeal(d,detail);});box.addView(hide);menu.show();
    }
    private TextView menuAction(String label,int color){TextView v=text(label,16,color,Typeface.BOLD);v.setGravity(Gravity.CENTER_VERTICAL);v.setMinHeight(dp(54));return v;}
    private TextView roundIconButton(String label,int color){TextView v=text(label,20,color,Typeface.BOLD);v.setGravity(Gravity.CENTER);v.setBackground(round(SURFACE2,999,1,OUTLINE));return v;}
    private TextView roundIconButton(String glyph,int color,boolean fontAwesome){TextView v=appIcon(glyph,16,color);v.setBackground(round(SURFACE2,999,1,OUTLINE));return v;}
    private TextView iconBadge(String glyph,int color){TextView v=appIcon(glyph,15,color==LIME?BG:TEXT);v.setBackground(round(color,999,0,0));return v;}
    private TextView scorePill(String label,int color){TextView v=text(label,13,BG,Typeface.BOLD);v.setGravity(Gravity.CENTER);v.setPadding(dp(13),0,dp(13),0);v.setBackground(round(color,999,0,0));return v;}
    private TextView softPill(String label){TextView v=text(label,12,TEXT,Typeface.BOLD);v.setGravity(Gravity.CENTER);v.setPadding(dp(12),0,dp(12),0);v.setBackground(round(SURFACE2,999,0,0));return v;}
    private View providerLinkCard(int iconRes,String title,String subtitle,int accent,boolean strong){LinearLayout root=new LinearLayout(this);root.setGravity(Gravity.CENTER_VERTICAL);root.setPadding(dp(12),dp(8),dp(14),dp(8));root.setBackground(round(strong?Color.rgb(17,48,53):SURFACE2,16,strong?1:0,strong?accent:OUTLINE));ImageView icon=new ImageView(this);icon.setImageResource(iconRes);icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);root.addView(icon,new LinearLayout.LayoutParams(dp(28),dp(28)));root.setMinimumHeight(dp(56));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);tx.setPadding(dp(10),0,0,0);tx.addView(text(title,14,TEXT,Typeface.BOLD));tx.addView(text(subtitle,11,strong?CYAN:MUTED,Typeface.NORMAL));root.addView(tx,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=text("↗",18,strong?CYAN:MUTED,Typeface.BOLD);root.addView(arrow);return root;}
    private LinearLayout entityLinkCard(GameRecord g){LinearLayout root=new LinearLayout(this);root.setGravity(Gravity.CENTER_VERTICAL);root.setPadding(dp(14),dp(12),dp(14),dp(12));root.setBackground(round(SURFACE,18,1,OUTLINE));ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.provider_bgg_logo);icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);root.addView(icon,new LinearLayout.LayoutParams(dp(38),dp(38)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);tx.setPadding(dp(12),0,0,0);tx.addView(text("Scheda gioco",11,MUTED,Typeface.BOLD));tx.addView(text(g.name,16,TEXT,Typeface.BOLD));root.addView(tx,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=appIcon(LudoIcons.CHEVRON_RIGHT,14,CYAN);root.addView(arrow);return root;}
    private View infoRow(String label,String value){LinearLayout r=new LinearLayout(this);r.setPadding(0,dp(9),0,dp(9));r.addView(text(label,13,MUTED,Typeface.NORMAL),new LinearLayout.LayoutParams(0,-2,1));TextView v=text(value,13,TEXT,Typeface.BOLD);v.setGravity(Gravity.END);r.addView(v);return r;}
    private LinearLayout linkedDealTags(DealRecord d,int max,Dialog parent){LinearLayout host=new LinearLayout(this);host.setOrientation(LinearLayout.VERTICAL);if(d==null)return host;ArrayList<String> tags=new ArrayList<>();if(!TextUtils.isEmpty(d.bggCategories))for(String x:d.bggCategories.split(" · "))if(!TextUtils.isEmpty(x.trim()))tags.add(x.trim());if(d.minPlayers!=null)tags.add(d.minPlayers+(d.maxPlayers!=null&&!d.maxPlayers.equals(d.minPlayers)?"–"+d.maxPlayers:"")+" giocatori");if(d.playtime!=null)tags.add(d.playtime+" min");int at=0;while(at<Math.min(max,tags.size())){LinearLayout row=new LinearLayout(this);for(int j=0;j<2&&at<Math.min(max,tags.size());j++,at++){String tag=tags.get(at);TextView chip=linkedTag(tag);if(tag.endsWith("giocatori")||tag.endsWith(" min"))chip.setOnClickListener(null);else chip.setOnClickListener(v->{if(parent!=null)parent.dismiss();openGameTag(tag);});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(38),1);if(j>0)lp.leftMargin=dp(8);row.addView(chip,lp);}LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(38));if(host.getChildCount()>0)rp.topMargin=dp(8);host.addView(row,rp);}return host;}
    private TextView linkedTag(String label){TextView v=text(label,12,TEXT,Typeface.BOLD);v.setGravity(Gravity.CENTER);v.setSingleLine(true);v.setEllipsize(TextUtils.TruncateAt.END);v.setPadding(dp(10),0,dp(10),0);v.setBackground(round(Color.rgb(25,50,58),999,1,OUTLINE));return v;}
    private void dismissMarketDetailStack(){ArrayList<Dialog> dialogs=new ArrayList<>(marketDetailDialogs);for(int i=dialogs.size()-1;i>=0;i--){Dialog dialog=dialogs.get(i);if(dialog.isShowing())dialog.dismiss();}marketDetailDialogs.clear();activeDetailDialog=null;activeDealSignature="";persistTransientUiSession();}
    private void openGameTag(String tag){if(TextUtils.isEmpty(tag))return;dismissMarketDetailStack();if(activeGameOverlay!=null&&activeGameOverlay.isShowing())activeGameOverlay.dismiss();databaseQuery=tag;databaseScope="verified";databaseVisible=24;selectedGameId=0;databaseDetailReturnTab="";if(!"database".equals(tab)){navigate("database");}else{scheduleRender(0);uiUpdates.postDelayed(()->{if(scroll!=null)scroll.scrollTo(0,0);},30);}}
    private void showLudoScoreInfo(DealRecord d){Dialog x=bottomSheet("Ludo Score · "+scoreLabel(d));LinearLayout b=x.findViewById(SHEET_ID);b.addView(text("Non è la media BGG. Ludo combina classifica BGG, Bayesian/geek rating, numero di votanti e voto medio per ridurre l’effetto dei giochi di nicchia con poche valutazioni.",14,MUTED,Typeface.NORMAL));b.addView(infoRow("Classifica",d.rank==null?"n/d":"#"+d.rank));b.addView(infoRow("Voto BGG",d.rating==null?"n/d":String.format(Locale.ITALY,"%.2f",d.rating)));b.addView(infoRow("Votanti",d.voters==null?"n/d":String.format(Locale.ITALY,"%,d",d.voters)));b.addView(infoRow("Pesi","rank 55% · geek 20% · votanti 15% · media 10%"));x.show();}
    private Integer gameQualityScore(GameRecord g){if(g==null||TextUtils.isEmpty(g.bggId))return null;BggSearchClient.Game local=bggSearch.localById(g.bggId);if(local!=null&&local.qualityScore!=null)return local.qualityScore;return QualityComposite.score(g.rank,null,g.rating,g.voters);}
    private void showGameScoreInfo(GameRecord g,int score){Dialog x=bottomSheet("Ludo Score · "+String.format(Locale.ITALY,"%.1f",score/10.0));LinearLayout b=x.findViewById(SHEET_ID);b.addView(text("Un punteggio di qualità più robusto del solo voto medio. Il rank pesa di più; il numero di votanti e la correzione Bayesian riducono i falsi entusiasmi su campioni piccoli.",14,MUTED,Typeface.NORMAL));b.addView(infoRow("BGG",g.rating==null?"n/d":String.format(Locale.ITALY,"%.2f",g.rating)));b.addView(infoRow("Rank",g.rank==null?"n/d":"#"+g.rank));b.addView(infoRow("Votanti",g.voters==null?"n/d":String.format(Locale.ITALY,"%,d",g.voters)));b.addView(infoRow("Pesi","rank 55% · geek 20% · votanti 15% · media 10%"));x.show();}
    private LinearLayout gameFactChips(GameRecord g){LinearLayout row=new LinearLayout(this);if(g.minPlayers!=null)row.addView(softPill(g.minPlayers+(g.maxPlayers==null?"":"–"+g.maxPlayers)+" gioc."),new LinearLayout.LayoutParams(0,dp(36),1));if(g.playtime!=null){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(36),1);lp.leftMargin=row.getChildCount()>0?dp(8):0;row.addView(softPill(g.playtime+" min"),lp);}if(g.weight!=null){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(36),1);lp.leftMargin=row.getChildCount()>0?dp(8):0;row.addView(softPill("Peso "+String.format(Locale.ITALY,"%.1f",g.weight)),lp);}return row;}
    private void addLinkedMetaSection(String title,String raw,int max){addLinkedMetaSection(body,title,raw,max);}
    private void addLinkedMetaSection(LinearLayout host,String title,String raw,int max){if(TextUtils.isEmpty(raw))return;TextView t=text(title,13,MUTED,Typeface.BOLD);t.setPadding(0,dp(18),0,dp(8));host.addView(t);HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(this);int n=0;for(String x:raw.split(" · |, ")){String tag=x.trim();if(tag.isEmpty()||n++>=max)continue;TextView chip=linkedTag(tagVisualLabel(tag));chip.setOnClickListener(v->openGameTag(tag));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(34));if(row.getChildCount()>0)lp.leftMargin=dp(7);row.addView(chip,lp);}hs.addView(row);host.addView(hs,new LinearLayout.LayoutParams(-1,dp(36)));}
    private Set<String> metaSet(String raw){Set<String> out=new HashSet<>();if(TextUtils.isEmpty(raw))return out;for(String x:raw.split(" · |, ")){String s=x.trim().toLowerCase(Locale.ROOT);if(!s.isEmpty())out.add(s);}return out;}
    private List<GameRecord> similarGames(GameRecord current,int limit){List<GameRecord> pool=marketStore.searchGamesAdvanced("",180,"verified",false,null,null,"rating");Set<String> cats=metaSet(current.categories),mechs=metaSet(current.mechanics);ArrayList<GameRecord> out=new ArrayList<>();Map<Long,Integer> score=new HashMap<>();for(GameRecord g:pool){if(g.id==current.id)continue;Set<String> c2=metaSet(g.categories),m2=metaSet(g.mechanics);int s=0;for(String x:cats)if(c2.contains(x))s+=3;for(String x:mechs)if(m2.contains(x))s+=4;if(s>0){score.put(g.id,s);out.add(g);}}out.sort((a,b)->{int x=Integer.compare(score.getOrDefault(b.id,0),score.getOrDefault(a.id,0));if(x!=0)return x;return Double.compare(b.rating==null?0:b.rating,a.rating==null?0:a.rating);});return out.size()>limit?new ArrayList<>(out.subList(0,limit)):out;}
    private View similarGameCard(GameRecord g){LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(8),dp(8),dp(8),dp(10));card.setBackground(round(SURFACE,18,0,0));ImageView art=new ImageView(this);art.setScaleType(ImageView.ScaleType.FIT_CENTER);setGameArtwork(art,g);card.addView(art,new LinearLayout.LayoutParams(-1,dp(132)));TextView title=text(g.name,14,TEXT,Typeface.BOLD);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);title.setPadding(0,dp(8),0,0);card.addView(title,new LinearLayout.LayoutParams(-1,dp(44)));String meta=(g.rating==null?"BGG n/d":"★ "+String.format(Locale.ITALY,"%.1f",g.rating))+(g.activeListingCount>0?" · "+g.activeListingCount+" attivi":"");card.addView(text(meta,11,MUTED,Typeface.NORMAL));card.setOnClickListener(v->openGameDetailOverlay(g.id));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(156),dp(214));lp.rightMargin=dp(10);card.setLayoutParams(lp);return card;}
    private Dialog fullScreenPanel(String title){return fullScreenPanel(title,null,null);}
    private Dialog fullScreenPanel(String title,String action,Runnable actionRun){Dialog d=new Dialog(this,android.R.style.Theme_Material_NoActionBar);LinearLayout root=new LinearLayout(this);root.setId(SHEET_ID+90);root.setOrientation(LinearLayout.VERTICAL);root.setBackground(discoverHomeBackground());LinearLayout head=new LinearLayout(this);head.setId(SHEET_ID+91);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(20),dp(8),dp(20),dp(8));TextView close=roundIconButton(LudoIcons.XMARK,TEXT,true);close.setOnClickListener(v->d.dismiss());head.addView(close,new LinearLayout.LayoutParams(dp(40),dp(40)));TextView h=text(title,24,TEXT,Typeface.BOLD);h.setPadding(dp(14),0,0,0);head.addView(h,new LinearLayout.LayoutParams(0,-2,1));if(!TextUtils.isEmpty(action)){TextView a=text(action,14,CYAN,Typeface.BOLD);a.setId(SHEET_ID+92);a.setGravity(Gravity.CENTER);a.setOnClickListener(v->{if(actionRun!=null)actionRun.run();});head.addView(a,new LinearLayout.LayoutParams(-2,dp(40)));}root.addView(head,new LinearLayout.LayoutParams(-1,dp(60)));ScrollView sc=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setId(SHEET_ID);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(8),dp(20),dp(28));sc.addView(box);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));root.setOnApplyWindowInsetsListener((view,in)->{head.setPadding(dp(20),dp(8)+in.getSystemWindowInsetTop(),dp(20),dp(8));LinearLayout.LayoutParams hp=(LinearLayout.LayoutParams)head.getLayoutParams();hp.height=dp(60)+in.getSystemWindowInsetTop();head.setLayoutParams(hp);box.setPadding(dp(20),dp(8),dp(20),dp(24)+in.getSystemWindowInsetBottom());return in;});d.setContentView(root);d.setOnShowListener(x->{Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setLayout(-1,-1);w.setStatusBarColor(BG);w.setNavigationBarColor(BG);}root.requestApplyInsets();});return d;}
    private void setFullScreenPanelAction(Dialog d,Runnable action){if(d==null)return;TextView v=d.findViewById(SHEET_ID+92);if(v!=null)v.setOnClickListener(x->action.run());}
    private View filterRow(String title,String value,Runnable action){LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(LinearLayout.VERTICAL);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(16),dp(13),dp(12),dp(13));row.setBackground(round(SURFACE,16,1,OUTLINE));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);tx.addView(text(title,16,TEXT,Typeface.NORMAL));TextView summary=text(value,13,TextUtils.equals(value,"Qualsiasi")||TextUtils.equals(value,"Tutte")?MUTED:CYAN,Typeface.NORMAL);summary.setPadding(0,dp(2),0,0);tx.addView(summary);row.addView(tx,new LinearLayout.LayoutParams(0,-2,1));TextView chevron=appIcon(LudoIcons.CHEVRON_RIGHT,15,MUTED);chevron.setGravity(Gravity.CENTER);row.addView(chevron,new LinearLayout.LayoutParams(dp(30),dp(42)));row.setOnClickListener(v->action.run());row.setMinimumHeight(dp(68));wrap.addView(row,new LinearLayout.LayoutParams(-1,-2));wrap.setPadding(0,0,0,dp(10));return wrap;}
    private void showChoicePage(String title,String[] labels,String[] keys,String selected,java.util.function.Consumer<String> choose){Dialog child=fullScreenPanel(title);LinearLayout box=child.findViewById(SHEET_ID);for(int i=0;i<labels.length;i++){final int at=i;LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(11),0,dp(11));row.addView(text(labels[i],16,TEXT,Typeface.NORMAL),new LinearLayout.LayoutParams(0,-2,1));if(keys[i].equals(selected)){TextView check=appIcon(LudoIcons.CHECK,15,CYAN);check.setGravity(Gravity.CENTER);row.addView(check,new LinearLayout.LayoutParams(dp(40),dp(40)));}row.setOnClickListener(v->{choose.accept(keys[at]);child.dismiss();});box.addView(row,new LinearLayout.LayoutParams(-1,dp(58)));View line=new View(this);line.setBackgroundColor(OUTLINE);box.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));}child.show();}
    private void showPricePage(String title,Integer current,java.util.function.Consumer<Integer> choose){Dialog child=fullScreenPanel(title);LinearLayout box=child.findViewById(SHEET_ID);EditText value=input("Nessun limite");value.setInputType(8194);if(current!=null)value.setText(String.format(Locale.ITALY,"%.2f",current/100.0));box.addView(value,new LinearLayout.LayoutParams(-1,dp(56)));Button done=button("Conferma",LIME);LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(-1,dp(52));dpv.topMargin=dp(18);box.addView(done,dpv);TextView clear=text("Nessun limite",14,CYAN,Typeface.BOLD);clear.setGravity(Gravity.CENTER);clear.setMinHeight(dp(48));box.addView(clear);done.setOnClickListener(v->{String raw=value.getText().toString().trim();Integer cents=raw.isEmpty()?null:parseEuro(raw);if(!raw.isEmpty()&&cents==null){value.setError("Inserisci un importo valido");return;}choose.accept(cents);child.dismiss();});clear.setOnClickListener(v->{choose.accept(null);child.dismiss();});child.show();}
    private void showOtherFiltersPage(FilterDraft draft,Runnable changed){Dialog child=fullScreenPanel("Altri filtri");LinearLayout box=child.findViewById(SHEET_ID);CheckBox shipping=filterCheck("Spedizione verificata ≤ 3 €",draft.shipping);CheckBox bundle=filterCheck("Bundle disponibile",draft.bundle);CheckBox verify=filterCheck("Da verificare",draft.verify);box.addView(shipping);box.addView(bundle);box.addView(verify);TextView excluded=text("Annunci esclusi  ›",14,CYAN,Typeface.BOLD);excluded.setGravity(Gravity.CENTER_VERTICAL);excluded.setMinHeight(dp(52));excluded.setPadding(0,dp(8),0,0);excluded.setOnClickListener(v->{child.dismiss();showExcluded();});box.addView(excluded);Button done=button("Fatto",LIME);done.setOnClickListener(v->{draft.shipping=shipping.isChecked();draft.bundle=bundle.isChecked();draft.verify=verify.isChecked();child.dismiss();changed.run();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(52));lp.topMargin=dp(18);box.addView(done,lp);child.show();}
    private String filterRatingSummary(Double v){return v==null?"Qualsiasi":String.valueOf(v.intValue())+"+";}
    private String filterDiscountSummary(Integer v){return v==null?"Qualsiasi":v+"%+";}
    private String filterLanguageSummary(String v){if("IT".equals(v))return"Italiano";if("EN".equals(v))return"Inglese";if("FR".equals(v))return"Francese";if("DE".equals(v))return"Tedesco";if("ES".equals(v))return"Spagnolo";if("NL".equals(v))return"Olandese";if("PT".equals(v))return"Portoghese";if("IND".equals(v))return"Indipendente";if("unknown".equals(v))return"Non verificata";return"Tutte";}
    private String filterTypeSummary(String v){if("base".equals(v))return"Gioco base";if("expansion".equals(v))return"Espansione";return"Tutti";}
    private String filterLinkSummary(String v){if("complete".equals(v))return"Completi";if("incomplete".equals(v))return"Da completare";if("pending".equals(v))return"Link mancante";return"Qualsiasi";}
    private String filterGameScopeSummary(String v){if("review".equals(v))return"Da verificare";if("all".equals(v))return"Tutti";return"Verificati";}
    private TextView filterIntro(String value){TextView v=text(value,13,MUTED,Typeface.NORMAL);v.setLineSpacing(0,1.12f);v.setPadding(0,0,0,dp(20));return v;}
    private TextView filterGroupTitle(String value){TextView v=text(value,11,MUTED,Typeface.BOLD);v.setPadding(0,dp(20),0,dp(8));return v;}

    private void addRelatedListings(LinearLayout box,DealRecord current,Dialog detail){if(TextUtils.isEmpty(current.bggId))return;List<DealRecord> related=new ArrayList<>();for(DealRecord candidate:db.getDeals("all_with_review",1000))if(current.bggId.equals(candidate.bggId)&&!current.signature.equals(candidate.signature))related.add(candidate);if(related.isEmpty())return;related.sort(Comparator.comparingInt(d->effectiveTotal(d)==null?Integer.MAX_VALUE:effectiveTotal(d)));section(box,"ALTRI ANNUNCI DI QUESTO GIOCO");for(DealRecord d:related){Button row=button(name(d)+" · "+total(d),CYAN);row.setOnClickListener(v->{detail.dismiss();openDetail(d);});box.addView(row,new LinearLayout.LayoutParams(-1,dp(54)));}}
    private void addSavedAccessories(LinearLayout box,DealRecord d){if(TextUtils.isEmpty(d.bggId))return;List<AccessoryDatabase.Item> items=accessoryDb.forGame(d.bggId);if(items.isEmpty())return;section(box,"ACCESSORI SALVATI");for(AccessoryDatabase.Item a:items){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(10),dp(12),dp(10));row.setBackground(round(SURFACE2,16,1,OUTLINE));ImageView im=new ImageView(this);im.setScaleType(ImageView.ScaleType.CENTER_CROP);if(!TextUtils.isEmpty(a.imageUrl))loadRemote(im,a.imageUrl);row.addView(im,new LinearLayout.LayoutParams(dp(58),dp(58)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);tx.setPadding(dp(12),0,0,0);tx.addView(text(TextUtils.isEmpty(a.title)?"Accessorio":a.title,14,TEXT,Typeface.BOLD));tx.addView(text(a.priceCents>0?money(a.priceCents):"Articolo collegato",12,MUTED,Typeface.NORMAL));row.addView(tx,new LinearLayout.LayoutParams(0,-2,1));if(!TextUtils.isEmpty(a.url))row.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(a.url))));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=dp(8);box.addView(row,rp);}}
    private View bundleModule(DealRecord source,List<BundleSuggestion> list,Dialog detail){
        LinearLayout wrap=verticalCard();wrap.setPadding(dp(14),dp(14),dp(14),dp(14));wrap.setBackground(round(detailSurface(bundleAccent(bundleDealsForSource(source))),20,1,bundleAccent(bundleDealsForSource(source))));
        wrap.addView(text("Stesso venditore · una sola spedizione",13,MUTED,Typeface.BOLD));
        int shown=0;
        for(BundleSuggestion suggestion:list){
            DealRecord partner=dealForSuggestion(suggestion,source);
            if(partner==null||!DealPolicy.ratingEligible(partner)||partner.signature.equals(source.signature))continue;
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(10),0,dp(10));
            row.addView(dealArtworkView(partner,dp(62),dp(78)),new LinearLayout.LayoutParams(dp(62),dp(78)));
            LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(12),0,dp(6),0);copy.addView(text(name(partner),16,TEXT,Typeface.BOLD));String meta=total(partner)+(partner.rating==null?"":" · BGG "+String.format(Locale.ITALY,"%.1f",partner.rating));copy.addView(text(meta,13,MUTED,Typeface.NORMAL));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
            TextView arrow=appIcon(LudoIcons.CHEVRON_RIGHT,16,CYAN);arrow.setGravity(Gravity.CENTER);row.addView(arrow,new LinearLayout.LayoutParams(dp(38),dp(48)));
            row.setOnClickListener(v->openDetail(partner,true));wrap.addView(row);if(++shown>=3)break;
        }
        Button bundle=button("Vedi il bundle",PINK);bundle.setOnClickListener(v->openBundleDetail(source));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(50));bp.topMargin=dp(8);wrap.addView(bundle,bp);
        return wrap;
    }

    private DealRecord dealForSuggestion(BundleSuggestion suggestion,DealRecord source){
        DealRecord d=null;
        if(suggestion!=null&&!TextUtils.isEmpty(suggestion.itemId))d=db.findByVintedItemId(suggestion.itemId);
        if(d==null&&suggestion!=null&&!TextUtils.isEmpty(suggestion.title))d=db.findByVintedTitle(suggestion.title);
        if(d==null&&suggestion!=null&&!TextUtils.isEmpty(suggestion.bggId)){
            DealRecord candidate=db.findByBggId(suggestion.bggId);
            if(candidate!=null&&sameSeller(source,candidate))d=candidate;
        }
        if(d==null||!sameSeller(source,d))return null;
        return d;
    }
    private boolean sameSeller(DealRecord a,DealRecord b){
        if(a==null||b==null)return false;
        if(!TextUtils.isEmpty(a.sellerId)&&!TextUtils.isEmpty(b.sellerId))return a.sellerId.equals(b.sellerId);
        if(!TextUtils.isEmpty(a.sellerName)&&!TextUtils.isEmpty(b.sellerName))return a.sellerName.equalsIgnoreCase(b.sellerName);
        return false;
    }
    private double bundleGameScore(DealRecord d){if(d==null)return 0;if(d.qualityScore!=null)return d.qualityScore/10.0;return d.rating==null?0:d.rating;}
    private boolean bundleGameEligible(DealRecord d){if(d==null||!"ACTIVE".equals(d.lifecycle)||"verify".equals(d.tier)||!DealPolicy.ratingEligible(d)||!DealEvaluator.evaluate(d).visible())return false;double score=bundleGameScore(d);if(score<6.0)return false;String lc=d.languageCode==null?"":d.languageCode.toUpperCase(Locale.ROOT);return !(isForeignLanguage(lc)&&lc.contains("DEP"));}
    private double bundleQuality(List<DealRecord> games){if(games==null||games.isEmpty())return 0;double sum=0;for(DealRecord d:games)sum+=bundleGameScore(d);return sum/games.size();}
    private boolean hasLiveBundle(DealRecord source){return source!=null&&bundleDb.countForSource(source.signature)>0&&bundleDealsForSource(source).size()>=2;}
    private List<DealRecord> bundleDealsForSource(DealRecord source){
        LinkedHashMap<String,DealRecord> unique=new LinkedHashMap<>();
        if(source==null||!bundleGameEligible(source))return new ArrayList<>();
        unique.put(source.signature,source);
        for(BundleSuggestion suggestion:bundleDb.forSource(source.signature,60)){
            DealRecord partner=dealForSuggestion(suggestion,source);
            if(partner!=null&&bundleGameEligible(partner))unique.put(partner.signature,partner);
        }
        return new ArrayList<>(unique.values());
    }
    private String sellerKey(DealRecord d){
        if(d==null)return"";
        if(!TextUtils.isEmpty(d.sellerId))return"id:"+d.sellerId;
        List<BundleSuggestion> s=bundleDb.forSource(d.signature,1);
        if(!s.isEmpty()&&!TextUtils.isEmpty(s.get(0).sellerId))return"id:"+s.get(0).sellerId;
        if(!TextUtils.isEmpty(d.sellerName))return"name:"+d.sellerName.toLowerCase(Locale.ROOT);
        return"";
    }
    private List<DealRecord> uniqueBundleSources(List<DealRecord> input){
        LinkedHashMap<String,DealRecord> bySeller=new LinkedHashMap<>();
        if(input==null)return new ArrayList<>();
        for(DealRecord d:input){
            if(d==null||!DealPolicy.ratingEligible(d)||bundleDb.countForSource(d.signature)<=0)continue;
            String key=sellerKey(d);if(TextUtils.isEmpty(key))continue;
            if(bundleDealsForSource(d).size()<2)continue;
            DealRecord previous=bySeller.get(key);
            int q=d.qualityScore==null?0:d.qualityScore,old=previous==null||previous.qualityScore==null?0:previous.qualityScore;
            if(previous==null||q>old)bySeller.put(key,d);
        }
        List<DealRecord> out=new ArrayList<>(bySeller.values());
        out.sort((a,b)->Integer.compare(b.qualityScore==null?0:b.qualityScore,a.qualityScore==null?0:a.qualityScore));
        return out;
    }
    private Integer bundleItemSaving(List<DealRecord> games){
        if(games==null||games.size()<2)return null;
        long item=0,bench=0;
        for(DealRecord d:games){item+=Math.max(0,d.itemPriceCents);if(d.benchmarkCents==null||d.benchmarkCents<=0)return null;bench+=d.benchmarkCents;}
        if(bench<=0)return null;
        return(int)Math.round((bench-item)*100.0/bench);
    }
    private int bundleAccent(List<DealRecord> games){
        Integer pct=bundleItemSaving(games);
        if(pct!=null&&pct>=30)return PINK;if(pct!=null&&pct>=20)return ORANGE;if(pct!=null&&pct>=10)return TEAL;return PURPLE;
    }
    private int detailSurface(int accent){
        if(accent==PINK)return Color.rgb(38,20,35);if(accent==ORANGE)return Color.rgb(38,29,18);if(accent==TEAL)return Color.rgb(14,39,38);return Color.rgb(30,24,45);
    }
    private String bundleTitle(List<DealRecord> games){
        if(games==null||games.isEmpty())return"Bundle";
        if(games.size()==2)return name(games.get(0))+" + "+name(games.get(1));
        Map<String,Integer> counts=new LinkedHashMap<>();
        for(DealRecord g:games){if(TextUtils.isEmpty(g.bggCategories))continue;for(String raw:g.bggCategories.split(" · ")){String key=raw.trim();if(key.isEmpty())continue;counts.put(key,counts.getOrDefault(key,0)+1);}}
        String best=null;int bestCount=0;Set<String> generic=new HashSet<>(Arrays.asList("Card Game","Dice","Miniatures","Collectible Components","Novel-based","Book","Educational","Electronic","Print & Play"));
        for(Map.Entry<String,Integer> e:counts.entrySet()){if(generic.contains(e.getKey()))continue;if(e.getValue()>bestCount&&e.getValue()>=Math.max(2,(int)Math.ceil(games.size()*0.66))){best=e.getKey();bestCount=e.getValue();}}
        if(best!=null)return"Bundle "+bundleThemeLabel(best);
        DealRecord lead=games.get(0);for(DealRecord g:games)if(nz(g.qualityScore,0)>nz(lead.qualityScore,0))lead=g;
        return name(lead)+" + altri "+(games.size()-1);
    }
    private String bundleThemeLabel(String raw){if(raw==null)return"";switch(raw){case"Adventure":return"Avventura";case"Exploration":return"Esplorazione";case"Racing":return"Corse";case"Animals":return"Animali";case"Medieval":return"Medievale";case"Science Fiction":return"Fantascienza";case"Economic":return"Economia";case"Fighting":return"Combattimento";case"Civilization":return"Civiltà";case"Deduction":return"Deduzione";case"Party Game":return"Party";case"Wargame":return"Guerra";case"Trains":return"Treni";case"Pirates":return"Pirati";default:return raw;}}

    private void openBundleDetail(DealRecord source){
        List<DealRecord> games=bundleDealsForSource(source);if(games.size()<2){Toast.makeText(this,"Questo bundle non ha ancora abbastanza dati.",Toast.LENGTH_SHORT).show();return;}int accent=bundleAccent(games),bg=detailSurface(accent);BundlePlan plan=bundlePlan(games);Map<DealRecord,Integer> totalShares=plan.shippingEstimated?Collections.emptyMap():allocateBundleShares(games,plan.totalCents);
        Dialog dialog=new Dialog(this,android.R.style.Theme_Material_NoActionBar);ScrollView sc=new ScrollView(this);sc.setBackgroundColor(bg);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(12),dp(20),dp(36));sc.addView(box);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView back=appIcon(LudoIcons.CHEVRON_LEFT,25,TEXT);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->dialog.dismiss());head.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));head.addView(text("Bundle",20,TEXT,Typeface.BOLD),new LinearLayout.LayoutParams(0,-2,1));box.addView(head);
        FrameLayout hero=new FrameLayout(this);hero.setBackground(round(bg,26,2,accent));hero.setClipToOutline(true);ImageView scenic=new ImageView(this);scenic.setImageResource(R.drawable.ludo_bg_bundle_glow);scenic.setScaleType(ImageView.ScaleType.CENTER_CROP);scenic.setAlpha(.18f);hero.addView(scenic,new FrameLayout.LayoutParams(-1,-1));hero.addView(bundleMiniFan(games),new FrameLayout.LayoutParams(-1,-1));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,dp(210));hp.topMargin=dp(6);box.addView(hero,hp);
        TextView chip=materialChip("BUNDLE · "+games.size()+" GIOCHI",accent,accent==ORANGE||accent==TEAL?BG:TEXT,true);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(40));cp.topMargin=dp(14);box.addView(chip,cp);TextView title=text(bundleTitle(games),28,TEXT,Typeface.BOLD);title.setPadding(0,dp(8),0,0);box.addView(title);
        LinearLayout summary=new LinearLayout(this);summary.setGravity(Gravity.CENTER);summary.setPadding(dp(10),dp(14),dp(10),dp(14));summary.setBackground(round(SURFACE,20,1,accent));summary.addView(bundleMetric("OFFERTA",money(plan.offerSubtotal),LIME),new LinearLayout.LayoutParams(0,-2,1));summary.addView(bundleMetric("TOTALE","da verificare",TEXT),new LinearLayout.LayoutParams(0,-2,1));summary.addView(bundleMetric("RISPARMIO","n/d",MUTED),new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(14);box.addView(summary,sp);
        section(box,"I GIOCHI");for(DealRecord d:games){Integer totalShare=totalShares.get(d);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(9),dp(10),dp(9));row.setBackground(round(SURFACE,18,1,OUTLINE));row.addView(dealArtworkView(d,dp(64),dp(84)),new LinearLayout.LayoutParams(dp(64),dp(84)));LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(12),0,dp(6),0);copy.addView(text(name(d),17,TEXT,Typeface.BOLD));String meta="Annuncio "+money(d.itemPriceCents)+(totalShare==null?" · totale bundle da verificare":" · nel bundle ~"+money(totalShare));copy.addView(text(meta,13,MUTED,Typeface.NORMAL));if(totalShare!=null&&d.benchmarkCents!=null&&d.benchmarkCents>0){int delta=d.benchmarkCents-totalShare;copy.addView(text((delta>=0?"Risparmi ":"Sfori ")+money(Math.abs(delta)),12,delta>=0?accent:RED,Typeface.BOLD));}row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=appIcon(LudoIcons.CHEVRON_RIGHT,15,MUTED);arrow.setGravity(Gravity.CENTER);row.addView(arrow,new LinearLayout.LayoutParams(dp(34),dp(44)));row.setOnClickListener(v->openDetail(d,true));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=dp(8);box.addView(row,rp);}
        LinearLayout math=new LinearLayout(this);math.setOrientation(LinearLayout.VERTICAL);math.setVisibility(View.GONE);math.addView(kv("Prezzo richiesto",money(plan.askSubtotal)));math.addView(kv("Offerta consigliata",money(plan.offerSubtotal)+(plan.offerEstimated?" · stimata":"")));math.addView(kv("Protezione stimata",money(plan.feeCents)));math.addView(kv("Spedizione","Da verificare su Vinted"));if(plan.benchmarkCents>0)math.addView(kv("Riferimento giochi",money(plan.benchmarkCents)));TextView disclosure=text("Come è calcolato?  ⌄",14,MUTED,Typeface.BOLD);disclosure.setGravity(Gravity.CENTER_VERTICAL);disclosure.setMinHeight(dp(50));disclosure.setOnClickListener(v->{boolean show=math.getVisibility()!=View.VISIBLE;math.setVisibility(show?View.VISIBLE:View.GONE);disclosure.setText(show?"Nascondi calcolo  ⌃":"Come è calcolato?  ⌄");});box.addView(disclosure);box.addView(math);
        if(!TextUtils.isEmpty(source.sellerId)){Button seller=button("Esplora venditore su Vinted",VINTED_BG);seller.setOnClickListener(v->{BundleExploration.begin(this,source);recordAction("bundle:seller:"+source.signature);startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.vinted.it/member/"+source.sellerId)));});LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,dp(52));slp.topMargin=dp(10);box.addView(seller,slp);}sc.setOnApplyWindowInsetsListener((view,insets)->{int top=insets.getSystemWindowInsetTop(),bottom=insets.getSystemWindowInsetBottom();box.setPadding(dp(20),dp(12)+top,dp(20),dp(20)+bottom);return insets;});dialog.setContentView(sc);marketDetailDialogs.add(dialog);dialog.setOnDismissListener(x->marketDetailDialogs.remove(dialog));dialog.show();Window w=dialog.getWindow();if(w!=null){w.setLayout(-1,-1);w.setStatusBarColor(bg);w.setNavigationBarColor(bg);}
    }
    private View bundleMetric(String label,String value,int valueColor){LinearLayout col=new LinearLayout(this);col.setOrientation(LinearLayout.VERTICAL);col.setGravity(Gravity.CENTER);col.addView(text(label,10,MUTED,Typeface.BOLD));TextView v=text(value,20,valueColor,Typeface.BOLD);v.setPadding(0,dp(4),0,0);col.addView(v);return col;}
    private boolean needsDataRefresh(DealRecord d){
        if(d==null)return false;
        return TextUtils.isEmpty(d.vintedUrl)||TextUtils.isEmpty(d.publishedLabel)||TextUtils.isEmpty(d.sellerId)||TextUtils.isEmpty(d.bggId)||d.rating==null||TextUtils.isEmpty(d.bggImageUrl);
    }
    private String missingDataSummary(DealRecord d){
        List<String> fields=new ArrayList<>();if(TextUtils.isEmpty(d.vintedUrl))fields.add("link Vinted");else{if(TextUtils.isEmpty(d.publishedLabel))fields.add("data Vinted");if(TextUtils.isEmpty(d.sellerId))fields.add("venditore");}if(TextUtils.isEmpty(d.bggId))fields.add("match BGG");else{if(d.rating==null)fields.add("rating BGG");if(TextUtils.isEmpty(d.bggImageUrl))fields.add("cover BGG");}
        return fields.isEmpty()?"Dati essenziali completi":TextUtils.join(" · ",fields);
    }
    private void requestDealRefresh(DealRecord d){
        if(d==null)return;
        maintenanceIo.execute(()->{
            int result=0;try{result=marketStore.enqueueListingFromLegacy(d);QueueKeepAliveService.ensureRunning(this);QueueWorkScheduler.schedule(this);}catch(Throwable ignored){}
            final int state=result;runOnUiThread(()->{String message=state==1?"Aggiunto alle attività":state==2?"Priorità aumentata":"Dati già completi";Toast.makeText(this,message,Toast.LENGTH_SHORT).show();recordAction("refresh:item:"+d.signature);updateActivityIndicator();if("activity".equals(tab))render();});
        });
    }

    private void startMissingDataRefresh(){
        int available=marketStore.unqueuedIncompleteCount();
        if(available<=0){Toast.makeText(this,marketStore.incompleteListingCount()>0?"Tutti i dati mancanti sono già nelle attività":"Non ci sono dati mancanti",Toast.LENGTH_SHORT).show();return;}
        final String id="maintenance:missing-data";
        OperationCenter.queued(this,id,OperationCenter.MAINTENANCE,"Preparo i dati mancanti");
        requestNotificationPermission();recordAction("refresh:bulk:start");
        maintenanceIo.execute(()->{
            try{
                db.inferMissingLanguages();
                int queued=marketStore.prioritizeIncompleteListings(5000);
                queued+=marketStore.enqueueMissingLegacyDeals();
                final int queuedCount=queued;final int remaining=marketStore.unqueuedIncompleteCount();
                QueueKeepAliveService.ensureRunning(this);QueueWorkScheduler.schedule(this);
                runOnUiThread(()->{
                    OperationCenter.remove(this,id);
                    if(queuedCount>0)Toast.makeText(this,queuedCount+" attività aggiunte",Toast.LENGTH_SHORT).show();
                    else Toast.makeText(this,"Tutto è già nelle attività",Toast.LENGTH_SHORT).show();
                    if(!"activity".equals(tab))navigate("activity");else render();
                });
            }catch(Throwable t){runOnUiThread(()->{OperationCenter.remove(this,id);Toast.makeText(this,"Non sono riuscito a preparare i controlli. Riprova.",Toast.LENGTH_LONG).show();});}
        });
    }

    private String cleanPhotoTitle(String raw){
        if(raw==null)return"";String q=raw.replaceAll("[^\\p{L}\\p{N}'’&:+ -]"," ").replaceAll("\\s+"," ").trim();
        if(q.length()>70)q=q.substring(0,70).trim();return q;
    }
    private List<String> photoTitleCandidates(com.google.mlkit.vision.text.Text resultText){
        final class LineScore{String text;int score;LineScore(String t,int s){text=t;score=s;}}
        List<LineScore> scored=new ArrayList<>();int maxH=1,maxW=1,maxBottom=1;
        if(resultText!=null)for(com.google.mlkit.vision.text.Text.TextBlock block:resultText.getTextBlocks())for(com.google.mlkit.vision.text.Text.Line line:block.getLines()){android.graphics.Rect r=line.getBoundingBox();if(r!=null){maxH=Math.max(maxH,r.height());maxW=Math.max(maxW,r.width());maxBottom=Math.max(maxBottom,r.bottom);}}
        if(resultText!=null)for(com.google.mlkit.vision.text.Text.TextBlock block:resultText.getTextBlocks())for(com.google.mlkit.vision.text.Text.Line line:block.getLines()){String cleaned=cleanPhotoTitle(line.getText());if(!photoTitleCandidate(cleaned))continue;android.graphics.Rect r=line.getBoundingBox();int score=photoTitleScore(cleaned);if(r!=null){score+=(int)(42.0*r.height()/maxH);score+=(int)(12.0*r.width()/maxW);double center=Math.abs(((r.top+r.bottom)/2.0)/maxBottom-.5);score+=(int)(16*Math.max(0,1-center*2));}String n=cleaned.toLowerCase(Locale.ROOT);if(n.matches(".*\\b(games|game|edizioni|editions|studio|publisher|designer|illustrator|by|autori?|autore|illustrato|illustration)\\b.*"))score-=30;if(cleaned.split(" +").length>6)score-=18;scored.add(new LineScore(cleaned,score));}
        scored.sort((a,b)->Integer.compare(b.score,a.score));LinkedHashSet<String> out=new LinkedHashSet<>();for(LineScore x:scored){out.add(x.text);if(out.size()>=3)break;}return new ArrayList<>(out);
    }
    private String bestPhotoTitle(com.google.mlkit.vision.text.Text resultText){List<String> c=photoTitleCandidates(resultText);return c.isEmpty()?cleanPhotoTitle(resultText==null?"":resultText.getText()):c.get(0);}
    private boolean photoTitleCandidate(String line){return photoTitleScore(line)>=12;}
    private int photoTitleScore(String line){if(TextUtils.isEmpty(line))return 0;String n=line.toLowerCase(Locale.ROOT);if(n.matches(".*\\b(bgg|boardgamegeek|cover|cache|box|cm|ml|venduto|spedizione|vinted)\\b.*"))return 0;String[] parts=n.split(" +");int letters=0,digits=0;for(int i=0;i<n.length();i++){char c=n.charAt(i);if(Character.isLetter(c))letters++;else if(Character.isDigit(c))digits++;}int tokens=0;for(String p:parts)if(p.length()>=2)tokens++;int score=letters+tokens*8-digits*2;return tokens==0?0:score;}
    private List<PhotoMatch> rankPhotoMatches(Bitmap photo,String query,List<BggSearchClient.Game> games){
        List<BggSearchClient.Game> candidates=new ArrayList<>();if(games!=null)for(BggSearchClient.Game g:games)if(g!=null&&DealPolicy.ratingEligible(g.rating))candidates.add(g);
        candidates.sort((a,b)->Integer.compare(SearchRanking.score(query,b.name),SearchRanking.score(query,a.name)));if(candidates.size()>6)candidates=new ArrayList<>(candidates.subList(0,6));
        List<PhotoMatch> out=new ArrayList<>();
        for(BggSearchClient.Game g:candidates){
            double textScore=Math.max(0,Math.min(1,SearchRanking.score(query,g.name)/1000.0));if(textScore<.45)continue;
            Bitmap cover=null;File cached=TextUtils.isEmpty(g.id)?null:ArtworkStore.bggFile(this,g.id);if(cached!=null&&cached.exists())cover=decodeLocalBitmap(cached,640,640);if(cover==null&&!TextUtils.isEmpty(g.imageUrl))cover=downloadMatchBitmap(g.imageUrl);
            double visual=cover==null?0:VisualCoverMatcher.similarity(photo,cover);double score=.84*textScore+.16*visual;out.add(new PhotoMatch(g,visual,textScore,score));
        }
        out.sort((a,b)->Double.compare(b.score,a.score));return out;
    }
    private Bitmap downloadMatchBitmap(String url){
        HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(7000);c.setRequestProperty("User-Agent","LudoScout/5.9.4 Android");if(c.getResponseCode()!=200)return null;try(InputStream in=c.getInputStream()){return decodeRemote(in);}}catch(Exception e){return null;}finally{if(c!=null)c.disconnect();}
    }
    private List<PhotoMatch> fallbackVisualMatches(List<VisualCoverMatcher.Candidate> visual){
        List<PhotoMatch> out=new ArrayList<>();if(visual==null)return out;
        for(VisualCoverMatcher.Candidate c:visual){if(c.similarity<.84)continue;BggSearchClient.Game g=knownVisualGame(c.bggId);if(g==null||!DealPolicy.ratingEligible(g.rating))continue;out.add(new PhotoMatch(g,c.similarity,0,c.similarity));if(out.size()>=3)break;}return out;
    }
    private void renderPhotoMatches(Bitmap bitmap,List<PhotoMatch> matches,String query){
        Dialog confirm=bottomSheet("Questa foto sembra…");LinearLayout target=confirm.findViewById(SHEET_ID);ImageView preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.CENTER_CROP);preview.setImageBitmap(bitmap);preview.setClipToOutline(true);preview.setBackground(round(SURFACE2,18,0,0));target.addView(preview,new LinearLayout.LayoutParams(-1,dp(180)));
        TextView hint=text(TextUtils.isEmpty(query)?"Nessun titolo leggibile: mostro solo match visivi molto affidabili.":"Titolo riconosciuto · "+query,14,TextUtils.isEmpty(query)?MUTED:CYAN,Typeface.BOLD);hint.setPadding(0,dp(14),0,dp(8));target.addView(hint);
        int shown=0;for(PhotoMatch m:matches){if(shown>=5)break;if(m.game==null||TextUtils.isEmpty(m.game.id))continue;LinearLayout candidate=new LinearLayout(this);candidate.setGravity(Gravity.CENTER_VERTICAL);candidate.setPadding(dp(12),dp(10),dp(12),dp(10));candidate.setBackground(round(SURFACE2,18,1,OUTLINE));ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.FIT_CENTER);File cached=ArtworkStore.bggFile(this,m.game.id);if(cached.exists())cover.setImageBitmap(decodeLocalBitmap(cached,220,320));else if(!TextUtils.isEmpty(m.game.imageUrl))loadRemote(cover,m.game.imageUrl);else cover.setImageDrawable(iconDrawable(LudoIcons.IMAGE,MUTED,24));candidate.addView(cover,new LinearLayout.LayoutParams(dp(72),dp(92)));LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(12),0,0,0);copy.addView(text(m.game.name,17,TEXT,Typeface.BOLD));String meta=Math.round(m.score*100)+"% confidenza";if(m.text>0)meta+=" · titolo "+Math.round(m.text*100)+"%";if(m.visual>0)meta+=" · cover "+Math.round(m.visual*100)+"%";copy.addView(text(meta,13,MUTED,Typeface.NORMAL));if(m.game.rating!=null)copy.addView(text("BGG "+String.format(Locale.ITALY,"%.1f",m.game.rating)+" · #"+m.game.id,12,YELLOW,Typeface.BOLD));else copy.addView(text("BGG #"+m.game.id,12,MUTED,Typeface.NORMAL));candidate.addView(copy,new LinearLayout.LayoutParams(0,-2,1));candidate.setOnClickListener(v->{confirm.dismiss();selectVisualGame(m.game.id,m.game);});LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(8);target.addView(candidate,cp);shown++;}
        if(shown==0)target.addView(text("Nessun candidato abbastanza affidabile. Correggi il titolo qui sotto e cerca su BGG.",14,MUTED,Typeface.NORMAL));
        EditText text=input("Titolo del gioco");text.setText(query);target.addView(text,new LinearLayout.LayoutParams(-1,dp(54)));Button search=button("Cerca questo titolo",CYAN);search.setOnClickListener(v->{String q=text.getText().toString().trim();if(q.isEmpty()){text.setError("Inserisci il titolo");return;}confirm.dismiss();addLibraryGame(q);});target.addView(search,new LinearLayout.LayoutParams(-1,dp(52)));confirm.show();
    }

    private View photoStrip(DealRecord d){List<String> items=galleryItems(d);if(items.isEmpty())return null;HorizontalScrollView hsv=new HorizontalScrollView(this);hsv.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(this);int show=Math.min(5,items.size());for(int i=0;i<show;i++){final int index=i;ImageView im=photoThumb();setGalleryImage(im,items.get(i));im.setOnClickListener(v->showGallery(d,index));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(72),dp(72));if(i>0)lp.leftMargin=dp(7);row.addView(im,lp);}if(items.size()>show){TextView more=text("+"+(items.size()-show),16,TEXT,Typeface.BOLD);more.setGravity(Gravity.CENTER);more.setBackground(round(SURFACE2,14,0,0));more.setOnClickListener(v->showGallery(d,show));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(72),dp(72));lp.leftMargin=dp(7);row.addView(more,lp);}hsv.addView(row);return hsv;}

    // ---------- shared view helpers ----------
    private View tileCard(DealRecord d){DealEvaluator.Evaluation e=DealEvaluator.evaluate(d);LinearLayout c=verticalCard();c.setPadding(dp(10),dp(10),dp(10),dp(10));ImageView im=new ImageView(this);im.setScaleType(ImageView.ScaleType.FIT_CENTER);setBggArtwork(im,d);c.addView(im,new LinearLayout.LayoutParams(-1,dp(128)));c.addView(text(name(d),15,TEXT,Typeface.BOLD));c.addView(text(scoreLabel(d)+"  ·  "+total(d),13,LIME,Typeface.BOLD));c.addView(text(e.label,12,dealAccent(d),Typeface.BOLD));c.setOnClickListener(v->openDetail(d));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(154),dp(206));lp.rightMargin=dp(10);c.setLayoutParams(lp);return c;}
    private View bundleStrip(DealRecord d){
        List<DealRecord> games=bundleDealsForSource(d);if(games.size()<2)return tileCardV51(d);
        LinearLayout card=verticalCard();card.setPadding(dp(14),dp(14),dp(14),dp(14));card.setBackground(round(DISCOVER_SURFACE,16,1,DISCOVER_OUTLINE));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);String seller=TextUtils.isEmpty(d.sellerName)?"Venditore Vinted":"@"+d.sellerName;TextView sellerLabel=discoverTextWeight(seller,13,DISCOVER_MUTED,500);sellerLabel.setSingleLine(true);sellerLabel.setEllipsize(TextUtils.TruncateAt.END);header.addView(sellerLabel,new LinearLayout.LayoutParams(0,-2,1));
        TextView count=discoverTextWeight(games.size()+" giochi",11,DISCOVER_TEXT,600);count.setGravity(Gravity.CENTER);count.setPadding(dp(8),dp(5),dp(8),dp(5));count.setBackground(round(SURFACE2,8,0,0));LinearLayout.LayoutParams countLp=new LinearLayout.LayoutParams(-2,-2);countLp.leftMargin=dp(8);header.addView(count,countLp);card.addView(header,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout content=new LinearLayout(this);content.setGravity(Gravity.CENTER_VERTICAL);content.setPadding(0,dp(10),0,0);FrameLayout covers=new FrameLayout(this);
        int shown=Math.min(3,games.size());for(int i=shown-1;i>=0;i--){View cover=discoverBggCover(games.get(i),dp(56),dp(80),7,true);FrameLayout.LayoutParams coverLp=new FrameLayout.LayoutParams(dp(56),dp(80),Gravity.TOP|Gravity.START);coverLp.leftMargin=dp(i*28);covers.addView(cover,coverLp);}content.addView(covers,new LinearLayout.LayoutParams(dp(shown==2?84:112),dp(80)));
        TextView title=discoverTextWeight(bundleTitle(games),17,DISCOVER_TEXT,700);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);title.setPadding(dp(12),0,dp(6),0);content.addView(title,new LinearLayout.LayoutParams(0,-2,1));content.addView(appIcon(LudoIcons.CHEVRON_RIGHT,14,DISCOVER_MUTED),new LinearLayout.LayoutParams(dp(18),dp(32)));card.addView(content,new LinearLayout.LayoutParams(-1,-2));
        card.setContentDescription(seller+", "+games.size()+" giochi. "+bundleTitle(games)+". Apri bundle");card.setOnClickListener(v->openBundleDetail(d));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);card.setLayoutParams(lp);return card;
    }
    private View staleCard(){LinearLayout c=verticalCard();c.setPadding(dp(14),dp(8),dp(16),dp(8));c.setGravity(Gravity.CENTER_VERTICAL);ImageView im=new ImageView(this);im.setImageResource(R.drawable.ludo_sleeping_stack);im.setScaleType(ImageView.ScaleType.FIT_CENTER);c.addView(im,new LinearLayout.LayoutParams(dp(92),dp(92)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);tx.addView(text("zzZ. niente nuove scansioni",16,TEXT,Typeface.BOLD));tx.addView(text("Apri Vinted e fai un giro quando ti va.",13,MUTED,Typeface.NORMAL));c.addView(tx,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(112));lp.topMargin=dp(20);c.setLayoutParams(lp);return c;}
    private View emptyState(String title,String sub,int art){LinearLayout c=verticalCard();c.setGravity(Gravity.CENTER);c.setPadding(dp(18),dp(24),dp(18),dp(24));ImageView im=new ImageView(this);im.setImageResource(art);im.setScaleType(ImageView.ScaleType.FIT_CENTER);c.addView(im,new LinearLayout.LayoutParams(dp(170),dp(150)));TextView t=text(title,20,TEXT,Typeface.BOLD);t.setGravity(Gravity.CENTER);c.addView(t);TextView s=text(sub,14,MUTED,Typeface.NORMAL);s.setGravity(Gravity.CENTER);s.setPadding(0,dp(6),0,0);c.addView(s);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(18);c.setLayoutParams(lp);return c;}
    private void sectionTitle(String title,String action){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);TextView t=text(title,20,TEXT,Typeface.BOLD);r.addView(t,new LinearLayout.LayoutParams(0,-2,1));if(action!=null){TextView a=text(action,12,LIME,Typeface.BOLD);a.setOnClickListener(v->{tab="catalog";render();});r.addView(a);}r.setPadding(0,dp(20),0,dp(8));body.addView(r);}
private LinearLayout verticalCard(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setBackground(round(SURFACE,18,1,OUTLINE));l.setElevation(0);return l;}
private TextView pill(String s,int fill,int fg){return materialChip(s,fill,fg,false);}
    private TextView badge(String s,int color){return pill(s,color,color==LIME?BG:TEXT);}
    private int dealAccent(DealRecord d){if(d!=null&&"verify".equals(d.tier))return ORANGE;switch(DealEvaluator.evaluate(d).decision){case GREAT_BUY:return PINK;case GOOD_PRICE:return ORANGE;case OFFER:return YELLOW;case FAIR:return TEAL;case INSUFFICIENT_DATA:return CYAN;default:return RED;}}
    private int detailBackground(DealRecord d){int a=dealAccent(d);if(a==PINK)return Color.rgb(28,15,29);if(a==ORANGE)return Color.rgb(30,22,13);if(a==TEAL)return Color.rgb(10,29,29);if(a==RED)return Color.rgb(35,17,17);return BG;}
    private TextView statusBadge(DealRecord d){DealEvaluator.Evaluation e=DealEvaluator.evaluate(d);String label="verify".equals(d.tier)?"DA VERIFICARE":e.label.toUpperCase(Locale.ITALY);int accent=dealAccent(d);TextView v=pill(label,accent,accent==ORANGE||accent==YELLOW||accent==TEAL?BG:TEXT);int icon="verify".equals(d.tier)||e.decision==DealEvaluator.Decision.INSUFFICIENT_DATA?R.drawable.ic_deal_warning:e.decision==DealEvaluator.Decision.GREAT_BUY?R.drawable.ic_deal_bolt:e.decision==DealEvaluator.Decision.OFFER?R.drawable.ic_deal_drop:R.drawable.ic_deal_good;setStartIcon(v,icon,v.getCurrentTextColor(),18);v.setMinHeight(dp(44));return v;}
private View langChip(String c){View chip=languageIndicators(c);chip.setBackground(round(SURFACE2,999,1,OUTLINE));chip.setPadding(dp(10),0,dp(10),0);chip.setContentDescription(HomePresentation.languageLabel(c)+". Tocca per correggere");return chip;}

    private LinearLayout categoryChips(DealRecord d){LinearLayout row=new LinearLayout(this);if(TextUtils.isEmpty(d.bggCategories))return row;String[]a=d.bggCategories.split(" · ");for(int i=0;i<Math.min(2,a.length);i++){TextView v=materialChip(a[i],Color.rgb(43,48,70),TEXT,false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(34));if(i>0)lp.leftMargin=dp(8);row.addView(v,lp);}if(a.length>2){TextView m=materialChip("+"+(a.length-2),Color.rgb(43,48,70),TEXT,false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(34));lp.leftMargin=dp(8);row.addView(m,lp);}return row;}
private ImageView photoThumb(){ImageView i=new ImageView(this);i.setScaleType(ImageView.ScaleType.CENTER_CROP);i.setBackground(round(SURFACE2,14,0,0));i.setClipToOutline(true);return i;}

private View dealArtworkView(DealRecord d,int w,int h){FrameLayout box=new FrameLayout(this);box.setBackground(round(SURFACE2,14,0,0));box.setClipToOutline(true);ImageView backdrop=new ImageView(this);backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);backdrop.setAlpha(.30f);box.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));TextView ph=text(coverPlaceholder(d),11,MUTED,Typeface.BOLD);ph.setGravity(Gravity.CENTER);ph.setPadding(dp(8),dp(8),dp(8),dp(8));box.addView(ph,new FrameLayout.LayoutParams(-1,-1));ImageView im=new ImageView(this);im.setScaleType(ImageView.ScaleType.FIT_CENTER);im.setPadding(dp(3),dp(3),dp(3),dp(3));box.addView(im,new FrameLayout.LayoutParams(-1,-1));setDealArtwork(backdrop,null,d);setDealArtwork(im,ph,d);return box;}
    private String coverPlaceholder(DealRecord d){String n=name(d);if(TextUtils.isEmpty(n))return"cover\nin arrivo";String[]a=n.trim().split("\\s+");String x=a[0].substring(0,Math.min(1,a[0].length())).toUpperCase(Locale.ROOT);if(a.length>1)x+=a[1].substring(0,Math.min(1,a[1].length())).toUpperCase(Locale.ROOT);return x+"\ncover in arrivo";}
    private void setBggArtwork(ImageView im,DealRecord d){setDealArtwork(im,null,d);}
private void setDealArtwork(ImageView im,TextView placeholder,DealRecord d){
        im.setImageDrawable(null);im.setBackgroundColor(Color.TRANSPARENT);File bgg=TextUtils.isEmpty(d.bggId)?null:ArtworkStore.bggFile(this,d.bggId);
        if(bgg!=null&&bgg.exists()&&bgg.length()>1024){Bitmap b=decodeLocalBitmap(bgg,260,360);if(b!=null){im.setImageBitmap(b);if(placeholder!=null)placeholder.setVisibility(View.GONE);return;}}
        if(!TextUtils.isEmpty(d.bggImageUrl)){if(!TextUtils.isEmpty(d.bggId))ArtworkStore.downloadBgg(this,d.bggId,d.bggImageUrl);List<String> urls=new ArrayList<>();urls.add(d.bggImageUrl);if(!TextUtils.isEmpty(d.imageUrl))urls.add(d.imageUrl);String lp=firstListingPhoto(d);if(!TextUtils.isEmpty(lp)&&!urls.contains(lp))urls.add(lp);loadFirstRemote(im,urls,()->{if(placeholder!=null)placeholder.setVisibility(View.GONE);},()->setLocalFallback(im,placeholder,d));return;}
        setLocalFallback(im,placeholder,d);
    }
    private String firstListingPhoto(DealRecord d){if(TextUtils.isEmpty(d.listingPhotosCsv))return"";for(String u:d.listingPhotosCsv.split(","))if(!u.trim().isEmpty())return u.trim();return"";}
private void loadFirstRemote(ImageView im,List<String> urls,Runnable ok){loadFirstRemote(im,urls,ok,null);}
    private Bitmap decodeLocalBitmap(File file,int reqW,int reqH){
        if(file==null||!file.exists())return null;int target=Math.max(1,Math.max(reqW,reqH));int bucket=target<=420?420:target<=800?800:1600;String key="file:"+file.getAbsolutePath()+":"+file.lastModified()+":"+bucket;Bitmap cached=imageCache.get(key);if(cached!=null&&!cached.isRecycled())return cached;
        try{
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getAbsolutePath(),bounds);
            int sample=1;while(Math.max(bounds.outWidth,bounds.outHeight)/sample>bucket*2)sample*=2;
            BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=Math.max(1,sample);opts.inPreferredConfig=Bitmap.Config.RGB_565;
            Bitmap decoded=BitmapFactory.decodeFile(file.getAbsolutePath(),opts);if(decoded!=null)imageCache.put(key,decoded);return decoded;
        }catch(OutOfMemoryError oom){getSharedPreferences("va_v3_diag",MODE_PRIVATE).edit().putString("lastImageMemoryWarning",file.getName()).apply();return null;}catch(Throwable ignored){return null;}
    }

    private void loadRemote(ImageView im,String u){if(TextUtils.isEmpty(u))return;loadFirstRemote(im,Collections.singletonList(u),null);}

    /** Only real listing photos: no text button or BGG cover in this rail. */
    private View listingPhotoThumbnails(DealRecord d){
        List<String> items=galleryItems(d);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(2),dp(4),dp(2));int number=0;
        for(int i=0;i<items.size();i++){String item=items.get(i);if(isBggPhoto(d,item))continue;final int galleryIndex=i;number++;
            FrameLayout tile=new FrameLayout(this);tile.setBackground(round(SURFACE2,10,1,OUTLINE));tile.setClipToOutline(true);tile.setContentDescription("Apri foto "+number+" dell’annuncio");tile.setFocusable(true);
            ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);tile.addView(image,new FrameLayout.LayoutParams(-1,-1));
            TextView pending=appIcon(LudoIcons.CAMERA,18,MUTED);pending.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);tile.addView(pending,new FrameLayout.LayoutParams(-1,-1));
            Object request=new Object();image.setTag(request);
            if(item.startsWith("file:"))galleryNet.execute(()->{Bitmap bitmap=decodeLocalBitmap(new File(item.substring(5)),160,160);runOnUiThread(()->{if(image.getTag()!=request||isFinishing()||isDestroyed())return;if(bitmap!=null){image.setImageBitmap(bitmap);pending.setVisibility(View.GONE);}});});
            else loadImageOn(galleryNet,image,Collections.singletonList(item),()->pending.setVisibility(View.GONE),null);
            tile.setForeground(new RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(65,255,255,255)),null,round(Color.WHITE,10,0,0)));tile.setOnClickListener(v->{int index=galleryItems(d).indexOf(item);showGallery(d,index>=0?index:galleryIndex);});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(64),dp(64));if(row.getChildCount()>0)lp.leftMargin=dp(8);row.addView(tile,lp);
        }
        if(number==0)return null;HorizontalScrollView rail=new HorizontalScrollView(this);rail.setHorizontalScrollBarEnabled(false);rail.addView(row);return rail;
    }
    private List<String> galleryItems(DealRecord d){List<String> out=new ArrayList<>();File bgg=TextUtils.isEmpty(d.bggId)?null:ArtworkStore.bggFile(this,d.bggId);if(bgg!=null&&bgg.exists()&&bgg.length()>1024)out.add("file:"+bgg.getAbsolutePath());else if(!TextUtils.isEmpty(d.bggImageUrl))out.add(d.bggImageUrl);if(!TextUtils.isEmpty(d.listingPhotosCsv))for(String u:d.listingPhotosCsv.split(",")){u=u.trim();if(!u.isEmpty()&&!out.contains(u))out.add(u);}File local=ThumbnailStore.fileFor(this,d.signature);if(out.isEmpty()&&local.exists()&&local.length()>1024){String p="file:"+local.getAbsolutePath();if(!out.contains(p))out.add(p);}if(!TextUtils.isEmpty(d.imageUrl)&&!out.contains(d.imageUrl))out.add(d.imageUrl);List<String> clean=new ArrayList<>();for(String item:out){if(item.startsWith("file:")){clean.add(item);continue;}String url=item.replace("&amp;","&").replace("\\/","/").replace("\\u002F","/");while(url.endsWith("\\"))url=url.substring(0,url.length()-1);try{java.net.URI uri=new java.net.URI(url);if(("https".equals(uri.getScheme())||"http".equals(uri.getScheme()))&&uri.getHost()!=null&&!clean.contains(url))clean.add(url);}catch(Exception ignored){}}return PhotoIdentity.unique(clean);}
    private void setGalleryImage(ImageView im,String item){if(item==null)return;im.setTag(new Object());im.setImageDrawable(null);if(item.startsWith("file:")){Bitmap b=decodeLocalBitmap(new File(item.substring(5)),1080,1440);if(b!=null)im.setImageBitmap(b);}else loadImageOn(galleryNet,im,Collections.singletonList(item),null,()->{im.setImageDrawable(iconDrawable(LudoIcons.TRIANGLE_EXCLAMATION,ORANGE,24));im.setContentDescription("Foto non caricata. Tocca per riprovare");im.setOnClickListener(v->setGalleryImage(im,item));});}
    private void showGallery(DealRecord d,int start){if(galleryItems(d).isEmpty())return;Dialog dialog=new Dialog(this,android.R.style.Theme_Material_NoActionBar);FrameLayout root=new FrameLayout(this);root.setBackgroundColor(BG);root.addView(galleryView(d,start,false),new FrameLayout.LayoutParams(-1,-1));TextView close=appIcon(LudoIcons.XMARK,20,TEXT);close.setGravity(Gravity.CENTER);close.setBackground(round(SURFACE2,999,0,0));close.setContentDescription("Chiudi galleria");close.setOnClickListener(v->dialog.dismiss());FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.END|Gravity.TOP);cp.topMargin=dp(20);cp.rightMargin=dp(12);root.addView(close,cp);root.setOnApplyWindowInsetsListener((view,in)->{root.setPadding(0,in.getSystemWindowInsetTop(),0,in.getSystemWindowInsetBottom());return in;});dialog.setContentView(root);dialog.show();root.requestApplyInsets();Window w=dialog.getWindow();if(w!=null){w.setLayout(-1,-1);w.setStatusBarColor(BG);w.setNavigationBarColor(BG);}}
    private View galleryView(DealRecord d,int start,boolean expand){List<String> items=galleryItems(d);FrameLayout root=new FrameLayout(this);root.setBackground(round(SURFACE2,20,0,0));root.setClipToOutline(true);if(items.isEmpty()){TextView empty=text("Foto non disponibili",16,MUTED,Typeface.NORMAL);empty.setGravity(Gravity.CENTER);root.addView(empty,new FrameLayout.LayoutParams(-1,-1));return root;}GalleryPager pager=new GalleryPager(this);ArrayList<ImageView> views=new ArrayList<>();for(String item:items){FrameLayout page=new FrameLayout(this);ImageView image=!expand?new ZoomImageView(this):new ImageView(this);image.setScaleType(isBggPhoto(d,item)?ImageView.ScaleType.FIT_CENTER:ImageView.ScaleType.CENTER_CROP);image.setPadding(isBggPhoto(d,item)?dp(8):0,isBggPhoto(d,item)?dp(8):0,isBggPhoto(d,item)?dp(8):0,dp(58));views.add(image);page.addView(image,new FrameLayout.LayoutParams(-1,-1));pager.addPage(page);}root.addView(pager,new FrameLayout.LayoutParams(-1,-1));TextView count=text("",13,TEXT,Typeface.BOLD);count.setGravity(Gravity.CENTER);count.setBackground(round(SURFACE,999,0,0));FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(dp(210),dp(40),Gravity.CENTER_HORIZONTAL|Gravity.BOTTOM);cp.bottomMargin=dp(8);root.addView(count,cp);TextView prev=appIcon(LudoIcons.CHEVRON_LEFT,25,TEXT),next=appIcon(LudoIcons.CHEVRON_RIGHT,25,TEXT);prev.setGravity(Gravity.CENTER);next.setGravity(Gravity.CENTER);prev.setContentDescription("Foto precedente");next.setContentDescription("Foto successiva");root.addView(prev,new FrameLayout.LayoutParams(dp(48),dp(56),Gravity.START|Gravity.CENTER_VERTICAL));root.addView(next,new FrameLayout.LayoutParams(dp(48),dp(56),Gravity.END|Gravity.CENTER_VERTICAL));int[] selected={GalleryPosition.clamp(start,items.size())};boolean[] loaded=new boolean[items.size()];pager.setListener(index->{selected[0]=index;getSharedPreferences("va_v3_diag",MODE_PRIVATE).edit().putInt("galleryPage",index+1).putInt("galleryPages",items.size()).apply();root.setTag(index);count.setText((index+1)+" / "+items.size()+" · "+(isBggPhoto(d,items.get(index))?"Cover BGG":"Annuncio"));prev.setVisibility(index>0?View.VISIBLE:View.INVISIBLE);next.setVisibility(index<items.size()-1?View.VISIBLE:View.INVISIBLE);for(int i=0;i<items.size();i++){if(i==index&&!loaded[i]){loaded[i]=true;setGalleryImage(views.get(i),items.get(i));}else if(Math.abs(i-index)>1&&loaded[i]){views.get(i).setTag(new Object());views.get(i).setImageDrawable(null);loaded[i]=false;}}});prev.setOnClickListener(v->pager.go(selected[0]-1));next.setOnClickListener(v->pager.go(selected[0]+1));if(expand)for(ImageView image:views){image.setOnClickListener(v->showGallery(d,selected[0]));image.setContentDescription("Apri fotografia a schermo intero");}pager.post(()->pager.go(selected[0]));return root;}
    private boolean isBggPhoto(DealRecord d,String path){return path.equals(d.bggImageUrl)||(!TextUtils.isEmpty(d.bggId)&&path.equals("file:"+ArtworkStore.bggFile(this,d.bggId).getAbsolutePath()));}
    private Button button(String s,int color){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(15);b.setTypeface(discoverTypeface(500));b.setTextColor(color==SURFACE||color==SURFACE2||color==BG?TEXT:BG);b.setBackground(round(color,999,0,0));b.setMinHeight(dp(48));return b;}
    private View kv(String k,String v){LinearLayout r=new LinearLayout(this);r.setPadding(0,dp(8),0,dp(8));r.addView(text(k,14,MUTED,Typeface.NORMAL),new LinearLayout.LayoutParams(0,-2,1));TextView x=text(v,14,TEXT,Typeface.BOLD);x.setGravity(Gravity.END);r.addView(x);return r;}
    private void section(LinearLayout b,String s){TextView t=text(s,11,MUTED,Typeface.BOLD);t.setPadding(0,dp(22),0,dp(6));b.addView(t);}


    private TextView materialChip(String s,int fill,int fg,boolean strong){TextView v=text(s,strong?13:12,fg,Typeface.BOLD);v.setGravity(Gravity.CENTER);v.setMinWidth(dp(54));v.setPadding(dp(16),0,dp(16),0);v.setBackground(round(fill,999,fill==SURFACE2?1:0,fill==SURFACE2?Color.rgb(35,60,67):fill));return v;}
    private GradientDrawable verticalGradient(int top,int bottom){return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{top,bottom});}
    private void setLocalFallback(ImageView im,TextView placeholder,DealRecord d){File local=ThumbnailStore.fileFor(this,d.signature);if(local.exists()&&local.length()>1024){Bitmap b=decodeLocalBitmap(local,260,360);if(b!=null){im.setImageBitmap(b);if(placeholder!=null)placeholder.setVisibility(View.GONE);return;}}List<String> urls=new ArrayList<>();String first=firstListingPhoto(d);if(!TextUtils.isEmpty(first))urls.add(first);if(!TextUtils.isEmpty(d.imageUrl)&&!urls.contains(d.imageUrl))urls.add(d.imageUrl);if(!urls.isEmpty())loadFirstRemote(im,urls,()->{if(placeholder!=null)placeholder.setVisibility(View.GONE);},null);}
    private void loadFirstRemote(ImageView im,List<String> urls,Runnable ok,Runnable fail){loadImageOn(net,im,urls,ok,fail);}
    private void loadImageOn(ExecutorService executor,ImageView im,List<String> urls,Runnable ok,Runnable fail){Object request=new Object();im.setTag(request);executor.execute(()->{for(String u:urls){if(im.getTag()!=request)return;Bitmap cached=imageCache.get(u);if(cached!=null){runOnUiThread(()->{if(im.getTag()==request){im.setImageBitmap(cached);if(ok!=null)ok.run();}});return;}HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(8000);c.setInstanceFollowRedirects(true);c.setRequestProperty("User-Agent","Mozilla/5.0 Android LudoScout/5.2");int code=c.getResponseCode();if(executor==galleryNet)getSharedPreferences("va_v3_diag",MODE_PRIVATE).edit().putInt("galleryLastHttp",code).putString("galleryLastHost",new URL(u).getHost()).apply();if(code<200||code>=400)continue;Bitmap b=decodeRemote(c.getInputStream());if(b!=null){imageCache.put(u,b);runOnUiThread(()->{if(im.getTag()!=request)return;im.setImageBitmap(b);if(ok!=null)ok.run();});return;}}catch(Exception ignored){}finally{if(c!=null)c.disconnect();}}if(fail!=null)runOnUiThread(()->{if(im.getTag()==request)fail.run();});});}
    private Bitmap decodeRemote(java.io.InputStream input)throws Exception{
        File tmp=File.createTempFile("ludo-img-",".bin",getCacheDir());
        try(java.io.InputStream in=input;java.io.FileOutputStream out=new java.io.FileOutputStream(tmp)){byte[] b=new byte[16*1024];int n,total=0;while((n=in.read(b))!=-1){total+=n;if(total>10*1024*1024)throw new java.io.IOException("Foto troppo grande");out.write(b,0,n);}}
        try{return decodeLocalBitmap(tmp,520,720);}finally{try{tmp.delete();}catch(Throwable ignored){}}
    }

    private View providerAction(int iconRes,String title,String subtitle,int accent,boolean enabled,Runnable action){LinearLayout root=new LinearLayout(this);root.setGravity(Gravity.CENTER_VERTICAL);root.setPadding(dp(12),dp(8),dp(12),dp(8));root.setBackground(round(accent,20,0,0));root.setElevation(dp(5));root.setAlpha(enabled?1f:.38f);ImageView icon=new ImageView(this);icon.setImageResource(iconRes);icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);icon.setPadding(dp(iconRes==R.drawable.provider_vinted_logo?3:7),dp(iconRes==R.drawable.provider_vinted_logo?3:7),dp(iconRes==R.drawable.provider_vinted_logo?3:7),dp(iconRes==R.drawable.provider_vinted_logo?3:7));root.addView(icon,new LinearLayout.LayoutParams(dp(iconRes==R.drawable.provider_vinted_logo?52:46),dp(iconRes==R.drawable.provider_vinted_logo?52:46)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);tx.setPadding(dp(10),0,0,0);tx.addView(text(title,15,TEXT,Typeface.BOLD));tx.addView(text(subtitle,11,TEXT,Typeface.NORMAL));root.addView(tx,new LinearLayout.LayoutParams(0,-2,1));TextView go=text("↗",20,TEXT,Typeface.BOLD);root.addView(go);if(enabled)root.setOnClickListener(v->action.run());return root;}

    // ---------- ranking/data ----------
    private boolean isOwned(DealRecord d){if(TextUtils.isEmpty(d.bggId))return false;for(LibraryGame game:libraryDb.all())if(d.bggId.equals(game.bggId)&&!"sold".equals(game.collectionState))return true;return false;}
    private boolean resaleCandidate(DealRecord d){Integer saving=saving(d);return d.shippingVerifiedCents!=null&&saving!=null&&saving>=40;}
    private int decisionPriority(DealRecord d){switch(DealEvaluator.evaluate(d).decision){case GREAT_BUY:return 5;case GOOD_PRICE:return 4;case OFFER:return 3;case FAIR:return 2;case INSUFFICIENT_DATA:return 1;default:return 0;}}private double relevance(DealRecord d){double q=d.qualityScore==null?50:d.qualityScore;Integer sv=saving(d);double deal=sv==null?0:Math.max(-15,Math.min(35,sv));double rating=d.rating==null?6.0:d.rating;double freshness=Math.max(0,18-(System.currentTimeMillis()-d.firstSeen)/3_600_000.0);String lc=d.languageCode==null?"":d.languageCode.toUpperCase(Locale.ROOT);double friction=(d.shippingVerifiedCents!=null&&d.shippingVerifiedCents<=300?10:0)+((lc.startsWith("IT")||lc.contains("IND"))?6:0)+(!TextUtils.isEmpty(d.vintedUrl)?5:0)+(hasLiveBundle(d)?14:0);boolean foreign=isForeignLanguage(lc);double languagePenalty=foreign&&lc.contains("DEP")?260:foreign&&!lc.contains("IND")?55:TextUtils.isEmpty(lc)||(!lc.contains("DEP")&&!lc.contains("IND"))?18:0;return decisionPriority(d)*42+q*.24+deal*.10+rating*3.0+freshness*.35+friction-languagePenalty-(isOwned(d)&&!resaleCandidate(d)?90:0);}private boolean isForeignLanguage(String lc){return lc.startsWith("FR")||lc.startsWith("DE")||lc.startsWith("ES")||lc.startsWith("NL")||lc.startsWith("PT");}private boolean personalDealEligible(DealRecord d){String lc=d.languageCode==null?"":d.languageCode.toUpperCase(Locale.ROOT);if(isForeignLanguage(lc)&&lc.contains("DEP"))return false;if(isForeignLanguage(lc)&&!lc.contains("IND"))return false;return !TextUtils.isEmpty(lc)&&(lc.startsWith("IT")||lc.startsWith("EN")||lc.contains("IND"));}
    private DealRecord mostRecent(List<DealRecord>l){return l.stream().max(Comparator.comparingLong(d->d.firstSeen)).orElse(null);}private DealRecord bestBundleSource(List<DealRecord> l){
        List<DealRecord> unique=uniqueBundleSources(l);
        DealRecord best=null;int bestScore=Integer.MIN_VALUE;
        for(DealRecord d:unique){int score=d.qualityScore==null?0:d.qualityScore;Integer pct=bundleItemSaving(bundleDealsForSource(d));if(pct!=null)score+=Math.max(-30,Math.min(60,pct));if(score>bestScore){bestScore=score;best=d;}}
        return best;
    }
    private long daysSinceLastObservation(){SharedPreferences p=getSharedPreferences("va_v3_diag",MODE_PRIVATE);long t=p.getLong("lastEventAt",0);if(t<=0)return 0;return Math.max(0,System.currentTimeMillis()-t)/(24*60*60_000L);}private String scoreLabel(DealRecord d){return d.qualityScore==null?"—":String.format(Locale.ITALY,"%.1f",d.qualityScore/10.0);}private String bggScoreLabel(DealRecord d){return d.rating==null?"BGG n/d":String.format(Locale.ITALY,"BGG %.1f",d.rating);}private String bggMeta(DealRecord d){if(d.rating==null)return"Valutazione BGG non ancora disponibile";return String.format(Locale.ITALY,"Media reale %.2f / 10%s",d.rating,d.voters==null?"":" · "+String.format(Locale.ITALY,"%,d",d.voters)+" votanti");}private Integer effectiveTotal(DealRecord d){if(d.shippingVerifiedCents!=null){int base=d.protectedPriceCents!=null?d.protectedPriceCents:d.itemPriceCents+PurchaseMath.vintedFee(d.itemPriceCents);return base+d.shippingVerifiedCents;}return d.totalCents!=null?d.totalCents:d.protectedPriceCents;}private String total(DealRecord d){Integer t=effectiveTotal(d);return t==null?money(d.itemPriceCents):money(t);}private Integer saving(DealRecord d){Integer t=effectiveTotal(d);if(t==null||d.benchmarkCents==null||d.benchmarkCents<=0)return null;return(int)Math.round(Math.max(-999,Math.min(99,(d.benchmarkCents-t)*100.0/d.benchmarkCents)));}private String ageLabel(DealRecord d){long anchor=d.resolvedAt!=null?d.resolvedAt:(d.lastSeen>0?d.lastSeen:d.firstSeen);return RelativeTime.fromLabel(d.publishedLabel,anchor,System.currentTimeMillis());}
private String publicationDisplay(DealRecord d){String exact=ageLabel(d);return !TextUtils.isEmpty(exact)?humanRelative(exact):"Data pubblicazione n/d";}
private String marketPublicationDisplay(MarketListingRecord l){if(l==null||TextUtils.isEmpty(l.publishedLabel))return"Data pubblicazione n/d";long anchor=l.enrichedAt>0?l.enrichedAt:l.lastSeen;String exact=RelativeTime.fromLabel(l.publishedLabel,anchor,System.currentTimeMillis());return !TextUtils.isEmpty(exact)?humanRelative(exact):l.publishedLabel;}
private TextView publicationText(DealRecord d,float sp,int style){TextView t=text(publicationDisplay(d),sp,!TextUtils.isEmpty(ageLabel(d))?ageColor(d):MUTED,style);Runnable tick=new Runnable(){public void run(){if(!t.isAttachedToWindow())return;t.setText(publicationDisplay(d));t.setTextColor(!TextUtils.isEmpty(ageLabel(d))?ageColor(d):MUTED);t.postDelayed(this,60_000L);}};t.postDelayed(tick,60_000L);return t;}
private String humanRelative(String compact){if(TextUtils.isEmpty(compact))return"tempo n/d";String a=compact.trim();if("ora".equals(a))return"adesso";if(a.endsWith(" min")){String n=a.substring(0,a.indexOf(' '));return n+" min fa";}if(a.endsWith(" h")){String n=a.substring(0,a.indexOf(' '));return"1".equals(n)?"1 ora fa":n+" ore fa";}if(a.endsWith(" g")){String n=a.substring(0,a.indexOf(' '));return"1".equals(n)?"1 giorno fa":n+" giorni fa";}return a;}
private int ageColor(DealRecord d){String a=ageLabel(d);if(a.endsWith(" min")){try{long m=Long.parseLong(a.substring(0,a.indexOf(' ')));return m<=20?ORANGE:CYAN;}catch(Exception ignored){}}if(a.startsWith("1 h"))return ORANGE;return MUTED;}private String languageShort(String c){String code=c==null?"":c.toUpperCase(Locale.ROOT);String lang=code.startsWith("IT")?"Italiano":code.startsWith("EN")?"Inglese":code.startsWith("FR")?"Francese":code.startsWith("DE")?"Tedesco":code.startsWith("ES")?"Spagnolo":code.startsWith("NL")?"Olandese":code.startsWith("PT")?"Portoghese":"Lingua non verificata";String dep=code.contains("IND")?" · indipendente dalla lingua":code.contains("DEP")?" · dipendente dalla lingua":" · dipendenza n/d";return lang+dep;}private static String name(DealRecord d){return!TextUtils.isEmpty(d.displayName)?d.displayName:!TextUtils.isEmpty(d.gameName)?d.gameName:d.vintedTitle;}private static String safeName(BundleSuggestion b){return!TextUtils.isEmpty(b.gameName)?b.gameName:b.title;}private static int nz(Integer x,int d){return x==null?d:x;}private static String money(int c){return NumberFormat.getCurrencyInstance(Locale.ITALY).format(c/100.0);}private Integer parseEuro(String raw){try{if(TextUtils.isEmpty(raw))return null;java.math.BigDecimal value=new java.math.BigDecimal(raw.trim().replace(',','.'));if(value.signum()<0||value.compareTo(new java.math.BigDecimal("1000000"))>0)return null;return value.movePointRight(2).setScale(0,java.math.RoundingMode.HALF_UP).intValueExact();}catch(Exception e){return null;}}

    private void setStartIcon(TextView view,int res,Integer tint,float sizeDp){Drawable d=getResources().getDrawable(res);if(d==null)return;d=d.mutate();if(tint!=null)d.setColorFilter(tint,PorterDuff.Mode.SRC_IN);int size=dp(sizeDp);d.setBounds(0,0,size,size);view.setCompoundDrawables(d,null,null,null);view.setCompoundDrawablePadding(dp(8));}

    // Helpers retained from the stable engine shell.
    private void openVinted(DealRecord d){if(d==null||TextUtils.isEmpty(d.vintedUrl))return;persistTransientUiSession();long listingId=marketStore.listingIdForSignature(d.signature);if(listingId<=0&&!TextUtils.isEmpty(d.vintedItemId))listingId=marketStore.listingIdForVintedItemId(d.vintedItemId);if(listingId>0){String exactSig=marketStore.signatureForListing(listingId);marketStore.beginOpenedVintedTarget(listingId,TextUtils.isEmpty(exactSig)?d.signature:exactSig,name(d),2L*60_000L);}Uri uri=Uri.parse(d.vintedUrl);Intent appIntent=new Intent(Intent.ACTION_VIEW,uri);appIntent.addCategory(Intent.CATEGORY_BROWSABLE);appIntent.setPackage("fr.vinted");Intent webIntent=new Intent(Intent.ACTION_VIEW,uri);webIntent.addCategory(Intent.CATEGORY_BROWSABLE);try{if(appIntent.resolveActivity(getPackageManager())!=null)startActivity(appIntent);else if(webIntent.resolveActivity(getPackageManager())!=null)startActivity(webIntent);else Toast.makeText(this,"Nessuna app disponibile per aprire Vinted",Toast.LENGTH_SHORT).show();}catch(ActivityNotFoundException e){try{startActivity(webIntent);}catch(Exception ignored){Toast.makeText(this,"Impossibile aprire il link Vinted",Toast.LENGTH_SHORT).show();}}catch(Exception e){Toast.makeText(this,"Impossibile aprire il link Vinted",Toast.LENGTH_SHORT).show();}}
    private void openBgg(String id){if(TextUtils.isEmpty(id))return;persistTransientUiSession();startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://boardgamegeek.com/boardgame/"+id)));}

    private Dialog bottomSheet(String title){
        Dialog dialog=new Dialog(this,android.R.style.Theme_Material_Dialog_NoActionBar_MinWidth);ScrollView scrollBox=new ScrollView(this){
            @Override protected void onMeasure(int widthSpec,int heightSpec){int available=View.MeasureSpec.getSize(heightSpec);int cap=Math.min(getResources().getDisplayMetrics().heightPixels*4/5,dp(680));if(View.MeasureSpec.getMode(heightSpec)!=View.MeasureSpec.UNSPECIFIED)cap=Math.min(cap,available);super.onMeasure(widthSpec,View.MeasureSpec.makeMeasureSpec(cap,View.MeasureSpec.AT_MOST));}
        };scrollBox.setFillViewport(false);LinearLayout box=new LinearLayout(this);box.setId(SHEET_ID);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(22),dp(18),dp(22),dp(24));box.setBackground(round(SURFACE,26,0,0));TextView grab=new TextView(this);grab.setBackground(round(OUTLINE,999,0,0));grab.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams glp=new LinearLayout.LayoutParams(dp(42),dp(4));glp.gravity=Gravity.CENTER_HORIZONTAL;glp.bottomMargin=dp(18);box.addView(grab,glp);TextView heading=text(title,24,TEXT,Typeface.BOLD);heading.setPadding(0,0,0,dp(16));box.addView(heading);scrollBox.addView(box);
        scrollBox.setOnApplyWindowInsetsListener((view,insets)->{Rect safe=contentSafeInsets(insets);int bottom=safe.bottom;if(Build.VERSION.SDK_INT<30&&bottom>dp(120))bottom=0;box.setPadding(dp(22)+safe.left,dp(18),dp(22)+safe.right,dp(24)+bottom);return insets;});
        dialog.setContentView(scrollBox);dialog.setCancelable(true);dialog.setCanceledOnTouchOutside(true);Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.setDimAmount(.55f);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setGravity(Gravity.BOTTOM);window.setLayout(-1,-2);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);window.setWindowAnimations(ValueAnimator.areAnimatorsEnabled()?R.style.LudoBottomSheetAnimation:0);}
        dialog.setOnShowListener(x->{Window w=dialog.getWindow();if(w!=null)w.setLayout(-1,-2);scrollBox.requestApplyInsets();});return dialog;
    }
    private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(MUTED);e.setTextColor(TEXT);e.setTextSize(15);e.setSingleLine(true);e.setPadding(dp(14),0,dp(14),0);e.setBackground(round(SURFACE2,16,0,0));return e;}

    // ---------- settings/intents ----------
    private void copyDiagnosticsAsync(TextView action){
        if(action==null||!action.isEnabled())return;
        action.setEnabled(false);action.setText("Preparazione diagnostica…");
        diagnosticIo.execute(()->{
            String report=null,error="";
            try{report=VintedAccessibilityService.diagnostics(getApplicationContext());}
            catch(Exception t){error=t.getClass().getSimpleName();}
            final String ready=report,failed=error;
            runOnUiThread(()->{
                if(isDestroyed())return;
                action.setEnabled(true);action.setText("Copia diagnostica tecnica");
                if(!TextUtils.isEmpty(failed)||ready==null){Toast.makeText(this,"Diagnostica non disponibile · "+(TextUtils.isEmpty(failed)?"errore":failed),Toast.LENGTH_LONG).show();return;}
                android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                if(clipboard!=null)clipboard.setPrimaryClip(ClipData.newPlainText("Ludo Scout",ready));
                Toast.makeText(this,"Diagnostica copiata",Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void settings(){
        Dialog dialog=bottomSheet("Impostazioni");LinearLayout box=dialog.findViewById(SHEET_ID);
        box.addView(text("RADAR",12,MUTED,Typeface.BOLD));TextView radar=text(isAccessibilityEnabled()?"Accessibilità Radar  ·  Attiva":"Accessibilità Radar  ·  Da attivare",16,TEXT,Typeface.BOLD);radar.setGravity(Gravity.CENTER_VERTICAL);radar.setMinHeight(dp(54));radar.setOnClickListener(v->{dialog.dismiss();startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));});box.addView(radar);
        box.addView(text("DATI",12,MUTED,Typeface.BOLD));TextView bgg=text(bggSearch.configured()?"BoardGameGeek  ·  Collegato":"BoardGameGeek  ·  Token mancante",16,TEXT,Typeface.BOLD);bgg.setGravity(Gravity.CENTER_VERTICAL);bgg.setMinHeight(dp(54));box.addView(bgg);
        box.addView(text("AVANZATE",12,MUTED,Typeface.BOLD));TextView diag=text("Copia diagnostica tecnica",15,MUTED,Typeface.NORMAL);diag.setGravity(Gravity.CENTER_VERTICAL);diag.setMinHeight(dp(52));diag.setOnClickListener(v->copyDiagnosticsAsync(diag));box.addView(diag);
        TextView reset=text("Cancella dati Radar",15,RED,Typeface.NORMAL);reset.setGravity(Gravity.CENTER_VERTICAL);reset.setMinHeight(dp(52));reset.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Cancellare i dati Radar?").setMessage("Rimuove annunci e dati locali raccolti da Ludo.").setNegativeButton("Annulla",null).setPositiveButton("Cancella",(d,w)->{db.clearAll();bundleDb.clearAll();dialog.dismiss();render();}).show());box.addView(reset);dialog.show();
    }

    private void openEnginePhase(int phase,EngineOverviewSnapshot snapshot){
        if(phase!=-2&&phase!=-3&&snapshot.pipelineRun==null){enginePhase=phase;engineRunStart=engineRunEnd=0;engineSection="phase";enginePhaseItems=Collections.emptyList();enginePhaseGames=new HashMap<>();render();if(scroll!=null)scroll.scrollTo(0,0);return;}enginePhase=phase;engineRunStart=phase==-3?engineRunStart:phase==-2?0:snapshot.pipelineRun.startAt;engineRunEnd=phase==-3?engineRunEnd:phase==-2?0:snapshot.pipelineRun.endAt;engineSection="phase";enginePhaseItems=null;enginePhaseGames=new HashMap<>();enginePhaseDeals=new HashMap<>();enginePhaseVisible=24;final int request=++phaseRequestId;final long start=engineRunStart,end=engineRunEnd;
        render();if(scroll!=null)scroll.scrollTo(0,0);
        uiDataIo.execute(()->{try{List<DealDatabase.PipelineItem> rows=phase==-3?db.waitingScrollItems(start,end):phase==-2?db.engineIntakeItems():db.enginePipelineItems(start,end,phase);Map<Long,GameRecord> games=new HashMap<>();Map<String,DealRecord> deals=new HashMap<>();for(DealDatabase.PipelineItem item:rows){if(item.gameId>0&&!games.containsKey(item.gameId))games.put(item.gameId,marketStore.gameStats(item.gameId));if(!TextUtils.isEmpty(item.signature)&&!deals.containsKey(item.signature))deals.put(item.signature,db.findBySignature(item.signature));}runOnUiThread(()->{if(request!=phaseRequestId||isDestroyed()||isFinishing())return;enginePhaseItems=rows;enginePhaseGames=games;enginePhaseDeals=deals;if("activity".equals(tab)&&"phase".equals(engineSection))render();});}catch(Throwable error){runOnUiThread(()->{if(request==phaseRequestId&&!isDestroyed()&&!isFinishing()&&"phase".equals(engineSection)){body.addView(text("Lettura non disponibile. Tocca per riprovare",14,RED,Typeface.NORMAL));body.getChildAt(body.getChildCount()-1).setOnClickListener(v->openEnginePhase(phase,snapshot));}});}});
    }
    private void renderEnginePhase(){
        String[] titles={"Osservati","Giochi","Idonei","Verifiche Vinted","Pronti"};
        renderEngineHeader(enginePhase==-3?"Annunci acquisiti":enginePhase==-2?"Da analizzare":enginePhase<0?"In lavorazione":titles[enginePhase],enginePhase==-2?"Tutti gli scroll":engineRunStart==0?"":"Scroll delle "+engineTime(engineRunStart),true);
        if(enginePhaseItems==null){body.addView(engineSnapshotMessage("Carico gli articoli…"));return;}
        TextView count=text(enginePhaseItems.size()+" elementi",13,MUTED,Typeface.NORMAL);count.setPadding(0,0,0,dp(8));body.addView(count);
        if(enginePhaseItems.isEmpty()){TextView empty=text("Nessun elemento in questa fase",15,MUTED,Typeface.NORMAL);empty.setPadding(0,dp(16),0,dp(16));body.addView(empty);}
        for(int i=0;i<Math.min(enginePhaseVisible,enginePhaseItems.size());i++){
            DealDatabase.PipelineItem item=enginePhaseItems.get(i);GameRecord game=enginePhaseGames.get(item.gameId);DealRecord deal=enginePhaseDeals.get(item.signature);
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(80));row.setPadding(dp(16),dp(16),dp(16),dp(16));row.setBackground(round(SURFACE,18,1,OUTLINE));
            if(game!=null){FrameLayout artwork=new FrameLayout(this);artwork.setBackground(round(SURFACE2,10,0,0));artwork.setClipToOutline(true);TextView missing=appIcon(LudoIcons.BOOK_OPEN,20,MUTED);missing.setGravity(Gravity.CENTER);missing.setContentDescription("Copertina non disponibile");artwork.addView(missing,new FrameLayout.LayoutParams(-1,-1));ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);artwork.addView(image,new FrameLayout.LayoutParams(-1,-1));setEngineGameArtwork(image,missing,game);LinearLayout.LayoutParams artParams=new LinearLayout.LayoutParams(dp(64),dp(80));artParams.rightMargin=dp(12);row.addView(artwork,artParams);}
            LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);String itemTitle=game==null?item.title:game.name;TextView title=text(TextUtils.isEmpty(itemTitle)?"Annuncio acquisito":itemTitle,16,TEXT,Typeface.BOLD);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);copy.addView(title);
            if(game!=null&&game.rating!=null){TextView rating=text("BGG "+String.format(Locale.ITALY,"%.1f",game.rating),13,DISCOVER_YELLOW,Typeface.BOLD);rating.setPadding(0,dp(4),0,0);copy.addView(rating);}
            TextView phase=text(enginePhase==-3?"Acquisito da Vinted":enginePhase==-2?"In attesa dell’analisi":titles[item.phase],12,CYAN,Typeface.NORMAL);phase.setPadding(0,dp(4),0,0);copy.addView(phase);
            if(deal!=null&&deal.itemPriceCents>0){TextView price=text("Vinted · "+money(deal.itemPriceCents),14,TEXT,Typeface.BOLD);price.setPadding(0,dp(6),0,0);copy.addView(price);}
            row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
            if(deal!=null||game!=null){TextView arrow=appIcon(LudoIcons.CHEVRON_RIGHT,14,MUTED);arrow.setGravity(Gravity.CENTER);LinearLayout.LayoutParams arrowParams=new LinearLayout.LayoutParams(dp(24),dp(48));arrowParams.leftMargin=dp(8);row.addView(arrow,arrowParams);row.setOnClickListener(v->{if(deal!=null)openDetail(deal);else openDatabaseGame(item.gameId,"activity");});}
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=dp(12);body.addView(row,rp);
        }
        if(enginePhaseVisible<enginePhaseItems.size()){TextView more=secondaryTextAction("Mostra altri elementi");more.setOnClickListener(v->{enginePhaseVisible+=24;render();});body.addView(more);}
    }
    private void setEngineGameArtwork(ImageView image,View missing,GameRecord game){
        String identity="engine:"+game.id;image.setTag(identity);
        galleryNet.execute(()->{File file=TextUtils.isEmpty(game.bggId)?null:ArtworkStore.bggFile(this,game.bggId);Bitmap bitmap=file!=null&&file.exists()?decodeLocalBitmap(file,220,300):null;
            runOnUiThread(()->{if(isDestroyed()||isFinishing()||!identity.equals(image.getTag()))return;if(bitmap!=null&&!bitmap.isRecycled()){image.setImageBitmap(bitmap);missing.setVisibility(View.GONE);}else{String url=TextUtils.isEmpty(game.imageUrl)?game.thumbnailUrl:game.imageUrl;if(!TextUtils.isEmpty(url))loadFirstRemote(image,Collections.singletonList(url),()->missing.setVisibility(View.GONE),null);}});
        });
    }

    private void renderOperationsPage(){
        lastQueueUiRenderAt=System.currentTimeMillis();
        if("waiting".equals(engineSection)){renderEngineWaiting();return;}
        if("phase".equals(engineSection)){renderEnginePhase();return;}
        if("review".equals(engineSection)){renderEngineReview();return;}
        if("history".equals(engineSection)){renderEngineHistory();return;}
        if("day".equals(engineSection)){renderEngineDay();return;}
        if("run".equals(engineSection)){renderEngineRun();return;}
        if("work".equals(engineSection)){renderEngineWork();return;}
        renderEngineOverview();
    }

    private void renderEngineHeader(String title,String subtitle,boolean back){
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(0,dp(8),0,dp(16));
        if(back){TextView b=appIcon(LudoIcons.CHEVRON_LEFT,25,TEXT);b.setGravity(Gravity.CENTER);b.setContentDescription("Indietro");b.setOnClickListener(v->{if("run".equals(engineSection)&&engineDayStart>0)engineSection="day";else if("day".equals(engineSection))engineSection="history";else engineSection="overview";render();});head.addView(b,new LinearLayout.LayoutParams(dp(48),dp(48)));}
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.addView(text(title,32,TEXT,Typeface.BOLD));if(!TextUtils.isEmpty(subtitle)){TextView sub=text(subtitle,13,MUTED,Typeface.NORMAL);sub.setPadding(0,dp(2),0,0);copy.addView(sub);}head.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        if(!back){TextView prefs=appIcon(LudoIcons.ELLIPSIS_VERTICAL,20,TEXT);prefs.setGravity(Gravity.CENTER);prefs.setContentDescription("Impostazioni e diagnostica");prefs.setOnClickListener(v->settings());head.addView(prefs,new LinearLayout.LayoutParams(dp(48),dp(48)));}
        body.addView(head);
    }

    private boolean engineScrollActive(DealDatabase.ObservationSession run,long now){return run!=null&&now-run.endAt<DealDatabase.ENGINE_SESSION_GAP_MS;}
    private boolean engineRunSettled(DealDatabase.ObservationSession run,long now){return DealDatabase.engineAutomaticDone(run,now);}

    private EngineOverviewSnapshot loadEngineOverviewSnapshot(){
        long started=System.currentTimeMillis();
        DealDatabase.ObservationSession run=db.activeObservationSession();long ownerAt=System.currentTimeMillis();
        int waitingRuns=db.waitingObservationSessionCount();int recoveryCount=marketStore.vintedReviewCount()+marketStore.bggMatchReviewCount();long reviewAt=System.currentTimeMillis();
        DealDatabase.ObservationSession pipelineRun=run!=null?run:db.latestObservationSession();
        int[] phases=pipelineRun==null?new int[5]:db.enginePipelineCounts(pipelineRun.startAt,pipelineRun.endAt);
        int activeMask=pipelineRun==null?0:db.enginePipelineActiveMask(pipelineRun.startAt,pipelineRun.endAt);int intakeCount=db.engineIntakeCount();
        getSharedPreferences("va_v3_diag",MODE_PRIVATE).edit().putString("activitySnapshotTiming","ownerMs="+(ownerAt-started)+";reviewMs="+(reviewAt-ownerAt)+";pipelineMs="+(System.currentTimeMillis()-reviewAt)).apply();
        boolean bggPaused=marketStore.isBggPaused(),vintedPaused=marketStore.isVintedPaused();long vintedWaitUntil=VintedPublicSession.waitUntil(this);EngineOverviewSnapshot snapshot=new EngineOverviewSnapshot(System.currentTimeMillis(),run,waitingRuns,recoveryCount,Collections.emptyList(),Collections.emptyList());snapshot.pipelineRun=pipelineRun;snapshot.phases=phases;snapshot.activeMask=activeMask;snapshot.intakeCount=intakeCount;snapshot.bggPaused=bggPaused;snapshot.vintedPaused=vintedPaused;snapshot.vintedWaitUntil=vintedWaitUntil;return snapshot;
    }

    private void requestEngineOverviewSnapshot(){
        long requestedAt=System.currentTimeMillis();
        if(requestedAt<engineOverviewRetryAt||!engineOverviewLoading.compareAndSet(false,true))return;
        engineUiIo.execute(()->{
            long startedAt=System.currentTimeMillis();EngineOverviewSnapshot loaded=null;
            try{loaded=loadEngineOverviewSnapshot();}
            catch(Throwable t){
                engineOverviewLoadError=String.valueOf(t);
                engineOverviewRetryAt=System.currentTimeMillis()+5_000L;
                getSharedPreferences("va_v3_diag",MODE_PRIVATE).edit()
                        .putString("activitySnapshotError",engineOverviewLoadError)
                        .putString("activitySnapshotState","ERROR;retryAt="+engineOverviewRetryAt+";elapsedMs="+(System.currentTimeMillis()-startedAt)).apply();
            }
            final EngineOverviewSnapshot ready=loaded;
            runOnUiThread(()->{
                engineOverviewLoading.set(false);
                if(ready!=null){
                    engineOverviewSnapshot=ready;engineOverviewRetryAt=0L;engineOverviewLoadError="";
                    getSharedPreferences("va_v3_diag",MODE_PRIVATE).edit().remove("activitySnapshotError")
                            .putString("activitySnapshotState","READY;elapsedMs="+(System.currentTimeMillis()-startedAt)).apply();
                }
                if("activity".equals(tab)&&"overview".equals(engineSection)&&engineUiResumed&&!isDestroyed()){
                    scheduleRender(0);
                    if(ready==null){
                        uiUpdates.removeCallbacks(activitySnapshotRetry);
                        uiUpdates.postDelayed(activitySnapshotRetry,Math.max(250L,engineOverviewRetryAt-System.currentTimeMillis()));
                    }
                }
            });
        });
    }

    private List<DealDatabase.ObservationSession> engineWaitingRows;
    private boolean engineWaitingLoading;
    private void renderEngineWaiting(){
        renderEngineHeader("Scroll in attesa","Annunci già acquisiti",true);
        if(engineWaitingRows==null){body.addView(engineSnapshotMessage("Carico gli scroll…"));if(!engineWaitingLoading){engineWaitingLoading=true;engineUiIo.execute(()->{List<DealDatabase.ObservationSession> rows=null;try{rows=db.waitingObservationSessions();}catch(Exception ignored){}final List<DealDatabase.ObservationSession> ready=rows;runOnUiThread(()->{engineWaitingLoading=false;if(isDestroyed()||isFinishing())return;engineWaitingRows=ready;if("waiting".equals(engineSection)){if(ready!=null)render();else{body.removeAllViews();TextView retry=secondaryTextAction("Riprova");retry.setOnClickListener(v->render());body.addView(retry);}}});});}return;}
        if(engineWaitingRows.isEmpty())body.addView(text("Nessuno scroll in attesa",15,MUTED,Typeface.NORMAL));
        for(DealDatabase.ObservationSession session:engineWaitingRows){LinearLayout row=verticalCard();row.setPadding(dp(16),dp(16),dp(16),dp(16));row.addView(text("Scroll delle "+engineTime(session.startAt),17,TEXT,Typeface.BOLD));row.addView(text(session.uniqueListings+" annunci acquisiti  ›",13,MUTED,Typeface.NORMAL));row.setOnClickListener(v->{engineRunStart=session.startAt;engineRunEnd=session.endAt;openEnginePhase(-3,engineOverviewSnapshot);});body.addView(row);}
    }
    private void renderEngineOverview(){
        long now=System.currentTimeMillis();EngineOverviewSnapshot snapshot=engineOverviewSnapshot;
        if(snapshot==null){
            requestEngineOverviewSnapshot();
            String loadingCopy=!TextUtils.isEmpty(engineOverviewLoadError)&&now<engineOverviewRetryAt
                    ?"Stato del Motore temporaneamente non disponibile · riprovo automaticamente"
                    :"Carico gli articoli…";
            body.addView(engineSnapshotMessage(loadingCopy));
            return;
        }
        renderEngineHeader("Motore","",false);
        // Stale-while-revalidate: never replace usable Activity data with an empty loading page.
        if(snapshot.loadedAt<engineEnteredAt||now-snapshot.loadedAt>30_000L)requestEngineOverviewSnapshot();
        DealDatabase.ObservationSession run=snapshot.run;
        body.addView(enginePipelineCard(snapshot));
        if(snapshot.recoveryCount>0){
            LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.topMargin=dp(14);
            body.addView(engineAttentionCard(snapshot.recoveryCount),ap);
        }

        uiUpdates.removeCallbacks(activityStatusPulse);if(engineUiResumed)uiUpdates.postDelayed(activityStatusPulse,10_000L);
    }

    private View engineIntakeCard(EngineOverviewSnapshot snapshot){
        boolean stacked=getResources().getConfiguration().fontScale>1.15f||getResources().getDisplayMetrics().widthPixels<dp(340);
        LinearLayout row=new LinearLayout(this);row.setOrientation(stacked?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);row.setPadding(0,0,0,dp(12));
        TextView waiting=text(snapshot.waitingRuns+" scroll in attesa",13,TEXT,Typeface.BOLD);waiting.setMinHeight(dp(48));waiting.setGravity(Gravity.CENTER_VERTICAL);waiting.setPadding(dp(12),dp(8),dp(12),dp(8));waiting.setBackground(round(SURFACE2,12,1,OUTLINE));waiting.setCompoundDrawables(null,null,iconDrawable(LudoIcons.CHEVRON_RIGHT,MUTED,12),null);waiting.setCompoundDrawablePadding(dp(6));waiting.setOnClickListener(v->{engineWaitingRows=null;engineSection="waiting";render();if(scroll!=null)scroll.scrollTo(0,0);});row.addView(waiting,new LinearLayout.LayoutParams(stacked?-1:0,-2,stacked?0:1));
        TextView pending=text(snapshot.intakeCount+(snapshot.intakeCount==1?" annuncio da analizzare":" annunci da analizzare"),13,TEXT,Typeface.BOLD);pending.setMinHeight(dp(48));pending.setGravity(Gravity.CENTER_VERTICAL);pending.setPadding(dp(12),dp(8),dp(12),dp(8));pending.setBackground(round(SURFACE2,12,1,OUTLINE));pending.setCompoundDrawables(null,null,iconDrawable(LudoIcons.CHEVRON_RIGHT,MUTED,12),null);pending.setCompoundDrawablePadding(dp(6));pending.setOnClickListener(v->openEnginePhase(-2,snapshot));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(stacked?-1:0,-2,stacked?0:1);if(stacked)pp.topMargin=dp(8);else pp.leftMargin=dp(8);row.addView(pending,pp);
        return row;
    }
    private View enginePipelineCard(EngineOverviewSnapshot snapshot){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(16),dp(16),dp(16));card.setBackground(round(SURFACE,22,1,OUTLINE));
        DealDatabase.ObservationSession scope=snapshot.pipelineRun;
        String motionScope=scope==null?"empty":String.valueOf(scope.startAt);android.content.SharedPreferences motionPrefs=getSharedPreferences("engine_motion_ui_v1",MODE_PRIVATE);
        if(!engineMotion.initialized()){int[] previous=new int[5];for(int i=0;i<5;i++)previous[i]=motionPrefs.getInt("phase"+i,0);engineMotion.restore(motionPrefs.getString("scope",""),previous);}
        int[] previous=snapshot.loadedAt<engineEnteredAt?snapshot.phases:engineMotion.consume(motionScope,snapshot.loadedAt,snapshot.phases);
        if(snapshot.loadedAt>=engineEnteredAt){android.content.SharedPreferences.Editor edit=motionPrefs.edit().putString("scope",motionScope);for(int i=0;i<5;i++)edit.putInt("phase"+i,snapshot.phases[i]);edit.apply();}
        TextView label=text(scope==null?"In attesa del prossimo scroll":"Scroll delle "+engineTime(scope.startAt),17,TEXT,Typeface.BOLD);card.addView(label);
        TextView status=text(snapshot.run==null?(scope==null?"Nessuno scroll acquisito":"Ultimo scroll elaborato"):snapshot.activeMask!=0?"Elaborazione in corso":snapshot.bggPaused&&snapshot.vintedPaused?"Elaborazione in pausa":snapshot.phases[3]>0&&snapshot.vintedPaused?"Verifiche Vinted in pausa":snapshot.phases[3]>0&&snapshot.vintedWaitUntil>System.currentTimeMillis()?"Attesa del prossimo controllo Vinted":snapshot.phases[1]>0&&snapshot.bggPaused?"Schede BGG in pausa":"In attesa di elaborazione",13,MUTED,Typeface.NORMAL);status.setPadding(0,dp(4),0,dp(12));card.addView(status);card.addView(engineIntakeCard(snapshot));

        String[] names={"Osservati","Giochi","Idonei","Verifiche\nVinted","Pronti"};String[] icons={LudoIcons.SEARCH,LudoIcons.BOOK_OPEN,LudoIcons.CHECK,LudoIcons.GEAR,LudoIcons.STAR};int[] colors={DISCOVER_LAVENDER,DISCOVER_PINK,DISCOVER_MINT,DISCOVER_YELLOW,Color.rgb(85,155,243)};
        if(getResources().getConfiguration().fontScale>1.15f||getResources().getDisplayMetrics().widthPixels<dp(340)){
            int working=0;for(int i=0;i<4;i++)working+=snapshot.phases[i];TextView total=text(working+" elementi in lavorazione",22,TEXT,Typeface.BOLD);total.setPadding(0,dp(20),0,dp(12));total.setOnClickListener(v->openEnginePhase(-1,snapshot));card.addView(total);
            for(int i=0;i<5;i++){final int phase=i;LinearLayout node=new LinearLayout(this);node.setGravity(Gravity.CENTER_VERTICAL);node.setPadding(dp(14),dp(12),dp(14),dp(12));node.setBackground(round(SURFACE,16,1,colors[i]));TextView title=text((i+1)+". "+names[i].replace('\n',' '),16,TEXT,Typeface.NORMAL);node.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView count=text(String.valueOf(snapshot.phases[i]),23,colors[i],Typeface.BOLD);LinearLayout counter=new LinearLayout(this);counter.setOrientation(LinearLayout.VERTICAL);counter.setGravity(Gravity.CENTER);counter.addView(count);node.addView(counter);animateEngineCount(count,counter,previous[i],snapshot.phases[i],(snapshot.activeMask&(1<<i))!=0);node.setOnClickListener(v->openEnginePhase(phase,snapshot));LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.bottomMargin=dp(10);card.addView(node,np);}return card;
        }
        FrameLayout wheel=new FrameLayout(this);wheel.setClipChildren(false);
        View ring=new View(this);ring.setBackground(new Drawable(){final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);public void draw(Canvas canvas){Rect b=getBounds();float cx=b.width()/2f,cy=b.height()/2f,r=Math.min(b.width(),b.height())*.33f;paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setStrokeCap(Paint.Cap.ROUND);for(int i=0;i<5;i++){paint.setColor(colors[i]);paint.setAlpha(110);canvas.drawArc(new RectF(cx-r,cy-r,cx+r,cy+r),-90+i*72+14,44,false,paint);}}public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}});wheel.addView(ring,new FrameLayout.LayoutParams(-1,-1));
        int working=0;for(int i=0;i<4;i++)working+=snapshot.phases[i];LinearLayout center=new LinearLayout(this);center.setOrientation(LinearLayout.VERTICAL);center.setGravity(Gravity.CENTER);TextView total=text(String.valueOf(working),31,TEXT,Typeface.BOLD);total.setGravity(Gravity.CENTER);center.addView(total);TextView caption=text("elementi in\nlavorazione",11,MUTED,Typeface.NORMAL);caption.setGravity(Gravity.CENTER);center.addView(caption);center.setOnClickListener(v->openEnginePhase(-1,snapshot));center.setContentDescription("Apri elementi in lavorazione");int oldWorking=0;for(int i=0;i<4;i++)oldWorking+=previous[i];animateEngineCount(total,center,oldWorking,working,false);total.setAutoSizeTextTypeUniformWithConfiguration(18,31,1,android.util.TypedValue.COMPLEX_UNIT_SP);wheel.addView(center,new FrameLayout.LayoutParams(dp(80),dp(76),Gravity.CENTER));
        for(int i=0;i<5;i++){final int phase=i;LinearLayout node=new LinearLayout(this);node.setOrientation(LinearLayout.VERTICAL);node.setGravity(Gravity.CENTER);node.setBackground(round(SURFACE,999,1,colors[i]));node.addView(appIcon(icons[i],16,colors[i]),new LinearLayout.LayoutParams(-1,dp(22)));TextView title=text(names[i],11,TEXT,Typeface.NORMAL);title.setGravity(Gravity.CENTER);title.setMaxLines(2);node.addView(title);TextView count=text(String.valueOf(snapshot.phases[i]),21,colors[i],Typeface.BOLD);count.setGravity(Gravity.CENTER);node.addView(count);animateEngineCount(count,node,previous[i],snapshot.phases[i],(snapshot.activeMask&(1<<i))!=0);node.setContentDescription((i+1)+". "+names[i].replace('\n',' ')+": "+snapshot.phases[i]+" elementi");node.setOnClickListener(v->openEnginePhase(phase,snapshot));FrameLayout.LayoutParams np=new FrameLayout.LayoutParams(dp(88),dp(88));wheel.addView(node,np);node.setTag(i);}
        wheel.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l==or-ol&&b-t==ob-ot)return;float cx=(r-l)/2f,cy=(b-t)/2f,radius=Math.min(r-l,b-t)*.33f;for(int i=2;i<wheel.getChildCount();i++){View node=wheel.getChildAt(i);int phase=(Integer)node.getTag();double angle=Math.toRadians(-90+phase*72);node.setX(cx+(float)Math.cos(angle)*radius-dp(44));node.setY(cy+(float)Math.sin(angle)*radius-dp(44));}});
        int width=getResources().getDisplayMetrics().widthPixels-dp(68);card.addView(wheel,new LinearLayout.LayoutParams(-1,Math.min(dp(340),width)));
        return card;
    }
    private void animateEngineCount(TextView count,View node,int old,int current,boolean active){
        if(!ValueAnimator.areAnimatorsEnabled())return;
        if(old!=current){count.setText(String.valueOf(old));ValueAnimator values=ValueAnimator.ofInt(old,current);values.setDuration(Math.min(1500,650+Math.abs((long)current-old)*8));values.addUpdateListener(v->count.setText(String.valueOf(v.getAnimatedValue())));values.start();node.animate().scaleX(1.07f).scaleY(1.07f).setDuration(250).withEndAction(()->node.animate().scaleX(1).scaleY(1).setDuration(350).start()).start();
            if(node instanceof LinearLayout){TextView delta=text((current>old?"↑ +":"↓ ")+(current-old),9,current>old?TEAL:RED,Typeface.BOLD);delta.setGravity(Gravity.CENTER);((LinearLayout)node).addView(delta);delta.animate().alpha(0).setStartDelay(2500).setDuration(500).start();}
            node.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){values.cancel();v.animate().cancel();}});
        }else if(active){node.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){v.animate().cancel();}});node.animate().scaleX(1.08f).scaleY(1.08f).setDuration(420).withEndAction(()->node.animate().scaleX(1).scaleY(1).setDuration(420).start()).start();}
    }
    private View engineCurrentRunHero(DealDatabase.ObservationSession run,long now,int waitingRuns){
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);hero.setPadding(dp(20),dp(20),dp(20),dp(18));
        if(run==null){
            hero.setBackground(round(SURFACE,26,1,TEAL));
            hero.addView(text("PRONTO",12,TEAL,Typeface.BOLD));
            TextView title=text("Puoi fare un nuovo scroll",21,TEXT,Typeface.BOLD);title.setPadding(0,dp(8),0,0);hero.addView(title);
            hero.addView(text("Apri Vinted e scorri normalmente. Ludo inizierà a lavorare automaticamente.",14,MUTED,Typeface.NORMAL));
            hero.setContentDescription("Motore pronto. Puoi fare un nuovo scroll.");
            return hero;
        }

        boolean acquiring=engineScrollActive(run,now);
        boolean settled=DealDatabase.engineContentSettled(run);
        int remoteRemaining=Math.max(run.corePendingListings,run.coreRemainingListings);
        long waitUntil=VintedPublicSession.waitUntil(this);
        String eyebrow,title,detail;
        int accent=settled?TEAL:CYAN;

        if(acquiring){
            eyebrow="STO RACCOGLIENDO";
            title="Sto osservando questo scroll";
            detail=run.uniqueListings+" annunci Vinted osservati finora.\nContinua a usare Vinted normalmente.";
        }else if(settled){
            eyebrow="COMPLETATO";
            title="Questo scroll è stato elaborato";
            detail="Il percorso automatico di questo scroll è concluso.";
        }else if(run.analysisPendingListings>0){
            eyebrow="IN LAVORAZIONE";
            title="Sto riconoscendo i giochi";
            detail=run.analysisPendingListings+(run.analysisPendingListings==1?" annuncio deve":" annunci devono")+" ancora essere riconosciut"+(run.analysisPendingListings==1?"o.":"i.");
        }else if(remoteRemaining>0&&waitUntil>now){
            eyebrow="IN LAVORAZIONE";
            title="In attesa del prossimo controllo Vinted";
            detail="Ludo riprenderà automaticamente.\n"+remoteRemaining+(remoteRemaining==1?" annuncio deve":" annunci devono")+" ancora essere verificat"+(remoteRemaining==1?"o.":"i.");
        }else if(remoteRemaining>0){
            eyebrow="IN LAVORAZIONE";
            title="Sto collegando gli annunci";
            detail=remoteRemaining+(remoteRemaining==1?" annuncio deve":" annunci devono")+" ancora essere verificat"+(remoteRemaining==1?"o":"i")+" su Vinted.";
        }else{
            eyebrow="IN LAVORAZIONE";
            title="Sto preparando i risultati";
            detail="I risultati già pronti sono disponibili.";
        }

        hero.setBackground(round(SURFACE,26,1,accent));
        hero.addView(text(eyebrow,10,accent,Typeface.BOLD));
        TextView titleView=text(title,21,TEXT,Typeface.BOLD);titleView.setPadding(0,dp(8),0,0);hero.addView(titleView);
        TextView scrollLabel=text("Scroll delle "+engineTime(run.startAt),12,MUTED,Typeface.BOLD);scrollLabel.setPadding(0,dp(6),0,0);hero.addView(scrollLabel);
        TextView detailView=text(detail,14,MUTED,Typeface.NORMAL);detailView.setPadding(0,dp(10),0,0);hero.addView(detailView);
        if(!acquiring){
            TextView more=text(settled?"Vedi cosa è successo  ›":"Vedi cosa sta facendo Ludo  ›",14,CYAN,Typeface.BOLD);
            more.setGravity(Gravity.CENTER_VERTICAL);more.setMinHeight(dp(46));more.setPadding(0,dp(7),0,0);
            more.setOnClickListener(v->openEngineWork(run));hero.addView(more);
        }
        hero.setContentDescription(eyebrow+". "+title+". "+detail.replace('\n',' '));
        return hero;
    }

    private View engineCurrentResultsCard(DealDatabase.ObservationSession run){
        LinearLayout card=verticalCard();card.setPadding(dp(18),dp(16),dp(18),dp(16));card.setBackground(round(SURFACE,22,1,OUTLINE));
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView label=text("Risultati di questo scroll",16,TEXT,Typeface.BOLD);heading.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=appIcon(LudoIcons.CHEVRON_RIGHT,15,MUTED);arrow.setGravity(Gravity.CENTER);heading.addView(arrow,new LinearLayout.LayoutParams(dp(32),dp(40)));card.addView(heading);
        TextView count=text(run.completeListings+(run.completeListings==1?" risultato pronto":" risultati pronti"),28,TEXT,Typeface.BOLD);count.setPadding(0,dp(8),0,0);card.addView(count);
        card.addView(text("da questo scroll",13,MUTED,Typeface.NORMAL));
        String meta=run.bggMatchedListings+" giochi riconosciuti · "+run.uniqueListings+" annunci osservati";
        TextView metadata=text(meta,12,MUTED,Typeface.NORMAL);metadata.setPadding(0,dp(12),0,0);card.addView(metadata);
        if(run.heldListings>0){TextView held=text(run.heldListings+(run.heldListings==1?" non pubblicato automaticamente":" non pubblicati automaticamente"),12,MUTED,Typeface.NORMAL);held.setPadding(0,dp(5),0,0);card.addView(held);}
        if(run.completeListings>0){
            Button open=button("Vedi "+(run.completeListings==1?"il risultato":"i "+run.completeListings+" risultati"),LIME);
            open.setOnClickListener(v->openEngineRun(run,"ready"));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(48));bp.topMargin=dp(14);card.addView(open,bp);
        }else{
            TextView waiting=text("I primi risultati compariranno qui appena saranno pronti.",12,MUTED,Typeface.NORMAL);waiting.setPadding(0,dp(10),0,0);card.addView(waiting);
        }
        return card;
    }

    private View engineAttentionCard(int count){
        LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(16),dp(14),dp(12),dp(14));card.setBackground(round(SURFACE,20,1,ORANGE));
        TextView icon=text("!",16,ORANGE,Typeface.BOLD);icon.setGravity(Gravity.CENTER);icon.setBackground(round(SURFACE2,999,1,ORANGE));card.addView(icon,new LinearLayout.LayoutParams(dp(34),dp(34)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(12),0,dp(8),0);
        copy.addView(text("Richiedono attenzione",16,TEXT,Typeface.BOLD));
        copy.addView(text(count+(count==1?" elemento richiede":" elementi richiedono")+" una scelta",12,MUTED,Typeface.NORMAL));
        card.addView(copy,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=appIcon(LudoIcons.CHEVRON_RIGHT,16,ORANGE);arrow.setGravity(Gravity.CENTER);card.addView(arrow,new LinearLayout.LayoutParams(dp(34),dp(44)));
        card.setOnClickListener(v->{engineSection="review";render();});card.setContentDescription("Serve il tuo aiuto. "+count+" elementi richiedono una scelta.");
        return card;
    }

    private View engineWorkQueueCard(DealDatabase.ObservationSession active,List<DealDatabase.ObservationSession> unfinished){
        if(unfinished==null||unfinished.isEmpty())return null;
        List<DealDatabase.ObservationSession> others=new ArrayList<>();
        for(DealDatabase.ObservationSession x:unfinished)if(active==null||x.startAt!=active.startAt)others.add(x);
        if(others.isEmpty())return null;
        Collections.sort(others,Comparator.comparingLong(x->x.startAt));
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.addView(text("Altri scroll",17,TEXT,Typeface.BOLD),new LinearLayout.LayoutParams(0,-2,1));
        if(others.size()>3){TextView all=text("Vedi tutti",13,CYAN,Typeface.BOLD);all.setOnClickListener(v->{engineSection="history";render();});head.addView(all);}box.addView(head);
        int shown=Math.min(3,others.size());
        for(int i=0;i<shown;i++){
            DealDatabase.ObservationSession x=others.get(i);boolean deferred=active!=null&&x.startAt<active.startAt;
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(2),dp(12),0,dp(12));
            LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.addView(text("Scroll · "+engineTime(x.startAt),15,TEXT,Typeface.BOLD));copy.addView(text(x.uniqueListings+" annunci osservati",12,MUTED,Typeface.NORMAL));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
            TextView status=text(deferred?"Riprenderà":"In attesa",12,deferred?PURPLE:MUTED,Typeface.BOLD);status.setGravity(Gravity.RIGHT);row.addView(status);
            TextView arrow=appIcon(LudoIcons.CHEVRON_RIGHT,15,MUTED);arrow.setGravity(Gravity.CENTER);row.addView(arrow,new LinearLayout.LayoutParams(dp(32),dp(44)));row.setOnClickListener(v->openObservationSession(x));box.addView(row);
            if(i<shown-1)box.addView(engineDivider());
        }
        return box;
    }

    private View engineFunnelRow(String label,int value,String detail,int accent){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(2),dp(8),0,dp(8));
        TextView number=text(String.valueOf(Math.max(0,value)),20,accent,Typeface.BOLD);number.setGravity(Gravity.CENTER);row.addView(number,new LinearLayout.LayoutParams(dp(46),dp(44)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(10),0,0,0);copy.addView(text(label,14,TEXT,Typeface.BOLD));copy.addView(text(detail,11,MUTED,Typeface.NORMAL));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));return row;
    }

    private View engineStepRow(int iconRes,String title,String detail,boolean done,int accent,Runnable action){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(2),dp(12),0,dp(12));ImageView icon=new ImageView(this);icon.setImageResource(iconRes);icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);if(iconRes!=R.drawable.provider_bgg_logo&&iconRes!=R.drawable.provider_vinted_logo)icon.setColorFilter(done?TEAL:MUTED);row.addView(icon,new LinearLayout.LayoutParams(dp(30),dp(30)));LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(12),0,dp(8),0);copy.addView(text(title,15,TEXT,Typeface.BOLD));copy.addView(text(detail,12,MUTED,Typeface.NORMAL));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));String marker=done?"✓":action!=null?"›":"•";TextView state=text(marker,done?20:action!=null?26:18,done?TEAL:MUTED,Typeface.BOLD);state.setGravity(Gravity.CENTER);row.addView(state,new LinearLayout.LayoutParams(dp(34),dp(42)));if(action!=null){row.setOnClickListener(v->action.run());row.setContentDescription(title+". "+detail+". Apri dettagli");}return row;
    }

    private View engineNavRow(String title,String subtitle,int accent,Runnable action){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(2),dp(13),0,dp(13));View dot=new View(this);dot.setBackground(round(accent,999,0,0));row.addView(dot,new LinearLayout.LayoutParams(dp(8),dp(8)));LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(14),0,dp(8),0);copy.addView(text(title,15,TEXT,Typeface.BOLD));copy.addView(text(subtitle,12,MUTED,Typeface.NORMAL));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=appIcon(LudoIcons.CHEVRON_RIGHT,16,MUTED);arrow.setGravity(Gravity.CENTER);row.addView(arrow,new LinearLayout.LayoutParams(dp(38),dp(48)));row.setOnClickListener(v->action.run());return row;}

    private View engineDivider(){View d=new View(this);d.setBackgroundColor(OUTLINE);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.leftMargin=dp(2);d.setLayoutParams(lp);return d;}

    private String engineDayLabel(long start){Calendar today=Calendar.getInstance();zeroDay(today);Calendar day=Calendar.getInstance();day.setTimeInMillis(start);zeroDay(day);long diff=(today.getTimeInMillis()-day.getTimeInMillis())/(24L*60L*60_000L);if(diff==0)return"Oggi";if(diff==1)return"Ieri";if(diff==2)return"L'altro ieri";return new SimpleDateFormat("EEE d MMM",Locale.ITALY).format(new Date(start));}
    private void zeroDay(Calendar c){c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);}
    private String engineTime(long at){return new SimpleDateFormat("HH:mm",Locale.ITALY).format(new Date(at));}

    private View engineDayRow(DealDatabase.ObservationDay day,boolean last){
        LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(LinearLayout.VERTICAL);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(2),dp(13),0,dp(13));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.addView(text(engineDayLabel(day.startAt),16,TEXT,Typeface.BOLD));copy.addView(text(day.sessions+" scroll · "+day.uniqueListings+" annunci osservati",12,MUTED,Typeface.NORMAL));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout state=new LinearLayout(this);state.setOrientation(LinearLayout.VERTICAL);state.setGravity(Gravity.RIGHT);
        String summary=day.validListings==0?"nessun risultato":day.completeListings+" risultati pronti"+(day.reviewListings>0?" · "+day.reviewListings+" da rivedere":"");
        TextView st=text(summary,13,day.validListings==0||day.completeListings+day.reviewListings+day.heldListings>=day.validListings?TEAL:CYAN,Typeface.BOLD);st.setGravity(Gravity.RIGHT);state.addView(st);row.addView(state);
        TextView arrow=appIcon(LudoIcons.CHEVRON_RIGHT,16,MUTED);arrow.setGravity(Gravity.CENTER);row.addView(arrow,new LinearLayout.LayoutParams(dp(34),dp(48)));
        row.setOnClickListener(v->{engineDayStart=day.startAt;engineDayEnd=day.endAt;engineSection="day";render();});wrap.addView(row);if(!last)wrap.addView(engineDivider());return wrap;
    }

    private void openEngineRun(DealDatabase.ObservationSession run,String filter){if(run==null)return;engineRunStart=run.startAt;engineRunEnd=run.endAt;engineRunFilter=TextUtils.isEmpty(filter)?"all":filter;engineRunPage=0;engineSection="run";render();if(scroll!=null)scroll.scrollTo(0,0);}
    private void openEngineWork(DealDatabase.ObservationSession run){if(run==null)return;engineRunStart=run.startAt;engineRunEnd=run.endAt;engineSection="work";render();if(scroll!=null)scroll.scrollTo(0,0);}
    private void openObservationReady(DealDatabase.ObservationSession run){openEngineRun(run,"ready");}
    private void openObservationSession(DealDatabase.ObservationSession session){openEngineRun(session,"all");}

    private void renderEngineWork(){
        requestEngineRunSnapshot(engineRunStart,engineRunEnd,"all");
        EngineRunSnapshot snapshot=engineRunSnapshot;
        if(snapshot==null||snapshot.startAt!=engineRunStart||snapshot.endAt!=engineRunEnd||!"all".equals(snapshot.filter)){
            renderEngineHeader("Lavoro automatico","Sto caricando lo stato dello scroll…",true);body.addView(engineSnapshotMessage("Lettura in corso"));return;
        }
        DealDatabase.ObservationSession run=snapshot.run;if(run==null){engineSection="overview";renderEngineOverview();return;}
        long now=System.currentTimeMillis();int remoteRemaining=Math.max(run.corePendingListings,run.coreRemainingListings);long waitUntil=VintedPublicSession.waitUntil(this);
        String current;
        if(engineScrollActive(run,now))current="Sto osservando questo scroll";
        else if(DealDatabase.engineContentSettled(run))current="Lavoro automatico concluso";
        else if(run.analysisPendingListings>0)current="Sto riconoscendo i giochi";
        else if(remoteRemaining>0&&waitUntil>now)current="In attesa del prossimo controllo Vinted";
        else if(remoteRemaining>0)current="Sto collegando gli annunci";
        else current="Sto preparando i risultati";
        renderEngineHeader("Lavoro automatico","Scroll delle "+engineTime(run.startAt),true);

        LinearLayout nowCard=verticalCard();nowCard.setPadding(dp(18),dp(16),dp(18),dp(16));nowCard.setBackground(round(SURFACE,22,1,CYAN));
        nowCard.addView(text("ORA",11,CYAN,Typeface.BOLD));TextView currentView=text(current,22,TEXT,Typeface.BOLD);currentView.setPadding(0,dp(7),0,0);nowCard.addView(currentView);
        String nowDetail=remoteRemaining>0?remoteRemaining+(remoteRemaining==1?" verifica online ancora necessaria":" verifiche online ancora necessarie"):run.completeListings+" risultati già pronti";
        TextView nowDetailView=text(nowDetail,13,MUTED,Typeface.NORMAL);nowDetailView.setPadding(0,dp(6),0,0);nowCard.addView(nowDetailView);body.addView(nowCard);

        TextView pathTitle=text("Percorso di questo scroll",17,TEXT,Typeface.BOLD);pathTitle.setPadding(dp(2),dp(24),0,dp(6));body.addView(pathTitle);
        boolean recognitionDone=run.analysisPendingListings==0;
        body.addView(engineStepRow(R.drawable.provider_bgg_logo,"Riconoscimento giochi",run.bggMatchedListings+" giochi riconosciuti",recognitionDone,PURPLE,null));
        body.addView(engineStepRow(R.drawable.provider_vinted_logo,"Collegamento annunci",run.vintedLinkedListings+" collegati"+(remoteRemaining>0?" · "+remoteRemaining+" da verificare":""),recognitionDone&&remoteRemaining==0,VINTED_BG,null));
        body.addView(engineStepRow(R.drawable.ic_deal_good,"Preparazione risultati",run.completeListings+" risultati pronti",DealDatabase.engineContentSettled(run),TEAL,null));

        TextView how=text("Come funziona questo lavoro",17,TEXT,Typeface.BOLD);how.setPadding(dp(2),dp(24),0,dp(8));body.addView(how);
        body.addView(engineExplanationRow("Riconoscimento giochi","Ludo confronta ciò che hai visto su Vinted con BoardGameGeek."));
        body.addView(engineExplanationRow("Collegamento annunci","Quando il gioco è riconosciuto, prova a identificare l’annuncio Vinted esatto."));
        body.addView(engineExplanationRow("Preparazione risultati","Solo i collegamenti abbastanza affidabili diventano risultati utilizzabili."));
        if(run.completeListings>0){Button ready=button("Vedi "+run.completeListings+(run.completeListings==1?" risultato pronto":" risultati pronti"),LIME);ready.setOnClickListener(v->openEngineRun(run,"ready"));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(50));bp.topMargin=dp(20);body.addView(ready,bp);}
        TextView all=text("Vedi tutte le card dello scroll  ›",14,CYAN,Typeface.BOLD);all.setGravity(Gravity.CENTER_VERTICAL);all.setMinHeight(dp(50));all.setOnClickListener(v->openEngineRun(run,"all"));body.addView(all);
    }

    private View engineExplanationRow(String title,String detail){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(dp(2),dp(10),dp(2),dp(10));row.addView(text(title,14,TEXT,Typeface.BOLD));row.addView(text(detail,12,MUTED,Typeface.NORMAL));return row;
    }

    private String engineRunFilterLabel(){if("ready".equals(engineRunFilter))return"Risultati pronti";if("working".equals(engineRunFilter))return"In lavorazione";return"Tutti i risultati";}

    private void renderEngineRun(){
        requestEngineRunSnapshot(engineRunStart,engineRunEnd,engineRunFilter);
        EngineRunSnapshot snapshot=engineRunSnapshot;
        if(snapshot==null||snapshot.startAt!=engineRunStart||snapshot.endAt!=engineRunEnd||!snapshot.filter.equals(engineRunFilter)){renderEngineHeader("Scroll","Sto caricando i dettagli dello scroll…",true);body.addView(engineSnapshotMessage("Lettura in corso"));return;}
        DealDatabase.ObservationSession run=snapshot.run;if(run==null){engineSection="overview";renderEngineOverview();return;}
        renderEngineHeader("Scroll · "+engineTime(run.startAt),run.validListings+" giochi trovati · "+run.uniqueListings+" annunci Vinted osservati",true);
        LinearLayout tabs=new LinearLayout(this);tabs.setGravity(Gravity.CENTER_VERTICAL);String[][] opts={{"all","Tutti"},{"ready","Pronti"},{"working","In lavorazione"}};for(int i=0;i<opts.length;i++){final String key=opts[i][0];boolean selected=key.equals(engineRunFilter);TextView chip=materialChip((selected?"✓ ":"")+opts[i][1],selected?SURFACE2:SURFACE,selected?TEXT:MUTED,selected);chip.setOnClickListener(v->{engineRunFilter=key;engineRunPage=0;render();});LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(38),1);if(i>0)cp.leftMargin=dp(6);tabs.addView(chip,cp);}body.addView(tabs);
        final int pageSize=24;List<DealDatabase.EngineRunItem> items=snapshot.items;int total=items.size();int pages=Math.max(1,(total+pageSize-1)/pageSize);if(engineRunPage>=pages)engineRunPage=pages-1;if(engineRunPage<0)engineRunPage=0;int pageStart=Math.min(total,engineRunPage*pageSize),pageEnd=Math.min(total,pageStart+pageSize);
        String shown=total==0?"0 risultati":(pageStart+1)+"–"+pageEnd+" di "+total;TextView count=text(shown,13,MUTED,Typeface.BOLD);count.setPadding(0,dp(16),0,dp(8));body.addView(count);
        if(items.isEmpty()){String msg="ready".equals(engineRunFilter)?"Nessun risultato è ancora pronto.":"working".equals(engineRunFilter)?"Non ci sono risultati ancora in lavorazione.":"Nessun risultato da mostrare.";body.addView(emptyState("Niente da mostrare",msg,R.drawable.ludo_refresh));return;}
        for(int i=pageStart;i<pageEnd;i++)body.addView(engineRunItemCard(items.get(i)));
        if(pages>1){LinearLayout pager=new LinearLayout(this);pager.setGravity(Gravity.CENTER_VERTICAL);pager.setPadding(0,dp(6),0,dp(6));Button prev=button("‹ Precedenti",SURFACE2);prev.setTextColor(engineRunPage>0?CYAN:MUTED);prev.setEnabled(engineRunPage>0);prev.setOnClickListener(v->{engineRunPage=Math.max(0,engineRunPage-1);render();if(scroll!=null)scroll.scrollTo(0,0);});pager.addView(prev,new LinearLayout.LayoutParams(0,dp(44),1));TextView page=text((engineRunPage+1)+" / "+pages,12,MUTED,Typeface.BOLD);page.setGravity(Gravity.CENTER);pager.addView(page,new LinearLayout.LayoutParams(dp(72),dp(44)));Button next=button("Successive ›",SURFACE2);next.setTextColor(engineRunPage+1<pages?CYAN:MUTED);next.setEnabled(engineRunPage+1<pages);next.setOnClickListener(v->{engineRunPage=Math.min(pages-1,engineRunPage+1);render();if(scroll!=null)scroll.scrollTo(0,0);});pager.addView(next,new LinearLayout.LayoutParams(0,dp(44),1));body.addView(pager);}
    }

    private View engineRunThumbnailView(DealDatabase.EngineRunItem item,int w,int h){
        FrameLayout frame=new FrameLayout(this);frame.setBackground(round(SURFACE2,14,1,OUTLINE));frame.setClipToOutline(true);ImageView im=new ImageView(this);im.setScaleType(ImageView.ScaleType.CENTER_CROP);frame.addView(im,new FrameLayout.LayoutParams(-1,-1));boolean loaded=false;
        if(item!=null&&!TextUtils.isEmpty(item.signature)){File local=ThumbnailStore.fileFor(this,item.signature);if(local.exists()&&local.length()>1024){im.setTag(item.signature);loadEngineRunThumbnail(im,local,item.signature,Math.max(140,w*2),Math.max(180,h*2));loaded=true;}}
        if(!loaded&&item!=null&&!TextUtils.isEmpty(item.imageUrl)){loadRemote(im,item.imageUrl);loaded=true;}
        if(!loaded){im.setImageDrawable(iconDrawable(LudoIcons.IMAGE,MUTED,24));}frame.setContentDescription("Foto osservata dell'annuncio Vinted");return frame;
    }
    private void loadEngineRunThumbnail(ImageView image,File file,String signature,int width,int height){
        galleryNet.execute(()->{Bitmap bitmap=decodeLocalBitmap(file,width,height);if(bitmap==null)return;runOnUiThread(()->{if(!isDestroyed()&&signature.equals(image.getTag()))image.setImageBitmap(bitmap);});});
    }

    private View engineRunItemCard(DealDatabase.EngineRunItem item){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(16),dp(16),dp(16));
        int accent=item.review?ORANGE:item.complete?TEAL:item.held?MUTED:CYAN;card.setBackground(round(SURFACE,18,1,OUTLINE));LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        View art=engineRunThumbnailView(item,66,82);row.addView(art,new LinearLayout.LayoutParams(dp(66),dp(82)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(12),0,dp(8),0);
        TextView title=text(TextUtils.isEmpty(item.title)?(TextUtils.isEmpty(item.canonical)?"Annuncio":item.canonical):item.title,15,TEXT,Typeface.BOLD);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);copy.addView(title);
        if(!TextUtils.isEmpty(item.canonical)&&!item.canonical.equals(item.title))copy.addView(text(item.canonical,12,MUTED,Typeface.NORMAL));
        String status;if(item.review)status="Serve una tua scelta";else if(item.held)status="Non pubblicato automaticamente";else if(item.complete)status="Pronto";else if(!item.bggReady)status="Riconosco il gioco";else if(!item.vintedReady)status="Cerco l’annuncio Vinted esatto";else if("BGG_VARIANT_PENDING".equals(item.listingMatchState))status="Confermo la variante BGG";else status="Preparo il risultato";
        copy.addView(text(status,12,accent,Typeface.BOLD));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));if(item.priceCents>0)row.addView(text(money(item.priceCents),15,LIME,Typeface.BOLD));card.addView(row);card.setOnClickListener(v->openEngineRunItem(item));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(8);card.setLayoutParams(lp);return card;
    }

    private void openEngineRunItem(DealDatabase.EngineRunItem item){
        if(item==null)return;
        openListingProductDetail(item.listingId,item.gameId,item.signature);
    }

    private void openListingProductDetail(long listingId,long gameId,String signature){
        MarketListingRecord listing=listingId>0?marketStore.listing(listingId):null;
        String sig=!TextUtils.isEmpty(signature)?signature:(listingId>0?marketStore.signatureForListing(listingId):null);
        DealRecord deal=!TextUtils.isEmpty(sig)?db.findBySignature(sig):null;
        if(deal==null&&listing!=null)deal=dealForListing(listing,gameId);
        if(deal!=null){openDetail(deal);return;}
        long resolvedGame=gameId>0?gameId:(listing!=null&&listing.gameId!=null?listing.gameId:0L);
        if(resolvedGame>0){openDatabaseGame(resolvedGame,tab);return;}
        Toast.makeText(this,"Questa card non ha ancora abbastanza dati per aprire la scheda.",Toast.LENGTH_SHORT).show();
    }

    private void renderEngineReview(){
        renderEngineHeader("Serve il tuo aiuto","Qui Ludo ti chiede solo una decisione precisa: collegamento Vinted, gioco BGG o variante.",true);
        List<MarketStore.Job> reviewJobs=marketStore.reviewJobs(80);List<GameRecord> bggReview=marketStore.bggMatchReviewGames(80);
        List<MarketStore.Job> vintedReviews=new ArrayList<>(),variantReviews=new ArrayList<>();
        for(MarketStore.Job job:reviewJobs){MarketListingRecord l=job==null?null:marketStore.listing(job.listingId);if(l!=null&&"BGG_VARIANT_REVIEW".equals(l.matchState))variantReviews.add(job);else vintedReviews.add(job);}
        if(vintedReviews.isEmpty()&&variantReviews.isEmpty()&&bggReview.isEmpty()){body.addView(emptyState("Tutto sbloccato","Non ci sono card che richiedono una tua scelta.",R.drawable.ludo_refresh));return;}
        if(!vintedReviews.isEmpty()){TextView h=text("Collegamento Vinted",14,MUTED,Typeface.BOLD);h.setPadding(2,dp(10),0,dp(4));body.addView(h);for(MarketStore.Job job:vintedReviews)body.addView(reviewJobCard(job));}
        if(!bggReview.isEmpty()){TextView h=text("Gioco BGG",14,MUTED,Typeface.BOLD);h.setPadding(2,dp(18),0,dp(4));body.addView(h);for(GameRecord game:bggReview)body.addView(bggMatchReviewCard(game));}
        if(!variantReviews.isEmpty()){TextView h=text("Variante / abbinamento BGG",14,MUTED,Typeface.BOLD);h.setPadding(2,dp(18),0,dp(4));body.addView(h);for(MarketStore.Job job:variantReviews)body.addView(reviewJobCard(job));}
    }

    private View engineSnapshotMessage(String message){
        LinearLayout loading=new LinearLayout(this);loading.setOrientation(LinearLayout.VERTICAL);loading.setGravity(Gravity.CENTER);loading.setPadding(dp(20),dp(32),dp(20),dp(32));FrameLayout orbit=new FrameLayout(this);orbit.setContentDescription("Caricamento in corso");TextView icon=appIcon(LudoIcons.GAMEPAD,38,DISCOVER_LAVENDER);icon.setGravity(Gravity.CENTER);orbit.addView(icon,new FrameLayout.LayoutParams(dp(52),dp(52),Gravity.CENTER));ProgressBar progress=new ProgressBar(this);progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(DISCOVER_LAVENDER));orbit.addView(progress,new FrameLayout.LayoutParams(dp(144),dp(144),Gravity.CENTER));loading.addView(orbit,new LinearLayout.LayoutParams(dp(168),dp(168)));TextView hint=text(message.contains("disponibile")?"Riprovo automaticamente":message,14,MUTED,Typeface.NORMAL);hint.setGravity(Gravity.CENTER);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=dp(24);loading.addView(hint,hp);loading.setLayoutParams(new LinearLayout.LayoutParams(-1,Math.max(dp(320),getResources().getDisplayMetrics().heightPixels-dp(180))));return loading;
    }
    private void requestEngineHistorySnapshot(){
        if((engineHistorySnapshot!=null&&System.currentTimeMillis()-engineHistoryLoadedAt<30_000L)||System.currentTimeMillis()<engineHistoryRetryAt||!engineHistoryLoading.compareAndSet(false,true))return;
        uiDataIo.execute(()->{List<DealDatabase.ObservationDay> loaded=null;try{loaded=db.recentObservationDays(30);}catch(Throwable t){engineHistoryRetryAt=System.currentTimeMillis()+5000L;}final List<DealDatabase.ObservationDay> ready=loaded;runOnUiThread(()->{engineHistoryLoading.set(false);if(ready!=null){engineHistorySnapshot=ready;engineHistoryLoadedAt=System.currentTimeMillis();}if("activity".equals(tab)&&"history".equals(engineSection)&&!isDestroyed())scheduleRender(ready==null?Math.max(250L,engineHistoryRetryAt-System.currentTimeMillis()):0);});});
    }
    private void requestEngineDaySnapshot(long start,long end){
        EngineDaySnapshot cached=engineDaySnapshot;if(cached!=null&&cached.startAt==start&&cached.endAt==end&&System.currentTimeMillis()-cached.loadedAt<30_000L)return;
        if(System.currentTimeMillis()<engineDayRetryAt||!engineDayLoading.compareAndSet(false,true))return;
        uiDataIo.execute(()->{EngineDaySnapshot loaded=null;try{List<DealDatabase.ObservationSession> sessions=db.observationSessionsBetween(start,end,50);DealDatabase.ObservationSession active=db.activeObservationSession();List<EngineDaySession> rows=new ArrayList<>();for(DealDatabase.ObservationSession session:sessions)rows.add(new EngineDaySession(session,db.isObservationSessionWaiting(session),db.isObservationSessionDeferred(session),active!=null&&active.startAt==session.startAt));loaded=new EngineDaySnapshot(start,end,rows);}catch(Throwable t){engineDayRetryAt=System.currentTimeMillis()+5000L;}final EngineDaySnapshot ready=loaded;runOnUiThread(()->{engineDayLoading.set(false);if(ready!=null)engineDaySnapshot=ready;if("activity".equals(tab)&&"day".equals(engineSection)&&!isDestroyed())scheduleRender(ready==null?Math.max(250L,engineDayRetryAt-System.currentTimeMillis()):0);});});
    }
    private void requestEngineRunSnapshot(long start,long end,String filter){
        EngineRunSnapshot cached=engineRunSnapshot;if(cached!=null&&cached.startAt==start&&cached.endAt==end&&cached.filter.equals(filter)&&System.currentTimeMillis()-cached.loadedAt<30_000L)return;
        if(engineRunLoading.get()&&engineRunRequestedStart==start&&engineRunRequestedEnd==end&&engineRunRequestedFilter.equals(filter))return;
        if(System.currentTimeMillis()<engineRunRetryAt)return;
        engineRunRequestedStart=start;engineRunRequestedEnd=end;engineRunRequestedFilter=filter;
        if(!engineRunLoading.compareAndSet(false,true))return;
        uiDataIo.execute(()->{EngineRunSnapshot loaded=null;try{List<DealDatabase.ObservationSession> sessions=db.observationSessionsBetween(start,end,8);DealDatabase.ObservationSession run=null;for(DealDatabase.ObservationSession session:sessions)if(session.startAt==start&&session.endAt==end){run=session;break;}if(run==null&&!sessions.isEmpty())run=sessions.get(0);List<DealDatabase.EngineRunItem> items=run==null?Collections.emptyList():db.engineRunItems(run.startAt,run.endAt,filter,220);loaded=new EngineRunSnapshot(start,end,filter,run,items);}catch(Throwable t){engineRunRetryAt=System.currentTimeMillis()+5000L;}final EngineRunSnapshot ready=loaded;runOnUiThread(()->{engineRunLoading.set(false);if(ready!=null)engineRunSnapshot=ready;if("activity".equals(tab)&&("run".equals(engineSection)||"work".equals(engineSection))&&!isDestroyed())scheduleRender(ready==null?Math.max(250L,engineRunRetryAt-System.currentTimeMillis()):0);});});
    }

    private void renderEngineHistory(){
        renderEngineHeader("Cronologia Motore","Un riepilogo per giorno; i singoli scroll restano dentro il dettaglio",true);
        requestEngineHistorySnapshot();List<DealDatabase.ObservationDay> days=engineHistorySnapshot;
        if(days==null){body.addView(engineSnapshotMessage("Carico la cronologia…"));return;}
        if(days.isEmpty()){body.addView(emptyState("Ancora nessuna cronologia","Il prossimo scroll creerà il primo giorno di attività.",R.drawable.ludo_refresh));return;}
        for(int i=0;i<days.size();i++)body.addView(engineDayRow(days.get(i),i==days.size()-1));
    }

    private void renderEngineDay(){
        if(engineDayStart<=0){engineSection="history";renderEngineHistory();return;}
        requestEngineDaySnapshot(engineDayStart,engineDayEnd);
        EngineDaySnapshot snapshot=engineDaySnapshot;
        if(snapshot==null||snapshot.startAt!=engineDayStart||snapshot.endAt!=engineDayEnd){renderEngineHeader(engineDayLabel(engineDayStart),"Sto caricando gli scroll di questa giornata…",true);body.addView(engineSnapshotMessage("Lettura in corso"));return;}
        String label=engineDayLabel(engineDayStart);renderEngineHeader(label,"Tutti gli scroll di questa giornata",true);
        if(snapshot.sessions.isEmpty()){body.addView(emptyState("Nessuno scroll","Non ci sono osservazioni in questa giornata.",R.drawable.ludo_refresh));return;}
        int cards=0,valid=0,ready=0,review=0,held=0;for(EngineDaySession entry:snapshot.sessions){DealDatabase.ObservationSession x=entry.session;cards+=x.uniqueListings;valid+=x.validListings;ready+=x.completeListings;review+=x.reviewListings;held+=x.heldListings;}
        LinearLayout summary=new LinearLayout(this);summary.setOrientation(LinearLayout.VERTICAL);summary.setPadding(0,dp(4),0,dp(18));summary.addView(text(ready+" risultati pronti",28,TEXT,Typeface.BOLD));summary.addView(text(cards+" annunci osservati · "+snapshot.sessions.size()+" scroll"+(review>0?" · "+review+" richiedono una scelta":"")+(held>0?" · "+held+" non pubblicati automaticamente":""),13,MUTED,Typeface.NORMAL));body.addView(summary);
        for(int i=0;i<snapshot.sessions.size();i++){EngineDaySession entry=snapshot.sessions.get(i);DealDatabase.ObservationSession x=entry.session;LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(LinearLayout.VERTICAL);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(2),dp(13),0,dp(13));LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.addView(text("Scroll · "+engineTime(x.startAt),16,TEXT,Typeface.BOLD));copy.addView(text(x.uniqueListings+" annunci osservati · "+x.validListings+" giochi trovati",12,MUTED,Typeface.NORMAL));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
            String status=entry.deferred?"Riprenderà":entry.waiting?"In attesa":entry.active&&!DealDatabase.engineContentSettled(x)?"In lavorazione":x.completeListings+" risultati pronti";int statusColor=entry.deferred?PURPLE:entry.waiting?MUTED:entry.active?CYAN:TEAL;TextView st=text(status,12,statusColor,Typeface.BOLD);st.setGravity(Gravity.RIGHT);row.addView(st);TextView arrow=appIcon(LudoIcons.CHEVRON_RIGHT,16,MUTED);arrow.setGravity(Gravity.CENTER);row.addView(arrow,new LinearLayout.LayoutParams(dp(34),dp(48)));row.setOnClickListener(v->openObservationSession(x));wrap.addView(row);if(i<snapshot.sessions.size()-1)wrap.addView(engineDivider());body.addView(wrap);}
    }

    private void addQueueControlPanels(){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(12),0,dp(4));View v=queueControlPanel(true);View b=queueControlPanel(false);row.addView(v,new LinearLayout.LayoutParams(0,dp(138),1));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(138),1);bp.leftMargin=dp(10);row.addView(b,bp);body.addView(row);
    }

    private View queueControlPanel(boolean vinted){
        long now=System.currentTimeMillis();boolean paused=vinted?marketStore.isVintedPaused():marketStore.isBggPaused();int accent=vinted?VINTED_BG:PURPLE;int iconRes=vinted?R.drawable.provider_vinted_logo:R.drawable.provider_bgg_logo;String title=vinted?"Vinted":"BGG";String state,detail;boolean stalled=false;
        if(vinted){int due=marketStore.runnableVintedDueCount(now),active=marketStore.vintedActiveCount(),deferred=marketStore.deferredVintedCount();long wait=VintedPublicSession.waitUntil(this),age=Math.max(0L,now-marketStore.laneHeartbeatAt("vinted"));boolean processing=marketStore.processingVintedCount()>0;if(paused){state="In pausa";detail=active+" attività di rete";}else if(processing){state="In corso";String current=marketStore.currentUserVisibleProcessingLabel();detail=TextUtils.isEmpty(current)?"Collego annuncio":current;}else if(wait>now&&(due>0||active>0||deferred>0)){state="Attesa";detail="Riparte "+retryCountdown(wait)+" · BGG/locali continuano";}else if(due>0&&age>35_000L){state="Fermo";detail=due+" pronte";stalled=true;}else if(due>0){state="Pronto";detail=due+" attività";}else if(active>0){long next=marketStore.nextRunnableVintedDueAt();state="Attesa";detail=next>now?retryCountdown(next):active+" in coda";}else if(deferred>0){state="Graduale";detail=deferred+" annunci da collegare";}else{state="Aggiornato";detail="Nessun collegamento pendente";}}
        else{int due=marketStore.runnableBggDueCount(now),active=marketStore.bggActiveCount(),matching=marketStore.bggMatchRequiredCount(),review=marketStore.bggMatchReviewCount();long age=Math.max(0L,now-marketStore.laneHeartbeatAt("bgg"));boolean processing=marketStore.processingCount(MarketStore.JOB_BGG)>0;MarketStore.RuntimeStatus rs=marketStore.laneStatus("bgg");if(paused){state="In pausa";detail=(active+matching)+" attività";}else if(processing){List<MarketStore.Job> running=marketStore.processingJobsOfType(MarketStore.JOB_BGG,20);state="In corso";detail=running.size()>1?"Batch · "+running.size()+" giochi":running.isEmpty()?"Aggiorno scheda":running.get(0).label;}else if("MATCHING".equals(rs.state)){state="Riconosco giochi";detail=TextUtils.isEmpty(rs.detail)?matching+" da abbinare":rs.detail;}else if(due>0&&age>35_000L){state="Fermo";detail=due+" schede pronte";stalled=true;}else if(due>0){state="Pronto";detail=due+" schede";}else if(matching>0){state="Abbino giochi";detail=matching+" da riconoscere";}else if(review>0){state="Da verificare";detail=review+" match manuali";}else if(active>0){state="Attesa";detail=active+" schede";}else{state="Aggiornato";detail="Nessuna scheda in coda";}}
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(12),dp(10),dp(12),dp(10));card.setBackground(round(SURFACE2,20,1,stalled?ORANGE:(paused?OUTLINE:accent)));LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);View logo=providerBadge(!vinted);top.addView(logo,new LinearLayout.LayoutParams(dp(38),dp(38)));LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(8),0,0,0);copy.addView(text(title,15,TEXT,Typeface.BOLD));copy.addView(text(state,12,stalled?ORANGE:(paused?MUTED:TEXT),Typeface.BOLD));top.addView(copy,new LinearLayout.LayoutParams(0,-2,1));TextView action=text(paused?"▶":stalled?"↻":"■",18,paused?TEAL:(stalled?ORANGE:MUTED),Typeface.BOLD);action.setGravity(Gravity.CENTER);top.addView(action,new LinearLayout.LayoutParams(dp(30),dp(38)));card.addView(top);TextView d=text(detail,11,MUTED,Typeface.NORMAL);d.setMaxLines(2);d.setEllipsize(TextUtils.TruncateAt.END);d.setPadding(0,dp(6),0,0);card.addView(d);int analysisTotal=vinted?marketStore.vintedActiveCount():(marketStore.bggActiveCount()+marketStore.bggMatchRequiredCount());int manualTotal=vinted?marketStore.vintedReviewCount():marketStore.bggMatchReviewCount();String countCopy=vinted?("Rete "+analysisTotal+" · da collegare "+marketStore.deferredVintedCount()+" · locali "+marketStore.localOnlyListingCount()+" · manuali "+manualTotal):("Giochi da analizzare "+analysisTotal+" · manuali "+manualTotal);TextView counts=text(countCopy,11,MUTED,Typeface.BOLD);counts.setPadding(0,dp(4),0,0);card.addView(counts);final boolean fStalled=stalled;card.setContentDescription(title+". "+state+". "+detail+". "+(paused?"Tocca per riprendere":stalled?"Tocca per riavviare":"Tocca per mettere in pausa"));card.setOnClickListener(x->{if(fStalled){kickActivityQueue();return;}if(vinted){marketStore.setVintedPaused(!paused);if(paused)QueueKeepAliveService.ensureRunning(this);}else{marketStore.setBggPaused(!paused);if(paused)QueueKeepAliveService.ensureRunning(this);}render();});return card;
    }

    private void addLocalBacklogOptimizer(){
        int local=marketStore.deferredVintedCount()+marketStore.localOnlyListingCount()+marketStore.pendingAnalysisCount();if(local<=0)return;
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(14),dp(10),dp(12),dp(10));row.setBackground(round(SURFACE2,16,1,OUTLINE));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.addView(text("Analisi locale · "+compactCount(local),13,TEXT,Typeface.BOLD));copy.addView(text("Zero rete · filtra, lingua, rating e backlog prima di Vinted",11,MUTED,Typeface.NORMAL));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        Button run=button("Ottimizza",CYAN);run.setTextColor(BG);run.setOnClickListener(v->runLocalBacklogOptimizer());row.addView(run,new LinearLayout.LayoutParams(dp(104),dp(42)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);body.addView(row,lp);
    }

    private void runLocalBacklogOptimizer(){
        final Dialog dialog=new Dialog(this);dialog.getWindow();LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(28),dp(28),dp(28),dp(28));box.setBackgroundColor(BG);TextView title=text("Ottimizzo il database",24,TEXT,Typeface.BOLD);box.addView(title);TextView status=text("Analizzo sul telefono senza contattare Vinted…",14,MUTED,Typeface.NORMAL);LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,-2);slp.topMargin=dp(14);box.addView(status,slp);ProgressBar progress=new ProgressBar(this);progress.setIndeterminate(true);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-2,-2);pp.gravity=Gravity.CENTER_HORIZONTAL;pp.topMargin=dp(28);box.addView(progress,pp);TextView note=text("Puoi lasciare che finisca questa operazione: i collegamenti Vinted verranno messi in rete solo dopo il filtro locale.",12,MUTED,Typeface.NORMAL);LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(-1,-2);nlp.topMargin=dp(22);box.addView(note,nlp);dialog.setContentView(box);Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(BG));w.setLayout(-1,-1);}dialog.setCancelable(false);dialog.show();if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,-1);
        maintenanceIo.execute(()->{MarketStore.LocalBatchSummary r=marketStore.optimizeLocalBacklog();runOnUiThread(()->{if(dialog.isShowing())dialog.dismiss();Toast.makeText(this,"Analisi locale completata · "+r.before+" → "+r.after+" · non-giochi "+r.nonGamesHidden,Toast.LENGTH_LONG).show();QueueKeepAliveService.ensureRunning(this);scheduleRender(0);});});
    }

    private View providerBadge(boolean bgg){FrameLayout wrap=new FrameLayout(this);int accent=bgg?PURPLE:VINTED_BG;wrap.setBackground(round(bgg?BGG_BG:VINTED_BG,999,1,accent));wrap.setClipToOutline(true);ImageView im=new ImageView(this);im.setImageResource(bgg?R.drawable.provider_bgg_logo:R.drawable.provider_vinted_logo);im.setScaleType(ImageView.ScaleType.CENTER_CROP);im.setPadding(bgg?dp(3):0,bgg?dp(3):0,bgg?dp(3):0,bgg?dp(3):0);wrap.addView(im,new FrameLayout.LayoutParams(-1,-1));wrap.setContentDescription(bgg?"BoardGameGeek":"Vinted");return wrap;}

    private void addQueueLaneControls(){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(12),0,0);
        boolean vintedPaused=marketStore.isVintedPaused(),databasePaused=marketStore.isBggPaused();
        TextView vinted=queueLaneChip("Vinted",!vintedPaused,VINTED_BG);vinted.setContentDescription(vintedPaused?"Vinted in pausa. Tocca per riprendere":"Vinted attivo. Tocca per mettere in pausa");vinted.setOnClickListener(v->{marketStore.setVintedPaused(!vintedPaused);if(vintedPaused)QueueKeepAliveService.ensureRunning(this);render();});row.addView(vinted,new LinearLayout.LayoutParams(0,dp(42),1));
        TextView database=queueLaneChip("Database",!databasePaused,PURPLE);database.setContentDescription(databasePaused?"Database BGG in pausa. Tocca per riprendere":"Database BGG attivo. Tocca per mettere in pausa");database.setOnClickListener(v->{marketStore.setBggPaused(!databasePaused);if(databasePaused)QueueKeepAliveService.ensureRunning(this);render();});LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(0,dp(42),1);dpv.leftMargin=dp(8);row.addView(database,dpv);body.addView(row);
    }

    private TextView queueLaneChip(String label,boolean active,int accent){TextView chip=text(label+(active?"  ●":"  Ⅱ"),13,active?TEXT:MUTED,Typeface.BOLD);chip.setGravity(Gravity.CENTER);chip.setBackground(round(active?SURFACE2:SURFACE,999,1,active?accent:OUTLINE));return chip;}

    private void addQueueLaneStatusCards(){
        long now=System.currentTimeMillis();
        int vDue=marketStore.runnableVintedDueCount(now),bDue=marketStore.runnableBggDueCount(now);int vActive=marketStore.coreVintedActiveCount(),bActive=marketStore.bggActiveCount(),bBlocked=marketStore.bggMatchRequiredCount();
        long vHeartbeat=marketStore.laneHeartbeatAt("vinted"),bHeartbeat=marketStore.laneHeartbeatAt("bgg");long vAge=vHeartbeat<=0?Long.MAX_VALUE:now-vHeartbeat,bAge=bHeartbeat<=0?Long.MAX_VALUE:now-bHeartbeat;
        MarketStore.RuntimeStatus vStatus=marketStore.laneStatus("vinted"),bStatus=marketStore.laneStatus("bgg");
        boolean vProcessing=marketStore.processingCount(MarketStore.JOB_VINTED)>0;long vWait=VintedPublicSession.waitUntil(this);String vWaitReason=VintedPublicSession.waitReason(this);String vt,vd;int va;boolean vStalled=false;
        if(marketStore.isVintedPaused()){vt="Vinted · in pausa";vd=vActive+" attività";va=MUTED;}
        else if(vProcessing){String current=marketStore.currentUserVisibleProcessingLabel();vt="Vinted · in corso";vd=TextUtils.isEmpty(current)?"Raccolgo i dati dell’annuncio":current;va=CYAN;}
        else if(vDue>0&&vWait>now){vt="Vinted · in attesa";vd=vintedWaitCopy(vWaitReason)+" · "+retryCountdown(vWait);va=YELLOW;}
        else if(vDue>0&&vAge>35_000L){vt="Vinted · fermo";vd=vDue+" attività pronte";va=ORANGE;vStalled=true;}
        else if(vDue>0){vt="Vinted · pronto";vd=vDue+" attività da elaborare";va=CYAN;}
        else if(vActive>0){long next=marketStore.nextRunnableVintedDueAt();vt="Vinted · in attesa";vd=next>now?"Riprendo "+retryCountdown(next):vActive+" attività non ancora eseguibili";va=YELLOW;}
        else{vt="Vinted · aggiornato";vd="Nessuna attività urgente";va=TEAL;}
        addLaneStatusCard(vt,vd,va,vStalled);

        boolean bProcessing=marketStore.processingCount(MarketStore.JOB_BGG)>0;String bt,bd;int ba;boolean bStalled=false;
        if(marketStore.isBggPaused()){bt="Database · in pausa";bd=bActive+" schede";ba=MUTED;}
        else if(bProcessing){List<MarketStore.Job> running=marketStore.processingJobsOfType(MarketStore.JOB_BGG,20);bt="Database · BGG in corso";bd=running.size()>1?"Batch di "+running.size()+" giochi":running.isEmpty()?"Aggiorno la scheda BGG":running.get(0).label;ba=PURPLE;}
        else if(bDue>0&&bAge>35_000L){bt="Database · fermo";bd=bDue+" schede BGG pronte";ba=ORANGE;bStalled=true;}
        else if(bDue>0){bt="Database · pronto";bd=bDue+" schede BGG pronte";ba=PURPLE;}
        else if(bBlocked>0){bt="Database · attende match BGG";bd=bBlocked+" giochi senza un BGG ID confermato";ba=YELLOW;}
        else if(bActive>0){long next=marketStore.nextRunnableBggDueAt();bt="Database · in attesa";bd=next>now?"Riprendo "+retryCountdown(next):"Nessuna scheda BGG pronta";ba=YELLOW;}
        else{bt="Database · aggiornato";bd="Nessuna scheda BGG in coda";ba=TEAL;}
        addLaneStatusCard(bt,bd,ba,bStalled);
    }

    private void addLaneStatusCard(String title,String detail,int accent,boolean stalled){LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(14),dp(11),dp(12),dp(11));card.setBackground(round(SURFACE2,18,1,accent));LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.addView(text(title,14,TEXT,Typeface.BOLD));copy.addView(text(detail,12,MUTED,Typeface.NORMAL));card.addView(copy,new LinearLayout.LayoutParams(0,-2,1));if(stalled){Button wake=button("Riavvia",CYAN);wake.setOnClickListener(v->kickActivityQueue());card.addView(wake,new LinearLayout.LayoutParams(dp(96),dp(40)));}LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);body.addView(card,lp);}

    private String vintedWaitCopy(String reason){if("REMOTE_LIMIT".equals(reason))return"Vinted ha chiesto una pausa";if("LOCAL_BUDGET".equals(reason))return"Pausa del budget richieste";if("COORDINATOR_BUSY".equals(reason))return"Coordino le richieste";return"Prossima richiesta consentita";}
    private String friendlyLaneState(String state,int due){if("STARTING".equals(state))return"Motore in avvio";if("CLAIMING".equals(state))return"Scelgo la prossima · "+due+" pronte";if("ACTIVE".equals(state))return"Passo alla prossima · "+due+" pronte";return due+" attività pronte";}

    private View persistentJobCard(MarketStore.Job job,boolean historical){
        LinearLayout card=verticalCard();card.setPadding(dp(13),dp(11),dp(13),dp(11));
        int accent=MarketStore.COMPLETE.equals(job.state)?TEAL:MarketStore.PROCESSING.equals(job.state)?CYAN:MarketStore.FAILED_RETRYABLE.equals(job.state)?YELLOW:OUTLINE;long progressNow=System.currentTimeMillis();int cardProgress=MarketStore.COMPLETE.equals(job.state)?100:MarketStore.PROCESSING.equals(job.state)?Math.max(18,marketStore.visualProgress(job,progressNow)):Math.max(job.progress,MarketStore.FAILED_RETRYABLE.equals(job.state)?15:6);long flowMs=MarketStore.PROCESSING.equals(job.state)?marketStore.visualRemainingMs(job,progressNow):0L;applyJobCardFill(card,job.id,SURFACE,accent,cardProgress,flowMs);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView art=new ImageView(this);art.setScaleType(ImageView.ScaleType.FIT_CENTER);art.setBackground(round(SURFACE2,10,0,0));
        GameRecord lightweight=null;if(job.displayGameId>0||!TextUtils.isEmpty(job.displayBggId)||!TextUtils.isEmpty(job.displayImageUrl)){lightweight=new GameRecord();lightweight.id=job.displayGameId;lightweight.bggId=job.displayBggId;lightweight.thumbnailUrl=job.displayThumbnailUrl;lightweight.imageUrl=job.displayImageUrl;}
        if(lightweight!=null)setGameArtwork(art,lightweight);else art.setImageResource(R.drawable.ludo_value_book);row.addView(art,new LinearLayout.LayoutParams(dp(48),dp(60)));
        LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);tx.setPadding(dp(12),0,0,0);String title=TextUtils.isEmpty(job.label)?"Attività":""+job.label;
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView name=text(title,15,TEXT,Typeface.BOLD);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);top.addView(name,new LinearLayout.LayoutParams(0,-2,1));String origin=(historical||MarketStore.JOB_BGG.equals(job.type))?"BGG":"Vinted";View tag=providerBadge(historical||MarketStore.JOB_BGG.equals(job.type));LinearLayout.LayoutParams tagLp=new LinearLayout.LayoutParams(dp(34),dp(34));tagLp.leftMargin=dp(8);top.addView(tag,tagLp);tx.addView(top);
        String state=MarketStore.COMPLETE.equals(job.state)?"Completato":MarketStore.PROCESSING.equals(job.state)?"In corso":MarketStore.FAILED_RETRYABLE.equals(job.state)?"Riprovo":"In coda";String action=MarketStore.JOB_BGG.equals(job.type)?"Scheda BGG":"Dettagli annuncio";String progressCopy=MarketStore.PROCESSING.equals(job.state)?" · "+jobStage(job):" · "+action;tx.addView(text(state+progressCopy,12,accent==OUTLINE?MUTED:accent,Typeface.BOLD));
        if(MarketStore.FAILED_RETRYABLE.equals(job.state)&&!TextUtils.isEmpty(job.lastError)){TextView why=text(friendlyJobError(job.lastError),12,MUTED,Typeface.NORMAL);why.setPadding(0,dp(3),0,0);tx.addView(why);}
        if(job.nextAttemptAt>System.currentTimeMillis())tx.addView(retryCountdownView("Riprovo ",job.nextAttemptAt));
        if(MarketStore.PROCESSING.equals(job.state)&&job.processingStartedAt>0&&progressNow-job.processingStartedAt>75_000L){
            tx.addView(text("Sta impiegando più del solito · "+elapsed(job.processingStartedAt),12,ORANGE,Typeface.BOLD));
        }
        row.addView(tx,new LinearLayout.LayoutParams(0,-2,1));card.addView(row);
        if(MarketStore.JOB_VINTED.equals(job.type)&&MarketStore.FAILED_RETRYABLE.equals(job.state)){Button fix=button("Risolvi",SURFACE2);fix.setTextColor(CYAN);fix.setOnClickListener(v->openVintedResolution(job));LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(40));fp.topMargin=dp(8);card.addView(fix,fp);}
        if(MarketStore.PROCESSING.equals(job.state)){
            Button skip=button("Salta e continua",SURFACE2);skip.setTextColor(ORANGE);skip.setContentDescription("Interrompi questa raccolta dati, rimandala più tardi e passa subito alla prossima attività");
            skip.setOnClickListener(v->{boolean moved=marketStore.deferProcessingJob(job.id,15*60_000L,"saltato manualmente");if(moved){Toast.makeText(this,"Passo alla prossima attività",Toast.LENGTH_SHORT).show();kickActivityQueue();scheduleRender(80);}else scheduleRender(80);});
            LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(40));sp.topMargin=dp(8);card.addView(skip,sp);
        }
        final long gameId=job.displayGameId;if(gameId>0){card.setContentDescription(origin+". "+title+". "+state+". "+action);card.setOnClickListener(v->openDatabaseGame(gameId,tab));}else card.setContentDescription(origin+". "+title+". "+state+". "+action);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(8);card.setLayoutParams(lp);return card;
    }

    private String friendlyJobError(String raw){if(TextUtils.isEmpty(raw))return"Tentativo non riuscito.";String s=raw.toLowerCase(Locale.ROOT);if(s.contains("pagina vinted non disponibile")||s.contains("404"))return"La pagina Vinted sembra non essere più pubblica.";if(s.contains("nessun annuncio compatibile")||s.contains("nessun candidato"))return"Non trovo un annuncio Vinted che corrisponda con sufficiente sicurezza.";if(s.contains("più annunci compatibili")||s.contains("ambigu"))return"Ci sono più annunci plausibili: non scelgo a caso.";if(s.contains("non corrispondono abbastanza")||s.contains("score"))return"Titolo e prezzo non coincidono abbastanza con i risultati Vinted.";if(s.contains("metadati")||s.contains("non sono leggibili"))return"La pagina si apre, ma i dati Vinted necessari non sono leggibili.";if(s.contains("429")||s.contains("403")||s.contains("rate")||s.contains("limit"))return"Vinted ha chiesto di rallentare.";if(s.contains("timeout")||s.contains("timed out"))return"La richiesta non ha risposto in tempo.";if(s.contains("cooldown")||s.contains("pacing")||s.contains("prossimo slot"))return"Aspetto il prossimo slot Vinted.";String clean=raw.replace('\n',' ').trim();return clean.length()>120?clean.substring(0,117)+"…":clean;}

    private View reviewJobCard(MarketStore.Job job){
        MarketListingRecord listing=job==null?null:marketStore.listing(job.listingId);boolean variant=listing!=null&&"BGG_VARIANT_REVIEW".equals(listing.matchState);boolean hasLinked=listing!=null&&!TextUtils.isEmpty(listing.url);
        LinearLayout card=verticalCard();card.setPadding(dp(13),dp(12),dp(13),dp(12));card.setBackground(round(SURFACE,18,1,variant?PURPLE:ORANGE));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);ImageView art=new ImageView(this);art.setScaleType(ImageView.ScaleType.FIT_CENTER);art.setBackground(round(SURFACE2,10,0,0));GameRecord light=null;if(job.displayGameId>0||!TextUtils.isEmpty(job.displayImageUrl)){light=new GameRecord();light.id=job.displayGameId;light.bggId=job.displayBggId;light.thumbnailUrl=job.displayThumbnailUrl;light.imageUrl=job.displayImageUrl;}if(light!=null)setGameArtwork(art,light);else art.setImageResource(R.drawable.ludo_value_book);head.addView(art,new LinearLayout.LayoutParams(dp(48),dp(60)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(12),0,0,0);LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);titleRow.addView(text(TextUtils.isEmpty(job.label)?"Card da verificare":job.label,15,TEXT,Typeface.BOLD),new LinearLayout.LayoutParams(0,-2,1));View badge=providerBadge(variant);LinearLayout.LayoutParams blp=new LinearLayout.LayoutParams(dp(34),dp(34));blp.leftMargin=dp(8);titleRow.addView(badge,blp);TextView trash=appIcon(LudoIcons.TRASH,17,RED);trash.setPadding(dp(8),dp(8),dp(8),dp(8));trash.setContentDescription("Elimina elemento");trash.setOnClickListener(v->confirmDeleteReviewListing(job));LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(dp(40),dp(40));tlp.leftMargin=dp(4);titleRow.addView(trash,tlp);copy.addView(titleRow);
        String status;if(variant)status=hasLinked?"Vinted è già collegato. Il dubbio riguarda il gioco/variante BGG, non l’URL Vinted.":"Il dubbio riguarda il gioco/variante BGG.";else if(hasLinked)status="Ludo ha proposto un annuncio Vinted, ma non lo considera confermato finché non scegli tu.";else status="Ludo non ha trovato un annuncio Vinted abbastanza sicuro dopo "+Math.max(1,job.attempt)+" tentativi.";
        copy.addView(text(status,12,variant?PURPLE:ORANGE,Typeface.BOLD));if(!variant&&!TextUtils.isEmpty(job.lastError)){TextView why=text(friendlyJobError(job.lastError),11,MUTED,Typeface.NORMAL);why.setPadding(0,dp(3),0,0);copy.addView(why);}head.addView(copy,new LinearLayout.LayoutParams(0,-2,1));card.addView(head);

        if(variant){
            LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);actions.setPadding(0,dp(9),0,0);Button confirm=button("BGG è giusto",PURPLE);confirm.setTextColor(TEXT);confirm.setOnClickListener(v->confirmCurrentBggVariant(job));actions.addView(confirm,new LinearLayout.LayoutParams(0,dp(42),1));Button change=button("Cambia BGG",SURFACE2);change.setTextColor(PURPLE);change.setOnClickListener(v->changeBggForReviewListing(job));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(42),1);cp.leftMargin=dp(8);actions.addView(change,cp);card.addView(actions);
            if(hasLinked){Button changeVinted=button("L’annuncio Vinted è sbagliato · collegane un altro",SURFACE2);changeVinted.setTextColor(CYAN);changeVinted.setOnClickListener(v->openVintedResolution(job));LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,dp(42));vp.topMargin=dp(8);card.addView(changeVinted,vp);}
        }else if(hasLinked){
            LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);actions.setPadding(0,dp(9),0,0);Button open=button("Apri annuncio",SURFACE2);open.setTextColor(CYAN);open.setOnClickListener(v->openMarketListing(listing));actions.addView(open,new LinearLayout.LayoutParams(0,dp(42),1));Button confirm=button("È quello giusto",CYAN);confirm.setTextColor(BG);confirm.setOnClickListener(v->confirmCurrentVintedLink(job,listing));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(42),1);cp.leftMargin=dp(8);actions.addView(confirm,cp);card.addView(actions);
            Button replace=button("Collega un altro annuncio",SURFACE2);replace.setTextColor(CYAN);replace.setOnClickListener(v->openVintedResolution(job));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(42));rp.topMargin=dp(8);card.addView(replace,rp);
        }else{
            LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);actions.setPadding(0,dp(9),0,0);Button resolve=button("Cerca / collega Vinted",CYAN);resolve.setTextColor(BG);resolve.setOnClickListener(v->openVintedResolution(job));actions.addView(resolve,new LinearLayout.LayoutParams(0,dp(42),1));Button archive=button("Non più su Vinted",SURFACE2);archive.setTextColor(RED);archive.setOnClickListener(v->confirmArchiveUnresolved(job));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(42),1);ap.leftMargin=dp(8);actions.addView(archive,ap);card.addView(actions);
        }
        head.setOnClickListener(v->openListingProductDetail(job.listingId,job.displayGameId,marketStore.signatureForListing(job.listingId)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(8);card.setLayoutParams(lp);return card;
    }

    private void confirmCurrentVintedLink(MarketStore.Job job,MarketListingRecord listing){
        if(job==null||listing==null||TextUtils.isEmpty(listing.url)){return;}String itemId=!TextUtils.isEmpty(listing.vintedItemId)?listing.vintedItemId:vintedItemId(listing.url);if(TextUtils.isEmpty(itemId)){openVintedResolution(job);return;}
        applyManualVintedChoice(job,itemId,listing.url,listing.imageUrl,listing.sellerName,false,null);
    }

    private void confirmCurrentBggVariant(MarketStore.Job job){
        if(job==null||job.listingId<=0)return;String sig=marketStore.signatureForListing(job.listingId);marketStore.confirmBggVariant(job.listingId,"Abbinamento BGG confermato dall’utente");if(!TextUtils.isEmpty(sig))db.confirmBggVariant(sig,"Abbinamento BGG confermato dall’utente");Toast.makeText(this,"Abbinamento BGG confermato",Toast.LENGTH_SHORT).show();refreshMarketReferencesAsync();scheduleRender(60);
    }

    private void changeBggForReviewListing(MarketStore.Job job){
        if(job==null||job.listingId<=0)return;MarketListingRecord l=marketStore.listing(job.listingId);if(l==null)return;String sig=marketStore.signatureForListing(l.id);DealRecord d=!TextUtils.isEmpty(sig)?db.findBySignature(sig):null;if(d==null)d=dealForListing(l,job.displayGameId);if(d!=null)searchCorrection(d,null);else Toast.makeText(this,"Non ho abbastanza dati per cambiare il gioco BGG.",Toast.LENGTH_SHORT).show();
    }

    private View bggMatchReviewCard(GameRecord game){
        LinearLayout card=verticalCard();card.setPadding(dp(13),dp(11),dp(13),dp(11));card.setBackground(round(SURFACE,18,1,PURPLE));LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);DealRecord observed=representativeDealForGame(game);boolean hasVinted=observed!=null&&!TextUtils.isEmpty(observed.vintedUrl);View observedArt;if(observed!=null)observedArt=observedListingPhotoView(observed,64,82);else{ImageView bggIcon=new ImageView(this);bggIcon.setImageResource(R.drawable.provider_bgg_logo);bggIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);bggIcon.setPadding(dp(7),dp(7),dp(7),dp(7));bggIcon.setBackground(round(BGG_BG,12,0,0));observedArt=bggIcon;}row.addView(observedArt,new LinearLayout.LayoutParams(dp(64),dp(82)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(12),0,0,0);LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView title=text(game.name,15,TEXT,Typeface.BOLD);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));View logo=providerBadge(true);LinearLayout.LayoutParams lpLogo=new LinearLayout.LayoutParams(dp(34),dp(34));lpLogo.leftMargin=dp(8);top.addView(logo,lpLogo);TextView trash=appIcon(LudoIcons.TRASH,17,RED);trash.setPadding(dp(8),dp(8),dp(8),dp(8));trash.setContentDescription("Non è un gioco: elimina");trash.setOnClickListener(v->{v.setEnabled(false);excludeGameAsNonGame(game,null);});LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(dp(40),dp(40));tlp.leftMargin=dp(4);top.addView(trash,tlp);copy.addView(top);
        String sub=observed==null?"Ludo non ha trovato un match BGG abbastanza sicuro":(hasVinted?"Annuncio Vinted già collegato · qui manca solo il gioco BGG":"Manca il gioco BGG; puoi anche correggere il collegamento Vinted");copy.addView(text(sub,12,ORANGE,Typeface.BOLD));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));card.addView(row);
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);actions.setPadding(0,dp(8),0,0);Button fix=button("Collega BGG",PURPLE);fix.setTextColor(TEXT);fix.setOnClickListener(v->chooseManualBggMatch(game));actions.addView(fix,new LinearLayout.LayoutParams(0,dp(42),1));
        if(observed!=null){Button vinted=button(hasVinted?"Cambia Vinted":"Collega Vinted",SURFACE2);vinted.setTextColor(CYAN);vinted.setOnClickListener(v->openVintedRecoveryForDeal(observed,null));LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(0,dp(42),1);vp.leftMargin=dp(8);actions.addView(vinted,vp);}card.addView(actions);
        card.setOnClickListener(v->{DealRecord d=representativeDealForGame(game);if(d!=null)openDetail(d);else openDatabaseGame(game.id,tab);});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(8);card.setLayoutParams(lp);return card;
    }

    private void openVintedRecoveryForDeal(DealRecord deal,Dialog parent){
        if(deal==null||TextUtils.isEmpty(deal.signature)){Toast.makeText(this,"Annuncio non disponibile",Toast.LENGTH_SHORT).show();return;}MarketStore.Job job=marketStore.latestJobForLegacySignature(deal.signature);
        if(job==null){marketStore.enqueueListingFromLegacy(deal);job=marketStore.latestJobForLegacySignature(deal.signature);}
        if(job==null||job.listingId<=0){Toast.makeText(this,"Non riesco ad aprire il collegamento manuale",Toast.LENGTH_SHORT).show();return;}openVintedResolution(job);
    }

    private void openVintedResolution(MarketStore.Job job){
        if(job==null||job.listingId<=0)return;MarketListingRecord l=marketStore.listing(job.listingId);if(l==null){Toast.makeText(this,"Annuncio non disponibile",Toast.LENGTH_SHORT).show();return;}boolean variant="BGG_VARIANT_REVIEW".equals(l.matchState);boolean hasLinked=!TextUtils.isEmpty(l.url);
        activeVintedResolutionListingId=job.listingId;if(activeResolutionDialog!=null&&activeResolutionDialog.isShowing()){suppressResolutionDismissState=true;activeResolutionDialog.dismiss();suppressResolutionDismissState=false;}Dialog dialog=bottomSheet(hasLinked?"Verifica collegamento Vinted":"Collega annuncio Vinted");activeResolutionDialog=dialog;dialog.setOnDismissListener(x->{if(activeResolutionDialog==dialog)activeResolutionDialog=null;if(!suppressResolutionDismissState)activeVintedResolutionListingId=0L;persistTransientUiSession();});LinearLayout box=dialog.findViewById(SHEET_ID);
        String known=l.title+"\n"+money(l.currentPriceCents)+(TextUtils.isEmpty(l.brand)?"":" · "+l.brand);TextView knownView=text(known,15,TEXT,Typeface.BOLD);knownView.setPadding(0,0,0,dp(10));box.addView(knownView);
        if(hasLinked){
            TextView tag=materialChip(variant?"ANNUNCIO COLLEGATO":"PROPOSTA NON ANCORA CONFERMATA",SURFACE2,variant?PURPLE:ORANGE,true);box.addView(tag,new LinearLayout.LayoutParams(-2,dp(32)));
            TextView urlView=text(l.url,11,MUTED,Typeface.NORMAL);urlView.setMaxLines(2);urlView.setPadding(0,dp(6),0,0);box.addView(urlView);
            LinearLayout currentActions=new LinearLayout(this);currentActions.setPadding(0,dp(8),0,0);Button open=button("Apri annuncio",SURFACE2);open.setTextColor(CYAN);open.setOnClickListener(v->openMarketListing(l));currentActions.addView(open,new LinearLayout.LayoutParams(0,dp(42),1));
            if(!variant){Button confirm=button("È quello giusto",CYAN);confirm.setTextColor(BG);confirm.setOnClickListener(v->{confirmCurrentVintedLink(job,l);if(dialog.isShowing())dialog.dismiss();});LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(42),1);cp.leftMargin=dp(8);currentActions.addView(confirm,cp);}box.addView(currentActions);
            TextView change=text(variant?"Se l’annuncio Vinted è sbagliato, sostituiscilo qui. Altrimenti torna indietro e risolvi il dubbio BGG.":"Se non è quello corretto, non confermarlo: cercane o incollane un altro.",12,MUTED,Typeface.NORMAL);change.setPadding(0,dp(12),0,dp(7));box.addView(change);
        }
        Button search=button(hasLinked?"Cerca un altro annuncio su Vinted":"Cerca questo annuncio su Vinted",VINTED_BG);search.setEnabled(true);search.setAlpha(1f);search.setOnClickListener(v->openManualVintedSearch(job,l));box.addView(search,new LinearLayout.LayoutParams(-1,dp(50)));
        TextView recoveryNote=text("Durante questa ricerca Ludo ignora gli altri risultati. Apri l’annuncio corretto e condividilo con Ludo oppure incolla qui il link /items/…",12,MUTED,Typeface.NORMAL);recoveryNote.setPadding(0,dp(7),0,0);box.addView(recoveryNote);
        EditText url=input("Incolla link Vinted");LinearLayout.LayoutParams ulp=new LinearLayout.LayoutParams(-1,dp(54));ulp.topMargin=dp(9);box.addView(url,ulp);
        Button link=button(hasLinked?"Sostituisci con questo link":"Collega link",CYAN);link.setOnClickListener(v->{String value=url.getText().toString().trim();String itemId=vintedItemId(value);if(TextUtils.isEmpty(itemId)){url.setError("Incolla un link Vinted /items/…");return;}applyManualVintedChoice(job,itemId,value,null,l.sellerName,false,dialog);});LinearLayout.LayoutParams llp=new LinearLayout.LayoutParams(-1,dp(48));llp.topMargin=dp(8);box.addView(link,llp);
        List<VintedLinkResolver.CandidateOption> candidates=marketStore.vintedCandidates(job.listingId);if(!candidates.isEmpty()){TextView h=text(candidates.size()==1?"Candidato automatico":"Candidati automatici",13,MUTED,Typeface.BOLD);h.setPadding(0,dp(14),0,0);box.addView(h);for(VintedLinkResolver.CandidateOption candidate:candidates){LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(8);box.addView(vintedCandidateRow(job,candidate,dialog),cp);}}
        if(!TextUtils.isEmpty(job.lastError)){TextView why=text(friendlyJobError(job.lastError),12,MUTED,Typeface.NORMAL);why.setPadding(0,dp(10),0,0);box.addView(why);}
        if(job.id>0){Button retry=button("Riprova automatico",SURFACE2);retry.setTextColor(MUTED);retry.setOnClickListener(v->{if(marketStore.retryNow(job.id)){dialog.dismiss();kickActivityQueue();scheduleRender(80);}});LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(44));rp.topMargin=dp(10);box.addView(retry,rp);}dialog.show();
    }

    private View vintedCandidateRow(MarketStore.Job job,VintedLinkResolver.CandidateOption c,Dialog dialog){
        LinearLayout card=verticalCard();card.setPadding(dp(10),dp(10),dp(10),dp(10));LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);ImageView im=new ImageView(this);im.setScaleType(ImageView.ScaleType.CENTER_CROP);im.setBackground(round(SURFACE2,10,0,0));if(!TextUtils.isEmpty(c.imageUrl))loadRemote(im,c.imageUrl);else im.setImageResource(R.drawable.provider_vinted_logo);row.addView(im,new LinearLayout.LayoutParams(dp(54),dp(64)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);tx.setPadding(dp(10),0,0,0);TextView t=text(TextUtils.isEmpty(c.title)?"Annuncio Vinted":c.title,14,TEXT,Typeface.BOLD);t.setMaxLines(2);tx.addView(t);String meta=(Double.isNaN(c.price)?"":String.format(Locale.ITALY,"%.2f €",c.price))+(TextUtils.isEmpty(c.sellerName)?"":" · @"+c.sellerName)+(Double.isNaN(c.photoSimilarity)?"":" · foto "+Math.round(c.photoSimilarity*100)+"%")+(c.score>0?" · match "+c.score:"");tx.addView(text(meta,11,MUTED,Typeface.NORMAL));row.addView(tx,new LinearLayout.LayoutParams(0,-2,1));card.addView(row);LinearLayout actions=new LinearLayout(this);actions.setPadding(0,dp(8),0,0);Button open=button("Apri",SURFACE2);open.setTextColor(CYAN);open.setOnClickListener(v->{String u=!TextUtils.isEmpty(c.url)?c.url:"https://www.vinted.it/items/"+c.id;try{persistTransientUiSession();startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u)));}catch(Exception ignored){}});actions.addView(open,new LinearLayout.LayoutParams(0,dp(40),1));Button choose=button("È questo",CYAN);choose.setOnClickListener(v->{String u=!TextUtils.isEmpty(c.url)?c.url:"https://www.vinted.it/items/"+c.id;applyManualVintedChoice(job,c.id,u,c.imageUrl,c.sellerName,c.sold,dialog);});LinearLayout.LayoutParams chp=new LinearLayout.LayoutParams(0,dp(40),1);chp.leftMargin=dp(8);actions.addView(choose,chp);card.addView(actions);return card;
    }

    private void openManualVintedSearch(MarketStore.Job job,MarketListingRecord l){if(l==null)return;GameRecord g=marketStore.gameForListing(l);String searchTitle=preferredVintedSearchTitle(l.title,g==null?null:g.name);String signature=marketStore.signatureForListing(l.id);rememberManualVintedTarget(job==null?0:job.id,l.id,signature,searchTitle);marketStore.beginManualVintedRecovery(l.id,signature,searchTitle,MANUAL_VINTED_RECOVERY_TTL);launchVintedSearch(searchTitle);}
    private void openManualVintedSearchForDeal(DealRecord d){if(d==null)return;MarketStore.Job job=marketStore.latestJobForLegacySignature(d.signature);if(job==null){marketStore.enqueueListingFromLegacy(d);job=marketStore.latestJobForLegacySignature(d.signature);}if(job!=null&&job.listingId>0){MarketListingRecord l=marketStore.listing(job.listingId);if(l!=null){openManualVintedSearch(job,l);return;}}long listingId=marketStore.listingIdForSignature(d.signature);if(listingId>0){MarketListingRecord l=marketStore.listing(listingId);if(l!=null){String title=preferredVintedSearchTitle(l.title,!TextUtils.isEmpty(d.gameName)?d.gameName:d.displayName);rememberManualVintedTarget(0,l.id,d.signature,title);launchVintedSearch(title);return;}}launchVintedSearch(preferredVintedSearchTitle(d.vintedTitle,!TextUtils.isEmpty(d.gameName)?d.gameName:d.displayName));}
    private static String preferredVintedSearchTitle(String observed,String fallback){String x=observed==null?"":observed.trim();String n=x.toLowerCase(Locale.ROOT);boolean noisy=x.isEmpty()||n.contains("protezione acquisti")||n.contains("include la protezione")||n.matches("^\\d+(?:[.,]\\d+)?\\s*(?:€|euro).*");return noisy&&!TextUtils.isEmpty(fallback)?fallback.trim():x;}
    private void launchVintedSearch(String title){try{persistTransientUiSession();String q=URLEncoder.encode(title==null?"":title,"UTF-8");startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.vinted.it/catalog?search_text="+q)));}catch(Exception e){Toast.makeText(this,"Non riesco ad aprire la ricerca Vinted",Toast.LENGTH_SHORT).show();}}

    private void rememberManualVintedTarget(long jobId,long listingId,String signature,String title){getSharedPreferences(PREF_MANUAL_VINTED_SHARE,MODE_PRIVATE).edit().putLong("job_id",jobId).putLong("listing_id",listingId).putLong("at",System.currentTimeMillis()).putString("signature",signature==null?"":signature).putString("title",title==null?"":title).apply();}

    private void handleExternalIntent(Intent intent){
        if(intent==null)return;
        String openSignature=intent.getStringExtra("open_deal_signature");if(!TextUtils.isEmpty(openSignature)){intent.removeExtra("open_deal_signature");DealRecord d=db.findBySignature(openSignature);if(d!=null)openDetail(d);}
        if(!Intent.ACTION_SEND.equals(intent.getAction()))return;Object raw=intent.getExtras()==null?null:intent.getExtras().get(Intent.EXTRA_TEXT);String shared=raw==null?"":String.valueOf(raw);String url=extractSharedVintedUrl(shared);intent.setAction(null);intent.removeExtra(Intent.EXTRA_TEXT);if(TextUtils.isEmpty(url)){Toast.makeText(this,"Il contenuto condiviso non contiene un link Vinted.",Toast.LENGTH_SHORT).show();return;}handleSharedVintedUrl(url);
    }

    private String extractSharedVintedUrl(String raw){if(TextUtils.isEmpty(raw))return"";Matcher m=VINTED_URL.matcher(raw);if(!m.find())return"";String u=m.group();while(u.endsWith(".")||u.endsWith(",")||u.endsWith(")")||u.endsWith("]"))u=u.substring(0,u.length()-1);return u;}

    private void handleSharedVintedUrl(String url){
        String itemId=vintedItemId(url);
        if(TextUtils.isEmpty(itemId)){Toast.makeText(this,"Link Vinted non riconosciuto.",Toast.LENGTH_SHORT).show();return;}
        SharedPreferences p=getSharedPreferences(PREF_MANUAL_VINTED_SHARE,MODE_PRIVATE);
        long listingId=p.getLong("listing_id",0),jobId=p.getLong("job_id",0),at=p.getLong("at",0);
        String title=p.getString("title","");
        if(listingId<=0||at<=0||System.currentTimeMillis()-at>MANUAL_VINTED_SHARE_TTL){Toast.makeText(this,"Apri prima un caso da collegare e usa ‘Cerca su Vinted’.",Toast.LENGTH_LONG).show();return;}
        manualLinkIo.execute(()->{
            MarketListingRecord loaded=null;
            try{loaded=marketStore.listing(listingId);}catch(Throwable ignored){}
            final MarketListingRecord l=loaded;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                if(l==null){Toast.makeText(this,"Il caso da collegare non è più disponibile.",Toast.LENGTH_LONG).show();return;}
                String label=TextUtils.isEmpty(title)?l.title:title;
                new AlertDialog.Builder(this).setTitle("Collegare questo annuncio?").setMessage(label+"\n\n"+url)
                        .setNegativeButton("Annulla",null)
                        .setPositiveButton("Collega",(d,w)->applySharedVintedLink(jobId,listingId,url,itemId)).show();
            });
        });
    }

    private void applySharedVintedLink(long jobId,long listingId,String url,String itemId){
        Toast.makeText(this,"Salvo il collegamento…",Toast.LENGTH_SHORT).show();
        manualLinkIo.execute(()->{
            long canonical=0L;String sig="";DealRecord fresh=null;String error="";
            try{
                canonical=marketStore.applyManualVintedLink(jobId,listingId,url,itemId,null,null);
                if(canonical>0){
                    sig=marketStore.signatureForListing(canonical);
                    if(!TextUtils.isEmpty(sig)){
                        DealRecord legacy=db.findBySignature(sig);
                        if(legacy!=null)db.applyResolvedLink(sig,itemId,url,null,100,"Condiviso da Vinted",null,legacy.sellerName,null,System.currentTimeMillis());
                        fresh=db.findBySignature(sig);
                    }
                }else error="Non riesco a salvare il collegamento.";
            }catch(Throwable t){error="Il collegamento non è stato salvato. Riprova.";}
            final boolean ok=canonical>0;final String finalSig=sig;final DealRecord finalFresh=fresh;final String finalError=error;
            if(ok)getSharedPreferences(PREF_MANUAL_VINTED_SHARE,MODE_PRIVATE).edit().clear().apply();
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                if(!ok){Toast.makeText(this,finalError,Toast.LENGTH_SHORT).show();return;}
                if(activeResolutionDialog!=null&&activeResolutionDialog.isShowing()){suppressResolutionDismissState=true;activeResolutionDialog.dismiss();suppressResolutionDismissState=false;}
                activeVintedResolutionListingId=0;
                Toast.makeText(this,"Annuncio Vinted collegato",Toast.LENGTH_SHORT).show();
                kickActivityQueue();refreshMarketReferencesAsync();scheduleRender(80);
                if(!TextUtils.isEmpty(finalSig)&&finalFresh!=null&&selectedGameId==0&&(activeDetailDialog==null||!activeDetailDialog.isShowing()))
                    uiUpdates.postDelayed(()->openDetail(finalFresh),160);
            });
        });
    }

    private String vintedItemId(String url){if(TextUtils.isEmpty(url))return"";Matcher m=Pattern.compile("https?://(?:www\\.)?vinted\\.[^/]+/items/([0-9]+)(?:-[^\\s?#]*)?(?:[?#].*)?",Pattern.CASE_INSENSITIVE).matcher(url.trim());return m.matches()?m.group(1):"";}

    private void applyManualVintedChoice(MarketStore.Job job,String itemId,String url,String image,String seller,boolean sold,Dialog dialog){
        if(job==null)return;
        if(dialog!=null&&dialog.isShowing())dialog.dismiss();
        Toast.makeText(this,"Salvo il collegamento…",Toast.LENGTH_SHORT).show();
        manualLinkIo.execute(()->{
            long canonical=0L;String error="";
            try{
                canonical=marketStore.applyManualVintedLink(job.id,job.listingId,url,itemId,seller,image);
                if(canonical>0){
                    String sig=marketStore.signatureForListing(canonical);
                    if(!TextUtils.isEmpty(sig)){
                        DealRecord legacy=db.findBySignature(sig);
                        if(legacy!=null){
                            db.applyResolvedLink(sig,itemId,url,image,100,"Confermato manualmente",null,seller,null,System.currentTimeMillis());
                            if(sold)db.markSold(sig);
                        }
                    }
                    if(sold)marketStore.markSold(canonical);
                }else error="Non riesco a salvare il collegamento";
            }catch(Throwable t){error="Il collegamento non è stato salvato. Riprova.";}
            final boolean ok=canonical>0;final String finalError=error;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                Toast.makeText(this,ok?"Collegamento salvato · Ludo completa la card":finalError,Toast.LENGTH_SHORT).show();
                if(ok){kickActivityQueue();scheduleRender(80);}
            });
        });
    }

    private void confirmDeleteReviewListing(MarketStore.Job job){if(job==null||job.listingId<=0)return;new AlertDialog.Builder(this).setTitle("Eliminare questo elemento?").setMessage("Uscirà dall’app e non influenzerà più prezzi o fact-check.").setNegativeButton("Annulla",null).setPositiveButton("Elimina",(d,w)->{String signature=marketStore.signatureForListing(job.listingId);DealRecord legacy=TextUtils.isEmpty(signature)?null:db.findBySignature(signature);if(legacy!=null){db.exclude(legacy,"Eliminato dalle attività");marketStore.setLegacyListingUserHidden(signature,true);bundleDb.invalidate(legacy);}else marketStore.archiveUnresolvedListing(job.listingId,"Eliminato dalle attività");refreshMarketReferencesAsync();scheduleRender(0);Toast.makeText(this,"Elemento eliminato dall'app",Toast.LENGTH_SHORT).show();}).show();}

    private void confirmArchiveUnresolved(MarketStore.Job job){if(job==null||job.listingId<=0)return;new AlertDialog.Builder(this).setTitle("Non è più su Vinted?").setMessage("L’annuncio uscirà dal catalogo attivo. Prezzi e storico restano salvati.").setNegativeButton("Annulla",null).setPositiveButton("Archivia",(d,w)->{String reason="Pagina Vinted non trovata dopo più tentativi";String signature=marketStore.archiveUnresolvedListing(job.listingId,reason);if(!TextUtils.isEmpty(signature))db.markUnavailable(signature,reason);sendBroadcast(new Intent("it.vintedaffari.app.DEALS_UPDATED").setPackage(getPackageName()));Toast.makeText(this,"Annuncio archiviato",Toast.LENGTH_SHORT).show();refreshMarketReferencesAsync();scheduleRender(80);}).show();}

    private void updateGlobalProgress(){updateActivityIndicator();}
    private HorizontalScrollView choiceChips(String[] labels,String[] keys,String current,java.util.function.Consumer<String> change){HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(this);for(int i=0;i<labels.length;i++){final int at=i;TextView chip=materialChip(labels[i],keys[i].equals(current)?Color.rgb(46,66,86):SURFACE2,keys[i].equals(current)?TEXT:MUTED,keys[i].equals(current));chip.setBackground(round(keys[i].equals(current)?Color.rgb(46,66,86):SURFACE2,999,1,keys[i].equals(current)?CYAN:OUTLINE));chip.setOnClickListener(v->{change.accept(keys[at]);for(int c=0;c<row.getChildCount();c++){TextView x=(TextView)row.getChildAt(c);boolean on=c==at;x.setTextColor(on?TEXT:MUTED);x.setBackground(round(on?Color.rgb(46,66,86):SURFACE2,999,1,on?CYAN:OUTLINE));}});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(38));if(i>0)lp.leftMargin=dp(8);row.addView(chip,lp);}scroll.addView(row);return scroll;}
    private CheckBox filterCheck(String label,boolean checked){CheckBox c=new CheckBox(this);c.setText(label);c.setTextColor(TEXT);c.setTextSize(15);c.setButtonTintList(android.content.res.ColorStateList.valueOf(CYAN));c.setChecked(checked);c.setMinHeight(dp(48));return c;}
    private void addBundleDiagnostics(LinearLayout host){host.addView(kv("Seller trovati",String.valueOf(bundleDb.counter("sellerFound"))));host.addView(kv("Seller unici",String.valueOf(bundleDb.uniqueSellerCount())));host.addView(kv("Snapshot analizzati",String.valueOf(bundleDb.counter("snapshotAnalyzed"))));host.addView(kv("Deep scan eseguiti",String.valueOf(bundleDb.counter("deepScanExecuted"))));host.addView(kv("Deep scan evitati",String.valueOf(bundleDb.counter("deepScanAvoided"))));host.addView(kv("Bundle candidati",String.valueOf(bundleDb.counter("bundleCandidates"))));Map<String,Integer> states=bundleDb.statusCounts();host.addView(kv("Bundle confermati",String.valueOf(states.containsKey("BUNDLE_READY")?states.get("BUNDLE_READY"):0)));host.addView(kv("Errori",String.valueOf(bundleDb.counter("errors"))));host.addView(kv("Rate limit / 429",String.valueOf(bundleDb.counter("rateLimited"))));host.addView(kv("Cache hit snapshot",String.valueOf(bundleDb.counter("cacheHitSnapshot"))));host.addView(kv("Cache hit catalogo",String.valueOf(bundleDb.counter("cacheHitCatalog"))));}
    private void addOperationGroup(String title,List<OperationCenter.Task> tasks,String state,int limit){
        List<OperationCenter.Task> group=new ArrayList<>();for(OperationCenter.Task t:tasks){boolean durableMirror=t.id!=null&&(t.id.startsWith("maintenance:item:")||t.id.startsWith("market:vinted:")||t.id.startsWith("bgg-enrich:"));if(state.equals(t.state)&&!durableMirror&&!(OperationCenter.MAINTENANCE.equals(t.type)&&"maintenance:missing-data".equals(t.id)))group.add(t);}if(group.isEmpty())return;sectionHeader("",title,"",null,null);int shown=0;
        for(OperationCenter.Task t:group){if(shown++>=limit)break;if(OperationCenter.MAINTENANCE.equals(t.type)&&t.id!=null&&t.id.startsWith("maintenance:item:")){body.addView(maintenanceItemCard(t));continue;}
            LinearLayout row=verticalCard();row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(14),dp(12),dp(12),dp(12));int rowAccent=operationColor(t.state);int rowProgress=Math.max(t.progress,OperationCenter.RUNNING.equals(t.state)?30:OperationCenter.QUEUED.equals(t.state)?6:0);row.setBackground(queueCardBackground(SURFACE,rowAccent,rowProgress));TextView dot=text("●",12,rowAccent,Typeface.BOLD);row.addView(dot,new LinearLayout.LayoutParams(dp(26),-2));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);tx.addView(text(friendlyTaskTitle(t),15,TEXT,Typeface.BOLD));String meta=t.state+" · "+elapsed(t.createdAt)+(TextUtils.isEmpty(t.error)?"":"\n"+friendlyOperationError(t.error));tx.addView(text(meta,12,MUTED,Typeface.NORMAL));row.addView(tx,new LinearLayout.LayoutParams(0,-2,1));TextView originTag=materialChip(operationOrigin(t),operationOriginColor(t),TEXT,true);LinearLayout.LayoutParams otp=new LinearLayout.LayoutParams(-2,dp(28));otp.leftMargin=dp(6);row.addView(originTag,otp);if(OperationCenter.ERROR.equals(t.state)){LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.VERTICAL);if(canResolveTask(t)){Button fix=button("Risolvi",CYAN);fix.setOnClickListener(v->resolveTaskError(t));actions.addView(fix,new LinearLayout.LayoutParams(dp(88),dp(40)));}Button retry=button("Riprova",ORANGE);retry.setOnClickListener(v->OperationCenter.retry(this,t));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(88),dp(40));ap.topMargin=canResolveTask(t)?dp(5):0;actions.addView(retry,ap);Button remove=button("Elimina",SURFACE2);remove.setTextColor(MUTED);remove.setOnClickListener(v->{OperationCenter.remove(this,t.id);render();});LinearLayout.LayoutParams rp2=new LinearLayout.LayoutParams(dp(88),dp(38));rp2.topMargin=dp(5);actions.addView(remove,rp2);row.addView(actions);}LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=dp(8);body.addView(row,rp);
        }
    }
    private String operationOrigin(OperationCenter.Task t){if(t!=null&&t.id!=null&&(t.id.startsWith("library:")||t.id.startsWith("wizard:")||t.id.startsWith("photo:")))return "Libreria";if(t!=null&&OperationCenter.MATCH.equals(t.type))return "Libreria";return "Vinted";}
    private int operationOriginColor(OperationCenter.Task t){return "Libreria".equals(operationOrigin(t))?PURPLE:VINTED_BG;}

    private View maintenanceItemCard(OperationCenter.Task t){
        String sig=t.id.substring("maintenance:item:".length());DealRecord d=db.findBySignature(sig);LinearLayout card=verticalCard();card.setPadding(dp(14),dp(12),dp(14),dp(12));int accent=operationColor(t.state);int visualProgress=Math.max(t.progress,OperationCenter.RUNNING.equals(t.state)?30:OperationCenter.QUEUED.equals(t.state)?6:0);card.setBackground(queueCardBackground(SURFACE,accent,visualProgress));LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);if(d!=null){View art=dealArtworkView(d,dp(56),dp(70));head.addView(art,new LinearLayout.LayoutParams(dp(56),dp(70)));}LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(d==null?0:dp(12),0,0,0);String title=d==null?maintenanceTitleFromDetail(t.detail):name(d);LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);titleRow.addView(text(title,16,TEXT,Typeface.BOLD),new LinearLayout.LayoutParams(0,-2,1));titleRow.addView(materialChip("Vinted",VINTED_BG,TEXT,true),new LinearLayout.LayoutParams(-2,dp(28)));copy.addView(titleRow);String state=OperationCenter.PAUSED.equals(t.state)?"In pausa":OperationCenter.RUNNING.equals(t.state)?"In corso":OperationCenter.QUEUED.equals(t.state)?"In coda":OperationCenter.ERROR.equals(t.state)?"Serve attenzione":"Completato";String progressLabel=(OperationCenter.QUEUED.equals(t.state)||t.progress<=0)?state:state+" · "+t.progress+"%";copy.addView(text(progressLabel,13,accent,Typeface.BOLD));String stage=maintenanceStageFromDetail(t.detail,title);if(!TextUtils.isEmpty(stage))copy.addView(text(stage,12,MUTED,Typeface.NORMAL));if(t.retryAt>System.currentTimeMillis())copy.addView(retryCountdownView("Riprendo ",t.retryAt));head.addView(copy,new LinearLayout.LayoutParams(0,-2,1));card.addView(head);
        if(OperationCenter.ERROR.equals(t.state)){TextView err=text(TextUtils.isEmpty(t.error)?"Il controllo non si è concluso.":friendlyOperationError(t.error),12,ORANGE,Typeface.NORMAL);err.setPadding(0,dp(8),0,0);card.addView(err);Button retry=button("Riprova questa card",ORANGE);retry.setOnClickListener(v->{if(d!=null)requestDealRefresh(d);else OperationCenter.remove(this,t.id);});LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(44));rp.topMargin=dp(8);card.addView(retry,rp);}else if(d!=null&&!OperationCenter.DONE.equals(t.state))card.setOnClickListener(v->openDetail(d));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(8);card.setLayoutParams(lp);return card;
    }
    private String maintenanceTitleFromDetail(String detail){if(TextUtils.isEmpty(detail))return"Annuncio";int i=detail.indexOf(" · ");return i>0?detail.substring(0,i):detail;}
    private String maintenanceStageFromDetail(String detail,String title){if(TextUtils.isEmpty(detail))return"";String prefix=title+" · ";return detail.startsWith(prefix)?detail.substring(prefix.length()):detail;}
    private String retryCountdown(long retryAt){long sec=Math.max(0,(retryAt-System.currentTimeMillis()+999)/1000);return sec>=60?"tra "+(sec/60)+"m "+(sec%60)+"s":"tra "+sec+"s";}
    private TextView retryCountdownView(String prefix,long retryAt){TextView v=text(prefix+retryCountdown(retryAt),12,YELLOW,Typeface.BOLD);Runnable tick=new Runnable(){public void run(){if(!v.isAttachedToWindow())return;if(retryAt<=System.currentTimeMillis()){v.setText(prefix+"ora");return;}v.setText(prefix+retryCountdown(retryAt));v.postDelayed(this,1000L);}};v.postDelayed(tick,1000L);return v;}

    private String friendlyTaskTitle(OperationCenter.Task t){if(t==null)return"Attività";String d=TextUtils.isEmpty(t.detail)?"":t.detail.replace("Ricerca BGG · ","").replace("BGG #","");if(OperationCenter.MATCH.equals(t.type))return d.toLowerCase(Locale.ROOT).contains("foto")?"Riconoscimento gioco · foto":"Ricerca gioco"+(TextUtils.isEmpty(d)?"":" · "+d);if(OperationCenter.LINK.equals(t.type))return"Controllo annuncio"+(TextUtils.isEmpty(d)?"":" · "+d);if(OperationCenter.COVER.equals(t.type))return"Immagine del gioco"+(TextUtils.isEmpty(d)?"":" · "+d);if(OperationCenter.MAINTENANCE.equals(t.type))return t.id!=null&&t.id.startsWith("maintenance:item:")?maintenanceTitleFromDetail(d):"Aggiornamento dati";return TextUtils.isEmpty(d)?"Attività":d;}
    private boolean canResolveTask(OperationCenter.Task t){return t!=null&&(OperationCenter.MATCH.equals(t.type)||OperationCenter.LINK.equals(t.type)||OperationCenter.SELLER.equals(t.type));}
    private void resolveTaskError(OperationCenter.Task t){if(t==null)return;String detail=t.detail==null?"":t.detail;int cut=detail.indexOf(" · ");if(cut>0)detail=detail.substring(0,cut);DealRecord d=db.findByVintedTitle(detail);if(d==null){Toast.makeText(this,"Apri il gioco dal Catalogo e usa ? per correggerlo manualmente.",Toast.LENGTH_LONG).show();return;}if(OperationCenter.MATCH.equals(t.type))searchCorrection(d,null);else correctVintedLink(d,null);}
    private String elapsed(long since){long s=Math.max(0,(System.currentTimeMillis()-since)/1000);if(s<60)return s+" s";long m=s/60;if(m<60)return m+" min";long h=m/60;return h<24?h+" h":h/24+" g";}
    private void addMaintenanceOverview(List<OperationCenter.Task> tasks){
        OperationCenter.Task master=null;for(OperationCenter.Task t:tasks)if(OperationCenter.MAINTENANCE.equals(t.type)&&"maintenance:missing-data".equals(t.id)&&(OperationCenter.RUNNING.equals(t.state)||OperationCenter.QUEUED.equals(t.state)||OperationCenter.PAUSED.equals(t.state))){master=t;break;}if(master==null)return;
        LinearLayout card=verticalCard();card.setPadding(dp(16),dp(14),dp(16),dp(14));int accent=OperationCenter.PAUSED.equals(master.state)?YELLOW:CYAN;card.setBackground(round(SURFACE2,18,1,accent));LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.addView(text(OperationCenter.PAUSED.equals(master.state)?"Coda in pausa":"Aggiornamento catalogo",18,TEXT,Typeface.BOLD),new LinearLayout.LayoutParams(0,-2,1));top.addView(text(master.progress+"%",15,accent,Typeface.BOLD));card.addView(top);TextView detail=text(TextUtils.isEmpty(master.detail)?"Preparo la coda.":master.detail,13,OperationCenter.PAUSED.equals(master.state)?ORANGE:MUTED,Typeface.NORMAL);detail.setPadding(0,dp(4),0,0);card.addView(detail);
        ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(false);progress.setMax(100);progress.setProgress(master.progress);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(OUTLINE));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(10));pp.topMargin=dp(12);card.addView(progress,pp);
        if(master.retryAt>System.currentTimeMillis()){TextView retry=retryCountdownView("Riprendo automaticamente ",master.retryAt);retry.setPadding(0,dp(8),0,0);card.addView(retry);}LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);body.addView(card,lp);
    }
    private View wizardOption(int iconRes,String title,String subtitle,int accent,Runnable action){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(16),dp(14),dp(16),dp(14));row.setBackground(round(SURFACE2,20,1,OUTLINE));ImageView icon=new ImageView(this);icon.setImageResource(iconRes);icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);if(iconRes!=R.drawable.provider_vinted_logo)icon.setColorFilter(accent);icon.setPadding(dp(iconRes==R.drawable.provider_vinted_logo?4:6),dp(iconRes==R.drawable.provider_vinted_logo?4:6),dp(iconRes==R.drawable.provider_vinted_logo?4:6),dp(iconRes==R.drawable.provider_vinted_logo?4:6));row.addView(icon,new LinearLayout.LayoutParams(dp(iconRes==R.drawable.provider_vinted_logo?54:50),dp(iconRes==R.drawable.provider_vinted_logo?54:50)));LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(14),0,dp(8),0);copy.addView(text(title,16,TEXT,Typeface.BOLD));copy.addView(text(subtitle,12,MUTED,Typeface.NORMAL));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=appIcon(LudoIcons.CHEVRON_RIGHT,16,MUTED);arrow.setGravity(Gravity.CENTER);row.addView(arrow,new LinearLayout.LayoutParams(dp(32),dp(44)));row.setOnClickListener(v->{try{action.run();}catch(Throwable e){OperationCenter.error(this,"wizard:"+System.currentTimeMillis(),OperationCenter.MATCH,title,String.valueOf(e.getMessage()));Toast.makeText(this,"Qualcosa è andato storto. Riprova fra un attimo.",Toast.LENGTH_LONG).show();}});return row;}
    private View wizardOption(String glyph,String title,String subtitle,int accent,Runnable action){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(16),dp(14),dp(16),dp(14));row.setBackground(round(SURFACE2,20,1,OUTLINE));
        row.addView(appIcon(glyph,19,accent),new LinearLayout.LayoutParams(dp(50),dp(50)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(14),0,dp(8),0);copy.addView(text(title,16,TEXT,Typeface.BOLD));copy.addView(text(subtitle,12,MUTED,Typeface.NORMAL));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=appIcon(LudoIcons.CHEVRON_RIGHT,16,MUTED);row.addView(arrow,new LinearLayout.LayoutParams(dp(32),dp(44)));
        row.setOnClickListener(v->{try{action.run();}catch(Throwable e){OperationCenter.error(this,"wizard:"+System.currentTimeMillis(),OperationCenter.MATCH,title,String.valueOf(e.getMessage()));Toast.makeText(this,"Qualcosa è andato storto. Riprova fra un attimo.",Toast.LENGTH_LONG).show();}});return row;
    }
    private View gamePreviewCompact(BggSearchClient.Game g){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(6),0,dp(4));ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.FIT_CENTER);cover.setBackground(round(SURFACE2,12,0,0));if(g!=null){File f=TextUtils.isEmpty(g.id)?null:ArtworkStore.bggFile(this,g.id);if(f!=null&&f.exists())cover.setImageBitmap(decodeLocalBitmap(f,300,420));else if(!TextUtils.isEmpty(g.imageUrl))loadRemote(cover,g.imageUrl);}row.addView(cover,new LinearLayout.LayoutParams(dp(64),dp(82)));LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(12),0,0,0);copy.addView(text(g==null?"Gioco":g.name,18,TEXT,Typeface.BOLD));if(g!=null&&g.rating!=null)copy.addView(text("BGG "+String.format(Locale.ITALY,"%.1f",g.rating),12,MUTED,Typeface.NORMAL));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));return row;}
    private void saveLibrary(BggSearchClient.Game g,Integer paid,String source,Integer shipping,Integer fee,boolean estimated){if(!safeLibraryAdd(g,paid,source,shipping,null,fee,estimated))return;if(!TextUtils.isEmpty(g.imageUrl)&&!TextUtils.isEmpty(g.id))ArtworkStore.downloadBgg(this,g.id,g.imageUrl);finishActiveLibrarySearchJob();completeLibraryWizardSession();clearLibraryWizardState();scheduleRender(0);Toast.makeText(this,"Aggiunto alla Libreria",Toast.LENGTH_SHORT).show();}
    private void openWizardStep(Dialog current,Runnable next){if(current!=null&&current.isShowing())current.dismiss();uiUpdates.postDelayed(()->{try{next.run();}catch(Throwable e){String message=e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());getSharedPreferences("va_v3_diag",MODE_PRIVATE).edit().putString("libraryLastError",message).apply();OperationCenter.error(this,"wizard:"+System.currentTimeMillis(),OperationCenter.MATCH,"wizard acquisto",message);Toast.makeText(this,"Il wizard ha avuto un problema. Ho salvato il dettaglio in Attività.",Toast.LENGTH_LONG).show();}},180);}
    private boolean safeLibraryAdd(BggSearchClient.Game g,Integer paid,String source,Integer shipping,Long acquired,Integer fee,boolean estimated){try{libraryDb.add(g,paid,source,shipping,acquired,fee,estimated);if(g!=null&&!TextUtils.isEmpty(g.id)&&huntDb!=null)huntDb.removeByBggId(g.id);getSharedPreferences("va_v3_diag",MODE_PRIVATE).edit().putString("libraryLastError","").apply();if(g!=null&&!TextUtils.isEmpty(g.name))OperationCenter.resolveForDeal(this,"",g.name);return true;}catch(Throwable e){String message=e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());getSharedPreferences("va_v3_diag",MODE_PRIVATE).edit().putString("libraryLastError",message).apply();OperationCenter.error(this,"library:"+System.currentTimeMillis(),OperationCenter.MATCH,g==null?"Libreria":String.valueOf(g.name),"Salvataggio Libreria: "+message);Toast.makeText(this,"Non sono riuscito ad aggiungere il gioco. Apri Attività per il dettaglio.",Toast.LENGTH_LONG).show();return false;}}

    private Dialog wizardSheet(String title){Dialog d=bottomSheet(title);d.setCanceledOnTouchOutside(false);return d;}
    private BggSearchClient.Game copyGame(BggSearchClient.Game g){if(g==null)return null;BggSearchClient.Game x=new BggSearchClient.Game();x.id=g.id;x.name=g.name;x.imageUrl=g.imageUrl;x.type=g.type;x.year=g.year;x.playtime=g.playtime;x.minPlayers=g.minPlayers;x.maxPlayers=g.maxPlayers;x.rank=g.rank;x.voters=g.voters;x.qualityScore=g.qualityScore;x.rating=g.rating;x.geekRating=g.geekRating;x.weight=g.weight;x.categories=g.categories;x.editionId=g.editionId;x.editionName=g.editionName;x.local=g.local;return x;}
    private BggSearchClient.Game gameFromDeal(DealRecord d){if(d==null)return null;BggSearchClient.Game g=new BggSearchClient.Game();g.id=d.bggId;g.name=name(d);g.imageUrl=!TextUtils.isEmpty(d.bggImageUrl)?d.bggImageUrl:d.imageUrl;g.rating=d.rating;g.rank=d.rank;g.voters=d.voters;g.qualityScore=d.qualityScore;g.minPlayers=d.minPlayers;g.maxPlayers=d.maxPlayers;g.playtime=d.playtime;g.weight=d.weight;g.categories=d.bggCategories;g.type=d.listingType!=null&&d.listingType.toLowerCase(Locale.ROOT).contains("expan")?"boardgameexpansion":"boardgame";return g;}
    private void addWizardBack(LinearLayout box,String label,Runnable action){TextView back=text("‹  "+label,14,CYAN,Typeface.BOLD);back.setGravity(Gravity.CENTER_VERTICAL);back.setMinHeight(dp(44));back.setOnClickListener(v->action.run());box.addView(back,new LinearLayout.LayoutParams(-1,dp(44)));}
    private void librarySearchHelp(LibrarySearchJob job){Dialog d=wizardSheet("Aiutami a trovare il gioco");LinearLayout box=d.findViewById(SHEET_ID);box.addView(text("La foto resta in coda. Correggi il titolo oppure incolla il link della scheda BGG.",14,MUTED,Typeface.NORMAL));EditText q=input("Nome del gioco");q.setText(job.label);LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(-1,dp(56));qp.topMargin=dp(14);box.addView(q,qp);EditText link=input("Link BGG oppure ID BGG");LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(56));lp.topMargin=dp(10);box.addView(link,lp);Button go=button("Cerca e collega",CYAN);go.setOnClickListener(v->{String raw=link.getText().toString().trim();String id=raw.replaceAll(".*boardgame/([0-9]+).*","$1");if(id.matches("[0-9]+")){d.dismiss();OperationCenter.running(this,job.id,OperationCenter.MATCH,"Recupero la scheda BGG");bggSearch.details(id,false,new BggSearchClient.Callback(){public void ok(List<BggSearchClient.Game> games){runOnUiThread(()->{job.running=false;job.error="";job.results.clear();job.results.addAll(games);job.label=games.isEmpty()?q.getText().toString().trim():games.get(0).name;OperationCenter.done(MainActivity.this,job.id,OperationCenter.MATCH,"Gioco trovato · controlla il risultato");render();});}public void error(String e){runOnUiThread(()->{job.error="Non riesco ad aprire questa scheda BGG";render();});}});return;}String name=q.getText().toString().trim();if(name.isEmpty()){q.setError("Scrivi il nome oppure incolla un link BGG");return;}d.dismiss();job.running=true;job.error="";job.label=name;render();bggSearch.searchFast(name,new BggSearchClient.Callback(){public void ok(List<BggSearchClient.Game> games){runOnUiThread(()->{job.running=false;job.results.clear();if(games!=null)job.results.addAll(games);if(job.results.isEmpty())job.error="Nessun gioco abbastanza convincente";render();});}public void error(String e){runOnUiThread(()->{job.running=false;job.error="Ricerca non riuscita";render();});}});});LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,dp(54));gp.topMargin=dp(14);box.addView(go,gp);d.show();}
    private void finishActiveLibrarySearchJob(){if(TextUtils.isEmpty(activeLibrarySearchJobId))return;for(LibrarySearchJob j:new ArrayList<>(librarySearchJobs))if(activeLibrarySearchJobId.equals(j.id)){librarySearchJobs.remove(j);break;}activeLibrarySearchJobId="";}
    private SharedPreferences wizardPrefs(){return getSharedPreferences("ludo_library_wizard",MODE_PRIVATE);}
    private void ensureLibraryWizardSession(){if(restoringLibraryWizard&&!TextUtils.isEmpty(wizardSessionId))return;if(!TextUtils.isEmpty(wizardSessionId)&&wizardSessionIsLive(wizardSessionId))return;wizardSessionId="wiz:"+System.currentTimeMillis()+":"+Math.abs(new Random().nextInt());wizardPrefs().edit().putString("session:"+wizardSessionId,"in_progress").putLong("started:"+wizardSessionId,System.currentTimeMillis()).commit();}
    private boolean wizardSessionIsLive(String id){return !TextUtils.isEmpty(id)&&"in_progress".equals(wizardPrefs().getString("session:"+id,""));}
    private void completeLibraryWizardSession(){if(TextUtils.isEmpty(wizardSessionId))return;wizardPrefs().edit().putString("session:"+wizardSessionId,"completed").putLong("completed:"+wizardSessionId,System.currentTimeMillis()).commit();recordAction("wizard:completed:"+wizardSessionId);}

    private void clearLibraryWizardState(){libraryWizardStep="";libraryWizardSource="";libraryWizardQuery="";wizardPaidText="";wizardShippingText="";wizardFeeText="";wizardDateText="";libraryWizardGame=null;libraryWizardDialog=null;wizardBundleGames.clear();restoringLibraryWizard=false;wizardSessionId="";}
    private void restoreLibraryWizard(){if(TextUtils.isEmpty(libraryWizardStep)||TextUtils.isEmpty(wizardSessionId)||!wizardSessionIsLive(wizardSessionId)||(libraryWizardDialog!=null&&libraryWizardDialog.isShowing())){if(!TextUtils.isEmpty(libraryWizardStep)&&!wizardSessionIsLive(wizardSessionId))clearLibraryWizardState();return;}restoringLibraryWizard=true;String step=libraryWizardStep;BggSearchClient.Game g=copyGame(libraryWizardGame);String source=libraryWizardSource;String q=libraryWizardQuery;uiUpdates.postDelayed(()->{try{if("search".equals(step))addLibraryGame(q);else if("edition".equals(step)&&g!=null)chooseEdition(g,this::libraryPurchaseDialog);else if("source".equals(step)&&g!=null)purchaseSource(g);else if("online".equals(step)&&g!=null)purchaseOnline(g);else if("amounts".equals(step)&&g!=null)purchaseAmounts(g,source);else clearLibraryWizardState();}finally{restoringLibraryWizard=false;}},80);}
    private void restoreUiState(Bundle b){if(b==null)return;if(b.containsKey("cameraPhoto"))cameraPhoto=Uri.parse(b.getString("cameraPhoto"));tab=b.getString("tab",tab);sort=b.getString("sort",sort);query=b.getString("query",query);catalogPreset=b.getString("catalogPreset",catalogPreset);filterMode=b.getString("filterMode",filterMode);catalogTextDependence=b.getString("catalogTextDependence","all");catalogCategory=b.getInt("catalogCategory",-1);catalogMinDiscountFilter=b.containsKey("catalogMinDiscount")?b.getInt("catalogMinDiscount"):null;languageFilter=b.getString("languageFilter",languageFilter);linkFilter=b.getString("linkFilter",linkFilter);typeFilter=b.getString("typeFilter",typeFilter);bundleSort=b.getString("bundleSort",bundleSort);filterShipping=b.getBoolean("filterShipping",filterShipping);filterBundle=b.getBoolean("filterBundle",filterBundle);filterVerify=b.getBoolean("filterVerify",filterVerify);if(b.containsKey("maxPriceFilter"))maxPriceFilter=b.getInt("maxPriceFilter");if(b.containsKey("catalogMinRatingFilter"))catalogMinRatingFilter=b.getDouble("catalogMinRatingFilter");databaseQuery=b.getString("databaseQuery",databaseQuery);databaseScope=b.getString("databaseScope",databaseScope);databaseCategory=b.getInt("databaseCategory",-1);int legacyCategory=DiscoverCategories.index(databaseQuery);if(legacyCategory>=0){databaseCategory=legacyCategory;databaseQuery="";}databaseSort=b.getString("databaseSort",databaseSort);selectedGameId=b.getLong("selectedGameId",selectedGameId);databaseDetailReturnTab=b.getString("databaseDetailReturnTab",databaseDetailReturnTab);activeDealSignature=b.getString("activeDealSignature",activeDealSignature);activeVintedResolutionListingId=b.getLong("activeVintedResolutionListingId",activeVintedResolutionListingId);for(String t:new String[]{"discover","catalog","bundles","database","library","companion","activity"})if(b.containsKey("scroll_"+t))tabScrollPositions.put(t,b.getInt("scroll_"+t));databaseActiveOnly=b.getBoolean("databaseActiveOnly",databaseActiveOnly);if(b.containsKey("databaseMinRating"))databaseMinRating=b.getDouble("databaseMinRating");if(b.containsKey("databaseMaxPrice"))databaseMaxPrice=b.getInt("databaseMaxPrice");restoredScrollY=b.getInt("scrollY",0);String savedSession=b.getString("wizardSessionId","");if(TextUtils.isEmpty(savedSession)||!wizardSessionIsLive(savedSession))return;wizardSessionId=savedSession;libraryWizardStep=b.getString("libraryWizardStep","");libraryWizardSource=b.getString("libraryWizardSource","");libraryWizardQuery=b.getString("libraryWizardQuery","");wizardPaidText=b.getString("wizardPaidText","");wizardShippingText=b.getString("wizardShippingText","");wizardFeeText=b.getString("wizardFeeText","");wizardDateText=b.getString("wizardDateText","");if(!TextUtils.isEmpty(b.getString("wizardGameId",""))){BggSearchClient.Game g=new BggSearchClient.Game();g.id=b.getString("wizardGameId","");g.name=b.getString("wizardGameName","");g.imageUrl=b.getString("wizardGameImage","");g.type=b.getString("wizardGameType","");if(b.containsKey("wizardGameRating"))g.rating=b.getDouble("wizardGameRating");if(b.containsKey("wizardGameYear"))g.year=b.getInt("wizardGameYear");g.editionId=b.getString("wizardEditionId",null);g.editionName=b.getString("wizardEditionName",null);libraryWizardGame=g;}}
    private void persistTransientUiSession(){
        try{SharedPreferences.Editor e=getSharedPreferences(PREF_UI_SESSION,MODE_PRIVATE).edit();e.putLong("at",System.currentTimeMillis()).putString("tab",tab==null?"discover":tab).putLong("selectedGameId",selectedGameId).putString("databaseDetailReturnTab",databaseDetailReturnTab==null?"":databaseDetailReturnTab).putString("activeDealSignature",activeDealSignature==null?"":activeDealSignature).putLong("activeVintedResolutionListingId",activeVintedResolutionListingId).putString("sort",sort==null?"relevance":sort).putString("query",query==null?"":query).putString("catalogPreset",catalogPreset==null?"all":catalogPreset).putString("filterMode",filterMode==null?"all":filterMode).putString("catalogTextDependence",catalogTextDependence).putInt("catalogCategory",catalogCategory).putInt("catalogMinDiscount",catalogMinDiscountFilter==null?-1:catalogMinDiscountFilter).putString("languageFilter",languageFilter==null?"all":languageFilter).putString("linkFilter",linkFilter==null?"all":linkFilter).putString("typeFilter",typeFilter==null?"all":typeFilter).putBoolean("filterShipping",filterShipping).putBoolean("filterBundle",filterBundle).putBoolean("filterVerify",filterVerify).putString("databaseQuery",databaseQuery==null?"":databaseQuery).putInt("databaseCategory",databaseCategory).putString("databaseScope",databaseScope==null?"verified":databaseScope).putString("databaseSort",databaseSort==null?"alpha":databaseSort).putBoolean("databaseActiveOnly",databaseActiveOnly);
        if(maxPriceFilter!=null)e.putInt("maxPriceFilter",maxPriceFilter).putBoolean("hasMaxPrice",true);else e.putBoolean("hasMaxPrice",false);if(catalogMinRatingFilter!=null)e.putLong("catalogMinRatingBits",Double.doubleToRawLongBits(catalogMinRatingFilter)).putBoolean("hasCatalogMinRating",true);else e.putBoolean("hasCatalogMinRating",false);if(databaseMinRating!=null)e.putLong("databaseMinRatingBits",Double.doubleToRawLongBits(databaseMinRating)).putBoolean("hasDatabaseMinRating",true);else e.putBoolean("hasDatabaseMinRating",false);if(databaseMaxPrice!=null)e.putInt("databaseMaxPrice",databaseMaxPrice).putBoolean("hasDatabaseMaxPrice",true);else e.putBoolean("hasDatabaseMaxPrice",false);
        for(String t:new String[]{"discover","catalog","bundles","database","library","companion","activity"}){int y=tabScrollPositions.getOrDefault(t,t.equals(tab)&&scroll!=null?scroll.getScrollY():0);e.putInt("scroll_"+t,y);}e.apply();}catch(Throwable ignored){}
    }
    private void restoreTransientUiSession(){
        try{SharedPreferences p=getSharedPreferences(PREF_UI_SESSION,MODE_PRIVATE);long at=p.getLong("at",0L);if(at<=0||System.currentTimeMillis()-at>UI_SESSION_TTL){p.edit().clear().apply();return;}tab=p.getString("tab",tab);selectedGameId=p.getLong("selectedGameId",0L);databaseDetailReturnTab=p.getString("databaseDetailReturnTab","");activeDealSignature=p.getString("activeDealSignature","");activeVintedResolutionListingId=p.getLong("activeVintedResolutionListingId",0L);sort=p.getString("sort",sort);query=p.getString("query",query);catalogPreset=p.getString("catalogPreset",catalogPreset);filterMode=p.getString("filterMode",filterMode);catalogTextDependence=p.getString("catalogTextDependence","all");catalogCategory=p.getInt("catalogCategory",-1);catalogMinDiscountFilter=p.getInt("catalogMinDiscount",-1)<0?null:p.getInt("catalogMinDiscount",0);languageFilter=p.getString("languageFilter",languageFilter);linkFilter=p.getString("linkFilter",linkFilter);typeFilter=p.getString("typeFilter",typeFilter);filterShipping=p.getBoolean("filterShipping",filterShipping);filterBundle=p.getBoolean("filterBundle",filterBundle);filterVerify=p.getBoolean("filterVerify",filterVerify);databaseQuery=p.getString("databaseQuery",databaseQuery);databaseScope=p.getString("databaseScope",databaseScope);databaseCategory=p.getInt("databaseCategory",-1);int legacyCategory=DiscoverCategories.index(databaseQuery);if(legacyCategory>=0){databaseCategory=legacyCategory;databaseQuery="";}databaseSort=p.getString("databaseSort",databaseSort);databaseActiveOnly=p.getBoolean("databaseActiveOnly",databaseActiveOnly);maxPriceFilter=p.getBoolean("hasMaxPrice",false)?p.getInt("maxPriceFilter",0):null;catalogMinRatingFilter=p.getBoolean("hasCatalogMinRating",false)?Double.longBitsToDouble(p.getLong("catalogMinRatingBits",0L)):null;databaseMinRating=p.getBoolean("hasDatabaseMinRating",false)?Double.longBitsToDouble(p.getLong("databaseMinRatingBits",0L)):null;databaseMaxPrice=p.getBoolean("hasDatabaseMaxPrice",false)?p.getInt("databaseMaxPrice",0):null;for(String t:new String[]{"discover","catalog","bundles","database","library","companion","activity"})tabScrollPositions.put(t,p.getInt("scroll_"+t,0));restoredScrollY=tabScrollPositions.getOrDefault(tab,0);}
        catch(Throwable ignored){}
    }
    private void restoreTransientRoute(){if(isDestroyed())return;if(!TextUtils.isEmpty(activeDealSignature)&&(activeDetailDialog==null||!activeDetailDialog.isShowing())){DealRecord d=db.findBySignature(activeDealSignature);if(d!=null)openDetail(d);}if(activeVintedResolutionListingId>0&&(activeResolutionDialog==null||!activeResolutionDialog.isShowing())){MarketStore.Job j=marketStore.latestJobForListing(activeVintedResolutionListingId);if(j!=null)openVintedResolution(j);}}

    private void saveUiState(Bundle state){if(state==null)return;if(cameraPhoto!=null)state.putString("cameraPhoto",cameraPhoto.toString());state.putString("tab",tab);state.putString("sort",sort);state.putString("query",query);state.putString("catalogPreset",catalogPreset);state.putString("filterMode",filterMode);state.putString("catalogTextDependence",catalogTextDependence);state.putInt("catalogCategory",catalogCategory);if(catalogMinDiscountFilter!=null)state.putInt("catalogMinDiscount",catalogMinDiscountFilter);state.putString("languageFilter",languageFilter);state.putString("linkFilter",linkFilter);state.putString("typeFilter",typeFilter);state.putString("bundleSort",bundleSort);state.putBoolean("filterShipping",filterShipping);state.putBoolean("filterBundle",filterBundle);state.putBoolean("filterVerify",filterVerify);if(maxPriceFilter!=null)state.putInt("maxPriceFilter",maxPriceFilter);if(catalogMinRatingFilter!=null)state.putDouble("catalogMinRatingFilter",catalogMinRatingFilter);state.putString("databaseQuery",databaseQuery);state.putString("databaseScope",databaseScope);state.putInt("databaseCategory",databaseCategory);state.putString("databaseSort",databaseSort);state.putLong("selectedGameId",selectedGameId);state.putString("databaseDetailReturnTab",databaseDetailReturnTab);state.putString("activeDealSignature",activeDealSignature);state.putLong("activeVintedResolutionListingId",activeVintedResolutionListingId);for(String t:new String[]{"discover","catalog","bundles","database","library","companion","activity"})state.putInt("scroll_"+t,tabScrollPositions.getOrDefault(t,t.equals(tab)&&scroll!=null?scroll.getScrollY():0));state.putBoolean("databaseActiveOnly",databaseActiveOnly);if(databaseMinRating!=null)state.putDouble("databaseMinRating",databaseMinRating);if(databaseMaxPrice!=null)state.putInt("databaseMaxPrice",databaseMaxPrice);if(scroll!=null)state.putInt("scrollY",scroll.getScrollY());if(!TextUtils.isEmpty(wizardSessionId)&&wizardSessionIsLive(wizardSessionId)){state.putString("wizardSessionId",wizardSessionId);state.putString("libraryWizardStep",libraryWizardStep);state.putString("libraryWizardSource",libraryWizardSource);state.putString("libraryWizardQuery",libraryWizardQuery);state.putString("wizardPaidText",wizardPaidText);state.putString("wizardShippingText",wizardShippingText);state.putString("wizardFeeText",wizardFeeText);state.putString("wizardDateText",wizardDateText);if(libraryWizardGame!=null){state.putString("wizardGameId",libraryWizardGame.id);state.putString("wizardGameName",libraryWizardGame.name);state.putString("wizardGameImage",libraryWizardGame.imageUrl);state.putString("wizardGameType",libraryWizardGame.type);if(libraryWizardGame.rating!=null)state.putDouble("wizardGameRating",libraryWizardGame.rating);if(libraryWizardGame.year!=null)state.putInt("wizardGameYear",libraryWizardGame.year);state.putString("wizardEditionId",libraryWizardGame.editionId);state.putString("wizardEditionName",libraryWizardGame.editionName);}}}
    private void showSellerPairs(DealRecord source){openBundleDetail(source);}
    private Uri cameraPhoto;private Dialog photoProgress;
    private void photoSearchOptions(){Dialog dialog=bottomSheet("Cerca un gioco da una foto");LinearLayout box=dialog.findViewById(SHEET_ID);box.addView(text("Inquadra bene il titolo sulla scatola.",15,MUTED,Typeface.NORMAL));Button camera=button("Scatta una foto",CYAN);camera.setCompoundDrawables(iconDrawable(LudoIcons.CAMERA,BG,18),null,null,null);camera.setCompoundDrawablePadding(dp(8));camera.setOnClickListener(v->{try{File folder=new File(getCacheDir(),"recognition");folder.mkdirs();File file=new File(folder,"box-"+System.currentTimeMillis()+".jpg");cameraPhoto=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".photos",file);Intent intent=new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT,cameraPhoto);intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);dialog.dismiss();startActivityForResult(intent,5801);}catch(Exception e){Toast.makeText(this,"Fotocamera non disponibile. Puoi scegliere una foto.",Toast.LENGTH_LONG).show();}});box.addView(camera,new LinearLayout.LayoutParams(-1,dp(52)));Button choose=button("Scegli una foto",SURFACE2);choose.setTextColor(TEXT);choose.setCompoundDrawables(iconDrawable(LudoIcons.IMAGE,TEXT,18),null,null,null);choose.setCompoundDrawablePadding(dp(8));choose.setOnClickListener(v->{dialog.dismiss();Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.setType("image/*");pick.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(pick,5802);});box.addView(choose,new LinearLayout.LayoutParams(-1,dp(52)));dialog.show();}
    @Override protected void onSaveInstanceState(Bundle state){saveUiState(state);super.onSaveInstanceState(state);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK)return;Uri uri=request==5801?cameraPhoto:request==5802&&data!=null?data.getData():null;if(uri==null)return;final Uri jobUri=snapshotLibraryPhoto(uri);String id="photo-search:"+System.nanoTime();LibrarySearchJob job=new LibrarySearchJob(id,"Foto appena aggiunta",jobUri);librarySearchJobs.add(0,job);OperationCenter.queued(this,id,OperationCenter.MATCH,"Riconoscimento di un gioco dalla foto");tab="library";render();LIBRARY_SEARCH_NET.execute(()->{OperationCenter.running(this,id,OperationCenter.MATCH,"Sto riconoscendo il gioco dalla foto");processLibraryPhotoInBackground(jobUri,job);});}
    private Uri snapshotLibraryPhoto(Uri source){try{File folder=new File(getCacheDir(),"recognition/jobs");folder.mkdirs();File dst=new File(folder,"job-"+System.nanoTime()+".jpg");try(InputStream in=getContentResolver().openInputStream(source);FileOutputStream out=new FileOutputStream(dst)){byte[] b=new byte[32768];int n;while((n=in.read(b))>0)out.write(b,0,n);}return androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".photos",dst);}catch(Exception e){return source;}}
    private void processLibraryPhotoInBackground(Uri uri,LibrarySearchJob job){try{Bitmap bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(),uri),(decoder,info,source)->{int w=info.getSize().getWidth(),h=info.getSize().getHeight();float scale=Math.min(1f,1600f/Math.max(w,h));decoder.setTargetSize(Math.max(1,(int)(w*scale)),Math.max(1,(int)(h*scale)));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);});com.google.mlkit.vision.common.InputImage image=com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap,0);com.google.mlkit.vision.text.TextRecognizer reader=com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS);reader.process(image).addOnSuccessListener(resultText->{reader.close();List<String> titles=photoTitleCandidates(resultText);String q=titles.isEmpty()?"":cleanPhotoTitle(titles.get(0));if(q.length()<3){runOnUiThread(()->{job.running=false;job.error="Non ho riconosciuto abbastanza testo. Puoi cercarlo per titolo.";OperationCenter.error(MainActivity.this,job.id,OperationCenter.MATCH,"Gioco dalla foto",job.error);render();});return;}job.label=q;bggSearch.searchFast(q,new BggSearchClient.Callback(){public void ok(List<BggSearchClient.Game> games){runOnUiThread(()->{job.running=false;job.results.clear();if(games!=null)for(BggSearchClient.Game candidate:games)if(DealPolicy.ratingEligible(candidate.rating))job.results.add(candidate);if(job.results.isEmpty())job.error="Nessun gioco abbastanza convincente";if(TextUtils.isEmpty(job.error))OperationCenter.done(MainActivity.this,job.id,OperationCenter.MATCH,"Gioco riconosciuto · conferma il risultato");else OperationCenter.error(MainActivity.this,job.id,OperationCenter.MATCH,q,job.error);render();});}public void error(String e){runOnUiThread(()->{job.running=false;job.error="La ricerca non è riuscita. Puoi riprovare.";OperationCenter.error(MainActivity.this,job.id,OperationCenter.MATCH,q,job.error);render();});}});}).addOnFailureListener(e->{reader.close();runOnUiThread(()->{job.running=false;job.error="Non riesco a leggere bene questa foto.";OperationCenter.error(MainActivity.this,job.id,OperationCenter.MATCH,"Gioco dalla foto",job.error);render();});});}catch(Exception e){runOnUiThread(()->{job.running=false;job.error="Non riesco ad aprire questa foto.";OperationCenter.error(MainActivity.this,job.id,OperationCenter.MATCH,"Gioco dalla foto",job.error);render();});}}
    private void showPhotoMatchConfirmation(Bitmap bitmap,List<VisualCoverMatcher.Candidate> visual,List<String> candidates){
        List<String> titles=candidates==null?Collections.emptyList():candidates;if(!titles.isEmpty()){photoProgress=bottomSheet("Cerco il gioco su BGG…");LinearLayout pbox=photoProgress.findViewById(SHEET_ID);ProgressBar busy=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);busy.setIndeterminate(true);pbox.addView(busy);photoProgress.show();tryPhotoTitleCandidate(bitmap,visual,titles,0);return;}renderPhotoMatches(bitmap,fallbackVisualMatches(visual),"");
    }
    private void tryPhotoTitleCandidate(Bitmap bitmap,List<VisualCoverMatcher.Candidate> visual,List<String> titles,int index){if(index>=Math.min(3,titles.size())){runOnUiThread(()->{if(photoProgress!=null)photoProgress.dismiss();renderPhotoMatches(bitmap,fallbackVisualMatches(visual),titles.isEmpty()?"":titles.get(0));});return;}String query=cleanPhotoTitle(titles.get(index));if(query.length()<3){tryPhotoTitleCandidate(bitmap,visual,titles,index+1);return;}bggSearch.search(query,new BggSearchClient.Callback(){public void ok(List<BggSearchClient.Game> games){net.execute(()->{List<PhotoMatch> ranked=rankPhotoMatches(bitmap,query,games);boolean credible=!ranked.isEmpty()&&ranked.get(0).text>=.55;if(credible)runOnUiThread(()->{if(photoProgress!=null)photoProgress.dismiss();renderPhotoMatches(bitmap,ranked,query);});else tryPhotoTitleCandidate(bitmap,visual,titles,index+1);});}public void error(String e){tryPhotoTitleCandidate(bitmap,visual,titles,index+1);}});}
    private BggSearchClient.Game knownVisualGame(String id){
        if(TextUtils.isEmpty(id))return null;
        for(LibraryGame l:libraryDb.all())if(id.equals(l.bggId)){BggSearchClient.Game g=new BggSearchClient.Game();g.id=id;g.name=l.name;g.imageUrl=l.imageUrl;g.rating=l.rating;g.playtime=l.playtime;g.weight=l.weight;g.local=true;return DealPolicy.ratingEligible(g.rating)?g:null;}
        for(DealRecord d:db.getDeals("all_with_review",1000))if(id.equals(d.bggId)){if(!DealPolicy.ratingEligible(d))return null;BggSearchClient.Game g=new BggSearchClient.Game();g.id=id;g.name=name(d);g.imageUrl=d.bggImageUrl;g.rating=d.rating;g.rank=d.rank;g.voters=d.voters;g.qualityScore=d.qualityScore;g.categories=d.bggCategories;g.minPlayers=d.minPlayers;g.maxPlayers=d.maxPlayers;g.playtime=d.playtime;g.weight=d.weight;g.type=d.listingType!=null&&d.listingType.toLowerCase(Locale.ROOT).contains("expan")?"boardgameexpansion":"boardgame";g.local=true;return g;}
        return null;
    }
    private void selectVisualGame(String id,BggSearchClient.Game known){if(bggSearch.configured()){OperationCenter.running(this,"visual:"+id,OperationCenter.MATCH,"BGG #"+id);bggSearch.details(id,false,new BggSearchClient.Callback(){public void ok(List<BggSearchClient.Game> games){runOnUiThread(()->{OperationCenter.done(MainActivity.this,"visual:"+id,OperationCenter.MATCH,"match visivo confermato");chooseLibraryGame(games);});}public void error(String e){runOnUiThread(()->{OperationCenter.error(MainActivity.this,"visual:"+id,OperationCenter.MATCH,"BGG #"+id,e);if(known!=null)chooseLibraryGame(Collections.singletonList(known));else Toast.makeText(MainActivity.this,e,Toast.LENGTH_LONG).show();});}});}else if(known!=null)chooseLibraryGame(Collections.singletonList(known));else Toast.makeText(this,"Cover simile a BGG #"+id+". Collega BGG_TOKEN per recuperarne i dettagli.",Toast.LENGTH_LONG).show();}
    private void photoFailure(Exception e){if(isDestroyed())return;if(photoProgress!=null)photoProgress.dismiss();Toast.makeText(this,"Non riesco a leggere il titolo. Prova una foto più nitida o scrivilo.",Toast.LENGTH_LONG).show();}

    private String jobStage(MarketStore.Job job){
        if(job==null)return"";int p=Math.max(0,job.progress);
        if(MarketStore.COMPLETE.equals(job.state)||p>=100)return"Completato";
        if(MarketStore.FAILED_RETRYABLE.equals(job.state))return"In attesa del prossimo tentativo";
        if(MarketStore.JOB_BGG.equals(job.type)){if(p<25)return"Preparo BGG";if(p<80)return"Scarico i dati BGG";if(p<94)return"Aggiorno la scheda";return"Salvo i dati";}
        if(p<25)return"Preparo la ricerca";if(p<60)return"Cerco l’annuncio su Vinted";if(p<78)return"Verifico il risultato";if(p<94)return"Salvo i dettagli";return"Finalizzo";
    }

    private void applyJobCardFill(View card,long jobId,int baseColor,int accent,int progress,long estimatedRemainingMs){
        int to=Math.max(0,Math.min(100,progress));
        Integer previous=jobProgressMemory.get(jobId);
        int from=previous==null?Math.max(0,to-6):Math.max(0,Math.min(100,previous));
        if(jobProgressMemory.size()>128)jobProgressMemory.clear();
        jobProgressMemory.put(jobId,to);
        GradientDrawable base=round(baseColor,18,1,accent);
        // More visible than v5.11.9, but still translucent enough to keep artwork and title readable.
        GradientDrawable fill=round(Color.argb(158,0,0,0),18,0,0);
        ClipDrawable clip=new ClipDrawable(fill,Gravity.START,ClipDrawable.HORIZONTAL);
        clip.setLevel(from*100);
        card.setBackground(new LayerDrawable(new Drawable[]{base,clip}));
        int destination=to;long duration=420L;
        if(estimatedRemainingMs>0L&&to<88){destination=88;duration=Math.max(1_500L,Math.min(180_000L,estimatedRemainingMs));}
        if(from!=destination){
            android.animation.ValueAnimator animator=android.animation.ValueAnimator.ofInt(from*100,destination*100);
            animator.setDuration(duration);
            animator.setInterpolator(new android.view.animation.LinearInterpolator());
            animator.addUpdateListener(a->clip.setLevel((Integer)a.getAnimatedValue()));
            card.post(animator::start);
        }
    }

    private Drawable queueCardBackground(int baseColor,int accent,int progress){
        GradientDrawable base=round(baseColor,18,1,accent);
        GradientDrawable fill=round(Color.argb(118,0,0,0),18,0,0);
        ClipDrawable clip=new ClipDrawable(fill,Gravity.START,ClipDrawable.HORIZONTAL);
        clip.setLevel(Math.max(0,Math.min(100,progress))*100);
        LayerDrawable layers=new LayerDrawable(new Drawable[]{base,clip});
        return layers;
    }

    private TextView text(String s,float sp,int color,int style){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setTypeface(discoverTypeface(style==Typeface.BOLD?700:400));t.setIncludeFontPadding(false);t.setLineSpacing(0,1.04f);return t;}private GradientDrawable round(int fill,float radius,int stroke,int strokeColor){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));if(stroke>0)g.setStroke(dp(stroke),strokeColor);return g;}private int dp(float x){return(int)(x*getResources().getDisplayMetrics().density+.5f);}private boolean isAccessibilityEnabled(){try{String e=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);return e!=null&&e.toLowerCase(Locale.ROOT).contains(getPackageName().toLowerCase(Locale.ROOT));}catch(Exception x){return false;}}

    private static final class ZoomImageView extends ImageView {private final ScaleGestureDetector detector;private float scale=1f;ZoomImageView(Context c){super(c);setScaleType(ScaleType.MATRIX);detector=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){public boolean onScale(ScaleGestureDetector d){float next=Math.max(1f,Math.min(5f,scale*d.getScaleFactor()));float factor=next/scale;scale=next;setScaleType(ScaleType.MATRIX);getImageMatrix().postScale(factor,factor,d.getFocusX(),d.getFocusY());setImageMatrix(getImageMatrix());return true;}});}public boolean onTouchEvent(android.view.MotionEvent e){detector.onTouchEvent(e);if(e.getAction()==android.view.MotionEvent.ACTION_UP&&scale<=1.02f){scale=1f;setScaleType(ScaleType.FIT_CENTER);}return true;}}


    // Restored in v5.10.12-fix1: helper/actions accidentally dropped during unified queue refactor.

    private BundlePlan bundlePlan(List<DealRecord> games){BundlePlan plan=new BundlePlan();plan.shippingEstimated=true;if(games==null)return plan;for(DealRecord d:games){int item=Math.max(0,d.itemPriceCents);plan.askSubtotal+=item;int suggested=d.offerCents!=null&&d.offerCents>0?d.offerCents:Math.max(100,(int)Math.round(item*.92));if(d.offerCents==null||d.offerCents<=0)plan.offerEstimated=true;plan.offerSubtotal+=Math.min(item,Math.max(0,suggested));if(d.benchmarkCents!=null&&d.benchmarkCents>0)plan.benchmarkCents+=d.benchmarkCents;if(d.shippingVerifiedCents!=null&&d.shippingVerifiedCents>0)plan.shippingCents=Math.max(plan.shippingCents,d.shippingVerifiedCents);else if(d.shippingCents!=null&&d.shippingCents>0)plan.shippingCents=Math.max(plan.shippingCents,d.shippingCents);}if(plan.shippingCents<=0)plan.shippingCents=349;plan.feeCents=PurchaseMath.vintedFee(Math.max(0,plan.offerSubtotal));plan.totalCents=plan.offerSubtotal+plan.feeCents+plan.shippingCents;if(plan.benchmarkCents>0){plan.savingCents=plan.benchmarkCents-plan.totalCents;plan.savingPct=(int)Math.round(plan.savingCents*100.0/plan.benchmarkCents);}return plan;}


    private Map<DealRecord,Integer> allocateBundleShares(List<DealRecord> games,int totalCents){LinkedHashMap<DealRecord,Integer> out=new LinkedHashMap<>();if(games==null||games.isEmpty())return out;int base=0;for(DealRecord d:games)base+=Math.max(0,d.itemPriceCents);int used=0;for(int i=0;i<games.size();i++){DealRecord d=games.get(i);int share=(i==games.size()-1)?Math.max(0,totalCents-used):(base<=0?Math.round(totalCents/(float)games.size()):(int)Math.round(totalCents*(Math.max(0,d.itemPriceCents)/(double)base)));used+=share;out.put(d,share);}return out;}


    private View bundleMiniFan(List<DealRecord> games){FrameLayout fan=new FrameLayout(this);fan.setClipChildren(false);int[] rots={-12,0,12};int shown=Math.min(3,games.size());for(int i=shown-1;i>=0;i--){ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.FIT_CENTER);cover.setBackground(round(SURFACE2,12,1,OUTLINE));cover.setPadding(dp(4),dp(4),dp(4),dp(4));setBggArtwork(cover,games.get(i));FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(86),dp(120),Gravity.CENTER);lp.leftMargin=dp((i-1)*22);lp.topMargin=dp(i*2);cover.setRotation(rots[Math.max(0,Math.min(rots.length-1,i))]);fan.addView(cover,lp);}return fan;}


    private void editListingInfo(DealRecord d,Dialog parent){Dialog dialog=bottomSheet("Modifica dati dell’annuncio");LinearLayout box=dialog.findViewById(SHEET_ID);box.addView(text("Completa solo ciò che Ludo non è riuscito a verificare.",14,MUTED,Typeface.NORMAL));EditText seller=input("Venditore");seller.setText(d.sellerName==null?"":d.sellerName);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(54));sp.topMargin=dp(10);box.addView(seller,sp);EditText published=input("Pubblicato es. 4 ore fa");published.setText(d.publishedLabel==null?"":d.publishedLabel);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(54));pp.topMargin=dp(10);box.addView(published,pp);String current=TextUtils.isEmpty(d.languageCode)?"":d.languageCode.toUpperCase(Locale.ROOT);final String[] selectedLang={current.contains("|")?current.substring(0,current.indexOf('|')):(current.equals("IND")||current.equals("DEP")?"":current)};final String[] selectedDep={current.contains("IND")?"IND":current.contains("DEP")?"DEP":""};box.addView(text("Lingua dell’edizione",14,TEXT,Typeface.BOLD));LinearLayout langRow=new LinearLayout(this);String[][] langs={{"IT","🇮🇹 IT"},{"EN","🇬🇧 EN"},{"FR","🇫🇷 FR"},{"DE","🇩🇪 DE"},{"ES","🇪🇸 ES"},{"","Non so"}};for(int i=0;i<langs.length;i++){final String code=langs[i][0];TextView chip=materialChip(langs[i][1],code.equals(selectedLang[0])?Color.rgb(46,66,86):SURFACE2,code.equals(selectedLang[0])?TEXT:MUTED,code.equals(selectedLang[0]));chip.setTag(code);chip.setOnClickListener(v->{selectedLang[0]=code;refreshChoiceRow(langRow,selectedLang[0]);});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(38));if(i>0)lp.leftMargin=dp(8);langRow.addView(chip,lp);}refreshChoiceRow(langRow,selectedLang[0]);HorizontalScrollView hsv=new HorizontalScrollView(this);hsv.setHorizontalScrollBarEnabled(false);hsv.addView(langRow);box.addView(hsv);TextView depLabel=text("Quanto conta la lingua?",14,TEXT,Typeface.BOLD);depLabel.setPadding(0,dp(16),0,dp(6));box.addView(depLabel);LinearLayout depRow=new LinearLayout(this);String[][] deps={{"IND","🌐 Indipendente"},{"DEP","🔒 Testo necessario"},{"","◌ Da verificare"}};for(int i=0;i<deps.length;i++){final String code=deps[i][0];TextView chip=materialChip(deps[i][1],code.equals(selectedDep[0])?Color.rgb(46,66,86):SURFACE2,code.equals(selectedDep[0])?TEXT:MUTED,code.equals(selectedDep[0]));chip.setTag(code);chip.setOnClickListener(v->{selectedDep[0]=code;refreshChoiceRow(depRow,selectedDep[0]);});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(38));if(i>0)lp.leftMargin=dp(8);depRow.addView(chip,lp);}refreshChoiceRow(depRow,selectedDep[0]);HorizontalScrollView dh=new HorizontalScrollView(this);dh.setHorizontalScrollBarEnabled(false);dh.addView(depRow);box.addView(dh);Button save=button("Salva",CYAN);save.setOnClickListener(v->{String combined=selectedLang[0];if(!TextUtils.isEmpty(selectedDep[0]))combined=(TextUtils.isEmpty(combined)?"?":combined)+"|"+selectedDep[0];db.updateUserFields(d.signature,combined,seller.getText().toString().trim(),published.getText().toString().trim());marketStore.updateLegacyListingLanguage(d.signature,combined);dialog.dismiss();refreshMarketReferencesAsync();if(parent!=null&&parent.isShowing())parent.dismiss();DealRecord fresh=db.findBySignature(d.signature);reconcileDealOperations(fresh);scheduleRender(0);if(fresh!=null)openDetail(fresh);});LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,dp(52));slp.topMargin=dp(16);box.addView(save,slp);dialog.show();}


    private void refreshChoiceRow(LinearLayout row,String selected){for(int i=0;i<row.getChildCount();i++){TextView x=(TextView)row.getChildAt(i);boolean on=Objects.equals((String)x.getTag(),selected);x.setTextColor(on?TEXT:MUTED);x.setBackground(round(on?Color.rgb(46,66,86):SURFACE2,999,1,on?CYAN:OUTLINE));}}


    private void hideDeal(DealRecord d,Dialog parent){new AlertDialog.Builder(this).setTitle("Nascondi annuncio").setMessage("Questo annuncio verrà rimosso dalle viste principali finché non deciderai di ripristinarlo.").setPositiveButton("Nascondi",(x,w)->{db.exclude(d,"Nascosto dall'utente");marketStore.setLegacyListingUserHidden(d.signature,true);if(parent!=null&&parent.isShowing())parent.dismiss();refreshMarketReferencesAsync();render();Toast.makeText(this,"Annuncio nascosto",Toast.LENGTH_SHORT).show();}).setNegativeButton("Annulla",null).show();}


    private int bundleStateColor(String status){if("BUNDLE_READY".equals(status))return PINK;if("RATE_LIMITED".equals(status)||"ERROR".equals(status))return ORANGE;if("BUNDLE_CANDIDATE".equals(status)||"DEEP_SCAN_RUNNING".equals(status))return Color.rgb(58,67,112);if("SELLER_FOUND".equals(status)||"SNAPSHOT_FOUND".equals(status))return Color.rgb(30,88,92);return SURFACE2;}


    private String friendlyOperationError(String e){if(e==null)return"";String x=e.toLowerCase(Locale.ROOT);if(x.contains("429")||x.contains("403")||x.contains("rate"))return"Vinted ha chiesto una pausa. Ludo riproverà senza aumentare le richieste.";if(x.contains("bgg")&&(x.contains("non")||x.contains("nessun")||x.contains("404")))return"Gioco non trovato su BGG. Tocca Risolvi per cercare titolo o ID manualmente.";if(x.contains("202"))return"BGG sta preparando i dati. Puoi riprovare oppure correggere il gioco manualmente.";if(x.contains("404"))return"La pagina non è disponibile o il link non è più valido. Tocca Risolvi per collegarlo manualmente.";return e;}


    private boolean activeTask(OperationCenter.Task t){return OperationCenter.RUNNING.equals(t.state)||OperationCenter.QUEUED.equals(t.state);}


    private int operationColor(String state){if(OperationCenter.ERROR.equals(state))return ORANGE;if(OperationCenter.DONE.equals(state))return Color.rgb(78,196,154);if(OperationCenter.RUNNING.equals(state))return CYAN;return YELLOW;}


    private void addIllustration(LinearLayout box,int resource,String copy){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);ImageView image=new ImageView(this);image.setImageResource(resource);image.setScaleType(ImageView.ScaleType.FIT_CENTER);row.addView(image,new LinearLayout.LayoutParams(dp(88),dp(100)));TextView description=text(copy,15,TEXT,Typeface.NORMAL);row.addView(description,new LinearLayout.LayoutParams(0,-2,1));box.addView(row);}


    private void showLanguageHelp(){Dialog dialog=bottomSheet("Lingua e testo nel gioco");LinearLayout box=dialog.findViewById(SHEET_ID);addIllustration(box,R.drawable.ludo_language,"La lingua dell'edizione e la dipendenza dal testo sono due informazioni diverse.");box.addView(text("Italiano / EN / FR / DE indica la lingua riconosciuta nell'annuncio.\n\n🌐 Indipendente indica che il gioco non richiede di comprendere testo nella lingua dell’edizione: controlla comunque l'edizione e il regolamento.\n\nLingua n/d significa che il dato non è stato verificato.",15,MUTED,Typeface.NORMAL));dialog.show();}


    private View bggFacts(DealRecord d){LinearLayout facts=new LinearLayout(this);facts.setOrientation(LinearLayout.VERTICAL);facts.addView(text(bggMeta(d)+(d.rank==null?"":" · #"+d.rank),13,MUTED,Typeface.NORMAL));if(d.minPlayers!=null)facts.addView(kv("Giocatori",d.minPlayers+(d.maxPlayers==null?"":"–"+d.maxPlayers)));if(d.playtime!=null)facts.addView(kv("Durata",d.playtime+" min"));if(d.weight!=null)facts.addView(kv("Complessità BGG",String.format(Locale.ITALY,"%.1f / 5",d.weight)));return facts;}


    private void refreshDetailBgg(DealRecord d,LinearLayout host,TextView status,boolean force,FrameLayout hero){if(TextUtils.isEmpty(d.bggId)){status.setText("Gioco non associato: usa ? per correggerlo.");return;}SharedPreferences prefs=getSharedPreferences("bgg_detail_refresh",MODE_PRIVATE);long previous=prefs.getLong(d.bggId,0);if(!force&&System.currentTimeMillis()-previous<6*60*60_000L){status.setText("Dati BGG aggiornati nelle ultime 6 ore.");return;}if(!bggSearch.configured()){status.setText("Dati locali: aggiornamento BGG non configurato.");return;}if(!detailRefreshes.add(d.bggId))return;String id=d.bggId;status.setText("Aggiornamento BGG in corso…");OperationCenter.running(this,"detail:"+id,OperationCenter.MATCH,name(d));bggSearch.details(id,false,new BggSearchClient.Callback(){public void ok(List<BggSearchClient.Game> games){runOnUiThread(()->{detailRefreshes.remove(id);if(isFinishing()||isDestroyed())return;BggSearchClient.Game g=games.get(0);db.applyBggGame(g);if(g.marketUsedCount!=null&&g.marketUsedCount>0)marketStore.saveBggMarketStats(g.id,g.marketUsedMedianCents,g.marketUsedMinCents,g.marketUsedCount,System.currentTimeMillis());prefs.edit().putLong(id,System.currentTimeMillis()).apply();d.rating=g.rating;d.rank=g.rank;d.voters=g.voters;d.qualityScore=g.qualityScore;d.bggImageUrl=g.imageUrl;d.bggCategories=g.categories;d.minPlayers=g.minPlayers;d.maxPlayers=g.maxPlayers;d.playtime=g.playtime;d.weight=g.weight;hero.removeAllViews();hero.addView(dealProductArtwork(d),new FrameLayout.LayoutParams(-1,-1));host.removeAllViews();host.addView(scoreView(d));host.addView(bggFacts(d));status.setText("Dati BGG aggiornati adesso.");OperationCenter.done(MainActivity.this,"detail:"+id,OperationCenter.MATCH,g.name);if(!TextUtils.isEmpty(g.imageUrl))ArtworkStore.downloadBgg(MainActivity.this,id,g.imageUrl);});}public void error(String e){runOnUiThread(()->{detailRefreshes.remove(id);if(isFinishing()||isDestroyed())return;status.setText("Aggiornamento non riuscito: "+e+" I dati precedenti restano disponibili.");OperationCenter.error(MainActivity.this,"detail:"+id,OperationCenter.MATCH,name(d),e);});}});}


    private View gameChoiceRow(BggSearchClient.Game g,Runnable selected){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(12),dp(12),dp(12));row.setBackground(round(SURFACE2,18,1,OUTLINE));ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.FIT_CENTER);cover.setBackground(round(SURFACE,12,0,0));File file=TextUtils.isEmpty(g.id)?null:ArtworkStore.bggFile(this,g.id);if(file!=null&&file.exists())cover.setImageBitmap(decodeLocalBitmap(file,240,320));else if(!TextUtils.isEmpty(g.imageUrl))loadRemote(cover,g.imageUrl);else cover.setImageDrawable(iconDrawable(LudoIcons.IMAGE,MUTED,24));row.addView(cover,new LinearLayout.LayoutParams(dp(78),dp(100)));LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(12),0,0,0);copy.addView(text(g.name,18,TEXT,Typeface.BOLD));copy.addView(text((g.year==null?"Anno n/d":String.valueOf(g.year))+" · "+("boardgameexpansion".equals(g.type)?"Espansione":"boardgame".equals(g.type)?"Gioco base":"Tipo da verificare"),13,MUTED,Typeface.NORMAL));copy.addView(text("BGG #"+g.id+(g.local?" · dati locali":""),12,MUTED,Typeface.NORMAL));if(g.notice!=null)copy.addView(text(g.notice,12,ORANGE,Typeface.NORMAL));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));row.setOnClickListener(v->selected.run());return row;}


    private void chooseGames(String title,List<BggSearchClient.Game> games,java.util.function.Consumer<BggSearchClient.Game> selected){if(games==null||games.isEmpty()){Toast.makeText(this,"Nessun risultato. Prova un altro titolo o l'ID BGG.",Toast.LENGTH_LONG).show();return;}Dialog dialog=bottomSheet(title);LinearLayout box=dialog.findViewById(SHEET_ID);for(BggSearchClient.Game g:games)box.addView(gameChoiceRow(g,()->{dialog.dismiss();selected.accept(g);}));dialog.show();}


    private void chooseEdition(BggSearchClient.Game game,java.util.function.Consumer<BggSearchClient.Game> done){ensureLibraryWizardSession();if(game==null){clearLibraryWizardState();return;}libraryWizardGame=copyGame(game);libraryWizardStep="edition";Dialog dialog=wizardSheet("Quale edizione?");libraryWizardDialog=dialog;LinearLayout box=dialog.findViewById(SHEET_ID);addWizardBack(box,"Torna alla ricerca",()->{dialog.dismiss();addLibraryGame(libraryWizardQuery);});LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,-2);gp.bottomMargin=dp(14);box.addView(gameChoiceRow(game,()->openBgg(game.id)),gp);TextView state=text("Le edizioni BGG si caricano in background. Puoi continuare subito.",13,MUTED,Typeface.NORMAL);state.setPadding(0,0,0,dp(12));box.addView(state);Button skip=button("Continua senza specificare edizione",CYAN);skip.setOnClickListener(v->{dialog.dismiss();done.accept(game);});box.addView(skip,new LinearLayout.LayoutParams(-1,dp(52)));dialog.show();if(!bggSearch.configured()){state.setText("Dati online BGG non disponibili: puoi continuare con il gioco selezionato.");return;}bggSearch.details(game.id,true,new BggSearchClient.Callback(){public void ok(List<BggSearchClient.Game> games){runOnUiThread(()->{if(!dialog.isShowing()||games.isEmpty())return;BggSearchClient.Game full=games.get(0);state.setText(full.editions.isEmpty()?"Nessuna edizione aggiuntiva disponibile.":"Oppure scegli l'edizione esatta:");for(BggSearchClient.Edition e:full.editions){LinearLayout row=new LinearLayout(MainActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(10),dp(8),dp(10));ImageView cover=new ImageView(MainActivity.this);cover.setScaleType(ImageView.ScaleType.FIT_CENTER);if(!TextUtils.isEmpty(e.imageUrl))loadRemote(cover,e.imageUrl);row.addView(cover,new LinearLayout.LayoutParams(dp(72),dp(90)));TextView label=text(e.label(),15,TEXT,Typeface.BOLD);label.setPadding(dp(12),0,0,0);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));row.setOnClickListener(v->{full.editionId=e.id;full.editionName=e.label();if(!TextUtils.isEmpty(e.imageUrl))full.imageUrl=e.imageUrl;libraryWizardGame=copyGame(full);dialog.dismiss();done.accept(full);});LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(10);box.addView(row,rp);}});}public void error(String e){runOnUiThread(()->{if(dialog.isShowing())state.setText("Edizioni non disponibili ora. Puoi comunque continuare.");});}});}


    private void refreshDetailPhotos(DealRecord d,FrameLayout hero,TextView status){status.setText(TextUtils.isEmpty(d.vintedUrl)?"Link non ancora associato · usa ? oppure Condividi da Vinted.":"Foto disponibili in memoria · apri Vinted per tutte le foto aggiornate.");}



    private void showMatchCorrection(DealRecord d,Dialog detail){
        Dialog dialog=wizardSheet("Correggi riconoscimento");LinearLayout box=dialog.findViewById(SHEET_ID);
        TextView step=text("Cosa è sbagliato?",20,TEXT,Typeface.BOLD);step.setPadding(0,dp(6),0,dp(14));box.addView(step);
        View gameWrong=wizardOption(R.drawable.provider_bgg_logo,"Cambia gioco BGG","L'annuncio è corretto: cambia solo il gioco associato.",BGG_BG,()->{dialog.dismiss();searchCorrection(d,detail);});box.addView(gameWrong);
        View listingWrong=wizardOption(R.drawable.provider_vinted_logo,"Cambia annuncio Vinted","Il gioco è corretto: cerca o incolla l'annuncio esatto.",VINTED_BG,()->{dialog.dismiss();correctVintedLink(d,detail);});LinearLayout.LayoutParams vlp=new LinearLayout.LayoutParams(-1,-2);vlp.topMargin=dp(10);box.addView(listingWrong,vlp);
        View accessory=wizardOption(LudoIcons.SLIDERS,"È un accessorio o ricambio","Conservalo come articolo collegato a "+name(d)+" invece di trattarlo come gioco.",ORANGE,()->{accessoryDb.save(d.bggId,d.vintedTitle,d.vintedUrl,d.imageUrl,d.itemPriceCents);dialog.dismiss();excludeListing(d,detail);Toast.makeText(this,"Accessorio salvato nella scheda di "+name(d),Toast.LENGTH_SHORT).show();});LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.topMargin=dp(10);box.addView(accessory,ap);
        View bad=wizardOption(LudoIcons.TRASH,"Non è un gioco","Elimina questo elemento da Ludo e dai riferimenti di mercato.",RED,()->{dialog.dismiss();excludeDealAsNonGame(d,detail);});LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=dp(14);box.addView(bad,bp);dialog.show();
    }

    private void excludeDealAsNonGame(DealRecord d,Dialog detail){
        if(d==null)return;new AlertDialog.Builder(this).setTitle("Eliminare questo elemento?").setMessage("Ludo lo considererà non pertinente: uscirà dal catalogo e non influenzerà più prezzi o review.").setNegativeButton("Annulla",null).setPositiveButton("Elimina",(x,w)->{db.exclude(d,"Non è un gioco da tavolo");marketStore.setLegacyListingUserHidden(d.signature,true);bundleDb.invalidate(d);refreshMarketReferencesAsync();if(detail!=null&&detail.isShowing())detail.dismiss();scheduleRender(0);Toast.makeText(this,"Elemento eliminato dall'app",Toast.LENGTH_SHORT).show();}).show();
    }

    private void excludeListing(DealRecord d,Dialog detail){Dialog dialog=bottomSheet("Che tipo di articolo è?");LinearLayout box=dialog.findViewById(SHEET_ID);for(String reason:new String[]{"Inserto o accessorio","Componenti o ricambi","Non è un gioco da tavolo","Offerta non interessante / prezzo senza senso"}){TextView option=text(reason,16,TEXT,Typeface.NORMAL);option.setMinHeight(dp(56));option.setGravity(Gravity.CENTER_VERTICAL);option.setOnClickListener(v->{db.exclude(d,reason);marketStore.setLegacyListingUserHidden(d.signature,true);bundleDb.invalidate(d);dialog.dismiss();refreshMarketReferencesAsync();if(detail!=null&&detail.isShowing())detail.dismiss();render();});box.addView(option);}dialog.show();}

    private void excludeGameAsNonGame(GameRecord game,Dialog parent){if(game==null)return;new AlertDialog.Builder(this).setTitle("Eliminare questo elemento?").setMessage("Ludo lo considererà un articolo non pertinente e lo rimuoverà dal catalogo, dalle review BGG e dai riferimenti di prezzo.").setNegativeButton("Annulla",null).setPositiveButton("Elimina",(x,w)->{for(MarketListingRecord row:marketStore.listingsForGame(game.id,false,300)){String sig=marketStore.signatureForListing(row.id);if(TextUtils.isEmpty(sig))continue;DealRecord d=db.findBySignature(sig);if(d!=null){db.exclude(d,"Non è un gioco da tavolo");bundleDb.invalidate(d);}}marketStore.hideGameAsNonGame(game.id);if(parent!=null&&parent.isShowing())parent.dismiss();refreshMarketReferencesAsync();scheduleRender(0);Toast.makeText(this,"Elemento eliminato dall'app",Toast.LENGTH_SHORT).show();}).show();}



    private void searchCorrection(DealRecord d,Dialog detail){Dialog dialog=bottomSheet("Cambia gioco BGG");LinearLayout box=dialog.findViewById(SHEET_ID);if(d!=null){LinearLayout preview=new LinearLayout(this);preview.setGravity(Gravity.CENTER_VERTICAL);preview.setPadding(0,0,0,dp(8));preview.addView(observedListingPhotoView(d,92,116),new LinearLayout.LayoutParams(dp(92),dp(116)));LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(12),0,0,0);TextView raw=text(TextUtils.isEmpty(d.vintedTitle)?name(d):d.vintedTitle,16,TEXT,Typeface.BOLD);raw.setMaxLines(3);raw.setEllipsize(TextUtils.TruncateAt.END);copy.addView(raw);if(d.itemPriceCents>0)copy.addView(text("Vinted · "+money(d.itemPriceCents),13,MUTED,Typeface.BOLD));copy.addView(text("Usa la foto originale per riconoscere il gioco",12,MUTED,Typeface.NORMAL));preview.addView(copy,new LinearLayout.LayoutParams(0,-2,1));box.addView(preview);}addBggPicker(box,dialog,d==null?"":(!TextUtils.isEmpty(d.gameName)?d.gameName:d.vintedTitle),g->chooseEdition(g,chosen->{long listingId=marketStore.listingIdForSignature(d.signature);DealRecord persisted=db.findBySignature(d.signature);if(persisted!=null)db.correctMatch(d,chosen);else if(listingId>0)marketStore.reassignListingToBggVariant(listingId,chosen,100.0,"Gioco corretto dall'utente");if(chosen.marketUsedCount!=null&&chosen.marketUsedCount>0)marketStore.saveBggMarketStats(chosen.id,chosen.marketUsedMedianCents,chosen.marketUsedMinCents,chosen.marketUsedCount,System.currentTimeMillis());DealRecord fresh=db.findBySignature(d.signature);if(fresh!=null)marketStore.syncLegacyCorrection(fresh);refreshMarketReferencesAsync();bundleDb.invalidate(d);dialog.dismiss();if(detail!=null&&detail.isShowing())detail.dismiss();if(fresh!=null)reconcileDealOperations(fresh);kickActivityQueue();scheduleRender(0);if(fresh!=null)openDetail(fresh);else if(listingId>0)openListingProductDetail(listingId,0,d.signature);}),false);View notGame=wizardOption(LudoIcons.TRASH,"Non è un gioco","Elimina questo elemento da Ludo.",RED,()->{dialog.dismiss();excludeDealAsNonGame(d,detail);});LinearLayout.LayoutParams ng=new LinearLayout.LayoutParams(-1,-2);ng.topMargin=dp(14);box.addView(notGame,ng);dialog.show();}

    private void lookupBgg(String raw,BggSearchClient.Callback cb){String id=bggIdFromInput(raw);if(id!=null)bggSearch.detailsDirect(id,false,cb);else bggSearch.searchFast(raw,cb);}

    private String bggIdFromInput(String raw){if(TextUtils.isEmpty(raw))return null;String x=raw.trim();if(x.matches("[0-9]+"))return x;java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?:boardgamegeek\\.com|bgg\\.cc)/(?:boardgame|boardgameexpansion)/(\\d+)",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(x);return m.find()?m.group(1):null;}


    private void showListingActions(DealRecord d){
        if(d==null)return;Dialog dialog=bottomSheet(name(d));LinearLayout box=dialog.findViewById(SHEET_ID);
        TextView open=text("Apri scheda annuncio",16,TEXT,Typeface.BOLD);open.setGravity(Gravity.CENTER_VERTICAL);open.setMinHeight(dp(54));open.setOnClickListener(v->{dialog.dismiss();openDetail(d);});box.addView(open);
        if(!TextUtils.isEmpty(d.vintedUrl)){TextView vinted=text("Apri su Vinted",16,CYAN,Typeface.BOLD);vinted.setGravity(Gravity.CENTER_VERTICAL);vinted.setMinHeight(dp(54));vinted.setOnClickListener(v->{dialog.dismiss();openVinted(d);});box.addView(vinted);}
        TextView sold=text("Questo annuncio è venduto",16,ORANGE,Typeface.BOLD);sold.setGravity(Gravity.CENTER_VERTICAL);sold.setMinHeight(dp(56));sold.setOnClickListener(v->{dialog.dismiss();confirmManualSold(d,null);});box.addView(sold);dialog.show();
    }
    private void confirmManualSold(DealRecord d,Dialog parent){
        if(d==null)return;Dialog dialog=bottomSheet("Segnare come venduto?");LinearLayout box=dialog.findViewById(SHEET_ID);box.addView(text("L’annuncio verrà rimosso dal Mercato attivo. Il gioco e il prezzo osservato resteranno nello storico.",14,MUTED,Typeface.NORMAL));Button confirm=button("Segna come venduto",RED);confirm.setTextColor(TEXT);confirm.setOnClickListener(v->{dialog.dismiss();markListingSoldByUser(d,parent);});LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(52));cp.topMargin=dp(14);box.addView(confirm,cp);TextView cancel=text("Annulla",14,MUTED,Typeface.BOLD);cancel.setGravity(Gravity.CENTER);cancel.setMinHeight(dp(48));cancel.setOnClickListener(v->dialog.dismiss());box.addView(cancel);dialog.show();
    }
    private void markListingSoldByUser(DealRecord d,Dialog parent){
        if(d==null)return;String sig=d.signature==null?"":d.signature;long listingId=TextUtils.isEmpty(sig)?0L:marketStore.listingIdForSignature(sig);if(listingId<=0&&!TextUtils.isEmpty(d.vintedItemId))listingId=marketStore.listingIdForVintedItemId(d.vintedItemId);
        if(!TextUtils.isEmpty(sig))db.markSold(sig);if(listingId>0)marketStore.markSold(listingId);if(bundleDb!=null)bundleDb.invalidate(d);OperationCenter.resolveForDeal(this,sig,name(d));getSharedPreferences("va_v3_diag",MODE_PRIVATE).edit().putString("lastManualSold","signature="+sig+";listing="+listingId+";at="+System.currentTimeMillis()).apply();if(parent!=null&&parent.isShowing())parent.dismiss();refreshMarketReferencesAsync();scheduleRender(0);Toast.makeText(this,"Annuncio spostato nello storico",Toast.LENGTH_SHORT).show();
    }

    private void showExcluded(){Dialog dialog=bottomSheet("Annunci esclusi");LinearLayout box=dialog.findViewById(SHEET_ID);List<DealRecord> hidden=db.excluded();if(hidden.isEmpty())box.addView(text("Nessun annuncio escluso.",15,MUTED,Typeface.NORMAL));for(DealRecord d:hidden){Button restore=button(d.vintedTitle+" · Ripristina",CYAN);restore.setOnClickListener(v->{db.restore(d.signature);marketStore.setLegacyListingUserHidden(d.signature,false);dialog.dismiss();refreshMarketReferencesAsync();render();});box.addView(restore);}dialog.show();}


    private void correctVintedLink(DealRecord d,Dialog detail){Dialog dialog=bottomSheet("Cambia annuncio Vinted");LinearLayout box=dialog.findViewById(SHEET_ID);Button search=button("Cerca su Vinted",VINTED_BG);search.setOnClickListener(v->openManualVintedSearchForDeal(d));box.addView(search,new LinearLayout.LayoutParams(-1,dp(52)));EditText url=input("Incolla link Vinted");LinearLayout.LayoutParams up=new LinearLayout.LayoutParams(-1,dp(56));up.topMargin=dp(12);box.addView(url,up);Button save=button("Collega link",CYAN);save.setOnClickListener(v->{String value=url.getText().toString().trim();String itemId=vintedItemId(value);if(TextUtils.isEmpty(itemId)){url.setError("Incolla un link Vinted /items/…");return;}long listingId=marketStore.listingIdForSignature(d.signature);if(listingId>0)marketStore.applyManualVintedLink(0,listingId,value,itemId,d.sellerName,null);db.applyResolvedLink(d.signature,itemId,value,null,100,"Link scelto dall'utente",null,d.sellerName,null,System.currentTimeMillis());bundleDb.invalidate(d);dialog.dismiss();refreshMarketReferencesAsync();scheduleRender(0);Toast.makeText(this,"Annuncio Vinted collegato",Toast.LENGTH_SHORT).show();});LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(50));sp.topMargin=dp(8);box.addView(save,sp);dialog.show();}
    private void purchaseSource(BggSearchClient.Game g){ensureLibraryWizardSession();if(g==null){clearLibraryWizardState();return;}libraryWizardGame=copyGame(g);libraryWizardStep="source";libraryWizardSource="";Dialog dialog=wizardSheet("Aggiungi alla Libreria · 2/4");libraryWizardDialog=dialog;LinearLayout box=dialog.findViewById(SHEET_ID);addWizardBack(box,"Cambia gioco o edizione",()->{dialog.dismiss();chooseEdition(g,this::libraryPurchaseDialog);});LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2);pp.bottomMargin=dp(16);box.addView(gamePreviewCompact(g),pp);TextView prompt=text("Da dove arriva la tua copia?",15,MUTED,Typeface.BOLD);prompt.setPadding(0,0,0,dp(10));box.addView(prompt);View online=wizardOption(R.drawable.ic_store,"Comprato online","Vinted o un altro negozio online.",CYAN,()->openWizardStep(dialog,()->purchaseOnline(g)));box.addView(online);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);View local=wizardOption(R.drawable.ic_nav_home,"Comprato in negozio","Registra il prezzo senza costi online.",TEAL,()->openWizardStep(dialog,()->purchaseAmounts(g,"Negozio fisico")));box.addView(local,lp);View gift=wizardOption(R.drawable.ic_gift,"Regalo","Aggiungilo senza prezzo.",PURPLE,()->{if(safeLibraryAdd(g,null,"Regalo",null,null,null,false)){completeLibraryWizardSession();clearLibraryWizardState();dialog.dismiss();scheduleRender(0);}});LinearLayout.LayoutParams glp=new LinearLayout.LayoutParams(-1,-2);glp.topMargin=dp(10);box.addView(gift,glp);TextView skip=text("Aggiungi senza dettagli",14,MUTED,Typeface.BOLD);skip.setGravity(Gravity.CENTER);skip.setMinHeight(dp(52));skip.setPadding(0,dp(12),0,0);skip.setOnClickListener(v->{if(safeLibraryAdd(g,null,null,null,null,null,false)){completeLibraryWizardSession();clearLibraryWizardState();dialog.dismiss();scheduleRender(0);}});box.addView(skip);dialog.show();}


    private void purchaseOnline(BggSearchClient.Game g){ensureLibraryWizardSession();if(g==null)return;libraryWizardGame=copyGame(g);libraryWizardStep="online";Dialog dialog=wizardSheet("Acquisto · 3/4");libraryWizardDialog=dialog;LinearLayout box=dialog.findViewById(SHEET_ID);addWizardBack(box,"Indietro",()->{dialog.dismiss();purchaseSource(g);});LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2);pp.bottomMargin=dp(16);box.addView(gamePreviewCompact(g),pp);View vinted=wizardOption(R.drawable.provider_vinted_logo,"Vinted","Prezzo obbligatorio; spedizione e protezione sono facoltative.",VINTED_BG,()->openWizardStep(dialog,()->purchaseAmounts(g,"Vinted")));box.addView(vinted);View other=wizardOption(R.drawable.ic_store,"Altro negozio online","Registra prezzo e, se vuoi, spedizione.",BGG_BG,()->openWizardStep(dialog,()->purchaseAmounts(g,"Altro negozio online")));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);box.addView(other,lp);dialog.show();}


    private Integer marketReferenceForBgg(String bggId){if(TextUtils.isEmpty(bggId))return null;List<Integer> benchmarkRefs=new ArrayList<>(),observedTotals=new ArrayList<>();for(DealRecord d:db.getDeals("all_with_review",1200))if(bggId.equals(d.bggId)){if(d.benchmarkCents!=null&&d.benchmarkCents>0)benchmarkRefs.add(d.benchmarkCents);Integer total=effectiveTotal(d);if(total!=null&&total>0)observedTotals.add(total);}if(!benchmarkRefs.isEmpty()){Collections.sort(benchmarkRefs);return benchmarkRefs.get(benchmarkRefs.size()/2);}Integer local=bggSearch.localMarketReferenceCents(bggId);if(local!=null&&local>0)return local;if(observedTotals.isEmpty())return null;Collections.sort(observedTotals);return observedTotals.get(observedTotals.size()/2);}


    private List<Integer> allocateLibraryBundleAmount(List<BggSearchClient.Game> games,int total){List<Integer> out=new ArrayList<>();if(games==null||games.isEmpty())return out;List<Integer> weights=new ArrayList<>();int weightTotal=0;for(BggSearchClient.Game game:games){Integer ref=marketReferenceForBgg(game.id);int w=ref==null||ref<=0?0:ref;weights.add(w);weightTotal+=w;}if(weightTotal<=0){weights.clear();for(int i=0;i<games.size();i++)weights.add(1);weightTotal=games.size();}int used=0;for(int i=0;i<games.size();i++){int share=i==games.size()-1?Math.max(0,total-used):Math.max(0,Math.round(total*(weights.get(i)/(float)weightTotal)));out.add(share);used+=share;}return out;}


    private String libraryBundleLabel(List<BggSearchClient.Game> games){if(games==null||games.isEmpty())return"Bundle";if(games.size()==2)return games.get(0).name+" + "+games.get(1).name;Map<String,Integer> counts=new LinkedHashMap<>();for(BggSearchClient.Game g:games){if(TextUtils.isEmpty(g.categories))continue;for(String raw:g.categories.split(" · ")){String k=raw.trim();if(!k.isEmpty())counts.put(k,counts.getOrDefault(k,0)+1);}}String best=null;int bestN=0;for(Map.Entry<String,Integer> e:counts.entrySet())if(e.getValue()>bestN&&e.getValue()>=Math.max(2,(int)Math.ceil(games.size()*.66))){best=e.getKey();bestN=e.getValue();}return best==null?games.get(0).name+" + altri "+(games.size()-1):"Bundle "+bundleThemeLabel(best);}


    private void refreshWizardBundleGames(LinearLayout host){host.removeAllViews();for(int i=0;i<wizardBundleGames.size();i++){BggSearchClient.Game game=wizardBundleGames.get(i);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(8),dp(10),dp(8));row.setBackground(round(SURFACE2,14,1,OUTLINE));ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.FIT_CENTER);if(!TextUtils.isEmpty(game.imageUrl))loadRemote(cover,game.imageUrl);row.addView(cover,new LinearLayout.LayoutParams(dp(48),dp(58)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);tx.setPadding(dp(10),0,0,0);tx.addView(text(game.name,15,TEXT,Typeface.BOLD));tx.addView(text("BGG #"+game.id,11,MUTED,Typeface.NORMAL));row.addView(tx,new LinearLayout.LayoutParams(0,-2,1));if(i>0){final int at=i;TextView del=appIcon(LudoIcons.XMARK,16,MUTED);del.setGravity(Gravity.CENTER);del.setOnClickListener(v->{if(at<wizardBundleGames.size()){wizardBundleGames.remove(at);refreshWizardBundleGames(host);}});row.addView(del,new LinearLayout.LayoutParams(dp(40),dp(44)));}LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);if(i>0)rp.topMargin=dp(6);host.addView(row,rp);}Button add=button("＋ Aggiungi un altro gioco",SURFACE2);add.setTextColor(CYAN);add.setOnClickListener(v->addWizardBundleGame(host));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(50));ap.topMargin=dp(8);host.addView(add,ap);}


    private void addWizardBundleGame(LinearLayout host){Dialog d=bottomSheet("Aggiungi un gioco al bundle");LinearLayout box=d.findViewById(SHEET_ID);box.addView(text("Puoi incollare direttamente il link BGG oppure cercare per nome.",14,MUTED,Typeface.NORMAL));EditText link=input("Link BGG oppure ID");LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(56));lp.topMargin=dp(12);box.addView(link,lp);Button linkGo=button("Aggiungi da BGG",CYAN);linkGo.setOnClickListener(v->{String raw=link.getText().toString().trim();if(raw.isEmpty()){link.setError("Incolla un link BGG");return;}fetchBggDirect(raw,game->{if(addWizardBundleGameIfNew(game)){d.dismiss();refreshWizardBundleGames(host);}});});LinearLayout.LayoutParams lgp=new LinearLayout.LayoutParams(-1,dp(50));lgp.topMargin=dp(8);box.addView(linkGo,lgp);TextView or=text("OPPURE CERCA PER NOME",12,MUTED,Typeface.BOLD);or.setPadding(0,dp(18),0,dp(8));box.addView(or);EditText q=input("Titolo del gioco");box.addView(q,new LinearLayout.LayoutParams(-1,dp(56)));Button search=button("Cerca",LIME);search.setOnClickListener(v->{String query=q.getText().toString().trim();if(query.isEmpty()){q.setError("Scrivi un titolo");return;}bggSearch.searchFast(query,new BggSearchClient.Callback(){public void ok(List<BggSearchClient.Game> games){runOnUiThread(()->chooseGames("Scegli il gioco da aggiungere",games,game->{if(addWizardBundleGameIfNew(game)){d.dismiss();refreshWizardBundleGames(host);}}));}public void error(String e){runOnUiThread(()->Toast.makeText(MainActivity.this,e,Toast.LENGTH_LONG).show());}});});LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(50));sp.topMargin=dp(8);box.addView(search,sp);d.show();}


    private boolean addWizardBundleGameIfNew(BggSearchClient.Game game){if(game==null||TextUtils.isEmpty(game.id))return false;for(BggSearchClient.Game x:wizardBundleGames)if(game.id.equals(x.id)){Toast.makeText(this,"Questo gioco è già nel bundle.",Toast.LENGTH_SHORT).show();return false;}wizardBundleGames.add(copyGame(game));return true;}


    private void purchaseAmounts(BggSearchClient.Game g,String source){ensureLibraryWizardSession();
        if(g==null||TextUtils.isEmpty(g.id)){Toast.makeText(this,"Manca il collegamento BGG. Cerca il gioco manualmente.",Toast.LENGTH_LONG).show();return;}
        boolean sameStep="amounts".equals(libraryWizardStep)&&Objects.equals(libraryWizardSource,source==null?"":source)&&libraryWizardGame!=null&&Objects.equals(libraryWizardGame.id,g.id);boolean keepInputs=restoringLibraryWizard&&sameStep;if(!keepInputs){wizardPaidText="";wizardShippingText="";wizardFeeText="";wizardDateText="";}if(!sameStep||wizardBundleGames.isEmpty()||!g.id.equals(wizardBundleGames.get(0).id)){wizardBundleGames.clear();wizardBundleGames.add(copyGame(g));}
        libraryWizardGame=copyGame(g);libraryWizardStep="amounts";libraryWizardSource=source==null?"":source;Dialog dialog=wizardSheet("Dettagli acquisto · 4/4");libraryWizardDialog=dialog;LinearLayout box=dialog.findViewById(SHEET_ID);addWizardBack(box,"Indietro",()->{dialog.dismiss();if("Vinted".equals(source)||(source!=null&&source.contains("online")))purchaseOnline(g);else purchaseSource(g);});LinearLayout.LayoutParams previewLp=new LinearLayout.LayoutParams(-1,-2);previewLp.bottomMargin=dp(14);box.addView(gamePreviewCompact(g),previewLp);TextView sourceLabel=text(source,13,CYAN,Typeface.BOLD);sourceLabel.setPadding(0,0,0,dp(8));box.addView(sourceLabel);
        CheckBox bundleBuy=new CheckBox(this);bundleBuy.setText("Acquistato in bundle");bundleBuy.setTextColor(TEXT);bundleBuy.setTextSize(15);bundleBuy.setButtonTintList(android.content.res.ColorStateList.valueOf(PINK));bundleBuy.setPadding(0,dp(6),0,dp(6));box.addView(bundleBuy);LinearLayout bundleGamesBox=new LinearLayout(this);bundleGamesBox.setOrientation(LinearLayout.VERTICAL);bundleGamesBox.setVisibility(View.GONE);refreshWizardBundleGames(bundleGamesBox);box.addView(bundleGamesBox);
        TextView priceLabel=text("PREZZO",12,MUTED,Typeface.BOLD);priceLabel.setPadding(0,dp(10),0,dp(8));box.addView(priceLabel);EditText paid=input("Prezzo del gioco €");paid.setInputType(8194);paid.setText(wizardPaidText);box.addView(paid,new LinearLayout.LayoutParams(-1,dp(58)));bundleBuy.setOnCheckedChangeListener((b,checked)->{bundleGamesBox.setVisibility(checked?View.VISIBLE:View.GONE);paid.setHint(checked?"Prezzo totale del bundle €":"Prezzo del gioco €");});
        LinearLayout extras=new LinearLayout(this);extras.setOrientation(LinearLayout.VERTICAL);extras.setPadding(0,dp(4),0,0);extras.setVisibility(View.GONE);boolean online="Vinted".equals(source)||(source!=null&&source.contains("online"));EditText shipping=input("Spedizione € · opzionale");shipping.setInputType(8194);shipping.setText(wizardShippingText);if(online){LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(56));ep.topMargin=dp(10);extras.addView(shipping,ep);}EditText fee=input("Protezione acquisti € · opzionale");fee.setInputType(8194);fee.setText(wizardFeeText);if("Vinted".equals(source)){LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(56));ep.topMargin=dp(10);extras.addView(fee,ep);}EditText date=input("Data AAAA-MM-GG · opzionale");date.setText(wizardDateText);LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(-1,dp(56));dpv.topMargin=dp(10);extras.addView(date,dpv);TextView toggle=text("Aggiungi costi o data  ⌄",14,MUTED,Typeface.BOLD);toggle.setGravity(Gravity.CENTER_VERTICAL);toggle.setMinHeight(dp(52));toggle.setPadding(0,dp(8),0,0);toggle.setOnClickListener(v->{boolean show=extras.getVisibility()!=View.VISIBLE;extras.setVisibility(show?View.VISIBLE:View.GONE);toggle.setText(show?"Nascondi dettagli  ⌃":"Aggiungi costi o data  ⌄");});box.addView(toggle);box.addView(extras);
        LinearLayout totalBox=new LinearLayout(this);totalBox.setOrientation(LinearLayout.VERTICAL);totalBox.setPadding(dp(14),dp(12),dp(14),dp(12));totalBox.setBackground(round(SURFACE2,16,1,OUTLINE));TextView total=text("",16,TEXT,Typeface.BOLD);totalBox.addView(total);LinearLayout.LayoutParams tbp=new LinearLayout.LayoutParams(-1,-2);tbp.topMargin=dp(14);box.addView(totalBox,tbp);android.text.TextWatcher persistWizard=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence x,int a,int c,int z){}public void onTextChanged(CharSequence x,int a,int b,int c){wizardPaidText=paid.getText().toString();wizardShippingText=shipping.getText().toString();wizardFeeText=fee.getText().toString();wizardDateText=date.getText().toString();}public void afterTextChanged(android.text.Editable e){}};paid.addTextChangedListener(persistWizard);shipping.addTextChangedListener(persistWizard);fee.addTextChangedListener(persistWizard);date.addTextChangedListener(persistWizard);
        Runnable recalc=()->{Integer item=parseEuro(paid.getText().toString());Integer ship=online?parseEuro(shipping.getText().toString()):Integer.valueOf(0);Integer actual="Vinted".equals(source)?parseEuro(fee.getText().toString()):Integer.valueOf(0);if(item==null){total.setText(bundleBuy.isChecked()?"Inserisci il prezzo totale del bundle.":"Inserisci il prezzo del gioco. I costi extra possono restare vuoti.");return;}Integer protection="Vinted".equals(source)?(actual==null?Integer.valueOf(PurchaseMath.vintedFee(item)):actual):Integer.valueOf(0);Integer previewShip=ship==null?Integer.valueOf(0):ship;Integer sum=PurchaseMath.total(item,previewShip,protection);String suffix=(ship==null&&online?" · spedizione non inclusa":"")+(actual==null&&"Vinted".equals(source)?" · protezione stimata":"");total.setText((bundleBuy.isChecked()?"Totale bundle ":"Totale ")+(sum==null?money(item):money(sum))+suffix);};android.text.TextWatcher watcher=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){recalc.run();}public void afterTextChanged(android.text.Editable e){}};paid.addTextChangedListener(watcher);shipping.addTextChangedListener(watcher);fee.addTextChangedListener(watcher);bundleBuy.setOnClickListener(v->recalc.run());recalc.run();
        Button save=button("Aggiungi alla Libreria",LIME);save.setOnClickListener(v->{for(EditText e:new EditText[]{paid,shipping,fee})if(!e.getText().toString().trim().isEmpty()&&(parseEuro(e.getText().toString())==null||parseEuro(e.getText().toString())<0)){e.setError("Importo non valido");return;}Integer item=parseEuro(paid.getText().toString());if(item==null){paid.setError(bundleBuy.isChecked()?"Inserisci il prezzo totale del bundle":"Inserisci il prezzo del gioco");return;}Integer ship=online?parseEuro(shipping.getText().toString()):Integer.valueOf(0);Integer actual="Vinted".equals(source)?parseEuro(fee.getText().toString()):Integer.valueOf(0);boolean estimate="Vinted".equals(source)&&actual==null;if(estimate)actual=Integer.valueOf(PurchaseMath.vintedFee(item));Long acquired=null;if(!date.getText().toString().trim().isEmpty())try{java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);format.setLenient(false);acquired=format.parse(date.getText().toString().trim()).getTime();}catch(Exception e){date.setError("Usa AAAA-MM-GG");return;}
            if(bundleBuy.isChecked()){if(wizardBundleGames.size()<2){Toast.makeText(this,"Aggiungi almeno un altro gioco al bundle.",Toast.LENGTH_LONG).show();return;}int n=wizardBundleGames.size();List<Integer> itemShares=allocateLibraryBundleAmount(wizardBundleGames,item);List<Integer> shipShares=allocateLibraryBundleAmount(wizardBundleGames,ship==null?0:ship);List<Integer> feeShares=allocateLibraryBundleAmount(wizardBundleGames,actual==null?0:actual);int bundleTotal=PurchaseMath.total(item,ship==null?0:ship,actual==null?0:actual);String groupId="bundle:"+System.currentTimeMillis()+":"+g.id;String label=libraryBundleLabel(wizardBundleGames);for(int i=0;i<n;i++){BggSearchClient.Game game=wizardBundleGames.get(i);if(!safeLibraryAdd(game,itemShares.get(i),source,shipShares.get(i),acquired,feeShares.get(i),estimate))return;libraryDb.setBundlePurchase(game.id,true,label,n,shipShares.get(i),groupId,bundleTotal);if(!TextUtils.isEmpty(game.imageUrl))ArtworkStore.downloadBgg(this,game.id,game.imageUrl);}}
            else{if(!safeLibraryAdd(g,item,source,ship,acquired,actual,estimate))return;if(!TextUtils.isEmpty(g.imageUrl))ArtworkStore.downloadBgg(this,g.id,g.imageUrl);}finishActiveLibrarySearchJob();completeLibraryWizardSession();clearLibraryWizardState();wizardBundleGames.clear();dialog.dismiss();scheduleRender(0);Toast.makeText(this,bundleBuy.isChecked()?"Bundle aggiunto alla Libreria":"Aggiunto alla Libreria",Toast.LENGTH_SHORT).show();});LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,dp(54));slp.topMargin=dp(18);box.addView(save,slp);dialog.show();
    }

}


