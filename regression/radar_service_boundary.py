"""Exercise the real Android service selection method with platform boundaries replaced.
Breaks caught: main-thread DB query, duplicate selection, stale callback after destroy,
or lost timer when selection fails/another remote run owns the queue.
"""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1]
src=(root/'app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java').read_text()
start=src.index('    private void flushPendingAnalysis()')
brace=src.index('{',start);depth=0
for i in range(brace,len(src)):
 if src[i]=='{':depth+=1
 elif src[i]=='}':
  depth-=1
  if depth==0:method=src[start:i+1];break
harness=r'''
package it.vintedaffari.app;
import java.util.*;import java.util.concurrent.*;
public class VintedAccessibilityService {
 boolean radarDestroyed,analysisBatchInFlight,analysisSelectionInFlight;int analyses;
 Engine engine=new Engine();Store marketStore=new Store();Db database=new Db();Handler handler=new Handler();RadarPersistence radarPersistence=new RadarPersistence();
 Map<String,VintedCard> pendingForAnalysis=new LinkedHashMap<>();Map<String,Long> recentlyAnalyzed=new LinkedHashMap<>();
 static class Engine {boolean isReady(){return true;}}
 static class VintedCard {String sig;VintedCard(String s){sig=s;}}
 static class DealDatabase {static String signature(VintedCard c){return c.sig;}}
 static class Store {
  volatile int queries;volatile boolean fail;List<VintedCard> cards=new ArrayList<>();CountDownLatch release=new CountDownLatch(1);
  List<VintedCard> pendingAnalysisCards(int n){if(!Thread.currentThread().getName().equals("LudoRadarPersistence"))throw new AssertionError("query on main");queries++;try{if(!release.await(3,TimeUnit.SECONDS))throw new AssertionError("blocked test");}catch(InterruptedException e){throw new RuntimeException(e);}if(fail)throw new IllegalStateException("busy");return cards;}
 }
 static class Db {boolean waiting=true;int waitingObservationSessionCount(){return waiting?1:0;}}
 static class Handler {Queue<Runnable> posted=new ConcurrentLinkedQueue<>();int timers;long delay;void post(Runnable r){posted.add(r);}void postDelayed(Runnable r,long ms){timers++;delay=ms;}void drain(){Runnable r;while((r=posted.poll())!=null)r.run();}}
 static class Log {static void e(String t,String m,Throwable e){}}
 static String TAG="test";
 void analyzeBatch(List<VintedCard> batch){if(!Thread.currentThread().getName().equals("main"))throw new AssertionError("WebView off main");analyses++;analysisBatchInFlight=true;}
 void continuePersistentAnalysis(){flushPendingAnalysis();}
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 void waitPosted()throws Exception{CountDownLatch done=new CountDownLatch(1);radarPersistence.submit(done::countDown);check(done.await(3,TimeUnit.SECONDS),"worker timeout");handler.drain();}
 void close()throws Exception{CountDownLatch done=new CountDownLatch(1);radarPersistence.close(done::countDown);check(done.await(3,TimeUnit.SECONDS),"close timeout");}
 public static void main(String[] a)throws Exception{
  VintedAccessibilityService n=new VintedAccessibilityService();n.marketStore.cards.add(new VintedCard("a"));n.pendingForAnalysis.put("a",new VintedCard("a"));n.flushPendingAnalysis();n.flushPendingAnalysis();check(n.analyses==0,"synchronous DB/analysis");n.marketStore.release.countDown();n.waitPosted();check(n.marketStore.queries==1&&n.analyses==1&&!n.pendingForAnalysis.containsKey("a")&&n.recentlyAnalyzed.containsKey("a"),"single flight or map ownership lost");n.close();
  n=new VintedAccessibilityService();n.marketStore.cards.add(new VintedCard("a"));n.flushPendingAnalysis();n.radarDestroyed=true;n.marketStore.release.countDown();n.waitPosted();check(n.analyses==0,"destroyed service invoked WebView");n.close();
  n=new VintedAccessibilityService();n.marketStore.release.countDown();n.flushPendingAnalysis();n.waitPosted();check(n.analyses==0&&n.handler.timers==1&&n.handler.delay==5000,"waiting queue lost recheck");n.close();
  n=new VintedAccessibilityService();n.marketStore.fail=true;n.marketStore.release.countDown();n.flushPendingAnalysis();n.waitPosted();check(!n.analysisSelectionInFlight&&n.handler.timers==1,"selection failure wedged lane");n.close();
  System.out.println("PASS real service selection single-flight, worker/main boundary, destroy and error/wait timers");
 }
 __PRODUCTION__
}
'''.replace('__PRODUCTION__',method)
with tempfile.TemporaryDirectory() as out:
 p=Path(out)/'VintedAccessibilityService.java';p.write_text(harness)
 subprocess.run(['javac','-d',out,str(p),str(root/'app/src/main/java/it/vintedaffari/app/RadarPersistence.java')],check=True)
 subprocess.run(['java','-cp',out,'it.vintedaffari.app.VintedAccessibilityService'],check=True)
