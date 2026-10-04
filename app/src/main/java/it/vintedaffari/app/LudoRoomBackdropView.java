package it.vintedaffari.app;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
/** Persistent room: atmosphere and shelf contents change; the actor stays in place. */
final class LudoRoomBackdropView extends View {
 private final Paint paint=new Paint(3),fade=new Paint(3);
 private final int background;
 private int from=0xff302540,to=from;
 private float blend=1,phase;
 private String room=LudoRoomState.EXPLORE;
 private ValueAnimator transition,particles;
 LudoRoomBackdropView(Context context,int background){super(context);this.background=background;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
 void setBitmap(Bitmap ignored){}
 void setRoom(String value){if(value.equals(room))return;room=value;from=mix(from,to,blend);to=LudoRoomState.HUNTS.equals(value)?0xff3d263a:LudoRoomState.HOME.equals(value)?0xff243b3b:0xff302540;
  if(transition!=null)transition.cancel();if(!ValueAnimator.areAnimatorsEnabled()){blend=1;invalidate();return;}
  blend=0;transition=ValueAnimator.ofFloat(0,1);transition.setDuration(450);transition.addUpdateListener(a->{blend=(Float)a.getAnimatedValue();invalidate();});transition.start();
 }
 private int mix(int a,int b,float p){return Color.rgb(Math.round(Color.red(a)+(Color.red(b)-Color.red(a))*p),Math.round(Color.green(a)+(Color.green(b)-Color.green(a))*p),Math.round(Color.blue(a)+(Color.blue(b)-Color.blue(a))*p));}
 private void sync(){boolean run=ValueAnimator.areAnimatorsEnabled()&&isAttachedToWindow()&&isShown()&&getWindowVisibility()==VISIBLE&&hasWindowFocus();
  if(!run){if(particles!=null){particles.cancel();particles=null;}return;}if(particles!=null)return;
  particles=ValueAnimator.ofFloat(0,1);particles.setDuration(9000);particles.setRepeatCount(ValueAnimator.INFINITE);particles.setInterpolator(new android.view.animation.LinearInterpolator());particles.addUpdateListener(a->{phase=(Float)a.getAnimatedValue();invalidate();});particles.start();
 }
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();sync();}
 @Override protected void onDetachedFromWindow(){if(particles!=null){particles.cancel();particles=null;}if(transition!=null)transition.cancel();super.onDetachedFromWindow();}
 @Override protected void onWindowVisibilityChanged(int value){super.onWindowVisibilityChanged(value);if(paint!=null)sync();}
 @Override public void onWindowFocusChanged(boolean value){super.onWindowFocusChanged(value);sync();}
 @Override protected void onSizeChanged(int w,int h,int ow,int oh){fade.setShader(new LinearGradient(0,h*.76f,0,h,background&0x00ffffff,background,Shader.TileMode.CLAMP));}
 @Override protected void onDraw(Canvas c){float w=getWidth(),h=getHeight();c.drawColor(mix(from,to,blend));
  paint.setStyle(Paint.Style.FILL);paint.setColor(0xff493b4a);c.drawRoundRect(new RectF(w*.04f,h*.45f,w*.21f,h*.70f),w*.08f,w*.08f,paint);paint.setColor(0xffb3a0bd);paint.setAlpha(65);c.drawOval(w*.055f,h*.47f,w*.195f,h*.64f,paint);paint.setAlpha(255);
  paint.setColor(0xff614451);c.drawRoundRect(new RectF(w*.73f,h*.56f,w*.96f,h*.60f),3,3,paint);
  int count=LudoRoomState.HOME.equals(room)?5:LudoRoomState.HUNTS.equals(room)?3:2;int[] colors={0xff887399,0xff629085,0xffb99a61,0xffa57485,0xff7a81a7};
  for(int i=0;i<count;i++){paint.setColor(colors[i]);float x=w*(.75f+i*.033f);c.drawRoundRect(new RectF(x,h*(.46f-(i%2)*.025f),x+w*.026f,h*.56f),2,2,paint);}
  paint.setColor(0xff211b2b);c.drawRect(0,h*.80f,w,h,paint);paint.setColor(0xff504053);paint.setStrokeWidth(1);for(int i=0;i<5;i++)c.drawLine(w*i/4,h*.8f,w*(i-.4f)/4,h,paint);
  for(int i=0;i<9;i++){float x=w*(.09f+(i*.117f)% .85f),y=h*(.38f+((i*.093f+phase*.12f)% .34f));paint.setColor(0xffedcf91);paint.setAlpha(35+(int)(35*Math.sin((phase+i*.12)*Math.PI*2)));c.drawCircle(x,y,1.2f*getResources().getDisplayMetrics().density,paint);}paint.setAlpha(255);
  c.drawRect(0,0,w,h,fade);
 }
}
