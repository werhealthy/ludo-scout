package it.vintedaffari.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public final class FirestoreWorkManagerDiagnosticsTest {
    @Test public void emptyResultSeparatesOptInFromScheduledWork() {
        String summary = FirestoreWorkManagerDiagnostics.format(Collections.emptyList());

        assertTrue(summary.contains("Nessuna richiesta WorkManager registrata"));
        assertTrue(summary.contains("opt-in ‘Invio automatico’ da solo non prova"));
    }

    @Test public void activeRecordShowsIdStateAttemptsAndDeclaredConstraints() {
        String summary = FirestoreWorkManagerDiagnostics.format(Collections.singletonList(
                new FirestoreWorkManagerDiagnostics.Entry(
                        "123e4567-e89b-12d3-a456-426614174000", "ENQUEUED", 2,
                        "CONNECTED", true, false, false, false, 1_797_000_000_000L)));

        assertTrue(summary.contains("Richiesta WorkManager presente"));
        assertTrue(summary.contains("ID: 123e4567-e89b-12d3-a456-426614174000"));
        assertTrue(summary.contains("Stato: ENQUEUED"));
        assertTrue(summary.contains("Tentativi: 2"));
        assertTrue(summary.contains("rete=CONNECTED"));
        assertTrue(summary.contains("ricarica=richiesta"));
        assertTrue(summary.contains("batteria non bassa=non richiesta"));
        assertTrue(summary.contains("dispositivo inattivo=non richiesta"));
        assertTrue(summary.contains("spazio disponibile=non richiesta"));
        assertTrue(summary.contains("WorkManager non espone qui il motivo puntuale dell’attesa"));
        assertTrue(summary.contains("Prima eleggibilità WorkManager:"));
    }

    @Test public void onlyTerminalRecordsAreNotReportedAsScheduled() {
        String summary = FirestoreWorkManagerDiagnostics.format(Collections.singletonList(
                new FirestoreWorkManagerDiagnostics.Entry(
                        "123e4567-e89b-12d3-a456-426614174000", "CANCELLED", 0,
                        "CONNECTED", false, false, false, false, 0L)));

        assertTrue(summary.contains("Nessuna richiesta WorkManager attiva"));
        assertTrue(summary.contains("Stato: CANCELLED"));
    }

    @Test public void unsafeFieldsAreSanitized() {
        String summary = FirestoreWorkManagerDiagnostics.format(Arrays.asList(
                new FirestoreWorkManagerDiagnostics.Entry(
                        "token=secret", "https://bad.invalid", -4, "credential=secret",
                        false, false, false, false, 0L)));

        assertTrue(summary.contains("ID: non disponibile"));
        assertTrue(summary.contains("Stato: non disponibile"));
        assertTrue(summary.contains("Tentativi: 0"));
        assertTrue(summary.contains("rete=non disponibile"));
        assertFalse(summary.contains("secret"));
        assertFalse(summary.contains("bad.invalid"));
    }
}
