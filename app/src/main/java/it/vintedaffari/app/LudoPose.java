package it.vintedaffari.app;
/** Reusable, time-based visual pose. No Android or data-pipeline dependencies. */
final class LudoPose {
 float bodyScaleY=1,headRotationDeg,reactionLift,gazeX,gazeY;
 static void sample(long elapsedMs,long reactionElapsedMs,boolean motionEnabled,float x,float y,LudoPose out){
  out.bodyScaleY=1;out.headRotationDeg=0;out.reactionLift=0;out.gazeX=0;out.gazeY=0;
  if(!motionEnabled)return;
  double phase=Math.max(0,elapsedMs)%4200L*(Math.PI*2/4200);
  float wave=(float)Math.sin(phase);
  out.bodyScaleY=1+wave*.004f;out.headRotationDeg=wave;
  out.gazeX=clamp(x);out.gazeY=clamp(y);
  if(reactionElapsedMs>0&&reactionElapsedMs<1600){double envelope=Math.sin(Math.PI*reactionElapsedMs/1600);out.reactionLift=(float)(envelope*envelope*.015);}
 }
 private static float clamp(float value){return Float.isFinite(value)?Math.max(-1,Math.min(1,value)):0;}
}
