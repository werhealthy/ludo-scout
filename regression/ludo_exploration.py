#!/usr/bin/env python3
"""Real journey SQL and pure URL/delta policy; DB boundaries only are fixtures."""
from pathlib import Path
import sqlite3,re,subprocess,tempfile,shutil
root=Path(__file__).resolve().parents[1]
source=root/'app/src/main/java/it/vintedaffari/app/EngineJourneySql.java'
assert source.exists(), 'daily journey SQL is missing: circles still show occupancy'
sql=re.search(r'return """\n(.*?)\n\s*""";',source.read_text(),re.S).group(1)
db=sqlite3.connect(':memory:')
db.executescript('''
CREATE TABLE observations(id INTEGER PRIMARY KEY,signature TEXT,observed_at INTEGER,vinted_title TEXT);
CREATE TABLE games(id INTEGER PRIMARY KEY,bgg_id TEXT,match_state TEXT,rating REAL,database_visible INTEGER);
CREATE TABLE market_listings(id INTEGER PRIMARY KEY,game_id INTEGER,legacy_signature TEXT,temp_fingerprint TEXT,lifecycle TEXT,enrichment_state TEXT,manual_review_required INTEGER,match_state TEXT,vinted_item_id TEXT,vinted_url TEXT);
CREATE TABLE deals(signature TEXT,verification_state TEXT,lifecycle TEXT,listing_type TEXT,tier TEXT,rating REAL,bgg_id TEXT,vinted_item_id TEXT,vinted_url TEXT,benchmark_cents INTEGER,total_cents INTEGER,discount REAL);
CREATE TABLE processing_jobs(listing_id INTEGER,game_id INTEGER,job_type TEXT,source TEXT,state TEXT);
''')
def add(sig,lid,at=100,ready=True):
 db.execute('INSERT INTO observations(signature,observed_at,vinted_title) VALUES(?,?,?)',(sig,at,sig))
 db.execute("INSERT OR IGNORE INTO games VALUES(1,'42','MATCHED',7,1)")
 db.execute("INSERT OR IGNORE INTO market_listings VALUES(?,1,?,?,'ACTIVE','CORE_COMPLETE',0,'MATCHED',?,'https://www.vinted.it/items/1')",(lid,sig,sig,str(lid)))
 db.execute("INSERT INTO deals VALUES(?,'OK','ACTIVE','GAME','good',7,'42',?,'url',2000,1000,?)",(sig,str(lid),50 if ready else None))
def rows():return list(db.execute(sql,(0,200)))
add('a',1); add('b',2,ready=False)
db.execute("INSERT INTO observations(signature,observed_at,vinted_title) VALUES('a',150,'again')")
db.execute("INSERT INTO observations(signature,observed_at,vinted_title) VALUES('raw',170,'raw')")
assert len(rows())==3, rows()
assert [sum(r[3+i] for r in rows()) for i in range(5)]==[3,2,2,2,1], rows()
db.execute("UPDATE deals SET discount=0 WHERE signature='a'");assert sum(r[7] for r in rows())==1
db.execute("UPDATE deals SET discount=-10 WHERE signature='a'");assert sum(r[7] for r in rows())==1
db.execute("UPDATE deals SET benchmark_cents=NULL WHERE signature='a'");assert sum(r[7] for r in rows())==0
db.execute("UPDATE market_listings SET manual_review_required=1 WHERE id=1");assert sum(r[6] for r in rows())==1
db.execute("UPDATE games SET match_state='BGG_MATCH_REVIEW'");assert sum(r[4] for r in rows())==0
assert len(list(db.execute(sql,(200,300))))==0
assert len(list(db.execute(sql,(150,200))))==2
print('PASS daily SQL: distinct listings, instant gates, review, pricing, boundaries')
plan=root/'app/src/main/java/it/vintedaffari/app/ExplorationPlan.java'
assert plan.exists(),'exploration policy missing'
if shutil.which('javac'):
 with tempfile.TemporaryDirectory() as temp:
  runner=Path(temp)/'JourneyCheck.java'
  runner.write_text('''package it.vintedaffari.app; public class JourneyCheck {
  static void yes(boolean x){if(!x)throw new AssertionError();}
  public static void main(String[] args){
   String u=ExplorationPlan.url("Azul & co","newest_first",100,500);
   yes(u.contains("search_text=Azul%20%26%20co")&&u.contains("price_from=1")&&u.contains("price_to=5")&&u.contains("page=1"));
   yes(ExplorationPlan.url("","bad",0,0).isEmpty());
   yes(ExplorationPlan.url("","relevance",500,100).isEmpty());
   yes(ExplorationPlan.delta(7,5).equals("↑ +2"));yes(ExplorationPlan.delta(2,5).equals("↓ −3"));yes(ExplorationPlan.delta(5,5).equals("= 0"));
   yes(ExplorationPlan.baselineApplies(10,10,100,50,150,200));
   yes(!ExplorationPlan.baselineApplies(10,10,100,50,90,200));
   yes(!ExplorationPlan.baselineApplies(10,10,100,250,260,200));
   yes(!ExplorationPlan.baselineApplies(10,9,100,50,150,200));
   long[] day=ExplorationPlan.day(1790982000000L);yes(day[1]-day[0]==86400000L);
   long[] dst=ExplorationPlan.day(1792879200000L);yes(dst[1]-dst[0]==90000000L);
   long[] spring=ExplorationPlan.day(1774738800000L);yes(spring[1]-spring[0]==82800000L);
  }}''')
  subprocess.run(['javac','-d',temp,str(plan),str(runner)],check=True)
  subprocess.run(['java','-cp',temp,'it.vintedaffari.app.JourneyCheck'],check=True)
  print('PASS JVM exploration URL, signed delta, Rome day')
