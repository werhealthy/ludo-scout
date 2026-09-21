package it.vintedaffari.app;

import android.app.Application;

/** Lightweight process bootstrap. Keep process-specific feature initialization lazy; only
 * diagnostics that must exist before component creation belong here. */
public final class LudoScoutApp extends Application {
    @Override public void onCreate(){
        super.onCreate();
        ProcessCrashJournal.install(this);
    }
}
