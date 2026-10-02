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
  System.out.println("PASS scope, duplicate, freshness, stop/error local activity and snapshot motion");
 }
}
'''
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp)/'LocalActivityCheck.java';p.write_text(harness)
 subprocess.run(['javac','-d',tmp,str(src/'EngineLocalActivity.java'),str(src/'EngineMotionState.java'),str(p)],check=True)
 subprocess.run(['java','-cp',tmp,'it.vintedaffari.app.LocalActivityCheck'],check=True)
