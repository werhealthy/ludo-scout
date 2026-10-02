package it.vintedaffari.app;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
/** Default-process local lane. SQLite claims are short; all WebView operations stay on main. */
public final class BrowserAnalysisRunner implements AutoCloseable {
 public interface Runtime {void start(JsGameEngine.ReadyListener listener);void analyze(List<VintedCard> cards,JsGameEngine.BatchListener listener);void destroy();}
 public interface RuntimeFactory {Runtime create();}
 private static final AtomicBoolean IN_FLIGHT=new AtomicBoolean();
 private final BrowserCaptureStore captures;private final BrowserAnalysisCommitter committer;private final RuntimeFactory factory;
 private final Handler main=new Handler(Looper.getMainLooper());private final long readyTimeout,batchTimeout;
 private final AtomicLong runtimeGeneration=new AtomicLong();
 private volatile boolean closed;private volatile Runtime runtime;private volatile CountDownLatch waiting;private volatile boolean ready;
 public BrowserAnalysisRunner(Context context,DealDatabase db,MarketStore market,BrowserCaptureStore captures){this(context,db,market,captures,()->{
  JsGameEngine engine=new JsGameEngine(context.getApplicationContext(),null,JsGameEngine.RuntimeHost.HEADLESS);
  return new Runtime(){public void start(JsGameEngine.ReadyListener listener){engine.start(listener);}public void analyze(List<VintedCard> cards,JsGameEngine.BatchListener listener){engine.analyze(cards,listener);}public void destroy(){engine.destroy();}};
 },30_000,60_000);}
 BrowserAnalysisRunner(Context context,DealDatabase db,MarketStore market,BrowserCaptureStore captures,RuntimeFactory factory,long readyTimeout,long batchTimeout){this.captures=captures;this.committer=new BrowserAnalysisCommitter(db,market,captures);this.factory=factory;this.readyTimeout=readyTimeout;this.batchTimeout=batchTimeout;}
 public boolean drainOnce(int limit,long now){
  if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Analisi locale sul thread principale");
  if(closed||!IN_FLIGHT.compareAndSet(false,true))return false;
  List<BrowserCaptureStore.Claim> claims=Collections.emptyList();
  try{
   claims=captures.claim(limit,now);if(claims.isEmpty())return false;
   ensureReady();if(closed)throw new CancellationException("Analisi interrotta");
   List<VintedCard> cards=new ArrayList<>();for(BrowserCaptureStore.Claim claim:claims)cards.add(BrowserAnalysisCommitter.toCard(claim.candidate));
   long batchGeneration=runtimeGeneration.get();
   AtomicReference<List<GameAnalysis>> result=new AtomicReference<>();AtomicReference<String> error=new AtomicReference<>();CountDownLatch done=new CountDownLatch(1);waiting=done;
   main.post(()->{if(closed||batchGeneration!=runtimeGeneration.get()){done.countDown();return;}try{runtime.analyze(cards,new JsGameEngine.BatchListener(){public void onResult(List<GameAnalysis> rows){if(!closed&&batchGeneration==runtimeGeneration.get())result.set(rows);done.countDown();}public void onError(String message){error.set(message);done.countDown();}});}catch(Throwable failure){error.set(String.valueOf(failure));done.countDown();}});
   if(!done.await(batchTimeout,TimeUnit.MILLISECONDS))throw new TimeoutException("Tempo di analisi locale scaduto");
   if(closed)throw new CancellationException("Analisi interrotta");
   List<GameAnalysis> rows=result.get();if(error.get()!=null||rows==null||rows.size()!=claims.size())throw new IllegalStateException(error.get()==null?"Risposta locale incompleta":error.get());
   for(int i=0;i<claims.size();i++){if(closed)throw new CancellationException("Analisi interrotta");committer.commit(claims.get(i),rows.get(i),System.currentTimeMillis());}
   return true;
  }catch(Exception failure){
   if(failure instanceof InterruptedException)Thread.currentThread().interrupt();String reason=failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage();if(reason.length()>400)reason=reason.substring(0,400);
   for(BrowserCaptureStore.Claim claim:claims)try{captures.fail(claim,reason,System.currentTimeMillis()+30_000L);}catch(RuntimeException ignored){}
   resetRuntime();return false;
  }finally{waiting=null;IN_FLIGHT.set(false);}
 }
 private void ensureReady()throws Exception{
  if(ready)return;long generation=runtimeGeneration.get();AtomicReference<String> error=new AtomicReference<>();CountDownLatch done=new CountDownLatch(1);waiting=done;
  main.post(()->{if(closed||generation!=runtimeGeneration.get()){done.countDown();return;}try{runtime=factory.create();runtime.start(new JsGameEngine.ReadyListener(){public void onReady(int count){if(!closed&&generation==runtimeGeneration.get()&&count>0)ready=true;else error.set("Catalogo locale non disponibile");done.countDown();}public void onError(String message){error.set(message);done.countDown();}});}catch(Throwable failure){error.set(String.valueOf(failure));done.countDown();}});
  if(!done.await(readyTimeout,TimeUnit.MILLISECONDS))throw new TimeoutException("Runtime locale non pronto");if(closed)throw new CancellationException("Analisi interrotta");if(error.get()!=null||!ready)throw new IllegalStateException(error.get()==null?"Runtime locale non pronto":error.get());
 }
 private void resetRuntime(){runtimeGeneration.incrementAndGet();ready=false;main.post(()->{Runtime current=runtime;runtime=null;if(current!=null)try{current.destroy();}catch(RuntimeException ignored){}});}
 public void close(){closed=true;CountDownLatch current=waiting;if(current!=null)current.countDown();resetRuntime();}
}
