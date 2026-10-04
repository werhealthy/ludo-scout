package it.vintedaffari.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class LudoPoseTest {
 @Test public void refreshRateDoesNotChangePose(){
  LudoPose expected=new LudoPose();LudoPose.sample(900,400,true,.5f,-.5f,expected);
  for(int fps:new int[]{30,60,120}){LudoPose actual=new LudoPose();for(long t=0;t<900;t+=1000/fps)LudoPose.sample(t,t,true,0,0,actual);LudoPose.sample(900,400,true,.5f,-.5f,actual);assertEquals(expected.bodyScaleY,actual.bodyScaleY,0);assertEquals(expected.headRotationDeg,actual.headRotationDeg,0);assertEquals(expected.reactionLift,actual.reactionLift,0);}
 }
 @Test public void reducedMotionIsNeutral(){LudoPose p=new LudoPose();LudoPose.sample(500,800,false,1,1,p);assertEquals(1,p.bodyScaleY,0);assertEquals(0,p.headRotationDeg,0);assertEquals(0,p.reactionLift,0);assertEquals(0,p.gazeX,0);assertEquals(0,p.gazeY,0);}
 @Test public void tapEnvelopeStartsAndEndsAtRest(){LudoPose p=new LudoPose();for(long t:new long[]{-1,0,1600,5000}){LudoPose.sample(0,t,true,0,0,p);assertEquals(0,p.reactionLift,0);}LudoPose.sample(0,800,true,0,0,p);assertEquals(.015f,p.reactionLift,.000001f);}
 @Test public void negativeClockAndInvalidGazeStayFinite(){LudoPose p=new LudoPose();LudoPose.sample(-90,-5,true,Float.NaN,Float.POSITIVE_INFINITY,p);assertEquals(1,p.bodyScaleY,0);assertTrue(Float.isFinite(p.headRotationDeg));assertTrue(Float.isFinite(p.gazeX));assertTrue(Float.isFinite(p.gazeY));}
 @Test public void idleIsBoundedAndPeriodic(){LudoPose p=new LudoPose(),q=new LudoPose();for(int t=0;t<=4200;t+=17){LudoPose.sample(t,-1,true,9,-9,p);assertTrue(p.bodyScaleY>=.988f&&p.bodyScaleY<=1.012f);assertTrue(Math.abs(p.headRotationDeg)<=2);assertEquals(1,p.gazeX,0);assertEquals(-1,p.gazeY,0);}LudoPose.sample(73,-1,true,0,0,p);LudoPose.sample(4273,-1,true,0,0,q);assertEquals(p.bodyScaleY,q.bodyScaleY,0);}
 @Test public void retriggerKeepsCurrentLiftAndReturnsToRest(){LudoPose p=new LudoPose();LudoPose.sample(0,0,true,0,0,.012f,p);assertEquals(.012f,p.reactionLift,0);LudoPose.sample(0,800,true,0,0,.012f,p);assertEquals(.015f,p.reactionLift,.000001f);LudoPose.sample(0,1600,true,0,0,.012f,p);assertEquals(0,p.reactionLift,0);}
 @Test public void blinkIsTimeBasedBoundedAndDisabledWithMotion(){LudoPose p=new LudoPose();LudoPose.sample(0,-1,true,0,0,p);assertEquals(1,p.eyeOpen,0);LudoPose.sample(3600,-1,true,0,0,p);assertEquals(0,p.eyeOpen,.00001f);LudoPose.sample(3700,-1,true,0,0,p);assertEquals(1,p.eyeOpen,0);LudoPose.sample(3600,800,false,1,1,p);assertEquals(1,p.eyeOpen,0);assertEquals(0,p.armRotationDeg,0);}
 @Test public void tapMovesArmsWithinSafeRange(){LudoPose p=new LudoPose();LudoPose.sample(0,800,true,0,0,p);assertTrue(p.armRotationDeg>10&&p.armRotationDeg<=14);LudoPose.sample(0,1600,true,0,0,p);assertEquals(0,p.armRotationDeg,0);}
}

