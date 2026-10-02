package it.vintedaffari.app;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Low-cost reader for Vinted public pages.
 *
 * <p>v5.11.13: the request gate is persisted in SQLite instead of SharedPreferences. Ludo Scout
 * has three Android processes (:ui, :radar and the default queue process), and SharedPreferences
 * is not a supported cross-process synchronization primitive. Reserving a request slot inside one
 * SQLite transaction gives every process one authoritative pacing/circuit state.</p>
 */
public final class VintedPublicSession {
    public static final String HOST="https://www.vinted.it";
    private static final String PREFS="va_v3_diag"; // diagnostics only, never authoritative
    private static final long PUBLIC_MIN_INTERVAL_MS=55_000L;
    private static final long PUBLIC_CACHE_MS=10*60_000L;
    private static final int PUBLIC_HOURLY_BUDGET=60;
    private static final long LIMIT_COOLDOWN_MS=45*60_000L;

    private static final String K_LAST="vinted_http_last_at";
    private static final String K_WINDOW="vinted_http_window_start";
    private static final String K_USED="vinted_http_window_count";
    private static final String K_CIRCUIT="vinted_http_circuit_until";
    private static final String K_LAST_CODE="vinted_http_last_code";

    // TEST 2: authoritative cross-process request ledger. This does not change pacing or requests;
    // it only classifies requests that already happen so we can measure amplification safely.
    private static final String LEDGER_PREFIX="t2_ledger:";
    private static final String LEDGER_BUILD="request-ledger-v3-efficiency";

    private static final Map<String,CachedResponse> PUBLIC_CACHE=Collections.synchronizedMap(
            new LinkedHashMap<String,CachedResponse>(20,.75f,true){
                @Override protected boolean removeEldestEntry(Map.Entry<String,CachedResponse> e){return size()>16;}
            });

    private static final class CachedResponse {final Response response;final long at;CachedResponse(Response r,long a){response=r;at=a;}}

    public static final class GateState {
        public final long allowedAt,windowStart,lastRequestAt,circuitUntil;
        public final int used;
        public final String reason;
        GateState(long allowedAt,String reason,long windowStart,int used,long lastRequestAt,long circuitUntil){this.allowedAt=allowedAt;this.reason=reason;this.windowStart=windowStart;this.used=used;this.lastRequestAt=lastRequestAt;this.circuitUntil=circuitUntil;}
        public boolean waiting(long now){return allowedAt>now;}
    }

    private static final class Permit {
        final boolean granted; final GateState state;
        Permit(boolean granted,GateState state){this.granted=granted;this.state=state;}
    }

    public static final class RateLimitedException extends java.io.IOException {
        public final long retryAt; public final String reason;
        public RateLimitedException(String message,long retryAt,String reason){super(message);this.retryAt=retryAt;this.reason=reason==null?"":reason;}
        public RateLimitedException(String message){this(message,0L,"");}
    }
    public static final class Response {public final int code;public final String body;Response(int code,String body){this.code=code;this.body=body==null?"":body;}}

    private final Context context;
    private final DealDatabase helper;
    public VintedPublicSession(Context context){this.context=context.getApplicationContext();this.helper=new DealDatabase(this.context);}

    /** Generic/private API traffic is intentionally unavailable. */
    public Response get(String url)throws Exception{throw new java.io.IOException("API Vinted generiche disattivate");}

    /**
     * Reads one public HTTPS page from a narrow allow-list. Cached responses cost no permit.
     * Short inter-request pacing is waited out inside the current request; hourly/remote limits are
     * returned immediately to the durable queue so the lane can expose an honest resume time.
     */
    public Response getPublic(String url)throws Exception{return getPublic(url,"unspecified");}

