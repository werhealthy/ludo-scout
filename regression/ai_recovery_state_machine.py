#!/usr/bin/env python3
"""Run real Java recovery policy with host-only JSON/Android value adapters.

No provider, Vinted request or phone DB write. Replacing recovery eligibility with
the old AUTO_EXCLUDED/UNCERTAIN rule must make the six recorded cases fail.
"""
from pathlib import Path
import subprocess
import tempfile
import re
import json
import sqlite3

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'app/src/main/java/it/vintedaffari/app'

def java_string(source, name):
    match=re.search(r'\b'+name+r'\s*=\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+);',source)
    assert match, name+' is not a Java string constant'
    return ''.join(json.loads(value) for value in re.findall(r'"(?:[^"\\]|\\.)*"',match.group(1)))

def run_sql():
    policy=(SRC/'AiEnginePolicy.java').read_text(encoding='utf-8')
    evidence=(SRC/'AiCategoryEvidence.java').read_text(encoding='utf-8')
    db=sqlite3.connect(':memory:')
    db.executescript('''
CREATE TABLE market_listings(id INTEGER PRIMARY KEY,legacy_signature TEXT,temp_fingerprint TEXT,
 vinted_item_id TEXT,vinted_title TEXT,brand TEXT,observed_text TEXT,listing_photos_csv TEXT,image_url TEXT,
 lifecycle TEXT,enrichment_state TEXT,match_state TEXT,last_error TEXT,manual_review_required INTEGER,category_normalized TEXT);
CREATE TABLE deals(signature TEXT,confirmed INTEGER,verification_state TEXT);
CREATE TABLE listing_overrides(signature TEXT,item_id TEXT);
CREATE TABLE queue_controls(name TEXT PRIMARY KEY,value INTEGER,updated_at INTEGER,text_value TEXT);
CREATE TABLE observations(signature TEXT,observed_at INTEGER,verification_reason TEXT);
''')
    db.execute("INSERT INTO market_listings VALUES(1,'sig','fp','42','Gioco da tavolo quiz','Giunti','Gioco da tavolo quiz','photos',NULL,'AUTO_FILTERED','AUTO_FILTERED','AUTO_FILTERED_NON_GAME','Nessun candidato BGG sufficientemente forte: scarto automatico, non fact-check',0,NULL)")
    sql='SELECT l.id FROM market_listings l WHERE '+java_string(policy,'RECOVERABLE_SQL')
    assert db.execute(sql).fetchall()==[(1,)],'BGG miss is a recoverable category miss'
    db.execute("UPDATE market_listings SET match_state='CATEGORY_INCOMPATIBLE'")
    assert db.execute(sql).fetchall()==[],'explicit incompatible state cannot recover'
    db.execute("UPDATE market_listings SET lifecycle='ACTIVE',enrichment_state='PENDING_ANALYSIS',match_state='PENDING_ANALYSIS'")
    db.execute(java_string(evidence,'STORE_SQL'),('1',12345,'snapshot-key'))
    read=java_string(evidence,'READ_SQL')
    assert db.execute(read,('1',)).fetchone()[4]=='snapshot-key'
    db.execute("INSERT INTO observations VALUES('sig',1234,'AI category recovery: base game visually recognized; BGG pending')")
    # Analysis mutates every observation reason; independent evidence must survive.
    db.execute("UPDATE observations SET verification_reason='local analysis result'")
    db.execute("UPDATE market_listings SET enrichment_state='LOCAL_ONLY',match_state='BGG_MATCH_REQUIRED',last_error=''")
    assert db.execute(read,('1',)).fetchone()[4]=='snapshot-key','evidence survives local analysis'
    assert db.execute("SELECT observed_at FROM observations").fetchone()==(1234,),'history is unchanged'
    for mutation,restore in [
        ("UPDATE deals SET confirmed=1","UPDATE deals SET confirmed=0"),
        ("UPDATE market_listings SET manual_review_required=1","UPDATE market_listings SET manual_review_required=0"),
        ("UPDATE market_listings SET lifecycle='USER_HIDDEN'","UPDATE market_listings SET lifecycle='ACTIVE'"),
        ("INSERT INTO listing_overrides VALUES(NULL,'42')","DELETE FROM listing_overrides")]:
        if mutation.startswith('UPDATE deals'):
            db.execute("INSERT INTO deals VALUES('sig',0,'MATCH_UNCERTAIN')")
        db.execute(mutation)
        assert db.execute(read,('1',)).fetchall()==[],mutation
        db.execute(restore)
    market=(SRC/'MarketStore.java').read_text(encoding='utf-8')
    helper=market[market.index('public boolean hasAiCategoryRecoveryEvidence'):market.index('public boolean quarantineUnresolvedObservation')]
    assert 'AiCategoryEvidence.has' in helper,'runtime must read independent category evidence'
    quarantine=market[market.index('public boolean autoQuarantineGame'):market.index('public int quarantineLegacyBggReviewBacklog')]
    assert 'AiCategoryEvidence.has' in quarantine,'BGG no-candidate must preserve AI-positive listings'
    print('PASS production recovery/evidence SQL and integration guards')

