package it.vintedaffari.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class EngineMotionStateTest {
 @Test public void freshReturnAnimatesOnlyOnce(){EngineMotionState s=new EngineMotionState();s.restore("run",new int[]{400,0,0,0,0});int[] now={450,0,0,0,0};assertEquals(400,s.consume("run",10,now)[0]);assertEquals(450,s.consume("run",10,now)[0]);assertEquals(450,s.consume("run",11,new int[]{430,0,0,0,0})[0]);assertEquals(430,s.consume("run",11,new int[]{430,0,0,0,0})[0]);}
 @Test public void differentScrollInitializesWithoutFalseDelta(){EngineMotionState s=new EngineMotionState();s.restore("old",new int[]{400,0,0,0,0});assertEquals(3,s.consume("new",10,new int[]{3,0,0,0,0})[0]);}
 @Test public void ownsDefensiveCopies(){EngineMotionState s=new EngineMotionState();int[] data={4,0,0,0,0};s.restore("run",data);data[0]=999;assertEquals(4,s.consume("run",1,new int[]{5,0,0,0,0})[0]);}
 @Test public void firstVisitHasNoInventedChanges(){EngineMotionState s=new EngineMotionState();assertEquals(5,s.consume("run",1,new int[]{5,0,0,0,0})[0]);}

 @Test public void readyStockIsNeverProcessing(){assertFalse(EngineOverviewPresentation.activePhase(16,4,false,false,0,100));assertEquals("Scroll elaborato",EngineOverviewPresentation.status(true,true,16,0,0,false,false,0,100));}
 @Test public void pauseAndPacingKeepQueuedWorkStill(){
 assertFalse(EngineOverviewPresentation.activePhase(2,1,true,false,0,100));
 assertFalse(EngineOverviewPresentation.activePhase(8,3,false,false,101,100));
 assertEquals("in pausa",EngineOverviewPresentation.phaseState(1,3,false,true,false,0,100));
 assertEquals("in attesa",EngineOverviewPresentation.phaseState(3,3,false,false,false,101,100));
 assertEquals("attive",EngineOverviewPresentation.phaseState(3,3,true,false,false,100,100));
 assertEquals("disponibili",EngineOverviewPresentation.phaseState(4,3,false,false,false,0,100));
 }
 @Test public void everyLifecycleGateStopsMotionAndResumeAllowsItAgain(){
 assertTrue(EngineOverviewPresentation.motionAllowed(true,true,true,true,true,true,true));
 for(int i=0;i<7;i++){boolean[] g={true,true,true,true,true,true,true};g[i]=false;assertFalse(EngineOverviewPresentation.motionAllowed(g[0],g[1],g[2],g[3],g[4],g[5],g[6]));}
 assertTrue(EngineOverviewPresentation.motionAllowed(true,true,true,true,true,true,true));
 }
}
