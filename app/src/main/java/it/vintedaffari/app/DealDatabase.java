package it.vintedaffari.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.text.Normalizer;
import java.util.*;

public final class DealDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME="vinted_affari.db";
    private static final int DB_VERSION=21;
    private static final String COLS="id,signature,first_seen,last_seen,seen_count,vinted_title,brand,item_condition,item_price_cents,protected_price_cents,favorites,analysis_status,bgg_id,game_name,display_name,rating,bgg_rank,voters,quality_score,tier,tier_label,total_cents,benchmark_cents,offer_cents,shipping_cents,discount,language_code,match_reason,lifecycle,confirmed,listing_type,verification_state,verification_reason,vinted_url,resolved_at,shipping_verified_cents,vinted_item_id,image_url,link_confidence,link_reason,published_label,bgg_image_url,bgg_categories,bgg_minplayers,bgg_maxplayers,bgg_weight,bgg_playtime,seller_id,seller_name,listing_photos_csv";

    public static final class MissingCounts { public int published, metadata, link, bgg; }
    public static final class ObservationSession {
        public long startAt,endAt;
        public int observations,uniqueListings,pendingListings,analysisPendingListings,validListings,bggMatchedListings,vintedLinkedListings,completeListings,reviewListings,heldListings;
        /** Core Vinted identity work is the expensive remote part of a run. coreWorkListings is
         * stable enough for the run target; corePendingListings drives the live ETA. */
        public int coreWorkListings,corePendingListings,coreRemainingListings;
        ObservationSession(long at){startAt=endAt=at;}
    }
    public static final class ObservationDay {
        public long startAt,endAt;
        public int sessions,observations,uniqueListings,validListings,bggMatchedListings,vintedLinkedListings,completeListings,reviewListings,heldListings;
        ObservationDay(long start,long end){startAt=start;endAt=end;}
    }
    /** One row in the engine run inspector. This intentionally reads the canonical marketplace
     * tables instead of the curated Catalog feed, so an active run can show every game it is
     * actually working on, including rows that are not yet publishable in Catalog. */
    public static final class EngineRunItem {
        public long listingId,gameId;
        public String signature,title,canonical,bggId,imageUrl,listingState,listingMatchState,gameState,publishedLabel,languageCode;
        public int priceCents;
        public boolean bggReady,vintedReady,complete,review,held;
    }

    public static final long ENGINE_SESSION_GAP_MS=3L*60_000L;
    /** 10 minutes is the target for a small/ordinary scroll, not a correctness deadline.
     * Vinted public pages are deliberately paced at roughly one request every 55 seconds, so larger
     * runs need a workload-aware estimate. Timing must never classify, hide or discard a listing. */
    public static final long ENGINE_RUN_TARGET_MIN_MS=10L*60_000L;
    /** One minute of local/setup allowance plus the measured public-page pacing for each core
     * candidate. The ten-minute floor keeps small-run expectations stable without becoming a cutoff. */
    public static final long ENGINE_RUN_TARGET_BASE_MS=60_000L;
    public static final long ENGINE_RUN_REMOTE_UNIT_MS=55_000L;
    /** Fairness is deliberately separate from correctness. A run may yield this lane after one
     * service slice only when another unfinished scroll is waiting; its listings remain intact. */
    public static final long ENGINE_RUN_FAIRNESS_SLICE_MS=90_000L;
    public static final long ENGINE_RUN_LOCAL_ETA_MS=60_000L;
    /** Legacy alias kept for older regression/diagnostic callers; do not use as a fixed deadline. */
    public static final long ENGINE_RUN_SLA_MS=ENGINE_RUN_TARGET_MIN_MS;
    public static final long ENGINE_DUPLICATE_SIGHTING_MS=10L*60_000L;
    private static final long ACTIVE_RUN_CACHE_MS=1_500L;private ObservationSession cachedActiveRun=null;private long cachedActiveRunAt=0L;

    /** Product-facing cut-over. Raw observations stay in SQLite, but Motore only treats rows at or
     * after this timestamp as current/history. Stored in queue_controls so every process sees the
     * same epoch without relying on multi-process SharedPreferences caches. */
    public synchronized long engineEpochStart(){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT value FROM queue_controls WHERE name='engine_epoch_start' LIMIT 1",null)){return c.moveToFirst()?Math.max(0L,c.getLong(0)):0L;}
        catch(Throwable ignored){return 0L;}
    }
    private long clampEngineStart(long start){return Math.max(start,engineEpochStart());}

    /** A run owns the automatic pipeline until all valid rows are either ready or explicitly
     * waiting for human review. Newer scrolls may already be captured/classified locally, but they
     * remain waiting and do not consume the ordinary backlog Vinted lane. */
    public static boolean engineContentSettled(ObservationSession s){
        if(s==null)return true;
        if(s.analysisPendingListings>0)return false;
        return s.validListings==0||s.completeListings+s.reviewListings+s.heldListings>=s.validListings;
    }
    public static long engineTargetMs(ObservationSession s){
        if(s==null)return ENGINE_RUN_TARGET_MIN_MS;
        int remote=Math.max(s.coreWorkListings,s.corePendingListings);
        // Before durable jobs are materialised, valid listings are the conservative fallback.
        if(remote<=0&&!engineContentSettled(s))remote=Math.max(0,s.validListings);
        long workload=ENGINE_RUN_TARGET_BASE_MS+(long)remote*ENGINE_RUN_REMOTE_UNIT_MS;
        return Math.max(ENGINE_RUN_TARGET_MIN_MS,workload);
    }
    /** Live remaining-time estimate. Unlike engineTargetMs this intentionally shrinks as remote
     * candidates settle. Local BGG/classifier work gets a small bounded allowance. */
    public static long engineEtaMs(ObservationSession s){
        if(s==null||engineContentSettled(s))return 0L;
        // A parked current-run link is still paced Vinted work; ETA must not collapse to the local
        // one-minute fallback merely because its durable job has not been materialised yet.
        long remote=(long)Math.max(Math.max(0,s.corePendingListings),Math.max(0,s.coreRemainingListings))*ENGINE_RUN_REMOTE_UNIT_MS;
        long local=s.analysisPendingListings>0?ENGINE_RUN_LOCAL_ETA_MS:0L;
        int unresolved=Math.max(0,s.validListings-s.completeListings-s.reviewListings-s.heldListings);
        if(remote==0&&unresolved>0)local=Math.max(local,ENGINE_RUN_LOCAL_ETA_MS);
        return remote+local;
    }
    /** Timing target only. This can drive ETA/diagnostics but never automatic correctness decisions. */
    public static boolean engineSlaExpired(ObservationSession s,long now){
        return s!=null&&now-s.endAt>=engineTargetMs(s);
    }
    public static boolean engineAutomaticDone(ObservationSession s,long now){
        if(s==null)return true;
        if(now-s.endAt<ENGINE_SESSION_GAP_MS)return false;
        return engineContentSettled(s);
    }

    private static void createOverrides(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS listing_overrides(signature TEXT PRIMARY KEY,item_id TEXT,payload TEXT,excluded INTEGER NOT NULL DEFAULT 0,reason TEXT)");}
    public DealDatabase(Context c){super(c,DB_NAME,null,DB_VERSION);try{setWriteAheadLoggingEnabled(true);}catch(Throwable ignored){}}
    @Override public void onConfigure(SQLiteDatabase db){
        super.onConfigure(db);
        // Radar, queue and UI are separate processes sharing the same WAL database. A transient
        // writer collision should wait briefly instead of immediately faulting the Vinted lane.
        try(Cursor c=db.rawQuery("PRAGMA busy_timeout=8000",null)){if(c.moveToFirst())c.getInt(0);}catch(Throwable ignored){}
    }
    @Override public void onCreate(SQLiteDatabase db){createOverrides(db);
        db.execSQL("CREATE TABLE observations(id INTEGER PRIMARY KEY AUTOINCREMENT,signature TEXT NOT NULL,observed_at INTEGER NOT NULL,vinted_title TEXT,brand TEXT,item_condition TEXT,item_price_cents INTEGER,protected_price_cents INTEGER,favorites INTEGER,analysis_status TEXT,bgg_id TEXT,game_name TEXT,display_name TEXT,rating REAL,geek_rating REAL,bgg_rank INTEGER,voters INTEGER,quality_score INTEGER,tier TEXT,tier_label TEXT,total_cents INTEGER,benchmark_cents INTEGER,offer_cents INTEGER,shipping_cents INTEGER,discount REAL,language_code TEXT,match_reason TEXT,listing_type TEXT,verification_state TEXT,verification_reason TEXT)");
        db.execSQL("CREATE INDEX idx_observations_bgg_time ON observations(bgg_id,observed_at)");
        db.execSQL("CREATE INDEX idx_observations_sig_time ON observations(signature,observed_at)");
        db.execSQL("CREATE TABLE deals(id INTEGER PRIMARY KEY AUTOINCREMENT,signature TEXT NOT NULL UNIQUE,first_seen INTEGER NOT NULL,last_seen INTEGER NOT NULL,seen_count INTEGER NOT NULL DEFAULT 1,vinted_title TEXT,brand TEXT,item_condition TEXT,item_price_cents INTEGER,protected_price_cents INTEGER,favorites INTEGER,analysis_status TEXT,bgg_id TEXT,game_name TEXT,display_name TEXT,rating REAL,geek_rating REAL,bgg_rank INTEGER,voters INTEGER,quality_score INTEGER,tier TEXT,tier_label TEXT,total_cents INTEGER,benchmark_cents INTEGER,offer_cents INTEGER,shipping_cents INTEGER,discount REAL,language_code TEXT,match_reason TEXT,lifecycle TEXT NOT NULL DEFAULT 'ACTIVE',confirmed INTEGER NOT NULL DEFAULT 0,listing_type TEXT,verification_state TEXT,verification_reason TEXT,vinted_url TEXT,resolved_at INTEGER,shipping_verified_cents INTEGER,vinted_item_id TEXT,image_url TEXT,link_confidence INTEGER,link_reason TEXT,published_label TEXT,bgg_image_url TEXT,bgg_categories TEXT,bgg_minplayers INTEGER,bgg_maxplayers INTEGER,bgg_weight REAL,bgg_playtime INTEGER,seller_id TEXT,seller_name TEXT,listing_photos_csv TEXT)");
        db.execSQL("CREATE INDEX idx_deals_tier_last ON deals(tier,last_seen DESC)");
        db.execSQL("CREATE INDEX idx_deals_bgg ON deals(bgg_id)");
        db.execSQL("CREATE INDEX idx_deals_seller ON deals(seller_id)");
        MarketStore.createSchema(db);
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldV,int newV){
        if(oldV<2){safeAlter(db,"ALTER TABLE observations ADD COLUMN listing_type TEXT");safeAlter(db,"ALTER TABLE observations ADD COLUMN verification_state TEXT");safeAlter(db,"ALTER TABLE observations ADD COLUMN verification_reason TEXT");safeAlter(db,"ALTER TABLE deals ADD COLUMN listing_type TEXT");safeAlter(db,"ALTER TABLE deals ADD COLUMN verification_state TEXT");safeAlter(db,"ALTER TABLE deals ADD COLUMN verification_reason TEXT");}
        if(oldV<3){safeAlter(db,"ALTER TABLE deals ADD COLUMN vinted_url TEXT");safeAlter(db,"ALTER TABLE deals ADD COLUMN resolved_at INTEGER");safeAlter(db,"ALTER TABLE deals ADD COLUMN shipping_verified_cents INTEGER");}
        if(oldV<4){safeAlter(db,"ALTER TABLE deals ADD COLUMN vinted_item_id TEXT");safeAlter(db,"ALTER TABLE deals ADD COLUMN image_url TEXT");safeAlter(db,"ALTER TABLE deals ADD COLUMN link_confidence INTEGER");safeAlter(db,"ALTER TABLE deals ADD COLUMN link_reason TEXT");}
        if(oldV<5){safeAlter(db,"ALTER TABLE deals ADD COLUMN published_label TEXT");safeAlter(db,"ALTER TABLE deals ADD COLUMN bgg_image_url TEXT");safeAlter(db,"ALTER TABLE deals ADD COLUMN bgg_categories TEXT");}
        if(oldV<6){safeAlter(db,"ALTER TABLE deals ADD COLUMN seller_id TEXT");safeAlter(db,"ALTER TABLE deals ADD COLUMN seller_name TEXT");safeAlter(db,"ALTER TABLE deals ADD COLUMN listing_photos_csv TEXT");try{db.execSQL("CREATE INDEX idx_deals_seller ON deals(seller_id)");}catch(Exception ignored){}}
        if(oldV<8)createOverrides(db);
        if(oldV<7){safeAlter(db,"ALTER TABLE deals ADD COLUMN bgg_minplayers INTEGER");safeAlter(db,"ALTER TABLE deals ADD COLUMN bgg_maxplayers INTEGER");safeAlter(db,"ALTER TABLE deals ADD COLUMN bgg_weight REAL");safeAlter(db,"ALTER TABLE deals ADD COLUMN bgg_playtime INTEGER");}
        if(oldV<9){MarketStore.createSchema(db);MarketStore.backfillLegacy(db);}
        if(oldV<10){MarketStore.upgradeV9ToV10(db);}
        if(oldV<11){MarketStore.upgradeV10ToV11(db);}
        if(oldV<12){MarketStore.upgradeV11ToV12(db);}
        if(oldV<13){MarketStore.upgradeV12ToV13(db);}
        if(oldV<14){MarketStore.upgradeV13ToV14(db);}
        if(oldV<15){MarketStore.upgradeV14ToV15(db);}
        if(oldV<16){MarketStore.upgradeV15ToV16(db);}
        if(oldV<17){MarketStore.upgradeV16ToV17(db);}
        if(oldV<18){MarketStore.upgradeV17ToV18(db);}
        if(oldV<19){MarketStore.upgradeV18ToV19(db);}
        if(oldV<20){MarketStore.upgradeV19ToV20(db);}
        if(oldV<21){MarketStore.upgradeV20ToV21(db);}
    }
    private static void safeAlter(SQLiteDatabase db,String sql){try{db.execSQL(sql);}catch(Exception ignored){}}

    public synchronized void recordSighting(VintedCard card,ListingClassifier.Result listing,long now){
        if(card==null)return;
        String sig=signature(card);long cutoff=Math.max(0L,now-ENGINE_DUPLICATE_SIGHTING_MS);
        // Persisted dedupe survives :radar process restarts. Re-rendering the same visible Vinted
        // card must not extend the Motore acquisition window or inflate its card count.
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM observations WHERE signature=? AND observed_at>=? LIMIT 1",new String[]{sig,String.valueOf(cutoff)})){if(c.moveToFirst())return;}
        cachedActiveRunAt=0L;ContentValues v=new ContentValues();v.put("signature",sig);v.put("observed_at",now);v.put("vinted_title",card.title);v.put("brand",card.brand);v.put("item_condition",card.condition);v.put("item_price_cents",cents(card.itemPrice));put(v,"protected_price_cents",card.protectedPrice==null?null:cents(card.protectedPrice));put(v,"favorites",card.favorites);v.put("analysis_status","pending");if(listing!=null){v.put("listing_type",listing.type.name());v.put("verification_state",listing.allowPriceModel?"PENDING_ANALYSIS":"BLOCKED_CLASSIFIER");v.put("verification_reason",listing.reason);}getWritableDatabase().insert("observations",null,v);
    }
    public synchronized void recordBlocked(VintedCard c,ListingClassifier.Result l,long n){recordSighting(c,l,n);}
    public synchronized void record(VintedCard card,GameAnalysis a,ListingClassifier.Result listing,long now){if(card==null||a==null)return;String sig=signature(card);boolean anomaly=ListingClassifier.isExtremePriceAnomaly(a);String verify=anomaly?"PRICE_ANOMALY":(!"matched".equals(a.status)?"MATCH_UNCERTAIN":"OK");String reason=anomaly?"Prezzo totale anomalo: verifica espansione/accessorio/annuncio incompleto.":null;if(listing!=null&&listing.type==ListingClassifier.Type.EXPANSION&&"OK".equals(verify)){verify="EXPANSION_CHECK";reason="Espansione: verifica benchmark della stessa edizione.";}DealEvaluator.Evaluation evaluation=DealEvaluator.evaluate(card,a);SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{ContentValues obs=commonValues(card,a,listing,verify,reason);int changed=db.update("observations",obs,"id=(SELECT id FROM observations WHERE signature=? AND analysis_status='pending' AND verification_state='PENDING_ANALYSIS' ORDER BY observed_at DESC LIMIT 1)",new String[]{sig});if(changed==0){obs.put("signature",sig);obs.put("observed_at",now);db.insert("observations",null,obs);}boolean review="PRICE_ANOMALY".equals(verify)||"EXPANSION_CHECK".equals(verify);boolean candidate="matched".equals(a.status)&&DealPolicy.ratingEligible(a.averageRating)&&"OK".equals(verify);if(review)upsertDeal(db,sig,card,a,listing,verify,reason,now,"verify",a.tierLabel);else if(candidate){upsertDeal(db,sig,card,a,listing,verify,reason,now,evaluation.storageTier(),evaluation.label);if(!evaluation.visible())markPriceFiltered(db,sig,now);}db.setTransactionSuccessful();}finally{db.endTransaction();}}
    /** Hunts are intent-first, not resale-first. Preserve an otherwise valid matched listing even
     * when its price is merely average; exact identity/review gates still decide whether it is shown/notified. */
    public synchronized void recordHuntCandidate(VintedCard card,GameAnalysis a,ListingClassifier.Result listing,long now){
        if(card==null||a==null||listing==null||!listing.allowPriceModel||!"matched".equals(a.status)||a.bggId==null)return;
        boolean anomaly=ListingClassifier.isExtremePriceAnomaly(a);
        String verify=anomaly?"PRICE_ANOMALY":(listing.type==ListingClassifier.Type.EXPANSION?"EXPANSION_CHECK":"OK");
        String reason=anomaly?"Prezzo insolitamente basso: verificare che sia il gioco completo.":
                (listing.type==ListingClassifier.Type.EXPANSION?"Espansione: verifica benchmark della stessa edizione.":null);
        DealEvaluator.Evaluation evaluation=DealEvaluator.evaluate(card,a);
        String tier=("OK".equals(verify)&&evaluation.discoverable())?evaluation.storageTier():("OK".equals(verify)?"hunt":"verify");
        upsertDeal(getWritableDatabase(),signature(card),card,a,listing,verify,reason,now,tier,"OK".equals(verify)&&evaluation.discoverable()?evaluation.label:a.tierLabel);
    }

    public synchronized DealRecord recordSellerItem(SellerBundleScanner.SellerItem item,String seller,VintedCard card,GameAnalysis analysis){
        if(analysis==null)return null;
        ListingClassifier.Result listing=ListingClassifier.classify(card);if(!listing.allowPriceModel)return null;
        String sig="vinted:"+item.id;SQLiteDatabase db=getWritableDatabase();try(Cursor existing=db.rawQuery("SELECT signature FROM deals WHERE vinted_item_id=? LIMIT 1",new String[]{item.id})){if(existing.moveToFirst())sig=existing.getString(0);}boolean review=!"matched".equals(analysis.status)||analysis.bggId==null||listing.type==ListingClassifier.Type.EXPANSION||ListingClassifier.isExtremePriceAnomaly(analysis);DealEvaluator.Evaluation evaluation=DealEvaluator.evaluate(card,analysis);
        if(!review&&!evaluation.visible()){upsertDeal(db,sig,card,analysis,listing,"OK",null,System.currentTimeMillis(),"filtered","");markPriceFiltered(db,sig,System.currentTimeMillis());return null;}upsertDeal(db,sig,card,analysis,listing,review?"EXPANSION_CHECK":"OK",review?"Controlla gioco base o espansione":null,System.currentTimeMillis(),review?"verify":evaluation.storageTier(),review?analysis.tierLabel:evaluation.label);
        DealRecord previous=findBySignature(sig);List<String> photos=new ArrayList<>();if(previous!=null&&previous.listingPhotosCsv!=null)photos.addAll(Arrays.asList(previous.listingPhotosCsv.split(",")));if(item.photosCsv!=null&&!item.photosCsv.isEmpty())photos.addAll(Arrays.asList(item.photosCsv.split(",")));if(item.imageUrl!=null&&!item.imageUrl.isEmpty())photos.add(item.imageUrl);applyResolvedLink(sig,item.id,item.url,item.imageUrl,100,"Pagina del venditore verificata",seller,seller,android.text.TextUtils.join(",",PhotoIdentity.unique(photos)),System.currentTimeMillis());if(item.publishedLabel!=null){ContentValues date=new ContentValues();date.put("published_label",item.publishedLabel);db.update("deals",date,"signature=?",new String[]{sig});}return findBySignature(sig);
    }
    private static void upsertDeal(SQLiteDatabase db,String sig,VintedCard card,GameAnalysis a,ListingClassifier.Result l,String verify,String reason,long now,String forcedTier){upsertDeal(db,sig,card,a,l,verify,reason,now,forcedTier,null);}private static void upsertDeal(SQLiteDatabase db,String sig,VintedCard card,GameAnalysis a,ListingClassifier.Result l,String verify,String reason,long now,String forcedTier,String forcedLabel){ContentValues deal=commonValues(card,a,l,verify,reason);deal.put("signature",sig);deal.put("last_seen",now);deal.put("lifecycle","ACTIVE");deal.put("tier",forcedTier);if(forcedLabel!=null)deal.put("tier_label",forcedLabel);Cursor c=db.rawQuery("SELECT id,seen_count,confirmed FROM deals WHERE signature=?",new String[]{sig});if(c.moveToFirst()){deal.put("seen_count",c.getInt(1)+1);deal.put("confirmed",c.getInt(2));db.update("deals",deal,"id=?",new String[]{String.valueOf(c.getLong(0))});}else{deal.put("first_seen",now);deal.put("seen_count",1);deal.put("confirmed",0);db.insert("deals",null,deal);}c.close();applyUserOverride(db,sig);}private static void markPriceFiltered(SQLiteDatabase db,String sig,long now){ContentValues v=new ContentValues();v.put("lifecycle","REMOVED");v.put("tier","filtered");v.put("tier_label","");v.put("verification_state","PRICE_FILTERED");v.put("verification_reason","Prezzo automaticamente escluso dal filtro convenienza");v.put("last_seen",now);db.update("deals",v,"signature=?",new String[]{sig});}

    public synchronized List<DealRecord> getDeals(String filter,int limit){
        String where="lifecycle='ACTIVE'";List<String>a=new ArrayList<>();boolean trusted="trusted".equals(filter)||"trusted_any_price".equals(filter);
        if("hot".equals(filter)){where+=" AND tier=?";a.add("hot");}
        else if("good".equals(filter)){where+=" AND tier=?";a.add("good");}
        else if("offer".equals(filter)){where+=" AND tier=?";a.add("offer");}
        else if("fair".equals(filter)){where+=" AND tier=?";a.add("fair");}
        else if("verify".equals(filter)){where+=" AND tier=?";a.add("verify");}
        else if("trusted".equals(filter))where+=" AND tier IN ('hot','good','offer')";
        else if("trusted_any_price".equals(filter))where+=" AND tier IN ('hot','good','offer','fair','insufficient','hunt')";
        else if(!"all_with_review".equals(filter))where+=" AND tier IN ('hot','good','offer','fair','insufficient','hunt')";
        if(!"all_with_review".equals(filter))where+=" AND rating IS NOT NULL AND rating>=6.0";
        if(trusted){
            where+=" AND bgg_id IS NOT NULL AND bgg_id<>'' AND vinted_item_id IS NOT NULL AND vinted_item_id<>'' AND vinted_url IS NOT NULL AND vinted_url<>''"+
                    " AND COALESCE(verification_state,'') IN ('OK','USER_CONFIRMED')"+
                    " AND COALESCE(listing_type,'') IN ('BASE_GAME','EXPANSION','GAME')"+
                    " AND EXISTS (SELECT 1 FROM market_listings l JOIN games g ON g.id=l.game_id WHERE "+
                    "(l.legacy_signature=deals.signature OR (deals.vinted_item_id IS NOT NULL AND l.vinted_item_id=deals.vinted_item_id)) "+
                    "AND l.lifecycle='ACTIVE' AND l.enrichment_state IN ('COMPLETE','CORE_COMPLETE') AND l.match_state='MATCHED' "+
                    "AND COALESCE(l.manual_review_required,0)=0 AND g.match_state='MATCHED' AND g.bgg_id=deals.bgg_id "+
                    "AND g.database_visible=1 AND g.rating IS NOT NULL AND g.rating>=6.0 "+
                    "AND NOT EXISTS(SELECT 1 FROM processing_jobs j WHERE j.listing_id=l.id AND (j.job_type<>'VINTED_DEEP_ENRICHMENT' OR j.source='MANUAL_RECOVERY') AND j.state IN ('PENDING','PROCESSING','FAILED_RETRYABLE')))";
        }
        a.add(String.valueOf(Math.max(1,limit)));Cursor c=getReadableDatabase().rawQuery("SELECT "+COLS+" FROM deals WHERE "+where+" ORDER BY last_seen DESC LIMIT ?",a.toArray(new String[0]));List<DealRecord>out=new ArrayList<>();while(c.moveToNext())out.add(readDeal(c));c.close();return out;
    }

    public synchronized List<DealRecord> getDealsByBggId(String bggId,int limit){List<DealRecord> out=new ArrayList<>();if(bggId==null||bggId.trim().isEmpty())return out;try(Cursor c=getReadableDatabase().rawQuery("SELECT "+COLS+" FROM deals WHERE lifecycle='ACTIVE' AND bgg_id=? ORDER BY last_seen DESC LIMIT ?",new String[]{bggId.trim(),String.valueOf(Math.max(1,limit))})){while(c.moveToNext())out.add(readDeal(c));}return out;}
    public synchronized DealRecord findByTitlePrice(String title,int price){Cursor c=getReadableDatabase().rawQuery("SELECT "+COLS+" FROM deals WHERE LOWER(vinted_title)=LOWER(?) AND item_price_cents=? LIMIT 1",new String[]{title==null?"":title,String.valueOf(price)});DealRecord d=c.moveToFirst()?readDeal(c):null;c.close();return d;}
    public synchronized DealRecord findBySignature(String sig){if(sig==null||sig.trim().isEmpty())return null;Cursor c=getReadableDatabase().rawQuery("SELECT "+COLS+" FROM deals WHERE signature=? LIMIT 1",new String[]{sig});DealRecord d=c.moveToFirst()?readDeal(c):null;c.close();return d;}
    public synchronized void markSold(String signature){if(signature==null||signature.isEmpty())return;ContentValues v=new ContentValues();v.put("lifecycle","SOLD");v.put("verification_state","SOLD");v.put("verification_reason","Articolo venduto su Vinted");v.put("last_seen",System.currentTimeMillis());getWritableDatabase().update("deals",v,"signature=?",new String[]{signature});}
    /** User-confirmed archive for listings that can no longer be resolved on Vinted. History stays in SQLite, but the card leaves the active catalog. */
    public synchronized void markUnavailable(String signature,String reason){if(signature==null||signature.isEmpty())return;ContentValues v=new ContentValues();v.put("lifecycle","REMOVED");v.put("verification_state","VINTED_UNAVAILABLE");v.put("verification_reason",reason==null?"Pagina Vinted non disponibile":reason);v.put("last_seen",System.currentTimeMillis());getWritableDatabase().update("deals",v,"signature=?",new String[]{signature});}

    public synchronized int countVintedIncomplete(){String sql="SELECT COUNT(*) FROM deals WHERE lifecycle='ACTIVE' AND ((vinted_url IS NULL OR vinted_url='') OR (published_label IS NULL OR published_label='') OR (seller_id IS NULL OR seller_id=''))";try(Cursor c=getReadableDatabase().rawQuery(sql,null)){return c.moveToFirst()?c.getInt(0):0;}}
    public synchronized void updateUserFields(String signature,String languageCode,String sellerName,String publishedLabel){if(signature==null||signature.isEmpty())return;ContentValues v=new ContentValues();if(languageCode==null||languageCode.trim().isEmpty())v.putNull("language_code");else v.put("language_code",languageCode.trim().toUpperCase(Locale.ROOT));if(sellerName==null||sellerName.trim().isEmpty())v.putNull("seller_name");else v.put("seller_name",sellerName.trim());if(publishedLabel==null||publishedLabel.trim().isEmpty())v.putNull("published_label");else v.put("published_label",publishedLabel.trim());v.put("resolved_at",System.currentTimeMillis());getWritableDatabase().update("deals",v,"signature=?",new String[]{signature});}
    public synchronized DealRecord findByVintedTitle(String title){Cursor c=getReadableDatabase().rawQuery("SELECT "+COLS+" FROM deals WHERE LOWER(vinted_title)=LOWER(?) AND lifecycle='ACTIVE' ORDER BY last_seen DESC LIMIT 1",new String[]{title==null?"":title});DealRecord d=c.moveToFirst()?readDeal(c):null;c.close();return d;}
    public synchronized DealRecord findByBggId(String bggId){if(bggId==null||bggId.isEmpty())return null;Cursor c=getReadableDatabase().rawQuery("SELECT "+COLS+" FROM deals WHERE bgg_id=? AND lifecycle='ACTIVE' ORDER BY last_seen DESC LIMIT 1",new String[]{bggId});DealRecord d=c.moveToFirst()?readDeal(c):null;c.close();return d;}
    public synchronized DealRecord findByVintedItemId(String itemId){if(itemId==null||itemId.isEmpty())return null;Cursor c=getReadableDatabase().rawQuery("SELECT "+COLS+" FROM deals WHERE vinted_item_id=? AND lifecycle='ACTIVE' ORDER BY last_seen DESC LIMIT 1",new String[]{itemId});DealRecord d=c.moveToFirst()?readDeal(c):null;c.close();return d;}
    /**
     * Creates the legacy Catalog row only after the canonical graph already proves the complete
     * product identity. The original observation remains the source for product type and price
     * evidence; async BGG/Vinted enrichment is never allowed to invent either.
     */
    public synchronized String materializeCanonicalDeal(long listingId){
        if(listingId<=0)return "INELIGIBLE";
        SQLiteDatabase db=getWritableDatabase();long now=System.currentTimeMillis();
        String sql="SELECT COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint),l.first_seen,l.last_seen,"+
                "l.vinted_title,l.brand,l.item_condition,l.current_price_cents,l.protected_price_cents,l.favorites,"+
                "l.seller_id,l.seller_name,l.vinted_item_id,l.vinted_url,l.image_url,l.listing_photos_csv,l.published_label,l.language_code,"+
                "g.bgg_id,g.canonical_name,g.rating,g.bgg_rank,g.voters,g.image_url,g.categories,g.min_players,g.max_players,g.weight,g.playtime,"+
                "o.item_price_cents,o.quality_score,o.tier_label,o.total_cents,o.benchmark_cents,o.offer_cents,o.shipping_cents,o.discount,o.match_reason,o.listing_type,o.verification_state,"+
                "(SELECT COUNT(*) FROM observations ox WHERE ox.signature=COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint)) "+
                "FROM market_listings l JOIN games g ON g.id=l.game_id "+
                "JOIN observations o ON o.id=(SELECT ox.id FROM observations ox WHERE ox.signature=COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) ORDER BY ox.observed_at DESC,ox.id DESC LIMIT 1) "+
                "WHERE l.id=? AND l.lifecycle='ACTIVE' AND l.enrichment_state IN ('COMPLETE','CORE_COMPLETE') AND l.match_state='MATCHED' "+
                "AND COALESCE(l.manual_review_required,0)=0 AND l.vinted_item_id IS NOT NULL AND l.vinted_item_id<>'' AND l.vinted_url IS NOT NULL AND l.vinted_url<>'' "+
                "AND g.match_state='MATCHED' AND g.database_visible=1 AND g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND g.rating IS NOT NULL AND g.rating>=? "+
                "AND NOT EXISTS(SELECT 1 FROM processing_jobs j WHERE j.listing_id=l.id AND (j.job_type<>'VINTED_DEEP_ENRICHMENT' OR j.source='MANUAL_RECOVERY') AND j.state IN ('PENDING','PROCESSING','FAILED_RETRYABLE'))";
        try(Cursor c=db.rawQuery(sql,new String[]{String.valueOf(listingId),String.valueOf(DealPolicy.MIN_BGG_RATING)})){
            if(!c.moveToFirst()){recordCatalogBridgeOutcome(db,"INELIGIBLE",listingId,now);return "INELIGIBLE";}
            String signature=c.getString(0),itemId=c.getString(11);
            if(TextUtilsCompat.empty(signature)||TextUtilsCompat.empty(itemId)){recordCatalogBridgeOutcome(db,"INELIGIBLE",listingId,now);return "INELIGIBLE";}
            try(Cursor duplicate=db.rawQuery("SELECT 1 FROM deals WHERE signature=? OR (vinted_item_id IS NOT NULL AND vinted_item_id=?) LIMIT 1",new String[]{signature,itemId})){
                if(duplicate.moveToFirst()){recordCatalogBridgeOutcome(db,"EXISTING",listingId,now);return "EXISTING";}
            }
            DealRecord draft=new DealRecord();draft.signature=signature;draft.firstSeen=c.getLong(1);draft.lastSeen=c.getLong(2);
            draft.vintedTitle=c.getString(3);draft.brand=c.getString(4);draft.condition=c.getString(5);draft.itemPriceCents=c.getInt(6);
            draft.protectedPriceCents=c.isNull(7)?null:c.getInt(7);draft.favorites=c.isNull(8)?null:c.getInt(8);draft.sellerId=c.getString(9);draft.sellerName=c.getString(10);
            draft.vintedItemId=itemId;draft.vintedUrl=c.getString(12);draft.imageUrl=c.getString(13);draft.listingPhotosCsv=c.getString(14);draft.publishedLabel=c.getString(15);draft.languageCode=c.getString(16);
            draft.bggId=c.getString(17);draft.gameName=c.getString(18);draft.displayName=draft.gameName;draft.rating=c.getDouble(19);draft.rank=c.isNull(20)?null:c.getInt(20);draft.voters=c.isNull(21)?null:c.getInt(21);
            draft.bggImageUrl=c.getString(22);draft.bggCategories=c.getString(23);draft.minPlayers=c.isNull(24)?null:c.getInt(24);draft.maxPlayers=c.isNull(25)?null:c.getInt(25);draft.weight=c.isNull(26)?null:c.getDouble(26);draft.playtime=c.isNull(27)?null:c.getInt(27);
            int observedPrice=c.getInt(28);boolean samePrice=observedPrice==draft.itemPriceCents;
            draft.qualityScore=c.isNull(29)?null:c.getInt(29);draft.tierLabel=c.getString(30);draft.benchmarkCents=c.isNull(32)?null:c.getInt(32);draft.shippingCents=c.isNull(34)?null:c.getInt(34);
            draft.totalCents=samePrice?(c.isNull(31)?null:c.getInt(31)):(draft.protectedPriceCents!=null&&draft.shippingCents!=null?draft.protectedPriceCents+draft.shippingCents:null);
            draft.offerCents=samePrice?(c.isNull(33)?null:c.getInt(33)):null;draft.discount=samePrice?(c.isNull(35)?null:c.getDouble(35)):null;draft.matchReason=c.getString(36);
            draft.listingType=c.getString(37);draft.verificationState=c.getString(38);draft.lifecycle="ACTIVE";draft.analysisStatus="matched";
            CatalogBridgePolicy.Result decision=CatalogBridgePolicy.decide(draft,true,false,false);
            if(!decision.publish){recordCatalogBridgeOutcome(db,decision.reason,listingId,now);return decision.reason;}
            ContentValues v=canonicalDealValues(draft,decision,now,Math.max(1,c.getInt(39)));
            long inserted=db.insertWithOnConflict("deals",null,v,SQLiteDatabase.CONFLICT_IGNORE);
            String outcome=inserted>0?"MATERIALIZED":"EXISTING";recordCatalogBridgeOutcome(db,outcome,listingId,now);
            if(inserted>0)applyUserOverride(db,signature);return outcome;
        }
    }
    private static ContentValues canonicalDealValues(DealRecord d,CatalogBridgePolicy.Result decision,long now,int seenCount){
        ContentValues v=new ContentValues();v.put("signature",d.signature);v.put("first_seen",d.firstSeen>0?d.firstSeen:now);v.put("last_seen",d.lastSeen>0?d.lastSeen:now);v.put("seen_count",seenCount);
        put(v,"vinted_title",d.vintedTitle);put(v,"brand",d.brand);put(v,"item_condition",d.condition);v.put("item_price_cents",d.itemPriceCents);put(v,"protected_price_cents",d.protectedPriceCents);put(v,"favorites",d.favorites);
        v.put("analysis_status","matched");put(v,"bgg_id",d.bggId);put(v,"game_name",d.gameName);put(v,"display_name",d.displayName);put(v,"rating",d.rating);put(v,"bgg_rank",d.rank);put(v,"voters",d.voters);put(v,"quality_score",d.qualityScore);
        v.put("tier",decision.tier);put(v,"tier_label",decision.label);put(v,"total_cents",d.totalCents);put(v,"benchmark_cents",d.benchmarkCents);put(v,"offer_cents",d.offerCents);put(v,"shipping_cents",d.shippingCents);put(v,"discount",d.discount);
        put(v,"language_code",d.languageCode);put(v,"match_reason",d.matchReason);v.put("lifecycle","ACTIVE");v.put("confirmed",0);v.put("listing_type",d.listingType);v.put("verification_state","OK");v.putNull("verification_reason");
        put(v,"vinted_url",d.vintedUrl);v.put("resolved_at",now);put(v,"vinted_item_id",d.vintedItemId);put(v,"image_url",d.imageUrl);v.put("link_confidence",100);v.put("link_reason","Sincronizzato dal record canonico verificato");
        put(v,"published_label",d.publishedLabel);put(v,"bgg_image_url",d.bggImageUrl);put(v,"bgg_categories",d.bggCategories);put(v,"bgg_minplayers",d.minPlayers);put(v,"bgg_maxplayers",d.maxPlayers);put(v,"bgg_weight",d.weight);put(v,"bgg_playtime",d.playtime);
        put(v,"seller_id",d.sellerId);put(v,"seller_name",d.sellerName);put(v,"listing_photos_csv",d.listingPhotosCsv);return v;
    }
    private static void recordCatalogBridgeOutcome(SQLiteDatabase db,String outcome,long listingId,long now){
        String safe=TextUtilsCompat.empty(outcome)?"UNKNOWN":outcome;String key="catalog_bridge_"+safe.toLowerCase(Locale.ROOT);
        long count=0;try(Cursor c=db.rawQuery("SELECT value FROM queue_controls WHERE name=?",new String[]{key})){if(c.moveToFirst())count=c.getLong(0);}
        ContentValues v=new ContentValues();v.put("name",key);v.put("value",count+1);v.put("updated_at",now);v.put("text_value","listing="+listingId+";outcome="+safe);db.insertWithOnConflict("queue_controls",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        ContentValues last=new ContentValues();last.put("name","catalog_bridge_last");last.put("value",listingId);last.put("updated_at",now);last.put("text_value","outcome="+safe);db.insertWithOnConflict("queue_controls",null,last,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public synchronized String catalogBridgeBreakdown(){
        SQLiteDatabase db=getReadableDatabase();return "bridgeMaterialized="+controlValue(db,"catalog_bridge_materialized")+
                "; bridgeExisting="+controlValue(db,"catalog_bridge_existing")+"; bridgeIneligible="+controlValue(db,"catalog_bridge_ineligible")+
                "; bridgePriceRejected="+controlValue(db,"catalog_bridge_price_rejected")+"; bridgeLast="+controlText(db,"catalog_bridge_last");
    }
    private static long controlValue(SQLiteDatabase db,String name){try(Cursor c=db.rawQuery("SELECT value FROM queue_controls WHERE name=?",new String[]{name})){return c.moveToFirst()?c.getLong(0):0L;}}
    private static String controlText(SQLiteDatabase db,String name){try(Cursor c=db.rawQuery("SELECT text_value FROM queue_controls WHERE name=?",new String[]{name})){return c.moveToFirst()?String.valueOf(c.getString(0)):"";}}
    private static final class TextUtilsCompat{static boolean empty(String value){return value==null||value.trim().isEmpty();}}
    public synchronized void applySellerHint(String signature,String sellerId,String sellerName){if(signature==null||sellerId==null||sellerId.isEmpty())return;ContentValues v=new ContentValues();v.put("seller_id",sellerId);if(sellerName!=null&&!sellerName.isEmpty())v.put("seller_name",sellerName);getWritableDatabase().update("deals",v,"signature=? AND (seller_id IS NULL OR seller_id='' OR seller_id=?)",new String[]{signature,sellerId});}
    public synchronized void updateSellerNameHint(String signature,String sellerName){if(signature==null||signature.isEmpty())return;ContentValues v=new ContentValues();if(sellerName==null||sellerName.trim().isEmpty())v.putNull("seller_name");else v.put("seller_name",sellerName.trim());getWritableDatabase().update("deals",v,"signature=?",new String[]{signature});}
    public synchronized int countDeals(String tier){String w="lifecycle='ACTIVE' AND (rating IS NULL OR rating>=6.0)";String[]a=null;if(tier!=null){w+=" AND tier=?";a=new String[]{tier};}Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM deals WHERE "+w,a);int n=c.moveToFirst()?c.getInt(0):0;c.close();return n;}
    public synchronized int countObservationsSince(long since){Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM observations WHERE observed_at>=?",new String[]{String.valueOf(since)});int n=c.moveToFirst()?c.getInt(0):0;c.close();return n;}
    public synchronized int countPendingObservationsSince(long since){Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM observations WHERE observed_at>=? AND analysis_status='pending' AND verification_state='PENDING_ANALYSIS'",new String[]{String.valueOf(since)});int n=c.moveToFirst()?c.getInt(0):0;c.close();return n;}
    /** UX timeline: clusters observation bursts into human sessions separated by >=3 minutes. */
    public synchronized List<ObservationSession> recentObservationSessions(long since,int limit){
        List<ObservationSession> out=new ArrayList<>(); if(limit<=0)return out;since=clampEngineStart(since);
        final long gap=ENGINE_SESSION_GAP_MS; ObservationSession current=null; long previous=-1L; Set<String> unique=null,pending=null,stateSeen=null;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT observed_at,signature,analysis_status,verification_state FROM observations WHERE observed_at>=? ORDER BY observed_at DESC LIMIT 2400",new String[]{String.valueOf(since)})){
            while(c.moveToNext()){long at=c.getLong(0);String sig=c.getString(1);boolean isPending="pending".equals(c.getString(2))&&"PENDING_ANALYSIS".equals(c.getString(3));
                if(current==null||(previous>0&&previous-at>=gap)){
                    if(current!=null){current.uniqueListings=unique.size();current.analysisPendingListings=pending.size();current.pendingListings=pending.size();fillEngineCounts(current);out.add(current);if(out.size()>=limit)break;}
                    current=new ObservationSession(at);unique=new HashSet<>();pending=new HashSet<>();stateSeen=new HashSet<>();
                }
                current.startAt=Math.min(current.startAt,at);current.endAt=Math.max(current.endAt,at);current.observations++;if(sig!=null){unique.add(sig);if(stateSeen.add(sig)&&isPending)pending.add(sig);}previous=at;
            }
        }
        if(current!=null&&out.size()<limit){current.uniqueListings=unique.size();current.analysisPendingListings=pending.size();current.pendingListings=pending.size();fillEngineCounts(current);out.add(current);}
        return out;
    }
    public synchronized Set<String> observationSignatures(long startAt,long endAt){
        Set<String> out=new HashSet<>();startAt=clampEngineStart(startAt);if(endAt<startAt)return out;try(Cursor c=getReadableDatabase().rawQuery("SELECT DISTINCT signature FROM observations WHERE observed_at>=? AND observed_at<=?",new String[]{String.valueOf(startAt),String.valueOf(endAt)})){while(c.moveToNext()){String sig=c.getString(0);if(sig!=null&&!sig.isEmpty())out.add(sig);}}return out;
    }

    /** Engine-facing run metrics. A card is considered ready when its BGG identity and exact Vinted
     * identity are both usable and no automatic/review job is still open for that listing. Optional
     * metadata such as publication time may remain unavailable after best-effort enrichment without
     * keeping the whole scroll permanently "unfinished". */
    private int[] engineRangeCounts(long startAt,long endAt){
        startAt=clampEngineStart(startAt);if(endAt<startAt)return new int[7];
        String eligible="l.id IS NOT NULL AND l.lifecycle='ACTIVE' AND l.enrichment_state NOT IN ('AUTO_EXCLUDED','AUTO_FILTERED') AND g.id IS NOT NULL AND g.database_visible=1 AND g.rating>=6.0";
        String bgg=eligible+" AND g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND g.match_state='MATCHED'";
        String vinted=bgg+" AND l.vinted_item_id IS NOT NULL AND l.vinted_item_id<>'' AND l.vinted_url IS NOT NULL AND l.vinted_url<>''";
        // Actionable review must describe the inbox the user can actually open. Historical/trust
        // holds stay non-publishable, but must not masquerade as a question for the user.
        String attention="("+eligible+" AND (COALESCE(l.manual_review_required,0)=1 OR (g.match_state='BGG_MATCH_REVIEW' AND (g.bgg_id IS NULL OR g.bgg_id=''))))";
        String trustHold="("+eligible+" AND NOT "+attention+" AND (l.enrichment_state IN ('NEEDS_REVIEW','LOCAL_ONLY') OR l.match_state='BGG_VARIANT_REVIEW' OR COALESCE(d.verification_state,'') IN ('BGG_VARIANT_REVIEW','MATCH_UNCERTAIN','PRICE_ANOMALY','EXPANSION_CHECK')))";
        String ready=vinted+" AND l.enrichment_state IN ('COMPLETE','CORE_COMPLETE') AND l.match_state='MATCHED' AND NOT "+attention+" AND NOT "+trustHold+" AND NOT EXISTS(SELECT 1 FROM processing_jobs j WHERE j.listing_id=l.id AND (j.job_type<>'VINTED_DEEP_ENRICHMENT' OR j.source='MANUAL_RECOVERY') AND j.state IN ('PENDING','PROCESSING','FAILED_RETRYABLE'))";
        // Product truth, independent from queue materialisation: a BGG-ready listing whose exact
        // Vinted id/url is still missing remains remote work even while temporarily DEFERRED_LINK. Explicitly
        // LOCAL_ONLY listings count as non-published holds, never as missing remote jobs; otherwise
        // price-filtered historical observations can monopolize the Motore cursor indefinitely.
        String coreRemaining=bgg+" AND NOT "+attention+" AND NOT "+trustHold+" AND (l.vinted_item_id IS NULL OR l.vinted_item_id='' OR l.vinted_url IS NULL OR l.vinted_url='')";
        String sql="SELECT COUNT(DISTINCT CASE WHEN "+eligible+" THEN l.id END),"+
                "COUNT(DISTINCT CASE WHEN "+bgg+" THEN l.id END),"+
                "COUNT(DISTINCT CASE WHEN "+vinted+" THEN l.id END),"+
                "COUNT(DISTINCT CASE WHEN "+ready+" THEN l.id END),"+
                "COUNT(DISTINCT CASE WHEN "+attention+" THEN l.id END),"+
                "COUNT(DISTINCT CASE WHEN "+trustHold+" THEN l.id END),"+
                "COUNT(DISTINCT CASE WHEN "+coreRemaining+" THEN l.id END) "+
                "FROM observations o LEFT JOIN market_listings l ON COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint)=o.signature "+
                "LEFT JOIN games g ON g.id=l.game_id LEFT JOIN deals d ON d.signature=o.signature "+
                "WHERE o.observed_at>=? AND o.observed_at<=?";
        int[] out=new int[7];try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{String.valueOf(startAt),String.valueOf(endAt)})){if(c.moveToFirst())for(int i=0;i<7;i++)out[i]=c.isNull(i)?0:c.getInt(i);}return out;
    }

    private void fillEngineCounts(ObservationSession s){
        if(s==null)return;int[] n=engineRangeCounts(s.startAt,s.endAt);s.validListings=n[0];s.bggMatchedListings=n[1];s.vintedLinkedListings=n[2];s.completeListings=n[3];s.reviewListings=n[4];s.heldListings=n[5];s.coreRemainingListings=n[6];
        // Raw observation rows are historical telemetry and older builds could leave duplicate
        // PENDING_ANALYSIS rows behind for one signature. Product progress must follow the current
        // canonical listing state, otherwise an already-analysed card can keep an old job alive.
        String pendingSql="SELECT COUNT(DISTINCT l.id) FROM observations o JOIN market_listings l ON COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint)=o.signature "+
                "WHERE o.observed_at>=? AND o.observed_at<=? AND l.lifecycle='ACTIVE' AND l.enrichment_state='PENDING_ANALYSIS'";
        try(Cursor c=getReadableDatabase().rawQuery(pendingSql,new String[]{String.valueOf(s.startAt),String.valueOf(s.endAt)})){if(c.moveToFirst())s.analysisPendingListings=c.getInt(0);}
        String coreSql="SELECT COUNT(DISTINCT l.id),COUNT(DISTINCT CASE WHEN j.state IN ('PENDING','PROCESSING','FAILED_RETRYABLE') THEN l.id END) "+
                "FROM observations o JOIN market_listings l ON COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint)=o.signature "+
                "JOIN processing_jobs j ON j.listing_id=l.id AND j.job_type=? WHERE o.observed_at>=? AND o.observed_at<=?";
        try(Cursor c=getReadableDatabase().rawQuery(coreSql,new String[]{MarketStore.JOB_VINTED,String.valueOf(s.startAt),String.valueOf(s.endAt)})){if(c.moveToFirst()){s.coreWorkListings=c.getInt(0);s.corePendingListings=c.getInt(1);}}
        s.pendingListings=Math.max(0,s.validListings-s.completeListings-s.reviewListings-s.heldListings)+s.analysisPendingListings;
    }

    public synchronized ObservationSession latestObservationSession(){List<ObservationSession> x=recentObservationSessions(System.currentTimeMillis()-7L*24L*60L*60_000L,1);return x.isEmpty()?null:x.get(0);}

    private synchronized long engineRunCursorStart(){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT value FROM queue_controls WHERE name='engine_run_cursor_start' LIMIT 1",null)){return c.moveToFirst()?Math.max(0L,c.getLong(0)):0L;}
        catch(Throwable ignored){return 0L;}
    }
    public synchronized void invalidateActiveObservationSessionCache(){cachedActiveRun=null;cachedActiveRunAt=0L;}

    /** Fair round-robin ownership. Without a cursor, the oldest unfinished run wins. When MarketStore
     * yields a large run, the cursor points at exactly one next unfinished run; after that run settles
     * the selection naturally wraps to the oldest unfinished work. No listing is reclassified here. */
    public synchronized ObservationSession activeObservationSession(){
        long now=System.currentTimeMillis();
        if(cachedActiveRunAt>0&&now-cachedActiveRunAt<ACTIVE_RUN_CACHE_MS)return cachedActiveRun;
        List<ObservationSession> sessions=recentObservationSessions(now-7L*24L*60L*60_000L,80);ObservationSession oldest=null,cursorRun=null;
        long cursorStart=engineRunCursorStart();
        for(int i=sessions.size()-1;i>=0;i--){
            ObservationSession candidate=sessions.get(i);if(engineAutomaticDone(candidate,now))continue;
            if(oldest==null)oldest=candidate;
            if(cursorStart>0&&candidate.startAt==cursorStart)cursorRun=candidate;
        }
        ObservationSession active=cursorRun!=null?cursorRun:oldest;
        cachedActiveRun=active;cachedActiveRunAt=now;return active;
    }

    /** Next unfinished run in chronological round-robin order, wrapping after the newest run. */
    public synchronized ObservationSession nextUnfinishedObservationSessionAfter(long currentStart,long now){
        List<ObservationSession> sessions=recentObservationSessions(now-7L*24L*60L*60_000L,80);ObservationSession next=null,wrap=null;
        for(ObservationSession candidate:sessions){
            if(candidate.startAt==currentStart||engineAutomaticDone(candidate,now))continue;
            if(wrap==null||candidate.startAt<wrap.startAt)wrap=candidate;
            if(candidate.startAt>currentStart&&(next==null||candidate.startAt<next.startAt))next=candidate;
        }
        return next!=null?next:wrap;
    }

    /** UI-facing count stays intentionally lightweight. It counts newly acquired sessions after the
     * current owner using timestamps only; older yielded work is shown as DEFERRED in run history and
     * diagnostics instead of making the overview rebuild every session's joined state. */
    public synchronized int waitingObservationSessionCount(){
        ObservationSession active=activeObservationSession();if(active==null)return 0;
        int sessions=0;long previous=-1L;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT observed_at FROM observations WHERE observed_at>? ORDER BY observed_at ASC",new String[]{String.valueOf(active.endAt)})){
            while(c.moveToNext()){long at=c.getLong(0);if(previous<0||at-previous>=ENGINE_SESSION_GAP_MS)sessions++;previous=at;}
        }
        return sessions;
    }

    public synchronized boolean isObservationSessionWaiting(ObservationSession session){
        if(session==null)return false;long now=System.currentTimeMillis();ObservationSession active=activeObservationSession();
        return active!=null&&session.startAt!=active.startAt&&!engineAutomaticDone(session,now);
    }

    public synchronized boolean isObservationSessionDeferred(ObservationSession session){
        if(session==null)return false;long now=System.currentTimeMillis();ObservationSession active=activeObservationSession();
        return active!=null&&session.startAt<active.startAt&&!engineAutomaticDone(session,now);
    }

    public synchronized List<ObservationSession> observationSessionsBetween(long startAt,long endAt,int limit){
        List<ObservationSession> out=new ArrayList<>();if(limit<=0)return out;startAt=clampEngineStart(startAt);if(endAt<startAt)return out;final long gap=ENGINE_SESSION_GAP_MS;ObservationSession current=null;long previous=-1L;Set<String> unique=null;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT observed_at,signature FROM observations WHERE observed_at>=? AND observed_at<=? ORDER BY observed_at DESC LIMIT 4000",new String[]{String.valueOf(startAt),String.valueOf(endAt)})){
            while(c.moveToNext()){long at=c.getLong(0);String sig=c.getString(1);if(current==null||(previous>0&&previous-at>=gap)){if(current!=null){current.uniqueListings=unique.size();fillEngineCounts(current);out.add(current);if(out.size()>=limit)break;}current=new ObservationSession(at);unique=new HashSet<>();}
                current.startAt=Math.min(current.startAt,at);current.endAt=Math.max(current.endAt,at);current.observations++;if(sig!=null)unique.add(sig);previous=at;}
        }
        if(current!=null&&out.size()<limit){current.uniqueListings=unique.size();fillEngineCounts(current);out.add(current);}return out;
    }

    /** Count only the temporal boundaries needed by the daily overview. Do not materialize and
     * individually join 100 session detail rows just to display a daily session count: that
     * old N+1 path amplified SQLite contention and CPU while :ui, :radar and queue were active. */
    private int countObservationBursts(long startAt,long endAt){
        int sessions=0;long previous=-1L;
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT observed_at FROM observations WHERE observed_at>=? AND observed_at<=? ORDER BY observed_at ASC",
                new String[]{String.valueOf(startAt),String.valueOf(endAt)})){
            while(c.moveToNext()){
                long at=c.getLong(0);
                if(previous<0||at-previous>=ENGINE_SESSION_GAP_MS)sessions++;
                previous=at;
            }
        }
        return sessions;
    }

    public synchronized List<ObservationDay> recentObservationDays(int days){
        List<ObservationDay> out=new ArrayList<>();Calendar cal=Calendar.getInstance();cal.set(Calendar.HOUR_OF_DAY,0);cal.set(Calendar.MINUTE,0);cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);
        long epoch=engineEpochStart();for(int i=0;i<Math.max(1,days);i++){long dayStart=cal.getTimeInMillis(),end=dayStart+24L*60L*60_000L-1;if(epoch>0&&end<epoch){cal.add(Calendar.DAY_OF_YEAR,-1);continue;}long start=Math.max(dayStart,epoch);int observations=0,unique=0;try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*),COUNT(DISTINCT signature) FROM observations WHERE observed_at>=? AND observed_at<=?",new String[]{String.valueOf(start),String.valueOf(end)})){if(c.moveToFirst()){observations=c.getInt(0);unique=c.getInt(1);}}if(observations>0){ObservationDay d=new ObservationDay(start,end);d.observations=observations;d.uniqueListings=unique;d.sessions=countObservationBursts(start,end);int[] n=engineRangeCounts(start,end);d.validListings=n[0];d.bggMatchedListings=n[1];d.vintedLinkedListings=n[2];d.completeListings=n[3];d.reviewListings=n[4];d.heldListings=n[5];out.add(d);}cal.add(Calendar.DAY_OF_YEAR,-1);}
        return out;
    }

    public synchronized List<EngineRunItem> engineRunItems(long startAt,long endAt,String filter,int limit){
        List<EngineRunItem> out=new ArrayList<>();startAt=clampEngineStart(startAt);if(endAt<startAt)return out;String mode=filter==null?"all":filter;
        String sql="SELECT l.id,COALESCE(l.game_id,0),COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint),"+
                "COALESCE(l.vinted_title,''),COALESCE(g.canonical_name,''),COALESCE(g.bgg_id,''),"+
                "COALESCE(l.image_url,''),COALESCE(l.enrichment_state,''),COALESCE(l.match_state,''),COALESCE(g.match_state,''),"+
                "COALESCE(l.published_label,''),COALESCE(l.language_code,''),l.current_price_cents,"+
                "CASE WHEN g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND g.match_state='MATCHED' THEN 1 ELSE 0 END,"+
                "CASE WHEN l.vinted_item_id IS NOT NULL AND l.vinted_item_id<>'' AND l.vinted_url IS NOT NULL AND l.vinted_url<>'' THEN 1 ELSE 0 END,"+
                "CASE WHEN l.enrichment_state='COMPLETE' AND g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND g.match_state='MATCHED' "+
                "AND l.vinted_item_id IS NOT NULL AND l.vinted_item_id<>'' AND l.vinted_url IS NOT NULL AND l.vinted_url<>'' "+
                "AND l.match_state='MATCHED' AND COALESCE(l.manual_review_required,0)=0 AND l.enrichment_state<>'NEEDS_REVIEW' "+
                "AND NOT EXISTS(SELECT 1 FROM processing_jobs j WHERE j.listing_id=l.id AND j.state IN ('PENDING','PROCESSING','FAILED_RETRYABLE')) THEN 1 ELSE 0 END,"+
                "CASE WHEN COALESCE(l.manual_review_required,0)=1 OR (g.match_state='BGG_MATCH_REVIEW' AND (g.bgg_id IS NULL OR g.bgg_id='')) THEN 1 ELSE 0 END,"+
                "CASE WHEN COALESCE(l.manual_review_required,0)=0 AND NOT (g.match_state='BGG_MATCH_REVIEW' AND (g.bgg_id IS NULL OR g.bgg_id='')) AND (l.enrichment_state='NEEDS_REVIEW' OR l.match_state='BGG_VARIANT_REVIEW' "+
                "OR COALESCE(d.verification_state,'') IN ('BGG_VARIANT_REVIEW','MATCH_UNCERTAIN','PRICE_ANOMALY','EXPANSION_CHECK')) THEN 1 ELSE 0 END "+
                "FROM observations o JOIN market_listings l ON COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint)=o.signature "+
                "LEFT JOIN games g ON g.id=l.game_id LEFT JOIN deals d ON d.signature=o.signature "+
                "WHERE o.observed_at>=? AND o.observed_at<=? AND l.lifecycle='ACTIVE' AND g.id IS NOT NULL AND g.database_visible=1 AND (g.rating IS NULL OR g.rating>=6.0) "+
                "GROUP BY l.id ORDER BY l.last_seen DESC LIMIT ?";
        try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{String.valueOf(startAt),String.valueOf(endAt),String.valueOf(Math.max(1,limit))})){
            while(c.moveToNext()){EngineRunItem x=new EngineRunItem();int i=0;x.listingId=c.getLong(i++);x.gameId=c.getLong(i++);x.signature=c.getString(i++);x.title=c.getString(i++);x.canonical=c.getString(i++);x.bggId=c.getString(i++);x.imageUrl=c.getString(i++);x.listingState=c.getString(i++);x.listingMatchState=c.getString(i++);x.gameState=c.getString(i++);x.publishedLabel=c.getString(i++);x.languageCode=c.getString(i++);x.priceCents=c.getInt(i++);x.bggReady=c.getInt(i++)!=0;x.vintedReady=c.getInt(i++)!=0;x.complete=c.getInt(i++)!=0;x.review=c.getInt(i++)!=0;x.held=c.getInt(i)!=0;
                boolean keep="all".equals(mode)||"bgg".equals(mode)||("vinted".equals(mode)&&x.bggReady)||("ready".equals(mode)&&x.complete)||("review".equals(mode)&&x.review)||("metadata".equals(mode)&&x.vintedReady&&!x.complete&&!x.review&&!x.held);if(keep)out.add(x);
            }
        }return out;
    }

    public synchronized Set<String> observationReadySignatures(long startAt,long endAt){
        Set<String> out=new HashSet<>();startAt=clampEngineStart(startAt);if(endAt<startAt)return out;String sql="SELECT DISTINCT o.signature FROM observations o JOIN market_listings l ON COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint)=o.signature JOIN games g ON g.id=l.game_id LEFT JOIN deals d ON d.signature=o.signature WHERE o.observed_at>=? AND o.observed_at<=? AND l.lifecycle='ACTIVE' AND g.database_visible=1 AND g.rating>=6.0 AND g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND g.match_state='MATCHED' AND l.vinted_item_id IS NOT NULL AND l.vinted_item_id<>'' AND l.vinted_url IS NOT NULL AND l.vinted_url<>'' AND l.enrichment_state='COMPLETE' AND l.match_state='MATCHED' AND COALESCE(l.manual_review_required,0)=0 AND l.enrichment_state<>'NEEDS_REVIEW' AND g.match_state<>'BGG_MATCH_REVIEW' AND COALESCE(d.verification_state,'') NOT IN ('BGG_VARIANT_REVIEW','MATCH_UNCERTAIN','PRICE_ANOMALY','EXPANSION_CHECK') AND NOT EXISTS(SELECT 1 FROM processing_jobs j WHERE j.listing_id=l.id AND j.state IN ('PENDING','PROCESSING','FAILED_RETRYABLE'))";try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{String.valueOf(startAt),String.valueOf(endAt)})){while(c.moveToNext()){String sig=c.getString(0);if(sig!=null&&!sig.isEmpty())out.add(sig);}}return out;
    }
    public synchronized int countNeedsVerification(){return countDeals("verify");}
    /** Lightweight summary for the Activity screen: COUNT in SQL, never materialize 4x500 DealRecord objects on Main. */
    public synchronized MissingCounts missingCounts(){
        MissingCounts out=new MissingCounts();
        String sql="SELECT " +
                "SUM(CASE WHEN vinted_url IS NOT NULL AND vinted_url<>'' AND (published_label IS NULL OR published_label='') THEN 1 ELSE 0 END)," +
                "SUM(CASE WHEN vinted_url IS NOT NULL AND vinted_url<>'' AND ((seller_id IS NULL OR seller_id='') OR (listing_photos_csv IS NULL OR listing_photos_csv='') OR (published_label IS NULL OR published_label='')) THEN 1 ELSE 0 END)," +
                "SUM(CASE WHEN (vinted_url IS NULL OR vinted_url='') THEN 1 ELSE 0 END)," +
                "SUM(CASE WHEN bgg_id IS NOT NULL AND bgg_id<>'' AND (bgg_image_url IS NULL OR bgg_image_url='') THEN 1 ELSE 0 END) " +
                "FROM deals WHERE lifecycle='ACTIVE' AND (rating IS NULL OR rating>=6.0) AND tier IN ('hot','good','offer','fair','insufficient','verify')";
        try(Cursor c=getReadableDatabase().rawQuery(sql,null)){if(c.moveToFirst()){out.published=c.isNull(0)?0:c.getInt(0);out.metadata=c.isNull(1)?0:c.getInt(1);out.link=c.isNull(2)?0:c.getInt(2);out.bgg=c.isNull(3)?0:c.getInt(3);}}
        return out;
    }

    public synchronized List<DealRecord> getUnresolvedDeals(int limit){Cursor c=getReadableDatabase().rawQuery("SELECT "+COLS+" FROM deals WHERE lifecycle='ACTIVE' AND (rating IS NULL OR rating>=6.0) AND (vinted_url IS NULL OR vinted_url='') AND tier IN ('hot','good','verify') ORDER BY last_seen DESC LIMIT ?",new String[]{String.valueOf(Math.max(1,limit))});List<DealRecord>o=new ArrayList<>();while(c.moveToNext())o.add(readDeal(c));c.close();return o;}
    public synchronized List<DealRecord> getDealsMissingBggEnrichment(int limit){Cursor c=getReadableDatabase().rawQuery("SELECT "+COLS+" FROM deals WHERE lifecycle='ACTIVE' AND (rating IS NULL OR rating>=6.0) AND bgg_id IS NOT NULL AND bgg_id<>'' AND (bgg_image_url IS NULL OR bgg_image_url='') ORDER BY last_seen DESC LIMIT ?",new String[]{String.valueOf(Math.max(1,limit))});List<DealRecord>o=new ArrayList<>();while(c.moveToNext())o.add(readDeal(c));c.close();return o;}
    public synchronized List<DealRecord> getDealsNeedingLinkMetadata(int limit){Cursor c=getReadableDatabase().rawQuery("SELECT "+COLS+" FROM deals WHERE lifecycle='ACTIVE' AND (rating IS NULL OR rating>=6.0) AND vinted_url IS NOT NULL AND vinted_url<>'' AND ((seller_id IS NULL OR seller_id='') OR (listing_photos_csv IS NULL OR listing_photos_csv='') OR (published_label IS NULL OR published_label='')) AND tier IN ('hot','good','offer','fair','insufficient','verify') ORDER BY CASE WHEN published_label IS NULL OR published_label='' THEN 0 ELSE 1 END,last_seen DESC LIMIT ?",new String[]{String.valueOf(Math.max(1,limit))});List<DealRecord>o=new ArrayList<>();while(c.moveToNext())o.add(readDeal(c));c.close();return o;}
    /** Known links that only miss Vinted publication time; used when high-priority link work is idle. */
    public synchronized List<DealRecord> getDealsNeedingPublishedTime(int limit){Cursor c=getReadableDatabase().rawQuery("SELECT "+COLS+" FROM deals WHERE lifecycle='ACTIVE' AND (rating IS NULL OR rating>=6.0) AND vinted_url IS NOT NULL AND vinted_url<>'' AND (published_label IS NULL OR published_label='') AND tier IN ('hot','good','offer','fair','insufficient','verify') ORDER BY last_seen DESC LIMIT ?",new String[]{String.valueOf(Math.max(1,limit))});List<DealRecord>o=new ArrayList<>();while(c.moveToNext())o.add(readDeal(c));c.close();return o;}

    public synchronized void resolveListing(String title,int price,String url,Integer shipping,long now){ContentValues v=new ContentValues();if(url!=null&&!url.trim().isEmpty())v.put("vinted_url",url.trim());v.put("resolved_at",now);if(shipping!=null){v.put("shipping_verified_cents",shipping);v.put("shipping_cents",shipping);}getWritableDatabase().update("deals",v,"LOWER(vinted_title)=LOWER(?) AND item_price_cents=?",new String[]{title==null?"":title,String.valueOf(price)});}
    public synchronized void updateProductContext(String title,int price,Integer shipping,String published,long now){ContentValues v=new ContentValues();v.put("resolved_at",now);if(published!=null&&!published.trim().isEmpty())v.put("published_label",published.trim());if(shipping!=null){v.put("shipping_verified_cents",shipping);v.put("shipping_cents",shipping);}getWritableDatabase().update("deals",v,"LOWER(vinted_title)=LOWER(?) AND item_price_cents=?",new String[]{title==null?"":title,String.valueOf(price)});}
    /** Update the display/current price from an exact Vinted item page without rewriting the
     * historical observations that originally created this deal. Derived deal fields are recomputed
     * when possible; stale buyer-protection/total values are cleared after a price change. */
    public synchronized boolean updateVerifiedCurrentPrice(String signature,Integer priceCents,Integer protectedPriceCents,long now){
        if(signature==null||signature.isEmpty()||priceCents==null||priceCents<=0)return false;SQLiteDatabase db=getWritableDatabase();
        int oldPrice;Integer oldProtected,benchmark,shippingVerified;String tier;try(Cursor c=db.rawQuery("SELECT item_price_cents,protected_price_cents,benchmark_cents,shipping_verified_cents,tier FROM deals WHERE signature=?",new String[]{signature})){if(!c.moveToFirst())return false;oldPrice=c.getInt(0);oldProtected=c.isNull(1)?null:c.getInt(1);benchmark=c.isNull(2)?null:c.getInt(2);shippingVerified=c.isNull(3)?null:c.getInt(3);tier=c.getString(4);}
        boolean priceChanged=oldPrice!=priceCents;Integer nextProtected=protectedPriceCents!=null&&protectedPriceCents>0?protectedPriceCents:(priceChanged?null:oldProtected);boolean protectedChanged=!Objects.equals(oldProtected,nextProtected);if(!priceChanged&&!protectedChanged)return false;
        int effective=(shippingVerified!=null?((nextProtected!=null?nextProtected:priceCents+PurchaseMath.vintedFee(priceCents))+shippingVerified):(nextProtected!=null?nextProtected:priceCents));
        ContentValues v=new ContentValues();v.put("item_price_cents",priceCents);if(nextProtected==null)v.putNull("protected_price_cents");else v.put("protected_price_cents",nextProtected);
        // total_cents historically may contain an estimate based on the old asking price. Keep only
        // a value that can be rebuilt from verified page data; otherwise let the UI fall back to the
        // new item/protected price rather than display a stale total.
        if(shippingVerified!=null||nextProtected!=null)v.put("total_cents",effective);else v.putNull("total_cents");
        v.put("resolved_at",now);
        if(benchmark!=null&&benchmark>0){double discount=(benchmark-effective)*100.0/benchmark;v.put("discount",discount);Integer shipping=shippingVerified;DealEvaluator.Evaluation evaluation=DealEvaluator.evaluate(priceCents,effective,benchmark,null,"hot".equals(tier),null,null,shipping);if(evaluation.suggestedOfferCents==null)v.putNull("offer_cents");else v.put("offer_cents",evaluation.suggestedOfferCents);if(!"verify".equals(tier)){if(evaluation.visible()){v.put("tier",evaluation.storageTier());v.put("tier_label",evaluation.label);v.put("lifecycle","ACTIVE");}else{v.put("tier","filtered");v.put("tier_label","");v.put("lifecycle","REMOVED");v.put("verification_state","PRICE_FILTERED");v.put("verification_reason","Prezzo automaticamente escluso dopo aggiornamento Vinted");}}}else{v.putNull("discount");v.putNull("offer_cents");if(!"verify".equals(tier)){v.put("tier","insufficient");v.put("tier_label","Pochi dati");}}
        db.update("deals",v,"signature=?",new String[]{signature});return true;
    }
    public synchronized void applyResolvedLink(String sig,String itemId,String url,String imageUrl,int conf,String reason,String sellerId,String sellerName,String photosCsv,long now){if(sig==null||sig.isEmpty())return;ContentValues v=new ContentValues();if(url!=null&&!url.isEmpty())v.put("vinted_url",url);if(itemId!=null&&!itemId.isEmpty())v.put("vinted_item_id",itemId);if(imageUrl!=null&&!imageUrl.isEmpty())v.put("image_url",imageUrl);if(sellerId!=null&&!sellerId.isEmpty())v.put("seller_id",sellerId);if(sellerName!=null&&!sellerName.isEmpty())v.put("seller_name",sellerName);if(photosCsv!=null&&!photosCsv.isEmpty())v.put("listing_photos_csv",photosCsv);v.put("link_confidence",conf);if(reason!=null)v.put("link_reason",reason);v.put("resolved_at",now);getWritableDatabase().update("deals",v,"signature=?",new String[]{sig});applyUserOverride(getWritableDatabase(),sig);}
    public synchronized void applyResolvedLink(String sig,String itemId,String url,String imageUrl,int conf,String reason,long now){applyResolvedLink(sig,itemId,url,imageUrl,conf,reason,null,null,null,now);}
    public synchronized void updateVintedTitle(String sig,String title){if(sig==null||sig.isEmpty()||title==null)return;String x=title.trim();if(x.length()<2||x.length()>180)return;String n=x.toLowerCase(Locale.ROOT);if(n.contains("protezione acquisti")||n.contains("include la protezione")||n.matches("^[\\d\\s.,€]+$"))return;ContentValues v=new ContentValues();v.put("vinted_title",x);getWritableDatabase().update("deals",v,"signature=?",new String[]{sig});}
    public synchronized void updatePublishedLabel(String sig,String label){if(sig==null||sig.isEmpty()||label==null||label.trim().isEmpty())return;ContentValues v=new ContentValues();v.put("published_label",label.trim());v.put("resolved_at",System.currentTimeMillis());getWritableDatabase().update("deals",v,"signature=?",new String[]{sig});}
    public synchronized void applyBggEnrichment(String bggId,String image,String cats){if(bggId==null||bggId.isEmpty())return;ContentValues v=new ContentValues();if(image!=null&&!image.isEmpty())v.put("bgg_image_url",image);if(cats!=null&&!cats.isEmpty())v.put("bgg_categories",cats);if(v.size()>0)getWritableDatabase().update("deals",v,"bgg_id=?",new String[]{bggId});}
    public synchronized void applyBggEnrichment(String bggId,String image,String cats,Integer minPlayers,Integer maxPlayers,Double weight,Integer playtime){if(bggId==null||bggId.isEmpty())return;ContentValues v=new ContentValues();if(image!=null&&!image.isEmpty())v.put("bgg_image_url",image);if(cats!=null&&!cats.isEmpty())v.put("bgg_categories",cats);put(v,"bgg_minplayers",minPlayers);put(v,"bgg_maxplayers",maxPlayers);put(v,"bgg_weight",weight);put(v,"bgg_playtime",playtime);getWritableDatabase().update("deals",v,"bgg_id=?",new String[]{bggId});}
    public synchronized void applyBggEnrichment(BggMetadata m){if(m==null||m.bggId==null||m.bggId.isEmpty())return;ContentValues v=new ContentValues();String image=(m.imageUrl==null||m.imageUrl.isEmpty())?m.thumbnailUrl:m.imageUrl;if(image!=null&&!image.isEmpty())v.put("bgg_image_url",image);if(m.categories!=null&&!m.categories.isEmpty())v.put("bgg_categories",m.categories);put(v,"bgg_minplayers",m.minPlayers);put(v,"bgg_maxplayers",m.maxPlayers);put(v,"bgg_weight",m.weight);put(v,"bgg_playtime",m.playtime);put(v,"rating",m.rating);put(v,"bgg_rank",m.rank);put(v,"voters",m.voters);getWritableDatabase().update("deals",v,"bgg_id=?",new String[]{m.bggId});}
    public synchronized void correctMatch(DealRecord d,BggSearchClient.Game g){org.json.JSONObject payload=new org.json.JSONObject();try{payload.put("bgg_id",g.id);payload.put("game_name",g.name);payload.put("display_name",g.name);payload.put("bgg_image_url",g.imageUrl==null?org.json.JSONObject.NULL:g.imageUrl);payload.put("rating",g.rating==null?org.json.JSONObject.NULL:g.rating);payload.put("bgg_rank",g.rank==null?org.json.JSONObject.NULL:g.rank);payload.put("voters",g.voters==null?org.json.JSONObject.NULL:g.voters);payload.put("quality_score",g.qualityScore==null?org.json.JSONObject.NULL:g.qualityScore);payload.put("edition_id",g.editionId==null?"":g.editionId);payload.put("edition_label",g.editionName==null?"":g.editionName);payload.put("listing_type","boardgameexpansion".equals(g.type)?"EXPANSION":"GAME");}catch(Exception e){throw new IllegalStateException(e);}ContentValues v=new ContentValues();v.put("signature",d.signature);v.put("item_id",d.vintedItemId);v.put("payload",payload.toString());v.put("excluded",0);getWritableDatabase().insertWithOnConflict("listing_overrides",null,v,SQLiteDatabase.CONFLICT_REPLACE);applyUserOverride(getWritableDatabase(),d.signature);applyBggGame(g);}
    private static void applyUserOverride(SQLiteDatabase db,String signature){try(Cursor c=db.rawQuery("SELECT payload,excluded FROM listing_overrides WHERE signature=? OR (item_id IS NOT NULL AND item_id=(SELECT vinted_item_id FROM deals WHERE signature=?)) ORDER BY signature=? DESC LIMIT 1",new String[]{signature,signature,signature})){if(!c.moveToFirst())return;ContentValues v=new ContentValues();if(c.getInt(1)==1){v.put("lifecycle","USER_HIDDEN");}else if(c.getString(0)!=null){try{org.json.JSONObject payload=new org.json.JSONObject(c.getString(0));for(String key:new String[]{"bgg_id","game_name","display_name","bgg_image_url","rating","bgg_rank","voters","quality_score","listing_type","bgg_categories","bgg_minplayers","bgg_maxplayers","bgg_weight","bgg_playtime"}){Object value=payload.opt(key);if(value==null||value==org.json.JSONObject.NULL)v.putNull(key);else if(value instanceof Number)v.put(key,((Number)value).doubleValue());else v.put(key,value.toString());}}catch(Exception e){return;}for(String key:new String[]{"benchmark_cents","discount","offer_cents","language_code"})v.putNull(key);v.put("tier","good");v.put("match_reason","Identità scelta dall'utente");v.put("verification_state","USER_CONFIRMED");v.put("lifecycle","ACTIVE");}if(v.size()>0)db.update("deals",v,"signature=?",new String[]{signature});}}
    public synchronized boolean hasUserItemDecision(String itemId){if(itemId==null||itemId.isEmpty())return false;try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM listing_overrides WHERE item_id=? AND (excluded=1 OR payload IS NOT NULL) LIMIT 1",new String[]{itemId})){return c.moveToFirst();}}
    public synchronized void exclude(DealRecord d,String reason){SQLiteDatabase db=getWritableDatabase();ContentValues v=new ContentValues();v.put("excluded",1);v.put("reason",reason);if(db.update("listing_overrides",v,"signature=?",new String[]{d.signature})==0){v.put("signature",d.signature);v.put("item_id",d.vintedItemId);db.insert("listing_overrides",null,v);}applyUserOverride(db,d.signature);}
    public synchronized List<DealRecord> excluded(){List<DealRecord> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT "+COLS+" FROM deals WHERE lifecycle='USER_HIDDEN' ORDER BY last_seen DESC",null)){while(c.moveToNext())out.add(readDeal(c));}return out;}
    public synchronized void restore(String signature){ContentValues v=new ContentValues();v.put("excluded",0);getWritableDatabase().update("listing_overrides",v,"signature=?",new String[]{signature});ContentValues active=new ContentValues();active.put("lifecycle","ACTIVE");getWritableDatabase().update("deals",active,"signature=?",new String[]{signature});applyUserOverride(getWritableDatabase(),signature);}
    public synchronized String edition(String signature){try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM listing_overrides WHERE signature=?",new String[]{signature})){if(c.moveToFirst()&&c.getString(0)!=null)try{return new org.json.JSONObject(c.getString(0)).optString("edition_label","");}catch(Exception ignored){}}return "";}
    public synchronized void applyBggGame(BggSearchClient.Game g){ContentValues v=new ContentValues();put(v,"rating",g.rating);put(v,"bgg_rank",g.rank);put(v,"voters",g.voters);put(v,"quality_score",g.qualityScore);put(v,"bgg_image_url",g.imageUrl);put(v,"bgg_categories",g.categories);put(v,"bgg_minplayers",g.minPlayers);put(v,"bgg_maxplayers",g.maxPlayers);put(v,"bgg_playtime",g.playtime);put(v,"bgg_weight",g.weight);SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{db.update("deals",v,"bgg_id=?",new String[]{g.id});try(Cursor c=db.rawQuery("SELECT signature,payload FROM listing_overrides WHERE payload IS NOT NULL",null)){while(c.moveToNext())try{org.json.JSONObject payload=new org.json.JSONObject(c.getString(1));if(!g.id.equals(payload.optString("bgg_id")))continue;for(String key:v.keySet()){Object value=v.get(key);payload.put(key,value==null?org.json.JSONObject.NULL:value);}ContentValues update=new ContentValues();update.put("payload",payload.toString());db.update("listing_overrides",update,"signature=?",new String[]{c.getString(0)});}catch(org.json.JSONException e){throw new IllegalStateException(e);}}db.setTransactionSuccessful();}finally{db.endTransaction();}}


    public synchronized void markBggVariantPending(String signature,String reason){
        if(signature==null||signature.isEmpty())return;ContentValues v=new ContentValues();v.put("verification_state","BGG_VARIANT_PENDING");v.put("verification_reason",reason==null?"Variante BGG da confermare":reason);getWritableDatabase().update("deals",v,"signature=?",new String[]{signature});
    }

    public synchronized void confirmBggVariant(String signature,String reason){
        if(signature==null||signature.isEmpty())return;ContentValues v=new ContentValues();v.put("verification_state","AUTO_VARIANT_CONFIRMED");v.put("verification_reason",reason==null?"Variante BGG confermata dalla pagina Vinted":reason);getWritableDatabase().update("deals",v,"signature=? AND verification_state IN ('BGG_VARIANT_PENDING','BGG_VARIANT_REVIEW')",new String[]{signature});
    }

    public synchronized void flagBggVariantReview(String signature,String reason){
        if(signature==null||signature.isEmpty())return;ContentValues v=new ContentValues();v.put("verification_state","BGG_VARIANT_REVIEW");v.put("verification_reason",reason==null?"Possibile variante BGG diversa":reason);getWritableDatabase().update("deals",v,"signature=?",new String[]{signature});
    }

    /** Automatic single-listing BGG variant correction driven by richer Vinted item-page text.
     * This deliberately does not create a user override: it remains reversible by the normal review UI. */
    public synchronized void applyAutoBggVariantCorrection(String signature,BggSearchClient.Game g,String reason){
        if(signature==null||signature.isEmpty()||g==null||g.id==null||g.id.isEmpty())return;SQLiteDatabase db=getWritableDatabase();ContentValues v=new ContentValues();v.put("bgg_id",g.id);v.put("game_name",g.name);v.put("display_name",g.name);put(v,"bgg_image_url",g.imageUrl);put(v,"rating",g.rating);put(v,"bgg_rank",g.rank);put(v,"voters",g.voters);put(v,"quality_score",g.qualityScore);put(v,"bgg_categories",g.categories);put(v,"bgg_minplayers",g.minPlayers);put(v,"bgg_maxplayers",g.maxPlayers);put(v,"bgg_weight",g.weight);put(v,"bgg_playtime",g.playtime);v.put("match_reason",reason==null?"Variante BGG riconosciuta dalla pagina Vinted":reason);v.put("verification_state","AUTO_VARIANT_VERIFIED");v.put("verification_reason",reason==null?"Variante BGG riconosciuta dalla pagina Vinted":reason);v.put("tier","normal");v.put("tier_label","Variante aggiornata · riferimento da ricalcolare");v.putNull("benchmark_cents");v.putNull("offer_cents");v.putNull("discount");db.beginTransaction();try{db.update("deals",v,"signature=?",new String[]{signature});ContentValues o=new ContentValues();o.put("bgg_id",g.id);o.put("game_name",g.name);o.put("display_name",g.name);put(o,"rating",g.rating);put(o,"bgg_rank",g.rank);put(o,"voters",g.voters);put(o,"quality_score",g.qualityScore);db.update("observations",o,"signature=?",new String[]{signature});db.setTransactionSuccessful();}finally{db.endTransaction();}
    }

    public synchronized void clearAll(){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{db.delete("processing_jobs",null,null);db.delete("price_observations",null,null);db.delete("market_listings",null,null);db.delete("game_aliases",null,null);db.delete("games",null,null);db.delete("observations",null,null);db.delete("deals",null,null);db.setTransactionSuccessful();}finally{db.endTransaction();}}

    private static ContentValues commonValues(VintedCard c,GameAnalysis a,ListingClassifier.Result l,String verify,String reason){ContentValues v=new ContentValues();v.put("vinted_title",c.title);v.put("brand",c.brand);v.put("item_condition",c.condition);v.put("item_price_cents",cents(c.itemPrice));put(v,"protected_price_cents",c.protectedPrice==null?null:cents(c.protectedPrice));put(v,"favorites",c.favorites);if(c!=null&&c.sellerName!=null&&!c.sellerName.trim().isEmpty())put(v,"seller_name",c.sellerName.trim());v.put("analysis_status",a.status);put(v,"bgg_id",a.bggId);put(v,"game_name",a.gameName);put(v,"display_name",a.displayName);put(v,"rating",a.averageRating);put(v,"bgg_rank",a.rank);put(v,"voters",a.voters);put(v,"quality_score",a.qualityScore);put(v,"tier",a.tier);put(v,"tier_label",a.tierLabel);put(v,"total_cents",a.totalCents);put(v,"benchmark_cents",a.benchmarkCents);put(v,"offer_cents",a.offerCents);put(v,"shipping_cents",a.shippingCents);put(v,"discount",a.discount);put(v,"language_code",resolvedLanguageCode(c,a));put(v,"match_reason",a.matchReason);if(l!=null)v.put("listing_type",l.type.name());put(v,"verification_state",verify);put(v,"verification_reason",reason);return v;}
    private static DealRecord readDeal(Cursor c){DealRecord d=new DealRecord();int i=0;d.id=c.getLong(i++);d.signature=c.getString(i++);d.firstSeen=c.getLong(i++);d.lastSeen=c.getLong(i++);d.seenCount=c.getInt(i++);d.vintedTitle=c.getString(i++);d.brand=c.getString(i++);d.condition=c.getString(i++);d.itemPriceCents=c.getInt(i++);d.protectedPriceCents=ni(c,i++);d.favorites=ni(c,i++);d.analysisStatus=c.getString(i++);d.bggId=c.getString(i++);d.gameName=c.getString(i++);d.displayName=c.getString(i++);d.rating=nd(c,i++);d.rank=ni(c,i++);d.voters=ni(c,i++);d.qualityScore=ni(c,i++);d.tier=c.getString(i++);d.tierLabel=c.getString(i++);d.totalCents=ni(c,i++);d.benchmarkCents=ni(c,i++);d.offerCents=ni(c,i++);d.shippingCents=ni(c,i++);d.discount=nd(c,i++);d.languageCode=c.getString(i++);d.matchReason=c.getString(i++);d.lifecycle=c.getString(i++);d.confirmed=c.getInt(i++);d.listingType=c.getString(i++);d.verificationState=c.getString(i++);d.verificationReason=c.getString(i++);d.vintedUrl=c.getString(i++);d.resolvedAt=c.isNull(i)?null:c.getLong(i);i++;d.shippingVerifiedCents=ni(c,i++);d.vintedItemId=c.getString(i++);d.imageUrl=c.getString(i++);d.linkConfidence=ni(c,i++);d.linkReason=c.getString(i++);d.publishedLabel=c.getString(i++);d.bggImageUrl=c.getString(i++);d.bggCategories=c.getString(i++);d.minPlayers=ni(c,i++);d.maxPlayers=ni(c,i++);d.weight=nd(c,i++);d.playtime=ni(c,i++);d.sellerId=c.getString(i++);d.sellerName=c.getString(i++);d.listingPhotosCsv=c.getString(i);return d;}

    private static String resolvedLanguageCode(VintedCard c,GameAnalysis a){String code=a==null||a.languageCode==null?"":a.languageCode.trim().toUpperCase(Locale.ROOT);String detected=ListingLanguageDetector.detect(c==null?null:c.title,c==null?null:c.rawDescription,c==null?null:c.brand);if(code.isEmpty()||code.startsWith("?"))code=ListingLanguageDetector.mergeWithDependency(detected,code);if(a!=null&&a.languageBlocked&&!code.contains("DEP")&&!code.contains("IND"))code=(code.isEmpty()?"?":code)+"|DEP";return code.isEmpty()?null:code;}
    private static String inferLanguage(String raw){return ListingLanguageDetector.detect(raw);}
    private static boolean hasAny(String s,String... terms){if(s==null)return false;for(String t:terms)if(s.contains(t))return true;return false;}
    public synchronized int inferMissingLanguages(){int changed=0;SQLiteDatabase db=getWritableDatabase();try(Cursor c=db.rawQuery("SELECT signature,vinted_title FROM deals WHERE language_code IS NULL OR TRIM(language_code)=''",null)){while(c.moveToNext()){String code=inferLanguage(c.getString(1));if(code.isEmpty())continue;ContentValues v=new ContentValues();v.put("language_code",code);changed+=db.update("deals",v,"signature=?",new String[]{c.getString(0)});}}return changed;}
    public static String signature(VintedCard c){return normalize(c.title)+"|"+normalize(c.brand)+"|"+cents(c.itemPrice);}private static String normalize(String v){if(v==null)return"";String n=Normalizer.normalize(v,Normalizer.Form.NFD).replaceAll("\\p{M}+","");return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();}private static int cents(double v){return(int)Math.round(v*100.0);}private static Integer ni(Cursor c,int i){return c.isNull(i)?null:c.getInt(i);}private static Double nd(Cursor c,int i){return c.isNull(i)?null:c.getDouble(i);}private static void put(ContentValues v,String k,Object o){if(o==null)v.putNull(k);else if(o instanceof String)v.put(k,(String)o);else if(o instanceof Integer)v.put(k,(Integer)o);else if(o instanceof Double)v.put(k,(Double)o);else v.put(k,String.valueOf(o));}
}
