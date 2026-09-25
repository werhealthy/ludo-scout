package it.vintedaffari.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Persistent candidate-snapshot store plus offline diagnostics. Capture is passive: it only records
 * catalogue pages that the normal resolver has already downloaded and never performs HTTP itself.
 * Production VintedBatchEngine may consume these snapshots later; the diagnostic replay below stays
 * read-only and is retained to audit matching quality after upgrades.
 */
public final class VintedCandidateSnapshotStore {
    private VintedCandidateSnapshotStore() {}

    private static final String BUILD="candidate-snapshot-store-v1";
    private static final int MAX_CANDIDATES=100;
    private static final Pattern ITEM_LINK=Pattern.compile("/items/(\\d+)-([^\\\"'<>?#]+)",Pattern.CASE_INSENSITIVE);
    private static final Pattern PRICE_DECIMAL=Pattern.compile("(?<![0-9])([0-9]{1,5})[.,]([0-9]{2})(?![0-9])");
    private static final Pattern VINTED_IMAGE=Pattern.compile("https?://images(?:\\d+)?\\.vinted\\.(?:net|com)/[^\\s<>\"']+",Pattern.CASE_INSENSITIVE);

    private static final class C {
        String id,title,brand,image; int priceCents=Integer.MIN_VALUE; final java.util.LinkedHashSet<Integer> priceHints=new java.util.LinkedHashSet<>();
    }
    private static final class LinkPos { String id,title; int start,end; LinkPos(String i,String t,int s,int e){id=i;title=t;start=s;end=e;} }
    private static final class FamilyStat {
        String label; int observations,candidates,strict,ambiguous,noMatch,titleOnly;
    }

    /** Compact durable catalogue candidate exposed to the production resolver.
     * Reading this structure never performs HTTP. */
    public static final class SnapshotCandidate {
        public final String id,title,brand,image;
        public final int priceCents;
        public final List<Integer> priceHints;
        SnapshotCandidate(C c){
            id=c.id;title=c.title;brand=c.brand;image=c.image;priceCents=c.priceCents;
            priceHints=Collections.unmodifiableList(new ArrayList<>(c.priceHints));
        }
        public int bestPriceDiff(int observedPrice){
            int best=Integer.MAX_VALUE;
            if(priceCents!=Integer.MIN_VALUE)best=Math.min(best,Math.abs(priceCents-observedPrice));
            for(Integer p:priceHints)if(p!=null)best=Math.min(best,Math.abs(p-observedPrice));
            return best;
        }
    }

    public static final class SearchSnapshot {
        public final long lastAt;
        public final List<SnapshotCandidate> candidates;
        SearchSnapshot(long lastAt,List<SnapshotCandidate> candidates){
            this.lastAt=lastAt;this.candidates=Collections.unmodifiableList(candidates);
        }
    }

    /** Returns a recent catalogue response captured by an earlier normal resolver request.
     * A snapshot may substitute the catalogue search only when it is not materially older than
     * the listing observation. Exact item-page verification remains the resolver's responsibility. */
    public static SearchSnapshot recentSearch(Context context,String query,long observedAt,long maxAgeMs){
        if(context==null||TextUtils.isEmpty(query))return null;
        DealDatabase helper=new DealDatabase(context.getApplicationContext());
        try{
            // Read-only reuse must never join the cross-process writer queue. The table is created
            // by capture(); if it does not exist yet this lookup simply falls back to normal HTTP.
            SQLiteDatabase db=helper.getReadableDatabase();
            String key=norm(query);if(TextUtils.isEmpty(key))return null;
            long now=System.currentTimeMillis(),age=Math.max(60_000L,maxAgeMs);
            try(Cursor cur=db.rawQuery("SELECT last_at,payload FROM vinted_shadow_snapshots_v3 WHERE query_key=? AND last_at>=? LIMIT 1",
                    new String[]{key,String.valueOf(now-age)})){
                if(!cur.moveToFirst())return null;
                long lastAt=cur.getLong(0);
                if(observedAt>0&&lastAt+60_000L<observedAt)return null;
                List<C> decoded=decode(cur.getString(1));if(decoded.isEmpty())return null;
                List<SnapshotCandidate> out=new ArrayList<>();
                for(C c:decoded)if(c!=null&&!TextUtils.isEmpty(c.id))out.add(new SnapshotCandidate(c));
                return out.isEmpty()?null:new SearchSnapshot(lastAt,out);
            }
        }catch(Throwable ignored){return null;}
        finally{try{helper.close();}catch(Throwable ignored){}}
    }

