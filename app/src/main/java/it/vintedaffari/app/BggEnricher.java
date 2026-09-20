package it.vintedaffari.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** BGG XML API2 enrichment with one shared rate limiter and HTTP batches of up to 20 ids. */
public final class BggEnricher {
    private static final String TAG="BggEnricher";
    private static final String PREFS="va_v3_diag";
    private static final int MAX_BATCH=20;
    private final Context context;
    private final DealDatabase db;
    private final MarketStore market;
    private final ExecutorService exec=Executors.newSingleThreadExecutor();
    private final Set<String> scheduled=java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private final Map<String,Long> failedUntil=java.util.Collections.synchronizedMap(new java.util.HashMap<>());

    public static final class BatchOutcome {
        public final Set<String> completed=new LinkedHashSet<>();
        public final Map<String,String> failed=new LinkedHashMap<>();
        public int httpCode;
    }

    public BggEnricher(Context c,DealDatabase db){this(c,db,null);}
    public BggEnricher(Context c,DealDatabase db,MarketStore market){this.context=c.getApplicationContext();this.db=db;this.market=market;}
    public boolean configured(){return true;}

    public void enrich(String bggId){
        if(TextUtils.isEmpty(bggId)||!configured())return;
        Long deferred=failedUntil.get(bggId);if(deferred!=null&&deferred>System.currentTimeMillis())return;
        if(!scheduled.add(bggId))return;
        if(market!=null)market.markBggProcessing(bggId);
        String task="bgg-enrich:"+bggId;OperationCenter.queued(context,task,OperationCenter.MATCH,labelFor(bggId));
        exec.execute(()->{
            try{
                OperationCenter.running(context,task,OperationCenter.MATCH,labelFor(bggId));
                BatchOutcome out=enrichBatchBlocking(java.util.Collections.singletonList(bggId),null);
                if(!out.completed.contains(bggId))throw new IllegalStateException(out.failed.getOrDefault(bggId,"BGG senza risultato"));
                failedUntil.remove(bggId);
                if(market!=null)market.markBggComplete(bggId);
                OperationCenter.done(context,task,OperationCenter.MATCH,labelFor(bggId));
            }catch(Throwable t){
                long next=System.currentTimeMillis()+10*60_000L;failedUntil.put(bggId,next);
                if(market!=null)market.markBggRetry(bggId,safe(t.getMessage()),next);
                String message=t.getClass().getSimpleName()+": "+safe(t.getMessage());diag().edit().putString("bggLastError",message).apply();
                OperationCenter.error(context,task,OperationCenter.MATCH,labelFor(bggId),message);Log.w(TAG,"enrich failed "+bggId,t);
            }finally{scheduled.remove(bggId);context.sendBroadcast(new android.content.Intent(OperationCenter.CHANGED).setPackage(context.getPackageName()));}
        });
    }

    /** Synchronous single-id entry point kept for callers outside the batch queue. */
    void enrichBlocking(String bggId)throws Exception{enrichBlocking(bggId,null);}
    void enrichBlocking(String bggId,IntConsumer progress)throws Exception{
        if(TextUtils.isEmpty(bggId))throw new IllegalArgumentException("BGG id mancante");
        BatchOutcome out=enrichBatchBlocking(java.util.Collections.singletonList(bggId),progress);
        if(!out.completed.contains(bggId))throw new IllegalStateException(out.failed.getOrDefault(bggId,"BGG senza risultato"));
    }

    /**
     * One XML API2 /thing request can carry up to 20 comma-separated ids. The shared limiter still
     * spaces request starts by ~5.2 s, but one permit can now finish up to twenty durable jobs.
     */
    BatchOutcome enrichBatchBlocking(List<String> rawIds,IntConsumer progress)throws Exception{
        LinkedHashSet<String> unique=new LinkedHashSet<>();
        if(rawIds!=null)for(String id:rawIds)if(!TextUtils.isEmpty(id)&&unique.size()<MAX_BATCH)unique.add(id.trim());
        if(unique.isEmpty())throw new IllegalArgumentException("BGG ids mancanti");
        ArrayList<String> ids=new ArrayList<>(unique);
        if(progress!=null)progress.accept(24);
        int lastCode=0;
        for(int attempt=1;attempt<=4;attempt++){
            BggRateLimiter.acquire(context);
            if(progress!=null)progress.accept(32);
            BatchOutcome out=fetchBatchOnce(ids,progress);lastCode=out.httpCode;
            diag().edit().putInt("bggLastHttpCode",lastCode).putInt("bggBatchLastSize",ids.size()).putString("bggLastId",TextUtils.join(",",ids)).apply();
            if(lastCode==200)return out;
            if(lastCode!=202)throw new IllegalStateException("BGG HTTP "+lastCode);
            // acquire() on the next loop enforces the real 5.2 s request spacing. This short sleep
            // only avoids a hot loop while a 202 response is still being queued server-side.
            Thread.sleep(Math.min(1_500L,400L*attempt));
        }
        throw new IllegalStateException("BGG ancora in coda (HTTP "+lastCode+")");
    }

