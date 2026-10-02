"""Run production async save coordination against deterministic IO/window boundaries."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1]
ui=(root/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text()
start=ui.index("private void saveLibraryChange(");brace=ui.index("{",start);depth=0
for i in range(brace,len(ui)):
    if ui[i]=="{":depth+=1
    elif ui[i]=="}":
        depth-=1
        if depth==0:body=ui[start:i+1];break
harness=r"""
import java.util.*;
public class LibraryWriteUiRegression {
 static final int SHEET_ID=1;String tab="library";int opened,renders;
 Set<Dialog> librarySaveInFlight=new HashSet<>();
 static class View {boolean enabled=true;void setEnabled(boolean v){enabled=v;}}
 static class ScrollView {int getScrollY(){return 230;}}
 static class Dialog {boolean showing=true;boolean isShowing(){return showing;}void dismiss(){showing=false;}ScrollView findViewById(int id){return new ScrollView();}}
 static class LibraryGame {String bggId="a";}
 static class LibraryDb {List<LibraryGame> all(){return Arrays.asList(new LibraryGame());}}
 static class Queue {List<Runnable> work=new ArrayList<>();void execute(Runnable r){work.add(r);}void flush(){while(!work.isEmpty())work.remove(0).run();}}
 static class Toast {static int LENGTH_LONG=1;static Toast makeText(Object c,String m,int duration){return new Toast();}void show(){}}
 LibraryDb libraryDb=new LibraryDb();Queue uiDataIo=new Queue();
 boolean isFinishing(){return false;}boolean isDestroyed(){return false;}
 void runOnUiThread(Runnable r){r.run();}void scheduleRender(int n){renders++;}
 void openLibraryDetail(LibraryGame game,int y){if(y!=230)throw new AssertionError("return position lost");opened++;}
 static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
 static void duplicateClickWritesOnce(){LibraryWriteUiRegression n=new LibraryWriteUiRegression();Dialog sheet=new Dialog(),parent=new Dialog();View button=new View();int[] writes={0};n.saveLibraryChange(new LibraryGame(),()->writes[0]++,sheet,parent,button);n.saveLibraryChange(new LibraryGame(),()->writes[0]++,sheet,parent,button);n.uiDataIo.flush();check(writes[0]==1,"duplicate click performed "+writes[0]+" writes");}
 static void failurePreservesInputAndAllowsRetry(){LibraryWriteUiRegression n=new LibraryWriteUiRegression();Dialog sheet=new Dialog(),parent=new Dialog();View button=new View();n.saveLibraryChange(new LibraryGame(),()->{throw new IllegalStateException("busy");},sheet,parent,button);n.uiDataIo.flush();check(sheet.showing&&parent.showing&&button.enabled&&n.opened==0,"failed save discarded input");n.saveLibraryChange(new LibraryGame(),()->{},sheet,parent,button);n.uiDataIo.flush();check(n.opened==1,"retry did not return to detail");}
 static void leavingDuringWriteDoesNotReopen(){LibraryWriteUiRegression n=new LibraryWriteUiRegression();Dialog sheet=new Dialog(),parent=new Dialog();n.saveLibraryChange(new LibraryGame(),()->{},sheet,parent,new View());sheet.dismiss();parent.dismiss();n.uiDataIo.flush();check(n.opened==0,"stale save reopened detail");}
 public static void main(String[] args){int failures=0;for(Runnable test:new Runnable[]{LibraryWriteUiRegression::duplicateClickWritesOnce,LibraryWriteUiRegression::failurePreservesInputAndAllowsRetry,LibraryWriteUiRegression::leavingDuringWriteDoesNotReopen})try{test.run();System.out.println("PASS Library async save");}catch(AssertionError e){failures++;System.out.println("FAIL "+e.getMessage());}if(failures>0)throw new AssertionError(failures+" async save failures");}
 __PRODUCTION__
}
""".replace("__PRODUCTION__",body)
with tempfile.TemporaryDirectory() as temp:
    p=Path(temp)/"LibraryWriteUiRegression.java";p.write_text(harness)
    subprocess.run(["javac","-d",temp,str(p)],check=True)
    subprocess.run(["java","-cp",temp,"LibraryWriteUiRegression"],check=True)

# Exercise the real overflow routing, independently from save coordination above.
action_start=ui.index("private void showLibraryProductActions(")
action_brace=ui.index("{",action_start);depth=0
for i in range(action_brace,len(ui)):
    if ui[i]=="{":depth+=1
    elif ui[i]=="}":
        depth-=1
        if depth==0:actions=ui[action_start:i+1];break
action_harness=r"""
import java.util.*;
public class LibraryActionsRegression {
 static final int SHEET_ID=1,TEXT=2,TEAL=3,ORANGE=4,RED=5,MUTED=6,LIME=7;
 static class Typeface {static int NORMAL=0;}
 static class TextUtils {static boolean isEmpty(String s){return s==null||s.isEmpty();}}
 interface Click {void click(View v);}
 static class View {Click listener;void setOnClickListener(Click c){listener=c;}void click(){listener.click(this);}}
 static class TextView extends View {String label;TextView(String s){label=s;}}
 static class Button extends TextView {Button(String s){super(s);}}
 static class LinearLayout extends View {
  List<View> children=new ArrayList<>();void addView(View v){children.add(v);}void addView(View v,LayoutParams p){addView(v);}
  static class LayoutParams {LayoutParams(int w,int h){}}
 }
 static class Dialog {boolean showing=true;LinearLayout content=new LinearLayout();void dismiss(){showing=false;}void show(){showing=true;}LinearLayout findViewById(int id){return content;}}
 static class LibraryGame {String bggId="42",name="Azul",imageUrl="cover",editionId="edition",editionLabel="Italiano",collectionState="owned";Double rating=7.5,weight=2.0;Integer rank=12,voters=30,qualityScore=88,playtime=45,minPlayers=2,maxPlayers=4;String categories="Tiles";}
 static class BggSearchClient {static class Game {String id,name,imageUrl,editionId,editionName,categories;Double rating,weight;Integer rank,voters,qualityScore,playtime,minPlayers,maxPlayers;}}
 static class Db {int restored;void restoreOwned(String id){if(!"42".equals(id))throw new AssertionError("wrong identity");restored++;}}
 Db libraryDb=new Db();List<Dialog> sheets=new ArrayList<>();int sold,deleteConfirm,saves;BggSearchClient.Game edited;
 Dialog bottomSheet(String name){Dialog d=new Dialog();sheets.add(d);return d;}
 TextView menuAction(String name,int color){return new TextView(name);}
 TextView text(String name,int size,int color,int style){return new TextView(name);}
 Button button(String name,int color){return new Button(name);}int dp(int n){return n;}
 void purchaseSource(BggSearchClient.Game game){edited=game;}
 void markLibraryGameSold(LibraryGame game,Dialog parent){if(!parent.showing)throw new AssertionError("parent lost");sold++;}
 void confirmDeleteLibraryGame(LibraryGame game,Dialog parent){if(!parent.showing)throw new AssertionError("parent lost");deleteConfirm++;}
 void saveLibraryChange(LibraryGame game,Runnable change,Dialog sheet,Dialog parent,View button){if(!parent.showing)throw new AssertionError("parent lost");saves++;change.run();}
 static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
 TextView row(Dialog d,String label){for(View v:d.content.children)if(v instanceof TextView&&((TextView)v).label.equals(label))return (TextView)v;throw new AssertionError("missing "+label);}
 __ACTIONS__
 public static void main(String[] args){
  LibraryActionsRegression n=new LibraryActionsRegression();LibraryGame g=new LibraryGame();Dialog parent=new Dialog();
  n.showLibraryProductActions(g,parent);Dialog menu=n.sheets.get(0);check(menu.content.children.size()==3,"owned actions");
  n.row(menu,"Segna come venduto").click();check(!menu.showing&&parent.showing&&n.sold==1,"sale routing");
  n.showLibraryProductActions(g,parent);menu=n.sheets.get(1);n.row(menu,"Elimina definitivamente").click();check(n.deleteConfirm==1&&parent.showing&&!menu.showing,"delete must preserve confirmation parent");
  n.showLibraryProductActions(g,parent);menu=n.sheets.get(2);n.row(menu,"Modifica acquisto").click();check(!menu.showing&&!parent.showing&&n.edited!=null,"edit routing");
  check(n.edited.id.equals(g.bggId)&&n.edited.editionName.equals(g.editionLabel)&&n.edited.editionId.equals(g.editionId)&&n.edited.rating.equals(g.rating)&&n.edited.categories.equals(g.categories),"edit lost game or edition");
  g.collectionState="sold";parent=new Dialog();n.showLibraryProductActions(g,parent);menu=n.sheets.get(3);check(menu.content.children.size()==2,"sold purchase edit must stay unavailable");
  n.row(menu,"Rimetti in Libreria").click();Dialog confirm=n.sheets.get(4);check(n.saves==0&&n.libraryDb.restored==0&&!menu.showing&&parent.showing,"restore ran before confirmation");
  confirm.dismiss();check(n.saves==0,"cancel changed data");
  n.showLibraryProductActions(g,parent);n.row(n.sheets.get(5),"Rimetti in Libreria").click();confirm=n.sheets.get(6);n.row(confirm,"Conferma").click();check(n.saves==1&&n.libraryDb.restored==1&&parent.showing,"restore bypassed save route");
  System.out.println("PASS production Library action routes: owned/sold, edit identity/edition, delete confirmation, restore cancellation/confirmation");
 }
}
""".replace("__ACTIONS__",actions)
with tempfile.TemporaryDirectory() as temp:
    p=Path(temp)/"LibraryActionsRegression.java";p.write_text(action_harness)
    subprocess.run(["javac","-d",temp,str(p)],check=True)
    subprocess.run(["java","-cp",temp,"LibraryActionsRegression"],check=True)
