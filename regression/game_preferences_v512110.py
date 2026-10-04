#!/usr/bin/env python3
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1]
source=root/"app/src/main/java/it/vintedaffari/app/GamePreferenceState.java"
assert source.exists(), "Game favorites and the last Ludo game need durable canonical identity"
harness='''package it.vintedaffari.app;
import java.util.*;
public class GamePreferenceRegression {
 static void expect(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
 static class Memory implements GamePreferenceState.Store {
  Set<String> saved=new HashSet<>();String last="";
  public Set<String> favorites(){return saved;}
  public void favorites(Set<String> value){saved=new HashSet<>(value);}
  public String lastGame(){return last;}
  public void lastGame(String value){last=value;}
 }
 public static void main(String[] args){
  Memory disk=new Memory();GamePreferenceState first=new GamePreferenceState(disk);
  expect(!first.saved("123"),"new game is not saved");
  expect(first.toggle("123"),"first listing saves its game");
  expect(first.saved(" 00123 "),"same canonical game across different listing identities");
  expect(first.saved("123"),"second listing shares favorite");
  first.toggle("456");
  GamePreferenceState reopened=new GamePreferenceState(disk);
  expect(reopened.saved("123")&&reopened.saved("456"),"favorites survive reopening");
  expect(!reopened.toggle("00123")&&!first.saved("123"),"removing on another listing removes the game everywhere");
  expect(first.saved("456"),"other game remains saved");
  Set<String> exposed=reopened.favorites();exposed.clear();
  expect(reopened.saved("456"),"callers cannot mutate preferences by reference");
  for(String id:new String[]{null,"","0","-1","listing:123","abc"}){expect(!first.toggle(id)&&!first.saved(id),"non-game IDs cannot be saved");}
  first.remember("456");expect("456".equals(new GamePreferenceState(disk).lastGame()),"Ludo retains last game through activity/process recreation");
  first.remember(null);first.remember("listing:123");
  expect("456".equals(first.lastGame()),"missing offer or invalid ID does not reset the remembered game");
  first.toggle("456");expect("456".equals(first.lastGame()),"favorite removal does not reset Ludo");
  first.remember("000123");expect("123".equals(first.lastGame()),"intentional next game replaces memory canonically");
  expect(first.favorites().isEmpty(),"all game favorites removed");
  LudoPetState cycle=new LudoPetState();
  cycle.refreshGames(new String[]{"A1","A2","B","bad"},new String[]{"123","00123","456","listing:789"});
  expect("A1".equals(cycle.suggest()),"first game uses its first eligible listing");
  expect(!cycle.eligible("A2")&&!cycle.eligible("bad"),"duplicate and invalid game identities not selectable");
  expect("B".equals(cycle.suggest()),"another advice reaches a distinct BGG game");
  expect("A1".equals(cycle.suggest()),"cycle remains bounded across distinct games");
  cycle.select("A2");expect("A1".equals(cycle.selected()),"duplicate listing cannot move canonical selection");
  cycle.refreshGames(new String[]{"A3","B"},new String[]{"123","456"});
  cycle.select("A3");expect("B".equals(cycle.suggest()),"refresh and remembered-game restore still advance");
  System.out.println("PASS game favorites across listings, persistence, identity and independent last-game memory");
 }
}'''
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp)/"GamePreferenceRegression.java";p.write_text(harness)
 subprocess.run(["javac","-d",tmp,str(source),str(root/"app/src/main/java/it/vintedaffari/app/LudoPetState.java"),str(p)],check=True)
 subprocess.run(["java","-cp",tmp,"it.vintedaffari.app.GamePreferenceRegression"],check=True)

