package it.vintedaffari.app;

import android.content.Context;
import android.util.AtomicFile;
import androidx.work.ListenableWorker;
import com.google.firebase.firestore.FirebaseFirestoreException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Properties;
import java.util.TimeZone;

/** Cross-process, local-only worker summary. It never stores catalog rows, IDs, hashes or exceptions. */
final class FirestoreWorkerDiagnostics {
    private static final String FILE_NAME = "firestore-worker-diagnostics-v1.properties";
    private static final long MAX_FILE_BYTES = 4096L;
    private FirestoreWorkerDiagnostics() {}

    static final class Record {
        long startedAt;
        long finishedAt;
        String result = "UNKNOWN";
        String stage = "UNKNOWN";
        String error = "NONE";
        long snapshotAt;
        String snapshotState = "NONE";
        long preparedRows;
        long uploadedRows;
    }

    static final class Run {
        private final AtomicFile file;
        private final Record record;
        private boolean resultSet;

        private Run(AtomicFile file, Record record) {
            this.file = file;
            this.record = record;
        }

        void progress(String stage) {
            record.stage = allowedStage(stage) ? stage : "UNKNOWN";
            persist(file, record);
        }

        void snapshot(String stage, FirestoreSnapshotStore.Snapshot snapshot) {
            record.stage = allowedStage(stage) ? stage : "UNKNOWN";
            if (snapshot != null && snapshot.state != null && !"PUBLISHED".equals(snapshot.state)) {
                record.snapshotAt = System.currentTimeMillis();
                record.snapshotState = allowedSnapshotState(snapshot.state) ? snapshot.state : "UNKNOWN";
                record.preparedRows = boundedCount(snapshot.count);
                record.uploadedRows = Math.min(record.preparedRows, boundedCount(snapshot.uploaded));
            }
            persist(file, record);
        }

        void published(String stage, FirestoreSnapshotStore.Snapshot snapshot) {
            record.stage = allowedStage(stage) ? stage : "UNKNOWN";
            if (snapshot != null) {
                record.snapshotAt = System.currentTimeMillis();
                record.snapshotState = "PUBLISHED";
                record.preparedRows = boundedCount(snapshot.count);
                record.uploadedRows = Math.min(record.preparedRows, boundedCount(snapshot.uploaded));
            }
            persist(file, record);
        }

        void error(String code) {
            record.error = allowedError(code) ? code : "WORKER_ERROR";
            persist(file, record);
        }

        void result(String outcome, String terminalStage) {
            record.result = allowedResult(outcome) ? outcome : "UNKNOWN";
            record.stage = allowedStage(terminalStage) ? terminalStage : "UNKNOWN";
            resultSet = true;
        }

        ListenableWorker.Result success() {
            record.result = "SUCCESS";
            resultSet = true;
            finish();
            return ListenableWorker.Result.success();
        }

        ListenableWorker.Result retry() {
            record.result = "RETRY";
            resultSet = true;
            finish();
            return ListenableWorker.Result.retry();
        }

        void finish() {
            if (!resultSet) {
                record.result = "FAILED";
                record.stage = "UNEXPECTED_EXIT";
                if ("NONE".equals(record.error)) record.error = "UNEXPECTED_WORKER_FAILURE";
            }
            record.finishedAt = System.currentTimeMillis();
            persist(file, record);
        }
    }

    static Run begin(Context context) {
        AtomicFile file = file(context);
        Record previous = read(file);
        Record record = previous == null ? new Record() : previous;
        record.startedAt = System.currentTimeMillis();
        record.finishedAt = 0L;
        record.result = "RUNNING";
        record.stage = "START";
        record.error = "NONE";
        persist(file, record);
        return new Run(file, record);
    }

