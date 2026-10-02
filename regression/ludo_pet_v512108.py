#!/usr/bin/env python3
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1]
source=root/"app/src/main/java/it/vintedaffari/app/LudoPetState.java"
room_source=root/"app/src/main/java/it/vintedaffari/app/LudoRoomState.java"
swipe_source=root/"app/src/main/java/it/vintedaffari/app/LudoRoomSwipe.java"
assert room_source.exists() and swipe_source.exists(), "Ludo rooms and directional swipe policy missing"
assert source.exists(), "Pet interaction missing: suggestions must resolve to current eligible IDs"
harness='''package it.vintedaffari.app;
public class PetRegression {
 static void expect(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
 LudoPetState s=new LudoPetState();
 s.refresh(new String[]{"a","b"});expect(s.selected()==null,"must wait for intentional suggestion");
 expect("a".equals(s.suggest()),"first real suggestion");
 s.refresh(new String[]{"b","a"});expect("a".equals(s.selected()),"refresh preserves active suggestion");
 s.refresh(new String[]{"b"});expect(s.selected()==null,"removed offer cannot remain actionable");
 expect("b".equals(s.suggest()),"replacement uses current eligible offer");
 s.refresh(new String[]{});expect(s.suggest()==null&&s.selected()==null,"empty never invents suggestion");
 s.refresh(new String[]{null,"","x","x","y"});expect("x".equals(s.suggest()),"invalid IDs ignored");
 expect("y".equals(s.suggest()),"new suggestion advances");expect("x".equals(s.suggest()),"wrap is bounded");
 expect(!LudoPetState.animate(false,true,true),"reduced motion");
 expect(!LudoPetState.animate(true,false,true),"paused screen");
 expect(!LudoPetState.animate(true,true,false),"detached scene");
 expect(LudoPetState.animate(true,true,true),"active visible scene");
 System.out.println("PASS 12 pet suggestion/lifecycle scenarios");
 }
}'''
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp)/"PetRegression.java";p.write_text(harness)
 subprocess.run(["javac","-d",tmp,str(source),str(root/"app/src/main/java/it/vintedaffari/app/GamePreferenceState.java"),str(p)],check=True)
 subprocess.run(["java","-cp",tmp,"it.vintedaffari.app.PetRegression"],check=True)

room_harness=r'''package it.vintedaffari.app;
public class LudoRoomsRegression {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
  LudoRoomState state=new LudoRoomState("invalid",-5,180);check(!state.isHome()&&state.position("explore")==0,"safe default");
  check(state.switchTo("home",640)==180&&state.isHome(),"open Casa at its position");
  check(state.switchTo("explore",360)==640,"return restores Esplorazione position");
  LudoRoomState reload=new LudoRoomState(state.room(),state.position("explore"),state.position("home"));check(reload.position("home")==360&&reload.position("explore")==640,"recreation");
  check("hunts".equals(LudoRoomState.swipeTarget("explore",1)),"swipe left opens Cacce before Casa");
  check("home".equals(LudoRoomState.swipeTarget("hunts",1)),"second swipe opens Libreria");
  check("hunts".equals(LudoRoomState.swipeTarget("home",-1)),"back from Libreria reaches Cacce");
  check("explore".equals(LudoRoomState.swipeTarget("hunts",-1)),"back from Cacce reaches Esplorazione");
  state.switchTo("hunts",640);state.recordScroll(270);check(state.switchTo("home",270)==360,"third room keeps Casa position");check(state.switchTo("hunts",400)==270,"Cacce restores independently");
  check(LudoRoomState.EXPLORE.equals(LudoRoomState.swipeTarget("explore",-1)),"first room boundary");
  check(LudoRoomState.HOME.equals(LudoRoomState.swipeTarget("home",1)),"room boundary");
  LudoRoomSwipe swipe=new LudoRoomSwipe();swipe.down(200,200);check(!swipe.move(198,280,8),"vertical must remain scroll");check(!swipe.move(100,282,8)&&swipe.release(60,282,72)==0,"vertical cannot turn into room swipe");
  swipe.down(200,200);check(!swipe.move(195,203,8),"touch slop");check(swipe.move(150,202,8),"intentional horizontal claim");check(swipe.release(100,203,72)==1,"horizontal opens Casa");
  swipe.down(200,200);check(swipe.move(230,200,8)&&swipe.release(230,200,72)==0,"short drag does not switch");
  swipe.down(100,200);check(swipe.move(150,203,8)&&swipe.release(200,204,72)==-1,"right opens Esplorazione");
  swipe.down(100,200);swipe.move(160,201,8);swipe.cancel();check(swipe.release(220,201,72)==0,"cancel or multipointer cannot switch");
  swipe.down(200,200);check(!swipe.move(150,245,8)&&swipe.release(100,280,72)==0,"diagonal stays harmless");
  System.out.println("PASS room restoration/boundaries and horizontal versus vertical/slop/short/cancel/diagonal input");
 }
}'''
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp)/"LudoRoomsRegression.java";p.write_text(room_harness)
 subprocess.run(["javac","-d",tmp,str(room_source),str(swipe_source),str(p)],check=True)
 subprocess.run(["java","-cp",tmp,"it.vintedaffari.app.LudoRoomsRegression"],check=True)
