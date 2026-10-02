package it.vintedaffari.app;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Durable background wake-up for the market queue.
 *
 * MainActivity intentionally lives in :ui while WorkManager and queue execution live in the
 * default app process. WorkManager's ordinary in-process API must not be silently invoked from
 * both processes: calls from :ui are forwarded to QueueWakeReceiver in the default process.
 */
public final class QueueWorkScheduler {
    static final String ACTION_NOW = "it.vintedaffari.app.QUEUE_WAKE_NOW";
    static final String ACTION_RECOVERY = "it.vintedaffari.app.QUEUE_WAKE_RECOVERY";
    static final String ACTION_AFTER = "it.vintedaffari.app.QUEUE_WAKE_AFTER";
    static final String EXTRA_DELAY = "delay_ms";

    private static final String UNIQUE_NOW = "ludo-market-queue-now";
    private static final String UNIQUE_RECOVERY = "ludo-market-queue-recovery";
    private static final String UNIQUE_CONTINUE = "ludo-market-queue-continue";
    private static final ExecutorService SCHEDULER_EXEC=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"ludo-work-scheduler");t.setDaemon(false);return t;});
    private QueueWorkScheduler() {}

    private static void runLocalOffMain(Runnable task) {
        if(task==null)return;
        try{
            if(Looper.myLooper()==Looper.getMainLooper())SCHEDULER_EXEC.execute(task);
            else task.run();
        }catch(Throwable ignored){}
    }

    private static Constraints network() {
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    }

    static boolean isDefaultProcess(Context context) {
        if (context == null) return false;
        try {
            String process = Application.getProcessName();
            return context.getPackageName().equals(process);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static void wakeDefaultProcess(Context context, String action, long delayMs) {
        if (context == null) return;
        try {
            Intent i = new Intent(action)
                    .setComponent(new ComponentName(context.getPackageName(), QueueWakeReceiver.class.getName()))
                    .setPackage(context.getPackageName());
            if (delayMs > 0) i.putExtra(EXTRA_DELAY, delayMs);
            context.getApplicationContext().sendBroadcast(i);
        } catch (Throwable ignored) {}
    }

    public static void schedule(Context context) {
        if (context == null) return;
        if (!isDefaultProcess(context)) { wakeDefaultProcess(context, ACTION_NOW, 0L); return; }
        Context app=context.getApplicationContext();runLocalOffMain(()->scheduleLocal(app));
    }

    static void scheduleLocal(Context context) {
        if (context == null) return;
        try {
            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(QueueDrainWorker.class)
                    .setConstraints(network())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .addTag("ludo-market-queue")
                    .build();
            WorkManager.getInstance(context.getApplicationContext())
                    .enqueueUniqueWork(UNIQUE_NOW, ExistingWorkPolicy.KEEP, request);
        } catch (Throwable ignored) {}
    }

    public static void ensureRecovery(Context context) {
        if (context == null) return;
        if (!isDefaultProcess(context)) { wakeDefaultProcess(context, ACTION_RECOVERY, 0L); return; }
        Context app=context.getApplicationContext();runLocalOffMain(()->ensureRecoveryLocal(app));
    }

    static void ensureRecoveryLocal(Context context) {
        if (context == null) return;
        try {
            PeriodicWorkRequest periodic = new PeriodicWorkRequest.Builder(QueueDrainWorker.class, 15, TimeUnit.MINUTES)
                    .setConstraints(network())
                    .addTag("ludo-market-queue-recovery")
                    .build();
            WorkManager.getInstance(context.getApplicationContext())
                    .enqueueUniquePeriodicWork(UNIQUE_RECOVERY, ExistingPeriodicWorkPolicy.KEEP, periodic);
        } catch (Throwable ignored) {}
    }

    public static void scheduleAfter(Context context, long delayMs) {
        if (context == null) return;
        if (!isDefaultProcess(context)) { wakeDefaultProcess(context, ACTION_AFTER, delayMs); return; }
        Context app=context.getApplicationContext();runLocalOffMain(()->scheduleAfterLocal(app,delayMs));
    }

    static void scheduleAfterLocal(Context context, long delayMs) {
        if (context == null) return;
        try {
            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(QueueDrainWorker.class)
                    .setConstraints(network())
                    .setInitialDelay(Math.max(10_000L, delayMs), TimeUnit.MILLISECONDS)
                    .addTag("ludo-market-queue-continuation")
                    .build();
            WorkManager.getInstance(context.getApplicationContext())
                    .enqueueUniqueWork(UNIQUE_CONTINUE, ExistingWorkPolicy.REPLACE, request);
        } catch (Throwable ignored) {}
    }

    static final String ACTION_BROWSER = "it.vintedaffari.app.BROWSER_LOCAL_WAKE";
    static final String ACTION_BROWSER_RECOVERY = "it.vintedaffari.app.BROWSER_LOCAL_RECOVERY";
    private static final String BROWSER_NOW="ludo-browser-local-now",BROWSER_RECOVERY="ludo-browser-local-recovery",BROWSER_CONTINUE="ludo-browser-local-continue";
    static OneTimeWorkRequest browserRequest(){return new OneTimeWorkRequest.Builder(BrowserLocalDrainWorker.class).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).addTag("ludo-browser-local").build();}
    static PeriodicWorkRequest browserRecoveryRequest(){return new PeriodicWorkRequest.Builder(BrowserLocalDrainWorker.class,15,TimeUnit.MINUTES).addTag("ludo-browser-local-recovery").build();}
    public static void scheduleLocalBrowser(Context context){if(context==null)return;if(!isDefaultProcess(context)){wakeDefaultProcess(context,ACTION_BROWSER,0);return;}Context app=context.getApplicationContext();runLocalOffMain(()->scheduleLocalBrowserInOwner(app));}
    static void scheduleLocalBrowserInOwner(Context context){WorkManager.getInstance(context).enqueueUniqueWork(BROWSER_NOW,ExistingWorkPolicy.APPEND_OR_REPLACE,browserRequest());ensureLocalBrowserRecoveryInOwner(context);QueueKeepAliveService.ensureRunning(context);}
    public static void ensureLocalBrowserRecovery(Context context){if(context==null)return;if(!isDefaultProcess(context)){wakeDefaultProcess(context,ACTION_BROWSER_RECOVERY,0);return;}Context app=context.getApplicationContext();runLocalOffMain(()->ensureLocalBrowserRecoveryInOwner(app));}
    static void ensureLocalBrowserRecoveryInOwner(Context context){WorkManager.getInstance(context).enqueueUniquePeriodicWork(BROWSER_RECOVERY,ExistingPeriodicWorkPolicy.KEEP,browserRecoveryRequest());}
    static void scheduleLocalBrowserAfter(Context context,long delay){OneTimeWorkRequest next=new OneTimeWorkRequest.Builder(BrowserLocalDrainWorker.class).setInitialDelay(Math.max(10_000L,delay),TimeUnit.MILLISECONDS).addTag("ludo-browser-local").build();WorkManager.getInstance(context).enqueueUniqueWork(BROWSER_CONTINUE,ExistingWorkPolicy.REPLACE,next);}
}
