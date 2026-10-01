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
  System.out.println("PASS game favorites across listings, persistence, identity and independent last-game memory");
 }
}'''
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp)/"GamePreferenceRegression.java";p.write_text(harness)
 subprocess.run(["javac","-d",tmp,str(source),str(p)],check=True)
 subprocess.run(["java","-cp",tmp,"it.vintedaffari.app.GamePreferenceRegression"],check=True)
