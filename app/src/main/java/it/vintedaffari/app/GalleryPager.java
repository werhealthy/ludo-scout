package it.vintedaffari.app;
import android.content.Context;import android.view.*;import android.widget.*;
/** Native horizontal drag. Initial selection is settled only after page measurement/layout. */
public final class GalleryPager extends HorizontalScrollView {
 private final LinearLayout pages;private int index,startIndex;private float downX;private boolean pendingPosition=true;private java.util.function.IntConsumer listener;
 public GalleryPager(Context c){super(c);setHorizontalScrollBarEnabled(false);setFillViewport(true);setOverScrollMode(OVER_SCROLL_NEVER);pages=new LinearLayout(c);addView(pages,new LayoutParams(-2,-1));}
 public void addPage(View view){pages.addView(view,new LinearLayout.LayoutParams(Math.max(1,getWidth()),-1));pendingPosition=true;}
 public void setListener(java.util.function.IntConsumer l){listener=l;}
 public void go(int page){index=GalleryPosition.clamp(page,pages.getChildCount());pendingPosition=true;if(getWidth()>0&&pages.getMeasuredWidth()>=getWidth()*pages.getChildCount()){smoothScrollTo(index*getWidth(),0);pendingPosition=false;}if(listener!=null)listener.accept(index);}
 @Override protected void onMeasure(int widthSpec,int heightSpec){int width=Math.max(1,MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight());for(int i=0;i<pages.getChildCount();i++){View v=pages.getChildAt(i);LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)v.getLayoutParams();if(lp.width!=width){lp.width=width;v.setLayoutParams(lp);}}super.onMeasure(widthSpec,heightSpec);}
 @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);pendingPosition=true;}
 @Override protected void onLayout(boolean changed,int l,int t,int r,int b){super.onLayout(changed,l,t,r,b);if(pendingPosition&&getWidth()>0){scrollTo(index*getWidth(),0);pendingPosition=false;}}
 @Override public boolean onInterceptTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();startIndex=index;}return super.onInterceptTouchEvent(e);}
 @Override public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();startIndex=index;}boolean handled=super.onTouchEvent(e);if(e.getActionMasked()==MotionEvent.ACTION_UP){go(GalleryPosition.afterDrag(startIndex,downX-e.getX(),getWidth(),pages.getChildCount()));}else if(e.getActionMasked()==MotionEvent.ACTION_CANCEL)go(startIndex);return handled;}
 @Override public void fling(int velocity){/* Settled by distance in onTouchEvent; slow swipes work too. */}
}
