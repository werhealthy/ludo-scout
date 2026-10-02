package it.vintedaffari.app;
import android.test.AndroidTestCase;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
/** The real runner and SQLite leases, with a controlled runtime boundary for timeout/race tests. */
public class BrowserLocalRunnerTest extends AndroidTestCase {
 private DealDatabase db;private BrowserCaptureStore store;private MarketStore market;private ExecutorService executor;
 protected void setUp()throws Exception{super.setUp();getContext().deleteDatabase("vinted_affari.db");db=new DealDatabase(getContext());store=new BrowserCaptureStore(db);market=new MarketStore(getContext(),db);executor=Executors.newSingleThreadExecutor();}
 protected void tearDown()throws Exception{executor.shutdownNow();executor.awaitTermination(3,TimeUnit.SECONDS);db.close();getContext().deleteDatabase("vinted_affari.db");super.tearDown();}
 private long capture(int count){long id=store.beginCapture("https://www.vinted.it/catalog",10);List<BrowserCandidate> rows=new ArrayList<>();for(int i=0;i<count;i++)rows.add(new BrowserCandidate(String.valueOf(101+i),"https://www.vinted.it/items/"+(101+i),"Azul gioco da tavolo",1000,null,Collections.emptyMap(),Collections.emptyMap(),10));store.commit(id,1,rows,10);return id;}
 private static class Runtime implements BrowserAnalysisRunner.Runtime {
  boolean ready=true;volatile JsGameEngine.BatchListener callback;CountDownLatch analyzing=new CountDownLatch(1);AtomicInteger destroyed=new AtomicInteger();
  public void start(JsGameEngine.ReadyListener listener){if(ready)listener.onReady(31181);else listener.onError("Test runtime not ready");}
  public void analyze(List<VintedCard> cards,JsGameEngine.BatchListener listener){callback=listener;analyzing.countDown();}
  public void destroy(){destroyed.incrementAndGet();}
 }
 private BrowserAnalysisRunner runner(Runtime runtime){return new BrowserAnalysisRunner(getContext(),db,market,store,()->runtime,200,200);}
 public void testReadinessFailureIsTechnicalRetryNotReview(){long id=capture(1);Runtime runtime=new Runtime();runtime.ready=false;try(BrowserAnalysisRunner runner=runner(runtime)){assertFalse(runner.drainOnce(8,20));}assertEquals("TECHNICAL_ERROR",store.snapshot(id).rows.get(0).state);assertEquals(1,store.activeJobs());}
 public void testBatchTimeoutKeepsRetryableLease(){long id=capture(1);try(BrowserAnalysisRunner runner=runner(new Runtime())){assertFalse(runner.drainOnce(8,20));}assertEquals("TECHNICAL_ERROR",store.snapshot(id).rows.get(0).state);assertEquals(1,store.activeJobs());}
 public void testSameProcessSingleFlightAndCloseCancelsLateResults()throws Exception{long id=capture(2);Runtime runtime=new Runtime();BrowserAnalysisRunner first=runner(runtime),second=runner(new Runtime());Future<Boolean> work=executor.submit(()->first.drainOnce(8,20));assertTrue(runtime.analyzing.await(3,TimeUnit.SECONDS));assertFalse(second.drainOnce(8,21));first.close();assertFalse(work.get(3,TimeUnit.SECONDS));runtime.callback.onResult(Collections.emptyList());second.close();assertEquals(2,store.activeJobs());for(BrowserCaptureStore.Row row:store.snapshot(id).rows)assertEquals("TECHNICAL_ERROR",row.state);}
 public void testProductionRuntimeDrainsAtMostEightThenResumes()throws Exception{long id=capture(10);try(BrowserAnalysisRunner runner=new BrowserAnalysisRunner(getContext(),db,market,store)){assertTrue(runner.drainOnce(8,20));assertEquals(2,store.activeJobs());assertTrue(runner.drainOnce(8,30));assertEquals(0,store.activeJobs());}assertEquals(10,store.snapshot(id).rows.size());for(BrowserCaptureStore.Row row:store.snapshot(id).rows)assertFalse("QUEUED".equals(row.state)||"ANALYZING".equals(row.state));}
 public void testLateReadinessCannotReviveTimedOutRuntime()throws Exception{
  capture(1);AtomicReference<JsGameEngine.ReadyListener> late=new AtomicReference<>();AtomicInteger creations=new AtomicInteger();
  Runtime delayed=new Runtime(){public void start(JsGameEngine.ReadyListener listener){late.set(listener);}};
  Runtime completed=new Runtime(){public void analyze(List<VintedCard> cards,JsGameEngine.BatchListener listener){try{GameAnalysis result=GameAnalysis.fromJson(new JSONObject().put("status","excluded"));listener.onResult(Collections.nCopies(cards.size(),result));}catch(Exception e){listener.onError(e.toString());}}};
  try(BrowserAnalysisRunner runner=new BrowserAnalysisRunner(getContext(),db,market,store,()->creations.incrementAndGet()==1?delayed:completed,200,200)){
   assertFalse(runner.drainOnce(8,20));assertNotNull(late.get());late.get().onReady(31181);
   assertTrue(runner.drainOnce(8,System.currentTimeMillis()+60_000));assertEquals(2,creations.get());assertEquals(0,store.activeJobs());
  }
 }
}
