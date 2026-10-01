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
