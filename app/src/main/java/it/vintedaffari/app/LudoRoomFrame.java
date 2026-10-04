package it.vintedaffari.app;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
/** Persistent full viewport room with independent actor, bottom commands and top chrome. */
final class LudoRoomFrame extends FrameLayout {
 private int heightBudget,controlsInset,actorY,commandY;
 private boolean compact;
 void setHeightBudget(int value){if(heightBudget!=value){heightBudget=value;requestLayout();}}
 void setControlsInset(int value){if(controlsInset!=value){controlsInset=value;requestLayout();}}
 LudoRoomFrame(Context context,boolean library){super(context);}
 @Override protected void onMeasure(int widthSpec,int heightSpec){
  int w=MeasureSpec.getSize(widthSpec),pad=Math.round(16*getResources().getDisplayMetrics().density);
  int h=Math.max(1,heightBudget>0?heightBudget:MeasureSpec.getSize(heightSpec));
  View commands=getChildAt(3),chrome=getChildAt(4);
  compact=h-controlsInset<Math.round(360*getResources().getDisplayMetrics().density);
  android.widget.LinearLayout sections=(android.widget.LinearLayout)((android.widget.LinearLayout)commands).getChildAt(1);
  ((android.widget.LinearLayout)commands).getChildAt(0).setVisibility(compact?GONE:VISIBLE);
  for(int i=0;i<sections.getChildCount();i++){android.widget.LinearLayout target=(android.widget.LinearLayout)sections.getChildAt(i);target.getChildAt(1).setVisibility(compact?GONE:VISIBLE);}
  int inner=Math.max(0,w-2*pad);
  int commandWidth=compact?Math.max(0,Math.round(w*.55f)-pad):inner;
  commands.measure(MeasureSpec.makeMeasureSpec(commandWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(0,h-controlsInset),MeasureSpec.AT_MOST));
  chrome.measure(MeasureSpec.makeMeasureSpec(inner,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(0,h-controlsInset),MeasureSpec.AT_MOST));
  commandY=compact?Math.max(0,h-controlsInset-commands.getMeasuredHeight()-pad):Math.max(chrome.getMeasuredHeight()+pad,h-controlsInset-commands.getMeasuredHeight()-pad);
  int actorBottom=compact?h-controlsInset-pad:Math.min(Math.round(h*.80f),commandY-pad);
  int actorH=Math.max(0,Math.min(Math.round(w*.70f),actorBottom-chrome.getMeasuredHeight()-2*pad));
  int actorW=Math.min(Math.round(w*(compact?.40f:.68f)),Math.round(actorH*1000f/1040));
  actorY=Math.max(chrome.getMeasuredHeight()+pad,actorBottom-actorH);
  getChildAt(0).measure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY));
  getChildAt(1).measure(MeasureSpec.makeMeasureSpec(actorW,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(actorH,MeasureSpec.EXACTLY));
  getChildAt(2).measure(MeasureSpec.makeMeasureSpec(0,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.EXACTLY));
  setMeasuredDimension(w,h);
 }
 @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
  int w=r-l,h=b-t,pad=Math.round(16*getResources().getDisplayMetrics().density);
  View actor=getChildAt(1),commands=getChildAt(3),chrome=getChildAt(4);
  getChildAt(0).layout(0,0,w,h);
  int ax=compact?Math.max(0,(Math.round(w*.45f)-actor.getMeasuredWidth())/2):(w-actor.getMeasuredWidth())/2;
  actor.layout(ax,actorY,ax+actor.getMeasuredWidth(),actorY+actor.getMeasuredHeight());
  getChildAt(2).layout(0,0,0,0);
  commands.layout(compact?Math.round(w*.45f):pad,commandY,w-pad,commandY+commands.getMeasuredHeight());
  chrome.layout(pad,pad,w-pad,pad+chrome.getMeasuredHeight());
 }
}
