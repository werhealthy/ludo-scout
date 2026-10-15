package it.vintedaffari.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public final class FirestoreSyncActivationTest {
    @Test public void activeLegacyOptInDoesNotRequireASecondConsentPrompt() {
        assertTrue(FirestoreOptInPolicy.accepts(true, ""));
        assertTrue(FirestoreOptInPolicy.accepts(true, null));
        assertTrue(FirestoreOptInPolicy.accepts(true, "minimal-v3"));
        assertFalse(FirestoreOptInPolicy.accepts(false, ""));
        assertFalse(FirestoreOptInPolicy.accepts(true, "unknown-version"));
    }

    @Test public void workManagerFailureAfterPauseDoesNotEscapeAndReopensPaused() {
        Map<String, Object> disk = new HashMap<>();
        FakeState before = new FakeState(disk);
        assertTrue(before.activate());
        FakeScheduler scheduler = new FakeScheduler("cancel:" + FirestoreSyncActivation.V2_WORK);
        List<String> diagnostics = new ArrayList<>();

        FirestoreSyncActivation.Result result = FirestoreSyncActivation.activate(before, scheduler,
                (stage, failure) -> diagnostics.add(FirestoreActivationDiagnostics.summary(stage, failure)));

        assertEquals(FirestoreSyncActivation.Result.FAILED, result);
        assertFalse(new FakeState(disk).isActive());
        assertEquals(0, scheduler.enqueueCount);
        assertEquals(1, diagnostics.size());
        assertEquals("stage=cancel_v2;exception=java.lang.IllegalStateException", diagnostics.get(0));
        assertFalse(diagnostics.get(0).contains("do-not-store"));
    }

    @Test public void activationSchedulesOneConnectedQuarterHourWorkAndSurvivesReopen() {
        Map<String, Object> disk = new HashMap<>();
        FakeState state = new FakeState(disk);
        FakeScheduler scheduler = new FakeScheduler(null);

        FirestoreSyncActivation.Result result = FirestoreSyncActivation.activate(state, scheduler);

        assertEquals(FirestoreSyncActivation.Result.ACTIVATED, result);
        assertTrue(new FakeState(disk).isActive());
        assertEquals(1, scheduler.enqueueCount);
        assertEquals(FirestoreSyncActivation.V3_WORK, scheduler.enqueuedName);
        assertEquals(FirestoreSyncActivation.INTERVAL_MINUTES, scheduler.intervalMinutes);
        assertTrue(scheduler.requireNetwork);
        assertTrue(scheduler.v3Queued);
    }

    @Test public void enqueueFailureLeavesOptInPausedAndReportsFailure() {
        Map<String, Object> disk = new HashMap<>();
        FakeState state = new FakeState(disk);
        FakeScheduler scheduler = new FakeScheduler("enqueue");
        List<String> diagnostics = new ArrayList<>();

        FirestoreSyncActivation.Result result = FirestoreSyncActivation.activate(state, scheduler,
                (stage, failure) -> diagnostics.add(FirestoreActivationDiagnostics.summary(stage, failure)));

        assertEquals(FirestoreSyncActivation.Result.FAILED, result);
        assertFalse(new FakeState(disk).isActive());
        assertFalse(scheduler.v3Queued);
        assertEquals("stage=enqueue_v3;exception=java.lang.IllegalStateException", diagnostics.get(0));
        assertFalse(diagnostics.get(0).contains("do-not-store"));
    }

    @Test public void failedPauseCommitDoesNotScheduleAndReportsStage() {
        Map<String, Object> disk = new HashMap<>();
        FakeState state = new FakeState(disk, false);
        FakeScheduler scheduler = new FakeScheduler(null);
        List<String> diagnostics = new ArrayList<>();

        FirestoreSyncActivation.Result result = FirestoreSyncActivation.activate(state, scheduler,
                (stage, failure) -> diagnostics.add(FirestoreActivationDiagnostics.summary(stage, failure)));

        assertEquals(FirestoreSyncActivation.Result.FAILED, result);
        assertTrue(scheduler.cancellations.isEmpty());
        assertEquals(0, scheduler.enqueueCount);
        assertEquals("stage=pause_state_commit;exception=java.lang.IllegalStateException", diagnostics.get(0));
    }

    private static final class FakeState implements FirestoreSyncActivation.StateStore {
        private final Map<String, Object> disk;
        private final boolean pauseSucceeds;
        FakeState(Map<String, Object> disk) { this(disk, true); }
        FakeState(Map<String, Object> disk, boolean pauseSucceeds) {
            this.disk = disk;
            this.pauseSucceeds = pauseSucceeds;
        }
        @Override public boolean pause() {
            if (!pauseSucceeds) return false;
            disk.put("enabled", false);
            disk.remove("opt_in_version");
            return true;
        }
        @Override public boolean activate() {
            disk.put("enabled", true);
            disk.put("opt_in_version", "minimal-v3");
            return true;
        }
        @Override public boolean isActive() {
            return Boolean.TRUE.equals(disk.get("enabled"))
                    && "minimal-v3".equals(disk.get("opt_in_version"));
        }
    }

    private static final class FakeScheduler implements FirestoreSyncActivation.Scheduler {
        private final String failAt;
        final List<String> cancellations = new ArrayList<>();
        int enqueueCount;
        String enqueuedName;
        long intervalMinutes;
        boolean requireNetwork;
        boolean v3Queued;
        FakeScheduler(String failAt) { this.failAt = failAt; }
        @Override public void cancel(String uniqueWorkName) {
            cancellations.add(uniqueWorkName);
            if (uniqueWorkName.equals(failAt) || ("cancel:" + uniqueWorkName).equals(failAt)) {
                throw new IllegalStateException("private token=do-not-store");
            }
            if (FirestoreSyncActivation.V3_WORK.equals(uniqueWorkName)) v3Queued = false;
        }
        @Override public void enqueuePeriodic(String uniqueWorkName, long intervalMinutes,
                                              boolean requireNetwork) {
            if ("enqueue".equals(failAt)) {
                throw new IllegalStateException("private token=do-not-store");
            }
            enqueueCount++;
            enqueuedName = uniqueWorkName;
            this.intervalMinutes = intervalMinutes;
            this.requireNetwork = requireNetwork;
            v3Queued = true;
        }
    }
}
