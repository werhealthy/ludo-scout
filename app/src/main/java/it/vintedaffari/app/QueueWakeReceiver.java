package it.vintedaffari.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Bridges queue scheduling from the isolated :ui process into the default background process. */
public final class QueueWakeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String action = intent.getAction();
        // For an explicit user wake, start the low-latency owner first. Scheduling WorkManager is
        // recovery only and must not delay the visible response to pull-to-refresh.
        if (QueueWorkScheduler.ACTION_NOW.equals(action)) QueueKeepAliveService.ensureRunning(context);
        if (QueueWorkScheduler.ACTION_RECOVERY.equals(action)) {
            QueueWorkScheduler.ensureRecoveryLocal(context);
        } else if (QueueWorkScheduler.ACTION_AFTER.equals(action)) {
            QueueWorkScheduler.scheduleAfterLocal(context, intent.getLongExtra(QueueWorkScheduler.EXTRA_DELAY, 10_000L));
        } else {
            QueueWorkScheduler.scheduleLocal(context);
        }
    }
}
