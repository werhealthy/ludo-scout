"""Run production gesture/progress against deterministic Android render boundaries."""
from pathlib import Path
import subprocess, tempfile

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/it/vintedaffari/app/MainActivity.java').read_text()

def extract(signature):
    start = source.index(signature)
    brace = source.index('{', start)
    depth = 0
    for i in range(brace, len(source)):
        if source[i] == '{': depth += 1
        elif source[i] == '}':
            depth -= 1
            if depth == 0: return source[start:i+1]
    raise ValueError(signature)

harness = r'''
public class ProductPullMotion {
 static class View {float translation;int height=160;View parent;boolean attached=true;boolean isAttachedToWindow(){return attached;}Animation animation=new Animation(this);
  View getParent(){return parent;}void setTranslationY(float y){translation=y;}float getTranslationY(){return translation;}
  Animation animate(){return animation;}}
 static class Animation {View view;Animation(View v){view=v;}void cancel(){}Animation translationY(float v){view.translation=v;return this;}Animation setDuration(long t){return this;}Animation setInterpolator(Object i){return this;}void start(){}}
 static class Drawable {}
 static class GamePullProgress extends Drawable {float progress;ValueAnimator returning;void setProgress(float p){progress=p;}void cancelReturn(){if(returning!=null){ValueAnimator old=returning;returning=null;old.cancel();}}}
 static class TextView extends View {String text="",description;float alpha;Drawable[] drawables={null,new GamePullProgress(),null,null};
  Drawable[] getCompoundDrawables(){return drawables;}String getText(){return text;}void setText(String t){text=t;}boolean isClickable(){return false;}void setAlpha(float a){alpha=a;}void setContentDescription(String s){description=s;}}
 static class ValueAnimator extends android.animation.Animator {
  interface Update {void on(ValueAnimator a);}static boolean enabled=true;static ValueAnimator latest;
  float from,to,current;boolean cancelled;Update update;android.animation.AnimatorListenerAdapter listener;
  static boolean areAnimatorsEnabled(){return enabled;}
  static ValueAnimator ofFloat(float from,float to){ValueAnimator a=new ValueAnimator();a.from=from;a.to=to;a.current=from;latest=a;return a;}
  ValueAnimator setDuration(long t){return this;}void setInterpolator(Object i){}void addUpdateListener(Update u){update=u;}void addListener(android.animation.AnimatorListenerAdapter l){listener=l;}
  Object getAnimatedValue(){return current;}void start(){}void cancel(){cancelled=true;if(listener!=null)listener.onAnimationEnd(this);}
  void frame(float fraction){current=from+(to-from)*fraction;if(update!=null)update.on(this);if(fraction==1&&listener!=null)listener.onAnimationEnd(this);}
 }
 int dp(int v){return v;}float dp(float v){return v;}
 __MODEL__
 __UPDATE__
 __RENDER__
 static void eq(float want,float got,String label){if(Math.abs(want-got)>.001f)throw new AssertionError(label+": "+got+" expected "+want);}
 public static void main(String[] args){
  GamePullGesture gesture=new GamePullGesture();gesture.down(300,true);
  eq(.25f,gesture.move(245,true,220),"quarter pull fills quarter ring");
  eq(.5f,gesture.move(190,true,220),"half pull fills half ring");
  eq(.75f,gesture.move(135,true,220),"three quarters remain continuous");
  eq(0,gesture.move(300,true,220),"retreat clears progress");
  ProductPullMotion app=new ProductPullMotion();TextView hint=new TextView();View content=new View();hint.parent=content;
  app.updateGamePullHint(hint,.25f,true);eq(-18,content.translation,"quarter pull lifts content");
  app.updateGamePullHint(hint,.5f,true);eq(-36,content.translation,"half pull lifts content");
  app.updateGamePullHint(hint,.75f,true);eq(-54,content.translation,"continuous lift");
  app.updateGamePullHint(hint,0,true);
  GamePullProgress ring=(GamePullProgress)hint.drawables[1];
  eq(.75f,ring.progress,"cancel must not immediately hide ring");eq(-54,content.translation,"cancel must retain initial position until next frame");
  ValueAnimator reverse=ValueAnimator.latest;if(reverse==null)throw new AssertionError("missing reverse animator");
  reverse.frame(.5f);eq(.375f,ring.progress,"reverse midpoint ring");eq(-27,content.translation,"reverse midpoint content");
  if(hint.text.isEmpty())throw new AssertionError("caption vanishes before reverse ends");
  reverse.frame(1);eq(0,ring.progress,"reverse completes ring");eq(0,content.translation,"reverse completes content");
  app.updateGamePullHint(hint,.6f,true);app.updateGamePullHint(hint,0,true);ValueAnimator interrupted=ValueAnimator.latest;interrupted.frame(.25f);
  app.updateGamePullHint(hint,.4f,true);interrupted.frame(1);eq(.4f,ring.progress,"obsolete return must not erase new gesture");eq(-28.8f,content.translation,"new gesture controls content");
  ValueAnimator.enabled=false;app.updateGamePullHint(hint,0,true);eq(0,ring.progress,"disabled animations clear immediately");eq(0,content.translation,"disabled animations restore content");ValueAnimator.enabled=true;
  app.updateGamePullHint(hint,.8f,true);app.updateGamePullHint(hint,0,true);ValueAnimator detached=ValueAnimator.latest;hint.attached=false;detached.frame(.5f);eq(0,ring.progress,"detached view clears return");eq(0,content.translation,"detached view resets content");if(ring.returning!=null)throw new AssertionError("detached return leaks animator");
  eq(160,content.height,"gesture leaves measured scroll extent unchanged");
  if(!hint.text.isEmpty())throw new AssertionError("resting hint must be invisible");
  System.out.println("PASS production linear fill, continuous content lift and cancellation");
 }
}
'''.replace('__MODEL__', extract('private static final class GamePullGesture')).replace('__UPDATE__', extract('private void updateGamePullHint(')).replace('__RENDER__', extract('private void renderGamePullHint(') if 'private void renderGamePullHint(' in source else '')
with tempfile.TemporaryDirectory() as tmp:
    p=Path(tmp)
    (p/'android/view/animation').mkdir(parents=True)
    (p/'android/view/animation/DecelerateInterpolator.java').write_text('package android.view.animation; public class DecelerateInterpolator {}')
    (p/'ProductPullMotion.java').write_text(harness)
    (p/'android/animation').mkdir(parents=True)
    (p/'android/animation/Animator.java').write_text('package android.animation; public class Animator {}')
    (p/'android/animation/AnimatorListenerAdapter.java').write_text('package android.animation; public class AnimatorListenerAdapter {public void onAnimationEnd(Animator animation){}}')
    subprocess.run(['javac','-d',tmp]+[str(f) for f in p.rglob('*.java')],check=True)
    subprocess.run(['java','-cp',tmp,'ProductPullMotion'],check=True)
