package it.vintedaffari.app;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
/** Persistent room: atmosphere and shelf contents change; the actor stays in place. */
final class LudoRoomBackdropView extends View {
 private final Paint paint=new Paint(3),fade=new Paint(3);
 private final int background;
 private boolean resumed;
 private static final int[] BOOK_COLORS={0xff887399,0xff629085,0xffb99a61,0xffa57485,0xff7a81a7};
 private int from=0xff302540,to=from;
 private float blend=1,phase;
 private String room=LudoRoomState.EXPLORE;
 private ValueAnimator transition,particles;
 LudoRoomBackdropView(Context context,int background){super(context);this.background=background;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
 private Bitmap bitmap,previousBitmap;
 private final Rect imageSource=new Rect();
 private final RectF imageTarget=new RectF();
 private float imageBlend=1;
 private ValueAnimator imageTransition;
 void setBitmap(Bitmap value){
  if(value==null||value.isRecycled()||value==bitmap)return;
  if(imageTransition!=null){imageTransition.cancel();imageTransition=null;}
  previousBitmap=bitmap;bitmap=value;imageBlend=1;
  if(previousBitmap!=null&&shouldAnimate()){
   imageBlend=0;imageTransition=ValueAnimator.ofFloat(0,1);imageTransition.setDuration(450);
   imageTransition.addUpdateListener(a->{imageBlend=(Float)a.getAnimatedValue();invalidate();});imageTransition.start();
  }else previousBitmap=null;
  invalidate();
 }
 private void drawRoom(Canvas canvas,Bitmap image,int alpha){
  if(image==null||image.isRecycled())return;
  // The approved Preferiti source includes a white export margin at its left edge.
  int left=image.getWidth()==916?24:0;
  imageSource.set(left,0,image.getWidth(),image.getHeight());
  float scale=Math.max(getWidth()/(float)imageSource.width(),getHeight()/(float)imageSource.height());
  float w=imageSource.width()*scale,h=imageSource.height()*scale;
  imageTarget.set((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);
  paint.setAlpha(alpha);canvas.drawBitmap(image,imageSource,imageTarget,paint);paint.setAlpha(255);
 }
 void setResumed(boolean value){resumed=value;sync();}
 private boolean shouldAnimate(){return resumed&&ValueAnimator.areAnimatorsEnabled()&&isAttachedToWindow()&&isShown()&&getWindowVisibility()==VISIBLE&&hasWindowFocus();}
 void setRoom(String value){if(value.equals(room))return;room=value;from=mix(from,to,blend);to=LudoRoomState.HUNTS.equals(value)?0xff3d263a:LudoRoomState.HOME.equals(value)?0xff243b3b:0xff302540;
  if(transition!=null)transition.cancel();if(!shouldAnimate()){blend=1;invalidate();return;}
  blend=0;transition=ValueAnimator.ofFloat(0,1);transition.setDuration(450);transition.addUpdateListener(a->{blend=(Float)a.getAnimatedValue();invalidate();});transition.start();
 }
 private int mix(int a,int b,float p){return Color.rgb(Math.round(Color.red(a)+(Color.red(b)-Color.red(a))*p),Math.round(Color.green(a)+(Color.green(b)-Color.green(a))*p),Math.round(Color.blue(a)+(Color.blue(b)-Color.blue(a))*p));}
 private void sync(){boolean run=shouldAnimate();
  if(!run){if(imageTransition!=null){imageTransition.cancel();imageTransition=null;imageBlend=1;previousBitmap=null;}if(particles!=null){particles.cancel();particles=null;}if(transition!=null){transition.cancel();transition=null;blend=1;}phase=0;invalidate();return;}if(particles!=null)return;
  particles=ValueAnimator.ofFloat(0,1);particles.setDuration(9000);particles.setRepeatCount(ValueAnimator.INFINITE);particles.setInterpolator(new android.view.animation.LinearInterpolator());particles.addUpdateListener(a->{if(!shouldAnimate()){sync();return;}phase=(Float)a.getAnimatedValue();invalidate();});particles.start();
 }
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();sync();}
 @Override protected void onDetachedFromWindow(){if(imageTransition!=null){imageTransition.cancel();imageTransition=null;}previousBitmap=null;imageBlend=1;if(particles!=null){particles.cancel();particles=null;}if(transition!=null)transition.cancel();super.onDetachedFromWindow();}
 @Override protected void onWindowVisibilityChanged(int value){super.onWindowVisibilityChanged(value);if(paint!=null)sync();}
 @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(paint!=null)sync();}
 @Override public void onWindowFocusChanged(boolean value){super.onWindowFocusChanged(value);sync();}
 @Override protected void onSizeChanged(int w,int h,int ow,int oh){fade.setShader(new LinearGradient(0,h*.76f,0,h,background&0x00ffffff,background,Shader.TileMode.CLAMP));}
 @Override protected void onDraw(Canvas c){float w=getWidth(),h=getHeight();c.drawColor(mix(from,to,blend));
  if(bitmap!=null){drawRoom(c,previousBitmap,255);drawRoom(c,bitmap,Math.round(imageBlend*255));}
  for(int i=0;i<9;i++){float x=w*(.09f+(i*.117f)% .85f),y=h*(.38f+((i*.093f+phase*.12f)% .34f));paint.setColor(0xffedcf91);paint.setAlpha(35+(int)(35*Math.sin((phase+i*.12)*Math.PI*2)));c.drawCircle(x,y,1.2f*getResources().getDisplayMetrics().density,paint);}paint.setAlpha(255);
  // Keep the original illustration continuous behind the overlaid controls.
 }
}