    public Response getPublic(String url,String purpose)throws Exception{
        URL parsed=validate(url);
        purpose=normalizePurpose(purpose);
        long now=System.currentTimeMillis();
        CachedResponse cached=PUBLIC_CACHE.get(url);
        if(cached!=null&&now-cached.at<PUBLIC_CACHE_MS){
            SharedPreferences d=diag();d.edit().putLong("vintedPublicCacheHits",d.getLong("vintedPublicCacheHits",0)+1).apply();
            recordLedger(parsed,purpose,true,cached.response.code);
            return cached.response;
        }

        while(true){
            Permit permit=reservePermit();
            if(permit.granted)break;
            long wait=Math.max(1L,permit.state.allowedAt-System.currentTimeMillis());
            mirrorWait(permit.state);
            if("PACING".equals(permit.state.reason)&&wait<=PUBLIC_MIN_INTERVAL_MS+2_000L){
                try{Thread.sleep(wait);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.io.IOException("richiesta interrotta",e);}continue;
            }
            String message="REMOTE_LIMIT".equals(permit.state.reason)?"Vinted ha chiesto di rallentare le richieste":"Ludo Scout distribuisce le richieste Vinted nel tempo";
            throw new RateLimitedException(message,permit.state.allowedAt,permit.state.reason);
        }

        Response response;
        try{response=plainPublicGet(parsed);}catch(Exception e){diag().edit().putString("vintedPublicWaitReason","").apply();throw e;}
        long finished=System.currentTimeMillis();
        recordResponse(response.code,finished);
        SharedPreferences d=diag();
        d.edit().putLong("vintedPublicRequests",d.getLong("vintedPublicRequests",0L)+1L).putInt("vintedPublicLastCode",response.code).apply();
        recordLedger(parsed,purpose,false,response.code);

        if(response.code==403||response.code==429){
            long until=finished+LIMIT_COOLDOWN_MS;setCircuit(until,response.code);mirrorWait(gateState(context));
            throw new RateLimitedException("HTTP "+response.code+" · Vinted ha chiesto di rallentare",until,"REMOTE_LIMIT");
        }
        if(response.code==200){
            d.edit().putString("vintedPublicWaitReason","").putLong("vintedPublicLocalBudgetUntil",0L).apply();
            PUBLIC_CACHE.put(url,new CachedResponse(response,finished));
        }
        return response;
    }