    /** Called only after the normal resolver has already obtained a catalogue response. */
    public static void capture(Context context,String query,VintedStructuredData.Scan scan,String body) {
        if(context==null||TextUtils.isEmpty(query)||TextUtils.isEmpty(body))return;
        DealDatabase helper=new DealDatabase(context.getApplicationContext());
        SQLiteDatabase db=null;
        try{
            db=helper.getWritableDatabase();ensure(db);
            LinkedHashMap<String,C> all=new LinkedHashMap<>();
            int structured=0,links=0;
            if(scan!=null){
                for(JSONObject x:scan.items){
                    C c=fromStructured(x);if(c==null||TextUtils.isEmpty(c.id))continue;
                    if(!all.containsKey(c.id)){all.put(c.id,c);structured++;}
                    if(all.size()>=MAX_CANDIDATES)break;
                }
            }
            if(all.size()<MAX_CANDIDATES){
                String normalizedBody=body.replace("\\/","/").replace("\\u002F","/");
                List<LinkPos> positions=new ArrayList<>();
                Matcher m=ITEM_LINK.matcher(normalizedBody);
                while(m.find()){
                    String id=m.group(1);String title=m.group(2).replace('-',' ').replace('_',' ').trim();
                    positions.add(new LinkPos(id,title,m.start(),m.end()));
                    C c=all.get(id);if(c==null&&all.size()<MAX_CANDIDATES){c=new C();c.id=id;c.title=title;all.put(id,c);links++;}
                }
                // Test 6: isolate each link occurrence using the midpoint to the nearest DIFFERENT item
                // on either side. This avoids importing prices from adjacent cards. Repeated links to
                // the same item (image/title) are allowed to contribute to the same candidate.
                for(int i=0;i<positions.size();i++){
                    LinkPos cur=positions.get(i);C c=all.get(cur.id);if(c==null)continue;
                    int prev=-1,next=-1;
                    for(int j=i-1;j>=0;j--){if(!positions.get(j).id.equals(cur.id)){prev=positions.get(j).end;break;}}
                    for(int j=i+1;j<positions.size();j++){if(!positions.get(j).id.equals(cur.id)){next=positions.get(j).start;break;}}
                    int left=prev<0?Math.max(0,cur.start-900):Math.max(0,(prev+cur.start)/2);
                    int right=next<0?Math.min(normalizedBody.length(),cur.end+1400):Math.min(normalizedBody.length(),(cur.end+next)/2);
                    // Guard against huge gaps in page chrome/recommendation markup.
                    left=Math.max(left,Math.max(0,cur.start-900));
                    right=Math.min(right,Math.min(normalizedBody.length(),cur.end+1400));
                    if(right>left){
                        String local=normalizedBody.substring(left,right);
                        collectPriceHints(local,c.priceHints);
                        if(TextUtils.isEmpty(c.image))c.image=extractNearbyImage(local);
                    }
                }
            }
            JSONArray payload=new JSONArray();
            for(C c:all.values()){
                JSONObject o=new JSONObject();o.put("id",c.id);o.put("t",c.title==null?"":c.title);
                if(c.priceCents!=Integer.MIN_VALUE)o.put("pc",c.priceCents);
                if(!c.priceHints.isEmpty()){JSONArray ph=new JSONArray();int n=0;for(Integer cents:c.priceHints){if(cents==null)continue;ph.put(cents);if(++n>=24)break;}o.put("ph",ph);}
                if(!TextUtils.isEmpty(c.brand))o.put("b",c.brand);
                if(!TextUtils.isEmpty(c.image))o.put("img",c.image);
                payload.put(o);
            }
            String key=norm(query);if(TextUtils.isEmpty(key))return;
            long now=System.currentTimeMillis();long first=now;int captures=0;
            try(Cursor cur=db.rawQuery("SELECT first_at,capture_count FROM vinted_shadow_snapshots_v3 WHERE query_key=? LIMIT 1",new String[]{key})){
                if(cur.moveToFirst()){first=cur.getLong(0);captures=cur.getInt(1);}
            }
            ContentValues v=new ContentValues();v.put("query_key",key);v.put("query_text",query.trim());v.put("first_at",first);v.put("last_at",now);
            v.put("capture_count",captures+1);v.put("candidate_count",all.size());v.put("structured_count",structured);v.put("link_count",links);v.put("payload",payload.toString());
            db.insertWithOnConflict("vinted_shadow_snapshots_v3",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        }catch(Throwable ignored){}finally{try{helper.close();}catch(Throwable ignored){}}
    }

    /** Lightweight production diagnostics. The old shadow replay decoded every saved candidate
     * payload and could temporarily allocate tens of thousands of objects on a mature backlog. */
    public static String summary(Context context){
        DealDatabase helper=new DealDatabase(context.getApplicationContext());
        try{
            SQLiteDatabase db=helper.getWritableDatabase();ensure(db);VintedPhotoHashCache.ensure(db);long now=System.currentTimeMillis();
            int snapshots=0,captures=0,candidates=0,structured=0;long newest=0L;
            try(Cursor c=db.rawQuery("SELECT COUNT(*),COALESCE(SUM(capture_count),0),COALESCE(SUM(candidate_count),0),COALESCE(SUM(structured_count),0),COALESCE(MAX(last_at),0) FROM vinted_shadow_snapshots_v3",null)){
                if(c.moveToFirst()){snapshots=c.getInt(0);captures=c.getInt(1);candidates=c.getInt(2);structured=c.getInt(3);newest=c.getLong(4);}
            }
            int eligible=0;
            try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM market_listings l JOIN games g ON g.id=l.game_id WHERE l.lifecycle='ACTIVE' AND (l.vinted_url IS NULL OR l.vinted_url='') AND g.database_visible=1 AND g.rating>=?",new String[]{String.valueOf(DealPolicy.MIN_BGG_RATING)})){if(c.moveToFirst())eligible=c.getInt(0);}
            return "build="+BUILD+", zeroExtraNetwork=true, snapshots="+snapshots+", captures="+captures+", candidates="+candidates+", structuredCandidates="+structured+", newestAgeMs="+(newest<=0?-1:Math.max(0L,now-newest))+", eligibleObservations="+eligible+", cachedPhotoHashes="+VintedPhotoHashCache.uniqueCount(db)+", photoHashHarvests="+VintedPhotoHashCache.captureCount(db);
        }catch(Throwable t){return "build="+BUILD+", error="+t.getClass().getSimpleName()+":"+safe(t.getMessage());}
        finally{try{helper.close();}catch(Throwable ignored){}}
    }

    private static void ensure(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS vinted_shadow_snapshots_v3(query_key TEXT PRIMARY KEY,query_text TEXT NOT NULL,first_at INTEGER NOT NULL,last_at INTEGER NOT NULL,capture_count INTEGER NOT NULL DEFAULT 1,candidate_count INTEGER NOT NULL DEFAULT 0,structured_count INTEGER NOT NULL DEFAULT 0,link_count INTEGER NOT NULL DEFAULT 0,payload TEXT NOT NULL)");
    }

    private static C fromStructured(JSONObject x){
        if(x==null)return null;String id=str(x.opt("id")),title=x.optString("title","");if(TextUtils.isEmpty(id)||!id.matches("\\d+")||TextUtils.isEmpty(title))return null;
        C c=new C();c.id=id;c.title=title;Double p=parseMoney(x.opt("price"));if(p!=null){c.priceCents=(int)Math.round(p*100.0);c.priceHints.add(c.priceCents);}
        c.brand=x.optString("brand_title","");if(TextUtils.isEmpty(c.brand)){JSONObject b=x.optJSONObject("brand_dto");if(b!=null)c.brand=b.optString("title","");}
        JSONObject photo=x.optJSONObject("photo");if(photo!=null)c.image=photo.optString("url",photo.optString("full_size_url",""));return c;
    }

    private static List<C> decode(String raw){
        List<C> out=new ArrayList<>();if(TextUtils.isEmpty(raw))return out;try{JSONArray a=new JSONArray(raw);for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;C c=new C();c.id=o.optString("id","");c.title=o.optString("t","");c.priceCents=o.has("pc")?o.optInt("pc",Integer.MIN_VALUE):Integer.MIN_VALUE;if(c.priceCents!=Integer.MIN_VALUE)c.priceHints.add(c.priceCents);JSONArray ph=o.optJSONArray("ph");if(ph!=null)for(int k=0;k<ph.length();k++){int v=ph.optInt(k,Integer.MIN_VALUE);if(v!=Integer.MIN_VALUE)c.priceHints.add(v);}c.brand=o.optString("b","");c.image=o.optString("img","");if(!TextUtils.isEmpty(c.id))out.add(c);}}catch(Throwable ignored){}return out;
    }

