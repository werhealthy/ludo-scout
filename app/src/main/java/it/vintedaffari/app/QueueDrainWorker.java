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
        // WorkManager is recovery only. If the foreground owner exists (including cold start),
        // stand down before opening SQLite or constructing duplicate resolver/matcher working sets.
        if(QueueKeepAliveService.isStarting()||QueueKeepAliveService.isRunning())return Result.success();
        DealDatabase db = new DealDatabase(context);
        MarketStore market = new MarketStore(context, db);
        // WorkManager is recovery, but a living Service process is not enough evidence that its
        // consumer lanes are alive. v5.11.11's process heartbeat could stay fresh even while the
        // Vinted executor had stopped making progress. Only stand down when every due lane has a
        // fresh lane-specific heartbeat (or Vinted is intentionally pacing).
        if (QueueKeepAliveService.isRunning()) {
            long now=System.currentTimeMillis();
            int vd=market.runnableVintedDueCount(now), bd=market.runnableBggDueCount(now), hp=market.historicalBggRevalidationPendingCount();
            long vh=market.laneHeartbeatAt("vinted"), bh=market.laneHeartbeatAt("bgg");
            boolean vHealthy=vd<=0 || VintedPublicSession.nextAllowedAt(context)>now || market.processingVintedCount()>0 || (vh>0&&now-vh<45_000L);
            boolean bHealthy=(bd<=0&&hp<=0) || market.processingCount(MarketStore.JOB_BGG)>0 || (bh>0&&now-bh<45_000L);
            if(vHealthy&&bHealthy)return Result.success();
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
        return Result.success();
    }

}
