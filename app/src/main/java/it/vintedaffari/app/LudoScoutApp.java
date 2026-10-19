package it.vintedaffari.app;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.work.Configuration;
import androidx.work.WorkManager;

/** Lightweight process bootstrap. Keep process-specific feature initialization lazy; only
 * diagnostics that must exist before component creation belong here. */
public final class LudoScoutApp extends Application implements Configuration.Provider {
    @NonNull @Override public Configuration getWorkManagerConfiguration(){
        return new Configuration.Builder().setDefaultProcessName(getPackageName()).build();
    }

    @Override public void onCreate(){
        super.onCreate();
        ProcessCrashJournal.install(this);
        if(getPackageName().equals(Application.getProcessName())){
            WorkManager.initialize(this,getWorkManagerConfiguration());
        }
        // The UI browser must not share the radar/queue WebView data directory.
        if (android.app.Application.getProcessName().endsWith(":ui")) android.webkit.WebView.setDataDirectorySuffix("ludo-ui");
    }
}
