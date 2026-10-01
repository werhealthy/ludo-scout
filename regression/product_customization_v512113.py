from pathlib import Path
import subprocess, tempfile
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/it/vintedaffari/app'
for name in ['FeaturedDismissalSession','CoverSource','CoverImportRequest']:
    assert (src/(name+'.java')).exists(), 'Missing production behavior: '+name
java=r'''package it.vintedaffari.app;
public class ProductCustomizationRegression {
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception {
  FeaturedDismissalSession s=new FeaturedDismissalSession();
  check(!s.excludes("a"),"fresh session excluded an offer");s.dismiss("a","Game");
  check(s.excludes("a")&&!s.excludes("b"),"dismissal must match listing identity only");
  s.dismiss("b","Other");s.undo();check(s.excludes("a")&&!s.excludes("b"),"undo must restore latest offer only");
  s.clear();check(!s.excludes("a"),"app close must reset exclusions");
  s.dismiss("", "Missing");check(!s.excludes(""),"unknown identity cannot exclude unrelated offers");
  check("6526676".equals(CoverSource.pageId("https://boardgamegeek.com/image/6526676/aye-dark-overlord-the-green-box")),"user page URL did not resolve its image ID");
  check(CoverSource.pageId("https://boardgamegeek.com.evil.test/image/6526676/x")==null,"lookalike host treated as BGG");
  check(CoverSource.pageId("https://boardgamegeek.com/boardgame/1/x")==null,"game page treated as image page");
  String want="https://cf.geekdo-images.com/front.jpg?a=1&b=2";
  check(want.equals(CoverSource.metaImage("<meta content='https://cf.geekdo-images.com/front.jpg?a=1&amp;b=2' property='og:image'>")),"metadata order/entities lost");
  check(CoverSource.metaImage("<meta property='og:image' content='https://boardgamegeek.com/logo.png'>")==null,"BGG logo accepted as product cover");
  for(String bad:new String[]{"http://example.com/x","https://localhost/x","https://127.0.0.1/x","https://user:pass@example.com/x","file:///tmp/x"}){
   boolean rejected=false;try{CoverSource.https(bad);}catch(Exception expected){rejected=true;}check(rejected,"unsafe or non-HTTPS input accepted: "+bad);
  }
  check("https://example.com/front.png".equals(CoverSource.https("https://example.com/front.png")),"direct HTTPS image URL rejected");
  CoverImportRequest request=new CoverImportRequest();request.check();request.cancel();boolean cancelled=false;try{request.check();}catch(java.io.IOException expected){cancelled=true;}check(cancelled,"cancelled request continued work");
  System.out.println("PASS contextual dismiss/undo/reset and BGG-page/metadata/direct-URL/cancellation cases");
 }
}'''
with tempfile.TemporaryDirectory() as temp:
 p=Path(temp)/'ProductCustomizationRegression.java';p.write_text(java)
 subprocess.run(['javac','-d',temp,str(src/'FeaturedDismissalSession.java'),str(src/'CoverSource.java'),str(src/'CoverImportRequest.java'),str(p)],check=True)
 subprocess.run(['java','-cp',temp,'it.vintedaffari.app.ProductCustomizationRegression'],check=True)

# Execute the actual pointer/cleanup methods with the Android preference boundary adapted.
# The fake commit mirrors Android's in-memory update even when the disk write reports failure.
def extract(source,signature):
 start=source.index(signature);brace=source.index('{',start);depth=0
 for i in range(brace,len(source)):
  if source[i]=='{':depth+=1
  elif source[i]=='}':
   depth-=1
   if depth==0:return source[start:i+1]
 raise ValueError(signature)
