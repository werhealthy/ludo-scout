package it.vintedaffari.app;

import android.content.Context;
import android.graphics.Rect;
import android.text.TextUtils;
import org.json.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Progressive seller reader.
 *
 * <p>Network policy: local observations / Accessibility seller rails first, then the already-known
 * public item page as an opportunistic snapshot. If that page exposes no seller rail we stop: no
 * private seller-items API, cookies, OAuth bootstrap or anti-bot bypass is used. This keeps the
 * bundle pipeline useful without turning one catalog item into an expensive seller crawl.</p>
 */
public final class SellerBundleScanner {
    private static final long SNAPSHOT_TTL=12*60*60_000L;
    private static final long CATALOG_TTL=12*60*60_000L;
    private static final int MAX_PROFILE_CANDIDATES=64;
    private static final Pattern ITEM_LINK=Pattern.compile("/items/(\\d+)(?:-([^\\\"'<>?#/]+))?",Pattern.CASE_INSENSITIVE);

    public static final class SellerItem {
        public String id,title,brand,url,imageUrl,photosCsv,publishedLabel,sourceKind;
        public int priceCents;
        public boolean ownerVerified;
    }
    public static final class Snapshot {
        public final List<SellerItem> items;public final String source;public final boolean cacheHit;
        Snapshot(List<SellerItem> i,String s,boolean h){items=i;source=s;cacheHit=h;}
    }
    public static final class ExtractedSnapshot {
        public final List<SellerItem> items;public final String summary;public final int verified,provisional;
        ExtractedSnapshot(List<SellerItem> i,String s,int v,int p){items=i;summary=s;verified=v;provisional=p;}
    }
    public interface SnapshotCallback {void onSnapshot(String sourceSignature,String sellerId,Snapshot snapshot);void onError(String sourceSignature,String reason);}
    public interface Callback {void onItems(String sourceSignature,String sellerId,List<SellerItem> items);void onDeferred(String sourceSignature,String reason);void onError(String sourceSignature,String reason);}

    private final VintedPublicSession session;
    private final ExecutorService exec=Executors.newSingleThreadExecutor();
    private final Context context;
    private final BundleDatabase cache;

    public SellerBundleScanner(Context c){context=c.getApplicationContext();session=new VintedPublicSession(c);cache=new BundleDatabase(c);}
    private boolean bulkMaintenanceActive(){return context.getSharedPreferences("va_v3_diag",Context.MODE_PRIVATE).getBoolean("manualBulkRequested",false);}
    private boolean test2bExclusiveActive(){DealDatabase h=new DealDatabase(context);try{return new MarketStore(context,h).isTest2bExclusiveActive();}catch(Throwable ignored){return false;}finally{try{h.close();}catch(Throwable ignored){}}}

