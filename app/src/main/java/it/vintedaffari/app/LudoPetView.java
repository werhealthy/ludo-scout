package it.vintedaffari.app;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.animation.ValueAnimator;
/** Flat vector pet with lifecycle-bound motion and no per-frame bitmap allocations. */
final class LudoPetView extends View {
 private final Paint paint=new Paint(3);private final Path mouth=new Path();
 private ValueAnimator idle;private boolean resumed;private float phase,reaction;private boolean lookingRight;
 LudoPetView(Context context){super(context);setContentDescription("Ludo, il tuo compagno di giochi");setFocusable(true);setOnClickListener(v->react());}
 void setResumed(boolean value){resumed=value;sync();}
 void lookAtGame(boolean value){lookingRight=value;invalidate();}
 void react(){reaction=ValueAnimator.areAnimatorsEnabled()?1f:0f;announceForAccessibility("Ludo ti saluta");invalidate();}
 private void sync(){boolean run=LudoPetState.animate(ValueAnimator.areAnimatorsEnabled(),resumed,isAttachedToWindow()&&getWindowVisibility()==VISIBLE&&isShown());if(!run){if(idle!=null){idle.cancel();idle=null;}phase=0;reaction=0;invalidate();return;}if(idle!=null)return;idle=ValueAnimator.ofFloat(0,1);idle.setDuration(4200);idle.setRepeatCount(ValueAnimator.INFINITE);idle.setInterpolator(new android.view.animation.LinearInterpolator());idle.addUpdateListener(a->{phase=(Float)a.getAnimatedValue();reaction=Math.max(0,reaction-.018f);invalidate();});idle.start();}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();sync();}
 @Override protected void onDetachedFromWindow(){if(idle!=null){idle.cancel();idle=null;}super.onDetachedFromWindow();}
 @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);sync();}
 @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(paint!=null)sync();}
 @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float unit=Math.min(getWidth()/300f,getHeight()/300f);canvas.save();canvas.translate((getWidth()-300*unit)/2,(getHeight()-300*unit)/2);canvas.scale(unit,unit);
 paint.setStyle(Paint.Style.FILL);paint.setColor(0xff241637);canvas.drawOval(45,260,255,282,paint);
 float breath=(float)Math.sin(phase*Math.PI*2)*2+reaction*6;canvas.save();canvas.translate(0,-breath);
 paint.setColor(0xffA26AF5);canvas.drawOval(35,80,265,261,paint);canvas.drawCircle(150,72,51,paint);canvas.drawCircle(80,100,48,paint);canvas.drawCircle(220,100,48,paint);canvas.drawCircle(49,155,43,paint);canvas.drawCircle(251,155,43,paint);canvas.drawCircle(63,211,43,paint);canvas.drawCircle(237,211,43,paint);canvas.drawOval(105,235,139,274,paint);canvas.drawOval(166,235,200,274,paint);
 float blink=phase>.90f&&phase<.94f?.12f:1f;float glance=lookingRight?9:reaction*4;
 for(int i=0;i<2;i++){float x=i==0?111:191,y=i==0?158:151;canvas.save();canvas.scale(1,blink,x,y);paint.setColor(0xffFFFFFF);canvas.drawOval(x-34,y-39,x+34,y+39,paint);paint.setColor(0xff101017);canvas.drawOval(x-19+glance,y-26,x+20+glance,y+27,paint);paint.setColor(0xffFFFFFF);canvas.drawCircle(x-9+glance,y-19,7,paint);canvas.restore();}
 paint.setColor(0xff30134C);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(8);paint.setStrokeCap(Paint.Cap.ROUND);
 mouth.reset();mouth.moveTo(83,105);mouth.quadTo(109,114,132,106);canvas.drawPath(mouth,paint);mouth.reset();mouth.moveTo(171,99);mouth.quadTo(192,85,215,98);canvas.drawPath(mouth,paint);
 mouth.reset();mouth.moveTo(135,211);mouth.cubicTo(136,233,151,234,160,220);mouth.cubicTo(171,236,187,229,188,211);canvas.drawPath(mouth,paint);paint.setStyle(Paint.Style.FILL);canvas.restore();canvas.restore();}
}