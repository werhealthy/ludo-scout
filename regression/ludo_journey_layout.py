"""Execute the actual journey row factory at phone widths/font scales.

Android view adapters inspect layout constraints and text destinations; these
are not Android pixel renders or a substitute for phone acceptance.
"""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/it/vintedaffari/app'
source=(src/'MainActivity.java').read_text()
start=source.index('private View ludoJourneyNode(');brace=source.index('{',start);depth=0
for end in range(brace,len(source)):
 depth+=(source[end]=='{')-(source[end]=='}')
 if depth==0:
  method=source[start:end+1];break
harness=r'''package it.vintedaffari.app;
import java.util.*;
public class JourneyLayoutCheck {
 static class View {String description;LinearLayout.LayoutParams lp;void setContentDescription(String s){description=s;}void setOnClickListener(Click c){}}
 interface Click {void accept(View v);}
 static class TextView extends View {String value;TextView(String s){value=s;}void setPadding(int a,int b,int c,int d){}void setGravity(int g){}void setMinWidth(int n){}void setMinHeight(int n){}void setBackground(Object o){}}
 static class LinearLayout extends View {
  static int HORIZONTAL=0,VERTICAL=1;int orientation;List<View> children=new ArrayList<>();LinearLayout(Object o){}
  void setOrientation(int value){orientation=value;}void setGravity(int g){}void setPadding(int a,int b,int c,int d){}void setMinimumHeight(int n){}void setBackground(Object o){}
  void addView(View v,LayoutParams p){v.lp=p;children.add(v);}
  static class LayoutParams {int width,height;float weight;LayoutParams(int w,int h){width=w;height=h;}LayoutParams(int w,int h,float f){this(w,h);weight=f;}}
 }
 static class Typeface {static int BOLD=1;}
 static class Gravity {static int CENTER=1,CENTER_VERTICAL=2;}
 static class Color {static int rgb(int r,int g,int b){return 0;}}
 static class Config {float fontScale;}
 static class Metrics {int widthPixels;}
 class Resources {Config getConfiguration(){Config c=new Config();c.fontScale=font;return c;}Metrics getDisplayMetrics(){Metrics m=new Metrics();m.widthPixels=width;return m;}}
 static class EngineOverviewSnapshot {int[] journeyCounts={0,1,31,31181,477043},journeyDelta;}
 float font;int width,opened=-1;static int DISCOVER_LAVENDER=1,DISCOVER_PINK=2,DISCOVER_MINT=3,DISCOVER_YELLOW=4,TEXT=5,SURFACE=6,MUTED=7,RED=8;
 Resources getResources(){return new Resources();}int dp(int n){return n;}Object round(int a,int b,int c,int d){return new Object();}
 TextView text(String s,int size,int color,int style){return new TextView(s);}void showJourneyItems(EngineOverviewSnapshot s,int stage){opened=stage;}
 __METHOD__
 static void check(boolean ok,String s){if(!ok)throw new AssertionError(s);}
 static void natural(View v){if(v.lp!=null)check(v.lp.height==-2,"text clipped by fixed height");if(v instanceof LinearLayout)for(View c:((LinearLayout)v).children)natural(c);}
 public static void main(String[] args){
  for(int width:new int[]{320,360,376,393})for(float font:new float[]{1f,1.3f,1.5f,2f})for(int deltaMode=0;deltaMode<3;deltaMode++){
   JourneyLayoutCheck n=new JourneyLayoutCheck();n.width=width;n.font=font;EngineOverviewSnapshot s=new EngineOverviewSnapshot();if(deltaMode>0)s.journeyDelta=new int[]{0,1,-1,1234,-9999};
   for(int phase=0;phase<5;phase++){
    LinearLayout row=(LinearLayout)n.ludoJourneyNode(s,phase,true);boolean stacked=font>1.3f||width<360;check(row.orientation==(stacked?1:0),"small width or large font not stacked");natural(row);
    TextView name=(TextView)row.children.get(0);LinearLayout values=(LinearLayout)row.children.get(1);TextView count=(TextView)values.children.get(0),delta=(TextView)values.children.get(1);
    check(name.value.equals(ExplorationPlan.label(phase)),"phase label lost");check(count.value.equals(String.valueOf(s.journeyCounts[phase])),"real count altered");
    check(delta.value.equals(s.journeyDelta==null?"oggi":ExplorationPlan.delta(s.journeyDelta[phase],0)),"signed delta lost");
    check(stacked?name.lp.width==-1:name.lp.width==0&&name.lp.weight==1,"phase label cannot wrap into remaining width");check(values.orientation==(stacked?1:0),"large values cannot grow vertically");check(row.description.contains(count.value),"accessible count lost");
   }
  }
  check(LudoPetMood.forJourney(true,true,false)==LudoPetMood.SLEEPING,"paused pet");check(LudoPetMood.forJourney(true,false,false)==LudoPetMood.SEARCHING,"working pet");check(LudoPetMood.forJourney(false,false,true)==LudoPetMood.FOUND,"finished pet");check(LudoPetMood.forJourney(false,false,false)==LudoPetMood.IDLE,"idle pet");
  System.out.println("PASS actual journey row factory: 320/360/376/393dp, fonts100/130/150/200%, real counts and signed deltas, natural heights; pet journey states");
 }
}'''.replace('__METHOD__',method)
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp)/'JourneyLayoutCheck.java';p.write_text(harness)
 subprocess.run(['javac','-d',tmp,str(src/'ExplorationPlan.java'),str(src/'LudoPetMood.java'),str(p)],check=True)
 subprocess.run(['java','-cp',tmp,'it.vintedaffari.app.JourneyLayoutCheck'],check=True)
