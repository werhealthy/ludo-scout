"""Run the real matcher/hash algorithm against Android boundary adapters.

Break caught: a repeated exact photo URL downloads again, or a cache hit changes
the score. HTTP is intercepted at URLConnection; no Vinted request is made.
"""
from pathlib import Path
import subprocess, tempfile

ROOT = Path(__file__).resolve().parents[1]
PKG = ROOT / 'app/src/main/java/it/vintedaffari/app'
STUBS = {
'android/content/Context.java': '''package android.content; import java.io.File; public class Context { public Context getApplicationContext(){return this;} public File getFilesDir(){return new File(System.getProperty("java.io.tmpdir"));} }''',
'android/text/TextUtils.java': '''package android.text; public class TextUtils {public static boolean isEmpty(CharSequence s){return s==null||s.length()==0;}}''',
'android/graphics/Color.java': '''package android.graphics; public class Color {public static int red(int c){return(c>>16)&255;}public static int green(int c){return(c>>8)&255;}public static int blue(int c){return c&255;}}''',
'android/graphics/Bitmap.java': '''package android.graphics; public class Bitmap {public enum Config{RGB_565} public final int w,h; public final int[] p; boolean recycled; public Bitmap(int w,int h){this.w=w;this.h=h;p=new int[w*h];}public int getWidth(){return w;}public int getHeight(){return h;}public int getPixel(int x,int y){return p[y*w+x];}public void recycle(){recycled=true;}public boolean isRecycled(){return recycled;}public static Bitmap createBitmap(Bitmap b,int x,int y,int w,int h){Bitmap a=new Bitmap(w,h);for(int j=0;j<h;j++)for(int i=0;i<w;i++)a.p[j*w+i]=b.getPixel(x+i,y+j);return a;}public static Bitmap createScaledBitmap(Bitmap b,int w,int h,boolean f){if(w==b.w&&h==b.h)return b;Bitmap a=new Bitmap(w,h);for(int j=0;j<h;j++)for(int i=0;i<w;i++)a.p[j*w+i]=b.getPixel(i*b.w/w,j*b.h/h);return a;}}''',
'android/graphics/BitmapFactory.java': '''package android.graphics; public class BitmapFactory {public static Bitmap fixture;public static class Options{public boolean inJustDecodeBounds;public int outWidth,outHeight,inSampleSize;public Bitmap.Config inPreferredConfig;}public static Bitmap decodeFile(String f){return copy();}public static Bitmap decodeFile(String f,Options o){return decode(o);}public static Bitmap decodeByteArray(byte[] b,int s,int n,Options o){return decode(o);}static Bitmap decode(Options o){if(o.inJustDecodeBounds){o.outWidth=fixture.w;o.outHeight=fixture.h;return null;}return copy();}static Bitmap copy(){Bitmap b=new Bitmap(fixture.w,fixture.h);System.arraycopy(fixture.p,0,b.p,0,b.p.length);return b;}}''',
'it/vintedaffari/app/ThumbnailStore.java': '''package it.vintedaffari.app;import java.io.File;import android.content.Context; public class ThumbnailStore{public static File observed;public static File fileFor(Context c,String s){return observed;}}''',
'it/vintedaffari/app/VintedPhotoHashCache.java': '''package it.vintedaffari.app;import java.util.*;import android.content.Context;import android.graphics.Bitmap;public class VintedPhotoHashCache{public static final Map<String,Long> hashes=new HashMap<>();public static boolean broken;public static Long lookupExact(Context c,String url){if(broken)throw new IllegalStateException();return hashes.get(url);}public static void record(Context c,String url,Bitmap b){hashes.put(url,VisualCoverMatcher.dHash64(b));}}''',
'it/vintedaffari/app/VintedPublicSession.java': '''package it.vintedaffari.app;import android.content.Context;public class VintedPublicSession{public static void recordPhotoMatcherEvent(Context c,String event,int code){}}''',
'it/vintedaffari/app/PhotoCacheRegression.java': '''package it.vintedaffari.app;
import android.content.Context;import android.graphics.*;import java.io.*;import java.net.*;
public class PhotoCacheRegression {
 static int downloads,code=200;static boolean ioFailure;static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
 public static void main(String[] args)throws Exception{
  URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler(){protected URLConnection openConnection(URL u){downloads++;return new HttpURLConnection(u){public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}public int getResponseCode()throws IOException{if(ioFailure)throw new IOException();return code;}public InputStream getInputStream(){return new ByteArrayInputStream(new byte[]{1,2});}};}}:null);
  Context c=new Context();File f=File.createTempFile("observed", ".png");try(FileOutputStream out=new FileOutputStream(f)){out.write(new byte[3000]);}ThumbnailStore.observed=f;
  Bitmap b=new Bitmap(32,40);for(int y=0;y<40;y++)for(int x=0;x<32;x++)b.p[y*32+x]=((x*7+y*3)&255)*0x010101;BitmapFactory.fixture=b;
  String url="https://images1.vinted.net/photo/f800/image.jpg?sig=one";
  double first=VintedPhotoMatcher.similarity(c,"s",url);check(first==1.0,"literal identical-frame score must be 1");check(downloads==1,"first photo downloads once");
  double cached=VintedPhotoMatcher.similarity(c,"s",url);check(cached==first,"cache preserves score");check(downloads==1,"cached exact photo must not download again");
  VintedPhotoHashCache.hashes.put(url,0L);double zero=VintedPhotoMatcher.similarity(c,"s",url);check(!Double.isNaN(zero)&&downloads==1,"zero hash is valid, not missing");
  VintedPhotoMatcher.similarity(c,"s",url.replace("f800","f300"));check(downloads==2,"different crop URL must download");
  VintedPhotoHashCache.broken=true;VintedPhotoMatcher.similarity(c,"s",url);check(downloads==3,"cache read error falls back to download");VintedPhotoHashCache.broken=false;
  code=403;check(Double.isNaN(VintedPhotoMatcher.similarity(c,"s",url+"x")),"403 returns no evidence");check(downloads==4,"403 gets no retry");
  ioFailure=true;check(Double.isNaN(VintedPhotoMatcher.similarity(c,"s",url+"y")),"I/O failure returns no evidence");check(downloads==5,"I/O failure gets no retry");
  f.delete();check(Double.isNaN(VintedPhotoMatcher.similarity(c,"s",url)),"missing observed photo yields no evidence");check(downloads==5,"missing observation performs no HTTP");
  for(int i=0;i<100;i++){long h=0x91ac3257L*i;check(VisualCoverMatcher.similarity(VisualCoverMatcher.queryHashes64(b),h)>=0,"hash score bounded");}
  System.out.println("Photo cache reuse behavior passed");
 }
}'''
}

with tempfile.TemporaryDirectory() as temp:
    build = Path(temp)
    sources = []
    for name, content in STUBS.items():
        file = build / name
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(content)
        sources.append(str(file))
    sources += [str(PKG / 'VintedPhotoMatcher.java'), str(PKG / 'VisualCoverMatcher.java')]
    subprocess.run(['javac', '-d', str(build), *sources], check=True)
    subprocess.run(['java', '-cp', str(build), 'it.vintedaffari.app.PhotoCacheRegression'], check=True)
