package it.vintedaffari.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class EngineMotionStateTest {
 @Test public void freshReturnAnimatesOnlyOnce(){EngineMotionState s=new EngineMotionState();s.restore("run",new int[]{400,0,0,0,0});int[] now={450,0,0,0,0};assertEquals(400,s.consume("run",10,now)[0]);assertEquals(450,s.consume("run",10,now)[0]);assertEquals(450,s.consume("run",11,new int[]{430,0,0,0,0})[0]);assertEquals(430,s.consume("run",11,new int[]{430,0,0,0,0})[0]);}
 @Test public void differentScrollInitializesWithoutFalseDelta(){EngineMotionState s=new EngineMotionState();s.restore("old",new int[]{400,0,0,0,0});assertEquals(3,s.consume("new",10,new int[]{3,0,0,0,0})[0]);}
 @Test public void ownsDefensiveCopies(){EngineMotionState s=new EngineMotionState();int[] data={4,0,0,0,0};s.restore("run",data);data[0]=999;assertEquals(4,s.consume("run",1,new int[]{5,0,0,0,0})[0]);}
 @Test public void firstVisitHasNoInventedChanges(){EngineMotionState s=new EngineMotionState();assertEquals(5,s.consume("run",1,new int[]{5,0,0,0,0})[0]);}
}
