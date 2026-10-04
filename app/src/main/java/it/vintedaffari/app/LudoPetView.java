package it.vintedaffari.app;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;
import android.animation.ValueAnimator;
import android.os.SystemClock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
/** Approved mascot with time-based native motion and an optional illustrated rig. */
final class LudoPetView extends View {
 private final LudoCharacterRenderer renderer=new LudoCharacterRenderer();
 private final RectF artBounds=new RectF();
 private final LudoPose pose=new LudoPose();
 private final Map<LudoPetMood,Bitmap> illustrations=new EnumMap<>(LudoPetMood.class);
 private Bitmap illustration;
 private LudoPetMood mood=LudoPetMood.IDLE;
 private ValueAnimator idle;
 private boolean resumed,portrait,greeting,windowFocused;
 private float gazeX,gazeY,reactionBaseLift;
 private long idleStarted,reactionStarted=-1;
 private final Runnable endGreeting=()->{greeting=false;reactionStarted=-1;reactionBaseLift=0;invalidate();};
 LudoPetView(Context context){super(context);setContentDescription("Ludo, il tuo compagno di giochi");setFocusable(true);setOnClickListener(v->react());}
 void setIllustration(Bitmap bitmap){illustration=bitmap;invalidate();}
 void setIllustrations(Map<LudoPetMood,Bitmap> art){illustrations.clear();illustrations.putAll(art);invalidate();}
 void setParts(List<LudoPart> parts,Map<String,Bitmap> images,float width,float height){renderer.setParts(parts,images,width,height);invalidate();}
 void setMood(LudoPetMood value){mood=value==null?LudoPetMood.IDLE:value;invalidate();}
 void setResumed(boolean value){resumed=value;sync();}
 void setPortrait(boolean value){portrait=value;invalidate();}
 void setExplorer(boolean value){if(value)setMood(LudoPetMood.SEARCHING);}
 void lookAtGame(boolean value){lookTowards(value?1:0,0);}
 void lookTowards(float x,float y){gazeX=Float.isFinite(x)?Math.max(-1,Math.min(1,x)):0;gazeY=Float.isFinite(y)?Math.max(-1,Math.min(1,y)):0;invalidate();}
 void react(){
  sync();removeCallbacks(endGreeting);announceForAccessibility("Ludo ti saluta");
  long now=SystemClock.uptimeMillis();greeting=idle!=null;
  LudoPose.sample(greeting?now-idleStarted:0,greeting&&reactionStarted>=0?now-reactionStarted:-1,greeting,gazeX,gazeY,reactionBaseLift,pose);
  reactionBaseLift=pose.reactionLift;reactionStarted=greeting?now:-1;
  if(greeting)postDelayed(endGreeting,1600);invalidate();
 }
 private boolean shouldAnimate(){return LudoPetState.animate(ValueAnimator.areAnimatorsEnabled(),resumed,isAttachedToWindow()&&getWindowVisibility()==VISIBLE&&isShown()&&windowFocused);}
 private void stopMotion(){if(idle!=null){idle.cancel();idle=null;}idleStarted=0;reactionStarted=-1;reactionBaseLift=0;greeting=false;removeCallbacks(endGreeting);invalidate();}
 private void sync(){
  if(!shouldAnimate()){stopMotion();return;}if(idle!=null)return;
  idleStarted=SystemClock.uptimeMillis();
  idle=ValueAnimator.ofFloat(0,1);idle.setDuration(4200);idle.setRepeatCount(ValueAnimator.INFINITE);idle.setInterpolator(new android.view.animation.LinearInterpolator());
  idle.addUpdateListener(a->{if(!shouldAnimate()){stopMotion();return;}invalidate();});idle.start();
 }
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();windowFocused=hasWindowFocus();sync();}
 @Override protected void onDetachedFromWindow(){stopMotion();windowFocused=false;super.onDetachedFromWindow();}
 @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(renderer!=null)sync();}
 @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(renderer!=null)sync();}
 @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);windowFocused=focused;if(renderer!=null)sync();}
 @Override protected void onDraw(Canvas canvas){
  super.onDraw(canvas);boolean moving=idle!=null&&shouldAnimate();long now=SystemClock.uptimeMillis();
  LudoPose.sample(moving?now-idleStarted:0,moving&&reactionStarted>=0?now-reactionStarted:-1,moving,gazeX,gazeY,reactionBaseLift,pose);
  Bitmap art=illustrations.get(greeting?LudoPetMood.GREETING:mood);if(art==null)art=illustration;renderer.setFallback(art);
  artBounds.set(getWidth()*.03f,getHeight()*.03f,getWidth()*.97f,getHeight()*.97f);renderer.draw(canvas,artBounds,pose,portrait);
 }
}
