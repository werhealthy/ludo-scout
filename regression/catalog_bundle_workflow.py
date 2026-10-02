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
code=r'''
import java.util.*;
class DealRecord {String bggId;DealRecord(String id){bggId=id;}}
class GameRecord {String bggId;GameRecord(String id){bggId=id;}}
class TextUtils {static boolean isEmpty(String s){return s==null||s.isEmpty();}}
public class CatalogBundleRegression {
 String query="azul",catalogGameQuery="azul",bundleSection="bundle",tab="bundles";
 List<GameRecord> catalogGames=new ArrayList<>();Map<String,Integer> bundleSectionScroll=new HashMap<>();
 FakeScroll scroll=new FakeScroll();String persistedSection;int persistedY;
 class FakeScroll {int y;ArrayDeque<Runnable> pending=new ArrayDeque<>();int getScrollY(){return y;}void scrollTo(int x,int v){y=v;}void post(Runnable r){pending.add(r);}void flush(){while(!pending.isEmpty())pending.remove().run();}}
 void render(){scroll.y=0;}
 void persistTransientUiSession(){bundleSectionScroll.put(bundleSection,scroll.y);persistedSection=bundleSection;persistedY=scroll.y;}
 static void eq(Object a,Object b){if(!Objects.equals(a,b))throw new AssertionError(a+" != "+b);}
 public static void main(String[] args){
  CatalogBundleRegression n=new CatalogBundleRegression();n.catalogGames.add(new GameRecord("230802"));
  eq(true,n.catalogMatchesGame(new DealRecord("230802")));eq(false,n.catalogMatchesGame(new DealRecord("1")));eq(false,n.catalogMatchesGame(new DealRecord(null)));eq(false,n.catalogMatchesGame(null));
  n.query="catan";eq(false,n.catalogMatchesGame(new DealRecord("230802")));n.query="azul";n.catalogGames.clear();eq(false,n.catalogMatchesGame(new DealRecord("230802")));
  n.scroll.y=420;n.bundleSectionScroll.put("explore",180);n.switchBundleSection("explore");eq(420,n.bundleSectionScroll.get("bundle"));eq(180,n.persistedY);eq("explore",n.persistedSection);n.scroll.flush();eq(180,n.scroll.y);
  n.scroll.y=260;n.switchBundleSection("bundle");eq(260,n.bundleSectionScroll.get("explore"));n.scroll.flush();eq(420,n.scroll.y);
  n.switchBundleSection("explore");n.tab="catalog";n.scroll.y=999;n.scroll.flush();eq(999,n.scroll.y);
  n.tab="bundles";n.switchBundleSection("bundle");n.switchBundleSection("explore");n.scroll.flush();eq(999,n.scroll.y);
  System.out.println("PASS canonical identity, stale query, independent scroll, persisted position, stale route/section callbacks");
 }
 __METHODS__
}
'''.replace('__METHODS__',method('private boolean catalogMatchesGame(')+'\n'+method('private void switchBundleSection('))
with tempfile.TemporaryDirectory() as temp:
 p=Path(temp)/'CatalogBundleRegression.java';p.write_text(code)
 subprocess.run(['javac','-d',temp,str(p)],check=True)
 subprocess.run(['java','-cp',temp,'CatalogBundleRegression'],check=True)
