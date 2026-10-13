package it.vintedaffari.app;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import android.util.AtomicFile;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Opt-in, owner-scoped transport. Never contacts Firebase if build configuration is absent. */
public final class FirestoreSync {
    private static final String APP = "ludo-catalog-sync";
    private static final String WORK = "ludo-catalog-sync-v3";
    private static final String LEGACY_WORK = "ludo-catalog-sync-v1";
    private static final String V2_WORK = "ludo-catalog-sync-v2";
    private static final String OPT_IN_VERSION = "minimal-v3";
    private static final String PREFS = "ludo_firestore_sync";
    private FirestoreSync() {}

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

    public static void start(Context context) {
        if (!authorized(context)) return;
        Context appContext = context.getApplicationContext();
        SharedPreferences p = prefs(context);
        // Stop all old publishers before recording a fresh, version-specific user tap.
        p.edit().putBoolean("enabled", false).commit();
        WorkManager manager = WorkManager.getInstance(appContext);
        manager.cancelUniqueWork(LEGACY_WORK);
        manager.cancelUniqueWork(V2_WORK);
        manager.cancelUniqueWork(WORK);
        p.edit().putString("opt_in_version", OPT_IN_VERSION).putBoolean("enabled", true).commit();
        Constraints network = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                FirestoreSnapshotWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(network).build();
        manager.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.REPLACE, request);
    }

    public static void stop(Context context) {
        prefs(context).edit().putBoolean("enabled", false).remove("opt_in_version").apply();
        WorkManager manager = WorkManager.getInstance(context.getApplicationContext());
        manager.cancelUniqueWork(WORK);
        manager.cancelUniqueWork(V2_WORK);
        manager.cancelUniqueWork(LEGACY_WORK);
        FirebaseAuth auth = auth(context);
        if (auth != null) auth.signOut();
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
