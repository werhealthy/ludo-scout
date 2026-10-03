package it.vintedaffari.app;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.animation.ValueAnimator;
import java.util.EnumMap;
import java.util.Map;
/** Supplied paper illustrations, cached once and animated only while visible. */
final class LudoPetView extends View {
 private final Paint paint=new Paint(3);
 private final RectF artBounds=new RectF();
 private final Map<LudoPetMood,Bitmap> illustrations=new EnumMap<>(LudoPetMood.class);
 private Bitmap illustration;
 private LudoPetMood mood=LudoPetMood.IDLE;
 private ValueAnimator idle;
 private boolean resumed,portrait,greeting;
 private float phase,reaction,gazeX,gazeY;
 private final Runnable endGreeting=()->{greeting=false;reaction=0;invalidate();};
 LudoPetView(Context context){super(context);setContentDescription("Ludo, il tuo compagno di giochi");setFocusable(true);setOnClickListener(v->react());}
 void setIllustration(Bitmap bitmap){illustration=bitmap;invalidate();}
 void setIllustrations(Map<LudoPetMood,Bitmap> art){illustrations.clear();illustrations.putAll(art);invalidate();}
 void setMood(LudoPetMood value){mood=value==null?LudoPetMood.IDLE:value;invalidate();}
 void setResumed(boolean value){resumed=value;sync();}
 void setPortrait(boolean value){portrait=value;invalidate();}
 void setExplorer(boolean value){if(value)setMood(LudoPetMood.SEARCHING);}
 void lookAtGame(boolean value){lookTowards(value?1:0,0);}
 void lookTowards(float x,float y){gazeX=Math.max(-1,Math.min(1,x));gazeY=Math.max(-1,Math.min(1,y));invalidate();}
 void react(){removeCallbacks(endGreeting);greeting=true;reaction=ValueAnimator.areAnimatorsEnabled()&&resumed?1f:0f;announceForAccessibility("Ludo ti saluta");postDelayed(endGreeting,1600);invalidate();}
 private void sync(){
  boolean run=LudoPetState.animate(ValueAnimator.areAnimatorsEnabled(),resumed,isAttachedToWindow()&&getWindowVisibility()==VISIBLE&&isShown());
  if(!run){if(idle!=null){idle.cancel();idle=null;}phase=0;reaction=0;greeting=false;removeCallbacks(endGreeting);invalidate();return;}
  if(idle!=null)return;
  idle=ValueAnimator.ofFloat(0,1);idle.setDuration(4200);idle.setRepeatCount(ValueAnimator.INFINITE);idle.setInterpolator(new android.view.animation.LinearInterpolator());idle.addUpdateListener(a->{phase=(Float)a.getAnimatedValue();reaction=Math.max(0,reaction-.018f);invalidate();});idle.start();
 }
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();sync();}
 @Override protected void onDetachedFromWindow(){removeCallbacks(endGreeting);if(idle!=null){idle.cancel();idle=null;}super.onDetachedFromWindow();}
 @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);sync();}
 @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(paint!=null)sync();}
 @Override protected void onDraw(Canvas canvas){
  super.onDraw(canvas);Bitmap art=illustrations.get(greeting?LudoPetMood.GREETING:mood);if(art==null)art=illustration;if(art==null||art.isRecycled())return;
  float scale=Math.min(getWidth()/(float)art.getWidth(),getHeight()/(float)art.getHeight())*.94f;
  float width=art.getWidth()*scale,height=art.getHeight()*scale;
  float breath=(float)Math.sin(phase*Math.PI*2)*getHeight()*.004f+reaction*getHeight()*.015f;
  artBounds.set((getWidth()-width)/2,(getHeight()-height)/2-breath,(getWidth()+width)/2,(getHeight()+height)/2-breath);
  int saved=canvas.save();if(portrait)canvas.rotate(gazeX*2+reaction*2,getWidth()/2f,getHeight()*.6f);canvas.translate(gazeX*getWidth()*.008f,gazeY*getHeight()*.004f);canvas.drawBitmap(art,null,artBounds,paint);canvas.restoreToCount(saved);
 }
}