    private static String normalizePurpose(String purpose){
        if(purpose==null||purpose.trim().isEmpty())return "unspecified";
        String p=purpose.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]","_");
        return p.length()>48?p.substring(0,48):p;
    }

    private static String routeKind(URL url){
        String p=url==null||url.getPath()==null?"":url.getPath();
        if(p.equals("/catalog")||p.startsWith("/catalog/"))return "catalog";
        if(p.startsWith("/items/"))return "item";
        if(p.startsWith("/member/"))return "member";
        return "other";
    }

    private void recordLedger(URL url,String purpose,boolean cacheHit,int code){
        SQLiteDatabase db=helper.getWritableDatabase();long now=System.currentTimeMillis();
        try{
            db.beginTransactionNonExclusive();ensureLedgerEpoch(db,now);
            String route=routeKind(url);String transport=cacheHit?"cache":"physical";
            incLedger(db,"calls",now);incLedger(db,transport,now);incLedger(db,"route:"+route+":"+transport,now);
            incLedger(db,"purpose:"+purpose+":"+transport,now);
            if(purpose.startsWith("link_"))incLedger(db,"link:"+transport,now);
            else if(purpose.startsWith("bundle_"))incLedger(db,"bundle:"+transport,now);
            if(!cacheHit){incLedger(db,"http:"+code,now);if(code==403)incLedger(db,"http403",now);if(code==429)incLedger(db,"http429",now);}
            putControl(db,LEDGER_PREFIX+"last",cacheHit?1L:0L,now,purpose+"|"+route+"|"+code+"|"+transport);
            db.setTransactionSuccessful();
        }catch(Throwable ignored){}finally{try{if(db.inTransaction())db.endTransaction();}catch(Throwable ignored){}}
    }

    /** Photo traffic is separate from the public-page budget and historical ledger.
     * Bounded keys, no URLs/titles or permission/retry changes. */
    public static void recordPhotoMatcherEvent(Context context,String event,int code){
        if(context==null||!("cache".equals(event)||"download".equals(event)||"http".equals(event)||"error".equals(event)))return;
        DealDatabase helper=null;SQLiteDatabase db=null;long now=System.currentTimeMillis();
        try{
            helper=new DealDatabase(context.getApplicationContext());db=helper.getWritableDatabase();
            db.beginTransactionNonExclusive();ensureLedgerEpoch(db,now);
            incLedger(db,"photo:"+event,now);
            if("http".equals(event)){
                String outcome=code==403?"403":code==429?"429":code>=200&&code<400?"ok":"other";
                incLedger(db,"photo:http:"+outcome,now);
            }
            putControl(db,LEDGER_PREFIX+"photo:last",code,now,event);
            db.setTransactionSuccessful();
        }catch(Throwable ignored){}
        finally{
            try{if(db!=null&&db.inTransaction())db.endTransaction();}catch(Throwable ignored){}
            try{if(helper!=null)helper.close();}catch(Throwable ignored){}
        }
    }

    /** Records a newly established exact Vinted identity. Unlike the old active-row delta,
     * this counter is monotonic inside the current ledger epoch and is not reduced when sold rows
     * later leave the active catalog. */
    public static void recordResolvedLink(SQLiteDatabase db,String source){
        if(db==null)return;long now=System.currentTimeMillis();boolean own=!db.inTransaction();
        try{
            if(own)db.beginTransactionNonExclusive();
            ensureLedgerEpoch(db,now);
            incLedger(db,"link:resolved",now);
            putControl(db,LEDGER_PREFIX+"last_resolved",1L,now,source==null?"":source);
            if(own)db.setTransactionSuccessful();
        }catch(Throwable ignored){}finally{if(own)try{if(db.inTransaction())db.endTransaction();}catch(Throwable ignored){}}
    }

    private static void ensureLedgerEpoch(SQLiteDatabase db,long now){
        String marker=controlText(db,LEDGER_PREFIX+"build");
        if(LEDGER_BUILD.equals(marker))return;
        db.delete("queue_controls","name LIKE ?",new String[]{LEDGER_PREFIX+"%"});
        long linked=0L;try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM market_listings WHERE vinted_item_id IS NOT NULL AND vinted_item_id<>''",null)){if(c.moveToFirst())linked=c.getLong(0);}catch(Throwable ignored){}
        putControl(db,LEDGER_PREFIX+"build",1L,now,LEDGER_BUILD);putControl(db,LEDGER_PREFIX+"start_at",now,now,null);putControl(db,LEDGER_PREFIX+"start_linked",linked,now,null);
    }

    private static void incLedger(SQLiteDatabase db,String key,long now){long v=control(db,LEDGER_PREFIX+key);putControl(db,LEDGER_PREFIX+key,v+1L,now,null);}
    private static String controlText(SQLiteDatabase db,String name){try(Cursor c=db.rawQuery("SELECT text_value FROM queue_controls WHERE name=? LIMIT 1",new String[]{name})){return c.moveToFirst()&&!c.isNull(0)?c.getString(0):"";}catch(Throwable ignored){return"";}}

    private static SQLiteDatabase ledgerReadDb(DealDatabase helper,long now){
        SQLiteDatabase db=helper.getReadableDatabase();
        if(LEDGER_BUILD.equals(controlText(db,LEDGER_PREFIX+"build")))return db;
        SQLiteDatabase writable=null;
        try{
            writable=helper.getWritableDatabase();
            writable.beginTransactionNonExclusive();
            ensureLedgerEpoch(writable,now);
            writable.setTransactionSuccessful();
            return writable;
        }catch(Throwable ignored){
            return db;
        }finally{
            try{if(writable!=null&&writable.inTransaction())writable.endTransaction();}catch(Throwable ignored){}
        }
    }

    public static final class LedgerSnapshot {
        public long physical,cacheHits,linkPhysical,linkCacheHits,bundlePhysical,catalogPhysical,itemPhysical,linkedNow,resolvedLinks;
    }

    /** Small numeric snapshot used by Test 2b to measure exactly one resolver invocation. */
    public static LedgerSnapshot requestLedgerSnapshot(Context context){
        LedgerSnapshot out=new LedgerSnapshot();DealDatabase h=new DealDatabase(context.getApplicationContext());SQLiteDatabase db=null;
        try{
            long now=System.currentTimeMillis();db=ledgerReadDb(h,now);
            out.physical=control(db,LEDGER_PREFIX+"physical");out.cacheHits=control(db,LEDGER_PREFIX+"cache");
            out.linkPhysical=control(db,LEDGER_PREFIX+"link:physical");out.linkCacheHits=control(db,LEDGER_PREFIX+"link:cache");
            out.bundlePhysical=control(db,LEDGER_PREFIX+"bundle:physical");
            out.catalogPhysical=control(db,LEDGER_PREFIX+"route:catalog:physical");out.itemPhysical=control(db,LEDGER_PREFIX+"route:item:physical");
            out.resolvedLinks=control(db,LEDGER_PREFIX+"link:resolved");
            try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM market_listings WHERE vinted_item_id IS NOT NULL AND vinted_item_id<>''",null)){if(c.moveToFirst())out.linkedNow=c.getLong(0);}
        }catch(Throwable ignored){}finally{try{h.close();}catch(Throwable ignored){}}
        return out;
    }

    /** Human-readable, SQLite-authoritative summary used by Copy diagnostics for Test 2. */
    public static String requestLedgerSummary(Context context){
        DealDatabase h=new DealDatabase(context.getApplicationContext());SQLiteDatabase db=null;
        try{
            long now=System.currentTimeMillis();db=ledgerReadDb(h,now);
            if(!LEDGER_BUILD.equals(controlText(db,LEDGER_PREFIX+"build")))return "build="+LEDGER_BUILD+", state=INITIALIZING";
            long startAt=control(db,LEDGER_PREFIX+"start_at"),startLinked=control(db,LEDGER_PREFIX+"start_linked");
            long linkedNow=0L;try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM market_listings WHERE vinted_item_id IS NOT NULL AND vinted_item_id<>''",null)){if(c.moveToFirst())linkedNow=c.getLong(0);}catch(Throwable ignored){}
            long activeDelta=Math.max(0L,linkedNow-startLinked),resolvedLinks=control(db,LEDGER_PREFIX+"link:resolved"),linkPhysical=control(db,LEDGER_PREFIX+"link:physical"),bundlePhysical=control(db,LEDGER_PREFIX+"bundle:physical"),physical=control(db,LEDGER_PREFIX+"physical"),cache=control(db,LEDGER_PREFIX+"cache");
            String ratio=resolvedLinks>0?String.format(Locale.US,"%.2f",linkPhysical/(double)resolvedLinks):"n/a";
            return "build="+LEDGER_BUILD+", ageMs="+(startAt<=0?-1:Math.max(0L,now-startAt))+", physical="+physical+", cacheHits="+cache+", linkPhysical="+linkPhysical+", linkCacheHits="+control(db,LEDGER_PREFIX+"link:cache")+", bundlePhysical="+bundlePhysical+", catalogPhysical="+control(db,LEDGER_PREFIX+"route:catalog:physical")+", itemPhysical="+control(db,LEDGER_PREFIX+"route:item:physical")+", http200="+control(db,LEDGER_PREFIX+"http:200")+", http403="+control(db,LEDGER_PREFIX+"http403")+", http429="+control(db,LEDGER_PREFIX+"http429")+", linkedStart="+startLinked+", linkedNow="+linkedNow+", activeLinkedDelta="+activeDelta+", resolvedLinks="+resolvedLinks+", linkRequestsPerNewLink="+ratio+", photoMatcher={build=photo-cache-reuse-v1;cacheHits="+control(db,LEDGER_PREFIX+"photo:cache")+";downloadAttempts="+control(db,LEDGER_PREFIX+"photo:download")+";httpResponses="+control(db,LEDGER_PREFIX+"photo:http")+";http403="+control(db,LEDGER_PREFIX+"photo:http:403")+";http429="+control(db,LEDGER_PREFIX+"photo:http:429")+";errors="+control(db,LEDGER_PREFIX+"photo:error")+";scope=matcher-only;separateFromPageBudget=true}, last="+controlText(db,LEDGER_PREFIX+"last");
        }catch(Throwable t){return "error="+t.getClass().getSimpleName();}
        finally{try{h.close();}catch(Throwable ignored){}}
    }

    /** Earliest authoritative request time shared by all Ludo Scout processes. */
    public static long nextAllowedAt(Context context){return gateState(context).allowedAt;}
    public static long waitUntil(Context context){GateState g=gateState(context);return g.allowedAt>System.currentTimeMillis()?g.allowedAt:0L;}
    public static String waitReason(Context context){GateState g=gateState(context);return g.allowedAt>System.currentTimeMillis()?g.reason:"";}
    public static int hourlyUsed(Context context){return gateState(context).used;}
    public static int hourlyBudget(){return PUBLIC_HOURLY_BUDGET;}

    public static GateState gateState(Context context){
        Context app=context.getApplicationContext();DealDatabase h=new DealDatabase(app);long now=System.currentTimeMillis();
        try{SQLiteDatabase db=h.getReadableDatabase();return readGate(db,now);}
        catch(Throwable ignored){return new GateState(now+1_500L,"COORDINATOR_BUSY",0L,0,0L,0L);}
        finally{try{h.close();}catch(Throwable ignored){}}
    }

    /** Bulk maintenance is intentionally slower than the hard hourly ceiling. */
    public static long recommendedBulkGapMs(){return recommendedBulkGapMs(1);}
    public static long recommendedBulkGapMs(int expectedRequests){long exact=(60*60_000L)/Math.max(1,PUBLIC_HOURLY_BUDGET);return Math.max(45_000L,exact+5_000L)*Math.max(1,expectedRequests);}

    private Permit reservePermit(){
        SQLiteDatabase db=helper.getWritableDatabase();long now=System.currentTimeMillis();
        try{
            db.beginTransactionNonExclusive();
            GateState state=readGate(db,now);
            if(state.allowedAt>now){db.setTransactionSuccessful();return new Permit(false,state);}
            long window=state.windowStart;int used=state.used;
            if(window==0L||now-window>=60*60_000L){window=now;used=0;}
            putControl(db,K_WINDOW,window,now,null);putControl(db,K_USED,used+1L,now,null);putControl(db,K_LAST,now,now,null);
            db.setTransactionSuccessful();
            return new Permit(true,new GateState(0L,"",window,used+1,now,state.circuitUntil));
        }catch(Throwable ignored){return new Permit(false,new GateState(now+1_500L,"COORDINATOR_BUSY",0L,0,0L,0L));}
        finally{try{if(db.inTransaction())db.endTransaction();}catch(Throwable ignored){}}
    }

    private static GateState readGate(SQLiteDatabase db,long now){
        long last=control(db,K_LAST),window=control(db,K_WINDOW),circuit=control(db,K_CIRCUIT);int used=(int)Math.max(0,control(db,K_USED));
        if(window==0L||now-window>=60*60_000L){window=now;used=0;}
        long allowed=0L;String reason="";
        if(circuit>now&&circuit>allowed){allowed=circuit;reason="REMOTE_LIMIT";}
        long pacing=last<=0L?0L:last+PUBLIC_MIN_INTERVAL_MS;
        if(pacing>now&&pacing>allowed){allowed=pacing;reason="PACING";}
        if(used>=PUBLIC_HOURLY_BUDGET){long budgetUntil=window+60*60_000L;if(budgetUntil>allowed){allowed=budgetUntil;reason="LOCAL_BUDGET";}}
        return new GateState(allowed,reason,window,used,last,circuit);
    }

    private void recordResponse(int code,long now){
        SQLiteDatabase db=helper.getWritableDatabase();try{db.beginTransactionNonExclusive();putControl(db,K_LAST_CODE,code,now,null);db.setTransactionSuccessful();}catch(Throwable ignored){}finally{try{if(db.inTransaction())db.endTransaction();}catch(Throwable ignored){}}
    }
    private void setCircuit(long until,int code){
        SQLiteDatabase db=helper.getWritableDatabase();long now=System.currentTimeMillis();try{db.beginTransactionNonExclusive();putControl(db,K_CIRCUIT,until,now,"REMOTE_LIMIT");putControl(db,K_LAST_CODE,code,now,null);db.setTransactionSuccessful();}catch(Throwable ignored){}finally{try{if(db.inTransaction())db.endTransaction();}catch(Throwable ignored){}}
    }

    private static long control(SQLiteDatabase db,String name){try(Cursor c=db.rawQuery("SELECT value FROM queue_controls WHERE name=? LIMIT 1",new String[]{name})){return c.moveToFirst()?c.getLong(0):0L;}}
    private static void putControl(SQLiteDatabase db,String name,long value,long updatedAt,String text){ContentValues v=new ContentValues();v.put("name",name);v.put("value",value);v.put("updated_at",updatedAt);if(text==null)v.putNull("text_value");else v.put("text_value",text);db.insertWithOnConflict("queue_controls",null,v,SQLiteDatabase.CONFLICT_REPLACE);}

    private void mirrorWait(GateState g){SharedPreferences.Editor e=diag().edit().putString("vintedPublicWaitReason",g.reason).putLong("vintedPublicLocalBudgetUntil","LOCAL_BUDGET".equals(g.reason)?g.allowedAt:0L).putLong("vintedPublicCircuitUntil",g.circuitUntil).putInt("vintedPublicWindowCount",g.used).putLong("vintedPublicWindowStart",g.windowStart);e.apply();}

    private URL validate(String raw)throws Exception{
        URL parsed=new URL(raw);String host=parsed.getHost()==null?"":parsed.getHost().toLowerCase(Locale.ROOT);
        if(!"https".equalsIgnoreCase(parsed.getProtocol())||!(host.equals("vinted.it")||host.endsWith(".vinted.it")))throw new java.io.IOException("host pubblico non consentito");
        String path=parsed.getPath()==null?"":parsed.getPath();if(!(path.startsWith("/items/")||path.startsWith("/member/")||path.equals("/catalog")||path.startsWith("/catalog/")))throw new java.io.IOException("route pubblica non consentita");return parsed;
    }

    private Response plainPublicGet(URL url)throws Exception{
        HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(9_000);c.setReadTimeout(14_000);c.setInstanceFollowRedirects(true);c.setRequestProperty("User-Agent",browserUserAgent());c.setRequestProperty("Accept","text/html,application/xhtml+xml,application/json;q=0.8,*/*;q=0.5");c.setRequestProperty("Accept-Language","it-IT,it;q=0.9,en;q=0.6");int code=c.getResponseCode();InputStream in=(code>=200&&code<400)?c.getInputStream():c.getErrorStream();String body=read(in);c.disconnect();diag().edit().putInt("vintedHtmlLastCode",code).putString("vintedSessionMode","public-pages-sqlite-coordinated-no-cookies").apply();return new Response(code,body);
    }
    private SharedPreferences diag(){return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    private static String browserUserAgent(){return "Mozilla/5.0 (Linux; Android "+Build.VERSION.RELEASE+"; "+Build.MODEL+") AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36";}
    private static String read(InputStream in)throws Exception{if(in==null)return"";final int maxChars=4_000_000;try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){StringBuilder b=new StringBuilder(Math.min(256_000,maxChars));char[] chunk=new char[8192];int n;while((n=br.read(chunk))!=-1){int room=maxChars-b.length();if(room<=0)break;b.append(chunk,0,Math.min(room,n));if(n>room)break;}return b.toString();}}
}