store=(src/'CoverOverrideStore.java').read_text()
methods='\n'.join(extract(store,s) for s in ['static SharedPreferences prefs(','static String key(','static File current(','static synchronized boolean save(','static synchronized boolean restore(','static void rollback('])
art=(src/'ArtworkStore.java').read_text()
display=extract(art,'public static File displayFile(')
prefs=r'''package android.content;
public interface SharedPreferences {
 String getString(String key,String fallback);Editor edit();
 interface Editor {Editor putString(String key,String value);Editor remove(String key);boolean commit();void apply();}
}'''
context=r'''package android.content;
import java.io.File;import java.util.*;
public class Context {
 public static final int MODE_PRIVATE=0;public final File files;public boolean fail;
 final Map<String,String> values=new HashMap<>();
 public Context(File f){files=f;}public File getFilesDir(){return files;}
 public SharedPreferences getSharedPreferences(String n,int mode){return new SharedPreferences(){
  public String getString(String k,String d){return values.getOrDefault(k,d);}
  public Editor edit(){return new Editor(){Map<String,String> updates=new HashMap<>();Set<String> removed=new HashSet<>();
   public Editor putString(String k,String v){updates.put(k,v);return this;}public Editor remove(String k){removed.add(k);return this;}
   public boolean commit(){for(String k:removed)values.remove(k);values.putAll(updates);return !fail;}public void apply(){commit();}
  };}
 };}
}'''
harness=r'''package it.vintedaffari.app;
import android.content.*;import java.io.*;import java.nio.file.*;
class CoverOverrideStore { __METHODS__ }
class ArtworkStore {
 static File bggFile(Context c,String id){return new File(c.getFilesDir(),"canonical.jpg");}
 __DISPLAY__
}
public class CoverStorageRegression {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception {
  File root=new File(args[0]);root.mkdirs();File dir=new File(root,"cover_overrides");dir.mkdirs();Context c=new Context(root);
  File original=new File(root,"canonical.jpg");Files.write(original.toPath(),new byte[]{1,2});
  File a=new File(dir,"cover-first.png"),b=new File(dir,"cover-second.png");Files.write(a.toPath(),new byte[]{3});Files.write(b.toPath(),new byte[]{4});
  check(ArtworkStore.displayFile(c,"12").equals(original),"default cover changed");
  check(CoverOverrideStore.save(c,"0012",a,"https://example.com/a"),"save rejected valid identity");
  check(ArtworkStore.displayFile(c,"12").equals(a),"canonical cache won over user cover");
  check(ArtworkStore.displayFile(c,"13").equals(original),"replacement leaked to another game");
  c.fail=true;check(!CoverOverrideStore.save(c,"12",b,"https://example.com/b"),"failed commit reported success");check(ArtworkStore.displayFile(c,"12").equals(a)&&a.exists(),"failed save lost previous artwork");
  check(!CoverOverrideStore.restore(c,"12"),"failed restore reported success");check(ArtworkStore.displayFile(c,"12").equals(a)&&a.exists(),"failed restore deleted active artwork");
  c.fail=false;check(CoverOverrideStore.save(c,"12",b,"https://example.com/b"),"second save failed");check(!a.exists()&&b.exists()&&original.exists(),"replacement cleanup damaged original or retained old PNG");
  check(CoverOverrideStore.restore(c,"12"),"restore failed");check(!b.exists()&&ArtworkStore.displayFile(c,"12").equals(original)&&original.exists(),"restore failed to return canonical cover or clean override");
  check(!CoverOverrideStore.save(c,"invalid",original,"bad"),"invalid identity saved");
  System.out.println("PASS real display-file precedence, identity, save/restore failure rollback and file cleanup");
 }
}'''.replace('__METHODS__',methods).replace('__DISPLAY__',display)
with tempfile.TemporaryDirectory() as temp:
 d=Path(temp);(d/'android/content').mkdir(parents=True)
 (d/'android/content/SharedPreferences.java').write_text(prefs);(d/'android/content/Context.java').write_text(context);(d/'CoverStorageRegression.java').write_text(harness)
 subprocess.run(['javac','-d',temp,str(d/'android/content/SharedPreferences.java'),str(d/'android/content/Context.java'),str(src/'GamePreferenceState.java'),str(d/'CoverStorageRegression.java')],check=True)
 subprocess.run(['java','-cp',temp,'it.vintedaffari.app.CoverStorageRegression',str(d/'files')],check=True)
