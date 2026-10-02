"""Verify current local work belongs to the displayed scroll, including stale/crashed owners."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/it/vintedaffari/app'
harness=r'''
package it.vintedaffari.app;
import java.util.*;
public class LocalActivityCheck {
 static void eq(int want,int got){if(want!=got)throw new AssertionError("wanted "+want+" got "+got);}
 public static void main(String[] args){
  List<String> scope=Arrays.asList("browser:101","browser:102");
  eq(2,EngineLocalActivity.activeCount(1000,2000,2,"RUNNING\nbrowser:101\nbrowser:102",scope));
  eq(0,EngineLocalActivity.activeCount(1000,2000,2,"RUNNING\nbrowser:201\nbrowser:202",scope));
  eq(1,EngineLocalActivity.activeCount(1000,2000,2,"RUNNING\nbrowser:101\nbrowser:101",scope));
  eq(0,EngineLocalActivity.activeCount(1000,20000,2,"RUNNING\nbrowser:101",scope));
  eq(0,EngineLocalActivity.activeCount(3000,2000,2,"RUNNING\nbrowser:101",scope));
  eq(0,EngineLocalActivity.activeCount(1000,2000,0,"IDLE\nbrowser:101",scope));
  eq(0,EngineLocalActivity.activeCount(1000,2000,2,"ERROR\nbrowser:101",scope));
  EngineMotionState motion=new EngineMotionState();
  eq(12,motion.consume("run1",1,new int[]{12})[0]);
  eq(12,motion.consume("run1",2,new int[]{4})[0]);
  eq(4,motion.consume("run1",2,new int[]{4})[0]);
  eq(8,motion.consume("run2",3,new int[]{8})[0]);
  WaitingStore store=new WaitingStore();store.counts=new int[]{2,1,0,0,0};
  store.items=Arrays.asList(new WaitingStore.PipelineItem("browser:101",true,false),new WaitingStore.PipelineItem("browser:102",true,false),new WaitingStore.PipelineItem("browser:103",true,true));
  MarketStore.RuntimeStatus work=new MarketStore.RuntimeStatus();work.updatedAt=1000;work.value=2;work.detail="RUNNING\nbrowser:101\nbrowser:103";
  eq(1,store.enginePipelineWaitingCounts(0,200,work,2000)[0]);
  eq(1,store.enginePipelineWaitingCounts(0,200,work,2000)[1]);
  eq(2,store.counts[0]); // presentation must not mutate the underlying queue state
  eq(2,store.enginePipelineWaitingCounts(0,200,work,20000)[0]);
  work.detail="RUNNING\nbrowser:999";eq(2,store.enginePipelineWaitingCounts(0,200,work,2000)[0]);
  work.detail="IDLE\nbrowser:101";eq(2,store.enginePipelineWaitingCounts(0,200,work,2000)[0]);
  System.out.println("PASS scope, duplicate, freshness, stop/error local activity and snapshot motion");
 }
}
'''
# Execute the actual waiting-count method with only DB/Android boundaries replaced.
java=(src/'DealDatabase.java').read_text()
start=java.index('    public synchronized int[] enginePipelineWaitingCounts(')
body=java.index('{',start);depth=1;end=body+1
while depth:
 if java[end]=='{':depth+=1
 elif java[end]=='}':depth-=1
 end+=1
harness+='\nclass MarketStore {static class RuntimeStatus {long updatedAt,value;String detail;}}\n'
harness+='class WaitingStore {\n static class PipelineItem {String signature;boolean queued,busy;PipelineItem(String s,boolean q,boolean b){signature=s;queued=q;busy=b;}}\n int[] counts;List<PipelineItem> items;\n int[] enginePipelineQueuedCounts(long start,long end){return counts.clone();}\n List<PipelineItem> enginePipelineItems(long start,long end,int phase){return items;}\n'+java[start:end]+'\n}\n'
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp)/'LocalActivityCheck.java';p.write_text(harness)
 subprocess.run(['javac','-d',tmp,str(src/'EngineLocalActivity.java'),str(src/'EngineMotionState.java'),str(p)],check=True)
 subprocess.run(['java','-cp',tmp,'it.vintedaffari.app.LocalActivityCheck'],check=True)
