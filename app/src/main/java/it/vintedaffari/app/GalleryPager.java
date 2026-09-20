package it.vintedaffari.app;
import android.content.Context;import android.view.*;import android.widget.*;
/** Native horizontal drag, including slow swipes. Each photo owns its own ImageView. */
public final class GalleryPager extends HorizontalScrollView {
 private final LinearLayout pages;private int index,startIndex;private float downX;private java.util.function.IntConsumer listener;
 public GalleryPager(Context c){super(c);setHorizontalScrollBarEnabled(false);setFillViewport(true);setOverScrollMode(OVER_SCROLL_NEVER);pages=new LinearLayout(c);addView(pages,new LayoutParams(-2,-1));}
 public void addPage(View view){pages.addView(view,new LinearLayout.LayoutParams(Math.max(1,getWidth()),-1));}
 public void setListener(java.util.function.IntConsumer l){listener=l;}
 public void go(int page){index=GalleryPosition.clamp(page,pages.getChildCount());smoothScrollTo(index*getWidth(),0);if(listener!=null)listener.accept(index);}
 @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);for(int i=0;i<pages.getChildCount();i++){View v=pages.getChildAt(i);v.setLayoutParams(new LinearLayout.LayoutParams(w,-1));}post(()->scrollTo(index*w,0));}
 @Override public boolean onInterceptTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();startIndex=index;}return super.onInterceptTouchEvent(e);}
 @Override public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();startIndex=index;}boolean handled=super.onTouchEvent(e);if(e.getActionMasked()==MotionEvent.ACTION_UP){go(GalleryPosition.afterDrag(startIndex,downX-e.getX(),getWidth(),pages.getChildCount()));}else if(e.getActionMasked()==MotionEvent.ACTION_CANCEL)go(startIndex);return handled;}
 @Override public void fling(int velocity){/* Settled by distance in onTouchEvent; slow swipes work too. */}
}
