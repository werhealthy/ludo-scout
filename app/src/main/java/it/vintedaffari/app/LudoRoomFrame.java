package it.vintedaffari.app;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
/** Background, actor, optional prop, heading and lateral controls are separate layers. */
final class LudoRoomFrame extends FrameLayout {
 private final boolean library;
 private int actorY;
 private int heightBudget;
 void setHeightBudget(int value){if(heightBudget!=value){heightBudget=value;requestLayout();}}
 LudoRoomFrame(Context context,boolean library){super(context);this.library=library;}
 @Override protected void onMeasure(int widthSpec,int heightSpec){
  int w=MeasureSpec.getSize(widthSpec),pad=Math.round(18*getResources().getDisplayMetrics().density);
  getChildAt(3).measure(MeasureSpec.makeMeasureSpec(Math.max(0,w-2*pad),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
  actorY=getChildAt(3).getMeasuredHeight()+pad;
  int actorW=Math.round(w*.42f),actorH=Math.round(w*.40f);
  int h=actorY+actorH+pad;
  if(heightBudget>0&&h>heightBudget){actorH=Math.max(0,heightBudget-actorY-pad);actorW=Math.min(actorW,Math.round(actorH*1.05f));h=actorY+actorH+pad;}
  getChildAt(0).measure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY));
  getChildAt(1).measure(MeasureSpec.makeMeasureSpec(actorW,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(actorH,MeasureSpec.EXACTLY));
  int propW=Math.round(w*.18f);getChildAt(2).measure(MeasureSpec.makeMeasureSpec(propW,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(propW,MeasureSpec.EXACTLY));
  getChildAt(4).measure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(pad*3,MeasureSpec.EXACTLY));
  setMeasuredDimension(w,resolveSize(h,heightSpec));
 }
 @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
  int w=r-l,h=b-t,pad=Math.round(18*getResources().getDisplayMetrics().density);View actor=getChildAt(1),prop=getChildAt(2),head=getChildAt(3),arrows=getChildAt(4);
  getChildAt(0).layout(0,0,w,h);
  int ax=(w-actor.getMeasuredWidth())/2;actor.layout(ax,actorY,ax+actor.getMeasuredWidth(),actorY+actor.getMeasuredHeight());
  int px=Math.round(w*.74f),py=Math.round(w*.40f);prop.layout(px,py,px+prop.getMeasuredWidth(),py+prop.getMeasuredHeight());
  head.layout(pad,pad,w-pad,pad+head.getMeasuredHeight());
  int arrowY=Math.round(w*.43f);arrows.layout(0,arrowY,w,arrowY+arrows.getMeasuredHeight());
 }
}
