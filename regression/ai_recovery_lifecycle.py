#!/usr/bin/env python3
"""Execute production candidate SQL and JVM policy; Android integration is tested separately."""
import json
import os
from pathlib import Path
import re
import shutil
import sqlite3
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'app/src/main/java/it/vintedaffari/app'

def constant(name):
    source=(SRC/'AiEngineListings.java').read_text(encoding='utf-8')
    if name.startswith('AI_RECOVERY_'): source=(SRC/'MarketStore.java').read_text(encoding='utf-8')
    if name.startswith('AiEnginePolicy.'):
        source=(SRC/'AiEnginePolicy.java').read_text(encoding='utf-8')
        name=name.split('.')[-1]
    match=re.search(r'\bString\s+'+re.escape(name)+r'\s*=\s*(.*?);',source,re.S)
    if not match: raise AssertionError('missing SQL constant: '+name)
    expression=match.group(1)
    tokens=re.findall(r'"(?:\\.|[^"\\])*"|[A-Za-z_][\w.]*',expression)
    return ''.join(json.loads(t) if t.startswith('"') else constant(t) for t in tokens)

def sql_checks():
    db=sqlite3.connect(':memory:')
    db.executescript('''
      CREATE TABLE market_listings(id INTEGER PRIMARY KEY,vinted_title TEXT,lifecycle TEXT,enrichment_state TEXT,match_state TEXT,last_error TEXT,manual_review_required INTEGER,legacy_signature TEXT,temp_fingerprint TEXT,vinted_item_id TEXT,game_id INTEGER);
      CREATE TABLE observations(signature TEXT,verification_reason TEXT);
      CREATE TABLE games(id INTEGER PRIMARY KEY,bgg_id TEXT);
      CREATE TABLE deals(signature TEXT,confirmed INTEGER,verification_state TEXT);
      CREATE TABLE listing_overrides(signature TEXT,item_id TEXT);
    ''')
    titles=['Indovina Chi? - gioco da tavolo','Dobbel kaart spelletje','Cluedo Junior','Gioco da tavolo "5 secondi"','Gioco mangia ippo','Gioco da tavolo quiz']
    for i,title in enumerate(titles,1):
        reason='Nessun candidato BGG sufficientemente forte: scarto automatico, non fact-check' if i in (1,4,6) else 'Nessuna prova positiva di prodotto gioco da tavolo'
        db.execute('INSERT INTO market_listings VALUES(?,?,?,?,?,?,?,?,?,?,NULL)',(i,title,'AUTO_FILTERED','AUTO_FILTERED','AUTO_FILTERED_NON_GAME',reason,0,'s'+str(i),'f'+str(i),str(i)))
        db.execute('INSERT INTO observations VALUES(?,?)',('s'+str(i),'original local classifier'))
    query='SELECT l.id FROM market_listings l LEFT JOIN games g ON g.id=l.game_id LEFT JOIN deals d ON d.signature=COALESCE(NULLIF(l.legacy_signature,\'\'),l.temp_fingerprint) WHERE '+constant('ELIGIBLE')+' ORDER BY l.id'
    assert db.execute(query).fetchall()==[(1,),(2,),(3,),(4,),(5,),(6,)],'phone evidence-gap rows are excluded before recovery'
    for change in ["UPDATE market_listings SET manual_review_required=1 WHERE id=1", "INSERT INTO deals VALUES('s1',1,'USER_CONFIRMED')", "INSERT INTO listing_overrides VALUES('s1',NULL)", "INSERT INTO games VALUES(10,'123'); UPDATE market_listings SET game_id=10 WHERE id=1", "UPDATE market_listings SET last_error='Prezzo chiaramente sopra il riferimento usato' WHERE id=1", "UPDATE market_listings SET lifecycle='USER_HIDDEN' WHERE id=1"]:
        db.execute('SAVEPOINT scenario')
        for statement in change.split(';'): db.execute(statement)
        assert (1,) not in db.execute(query).fetchall(),change
        db.execute('ROLLBACK TO scenario');db.execute('RELEASE scenario')
    print('PASS production SQLite recovery selection: six phone states and protected states')

    db.executescript("ALTER TABLE market_listings ADD COLUMN brand TEXT; ALTER TABLE market_listings ADD COLUMN observed_text TEXT; ALTER TABLE market_listings ADD COLUMN listing_photos_csv TEXT; ALTER TABLE market_listings ADD COLUMN image_url TEXT; ALTER TABLE market_listings ADD COLUMN category_normalized TEXT; ALTER TABLE observations ADD COLUMN id INTEGER; ALTER TABLE observations ADD COLUMN observed_at INTEGER;")
    db.execute("UPDATE market_listings SET lifecycle='ACTIVE',enrichment_state='NEEDS_REVIEW',match_state='BGG_MATCH_REVIEW' WHERE id=1")
    db.execute("UPDATE observations SET id=1,observed_at=100,verification_reason='AI category recovery:fixture-key: base game' WHERE signature='s1'")
    evidence=constant('AI_RECOVERY_EVIDENCE_SQL')
    assert db.execute(evidence,('1',)).fetchone()[5]=='s1'
    marker=constant('AI_RECOVERY_MARKER_SQL')
    assert db.execute(marker,('s1','AI category recovery:fixture-key:%')).fetchone(), 'BGG review consumed proof'
    db.execute("INSERT INTO observations VALUES('s1','new local source',2,200)")
    assert db.execute(marker,('s1','AI category recovery:fixture-key:%')).fetchone(), 'unchanged new observation erased proof'
    assert db.execute(marker,('s1','AI category recovery:changed-key:%')).fetchone() is None, 'stale fingerprint accepted'
    for change in ["UPDATE market_listings SET manual_review_required=1 WHERE id=1","INSERT INTO deals VALUES('s1',1,'USER_CONFIRMED')","INSERT INTO listing_overrides VALUES(NULL,'1')","UPDATE market_listings SET lifecycle='USER_HIDDEN' WHERE id=1"]:
        db.execute('SAVEPOINT scenario');db.execute(change)
        assert db.execute(evidence,('1',)).fetchone() is None,change
        db.execute('ROLLBACK TO scenario');db.execute('RELEASE scenario')
    print('PASS production SQLite product-evidence read: BGG review, repeated observations, fingerprint, manual protections')

