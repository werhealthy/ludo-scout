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
'it/vintedaffari/app/VintedPublicSession.java': '''package it.vintedaffari.app;import android.content.Context;public class VintedPublicSession{public static final java.util.Map<String,Integer> events=new java.util.HashMap<>();public static void recordPhotoMatcherEvent(Context c,String event,int code){events.merge(event,1,Integer::sum);}}''',
'it/vintedaffari/app/PhotoCacheRegression.java': '''package it.vintedaffari.app;
import android.content.Context;import android.graphics.*;import java.io.*;import java.net.*;
public class PhotoCacheRegression {
 static int downloads,code=200;static boolean ioFailure;static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
 public static void main(String[] args)throws Exception{
  URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler(){protected URLConnection openConnection(URL u){downloads++;return new HttpURLConnection(u){public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}public int getResponseCode()throws IOException{if(ioFailure)throw new IOException();return code;}public InputStream getInputStream(){return new ByteArrayInputStream(new byte[]{1,2});}};}}:null);
  Context c=new Context();File f=File.createTempFile("observed", ".png");try(FileOutputStream out=new FileOutputStream(f)){out.write(new byte[3000]);}ThumbnailStore.observed=f;
  Bitmap b=new Bitmap(32,40);for(int y=0;y<40;y++)for(int x=0;x<32;x++)b.p[y*32+x]=(255-x*7)*0x010101;BitmapFactory.fixture=b;
  String url="https://images1.vinted.net/photo/f800/image.jpg?sig=one";
  double first=VintedPhotoMatcher.similarity(c,"s",url);check(first==1.0,"literal identical-frame score must be 1");check(downloads==1,"first photo downloads once");
  double cached=VintedPhotoMatcher.similarity(c,"s",url);check(cached==first,"cache preserves score");check(downloads==1,"cached exact photo must not download again");
  VintedPhotoHashCache.hashes.put(url,0L);double zero=VintedPhotoMatcher.similarity(c,"s",url);check(zero==0.0&&downloads==1,"zero hash is valid, not missing");
  VintedPhotoMatcher.similarity(c,"s",url.replace("f800","f300"));check(downloads==2,"different crop URL must download");
  VintedPhotoHashCache.broken=true;VintedPhotoMatcher.similarity(c,"s",url);check(downloads==3,"cache read error falls back to download");VintedPhotoHashCache.broken=false;
  code=403;check(Double.isNaN(VintedPhotoMatcher.similarity(c,"s",url+"x")),"403 returns no evidence");check(downloads==4,"403 gets no retry");
  ioFailure=true;check(Double.isNaN(VintedPhotoMatcher.similarity(c,"s",url+"y")),"I/O failure returns no evidence");check(downloads==5,"I/O failure gets no retry");
  f.delete();check(Double.isNaN(VintedPhotoMatcher.similarity(c,"s",url)),"missing observed photo yields no evidence");check(downloads==5,"missing observation performs no HTTP");
  check(VintedPublicSession.events.get("cache")==2,"matcher emits two cache hits");check(VintedPublicSession.events.get("download")==5,"matcher emits five attempts");check(VintedPublicSession.events.get("http")==4,"matcher counts responses separately from I/O errors");
  check(VisualCoverMatcher.similarity(new long[]{0L},1L)==0.984375,"one bit distance literal score");
  for(int i=0;i<100;i++){Bitmap cover=new Bitmap(31,37);for(int y=0;y<37;y++)for(int x=0;x<31;x++)cover.p[y*31+x]=((x*13+y*17+i*23)&255)*0x010101;double bitmapScore=VisualCoverMatcher.similarity(b,cover);double hashScore=VisualCoverMatcher.similarity(VisualCoverMatcher.queryHashes64(b),VisualCoverMatcher.dHash64(cover));check(bitmapScore==hashScore,"cache and bitmap scores must agree");}
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

# Execute the exact production lookup SQL with SQLite, including malformed rows.
# Removing any URL/type/time predicate must fail this fixture.
import re, sqlite3
cache_source = (PKG / 'VintedPhotoHashCache.java').read_text()
method = cache_source.split('public static Long lookupExact(', 1)[1].split('public static Long lookup(', 1)[0]
suffix = method.split('"SELECT hash64 FROM "+TABLE+"', 1)[1].split('"', 1)[0]
sql = 'SELECT hash64 FROM vinted_photo_hash_cache_v1' + suffix
db = sqlite3.connect(':memory:')
db.execute('CREATE TABLE vinted_photo_hash_cache_v1(photo_key TEXT PRIMARY KEY,image_url TEXT,hash64 INTEGER,last_at INTEGER)')
now = 1_000_000_000
args = ('photo', 'https://image/f800.jpg?a=1&b=2', str(now-86_400_000), str(now))
def row(url, value, at):
    db.execute('DELETE FROM vinted_photo_hash_cache_v1')
    db.execute('INSERT INTO vinted_photo_hash_cache_v1 VALUES(?,?,?,?)', ('photo',url,value,at))
    return db.execute(sql,args).fetchone()
assert row('https://image/f800.jpg?a=1&amp;b=2', 0, now) == (0,)
assert row('https://image/f800.jpg?a=1&b=2', -1, now) == (-1,)
assert row('https://image/f300.jpg?a=1&b=2', 7, now) is None
assert row('https://image/f800.jpg?a=1&b=2', 'broken', now) is None
assert row('https://image/f800.jpg?a=1&b=2', 7, now-86_400_001) is None
assert row('https://image/f800.jpg?a=1&b=2', 7, now+1) is None
print('Exact photo cache SQLite fixtures passed')

# Run the real telemetry/epoch methods with SQLite boundary adapters.
def java_method(source, name):
    match = re.search(r'(?:public |private )static [^\n]+\b'+name+r'\([^\n]*\)\{', source)
    start = match.start()
    brace = source.index('{', start)
    depth = 1
    end = brace+1
    while depth:
        depth += (source[end]=='{') - (source[end]=='}')
        end += 1
    return source[start:end]
session_source = (PKG / 'VintedPublicSession.java').read_text()
telemetry = 'package it.vintedaffari.app;import android.content.*;import android.database.*;import android.database.sqlite.*;public class VintedPublicSession {private static final String LEDGER_PREFIX="t2_ledger:",LEDGER_BUILD="request-ledger-v3-efficiency";'
for method_name in ['recordPhotoMatcherEvent','ensureLedgerEpoch','incLedger','control','controlText','putControl']:
    telemetry += java_method(session_source, method_name)
telemetry += '}'
ledger_stubs = {
'android/content/Context.java': STUBS['android/content/Context.java'],
'android/content/ContentValues.java': '''package android.content;public class ContentValues extends java.util.HashMap<String,Object>{public void putNull(String k){put(k,null);}}''',
'android/database/Cursor.java': '''package android.database;public class Cursor implements AutoCloseable{final Object v;public Cursor(Object v){this.v=v;}public boolean moveToFirst(){return v!=null;}public long getLong(int i){return ((Number)v).longValue();}public String getString(int i){return (String)v;}public boolean isNull(int i){return v==null;}public void close(){}}''',
'android/database/sqlite/SQLiteDatabase.java': '''package android.database.sqlite;import android.content.ContentValues;import android.database.Cursor;import java.util.*;public class SQLiteDatabase{public static final int CONFLICT_REPLACE=5;public final Map<String,ContentValues> rows=new HashMap<>();boolean transaction;public void beginTransactionNonExclusive(){transaction=true;}public boolean inTransaction(){return transaction;}public void setTransactionSuccessful(){}public void endTransaction(){transaction=false;}public int delete(String table,String where,String[] args){rows.entrySet().removeIf(e->e.getKey().startsWith("t2_ledger:"));return 1;}public Cursor rawQuery(String sql,String[] args){if(sql.contains("COUNT(*)"))return new Cursor(0L);ContentValues r=rows.get(args[0]);return new Cursor(r==null?null:r.get(sql.contains("text_value")?"text_value":"value"));}public long insertWithOnConflict(String table,String nullHack,ContentValues v,int conflict){rows.put((String)v.get("name"),v);return 1;} }''',
'it/vintedaffari/app/DealDatabase.java': '''package it.vintedaffari.app;import android.content.Context;import android.database.sqlite.SQLiteDatabase;public class DealDatabase{public static final SQLiteDatabase db=new SQLiteDatabase();public DealDatabase(Context c){}public SQLiteDatabase getWritableDatabase(){return db;}public void close(){}}''',
'it/vintedaffari/app/VintedPublicSession.java': telemetry,
'it/vintedaffari/app/PhotoLedgerRegression.java': '''package it.vintedaffari.app;import android.content.*;import java.lang.reflect.*;public class PhotoLedgerRegression{static void check(boolean b,String m){if(!b)throw new AssertionError(m);}static long count(String k){ContentValues v=DealDatabase.db.rows.get("t2_ledger:"+k);return v==null?0:((Number)v.get("value")).longValue();}public static void main(String[] args)throws Exception{Context c=new Context();VintedPublicSession.recordPhotoMatcherEvent(c,"cache",0);Method init=VintedPublicSession.class.getDeclaredMethod("ensureLedgerEpoch",android.database.sqlite.SQLiteDatabase.class,long.class);init.setAccessible(true);init.invoke(null,DealDatabase.db,System.currentTimeMillis());check(count("photo:cache")==1,"first photo event must survive diagnostic epoch initialization");VintedPublicSession.recordPhotoMatcherEvent(c,"download",0);VintedPublicSession.recordPhotoMatcherEvent(c,"http",403);VintedPublicSession.recordPhotoMatcherEvent(c,"http",429);VintedPublicSession.recordPhotoMatcherEvent(c,"error",0);VintedPublicSession.recordPhotoMatcherEvent(c,"invalid",0);check(count("photo:download")==1&&count("photo:http")==2&&count("photo:http:403")==1&&count("photo:http:429")==1&&count("photo:error")==1,"photo outcomes persist");check(count("physical")==0&&count("link:physical")==0,"photo traffic leaves page ledger untouched");check(count("photo:invalid")==0,"unbounded keys rejected");check(!DealDatabase.db.inTransaction(),"telemetry releases transaction");System.out.println("Photo ledger epoch behavior passed");}}'''
}
with tempfile.TemporaryDirectory() as temp:
    build = Path(temp)
    files = []
    for name, content in ledger_stubs.items():
        file = build / name
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(content)
        files.append(str(file))
    subprocess.run(['javac','-d',str(build),*files],check=True)
    subprocess.run(['java','-cp',str(build),'it.vintedaffari.app.PhotoLedgerRegression'],check=True)
