package it.vintedaffari.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class FirestoreActivationDiagnosticsTest {
    @Test public void activationSummaryKeepsStageAndTypeButOmitsExceptionMessage() {
        String summary = FirestoreActivationDiagnostics.summary(
                "enqueue_v3", new IllegalStateException("token=private-value email=person@example.test"));

        assertTrue(summary.contains("stage=enqueue_v3"));
        assertTrue(summary.contains("exception=java.lang.IllegalStateException"));
        assertFalse(summary.contains("private-value"));
        assertFalse(summary.contains("person@example.test"));
        assertFalse(summary.contains("token="));
    }

    @Test public void crashJournalSummaryOmitsMessageAndStack() {
        String summary = ProcessCrashJournal.safeCrashSummary(1_797_000_000_000L,
                "it.vintedaffari.app:ui", "UNCAUGHT",
                "java.lang.IllegalStateException: token=private-value\nstack=private-stack");

        assertTrue(summary.contains("process=it.vintedaffari.app_ui"));
        assertTrue(summary.contains("phase=UNCAUGHT"));
        assertTrue(summary.contains("exception=java.lang.IllegalStateException"));
        assertFalse(summary.contains("private-value"));
        assertFalse(summary.contains("private-stack"));
        assertFalse(summary.contains("token="));
    }
}
