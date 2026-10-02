package it.vintedaffari.app;
import android.test.InstrumentationTestCase;
import android.graphics.Rect;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
/** Runs the production local asset in a detached WebView; no accessibility or overlay service. */
public class BrowserRuntimeTest extends InstrumentationTestCase {
 public void testHeadlessLoadsAndAnalyzesWithoutAccessibility()throws Exception{
  CountDownLatch ready=new CountDownLatch(1),done=new CountDownLatch(1);AtomicInteger games=new AtomicInteger();AtomicReference<String> error=new AtomicReference<>();AtomicReference<List<GameAnalysis>> result=new AtomicReference<>();AtomicReference<JsGameEngine> runtime=new AtomicReference<>();
  getInstrumentation().runOnMainSync(()->{JsGameEngine engine=new JsGameEngine(getInstrumentation().getTargetContext(),null,JsGameEngine.RuntimeHost.HEADLESS);runtime.set(engine);engine.start(new JsGameEngine.ReadyListener(){public void onReady(int count){games.set(count);ready.countDown();}public void onError(String message){error.set(message);ready.countDown();}});});
  try{assertTrue("Readiness callback timed out",ready.await(35,TimeUnit.SECONDS));assertNull(error.get());assertEquals(31181,games.get());getInstrumentation().runOnMainSync(()->runtime.get().analyze(Collections.singletonList(new VintedCard("Azul gioco da tavolo","","",10.0,null,null,new Rect(),"")),new JsGameEngine.BatchListener(){public void onResult(List<GameAnalysis> rows){result.set(rows);done.countDown();}public void onError(String message){error.set(message);done.countDown();}}));assertTrue(done.await(60,TimeUnit.SECONDS));assertNull(error.get());assertEquals(1,result.get().size());assertEquals("matched",result.get().get(0).status);assertEquals("230802",result.get().get(0).bggId);}finally{getInstrumentation().runOnMainSync(()->runtime.get().destroy());}
 }
}