ui=(root/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text()
assert 'navItem(LudoIcons.BOOK_OPEN,"Libreria","library")' not in ui
assert 'if("library".equals(value)){openLudoHome();return;}' in ui
assert 'renderLudoHomeScene();renderLibrary();return;' in ui
assert 'addLudoRoomTab(tabs,"Esplorazione",LudoRoomState.EXPLORE)' in ui
assert 'addLudoRoomTab(tabs,"Libreria",LudoRoomState.HOME)' in ui
assert 'addLudoRoomTab(tabs,"Cacce",LudoRoomState.HUNTS)' in ui
assert 'refreshHost.setRoomSwipeHandler' in ui
assert 'petView.setExplorer(true)' in ui and 'ludoFireplaceBackground()' in ui
assert 'Avvia nuove ricerche · test' not in ui
assert 'https://www.vinted.it/catalog?search_text=catan' not in ui
print("PASS Ludo composition: preserved library route, real collection, three selector tabs and themed scenes")
import runpy
runpy.run_path(str(root/"regression/ludo_monthly_overview.py"))

def activity_method(signature):
 start=ui.index(signature);brace=ui.index("{",start);depth=0
 for i in range(brace,len(ui)):
  if ui[i]=="{":depth+=1
  elif ui[i]=="}":
   depth-=1
   if depth==0:return ui[start:i+1]
bridge=r'''package it.vintedaffari.app;
import java.util.*;
public class LudoRoomBridgeRegression {
 String tab="catalog",renderedLudoRoom="";LudoRoomState ludoRooms=new LudoRoomState("explore",300,500);
 Map<String,Integer> tabScrollPositions=new HashMap<>();Scroll scroll=new Scroll();int saves,switches;
 static class Scroll {int y=900;int getScrollY(){return y;}}
 LudoRoomState ludoRoomState(){return ludoRooms;}
 void saveLudoRooms(){saves++;}
 void navigate(String value){tab=value;}
 void switchLudoRoom(String value){switches++;ludoRooms.switchTo(value,scroll.y);}
 __ACTUAL__
 static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
 public static void main(String[] args){
  LudoRoomBridgeRegression n=new LudoRoomBridgeRegression();n.openLudoHome();check("companion".equals(n.tab)&&n.ludoRooms.isHome(),"legacy route must reach Casa");check(n.tabScrollPositions.get("companion")==500,"legacy route home position");check(n.ludoRooms.position("explore")==300,"other page scroll must not overwrite room");
  check(n.isLibraryVisible(),"Casa refreshes collection");n.ludoRooms.switchTo("explore",500);check(!n.isLibraryVisible(),"Esplorazione must not pretend collection visible");
  n.tab="library";check(n.isLibraryVisible(),"legacy visibility");n.tab="catalog";n.prepareLudoNavigation("companion");check(n.tabScrollPositions.get("companion")==300,"normal entry room position");
  n.tab="companion";n.renderedLudoRoom="explore";n.scroll.y=640;n.recordLudoRoomPosition();check(n.ludoRooms.position("explore")==640,"current room position saved");
  n.openLudoHome();check(n.switches==1,"same destination uses room switch");
  System.out.println("PASS actual Library-to-Casa bridge, collection visibility and independent navigation positions");
 }
}'''.replace("__ACTUAL__","\n".join(activity_method(s) for s in ["private void openLudoHome(","private boolean isLibraryVisible(","private void prepareLudoNavigation(","private void recordLudoRoomPosition("]))
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp)/"LudoRoomBridgeRegression.java";p.write_text(bridge)
 subprocess.run(["javac","-d",tmp,str(room_source),str(p)],check=True)
 subprocess.run(["java","-cp",tmp,"it.vintedaffari.app.LudoRoomBridgeRegression"],check=True)

