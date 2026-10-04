package it.vintedaffari.app;
/** Reusable, time-based visual pose. No Android or data-pipeline dependencies. */
final class LudoPose {
 float bodyScaleY=1,headRotationDeg,reactionLift,gazeX,gazeY,eyeOpen=1,armRotationDeg;
 static void sample(long elapsedMs,long reactionElapsedMs,boolean motionEnabled,float x,float y,LudoPose out){
  sample(elapsedMs,reactionElapsedMs,motionEnabled,x,y,0,out);
 }
 static void sample(long elapsedMs,long reactionElapsedMs,boolean motionEnabled,float x,float y,float initialLift,LudoPose out){
  out.eyeOpen=1;out.armRotationDeg=0;out.bodyScaleY=1;out.headRotationDeg=0;out.reactionLift=0;out.gazeX=0;out.gazeY=0;
  if(!motionEnabled)return;
  double phase=Math.max(0,elapsedMs)%4200L*(Math.PI*2/4200);
  float wave=(float)Math.sin(phase);
  out.bodyScaleY=1+wave*.012f;out.headRotationDeg=wave*2;
  out.gazeX=clamp(x+wave*.45f);out.gazeY=clamp(y+(float)Math.sin(phase*.5)*.12f);
  long blinkTime=Math.max(0,elapsedMs)%6000L;
  if(blinkTime>=3500&&blinkTime<=3700)out.eyeOpen=Math.abs(blinkTime-3600)/100f;
  if(reactionElapsedMs>=0&&reactionElapsedMs<1600){double t=reactionElapsedMs/1600.0,waveTap=Math.sin(Math.PI*t),envelope=waveTap*waveTap;
   float start=Float.isFinite(initialLift)?Math.max(0,Math.min(.015f,initialLift)):0;
   double fade=1-t*t*(3-2*t);out.reactionLift=(float)(envelope*.015+start*(1-envelope)*fade);
  }
  out.armRotationDeg=out.reactionLift/.015f*14f;
 }
 private static float clamp(float value){return Float.isFinite(value)?Math.max(-1,Math.min(1,value)):0;}
}

