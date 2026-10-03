"""Run production identity matching and section navigation against deterministic UI boundaries."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1]
ui=(root/'app/src/main/java/it/vintedaffari/app/MainActivity.java').read_text()
def method(signature):
 start=ui.index(signature);brace=ui.index('{',start);depth=0
 for i in range(brace,len(ui)):
  if ui[i]=='{':depth+=1
  elif ui[i]=='}':
   depth-=1
   if depth==0:return ui[start:i+1]
 raise AssertionError(signature)
header=method('private void addMarketHeader(')
assert 'addMarketTab(tabs,"Annunci"' not in header
assert 'search.addTextChangedListener(new TextWatcher()' in method('private void renderCatalog(')
assert 'queueCatalogSearch(180)' in method('private void renderCatalog(')
assert 'render();' not in method('private void queueCatalogSearch(')
assert 'catalogMatchesGame(d)' in method('private void renderCatalogResults(')
assert 'epoch!=catalogGameEpoch' in method('private void addCatalogGameResults(')
assert 'requested.equals(query)' in method('private void addCatalogGameResults(')
assert '"verified",false,null,null,"alpha"' in method('private void addCatalogGameResults(')
assert 'return catalogProductCard(d,g.id)' in method('private View catalogGameCard(')
assert 'installPullToGame(sc,pullHint,game.id,dialog)' in ui
assert 'Dopo l’esplorazione' not in method('private void renderBundles(')
assert 'Map<String,DealRecord> explored' not in method('private void renderBundles(')
assert 'BundleExploration.markExplored(this,d.sellerId)' in method('private void openBundleProspect(')
for signature in ['private void saveUiState(', 'private void restoreUiState(', 'private void persistTransientUiSession(', 'private void restoreTransientUiSession(']:
 assert 'bundleSection' in method(signature) and 'bundleScroll_' in method(signature)
# Execute the production identity SELECT with more matches than the preview can hold.
import re,sqlite3
market=(root/'app/src/main/java/it/vintedaffari/app/MarketStore.java').read_text()
helper=market[market.index('public Set<String> catalogGameMatches('):market.index('    public int countVisibleGamesAdvanced(')]
parts=re.search(r'String sql="(.*?)"\+where\+"(.*?)";',helper).groups()
conn=sqlite3.connect(':memory:')
conn.executescript("CREATE TABLE games(id INTEGER,bgg_id TEXT,database_visible INTEGER,match_state TEXT,normalized_name TEXT,categories TEXT,mechanics TEXT,designers TEXT,publishers TEXT,families TEXT);CREATE TABLE game_aliases(game_id INTEGER,normalized_alias TEXT);")
for i in range(40):
 conn.execute("INSERT INTO games VALUES(?,?,1,'MATCHED',?,'','','','','')",(i,str(i),'unrelated title '+str(i)))
 conn.execute("INSERT INTO game_aliases VALUES(?,?)",(i,'catan edition '+str(i)))
conn.execute("UPDATE games SET database_visible=0 WHERE id=38")
conn.execute("UPDATE games SET match_state='BGG_MATCH_REQUIRED' WHERE id=37")
eligible=[str(i) for i in range(25,40)]
where="g.database_visible=1 AND g.match_state='MATCHED' AND g.bgg_id IN ("+','.join('?' for _ in eligible)+')'
rows={r[0] for r in conn.execute(parts[0]+where+parts[1],eligible+['%catan%']*7)}
assert rows==set(eligible)-{'37','38'}
assert '39' in rows and '0' not in rows
assert 'LIMIT' not in helper and 'start+=800' in helper
print('PASS production SQL alias beyond preview, eligibility, canonical match state, chunked binds')
exploration=(root/'app/src/main/java/it/vintedaffari/app/BundleExploration.java').read_text()
mark=exploration[exploration.index('public static void markExplored('):exploration.index('public static long exploredAt(')]
assert 'EXPLORED_PREFIX+sellerId' in mark and 'expires_at' not in mark and 'source_signature' not in mark
assert 'BundleExploration.begin(this,d)' not in method('private void openBundleProspect(')
code=r'''
import java.util.*;
class ViewTreeObserver {interface OnGlobalLayoutListener {void onGlobalLayout();}ArrayList<OnGlobalLayoutListener> pending=new ArrayList<>();boolean isAlive(){return true;}void addOnGlobalLayoutListener(OnGlobalLayoutListener r){pending.add(r);}void removeOnGlobalLayoutListener(OnGlobalLayoutListener r){pending.remove(r);}void flush(){for(OnGlobalLayoutListener r:new ArrayList<>(pending))r.onGlobalLayout();}}
class DealRecord {String bggId;DealRecord(String id){bggId=id;}}
class GameRecord {String bggId;GameRecord(String id){bggId=id;}}
class TextUtils {static boolean isEmpty(String s){return s==null||s.isEmpty();}}
public class CatalogBundleRegression {
 String query="azul",catalogGameQuery="azul",bundleSection="bundle",tab="bundles";
 List<GameRecord> catalogGames=new ArrayList<>();Map<String,Integer> bundleSectionScroll=new HashMap<>();
 Set<String> catalogMatchedIds=new HashSet<>();long bundleSectionEpoch=0;boolean bundleSectionRestorePending;FakeScroll scroll=new FakeScroll();String persistedSection;int persistedY;
 class FakeScroll {int y;ViewTreeObserver observer=new ViewTreeObserver();int getScrollY(){return y;}void scrollTo(int x,int v){y=v;}ViewTreeObserver getViewTreeObserver(){return observer;}void requestLayout(){}void flush(){observer.flush();}}
 void render(){scroll.y=0;}
 void persistTransientUiSession(){if(!bundleSectionRestorePending)bundleSectionScroll.put(bundleSection,scroll.y);persistedSection=bundleSection;persistedY=bundleSectionScroll.getOrDefault(bundleSection,0);}
 static void eq(Object a,Object b){if(!Objects.equals(a,b))throw new AssertionError(a+" != "+b);}
 public static void main(String[] args){
  CatalogBundleRegression n=new CatalogBundleRegression();n.catalogGames.add(new GameRecord("230802"));n.catalogMatchedIds.add("230802");n.catalogMatchedIds.add("ranked-after-preview");eq(true,n.catalogMatchesGame(new DealRecord("ranked-after-preview")));
  eq(true,n.catalogMatchesGame(new DealRecord("230802")));eq(false,n.catalogMatchesGame(new DealRecord("1")));eq(false,n.catalogMatchesGame(new DealRecord(null)));eq(false,n.catalogMatchesGame(null));
  n.query="catan";eq(false,n.catalogMatchesGame(new DealRecord("230802")));n.query="azul";n.catalogGames.clear();n.catalogMatchedIds.clear();eq(false,n.catalogMatchesGame(new DealRecord("230802")));
  n.scroll.y=420;n.bundleSectionScroll.put("explore",180);n.switchBundleSection("explore");eq(420,n.bundleSectionScroll.get("bundle"));eq(180,n.persistedY);eq("explore",n.persistedSection);n.scroll.flush();eq(180,n.scroll.y);
  n.scroll.y=260;n.switchBundleSection("bundle");eq(260,n.bundleSectionScroll.get("explore"));n.scroll.flush();eq(420,n.scroll.y);
  n.switchBundleSection("explore");n.tab="catalog";n.scroll.y=999;n.scroll.flush();eq(999,n.scroll.y);
  n.tab="bundles";n.switchBundleSection("bundle");n.switchBundleSection("explore");n.scroll.flush();eq(999,n.scroll.y);
  System.out.println("PASS canonical identity, stale query, independent scroll, persisted position, stale route/section callbacks");
 }
 __METHODS__
}
'''.replace('__METHODS__',method('private boolean catalogMatchesGame(')+'\n'+method('private void switchBundleSection(').replace('android.view.ViewTreeObserver.OnGlobalLayoutListener','ViewTreeObserver.OnGlobalLayoutListener'))
with tempfile.TemporaryDirectory() as temp:
 p=Path(temp)/'CatalogBundleRegression.java';p.write_text(code)
 subprocess.run(['javac','-d',temp,str(p)],check=True)
 subprocess.run(['java','-cp',temp,'CatalogBundleRegression'],check=True)

# Execute the real debounce/request/callback methods. Workers and the UI queue are
# deliberately separate so an obsolete response can arrive after a newer input.
live=r'''
import java.util.*;import java.util.concurrent.*;import java.util.function.*;
class View {Object parent=new Object();Object getParent(){return parent;} }
class LinearLayout extends View {int count;void removeAllViews(){count=0;}void addView(View v){count++;}}
class TextView extends View {Consumer<View> click;void setOnClickListener(Consumer<View> c){click=c;}}
class DealRecord {String bggId="1";}
class GameRecord {}
class TextUtils {static boolean isEmpty(String s){return s==null||s.isEmpty();}}
public class LiveCatalogRegression {
 String query="",tab="catalog",catalogGameQuery="",catalogGameError;long catalogGameEpoch;int catalogRestoreY=900;
 LinearLayout catalogContentHost=new LinearLayout(),catalogResultsHost;List<DealRecord> catalogResultSnapshot=new ArrayList<>();List<GameRecord> catalogGames=new ArrayList<>();Set<String> catalogMatchedIds=new HashSet<>();
 Runnable catalogLiveSearch;Future<?> catalogSearchFuture;Handler uiUpdates=new Handler();Executor uiDataIo=new Executor();ArrayDeque<Runnable> callbacks=new ArrayDeque<>();DB db=new DB();Market marketStore=new Market();int renders;String rendered="";
 static class Handler {ArrayList<Runnable> tasks=new ArrayList<>();void removeCallbacks(Runnable r){tasks.remove(r);}void postDelayed(Runnable r,long delay){tasks.add(r);}void flush(){for(Runnable r:new ArrayList<>(tasks)){tasks.remove(r);r.run();}}}
 static class Executor {ArrayDeque<FutureTask<Void>> tasks=new ArrayDeque<>();Future<?> submit(Runnable r){FutureTask<Void> task=new FutureTask<>(r,null);tasks.add(task);return task;}void flush(){while(!tasks.isEmpty())tasks.remove().run();}}
 static class DB {int reads;boolean fail;List<DealRecord> getDeals(String scope,int limit){reads++;if(fail)throw new IllegalStateException();return new ArrayList<>(Arrays.asList(new DealRecord()));}int countDeals(Object ignored){return 1;}}
 static class Market {Set<String> catalogGameMatches(String q,Set<String> ids){return ids;}List<GameRecord> searchGamesAdvanced(String q,int limit,String scope,boolean active,Object rating,Object price,String order){return new ArrayList<>(Arrays.asList(new GameRecord()));}}
 boolean isDestroyed(){return false;}void runOnUiThread(Runnable r){callbacks.add(r);}void flushUi(){while(!callbacks.isEmpty())callbacks.remove().run();}void cancelImageRequests(View v){}View loadingMoreView(String s){return new View();}TextView secondaryTextAction(String s){return new TextView();}
 void renderCatalogResults(LinearLayout h,List<DealRecord> deals,int total){renders++;rendered=query;}
 void restoreCatalogResultsPosition(LinearLayout h,long epoch){}
 static void eq(Object a,Object b){if(!Objects.equals(a,b))throw new AssertionError(a+" != "+b);}
 public static void main(String[] args){
  LiveCatalogRegression n=new LiveCatalogRegression();LinearLayout editorSibling=n.catalogContentHost;n.query="A";n.queueCatalogSearch(180);n.query="AB";n.queueCatalogSearch(180);eq(1,n.uiUpdates.tasks.size());eq(0,n.catalogRestoreY);n.uiUpdates.flush();n.uiDataIo.flush();n.flushUi();eq(1,n.db.reads);eq("AB",n.rendered);eq(editorSibling,n.catalogContentHost);
  n.query="A";n.queueCatalogSearch(180);n.uiUpdates.flush();n.uiDataIo.flush();n.query="Az";n.queueCatalogSearch(180);n.flushUi();eq(1,n.renders);n.uiUpdates.flush();n.uiDataIo.flush();n.flushUi();eq("Az",n.rendered);eq(2,n.renders);
  n.query="";n.queueCatalogSearch(180);n.uiUpdates.flush();n.uiDataIo.flush();n.flushUi();eq("",n.rendered);eq(0,n.catalogGames.size());eq(0,n.catalogMatchedIds.size());
  n.query="A";n.queueCatalogSearch(180);n.uiUpdates.flush();n.tab="companion";n.uiDataIo.flush();n.flushUi();eq(3,n.renders);
  n.tab="catalog";n.queueCatalogSearch(180);n.uiUpdates.flush();n.catalogContentHost=new LinearLayout();n.uiDataIo.flush();n.flushUi();eq(3,n.renders);
  n.queueCatalogSearch(180);n.uiUpdates.flush();n.catalogContentHost.parent=null;n.uiDataIo.flush();n.flushUi();eq(3,n.renders);
  n.catalogContentHost=new LinearLayout();n.db.fail=true;n.queueCatalogSearch(180);n.uiUpdates.flush();n.uiDataIo.flush();n.flushUi();eq(3,n.renders);eq("Ricerca non disponibile. Riprova.",n.catalogGameError);eq(1,n.catalogContentHost.count);
  System.out.println("PASS live A/AB, debounce, stale callback, clear, route/host guard, error without fake zero, editor host retained");
 }
 __METHODS__
}
'''.replace('__METHODS__',method('private void queueCatalogSearch(')+'\n'+method('private void addCatalogGameResults('))
with tempfile.TemporaryDirectory() as temp:
 p=Path(temp)/'LiveCatalogRegression.java';p.write_text(live)
 subprocess.run(['javac','-d',temp,str(p)],check=True)
 subprocess.run(['java','-cp',temp,'LiveCatalogRegression'],check=True)
# Hydration must finish on the worker before any UI rendering of page or overlay.
for signature in ['private void renderDatabaseDetail(', 'private void openGameDetailOverlay(']:
 detail=method(signature)
 assert detail.index('uiDataIo.execute') < detail.index('loadGameDetailData') < detail.index('runOnUiThread')
 assert 'snapshot);' in detail
shared=method('private View catalogProductCard(DealRecord d,long gameId)')
assert 'if(gameId<=0){LinearLayout priceRow' in shared
assert 'addGameFavorite(artwork' in shared
assert 'catalogGameCard(catalogGames.get(index))' in method('private void renderCatalogResults(')
assert 'Il gioco che cerchi' not in ui and 'Panoramica, mercato e come si gioca' not in ui
print('PASS shared game/listing card grammar, listing-only price, worker detail hydration, no separate game banners')

assert 'return catalogGameCard(g)' in method('private View databaseGameCard(')
assert 'openDatabaseGame(gameId,gameSource)' in shared
print('PASS complete game results reuse the same price-free cards and source-aware Back')
