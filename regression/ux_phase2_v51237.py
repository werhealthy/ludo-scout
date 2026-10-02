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
     "libraryScopeTabs(owned.size(),sold.size())" in library),
    ("Ludo has explicit Per me Cacce Profilo spaces",
     'addCompanionTab(tabs,"Per me","for_you")' in companion and
     'addCompanionTab(tabs,"Cacce","hunts")' in companion and
     'addCompanionTab(tabs,"Profilo","profile")' in companion),
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

# Execute the production shelf partition and context restoration policy.
import subprocess, tempfile
start=ui.index("private View libraryShelves(");brace=ui.index("{",start);depth=0
for i in range(brace,len(ui)):
    if ui[i]=="{":depth+=1
    elif ui[i]=="}":
        depth-=1
        if depth==0:shelves=ui[start:i+1];break
harness=r"""
import java.util.*;
public class LibraryShelfRegression {
 String libraryScope="owned",libraryQuery="",libraryShelfPositionKey="";
 final int[] libraryShelfPositions={0,0};List<Integer> shown=new ArrayList<>();int rails;
 static class View {}
 static class LibraryGame {int id;LibraryGame(int value){id=value;}}
 static class LinearLayout extends View {
  static int VERTICAL=1;LinearLayout(Object context){}void setOrientation(int value){}
  void addView(View v){}void addView(View v,LayoutParams p){}
  static class LayoutParams {int topMargin;LayoutParams(int w,int h){}}
 }
 int dp(int value){return value;}
 View libraryShelf(List<LibraryGame> games,int start,int end,int shelf){if(shelf!=rails++)throw new AssertionError("shelf order");for(int i=start;i<end;i++)shown.add(games.get(i).id);return new View();}
 __METHOD__
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  for(int count:new int[]{0,1,2,3,4,9,100,101}){
   LibraryShelfRegression n=new LibraryShelfRegression();List<LibraryGame> games=new ArrayList<>();for(int i=0;i<count;i++)games.add(new LibraryGame(i));
   n.libraryShelves(games);check(n.rails==2,"two shelves required");check(n.shown.size()==count,"lost games");for(int i=0;i<count;i++)check(n.shown.get(i)==i,"order/duplicate");
   n.libraryShelfPositions[0]=80;n.libraryShelfPositions[1]=130;n.rails=0;n.shown.clear();n.libraryShelves(games);check(n.libraryShelfPositions[0]==80&&n.libraryShelfPositions[1]==130,"refresh lost shelf position");
   n.libraryScope="sold";n.rails=0;n.libraryShelves(games);check(n.libraryShelfPositions[0]==0&&n.libraryShelfPositions[1]==0,"sold reused owned offset");
   n.libraryShelfPositions[0]=100;n.libraryQuery="Azul";n.rails=0;n.libraryShelves(games);check(n.libraryShelfPositions[0]==0,"search starts outside result");
  }
  System.out.println("PASS production shelf partition: all games exactly once, two rails, refresh/scope/search positions");
 }
}
""".replace("__METHOD__",shelves)
with tempfile.TemporaryDirectory() as temp:
    path=Path(temp)/"LibraryShelfRegression.java";path.write_text(harness)
    subprocess.run(["javac","-d",temp,str(path)],check=True)
    subprocess.run(["java","-cp",temp,"LibraryShelfRegression"],check=True)
