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
 public int getPointerCount(){return pointers;} public float getY(){return y;}
}
"""
harness=r"""
import android.view.MotionEvent;
public class ProductGestureRegression {
 static class View {int height=1000;int getHeight(){return height;} }
 interface Touch {boolean on(View v,MotionEvent e);}
 static class ScrollView extends View {
  int y=500;View child=new View();Touch touch;
  ScrollView(){height=500;}
  int getScrollY(){return y;}View getChildAt(int i){return child;}
  boolean canScrollVertically(int direction){return direction>0&&y<500;}
  void setOnTouchListener(Touch t){touch=t;}
  void send(int action,float finger){touch.on(this,new MotionEvent(action,finger,1));}
 }
 static class Animation {Animation alpha(float a){return this;}Animation setDuration(long t){return this;}void start(){}}
 static class TextView {float alpha,progress;void setAlpha(float a){alpha=a;}void setText(String s){}Animation animate(){return new Animation();}}
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
 static void fullIntentionalPullOpensOnce(){ScrollView sc=new ScrollView();Dialog d=new Dialog();ProductGestureRegression n=fixture(sc,d);sc.send(0,300);sc.send(2,220);sc.send(1,220);sc.send(1,220);equal(1,n.opens,"completed gesture");}
 static void retreatCancels(){ScrollView sc=new ScrollView();Dialog d=new Dialog();ProductGestureRegression n=fixture(sc,d);sc.send(0,300);sc.send(2,200);sc.send(2,280);sc.send(1,280);equal(0,n.opens,"retreat");}
 static void finalReleaseRetreatCancels(){ScrollView sc=new ScrollView();Dialog d=new Dialog();ProductGestureRegression n=fixture(sc,d);sc.send(0,300);sc.send(2,200);sc.send(1,280);equal(0,n.opens,"retreat at final release");}
 static void closedSourceCannotOpen(){ScrollView sc=new ScrollView();Dialog d=new Dialog();ProductGestureRegression n=fixture(sc,d);sc.send(0,300);sc.send(2,200);d.showing=false;sc.send(1,200);equal(0,n.opens,"dismissed source");}
 static void unavailablePreloadCannotOpen(){ScrollView sc=new ScrollView();Dialog d=new Dialog();ProductGestureRegression n=fixture(sc,d);sc.send(0,300);sc.send(2,200);n.ready=false;sc.send(1,200);equal(0,n.opens,"unavailable preload");}
 static void extraPointerCancels(){ScrollView sc=new ScrollView();Dialog d=new Dialog();ProductGestureRegression n=fixture(sc,d);sc.send(0,300);sc.send(2,200);sc.touch.on(sc,new MotionEvent(5,200,2));sc.send(1,200);equal(0,n.opens,"multiple fingers");}
 public static void main(String[] args){
  int failures=0;Runnable[] tests={ProductGestureRegression::cancelNeverOpens,ProductGestureRegression::arrivingAtBottomDoesNotCountEarlierScroll,ProductGestureRegression::insufficientPullDoesNotOpen,ProductGestureRegression::fullIntentionalPullOpensOnce,ProductGestureRegression::retreatCancels,ProductGestureRegression::closedSourceCannotOpen,ProductGestureRegression::unavailablePreloadCannotOpen,ProductGestureRegression::extraPointerCancels,ProductGestureRegression::finalReleaseRetreatCancels};
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
    subprocess.run(["javac","-d",temp,str(p/"android/view/MotionEvent.java"),str(p/"ProductGestureRegression.java")],check=True)
    subprocess.run(["java","-cp",temp,"ProductGestureRegression"],check=True)
