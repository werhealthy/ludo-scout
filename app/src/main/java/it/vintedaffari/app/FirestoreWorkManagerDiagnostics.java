package it.vintedaffari.app;

import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.WorkInfo;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

/** Read-only, local summary of the WorkManager request for the Firestore sync. */
final class FirestoreWorkManagerDiagnostics {
    private FirestoreWorkManagerDiagnostics() {}

    static final class Entry {
        final String id;
        final String state;
        final int attempts;
        final String network;
        final boolean charging;
        final boolean batteryNotLow;
        final boolean deviceIdle;
        final boolean storageNotLow;
        final long nextScheduleTimeMillis;

        Entry(String id, String state, int attempts, String network, boolean charging,
              boolean batteryNotLow, boolean deviceIdle, boolean storageNotLow,
              long nextScheduleTimeMillis) {
            this.id = id;
            this.state = state;
            this.attempts = attempts;
            this.network = network;
            this.charging = charging;
            this.batteryNotLow = batteryNotLow;
            this.deviceIdle = deviceIdle;
            this.storageNotLow = storageNotLow;
            this.nextScheduleTimeMillis = nextScheduleTimeMillis;
        }
    }

    static Entry from(WorkInfo info) {
        if (info == null) return null;
        Constraints c = info.getConstraints();
        UUID id = info.getId();
        String state = state(info.getState());
        long nextSchedule = "ENQUEUED".equals(state) ? info.getNextScheduleTimeMillis() : 0L;
        return new Entry(id == null ? "" : id.toString(), state,
                Math.max(0, info.getRunAttemptCount()), network(c.getRequiredNetworkType()),
                c.requiresCharging(), c.requiresBatteryNotLow(), c.requiresDeviceIdle(),
                c.requiresStorageNotLow(), nextSchedule);
    }

    static String summary(List<WorkInfo> work) {
        if (work == null) return "Stato WorkManager non disponibile: lettura locale non riuscita.";
        java.util.ArrayList<Entry> entries = new java.util.ArrayList<>();
        for (WorkInfo info : work) {
            Entry entry = from(info);
            if (entry != null) entries.add(entry);
        }
        return format(entries);
    }

    static String format(List<Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            return "Nessuna richiesta WorkManager registrata per la sync Firestore. "
                    + "L’opt-in ‘Invio automatico’ da solo non prova che il lavoro sia pianificato.";
        }
        int active = 0;
        for (Entry entry : entries) if (isNonTerminal(entry.state)) active++;
        StringBuilder out = new StringBuilder();
        if (active > 0) {
            out.append("Richiesta WorkManager presente; richieste non terminali: ").append(active)
                    .append(".");
        } else {
            out.append("Nessuna richiesta WorkManager attiva; presenti solo record terminali: ")
                    .append(entries.size()).append(".");
        }
        for (Entry entry : entries) {
            out.append("\nID: ").append(safeId(entry.id))
                    .append("\nStato: ").append(safeState(entry.state))
                    .append("\nTentativi: ").append(Math.max(0, entry.attempts))
                    .append("\nVincoli dichiarati: rete=").append(safeNetwork(entry.network))
                    .append("; ricarica=").append(required(entry.charging))
                    .append("; batteria non bassa=").append(required(entry.batteryNotLow))
                    .append("; dispositivo inattivo=").append(required(entry.deviceIdle))
                    .append("; spazio disponibile=").append(required(entry.storageNotLow)).append('.');
            if ("ENQUEUED".equals(entry.state)) {
                if (entry.nextScheduleTimeMillis > 0L
                        && entry.nextScheduleTimeMillis < Long.MAX_VALUE) {
                    DateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
                    formatter.setTimeZone(TimeZone.getDefault());
                    out.append("\nPrima eleggibilità WorkManager: ")
                            .append(formatter.format(new Date(entry.nextScheduleTimeMillis)))
                            .append(" (può essere rinviato dal sistema).");
                } else {
                    out.append("\nProssima eleggibilità WorkManager: non disponibile.");
                }
                out.append("\nIn coda: WorkManager non espone qui il motivo puntuale dell’attesa corrente.");
            } else if ("BLOCKED".equals(entry.state)) {
                out.append("\nBloccato: in attesa di una dipendenza precedente.");
            }
        }
        out.append("\nQuesti sono i record WorkManager e i vincoli richiesti; non attestano "
                + "che JobScheduler abbia già registrato il job o che il sistema lo avvii subito.");
        return out.toString();
    }

    static String unavailable() {
        return "Stato WorkManager non disponibile: lettura locale non riuscita.";
    }

    private static boolean isNonTerminal(String state) {
        return "ENQUEUED".equals(state) || "RUNNING".equals(state) || "BLOCKED".equals(state);
    }

    private static String state(WorkInfo.State state) {
        if (state == null) return "UNKNOWN";
        switch (state) {
            case ENQUEUED: return "ENQUEUED";
            case RUNNING: return "RUNNING";
            case BLOCKED: return "BLOCKED";
            case SUCCEEDED: return "SUCCEEDED";
            case FAILED: return "FAILED";
            case CANCELLED: return "CANCELLED";
            default: return "UNKNOWN";
        }
    }

    private static String safeState(String state) {
        if ("ENQUEUED".equals(state) || "RUNNING".equals(state) || "BLOCKED".equals(state)
                || "SUCCEEDED".equals(state) || "FAILED".equals(state) || "CANCELLED".equals(state)) {
            return state;
        }
        return "non disponibile";
    }

    private static String network(NetworkType type) {
        if (type == null) return "UNKNOWN";
        switch (type) {
            case NOT_REQUIRED: return "NOT_REQUIRED";
            case CONNECTED: return "CONNECTED";
            case UNMETERED: return "UNMETERED";
            case NOT_ROAMING: return "NOT_ROAMING";
            case METERED: return "METERED";
            case TEMPORARILY_UNMETERED: return "TEMPORARILY_UNMETERED";
            default: return "UNKNOWN";
        }
    }

    private static String safeNetwork(String network) {
        if ("NOT_REQUIRED".equals(network) || "CONNECTED".equals(network)
                || "UNMETERED".equals(network) || "NOT_ROAMING".equals(network)
                || "METERED".equals(network) || "TEMPORARILY_UNMETERED".equals(network)) {
            return network;
        }
        return "non disponibile";
    }

    private static String safeId(String id) {
        if (id != null && id.matches("(?i)[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")) return id;
        return "non disponibile";
    }

    private static String required(boolean value) { return value ? "richiesta" : "non richiesta"; }
}
