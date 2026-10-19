package it.vintedaffari.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import java.text.SimpleDateFormat;
import java.util.Locale;
import org.junit.Test;

public final class FirestoreWorkerDiagnosticsTest {
    @Test public void summaryShowsWorkerOutcomeAndAcknowledgedSnapshotCountsWithoutPayload() {
        FirestoreWorkerDiagnostics.Record record = new FirestoreWorkerDiagnostics.Record();
        record.startedAt = 1_797_000_000_000L;
        record.finishedAt = record.startedAt + 12_000L;
        record.result = "RETRY";
        record.stage = "UPLOAD_BATCH";
        record.error = "FIRESTORE_UNAVAILABLE";
        record.operation = "COMMIT_UPLOAD_BATCH";
        record.snapshotAt = record.startedAt + 5_000L;
        record.snapshotState = "UPLOADING";
        record.preparedRows = 100;
        record.uploadedRows = 80;

        String summary = FirestoreWorkerDiagnostics.formatSummary(
                record, new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ITALY));

        assertTrue(summary.contains("Avvio:"));
        assertTrue(summary.contains("Fine:"));
        assertTrue(summary.contains("Esito worker: RETRY"));
        assertTrue(summary.contains("Fase: invio batch"));
        assertTrue(summary.contains("Operazione: commit batch Firestore"));
        assertTrue(summary.contains("Errore: Firestore non disponibile"));
        assertTrue(summary.contains("FIRESTORE_UNAVAILABLE"));
        assertTrue(summary.contains("righe preparate 100"));
        assertTrue(summary.contains("righe inviate e confermate dal worker 80/100"));
        assertTrue(summary.contains("non conferma da solo lo stato remoto complete"));
        assertFalse(summary.contains("snapshot_id"));
        assertFalse(summary.contains("content_hash"));
    }

    @Test public void malformedDiagnosticValuesAreReducedToAllowlistedLabels() {
        FirestoreWorkerDiagnostics.Record record = new FirestoreWorkerDiagnostics.Record();
        record.startedAt = 1_797_000_000_000L;
        record.result = "token=do-not-show";
        record.stage = "https://private.invalid/path";
        record.error = "email=person@example.test";
        record.operation = "token=do-not-show";
        record.snapshotState = "secret-state";
        record.preparedRows = 5;
        record.uploadedRows = 99;
        record.snapshotAt = record.startedAt;

        String summary = FirestoreWorkerDiagnostics.formatSummary(
                record, new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ITALY));

        assertTrue(summary.contains("Esito worker: non disponibile"));
        assertTrue(summary.contains("Fase: non disponibile"));
        assertTrue(summary.contains("Operazione: non disponibile"));
        assertTrue(summary.contains("Errore: errore worker (dettagli esclusi)"));
        assertTrue(summary.contains("stato non disponibile"));
        assertTrue(summary.contains("righe inviate e confermate dal worker 5/5"));
        assertFalse(summary.contains("do-not-show"));
        assertFalse(summary.contains("private.invalid"));
        assertFalse(summary.contains("person@example.test"));
    }

    @Test public void missingRunDoesNotInferActivityFromOptIn() {
        assertTrue(FirestoreWorkerDiagnostics.formatSummary(null,
                new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ITALY))
                .contains("Nessuna esecuzione registrata da questa build"));
    }

    @Test public void firestorePermissionCodeIsAllowlistedAndRenderedWithoutRawMessage() {
        String error = FirestoreWorkerDiagnostics.safeFirestoreErrorCode("PERMISSION_DENIED");
        assertEquals("FIRESTORE_PERMISSION_DENIED", error);
        assertEquals("FIRESTORE_ERROR", FirestoreWorkerDiagnostics.safeFirestoreErrorCode("token=secret"));
        FirestoreWorkerDiagnostics.Record record = new FirestoreWorkerDiagnostics.Record();
        record.startedAt = 1_797_000_000_000L;
        record.error = error;
        String summary = FirestoreWorkerDiagnostics.formatSummary(record,
                new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ITALY));
        assertTrue(summary.contains("permesso Firestore negato"));
    }

    @Test public void firestoreStatusCodesAreAllowlistedAndUnknownInputIsDiscarded() {
        assertEquals("FIRESTORE_RESOURCE_EXHAUSTED",
                FirestoreWorkerDiagnostics.safeFirestoreErrorCode("RESOURCE_EXHAUSTED"));
        assertEquals("FIRESTORE_FAILED_PRECONDITION",
                FirestoreWorkerDiagnostics.safeFirestoreErrorCode("FAILED_PRECONDITION"));
        assertEquals("FIRESTORE_ERROR",
                FirestoreWorkerDiagnostics.safeFirestoreErrorCode("token=secret"));
    }

    @Test public void localPayloadAndArgumentFailuresUseSanitizedCodesAndOperations() {
        assertEquals("WORKER_PAYLOAD_DECODE_ERROR",
                FirestoreWorkerDiagnostics.safeFailureCode(new org.json.JSONException("private payload")));
        assertEquals("WORKER_INVALID_ARGUMENT",
                FirestoreWorkerDiagnostics.safeFailureCode(new IllegalArgumentException("token=private")));
        assertEquals("WORKER_RUNTIME_ERROR",
                FirestoreWorkerDiagnostics.safeFailureCode(new RuntimeException("private SDK detail")));

        FirestoreWorkerDiagnostics.Record record = new FirestoreWorkerDiagnostics.Record();
        record.startedAt = 1_797_000_000_000L;
        record.result = "RETRY";
        record.stage = "UPLOAD_BATCH";
        record.operation = "BUILD_UPLOAD_BATCH";
        record.error = "WORKER_INVALID_ARGUMENT";
        String summary = FirestoreWorkerDiagnostics.formatSummary(record,
                new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ITALY));
        assertTrue(summary.contains("costruzione batch Firestore"));
        assertTrue(summary.contains("WORKER_INVALID_ARGUMENT"));
        assertFalse(summary.contains("token=private"));
        assertFalse(summary.contains("private payload"));
    }
}
