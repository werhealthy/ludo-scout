package it.vintedaffari.app;
import android.test.AndroidTestCase;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
/** Real SQLite commit boundary, including accepted writes surviving a closed document. */
public class BrowserCaptureIntakeTest extends AndroidTestCase {
 private DealDatabase db;private BrowserCaptureStore store;private ThreadPoolExecutor executor;
 protected void setUp()throws Exception{super.setUp();setContext(new BrowserTestContext(getContext(),getClass().getSimpleName()));getContext().deleteDatabase("vinted_affari.db");db=new DealDatabase(getContext());store=new BrowserCaptureStore(db);executor=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(4));}
 protected void tearDown()throws Exception{executor.shutdown();executor.awaitTermination(5,TimeUnit.SECONDS);db.close();getContext().deleteDatabase("vinted_affari.db");super.tearDown();}
 public void testAckAfterDurableCommitAndAcceptedWorkSurvivesClose()throws Exception{
  long capture=store.beginCapture("https://www.vinted.it/catalog",10);CountDownLatch blocked=new CountDownLatch(1),entered=new CountDownLatch(1),finished=new CountDownLatch(1);executor.execute(()->{entered.countDown();try{blocked.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});assertTrue(entered.await(2,TimeUnit.SECONDS));AtomicInteger replies=new AtomicInteger(),wakes=new AtomicInteger();AtomicBoolean document=new AtomicBoolean(true);
  BrowserCaptureIntake intake=new BrowserCaptureIntake(store,executor,Runnable::run,wakes::incrementAndGet);BrowserCandidate card=new BrowserCandidate("101","https://www.vinted.it/items/101","Azul",1000,null,Collections.emptyMap(),Collections.emptyMap(),10);
  assertTrue(intake.submit(capture,1,Collections.singletonList(card),10,document::get,(status,receipt)->{assertEquals("ready",status);assertEquals(1,store.snapshot(capture).rows.size());replies.incrementAndGet();}));assertEquals(0,replies.get());document.set(false);intake.close(finished::countDown);blocked.countDown();assertTrue(finished.await(5,TimeUnit.SECONDS));assertEquals(1,store.snapshot(capture).rows.size());assertEquals(1,wakes.get());assertEquals(0,replies.get());assertEquals(1,store.activeJobs());
 }
 public void testCapacityFailureHasNoAcknowledgedAcquisition()throws Exception{
  long capture=store.beginCapture("https://www.vinted.it/catalog",10);CountDownLatch block=new CountDownLatch(1);executor.execute(()->{try{block.await();}catch(InterruptedException e){}});for(int i=0;i<4;i++)executor.execute(()->{});BrowserCaptureIntake intake=new BrowserCaptureIntake(store,executor,Runnable::run,()->{});AtomicReference<String> result=new AtomicReference<>();assertFalse(intake.submit(capture,1,Collections.emptyList(),10,()->true,(status,receipt)->result.set(status)));assertEquals("retry",result.get());assertTrue(store.snapshot(capture).rows.isEmpty());block.countDown();
 }
}