# Execute the actual Activity selection/cache/visibility methods with only platform adapters stubbed.
ui=(root/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text()
def method(marker):
 start=ui.index(marker);brace=ui.index("{",start);depth=1;end=brace+1
 while depth:
  if ui[end]=="{":depth+=1
  elif ui[end]=="}":depth-=1
  end+=1
 return ui[start:end]
methods="\n".join(method(marker) for marker in [
 "private DealRecord petCurrentDeal()","private DealRecord petSuggestion()",
 "private Long petSuggestionGameId(","private void rememberPetChoice(",
 "private void rememberPetRefresh(","private void syncPetVisibility()"])
activity='''package it.vintedaffari.app;
import java.util.*;
public class PetSelectionRegression {
 static void expect(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
 static class TextUtils {static boolean isEmpty(String s){return s==null||s.isEmpty();}}
 static class Snapshot {List<DealRecord> picks=new ArrayList<>();}
 static class PetView {boolean running;void setResumed(boolean v){running=v;}}
 static class Panel {boolean showing;boolean isShowing(){return showing;}}
 static class Memory implements GamePreferenceState.Store {
  Set<String> saved=new HashSet<>();String last="";
  public Set<String> favorites(){return saved;}
  public void favorites(Set<String> value){saved=new HashSet<>(value);}
  public String lastGame(){return last;}
  public void lastGame(String value){last=value;}
 }
 GamePreferenceState preferences;GamePreferenceState gamePreferences(){return preferences;}
 Snapshot petSnapshot;String petFailure;GameRecord petRememberedGame;
 LudoPetState petState=new LudoPetState();
 Map<String,Long> petGameIds=new HashMap<>();
 Map<String,GameRecord> petCanonicalGames=new HashMap<>();
 PetView petView=new PetView(),ludoBackdrop=new PetView();boolean petResumed,petWindowFocused,ludoRoomPanelOpen;String tab="companion";
 Panel activePetPanel,activeGameOverlay,activeDetailDialog;
 '''+methods+'''
 static DealRecord listing(String signature,String game){DealRecord d=new DealRecord();d.signature=signature;d.bggId=game;d.totalCents=500;d.vintedUrl="live";return d;}
 static GameRecord game(long id,String bgg,String name){GameRecord g=new GameRecord();g.id=id;g.bggId=bgg;g.name=name;return g;}
 public static void main(String[] args){
  Memory disk=new Memory();PetSelectionRegression c=new PetSelectionRegression();c.preferences=new GamePreferenceState(disk);
  GameRecord a=game(1,"123","A"),b=game(2,"456","B");DealRecord a1=listing("A1","123"),a2=listing("A2","00123"),b1=listing("B","456");
  c.petSnapshot=new Snapshot();c.petSnapshot.picks.addAll(Arrays.asList(a1,a2,b1));c.petGameIds.put("A1",1L);c.petGameIds.put("A2",1L);c.petGameIds.put("B",2L);
  c.petCanonicalGames.put("123",a);c.petCanonicalGames.put("456",b);c.petState.refreshGames(new String[]{"A1","A2","B"},new String[]{"123","00123","456"});
  c.rememberPetChoice(a1);c.petState.select(c.petCurrentDeal().signature);
  expect("B".equals(c.petState.suggest()),"duplicate A listing must not trap next-game selection");
  // User chooses B while a refresh for A remains in flight.
  c.rememberPetChoice(b1);expect(c.petSuggestion()==b1,"current eligible B listing is shown");
  c.petSnapshot.picks.clear();c.petSnapshot.picks.add(a1);c.petGameIds.clear();c.petGameIds.put("A1",1L);c.petState.refreshGames(new String[]{"A1"},new String[]{"123"});
  c.rememberPetRefresh("123",a);expect("456".equals(c.petSuggestion().bggId),"stale A refresh cannot replace chosen B");
  DealRecord retained=c.petSuggestion();expect(retained.totalCents==null&&retained.vintedUrl==null&&retained.protectedPriceCents==null,"removed B offer cannot show stale price or link");
  expect(c.petSuggestionGameId(retained)==2L,"retained game still opens canonical B");
  PetSelectionRegression reopened=new PetSelectionRegression();reopened.preferences=new GamePreferenceState(disk);reopened.rememberPetRefresh("456",b);
  expect("456".equals(reopened.petSuggestion().bggId),"recreated screen restores B without an eligible offer");
  reopened.petFailure="offline";expect("456".equals(reopened.petSuggestion().bggId),"failure preserves game metadata");
  c.petResumed=true;c.petWindowFocused=true;c.syncPetVisibility();expect(c.petView.running&&c.ludoBackdrop.running,"visible resumed scene animates both layers");
  c.ludoRoomPanelOpen=true;c.syncPetVisibility();expect(!c.petView.running&&!c.ludoBackdrop.running,"embedded panel pauses both layers");c.ludoRoomPanelOpen=false;c.syncPetVisibility();expect(c.petView.running&&c.ludoBackdrop.running,"closing embedded panel resumes both layers");
  c.activeGameOverlay=new Panel();c.activeGameOverlay.showing=true;c.syncPetVisibility();expect(!c.petView.running&&!c.ludoBackdrop.running,"game overlay pauses both layers");
  c.activeGameOverlay=null;c.activePetPanel=new Panel();c.syncPetVisibility();expect(!c.petView.running,"pet panel pauses underlying pet");
  c.activePetPanel=null;c.petWindowFocused=false;c.syncPetVisibility();expect(!c.petView.running,"focus loss pauses pet");
  c.petWindowFocused=true;c.activeDetailDialog=new Panel();c.activeDetailDialog.showing=true;c.syncPetVisibility();expect(!c.petView.running,"listing detail pauses pet");
  c.activeDetailDialog=null;c.petResumed=false;c.syncPetVisibility();expect(!c.petView.running&&!c.ludoBackdrop.running,"paused activity never resumes either layer");
  c.petResumed=true;c.syncPetVisibility();expect(c.petView.running&&c.ludoBackdrop.running,"closing overlays resumes both visible layers");
  System.out.println("PASS actual Activity selection during refresh, removed-offer fallback, recreation and covering-window gates");
 }
}'''
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp)/"PetSelectionRegression.java";p.write_text(activity)
 sources=[root/"app/src/main/java/it/vintedaffari/app"/f for f in ["GamePreferenceState.java","LudoPetState.java","GameRecord.java","DealRecord.java"]]
 subprocess.run(["javac","-d",tmp,*map(str,sources),str(p)],check=True)
 subprocess.run(["java","-cp",tmp,"it.vintedaffari.app.PetSelectionRegression"],check=True)

