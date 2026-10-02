package it.vintedaffari.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bridges queue scheduling from the isolated :ui/:radar processes into the default background process. */
public final class QueueWakeReceiver extends BroadcastReceiver {
    private static final ExecutorService WAKE_EXEC=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"ludo-queue-wake");t.setDaemon(false);return t;});

    @Override public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        final Context app=context.getApplicationContext();
        final String action=intent.getAction();
        final long delay=intent.getLongExtra(QueueWorkScheduler.EXTRA_DELAY,10_000L);

        // ACTION_NOW has one owner only: the foreground service. Falling through and also
        // enqueuing QueueDrainWorker created two same-process queue consumers and unnecessary
        // SQLite/heap contention exactly while the default process was cold-starting.
        if (QueueWorkScheduler.ACTION_NOW.equals(action)) {
            QueueKeepAliveService.ensureRunning(app);
            return;
        }

        final BroadcastReceiver.PendingResult pending=goAsync();
        try{
            WAKE_EXEC.execute(()->{
                try{
                    if(QueueWorkScheduler.ACTION_BROWSER.equals(action)){QueueWorkScheduler.scheduleLocalBrowserInOwner(app);
                    }else if(QueueWorkScheduler.ACTION_BROWSER_RECOVERY.equals(action)){QueueWorkScheduler.ensureLocalBrowserRecoveryInOwner(app);
                    }else if(QueueWorkScheduler.ACTION_RECOVERY.equals(action)){
                        QueueWorkScheduler.ensureRecoveryLocal(app);
                    }else if(QueueWorkScheduler.ACTION_AFTER.equals(action)){
                        QueueWorkScheduler.scheduleAfterLocal(app,delay);
                    }else{
                        QueueWorkScheduler.scheduleLocal(app);
                    }
                }catch(Throwable ignored){
                }finally{
                    try{pending.finish();}catch(Throwable ignored){}
                }
            });
        }catch(Throwable t){
            try{pending.finish();}catch(Throwable ignored){}
        }
    }
}
