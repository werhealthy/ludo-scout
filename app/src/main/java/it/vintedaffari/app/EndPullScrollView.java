package it.vintedaffari.app;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ScrollView;

/** Observe a bottom gesture before child dispatch; ordinary child taps remain native. */
final class EndPullScrollView extends ScrollView {
    private View.OnTouchListener endPull;
    private boolean claimed;

    EndPullScrollView(Context context){super(context);}

    public void setEndPullListener(View.OnTouchListener listener){endPull=listener;claimed=false;}

    @Override public boolean dispatchTouchEvent(MotionEvent event){
        View.OnTouchListener listener=endPull;
        if(listener==null)return super.dispatchTouchEvent(event);
        int action=event.getActionMasked();boolean wasClaimed=claimed;
        if(listener.onTouch(this,event)){
            if(!wasClaimed){
                MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);cancel.recycle();
            }
            claimed=action!=MotionEvent.ACTION_UP&&action!=MotionEvent.ACTION_CANCEL;
            return true;
        }
        // A cancelled owned gesture cannot restart native scrolling without a new DOWN.
        if(wasClaimed){claimed=action!=MotionEvent.ACTION_UP&&action!=MotionEvent.ACTION_CANCEL;return true;}
        return super.dispatchTouchEvent(event);
    }
}
