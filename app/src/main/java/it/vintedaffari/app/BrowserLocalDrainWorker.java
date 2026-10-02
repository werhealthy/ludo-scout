package it.vintedaffari.app;
import android.content.Context;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
/** Offline local recovery. All claims share the service's SQLite lease and process single-flight. */
public final class BrowserLocalDrainWorker extends Worker {
 public BrowserLocalDrainWorker(Context context,WorkerParameters parameters){super(context,parameters);}
 public Result doWork(){
  DealDatabase db=new DealDatabase(getApplicationContext());
  try{
   MarketStore market=new MarketStore(getApplicationContext(),db);MarketStore.RuntimeStatus lane=market.laneStatus("browser");
   if(QueueKeepAliveService.isRunning()&&lane.updatedAt>0&&System.currentTimeMillis()-lane.updatedAt<120_000L)return Result.success();
   BrowserCaptureStore captures=new BrowserCaptureStore(db);if(captures.activeJobs()==0)return Result.success();
   try(BrowserAnalysisRunner runner=new BrowserAnalysisRunner(getApplicationContext(),db,market,captures)){
    for(int batch=0;batch<8&&!isStopped();batch++){market.setLaneStatus("browser","RECOVERING","Analisi locale senza rete",0);if(!runner.drainOnce(8,System.currentTimeMillis()))break;}
   }
   if(captures.activeJobs()>0)QueueWorkScheduler.scheduleLocalBrowserAfter(getApplicationContext(),30_000L);
   return Result.success();
  }catch(RuntimeException failure){return Result.retry();}finally{db.close();}
 }
}
