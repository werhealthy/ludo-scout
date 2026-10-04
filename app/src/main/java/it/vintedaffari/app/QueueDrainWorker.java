package it.vintedaffari.app;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bounded background drain of the durable queue.
 *
 * It intentionally does not depend on Activity or Accessibility UI state. Each invocation works
 * for a short bounded interval and lets WorkManager reschedule later if more work remains.
 */
public final class QueueDrainWorker extends Worker {
    private static final String TAG = "LudoBackground";
    private static final long MAX_RUN_MS = 7 * 60_000L;
    private static final int MAX_ITEMS = 36;

    public QueueDrainWorker(@NonNull Context appContext, @NonNull WorkerParameters params) {
        super(appContext, params);
    }

    @NonNull @Override public Result doWork() {
        Context context = getApplicationContext();
        QueueWorkScheduler.ensureRecovery(context);
        AiEngineRunner.schedule(context);
        // During service cold start there is no safe second owner yet. Once startup has finished,
        // however, RUNNING alone is not proof of liveness: Android can leave the Service process alive
        // while a consumer/control executor is stalled. WorkManager therefore checks SQLite-backed lane
        // heartbeats and only stands down when the foreground owner is actually healthy.
        DealDatabase db = new DealDatabase(context);
        MarketStore market = new MarketStore(context, db);
        if(QueueKeepAliveService.isStarting()){
            market.setDiagnosticState("queue_recovery",1,"build=queue-recovery-v3;state=SKIPPED_STARTING;reason=service-cold-start");
            return Result.success();
        }
        market.setDiagnosticState("queue_recovery",1,"build=queue-recovery-v3;state=STARTED;serviceRunning="+QueueKeepAliveService.isRunning());
        if (QueueKeepAliveService.isRunning()) {
            long now=System.currentTimeMillis();
            int vd=market.runnableVintedDueCount(now), activeDeferred=market.activeRunDeferredVintedCount();
            int bd=market.runnableBggDueCount(now), hp=market.historicalBggRevalidationPendingCount();
            long vh=market.laneHeartbeatAt("vinted"), bh=market.laneHeartbeatAt("bgg");
            long vAge=vh<=0?Long.MAX_VALUE:Math.max(0L,now-vh),bAge=bh<=0?Long.MAX_VALUE:Math.max(0L,now-bh);
            boolean vNeeds=vd>0||activeDeferred>0;
            boolean bNeeds=bd>0||hp>0;
            long gateUntil=VintedPublicSession.nextAllowedAt(context);
            boolean vHealthy=!vNeeds || gateUntil>now || vAge<45_000L ||
                    (market.processingVintedCount()>0&&vAge<180_000L);
            boolean bHealthy=!bNeeds || bAge<45_000L ||
                    (market.processingCount(MarketStore.JOB_BGG)>0&&bAge<180_000L);
            if(vHealthy&&bHealthy){
                String vReason=!vNeeds?"NO_WORK":gateUntil>now?"GATE":"HEARTBEAT";
                String bReason=!bNeeds?"NO_WORK":"HEARTBEAT";
                market.setDiagnosticState("queue_recovery",1,"build=queue-recovery-v3;state=SKIPPED_SERVICE_HEALTHY;vReason="+vReason+
                        ";vDue="+vd+";vDeferred="+activeDeferred+";vHeartbeatAgeMs="+vAge+";vGateRemainingMs="+Math.max(0L,gateUntil-now)+
                        ";bReason="+bReason+";bDue="+bd+";bHistorical="+hp+";bHeartbeatAgeMs="+bAge);
                return Result.success();
            }
            market.setDiagnosticState("queue_recovery",1,"build=queue-recovery-v3;state=TAKEOVER;vDue="+vd+
                    ";vDeferred="+activeDeferred+";vHeartbeatAgeMs="+vAge+";bDue="+bd+";bHistorical="+hp+
                    ";bHeartbeatAgeMs="+bAge+";serviceRunning=true");
        }
        market.resetStaleProcessingOlderThan(15 * 60_000L);
        market.reconcileQueue();
        market.touchProcessorHeartbeat();
        AutoLinkResolver resolver = new AutoLinkResolver(context);
        BggEnricher bgg = new BggEnricher(context, db, market);
        BggSearchClient bggMatcher = new BggSearchClient(context);
        long started = System.currentTimeMillis();
        int processed = 0;

        QueueJobRunner.sweepMissing(context, market);

        try {
            while (!isStopped() && processed < MAX_ITEMS && System.currentTimeMillis() - started < MAX_RUN_MS) {
                market.touchProcessorHeartbeat();
                boolean didWork = false;
                int room=Math.max(1,MAX_ITEMS-processed);
                if(market.bggMatchRequiredCount()>0){int matched=QueueJobRunner.matchBggIdentities(context,market,bggMatcher,Math.min(20,room));if(matched>0){didWork=true;processed+=matched;room=Math.max(1,MAX_ITEMS-processed);}}
                int bggDone=QueueJobRunner.processBggBatch(context,market,bgg,Math.min(20,room));
                if(bggDone>0){didWork=true;processed+=bggDone;}
                if (isStopped() || processed >= MAX_ITEMS || System.currentTimeMillis() - started >= MAX_RUN_MS) break;
                int activeRunCore=market.activeRunCoreVintedCount();
                if(activeRunCore<6&&market.activeRunDeferredVintedCount()>0){
                    int promoted=market.promoteDeferredVintedBatch(6-activeRunCore);
                    if(promoted>0)didWork=true;
                }
                if (QueueJobRunner.processOneVinted(context, db, market, resolver)) { didWork = true; processed++; }
                if (!didWork) break;
            }
        } catch (Throwable t) {
            Log.w(TAG, "background queue pass failed", t);
        }

        // Recovery follows the same priority as the foreground service: only spend remaining local
        // budget on historical revalidation after current BGG identity/enrichment work is clear.
        try{
            long now=System.currentTimeMillis();
            if(!isStopped()&&processed<MAX_ITEMS&&now-started<MAX_RUN_MS&&market.bggMatchRequiredCount()==0&&market.runnableBggDueCount(now)==0){
                int historical=BggHistoricalRevalidator.runSlice(market,bggMatcher,Math.min(24,MAX_ITEMS-processed));
                processed+=historical;
            }
        }catch(Throwable t){Log.w(TAG,"historical BGG recovery pass failed",t);}

        MarketStore.JobSummary remaining = market.jobSummary();
        context.sendBroadcast(new android.content.Intent(OperationCenter.CHANGED).setPackage(context.getPackageName()));
        // Do not use WorkManager Result.retry() as a queue continuation: its exponential backoff
        // made a large backlog look frozen overnight. Persisted state is authoritative, so finish
        // this bounded pass successfully and schedule the next due pass explicitly.
        if (remaining.active() > 0 || market.deferredVintedCount() > 0 || market.historicalBggRevalidationPendingCount() > 0) {
            long now=System.currentTimeMillis();
            long due = market.nextDueAt();
            long deferredDue=market.nextDeferredVintedDueAt();
            if(deferredDue>0L&&(due<=0L||deferredDue<due))due=deferredDue;
            long gate=VintedPublicSession.nextAllowedAt(context);
            long target=due>0L?due:now+10_000L;
            if(gate>target)target=gate;
            long delay=Math.max(10_000L,target-now);
            QueueWorkScheduler.scheduleAfter(context, Math.min(delay, 15 * 60_000L));
        }
        try{bggMatcher.shutdown();}catch(Throwable ignored){}
        market.setDiagnosticState("queue_recovery",1,"build=queue-recovery-v3;state=FINISHED;processed="+processed+
                ";remainingActive="+remaining.active()+";deferred="+market.deferredVintedCount()+
                ";historical="+market.historicalBggRevalidationPendingCount()+";elapsedMs="+(System.currentTimeMillis()-started));
        return Result.success();
    }

}