def java_checks():
    with tempfile.TemporaryDirectory() as folder:
        root=Path(folder);package=root/'it/vintedaffari/app';package.mkdir(parents=True)
        for name in ['ListingClassifier.java','BoardGameIntakeGate.java','AiEnginePolicy.java']:
            shutil.copyfile(SRC/name,package/name)
        shutil.copyfile(ROOT/'regression/AiRecoveryLifecycleProbe.java',package/'AiRecoveryLifecycleProbe.java')
        shutil.copyfile(ROOT/'regression/AiEnginePolicyProbe.java',package/'AiEnginePolicyProbe.java')
        (package/'Fixtures.java').write_text('''package it.vintedaffari.app;
import java.util.*;
class VintedCard {String title,rawDescription,brand="";VintedCard(String title){this.title=title;rawDescription=title;}}
class GameAnalysis {String status="unmatched",candidateName="",productPublisher="";Double matchConfidence=0.0;Integer totalCents,benchmarkCents;}
class BggSearchClient {static class Game {String name;List<String> aliases=new ArrayList<>();int searchScore;}}
''')
        # Only JSON construction is substituted; all decisions come from production classes.
        jsondir=root/'org/json';jsondir.mkdir(parents=True)
        (jsondir/'JSONObject.java').write_text('''package org.json;
import java.util.*;
public class JSONObject {private final Map<String,Object> values=new HashMap<>();
public JSONObject put(String k,Object v){values.put(k,v);return this;}
public String optString(String k){return optString(k,"");}public String optString(String k,String d){Object v=values.get(k);return v==null?d:v.toString();}
public int optInt(String k,int d){Object v=values.get(k);return v instanceof Number?((Number)v).intValue():d;}
public boolean optBoolean(String k,boolean d){Object v=values.get(k);return v instanceof Boolean?(Boolean)v:d;}
public JSONArray optJSONArray(String k){Object v=values.get(k);return v instanceof JSONArray?(JSONArray)v:null;}}
''')
        (jsondir/'JSONArray.java').write_text('''package org.json;import java.util.*;
public class JSONArray {private final List<Object> values=new ArrayList<>();public JSONArray put(Object v){values.add(v);return this;}public int length(){return values.size();}}
''')
        java=shutil.which('java');javac=shutil.which('javac')
        if not java: raise RuntimeError('Java 17 required')
        command=[javac] if javac else [java,'com.sun.tools.javac.Main']
        subprocess.run(command+['-d',str(root/'classes')]+[str(p) for p in root.rglob('*.java')],check=True)
        subprocess.run([java,'-cp',str(root/'classes'),'it.vintedaffari.app.AiRecoveryLifecycleProbe'],check=True)
        subprocess.run([java,'-cp',str(root/'classes'),'it.vintedaffari.app.AiEnginePolicyProbe'],check=True)

if __name__=='__main__':
    failures=[]
    for check in [sql_checks,java_checks]:
        try: check()
        except (AssertionError,subprocess.CalledProcessError) as error: failures.append(str(error))
    if failures: raise SystemExit('\n'.join(failures))