def run_policy():
    with tempfile.TemporaryDirectory() as tmp:
        base = Path(tmp)
        files = {
            'org/json/JSONObject.java': '''package org.json;
import java.util.*;
public class JSONObject {
 private final Map<String,Object> data=new HashMap<>();
 public JSONObject put(String k,Object v){data.put(k,v);return this;}
 public String optString(String k){return optString(k,"");}
 public String optString(String k,String d){Object v=data.get(k);return v==null?d:String.valueOf(v);}
 public int optInt(String k,int d){Object v=data.get(k);return v instanceof Number?((Number)v).intValue():d;}
 public boolean optBoolean(String k,boolean d){Object v=data.get(k);return v instanceof Boolean?(Boolean)v:d;}
 public JSONArray optJSONArray(String k){Object v=data.get(k);return v instanceof JSONArray?(JSONArray)v:null;}
}''',
            'org/json/JSONArray.java': '''package org.json; import java.util.*;
public class JSONArray {private final List<Object> values=new ArrayList<>();
 public JSONArray put(Object value){values.add(value);return this;} public int length(){return values.size();}}
''',
            'android/graphics/Rect.java': '''package android.graphics;
public class Rect { public Rect(int a,int b,int c,int d){} public Rect(Rect r){} }''',
            'it/vintedaffari/app/GameAnalysis.java': '''package it.vintedaffari.app;
public class GameAnalysis {public String status,candidateName,productPublisher;public Double matchConfidence;
 public Integer totalCents,benchmarkCents;}''',
            'it/vintedaffari/app/BggSearchClient.java': '''package it.vintedaffari.app;
public class BggSearchClient {public static class Game {public String name; public int searchScore;
 public java.util.List<String> aliases=new java.util.ArrayList<>();}}''',
            'it/vintedaffari/app/RecoveryPolicyProbe.java': '''package it.vintedaffari.app;
import org.json.*;
public class RecoveryPolicyProbe {
 static int failures=0,checks=0;
 static void check(boolean ok,String name){checks++;if(!ok){failures++;System.out.println("FAIL "+name);}}
 static JSONObject row(String title,String brand){
  VintedCard c=new VintedCard(title,brand,"",10,null,null,new android.graphics.Rect(0,0,1,1),title);
  return new JSONObject().put("title",title).put("brand",brand).put("source_text",title)
   .put("local_type",ListingClassifier.classify(c).type.name()).put("lifecycle","AUTO_FILTERED")
   .put("listing_match_state","AUTO_FILTERED_NON_GAME").put("engine_enrichment","AUTO_FILTERED")
   .put("engine_last_error","Nessuna prova positiva di prodotto gioco da tavolo")
   .put("engine_has_observation",true).put("photos",new JSONArray().put("https://images.vinted.net/photo.jpg"));
 }
 public static void main(String[] args){
  JSONObject answer=new JSONObject().put("proposed_type","BASE_GAME").put("product_title","Board game")
   .put("evidence","Scatola di gioco visibile; Componenti di gioco riconoscibili");
  String[][] real={{"Indovina Chi? - gioco da tavolo","Hasbro"},{"Dobbel kaart spelletje","Dobble"},
   {"Cluedo Junior","Hasbro"},{"Gioco da tavolo \\\"5 secondi\\\"","Megableu"},
   {"Gioco mangia ippo","Hasbro"},{"Gioco da tavolo quiz","Giunti"}};
  for(String[] input:real){JSONObject r=row(input[0],input[1]);check(AiEnginePolicy.recover(r,answer),input[0]+" can re-enter BGG");}
  JSONObject r=row("Cluedo Junior","Hasbro");r.put("engine_confirmed",1);check(!AiEnginePolicy.recover(r,answer),"human confirmation");
  r=row("Cluedo Junior","Hasbro");r.put("engine_manual_review",1);check(!AiEnginePolicy.recover(r,answer),"manual review");
  r=row("Cluedo Junior","Hasbro");r.put("bgg_id","123");check(!AiEnginePolicy.recover(r,answer),"existing identity");
  r=row("Cluedo Junior","Hasbro");r.put("photos",new JSONArray());check(!AiEnginePolicy.recover(r,answer),"missing photos");
  r=row("Cluedo Junior","Hasbro");r.put("listing_match_state","CATEGORY_INCOMPATIBLE");check(!AiEnginePolicy.recover(r,answer),"incompatible category state");
  r=row("Cluedo Junior","Hasbro");r.put("engine_enrichment","AUTO_EXCLUDED");check(AiEnginePolicy.recover(r,answer),"original recovery preserved");
  r=row("Libro Cluedo Junior","Hasbro");check(!AiEnginePolicy.recover(r,answer),"strong non-game evidence");
  r=row("Cluedo Junior","Hasbro");r.put("listing_match_state","AUTO_FILTERED_COLLISION").put("engine_last_error","Collisione reale confermata");check(!AiEnginePolicy.recover(r,answer),"supported collision");
  r=row("Cluedo Junior","Hasbro");r.put("lifecycle","USER_HIDDEN");check(!AiEnginePolicy.recover(r,answer),"hidden lifecycle");
  for(String type:new String[]{"NON_GAME","EXPANSION","BUNDLE","ACCESSORY_COMPONENT","UNKNOWN"}){
   answer.put("proposed_type",type);check(!AiEnginePolicy.recover(row("Cluedo Junior","Hasbro"),answer),"AI "+type);
  }
  System.out.println(checks+" checks; "+failures+" failures");if(failures>0)System.exit(1);
 }
}''',
        }
        for name in ['AiEnginePolicy.java','ListingClassifier.java','BoardGameIntakeGate.java','VintedCard.java']:
            files['it/vintedaffari/app/'+name] = (SRC/name).read_text(encoding='utf-8')
        for path, content in files.items():
            target=base/path
            target.parent.mkdir(parents=True,exist_ok=True)
            target.write_text(content,encoding='utf-8')
        subprocess.run(['java','--module','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-d',str(base/'classes'),*[str(base/p) for p in files]],check=True)
        subprocess.run(['java','-cp',str(base/'classes'),'it.vintedaffari.app.RecoveryPolicyProbe'],check=True)

if __name__ == '__main__':
    run_policy()
    run_sql()
