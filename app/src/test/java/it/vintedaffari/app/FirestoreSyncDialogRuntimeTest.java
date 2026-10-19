package it.vintedaffari.app;

import static org.junit.Assert.*;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Renders the production Firestore dialog under Robolectric; no device or Firebase connection. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 34)
public final class FirestoreSyncDialogRuntimeTest {
    private Context context;
    private long txBefore;
    private static final String METADATA_WORK = "ludo-firestore-sync-v3";

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        FirestoreSync.progress(context).edit().putBoolean("enabled", false).remove("opt_in_version")
                .putBoolean("evidence_enabled", false).remove("evidence_opt_in_version").commit();
        txBefore = android.net.TrafficStats.getUidTxBytes(android.os.Process.myUid());
    }

    @After public void tearDown() {
        FirestoreSync.progress(context).edit().putBoolean("enabled", false).remove("opt_in_version")
                .putBoolean("evidence_enabled", false).remove("evidence_opt_in_version").commit();
    }

    @Test public void disconnectedDialogRendersDefaultOffDisabledCheckboxAndToggleHasNoSideEffects() throws Exception {
        android.app.Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        try {
            FirestoreSyncDialog.showSettings(activity);
            CheckBox checkbox = findDialogCheckbox(activity);
            assertNotNull("disconnected screen renders evidence checkbox", checkbox);
            assertFalse("default off", checkbox.isChecked());
            assertFalse("cannot grant consent without configured authorized account", checkbox.isEnabled());
            assertTrue("disconnected checkbox remains visible", checkbox.isShown());
            checkbox.performClick();
            org.robolectric.shadows.ShadowLooper.idleMainLooper(300, java.util.concurrent.TimeUnit.MILLISECONDS);
            assertFalse("toggle cannot grant while signed out", FirestoreSync.evidenceConsented(context));
            assertFalse("toggle cannot enable metadata sync", FirestoreSync.enabled(context));
            assertTrafficUnchanged();
        } finally { activity.finish(); }
    }

    @Test public void pausedDialogRendersCheckboxAndClickDoesNotResumeOrScheduleSync() throws Exception {
        FirestoreSync.progress(context).edit().putBoolean("enabled", true).putString("opt_in_version", "metadata-v1").commit();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        try {
            FirestoreSyncDialog.showSettings(activity);
            CheckBox checkbox = findDialogCheckbox(activity);
            assertNotNull("paused screen renders evidence checkbox", checkbox);
            assertTrue("paused branch is visible", checkbox.isShown());
            assertFalse("consent toggle does not enable metadata sync", FirestoreSync.enabled(context));
            checkbox.performClick();
            org.robolectric.shadows.ShadowLooper.idleMainLooper(300, java.util.concurrent.TimeUnit.MILLISECONDS);
            assertFalse("toggle cannot enable metadata sync", FirestoreSync.enabled(context));
            assertTrafficUnchanged();
        } finally { activity.finish(); }
    }

    @Test public void activeDialogRendersCheckedExplicitPersistedEvidenceConsent() throws Exception {
        FirestoreSync.progress(context).edit().putBoolean("enabled", true).putString("opt_in_version", "metadata-v1")
                .putBoolean("evidence_enabled", true).putString("evidence_opt_in_version", FirestoreEvidenceConsentPolicy.CURRENT_VERSION).commit();
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        try {
            FirestoreSyncDialog.showSettings(activity);
            CheckBox checkbox = findDialogCheckbox(activity);
            assertNotNull("active screen renders evidence checkbox", checkbox);
            assertTrue("displayed value comes from persisted versioned consent", checkbox.isChecked());
            assertTrafficUnchanged();
        } finally { activity.finish(); }
    }

    private void assertTrafficUnchanged() {
        long after = android.net.TrafficStats.getUidTxBytes(android.os.Process.myUid());
        if (txBefore >= 0 && after >= 0) assertEquals("dialog/checkbox must not transmit", txBefore, after);
    }

    private CheckBox findDialogCheckbox(Activity activity) {
        AlertDialog dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        if (!(dialog instanceof AlertDialog) || !dialog.isShowing()) return null;
        return findCheckbox(dialog.getWindow().getDecorView());
    }

    private CheckBox findCheckbox(View root) {
        if (root instanceof CheckBox && ((CheckBox) root).getText().toString().contains("descrizioni e URL foto")) return (CheckBox) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i=0; i<group.getChildCount(); i++) {
                CheckBox found = findCheckbox(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
}