    private static int score(String observedTitle,int observedPrice,String observedBrand,C c){
        String a=norm(observedTitle),b=norm(c.title);int s=0;if(a.equals(b))s+=60;else{double j=jaccard(a,b);if(j>=.92)s+=50;else if(j>=.82)s+=40;else if(j>=.68)s+=26;else return 0;}
        int diff=bestPriceDiff(c,observedPrice);if(diff==Integer.MAX_VALUE)return 0;if(diff<=1)s+=32;else if(diff<=5)s+=26;else if(diff<=20)s+=10;else return 0;
        if(!TextUtils.isEmpty(observedBrand)&&!TextUtils.isEmpty(c.brand)){double j=jaccard(norm(observedBrand),norm(c.brand));if(j>=.90)s+=14;else if(j>=.62)s+=6;}return s;
    }

    private static boolean priceCompatible(C c,int observedPrice){return bestPriceDiff(c,observedPrice)<=5;}
    private static int bestPriceDiff(C c,int observedPrice){if(c==null)return Integer.MAX_VALUE;int best=Integer.MAX_VALUE;if(c.priceCents!=Integer.MIN_VALUE)best=Math.min(best,Math.abs(c.priceCents-observedPrice));for(Integer v:c.priceHints)if(v!=null)best=Math.min(best,Math.abs(v-observedPrice));return best;}
    private static String extractNearbyImage(String html){if(TextUtils.isEmpty(html))return"";Matcher m=VINTED_IMAGE.matcher(html.replace("\\u002F","/").replace("\\/","/"));return m.find()?m.group().replace("&amp;","&"):"";}
    private static void collectPriceHints(String near,java.util.Set<Integer> out){
        if(TextUtils.isEmpty(near)||out==null)return;Matcher m=PRICE_DECIMAL.matcher(near);while(m.find()&&out.size()<24){try{int whole=Integer.parseInt(m.group(1)),frac=Integer.parseInt(m.group(2));int cents=whole*100+frac;if(cents>=50&&cents<=500000)out.add(cents);}catch(Exception ignored){}}
    }

