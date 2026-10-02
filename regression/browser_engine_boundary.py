"""Run actual native capture/pending-selection bodies against a real SQLite process.
Android JSON/Rect/SQLite APIs are controlled test adapters. JSON parsing and Android
lifecycle themselves are not verified here. No HTTP and no extra product dependency.
"""
from pathlib import Path
import re, subprocess, tempfile, sqlite3
ROOT=Path(__file__).resolve().parents[1]; SRC=ROOT/'app/src/main/java/it/vintedaffari/app'
def method(file,signature):
 s=(SRC/file).read_text();a=s.index(signature);b=s.index('{',a);level=1;i=b+1
 while level:
  level+=(s[i]=='{')-(s[i]=='}');i+=1
 return s[a:i]
server=r'''import sqlite3,sys,base64
c=sqlite3.connect(sys.argv[1],isolation_level=None)
def dec(s):
 if s=='N':return None
 if s.startswith('I'):return int(s[1:])
 if s.startswith('D'):return float(s[1:])
 return base64.b64decode(s[1:]).decode()
def enc(x):
 if x is None:return 'N'
 if isinstance(x,int):return 'I'+str(x)
 if isinstance(x,float):return 'D'+str(x)
 return 'S'+base64.b64encode(str(x).encode()).decode()
for line in sys.stdin:
 try:
  p=line.rstrip('\n').split('\t');q=base64.b64decode(p[0]).decode();r=c.execute(q,[dec(x) for x in p[1:]])
  rows=r.fetchall() if r.description else [];print('OK\t'+str(len(rows)),flush=True)
  for row in rows:print('\t'.join(enc(x) for x in row),flush=True)
 except Exception as e:print('ERR\t'+str(e),flush=True)
'''
stubs={
'android/graphics/Rect.java':'package android.graphics;public class Rect{public Rect(int a,int b,int c,int d){}public Rect(Rect r){}}',
'android/text/TextUtils.java':'package android.text;public class TextUtils{public static boolean isEmpty(CharSequence s){return s==null||s.length()==0;}}',
'android/content/ContentValues.java':'''package android.content;import java.util.*;import java.text.Normalizer;public class ContentValues{public final Map<String,Object> m=new LinkedHashMap<>();public void put(String k,String v){m.put(k,v);}public void put(String k,Integer v){m.put(k,v);}public void put(String k,Long v){m.put(k,v);}public void put(String k,Double v){m.put(k,v);}public void put(String k,int v){m.put(k,v);}public void put(String k,long v){m.put(k,v);}public void putNull(String k){m.put(k,null);}}''',
'android/database/Cursor.java':'''package android.database;import java.util.*;import java.text.Normalizer;public class Cursor implements AutoCloseable{final List<Object[]> rows;int n=-1;public Cursor(List<Object[]> r){rows=r;}public boolean moveToFirst(){n=0;return !rows.isEmpty();}public boolean moveToNext(){return ++n<rows.size();}public boolean isNull(int i){return rows.get(n)[i]==null;}public String getString(int i){Object x=rows.get(n)[i];return x==null?null:x.toString();}public int getInt(int i){return ((Number)rows.get(n)[i]).intValue();}public long getLong(int i){return ((Number)rows.get(n)[i]).longValue();}public void close(){}}''',
'org/json/JSONException.java':'package org.json;public class JSONException extends Exception{public JSONException(String s){super(s);}}',
'org/json/JSONObject.java':'''package org.json;import java.util.*;import java.text.Normalizer;public class JSONObject{public static final Object NULL=new Object();static final Map<String,Map<String,Object>> snapshots=new HashMap<>();final Map<String,Object> m=new LinkedHashMap<>();public JSONObject(){}public JSONObject(String text)throws JSONException{Map<String,Object> x=snapshots.get(text);if(x==null)throw new JSONException("invalid");m.putAll(x);}public Object opt(String k){return m.get(k);}public String optString(String k){return optString(k,"");}public String optString(String k,String f){Object x=m.get(k);return x==null?f:x.toString();}public boolean has(String k){return m.containsKey(k);}public JSONObject put(String k,Object v)throws JSONException{m.put(k,v);return this;}public Object remove(String k){return m.remove(k);}public Iterator<String> keys(){return m.keySet().iterator();}public JSONArray optJSONArray(String k){return m.get(k) instanceof JSONArray?(JSONArray)m.get(k):null;}public JSONObject optJSONObject(String k){return m.get(k) instanceof JSONObject?(JSONObject)m.get(k):null;}public String toString(){String key="snapshot-"+snapshots.size();snapshots.put(key,new LinkedHashMap<>(m));return key;}}''',
'org/json/JSONArray.java':'''package org.json;import java.util.*;import java.text.Normalizer;public class JSONArray{final List<Object> a=new ArrayList<>();public JSONArray put(Object o){a.add(o);return this;}public int length(){return a.size();}public String optString(int n){return a.get(n).toString();}}''',
'android/database/sqlite/SQLiteDatabase.java':r'''package android.database.sqlite;
import android.database.Cursor;import android.content.ContentValues;import java.util.*;import java.text.Normalizer;import java.io.*;import java.nio.charset.StandardCharsets;
public class SQLiteDatabase{final Process process;final BufferedReader in;final PrintWriter out;int depth;boolean success;
 public SQLiteDatabase(String server,String file)throws Exception{process=new ProcessBuilder("python3","-u",server,file).start();in=new BufferedReader(new InputStreamReader(process.getInputStream()));out=new PrintWriter(process.getOutputStream(),true);}
 String encode(Object x){if(x==null)return "N";if(x instanceof Number)return "I"+((Number)x).longValue();return "S"+Base64.getEncoder().encodeToString(x.toString().getBytes(StandardCharsets.UTF_8));}
 Object decode(String s){if(s.equals("N"))return null;if(s.startsWith("I"))return Long.parseLong(s.substring(1));if(s.startsWith("D"))return Double.parseDouble(s.substring(1));return new String(Base64.getDecoder().decode(s.substring(1)),StandardCharsets.UTF_8);}
 synchronized List<Object[]> request(String sql,Object[] args){try{String line=Base64.getEncoder().encodeToString(sql.getBytes(StandardCharsets.UTF_8));if(args!=null)for(Object x:args)line+="\t"+encode(x);out.println(line);String head=in.readLine();if(head==null||!head.startsWith("OK\t"))throw new IllegalStateException(head+" SQL="+sql);int count=Integer.parseInt(head.substring(3));List<Object[]> rows=new ArrayList<>();for(int n=0;n<count;n++){String[] cols=in.readLine().split("\t",-1);Object[] row=new Object[cols.length];for(int k=0;k<cols.length;k++)row[k]=decode(cols[k]);rows.add(row);}return rows;}catch(IOException e){throw new RuntimeException(e);}}
 public Cursor rawQuery(String sql,String[] args){return new Cursor(request(sql,args));}public void execSQL(String sql,Object[] args){request(sql,args);}public void beginTransaction(){if(depth++==0){request("BEGIN IMMEDIATE",null);success=false;}}public void setTransactionSuccessful(){success=true;}public void endTransaction(){if(--depth==0)request(success?"COMMIT":"ROLLBACK",null);}
 public long insert(String table,String nil,ContentValues v){return insertOrThrow(table,nil,v);}public long insertOrThrow(String table,String nil,ContentValues v){String qs=String.join(",",Collections.nCopies(v.m.size(),"?"));request("INSERT INTO "+table+"("+String.join(",",v.m.keySet())+") VALUES("+qs+")",v.m.values().toArray());return ((Number)request("SELECT last_insert_rowid()",null).get(0)[0]).longValue();}
 public int update(String table,ContentValues v,String where,String[] args){List<String> sets=new ArrayList<>();for(String key:v.m.keySet())sets.add(key+"=?");List<Object> params=new ArrayList<>(v.m.values());if(args!=null)Collections.addAll(params,args);request("UPDATE "+table+" SET "+String.join(",",sets)+" WHERE "+where,params.toArray());return ((Number)request("SELECT changes()",null).get(0)[0]).intValue();}
 public void close(){process.destroy();}
}''',
'it/vintedaffari/app/BggSearchClient.java':'package it.vintedaffari.app;class BggSearchClient{static class Game{String name;int searchScore;java.util.List<String> aliases=new java.util.ArrayList<>();}}',
'it/vintedaffari/app/GameAnalysis.java':'''package it.vintedaffari.app;class GameAnalysis{String candidateName,productPublisher,status;Double matchConfidence;Integer benchmarkCents,totalCents;}'''
}
imports='''package it.vintedaffari.app;import android.database.Cursor;import android.database.sqlite.SQLiteDatabase;import android.content.ContentValues;import android.graphics.Rect;import android.text.TextUtils;import org.json.*;import java.util.*;import java.text.Normalizer;'''
market=imports+'class MarketStore{final DealDatabase helper;MarketStore(DealDatabase h){helper=h;}'+''.join(method('MarketStore.java',sig) for sig in ['public long captureBrowserItem(','private static boolean browserOwned(','private static Long listingIdForCard(','public static String fingerprint(','private static int cents(','private static String normalize(','private static void syncBrowserDeal(','public List<VintedCard> pendingAnalysisCards(','private static Long scalarLong(','private static String scalarString(','private static String safe(','private static void put('])+'Long selected(VintedCard card){return listingIdForCard(helper.getReadableDatabase(),card);}}'
database=imports+'''class DealDatabase{final SQLiteDatabase db;long cachedActiveRunAt;static final long ENGINE_DUPLICATE_SIGHTING_MS=600000;DealDatabase(SQLiteDatabase d){db=d;}SQLiteDatabase getWritableDatabase(){return db;}SQLiteDatabase getReadableDatabase(){return db;}void invalidateActiveObservationSessionCache(){cachedActiveRunAt=0;}static String signature(VintedCard c){return c.capturedSignature;}static int cents(double d){return (int)Math.round(d*100);}'''+method('DealDatabase.java','public synchronized void recordSighting(')+method('MarketStore.java','private static void put(')+'}'
harness=imports+r'''public class BrowserEngineBoundary{static void check(boolean b,String why){if(!b)throw new AssertionError(why);}static JSONObject card(String id,Integer price)throws Exception{JSONObject o=new JSONObject().put("id",id).put("url","https://www.vinted.it/items/"+id).put("title","Catan").put("brand","Kosmos").put("currency","EUR").put("source","dom");if(price!=null)o.put("priceCents",price);return o;}static long number(SQLiteDatabase d,String sql){try(Cursor c=d.rawQuery(sql,null)){return c.moveToFirst()?c.getLong(0):-1;}}public static void main(String[] a)throws Exception{SQLiteDatabase d=new SQLiteDatabase(a[0],a[1]);DealDatabase h=new DealDatabase(d);MarketStore s=new MarketStore(h);
 long one=s.captureBrowserItem(card("101",1000),1000000),two=s.captureBrowserItem(card("102",1000),1000001);check(one!=two,"different IDs collapsed");check(number(d,"SELECT COUNT(*) FROM observations")==2,"missing observations");s.captureBrowserItem(card("101",1000),1000002);check(number(d,"SELECT COUNT(*) FROM price_observations")==2,"repeat inflated history");check(s.captureBrowserItem(card("103",null),1000003)==-1,"unknown price became an item");check(number(d,"SELECT COUNT(*) FROM queue_controls WHERE name='browser_snapshot:103'")==1,"missing-price snapshot lost");check(number(d,"SELECT COUNT(*) FROM observations")==2,"unknown price became zero observation");
 s=new MarketStore(new DealDatabase(d));check(s.captureBrowserItem(card("101",1000),1000004)==one,"restart duplicated ID");List<VintedCard> pending=s.pendingAnalysisCards(8);check(pending.size()==2,"pending selection lost durable work");check(pending.get(0).capturedSignature.equals("browser:101")&&pending.get(1).capturedSignature.equals("browser:102"),"pending reconstruction lost ID");
 s.captureBrowserItem(card("103",900),1000005);check(number(d,"SELECT COUNT(*) FROM observations")==3,"later price did not unlock capture");s.captureBrowserItem(card("101",1200),1000006);check(number(d,"SELECT COUNT(*) FROM market_listings")==3,"price edit duplicated ID");check(number(d,"SELECT current_price_cents FROM market_listings WHERE vinted_item_id='101'")==1200,"price not updated");
 VintedCard before=s.pendingAnalysisCards(8).get(0);check(s.selected(before)!=null,"fresh card rejected");s.captureBrowserItem(card("101",1200).put("protectedPriceCents",1350),1000007);check(s.selected(before)==null,"stale protected total accepted");VintedCard fresh=s.pendingAnalysisCards(8).get(0);check(s.selected(fresh)!=null,"new total rejected");d.execSQL("UPDATE market_listings SET manual_review_required=1 WHERE id=?",new Object[]{two});check(s.pendingAnalysisCards(8).size()==2,"manual hold selected");d.execSQL("UPDATE market_listings SET manual_review_required=0 WHERE id=?",new Object[]{two});
 d.execSQL("UPDATE market_listings SET lifecycle='USER_HIDDEN' WHERE id=?",new Object[]{one});s.captureBrowserItem(card("101",800),1000007);check(number(d,"SELECT current_price_cents FROM market_listings WHERE id="+one)==1200,"hidden listing revived");check(s.pendingAnalysisCards(8).size()==2,"hidden listing leaked into pending");
 JSONObject bad=card("104",800).put("title","Libro guida");s.captureBrowserItem(bad,1000008);check(s.pendingAnalysisCards(8).size()==2,"classifier exclusion was relaxed");
 check(s.captureBrowserItem(card("105",800).put("url","https://evil.org/items/105"),1000009)==-1,"untrusted URL entered intake");check(number(d,"SELECT COUNT(*) FROM observations")==4,"wrong observation count");d.close();System.out.println("PASS native browser capture, durable pending IDs, restart, sparse price, repeats, hidden/type/trust gates");}}'''
