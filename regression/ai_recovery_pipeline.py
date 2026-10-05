#!/usr/bin/env python3
"""Execute production category evidence and BGG quarantine against real SQLite.

Reuse the existing host adapters; Android lifecycle and platform JSON serialization
are outside this test. No HTTP/provider, no phone data and no parallel product logic.
"""
import ast
import json
from pathlib import Path
import re
import subprocess
import tempfile
import sqlite3
import sys

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'app/src/main/java/it/vintedaffari/app'
tree=ast.parse((ROOT/'regression/browser_engine_boundary.py').read_text(encoding='utf-8'))
shared={}
for node in tree.body:
    if isinstance(node,ast.Assign):
        for target in node.targets:
            if isinstance(target,ast.Name) and target.id in ('stubs','server'):
                shared[target.id]=ast.literal_eval(node.value)
stubs=shared['stubs']
stubs['android/database/sqlite/SQLiteDatabase.java']=stubs['android/database/sqlite/SQLiteDatabase.java'].replace('new ProcessBuilder("python3",', 'new ProcessBuilder('+json.dumps(sys.executable)+',')
stubs['android/database/sqlite/SQLiteDatabase.java']=stubs['android/database/sqlite/SQLiteDatabase.java'].replace(
    'public void close(){process.destroy();}',
    'public void close(){out.close();try{process.waitFor();}catch(InterruptedException e){Thread.currentThread().interrupt();process.destroyForcibly();}if(process.isAlive()){process.destroyForcibly();try{process.waitFor();}catch(InterruptedException e){Thread.currentThread().interrupt();}}}',
)

def method(file,signature):
    text=(SRC/file).read_text(encoding='utf-8');start=text.index(signature);pos=text.index('{',start);depth=1;end=pos+1
    while depth:
        depth+=(text[end]=='{')-(text[end]=='}');end+=1
    return text[start:end]

imports='''package it.vintedaffari.app;
import android.database.Cursor;import android.database.sqlite.SQLiteDatabase;
import android.content.ContentValues;import android.text.TextUtils;
import java.util.*;import java.text.Normalizer;'''
market=imports+'''class MarketStore {
 final DealDatabase helper;static final int BGG_MATCH_ALGORITHM_VERSION=999;
 static final String COMPLETE="COMPLETE";
 MarketStore(DealDatabase h){helper=h;}void notifyQueueChanged(){}
'''+''.join(method('MarketStore.java',sig) for sig in [
    'public boolean autoQuarantineGame(', 'public boolean hasAiCategoryRecoveryEvidence(', 'public boolean isStaleAiCategoryAnalysis(',
    'private static Long listingIdForCard(', 'public static String fingerprint(',
    'private static int cents(', 'private static String normalize(',
    'private static Long scalarLong(', 'private static String scalarString(',
    'private static String safe('])+'}'