    static String summary(Context context) {
        if (context == null) return "Riepilogo worker non disponibile.";
        Record record = read(file(context));
        return formatSummary(record, new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()));
    }

    static String safeFailureCode(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 6; depth++) {
            if (current instanceof FirebaseFirestoreException) {
                FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) current).getCode();
                return code == null ? "FIRESTORE_ERROR" : safeFirestoreErrorCode(code.name());
            }
            Throwable next = current.getCause();
            if (next == current) break;
            current = next;
        }
        return "RETRYABLE_WORKER_ERROR";
    }

    static String safeFirestoreErrorCode(String code) {
        if (code == null) return "FIRESTORE_ERROR";
        switch (code) {
            case "PERMISSION_DENIED": return "FIRESTORE_PERMISSION_DENIED";
            case "UNAUTHENTICATED": return "FIRESTORE_UNAUTHENTICATED";
            case "UNAVAILABLE": return "FIRESTORE_UNAVAILABLE";
            case "DEADLINE_EXCEEDED": return "FIRESTORE_TIMEOUT";
            case "NOT_FOUND": return "FIRESTORE_NOT_FOUND";
            default: return "FIRESTORE_ERROR";
        }
    }

    static String formatSummary(Record record, DateFormat formatter) {
        if (record == null || record.startedAt <= 0L) return "Nessuna esecuzione registrata da questa build.";
        StringBuilder out = new StringBuilder();
        out.append("Avvio: ").append(formatTime(formatter, record.startedAt));
        out.append("\nFine: ").append(formatTime(formatter, record.finishedAt));
        out.append("\nEsito worker: ").append(resultLabel(record.result));
        out.append("\nFase: ").append(stageLabel(record.stage));
        out.append("\nErrore: ").append(errorLabel(record.error));
        if (record.snapshotAt > 0L) {
            long prepared = boundedCount(record.preparedRows);
            long uploaded = Math.min(prepared, boundedCount(record.uploadedRows));
            out.append("\nUltimo snapshot locale: ").append(snapshotLabel(record.snapshotState))
                    .append("; righe preparate ").append(prepared)
                    .append("; righe inviate e confermate dal worker ").append(uploaded).append('/').append(prepared)
                    .append("; aggiornato ").append(formatTime(formatter, record.snapshotAt));
        } else {
            out.append("\nUltimo snapshot locale: nessuno registrato.");
        }
        out.append("\nRiepilogo locale: non conferma da solo lo stato remoto complete.");
        return out.toString();
    }

    private static String formatTime(DateFormat formatter, long at) {
        if (at <= 0L) return "non disponibile";
        formatter.setTimeZone(TimeZone.getDefault());
        return formatter.format(at);
    }

    private static AtomicFile file(Context context) {
        return new AtomicFile(new File(context.getNoBackupFilesDir(), FILE_NAME));
    }

    private static Record read(AtomicFile file) {
        try {
            Properties p = new Properties();
            try (FileInputStream in = file.openRead()) {
                if (in.available() > MAX_FILE_BYTES) return null;
                p.load(in);
            }
            Record r = new Record();
            r.startedAt = number(p.getProperty("started_at"));
            r.finishedAt = number(p.getProperty("finished_at"));
            r.result = allowedResult(p.getProperty("result")) ? p.getProperty("result") : "UNKNOWN";
            r.stage = allowedStage(p.getProperty("stage")) ? p.getProperty("stage") : "UNKNOWN";
            r.error = allowedError(p.getProperty("error")) ? p.getProperty("error") : "WORKER_ERROR";
            r.snapshotAt = number(p.getProperty("snapshot_at"));
            r.snapshotState = allowedSnapshotState(p.getProperty("snapshot_state")) ? p.getProperty("snapshot_state") : "UNKNOWN";
            r.preparedRows = boundedCount(number(p.getProperty("prepared_rows")));
            r.uploadedRows = Math.min(r.preparedRows, boundedCount(number(p.getProperty("uploaded_rows"))));
            return r;
        } catch (Exception ignored) { return null; }
    }

    private static void persist(AtomicFile file, Record r) {
        FileOutputStream out = null;
        try {
            Properties p = new Properties();
            p.setProperty("started_at", Long.toString(Math.max(0L, r.startedAt)));
            p.setProperty("finished_at", Long.toString(Math.max(0L, r.finishedAt)));
            p.setProperty("result", allowedResult(r.result) ? r.result : "UNKNOWN");
            p.setProperty("stage", allowedStage(r.stage) ? r.stage : "UNKNOWN");
            p.setProperty("error", allowedError(r.error) ? r.error : "WORKER_ERROR");
            p.setProperty("snapshot_at", Long.toString(Math.max(0L, r.snapshotAt)));
            p.setProperty("snapshot_state", allowedSnapshotState(r.snapshotState) ? r.snapshotState : "UNKNOWN");
            p.setProperty("prepared_rows", Long.toString(boundedCount(r.preparedRows)));
            p.setProperty("uploaded_rows", Long.toString(Math.min(boundedCount(r.preparedRows), boundedCount(r.uploadedRows))));
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            p.store(bytes, null);
            out = file.startWrite();
            out.write(bytes.toByteArray());
            out.flush();
            out.getFD().sync();
            file.finishWrite(out);
        } catch (Exception ignored) {
            if (out != null) try { file.failWrite(out); } catch (Exception alsoIgnored) { }
        }
    }

    private static long number(String value) {
        if (value == null || !value.matches("[0-9]{1,16}")) return 0L;
        try { return Long.parseLong(value); } catch (NumberFormatException ignored) { return 0L; }
    }

    private static long boundedCount(long value) {
        return Math.max(0L, Math.min(FirestoreSnapshotStore.MAX_ROWS, value));
    }

    private static boolean allowedResult(String value) {
        return "RUNNING".equals(value) || "SUCCESS".equals(value) || "RETRY".equals(value)
                || "FAILED".equals(value) || "UNKNOWN".equals(value);
    }

    private static boolean allowedStage(String value) {
        if (value == null) return false;
        switch (value) {
            case "START": case "CHECKS": case "LOAD_SNAPSHOT": case "MATERIALIZE": case "STAGE_MANIFEST":
            case "UPLOAD_BATCH": case "FINALIZE_MANIFEST": case "PUBLISH_CURRENT":
            case "CLEANUP": case "IDLE": case "LOCK_BUSY": case "BUDGET_LIMIT":
            case "CATALOG_MISSING": case "NOT_ACTIVE": case "NO_CHANGE":
            case "LOCAL_ERROR": case "RETRYABLE_ERROR": case "UNEXPECTED_EXIT":
            case "SNAPSHOT_PREPARED": case "MANIFEST_STAGED": case "BATCH_SENT":
            case "MANIFEST_COMPLETE": case "POINTER_PUBLISHED": case "ABORTED":
                return true;
            default: return false;
        }
    }

    private static boolean allowedError(String value) {
        return "NONE".equals(value) || "LOCAL_DATABASE_ERROR".equals(value)
                || "SNAPSHOT_VALIDATION_ERROR".equals(value) || "RETRYABLE_WORKER_ERROR".equals(value)
                || "UNEXPECTED_WORKER_FAILURE".equals(value) || "WORKER_ERROR".equals(value)
                || "FIRESTORE_PERMISSION_DENIED".equals(value) || "FIRESTORE_UNAUTHENTICATED".equals(value)
                || "FIRESTORE_UNAVAILABLE".equals(value) || "FIRESTORE_TIMEOUT".equals(value)
                || "FIRESTORE_NOT_FOUND".equals(value) || "FIRESTORE_ERROR".equals(value);
    }

    private static boolean allowedSnapshotState(String value) {
        return value != null && ("NONE".equals(value) || "UNKNOWN".equals(value)
                || "MATERIALIZING".equals(value) || "STAGE_MANIFEST".equals(value)
                || "UPLOADING".equals(value) || "MANIFEST".equals(value) || "PUBLISH".equals(value)
                || "CLEAN_OLD".equals(value) || "ABORTING".equals(value)
                || "ABORTING_SEALED".equals(value) || "PUBLISHED".equals(value));
    }

    private static String resultLabel(String value) {
        switch (value) {
            case "RUNNING": return "RUNNING (esito non ancora registrato)";
            case "SUCCESS": return "SUCCESS";
            case "RETRY": return "RETRY";
            case "FAILED": return "FAILED";
            default: return "non disponibile";
        }
    }

    private static String stageLabel(String value) {
        switch (value) {
            case "START": return "avvio";
            case "CHECKS": return "controlli iniziali";
            case "LOAD_SNAPSHOT": return "lettura stato snapshot locale";
            case "MATERIALIZE": return "preparazione snapshot locale";
            case "STAGE_MANIFEST": return "creazione manifest iniziale";
            case "UPLOAD_BATCH": return "invio batch";
            case "FINALIZE_MANIFEST": return "completamento manifest";
            case "PUBLISH_CURRENT": return "pubblicazione puntatore current";
            case "CLEANUP": return "pulizia snapshot precedente";
            case "IDLE": return "nessuna modifica da inviare";
            case "LOCK_BUSY": return "worker già in esecuzione";
            case "BUDGET_LIMIT": return "limite giornaliero";
            case "CATALOG_MISSING": return "archivio locale assente";
            case "NOT_ACTIVE": return "opt-in non attivo";
            case "NO_CHANGE": return "contenuto invariato";
            case "LOCAL_ERROR": return "errore locale";
            case "RETRYABLE_ERROR": return "errore ritentabile";
            case "SNAPSHOT_PREPARED": return "snapshot preparato";
            case "MANIFEST_STAGED": return "manifest iniziale scritto";
            case "BATCH_SENT": return "batch inviato";
            case "MANIFEST_COMPLETE": return "manifest completato";
            case "POINTER_PUBLISHED": return "puntatore pubblicato";
            case "ABORTED": return "snapshot annullato";
            case "UNEXPECTED_EXIT": return "uscita senza esito";
            default: return "non disponibile";
        }
    }

    private static String errorLabel(String value) {
        switch (value) {
            case "NONE": return "nessuno registrato";
            case "LOCAL_DATABASE_ERROR": return "errore database snapshot locale (dettagli esclusi)";
            case "SNAPSHOT_VALIDATION_ERROR": return "errore validazione snapshot (dettagli esclusi)";
            case "RETRYABLE_WORKER_ERROR": return "errore ritentabile (dettagli esclusi)";
            case "FIRESTORE_PERMISSION_DENIED": return "permesso Firestore negato (dettagli esclusi)";
            case "FIRESTORE_UNAUTHENTICATED": return "autenticazione Firestore rifiutata (dettagli esclusi)";
            case "FIRESTORE_UNAVAILABLE": return "Firestore non disponibile (dettagli esclusi)";
            case "FIRESTORE_TIMEOUT": return "timeout Firestore (dettagli esclusi)";
            case "FIRESTORE_NOT_FOUND": return "risorsa Firestore non trovata (dettagli esclusi)";
            case "FIRESTORE_ERROR": return "errore Firestore (dettagli esclusi)";
            case "UNEXPECTED_WORKER_FAILURE": return "worker terminato senza esito (dettagli esclusi)";
            default: return "errore worker (dettagli esclusi)";
        }
    }

    private static String snapshotLabel(String value) {
        switch (value) {
            case "MATERIALIZING": return "preparazione in corso";
            case "STAGE_MANIFEST": return "preparato, manifest iniziale da scrivere";
            case "UPLOADING": return "invio in corso";
            case "MANIFEST": return "righe inviate, manifest da completare";
            case "PUBLISH": return "manifest completo, puntatore da pubblicare";
            case "CLEAN_OLD": return "current pubblicato, pulizia precedente";
            case "PUBLISHED": return "pubblicato";
            case "ABORTING": case "ABORTING_SEALED": return "annullato/in pulizia";
            case "NONE": return "nessuno";
            default: return "stato non disponibile";
        }
    }
}
