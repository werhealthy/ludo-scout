"""Execute the real favorite loader with deterministic IO, UI and layout boundaries."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1]
ui=(root/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text()
start=ui.index("private void loadLudoHunts(");brace=ui.index("{",start);depth=0
for i in range(brace,len(ui)):
 if ui[i]=="{":depth+=1
 elif ui[i]=="}":
  depth-=1
  if depth==0:method=ui[start:i+1];break
harness=r'''package it.vintedaffari.app;
import java.util.*;
public class LudoHuntsUiRegression {
 static class Queue {List<Runnable> work=new ArrayList<>();void execute(Runnable r){work.add(r);}void flush(){while(!work.isEmpty())work.remove(0).run();}}
 static final Queue main=new Queue();
 static class View {String tag;View(){}View(String s){tag=s;}}
 interface Click {void click(View v);}
 static class Button extends View {void setTextColor(int c){}void setOnClickListener(Click c){}}
 static class TextView extends View {TextView(String s){super(s);}void setPadding(int a,int b,int c,int d){}void setMinHeight(int n){}void setOnClickListener(Click c){}}
 static class LinearLayout extends View {
  List<View> children=new ArrayList<>();Object parent=new Object();LinearLayout(Object c){}
  void addView(View v){children.add(v);}void addView(View v,LayoutParams p){children.add(v);}void removeAllViews(){children.clear();}
  Object getParent(){return parent;}int getChildCount(){return children.size();}void setGravity(int g){}void post(Runnable r){main.execute(r);}
  static class LayoutParams {int topMargin,leftMargin;LayoutParams(int w,int h){}LayoutParams(int w,int h,int weight){}}
 }
 static class Space extends View {Space(Object c){}}
 static class Gravity {static int TOP=1,CENTER_VERTICAL=2;}
 static class Typeface {static int NORMAL=0,BOLD=1;}
 static class Config {float fontScale=1;}
 static class Metrics {int widthPixels=400;}
 static class Resources {Config getConfiguration(){return new Config();}Metrics getDisplayMetrics(){return new Metrics();}}
 static class Preferences {Set<String> saved=new TreeSet<>();Set<String> favorites(){return new TreeSet<>(saved);}}
 static class Market {Map<String,GameRecord> games=new HashMap<>();int reads;GameRecord gameStatsByBggId(String id){reads++;return games.get(id);}}
 static class Scroll {int y;void scrollTo(int x,int value){y=value;}}
 int ludoHuntsRequest;String tab="companion";static int MUTED=1,TEXT=2,VINTED_BG=3;
 Queue uiDataIo=new Queue();Preferences prefs=new Preferences();Market marketStore=new Market();Scroll scroll=new Scroll();
 LudoRoomState rooms=new LudoRoomState("hunts",110,800,330);
 Preferences gamePreferences(){return prefs;}LudoRoomState ludoRoomState(){return rooms;}Resources getResources(){return new Resources();}
 boolean isDestroyed(){return false;}void runOnUiThread(Runnable r){main.execute(r);}int dp(int n){return n;}
 TextView text(String s,int size,int color,int font){return new TextView(s);}TextView secondaryTextAction(String s){return new TextView(s);}
 Button button(String s,int c){return new Button();}void switchLudoRoom(String s){rooms.switchTo(s,scroll.y);}
 View gameFavoriteButton(String id,String name){return new View("favorite:"+id);}View ludoHuntCard(GameRecord game){return new View("game:"+game.bggId);}
 __METHOD__
 void drain(){while(!uiDataIo.work.isEmpty()||!main.work.isEmpty()){uiDataIo.flush();main.flush();}}
 static List<String> tags(LinearLayout host){List<String> out=new ArrayList<>();for(View v:host.children){if(v.tag!=null)out.add(v.tag);if(v instanceof LinearLayout)out.addAll(tags((LinearLayout)v));}return out;}
 static GameRecord game(String id,String name){GameRecord g=new GameRecord();g.bggId=id;g.name=name;return g;}
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
  LudoHuntsUiRegression n=new LudoHuntsUiRegression();n.prefs.saved.addAll(Arrays.asList("123","456","999"));n.marketStore.games.put("123",game("123","Z"));n.marketStore.games.put("456",game("456","A"));LinearLayout host=new LinearLayout(n);
  n.loadLudoHunts(host);check(n.marketStore.reads==0,"database work must stay off immediate UI");n.drain();List<String> rows=tags(host);
  check(rows.indexOf("game:456")<rows.indexOf("game:123")&&rows.contains("favorite:999"),"canonical games sort and missing favorite identity remains accessible");
  check(n.scroll.y==800,"deep Cacce position must restore after asynchronous grid layout");
  n.prefs.saved.clear();n.prefs.saved.add("123");n.loadLudoHunts(host);n.prefs.saved.clear();n.prefs.saved.add("456");n.drain();rows=tags(host);check(rows.contains("game:456")&&!rows.contains("game:123"),"stale favorite snapshot cannot win");
  n.scroll.y=77;n.loadLudoHunts(host);n.tab="catalog";List<String> before=tags(host);n.drain();check(n.scroll.y==77&&before.equals(tags(host)),"leaving before IO completes must not rewrite page or scroll");
  n.tab="companion";n.loadLudoHunts(host);host.parent=null;n.drain();check(n.scroll.y==77,"detached host must not restore scroll");
  System.out.println("PASS actual Cacce loader: background IO, canonical sorting, missing identities, layout restoration, stale favorites and abandoned page");
 }
}'''.replace("__METHOD__",method)
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp)/"LudoHuntsUiRegression.java";p.write_text(harness)
 subprocess.run(["javac","-d",tmp,str(root/"app/src/main/java/it/vintedaffari/app/LudoRoomState.java"),str(root/"app/src/main/java/it/vintedaffari/app/GameRecord.java"),str(p)],check=True)
 subprocess.run(["java","-cp",tmp,"it.vintedaffari.app.LudoHuntsUiRegression"],check=True)
