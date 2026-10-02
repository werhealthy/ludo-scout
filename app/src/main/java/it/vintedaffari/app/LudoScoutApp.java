package it.vintedaffari.app;

import android.app.Application;

/** Lightweight process bootstrap. Keep process-specific feature initialization lazy; only
 * diagnostics that must exist before component creation belong here. */
public final class LudoScoutApp extends Application {
    @Override public void onCreate(){
        super.onCreate();
        // The UI browser must not share the radar/queue WebView data directory.
        if (android.app.Application.getProcessName().endsWith(":ui")) android.webkit.WebView.setDataDirectorySuffix("ludo-ui");
        ProcessCrashJournal.install(this);
    }
}