database=imports+'''class DealDatabase {
 final SQLiteDatabase db;DealDatabase(SQLiteDatabase d){db=d;}
 SQLiteDatabase getWritableDatabase(){return db;}SQLiteDatabase getReadableDatabase(){return db;}
 static String signature(VintedCard c){return c.capturedSignature;}
 DealRecord findBySignature(String sig){try(Cursor c=db.rawQuery("SELECT signature FROM deals WHERE signature=?",new String[]{sig})){
  return c.moveToFirst()?new DealRecord(sig):null;}}
 void exclude(DealRecord r,String reason){db.execSQL("UPDATE deals SET lifecycle='USER_HIDDEN' WHERE signature=?",new Object[]{r.signature});}
}
class DealRecord {final String signature;DealRecord(String s){signature=s;}}
'''
key=method('AiBetaListings.java','static String inputKey(')
stubs['org/json/JSONArray.java']=stubs['org/json/JSONArray.java'].replace('public int length()', 'public String toString(){return a.toString();}public int length()')
stubs.update({
 'it/vintedaffari/app/MarketStore.java':market,
 'it/vintedaffari/app/DealDatabase.java':database,
 'it/vintedaffari/app/AiBetaListings.java':'package it.vintedaffari.app;import org.json.JSONArray;class AiBetaListings{'+key+'}',
 'it/vintedaffari/app/BuildConfig.java':'package it.vintedaffari.app;class BuildConfig{static final boolean DEBUG=true;}',
 'it/vintedaffari/app/AiRecoveryPipelineProbe.java':imports+'''
public class AiRecoveryPipelineProbe {
 static int checks;
 static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 static String scalar(SQLiteDatabase db,String sql){try(Cursor c=db.rawQuery(sql,null)){return c.moveToFirst()?c.getString(0):"";}}
 static void remember(SQLiteDatabase db,long id){try(Cursor c=db.rawQuery("SELECT vinted_title,brand,observed_text,listing_photos_csv FROM market_listings WHERE id=?",new String[]{String.valueOf(id)})){
  c.moveToFirst();AiCategoryEvidence.remember(db,id,AiBetaListings.inputKey(c.getString(0),c.getString(1),c.getString(2),c.getString(3)),1234);}}
 public static void main(String[] args)throws Exception {
  SQLiteDatabase db=new SQLiteDatabase(args[0],args[1]);MarketStore market=new MarketStore(new DealDatabase(db));
  remember(db,1);check(AiCategoryEvidence.has(db,1),"fresh product evidence");
  for(String[] change:new String[][]{{"vinted_title","Different game"},{"brand","Different publisher"},{"observed_text","Different full description"},{"listing_photos_csv","Different photo"}}){
   String prior=scalar(db,"SELECT "+change[0]+" FROM market_listings WHERE id=1");
   db.execSQL("UPDATE market_listings SET "+change[0]+"=? WHERE id=1",new Object[]{change[1]});
   check(!AiCategoryEvidence.has(db,1),"changed "+change[0]+" invalidates proof");
   db.execSQL("UPDATE market_listings SET "+change[0]+"=? WHERE id=1",new Object[]{prior});
  }
  db.execSQL("UPDATE observations SET verification_reason='local classifier overwrite'",null);
  check(AiCategoryEvidence.has(db,1),"analysis reason cannot erase proof");
  db.execSQL("UPDATE market_listings SET category_normalized='books' WHERE id=1",null);
  check(!AiCategoryEvidence.has(db,1),"explicit incompatible category wins");
  db.execSQL("UPDATE market_listings SET category_normalized=NULL WHERE id=1",null);
  db.execSQL("INSERT INTO listing_overrides(signature,item_id) VALUES(NULL,'42')",null);
  check(!AiCategoryEvidence.has(db,1),"exact-item user override wins");
  db.execSQL("DELETE FROM listing_overrides WHERE item_id='42'",null);
  db.execSQL("UPDATE deals SET confirmed=1 WHERE signature='sig1'",null);
  check(!AiCategoryEvidence.has(db,1),"human confirmation wins");
  db.execSQL("UPDATE deals SET confirmed=0 WHERE signature='sig1'",null);
  String longSource="Gioco da tavolo quiz "+"dettagli ".repeat(100);
  db.execSQL("UPDATE market_listings SET observed_text=? WHERE id=1",new Object[]{longSource});remember(db,1);
  VintedCard fresh=new VintedCard("Gioco da tavolo quiz","Giunti","",10,null,null,new android.graphics.Rect(0,0,1,1),longSource,"","sig1");
  check(market.hasAiCategoryRecoveryEvidence(fresh),"full source longer than600 resolves by browser identity");
  VintedCard stale=new VintedCard("Gioco da tavolo quiz","Giunti","",10,null,null,new android.graphics.Rect(0,0,1,1),"old source");
  db.execSQL("UPDATE market_listings SET temp_fingerprint=? WHERE id=1",new Object[]{MarketStore.fingerprint(stale)});
  check(market.isStaleAiCategoryAnalysis(stale),"stale legacy callback rejected for newer proof");
  check(!market.hasAiCategoryRecoveryEvidence(stale),"new source proof cannot authorize old source");
  db.execSQL("UPDATE market_listings SET temp_fingerprint='fp1' WHERE id=1",null);
  check(!market.autoQuarantineGame(10,"Nessuna evidenza sufficiente che l'articolo sia un gioco BGG"),"BGG miss becomes review, not quarantine");
  check("ACTIVE".equals(scalar(db,"SELECT lifecycle FROM market_listings WHERE id=1")),"AI-positive remains active");
  check("BGG_MATCH_REVIEW".equals(scalar(db,"SELECT match_state FROM market_listings WHERE id=1")),"identity uncertainty reaches BGG review");
  check("NEEDS_REVIEW".equals(scalar(db,"SELECT enrichment_state FROM market_listings WHERE id=1")),"local analysis leaves pending batch");
  check("AUTO_FILTERED".equals(scalar(db,"SELECT lifecycle FROM market_listings WHERE id=2")),"unproven sibling remains quarantined");
  check("".equals(scalar(db,"SELECT COALESCE(bgg_id,'') FROM games WHERE id=10")),"AI cannot choose identity");
  check("1000".equals(scalar(db,"SELECT current_price_cents FROM market_listings WHERE id=1")),"price unchanged");
  check("MATCH_UNCERTAIN".equals(scalar(db,"SELECT verification_state FROM deals WHERE signature='sig1'")),"trust unchanged");
  check("ACTIVE".equals(scalar(db,"SELECT lifecycle FROM deals WHERE signature='sig1'")),"AI-positive legacy row not excluded");
  check("USER_HIDDEN".equals(scalar(db,"SELECT lifecycle FROM deals WHERE signature='sig2'")),"negative sibling legacy row excluded");
  check("ACTIVE".equals(scalar(db,"SELECT lifecycle FROM market_listings WHERE id=3")),"manual sibling remains active");
  check("MANUAL_STATE".equals(scalar(db,"SELECT match_state FROM market_listings WHERE id=3")),"manual sibling decision unchanged");
  check("ACTIVE".equals(scalar(db,"SELECT lifecycle FROM deals WHERE signature='sig3'")),"manual sibling legacy row unchanged");
  check("1234".equals(scalar(db,"SELECT observed_at FROM observations LIMIT 1")),"observation time unchanged");
  check(longSource.equals(scalar(db,"SELECT observed_text FROM market_listings WHERE id=1")),"full source remains unchanged through BGG review");
  db.close();System.out.println("PASS native category evidence / BGG state machine: "+checks+" checks");
 }
}''',
})

