package it.vintedaffari.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabaseLockedException;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Foreground owner and supervisor for the durable queue. */
public final class QueueKeepAliveService extends Service {
    private static final String TAG="LudoQueueService";
    private static final String CHANNEL="ludo_background_processing";
    private static final String NEXT_ACTION_CHANNEL="ludo_next_action_v1";
    private static final int NOTIFICATION_ID=5113;
    private static final int ENGINE_COMPLETE_ID=5114;
    private static final long ENGINE_RUN_IDLE_MS=3L*60_000L;
    private static final long IDLE_SLEEP_MS=4_000L;
    private static final long LANE_STALE_MS=35_000L;
    private static volatile boolean RUNNING=false;
    private static volatile boolean STARTING=false;

    private volatile boolean alive=false;
    private final ScheduledExecutorService controlExecutor=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"ludo-queue-control");t.setDaemon(false);return t;});
    private ExecutorService vintedExecutor,bggExecutor;
    private Future<?> vintedFuture,bggFuture;
    private int idleNotificationPulses=0;
    private int sessionStartRemaining=-1,lastRemaining=-1;
    private long lastProgressAt=0L,lastReconcileAt=0L,lastLocalMaintenanceAt=0L;
    private DealDatabase db;
    private MarketStore market;
    private AutoLinkResolver resolver;
    private BggEnricher bgg;
    private BggSearchClient bggMatcher;

    private final Runnable notificationPulse=new Runnable(){@Override public void run(){
        if(!alive)return;
        try{
            long now=System.currentTimeMillis();
            // This entire supervisor pulse runs off the process main thread. Android delivers
            // JobService/Service callbacks on that thread, so SQLite/reconcile work here must never
            // delay WorkManager's SystemJobService.onStartJob acknowledgement.
            if(market!=null)market.touchProcessorHeartbeat();
            if(market!=null)market.deferStuckVintedProcessing(180_000L,10*60_000L);
            if(market!=null&&now-lastReconcileAt>=30_000L){market.reconcileQueue();lastReconcileAt=now;}
            if(market!=null&&now-lastLocalMaintenanceAt>=20_000L){try{market.inferDeferredLanguages(80);}catch(Throwable ignored){}lastLocalMaintenanceAt=now;}
            superviseLanes(false);
            DealDatabase.ObservationSession activeRun=db==null?null:db.activeObservationSession();
            maybeNotifyNextRunComplete(now);
            int active=market==null?1:market.jobSummary().active()+market.bggMatchRequiredCount()+market.deferredVintedReadyCount(now)+market.historicalBggRevalidationPendingCount();
            if(activeRun!=null)active++;
            if(active<=0){if(++idleNotificationPulses>=3){stopSelf();return;}}else idleNotificationPulses=0;
            NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.notify(NOTIFICATION_ID,notification());
        }catch(Throwable t){Log.w(TAG,"notification/supervisor pulse failed",t);ProcessCrashJournal.recordHandled(QueueKeepAliveService.this,"queue:pulse",t);}
        if(alive&&!controlExecutor.isShutdown())try{controlExecutor.schedule(this,8_000L,TimeUnit.MILLISECONDS);}catch(Throwable ignored){}
    }};

    public static boolean isRunning(){return RUNNING;}
    public static boolean isStarting(){return STARTING;}

    public static void ensureRunning(Context context){
        if(context==null)return;
        try{Intent i=new Intent(context.getApplicationContext(),QueueKeepAliveService.class);ContextCompat.startForegroundService(context.getApplicationContext(),i);}
        catch(Throwable ignored){QueueWorkScheduler.schedule(context);}
    }

    @Override public void onCreate(){
        super.onCreate();
        try{
            createChannel();
            startForeground(NOTIFICATION_ID,baseNotification("Avvio…",0,0,true));
        }catch(Throwable t){
            Log.e(TAG,"foreground startup failed",t);ProcessCrashJournal.recordHandled(this,"queue:onCreate:foreground",t);stopSelf();return;
        }
        // From this point the foreground-service contract is satisfied. Everything that can touch
        // SQLite, WorkManager or the 31k-game matcher is moved off the process main thread.
        alive=true;RUNNING=true;STARTING=true;
        try{controlExecutor.execute(this::initializeBackground);}catch(Throwable t){
            STARTING=false;Log.e(TAG,"queue async startup submit failed",t);ProcessCrashJournal.recordHandled(this,"queue:onCreate:submit",t);stopSelf();
        }
    }

    private void initializeBackground(){
        try{
            db=new DealDatabase(this);market=new MarketStore(this,db);EngineStartupMaintenance.run(this,market);market.touchProcessorHeartbeat();
        }catch(Throwable t){
            STARTING=false;Log.e(TAG,"database startup failed",t);ProcessCrashJournal.recordHandled(this,"queue:onCreate:database",t);stopSelf();return;
        }
        try{resolver=new AutoLinkResolver(this);}catch(Throwable t){Log.w(TAG,"resolver init",t);ProcessCrashJournal.recordHandled(this,"queue:onCreate:resolver",t);}
        try{bgg=new BggEnricher(this,db,market);bggMatcher=new BggSearchClient(this);}catch(Throwable t){Log.w(TAG,"bgg init",t);ProcessCrashJournal.recordHandled(this,"queue:onCreate:bgg",t);}
        try{market.applySafeModeQualityCutover();market.resetStaleProcessing();market.reconcileQueue();lastReconcileAt=System.currentTimeMillis();}
        catch(Throwable t){Log.e(TAG,"queue reconcile startup failed",t);ProcessCrashJournal.recordHandled(this,"queue:onCreate:reconcile",t);}
        try{QueueWorkScheduler.ensureRecovery(this);}catch(Throwable t){Log.w(TAG,"recovery scheduler startup failed",t);ProcessCrashJournal.recordHandled(this,"queue:onCreate:recovery",t);}
        try{QueueJobRunner.sweepMissing(this,market);}catch(Throwable t){Log.w(TAG,"sweep startup failed",t);ProcessCrashJournal.recordHandled(this,"queue:onCreate:sweep",t);}
        try{sessionStartRemaining=market.jobSummary().active();lastRemaining=sessionStartRemaining;lastProgressAt=System.currentTimeMillis();}catch(Throwable t){ProcessCrashJournal.recordHandled(this,"queue:onCreate:summary",t);}
        STARTING=false;
        if(!alive)return;
        try{superviseLanes(true);}catch(Throwable t){Log.e(TAG,"lane startup failed",t);ProcessCrashJournal.recordHandled(this,"queue:onCreate:lanes",t);}
        notificationPulse.run();
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(!alive)return START_NOT_STICKY;
        // onStartCommand is a main-thread Android callback. Keep it acknowledgement-only; queue
        // maintenance runs on the same serialized control executor used by startup/pulses.
        try{controlExecutor.execute(()->{
            if(!alive||market==null)return;
            try{market.touchProcessorHeartbeat();QueueJobRunner.sweepMissing(QueueKeepAliveService.this,market);}catch(Throwable t){Log.w(TAG,"start command maintenance failed",t);ProcessCrashJournal.recordHandled(QueueKeepAliveService.this,"queue:onStartCommand:maintenance",t);}
            try{superviseLanes(true);}catch(Throwable t){Log.e(TAG,"start command lanes failed",t);ProcessCrashJournal.recordHandled(QueueKeepAliveService.this,"queue:onStartCommand:lanes",t);}
        });}catch(Throwable t){ProcessCrashJournal.recordHandled(this,"queue:onStartCommand:submit",t);}
        return START_STICKY;
    }

    private synchronized void superviseLanes(boolean userWake){
        if(!alive||market==null)return;long now=System.currentTimeMillis();
        boolean vintedNeeds=market.runnableVintedDueCount(now)>0||market.deferredVintedReadyCount(now)>0||market.activeRunDeferredVintedCount()>0;
        long gate=VintedPublicSession.nextAllowedAt(this);
        boolean vintedCanRun=gate<=now;
        long vh=market.laneHeartbeatAt("vinted");
        boolean vintedStale=vh<=0||now-vh>LANE_STALE_MS;
        boolean vintedProcessing=market.processingVintedCount()>0;
        if(vintedExecutor==null||vintedExecutor.isShutdown()||vintedFuture==null||vintedFuture.isDone()||vintedFuture.isCancelled())restartVintedLane("start");
        else if(vintedNeeds&&vintedCanRun&&!vintedProcessing&&vintedStale&&(userWake||now-vh>LANE_STALE_MS+10_000L))restartVintedLane("stale heartbeat");

        boolean bggNeeds=market.runnableBggDueCount(now)>0||market.bggMatchRequiredCount()>0||market.historicalBggRevalidationPendingCount()>0;
        long bh=market.laneHeartbeatAt("bgg");boolean bggStale=bh<=0||now-bh>LANE_STALE_MS;
        if(bggExecutor==null||bggExecutor.isShutdown()||bggFuture==null||bggFuture.isDone()||bggFuture.isCancelled())restartBggLane("start");
        else if(bggNeeds&&market.processingCount(MarketStore.JOB_BGG)==0&&bggStale&&(userWake||now-bh>LANE_STALE_MS+10_000L))restartBggLane("stale heartbeat");
    }

    private synchronized void restartVintedLane(String why){
        try{if(vintedFuture!=null)vintedFuture.cancel(true);}catch(Throwable ignored){}
        try{if(vintedExecutor!=null)vintedExecutor.shutdownNow();}catch(Throwable ignored){}
        vintedExecutor=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"ludo-vinted-lane");t.setDaemon(false);return t;});
        market.setLaneStatus("vinted","STARTING",why,0L);vintedFuture=vintedExecutor.submit(this::vintedLoop);Log.i(TAG,"Vinted lane restarted: "+why);
    }

    private synchronized void restartBggLane(String why){
        try{if(bggFuture!=null)bggFuture.cancel(true);}catch(Throwable ignored){}
        try{if(bggExecutor!=null)bggExecutor.shutdownNow();}catch(Throwable ignored){}
        bggExecutor=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"ludo-bgg-lane");t.setDaemon(false);return t;});
        market.setLaneStatus("bgg","STARTING",why,0L);bggFuture=bggExecutor.submit(this::bggLoop);Log.i(TAG,"BGG lane restarted: "+why);
    }

    private void vintedLoop(){
        while(alive&&!Thread.currentThread().isInterrupted()){
            try{
                long now=System.currentTimeMillis();market.touchLaneHeartbeat("vinted");
                if(market.isVintedPaused()){market.setLaneStatus("vinted","PAUSED","Vinted in pausa",0L);sleep(2_000L);continue;}
                // Cached batch linking is strictly local. Run it even while the public Vinted lane is
                // pacing/cooling down: waiting for an HTTP permit must never stall free work that can
                // already finish identities from snapshots captured earlier.
                int localApplied=VintedBatchEngine.applyCached(this,db,market,8,false);
                if(localApplied>0){market.setLaneStatus("vinted","LOCAL_BATCH",localApplied+" collegamenti risolti senza rete",0L);sleep(450L);continue;}
                long allowed=VintedPublicSession.nextAllowedAt(this);
                if(allowed>now){String reason=VintedPublicSession.waitReason(this);market.setLaneStatus("vinted","WAITING",reason,allowed);sleep(Math.min(8_000L,Math.max(1_000L,allowed-now)));continue;}
                long urgentReserve=market.urgentVintedReservationUntil(now);
                if(urgentReserve>now){long sec=Math.max(1L,(urgentReserve-now+999L)/1000L);market.setLaneStatus("vinted","WAITING","priorità Vinted tra "+sec+" s",urgentReserve);sleep(Math.min(8_000L,Math.max(1_000L,urgentReserve-now)));continue;}
                int due=market.runnableVintedDueCount(now);
                int activeRunCore=market.activeRunCoreVintedCount();if(activeRunCore<6&&market.deferredVintedCount()>0){int promoted=market.promoteDeferredVintedBatch(6-activeRunCore);if(promoted>0)due=market.runnableVintedDueCount(System.currentTimeMillis());}
                if(due<=0){long next=market.nextRunnableVintedDueAt(),deferredDue=market.nextDeferredVintedDueAt();if(deferredDue>0&&(next<=0||deferredDue<next))next=deferredDue;int deferred=market.deferredVintedCount();market.setLaneStatus("vinted","IDLE",deferred>0?(deferred+" annunci da collegare gradualmente"):"nessuna attività Vinted pronta",next);sleepUntil(next);continue;}
                market.setLaneStatus("vinted","CLAIMING",due+" attività pronte",0L);
                if(resolver==null)resolver=new AutoLinkResolver(this);
                boolean did=QueueJobRunner.processOneVinted(this,db,market,resolver);
                market.touchLaneHeartbeat("vinted");
                if(did)market.setLaneStatus("vinted","ACTIVE","attività completata o rimandata",0L);
                else{market.setLaneStatus("vinted","IDLE","nessuna attività rivendicabile",market.nextRunnableVintedDueAt());sleep(1_500L);}
            }catch(InterruptedException e){Thread.currentThread().interrupt();break;}
            catch(SQLiteDatabaseLockedException e){
                // Cross-process WAL writers can overlap for a few milliseconds. Treat SQLITE_BUSY
                // as backpressure, not as a broken lane, and retry after a short bounded pause.
                long retryAt=System.currentTimeMillis()+3_000L;
                try{market.setLaneStatus("vinted","WAITING","database occupato · riprovo",retryAt);market.touchLaneHeartbeat("vinted");}catch(Throwable ignored){}
                sleepQuiet(3_000L);
            }
            catch(Throwable t){Log.e(TAG,"Vinted lane fault",t);try{market.setLaneStatus("vinted","FAULT",safe(t),System.currentTimeMillis());market.touchLaneHeartbeat("vinted");}catch(Throwable ignored){}sleepQuiet(2_000L);}
        }
    }

    private void bggLoop(){
        while(alive&&!Thread.currentThread().isInterrupted()){
            try{
                long now=System.currentTimeMillis();market.touchLaneHeartbeat("bgg");
                if(market.isBggPaused()){market.setLaneStatus("bgg","PAUSED","Database in pausa",0L);sleep(2_000L);continue;}
                // Current-run identity work always precedes historical cleanup.
                int matching=market.bggMatchRequiredCount();
                if(matching>0){market.setLaneStatus("bgg","MATCHING","Riconosco giochi · "+matching+" da abbinare",0L);resolveLocalBggMatches(8);market.touchLaneHeartbeat("bgg");}

                int due=market.runnableBggDueCount(System.currentTimeMillis());
                // Queue reconciliation has one serialized owner: the control pulse. Lanes only consume claimed work.
                if(due<=0){due=market.runnableBggDueCount(System.currentTimeMillis());}
                if(due>0){
                    market.setLaneStatus("bgg","CLAIMING",due+" schede pronte",0L);if(bgg==null)bgg=new BggEnricher(this,db,market);
                    int batch=QueueJobRunner.processBggBatch(this,market,bgg,20);market.touchLaneHeartbeat("bgg");if(batch>0)market.setLaneStatus("bgg","ACTIVE","batch "+batch+" schede",0L);else sleep(1_000L);
                    continue;
                }

                // Only when no current BGG identity/enrichment work is runnable do we spend CPU on
                // the one-shot historical audit. The local indexes make this cheap; a bounded burst
                // drains useful work quickly without stealing priority from current observations.
                int remainingCurrent=market.bggMatchRequiredCount();
                int historicalPending=market.historicalBggRevalidationPendingCount();
                if(remainingCurrent<=0&&historicalPending>0){
                    int historical=BggHistoricalRevalidator.runSlice(market,bggMatcher,24);
                    market.setLaneStatus("bgg","REVALIDATING",historical+" identità storiche controllate · "+market.historicalBggRevalidationPendingCount()+" residue",0L);
                    market.touchLaneHeartbeat("bgg");
                    if(historical>0){sleep(350L);continue;}
                }

                long next=market.nextRunnableBggDueAt();int review=market.bggMatchReviewCount();int remaining=market.bggMatchRequiredCount();String detail=remaining>0?remaining+" giochi da riconoscere":(historicalPending>0?historicalPending+" identità storiche da rivalidare":(review>0?review+" match BGG da verificare":"nessuna scheda BGG pronta"));market.setLaneStatus("bgg","IDLE",detail,next);sleepUntil(next);continue;
            }catch(InterruptedException e){Thread.currentThread().interrupt();break;}
            catch(Throwable t){Log.e(TAG,"BGG lane fault",t);try{market.setLaneStatus("bgg","FAULT",safe(t),System.currentTimeMillis());market.touchLaneHeartbeat("bgg");}catch(Throwable ignored){}sleepQuiet(2_000L);}
        }
    }

    private int resolveLocalBggMatches(int limit){return QueueJobRunner.matchBggIdentities(this,market,bggMatcher,limit);}

    private void sleepUntil(long due)throws InterruptedException{long now=System.currentTimeMillis();long wait=due>now?Math.min(12_000L,Math.max(IDLE_SLEEP_MS,due-now)):IDLE_SLEEP_MS;Thread.sleep(wait);}
    private static void sleep(long ms)throws InterruptedException{Thread.sleep(Math.max(300L,ms));}
    private static void sleepQuiet(long ms){try{Thread.sleep(Math.max(300L,ms));}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    private static String safe(Throwable t){String s=t==null?"errore":t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage());return s.length()>180?s.substring(0,180):s;}

    private void createChannel(){if(Build.VERSION.SDK_INT<26)return;NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);NotificationChannel ch=new NotificationChannel(CHANNEL,"Attività di Ludo Scout",NotificationManager.IMPORTANCE_LOW);ch.setShowBadge(false);ch.setDescription("Stato delle elaborazioni in background");nm.createNotificationChannel(ch);NotificationChannel next=new NotificationChannel(NEXT_ACTION_CHANNEL,"Prossimo passo",NotificationManager.IMPORTANCE_LOW);next.setShowBadge(true);next.enableVibration(false);next.setSound(null,null);next.setDescription("Suggerimenti discreti quando puoi fare un nuovo scroll o quando serve una conferma manuale");nm.createNotificationChannel(next);}

    /** Notify completed scrolls in chronological order. A newer scroll never announces completion
     * while an older automatic run is still unfinished. */
    private void maybeNotifyNextRunComplete(long now){
        if(db==null)return;SharedPreferences p=getSharedPreferences("ludo_engine_notifications",MODE_PRIVATE);long notified=p.getLong("last_completed_run_end",0L);
        java.util.List<DealDatabase.ObservationSession> runs=db.recentObservationSessions(now-7L*24L*60L*60_000L,80);
        for(int i=runs.size()-1;i>=0;i--){DealDatabase.ObservationSession run=runs.get(i);if(run.endAt<=notified)continue;if(!DealDatabase.engineAutomaticDone(run,now))return;maybeNotifyRunComplete(now,run);return;}
    }

    private void maybeNotifyRunComplete(long now,DealDatabase.ObservationSession run){
        if(run==null||now-run.endAt<ENGINE_RUN_IDLE_MS||run.analysisPendingListings>0)return;
        if(!DealDatabase.engineContentSettled(run))return;
        SharedPreferences p=getSharedPreferences("ludo_engine_notifications",MODE_PRIVATE);if(p.getLong("last_completed_run_end",0L)==run.endAt)return;
        int manual=market==null?Math.max(0,run.reviewListings):Math.max(0,market.vintedReviewCount()+market.bggMatchReviewCount());
        String title,copy;Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if(manual>0){
            title=manual+(manual==1?" gioco da confermare":" giochi da confermare");
            copy="Ludo ha finito il lavoro automatico · tocca per aprire Da completare";
            open.putExtra("open_engine_review",true);
        }else{
            title="Puoi fare un nuovo scroll";
            if(run.validListings==0)copy="Lo scroll precedente è stato elaborato · nessun gioco valido rimasto in attesa";
            else copy=run.completeListings+(run.completeListings==1?" gioco pronto":" giochi pronti")+" · il Motore è libero";
            open.putExtra("open_engine",true);
        }
        PendingIntent pi=PendingIntent.getActivity(this,ENGINE_COMPLETE_ID,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b=new NotificationCompat.Builder(this,NEXT_ACTION_CHANNEL).setSmallIcon(R.mipmap.ic_launcher).setContentIntent(pi).setContentTitle(title).setContentText(copy).setStyle(new NotificationCompat.BigTextStyle().bigText(copy)).setAutoCancel(true).setOnlyAlertOnce(true).setSilent(true).setCategory(NotificationCompat.CATEGORY_STATUS).setPriority(NotificationCompat.PRIORITY_LOW);
        try{((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(ENGINE_COMPLETE_ID,b.build());p.edit().putLong("last_completed_run_end",run.endAt).putInt("last_manual_count",manual).apply();}catch(SecurityException ignored){}
    }

    private Notification notification(){
        if(market==null)return baseNotification("Avvio…",0,0,true);MarketStore.JobSummary summary=market.jobSummary();int deferred=market.deferredVintedCount(),allRemaining=summary.active()+deferred,remaining=market.userVisibleActiveCount(),processing=market.userVisibleProcessingCount();long now=System.currentTimeMillis();int dueNow=market.dueNowCount(now);if(sessionStartRemaining<0)sessionStartRemaining=remaining;if(lastRemaining<0)lastRemaining=remaining;if(remaining<lastRemaining){lastProgressAt=now;lastRemaining=remaining;}
        java.util.Calendar cal=java.util.Calendar.getInstance();cal.set(java.util.Calendar.HOUR_OF_DAY,0);cal.set(java.util.Calendar.MINUTE,0);cal.set(java.util.Calendar.SECOND,0);cal.set(java.util.Calendar.MILLISECOND,0);int completedToday=market.userVisibleCompletedSince(cal.getTimeInMillis());String current=market.currentUserVisibleProcessingLabel();int currentProgress=market.currentUserVisibleProcessingProgress();int deep=market.deepMetadataActiveCount();
        String title=remaining>0?"Ludo Scout · "+remaining+" da completare":(deferred>0?"Ludo Scout · "+deferred+" da collegare":"Ludo Scout · aggiornato");String text;
        if(processing>0&&!TextUtils.isEmpty(current))text=current+" · "+Math.max(15,currentProgress)+"%";
        else{
            int vDue=market.runnableVintedDueCount(now),bDue=market.runnableBggDueCount(now);
            long wait=VintedPublicSession.waitUntil(this);String reason=VintedPublicSession.waitReason(this);
            MarketStore.RuntimeStatus vs=market.laneStatus("vinted"),bs=market.laneStatus("bgg");
            long vh=market.laneHeartbeatAt("vinted"),bh=market.laneHeartbeatAt("bgg");
            if(wait>now&&(vDue>0||deferred>0))text=waitText(reason,wait-now)+" · BGG/locali continuano";
            else if(vDue>0&&(vh<=0||now-vh>LANE_STALE_MS))text="Riavvio il motore Vinted · "+vDue+" pronte";
            else if(bDue>0&&(bh<=0||now-bh>LANE_STALE_MS))text="Riavvio il motore Database · "+bDue+" pronte";
            else if(vDue>0&&"FAULT".equals(vs.state))text="Riprovo Vinted · "+safeDetail(vs.detail);
            else if(vDue>0&&"CLAIMING".equals(vs.state))text="Scelgo la prossima attività · "+vDue+" pronte";
            else if(vDue>0&&"STARTING".equals(vs.state))text="Avvio il motore Vinted";
            else if(bDue>0&&"STARTING".equals(bs.state))text="Avvio il motore Database";
            else if(remaining>0&&dueNow==0){long due=market.nextDueAt();text=due>now?"In attesa · riprendo "+shortWait(due-now):"In attesa";}
            else if(remaining>0)text=completedToday+" completate oggi · coda attiva";
            else if(deferred>0)text="Collegamenti Vinted graduali";
            else if(deep>0)text="Metadati opzionali in background";
            else text=completedToday>0?completedToday+" completate oggi":"Nessuna attività in attesa";
        }
        String sub=deep>0?deep+" dettagli in background":(completedToday>0?completedToday+" completate oggi":null);
        NotificationCompat.Builder b=builder().setContentTitle(title).setContentText(text).setSubText(sub).setOngoing(allRemaining>0).setOnlyAlertOnce(true).setSilent(true);if(processing>0)b.setProgress(100,Math.max(1,Math.min(99,currentProgress)),false);else if(remaining>0||deferred>0)b.setProgress(0,0,true);else b.setProgress(0,0,false);return b.build();
    }

    private static String waitText(String reason,long ms){String prefix="REMOTE_LIMIT".equals(reason)?"Vinted ha chiesto una pausa":"LOCAL_BUDGET".equals(reason)?"Budget Vinted in pausa":"COORDINATOR_BUSY".equals(reason)?"Coordino la coda":"Prossima richiesta Vinted";return prefix+" · "+shortWait(ms);}
    private static String safeDetail(String detail){if(TextUtils.isEmpty(detail))return"riprovo automaticamente";String x=detail.replace('\n',' ').trim();return x.length()>80?x.substring(0,80):x;}
    private static String shortWait(long ms){long sec=Math.max(1,(ms+999)/1000);if(sec<60)return"tra "+sec+" s";long min=sec/60;return"tra "+min+" min";}
    private Notification baseNotification(String text,int max,int progress,boolean indeterminate){NotificationCompat.Builder b=builder().setContentTitle("Ludo Scout").setContentText(text).setOngoing(true).setOnlyAlertOnce(true).setSilent(true);if(max>0||indeterminate)b.setProgress(Math.max(1,max),Math.max(0,progress),indeterminate);return b.build();}
    private NotificationCompat.Builder builder(){Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);return new NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.mipmap.ic_launcher).setContentIntent(pi).setCategory(NotificationCompat.CATEGORY_PROGRESS);}

    @Override public void onDestroy(){alive=false;RUNNING=false;STARTING=false;try{controlExecutor.shutdownNow();}catch(Throwable ignored){}try{if(vintedFuture!=null)vintedFuture.cancel(true);}catch(Throwable ignored){}try{if(bggFuture!=null)bggFuture.cancel(true);}catch(Throwable ignored){}try{if(vintedExecutor!=null)vintedExecutor.shutdownNow();}catch(Throwable ignored){}try{if(bggExecutor!=null)bggExecutor.shutdownNow();}catch(Throwable ignored){}try{if(bggMatcher!=null)bggMatcher.shutdown();}catch(Throwable ignored){}try{if(db!=null)db.close();}catch(Throwable ignored){}super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
