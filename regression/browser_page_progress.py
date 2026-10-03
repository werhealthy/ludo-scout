"""Exercise the native page completion predicate, with no Android runtime dependency."""
from pathlib import Path
import re, shutil, subprocess, tempfile
root=Path(__file__).resolve().parents[1]
browser=(root/'app/src/main/java/it/vintedaffari/app/VintedBrowserActivity.java').read_text()
match=re.search(r'private static boolean captureReady\([^\n]+?\{[^{}]+\}',browser)
assert match, 'native Next still follows WebView load instead of saved capture'
assert 'LudoCaptureControl.canAdvance(' in browser, 'navigation must recheck live JS before leaving'
assert 'token!=pageGeneration' in browser, 'late navigation callback may leave a newer page'
if not shutil.which('javac'):
 print('SKIP native JVM locally; CI must run Java17')
else:
 with tempfile.TemporaryDirectory() as temp:
  p=Path(temp)/'ProgressCheck.java'
  p.write_text('''package it.vintedaffari.app;public class ProgressCheck {
  '''+match.group()+'''
  static void yes(boolean value){if(!value)throw new AssertionError();}
  public static void main(String[] args){
   yes(!captureReady(true,false,32,128,128,"",false));
   yes(!captureReady(true,true,127,128,128,"",false));
   yes(!captureReady(true,true,128,128,127,"",false));
   yes(!captureReady(false,true,128,128,128,"",false));
   yes(!captureReady(true,true,128,128,128,"IOException",false));
   yes(!captureReady(true,true,128,128,128,"",true));
   yes(captureReady(true,true,128,128,128,"",false));
   yes(captureReady(true,true,0,0,0,"",false));
   yes(LudoJourneyFocus.stage(0)==-1);yes(LudoJourneyFocus.stage(4)==2);
   yes(LudoJourneyFocus.stage(18)==1);yes(LudoJourneyFocus.x(-1)==0);
   yes(LudoJourneyFocus.y(0)<-.99f);yes(LudoJourneyFocus.x(1)>.9f);
   yes(LudoJourneyFocus.x(4)<-.9f);
  }}''')
  subprocess.run(['javac','-d',temp,str(p),str(root/'app/src/main/java/it/vintedaffari/app/LudoJourneyFocus.java')],check=True)
  subprocess.run(['java','-cp',temp,'it.vintedaffari.app.ProgressCheck'],check=True)
  print('PASS actual native Next predicate and Ludo stage focus')
