package it.vintedaffari.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Bridges queue scheduling from the isolated :ui/:radar processes into the default background process. */
public final class QueueWakeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String action = intent.getAction();

        // ACTION_NOW has exactly one owner: the foreground service. The previous fall-through also
        // enqueued QueueDrainWorker, creating two same-process consumers that raced on SQLite and
        // duplicated the queue's in-memory working set. WorkManager is recovery only.
        if (QueueWorkScheduler.ACTION_NOW.equals(action)) {
            QueueKeepAliveService.ensureRunning(context);
            return;
        }
        if (QueueWorkScheduler.ACTION_RECOVERY.equals(action)) {
            QueueWorkScheduler.ensureRecoveryLocal(context);
            return;
        }
        if (QueueWorkScheduler.ACTION_AFTER.equals(action)) {
            QueueWorkScheduler.scheduleAfterLocal(context, intent.getLongExtra(QueueWorkScheduler.EXTRA_DELAY, 10_000L));
            return;
        }
        QueueWorkScheduler.scheduleLocal(context);
    }
}
