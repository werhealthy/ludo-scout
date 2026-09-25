package it.vintedaffari.app;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative high-confidence resolver for listings already observed in the Vinted app.
 * It performs at most one narrow public catalogue-page lookup and verifies a unique match using
 * the public item page. Private Vinted APIs are intentionally not used.
 */
public final class VintedLinkResolver {
    private static final String TAG="VintedLinkResolver";
    private static final long RETRY_MS=60_000L;
    private static final long DURABLE_SNAPSHOT_MAX_AGE_MS=24L*60L*60_000L;
    private static final Pattern ITEM_LINK=Pattern.compile("/items/(\\d+)-([^\"\'<>?#]+)",Pattern.CASE_INSENSITIVE);
    private static final Pattern LDJSON=Pattern.compile("<script[^>]*type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
    private static final Pattern VINTED_IMAGE=Pattern.compile("https?://images(?:\\d+)?\\.vinted\\.(?:net|com)/[^\\s<>\"']+",Pattern.CASE_INSENSITIVE);
    private final ExecutorService exec=Executors.newSingleThreadExecutor();
    private final Map<String,Long> attempted=Collections.synchronizedMap(new LinkedHashMap<>());
    private final VintedPublicSession session;
    private final android.content.Context context;

    public interface Callback{
        void onResolved(Result r);
        void onUnresolved(String signature,String reason);
        default void onProgress(String signature,int progress,String stage){}
        default void onCandidates(String signature,List<CandidateOption> candidates){}
    }
    /** Lightweight public representation of a resolver candidate. Stored durably so a human can
     * finish ambiguous matches without forcing another network request. */
    public static final class CandidateOption{
        public String id,title,url,imageUrl,sellerId,sellerName;
        public int score; public double price=Double.NaN,photoSimilarity=Double.NaN; public boolean sold;
    }
    public static final class Result{
        public String signature,itemId,url,imageUrl,reason,matchedTitle,sellerId,sellerName,photosCsv,snapshotParser,publishedLabel,detailsText;
        /** Price fields are populated only when the exact public item page supplied them.
         * Catalogue/search hints are intentionally never promoted to a current-price update. */
        public Integer verifiedCurrentPriceCents,verifiedProtectedPriceCents;
        public int confidence;public boolean sold,needsDeepMetadata,catalogFastPath,exactPagePrice;
        public List<SellerBundleScanner.SellerItem> sellerSnapshot=new ArrayList<>();
    }
    private static final class Candidate{
        String id,title,url,brand,photo,sellerId,sellerName,photosCsv,snapshotParser,publishedLabel,detailsText; List<SellerBundleScanner.SellerItem> sellerSnapshot=new ArrayList<>(); double price=Double.NaN,total=Double.NaN,photoSimilarity=Double.NaN; int favorites=-1,score; Integer catalogId; boolean brandMatch,favMatch,sold,catalogStructured,observedTitleMatch,canonicalTitleMatch;
    }
    private static final class Selection{
        List<Candidate> ranked=new ArrayList<>();Candidate best;int second;boolean unique;
    }

    public VintedLinkResolver(Context c){context=c.getApplicationContext();session=new VintedPublicSession(c);}

    /** Authoritative retry time for this listing. It combines the shared public-session
     * circuit with the resolver-wide and per-listing retry guards. */
    public long nextAllowedAt(DealRecord d){
        long until=diag().getLong("linkRetryAfter",0L);
        until=Math.max(until,VintedPublicSession.nextAllowedAt(context));
        if(d!=null&&!TextUtils.isEmpty(d.signature)){Long last=attempted.get(d.signature);if(last!=null)until=Math.max(until,last+RETRY_MS);}
        return until;
    }

    public void resolve(DealRecord d,Callback cb){
        if(d==null||TextUtils.isEmpty(d.signature)||TextUtils.isEmpty(d.vintedTitle))return;
        long now=System.currentTimeMillis();if(now<diag().getLong("linkRetryAfter",0)){if(cb!=null)cb.onUnresolved(d.signature,"cooldown");return;}Long last=attempted.get(d.signature);if(last!=null&&now-last<RETRY_MS){if(cb!=null)cb.onUnresolved(d.signature,"cooldown");return;}attempted.put(d.signature,now);
        diag().edit().putInt("linkPublicVerifyCode",0).putInt("linkCandidateCount",0).putInt("linkBestScore",0).putInt("linkSecondScore",0).putString("linkBestTitle","").apply();
        if(cb!=null)cb.onProgress(d.signature,24,"preparo ricerca");
        exec.execute(()->{try{Result r=!TextUtils.isEmpty(d.vintedUrl)?resolveExistingMetadata(d,cb):resolveSync(d,cb);if(r!=null){if(cb!=null)cb.onResolved(r);}else if(cb!=null)cb.onUnresolved(d.signature,explainUnresolved(d));}catch(Throwable t){Log.w(TAG,"resolve failed",t);if(cb!=null)cb.onUnresolved(d.signature,t.getClass().getSimpleName()+": "+safe(t.getMessage()));}});
    }

    private Result resolveExistingMetadata(DealRecord d,Callback cb)throws Exception{
        if(cb!=null)cb.onProgress(d.signature,48,"apro annuncio");
        String id=d.vintedItemId;if(TextUtils.isEmpty(id)&&!TextUtils.isEmpty(d.vintedUrl)){Matcher m=Pattern.compile("/items/(\\d+)").matcher(d.vintedUrl);if(m.find())id=m.group(1);}if(TextUtils.isEmpty(id))return null;
        Candidate c=verifyPublicItem(id);
        if(c==null){
            // The item page can still expose a reliable publication timestamp even when Vinted
            // changes the product JSON enough that the full candidate parser cannot validate price.
            // The second read is normally a VintedPublicSession cache hit, so it adds no HTTP call.
            VintedPublicSession.Response page=session.getPublic(VintedPublicSession.HOST+"/items/"+id,"link_existing_item");
            if(page.code!=200)return null;if(isSoldHtml(page.body)){Result sold=new Result();sold.signature=d.signature;sold.itemId=id;sold.url=d.vintedUrl;sold.matchedTitle=d.vintedTitle;sold.sold=true;sold.reason="Vinted public-page · articolo venduto";return sold;}String age=extractPublishedLabelFromHtml(page.body);if(TextUtils.isEmpty(age))return null;
            Result timeOnly=new Result();timeOnly.signature=d.signature;timeOnly.itemId=id;timeOnly.url=d.vintedUrl;timeOnly.imageUrl=d.imageUrl;timeOnly.confidence=d.linkConfidence==null?95:d.linkConfidence;timeOnly.matchedTitle=d.vintedTitle;timeOnly.sellerId=d.sellerId;timeOnly.sellerName=d.sellerName;timeOnly.photosCsv=d.listingPhotosCsv;timeOnly.publishedLabel=age;timeOnly.needsDeepMetadata=false;timeOnly.reason="Vinted public-page · orario pubblicazione recuperato";diag().edit().putString("linkVerifyMode","public-page-time-only").apply();return timeOnly;
        }
        if(cb!=null)cb.onProgress(d.signature,86,"metadati ricevuti");
        Result r=new Result();r.signature=d.signature;r.itemId=id;r.url=!TextUtils.isEmpty(d.vintedUrl)?d.vintedUrl:c.url;r.imageUrl=!TextUtils.isEmpty(c.photo)?c.photo:d.imageUrl;r.confidence=d.linkConfidence==null?95:d.linkConfidence;r.matchedTitle=c.title;r.sellerId=c.sellerId;r.sellerName=c.sellerName;r.photosCsv=c.photosCsv;r.sellerSnapshot=c.sellerSnapshot;r.snapshotParser=c.snapshotParser;r.publishedLabel=c.publishedLabel;r.detailsText=c.detailsText;r.sold=c.sold;r.needsDeepMetadata=false;applyExactPagePrice(r,c);r.reason="Vinted public-page · metadati seller/foto aggiornati";diag().edit().putString("linkVerifyMode","public-page-metadata").apply();return r;
    }

    private Result resolveSync(DealRecord d,Callback cb)throws Exception{
        if(cb!=null)cb.onProgress(d.signature,32,"cerco su Vinted");
        Map<String,Candidate> candidates=new LinkedHashMap<>();
        List<String> queries=new ArrayList<>();
        // Canonical game name first: several listings of the same game can reuse the exact same
        // cached Vinted search page. The observed title remains the fallback and still drives local
        // title/price validation, so batching does not make matching less conservative.
        if(!TextUtils.isEmpty(d.displayName))queries.add(cleanQuery(d.displayName));
        if(!TextUtils.isEmpty(d.gameName)&&!norm(d.gameName).equals(norm(d.displayName)))queries.add(cleanQuery(d.gameName));
        if(!TextUtils.isEmpty(d.vintedTitle)&&!norm(d.vintedTitle).equals(norm(d.displayName))&&!norm(d.vintedTitle).equals(norm(d.gameName)))queries.add(cleanQuery(d.vintedTitle));
        String compact=compactQuery(d.vintedTitle);if(!TextUtils.isEmpty(compact))queries.add(compact);

        // First reuse catalogue pages already downloaded by a previous resolver invocation.
        // We may skip HTTP only when every earlier query in the normal two-query chain has a fresh
        // durable snapshot. A weak/ambiguous snapshot falls back to the exact existing network path.
        Selection snapshotSelection=null;boolean fromDurableSnapshot=false;int snapshotAttempts=0;
        for(String q:queries){
            if(TextUtils.isEmpty(q)||snapshotAttempts>=2)continue;
            snapshotAttempts++;
            int before=candidates.size();
            boolean snapshotPresent=searchSnapshotInto(d,q,candidates);
            if(!snapshotPresent){candidates.clear();break;}
            if(candidates.size()>before){
                snapshotSelection=selectCandidates(d,candidates);
                if(snapshotSelection!=null&&snapshotSelection.unique)fromDurableSnapshot=true;
                break;
            }
        }

        Selection selection=snapshotSelection;
        if(snapshotSelection==null||!snapshotSelection.unique){
            candidates.clear();
            int attempts=0;
            for(String q:queries){
                if(TextUtils.isEmpty(q)||attempts>=2)continue;
                if(attempts>0&&!candidates.isEmpty())break; // only spend the fallback request after a true miss
                attempts++;searchInto(d,q,candidates);
            }
            selection=selectCandidates(d,candidates);
        }

        if(cb!=null)cb.onProgress(d.signature,52,"risultati ricevuti");
        diag().edit().putInt("linkCandidateCount",selection==null?0:selection.ranked.size()).apply();
        if(selection==null||!selection.unique)return null;
        List<Candidate> ranked=selection.ranked;Candidate best=selection.best;int second=selection.second;
        if(cb!=null){List<CandidateOption> options=new ArrayList<>();for(int i=0;i<Math.min(6,ranked.size());i++)options.add(toOption(ranked.get(i)));cb.onCandidates(d.signature,options);}
        diag().edit().putInt("linkBestScore",best.score).putInt("linkSecondScore",second).putString("linkBestTitle",safe(best.title)).apply();
        boolean canonicalOnly=best.canonicalTitleMatch&&!best.observedTitleMatch;

        // Only a live structured catalogue response may use the zero-item-page fast path.
        // Durable snapshots can save the catalogue request, but still require exact public item verification.
        if(!fromDurableSnapshot&&!canonicalOnly&&best.catalogStructured && best.score>=92){
            Result fast=resultFromCandidate(d,best,best.score,"Vinted catalog · identità ad alta confidenza");
            fast.catalogFastPath=true;
            fast.needsDeepMetadata=TextUtils.isEmpty(best.sellerId)||TextUtils.isEmpty(best.photo)||TextUtils.isEmpty(best.publishedLabel);
            android.content.SharedPreferences p=diag();
            p.edit().putLong("vintedCoreFastResolved",p.getLong("vintedCoreFastResolved",0L)+1L).putString("linkVerifyMode","catalog-structured-fast-path").apply();
            if(cb!=null)cb.onProgress(d.signature,86,"identità confermata dal catalogo");
            return fast;
        }

        if(cb!=null)cb.onProgress(d.signature,68,"verifico annuncio");
        Candidate verified=verifyPublicItem(best.id);
        String verifyMode=fromDurableSnapshot?"durable-snapshot+public-page":"public-page";
        if(verified==null)return null;
        if(cb!=null)cb.onProgress(d.signature,86,"verifica ricevuta");
        int verifiedScore=score(d,verified);
        if(verifiedScore<88)return null;

        Result r=new Result();r.signature=d.signature;r.itemId=verified.id;r.url=!TextUtils.isEmpty(verified.url)?verified.url:VintedPublicSession.HOST+"/items/"+verified.id;r.imageUrl=verified.photo;r.confidence=Math.min(100,Math.max(best.score,verifiedScore));r.matchedTitle=verified.title;r.sellerId=!TextUtils.isEmpty(verified.sellerId)?verified.sellerId:best.sellerId;r.sellerName=!TextUtils.isEmpty(verified.sellerName)?verified.sellerName:best.sellerName;r.photosCsv=!TextUtils.isEmpty(verified.photosCsv)?verified.photosCsv:best.photosCsv;r.sellerSnapshot=verified.sellerSnapshot;r.snapshotParser=verified.snapshotParser;r.publishedLabel=verified.publishedLabel;r.detailsText=!TextUtils.isEmpty(verified.detailsText)?verified.detailsText:best.detailsText;r.sold=verified.sold;r.needsDeepMetadata=false;applyExactPagePrice(r,verified);r.reason="Vinted "+verifyMode+" · titolo/prezzo verificati · score "+verifiedScore+(second>0?" / secondo "+second:"");
        diag().edit().putString("linkVerifyMode",verifyMode).apply();
        return r;
    }

    private Selection selectCandidates(DealRecord d,Map<String,Candidate> candidates){
        if(candidates==null||candidates.isEmpty())return null;
        Selection out=new Selection();out.ranked.addAll(candidates.values());
        for(Candidate c:out.ranked)c.score=score(d,c);
        out.ranked.sort((a,b)->Integer.compare(b.score,a.score));
        applyPhotoEvidence(d,out.ranked);
        out.ranked.sort((a,b)->Integer.compare(b.score,a.score));
        out.best=out.ranked.get(0);out.second=out.ranked.size()>1?out.ranked.get(1).score:0;
        boolean canonicalOnly=out.best.canonicalTitleMatch&&!out.best.observedTitleMatch;
        boolean exactObservedPrice=exactObservedPrice(d,out.best);
        boolean canonicalProof=!canonicalOnly||(exactObservedPrice&&out.best.photoSimilarity>=.84);
        out.unique=out.best.score>=90&&(out.best.score-out.second>=12||out.ranked.size()==1)&&canonicalProof;
        return out;
    }

    private boolean searchSnapshotInto(DealRecord d,String query,Map<String,Candidate> out){
        VintedCandidateSnapshotStore.SearchSnapshot snapshot=
                VintedCandidateSnapshotStore.recentSearch(context,query,d==null?0L:d.firstSeen,DURABLE_SNAPSHOT_MAX_AGE_MS);
        if(snapshot==null)return false;
        double euros=d.itemPriceCents/100.0;
        for(VintedCandidateSnapshotStore.SnapshotCandidate x:snapshot.candidates){
            if(x==null||TextUtils.isEmpty(x.id)||x.bestPriceDiff(d.itemPriceCents)>5)continue;
            boolean titleOk=jaccard(norm(d.vintedTitle),norm(x.title))>=.68||jaccard(norm(query),norm(x.title))>=.72;
            if(!titleOk)continue;
            Candidate c=new Candidate();c.id=x.id;c.title=x.title;c.brand=x.brand;c.photo=x.image;c.price=euros;
            c.url=VintedPublicSession.HOST+"/items/"+x.id;
            out.put(c.id,c);
        }
        return true;
    }

    private static void applyExactPagePrice(Result r,Candidate c){
        if(r==null||c==null||Double.isNaN(c.price)||c.price<=0)return;
        r.verifiedCurrentPriceCents=(int)Math.round(c.price*100.0);
        if(!Double.isNaN(c.total)&&c.total>0)r.verifiedProtectedPriceCents=(int)Math.round(c.total*100.0);
        r.exactPagePrice=true;
    }

    private static CandidateOption toOption(Candidate c){CandidateOption o=new CandidateOption();o.id=c.id;o.title=c.title;o.url=c.url;o.imageUrl=c.photo;o.sellerId=c.sellerId;o.sellerName=c.sellerName;o.score=c.score;o.price=c.price;o.photoSimilarity=c.photoSimilarity;o.sold=c.sold;return o;}

    private Result resultFromCandidate(DealRecord d,Candidate c,int confidence,String reason){
        Result r=new Result();r.signature=d.signature;r.itemId=c.id;r.url=!TextUtils.isEmpty(c.url)?c.url:VintedPublicSession.HOST+"/items/"+c.id;r.imageUrl=c.photo;r.confidence=Math.min(100,Math.max(0,confidence));r.matchedTitle=c.title;r.sellerId=c.sellerId;r.sellerName=c.sellerName;r.photosCsv=c.photosCsv;r.sellerSnapshot=c.sellerSnapshot;r.snapshotParser=c.snapshotParser;r.publishedLabel=c.publishedLabel;r.detailsText=c.detailsText;r.sold=c.sold;r.reason=reason;return r;
    }

    private void searchInto(DealRecord d,String query,Map<String,Candidate> out)throws Exception{
        // One narrow public HTML search only. No private catalog API, no broad retry.
        searchHtmlInto(d,query,out);
    }

    private void searchHtmlInto(DealRecord d,String query,Map<String,Candidate> out)throws Exception{
        double euros=d.itemPriceCents/100.0;
        // Search by text and validate price locally. Vinted's exact price URL filter has proved less
        // reliable than the visible app search for some listings, while local price validation keeps
        // the resolver conservative.
        String u=VintedPublicSession.HOST+"/catalog?search_text="+enc(query)+"&order=newest_first";
        VintedPublicSession.Response hr=session.getPublic(u,"link_catalog");diag().edit().putInt("linkHtmlCode",hr.code).putString("linkStrategy","html-search-local-score").apply();if(hr.code==429||hr.code==403){diag().edit().putLong("linkRetryAfter",System.currentTimeMillis()+20*60_000L).apply();throw new IllegalStateException("HTTP "+hr.code+": richieste link sospese per 20 minuti");}if(hr.code!=200)return;
        String body=hr.body.replace("\\/","/").replace("\\u002F","/");
        int structured=0;
        VintedStructuredData.Scan shadowScan=null;
        try{
            VintedStructuredData.Scan scan=VintedStructuredData.scan(body,null);shadowScan=scan;
            int categoryRejected=0;
            for(JSONObject x:scan.items){
                Candidate c=parseCandidate(x);if(c==null)continue;
                if(c.catalogId!=null&&!isBoardGameCatalog(c.catalogId)){categoryRejected++;continue;}
                int pc=Double.isNaN(c.price)?Integer.MIN_VALUE:(int)Math.round(c.price*100.0);
                boolean priceOk=pc!=Integer.MIN_VALUE&&Math.abs(pc-d.itemPriceCents)<=5;
                boolean titleOk=jaccard(norm(d.vintedTitle),norm(c.title))>=.68||jaccard(norm(query),norm(c.title))>=.72;
                if(!priceOk||!titleOk)continue;c.catalogStructured=true;out.put(c.id,c);structured++;
            }
            diag().edit().putInt("linkStructuredCandidates",structured).putInt("linkCategoryRejected",categoryRejected).putString("linkStructuredScan",scan.summary()).apply();
        }catch(Throwable ignored){}
        // Persist a compact candidate snapshot from the catalogue response we already downloaded.
        // Capture itself performs no HTTP; VintedBatchEngine can reuse it for sibling observations.
        try{VintedCandidateSnapshotStore.capture(context,query,shadowScan,body);}catch(Throwable ignored){}
        Matcher m=ITEM_LINK.matcher(body);int n=0;
        while(m.find()&&n<40){
            String id=m.group(1);if(out.containsKey(id))continue;
            String slug=m.group(2);
            Candidate c=new Candidate();c.id=id;c.url=VintedPublicSession.HOST+"/items/"+id+"-"+slug;c.title=slug.replace('-', ' ').replace('_',' ').trim();
            int a=Math.max(0,m.start()-1800),b=Math.min(body.length(),m.end()+3000);String near=body.substring(a,b);
            // The broad search is only safe if the observed exact price is also present near this item.
            if(!containsPrice(near,euros))continue;c.price=euros;c.photo=extractNearbyImage(near);
            // Search HTML often contains recommendations too. Keep only links whose slug resembles the observed title.
            if(jaccard(norm(d.vintedTitle),norm(c.title))>=.62 || jaccard(norm(query),norm(c.title))>=.68){if(!out.containsKey(id))out.put(id,c);n++;}
        }
        diag().edit().putInt("linkHtmlLocalCandidates",n).apply();
    }


    private void applyPhotoEvidence(DealRecord d,List<Candidate> ranked){
        if(d==null||ranked==null||ranked.isEmpty()||TextUtils.isEmpty(d.signature))return;
        boolean singleCanonicalOnly=ranked.size()==1&&ranked.get(0).canonicalTitleMatch&&!ranked.get(0).observedTitleMatch;
        if(ranked.size()<2&&!singleCanonicalOnly)return;
        java.io.File observed=ThumbnailStore.fileFor(context,d.signature);if(!observed.exists()||observed.length()<2048)return;
        int compared=0;double best=Double.NaN;
        // Compare only the strongest textual candidates. This keeps image traffic bounded and never
        // turns visual matching into a crawler.
        for(int i=0;i<Math.min(6,ranked.size());i++){
            Candidate c=ranked.get(i);if(TextUtils.isEmpty(c.photo)||c.score<70)continue;
            double sim=VintedPhotoMatcher.similarity(context,d.signature,c.photo);if(Double.isNaN(sim))continue;
            c.photoSimilarity=sim;compared++;if(Double.isNaN(best)||sim>best)best=sim;
            if(sim>=.84)c.score+=48;else if(sim>=.76)c.score+=34;else if(sim>=.66)c.score+=18;else if(sim<.42)c.score-=18;
        }
        diag().edit().putInt("linkPhotoCompared",compared).putInt("linkBestPhotoSimilarity",Double.isNaN(best)?-1:(int)Math.round(best*100)).apply();
    }

    private static String extractNearbyImage(String html){
        if(TextUtils.isEmpty(html))return"";Matcher m=VINTED_IMAGE.matcher(html.replace("\\u002F","/").replace("\\/","/"));
        return m.find()?m.group().replace("&amp;","&"):"";
    }

    private static boolean containsPrice(String s,double euros){
        String dot=String.format(Locale.US,"%.2f",euros);String comma=dot.replace('.',',');
        return s.contains(dot)||s.contains(comma)||s.contains(dot.replace(".","\\u002E"))||s.contains("\"amount\":\""+dot+"\"")||s.contains("\"amount\":"+dot);
    }

    private Candidate verifyPublicItem(String id)throws Exception{
        VintedPublicSession.Response hr=session.getPublic(VintedPublicSession.HOST+"/items/"+enc(id),"link_verify_item");diag().edit().putInt("linkPublicVerifyCode",hr.code).apply();if(hr.code!=200)return null;
        String html=hr.body;boolean sold=isSoldHtml(html);JSONObject item=VintedStructuredData.item(html,id);Candidate c=item==null?null:parseCandidate(item);Matcher sm=LDJSON.matcher(html);
        while(c==null&&sm.find()){String raw=sm.group(1).trim();try{Object parsed=new org.json.JSONTokener(raw).nextValue();Candidate x=candidateFromLd(parsed,id);if(x!=null){c=x;break;}}catch(Exception ignored){}}
        if(c==null&&sold){c=new Candidate();c.id=id;c.title="Articolo Vinted";c.url=VintedPublicSession.HOST+"/items/"+id;c.sold=true;return c;}if(c==null||Double.isNaN(c.price))return null;
        if(c.catalogId!=null&&!isBoardGameCatalog(c.catalogId)){diag().edit().putInt("linkRejectedCatalogId",c.catalogId).apply();return null;}c.sold=sold;
        enrichFromPublicHtml(c,html);if(TextUtils.isEmpty(c.publishedLabel))c.publishedLabel=extractPublishedLabelFromHtml(html);
        if(!TextUtils.isEmpty(c.sellerId)){SellerBundleScanner.ExtractedSnapshot snapshot=SellerBundleScanner.extractPageSnapshot(html,c.sellerId,id,32,"item-page");c.snapshotParser=snapshot.summary;c.sellerSnapshot.addAll(snapshot.items);}
        return TextUtils.isEmpty(c.title)?null:c;
    }

    private void enrichFromPublicHtml(Candidate c,String html){
        if(c==null||html==null)return;JSONObject item=VintedStructuredData.item(html,c.id);if(item==null)return;Candidate exact=parseCandidate(item);if(exact==null)return;c.sellerId=exact.sellerId;c.sellerName=exact.sellerName;if(!TextUtils.isEmpty(exact.photosCsv))c.photosCsv=exact.photosCsv;if(!TextUtils.isEmpty(exact.photo))c.photo=exact.photo;if(!TextUtils.isEmpty(exact.publishedLabel))c.publishedLabel=exact.publishedLabel;if(!TextUtils.isEmpty(exact.detailsText))c.detailsText=exact.detailsText;if(!Double.isNaN(exact.price)&&exact.price>0)c.price=exact.price;if(!Double.isNaN(exact.total)&&exact.total>0)c.total=exact.total;

    }

    private Candidate candidateFromLd(Object parsed,String id){
        if(parsed instanceof JSONArray){JSONArray a=(JSONArray)parsed;for(int i=0;i<a.length();i++){Candidate c=candidateFromLd(a.opt(i),id);if(c!=null)return c;}return null;}
        if(!(parsed instanceof JSONObject))return null;JSONObject j=(JSONObject)parsed;String type=j.optString("@type","");
        if(!"Product".equalsIgnoreCase(type)&&j.has("@graph"))return candidateFromLd(j.opt("@graph"),id);
        if(!"Product".equalsIgnoreCase(type))return null;String identity=j.optString("url",j.optString("@id",""));if(identity.isEmpty()&&!id.equals(j.optString("sku",j.optString("productID",""))))return null;if(!identity.isEmpty()&&!Pattern.compile("/items/"+Pattern.quote(id)+"(?:-|$|[/?#])").matcher(identity).find())return null;
        Candidate c=new Candidate();c.id=id;c.title=j.optString("name","");if(TextUtils.isEmpty(c.title))return null;c.url=VintedPublicSession.HOST+"/items/"+id;c.detailsText=j.optString("description","");
        Object image=j.opt("image");if(image instanceof String){c.photo=(String)image;c.photosCsv=c.photo;}else if(image instanceof JSONArray){JSONArray ia=(JSONArray)image;java.util.List<String>ps=new java.util.ArrayList<>();for(int i=0;i<ia.length()&&ps.size()<12;i++){String u=ia.optString(i,"");if(!TextUtils.isEmpty(u))ps.add(u);}if(!ps.isEmpty()){c.photo=ps.get(0);c.photosCsv=android.text.TextUtils.join(",",ps);}}
        Object brand=j.opt("brand");if(brand instanceof JSONObject)c.brand=((JSONObject)brand).optString("name","");else if(brand instanceof String)c.brand=(String)brand;
        Object offers=j.opt("offers");JSONObject o=offers instanceof JSONObject?(JSONObject)offers:(offers instanceof JSONArray?((JSONArray)offers).optJSONObject(0):null);if(o!=null){Double p=parseMoney(o.opt("price"));if(p!=null)c.price=p;}for(String k:new String[]{"datePosted","dateCreated","uploadDate"}){String v=j.optString(k,"");if(!TextUtils.isEmpty(v)){String rel=relativeFromIso(v);if(!TextUtils.isEmpty(rel)){c.publishedLabel=rel;break;}}}
        return c;
    }

    private Candidate parseCandidate(JSONObject x){
        if(x==null)return null;Candidate c=new Candidate();c.id=str(x.opt("id"));c.title=x.optString("title","");if(TextUtils.isEmpty(c.id)||TextUtils.isEmpty(c.title))return null;c.detailsText=x.optString("description",x.optString("description_text",""));c.url=x.optString("url","");if(TextUtils.isEmpty(c.url))c.url=VintedPublicSession.HOST+"/items/"+c.id;c.brand=x.optString("brand_title","");if(TextUtils.isEmpty(c.brand)){JSONObject b=x.optJSONObject("brand_dto");if(b!=null)c.brand=b.optString("title","");}c.catalogId=parseCatalogId(x.opt("catalog_id"));if(c.catalogId==null){JSONObject catalog=x.optJSONObject("catalog");if(catalog!=null)c.catalogId=parseCatalogId(catalog.opt("id"));}Double p=parseMoney(x.opt("price"));if(p!=null)c.price=p;Double t=parseMoney(x.opt("total_item_price"));if(t!=null)c.total=t;c.favorites=x.has("favourite_count")?x.optInt("favourite_count",-1):-1;
        JSONObject photo=x.optJSONObject("photo");if(photo!=null){c.photo=photo.optString("url","");if(TextUtils.isEmpty(c.photo))c.photo=photo.optString("full_size_url","");}
        JSONArray photos=x.optJSONArray("photos");if(photos!=null){java.util.List<String> ps=new java.util.ArrayList<>();for(int i=0;i<photos.length()&&ps.size()<8;i++){JSONObject pp=photos.optJSONObject(i);if(pp==null)continue;String u=pp.optString("url",pp.optString("full_size_url",""));if(!TextUtils.isEmpty(u))ps.add(u);}if(!ps.isEmpty())c.photosCsv=android.text.TextUtils.join(",",ps);}
        c.sellerId=x.optString("user_id",x.optString("seller_id",""));JSONObject user=x.optJSONObject("user");if(user!=null){c.sellerId=str(user.opt("id"));c.sellerName=user.optString("login",user.optString("name",""));}c.publishedLabel=SellerBundleScanner.publishedLabel(x);
        return c;
    }

    private String explainUnresolved(DealRecord d){
        android.content.SharedPreferences p=diag();
        int verify=p.getInt("linkPublicVerifyCode",0);
        if(verify==429||verify==403)return "Vinted ha limitato temporaneamente la verifica (HTTP "+verify+")";
        if(verify==404)return "Pagina Vinted non disponibile (404): annuncio rimosso, venduto o non più pubblico";
        if(!TextUtils.isEmpty(d==null?null:d.vintedUrl)){
            if(verify==200)return "Pagina Vinted raggiunta, ma i metadati dell’annuncio non sono leggibili";
            return "Pagina Vinted conosciuta, ma i dettagli non sono disponibili ora";
        }
        int candidates=p.getInt("linkCandidateCount",0),best=p.getInt("linkBestScore",0),second=p.getInt("linkSecondScore",0);
        if(candidates<=0)return "Nessun annuncio compatibile trovato nella ricerca Vinted";
        if(candidates>1&&best>0&&best-second<12)return "Più annunci compatibili: non posso scegliere quello giusto con sufficiente sicurezza";
        if(best>0&&best<90)return "Ho trovato un annuncio, ma titolo e prezzo non corrispondono abbastanza";
        if(verify==200)return "Il candidato trovato non supera la verifica della pagina Vinted";
        return "Nessun candidato Vinted abbastanza univoco";
    }

    private static boolean isSoldHtml(String html){
        if(html==null||html.isEmpty())return false;String n=boundedHtmlSample(html,800_000).toLowerCase(Locale.ROOT);
        if(n.contains("schema.org/outofstock")||n.contains("\"availability\":\"out_of_stock\"")||n.contains("\"status\":\"sold\""))return true;
        String visible=visibleHtmlSample(html).toLowerCase(Locale.ROOT);return Pattern.compile("\\b(venduto|venduta|sold)\\b").matcher(visible).find();
    }
    private static String relativeFromIso(String raw){try{return RelativeTime.compact(java.time.Instant.parse(raw).toEpochMilli(),System.currentTimeMillis());}catch(Exception ignored){return"";}}
    static String extractPublishedLabelFromHtml(String html){
        if(html==null||html.isEmpty())return"";
        Matcher iso=Pattern.compile("\"(?:created_at_ts|created_at|createdAt|uploaded_at|uploadedAt|datePosted|dateCreated|uploadDate)\"\\s*:\\s*\"([^\"]{16,40})\"",Pattern.CASE_INSENSITIVE).matcher(html);
        while(iso.find()){String rel=relativeFromIso(iso.group(1));if(!TextUtils.isEmpty(rel))return rel;}
        Matcher epoch=Pattern.compile("\"(?:created_at_ts|uploaded_at_ts)\"\\s*:\\s*(\\d{10,13})").matcher(html);
        if(epoch.find())try{long v=Long.parseLong(epoch.group(1));if(v<100000000000L)v*=1000L;return RelativeTime.compact(v,System.currentTimeMillis());}catch(Exception ignored){}
        String visible=visibleHtmlSample(html);Matcher m=Pattern.compile("(?:Caricato|Pubblicato)\\s+((?:\\d+|un|una)\\s+(?:minut[oi]?|ore?|giorn[oi]?|settiman[ae]|mes[ei]|ann[oi])\\s+fa)",Pattern.CASE_INSENSITIVE).matcher(visible);return m.find()?m.group(1).trim():"";
    }
    private static String boundedHtmlSample(String html,int max){if(html==null||html.length()<=max)return html==null?"":html;int half=Math.max(1,max/2);return html.substring(0,half)+html.substring(html.length()-half);}
    private static String visibleHtmlSample(String html){String sample=boundedHtmlSample(html,600_000);return sample.replaceAll("(?is)<script\\b[^>]*>.*?</script>"," ").replaceAll("(?is)<style\\b[^>]*>.*?</style>"," ").replaceAll("(?s)<[^>]+>"," ").replace("&nbsp;"," ").replace("&middot;"," · ").replaceAll("\\s+"," ");}

    private int score(DealRecord d,Candidate c){
        String observed=norm(d.vintedTitle),candidate=norm(c.title);int observedScore=observedTitleScore(observed,candidate);
        String canonicalCandidate=titleWithoutKnownBrand(c.title,d.brand);boolean canonicalExact=canonicalTitleExact(d.displayName,canonicalCandidate)||canonicalTitleExact(d.gameName,canonicalCandidate);int canonicalScore=canonicalExact?60:0;
        c.observedTitleMatch=observedScore>0;c.canonicalTitleMatch=canonicalExact;int s=Math.max(observedScore,canonicalScore);if(s<=0)return 0;
        if(!Double.isNaN(c.price)){int pc=(int)Math.round(c.price*100);int diff=Math.abs(pc-d.itemPriceCents);if(diff<=1)s+=32;else if(diff<=5)s+=26;else if(diff<=20)s+=10;else return 0;}
        if(!TextUtils.isEmpty(d.brand)&&!TextUtils.isEmpty(c.brand)){double j=jaccard(norm(d.brand),norm(c.brand));if(j>=.90){s+=14;c.brandMatch=true;}else if(j>=.62)s+=6;}
        if(d.favorites!=null&&c.favorites>=0&&Math.abs(d.favorites-c.favorites)<=1){s+=7;c.favMatch=true;}
        if(d.protectedPriceCents!=null&&!Double.isNaN(c.total)&&Math.abs((int)Math.round(c.total*100)-d.protectedPriceCents)<=4)s+=8;
        // Seller is a strong disambiguator when Accessibility or the user already supplied it.
        if(!TextUtils.isEmpty(d.sellerName)&&!TextUtils.isEmpty(c.sellerName)){String ds=norm(d.sellerName),cs=norm(c.sellerName);if(ds.equals(cs))s+=24;else if(!ds.isEmpty()&&!cs.isEmpty()&&(ds.contains(cs)||cs.contains(ds)))s+=10;}
        if(!TextUtils.isEmpty(d.sellerId)&&!TextUtils.isEmpty(c.sellerId)&&d.sellerId.equals(c.sellerId))s+=30;
        return s;
    }

    private static int observedTitleScore(String observed,String candidate){if(observed.equals(candidate)&&!observed.isEmpty())return 60;double j=jaccard(observed,candidate);if(j>=.92)return 50;if(j>=.82)return 40;if(j>=.68)return 26;return 0;}
    private static boolean canonicalTitleExact(String canonical,String candidateWithoutBrand){String c=norm(canonical);return !c.isEmpty()&&c.equals(candidateWithoutBrand);}
    private static String titleWithoutKnownBrand(String title,String brand){String t=norm(title),b=norm(brand);if(t.isEmpty()||b.isEmpty())return t;java.util.Set<String> remove=new java.util.HashSet<>(java.util.Arrays.asList(b.split(" ")));StringBuilder out=new StringBuilder();for(String token:t.split(" ")){if(remove.contains(token))continue;if(out.length()>0)out.append(' ');out.append(token);}return out.toString();}
    private static boolean exactObservedPrice(DealRecord d,Candidate c){return d!=null&&c!=null&&!Double.isNaN(c.price)&&Math.abs((int)Math.round(c.price*100)-d.itemPriceCents)<=1;}

    // Current Vinted Italy board-game catalog ids. Category is a strong negative signal when
    // present, but never the only positive signal because sellers can miscategorise items even inside
    // these categories.
    private static boolean isBoardGameCatalog(Integer id){return id!=null&&(id==4881||id==4883);}
    private static Integer parseCatalogId(Object value){try{if(value==null||value==JSONObject.NULL)return null;if(value instanceof Number)return((Number)value).intValue();String s=String.valueOf(value).trim();return s.matches("\\d+")?Integer.valueOf(s):null;}catch(Exception e){return null;}}

    private static String compactQuery(String s){String n=norm(s);if(n.isEmpty())return"";String[] words=n.split(" ");StringBuilder b=new StringBuilder();int count=0;for(String w:words){if(w.length()<2||GENERIC.contains(" "+w+" "))continue;if(b.length()>0)b.append(' ');b.append(w);if(++count>=5)break;}return b.toString();}
    private static String cleanQuery(String s){return s==null?"":s.replaceAll("[\\n\\r]+"," ").replaceAll("\\s+"," ").trim();}
    private static final String GENERIC=" gioco giochi tavolo boardgame board game italiano italiana nuovo nuova ottime condizioni carte imbustate completo completa ";
    private static String norm(String s){if(s==null)return"";String n=Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}+","");return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();}
    private static double jaccard(String a,String b){if(a.isEmpty()||b.isEmpty())return 0;java.util.Set<String>A=new java.util.HashSet<>(java.util.Arrays.asList(a.split(" ")));java.util.Set<String>B=new java.util.HashSet<>(java.util.Arrays.asList(b.split(" ")));java.util.Set<String>I=new java.util.HashSet<>(A);I.retainAll(B);java.util.Set<String>U=new java.util.HashSet<>(A);U.addAll(B);return U.isEmpty()?0:(double)I.size()/U.size();}
    private static String enc(String s){return URLEncoder.encode(s==null?"":s, StandardCharsets.UTF_8);}
    private static String str(Object o){return o==null?"":String.valueOf(o);}
    private static Double parseMoney(Object o){try{if(o==null)return null;if(o instanceof Number)return((Number)o).doubleValue();if(o instanceof JSONObject){JSONObject j=(JSONObject)o;String a=j.optString("amount","");if(!a.isEmpty())return Double.parseDouble(a.replace(',','.'));}String s=String.valueOf(o).replace("€","").replace(" ","").trim().replace(',','.');return Double.parseDouble(s);}catch(Exception e){return null;}}
    private android.content.SharedPreferences diag(){return context.getSharedPreferences("va_v3_diag",android.content.Context.MODE_PRIVATE);}
    private static String safe(String s){return s==null?"":s.length()>180?s.substring(0,180):s;}
}
