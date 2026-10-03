package it.vintedaffari.app;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
/** Six native, independently accessible controls around a measured native center. */
final class LudoOrbitView extends ViewGroup {
 private final Paint ring=new Paint(Paint.ANTI_ALIAS_FLAG);
 private final RectF ellipse=new RectF();
 private int nodeHeight;
 LudoOrbitView(Context context){super(context);setWillNotDraw(false);ring.setColor(0xff34364e);ring.setStyle(Paint.Style.STROKE);ring.setStrokeWidth(getResources().getDisplayMetrics().density);}
 @Override protected void onMeasure(int widthSpec,int heightSpec){
  int width=MeasureSpec.getSize(widthSpec),col=width/3;nodeHeight=1;
  for(int i=0;i<getChildCount();i++){
   View child=getChildAt(i);int childWidth=i==0||i>=5?width-2*col:col;
   child.measure(MeasureSpec.makeMeasureSpec(childWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
   nodeHeight=Math.max(nodeHeight,i==6?(child.getMeasuredHeight()+1)/2:child.getMeasuredHeight());
  }
  setMeasuredDimension(width,resolveSize(LudoOrbitGeometry.height(nodeHeight),heightSpec));
 }
 @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
  int[][] bounds=LudoOrbitGeometry.bounds(r-l,nodeHeight);
  for(int i=0;i<getChildCount();i++){View child=getChildAt(i);int[] box=bounds[i];int y=box[1]+(box[3]-child.getMeasuredHeight())/2;child.layout(box[0],y,box[0]+box[2],y+child.getMeasuredHeight());}
  ellipse.set((r-l)/6f,nodeHeight*.5f,(r-l)*5/6f,nodeHeight*3.5f);
 }
 @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.drawOval(ellipse,ring);}
}
