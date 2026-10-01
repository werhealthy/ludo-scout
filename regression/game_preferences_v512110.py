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