with tempfile.TemporaryDirectory() as tmp:
    base=Path(tmp);server=base/'sqlite_server.py';server.write_text(shared['server'],encoding='utf-8');dbfile=base/'fixture.sqlite'
    db=sqlite3.connect(dbfile)
    # Production table declarations plus columns introduced by existing migrations.
    sources=[(SRC/'MarketStore.java').read_text(encoding='utf-8'),(SRC/'DealDatabase.java').read_text(encoding='utf-8')]
    for table in ('games','market_listings','queue_controls','processing_jobs','deals','observations','listing_overrides'):
        for source in sources:
            m=re.search(r'"CREATE TABLE (?:IF NOT EXISTS )?'+table+r'\(.*?"\);',source,re.S)
            if m:
                db.execute(''.join(json.loads(s) for s in re.findall(r'"(?:[^"\\]|\\.)*"',m.group(0))))
                break
        else:
            raise AssertionError('missing production schema '+table)
    for table,name,decl in [('games','match_algorithm_version','INTEGER DEFAULT 0'),('market_listings','category_normalized','TEXT')]:
        if name not in [c[1] for c in db.execute('PRAGMA table_info('+table+')')]:
            db.execute('ALTER TABLE '+table+' ADD COLUMN '+name+' '+decl)
    db.execute("INSERT INTO games(id,provisional_key,canonical_name,normalized_name,match_state,first_seen,last_seen) VALUES(10,'quiz','Quiz','quiz','BGG_MATCH_REQUIRED',1234,1234)")
    for i in (1,2,3):
        db.execute("INSERT INTO market_listings(id,temp_fingerprint,legacy_signature,vinted_item_id,game_id,vinted_title,brand,observed_text,listing_photos_csv,current_price_cents,lifecycle,enrichment_state,match_state,first_seen,last_seen) VALUES(?,?,?,?,10,'Gioco da tavolo quiz','Giunti','Gioco da tavolo quiz','photos',1000,'ACTIVE','LOCAL_ONLY','BGG_MATCH_REQUIRED',1234,1234)",(i,'fp'+str(i),'sig'+str(i),str(41+i)))
        db.execute("INSERT INTO deals(signature,vinted_title,item_price_cents,first_seen,last_seen,lifecycle,verification_state,confirmed) VALUES(?,'Gioco da tavolo quiz',1000,1234,1234,'ACTIVE','MATCH_UNCERTAIN',0)",('sig'+str(i),))
        db.execute("INSERT INTO observations(signature,observed_at,vinted_title,item_price_cents,verification_reason) VALUES(?,1234,'Gioco da tavolo quiz',1000,'old marker')",('sig'+str(i),))
    db.execute("UPDATE market_listings SET match_state='MANUAL_STATE' WHERE id=3")
    db.execute("INSERT INTO listing_overrides(signature,item_id) VALUES('sig3',NULL)")
    db.commit();db.close()
    for name,content in stubs.items():
        p=base/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content,encoding='utf-8')
    files=[str(p) for p in base.rglob('*.java')]+[str(SRC/(name+'.java')) for name in ['AiCategoryEvidence','AiBetaProtocol','VintedCard','ListingClassifier','BoardGameIntakeGate']]
    subprocess.run(['java','--module','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-d',str(base),*files],check=True)
    subprocess.run(['java','-cp',str(base),'it.vintedaffari.app.AiRecoveryPipelineProbe',str(server),str(dbfile)],check=True)
    sys.path.insert(0,str(ROOT/'tools'))
    from ai_recovery_runtime_audit import report
    with sqlite3.connect(dbfile) as db:
        result=report(db,[1])
        assert result['cohort_pass'] and result['ai_positive_filtered']==0
        assert not report(db,[1,2])['cohort_pass'], 'incomplete AI cohort cannot pass'
        db.execute("UPDATE market_listings SET lifecycle='AUTO_FILTERED' WHERE id=1")
        assert not report(db,[1])['cohort_pass'], 're-filtered positive cannot pass'
    print('PASS runtime audit rejects incomplete and re-filtered cohorts')
