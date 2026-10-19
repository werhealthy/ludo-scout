package it.vintedaffari.app;

/** Failure-safe activation state machine, separated from Android/WorkManager for testing. */
final class FirestoreSyncActivation {
    static final String LEGACY_WORK = "ludo-catalog-sync-v1";
    static final String V2_WORK = "ludo-catalog-sync-v2";
    static final String V3_WORK = "ludo-catalog-sync-v3";
    static final long INTERVAL_MINUTES = 15L;

    interface StateStore {
        boolean pause();
        boolean activate();
        boolean isActive();
    }

    interface Scheduler {
        void cancel(String uniqueWorkName) throws Exception;
        void enqueuePeriodic(String uniqueWorkName, long intervalMinutes,
                             boolean requireNetwork) throws Exception;
    }

    interface FailureRecorder { void record(String stage, Throwable failure); }

    enum Result { ACTIVATED, FAILED }

    private FirestoreSyncActivation() {}

    static Result activate(StateStore state, Scheduler scheduler) {
        return activate(state, scheduler, null);
    }

    static Result activate(StateStore state, Scheduler scheduler, FailureRecorder recorder) {
        if (state == null || scheduler == null) return Result.FAILED;
        boolean enqueued = false;
        String stage = "pause_state_commit";
        try {
            // Fail closed before cancelling/replacing any publisher.
            if (!state.pause()) {
                record(recorder, stage, new IllegalStateException("activation_pause_commit_failed"));
                return Result.FAILED;
            }
            stage = "cancel_v1";
            scheduler.cancel(LEGACY_WORK);
            stage = "cancel_v2";
            scheduler.cancel(V2_WORK);
            stage = "cancel_v3";
            scheduler.cancel(V3_WORK);
            stage = "enqueue_v3";
            scheduler.enqueuePeriodic(V3_WORK, INTERVAL_MINUTES, true);
            enqueued = true;
            stage = "active_state_commit";
            if (!state.activate() || !state.isActive()) {
                throw new IllegalStateException("activation_state_commit_failed");
            }
            return Result.ACTIVATED;
        } catch (Exception failure) {
            record(recorder, stage, failure);
            // A failed WorkManager operation must never escape into the UI or leave
            // the local opt-in enabled without a confirmed periodic request.
            try { state.pause(); } catch (RuntimeException ignored) { }
            if (enqueued) {
                try { scheduler.cancel(V3_WORK); } catch (Exception ignored) { }
            }
            return Result.FAILED;
        }
    }

    private static void record(FailureRecorder recorder, String stage, Throwable failure) {
        if (recorder == null) return;
        try { recorder.record(stage, failure); } catch (RuntimeException ignored) { }
    }
}