else: print('SKIP JVM locally; CI Java17 required')
main=(root/'app/src/main/java/it/vintedaffari/app/MainActivity.java').read_text()
assert 'renderLudoJourney()' in main,'Ludo still separated from motor'
browser=(root/'app/src/main/java/it/vintedaffari/app/VintedBrowserActivity.java').read_text()
assert 'Nuova esplorazione' in browser and 'Sorprendimi' in browser
assert 'LudoIcons.PAUSE' in browser and 'LudoIcons.CAMERA' in browser
assert 'enterEngineDetail("phase")' in main,'phase click from Ludo does not switch destination tab'
assert 'enterEngineDetail("review")' in main,'manual help from Ludo does not switch destination tab'
assert 'i.completed[1]==0' not in main.split('private void showJourneyItems',1)[1].split('private void appendJourneyRows',1)[0],'post-BGG daily holds are hidden'
print('PASS Ludo/browser integration guards')

# Execute the real MainActivity route method with only Android/UI boundaries replaced.
def method(source,name):
 start=source.index('    private void '+name+'(')
 brace=source.index('{',start);depth=1;end=brace+1
 while depth:
  depth+=(source[end]=='{')-(source[end]=='}');end+=1
 return source[start:end]
if shutil.which('javac'):
 with tempfile.TemporaryDirectory() as temp:
  runner=Path(temp)/'JourneyRouteCheck.java'
  runner.write_text('''package it.vintedaffari.app; import java.util.*;
  public class JourneyRouteCheck {
   String tab="companion",engineSection="overview";boolean engineDetailReturnToLudo;
   int recorded,persisted,nav;Map<String,Integer> tabScrollPositions=new HashMap<>();
   static class Scroll {int getScrollY(){return 123;}} Scroll scroll=new Scroll();
   void recordLudoRoomPosition(){recorded++;}void persistTransientUiSession(){persisted++;}void renderNav(){nav++;}
   '''+method(main,'enterEngineDetail')+'''
   public static void main(String[] args){JourneyRouteCheck r=new JourneyRouteCheck();r.enterEngineDetail("phase");
    if(!r.tab.equals("activity")||!r.engineSection.equals("phase")||!r.engineDetailReturnToLudo||r.tabScrollPositions.get("companion")!=123||r.persisted!=1||r.nav!=1)throw new AssertionError("Ludo phase destination");
    r.scroll=new Scroll(){int getScrollY(){return 999;}};r.enterEngineDetail("review");
    if(!r.engineSection.equals("review")||r.tabScrollPositions.get("companion")!=123||!r.engineDetailReturnToLudo)throw new AssertionError("nested detail preserves return");
   }}''')
  subprocess.run(['javac','-d',temp,str(runner)],check=True)
  subprocess.run(['java','-cp',temp,'it.vintedaffari.app.JourneyRouteCheck'],check=True)
  print('PASS real Activity detail route with UI boundaries replaced')