    public void snapshot(DealRecord source,SnapshotCallback cb){
        if(source==null||TextUtils.isEmpty(source.signature)||TextUtils.isEmpty(source.sellerId)){if(cb!=null)cb.onError(source==null?"":source.signature,"sellerId assente");return;}
        exec.execute(()->{try{Snapshot s=snapshotSync(source);if(cb!=null)cb.onSnapshot(source.signature,source.sellerId,s);}catch(Throwable t){if(cb!=null)cb.onError(source.signature,t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage()));}});
    }

    private Snapshot snapshotSync(DealRecord source){
        LinkedHashMap<String,SellerItem> merged=new LinkedHashMap<>();boolean hit=false;List<String> sources=new ArrayList<>();
        if(cache.hasFreshSnapshot(source.sellerId,SNAPSHOT_TTL)){
            hit=true;List<SellerItem> saved=cache.cachedSnapshot(source.sellerId,SNAPSHOT_TTL);mergeCached(merged,saved,source.vintedItemId);
            if(!saved.isEmpty())sources.add("item-page cache");
        }
        List<SellerItem> observed=observed(source.sellerId,source.vintedItemId);merge(merged,observed,source.vintedItemId,true,"observed-local");
        if(!observed.isEmpty())sources.add("annunci già osservati");
        List<SellerItem> out=new ArrayList<>(merged.values());
        if(!observed.isEmpty())cache.storeSnapshot(source.sellerId,source.vintedItemId,"merged-local",out);
        if(out.isEmpty()){diag("snapshot-empty",0,0);return new Snapshot(out,"nessun dato leggero disponibile",hit);}
        diag("snapshot-merged",0,out.size());return new Snapshot(out,TextUtils.join(" + ",sources),hit);
    }

    /**
     * Deep scan stays on the already-known public item page. Local seller graphs and Accessibility
     * seller rails are preferred; no seller catalog/private API is opened here.
     */
    public void deepScan(DealRecord source,Callback cb){
        if(source==null||TextUtils.isEmpty(source.signature)||TextUtils.isEmpty(source.sellerId)){if(cb!=null)cb.onError(source==null?"":source.signature,"sellerId assente");return;}
        exec.execute(()->{try{
            if(bulkMaintenanceActive()){if(cb!=null)cb.onDeferred(source.signature,"aggiornamento dati prioritario");return;}
            if(test2bExclusiveActive()){if(cb!=null)cb.onDeferred(source.signature,"test diagnostico Vinted in corso");return;}
            if(cache.hasFreshSellerCatalog(source.sellerId,CATALOG_TTL)){
                List<SellerItem> saved=cache.cachedSellerCatalog(source.sellerId,CATALOG_TTL);
                if(!saved.isEmpty()){
                    for(SellerItem s:saved){s.ownerVerified=true;s.sourceKind="seller-cache";}
                    diag("seller-catalog-cache",200,saved.size());if(cb!=null)cb.onItems(source.signature,source.sellerId,excluding(saved,source.vintedItemId));return;
                }
                // Ignore legacy empty-cache rows: they must never suppress the local/public-item paths.
            }
            // Best low-cost source: the already-known public item page. Vinted often embeds a
            // lightweight "more from this seller" payload there. The public-session cache makes
            // this zero-network when link resolution fetched the page moments earlier.
            if(!TextUtils.isEmpty(source.vintedUrl)&&!TextUtils.isEmpty(source.vintedItemId)){
                VintedPublicSession.Response itemPage=session.getPublic(VintedPublicSession.HOST+"/items/"+source.vintedItemId,"bundle_snapshot_item");recordResponse("item-public-snapshot",itemPage);
                if(itemPage.code==429||itemPage.code==403){cache.increment("rateLimited");if(cb!=null)cb.onDeferred(source.signature,"HTTP "+itemPage.code+" · pausa automatica");return;}
                if(itemPage.code==200){
                    ExtractedSnapshot itemSnapshot=extractPageSnapshot(itemPage.body,source.sellerId,source.vintedItemId,MAX_PROFILE_CANDIDATES,"item-page");
                    cache.storeSnapshot(source.sellerId,source.vintedItemId,"item-public-expanded",itemSnapshot.items);
                    String age=VintedLinkResolver.extractPublishedLabelFromHtml(itemPage.body);
                    if(TextUtils.isEmpty(age)){JSONObject exact=VintedStructuredData.item(itemPage.body,source.vintedItemId);age=publishedLabel(exact);}
                    if(!TextUtils.isEmpty(age)){DealDatabase deals=new DealDatabase(context);try{deals.updatePublishedLabel(source.signature,age);}finally{deals.close();}android.content.SharedPreferences prefs=context.getSharedPreferences("va_v3_diag",Context.MODE_PRIVATE);prefs.edit().putString("lastPublishedLabel",age).putLong("publishedMetadataResolved",prefs.getLong("publishedMetadataResolved",0)+1).apply();}
                    context.getSharedPreferences("va_v3_diag",Context.MODE_PRIVATE).edit().putString("bundleParser",itemSnapshot.summary).putInt("bundleProfileVerified",itemSnapshot.verified).putInt("bundleProfileProvisional",itemSnapshot.provisional).apply();
                    if(!itemSnapshot.items.isEmpty()){diag("item-page-opportunistic",itemPage.code,itemSnapshot.items.size());if(cb!=null)cb.onItems(source.signature,source.sellerId,itemSnapshot.items);return;}
                }
            }

            // The public item page did not expose another seller item. Do not fall back to
            // /api/v2/users/.../items: that route is session-dependent and defeats the low-cost
            // policy. The service will keep accumulating a local seller graph from Vinted UI rails
            // and from future resolved item pages.
            cache.increment("sellerDataEmpty");
            context.getSharedPreferences("va_v3_diag",Context.MODE_PRIVATE).edit()
                    .putString("bundleParser","public item page: no usable seller rail; awaiting local seller graph")
                    .putInt("bundleProfileVerified",0).putInt("bundleProfileProvisional",0).apply();
            diag("public-item-no-seller-rail",200,0);
            if(cb!=null)cb.onItems(source.signature,source.sellerId,Collections.emptyList());
        }catch(VintedPublicSession.RateLimitedException e){cache.increment("rateLimited");if(cb!=null)cb.onDeferred(source.signature,e.getMessage());}
        catch(Throwable t){cache.increment("errors");if(cb!=null)cb.onError(source.signature,t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage()));}});
    }

    /** Verify only BGG-matched provisional profile candidates; maxChecks is a hard network cap. */
    public void verifyMatches(DealRecord source,List<SellerItem> matched,int maxChecks,Callback cb){
        if(source==null||TextUtils.isEmpty(source.sellerId)){if(cb!=null)cb.onError(source==null?"":source.signature,"sellerId assente");return;}
        exec.execute(()->{try{
            if(bulkMaintenanceActive()){if(cb!=null)cb.onDeferred(source.signature,"aggiornamento dati prioritario");return;}
            if(test2bExclusiveActive()){if(cb!=null)cb.onDeferred(source.signature,"test diagnostico Vinted in corso");return;}
            LinkedHashMap<String,SellerItem> verified=new LinkedHashMap<>();int checks=0,rejected=0;
            if(matched!=null)for(SellerItem candidate:matched){
                if(candidate==null||TextUtils.isEmpty(candidate.id)||Objects.equals(candidate.id,source.vintedItemId))continue;
                if(candidate.ownerVerified){put(verified,candidate,source.vintedItemId);continue;}
                if(checks>=Math.max(0,maxChecks))continue;checks++;
                VintedPublicSession.Response r=session.getPublic(VintedPublicSession.HOST+"/items/"+candidate.id,"bundle_verify_item");
                if(r.code!=200){if(r.code==403||r.code==429)throw new VintedPublicSession.RateLimitedException("HTTP "+r.code+" · verifica bundle sospesa");rejected++;continue;}
                JSONObject exact=VintedStructuredData.item(r.body,candidate.id);String owner=ownerId(exact);
                boolean owned=source.sellerId.equals(owner);
                if(!owned){VintedStructuredData.Scan exactScan=VintedStructuredData.scan(r.body,source.sellerId);for(JSONObject x:exactScan.items)if(candidate.id.equals(x.optString("id"))){owned=true;break;}}
                if(!owned){rejected++;continue;}
                SellerItem item=parse(exact);if(item==null){rejected++;continue;}item.ownerVerified=true;item.sourceKind="item-owner-verified";put(verified,item,source.vintedItemId);
                // The verification page may itself expose free same-seller snapshot items.
                VintedStructuredData.Scan snapshot=VintedStructuredData.scan(r.body,source.sellerId);
                for(JSONObject x:snapshot.items){SellerItem s=parse(x);if(s!=null){s.ownerVerified=true;s.sourceKind="item-snapshot-verified";put(verified,s,source.vintedItemId);}}
            }
            List<SellerItem> out=new ArrayList<>(verified.values());if(out.size()>=2)cache.storeSellerCatalog(source.sellerId,out,200);
            cache.increment("candidateVerifyRequests",checks);cache.increment("candidateVerifyRejected",rejected);
            context.getSharedPreferences("va_v3_diag",Context.MODE_PRIVATE).edit().putInt("bundleVerifyRequestsLast",checks).putInt("bundleVerifyRejectedLast",rejected).putInt("bundleVerifyAcceptedLast",out.size()).apply();
            if(cb!=null)cb.onItems(source.signature,source.sellerId,out);
        }catch(VintedPublicSession.RateLimitedException e){cache.increment("rateLimited");if(cb!=null)cb.onDeferred(source.signature,e.getMessage());}
        catch(Throwable t){cache.increment("errors");if(cb!=null)cb.onError(source.signature,t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage()));}});
    }

    /** Compatibility entry. */
    public void fetch(DealRecord source,Callback cb){snapshot(source,new SnapshotCallback(){public void onSnapshot(String sig,String seller,Snapshot s){if(s.items.isEmpty()){deepScan(source,cb);return;}deepScan(source,cb);}public void onError(String sig,String reason){if(cb!=null)cb.onError(sig,reason);}});}

    public static List<SellerItem> prefilter(List<SellerItem> items){List<SellerItem> out=new ArrayList<>();if(items==null)return out;for(SellerItem s:items)if(likelyBoardgame(s))out.add(s);return out;}
    public static boolean likelyBoardgame(SellerItem s){
        if(s==null||TextUtils.isEmpty(s.title))return false;if("observed-local".equals(s.sourceKind))return true;
        String x=(s.title+" "+(s.brand==null?"":s.brand)).toLowerCase(Locale.ROOT);
        String[] positive={"gioco","board","game","asmodee","giochi uniti","cranio","ghenos","dvgiochi","ravensburger","hasbro","kosmos","cmon","carcassonne","catan","root","azul","dixit","terraforming","pandemic","wingspan","ticket to ride","7 wonders","splendor","brass","ark nova","kluster"};
        String[] negative={"inserto","organizer","ricambio","sleeve","bustine","playmat","dadi sfusi","manuale","scatola vuota","box vuoto"};
        for(String n:negative)if(x.contains(n))return false;for(String p:positive)if(x.contains(p))return true;
        return s.ownerVerified&&s.priceCents>=500&&s.priceCents<=25000;
    }

    private List<SellerItem> observed(String seller,String sourceId){
        List<SellerItem> observed=new ArrayList<>();Set<String> ids=new HashSet<>();DealDatabase database=new DealDatabase(context);
        try{for(DealRecord d:database.getDeals("all_with_review",2000)){
            if(!seller.equals(d.sellerId)||TextUtils.isEmpty(d.vintedItemId)||TextUtils.isEmpty(d.vintedUrl)||d.itemPriceCents<=0||!ids.add(d.vintedItemId)||Objects.equals(sourceId,d.vintedItemId))continue;
            SellerItem item=new SellerItem();item.id=d.vintedItemId;item.title=d.vintedTitle;item.brand=d.brand;item.url=d.vintedUrl;item.imageUrl=d.imageUrl;item.photosCsv=d.listingPhotosCsv;item.priceCents=d.itemPriceCents;item.publishedLabel=d.publishedLabel;item.ownerVerified=true;item.sourceKind="observed-local";observed.add(item);
        }}finally{database.close();}return observed;
    }

    /** Extract verified and provisional item candidates from HTML we already downloaded. */
    public static ExtractedSnapshot extractPageSnapshot(String html,String sellerId,String sourceId,int max,String sourcePrefix){
        VintedStructuredData.Scan owned=VintedStructuredData.scan(html,sellerId),all=VintedStructuredData.scan(html,null);LinkedHashMap<String,SellerItem> out=new LinkedHashMap<>();int cap=Math.max(1,max);
        for(JSONObject x:owned.items){SellerItem sellerItem=parse(x);if(sellerItem==null)continue;sellerItem.ownerVerified=true;sellerItem.sourceKind=sourcePrefix+"-owned";put(out,sellerItem,sourceId);if(out.size()>=cap)break;}
        if(out.size()<cap)for(JSONObject x:all.items){if(out.size()>=cap)break;SellerItem sellerItem=parse(x);if(sellerItem==null)continue;String owner=ownerId(x);if(!TextUtils.isEmpty(owner)&&!sellerId.equals(owner))continue;sellerItem.ownerVerified=sellerId.equals(owner);sellerItem.sourceKind=sourcePrefix+(sellerItem.ownerVerified?"-owned":"-provisional");put(out,sellerItem,sourceId);}
        if(out.size()<cap)discoverLinks(html,out,sourceId,cap,sourcePrefix+"-link");
        int verified=0,provisional=0;for(SellerItem sellerItem:out.values())if(sellerItem.ownerVerified)verified++;else provisional++;
        return new ExtractedSnapshot(new ArrayList<>(out.values()),owned.summary()+"; all="+all.items.size()+"; verified="+verified+"; provisional="+provisional,verified,provisional);
    }

    private static void discoverLinks(String html,LinkedHashMap<String,SellerItem> out,String sourceId,int max,String kind){
        if(html==null||out.size()>=max)return;
        String body=html.replace("\\/","/").replace("\\u002F","/").replace("\\\"","\"").replace("&quot;","\"");
        Matcher m=ITEM_LINK.matcher(body);
        while(m.find()&&out.size()<max){
            String id=m.group(1);if(id.equals(sourceId)||out.containsKey(id))continue;
            String slug=m.group(2),title="";
            if(!TextUtils.isEmpty(slug)){title=slug.replace('-',' ').replace('_',' ').trim();try{title=URLDecoder.decode(title,StandardCharsets.UTF_8.name());}catch(Exception ignored){}}
            if(title.length()<2)title=nearbyTitle(body,m.start(),m.end());
            if(title.length()<2)continue;
            SellerItem sellerItem=new SellerItem();sellerItem.id=id;sellerItem.title=cleanHtmlText(title);sellerItem.url=VintedPublicSession.HOST+"/items/"+id+(TextUtils.isEmpty(slug)?"":"-"+slug);sellerItem.priceCents=0;sellerItem.ownerVerified=false;sellerItem.sourceKind=kind;out.put(id,sellerItem);
        }
    }
    private static String nearbyTitle(String body,int start,int end){
        int a=Math.max(0,start-420),b=Math.min(body.length(),end+620);String snippet=body.substring(a,b);
        Pattern[] patterns={
                Pattern.compile("(?:\\\"title\\\"|\\\"name\\\")\\s*:\\s*\\\"([^\\\"]{2,140})\\\"",Pattern.CASE_INSENSITIVE),
                Pattern.compile("(?:aria-label|alt|title)\\s*=\\s*\\\"([^\\\"]{2,140})\\\"",Pattern.CASE_INSENSITIVE)};
        for(Pattern p:patterns){Matcher m=p.matcher(snippet);if(m.find())return m.group(1);}return"";
    }
    private static String cleanHtmlText(String s){if(s==null)return"";return s.replace("&amp;","&").replace("&#39;","'").replace("&quot;","\"").replaceAll("\\s+"," ").trim();}
    private static void merge(Map<String,SellerItem> out,List<SellerItem> items,String sourceId,boolean verified,String kind){if(items==null)return;for(SellerItem s:items){if(s==null)continue;s.ownerVerified=verified;s.sourceKind=kind;put(out,s,sourceId);}}
    private static void mergeCached(Map<String,SellerItem> out,List<SellerItem> items,String sourceId){if(items==null)return;for(SellerItem sellerItem:items){if(sellerItem==null)continue;if(TextUtils.isEmpty(sellerItem.sourceKind))sellerItem.sourceKind="item-page-cache";put(out,sellerItem,sourceId);}}
    private static void put(Map<String,SellerItem> out,SellerItem s,String sourceId){if(s==null||TextUtils.isEmpty(s.id)||TextUtils.isEmpty(s.title)||Objects.equals(sourceId,s.id))return;SellerItem old=out.get(s.id);if(old==null||(!old.ownerVerified&&s.ownerVerified))out.put(s.id,s);}
    static String ownerId(JSONObject x){if(x==null)return"";for(String k:new String[]{"user_id","seller_id","owner_id","userId","sellerId","ownerId"}){String v=x.optString(k,"");if(v.matches("[0-9]+"))return v;}for(String k:new String[]{"user","seller","owner"}){JSONObject u=x.optJSONObject(k);if(u!=null){String v=String.valueOf(u.opt("id"));if(v.matches("[0-9]+"))return v;}}return"";}

    static List<SellerItem> parseSellerItemsResponse(String body,String sellerId,String sourceId,int max){
        LinkedHashMap<String,SellerItem> out=new LinkedHashMap<>();if(body==null||body.trim().isEmpty())return new ArrayList<>();
        try{
            Object root=new JSONTokener(body).nextValue();JSONArray items=null;
            if(root instanceof JSONObject){JSONObject o=(JSONObject)root;items=o.optJSONArray("items");if(items==null){JSONObject data=o.optJSONObject("data");if(data!=null)items=data.optJSONArray("items");}}
            else if(root instanceof JSONArray)items=(JSONArray)root;
            if(items!=null)for(int i=0;i<items.length()&&out.size()<Math.max(1,max);i++){
                JSONObject x=items.optJSONObject(i);SellerItem sellerItem=parse(x);if(sellerItem==null||Objects.equals(sourceId,sellerItem.id))continue;
                String owner=ownerId(x);if(!TextUtils.isEmpty(owner)&&!sellerId.equals(owner))continue;
                // The response itself is scoped by /users/{sellerId}/items, therefore ownership
                // is considered verified even when a compact item object omits user_id.
                sellerItem.ownerVerified=true;sellerItem.sourceKind="seller-items-scoped";put(out,sellerItem,sourceId);
            }
        }catch(Exception ignored){}
        return new ArrayList<>(out.values());
    }

    private void recordResponse(String route,VintedPublicSession.Response r){String b=r.body==null?"":r.body;String format=b.trim().startsWith("{")?"json":b.toLowerCase(Locale.ROOT).contains("<html")?"html":"altro";boolean challenge=b.toLowerCase(Locale.ROOT).contains("captcha")||b.toLowerCase(Locale.ROOT).contains("access denied");context.getSharedPreferences("va_v3_diag",Context.MODE_PRIVATE).edit().putInt("bundleSellerPublicHttp",r.code).putString("bundleLastResponse",route+" HTTP "+r.code+"; formato="+format+"; caratteri="+b.length()+"; challenge="+challenge).apply();}
    private void diag(String strategy,int code,int count){context.getSharedPreferences("va_v3_diag",Context.MODE_PRIVATE).edit().putInt("bundleSellerHttp",code).putString("bundleStrategy",strategy).putInt("bundleCatalogItems",count).apply();}
    private static List<SellerItem> excluding(List<SellerItem> in,String sourceId){List<SellerItem>o=new ArrayList<>();if(in!=null)for(SellerItem s:in)if(sourceId==null||!sourceId.equals(s.id))o.add(s);return o;}

    public void close(){exec.shutdownNow();cache.close();}

    static SellerItem parse(JSONObject x){
        if(x==null||x.optBoolean("is_closed",false)||x.optBoolean("is_sold",false)||x.optBoolean("is_reserved",false))return null;
        String id=String.valueOf(x.opt("id")),title=x.optString("title","");if("null".equals(id)||!id.matches("[0-9]+")||title.isEmpty())return null;
        SellerItem s=new SellerItem();s.id=id;s.title=title;s.url=x.optString("url",VintedPublicSession.HOST+"/items/"+id);s.brand=x.optString("brand_title","");
        JSONObject p=x.optJSONObject("price");String currency=p==null?x.optString("currency","EUR"):p.optString("currency_code",p.optString("currency","EUR"));if(!"EUR".equalsIgnoreCase(currency))return null;
        try{s.priceCents=(int)Math.round(Double.parseDouble(p!=null?p.optString("amount","0"):String.valueOf(x.opt("price")))*100);}catch(Exception e){return null;}if(s.priceCents<=0)return null;
        List<String> photos=new ArrayList<>();JSONArray images=x.optJSONArray("photos");if(images!=null)for(int i=0;i<images.length();i++){JSONObject image=images.optJSONObject(i);if(image!=null){String u=image.optString("full_size_url",image.optString("url",""));if(u.startsWith("https://"))photos.add(u);}}
        s.photosCsv=TextUtils.join(",",PhotoIdentity.unique(photos));JSONObject ph=x.optJSONObject("photo");if(ph!=null)s.imageUrl=ph.optString("url",ph.optString("full_size_url",""));if(TextUtils.isEmpty(s.imageUrl)&&!photos.isEmpty())s.imageUrl=photos.get(0);
        s.publishedLabel=publishedLabel(x);return s;
    }
    public static String publishedLabel(JSONObject x){if(x==null)return"";for(String key:new String[]{"created_at_ts","created_at","uploaded_at","createdAt","uploadedAt"}){Object val=x.opt(key);if(val==null||val==JSONObject.NULL)continue;Long time=parseTime(val);if(time!=null)return RelativeTime.compact(time,System.currentTimeMillis());}return"";}
    private static Long parseTime(Object raw){try{String s=String.valueOf(raw);long t=s.matches("[0-9]+")?Long.parseLong(s):java.time.Instant.parse(s).toEpochMilli();if(t<100000000000L)t*=1000;if(t>946684800000L&&t<=System.currentTimeMillis()+86400000L)return t;}catch(Exception ignored){}return null;}
    public static List<VintedCard> asCards(List<SellerItem> items){List<VintedCard>o=new ArrayList<>();for(SellerItem s:items)o.add(new VintedCard(s.title,s.brand,"",Math.max(0,s.priceCents)/100.0,null,null,new Rect(),s.title+", "+(s.priceCents/100.0)+" €"));return o;}
}
