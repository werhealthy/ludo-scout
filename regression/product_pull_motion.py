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
 static class View {float translation;int height=160;View parent;Animation animation=new Animation(this);
  View getParent(){return parent;}void setTranslationY(float y){translation=y;}float getTranslationY(){return translation;}
  Animation animate(){return animation;}}
 static class Animation {View view;Animation(View v){view=v;}void cancel(){}Animation translationY(float v){view.translation=v;return this;}Animation setDuration(long t){return this;}Animation setInterpolator(Object i){return this;}void start(){}}
 static class Drawable {}
 static class GamePullProgress extends Drawable {float progress;void setProgress(float p){progress=p;}}
 static class TextView extends View {String text="",description;float alpha;Drawable[] drawables={null,new GamePullProgress(),null,null};
  Drawable[] getCompoundDrawables(){return drawables;}String getText(){return text;}void setText(String t){text=t;}boolean isClickable(){return false;}void setAlpha(float a){alpha=a;}void setContentDescription(String s){description=s;}}
 static class ValueAnimator {static boolean areAnimatorsEnabled(){return true;}}
 int dp(int v){return v;}float dp(float v){return v;}
 __MODEL__
 __UPDATE__
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
  app.updateGamePullHint(hint,0,true);eq(0,content.translation,"cancel returns content");
  eq(160,content.height,"gesture leaves measured scroll extent unchanged");
  if(!hint.text.isEmpty())throw new AssertionError("resting hint must be invisible");
  System.out.println("PASS production linear fill, continuous content lift and cancellation");
 }
}
'''.replace('__MODEL__', extract('private static final class GamePullGesture')).replace('__UPDATE__', extract('private void updateGamePullHint('))
with tempfile.TemporaryDirectory() as tmp:
    p=Path(tmp)
    (p/'android/view/animation').mkdir(parents=True)
    (p/'android/view/animation/DecelerateInterpolator.java').write_text('package android.view.animation; public class DecelerateInterpolator {}')
    (p/'ProductPullMotion.java').write_text(harness)
    subprocess.run(['javac','-d',tmp,str(p/'ProductPullMotion.java'),str(p/'android/view/animation/DecelerateInterpolator.java')],check=True)
    subprocess.run(['java','-cp',tmp,'ProductPullMotion'],check=True)
