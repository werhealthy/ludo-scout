package it.vintedaffari.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One-time engine/database cutovers owned by the default-process queue service. */
final class EngineStartupMaintenance {
    private static final ExecutorService SWEEP_EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ludo-startup-maintenance");
        thread.setDaemon(true);
        return thread;
    });

    private EngineStartupMaintenance() {}

    static void run(Context context, MarketStore market) {
        applyFreshStart(context, market);
        applyOperationalEpoch(context, market);
        applyReviewTurnaround(context, market);
        applyLegacyQueueRepairs(context, market);
    }

    private static SharedPreferences diagnostics(Context context) {
        return context.getSharedPreferences("va_v3_diag", Context.MODE_PRIVATE);
    }

    private static void applyFreshStart(Context context, MarketStore market) {
        try {
            SharedPreferences prefs = diagnostics(context);
            if (prefs.getBoolean("v5121FreshStartApplied", false)) return;
            long cutoff = System.currentTimeMillis();
            MarketStore.FreshStartSummary summary = market.freshStartLegacyBacklog(cutoff);
            context.getSharedPreferences(OperationCenter.PREFS, Context.MODE_PRIVATE)
                    .edit().remove("tasks").apply();
            prefs.edit().putBoolean("v5121FreshStartApplied", true)
                    .putLong("v5121FreshStartAt", cutoff)
                    .putInt("v5121FreshJobsRemoved", summary.jobsRemoved)
                    .putInt("v5121FreshListingsArchived", summary.listingsArchived)
                    .putInt("v5121FreshDealsArchived", summary.dealsArchived)
                    .putInt("v5121FreshGamesHidden", summary.gamesHidden)
                    .putString("v5121FreshSummary", summary.toString()).apply();
        } catch (Throwable error) {
            diagnostics(context).edit().putString("v5121FreshResetError", String.valueOf(error)).apply();
        }
    }

    private static void applyOperationalEpoch(Context context, MarketStore market) {
        try {
            MarketStore.OperationalEpochSummary cut = market.startOperationalEpochIfMissing();
            SharedPreferences prefs = diagnostics(context);
            if (prefs.getBoolean("v51216OperationalEpochApplied", false)) return;
            context.getSharedPreferences(OperationCenter.PREFS, Context.MODE_PRIVATE)
                    .edit().remove("tasks").apply();
            prefs.edit().putBoolean("v51216OperationalEpochApplied", true)
                    .putLong("v51216OperationalEpochAt", cut.epochAt)
                    .putString("v51216OperationalEpochSummary", cut.toString()).apply();
        } catch (Throwable error) {
            diagnostics(context).edit().putString("v51216OperationalEpochError", String.valueOf(error)).apply();
        }
    }

    private static void applyReviewTurnaround(Context context, MarketStore market) {
        try {
            SharedPreferences prefs = diagnostics(context);
            if (!prefs.getBoolean("v51221ReviewTurnaroundApplied", false)) {
                long cutoff = System.currentTimeMillis();
                int archived = market.archiveAutomaticReviewDebtBefore(cutoff);
                prefs.edit().putBoolean("v51221ReviewTurnaroundApplied", true)
                        .putLong("v51221ReviewTurnaroundAt", cutoff)
                        .putInt("v51221ReviewTurnaroundArchived", archived).apply();
            }
            if (!prefs.getBoolean("v51221ProductNoiseSweepApplied", false)) {
                SWEEP_EXEC.execute(() -> {
                    try {
                        int hidden = market.autoHideStrongNonGameListings();
                        diagnostics(context).edit().putBoolean("v51221ProductNoiseSweepApplied", true)
                                .putInt("v51221ProductNoiseSweepHidden", hidden).apply();
                        context.sendBroadcast(new android.content.Intent(OperationCenter.CHANGED)
                                .setPackage(context.getPackageName()));
                    } catch (Throwable error) {
                        diagnostics(context).edit()
                                .putString("v51221ProductNoiseSweepError", String.valueOf(error)).apply();
                    }
                });
            }
        } catch (Throwable error) {
            diagnostics(context).edit().putString("v51221ReviewTurnaroundError", String.valueOf(error)).apply();
        }
    }

    private static void applyLegacyQueueRepairs(Context context, MarketStore market) {
        try {
            SharedPreferences prefs = diagnostics(context);
            if (!prefs.getBoolean("v51124ReviewBacklogReset", false)) {
                int quarantined = market.quarantineLegacyBggReviewBacklog();
                prefs.edit().putBoolean("v51124ReviewBacklogReset", true)
                        .putInt("v51124LegacyReviewsQuarantined", quarantined).apply();
            }
            if (!prefs.getBoolean("v51125CollisionCleanup", false)) {
                prefs.edit().putBoolean("v51125CollisionCleanup", true).apply();
            }
            if (!prefs.getBoolean("v51126CollisionRepair", false)) {
                int repaired = market.repairV51125CollisionCleanup();
                prefs.edit().putBoolean("v51126CollisionRepair", true)
                        .putInt("v51126CollisionListingsRepaired", repaired).apply();
            }
            if (!prefs.getBoolean("v51126MarketMedian", false)) {
                prefs.edit().putBoolean("v51126MarketMedian", true)
                        .putLong("marketReferenceRefreshAt", 0L).apply();
            }
            if (!prefs.getBoolean("v51268SafeModePricing", false)) {
                prefs.edit().putBoolean("v51268SafeModePricing", true)
                        .putLong("marketReferenceRefreshAt", 0L).apply();
            }
            if (!prefs.getBoolean("v51125VintedLiveLane", false)) {
                int compacted = market.compactVintedBacklogToLiveLane();
                prefs.edit().putBoolean("v51125VintedLiveLane", true)
                        .putInt("v51125VintedJobsCompacted", compacted).apply();
            }
        } catch (Throwable error) {
            diagnostics(context).edit().putString("v51269StartupRepairError", String.valueOf(error)).apply();
        }
    }
}
