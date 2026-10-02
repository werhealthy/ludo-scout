package it.vintedaffari.app;
import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import android.database.DatabaseErrorHandler;
import java.io.File;
/** Separate contract DB/preferences and no platform queue wakes; production SQL/runtime stay real. */
final class BrowserTestContext extends ContextWrapper {
 private final String prefix;
 BrowserTestContext(Context base,String scope){super(base);prefix="browser-contract-"+scope+"-";}
 public Context getApplicationContext(){return this;}
 public File getDatabasePath(String name){return super.getDatabasePath(prefix+name);}
 public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory){return super.openOrCreateDatabase(prefix+name,mode,factory);}
 public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,DatabaseErrorHandler handler){return super.openOrCreateDatabase(prefix+name,mode,factory,handler);}
 public boolean deleteDatabase(String name){return super.deleteDatabase(prefix+name);}
 public SharedPreferences getSharedPreferences(String name,int mode){return super.getSharedPreferences(prefix+name,mode);}
 public void sendBroadcast(Intent intent){}
 public ComponentName startService(Intent intent){return intent.getComponent();}
 public ComponentName startForegroundService(Intent intent){return intent.getComponent();}
}