with tempfile.TemporaryDirectory() as tmp:
 b=Path(tmp);serverfile=b/'sqlite_server.py';serverfile.write_text(server);dbfile=b/'data.sqlite'
 # Use the production table declarations; the test does not invent a parallel schema.
 conn=sqlite3.connect(dbfile)
 sources=[(SRC/'MarketStore.java').read_text(),(SRC/'DealDatabase.java').read_text()]
 for table in ['market_listings','queue_controls','price_observations','deals','observations']:
  declaration=None
  for text in sources:
   pattern=r'"CREATE TABLE (?:IF NOT EXISTS )?'+table+r'\(.*?"\);'
   m=re.search(pattern,text,re.S)
   if m:
    import json
    declaration=''.join(json.loads(q) for q in re.findall(r'"(?:[^"\\]|\\.)*"',m.group(0)));break
  assert declaration,table
  conn.execute(declaration)
 conn.close()
 stubs.update({'it/vintedaffari/app/MarketStore.java':market,'it/vintedaffari/app/DealDatabase.java':database,'it/vintedaffari/app/BrowserEngineBoundary.java':harness})
 for name,content in stubs.items():
  p=b/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content)
 files=[str(p) for p in b.rglob('*.java')]+[str(SRC/(name+'.java')) for name in ['VintedCard','VintedBrowserPolicy','BrowserCapturePolicy','BrowserIntakeSql','ListingClassifier','BoardGameIntakeGate']]
 subprocess.run(['javac','-d',tmp,*files],check=True)
 subprocess.run(['java','-cp',tmp,'it.vintedaffari.app.BrowserEngineBoundary',str(serverfile),str(dbfile)],check=True)
