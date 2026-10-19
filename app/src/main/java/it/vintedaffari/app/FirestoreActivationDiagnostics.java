package it.vintedaffari.app;

/** Produces bounded activation diagnostics without exception messages or application data. */
final class FirestoreActivationDiagnostics {
    private FirestoreActivationDiagnostics() {}

    static String summary(String stage, Throwable failure) {
        String safeStage = allowedStage(stage) ? stage : "unknown";
        String type = failure == null ? "UnknownException" : failure.getClass().getName();
        if (type == null || !type.matches("[A-Za-z0-9_.$]{1,100}")) type = "UnknownException";
        StringBuilder result = new StringBuilder("stage=").append(safeStage).append(";exception=").append(type);
        Throwable root = failure;
        for (int depth = 0; root != null && root.getCause() != null && root.getCause() != root && depth < 8; depth++) {
            root = root.getCause();
        }
        if (root != null && root != failure) {
            String cause = root.getClass().getName();
            if (cause.matches("[A-Za-z0-9_.$]{1,100}")) result.append(";cause=").append(cause);
        }
        return result.toString();
    }

    private static boolean allowedStage(String stage) {
        if (stage == null) return false;
        switch (stage) {
            case "owner_authorization":
            case "executor_submit":
            case "workmanager_init":
            case "request_build":
            case "pause_state_commit":
            case "cancel_v1":
            case "cancel_v2":
            case "cancel_v3":
            case "enqueue_v3":
            case "active_state_commit":
            case "activation_failed":
                return true;
            default:
                return false;
        }
    }
}
