package it.vintedaffari.app;
import android.content.*;import android.graphics.Color;import android.view.*;import android.widget.*;

/** Small native pull-to-refresh container with a Ludo illustration instead of a spinner. */
public final class PullRefreshScrollView extends FrameLayout{
    private final LudoRoomSwipe roomSwipe=new LudoRoomSwipe();private java.util.function.BooleanSupplier roomSwipeEnabled;private java.util.function.IntConsumer roomSwipeAction;
    public void setRoomSwipeHandler(java.util.function.BooleanSupplier enabled,java.util.function.IntConsumer action){roomSwipeEnabled=enabled;roomSwipeAction=action;}
    @Override public boolean onInterceptTouchEvent(MotionEvent event){
        if(roomSwipeEnabled==null||!roomSwipeEnabled.getAsBoolean()||refreshing){roomSwipe.cancel();return super.onInterceptTouchEvent(event);}
        if(event.getPointerCount()!=1){roomSwipe.cancel();return super.onInterceptTouchEvent(event);}
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN)roomSwipe.down(event.getX(),event.getY());
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&roomSwipe.move(event.getX(),event.getY(),ViewConfiguration.get(getContext()).getScaledTouchSlop())){cancelRefreshGesture();return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_CANCEL)roomSwipe.cancel();
        return super.onInterceptTouchEvent(event);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(!roomSwipe.owns())return super.onTouchEvent(event);
        if(event.getPointerCount()!=1||event.getActionMasked()==MotionEvent.ACTION_CANCEL){roomSwipe.cancel();return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_UP){int direction=roomSwipe.release(event.getX(),event.getY(),dp(72));if(direction!=0&&roomSwipeEnabled!=null&&roomSwipeEnabled.getAsBoolean()&&roomSwipeAction!=null)roomSwipeAction.accept(direction);}
        return true;
    }
    private final ScrollView scroll;private final ImageView indicator;private float downY;private boolean dragging,refreshing;private Runnable action;
    public PullRefreshScrollView(Context c){super(c);scroll=new EndPullScrollView(c);scroll.setFillViewport(true);scroll.setClipToPadding(false);addView(scroll,new LayoutParams(-1,-1));indicator=new ImageView(c);indicator.setImageResource(R.drawable.ludo_deal_search);indicator.setScaleType(ImageView.ScaleType.FIT_CENTER);indicator.setAlpha(0f);LayoutParams p=new LayoutParams(dp(72),dp(72),Gravity.TOP|Gravity.CENTER_HORIZONTAL);p.topMargin=dp(4);addView(indicator,p);scroll.setOnTouchListener(this::touch);}
    public void setEndPullListener(View.OnTouchListener listener){
        ((EndPullScrollView)scroll).setEndPullListener(listener==null?null:(v,e)->{
            boolean handled=listener.onTouch(v,e);
            if(handled&&!refreshing&&(dragging||scroll.getTranslationY()!=0))cancelRefreshGesture();
            return handled;
        });
    }
    public boolean isRefreshing(){return refreshing;}
    private void cancelRefreshGesture(){
        dragging=false;scroll.animate().cancel();indicator.animate().cancel();
        scroll.setTranslationY(0);indicator.setTranslationY(0);indicator.setAlpha(0f);indicator.setRotation(0f);
    }
    public ScrollView scroll(){return scroll;}public void setContent(View v){scroll.removeAllViews();scroll.addView(v);}public void setOnRefresh(Runnable r){action=r;}public void finish(){refreshing=false;indicator.animate().alpha(0f).translationY(0).setDuration(180).start();scroll.animate().translationY(0).setDuration(180).start();}
    private boolean touch(View v,android.view.MotionEvent e){if(refreshing)return false;switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:downY=e.getY();dragging=scroll.getScrollY()==0;break;case MotionEvent.ACTION_MOVE:if(dragging){float d=Math.max(0,e.getY()-downY);if(d>0){float y=Math.min(dp(84),d*.45f);scroll.setTranslationY(y);indicator.setTranslationY(Math.min(dp(24),y*.25f));indicator.setAlpha(Math.min(1f,y/dp(54f)));}}break;case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:if(dragging&&scroll.getTranslationY()>=dp(54)){refreshing=true;scroll.animate().translationY(dp(68)).setDuration(120).start();indicator.animate().alpha(1f).rotationBy(8).setDuration(120).start();if(action!=null)action.run();}else finish();dragging=false;break;}return false;}
    private int dp(float v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
}