    private static Double parseMoney(Object o){try{if(o==null||o==JSONObject.NULL)return null;if(o instanceof Number)return((Number)o).doubleValue();if(o instanceof JSONObject){JSONObject j=(JSONObject)o;String a=j.optString("amount","");if(!a.isEmpty())return Double.parseDouble(a.replace(',','.'));}String s=String.valueOf(o).replace("€","").replace(" ","").trim().replace(',','.');return Double.parseDouble(s);}catch(Exception e){return null;}}
    private static String str(Object o){return o==null||o==JSONObject.NULL?"":String.valueOf(o);}
    private static String norm(String s){if(s==null)return"";String n=Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}+","");return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim().replaceAll("\\s+"," ");}
    private static double jaccard(String a,String b){if(a.isEmpty()||b.isEmpty())return 0;java.util.Set<String>A=new java.util.HashSet<>(java.util.Arrays.asList(a.split(" ")));java.util.Set<String>B=new java.util.HashSet<>(java.util.Arrays.asList(b.split(" ")));java.util.Set<String>I=new java.util.HashSet<>(A);I.retainAll(B);java.util.Set<String>U=new java.util.HashSet<>(A);U.addAll(B);return U.isEmpty()?0:(double)I.size()/U.size();}
    private static double containment(String a,String b){if(a.isEmpty()||b.isEmpty())return 0;java.util.Set<String>A=new java.util.HashSet<>(java.util.Arrays.asList(a.split(" ")));java.util.Set<String>B=new java.util.HashSet<>(java.util.Arrays.asList(b.split(" ")));java.util.Set<String>I=new java.util.HashSet<>(A);I.retainAll(B);int den=Math.min(A.size(),B.size());return den<=0?0:(double)I.size()/den;}
    private static String fmt2(double v){return String.format(Locale.US,"%.2f",v);}
    private static String cleanLong(String s,int max){if(s==null)return"";String x=s.replace('\n',' ').replace('\r',' ').replace(';',',').replace('|','/').trim();return x.length()>max?x.substring(0,Math.max(1,max-3))+"...":x;}
    private static String clean(String s){if(s==null)return"";String x=s.replace('\n',' ').replace('\r',' ').replace(';',',').trim();return x.length()>32?x.substring(0,29)+"...":x;}
    private static String pct(int a,int b){return b<=0?"0.0%":String.format(Locale.US,"%.1f%%",100.0*a/b);}
    private static String safe(String s){return s==null?"":s.replace('\n',' ').replace('\r',' ');}
}
