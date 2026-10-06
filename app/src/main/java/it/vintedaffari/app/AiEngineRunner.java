package it.vintedaffari.app;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/** Separate bounded lane: neither the Vinted lane, BGG lane nor the main thread waits on AI. */
final class AiEngineRunner {
 static final String RECOVERY_READY="it.vintedaffari.app.AI_RECOVERY_READY";
 private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->new Thread(r,"ludo-ai-engine"));
 private static final AtomicBoolean BUSY=new AtomicBoolean();
 private static volatile boolean more;
 private static volatile long nextAttempt;
 private AiEngineRunner(){}
 static boolean hasPending(){return BUSY.get()||more;}
 static AiBetaSettings journal(Context c){return new AiBetaSettings(c,"ai-engine.private",900000);}
 static void configurationChanged(Context context){nextAttempt=0;more=false;schedule(context);}
 static void schedule(Context context){
  if(context==null||System.currentTimeMillis()<nextAttempt||!BUSY.compareAndSet(false,true))return;
  Context app=context.getApplicationContext();
  IO.execute(()->{
   try{
    File dir=app.getNoBackupFilesDir(),file=app.getDatabasePath("vinted_affari.db");
    if(!file.isFile()){more=false;return;}
    try(RandomAccessFile lockFile=new RandomAccessFile(new File(dir,"ai-engine.lock"),"rw");
        java.nio.channels.FileChannel channel=lockFile.getChannel()){
     FileLock lease;
     try{lease=channel.tryLock();}catch(java.nio.channels.OverlappingFileLockException busy){return;}
     if(lease==null)return;
     try(FileLock owned=lease;SQLiteDatabase db=SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,SQLiteDatabase.OPEN_READWRITE)){
      AiBetaSettings settings=new AiBetaSettings(app),privateJournal=journal(app);JSONObject config=settings.load();
      AiEngineSession.Journal store=new AiEngineSession.Journal(){
       public JSONObject load()throws Exception{return privateJournal.load();}
       public void save(JSONObject value)throws Exception{privateJournal.save(value);}
      };
      AiEngineListings listings=new AiEngineListings(db);
      AiEngineSession.Source source=new AiEngineSession.Source(){
       public JSONArray select(JSONObject j,long now)throws Exception{return listings.select(j,now);}
       public boolean current(JSONArray rows)throws Exception{return sameConfiguration(config,settings.load())&&listings.current(rows);}
       public int apply(JSONArray rows,JSONObject response)throws Exception{
        if(!sameConfiguration(config,settings.load()))return 0;return listings.apply(rows,response);
       }
      };
      AiEngineSession.Transport transport=new AiEngineSession.Transport(){
       public JSONObject status()throws Exception{return AiBetaClient.status(config.optString("endpoint"),config.optString("token"));}
       public JSONObject submit(String id,JSONArray rows)throws Exception{
        if(!sameConfiguration(config,settings.load()))throw new Exception("configuration changed");
        return AiBetaClient.submit(config.optString("endpoint"),config.optString("token"),id,rows);
       }
      };
      AiEngineSession.Result result=AiEngineSession.run(config,store,source,transport,System.currentTimeMillis());
      int recovered=listings.recoveredCount(),refreshed=listings.refreshedCount();
      more=result.more;
      JSONObject progress=privateJournal.load();
      long completedAt=System.currentTimeMillis();
      long persistedNextAt=progress.optLong("next_at");
      long retryAt=AiEngineBackoff.nextAttemptAt(result.state,completedAt,persistedNextAt,result.more,AiEnginePolicy.BACKOFF);
      nextAttempt=retryAt;
      android.content.ContentValues diagnostic=new android.content.ContentValues();
      diagnostic.put("name","diag:ai_engine");diagnostic.put("value",result.checked);diagnostic.put("updated_at",System.currentTimeMillis());
      diagnostic.put("text_value","build=ai-engine-v3;state="+result.state+";checked="+result.checked+";held="+result.held+";recovered="+recovered+";refreshed="+refreshed+";checksTotal="+progress.optLong("checked_total")+";holdsTotal="+progress.optLong("held_total")+";failedBatches="+progress.optInt("failed_batches")+";more="+result.more+";retryAt="+retryAt);
      db.insertWithOnConflict("queue_controls",null,diagnostic,SQLiteDatabase.CONFLICT_REPLACE);
      if(result.held>0||recovered>0||refreshed>0)app.sendBroadcast(new android.content.Intent(OperationCenter.CHANGED).setPackage(app.getPackageName()));
      if(recovered>0||refreshed>0){
       app.sendBroadcast(new android.content.Intent(RECOVERY_READY).setPackage(app.getPackageName()));
       // The queue owner performs the zero-network AI -> provisional BGG handoff even when
       // Accessibility/JS analysis is not running.
       QueueKeepAliveService.ensureRunning(app);
       QueueWorkScheduler.schedule(app);
      }
      if(result.more)QueueWorkScheduler.scheduleAfter(app,10000);
      else if("WAIT".equals(result.state))QueueWorkScheduler.scheduleAfter(app,Math.max(10000L,retryAt-System.currentTimeMillis()));
     }
    }
   }catch(Exception unavailable){
    more=false;nextAttempt=System.currentTimeMillis()+AiEnginePolicy.BACKOFF;
    // No exception text: transport/configuration context may contain private data.
    android.util.Log.w("LudoAI","Automatic AI pass unavailable; reserved request retained");
   }finally{if(nextAttempt<=System.currentTimeMillis())nextAttempt=System.currentTimeMillis()+10000;BUSY.set(false);}
  });
 }
 private static boolean sameConfiguration(JSONObject expected,JSONObject current){
  return current.optBoolean("enabled")&&expected.optString("endpoint").equals(current.optString("endpoint"))&&expected.optString("token").equals(current.optString("token"));
 }
}
