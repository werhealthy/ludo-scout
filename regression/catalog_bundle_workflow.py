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
assert 'addCatalogGameResults();' in method('private void renderCatalog(')
assert 'catalogMatchesGame(d)' in method('private void renderCatalog(')
assert 'epoch!=catalogGameEpoch' in method('private void addCatalogGameResults(')
assert 'requested.equals(query)' in method('private void addCatalogGameResults(')
assert '"verified",false,null,null,"alpha"' in method('private void addCatalogGameResults(')
assert 'openDatabaseGame(g.id,"catalog")' in method('private void addCatalogGameResults(')
assert 'installPullToGame(sc,pullHint,game.id,dialog)' in ui
assert 'Bundle trovato' in method('private void renderBundles(')
assert 'Nessun bundle confermato' in method('private void renderBundles(')
assert 'BundleExploration.begin(this,d)' in method('private void openBundleProspect(')
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
