"""Protect the three browser states and existing persistence boundary."""
from pathlib import Path
import re, shutil, subprocess, tempfile
root=Path(__file__).resolve().parents[1]
source=(root/'app/src/main/java/it/vintedaffari/app/VintedBrowserActivity.java').read_text()
assert 'enum BrowserUiState' in source, 'missing explicit expanded/collapsed/manual states'
assert 'Nuova esplorazione' not in source and 'Ricerca e filtri' not in source
assert 'Leggi questo annuncio' not in source and 'Torna a Esplora' not in source
assert 'LudoIcons.HEART' in source and 'Dalle mie cacce' in source
assert 'LudoCaptureControl.canAdvance(' in source, 'capture gate removed'
assert 'persistedPageIds.size()' in source, 'counter must use persisted IDs'
assert 'currentPriceLimit' in source, 'price threshold lost'
method=re.search(r'private boolean pageAdvanceReady\([^\n]*\{[^{}]+\}',source)
assert method, 'manual pagination must be separate from saved-capture gating'
if shutil.which('javac'):
 with tempfile.TemporaryDirectory() as temp:
  p=Path(temp)/'BrowserUiCheck.java'
  p.write_text('''public class BrowserUiCheck {
   enum BrowserUiState {exploreExpanded,exploreCollapsed,manualMatch}
   BrowserUiState uiState; boolean enabled,pageDrained; int pageObserved;
   String intakeError=""; static boolean captureReady(boolean on,boolean drained,int saved,int observed,int size,String error,boolean moving){return on&&drained&&saved==observed&&saved==size&&error.isEmpty()&&!moving;}
   '''+method.group()+'''
   public static void main(String[] args){BrowserUiCheck b=new BrowserUiCheck();
    b.uiState=BrowserUiState.exploreExpanded;if(b.pageAdvanceReady(1,1,false))throw new AssertionError("expanded advanced before drain");
    b.enabled=true;b.pageDrained=true;b.pageObserved=1;if(!b.pageAdvanceReady(1,1,false))throw new AssertionError("persisted expanded blocked");
    b.uiState=BrowserUiState.exploreCollapsed;if(b.pageAdvanceReady(1,1,true))throw new AssertionError("collapsed bypassed motion");
    b.uiState=BrowserUiState.manualMatch;b.enabled=false;b.pageDrained=false;if(!b.pageAdvanceReady(0,0,false))throw new AssertionError("manual paging waits for disabled capture");
   }}''')
  subprocess.run(['javac','-d',temp,str(p)],check=True)
  subprocess.run(['java','-cp',temp,'BrowserUiCheck'],check=True)
else: print('SKIP native UI-state JVM: javac unavailable locally')
print('PASS browser UI contract and native pagination states')