draft_harness=r'''
public class LudoSearchDraftRegression {
 String ludoSearchQuery="Azul";Input ludoSearchInput;
 static class Input {String value;Input(String s){value=s;}String getText(){return value;}}
 __DRAFT__
 public static void main(String[] args){
  LudoSearchDraftRegression n=new LudoSearchDraftRegression();
  if(!"Azul".equals(n.ludoSearchDraft()))throw new AssertionError("restored text lost");
  n.ludoSearchInput=new Input("Mille Fiori");n.ludoSearchQuery=n.ludoSearchDraft();n.ludoSearchInput=null;
  if(!"Mille Fiori".equals(n.ludoSearchDraft()))throw new AssertionError("live text lost during view rebuild");
  n.ludoSearchInput=new Input("");if(!n.ludoSearchDraft().isEmpty())throw new AssertionError("cleared query reused old text");
  System.out.println("PASS actual search draft: restored, live/rebuilt and explicitly cleared text");
 }
}'''.replace("__DRAFT__",activity_method("private String ludoSearchDraft("))
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp)/"LudoSearchDraftRegression.java";p.write_text(draft_harness)
 subprocess.run(["javac","-d",tmp,str(p)],check=True)
 subprocess.run(["java","-cp",tmp,"LudoSearchDraftRegression"],check=True)
assert 'search.setText(ludoSearchQuery)' in ui and '.putString("search",ludoSearchDraft())' in ui

month_harness=r'''package it.vintedaffari.app;
import java.time.*;
public class LudoMonthBoundaryRegression {
 static void check(long now,long want){if(LudoMonthlyOverview.monthStart(now)!=want)throw new AssertionError("Europe/Rome calendar month");}
 public static void main(String[] args){
  check(Instant.parse("2026-10-01T00:05:00Z").toEpochMilli(),Instant.parse("2026-09-30T22:00:00Z").toEpochMilli());
  check(Instant.parse("2026-10-31T23:30:00Z").toEpochMilli(),Instant.parse("2026-10-31T23:00:00Z").toEpochMilli());
  check(Instant.parse("2026-03-31T23:30:00Z").toEpochMilli(),Instant.parse("2026-03-31T22:00:00Z").toEpochMilli());
  LudoRoomState restored=new LudoRoomState("hunts",110,220,330);
  if(restored.position("hunts")!=220||restored.switchTo("home",240)!=330||restored.switchTo("explore",350)!=110||restored.switchTo("hunts",130)!=240)throw new AssertionError("all room positions must survive persistence");
  System.out.println("PASS calendar month in Europe/Rome across DST and all three restored room positions");
 }
}'''
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp)/"LudoMonthBoundaryRegression.java";p.write_text(month_harness)
 subprocess.run(["javac","-d",tmp,str(room_source),str(root/"app/src/main/java/it/vintedaffari/app/LudoMonthlyOverview.java"),str(p)],check=True)
 subprocess.run(["java","-cp",tmp,"it.vintedaffari.app.LudoMonthBoundaryRegression"],check=True)
