package it.vintedaffari.app;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Source-level assertions for consent revocation; never opens app data or Firebase. */
final class FirestoreSyncSourceContract {
    private FirestoreSyncSourceContract() {}


    static boolean sourceDisconnectClearsBothConsents() {
        try {
            Path root = Path.of("").toAbsolutePath();
            while (root != null && !Files.isDirectory(root.resolve("app/src/main"))) root = root.getParent();
            if (root == null) return false;
            String text = new String(Files.readAllBytes(root.resolve("app/src/main/java/it/vintedaffari/app/FirestoreSync.java")), StandardCharsets.UTF_8);
            int start = text.indexOf("public static void stop(");
            int end = text.indexOf("static SharedPreferences progress(", start);
            if (start < 0 || end < 0) return false;
            String method = text.substring(start, end);
            return method.contains("putBoolean(\"enabled\", false)") && method.contains("remove(\"opt_in_version\")")
                    && method.contains("putBoolean(\"evidence_enabled\", false)")
                    && method.contains("remove(\"evidence_opt_in_version\")");
        } catch (Exception unavailable) { return false; }
    }

    static boolean writerProjectionIsConsentGated() {
        try {
            Path root = Path.of("").toAbsolutePath();
            while (root != null && !Files.isDirectory(root.resolve("app/src/main"))) root = root.getParent();
            if (root == null) return false;
            String worker = new String(Files.readAllBytes(root.resolve("app/src/main/java/it/vintedaffari/app/FirestoreSnapshotWorker.java")), StandardCharsets.UTF_8);
            int start = worker.indexOf("private static Row project(");
            int end = worker.indexOf("private static String sourceHash(", start);
            if (start < 0 || end < 0) return false;
            String project = worker.substring(start, end);
            return project.contains("includeEvidence") && project.contains("observed_text")
                    && project.contains("photo_urls") && project.contains("if(includeEvidence)");
        } catch (Exception unavailable) { return false; }
    }

    static boolean legacySidecarIsCleanedBeforeMigration() {
        try {
            Path root = Path.of("").toAbsolutePath();
            while (root != null && !Files.isDirectory(root.resolve("app/src/main"))) root = root.getParent();
            if (root == null) return false;
            String store = new String(Files.readAllBytes(root.resolve("app/src/main/java/it/vintedaffari/app/FirestoreSnapshotStore.java")), StandardCharsets.UTF_8);
            String worker = new String(Files.readAllBytes(root.resolve("app/src/main/java/it/vintedaffari/app/FirestoreSnapshotWorker.java")), StandardCharsets.UTF_8);
            int open = store.indexOf("private FirestoreSnapshotStore(Context context)");
            int reset = store.indexOf("void resetIdle()", open);
            if (open < 0 || reset < 0) return false;
            String constructor = store.substring(open, reset);
            if (constructor.contains("metadata.getInt(1) < 5") || constructor.contains("DELETE FROM snapshot_rows")) return false;
            int legacy = worker.indexOf("state.protocolVersion!=SNAPSHOT_PROTOCOL_VERSION");
            int abort = worker.indexOf("store.markAborted();state=store.snapshot();", legacy);
            int cleanup = worker.indexOf("return cleanupAborted(", abort);
            int newGeneration = worker.indexOf("store.begin(FirestoreSnapshotStore.newId()", legacy);
            return legacy >= 0 && abort > legacy && cleanup > abort && newGeneration > cleanup;
        } catch (Exception unavailable) { return false; }
    }

    static boolean evidenceConsentDoesNotMigrateFromMetadata() {
        try {
            Path root = Path.of("").toAbsolutePath();
            while (root != null && !Files.isDirectory(root.resolve("app/src/main"))) root = root.getParent();
            if (root == null) return false;
            String source = new String(Files.readAllBytes(root.resolve("app/src/main/java/it/vintedaffari/app/FirestoreSync.java")), StandardCharsets.UTF_8);
            int start = source.indexOf("public static boolean evidenceConsented(");
            int end = source.indexOf("public static boolean evidenceEnabled(", start);
            if (start < 0 || end < 0) return false;
            String method = source.substring(start, end);
            return method.contains("evidence_enabled") && method.contains("evidence_opt_in_version")
                    && !method.contains("\"opt_in_version\"");
        } catch (Exception unavailable) { return false; }
    }

    static boolean evidenceRevocationScrubsBeforeConsentCommit() {
        try {
            Path root = Path.of("").toAbsolutePath();
            while (root != null && !Files.isDirectory(root.resolve("app/src/main"))) root = root.getParent();
            if (root == null) return false;
            String source = new String(Files.readAllBytes(root.resolve("app/src/main/java/it/vintedaffari/app/FirestoreSync.java")), StandardCharsets.UTF_8);
            int start = source.indexOf("public static boolean setEvidenceConsent(");
            int end = source.indexOf("static boolean commitEvidenceConsent(", start);
            if (start < 0 || end < 0) return false;
            String method = source.substring(start, end);
            int scrub = method.indexOf("scrubEvidenceSidecar(context)");
            int persist = method.indexOf("commitEvidenceConsent(preferences, enabled, scrubbed)");
            int failClosed = source.indexOf("edit.putBoolean(\"evidence_enabled\", false)", end);
            return method.contains("synchronized (FirestoreSync.class)") && scrub >= 0 && persist > scrub
                    && failClosed > end && source.contains("evidence_revoke_local_scrub_failed");
        } catch (Exception unavailable) { return false; }
    }

    static boolean sidecarRevocationIsFailClosed() {
        try {
            Path root = Path.of("").toAbsolutePath();
            while (root != null && !Files.isDirectory(root.resolve("app/src/main"))) root = root.getParent();
            if (root == null) return false;
            String store = new String(Files.readAllBytes(root.resolve("app/src/main/java/it/vintedaffari/app/FirestoreSnapshotStore.java")), StandardCharsets.UTF_8);
            String worker = new String(Files.readAllBytes(root.resolve("app/src/main/java/it/vintedaffari/app/FirestoreSnapshotWorker.java")), StandardCharsets.UTF_8);
            String[] required = {"void resetIdle() {", "protocol_version INTEGER NOT NULL DEFAULT 2", "protocol_version=6", "evidence_revoke_local_scrub_failed"};
            for (String token : required) if (!store.contains(token)) return false;
            if (store.indexOf("void resetIdle() {") != store.lastIndexOf("void resetIdle() {")) return false;
            int scrubStart = store.indexOf("private void clearExistingEvidenceRows()");
            int scrubEnd = store.indexOf("private static String canonical(", scrubStart);
            if (scrubStart < 0 || scrubEnd < 0) return false;
            String scrub = store.substring(scrubStart, scrubEnd);
            if (!scrub.contains("observed_text") || !scrub.contains("photo_urls")) return false;
            int attempted = worker.indexOf("store.markUploadAttempted(done)");
            int gate = worker.indexOf("commitIfEvidenceModeMatches(", attempted);
            int submit = worker.indexOf("batch::commit", gate);
            int await = worker.indexOf("Tasks.await(uploadTask", submit);
            return attempted >= 0 && gate > attempted && submit > gate && await > submit
                    && worker.contains("if(!evidenceModeMatches(metadataEnabled,evidenceEnabled,state))return null;");
        } catch (Exception unavailable) { return false; }
    }

}