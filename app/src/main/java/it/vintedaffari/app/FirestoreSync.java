package it.vintedaffari.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.multiprocess.RemoteWorkManager;
import android.util.AtomicFile;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Opt-in, owner-scoped transport. Never contacts Firebase if build configuration is absent. */
public final class FirestoreSync {
    private static final String APP = "ludo-catalog-sync";
    private static final String WORK = FirestoreSyncActivation.V3_WORK;
    private static final String LEGACY_WORK = FirestoreSyncActivation.LEGACY_WORK;
    private static final String V2_WORK = FirestoreSyncActivation.V2_WORK;
    private static final String OPT_IN_VERSION = "minimal-v3";
    private static final String PREFS = "ludo_firestore_sync";
    private static final String ACTIVATION_DIAGNOSTIC = "activation_diagnostic";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService ACTIVATION_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ludo-firestore-activation");
        thread.setDaemon(true);
        return thread;
    });
    private FirestoreSync() {}

    public interface ActivationCallback { void onComplete(boolean active); }

    public static boolean configured() {
        return !BuildConfig.LUDO_FIREBASE_APP_ID.isEmpty()
                && !BuildConfig.LUDO_FIREBASE_API_KEY.isEmpty()
                && !BuildConfig.LUDO_FIREBASE_PROJECT_ID.isEmpty()
                && !BuildConfig.LUDO_SYNC_OWNER_UID.isEmpty();
    }

    public static synchronized FirebaseApp app(Context context) {
        if (!configured()) return null;
        try { return FirebaseApp.getInstance(APP); }
        catch (IllegalStateException absent) {
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setApplicationId(BuildConfig.LUDO_FIREBASE_APP_ID)
                    .setApiKey(BuildConfig.LUDO_FIREBASE_API_KEY)
                    .setProjectId(BuildConfig.LUDO_FIREBASE_PROJECT_ID)
                    .build();
            return FirebaseApp.initializeApp(context.getApplicationContext(), options, APP);
        }
    }

    public static FirebaseAuth auth(Context context) {
        FirebaseApp app = app(context);
        return app == null ? null : FirebaseAuth.getInstance(app);
    }

    public static FirebaseFirestore store(Context context) {
        FirebaseApp app = app(context);
        return app == null ? null : FirebaseFirestore.getInstance(app);
    }

    public static boolean authorized(Context context) {
        FirebaseAuth auth = auth(context);
        return auth != null && auth.getCurrentUser() != null
                && BuildConfig.LUDO_SYNC_OWNER_UID.equals(auth.getCurrentUser().getUid());
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context context) {
        SharedPreferences p = prefs(context);
        return "minimal-v3".equals(p.getString("opt_in_version", ""))
                && p.getBoolean("enabled", false) && authorized(context);
    }

    public static void start(Context context, ActivationCallback callback) {
        Context appContext = context.getApplicationContext();
        SharedPreferences diagnosticPrefs = prefs(appContext);
        try {
            if (!authorized(appContext)) {
                recordActivationFailure(diagnosticPrefs, "owner_authorization",
                        new SecurityException("owner_not_authorized"));
                deliver(callback, false);
                return;
            }
        } catch (RuntimeException failure) {
            recordActivationFailure(diagnosticPrefs, "owner_authorization", failure);
            deliver(callback, false);
            return;
        }
        try {
            if (!new PreferenceActivationState(diagnosticPrefs).pause()) {
                recordActivationFailure(diagnosticPrefs, "pause_state_commit",
                        new IllegalStateException("activation_pause_commit_failed"));
                deliver(callback, false);
                return;
            }
        } catch (RuntimeException failure) {
            recordActivationFailure(diagnosticPrefs, "pause_state_commit", failure);
            deliver(callback, false);
            return;
        }
        diagnosticPrefs.edit().remove(ACTIVATION_DIAGNOSTIC).apply();
        try {
            ACTIVATION_EXECUTOR.execute(() -> {
                SharedPreferences preferences = prefs(appContext);
                PreferenceActivationState state = new PreferenceActivationState(preferences);
                FirestoreSyncActivation.Result result = FirestoreSyncActivation.Result.FAILED;
                String stage = "workmanager_init";
                try {
                    RemoteWorkManager manager = RemoteWorkManager.getInstance(appContext);
                    stage = "request_build";
                    Constraints network = new Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED).build();
                    PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                            FirestoreSnapshotWorker.class, FirestoreSyncActivation.INTERVAL_MINUTES,
                            TimeUnit.MINUTES).setConstraints(network).build();
                    stage = "pause_state_commit";
                    result = FirestoreSyncActivation.activate(state,
                            new WorkManagerActivationScheduler(manager, request),
                            (failureStage, failure) -> recordActivationFailure(preferences, failureStage, failure));
                } catch (Exception failure) {
                    try { state.pause(); } catch (RuntimeException ignored) { }
                    recordActivationFailure(preferences, stage, failure);
                }
                if (result == FirestoreSyncActivation.Result.ACTIVATED) {
                    preferences.edit().remove(ACTIVATION_DIAGNOSTIC).apply();
                } else if (preferences.getString(ACTIVATION_DIAGNOSTIC, "").isEmpty()) {
                    recordActivationFailure(preferences, "activation_failed",
                            new IllegalStateException("activation_failed"));
                }
                deliver(callback, result == FirestoreSyncActivation.Result.ACTIVATED);
            });
        } catch (RuntimeException rejected) {
            try { new PreferenceActivationState(prefs(appContext)).pause(); }
            catch (RuntimeException ignored) { }
            recordActivationFailure(diagnosticPrefs, "executor_submit", rejected);
            deliver(callback, false);
        }
    }

    public static String activationDiagnostic(Context context) {
        try { return prefs(context).getString(ACTIVATION_DIAGNOSTIC, ""); }
        catch (RuntimeException ignored) { return ""; }
    }

    private static void recordActivationFailure(SharedPreferences preferences, String stage,
                                                Throwable failure) {
        try {
            preferences.edit().putString(ACTIVATION_DIAGNOSTIC,
                    FirestoreActivationDiagnostics.summary(stage, failure)).commit();
        } catch (RuntimeException ignored) { }
    }

    private static void deliver(ActivationCallback callback, boolean active) {
        if (callback != null) MAIN.post(() -> callback.onComplete(active));
    }

    private static final class PreferenceActivationState implements FirestoreSyncActivation.StateStore {
        private final SharedPreferences preferences;
        PreferenceActivationState(SharedPreferences preferences) { this.preferences = preferences; }
        @Override public boolean pause() {
            return preferences.edit().putBoolean("enabled", false).remove("opt_in_version").commit();
        }
        @Override public boolean activate() {
            return preferences.edit().putString("opt_in_version", OPT_IN_VERSION)
                    .putBoolean("enabled", true).commit();
        }
        @Override public boolean isActive() {
            return OPT_IN_VERSION.equals(preferences.getString("opt_in_version", ""))
                    && preferences.getBoolean("enabled", false);
        }
    }

    private static final class WorkManagerActivationScheduler implements FirestoreSyncActivation.Scheduler {
        private final RemoteWorkManager manager;
        private final PeriodicWorkRequest request;
        WorkManagerActivationScheduler(RemoteWorkManager manager, PeriodicWorkRequest request) {
            this.manager = manager;
            this.request = request;
        }
        @Override public void cancel(String uniqueWorkName) throws Exception {
            if (LEGACY_WORK.equals(uniqueWorkName)) await(manager.cancelUniqueWork(LEGACY_WORK));
            else if (V2_WORK.equals(uniqueWorkName)) await(manager.cancelUniqueWork(V2_WORK));
            else if (WORK.equals(uniqueWorkName)) await(manager.cancelUniqueWork(WORK));
            else throw new IllegalArgumentException("firestore_sync_work_name_not_allowed");
        }
        @Override public void enqueuePeriodic(String uniqueWorkName, long intervalMinutes,
                                              boolean requireNetwork) throws Exception {
            if (!FirestoreSyncActivation.V3_WORK.equals(uniqueWorkName)
                    || intervalMinutes != FirestoreSyncActivation.INTERVAL_MINUTES || !requireNetwork) {
                throw new IllegalArgumentException("firestore_sync_schedule_contract_mismatch");
            }
            await(manager.enqueueUniquePeriodicWork(uniqueWorkName,
                    ExistingPeriodicWorkPolicy.REPLACE, request));
        }
        private static void await(ListenableFuture<?> operation) throws Exception {
            try {
                operation.get(30, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw interrupted;
            }
        }
    }

    public static void stop(Context context) {
        Context appContext = context.getApplicationContext();
        prefs(appContext).edit().putBoolean("enabled", false).remove("opt_in_version").apply();
        try {
            FirebaseAuth auth = auth(appContext);
            if (auth != null) auth.signOut();
        } catch (RuntimeException ignored) { }
        try {
            ACTIVATION_EXECUTOR.execute(() -> {
                try {
                    RemoteWorkManager manager = RemoteWorkManager.getInstance(appContext);
                    WorkManagerActivationScheduler.await(manager.cancelUniqueWork(WORK));
                    WorkManagerActivationScheduler.await(manager.cancelUniqueWork(V2_WORK));
                    WorkManagerActivationScheduler.await(manager.cancelUniqueWork(LEGACY_WORK));
                } catch (Exception ignored) { }
            });
        } catch (RuntimeException ignored) { }
    }

    static SharedPreferences progress(Context context) {
        return prefs(context);
    }

    /** Stable per-install identity; stored in no-backup storage to avoid restore collisions. */
    public static String deviceId(Context context) throws Exception {
        AtomicFile file = new AtomicFile(new File(context.getNoBackupFilesDir(), "firestore-sync-device"));
        if (file.getBaseFile().exists()) {
            String value = new String(file.readFully(), StandardCharsets.UTF_8).trim();
            if (value.matches("[a-z0-9-]{36}")) return value;
            throw new IllegalStateException("invalid device identity");
        }
        String value = UUID.randomUUID().toString();
        FileOutputStream out = null;
        try {
            out = file.startWrite();
            out.write(value.getBytes(StandardCharsets.UTF_8));
            file.finishWrite(out);
        } catch (Exception failure) {
            if (out != null) file.failWrite(out);
            throw failure;
        }
        return value;
    }
}
