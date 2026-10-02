#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ui=(ROOT/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
hunts=(ROOT/"app/src/main/java/it/vintedaffari/app/HuntDatabase.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

discover=ui[ui.index("private View discoverHeader()"):ui.index("private void addDiscoverCategories",ui.index("private View discoverHeader()"))]
catalog=ui[ui.index("private View catalogRowV51"):ui.index("private int photoCount",ui.index("private View catalogRowV51"))]
library=ui[ui.index("private void renderLibrary()"):ui.index("private View libraryRow",ui.index("private void renderLibrary()"))]
companion=ui[ui.index("private void renderCompanion()"):ui.index("private void renderLibraryInsights",ui.index("private void renderCompanion()"))]
huntrow=ui[ui.index("private View huntRow"):ui.index("private void addHuntFlow",ui.index("private View huntRow"))]

checks=[
    ("phase2 release identity",
     "versionName '5.12." in build and
     "applicationId 'it.vintedaffari.app'" in build and
     "1000000 + ciVersionCode.toInteger()" in build),
    ("Home uses the updated Discover editorial composition",
     "private void renderDiscover()" in ui and
     '"Bentornato,"' in discover and
     "addDiscoverFreshRail" in ui and
     "addDiscoverTopRatedRail" in ui and
     "addDiscoverValueRail" in ui),
    ("Catalog preview is a true horizontal listing row",
     "new LinearLayout(this)" in catalog and
     "setOrientation(LinearLayout.HORIZONTAL)" in catalog and
     "row.addView(info,new LinearLayout.LayoutParams(0,-2,1))" in catalog and
     "LinearLayout c=verticalCard()" not in catalog),
    ("Library keeps collection rows search and history scope",
     "body.addView(libraryShelves(shown))" in library and
     "body.addView(librarySummaryCard(owned))" not in library and
     "librarySearchBar()" in library and
     "showLibraryArchiveMenu(sold.size())" in library and
     "libraryScopeTabs(" not in library),
    ("Ludo uses Esplorazione and Casa with existing collection and pet spaces",
     'addLudoRoomTab(tabs,"Esplorazione",LudoRoomState.EXPLORE)' in ui and
     'addLudoRoomTab(tabs,"Casa",LudoRoomState.HOME)' in ui and
     'renderLudoHomeScene();renderLibrary();return;' in companion and
     'openPetSpace(true)' in companion and 'openPetSpace(false)' in companion),
    ("Ludo root page no longer exposes a back affordance",
     'TextView back=text("‹"' not in ui[ui.index("private View companionHeader"):ui.index("private boolean hasGreatDeal")]),
    ("Hunt rows are horizontal",
     "setOrientation(LinearLayout.HORIZONTAL)" in huntrow and "verticalCard()" not in huntrow),
    ("Adding a game fulfills its active Hunt",
     "huntDb.removeByBggId(g.id)" in ui and
     "public synchronized void removeByBggId" in hunts),
]

for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("5.12.37 UX phase 2 regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} 5.12.37 UX phase 2 guards")

# Execute production viewport partition, rating fractions and sale differences.
import subprocess, tempfile
def method(name):
    start=ui.index(name);brace=ui.index("{",start);depth=0
    for i in range(brace,len(ui)):
        if ui[i]=="{":depth+=1
        elif ui[i]=="}":
            depth-=1
            if depth==0:return ui[start:i+1]
harness=r"""
import java.util.*;
public class LibraryShelfRegression {
 List<Integer> shown=new ArrayList<>();int rows;
 static class View {}
 static class LibraryGame {int id;LibraryGame(int value){id=value;}}
 static class LinearLayout extends View {
  LinearLayout(Object context){}void removeAllViews(){}void addView(View v,LayoutParams p){}
  static class LayoutParams {int topMargin;LayoutParams(int w,int h){}}
 }
 int dp(int value){return value;}
 View libraryShelf(List<LibraryGame> games,int start,int end,int capacity){if(end-start>capacity)throw new AssertionError("overflow");rows++;for(int i=start;i<end;i++)shown.add(games.get(i).id);return new View();}
 __METHODS__
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  check(libraryShelfCapacity(320,1f)==2,"narrow screen");check(libraryShelfCapacity(372,1f)==3,"wider phone");check(libraryShelfCapacity(372,1.5f)==2,"large text must reduce density");check(libraryShelfCapacity(0,1f)==1,"zero width");
  for(int width:new int[]{0,240,320,372,600,1000})for(float font:new float[]{1f,1.5f,2f})for(int count:new int[]{0,1,2,3,4,9,100,101}){
   LibraryShelfRegression n=new LibraryShelfRegression();List<LibraryGame> games=new ArrayList<>();for(int i=0;i<count;i++)games.add(new LibraryGame(i));int capacity=libraryShelfCapacity(width,font);
   n.fillLibraryShelves(new LinearLayout(n),games,capacity);check(n.rows==(count+capacity-1)/capacity,"shelf count");check(n.shown.size()==count,"lost games");for(int i=0;i<count;i++)check(n.shown.get(i)==i,"order/duplicate");
  }
  for(int n=1;n<=5;n++){check(libraryHeartFill(null,n)==0,"unrated");check(libraryHeartFill(0,n)==0,"zero");check(libraryHeartFill(10,n)==1,"five hearts");}
  check(libraryHeartFill(7,3)==1&&libraryHeartFill(7,4)==.5f&&libraryHeartFill(7,5)==0,"legacy half heart");
  check(librarySaleDifference(null,100)==null&&librarySaleDifference(100,null)==null,"unknown is not zero");check(librarySaleDifference(0,100)==-100L,"zero proceeds");check(librarySaleDifference(100,0)==100L,"free acquisition");check(librarySaleDifference(100,100)==0L,"break even");check(librarySaleDifference(Integer.MAX_VALUE,-1)==2147483648L,"difference overflow");
  System.out.println("PASS production vertical shelves: all games once, viewport/font capacity; null/zero/half hearts; unknown/negative/zero sale differences");
 }
}
""".replace("__METHODS__","\n".join(method(name) for name in ["private static int libraryShelfCapacity(","private void fillLibraryShelves(","private static float libraryHeartFill(","private static Long librarySaleDifference("]))
with tempfile.TemporaryDirectory() as temp:
    path=Path(temp)/"LibraryShelfRegression.java";path.write_text(harness)
    subprocess.run(["javac","-d",temp,str(path)],check=True)
    subprocess.run(["java","-cp",temp,"LibraryShelfRegression"],check=True)

# Provider stays outside the scroll viewport; original purchase details remain available.
detail=method("private void openLibraryDetail(LibraryGame g,int")
assert "footer.addView(bgg" in detail and "addProductSection(box,bgg" not in detail
assert "safe.bottom" in detail and "page.addView(scrollStage" in detail
assert "Dettagli dell’acquisto" in detail and "bundleTotalCents" in detail
assert "librarySaleDifference(g.salePriceCents,total)" in detail
assert "new EndPullScrollView(this)" in detail
transition=method("private void prepareLibraryGameTransition(")
assert transition.index("uiDataIo.execute")<transition.index("gameStatsByBggId")<transition.index("runOnUiThread")
assert "!source.isShowing()" in transition and "canonical==null||snapshot==null" in transition
assert "installPullToGame(sc,hint,canonical.id,source)" in transition and "new GameRecord" not in transition
print("PASS Library pinned provider, preserved details, canonical prepared pull and missing-local fallback")

# Execute the actual transition readiness gate for both permitted source types.
readiness=r"""
import java.util.*;
public class LibraryPullReadinessRegression {
 static class Dialog {boolean showing=true;boolean isShowing(){return showing;}}
 static class PreparedGameOverlay {long gameId;Dialog dialog;PreparedGameOverlay(long id){gameId=id;dialog=new Dialog();}}
 Dialog activeDetailDialog,activeGameOverlay;
 Set<Dialog> libraryDetailDialogs=new HashSet<>();
 Map<Dialog,PreparedGameOverlay> preparedGameOverlays=new HashMap<>();
 boolean isFinishing(){return false;}boolean isDestroyed(){return false;}
 __READY__
 static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
 public static void main(String[] args){
  LibraryPullReadinessRegression n=new LibraryPullReadinessRegression();Dialog source=new Dialog();
  n.preparedGameOverlays.put(source,new PreparedGameOverlay(42));
  check(!n.readyGameTransition(source,42),"unregistered source");
  n.libraryDetailDialogs.add(source);check(n.readyGameTransition(source,42),"Library must open canonical game");
  check(!n.readyGameTransition(source,43),"wrong canonical identity");
  n.activeGameOverlay=new Dialog();check(!n.readyGameTransition(source,42),"duplicate overlay");
  n.activeGameOverlay=null;source.showing=false;check(!n.readyGameTransition(source,42),"closed Library source");
  source.showing=true;n.libraryDetailDialogs.remove(source);check(!n.readyGameTransition(source,42),"dismissed registration");
  n.activeDetailDialog=source;check(n.readyGameTransition(source,42),"existing listing gesture");
  n.preparedGameOverlays.get(source).dialog=null;check(!n.readyGameTransition(source,42),"consumed preparation");
  System.out.println("PASS actual transition readiness: Library and listing, closed/unrelated source, canonical identity, duplicate and consumed preparation");
 }
}
""".replace("__READY__",method("private boolean readyGameTransition("))
with tempfile.TemporaryDirectory() as temp:
    path=Path(temp)/"LibraryPullReadinessRegression.java";path.write_text(readiness)
    subprocess.run(["javac","-d",temp,str(path)],check=True)
    subprocess.run(["java","-cp",temp,"LibraryPullReadinessRegression"],check=True)
assert "libraryDetailDialogs.add(d)" in detail and "libraryDetailDialogs.remove(d)" in detail