    public int scheduledCount(){return scheduled.size();}
    public boolean isScheduled(String bggId){return !TextUtils.isEmpty(bggId)&&scheduled.contains(bggId);}
    public boolean isDeferred(String bggId){Long until=failedUntil.get(bggId);return until!=null&&until>System.currentTimeMillis();}

    private BatchOutcome fetchBatchOnce(List<String> ids,IntConsumer progress)throws Exception{
        BatchOutcome out=new BatchOutcome();
        String token=BuildConfig.BGG_TOKEN;
        String joined=TextUtils.join(",",ids);
        HttpURLConnection c=(HttpURLConnection)new URL("https://boardgamegeek.com/xmlapi2/thing?id="+joined+"&stats=1&marketplace=1").openConnection();
        c.setConnectTimeout(9000);c.setReadTimeout(20000);
        if(!TextUtils.isEmpty(token)&&!"PASTE_YOUR_BGG_TOKEN_HERE".equals(token))c.setRequestProperty("Authorization","Bearer "+token);
        c.setRequestProperty("User-Agent","LudoScout/5.11 Android");c.setRequestProperty("Accept","application/xml,text/xml,*/*");
        int code=c.getResponseCode();out.httpCode=code;if(progress!=null)progress.accept(48);
        if(code!=200){c.disconnect();return out;}
        LinkedHashSet<String> requested=new LinkedHashSet<>(ids);
        int applied=0;
        try(InputStream in=c.getInputStream()){
            Document doc=SafeXml.parse(in);if(progress!=null)progress.accept(62);
            NodeList items=doc.getElementsByTagName("item");
            for(int i=0;i<items.getLength();i++){
                if(!(items.item(i) instanceof Element))continue;Element item=(Element)items.item(i);
                String id=item.getAttribute("id");if(TextUtils.isEmpty(id)||!requested.contains(id))continue;
                try{
                    BggMetadata m=parseMetadata(id,item);
                    db.applyBggEnrichment(m);
                    if(market!=null)market.applyBggMetadata(m);
                    for(DealRecord deal:db.getDealsByBggId(id,120))DealAlertNotifier.evaluateAndNotify(context,deal);
                    String art=!TextUtils.isEmpty(m.imageUrl)?m.imageUrl:m.thumbnailUrl;if(!TextUtils.isEmpty(art))ArtworkStore.downloadBgg(context,id,art);
                    out.completed.add(id);applied++;
                }catch(Throwable t){out.failed.put(id,safe(t.getClass().getSimpleName()+": "+t.getMessage()));}
            }
            for(String id:ids)if(!out.completed.contains(id)&&!out.failed.containsKey(id))out.failed.put(id,"BGG 200 senza item");
            if(progress!=null)progress.accept(92);
            SharedPreferences p=diag();
            p.edit().putLong("bggEnriched",p.getLong("bggEnriched",0)+applied)
                    .putLong("bggBatchRequests",p.getLong("bggBatchRequests",0)+1)
                    .putLong("bggBatchItems",p.getLong("bggBatchItems",0)+applied)
                    .putString("bggLastError",out.failed.isEmpty()?"":out.failed.values().iterator().next()).apply();
            context.sendBroadcast(new android.content.Intent("it.vintedaffari.app.DEALS_UPDATED").setPackage(context.getPackageName()));
        }finally{c.disconnect();}
        return out;
    }

