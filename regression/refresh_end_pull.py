"""Execute the real refresh/end-pull integration against controlled Android views."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1]
source=(root/'app/src/main/java/it/vintedaffari/app/PullRefreshScrollView.java').read_text()
def method(signature):
    start=source.index(signature);brace=source.index('{',start);depth=0
    for i in range(brace,len(source)):
        if source[i]=='{':depth+=1
        elif source[i]=='}':
            depth-=1
            if depth==0:return source[start:i+1]
    raise ValueError(signature)
harness=r'''
import android.view.MotionEvent;
public class RefreshEndPullRegression {
 static class View {interface OnTouchListener {boolean onTouch(View v,MotionEvent e);}float translation,alpha,rotation;Animation animate(){return new Animation(this);}void setTranslationY(float f){translation=f;}float getTranslationY(){return translation;}void setAlpha(float f){alpha=f;}void setRotation(float f){rotation=f;}}
 static class Animation {View v;Animation(View x){v=x;}void cancel(){}Animation alpha(float f){v.alpha=f;return this;}Animation translationY(float f){v.translation=f;return this;}Animation rotationBy(float f){v.rotation+=f;return this;}Animation setDuration(long l){return this;}void start(){}}
 static class ScrollView extends View {int getScrollY(){return 0;}}
 static class EndPullScrollView extends ScrollView {View.OnTouchListener endPull;void setEndPullListener(View.OnTouchListener l){endPull=l;}}
 static class Host {
  final ScrollView scroll=new EndPullScrollView();final View indicator=new View();float downY;boolean dragging,refreshing;Runnable action;View.OnTouchListener endPull;
  int dp(float f){return Math.round(f);}
  boolean dispatchEnd(MotionEvent e){View.OnTouchListener l=((EndPullScrollView)scroll).endPull;return l!=null?l.onTouch(scroll,e):endPull!=null&&endPull.onTouch(scroll,e);}
  __METHODS__
 }
 static MotionEvent event(int a,float y){return new MotionEvent(a,y);}
 static void eq(float want,float got,String message){if(Math.abs(want-got)>.001f)throw new AssertionError(message+": "+got);}
 public static void main(String[] args){
  Host h=new Host();int[] refreshes={0};h.action=()->refreshes[0]++;
  h.touch(h.scroll,event(0,300));h.touch(h.scroll,event(2,350));eq(22.5f,h.scroll.translation,"top pull setup");
  h.setEndPullListener((v,e)->e.getY()<300);
  if(!h.dispatchEnd(event(2,250)))throw new AssertionError("bottom ownership missing");
  eq(0,h.scroll.translation,"bottom ownership must clear previous top translation");eq(0,h.indicator.alpha,"bottom ownership must hide refresh illustration");
  if(h.dragging)throw new AssertionError("top gesture remains armed after bottom ownership");
  h.dispatchEnd(event(1,250));if(refreshes[0]!=0)throw new AssertionError("reverse pull must not refresh");
  Host top=new Host();top.action=()->refreshes[0]++;top.setEndPullListener((v,e)->false);top.touch(top.scroll,event(0,300));top.touch(top.scroll,event(2,440));top.touch(top.scroll,event(1,440));
  if(refreshes[0]!=1||!top.refreshing)throw new AssertionError("ordinary top refresh changed");
  System.out.println("PASS short page top-to-bottom reversal resets top refresh and preserves ordinary refresh");
 }
}
'''
methods=method('public void setEndPullListener(')+'\n'+method('public void finish(')+'\n'+method('private boolean touch(')
if 'private void cancelRefreshGesture(' in source:methods+='\n'+method('private void cancelRefreshGesture(')
harness=harness.replace('__METHODS__',methods)
motion='package android.view;public class MotionEvent {public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3;int action;float y;public MotionEvent(int a,float f){action=a;y=f;}public int getActionMasked(){return action;}public float getY(){return y;}}'
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp);(p/'android/view').mkdir(parents=True);(p/'android/view/MotionEvent.java').write_text(motion);(p/'RefreshEndPullRegression.java').write_text(harness)
 subprocess.run(['javac','-d',tmp]+[str(x) for x in p.rglob('*.java')],check=True)
 subprocess.run(['java','-cp',tmp,'RefreshEndPullRegression'],check=True)

# Exercise the actual horizontal adapter without disturbing vertical refresh/end pull.
room_adapter=r'''
import java.util.function.*;
public class RoomSwipeAdapterRegression {
 static class MotionEvent {static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3;int action,pointers=1;float x,y;MotionEvent(int a,float xx,float yy){action=a;x=xx;y=yy;}int getActionMasked(){return action;}int getPointerCount(){return pointers;}float getX(){return x;}float getY(){return y;}}
 static class ViewConfiguration {static ViewConfiguration get(Object c){return new ViewConfiguration();}int getScaledTouchSlop(){return 8;}}
 static class Base {public boolean onInterceptTouchEvent(MotionEvent e){return false;}public boolean onTouchEvent(MotionEvent e){return false;}}
 static class Host extends Base {
  final LudoRoomSwipe roomSwipe=new LudoRoomSwipe();BooleanSupplier roomSwipeEnabled;IntConsumer roomSwipeAction;boolean refreshing;int cancelled;
  Object getContext(){return this;}int dp(float f){return Math.round(f);}void cancelRefreshGesture(){cancelled++;}
  __ACTUAL_ADAPTER__
 }
 static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
 static MotionEvent event(int a,float x,float y){return new MotionEvent(a,x,y);}
 public static void main(String[] args){
  Host h=new Host();int[] direction={0},count={0};h.setRoomSwipeHandler(()->true,d->{direction[0]=d;count[0]++;});
  h.onInterceptTouchEvent(event(0,200,200));check(!h.onInterceptTouchEvent(event(2,199,290)),"vertical intercepted");check(h.cancelled==0,"vertical refresh cancelled");
  h.onInterceptTouchEvent(event(0,200,200));check(h.onInterceptTouchEvent(event(2,150,201)),"horizontal not claimed");check(h.cancelled==1,"old refresh gesture not cancelled");check(h.onTouchEvent(event(1,100,201))&&count[0]==1&&direction[0]==1,"left swipe adapter");
  h.onInterceptTouchEvent(event(0,200,200));h.onInterceptTouchEvent(event(2,160,200));h.onTouchEvent(event(1,160,200));check(count[0]==1,"short switched room");
  h.onInterceptTouchEvent(event(0,200,200));h.onInterceptTouchEvent(event(2,150,200));h.onTouchEvent(event(3,100,200));check(count[0]==1,"cancel switched room");
  h.onInterceptTouchEvent(event(0,200,200));h.onInterceptTouchEvent(event(2,150,200));MotionEvent multi=event(2,100,200);multi.pointers=2;h.onTouchEvent(multi);h.onTouchEvent(event(1,80,200));check(count[0]==1,"multipointer switched room");
  h.roomSwipeEnabled=()->false;h.onInterceptTouchEvent(event(0,200,200));check(!h.onInterceptTouchEvent(event(2,100,200)),"other page intercepted");
  h.roomSwipeEnabled=()->true;h.refreshing=true;h.onInterceptTouchEvent(event(0,200,200));check(!h.onInterceptTouchEvent(event(2,100,200)),"active refresh intercepted");
  System.out.println("PASS actual room swipe adapter: vertical untouched, horizontal cancellation, callback direction, short/cancel/multipointer/disabled/refresh guards");
 }
}'''.replace("__ACTUAL_ADAPTER__","\n".join(method(s) for s in ["public void setRoomSwipeHandler(","@Override public boolean onInterceptTouchEvent(","@Override public boolean onTouchEvent("]))
room_java=(root/"app/src/main/java/it/vintedaffari/app/LudoRoomSwipe.java").read_text().replace("package it.vintedaffari.app;","")
with tempfile.TemporaryDirectory() as temp:
 p=Path(temp);(p/"LudoRoomSwipe.java").write_text(room_java);(p/"RoomSwipeAdapterRegression.java").write_text(room_adapter)
 subprocess.run(["javac","-d",temp,str(p/"LudoRoomSwipe.java"),str(p/"RoomSwipeAdapterRegression.java")],check=True)
 subprocess.run(["java","-cp",temp,"RoomSwipeAdapterRegression"],check=True)
