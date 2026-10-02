package it.vintedaffari.app;
import android.database.sqlite.SQLiteDatabaseLockedException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
/** Acceptance and document lifetimes differ: accepted transactions finish after the UI closes. */
public final class BrowserCaptureIntake {
 public interface Reply {void result(String status,BrowserCaptureStore.CommitReceipt receipt);}
 private final BrowserCaptureStore store;private final ThreadPoolExecutor writer;private final Executor replies;private final Runnable wake;
 private final Map<String,Set<String>> pageIds=new HashMap<>();private volatile boolean closed;
 public BrowserCaptureIntake(BrowserCaptureStore store,ThreadPoolExecutor writer,Executor replies,Runnable wake){this.store=store;this.writer=writer;this.replies=replies;this.wake=wake;}
 public boolean submit(long captureId,int page,List<BrowserCandidate> packet,long now,BooleanSupplier documentCurrent,Reply reply){
  List<BrowserCandidate> admitted=Collections.unmodifiableList(new ArrayList<>(packet));
  if(closed){send("paused",null,documentCurrent,reply);return false;}
  try{writer.execute(()->{
   BrowserCaptureStore.CommitReceipt receipt=null;String status;
   try{
    String key=captureId+":"+page;Set<String> seen=pageIds.computeIfAbsent(key,k->new HashSet<>()),combined=new HashSet<>(seen);for(BrowserCandidate candidate:admitted)combined.add(candidate.itemId);
    if(combined.size()>500){send("limit",null,documentCurrent,reply);return;}
    receipt=store.commit(captureId,page,admitted,now);seen.addAll(combined);status="ready";
    try{if(store.activeJobs()>0)wake.run();}catch(RuntimeException ignored){} // A wake failure cannot undo a committed packet; periodic recovery remains durable.
   }catch(SQLiteDatabaseLockedException busy){status="retry";}catch(RuntimeException failure){status="rejected";}
   send(status,receipt,documentCurrent,reply);
  });return true;}catch(RejectedExecutionException full){send("retry",null,documentCurrent,reply);return false;}
 }
 private void send(String status,BrowserCaptureStore.CommitReceipt receipt,BooleanSupplier current,Reply reply){if(!current.getAsBoolean())return;try{replies.execute(()->{if(!closed&&current.getAsBoolean())reply.result(status,receipt);});}catch(RejectedExecutionException ignored){}}
 public void close(Runnable afterAcceptedWrites){closed=true;writer.shutdown();Thread finish=new Thread(()->{boolean interrupted=false;for(;;)try{if(writer.awaitTermination(30,TimeUnit.SECONDS))break;}catch(InterruptedException e){interrupted=true;}try{afterAcceptedWrites.run();}finally{if(interrupted)Thread.currentThread().interrupt();}},"ludo-browser-finish-writes");finish.start();}
}