    private static BggMetadata parseMetadata(String bggId,Element item){
        BggMetadata m=new BggMetadata();m.bggId=bggId;m.thumbnailUrl=text(item,"thumbnail");m.imageUrl=text(item,"image");m.description=text(item,"description");
        m.year=intAttr(item,"yearpublished");m.minPlayers=intAttr(item,"minplayers");m.maxPlayers=intAttr(item,"maxplayers");m.playtime=intAttr(item,"playingtime");m.minAge=intAttr(item,"minage");
        LinkedHashSet<String> alt=new LinkedHashSet<>();NodeList names=item.getElementsByTagName("name");
        for(int i=0;i<names.getLength();i++){Element e=(Element)names.item(i);String v=e.getAttribute("value");if(TextUtils.isEmpty(v))continue;if("primary".equals(e.getAttribute("type"))&&TextUtils.isEmpty(m.name))m.name=v;else alt.add(v);}
        m.alternateNames=join(alt," | ");
        LinkedHashSet<String> cat=new LinkedHashSet<>(),mech=new LinkedHashSet<>(),des=new LinkedHashSet<>(),art=new LinkedHashSet<>(),pub=new LinkedHashSet<>(),fam=new LinkedHashSet<>(),exp=new LinkedHashSet<>(),base=new LinkedHashSet<>();
        NodeList links=item.getElementsByTagName("link");for(int i=0;i<links.getLength();i++){Element e=(Element)links.item(i);String type=e.getAttribute("type"),v=e.getAttribute("value");if(TextUtils.isEmpty(v))continue;
            if("boardgamecategory".equals(type))cat.add(v);else if("boardgamemechanic".equals(type))mech.add(v);else if("boardgamedesigner".equals(type))des.add(v);else if("boardgameartist".equals(type))art.add(v);else if("boardgamepublisher".equals(type))pub.add(v);else if("boardgamefamily".equals(type))fam.add(v);else if("boardgameexpansion".equals(type)){if("true".equalsIgnoreCase(e.getAttribute("inbound")))base.add(v);else exp.add(v);}}
        m.categories=join(cat," · ");m.mechanics=join(mech," · ");m.designers=join(des," · ");m.artists=join(art," · ");m.publishers=join(pub," · ");m.families=join(fam," · ");m.expansions=join(exp," · ");m.baseGames=join(base," · ");
        Element ratings=first(item,"ratings");if(ratings!=null){m.rating=doubleAttr(ratings,"average");m.voters=intAttr(ratings,"usersrated");m.weight=doubleAttr(ratings,"averageweight");NodeList ranks=ratings.getElementsByTagName("rank");for(int i=0;i<ranks.getLength();i++){Element r=(Element)ranks.item(i);if("boardgame".equals(r.getAttribute("name"))){try{m.rank=Integer.valueOf(r.getAttribute("value"));}catch(Exception ignored){}break;}}}
        applyMarketplace(m,item);
        return m;
    }

    /** XML API2 marketplace snapshot. We only mix current EUR listings marked as non-new; this is
     * informational/current-market evidence, never the single source of truth for language-dependent games. */
    private static void applyMarketplace(BggMetadata m,Element item){
        ArrayList<Integer> used=new ArrayList<>();NodeList listings=item.getElementsByTagName("listing");
        for(int i=0;i<listings.getLength();i++){if(!(listings.item(i) instanceof Element))continue;Element listing=(Element)listings.item(i);Element price=first(listing,"price");if(price==null)continue;String currency=price.getAttribute("currency");if(!TextUtils.isEmpty(currency)&&!"EUR".equalsIgnoreCase(currency))continue;String raw=price.getAttribute("value");double eur;try{eur=Double.parseDouble(raw);}catch(Exception ignored){continue;}if(eur<=0||eur>10000)continue;Element condition=first(listing,"condition");String cv=condition==null?"":condition.getAttribute("value");if("new".equalsIgnoreCase(cv))continue;used.add((int)Math.round(eur*100.0));}
        if(used.isEmpty())return;java.util.Collections.sort(used);m.marketUsedCount=used.size();m.marketUsedMinCents=used.get(0);int n=used.size();m.marketUsedMedianCents=n%2==1?used.get(n/2):(used.get(n/2-1)+used.get(n/2))/2;
    }

    private String labelFor(String bggId){DealRecord d=db.findByBggId(bggId);return d==null?"BGG #"+bggId:(TextUtils.isEmpty(d.vintedTitle)?"BGG #"+bggId:d.vintedTitle);}
    private SharedPreferences diag(){return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    private static Element first(Element e,String tag){NodeList n=e.getElementsByTagName(tag);return n.getLength()>0&&n.item(0) instanceof Element?(Element)n.item(0):null;}
    private static String text(Element e,String tag){NodeList n=e.getElementsByTagName(tag);return n.getLength()>0?n.item(0).getTextContent():"";}
    private static Integer intAttr(Element e,String tag){NodeList n=e.getElementsByTagName(tag);if(n.getLength()==0)return null;try{return Integer.valueOf(((Element)n.item(0)).getAttribute("value"));}catch(Exception x){return null;}}
    private static Double doubleAttr(Element e,String tag){NodeList n=e.getElementsByTagName(tag);if(n.getLength()==0)return null;try{return Double.valueOf(((Element)n.item(0)).getAttribute("value"));}catch(Exception x){return null;}}
    private static String join(Set<String>s,String sep){return s==null||s.isEmpty()?null:TextUtils.join(sep,s);}
    private static String safe(String s){if(s==null)return"";return s.length()>220?s.substring(0,220):s;}
}
