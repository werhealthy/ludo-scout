"""Execute the production touch listener; Android event/render boundaries are deterministic doubles."""
from pathlib import Path
import subprocess, tempfile
root=Path(__file__).resolve().parents[1]
ui=(root/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text()
def extract(signature):
    start=ui.index(signature);brace=ui.index("{",start);depth=0
    for i in range(brace,len(ui)):
        if ui[i]=="{": depth+=1
        elif ui[i]=="}":
            depth-=1
            if depth==0:return ui[start:i+1]
    raise ValueError(signature)
listener=extract("private void installPullToGame(")
model=extract("private static final class GamePullGesture") if "private static final class GamePullGesture" in ui else ""
motion=r"""
package android.view;
public class MotionEvent {
 public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3,ACTION_POINTER_DOWN=5;
 int action,pointers;float y;
 public MotionEvent(int a,float v,int p){action=a;y=v;pointers=p;}
 public int getAction(){return action;} public int getActionMasked(){return action;}
 public int getPointerCount(){return pointers;} public float getY(){return y;} public float getX(){return y;}
}
"""
harness=r"""
import android.view.MotionEvent;
public class ProductGestureRegression {
 static class Bundle {}
 static class View {int height=1000;int getHeight(){return height;}
  static class AccessibilityDelegate {
   public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){}
   public boolean performAccessibilityAction(View host,int action,Bundle args){return false;}
  }
 }
 interface Touch {boolean on(View v,MotionEvent e);}
 static class ScrollView extends View {
  int y=500;View child=new View();Touch touch;AccessibilityDelegate delegate;void setFocusable(boolean b){}void setAccessibilityDelegate(AccessibilityDelegate d){delegate=d;}void setOnKeyListener(TextView.KeyListener l){}
  ScrollView(){height=500;}
  int getScrollY(){return y;}View getChildAt(int i){return child;}
  boolean canScrollVertically(int direction){return direction>0&&y<500;}
  void setOnTouchListener(Touch t){touch=t;}
  void send(int action,float finger){touch.on(this,new MotionEvent(action,finger,1));}
 }
 static class Animation {Animation alpha(float a){return this;}Animation setDuration(long t){return this;}void start(){}}
 static class KeyEvent {static final int KEYCODE_ENTER=66,KEYCODE_DPAD_CENTER=23,ACTION_UP=1;int getAction(){return ACTION_UP;}}
 static class TextView extends View {interface KeyListener {boolean on(View v,int key,KeyEvent event);}void setOnKeyListener(KeyListener l){}boolean performClick(){return false;}AccessibilityDelegate delegate;void setFocusable(boolean b){}void setAccessibilityDelegate(AccessibilityDelegate d){delegate=d;}float alpha,progress;void setAlpha(float a){alpha=a;}void setText(String s){}Animation animate(){return new Animation();}}
 static class Dialog {boolean showing=true;boolean isShowing(){return showing;}void dismiss(){showing=false;}}
 boolean ready=true;int opens;Dialog activeDetailDialog; 
 float dp(float v){return v;}int dp(int v){return v;}
 void openGameDetailOverlay(long id){if(id!=42)throw new AssertionError("wrong game");opens++;}
 boolean readyGameTransition(Dialog source,long gameId){return ready&&source.isShowing()&&activeDetailDialog==source&&gameId==42;}
 void showPreparedGameTransition(Dialog source,long gameId){if(!readyGameTransition(source,gameId))throw new AssertionError("invalid transition");opens++;}
 void updateGamePullHint(TextView hint,float progress,boolean ready){hint.progress=progress;}
 static ProductGestureRegression fixture(ScrollView sc,Dialog source){
  ProductGestureRegression n=new ProductGestureRegression();n.activeDetailDialog=source;n.installPullToGame(sc,new TextView(),42,source);return n;
 }
 static void equal(int expected,int actual,String label){if(expected!=actual)throw new AssertionError(label+": expected "+expected+" got "+actual);}
 static void cancelNeverOpens(){ScrollView sc=new ScrollView();Dialog d=new Dialog();ProductGestureRegression n=fixture(sc,d);sc.send(0,300);sc.send(2,200);sc.send(3,200);equal(0,n.opens,"Android cancelled touch");}
 static void arrivingAtBottomDoesNotCountEarlierScroll(){ScrollView sc=new ScrollView();sc.y=0;Dialog d=new Dialog();ProductGestureRegression n=fixture(sc,d);sc.send(0,400);sc.y=500;sc.send(2,200);sc.send(1,200);equal(0,n.opens,"ordinary scroll to bottom");}
 static void insufficientPullDoesNotOpen(){ScrollView sc=new ScrollView();Dialog d=new Dialog();ProductGestureRegression n=fixture(sc,d);sc.send(0,300);sc.send(2,240);sc.send(1,240);equal(0,n.opens,"incomplete progress");}
 static void fullIntentionalPullOpensOnce(){ScrollView sc=new ScrollView();Dialog d=new Dialog();ProductGestureRegression n=fixture(sc,d);sc.send(0,300);sc.send(2,70);sc.send(1,70);sc.send(1,70);equal(1,n.opens,"completed gesture");}
 static void shortOldPullDoesNotOpen(){ScrollView sc=new ScrollView();Dialog d=new Dialog();ProductGestureRegression n=fixture(sc,d);sc.send(0,300);sc.send(2,200);sc.send(1,200);equal(0,n.opens,"short pull must remain reading");}
 static void ongoingScrollCannotBecomePull(){ScrollView sc=new ScrollView();sc.y=0;Dialog d=new Dialog();ProductGestureRegression n=fixture(sc,d);sc.send(0,500);sc.y=500;sc.send(2,350);sc.send(2,100);sc.send(1,100);equal(0,n.opens,"same touch cannot arm at bottom");}
 static void retreatCancels(){ScrollView sc=new ScrollView();Dialog d=new Dialog();ProductGestureRegression n=fixture(sc,d);sc.send(0,300);sc.send(2,200);sc.send(2,280);sc.send(1,280);equal(0,n.opens,"retreat");}
 static void finalReleaseRetreatCancels(){ScrollView sc=new ScrollView();Dialog d=new Dialog();ProductGestureRegression n=fixture(sc,d);sc.send(0,300);sc.send(2,200);sc.send(1,280);equal(0,n.opens,"retreat at final release");}
 static void closedSourceCannotOpen(){ScrollView sc=new ScrollView();Dialog d=new Dialog();ProductGestureRegression n=fixture(sc,d);sc.send(0,300);sc.send(2,200);d.showing=false;sc.send(1,200);equal(0,n.opens,"dismissed source");}
 static void unavailablePreloadCannotOpen(){ScrollView sc=new ScrollView();Dialog d=new Dialog();ProductGestureRegression n=fixture(sc,d);sc.send(0,300);sc.send(2,200);n.ready=false;sc.send(1,200);equal(0,n.opens,"unavailable preload");}
 static void extraPointerCancels(){ScrollView sc=new ScrollView();Dialog d=new Dialog();ProductGestureRegression n=fixture(sc,d);sc.send(0,300);sc.send(2,200);sc.touch.on(sc,new MotionEvent(5,200,2));sc.send(1,200);equal(0,n.opens,"multiple fingers");}
 public static void main(String[] args){
  int failures=0;Runnable[] tests={ProductGestureRegression::cancelNeverOpens,ProductGestureRegression::arrivingAtBottomDoesNotCountEarlierScroll,ProductGestureRegression::insufficientPullDoesNotOpen,ProductGestureRegression::fullIntentionalPullOpensOnce,ProductGestureRegression::retreatCancels,ProductGestureRegression::closedSourceCannotOpen,ProductGestureRegression::unavailablePreloadCannotOpen,ProductGestureRegression::extraPointerCancels,ProductGestureRegression::finalReleaseRetreatCancels,ProductGestureRegression::shortOldPullDoesNotOpen,ProductGestureRegression::ongoingScrollCannotBecomePull};
  for(Runnable test:tests)try{test.run();System.out.println("PASS product gesture");}catch(AssertionError e){failures++;System.out.println("FAIL "+e.getMessage());}
  if(failures>0)throw new AssertionError(failures+" gesture scenarios failed");
 }
 __MODEL__
 __LISTENER__
}
""".replace("__MODEL__",model).replace("__LISTENER__",listener)
with tempfile.TemporaryDirectory() as temp:
    p=Path(temp);(p/"android/view").mkdir(parents=True)
    (p/"android/view/MotionEvent.java").write_text(motion)
    (p/"ProductGestureRegression.java").write_text(harness)
    (p/"android/view/accessibility").mkdir(parents=True)
    (p/"android/view/accessibility/AccessibilityNodeInfo.java").write_text('package android.view.accessibility; public class AccessibilityNodeInfo {public static final int ACTION_CLICK=16;public static class AccessibilityAction {public AccessibilityAction(int id,String label){}}public void addAction(AccessibilityAction a){}public void setClickable(boolean b){}}')
    subprocess.run(["javac","-d",temp,str(p/"android/view/MotionEvent.java"),str(p/"ProductGestureRegression.java"),str(p/"android/view/accessibility/AccessibilityNodeInfo.java")],check=True)
    gesture_result=subprocess.run(["java","-cp",temp,"ProductGestureRegression"],check=False)

# Characterize first-layout clamping: execute the real pager against a
# deterministic Android boundary whose scroll range uses measured child width.
pager_sources={
"android/content/Context.java": "package android.content; public class Context {}",
"android/view/MotionEvent.java": motion,
"android/view/View.java": r"""
package android.view;
public class View {
 public int width,measuredWidth;public android.widget.LinearLayout.LayoutParams lp;
 public int getWidth(){return width;}public int getMeasuredWidth(){return measuredWidth;}
 public void setLayoutParams(android.widget.LinearLayout.LayoutParams p){lp=p;}
 public android.widget.LinearLayout.LayoutParams getLayoutParams(){return lp;}
 public int getPaddingLeft(){return 0;}public int getPaddingRight(){return 0;}
 public static class MeasureSpec {public static int getSize(int s){return s;} }
 public void post(Runnable r){android.widget.HorizontalScrollView.tasks.add(r);}
}
""",
"android/widget/LinearLayout.java":r"""
package android.widget;
import android.view.View;import android.content.Context;import java.util.*;
public class LinearLayout extends View {
 final java.util.List<View> children=new ArrayList<>();
 public LinearLayout(Context c){}
 public static class LayoutParams {public int width,height;public LayoutParams(int w,int h){width=w;height=h;}}
 public void addView(View v,LayoutParams p){v.setLayoutParams(p);children.add(v);}
 public int getChildCount(){return children.size();}public View getChildAt(int i){return children.get(i);}
 public void measurePages(){measuredWidth=0;for(View v:children){v.measuredWidth=v.lp.width;measuredWidth+=v.measuredWidth;}}
}
""",
"android/widget/HorizontalScrollView.java":r"""
package android.widget;
import android.view.*;import android.content.Context;import java.util.*;
public class HorizontalScrollView extends View {
 public static final int OVER_SCROLL_NEVER=2;public static final java.util.List<Runnable> tasks=new ArrayList<>();
 LinearLayout child;int scroll;public HorizontalScrollView(Context c){}
 public static class LayoutParams {public LayoutParams(int w,int h){}}
 public void setHorizontalScrollBarEnabled(boolean b){}public void setFillViewport(boolean b){}public void setOverScrollMode(int m){}
 public void addView(LinearLayout v,LayoutParams p){child=v;}
 public void smoothScrollTo(int x,int y){scrollTo(x,y);}
 public void scrollTo(int x,int y){scroll=Math.max(0,Math.min(x,Math.max(0,child.measuredWidth-width)));}
 public int getScrollX(){return scroll;}
 protected void onMeasure(int w,int h){measuredWidth=w;child.measurePages();}
 protected void onSizeChanged(int w,int h,int ow,int oh){}
 protected void onLayout(boolean changed,int l,int t,int r,int b){scrollTo(scroll,0);}
 public void layoutFixture(int w){onMeasure(w,600);int old=width;width=w;onSizeChanged(w,600,old,600);onLayout(true,0,0,w,600);for(Runnable r:new ArrayList<>(tasks))r.run();tasks.clear();}
 public boolean onInterceptTouchEvent(MotionEvent e){return false;}public boolean onTouchEvent(MotionEvent e){return true;}
 public void fling(int v){}
}
""",
"it/vintedaffari/app/PagerRegression.java":r"""
package it.vintedaffari.app;
import android.view.View;import android.content.Context;
public class PagerRegression {
 static void eq(int want,int got,String label){if(want!=got)throw new AssertionError(label+": "+got+" expected "+want);}
 public static void main(String[] args){
  GalleryPager pager=new GalleryPager(new Context());for(int i=0;i<4;i++)pager.addPage(new View());
  int[] selected={-1};pager.setListener(i->selected[0]=i);pager.go(2);pager.layoutFixture(360);
  eq(720,pager.getScrollX(),"open third photo on first layout");eq(2,selected[0],"selected photo");
  pager.layoutFixture(240);eq(480,pager.getScrollX(),"resize preserves selected photo");
  pager.go(3);eq(720,pager.getScrollX(),"next photo");
  pager.go(99);eq(720,pager.getScrollX(),"clamp final page");pager.go(-1);eq(0,pager.getScrollX(),"clamp first page");
  System.out.println("PASS production GalleryPager initial layout and resize");
 }
}
"""
}
with tempfile.TemporaryDirectory() as temp:
    p=Path(temp)
    for path,code in pager_sources.items():
        dest=p/path;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(code)
    for name in ("GalleryPager","GalleryPosition"):
        dest=p/("it/vintedaffari/app/"+name+".java");dest.write_text((root/("app/src/main/java/it/vintedaffari/app/"+name+".java")).read_text())
    subprocess.run(["javac","-d",temp,*map(str,p.rglob("*.java"))],check=True)
    pager_result=subprocess.run(["java","-cp",temp,"it.vintedaffari.app.PagerRegression"],check=False)
if gesture_result.returncode or pager_result.returncode:
    raise AssertionError("Product gesture/gallery behavior failed")

# The real preparation callback must offer recovery after an empty/failed snapshot.
prepare=extract("private void prepareGameTransition(")
prepared_model=extract("private static final class PreparedGameOverlay")
recovery=r"""
import java.util.*;
public class RecoveryRegression {
 static class Dialog {boolean showing=true;boolean isShowing(){return showing;}}
 static class TextView {Runnable click;String value="";boolean clickable;
  void setText(String s){value=s;}void setOnClickListener(java.util.function.Consumer<TextView> c){click=c==null?null:()->c.accept(this);}
  void setClickable(boolean b){clickable=b;}
 }
 static class GameRecord {}
 static class GameDetailData {}
 static class MarketStore {boolean available;GameRecord gameStats(long id){return available?new GameRecord():null;}}
 static class Executor {List<Runnable> queue=new ArrayList<>();void execute(Runnable r){queue.add(r);}void drain(){queue.remove(0).run();}}
 final MarketStore marketStore=new MarketStore();final Executor uiDataIo=new Executor();
 final Map<Dialog,PreparedGameOverlay> preparedGameOverlays=new HashMap<>();
 boolean destroyed;boolean isFinishing(){return false;}boolean isDestroyed(){return destroyed;}
 GameDetailData loadGameDetailData(GameRecord g){return new GameDetailData();}
 void runOnUiThread(Runnable r){r.run();}Dialog buildPreparedGameOverlay(GameRecord g,GameDetailData s,Runnable done){return new Dialog();}
 void updateGamePullHint(TextView hint,float progress,boolean ready){hint.setText(ready?"ready":"waiting");}
 static void check(boolean b,String label){if(!b)throw new AssertionError(label);}
 public static void main(String[] args){
  RecoveryRegression a=new RecoveryRegression();Dialog source=new Dialog();TextView hint=new TextView();
  a.prepareGameTransition(source,42,hint);a.uiDataIo.drain();
  check(hint.click!=null,"failed preparation has no retry action");
  a.marketStore.available=true;hint.click.run();a.uiDataIo.drain();
  check(a.preparedGameOverlays.get(source).dialog!=null,"retry did not prepare game");
  check(hint.click==null,"retry action remains after success");
  a=new RecoveryRegression();source=new Dialog();hint=new TextView();a.prepareGameTransition(source,42,hint);source.showing=false;a.uiDataIo.drain();
  check(hint.click==null,"closed listing received recovery controls");
  System.out.println("PASS real game preparation failure/retry and closed-source guard");
 }
 __MODEL__
 __PREPARE__
}
""".replace("__MODEL__",prepared_model).replace("__PREPARE__",prepare)
with tempfile.TemporaryDirectory() as temp:
    p=Path(temp)/"RecoveryRegression.java";p.write_text(recovery)
    subprocess.run(["javac","-d",temp,str(p)],check=True)
    subprocess.run(["java","-cp",temp,"RecoveryRegression"],check=True)

