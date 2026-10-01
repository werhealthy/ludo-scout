#!/usr/bin/env python3
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1]
source=root/"app/src/main/java/it/vintedaffari/app/LudoPetState.java"
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
 subprocess.run(["javac","-d",tmp,str(source),str(p)],check=True)
 subprocess.run(["java","-cp",tmp,"it.vintedaffari.app.PetRegression"],check=True)
