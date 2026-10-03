package it.vintedaffari.app;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
/** Full-width room art, faded into the app background, with no hero frame. */
final class LudoRoomBackdropView extends View {
 private final Paint paint=new Paint(3),fade=new Paint(3);
 private final RectF bounds=new RectF();
 private final int background;
 private Bitmap bitmap;
 LudoRoomBackdropView(Context context,int background){super(context);this.background=background;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
 void setBitmap(Bitmap value){bitmap=value;invalidate();}
 @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){fade.setShader(new LinearGradient(0,h*.67f,0,h,background&0x00ffffff,background,Shader.TileMode.CLAMP));}
 @Override protected void onDraw(Canvas canvas){
  canvas.drawColor(background);
  if(bitmap!=null&&!bitmap.isRecycled()){bounds.set(0,0,getWidth(),getWidth()*bitmap.getHeight()/(float)bitmap.getWidth());canvas.drawBitmap(bitmap,null,bounds,paint);}
  canvas.drawRect(0,0,getWidth(),getHeight(),fade);
 }
}
