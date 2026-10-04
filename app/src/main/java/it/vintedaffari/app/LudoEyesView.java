package it.vintedaffari.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** The mascot's two scribbled eyes; animation never represents measured progress. */
final class LudoEyesView extends View {
 private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
 private final Path spiral=new Path();
 private ValueAnimator motion;
 private float angle;
 private boolean running=true;
 LudoEyesView(Context context){super(context);setContentDescription("Caricamento in corso");
  for(int i=0;i<=100;i++){double a=i*.13;float radius=.035f+i*.0034f,x=(float)Math.cos(a)*radius,y=(float)Math.sin(a)*radius;if(i==0)spiral.moveTo(x,y);else spiral.lineTo(x,y);}
 }
 void setRunning(boolean value){running=value;sync();}
 private void sync(){boolean allowed=running&&ValueAnimator.areAnimatorsEnabled()&&isAttachedToWindow()&&isShown()&&getWindowVisibility()==VISIBLE&&hasWindowFocus();
  if(!allowed){if(motion!=null){motion.cancel();motion=null;}angle=0;invalidate();return;}if(motion!=null)return;
  motion=ValueAnimator.ofFloat(0,360);motion.setDuration(2000);motion.setRepeatCount(ValueAnimator.INFINITE);motion.setInterpolator(new android.view.animation.LinearInterpolator());motion.addUpdateListener(a->{angle=(Float)a.getAnimatedValue();invalidate();});motion.start();
 }
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();sync();}
 @Override protected void onDetachedFromWindow(){if(motion!=null){motion.cancel();motion=null;}super.onDetachedFromWindow();}
 @Override protected void onWindowVisibilityChanged(int v){super.onWindowVisibilityChanged(v);if(ink!=null)sync();}
 @Override protected void onVisibilityChanged(View v,int visibility){super.onVisibilityChanged(v,visibility);if(ink!=null)sync();}
 @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);sync();}
 @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float unit=Math.min(getWidth()/2.3f,getHeight());
  for(int eye=0;eye<2;eye++){int save=canvas.save();canvas.translate(getWidth()/2f+(eye==0?-.58f:.58f)*unit,getHeight()/2f);canvas.rotate(eye==0?-8:8);canvas.scale(unit,unit);
   ink.setStyle(Paint.Style.FILL);ink.setColor(0xfff7e9a0);canvas.drawOval(-.48f,-.47f,.48f,.47f,ink);
   ink.setStyle(Paint.Style.STROKE);ink.setStrokeCap(Paint.Cap.ROUND);ink.setStrokeWidth(.035f);ink.setColor(0xff63304e);canvas.drawOval(-.48f,-.47f,.48f,.47f,ink);
   canvas.rotate(eye==0?angle:-angle);canvas.drawPath(spiral,ink);canvas.restoreToCount(save);
  }
 }
}
