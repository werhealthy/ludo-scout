"""Execute production photo loading/cleanup and Home policy at JVM boundaries."""
from pathlib import Path
import subprocess, tempfile

root=Path(__file__).resolve().parents[1]
src=root/"app/src/main/java/it/vintedaffari/app"
main=(src/"MainActivity.java").read_text()
def method(signature):
    start=main.index(signature)
    end=main.index("\n    private ",start+len(signature))
    return main[start:end]

harness=r'''
package it.vintedaffari.app;
import java.io.*;import java.net.*;import java.util.*;import java.util.concurrent.*;
public class HomeMediaRegression {
 static class Bitmap {final int bytes;Bitmap(int n){bytes=n;}}
 static class ImageView {Object tag;Bitmap bitmap;void setTag(Object t){tag=t;}Object getTag(){return tag;}void setImageBitmap(Bitmap b){bitmap=b;}}
 static class Prefs {Prefs edit(){return this;}Prefs putInt(String k,int v){return this;}Prefs putString(String k,String v){return this;}Prefs putBoolean(String k,boolean v){return this;}void apply(){}}
 final ExecutorService galleryNet=Executors.newSingleThreadExecutor();
 final Map<String,Bitmap> imageCache=new ConcurrentHashMap<>();
 final File cache;static final int MODE_PRIVATE=0;
 HomeMediaRegression(File f){cache=f;}
 File getCacheDir(){return cache;}Prefs getSharedPreferences(String n,int mode){return new Prefs();}
 void runOnUiThread(Runnable r){r.run();}
 Bitmap decodeLocalBitmap(File f,int w,int h){return f.length()>0?new Bitmap((int)f.length()):null;}
 // Only transport, pixels and Android UI are adapters: production loader/decoder run below.
 __METHODS__
 static int physical;static final List<String> fetched=new ArrayList<>();
 static void eq(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 static void installHttp(){URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler(){protected URLConnection openConnection(URL url){return new HttpURLConnection(url){
  public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}
  public int getResponseCode(){physical++;fetched.add(url.toString());return url.toString().contains("blocked")?403:url.toString().contains("limited")?429:url.toString().contains("missing")?404:200;}
  public InputStream getInputStream(){return new ByteArrayInputStream(new byte[]{1,2,3});}
 };}}:null);}
 void drain()throws Exception{galleryNet.submit(()->{}).get(5,TimeUnit.SECONDS);}
 @SuppressWarnings("unchecked") static List<String> variants(String preferred,List<String> observed)throws Exception{
  try{return (List<String>)PhotoIdentity.class.getDeclaredMethod("sources",String.class,List.class).invoke(null,preferred,observed);}
  catch(NoSuchMethodException old){return Collections.singletonList(preferred);}
 }
 static void test(String name,Throwing action,List<String> failures){try{action.run();System.out.println("PASS "+name);}catch(Throwable e){failures.add(name+": "+e);System.out.println("FAIL "+name+": "+e);}}
 interface Throwing {void run()throws Exception;}
 public static void main(String[] args)throws Exception{
  installHttp();List<String> failures=new ArrayList<>();File dir=new File(args[0]);dir.mkdirs();HomeMediaRegression h=new HomeMediaRegression(dir);
  String id="12345678-1234-1234-1234-123456789abc";
  String missing="https://images.vinted.net/t/"+id+"/f800/missing.jpg";
  String good="https://images.vinted.net/t/"+id+"/f300/good.jpg";
  String other="https://images.vinted.net/t/87654321-4321-4321-4321-cba987654321/f300/good.jpg";
  test("owned canonical identity",()->eq(!HomeDiscoveryPolicy.eligible("00010","a","IT",Collections.singleton("10"),0),"padded BGG ID must not reappear"),failures);
  test("foreign editions stay available outside offers",()->eq(HomeDiscoveryPolicy.eligible("20","a","FR|DEP",Collections.emptySet(),0),"declass rather than erase all Home candidates"),failures);
  test("observed variants recover same photo only",()->{List<String> urls=variants(missing,Arrays.asList(missing,good,other,good));eq(urls.equals(Arrays.asList(missing,good)),"must retain one known fallback of the same photo");},failures);
  test("cached alternative avoids remote requests",()->{physical=0;h.imageCache.clear();h.imageCache.put(good,new Bitmap(7));ImageView im=new ImageView();h.loadImageOn(h.galleryNet,im,Arrays.asList(missing,good),null,null);h.drain();eq(physical==0&&im.bitmap!=null&&im.bitmap.bytes==7,"all known cached variants must precede HTTP");},failures);
  test("404 falls back to observed URL",()->{physical=0;h.imageCache.clear();ImageView im=new ImageView();h.loadImageOn(h.galleryNet,im,Arrays.asList(missing,good),null,null);h.drain();eq(physical==2&&im.bitmap!=null,"known fallback loads");},failures);
  for(String gate:new String[]{"blocked","limited"})test(gate+" does not fan out",()->{physical=0;h.imageCache.clear();ImageView im=new ImageView();int[] failed={0};h.loadImageOn(h.galleryNet,im,Arrays.asList("https://images.vinted.net/"+gate,good),null,()->failed[0]++);h.drain();eq(physical==1&&im.bitmap==null&&failed[0]==1,"remote control stops this attempt");},failures);
  test("failed input deletes temporary file",()->{try{h.decodeRemote(new InputStream(){public int read()throws IOException{throw new IOException("fixture");}});}catch(IOException expected){}eq(dir.list().length==0,"copy failure leaks temp file");},failures);
  // Clear any leaked file so the following assertion has an independent baseline.
  for(File f:dir.listFiles())f.delete();
  test("oversize input deletes temporary file",()->{try{h.decodeRemote(new InputStream(){int left=11*1024*1024;public int read(){return left-->0?1:-1;}public int read(byte[] b,int off,int len){if(left<=0)return -1;int n=Math.min(left,len);Arrays.fill(b,off,off+n,(byte)1);left-=n;return n;}});}catch(IOException expected){}eq(dir.list().length==0,"size rejection leaks temp file");},failures);
  h.galleryNet.shutdownNow();if(!failures.isEmpty())throw new AssertionError(String.join("\n",failures));
 }
}
'''
body=harness.replace("__METHODS__",method("private void loadImageOn(")+"\n"+method("private Bitmap decodeRemote("))
with tempfile.TemporaryDirectory() as tmp:
    out=Path(tmp);java=out/"HomeMediaRegression.java";java.write_text(body)
    subprocess.run(["javac","-d",tmp,str(java)]+[str(src/(n+".java")) for n in ["PhotoIdentity","HomeDiscoveryPolicy","HomePresentation","GamePreferenceState"]],check=True)
    subprocess.run(["java","-cp",tmp,"it.vintedaffari.app.HomeMediaRegression",str(out/"cache")],check=True)
