package it.vintedaffari.app;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.concurrent.TimeUnit;

/** Opt-in, owner-scoped transport. Never contacts Firebase if build configuration is absent. */
public final class FirestoreSync {
    private static final String APP = "ludo-catalog-sync";
    private static final String WORK = "ludo-catalog-sync-v1";
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
        return prefs(context).getBoolean("enabled", false) && authorized(context);
    }

    public static void start(Context context) {
        if (!authorized(context)) return;
        prefs(context).edit().putBoolean("enabled", true).apply();
        Constraints network = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                FirestoreSyncWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(network).build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniquePeriodicWork(
                WORK, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    public static void stop(Context context) {
        prefs(context).edit().putBoolean("enabled", false).apply();
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(WORK);
        FirebaseAuth auth = auth(context);
        if (auth != null) auth.signOut();
    }

    static SharedPreferences progress(Context context) {
        return prefs(context);
    }
}
