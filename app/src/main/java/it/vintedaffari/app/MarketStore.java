package it.vintedaffari.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Rect;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Canonical market store layered on the existing SQLite database.
 *
 * Legacy `deals` stays intact because Scopri/Catalogo depend on it. This store owns the durable
 * Game -> Listing -> PriceObservation model and the persistent enrichment queue.
 */
public final class MarketStore {
    public static final int BGG_MATCH_ALGORITHM_VERSION = 4;
    private final Map<String,Boolean> collisionRiskMemo=new HashMap<>();
    public static final String JOB_VINTED = "VINTED_ENRICHMENT";
    /** Low-priority second phase: the listing URL/id is already known, only optional seller/photo/time metadata remains. */
    public static final String JOB_VINTED_DEEP = "VINTED_DEEP_ENRICHMENT";
    public static final String JOB_BGG = "BGG_ENRICHMENT";
    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String FAILED_RETRYABLE = "FAILED_RETRYABLE";
    public static final String FAILED_PERMANENT = "FAILED_PERMANENT";
    public static final String COMPLETE = "COMPLETE";
    private static final String TAG = "LudoPipeline";
    private static final String RESET_LISTING_EVIDENCE_SQL =
            "SELECT COALESCE(lifecycle,'UNKNOWN_STATE'),COUNT(*),SUM(CASE WHEN vinted_url IS NULL OR vinted_url='' THEN 1 ELSE 0 END) FROM market_listings GROUP BY COALESCE(lifecycle,'UNKNOWN_STATE') ORDER BY 1";
    private static final String RESET_OBSERVATION_EVIDENCE_SQL =
            "SELECT COUNT(*),MIN(observed_at),MAX(observed_at),SUM(CASE WHEN observed_at>=CAST(? AS INTEGER) AND observed_at<=CAST(? AS INTEGER) THEN 1 ELSE 0 END) FROM observations";
    private static final String RESET_MARKER_EVIDENCE_SQL =
            "SELECT value,updated_at,text_value FROM queue_controls WHERE name='diag:fresh_start_reset' LIMIT 1";
    private static final String VINTED_MISSING_BREAKDOWN_SQL =
            "SELECT COUNT(*),"+
            "SUM(CASE WHEN stage='notBggQualified' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN stage NOT IN ('notBggQualified','localOnly') THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN stage='queued' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN stage='awaitingAttempt' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN stage='noCandidate' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN stage='ambiguous' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN stage='weakMatch' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN stage='unavailable' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN stage='throttled' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN stage='verificationFailed' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN stage='other' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN stage='localOnly' THEN 1 ELSE 0 END) FROM ("+
            "SELECT CASE "+
            "WHEN g.id IS NULL OR g.bgg_id IS NULL OR g.bgg_id='' OR COALESCE(g.match_state,'')<>'MATCHED' OR g.rating IS NULL OR g.rating<6.0 OR COALESCE(g.database_visible,0)<>1 THEN 'notBggQualified' "+
            "WHEN l.enrichment_state='LOCAL_ONLY' THEN 'localOnly' "+
            "WHEN COALESCE(j.state,'') IN ('PENDING','PROCESSING') THEN 'queued' "+
            "WHEN LOWER(COALESCE(NULLIF(j.last_error,''),l.last_error,'')) LIKE '%429%' OR LOWER(COALESCE(NULLIF(j.last_error,''),l.last_error,'')) LIKE '%403%' OR LOWER(COALESCE(NULLIF(j.last_error,''),l.last_error,'')) LIKE '%cooldown%' OR LOWER(COALESCE(NULLIF(j.last_error,''),l.last_error,'')) LIKE '%limitato temporaneamente%' OR LOWER(COALESCE(NULLIF(j.last_error,''),l.last_error,'')) LIKE '%budget pubblico%' THEN 'throttled' "+
            "WHEN LOWER(COALESCE(NULLIF(j.last_error,''),l.last_error,'')) LIKE '%nessun annuncio compatibile%' OR LOWER(COALESCE(NULLIF(j.last_error,''),l.last_error,'')) LIKE '%nessun candidato%' THEN 'noCandidate' "+
            "WHEN LOWER(COALESCE(NULLIF(j.last_error,''),l.last_error,'')) LIKE '%più annunci compatibili%' OR LOWER(COALESCE(NULLIF(j.last_error,''),l.last_error,'')) LIKE '%ambigu%' THEN 'ambiguous' "+
            "WHEN LOWER(COALESCE(NULLIF(j.last_error,''),l.last_error,'')) LIKE '%non corrispondono abbastanza%' OR LOWER(COALESCE(NULLIF(j.last_error,''),l.last_error,'')) LIKE '%titolo e prezzo%' OR LOWER(COALESCE(NULLIF(j.last_error,''),l.last_error,'')) LIKE '%score%' THEN 'weakMatch' "+
            "WHEN LOWER(COALESCE(NULLIF(j.last_error,''),l.last_error,'')) LIKE '%404%' OR LOWER(COALESCE(NULLIF(j.last_error,''),l.last_error,'')) LIKE '%pagina vinted non disponibile%' OR LOWER(COALESCE(NULLIF(j.last_error,''),l.last_error,'')) LIKE '%non più pubblico%' THEN 'unavailable' "+
            "WHEN LOWER(COALESCE(NULLIF(j.last_error,''),l.last_error,'')) LIKE '%metadati%' OR LOWER(COALESCE(NULLIF(j.last_error,''),l.last_error,'')) LIKE '%non supera la verifica%' OR LOWER(COALESCE(NULLIF(j.last_error,''),l.last_error,'')) LIKE '%dettagli non sono disponibili%' THEN 'verificationFailed' "+
            "WHEN COALESCE(NULLIF(j.last_error,''),l.last_error,'')='' THEN 'awaitingAttempt' ELSE 'other' END AS stage "+
            "FROM market_listings l LEFT JOIN games g ON g.id=l.game_id "+
            "LEFT JOIN processing_jobs j ON j.id=(SELECT j2.id FROM processing_jobs j2 WHERE j2.listing_id=l.id AND j2.job_type IN ('VINTED_ENRICHMENT','VINTED_DEEP_ENRICHMENT') ORDER BY j2.updated_at DESC,j2.id DESC LIMIT 1) "+
            "WHERE l.lifecycle='ACTIVE' AND (l.vinted_url IS NULL OR l.vinted_url=''))";
    private static final String CATALOG_VISIBILITY_BREAKDOWN_SQL =
            "WITH core AS ("+
            "SELECT l.id AS listing_id FROM market_listings l JOIN games g ON g.id=l.game_id WHERE "+
            "l.lifecycle='ACTIVE' AND g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND g.match_state='MATCHED' "+
            "AND g.database_visible=1 AND g.rating IS NOT NULL AND g.rating>=6.0 "+
            "AND l.vinted_item_id IS NOT NULL AND l.vinted_item_id<>'' AND l.vinted_url IS NOT NULL AND l.vinted_url<>'' "+
            "AND l.enrichment_state IN ('COMPLETE','CORE_COMPLETE') AND COALESCE(l.manual_review_required,0)=0"+
            "), core_bridged AS ("+
            "SELECT DISTINCT d.id AS deal_id FROM core c JOIN market_listings l ON l.id=c.listing_id "+
            "JOIN deals d ON (l.legacy_signature=d.signature OR (d.vinted_item_id IS NOT NULL AND l.vinted_item_id=d.vinted_item_id))"+
            "), catalog_base AS ("+
            "SELECT d.id AS deal_id FROM deals d WHERE d.lifecycle='ACTIVE' "+
            "AND d.tier IN ('hot','good','offer','fair','insufficient','hunt') AND d.rating IS NOT NULL AND d.rating>=6.0"+
            "), deal_identity AS ("+
            "SELECT b.deal_id FROM catalog_base b JOIN deals d ON d.id=b.deal_id WHERE d.bgg_id IS NOT NULL AND d.bgg_id<>'' "+
            "AND d.vinted_item_id IS NOT NULL AND d.vinted_item_id<>'' AND d.vinted_url IS NOT NULL AND d.vinted_url<>''"+
            "), review_clear AS ("+
            "SELECT b.deal_id FROM deal_identity b JOIN deals d ON d.id=b.deal_id WHERE COALESCE(d.verification_state,'') IN ('OK','USER_CONFIRMED') "+
            "AND COALESCE(d.listing_type,'') IN ('BASE_GAME','EXPANSION','GAME')"+
            "), listing_matched AS ("+
            "SELECT DISTINCT b.deal_id,l.id AS listing_id FROM review_clear b JOIN deals d ON d.id=b.deal_id "+
            "JOIN market_listings l ON (l.legacy_signature=d.signature OR (d.vinted_item_id IS NOT NULL AND l.vinted_item_id=d.vinted_item_id)) "+
            "WHERE l.lifecycle='ACTIVE' AND l.enrichment_state IN ('COMPLETE','CORE_COMPLETE') AND l.match_state='MATCHED' "+
            "AND COALESCE(l.manual_review_required,0)=0"+
            "), bgg_agreement AS ("+
            "SELECT b.deal_id,b.listing_id FROM listing_matched b JOIN market_listings l ON l.id=b.listing_id "+
            "JOIN games g ON g.id=l.game_id JOIN deals d ON d.id=b.deal_id WHERE g.match_state='MATCHED' AND g.bgg_id=d.bgg_id "+
            "AND g.database_visible=1 AND g.rating IS NOT NULL AND g.rating>=6.0"+
            "), job_clear AS ("+
            "SELECT b.deal_id,b.listing_id FROM bgg_agreement b WHERE NOT EXISTS(SELECT 1 FROM processing_jobs j WHERE j.listing_id=b.listing_id "+
            "AND (j.job_type<>'VINTED_DEEP_ENRICHMENT' OR j.source='MANUAL_RECOVERY') "+
            "AND j.state IN ('PENDING','PROCESSING','FAILED_RETRYABLE'))"+
            "), catalog_rows AS ("+
            "SELECT d.id AS deal_id FROM deals d JOIN (SELECT DISTINCT deal_id FROM job_clear) j ON j.deal_id=d.id "+
            "ORDER BY d.last_seen DESC LIMIT 800"+
            ") SELECT "+
            "(SELECT COUNT(*) FROM core),"+
            "(SELECT COUNT(*) FROM core_bridged),"+
            "(SELECT COUNT(*) FROM core_bridged c WHERE EXISTS(SELECT 1 FROM catalog_rows r WHERE r.deal_id=c.deal_id)),"+
            "(SELECT COUNT(*) FROM core_bridged c WHERE NOT EXISTS(SELECT 1 FROM catalog_rows r WHERE r.deal_id=c.deal_id)),"+
            "(SELECT COUNT(*) FROM catalog_rows r WHERE NOT EXISTS(SELECT 1 FROM core_bridged c WHERE c.deal_id=r.deal_id)),"+
            "(SELECT COUNT(*) FROM catalog_base),"+
            "(SELECT COUNT(*) FROM deal_identity),"+
            "(SELECT COUNT(*) FROM review_clear),"+
            "(SELECT COUNT(DISTINCT deal_id) FROM listing_matched),"+
            "(SELECT COUNT(DISTINCT deal_id) FROM bgg_agreement),"+
            "(SELECT COUNT(DISTINCT deal_id) FROM job_clear),"+
            "(SELECT COUNT(*) FROM catalog_rows)";
    private static final String BGG_PRODUCT_TYPE_EVIDENCE_SQL =
            "SELECT o.listing_type FROM market_listings l JOIN observations o ON "+
            "o.signature=COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) "+
            "WHERE l.game_id=? AND l.lifecycle='ACTIVE' AND o.listing_type IN ('BASE_GAME','EXPANSION','GAME') "+
            "ORDER BY CASE o.listing_type WHEN 'BASE_GAME' THEN 0 WHEN 'EXPANSION' THEN 1 WHEN 'GAME' THEN 2 ELSE 3 END,o.observed_at DESC,o.id DESC LIMIT 1";
    private static final String CATALOG_BRIDGE_BACKFILL_SQL =
            "SELECT l.id FROM market_listings l JOIN games g ON g.id=l.game_id WHERE "+
            "l.lifecycle='ACTIVE' AND l.enrichment_state IN ('COMPLETE','CORE_COMPLETE') AND l.match_state='MATCHED' AND COALESCE(l.manual_review_required,0)=0 "+
            "AND l.vinted_item_id IS NOT NULL AND l.vinted_item_id<>'' AND l.vinted_url IS NOT NULL AND l.vinted_url<>'' "+
            "AND g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND g.match_state='MATCHED' AND g.database_visible=1 AND g.rating IS NOT NULL AND g.rating>=6.0 "+
            "AND NOT EXISTS(SELECT 1 FROM deals d WHERE d.signature=COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) OR (d.vinted_item_id IS NOT NULL AND d.vinted_item_id=l.vinted_item_id)) "+
            "AND NOT EXISTS(SELECT 1 FROM queue_controls q WHERE q.name='catalog_bridge_retry:'||l.id AND q.value>?) "+
            "AND NOT EXISTS(SELECT 1 FROM processing_jobs j WHERE j.listing_id=l.id AND (j.job_type<>'VINTED_DEEP_ENRICHMENT' OR j.source='MANUAL_RECOVERY') AND j.state IN ('PENDING','PROCESSING','FAILED_RETRYABLE')) "+
            "ORDER BY l.last_seen DESC,l.id DESC LIMIT ?";
    private static final String QUEUE_PREFS = "ludo_market_queue_controls";
    private static final String KEY_HISTORY_PAUSED = "history_paused";
    private static final String KEY_VINTED_PAUSED = "vinted_paused";
    private static final String KEY_BGG_PAUSED = "bgg_paused";
    public static final String KEY_ENGINE_EPOCH_START = "engine_epoch_start";
    public static final String HISTORICAL_SOURCE = "LEGACY_V9";
    public static final String CATALOG_HEALTH_SOURCE = "CATALOG_HEALTH";
    public static final String CATALOG_RECOVERY_SOURCE = "CATALOG_RECOVERY";
    public static final String SELLER_BACKFILL_SOURCE = "SELLER_BACKFILL";
    private static final String SELLER_BACKFILL_MARKER_PREFIX = "seller_backfill_once:";
    public static final String MANUAL_RECOVERY_SOURCE = "MANUAL_RECOVERY";
    public static final String OPENED_VERIFY_SOURCE = "OPENED_VERIFY";
    private static final long CATALOG_HEALTH_MAX_AGE_MS=24L*60L*60_000L;
    private static final long CATALOG_RECOVERY_MIN_AGE_MS=30L*60_000L;
    private static final String BGG_REVALIDATION_PREFIX = "bgg_revalidation_v1:";
    private static final String ENGINE_RUN_CURSOR = "engine_run_cursor_start";
    private static final String ENGINE_RUN_SLICE = "engine_run_slice";
    private static final String MANUAL_VINTED_RECOVERY = "manual_vinted_recovery";
    private static final String OPENED_VINTED_TARGET = "opened_vinted_target";
    private static final String CATALOG_RATING_SWEEP_V51234 = "catalog_rating_sweep_v51234";

    public static final class Job {
        public long id, listingId, gameId, displayGameId, nextAttemptAt, processingStartedAt;
        public int attempt, priority, progress;
        public String key, type, state, lastError, label, source, displayBggId, displayThumbnailUrl, displayImageUrl;
    }

    public static final class JobSummary {
        public int pending, processing, retryable, permanent, complete;
        public int active() { return pending + processing + retryable; }
    }

    public static final class RuntimeStatus {
        public String state="", detail="";
        public long value=0L, updatedAt=0L;
    }

    /** Cross-process recovery scope: while the user is searching one exact Vinted item, visible
     * search results are context, not a new scouting session. */
    public static final class ManualVintedRecovery {
        public long listingId,until;
        public String signature="",title="";
        public boolean active(long now){return listingId>0&&until>now;}
    }

    public static final class OperationalEpochSummary {
        public long epochAt;
        public int jobsArchived,listingReviewsCleared,dealsReviewArchived,bggReviewsArchived,queueControlsCleared;
        @Override public String toString(){return "epochAt="+epochAt+"; jobsArchived="+jobsArchived+"; listingReviewsCleared="+listingReviewsCleared+"; dealsReviewArchived="+dealsReviewArchived+"; bggReviewsArchived="+bggReviewsArchived+"; queueControlsCleared="+queueControlsCleared;}
    }

    public static final class FreshStartSummary {
        public int jobsRemoved,observationsRemoved,listingsArchived,dealsArchived,gamesHidden,snapshotsCleared;
        public int totalRemoved(){return jobsRemoved+listingsArchived+dealsArchived;}
        @Override public String toString(){return "jobs="+jobsRemoved+", observations="+observationsRemoved+", listings="+listingsArchived+", deals="+dealsArchived+", gamesHidden="+gamesHidden+", snapshots="+snapshotsCleared;}
    }

    public static final class HistoricalBggListing {
        public long id;
        public int priceCents;
        public String title="",brand="",condition="",observedText="",signature="";
    }

    public static final class HistoricalBggCandidate {
        public long gameId;
        public String bggId="",canonicalName="";
        public final List<HistoricalBggListing> listings=new ArrayList<>();
    }

    public static final class PricePoint {
        public long observedAt; public int priceCents;
        PricePoint(long observedAt,int priceCents){this.observedAt=observedAt;this.priceCents=priceCents;}
    }

    public static final class MarketReferenceStats {
        public final int count; public final Integer typicalCents,q25Cents,q75Cents,minCents;
        MarketReferenceStats(int count,Integer typicalCents,Integer minCents){this(count,typicalCents,null,null,minCents);}
        MarketReferenceStats(int count,Integer typicalCents,Integer q25Cents,Integer q75Cents,Integer minCents){this.count=count;this.typicalCents=typicalCents;this.q25Cents=q25Cents;this.q75Cents=q75Cents;this.minCents=minCents;}
    }

    public static final class BggMarketStats {
        public final int count; public final Integer typicalCents,minCents; public final long updatedAt;
        BggMarketStats(int count,Integer typicalCents,Integer minCents,long updatedAt){this.count=count;this.typicalCents=typicalCents;this.minCents=minCents;this.updatedAt=updatedAt;}
        public boolean fresh(long now){return updatedAt>0&&now-updatedAt<=48L*60L*60_000L;}
    }

    private final Context context;
    private final DealDatabase helper;

    public MarketStore(Context context, DealDatabase helper) {
        this.context = context.getApplicationContext();
        this.helper = helper;
    }

    public void beginManualVintedRecovery(long listingId,String signature,String title,long ttlMs){
        if(listingId<=0)return;long now=System.currentTimeMillis(),until=now+Math.max(60_000L,ttlMs);
        try{JSONObject o=new JSONObject();o.put("until",until);o.put("signature",safe(signature));o.put("title",safe(title));
            ContentValues v=new ContentValues();v.put("name",MANUAL_VINTED_RECOVERY);v.put("value",listingId);v.put("updated_at",now);v.put("text_value",o.toString());
            helper.getWritableDatabase().insertWithOnConflict("queue_controls",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            setDiagnosticState("manual_vinted_recovery",1,"state=ACTIVE;listing="+listingId+";until="+until+";scope=target-only");
        }catch(Exception ignored){}
    }

    public ManualVintedRecovery activeManualVintedRecovery(long now){
        ManualVintedRecovery out=new ManualVintedRecovery();
        try(Cursor cur=helper.getReadableDatabase().rawQuery("SELECT value,text_value FROM queue_controls WHERE name=? LIMIT 1",new String[]{MANUAL_VINTED_RECOVERY})){
            if(!cur.moveToFirst())return out;out.listingId=cur.getLong(0);JSONObject o=new JSONObject(cur.isNull(1)?"{}":cur.getString(1));out.until=o.optLong("until",0L);out.signature=o.optString("signature","");out.title=o.optString("title","");
        }catch(Exception ignored){return new ManualVintedRecovery();}
        if(!out.active(now)){clearManualVintedRecovery(out.listingId);return new ManualVintedRecovery();}return out;
    }

    public void clearManualVintedRecovery(long listingId){
        SQLiteDatabase db=helper.getWritableDatabase();int changed;
        if(listingId>0)changed=db.delete("queue_controls","name=? AND value=?",new String[]{MANUAL_VINTED_RECOVERY,String.valueOf(listingId)});
        else changed=db.delete("queue_controls","name=?",new String[]{MANUAL_VINTED_RECOVERY});
        if(changed>0)setDiagnosticState("manual_vinted_recovery",0,"state=IDLE;clearedListing="+listingId);
    }

    /** Exact outbound provenance. Unlike manual search recovery, this is set only when Ludo itself
     * opens a known /items/... URL, so the first product page can be reconciled to the exact card. */
    public void beginOpenedVintedTarget(long listingId,String signature,String title,long ttlMs){
        if(listingId<=0)return;long now=System.currentTimeMillis(),until=now+Math.max(30_000L,ttlMs);
        try{JSONObject o=new JSONObject();o.put("until",until);o.put("signature",safe(signature));o.put("title",safe(title));
            ContentValues v=new ContentValues();v.put("name",OPENED_VINTED_TARGET);v.put("value",listingId);v.put("updated_at",now);v.put("text_value",o.toString());
            helper.getWritableDatabase().insertWithOnConflict("queue_controls",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            setDiagnosticState("opened_vinted_target",1,"state=ACTIVE;listing="+listingId+";until="+until);
        }catch(Exception ignored){}
    }
    public ManualVintedRecovery activeOpenedVintedTarget(long now){
        ManualVintedRecovery out=new ManualVintedRecovery();
        try(Cursor cur=helper.getReadableDatabase().rawQuery("SELECT value,text_value FROM queue_controls WHERE name=? LIMIT 1",new String[]{OPENED_VINTED_TARGET})){
            if(!cur.moveToFirst())return out;out.listingId=cur.getLong(0);JSONObject o=new JSONObject(cur.isNull(1)?"{}":cur.getString(1));out.until=o.optLong("until",0L);out.signature=o.optString("signature","");out.title=o.optString("title","");
        }catch(Exception ignored){return new ManualVintedRecovery();}
        if(!out.active(now)){clearOpenedVintedTarget(out.listingId);return new ManualVintedRecovery();}return out;
    }
    public void clearOpenedVintedTarget(long listingId){
        SQLiteDatabase db=helper.getWritableDatabase();int changed=listingId>0?db.delete("queue_controls","name=? AND value=?",new String[]{OPENED_VINTED_TARGET,String.valueOf(listingId)}):db.delete("queue_controls","name=?",new String[]{OPENED_VINTED_TARGET});
        if(changed>0)setDiagnosticState("opened_vinted_target",0,"state=IDLE;clearedListing="+listingId);
    }
    public boolean updateExactProductMetadata(long listingId,String sellerName,String publishedLabel,String detailsText,Integer priceCents,Integer protectedPriceCents){
        if(listingId<=0)return false;SQLiteDatabase db=helper.getWritableDatabase();ContentValues v=new ContentValues();
        if(!TextUtils.isEmpty(sellerName))v.put("seller_name",sellerName.trim());
        if(!TextUtils.isEmpty(publishedLabel))v.put("published_label",publishedLabel.trim());
        if(!TextUtils.isEmpty(detailsText))v.put("observed_text",safe(detailsText.trim()));
        if(v.size()>0){v.put("enriched_at",System.currentTimeMillis());db.update("market_listings",v,"id=? AND lifecycle='ACTIVE'",new String[]{String.valueOf(listingId)});}
        boolean priceChanged=priceCents!=null&&priceCents>0&&updateVerifiedCurrentPrice(listingId,priceCents,protectedPriceCents);
        return v.size()>0||priceChanged;
    }

    /** Stores category provenance without manufacturing a feed category. A structured non-game
     * Vinted category wins over a title match and leaves the row reversible in AUTO_FILTERED. */
    public boolean updateVintedCategoryEvidence(long listingId,String raw,String normalized,String source,int confidence) {
        if(listingId<=0||TextUtils.isEmpty(raw)||TextUtils.isEmpty(normalized))return false;
        long now=System.currentTimeMillis();SQLiteDatabase db=helper.getWritableDatabase();boolean requeuedBgg=false;int changed=0;db.beginTransaction();
        try{
            ContentValues v=new ContentValues();v.put("category_raw",raw.trim());v.put("category_normalized",normalized.trim());v.put("category_source",safe(source));v.put("category_confidence",Math.max(0,Math.min(100,confidence)));v.put("category_observed_at",now);
            changed=db.update("market_listings",v,"id=? AND lifecycle='ACTIVE'",new String[]{String.valueOf(listingId)});
            if(changed>0&&ListingClassifier.isExplicitNonGameCategory(normalized)){
                String reason="Categoria Vinted incompatibile con gioco da tavolo: "+raw.trim();
                ContentValues hidden=new ContentValues();hidden.put("lifecycle","AUTO_FILTERED");hidden.put("enrichment_state","AUTO_FILTERED");hidden.put("match_state","CATEGORY_INCOMPATIBLE");hidden.put("last_error",reason);
                db.update("market_listings",hidden,"id=? AND lifecycle='ACTIVE'",new String[]{String.valueOf(listingId)});
                ContentValues legacy=new ContentValues();legacy.put("verification_state","CATEGORY_INCOMPATIBLE");legacy.put("verification_reason",reason);
                db.update("deals",legacy,"signature=(SELECT COALESCE(NULLIF(legacy_signature,''),temp_fingerprint) FROM market_listings WHERE id=?)",new String[]{String.valueOf(listingId)});
            }else if(changed>0&&ListingClassifier.isExplicitBoardGameCategory(normalized)){
                long gameId=0L;String signature="",matchState="";
                try(Cursor c=db.rawQuery("SELECT COALESCE(game_id,0),COALESCE(NULLIF(legacy_signature,''),temp_fingerprint),COALESCE(match_state,'') FROM market_listings WHERE id=? AND lifecycle='ACTIVE'",new String[]{String.valueOf(listingId)})){
                    if(c.moveToFirst()){gameId=c.getLong(0);signature=c.getString(1);matchState=c.getString(2);}
                }
                if(gameId>0&&"TYPE_UNVERIFIED".equals(matchState)){
                    // UPDATE_DEAL_LISTING_TYPE_BASE_GAME: category evidence belongs to this exact listing.
                    ContentValues corrected=new ContentValues();corrected.put("match_state","BGG_MATCH_REQUIRED");corrected.put("enrichment_state","PENDING_ANALYSIS");corrected.put("last_error","");
                    db.update("market_listings",corrected,"id=? AND lifecycle='ACTIVE'",new String[]{String.valueOf(listingId)});
                    ContentValues legacy=new ContentValues();legacy.put("listing_type","BASE_GAME");legacy.put("verification_reason","");
                    db.update("deals",legacy,"signature=? AND lifecycle='ACTIVE' AND verification_state='TYPE_UNVERIFIED'",new String[]{signature});
                    enqueueGameJob(db,gameId,JOB_BGG,now);requeuedBgg=true;
                    setDiagnosticState("category_recovery",1,"state=BGG_REVALIDATION_QUEUED;listing="+listingId+";game="+gameId);
                }
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        if(requeuedBgg){QueueKeepAliveService.ensureRunning(context);QueueWorkScheduler.schedule(context);notifyQueueChanged();}
        return changed>0;
    }

    /** One-time UX cut-over. Old incomplete observations are removed from the active product so the
     * new engine can be validated from a genuine zero state. Completed listings are preserved.
     * Incomplete rows are archived rather than physically destroyed, so a future sighting can revive
     * them and we still have a recovery path if needed. Request pacing/circuit-breaker controls are
     * deliberately preserved. */
    public FreshStartSummary freshStartLegacyBacklog(long cutoff){
        FreshStartSummary out=new FreshStartSummary();SQLiteDatabase db=helper.getWritableDatabase();long now=System.currentTimeMillis();
        db.beginTransaction();try{
            out.jobsRemoved=db.delete("processing_jobs",null,null);
            out.observationsRemoved=db.delete("observations","observed_at<?",new String[]{String.valueOf(cutoff)});
            ContentValues archived=new ContentValues();archived.put("lifecycle","RESET_LEGACY");archived.put("enrichment_state","HISTORICAL_PARTIAL");archived.put("last_error","Archiviato alla ripartenza pulita UX 5.12.1");
            String archiveWhere="lifecycle='ACTIVE' AND first_seen<? AND NOT (enrichment_state='COMPLETE' AND game_id IS NOT NULL AND vinted_item_id IS NOT NULL AND vinted_item_id<>'' AND vinted_url IS NOT NULL AND vinted_url<>'' AND current_price_cents>0 AND published_label IS NOT NULL AND published_label<>'' AND EXISTS(SELECT 1 FROM games g WHERE g.id=market_listings.game_id AND g.database_visible=1 AND g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND g.match_state='MATCHED' AND g.rating>=6.0))";
            out.listingsArchived=db.update("market_listings",archived,archiveWhere,new String[]{String.valueOf(cutoff)});
            ContentValues hiddenDeal=new ContentValues();hiddenDeal.put("lifecycle","RESET_LEGACY");
            out.dealsArchived=db.update("deals",hiddenDeal,"lifecycle='ACTIVE' AND first_seen<? AND NOT (bgg_id IS NOT NULL AND bgg_id<>'' AND rating>=6.0 AND vinted_item_id IS NOT NULL AND vinted_item_id<>'' AND vinted_url IS NOT NULL AND vinted_url<>'' AND item_price_cents>0 AND published_label IS NOT NULL AND published_label<>'')",new String[]{String.valueOf(cutoff)});
            ContentValues hideGame=new ContentValues();hideGame.put("database_visible",0);hideGame.put("filter_reason","FRESH_START_RESET_NO_ACTIVE_LISTINGS");
            out.gamesHidden=db.update("games",hideGame,"database_visible=1 AND NOT EXISTS(SELECT 1 FROM market_listings l WHERE l.game_id=games.id AND l.lifecycle='ACTIVE')",null);
            ContentValues freshMeta=new ContentValues();freshMeta.put("metadata_updated_at",now);db.update("games",freshMeta,"database_visible=1 AND EXISTS(SELECT 1 FROM market_listings l WHERE l.game_id=games.id AND l.lifecycle='ACTIVE' AND l.enrichment_state='COMPLETE')",null);
            db.delete("queue_controls","name LIKE 'vinted_candidates:%' OR name LIKE 'lane_vinted_%' OR name LIKE 'lane_bgg_%' OR name LIKE 'diag:%' OR name='processor_heartbeat' OR name LIKE 't2_ledger:%'",null);
            try{out.snapshotsCleared=db.delete("vinted_shadow_snapshots_v3",null,null);}catch(Throwable ignored){}
            ContentValues reset=new ContentValues();reset.put("name","diag:fresh_start_reset");reset.put("value",out.totalRemoved());reset.put("updated_at",now);reset.put("text_value",out.toString());db.insertWithOnConflict("queue_controls",null,reset,SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        notifyQueueChanged();return out;
    }

    /** Starts a new product-facing Motore epoch without deleting observations, price history or
     * authoritative matched data. Old automatic queue debt and unresolved review states are archived
     * so the next Vinted scroll is a genuinely fresh job. Explicit HUNT work is preserved. */
    public OperationalEpochSummary startOperationalEpochIfMissing(){
        OperationalEpochSummary out=new OperationalEpochSummary();SQLiteDatabase db=helper.getWritableDatabase();
        long existing=engineEpochStart();if(existing>0){out.epochAt=existing;return out;}
        long now=System.currentTimeMillis();out.epochAt=now;db.beginTransaction();try{
            ContentValues done=new ContentValues();done.put("state",COMPLETE);done.put("next_attempt_at",0);done.put("updated_at",now);done.put("progress",100);done.put("processing_started_at",0);done.put("last_error","archiviato al nuovo ciclo Motore 5.12.16");
            out.jobsArchived=db.update("processing_jobs",done,"state IN (?,?,?,?) AND COALESCE(source,'AUTO')<>'HUNT_PRIORITY'",new String[]{PENDING,PROCESSING,FAILED_RETRYABLE,FAILED_PERMANENT});

            ContentValues listingReview=new ContentValues();listingReview.put("manual_review_required",0);listingReview.putNull("manual_review_reason");
            out.listingReviewsCleared=db.update("market_listings",listingReview,"COALESCE(manual_review_required,0)=1",null);

            ContentValues archivedDeal=new ContentValues();archivedDeal.put("verification_state","EPOCH_ARCHIVED_REVIEW");archivedDeal.put("verification_reason","Archiviato al nuovo ciclo Motore; verrà rivalutato se ricompare");
            out.dealsReviewArchived=db.update("deals",archivedDeal,"lifecycle='ACTIVE' AND COALESCE(verification_state,'') IN ('BGG_VARIANT_REVIEW','MATCH_UNCERTAIN','PRICE_ANOMALY','EXPANSION_CHECK')",null);

            ContentValues archivedGame=new ContentValues();archivedGame.put("match_state","EPOCH_ARCHIVED_REVIEW");archivedGame.put("database_visible",0);archivedGame.put("filter_reason","Archiviato al nuovo ciclo Motore; verrà rivalutato se ricompare");
            out.bggReviewsArchived=db.update("games",archivedGame,"database_visible=1 AND (bgg_id IS NULL OR bgg_id='') AND match_state='BGG_MATCH_REVIEW'",null);

            out.queueControlsCleared=db.delete("queue_controls","name LIKE 'vinted_candidates:%' OR name LIKE 'lane_vinted_%' OR name LIKE 'lane_bgg_%' OR name='processor_heartbeat'",null);
            ContentValues epoch=new ContentValues();epoch.put("name",KEY_ENGINE_EPOCH_START);epoch.put("value",now);epoch.put("updated_at",now);epoch.put("text_value",out.toString());
            db.insertWithOnConflict("queue_controls",null,epoch,SQLiteDatabase.CONFLICT_REPLACE);
            ContentValues diag=new ContentValues();diag.put("name","diag:operational_epoch");diag.put("value",1);diag.put("updated_at",now);diag.put("text_value",out.toString());
            db.insertWithOnConflict("queue_controls",null,diag,SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        notifyQueueChanged();return out;
    }

    public long engineEpochStart(){
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT value FROM queue_controls WHERE name=? LIMIT 1",new String[]{KEY_ENGINE_EPOCH_START})){return c.moveToFirst()?Math.max(0L,c.getLong(0)):0L;}
        catch(Throwable ignored){return 0L;}
    }

    public static boolean isVintedJobType(String type) {
        return JOB_VINTED.equals(type) || JOB_VINTED_DEEP.equals(type);
    }


    /** New schema is additive; no legacy table is dropped or rewritten. */
    public static void createSchema(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS games(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "bgg_id TEXT UNIQUE," +
                "provisional_key TEXT UNIQUE," +
                "canonical_name TEXT NOT NULL," +
                "normalized_name TEXT NOT NULL," +
                "original_name TEXT,alternate_names TEXT,year INTEGER,description TEXT," +
                "thumbnail_url TEXT,image_url TEXT,min_players INTEGER,max_players INTEGER," +
                "playtime INTEGER,min_age INTEGER,weight REAL,rating REAL,voters INTEGER,bgg_rank INTEGER," +
                "categories TEXT,mechanics TEXT,designers TEXT,artists TEXT,publishers TEXT,families TEXT," +
                "expansions TEXT,base_games TEXT,bgg_url TEXT," +
                "match_state TEXT NOT NULL DEFAULT 'BGG_MATCH_REQUIRED',match_confidence REAL," +
                "first_seen INTEGER NOT NULL,last_seen INTEGER NOT NULL,metadata_updated_at INTEGER NOT NULL DEFAULT 0," +
                "database_visible INTEGER NOT NULL DEFAULT 1,filter_reason TEXT," +
                "match_algorithm_version INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_games_normalized_name ON games(normalized_name)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_games_last_seen ON games(last_seen DESC)");

        db.execSQL("CREATE TABLE IF NOT EXISTS game_aliases(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,game_id INTEGER NOT NULL,alias TEXT NOT NULL," +
                "normalized_alias TEXT NOT NULL,source TEXT NOT NULL," +
                "UNIQUE(game_id,normalized_alias))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_game_aliases_norm ON game_aliases(normalized_alias)");

        db.execSQL("CREATE TABLE IF NOT EXISTS market_listings(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,temp_fingerprint TEXT NOT NULL UNIQUE,legacy_signature TEXT," +
                "vinted_item_id TEXT UNIQUE,game_id INTEGER,vinted_title TEXT NOT NULL,brand TEXT,item_condition TEXT," +
                "current_price_cents INTEGER NOT NULL,protected_price_cents INTEGER,favorites INTEGER," +
                "seller_id TEXT,seller_name TEXT,vinted_url TEXT,image_url TEXT,listing_photos_csv TEXT," +
                "published_label TEXT,language_code TEXT,observed_text TEXT,category_raw TEXT,category_normalized TEXT,category_source TEXT,category_confidence INTEGER NOT NULL DEFAULT 0,category_observed_at INTEGER NOT NULL DEFAULT 0,deferred_retry_at INTEGER NOT NULL DEFAULT 0,lifecycle TEXT NOT NULL DEFAULT 'ACTIVE'," +
                "enrichment_state TEXT NOT NULL DEFAULT 'PENDING_ANALYSIS',match_state TEXT NOT NULL DEFAULT 'PENDING_ANALYSIS'," +
                "match_confidence REAL,first_seen INTEGER NOT NULL,last_seen INTEGER NOT NULL,seen_count INTEGER NOT NULL DEFAULT 1," +
                "manual_review_required INTEGER NOT NULL DEFAULT 0,manual_review_reason TEXT," +
                "enriched_at INTEGER NOT NULL DEFAULT 0,last_error TEXT)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_market_listings_game ON market_listings(game_id,lifecycle,current_price_cents)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_market_listings_legacy_sig ON market_listings(legacy_signature)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_market_listings_last ON market_listings(last_seen DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_market_listings_state ON market_listings(enrichment_state,last_seen DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_market_deferred ON market_listings(enrichment_state,deferred_retry_at,last_seen DESC)");

        db.execSQL("CREATE TABLE IF NOT EXISTS price_observations(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,listing_id INTEGER NOT NULL,observed_at INTEGER NOT NULL," +
                "price_cents INTEGER NOT NULL,protected_price_cents INTEGER,source TEXT NOT NULL DEFAULT 'accessibility')");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_price_listing_time ON price_observations(listing_id,observed_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_price_listing_price ON price_observations(listing_id,price_cents)");

        db.execSQL("CREATE TABLE IF NOT EXISTS processing_jobs(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,job_key TEXT NOT NULL UNIQUE,job_type TEXT NOT NULL," +
                "listing_id INTEGER,game_id INTEGER,state TEXT NOT NULL,attempt INTEGER NOT NULL DEFAULT 0," +
                "next_attempt_at INTEGER NOT NULL DEFAULT 0,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,last_error TEXT," +
                "priority INTEGER NOT NULL DEFAULT 50,source TEXT NOT NULL DEFAULT 'AUTO',progress INTEGER NOT NULL DEFAULT 0,processing_started_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_jobs_due ON processing_jobs(job_type,state,priority DESC,next_attempt_at,created_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_jobs_state_due ON processing_jobs(state,next_attempt_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_jobs_state_updated ON processing_jobs(state,updated_at)");
        db.execSQL("CREATE TABLE IF NOT EXISTS queue_controls(name TEXT PRIMARY KEY,value INTEGER NOT NULL DEFAULT 0,updated_at INTEGER NOT NULL DEFAULT 0,text_value TEXT)");
    }

    /** Best-effort, lossless additive backfill from the existing v8 tables. */
    public static void upgradeV9ToV10(SQLiteDatabase db) {
        try { db.execSQL("ALTER TABLE processing_jobs ADD COLUMN priority INTEGER NOT NULL DEFAULT 50"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE processing_jobs ADD COLUMN source TEXT NOT NULL DEFAULT 'LEGACY_V9'"); } catch (Exception ignored) {}
        try { db.execSQL("DROP INDEX IF EXISTS idx_jobs_due"); } catch (Exception ignored) {}
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_jobs_due ON processing_jobs(job_type,state,priority DESC,next_attempt_at,created_at)");
        // v5.11.0 put the complete historical migration backlog in front of current work.
        // Keep it durable, but demote it so fresh sightings and explicit General Check jobs run first.
        db.execSQL("UPDATE processing_jobs SET priority=CASE WHEN job_type='BGG_ENRICHMENT' THEN 30 ELSE 10 END,source='LEGACY_V9' WHERE state IN ('PENDING','FAILED_RETRYABLE')");
        db.execSQL("UPDATE processing_jobs SET state='FAILED_RETRYABLE',next_attempt_at=?,last_error='recovered by v10 upgrade',priority=80,source='RECOVERY' WHERE state='PROCESSING'", new Object[]{System.currentTimeMillis()});
    }




    /** v5.11.19: retry old BGG reviews once with the improved local matcher. The version marker
     * prevents genuine ambiguities from looping forever while allowing future matcher upgrades to
     * re-evaluate them deliberately. */
    public static void upgradeV17ToV18(SQLiteDatabase db) {
        try { db.execSQL("ALTER TABLE games ADD COLUMN match_algorithm_version INTEGER NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
        try { db.execSQL("UPDATE games SET match_algorithm_version=0 WHERE match_algorithm_version IS NULL"); } catch (Exception ignored) {}
    }

    /** v5.11.26: ordinary verified listings are deferred, not abandoned. Only a small window is
     * materialised as network jobs; language hints are persisted from the original Accessibility text. */
    public static void upgradeV18ToV19(SQLiteDatabase db) {
        long now=System.currentTimeMillis();
        try { db.execSQL("ALTER TABLE market_listings ADD COLUMN observed_text TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE market_listings ADD COLUMN deferred_retry_at INTEGER NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
        try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_market_deferred ON market_listings(enrichment_state,deferred_retry_at,last_seen DESC)"); } catch (Exception ignored) {}
        // Undo the v5.11.25 semantic mistake: a valid >=6 BGG listing without a URL still needs an
        // eventual link, just not all at once. Below-6 and unresolved/non-game rows remain local.
        try { db.execSQL("UPDATE market_listings SET enrichment_state='DEFERRED_LINK',deferred_retry_at=0 WHERE lifecycle='ACTIVE' AND enrichment_state='LOCAL_ONLY' AND (vinted_url IS NULL OR vinted_url='') AND game_id IN (SELECT id FROM games WHERE bgg_id IS NOT NULL AND bgg_id<>'' AND rating IS NOT NULL AND rating>=6.0 AND database_visible=1)"); } catch (Exception ignored) {}
        // Existing normal automatic Vinted jobs from the old backlog remain completed; the promoter
        // below will rematerialise only a bounded group at a time.
        try { db.execSQL("UPDATE processing_jobs SET state='COMPLETE',progress=100,next_attempt_at=0,updated_at=?,processing_started_at=0,last_error='deferred by v5.11.26 bounded linker' WHERE job_type IN ('VINTED_ENRICHMENT','VINTED_DEEP_ENRICHMENT') AND state IN ('PENDING','FAILED_RETRYABLE','PROCESSING') AND source NOT IN ('MANUAL_PRIORITY','HUNT_PRIORITY','LIVE_DEAL')",new Object[]{now}); } catch (Exception ignored) {}
    }

    /** v5.12.2: human review is a durable inbox state, not an accidental reflection of a transient
     * queue job. Opening/exploring a case never dismisses it; only an explicit user resolution does. */
    public static void upgradeV19ToV20(SQLiteDatabase db) {
        try { db.execSQL("ALTER TABLE market_listings ADD COLUMN manual_review_required INTEGER NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE market_listings ADD COLUMN manual_review_reason TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("UPDATE market_listings SET manual_review_required=1,manual_review_reason=COALESCE(last_error,'Da verificare') WHERE lifecycle='ACTIVE' AND id IN (SELECT listing_id FROM processing_jobs WHERE job_type='VINTED_ENRICHMENT' AND state='FAILED_PERMANENT' AND listing_id IS NOT NULL)"); } catch (Exception ignored) {}
    }

    /** Additive category evidence observed only on a Vinted product page. */
    public static void upgradeV20ToV21(SQLiteDatabase db) {
        try { db.execSQL("ALTER TABLE market_listings ADD COLUMN category_raw TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE market_listings ADD COLUMN category_normalized TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE market_listings ADD COLUMN category_source TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE market_listings ADD COLUMN category_confidence INTEGER NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE market_listings ADD COLUMN category_observed_at INTEGER NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
    }

    /** v5.11.14: split Vinted core identity work from optional deep metadata. Existing rows that
     * already know their public item URL do not belong in the expensive search lane anymore. */
    public static void upgradeV16ToV17(SQLiteDatabase db) {
        long now=System.currentTimeMillis();
        try {
            db.execSQL("UPDATE OR IGNORE processing_jobs SET job_key='vinted-deep:'||listing_id,job_type='VINTED_DEEP_ENRICHMENT'," +
                    "state=CASE WHEN state='PROCESSING' THEN 'FAILED_RETRYABLE' ELSE state END," +
                    "next_attempt_at=CASE WHEN state='PROCESSING' THEN ? ELSE next_attempt_at END,processing_started_at=0," +
                    "priority=CASE WHEN priority>40 THEN 40 ELSE priority END,source=CASE WHEN source='MANUAL_PRIORITY' THEN source ELSE 'DEEP_METADATA' END,updated_at=? " +
                    "WHERE job_type='VINTED_ENRICHMENT' AND state IN ('PENDING','FAILED_RETRYABLE','PROCESSING') AND listing_id IN " +
                    "(SELECT id FROM market_listings WHERE vinted_url IS NOT NULL AND vinted_url<>'')",new Object[]{now,now});
        } catch (Exception ignored) {}
        try {
            db.execSQL("UPDATE market_listings SET enrichment_state='CORE_COMPLETE' WHERE lifecycle='ACTIVE' " +
                    "AND vinted_url IS NOT NULL AND vinted_url<>'' AND enrichment_state IN ('PENDING_ANALYSIS','PENDING_ENRICHMENT','FAILED_RETRYABLE')");
        } catch (Exception ignored) {}
    }

    /** v5.11.13: queue/runtime coordination is now authoritative in SQLite across :ui, :radar
     * and the default processor process. SharedPreferences are intentionally not used for
     * cross-process network pacing because Android does not support that contract. */
    public static void upgradeV15ToV16(SQLiteDatabase db) {
        try { db.execSQL("ALTER TABLE queue_controls ADD COLUMN text_value TEXT"); } catch (Exception ignored) {}
    }

    /** v5.11.10: persist the start of the current attempt. This supports an honest time-based
     * visual estimate without pretending that network requests expose byte-level progress. */
    public static void upgradeV14ToV15(SQLiteDatabase db) {
        try { db.execSQL("ALTER TABLE processing_jobs ADD COLUMN processing_started_at INTEGER NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
        db.execSQL("UPDATE processing_jobs SET processing_started_at=updated_at WHERE state='PROCESSING' AND processing_started_at=0");
    }

    /** v5.11.8: the first market backfill incorrectly treated every historical observation as a
     * currently-active Vinted listing. That created thousands of low-value HTTP jobs and made the
     * queue look frozen. Keep the historical rows/prices, but archive stale unresolved migration
     * listings and stop deterministic ambiguous lookups from retrying forever. */
    public static void upgradeV13ToV14(SQLiteDatabase db) {
        long now=System.currentTimeMillis();
        try { db.execSQL("ALTER TABLE processing_jobs ADD COLUMN progress INTEGER NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_jobs_state_due ON processing_jobs(state,next_attempt_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_jobs_state_updated ON processing_jobs(state,updated_at)");
        // LEGACY_V9 rows came from historical observations, not from a proof that the Vinted
        // listing is still live. Do not spend thousands of public-page requests trying to recover
        // URLs for old sightings. Preserve listing + price history as UNKNOWN; a fresh sighting
        // after upgrade will reactivate the same deduplicated listing and enqueue normal work.
        db.execSQL("UPDATE market_listings SET lifecycle='UNKNOWN',enrichment_state=CASE WHEN enrichment_state='COMPLETE' THEN 'COMPLETE' ELSE 'HISTORICAL_PARTIAL' END " +
                "WHERE id IN (SELECT listing_id FROM processing_jobs WHERE source='LEGACY_V9' AND job_type='VINTED_ENRICHMENT' AND listing_id IS NOT NULL) " +
                "AND (vinted_item_id IS NULL OR vinted_item_id='') AND (vinted_url IS NULL OR vinted_url='')");
        db.execSQL("UPDATE processing_jobs SET state='COMPLETE',progress=100,next_attempt_at=0,updated_at=?,last_error='historical sighting preserved; live lookup deferred until seen again' " +
                "WHERE job_type='VINTED_ENRICHMENT' AND source='LEGACY_V9' AND state IN ('PENDING','FAILED_RETRYABLE') AND listing_id IN " +
                "(SELECT id FROM market_listings WHERE lifecycle='UNKNOWN')", new Object[]{now});
        db.execSQL("UPDATE processing_jobs SET state='FAILED_PERMANENT',next_attempt_at=0,updated_at=? " +
                "WHERE job_type='VINTED_ENRICHMENT' AND state='FAILED_RETRYABLE' AND attempt>=2 AND " +
                "(last_error LIKE 'nessun candidato Vinted abbastanza univoco%' OR last_error LIKE 'metadati seller/foto non disponibili%')", new Object[]{now});
        db.execSQL("UPDATE market_listings SET enrichment_state='NEEDS_REVIEW' WHERE id IN " +
                "(SELECT listing_id FROM processing_jobs WHERE job_type='VINTED_ENRICHMENT' AND state='FAILED_PERMANENT' AND listing_id IS NOT NULL) " +
                "AND enrichment_state<>'COMPLETE'");
    }

    /** v5.11.7: queue controls move from SharedPreferences to SQLite so the isolated UI
     * process and the background processor share one authoritative state. */
    public static void upgradeV12ToV13(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS queue_controls(name TEXT PRIMARY KEY,value INTEGER NOT NULL DEFAULT 0,updated_at INTEGER NOT NULL DEFAULT 0,text_value TEXT)");
    }

    /** v5.11.3: live sightings always preempt historical Database maintenance. */
    public static void upgradeV11ToV12(SQLiteDatabase db) {
        db.execSQL("UPDATE processing_jobs SET priority=20 WHERE state IN ('PENDING','FAILED_RETRYABLE') AND source='LEGACY_V9'");
        db.execSQL("UPDATE processing_jobs SET priority=150 WHERE state IN ('PENDING','FAILED_RETRYABLE') AND source IN ('GENERAL_CHECK','MANUAL_CHECK')");
        db.execSQL("UPDATE processing_jobs SET priority=200 WHERE state IN ('PENDING','FAILED_RETRYABLE') AND source='AUTO'");
        db.execSQL("UPDATE processing_jobs SET priority=120 WHERE state IN ('PENDING','FAILED_RETRYABLE') AND source='RECOVERY'");
    }

    /** One-time safe-mode cleanup for already persisted data. Raw observations and price history
     * are preserved; low-rated/children rows simply leave active product/review surfaces. */
    public int applySafeModeQualityCutover(){
        final String marker="safe_mode_quality_v51268";SQLiteDatabase db=helper.getWritableDatabase();
        try(Cursor c=db.rawQuery("SELECT 1 FROM queue_controls WHERE name=? LIMIT 1",new String[]{marker})){if(c.moveToFirst())return 0;}
        long now=System.currentTimeMillis();int changed=0;db.beginTransaction();try{
            ContentValues low=new ContentValues();low.put("database_visible",0);low.put("filter_reason","BGG_RATING_BELOW_6");
            changed+=db.update("games",low,"rating IS NOT NULL AND rating<?",new String[]{String.valueOf(DealPolicy.MIN_BGG_RATING)});
            String childWhere="LOWER(COALESCE(categories,'')) LIKE '%children%game%' OR LOWER(COALESCE(categories,'')) LIKE '%gioc%per%bambin%'";
            ContentValues child=new ContentValues();child.put("database_visible",0);child.put("filter_reason","BGG_CHILDRENS_GAME");
            changed+=db.update("games",child,childWhere,null);

            ContentValues listing=new ContentValues();listing.put("lifecycle","AUTO_FILTERED");listing.put("enrichment_state","AUTO_FILTERED");listing.put("manual_review_required",0);listing.putNull("manual_review_reason");listing.put("last_error","Escluso dal safe mode qualità BGG");
            changed+=db.update("market_listings",listing,"lifecycle='ACTIVE' AND game_id IN (SELECT id FROM games WHERE database_visible=0 AND filter_reason IN ('BGG_RATING_BELOW_6','BGG_CHILDRENS_GAME'))",null);

            ContentValues deal=new ContentValues();deal.put("lifecycle","REMOVED");deal.put("verification_reason","Escluso dal safe mode qualità BGG");
            changed+=db.update("deals",deal,"lifecycle='ACTIVE' AND bgg_id IN (SELECT bgg_id FROM games WHERE bgg_id IS NOT NULL AND bgg_id<>'' AND database_visible=0 AND filter_reason IN ('BGG_RATING_BELOW_6','BGG_CHILDRENS_GAME'))",null);

            ContentValues done=new ContentValues();done.put("state",COMPLETE);done.put("progress",100);done.put("next_attempt_at",0);done.put("updated_at",now);done.put("processing_started_at",0);done.put("last_error","safe mode: gioco fuori target qualità");
            changed+=db.update("processing_jobs",done,"state IN (?,?,?) AND (game_id IN (SELECT id FROM games WHERE database_visible=0 AND filter_reason IN ('BGG_RATING_BELOW_6','BGG_CHILDRENS_GAME')) OR listing_id IN (SELECT l.id FROM market_listings l JOIN games g ON g.id=l.game_id WHERE g.database_visible=0 AND g.filter_reason IN ('BGG_RATING_BELOW_6','BGG_CHILDRENS_GAME')))",new String[]{PENDING,FAILED_RETRYABLE,PROCESSING});

            ContentValues q=new ContentValues();q.put("name",marker);q.put("value",changed);q.put("updated_at",now);q.put("text_value","build=safe-mode-v1;changed="+changed);db.insertWithOnConflict("queue_controls",null,q,SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        setDiagnosticState("safe_mode_quality",changed,"build=safe-mode-v1;changed="+changed);if(changed>0)notifyQueueChanged();return changed;
    }

    /** v5.11.2: keep raw market history, but the Game Database only admits BGG-rated games >= 6.0.
     * Unknown/unconfirmed ratings remain visible until BGG metadata is authoritative. */
    public static void upgradeV10ToV11(SQLiteDatabase db) {
        try { db.execSQL("ALTER TABLE games ADD COLUMN database_visible INTEGER NOT NULL DEFAULT 1"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE games ADD COLUMN filter_reason TEXT"); } catch (Exception ignored) {}
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_games_visible_last ON games(database_visible,last_seen DESC)");
        // Immediate cleanup for the existing local DB without deleting observations/listings/history.
        db.execSQL("UPDATE games SET database_visible=0,filter_reason='BGG_RATING_BELOW_6' WHERE rating IS NOT NULL AND rating<6.0");
        db.execSQL("UPDATE games SET database_visible=1,filter_reason=NULL WHERE rating IS NOT NULL AND rating>=6.0");
        // Do not spend the durable backlog on games already rejected by the quality gate.
        db.execSQL("UPDATE processing_jobs SET state='COMPLETE',updated_at=?,next_attempt_at=0,last_error='skipped: BGG rating below 6' " +
                "WHERE state IN ('PENDING','FAILED_RETRYABLE') AND (game_id IN (SELECT id FROM games WHERE database_visible=0) " +
                "OR listing_id IN (SELECT l.id FROM market_listings l JOIN games g ON g.id=l.game_id WHERE g.database_visible=0))",
                new Object[]{System.currentTimeMillis()});
    }

    public static void backfillLegacy(SQLiteDatabase db) {
        long now = System.currentTimeMillis();
        db.beginTransaction();
        try {
            db.execSQL("INSERT OR IGNORE INTO games(bgg_id,canonical_name,normalized_name,year,image_url,min_players,max_players,playtime,weight,rating,voters,bgg_rank,categories,bgg_url,match_state,match_confidence,first_seen,last_seen,metadata_updated_at) " +
                    "SELECT bgg_id,COALESCE(NULLIF(game_name,''),NULLIF(display_name,''),NULLIF(vinted_title,''),'Gioco sconosciuto'),LOWER(COALESCE(NULLIF(game_name,''),NULLIF(display_name,''),NULLIF(vinted_title,''),'Gioco sconosciuto')),NULL,MAX(bgg_image_url),MAX(bgg_minplayers),MAX(bgg_maxplayers),MAX(bgg_playtime),MAX(bgg_weight),MAX(rating),MAX(voters),MAX(bgg_rank),MAX(bgg_categories),'https://boardgamegeek.com/boardgame/'||bgg_id,'MATCHED',100,MIN(first_seen),MAX(last_seen),0 " +
                    "FROM deals WHERE bgg_id IS NOT NULL AND bgg_id<>'' GROUP BY bgg_id");

            // v8 `deals` was only a filtered opportunity feed. Rebuild the canonical game catalog
            // from every analyzed observation as well, otherwise ordinary games seen on Vinted vanish.
            db.execSQL("INSERT OR IGNORE INTO games(bgg_id,canonical_name,normalized_name,rating,voters,bgg_rank,bgg_url,match_state,match_confidence,first_seen,last_seen,metadata_updated_at) " +
                    "SELECT bgg_id,COALESCE(NULLIF(MAX(game_name),''),NULLIF(MAX(display_name),''),NULLIF(MAX(vinted_title),''),'Gioco sconosciuto'),LOWER(COALESCE(NULLIF(MAX(game_name),''),NULLIF(MAX(display_name),''),NULLIF(MAX(vinted_title),''),'Gioco sconosciuto')),MAX(rating),MAX(voters),MAX(bgg_rank),'https://boardgamegeek.com/boardgame/'||bgg_id,'MATCHED',100,MIN(observed_at),MAX(observed_at),0 " +
                    "FROM observations WHERE bgg_id IS NOT NULL AND bgg_id<>'' AND analysis_status IS NOT NULL AND analysis_status<>'pending' GROUP BY bgg_id");

            db.execSQL("INSERT OR IGNORE INTO games(provisional_key,canonical_name,normalized_name,match_state,first_seen,last_seen,metadata_updated_at) " +
                    "SELECT 'legacy:'||LOWER(COALESCE(NULLIF(vinted_title,''),'gioco sconosciuto')),COALESCE(NULLIF(MAX(vinted_title),''),'Gioco sconosciuto'),LOWER(COALESCE(NULLIF(MAX(vinted_title),''),'Gioco sconosciuto')),'BGG_MATCH_REQUIRED',MIN(observed_at),MAX(observed_at),0 " +
                    "FROM observations WHERE (bgg_id IS NULL OR bgg_id='') AND analysis_status IS NOT NULL AND analysis_status<>'pending' GROUP BY LOWER(COALESCE(NULLIF(vinted_title,''),'gioco sconosciuto'))");

            db.execSQL("INSERT OR IGNORE INTO market_listings(temp_fingerprint,legacy_signature,vinted_item_id,game_id,vinted_title,brand,item_condition,current_price_cents,protected_price_cents,favorites,seller_id,seller_name,vinted_url,image_url,listing_photos_csv,published_label,language_code,lifecycle,enrichment_state,match_state,match_confidence,first_seen,last_seen,seen_count,enriched_at) " +
                    "SELECT d.signature,d.signature,d.vinted_item_id,g.id,COALESCE(d.vinted_title,''),d.brand,d.item_condition,d.item_price_cents,d.protected_price_cents,d.favorites,d.seller_id,d.seller_name,d.vinted_url,d.image_url,d.listing_photos_csv,d.published_label,d.language_code,d.lifecycle," +
                    "CASE WHEN d.vinted_url IS NOT NULL AND d.vinted_url<>'' THEN 'COMPLETE' ELSE 'PENDING_ENRICHMENT' END," +
                    "CASE WHEN d.bgg_id IS NOT NULL AND d.bgg_id<>'' THEN 'MATCHED' ELSE 'BGG_MATCH_REQUIRED' END,100,d.first_seen,d.last_seen,d.seen_count,COALESCE(d.resolved_at,0) " +
                    "FROM deals d LEFT JOIN games g ON g.bgg_id=d.bgg_id");

            db.execSQL("INSERT OR IGNORE INTO market_listings(temp_fingerprint,legacy_signature,vinted_title,brand,item_condition,current_price_cents,protected_price_cents,favorites,lifecycle,enrichment_state,match_state,first_seen,last_seen,seen_count) " +
                    "SELECT signature,signature,COALESCE(MAX(vinted_title),''),MAX(brand),MAX(item_condition),MAX(item_price_cents),MAX(protected_price_cents),MAX(favorites),'ACTIVE'," +
                    "CASE WHEN MAX(CASE WHEN analysis_status IS NOT NULL AND analysis_status<>'pending' THEN 1 ELSE 0 END)=1 THEN 'PENDING_ENRICHMENT' ELSE 'PENDING_ANALYSIS' END," +
                    "CASE WHEN MAX(CASE WHEN bgg_id IS NOT NULL AND bgg_id<>'' THEN 1 ELSE 0 END)=1 THEN 'MATCHED' ELSE 'PENDING_ANALYSIS' END," +
                    "MIN(observed_at),MAX(observed_at),COUNT(*) FROM observations GROUP BY signature");

            db.execSQL("UPDATE market_listings SET game_id=(SELECT g.id FROM observations o JOIN games g ON g.bgg_id=o.bgg_id WHERE o.signature=market_listings.temp_fingerprint AND o.bgg_id IS NOT NULL AND o.bgg_id<>'' ORDER BY o.observed_at DESC LIMIT 1) WHERE game_id IS NULL");
            db.execSQL("UPDATE market_listings SET game_id=(SELECT g.id FROM observations o JOIN games g ON g.provisional_key='legacy:'||LOWER(COALESCE(NULLIF(o.vinted_title,''),'gioco sconosciuto')) WHERE o.signature=market_listings.temp_fingerprint AND (o.bgg_id IS NULL OR o.bgg_id='') AND o.analysis_status IS NOT NULL AND o.analysis_status<>'pending' ORDER BY o.observed_at DESC LIMIT 1),match_state='BGG_MATCH_REQUIRED' WHERE game_id IS NULL AND EXISTS(SELECT 1 FROM observations o WHERE o.signature=market_listings.temp_fingerprint AND o.analysis_status IS NOT NULL AND o.analysis_status<>'pending')");

            db.execSQL("INSERT INTO price_observations(listing_id,observed_at,price_cents,protected_price_cents,source) " +
                    "SELECT ml.id,MIN(o.observed_at),o.item_price_cents,o.protected_price_cents,'migration-v8' FROM observations o " +
                    "LEFT JOIN deals d ON d.signature=o.signature " +
                    "JOIN market_listings ml ON ml.temp_fingerprint=o.signature OR (d.vinted_item_id IS NOT NULL AND d.vinted_item_id<>'' AND ml.vinted_item_id=d.vinted_item_id) " +
                    "WHERE o.item_price_cents IS NOT NULL GROUP BY ml.id,o.item_price_cents,COALESCE(o.protected_price_cents,-1)");

            // Ensure every migrated listing has at least one historical price point.
            db.execSQL("INSERT INTO price_observations(listing_id,observed_at,price_cents,protected_price_cents,source) " +
                    "SELECT ml.id,ml.first_seen,ml.current_price_cents,ml.protected_price_cents,'migration-deal' FROM market_listings ml " +
                    "WHERE NOT EXISTS(SELECT 1 FROM price_observations p WHERE p.listing_id=ml.id)");

            // Preserve Vinted titles as aliases for known games.
            db.execSQL("INSERT OR IGNORE INTO game_aliases(game_id,alias,normalized_alias,source) " +
                    "SELECT DISTINCT game_id,vinted_title,LOWER(vinted_title),'VINTED' FROM market_listings WHERE game_id IS NOT NULL AND vinted_title<>''");

            // Turn the old implicit backlog into durable, deduplicated jobs. Re-running this
            // migration is harmless because job_key is UNIQUE.
            db.execSQL("INSERT OR IGNORE INTO processing_jobs(job_key,job_type,listing_id,state,attempt,next_attempt_at,created_at,updated_at,last_error,priority,source) " +
                    "SELECT 'vinted:'||id,'VINTED_ENRICHMENT',id,'PENDING',0,0,?,?,'' ,10,'MIGRATION' FROM market_listings " +
                    "WHERE lifecycle='ACTIVE' AND enrichment_state<>'COMPLETE'", new Object[]{now, now});
            db.execSQL("INSERT OR IGNORE INTO processing_jobs(job_key,job_type,game_id,state,attempt,next_attempt_at,created_at,updated_at,last_error,priority,source) " +
                    "SELECT 'bgg:'||id,'BGG_ENRICHMENT',id,'PENDING',0,0,?,?,'' ,30,'MIGRATION' FROM games " +
                    "WHERE bgg_id IS NOT NULL AND bgg_id<>'' AND metadata_updated_at=0", new Object[]{now, now});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        Log.i(TAG, "migration=v8->v9 market schema backfill completed at=" + now);
    }

    /** Replays metadata already captured by the embedded browser. Zero network, zero AI.
     * Exact Vinted item identity is the only join key; existing non-empty values always win. */
    public int materializeBrowserSnapshotMetadataBatch(int limit) {
        SQLiteDatabase db=helper.getWritableDatabase();
        int changed=0,scanned=0,max=Math.max(1,Math.min(500,limit));
        db.beginTransaction();
        try(Cursor c=db.rawQuery(
                "SELECT l.id,l.vinted_item_id,COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint),q.text_value "+
                "FROM market_listings l JOIN queue_controls q ON q.name='browser_snapshot:'||l.vinted_item_id "+
                "WHERE l.lifecycle='ACTIVE' AND l.vinted_item_id IS NOT NULL AND l.vinted_item_id<>'' "+
                "AND (COALESCE(l.seller_id,'')='' OR COALESCE(l.published_label,'')='' OR "+
                "COALESCE(l.image_url,'')='' OR COALESCE(l.listing_photos_csv,'')='' OR COALESCE(l.language_code,'')='') "+
                "ORDER BY l.last_seen DESC,l.id DESC LIMIT ?",
                new String[]{String.valueOf(max)})){
            while(c.moveToNext()){
                scanned++;
                long id=c.getLong(0);String itemId=c.getString(1),signature=c.getString(2),raw=c.getString(3);
                if(TextUtils.isEmpty(raw))continue;
                JSONObject snap;try{snap=new JSONObject(raw);}catch(org.json.JSONException malformed){continue;}
                ContentValues v=new ContentValues();
                String sellerId=snap.optString("sellerId");
                if(sellerId.matches("[1-9][0-9]{0,18}"))v.put("seller_id",sellerId);
                String sellerName=snap.optString("sellerName");
                if(!TextUtils.isEmpty(sellerName))v.put("seller_name",sellerName);
                JSONObject publication=snap.optJSONObject("publication");
                String published=publication==null?"":publication.optString("raw");
                if(!TextUtils.isEmpty(published))v.put("published_label",published);
                JSONArray photos=snap.optJSONArray("photos");String image="",photoCsv="";
                if(photos!=null)for(int n=0;n<Math.min(10,photos.length());n++){
                    String photo=BrowserCapturePolicy.photo(photos.optString(n));if(TextUtils.isEmpty(photo))continue;
                    if(TextUtils.isEmpty(image))image=photo;photoCsv+=(photoCsv.isEmpty()?"":",")+photo;
                }
                if(!TextUtils.isEmpty(image))v.put("image_url",image);
                if(!TextUtils.isEmpty(photoCsv))v.put("listing_photos_csv",photoCsv);
                String language=snap.optString("language");
                if(!TextUtils.isEmpty(language))v.put("language_code",language);
                if(v.size()==0)continue;
                StringBuilder set=new StringBuilder();ArrayList<Object> args=new ArrayList<>();
                for(String key:v.keySet()){
                    if(set.length()>0)set.append(',');
                    set.append(key).append("=COALESCE(NULLIF(").append(key).append(",''),NULLIF(?,''))");
                    args.add(v.get(key));
                }
                args.add(id);
                db.execSQL("UPDATE market_listings SET "+set+" WHERE id=? AND lifecycle='ACTIVE'",args.toArray());
                if(!TextUtils.isEmpty(published)){
                    ContentValues deal=new ContentValues();deal.put("published_label",published);
                    db.update("deals",deal,
                            "lifecycle='ACTIVE' AND COALESCE(published_label,'')='' AND (signature=? OR (vinted_item_id IS NOT NULL AND vinted_item_id=?))",
                            new String[]{signature,itemId});
                }
                changed++;
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        setDiagnosticState("browser_snapshot_metadata",changed,
                "build=browser-snapshot-metadata-v1;scanned="+scanned+";changed="+changed+";zeroNetwork=true");
        return changed;
    }

    /** Persist validated public captures by exact ID. Missing-price snapshots remain durable,
     * but cannot become zero-price observations. Called exclusively on the browser IO lane. */
    public long captureBrowserItem(JSONObject item,long now) throws org.json.JSONException {
        String itemId=item.optString("id"),url=item.optString("url");
        if(!VintedBrowserPolicy.matchesItem(url,itemId))return -1;
        SQLiteDatabase db=helper.getWritableDatabase();
        db.beginTransaction();
        try{
            String snapshotKey="browser_snapshot:"+itemId;
            JSONObject merged=new JSONObject();
            String previous=scalarString(db,"SELECT text_value FROM queue_controls WHERE name=?",new String[]{snapshotKey});
            if(!TextUtils.isEmpty(previous))try{merged=new JSONObject(previous);}catch(org.json.JSONException ignored){}
            Integer previousSnapshotPrice=BrowserCapturePolicy.priceCents(merged.opt("priceCents"),merged.optString("currency"));
            Integer incomingPrice=BrowserCapturePolicy.priceCents(item.opt("priceCents"),item.optString("currency"));
            if(incomingPrice!=null&&!incomingPrice.equals(previousSnapshotPrice)&&!item.has("protectedPriceCents"))merged.remove("protectedPriceCents");
            boolean preserveRich="dom".equals(item.optString("source"))&&!"dom".equals(merged.optString("source","dom"));
            for(java.util.Iterator<String> keys=item.keys();keys.hasNext();){String key=keys.next();Object value=item.opt(key);
                if(value==null||value==JSONObject.NULL||(value instanceof String&&((String)value).isEmpty()))continue;
                if(preserveRich&&("title".equals(key)||"description".equals(key)||"source".equals(key))&&merged.has(key))continue;
                merged.put(key,value);
            }
            db.execSQL(BrowserIntakeSql.PROVENANCE,new Object[]{snapshotKey,1,now,merged.toString()});
            Integer price=BrowserCapturePolicy.priceCents(merged.opt("priceCents"),merged.optString("currency"));
            String title=merged.optString("title").trim();
            if(price==null||title.isEmpty()){db.setTransactionSuccessful();return -1;}
            Integer protectedPrice=BrowserCapturePolicy.priceCents(merged.opt("protectedPriceCents"),merged.optString("currency"));
            String brand=merged.optString("brand"),condition=merged.optString("condition");
            String raw=title+(merged.optString("description").isEmpty()?"":" · "+merged.optString("description"));
            if(!merged.optString("language").isEmpty())raw+=" · Lingua: "+merged.optString("language");
            raw=safe(raw);
            long id=0;String signature="browser:"+itemId;boolean changed=true,active=true;
            try(Cursor c=db.rawQuery(BrowserIntakeSql.LOOKUP,new String[]{itemId})){
                if(c.moveToFirst()){
                    id=c.getLong(0);signature=c.getString(1);
                    changed=!title.equals(c.getString(2))||(!brand.isEmpty()&&!brand.equals(c.getString(3)))||(!condition.isEmpty()&&!condition.equals(c.getString(4)))||price!=c.getInt(5)||(!raw.equals(c.getString(10)))||protectedPrice!=null&&(c.isNull(6)||protectedPrice!=c.getInt(6));
                    active="ACTIVE".equals(c.getString(7))&&c.getInt(8)==0;
                    if(protectedPrice==null&&price==c.getInt(5)&&!c.isNull(6))protectedPrice=c.getInt(6);
                    if(brand.isEmpty())brand=c.isNull(3)?"":c.getString(3);
                    if(condition.isEmpty())condition=c.isNull(4)?"":c.getString(4);
                }
            }
            VintedCard card=new VintedCard(title,brand,condition,price/100.0,protectedPrice==null?null:protectedPrice/100.0,null,new Rect(0,0,1,1),raw,"",signature);
            ListingClassifier.Result classified=ListingClassifier.classify(card);
            String analysisState=classified.allowIdentityCandidate?"PENDING_ANALYSIS":"BLOCKED_CLASSIFIER";
            if(id==0){
                db.execSQL(BrowserIntakeSql.INSERT,new Object[]{signature,signature,itemId,title,brand,condition,price,protectedPrice,raw,url,now,now,analysisState,analysisState});
                Long inserted=scalarLong(db,"SELECT id FROM market_listings WHERE vinted_item_id=?",new String[]{itemId});
                if(inserted==null)throw new IllegalStateException("Browser identity could not be persisted");id=inserted;
            }
            db.execSQL(BrowserIntakeSql.PROVENANCE,new Object[]{"browser_listing:"+id,1,now,"browser-public-capture-v1"});
            String photos="",image="";JSONArray receivedPhotos=merged.optJSONArray("photos");
            if(receivedPhotos!=null)for(int n=0;n<Math.min(10,receivedPhotos.length());n++){
                String photo=BrowserCapturePolicy.photo(receivedPhotos.optString(n));if(photo.isEmpty())continue;
                if(image.isEmpty())image=photo;photos+=(photos.isEmpty()?"":",")+photo;
            }
            JSONObject publication=merged.optJSONObject("publication");String published=publication==null?"":publication.optString("raw");
            if(active){
                db.execSQL(BrowserIntakeSql.UPDATE,new Object[]{title,brand,condition,price,protectedPrice,protectedPrice,price,raw,url,image,photos,merged.optString("sellerId").matches("[1-9][0-9]{0,18}")?merged.optString("sellerId"):"",merged.optString("sellerName"),published,"",now,id});
                if(changed){
                    ContentValues state=new ContentValues();state.put("enrichment_state",analysisState);state.put("match_state",analysisState);
                    db.update("market_listings",state,"id=?",new String[]{String.valueOf(id)});
                    ContentValues history=new ContentValues();history.put("listing_id",id);history.put("observed_at",now);history.put("price_cents",price);put(history,"protected_price_cents",protectedPrice);history.put("source","browser-public-capture");db.insertOrThrow("price_observations",null,history);
                    // Same transaction/database as the canonical row. ID signatures do not collapse
                    // different sellers' cards even when title and price happen to be identical.
                }
                helper.recordSighting(card,classified,now);
                syncBrowserDeal(db,id);
            }
            db.setTransactionSuccessful();return id;
        }finally{db.endTransaction();helper.invalidateActiveObservationSessionCache();}
    }

    /** Serialize a browser result with new captures so stale JS callbacks cannot reprice a row. */
    public boolean commitBrowserAnalysis(VintedCard card,GameAnalysis analysis,ListingClassifier.Result classified,long now,boolean huntCandidate){
        SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();
        try{Long id=listingIdForCard(db,card);if(id==null||!browserOwned(db,id)){db.setTransactionSuccessful();return false;}
            String life=scalarString(db,"SELECT lifecycle FROM market_listings WHERE id=? AND COALESCE(manual_review_required,0)=0",new String[]{String.valueOf(id)});
            if(!"ACTIVE".equals(life)){db.setTransactionSuccessful();return false;}
            helper.record(card,analysis,classified,now);applyAnalysis(card,analysis,classified,now);if(huntCandidate){helper.recordHuntCandidate(card,analysis,classified,now);syncBrowserDeal(db,id);}
            db.setTransactionSuccessful();return true;
        }finally{db.endTransaction();}
    }

    private static boolean browserOwned(SQLiteDatabase db,long listingId){
        try(Cursor c=db.rawQuery(BrowserIntakeSql.BROWSER_OWNED,new String[]{String.valueOf(listingId)})){return c.moveToFirst();}
    }

    private static void syncBrowserDeal(SQLiteDatabase db,long listingId){
        db.execSQL("UPDATE deals SET vinted_item_id=(SELECT vinted_item_id FROM market_listings WHERE id=?),vinted_url=(SELECT vinted_url FROM market_listings WHERE id=?),image_url=COALESCE((SELECT image_url FROM market_listings WHERE id=?),image_url),listing_photos_csv=COALESCE((SELECT listing_photos_csv FROM market_listings WHERE id=?),listing_photos_csv),seller_id=COALESCE((SELECT seller_id FROM market_listings WHERE id=?),seller_id),seller_name=COALESCE((SELECT seller_name FROM market_listings WHERE id=?),seller_name),published_label=COALESCE((SELECT published_label FROM market_listings WHERE id=?),published_label) WHERE signature=(SELECT COALESCE(NULLIF(legacy_signature,''),temp_fingerprint) FROM market_listings WHERE id=?)",new Object[]{listingId,listingId,listingId,listingId,listingId,listingId,listingId,listingId});
    }

    public long recordSighting(VintedCard card, ListingClassifier.Result listing, long now) {
        DbContentionTrace.Scope trace=DbContentionTrace.start("MarketStore.recordSighting");
        try{
        if (card == null) return -1L;
        trace.phase("OPEN_DATABASE");SQLiteDatabase db = helper.getWritableDatabase();trace.phase("PRE_TRANSACTION");
        String fp = fingerprint(card);
        trace.phase("ACQUIRE_WRITER");db.beginTransaction();trace.phase("TRANSACTION");
        try {
            long id;
            int previousPrice = Integer.MIN_VALUE;
            Integer previousProtected = null;
            String legacy=DealDatabase.signature(card);
            try (Cursor c = db.rawQuery("SELECT id,current_price_cents,protected_price_cents,temp_fingerprint FROM market_listings WHERE temp_fingerprint=? OR (legacy_signature=? AND temp_fingerprint=legacy_signature) ORDER BY CASE WHEN temp_fingerprint=? THEN 0 ELSE 1 END LIMIT 1", new String[]{fp,legacy,fp})) {
                if (c.moveToFirst()) {
                    id = c.getLong(0);
                    previousPrice = c.getInt(1);
                    previousProtected = c.isNull(2) ? null : c.getInt(2);
                    String storedFingerprint=c.getString(3);
                    ContentValues v = new ContentValues();
                    // Upgrade v8 legacy signatures to the stronger v9 fingerprint on first sight.
                    if(legacy.equals(storedFingerprint)&&!fp.equals(storedFingerprint))v.put("temp_fingerprint",fp);
                    v.put("vinted_title", safe(card.title));
                    v.put("observed_text", safe(card.rawDescription));
                    v.put("brand", safe(card.brand));
                    v.put("item_condition", safe(card.condition));
                    v.put("current_price_cents", cents(card.itemPrice));
                    put(v, "protected_price_cents", card.protectedPrice == null ? null : cents(card.protectedPrice));
                    put(v, "favorites", card.favorites);
                    if(!TextUtils.isEmpty(card.sellerName))v.put("seller_name",safe(card.sellerName));
                    v.put("last_seen", now);
                    v.put("lifecycle", "ACTIVE");
                    db.execSQL("UPDATE market_listings SET seen_count=seen_count+1 WHERE id=?", new Object[]{id});
                    db.update("market_listings", v, "id=?", new String[]{String.valueOf(id)});
                } else {
                    ContentValues v = new ContentValues();
                    v.put("temp_fingerprint", fp);
                    v.put("legacy_signature", legacy);
                    v.put("vinted_title", safe(card.title));
                    v.put("observed_text", safe(card.rawDescription));
                    v.put("brand", safe(card.brand));
                    v.put("item_condition", safe(card.condition));
                    v.put("current_price_cents", cents(card.itemPrice));
                    put(v, "protected_price_cents", card.protectedPrice == null ? null : cents(card.protectedPrice));
                    put(v, "favorites", card.favorites);
                    if(!TextUtils.isEmpty(card.sellerName))v.put("seller_name",safe(card.sellerName));
                    v.put("first_seen", now);
                    v.put("last_seen", now);
                    v.put("lifecycle", "ACTIVE");
                    boolean allowed = listing == null || listing.allowIdentityCandidate;
                    v.put("enrichment_state", allowed ? "PENDING_ANALYSIS" : "BLOCKED_CLASSIFIER");
                    v.put("match_state", allowed ? "PENDING_ANALYSIS" : "BLOCKED_CLASSIFIER");
                    id = db.insertOrThrow("market_listings", null, v);
                }
            }
            int p = cents(card.itemPrice);
            Integer pp = card.protectedPrice == null ? null : cents(card.protectedPrice);
            if (previousPrice == Integer.MIN_VALUE || previousPrice != p || !same(previousProtected, pp)) {
                ContentValues obs = new ContentValues();
                obs.put("listing_id", id);
                obs.put("observed_at", now);
                obs.put("price_cents", p);
                put(obs, "protected_price_cents", pp);
                obs.put("source", "accessibility");
                db.insert("price_observations", null, obs);
            }
            db.setTransactionSuccessful();
            return id;
        } finally {
            trace.phase("COMMIT");try{db.endTransaction();}finally{trace.phase("POST_TRANSACTION");}
        }
    
        }catch(RuntimeException|Error failure){trace.failed(failure);throw failure;}finally{trace.close();}
    }

    public void applyAnalysis(VintedCard card, GameAnalysis analysis, ListingClassifier.Result listing, long now) {
        DbContentionTrace.Scope trace=DbContentionTrace.start("MarketStore.applyAnalysis");
        try{
        if (card == null || analysis == null) return;
        trace.phase("OPEN_DATABASE");SQLiteDatabase db = helper.getWritableDatabase();trace.phase("PRE_TRANSACTION");
        trace.phase("ACQUIRE_WRITER");db.beginTransaction();trace.phase("TRANSACTION");
        try {
            Long listingId = listingIdForCard(db,card);
            if (listingId == null) { db.setTransactionSuccessful(); return; }
            if(AiCategoryEvidence.has(db,listingId)&&!AiCategoryEvidence.has(db,listingId,card)){db.setTransactionSuccessful();return;}
            String listingLifecycle=scalarString(db,"SELECT lifecycle FROM market_listings WHERE id=?",new String[]{String.valueOf(listingId)});
            // Analyses can finish after a classifier/quarantine decision. Never let stale async work
            // resurrect an inactive listing or overwrite its terminal state.
            if(!TextUtils.isEmpty(listingLifecycle)&&!"ACTIVE".equals(listingLifecycle)){db.setTransactionSuccessful();return;}
            Long oldGameId = scalarLong(db, "SELECT game_id FROM market_listings WHERE id=?", new String[]{String.valueOf(listingId)});
            String oldGameState=oldGameId==null?"":scalarString(db,"SELECT match_state FROM games WHERE id=?",new String[]{String.valueOf(oldGameId)});
            if("BGG_MATCH_REVIEW".equals(oldGameState)){
                ContentValues hold=new ContentValues();hold.put("match_state","BGG_MATCH_REVIEW");hold.put("enrichment_state","NEEDS_REVIEW");
                String why=scalarString(db,"SELECT filter_reason FROM games WHERE id=?",new String[]{String.valueOf(oldGameId)});if(!TextUtils.isEmpty(why))hold.put("last_error",why);
                db.update("market_listings",hold,"id=?",new String[]{String.valueOf(listingId)});db.setTransactionSuccessful();return;
            }
            boolean aiProduct=hasAiCategoryRecoveryEvidence(card);
            if("AUTO_QUARANTINED".equals(oldGameState)&&!aiProduct){
                ContentValues hold=new ContentValues();hold.put("lifecycle","AUTO_FILTERED");hold.put("enrichment_state","AUTO_FILTERED");hold.put("match_state","AUTO_FILTERED_NON_GAME");
                String why=scalarString(db,"SELECT filter_reason FROM games WHERE id=?",new String[]{String.valueOf(oldGameId)});if(!TextUtils.isEmpty(why))hold.put("last_error",why);
                db.update("market_listings",hold,"id=?",new String[]{String.valueOf(listingId)});db.setTransactionSuccessful();return;
            }
            long gameId; String matchState;
            if ("matched".equals(analysis.status) && !TextUtils.isEmpty(analysis.bggId)) {
                gameId = upsertMatchedGame(db, analysis, card.title, now); matchState = "MATCHED";
            } else {
                gameId = upsertProvisionalGame(db, card.title, "BGG_MATCH_REQUIRED", analysis.matchConfidence, now);
                String persisted=scalarString(db,"SELECT match_state FROM games WHERE id=?",new String[]{String.valueOf(gameId)});
                matchState=TextUtils.isEmpty(persisted)?"BGG_MATCH_REQUIRED":persisted;
                if("AUTO_QUARANTINED".equals(matchState)&&aiProduct){
                    // Fresh listing-specific product proof changes a type quarantine, never identity.
                    ContentValues pending=new ContentValues();pending.put("match_state","BGG_MATCH_REQUIRED");
                    pending.put("database_visible",1);pending.putNull("filter_reason");
                    db.update("games",pending,"id=? AND (bgg_id IS NULL OR bgg_id='') AND match_state='AUTO_QUARANTINED'",new String[]{String.valueOf(gameId)});
                    matchState="BGG_MATCH_REQUIRED";
                }
                if("AUTO_QUARANTINED".equals(matchState)){
                    ContentValues q=new ContentValues();q.put("lifecycle","AUTO_FILTERED");q.put("enrichment_state","AUTO_FILTERED");q.put("match_state","AUTO_FILTERED_NON_GAME");
                    q.put("last_error",safe(scalarString(db,"SELECT filter_reason FROM games WHERE id=?",new String[]{String.valueOf(gameId)})));
                    db.update("market_listings",q,"id=?",new String[]{String.valueOf(listingId)});
                    db.setTransactionSuccessful();return;
                }
            }
            boolean hasUrl=!TextUtils.isEmpty(scalarString(db,"SELECT vinted_url FROM market_listings WHERE id=?",new String[]{String.valueOf(listingId)}));
            DealEvaluator.Evaluation priceDecision=DealEvaluator.evaluate(card,analysis);
            boolean liveResolve=priceDecision.visible()&&shouldAutoResolveVinted(analysis)&&!hasUrl;
            boolean eventualLink=priceDecision.visible()&&!hasUrl && "MATCHED".equals(matchState) && analysis.averageRating!=null && analysis.averageRating>=DealPolicy.MIN_BGG_RATING && !analysis.languageBlocked;
            ContentValues v = new ContentValues();
            v.put("game_id", gameId); v.put("match_state", matchState); put(v, "match_confidence", analysis.matchConfidence);
            String detected=ListingLanguageDetector.detect(card.title,card.rawDescription,card.brand);
            v.put("language_code", ListingLanguageDetector.mergeWithDependency(detected,resolvedLanguage(analysis)));
            // Acquisition owns source evidence. Do not truncate/replace a proven source on analysis.
            if(!aiProduct)v.put("observed_text",safe(card.rawDescription));
            v.put("enrichment_state", hasUrl ? "CORE_COMPLETE" : (liveResolve ? "PENDING_ENRICHMENT" : (eventualLink?"DEFERRED_LINK":"LOCAL_ONLY")));
            if(eventualLink)v.put("deferred_retry_at",0);
            v.put("last_error", ""); db.update("market_listings", v, "id=?", new String[]{String.valueOf(listingId)});
            addAlias(db, gameId, card.title, "VINTED");
            if (!TextUtils.isEmpty(analysis.matchedAlias)) addAlias(db, gameId, analysis.matchedAlias, "BGG_ALIAS");
            if (oldGameId != null && oldGameId != gameId) deleteOrphanProvisional(db, oldGameId);
            // The remote Vinted lane is scarce. Only a fresh, already-qualified opportunity is
            // resolved automatically; ordinary observations stay useful locally without becoming jobs.
            if(liveResolve)enqueueListingJob(db, listingId, JOB_VINTED, now,320,"LIVE_DEAL");
            if ("MATCHED".equals(matchState)&&bggRefreshDue(db,gameId,now)) enqueueGameJob(db, gameId, JOB_BGG, now);
            if(!card.capturedSignature.isEmpty())syncBrowserDeal(db,listingId);
            db.setTransactionSuccessful();
        } finally { trace.phase("COMMIT");try{db.endTransaction();}finally{trace.phase("POST_TRANSACTION");} }
    
        }catch(RuntimeException|Error failure){trace.failed(failure);throw failure;}finally{trace.close();}
    }

    private static boolean shouldAutoResolveVinted(GameAnalysis a){
        if(a==null||!"matched".equals(a.status)||TextUtils.isEmpty(a.bggId)||a.languageBlocked)return false;
        if(a.averageRating!=null&&a.averageRating<DealPolicy.MIN_BGG_RATING)return false;
        // Unknown rating is enriched by BGG first. Once authoritative metadata arrives, hot rows
        // are promoted by applyBggMetadata.
        if(a.averageRating==null)return false;
        return "hot".equals(a.tier)||(a.discount!=null&&a.discount>=30.0);
    }


    /** Local classification never needs the scarce public Vinted lane. Classify freshly captured
     * scrolls in bounded newest-first batches even while an older session owns remote linking.
     * Remote jobs remain subject to active-run ownership, fairness and public-page pacing. */
    public List<VintedCard> pendingAnalysisCards(int limit) {
        List<VintedCard> out=new ArrayList<>();
        int bounded=Math.max(1,Math.min(8,limit));
        String sql="SELECT vinted_title,brand,item_condition,current_price_cents,protected_price_cents,favorites,observed_text,COALESCE(NULLIF(legacy_signature,''),temp_fingerprint),id " +
                "FROM market_listings WHERE lifecycle='ACTIVE' AND enrichment_state='PENDING_ANALYSIS' AND COALESCE(manual_review_required,0)=0 " +
                "ORDER BY last_seen DESC LIMIT ?";
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{String.valueOf(bounded)})){
            while(c.moveToNext()){
                double price=c.getInt(3)/100.0;
                Double protectedPrice=c.isNull(4)?null:c.getInt(4)/100.0;
                Integer fav=c.isNull(5)?null:c.getInt(5);
                out.add(new VintedCard(c.getString(0),c.getString(1),c.getString(2),price,protectedPrice,fav,
                        new Rect(0,0,1,1),c.isNull(6)?c.getString(0):c.getString(6),"",browserOwned(helper.getReadableDatabase(),c.getLong(8))?c.getString(7):""));
            }
        }
        return out;
    }

    /** Imports current legacy feed rows and schedules only missing work; safe to call repeatedly. */
    public int enqueueMissingLegacyDeals() {
        SQLiteDatabase db = helper.getWritableDatabase();
        long now = System.currentTimeMillis();
        int queued = 0;
        db.beginTransaction();
        try (Cursor c = db.rawQuery("SELECT signature,vinted_item_id,vinted_title,brand,item_condition,item_price_cents,protected_price_cents,favorites,bgg_id,seller_id,seller_name,vinted_url,image_url,listing_photos_csv,published_label,language_code,first_seen,last_seen,lifecycle,tier,rating " +
                "FROM deals WHERE lifecycle='ACTIVE' AND ((vinted_url IS NULL OR vinted_url='') OR (bgg_id IS NOT NULL AND bgg_id<>'' AND (bgg_image_url IS NULL OR bgg_image_url=''))) ORDER BY last_seen DESC", null)) {
            while (c.moveToNext()) {
                long listingId = ensureListingFromLegacyCursor(db, c, now);
                String bggId = c.getString(8);
                String vintedUrl = c.getString(11);
                String tier = c.isNull(19)?"":c.getString(19);
                Double rating = c.isNull(20)?null:c.getDouble(20);
                boolean alreadyActive=hasActiveWork(db,listingId);
                boolean scheduled=false;
                if (!TextUtils.isEmpty(bggId)) {
                    Long gameId = scalarLong(db, "SELECT id FROM games WHERE bgg_id=?", new String[]{bggId});
                    if (gameId != null&&bggRefreshDue(db,gameId,now)){enqueueGameJob(db, gameId, JOB_BGG, now);scheduled=true;}
                }
                // Hot deals enter the fast lane. Normal verified >=6 listings are kept as deferred
                // link work and are materialised in tiny batches, instead of creating a 1,500-job queue.
                boolean eligible=TextUtils.isEmpty(vintedUrl)&&rating!=null&&rating>=DealPolicy.MIN_BGG_RATING;
                boolean live=eligible&&"hot".equals(tier);
                if(live){enqueueListingJob(db,listingId,JOB_VINTED,now,320,"LIVE_DEAL");ContentValues st=new ContentValues();st.put("enrichment_state","PENDING_ENRICHMENT");db.update("market_listings",st,"id=?",new String[]{String.valueOf(listingId)});scheduled=true;}
                else if(eligible){ContentValues st=new ContentValues();st.put("enrichment_state","DEFERRED_LINK");st.put("deferred_retry_at",0);db.update("market_listings",st,"id=?",new String[]{String.valueOf(listingId)});}
                else if(TextUtils.isEmpty(vintedUrl)){ContentValues st=new ContentValues();st.put("enrichment_state","LOCAL_ONLY");db.update("market_listings",st,"id=?",new String[]{String.valueOf(listingId)});}
                if(scheduled&&!alreadyActive)queued++;
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return queued;
    }

    /** Quiet bounded sweep for incomplete current listings. Fresh listing maintenance stays ahead of historical recovery. */
    public int enqueueIncompleteListingsBackground(int limit) {
        SQLiteDatabase db=helper.getWritableDatabase();long now=System.currentTimeMillis();int queued=0;
        db.beginTransaction();
        try(Cursor c=db.rawQuery("SELECT id,game_id FROM market_listings WHERE lifecycle='ACTIVE' AND enrichment_state IN ('PENDING_ANALYSIS','PENDING_ENRICHMENT','FAILED_RETRYABLE') ORDER BY last_seen DESC LIMIT ?",new String[]{String.valueOf(Math.max(1,limit))})){
            while(c.moveToNext()){
                long listingId=c.getLong(0);String type=listingVintedJobType(db,listingId);
                if(type!=null){enqueueListingJob(db,listingId,type,now,JOB_VINTED.equals(type)?180:30,JOB_VINTED.equals(type)?"AUTO_MISSING":"DEEP_METADATA");queued++;}
                if(!c.isNull(1)){long gameId=c.getLong(1);if(bggRefreshDue(db,gameId,now))enqueueJob(db,"bgg:"+gameId,JOB_BGG,null,gameId,now,180,"AUTO_MISSING");}
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        return queued;
    }

    /** Promotes only incomplete listings that are not already represented by an active durable job.
     * This makes the global action idempotent: pressing it twice cannot keep "adding" the same rows. */
    public int prioritizeIncompleteListings(int limit) {
        SQLiteDatabase db=helper.getWritableDatabase();long now=System.currentTimeMillis();int queued=0;
        db.beginTransaction();
        String sql="SELECT l.id,l.game_id FROM market_listings l WHERE l.lifecycle='ACTIVE' " +
                "AND (l.enrichment_state IN ('PENDING_ANALYSIS','PENDING_ENRICHMENT','FAILED_RETRYABLE') OR " +
                "((l.vinted_url IS NOT NULL AND l.vinted_url<>'') AND ((l.published_label IS NULL OR l.published_label='') OR (l.seller_id IS NULL OR l.seller_id='')))) " +
                "AND NOT EXISTS(SELECT 1 FROM processing_jobs j WHERE j.state IN ('PENDING','PROCESSING','FAILED_RETRYABLE') " +
                "AND (j.listing_id=l.id OR (l.game_id IS NOT NULL AND j.game_id=l.game_id))) " +
                "ORDER BY CASE WHEN (l.published_label IS NULL OR l.published_label='' OR l.seller_id IS NULL OR l.seller_id='') THEN 0 ELSE 1 END,l.last_seen DESC LIMIT ?";
        try(Cursor c=db.rawQuery(sql,new String[]{String.valueOf(Math.max(1,limit))})){
            while(c.moveToNext()){
                long listingId=c.getLong(0);String type=listingVintedJobType(db,listingId);
                if(type!=null)enqueueListingJob(db,listingId,type,now,JOB_VINTED.equals(type)?170:45,JOB_VINTED.equals(type)?"GENERAL_CHECK":"DEEP_METADATA");
                if(!c.isNull(1)){long gameId=c.getLong(1);if(bggRefreshDue(db,gameId,now))enqueueJob(db,"bgg:"+gameId,JOB_BGG,null,gameId,now,170,"GENERAL_CHECK");}
                queued++;
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        if(queued>0){QueueWorkScheduler.schedule(context);QueueKeepAliveService.ensureRunning(context);}
        return queued;
    }

    /** Number of incomplete active listings that are not already covered by an active job. */
    public int unqueuedIncompleteCount() {
        String sql="SELECT COUNT(*) FROM market_listings l WHERE l.lifecycle='ACTIVE' " +
                "AND (l.enrichment_state IN ('PENDING_ANALYSIS','PENDING_ENRICHMENT','FAILED_RETRYABLE') OR " +
                "((l.vinted_url IS NOT NULL AND l.vinted_url<>'') AND ((l.published_label IS NULL OR l.published_label='') OR (l.seller_id IS NULL OR l.seller_id='')))) " +
                "AND NOT EXISTS(SELECT 1 FROM processing_jobs j WHERE j.state IN ('PENDING','PROCESSING','FAILED_RETRYABLE') " +
                "AND (j.listing_id=l.id OR (l.game_id IS NOT NULL AND j.game_id=l.game_id)))";
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,null)){return c.moveToFirst()?c.getInt(0):0;}
    }

    /** Active job for one legacy/feed card, used to render a real button state. */
    public Job activeJobForLegacySignature(String signature) {
        if(TextUtils.isEmpty(signature))return null;
        String sql="SELECT j.id,j.job_key,j.job_type,j.listing_id,j.game_id,j.state,j.attempt,j.next_attempt_at,j.last_error,j.priority,j.source,j.progress AS stored_progress,"+
                "COALESCE(l.vinted_title,g.canonical_name,'') AS label,COALESCE(j.game_id,l.game_id,0) AS display_game_id,g.bgg_id,g.thumbnail_url,g.image_url,j.progress AS progress " +
                "FROM market_listings l JOIN processing_jobs j ON (j.listing_id=l.id OR (l.game_id IS NOT NULL AND j.game_id=l.game_id)) " +
                "LEFT JOIN games g ON g.id=COALESCE(j.game_id,l.game_id) WHERE (l.legacy_signature=? OR l.temp_fingerprint=?) " +
                "AND j.state IN ('PENDING','PROCESSING','FAILED_RETRYABLE') " +
                "ORDER BY CASE j.state WHEN 'PROCESSING' THEN 0 WHEN 'FAILED_RETRYABLE' THEN 1 ELSE 2 END,j.priority DESC,j.updated_at DESC LIMIT 1";
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{signature,signature})){return c.moveToFirst()?readJob(c):null;}
    }

    /** Latest durable job for a legacy/feed card, including rows that already require manual review.
     * Used by the detail screen so manual repair is always reachable even after automatic retries stop. */
    public Job latestJobForLegacySignature(String signature) {
        if(TextUtils.isEmpty(signature))return null;
        String sql="SELECT j.id,j.job_key,j.job_type,j.listing_id,j.game_id,j.state,j.attempt,j.next_attempt_at,j.last_error,j.priority,j.source,j.progress AS stored_progress,"+
                "COALESCE(l.vinted_title,g.canonical_name,'') AS label,COALESCE(j.game_id,l.game_id,0) AS display_game_id,g.bgg_id,g.thumbnail_url,g.image_url,j.progress AS progress " +
                "FROM market_listings l JOIN processing_jobs j ON (j.listing_id=l.id OR (l.game_id IS NOT NULL AND j.game_id=l.game_id)) " +
                "LEFT JOIN games g ON g.id=COALESCE(j.game_id,l.game_id) WHERE (l.legacy_signature=? OR l.temp_fingerprint=?) AND j.job_type IN (?,?) " +
                "ORDER BY CASE j.state WHEN 'PROCESSING' THEN 0 WHEN 'FAILED_RETRYABLE' THEN 1 WHEN 'PENDING' THEN 2 WHEN 'FAILED_PERMANENT' THEN 3 ELSE 4 END,j.priority DESC,j.updated_at DESC LIMIT 1";
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{signature,signature,JOB_VINTED,JOB_VINTED_DEEP})){return c.moveToFirst()?readJob(c):null;}
    }

    /** Latest Vinted job for one canonical listing. Used to restore an open recovery sheet after
     * configuration/process recreation without sending the user back to the home screen. */
    public Job latestJobForListing(long listingId) {
        if(listingId<=0)return null;
        String sql="SELECT j.id,j.job_key,j.job_type,j.listing_id,j.game_id,j.state,j.attempt,j.next_attempt_at,j.last_error,j.priority,j.source,j.progress AS stored_progress,"+
                "COALESCE(l.vinted_title,g.canonical_name,'') AS label,COALESCE(j.game_id,l.game_id,0) AS display_game_id,g.bgg_id,g.thumbnail_url,g.image_url,j.progress AS progress " +
                "FROM processing_jobs j LEFT JOIN market_listings l ON l.id=j.listing_id " +
                "LEFT JOIN games g ON g.id=COALESCE(j.game_id,l.game_id) WHERE j.listing_id=? AND j.job_type IN (?,?) " +
                "ORDER BY CASE j.state WHEN 'PROCESSING' THEN 0 WHEN 'FAILED_RETRYABLE' THEN 1 WHEN 'PENDING' THEN 2 WHEN 'FAILED_PERMANENT' THEN 3 ELSE 4 END,j.priority DESC,j.updated_at DESC LIMIT 1";
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{String.valueOf(listingId),JOB_VINTED,JOB_VINTED_DEEP})){return c.moveToFirst()?readJob(c):null;}
    }

    /** Raises an already queued listing to interactive priority and makes a retry immediately due. */
    public boolean promoteLegacyListing(String signature) {
        if(TextUtils.isEmpty(signature))return false;
        SQLiteDatabase db=helper.getWritableDatabase();long now=System.currentTimeMillis();
        ContentValues v=new ContentValues();v.put("priority",260);v.put("source","MANUAL_PRIORITY");v.put("next_attempt_at",0);v.put("updated_at",now);
        int changed=db.update("processing_jobs",v,"state IN (?,?) AND (listing_id IN (SELECT id FROM market_listings WHERE legacy_signature=? OR temp_fingerprint=?) OR game_id IN (SELECT game_id FROM market_listings WHERE legacy_signature=? OR temp_fingerprint=?))",
                new String[]{PENDING,FAILED_RETRYABLE,signature,signature,signature,signature});
        if(changed>0){QueueWorkScheduler.schedule(context);QueueKeepAliveService.ensureRunning(context);notifyQueueChanged();}
        return changed>0;
    }

    /** Returns 1 when newly queued, 2 when already queued/promoted, 0 when no work was necessary. */
    public int enqueueListingFromLegacy(DealRecord d) {
        if (d == null || TextUtils.isEmpty(d.signature)) return 0;
        Job existing=activeJobForLegacySignature(d.signature);
        if(existing!=null){
            if(PROCESSING.equals(existing.state))return 2;
            promoteLegacyListing(d.signature);return 2;
        }
        SQLiteDatabase db = helper.getWritableDatabase();
        long now = System.currentTimeMillis();
        db.beginTransaction();
        try {
            long listingId = ensureListingFromDeal(db, d, now);
            String listingType=TextUtils.isEmpty(d.vintedUrl)?JOB_VINTED:JOB_VINTED_DEEP;
            enqueueListingJob(db, listingId, listingType, now,260,"MANUAL_PRIORITY");
            if (!TextUtils.isEmpty(d.bggId)) {
                long gameId = ensureGameFromDeal(db, d, now);
                ContentValues link = new ContentValues(); link.put("game_id", gameId); link.put("match_state", "MATCHED");
                db.update("market_listings", link, "id=?", new String[]{String.valueOf(listingId)});
                if(bggRefreshDue(db,gameId,now))enqueueJob(db,"bgg:"+gameId,JOB_BGG,null,gameId,now,260,"MANUAL_PRIORITY");
            }
            db.setTransactionSuccessful();
            return 1;
        } finally { db.endTransaction(); }
    }

    public void resetStaleProcessing() {
        resetStaleProcessingOlderThan(Long.MAX_VALUE);
    }

    /** Recovery-safe variant for WorkManager: never steals a job that another live lane has just claimed. */
    public void resetStaleProcessingOlderThan(long ageMs) {
        long now=System.currentTimeMillis();
        long cutoff=ageMs==Long.MAX_VALUE?Long.MAX_VALUE:now-Math.max(60_000L,ageMs);
        ContentValues v = new ContentValues();
        v.put("state", FAILED_RETRYABLE);
        v.put("next_attempt_at", now);
        v.put("updated_at", now);
        v.put("last_error", "process restart");
        if(ageMs==Long.MAX_VALUE) helper.getWritableDatabase().update("processing_jobs", v, "state=?", new String[]{PROCESSING});
        else helper.getWritableDatabase().update("processing_jobs", v, "state=? AND updated_at<?", new String[]{PROCESSING,String.valueOf(cutoff)});
    }

    public Job claimNextVintedJob(long now) {
        return claimNextVintedJobInternal(now,false);
    }

    /** TEST 2b only: claim one ordinary Vinted job while the diagnostic exclusive lock is held.
     * The lock blocks normal queue consumers but never bypasses pause, pacing, cooldown or budget. */
    public Job claimNextVintedJobForTest2b(long now) {
        return claimNextVintedJobInternal(now,true);
    }

    private Job claimNextVintedJobInternal(long now, boolean test2bOwner) {
        DbContentionTrace.Scope trace=DbContentionTrace.start("MarketStore.claimNextVintedJobInternal");
        try{
        if (isVintedPaused()) return null;
        if (!test2bOwner && isTest2bExclusiveActive()) return null;
        trace.phase("OPEN_DATABASE");SQLiteDatabase db = helper.getWritableDatabase();trace.phase("PRE_TRANSACTION");
        trace.phase("ACQUIRE_WRITER");db.beginTransaction();trace.phase("TRANSACTION");
        try {
            Job job = null;
            // Core identity and deep metadata share exactly one public-page lane. Core always wins.
            try (Cursor running = db.rawQuery("SELECT 1 FROM processing_jobs WHERE job_type IN (?,?) AND state=? LIMIT 1", new String[]{JOB_VINTED, JOB_VINTED_DEEP, PROCESSING})) {
                if (running.moveToFirst()) { db.setTransactionSuccessful(); return null; }
            }
            boolean allowHistory = vintedHistoryAllowed(db, now);
            trace.phase("HELPER_CALL");
            DealDatabase.ObservationSession activeRun=test2bOwner?null:helper.activeObservationSession();
            trace.phase("TRANSACTION");
            String runGate=test2bOwner?"":"AND (j.source IN ('HUNT_PRIORITY','MANUAL_PRIORITY','MANUAL_RECOVERY','OPENED_VERIFY') OR (CAST(? AS INTEGER) = 0 AND j.source IN ('LIVE_DEAL','CATALOG_HEALTH','CATALOG_RECOVERY','SELLER_BACKFILL')) OR (CAST(? AS INTEGER) > 0 AND j.source<>'SELLER_BACKFILL' AND COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) IN (SELECT signature FROM observations WHERE observed_at>=? AND observed_at<=?))) ";
            String sql = "SELECT j.id,j.job_key,j.job_type,j.listing_id,j.game_id,j.state,j.attempt,j.next_attempt_at,j.last_error,j.priority,j.source " +
                    "FROM processing_jobs j JOIN market_listings l ON l.id=j.listing_id " +
                    "WHERE j.job_type IN (?,?) AND j.state IN (?,?) AND j.next_attempt_at<=? AND l.lifecycle='ACTIVE' " +
                    (allowHistory ? "" : "AND j.source<>? ") + runGate +
                    "ORDER BY CASE WHEN j.source='OPENED_VERIFY' THEN 0 ELSE 1 END,CASE WHEN j.job_type=? THEN 0 ELSE 1 END,j.priority DESC,CASE WHEN j.priority>=300 THEN j.created_at END DESC,j.next_attempt_at ASC,j.created_at ASC LIMIT 1";
            java.util.ArrayList<String> argList=new java.util.ArrayList<>();argList.add(JOB_VINTED);argList.add(JOB_VINTED_DEEP);argList.add(PENDING);argList.add(FAILED_RETRYABLE);argList.add(String.valueOf(now));if(!allowHistory)argList.add(HISTORICAL_SOURCE);if(!test2bOwner){long start=activeRun==null?0:activeRun.startAt,end=activeRun==null?0:activeRun.endAt;argList.add(String.valueOf(start));argList.add(String.valueOf(start));argList.add(String.valueOf(start));argList.add(String.valueOf(end));}argList.add(JOB_VINTED);
            try (Cursor c = db.rawQuery(sql, argList.toArray(new String[0]))) {
                if (c.moveToFirst()) job = readJob(c);
            }
            if (job != null) {
                ContentValues v = new ContentValues();
                v.put("state", PROCESSING);
                v.put("attempt", job.attempt + 1);
                v.put("updated_at", now);
                v.put("progress", Math.max(15, job.progress));
                v.put("processing_started_at", now);
                int changed = db.update("processing_jobs", v, "id=? AND state IN (?,?)", new String[]{String.valueOf(job.id), PENDING, FAILED_RETRYABLE});
                if (changed == 1) { job.state = PROCESSING; job.attempt++; job.progress=Math.max(15,job.progress); job.processingStartedAt=now; }
                else job = null;
            }
            db.setTransactionSuccessful();
            return job;
        } finally { trace.phase("COMMIT");try{db.endTransaction();}finally{trace.phase("POST_TRANSACTION");} }
    
        }catch(RuntimeException|Error failure){trace.failed(failure);throw failure;}finally{trace.close();}
    }



    public Job claimNextBggJob(long now) {
        DbContentionTrace.Scope trace=DbContentionTrace.start("MarketStore.claimNextBggJob");
        try{
        if (isBggPaused()) return null;
        trace.phase("OPEN_DATABASE");SQLiteDatabase db = helper.getWritableDatabase();trace.phase("PRE_TRANSACTION");
        trace.phase("ACQUIRE_WRITER");db.beginTransaction();trace.phase("TRANSACTION");
        try {
            Job job = null;
            // BGG can use two lanes, but never fan out without a bound.
            try (Cursor running = db.rawQuery("SELECT COUNT(*) FROM processing_jobs WHERE job_type=? AND state=?", new String[]{JOB_BGG, PROCESSING})) {
                if (running.moveToFirst() && running.getInt(0) >= 2) { db.setTransactionSuccessful(); return null; }
            }
            trace.phase("HELPER_CALL");
            boolean allowHistory = historyAllowed(db, now, JOB_BGG);DealDatabase.ObservationSession run=helper.activeObservationSession();
            trace.phase("TRANSACTION");
            String historyExtra=allowHistory?"":"AND j.source<>? ";
            String runExtra=run==null?"":"AND (j.source IN ('HUNT_PRIORITY','MANUAL_PRIORITY') OR EXISTS (SELECT 1 FROM market_listings l WHERE l.game_id=j.game_id AND COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) IN (SELECT signature FROM observations WHERE observed_at>=? AND observed_at<=?))) ";
            String sql = "SELECT j.id,j.job_key,j.job_type,j.listing_id,j.game_id,j.state,j.attempt,j.next_attempt_at,j.last_error,j.priority,j.source " +
                    "FROM processing_jobs j JOIN games g ON g.id=j.game_id " +
                    "WHERE j.job_type=? AND j.state IN (?,?) AND j.next_attempt_at<=? AND g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND g.database_visible=1 " +
                    historyExtra+runExtra+
                    "ORDER BY j.priority DESC,j.next_attempt_at ASC,j.created_at ASC LIMIT 1";
            java.util.ArrayList<String> args=new java.util.ArrayList<>();args.add(JOB_BGG);args.add(PENDING);args.add(FAILED_RETRYABLE);args.add(String.valueOf(now));if(!allowHistory)args.add(HISTORICAL_SOURCE);if(run!=null){args.add(String.valueOf(run.startAt));args.add(String.valueOf(run.endAt));}
            try (Cursor c = db.rawQuery(sql, args.toArray(new String[0]))) {
                if (c.moveToFirst()) job = readJob(c);
            }
            if (job != null) {
                ContentValues v = new ContentValues();
                v.put("state", PROCESSING);
                v.put("attempt", job.attempt + 1);
                v.put("updated_at", now);
                v.put("progress", Math.max(15, job.progress));
                v.put("processing_started_at", now);
                int changed = db.update("processing_jobs", v, "id=? AND state IN (?,?)", new String[]{String.valueOf(job.id), PENDING, FAILED_RETRYABLE});
                if (changed == 1) { job.state = PROCESSING; job.attempt++; job.progress=Math.max(15,job.progress); job.processingStartedAt=now; }
                else job = null;
            }
            db.setTransactionSuccessful();
            return job;
        } finally { trace.phase("COMMIT");try{db.endTransaction();}finally{trace.phase("POST_TRANSACTION");} }
    
        }catch(RuntimeException|Error failure){trace.failed(failure);throw failure;}finally{trace.close();}
    }


    /** Claims one HTTP-sized BGG batch atomically. XML API2 accepts at most 20 ids per thing call. */
    public List<Job> claimBggBatch(long now,int limit) {
        DbContentionTrace.Scope trace=DbContentionTrace.start("MarketStore.claimBggBatch");
        try{
        if (isBggPaused()) return new ArrayList<>();
        int wanted=Math.max(1,Math.min(20,limit));
        trace.phase("OPEN_DATABASE");SQLiteDatabase db=helper.getWritableDatabase();trace.phase("PRE_TRANSACTION");
        trace.phase("ACQUIRE_WRITER");db.beginTransaction();trace.phase("TRANSACTION");
        try {
            List<Job> jobs=new ArrayList<>();
            // One batch request at a time. This is still up to 20 database jobs per HTTP call.
            try(Cursor running=db.rawQuery("SELECT 1 FROM processing_jobs WHERE job_type=? AND state=? LIMIT 1",new String[]{JOB_BGG,PROCESSING})){
                if(running.moveToFirst()){db.setTransactionSuccessful();return jobs;}
            }
            trace.phase("HELPER_CALL");
            boolean allowHistory=historyAllowed(db,now,JOB_BGG);DealDatabase.ObservationSession run=helper.activeObservationSession();
            trace.phase("TRANSACTION");
            String historyExtra=allowHistory?"":"AND j.source<>? ";
            String runExtra=run==null?"":"AND (j.source IN ('HUNT_PRIORITY','MANUAL_PRIORITY') OR EXISTS (SELECT 1 FROM market_listings l WHERE l.game_id=j.game_id AND COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) IN (SELECT signature FROM observations WHERE observed_at>=? AND observed_at<=?))) ";
            String sql="SELECT j.id,j.job_key,j.job_type,j.listing_id,j.game_id,j.state,j.attempt,j.next_attempt_at,j.last_error,j.priority,j.source " +
                    "FROM processing_jobs j JOIN games g ON g.id=j.game_id " +
                    "WHERE j.job_type=? AND j.state IN (?,?) AND j.next_attempt_at<=? AND g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND g.database_visible=1 " +
                    historyExtra+runExtra+
                    "ORDER BY j.priority DESC,j.next_attempt_at ASC,j.created_at ASC LIMIT ?";
            java.util.ArrayList<String> args=new java.util.ArrayList<>();args.add(JOB_BGG);args.add(PENDING);args.add(FAILED_RETRYABLE);args.add(String.valueOf(now));if(!allowHistory)args.add(HISTORICAL_SOURCE);if(run!=null){args.add(String.valueOf(run.startAt));args.add(String.valueOf(run.endAt));}args.add(String.valueOf(wanted));
            try(Cursor c=db.rawQuery(sql,args.toArray(new String[0]))){while(c.moveToNext())jobs.add(readJob(c));}
            if(jobs.isEmpty()){db.setTransactionSuccessful();return jobs;}
            long started=System.currentTimeMillis();
            ArrayList<Job> claimed=new ArrayList<>();
            for(Job job:jobs){
                ContentValues v=new ContentValues();v.put("state",PROCESSING);v.put("attempt",job.attempt+1);v.put("updated_at",started);v.put("progress",Math.max(15,job.progress));v.put("processing_started_at",started);
                int changed=db.update("processing_jobs",v,"id=? AND state IN (?,?)",new String[]{String.valueOf(job.id),PENDING,FAILED_RETRYABLE});
                if(changed==1){job.state=PROCESSING;job.attempt++;job.progress=Math.max(15,job.progress);job.processingStartedAt=started;claimed.add(job);}
            }
            db.setTransactionSuccessful();return claimed;
        } finally {trace.phase("COMMIT");try{db.endTransaction();}finally{trace.phase("POST_TRANSACTION");}}
    
        }catch(RuntimeException|Error failure){trace.failed(failure);throw failure;}finally{trace.close();}
    }


    public String bggIdForGame(long gameId) {
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT bgg_id FROM games WHERE id=?",new String[]{String.valueOf(gameId)})){
            return c.moveToFirst()?c.getString(0):null;
        }
    }

    public List<String> dueBggIds(long now, int limit) {
        List<String> out = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        boolean allowHistory = historyAllowed(db, now, JOB_BGG);
        String sql = "SELECT g.bgg_id FROM processing_jobs j JOIN games g ON g.id=j.game_id " +
                "WHERE j.job_type=? AND j.state IN (?,?) AND j.next_attempt_at<=? AND g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND g.database_visible=1 " +
                (allowHistory ? "" : "AND j.source<>? ") +
                "ORDER BY j.priority DESC,j.next_attempt_at,j.created_at LIMIT ?";
        String[] args = allowHistory
                ? new String[]{JOB_BGG, PENDING, FAILED_RETRYABLE, String.valueOf(now), String.valueOf(Math.max(1, limit))}
                : new String[]{JOB_BGG, PENDING, FAILED_RETRYABLE, String.valueOf(now), HISTORICAL_SOURCE, String.valueOf(Math.max(1, limit))};
        try (Cursor c = db.rawQuery(sql, args)) {
            while (c.moveToNext()) out.add(c.getString(0));
        }
        return out;
    }


    public void markBggProcessing(String bggId) {
        Long gameId = gameIdForBgg(bggId); if (gameId == null) return;
        markJobState("bgg:" + gameId, PROCESSING, 0L, null, true);
    }

    public void markBggComplete(String bggId) {
        Long gameId = gameIdForBgg(bggId); if (gameId == null) return;
        markJobState("bgg:" + gameId, COMPLETE, 0L, null, false);
    }

    public void markBggRetry(String bggId, String error, long nextAt) {
        Long gameId = gameIdForBgg(bggId); if (gameId == null) return;
        markJobState("bgg:" + gameId, FAILED_RETRYABLE, nextAt, error, false);
    }


    /** True only while this exact processing lease still owns the row. */
    public boolean isLeaseActive(Job job) {
        if(job==null||job.id<=0)return false;
        String sql=job.processingStartedAt>0
                ? "SELECT 1 FROM processing_jobs WHERE id=? AND state=? AND processing_started_at=? LIMIT 1"
                : "SELECT 1 FROM processing_jobs WHERE id=? AND state=? LIMIT 1";
        String[] args=job.processingStartedAt>0
                ? new String[]{String.valueOf(job.id),PROCESSING,String.valueOf(job.processingStartedAt)}
                : new String[]{String.valueOf(job.id),PROCESSING};
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,args)){return c.moveToFirst();}
    }

    /**
     * Release the current item without losing it. It moves behind the current work and becomes
     * retryable later; the serial Vinted lane can claim the next due row immediately.
     */
    public boolean deferProcessingJob(long jobId,long delayMs,String reason) {
        long now=System.currentTimeMillis();
        ContentValues v=new ContentValues();
        v.put("state",FAILED_RETRYABLE);
        v.put("next_attempt_at",now+Math.max(60_000L,delayMs));
        v.put("updated_at",now);
        v.put("last_error",safe(reason));
        v.put("progress",15);
        v.put("processing_started_at",0);
        int changed=helper.getWritableDatabase().update("processing_jobs",v,"id=? AND state=?",new String[]{String.valueOf(jobId),PROCESSING});
        if(changed>0){notifyQueueChanged();Log.w(TAG,"job="+jobId+" manually deferred: "+safe(reason));}
        return changed>0;
    }

    /** Safety net: a Vinted item must never monopolise the single network lane forever. */
    public int deferStuckVintedProcessing(long maxAgeMs,long retryDelayMs) {
        long now=System.currentTimeMillis(),cutoff=now-Math.max(60_000L,maxAgeMs);
        ContentValues v=new ContentValues();
        v.put("state",FAILED_RETRYABLE);
        v.put("next_attempt_at",now+Math.max(60_000L,retryDelayMs));
        v.put("updated_at",now);
        v.put("last_error","interrotto automaticamente: elaborazione troppo lunga");
        v.put("progress",15);
        v.put("processing_started_at",0);
        int changed=helper.getWritableDatabase().update("processing_jobs",v,"job_type IN (?,?) AND state=? AND COALESCE(NULLIF(processing_started_at,0),updated_at)<CAST(? AS INTEGER)",new String[]{JOB_VINTED,JOB_VINTED_DEEP,PROCESSING,String.valueOf(cutoff)});
        if(changed>0){notifyQueueChanged();Log.w(TAG,"watchdog deferred "+changed+" stuck Vinted job(s)");}
        return changed;
    }

    /** Safety net: a BGG item must not hold its lane forever when its owner stops making progress. */
    public int deferStuckBggProcessing(long maxAgeMs,long retryDelayMs) {
        long now=System.currentTimeMillis(),cutoff=now-Math.max(60_000L,maxAgeMs);
        ContentValues v=new ContentValues();
        v.put("state",FAILED_RETRYABLE);
        v.put("next_attempt_at",now+Math.max(60_000L,retryDelayMs));
        v.put("updated_at",now);
        v.put("last_error","interrotto automaticamente: elaborazione troppo lunga");
        v.put("progress",15);
        v.put("processing_started_at",0);
        int changed=helper.getWritableDatabase().update("processing_jobs",v,"job_type=? AND state=? AND processing_started_at>0 AND processing_started_at<?",new String[]{JOB_BGG,PROCESSING,String.valueOf(cutoff)});
        if(changed>0){notifyQueueChanged();Log.w(TAG,"watchdog deferred "+changed+" stuck BGG job(s)");}
        return changed;
    }

    public void retryJob(Job job, String error, long nextAt) {
        if (job == null) return;
        ContentValues v = new ContentValues();
        v.put("state", FAILED_RETRYABLE);
        v.put("next_attempt_at", Math.max(System.currentTimeMillis() + 1_000L, nextAt));
        v.put("updated_at", System.currentTimeMillis());
        v.put("last_error", safe(error));
        v.put("progress", Math.min(35, Math.max(10, job.progress)));
        int changed=job.processingStartedAt>0
                ? helper.getWritableDatabase().update("processing_jobs",v,"id=? AND state=? AND processing_started_at=?",new String[]{String.valueOf(job.id),PROCESSING,String.valueOf(job.processingStartedAt)})
                : helper.getWritableDatabase().update("processing_jobs",v,"id=?",new String[]{String.valueOf(job.id)});
        if(changed==0)return;
        updateListingState(job.listingId, FAILED_RETRYABLE, error);
        Log.w(TAG, "job=" + job.id + " state=FAILED_RETRYABLE attempt=" + job.attempt + " next=" + nextAt + " error=" + safe(error));
        notifyQueueChanged();
    }

    public void setJobProgress(Job job,int progress) {
        if(job==null)return;
        int p=Math.max(0,Math.min(99,progress));
        job.progress=p;
        ContentValues v=new ContentValues();v.put("progress",p);v.put("updated_at",System.currentTimeMillis());
        helper.getWritableDatabase().update("processing_jobs",v,"id=? AND state=?",new String[]{String.valueOf(job.id),PROCESSING});
        notifyQueueChanged();
    }

    /** Update one BGG batch with one transaction/broadcast instead of up to twenty UI wakeups. */
    public void setJobsProgress(List<Job> jobs,int progress) {
        if(jobs==null||jobs.isEmpty())return;
        int p=Math.max(0,Math.min(99,progress));long now=System.currentTimeMillis();
        SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();boolean changed=false;
        try{
            for(Job job:jobs){
                if(job==null||job.id<=0)continue;ContentValues v=new ContentValues();v.put("progress",p);v.put("updated_at",now);
                String where=job.processingStartedAt>0?"id=? AND state=? AND processing_started_at=?":"id=? AND state=?";
                String[] args=job.processingStartedAt>0?new String[]{String.valueOf(job.id),PROCESSING,String.valueOf(job.processingStartedAt)}:new String[]{String.valueOf(job.id),PROCESSING};
                if(db.update("processing_jobs",v,where,args)>0){job.progress=p;changed=true;}
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        if(changed)notifyQueueChanged();
    }

    public void failPermanent(Job job, String error) {
        if (job == null) return;
        ContentValues v = new ContentValues();
        v.put("state", FAILED_PERMANENT); v.put("updated_at", System.currentTimeMillis()); v.put("last_error", safe(error)); v.put("progress", Math.max(10, job.progress));
        int changed=job.processingStartedAt>0
                ? helper.getWritableDatabase().update("processing_jobs",v,"id=? AND state=? AND processing_started_at=?",new String[]{String.valueOf(job.id),PROCESSING,String.valueOf(job.processingStartedAt)})
                : helper.getWritableDatabase().update("processing_jobs",v,"id=?",new String[]{String.valueOf(job.id)});
        if(changed==0)return;
        updateListingState(job.listingId, FAILED_PERMANENT, error);
        markManualReviewOpen(job.listingId,error);
        Log.e(TAG, "job=" + job.id + " state=FAILED_PERMANENT attempt=" + job.attempt + " error=" + safe(error));
        notifyQueueChanged();
    }

    public void needsReview(Job job,String error) {
        if(job==null)return;
        ContentValues v=new ContentValues();v.put("state",FAILED_PERMANENT);v.put("next_attempt_at",0);v.put("updated_at",System.currentTimeMillis());v.put("last_error",safe(error));v.put("progress",Math.max(10,job.progress));
        int changed=job.processingStartedAt>0
                ? helper.getWritableDatabase().update("processing_jobs",v,"id=? AND state=? AND processing_started_at=?",new String[]{String.valueOf(job.id),PROCESSING,String.valueOf(job.processingStartedAt)})
                : helper.getWritableDatabase().update("processing_jobs",v,"id=?",new String[]{String.valueOf(job.id)});
        if(changed==0)return;
        updateListingState(job.listingId,"NEEDS_REVIEW",error);
        markManualReviewOpen(job.listingId,error);
        Log.i(TAG,"job="+job.id+" state=NEEDS_REVIEW attempt="+job.attempt+" error="+safe(error));
        notifyQueueChanged();
    }

    /** Automatic ambiguity is not a user task. Ordinary rows that cannot be resolved safely are
     * parked reversibly; an explicit Hunt/manual request keeps the old human-review path. */
    public void autoExcludeJob(Job job,String reason){
        if(job==null)return;SQLiteDatabase db=helper.getWritableDatabase();long now=System.currentTimeMillis();
        db.beginTransaction();try{
            ContentValues j=new ContentValues();j.put("state",COMPLETE);j.put("next_attempt_at",0);j.put("updated_at",now);j.put("progress",100);j.put("processing_started_at",0);j.put("last_error","auto-excluded: "+safe(reason));
            db.update("processing_jobs",j,"id=?",new String[]{String.valueOf(job.id)});
            ContentValues l=new ContentValues();l.put("lifecycle","AUTO_FILTERED");l.put("enrichment_state","AUTO_EXCLUDED");l.put("last_error",safe(reason));l.put("manual_review_required",0);l.putNull("manual_review_reason");
            db.update("market_listings",l,"id=?",new String[]{String.valueOf(job.listingId)});
            String sig=scalarString(db,"SELECT legacy_signature FROM market_listings WHERE id=?",new String[]{String.valueOf(job.listingId)});
            if(!TextUtils.isEmpty(sig)){ContentValues d=new ContentValues();d.put("lifecycle","USER_HIDDEN");d.put("verification_state","AUTO_EXCLUDED");d.put("verification_reason",safe(reason));db.update("deals",d,"signature=?",new String[]{sig});}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        notifyQueueChanged();
    }

    public static boolean isExplicitUserPriority(Job job){
        return job!=null&&("HUNT_PRIORITY".equals(job.source)||"MANUAL_PRIORITY".equals(job.source)||MANUAL_RECOVERY_SOURCE.equals(job.source));
    }

    public boolean hasExplicitUserPriorityHistory(long listingId){
        if(listingId<=0)return false;
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT 1 FROM processing_jobs WHERE listing_id=? AND source IN ('HUNT_PRIORITY','MANUAL_PRIORITY','MANUAL_RECOVERY') LIMIT 1",new String[]{String.valueOf(listingId)})){return c.moveToFirst();}
    }

    private void markManualReviewOpen(long listingId,String reason){
        if(listingId<=0)return;ContentValues r=new ContentValues();r.put("manual_review_required",1);r.put("manual_review_reason",safe(reason));helper.getWritableDatabase().update("market_listings",r,"id=?",new String[]{String.valueOf(listingId)});
    }

    private void clearManualReview(long listingId){
        if(listingId<=0)return;ContentValues r=new ContentValues();r.put("manual_review_required",0);r.putNull("manual_review_reason");helper.getWritableDatabase().update("market_listings",r,"id=?",new String[]{String.valueOf(listingId)});
    }

    public void completeJob(Job job) {
        if (job == null) return;
        ContentValues v = new ContentValues();
        v.put("state", COMPLETE); v.put("next_attempt_at", 0); v.put("updated_at", System.currentTimeMillis()); v.put("last_error", ""); v.put("progress", 100);
        int changed=job.processingStartedAt>0
                ? helper.getWritableDatabase().update("processing_jobs",v,"id=? AND state=? AND processing_started_at=?",new String[]{String.valueOf(job.id),PROCESSING,String.valueOf(job.processingStartedAt)})
                : helper.getWritableDatabase().update("processing_jobs",v,"id=?",new String[]{String.valueOf(job.id)});
        if(changed==0)return;
        updateListingState(job.listingId, COMPLETE, "");
        if(job.listingId>0)helper.materializeCanonicalDeal(job.listingId);
        Log.i(TAG, "job=" + job.id + " state=COMPLETE attempt=" + job.attempt + " listing=" + job.listingId);
        notifyQueueChanged();
    }

    /** Completes the leased source job, then bridges the surviving listing returned by a merge. */
    public void completeResolvedVintedJob(Job job,long canonicalListingId){
        completeJob(job);
        if(canonicalListingId>0)helper.materializeCanonicalDeal(canonicalListingId);
    }

    public MarketListingRecord listing(long id) {
        try (Cursor c = helper.getReadableDatabase().rawQuery("SELECT id,game_id,temp_fingerprint,vinted_item_id,vinted_title,brand,item_condition,current_price_cents,protected_price_cents,favorites,seller_id,seller_name,vinted_url,image_url,listing_photos_csv,published_label,language_code,lifecycle,enrichment_state,match_state,match_confidence,first_seen,last_seen,enriched_at,last_error FROM market_listings WHERE id=?", new String[]{String.valueOf(id)})) {
            return c.moveToFirst() ? readListing(c) : null;
        }
    }

    public String signatureForListing(long id){try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT COALESCE(NULLIF(legacy_signature,''),temp_fingerprint) FROM market_listings WHERE id=?",new String[]{String.valueOf(id)})){return c.moveToFirst()?c.getString(0):null;}}
    public long listingIdForSignature(String signature){if(TextUtils.isEmpty(signature))return 0L;Long id=scalarLong(helper.getReadableDatabase(),"SELECT id FROM market_listings WHERE legacy_signature=? OR temp_fingerprint=? LIMIT 1",new String[]{signature,signature});return id==null?0L:id;}
    public long listingIdForVintedItemId(String itemId){if(TextUtils.isEmpty(itemId))return 0L;Long id=scalarLong(helper.getReadableDatabase(),"SELECT id FROM market_listings WHERE vinted_item_id=? ORDER BY CASE WHEN lifecycle='ACTIVE' THEN 0 ELSE 1 END,id DESC LIMIT 1",new String[]{itemId.trim()});return id==null?0L:id;}

    /** Refresh the current asking price only from an exact public item page. Historical observations
     * stay immutable: a new price point is appended instead of rewriting what Accessibility saw.
     * If the item price changed but the page did not expose a fresh buyer-protection total, the old
     * protected value is cleared because it necessarily belongs to the previous asking price. */
    public boolean updateVerifiedCurrentPrice(long listingId,Integer priceCents,Integer protectedPriceCents){
        if(listingId<=0||priceCents==null||priceCents<=0)return false;SQLiteDatabase db=helper.getWritableDatabase();long now=System.currentTimeMillis();
        Integer oldPrice=null,oldProtected=null;try(Cursor c=db.rawQuery("SELECT current_price_cents,protected_price_cents FROM market_listings WHERE id=?",new String[]{String.valueOf(listingId)})){if(!c.moveToFirst())return false;oldPrice=c.getInt(0);oldProtected=c.isNull(1)?null:c.getInt(1);}
        boolean priceChanged=oldPrice==null||oldPrice.intValue()!=priceCents.intValue();
        Integer nextProtected=protectedPriceCents!=null&&protectedPriceCents>0?protectedPriceCents:(priceChanged?null:oldProtected);
        boolean protectedChanged=!sameInt(oldProtected,nextProtected);if(!priceChanged&&!protectedChanged)return false;
        db.beginTransaction();try{ContentValues v=new ContentValues();v.put("current_price_cents",priceCents);if(nextProtected==null)v.putNull("protected_price_cents");else v.put("protected_price_cents",nextProtected);v.put("last_seen",now);db.update("market_listings",v,"id=?",new String[]{String.valueOf(listingId)});ContentValues obs=new ContentValues();obs.put("listing_id",listingId);obs.put("observed_at",now);obs.put("price_cents",priceCents);put(obs,"protected_price_cents",nextProtected);obs.put("source","vinted-item-page");db.insert("price_observations",null,obs);db.setTransactionSuccessful();}finally{db.endTransaction();}
        return true;
    }

    public long applyResolvedLink(Job job, VintedLinkResolver.Result r) {
        if (job == null || r == null) return -1L;
        if (!isLeaseActive(job)) return -1L;
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            long sourceId = job.listingId;
            Long targetId = TextUtils.isEmpty(r.itemId) ? null : scalarLong(db, "SELECT id FROM market_listings WHERE vinted_item_id=? AND id<>?", new String[]{r.itemId, String.valueOf(sourceId)});
            long canonicalId = sourceId;
            if (targetId != null) {
                mergeListings(db, sourceId, targetId);
                canonicalId = targetId;
            }
            ContentValues v = new ContentValues();
            put(v, "vinted_item_id", emptyToNull(r.itemId));
            put(v, "vinted_url", emptyToNull(r.url));
            if(isUsableResolvedTitle(r.matchedTitle))v.put("vinted_title",safe(r.matchedTitle.trim()));
            put(v, "image_url", emptyToNull(r.imageUrl));
            put(v, "seller_id", emptyToNull(r.sellerId));
            put(v, "seller_name", emptyToNull(r.sellerName));
            put(v, "listing_photos_csv", emptyToNull(r.photosCsv));
            put(v, "published_label", emptyToNull(r.publishedLabel));
            // Reuse rich text already returned by the exact public item page. This does not add a
            // Vinted request; it prevents description evidence from being discarded after parsing.
            if(!TextUtils.isEmpty(r.detailsText))v.put("observed_text",safe(r.detailsText.trim()));
            v.put("enriched_at", System.currentTimeMillis());
            v.put("last_error", "");
            v.put("manual_review_required",0);v.putNull("manual_review_reason");
            if (r.sold) v.put("lifecycle", "SOLD");
            db.update("market_listings", v, "id=?", new String[]{String.valueOf(canonicalId)});
            ContentValues state = new ContentValues(); state.put("enrichment_state", r.needsDeepMetadata ? "CORE_COMPLETE" : COMPLETE); state.put("last_error", "");
            db.update("market_listings", state, "id=?", new String[]{String.valueOf(canonicalId)});
            db.setTransactionSuccessful();
            Log.i(TAG, "job=" + job.id + " op=vinted-resolve state="+(r.needsDeepMetadata?"CORE_COMPLETE":"COMPLETE")+" listing=" + canonicalId + " item=" + safe(r.itemId));
            return canonicalId;
        } finally { db.endTransaction(); }
    }

    /** Schedules optional public item metadata after the core Vinted identity is already usable. */
    public void enqueueDeepMetadata(long listingId) {
        if(listingId<=0)return;SQLiteDatabase db=helper.getWritableDatabase();long now=System.currentTimeMillis();
        db.beginTransaction();try{
            String type=listingVintedJobType(db,listingId);
            if(JOB_VINTED_DEEP.equals(type)){
                ContentValues st=new ContentValues();st.put("enrichment_state","CORE_COMPLETE");st.put("last_error","");db.update("market_listings",st,"id=?",new String[]{String.valueOf(listingId)});
                enqueueListingJob(db,listingId,JOB_VINTED_DEEP,now,30,"DEEP_METADATA");
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        QueueKeepAliveService.ensureRunning(context);QueueWorkScheduler.schedule(context);notifyQueueChanged();
    }

    /** Exact fallback after the user has opened a known Vinted item from Ludo. Accessibility normally
     * reconciles the item page immediately; if Vinted hides the sold/unavailable label from the
     * accessibility tree, this rechecks the same exact URL through the existing paced public lane. */
    public void enqueueOpenedListingVerification(long listingId){
        if(listingId<=0)return;SQLiteDatabase db=helper.getWritableDatabase();long now=System.currentTimeMillis();boolean queued=false;
        db.beginTransaction();try{
            try(Cursor c=db.rawQuery("SELECT lifecycle,vinted_url FROM market_listings WHERE id=?",new String[]{String.valueOf(listingId)})){
                if(c.moveToFirst()&&"ACTIVE".equals(c.getString(0))&&!TextUtils.isEmpty(c.getString(1))){
                    enqueueListingJob(db,listingId,JOB_VINTED_DEEP,now,245,OPENED_VERIFY_SOURCE);queued=true;
                }
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        if(queued){setDiagnosticState("opened_vinted_verify",1,"state=QUEUED;listing="+listingId);QueueKeepAliveService.ensureRunning(context);QueueWorkScheduler.schedule(context);notifyQueueChanged();}
    }

    public void markSold(long listingId) {
        if(listingId<=0)return;SQLiteDatabase db=helper.getWritableDatabase();long now=System.currentTimeMillis();
        db.beginTransaction();try{
            ContentValues v=new ContentValues();v.put("lifecycle","SOLD");v.put("enrichment_state","SOLD");v.put("last_seen",now);v.put("last_error","Articolo venduto su Vinted");v.put("manual_review_required",0);v.putNull("manual_review_reason");
            db.update("market_listings",v,"id=?",new String[]{String.valueOf(listingId)});
            // A sold listing must leave every active lane immediately; otherwise the queue can keep
            // spending paced Vinted slots on a card that can no longer return to the catalog.
            ContentValues j=new ContentValues();j.put("state",COMPLETE);j.put("next_attempt_at",0);j.put("updated_at",now);j.put("progress",100);j.put("processing_started_at",0);j.put("last_error","sold: listing retired");
            db.update("processing_jobs",j,"listing_id=? AND state IN (?,?,?,?)",new String[]{String.valueOf(listingId),PENDING,PROCESSING,FAILED_RETRYABLE,FAILED_PERMANENT});
            db.delete("queue_controls","name=?",new String[]{"vinted_candidates:"+listingId});
            db.delete("queue_controls","name=? AND value=?",new String[]{MANUAL_VINTED_RECOVERY,String.valueOf(listingId)});
            db.delete("queue_controls","name=? AND value=?",new String[]{OPENED_VINTED_TARGET,String.valueOf(listingId)});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        helper.invalidateActiveObservationSessionCache();notifyQueueChanged();
    }

    public void markUnavailable(long listingId,String reason){
        if(listingId<=0)return;ContentValues v=new ContentValues();v.put("lifecycle","REMOVED");v.put("last_seen",System.currentTimeMillis());v.put("last_error",safe(reason));
        helper.getWritableDatabase().update("market_listings",v,"id=?",new String[]{String.valueOf(listingId)});
        notifyQueueChanged();
    }

    private void restoreCategoryConfirmedListings(SQLiteDatabase db,long gameId,Double rating,String bggId){
        List<Long> ids=new ArrayList<>();List<String> signatures=new ArrayList<>();List<String> urls=new ArrayList<>();List<String> itemIds=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT id,COALESCE(NULLIF(legacy_signature,''),temp_fingerprint),COALESCE(vinted_url,''),COALESCE(vinted_item_id,''),COALESCE(category_normalized,'') FROM market_listings WHERE game_id=? AND lifecycle='ACTIVE'",new String[]{String.valueOf(gameId)})){
            while(c.moveToNext())if(ListingClassifier.isExplicitBoardGameCategory(c.getString(4))){ids.add(c.getLong(0));signatures.add(c.getString(1));urls.add(c.getString(2));itemIds.add(c.getString(3));}
        }
        for(int i=0;i<ids.size();i++){
            // The id list is built only from rows with this exact structured category evidence.
            boolean categoryConfirmed=true;
            boolean exactIdentity=!TextUtils.isEmpty(urls.get(i))&&!TextUtils.isEmpty(itemIds.get(i));
            String recoveredState=CategoryRecoveryPolicy.enrichmentState(categoryConfirmed,rating,exactIdentity);
            if(recoveredState==null)continue;
            ContentValues listing=new ContentValues();listing.put("match_state","MATCHED");listing.put("last_error","");
            listing.put("enrichment_state",recoveredState);
            if("DEFERRED_LINK".equals(recoveredState))listing.put("deferred_retry_at",0);
            db.update("market_listings",listing,"id=? AND lifecycle='ACTIVE'",new String[]{String.valueOf(ids.get(i))});
            if(CategoryRecoveryPolicy.mayClearTypeHold(categoryConfirmed,rating)){
                ContentValues deal=new ContentValues();deal.put("verification_state","OK");deal.putNull("verification_reason");
                db.update("deals",deal,"signature=? AND bgg_id=? AND lifecycle='ACTIVE' AND verification_state='TYPE_UNVERIFIED'",new String[]{signatures.get(i),bggId});
            }
        }
    }

    public void applyBggMetadata(BggMetadata m) {
        DbContentionTrace.Scope trace=DbContentionTrace.start("MarketStore.applyBggMetadata");
        try{
        if (m == null || TextUtils.isEmpty(m.bggId)) return;
        trace.phase("OPEN_DATABASE");SQLiteDatabase db = helper.getWritableDatabase();trace.phase("PRE_TRANSACTION");
        trace.phase("ACQUIRE_WRITER");db.beginTransaction();trace.phase("TRANSACTION");
        try {
            Long id = scalarLong(db, "SELECT id FROM games WHERE bgg_id=?", new String[]{m.bggId});
            if (id == null) { db.setTransactionSuccessful(); return; }
            String listingTypeRaw=scalarString(db,"SELECT listing_type FROM deals WHERE bgg_id=? AND lifecycle='ACTIVE' ORDER BY CASE listing_type WHEN 'BASE_GAME' THEN 0 WHEN 'EXPANSION' THEN 1 ELSE 2 END,last_seen DESC LIMIT 1",new String[]{m.bggId});
            if(TextUtils.isEmpty(listingTypeRaw))listingTypeRaw=scalarString(db,BGG_PRODUCT_TYPE_EVIDENCE_SQL,new String[]{String.valueOf(id)});
            ListingClassifier.Type listingType=ListingClassifier.Type.UNCERTAIN;
            try{ if(!TextUtils.isEmpty(listingTypeRaw))listingType=ListingClassifier.Type.valueOf(listingTypeRaw); }catch(Throwable ignored){}
            BggProductCompatibility.Verdict typeVerdict=BggProductCompatibility.validate(listingType.name(),m.itemType);
            if(typeVerdict!=BggProductCompatibility.Verdict.COMPATIBLE){
                String state=typeVerdict==BggProductCompatibility.Verdict.INCOMPATIBLE?"TYPE_MISMATCH":"TYPE_UNVERIFIED";
                String reason=typeVerdict==BggProductCompatibility.Verdict.INCOMPATIBLE
                        ?"Tipo BGG incompatibile: annuncio "+listingType+" / BGG "+safe(m.itemType)
                        :"Tipo prodotto non verificabile: annuncio "+listingType+" / BGG "+safe(m.itemType);
                ContentValues hidden=new ContentValues();hidden.put("database_visible",0);hidden.put("filter_reason",reason);hidden.put("match_state",state);
                db.update("games",hidden,"id=?",new String[]{String.valueOf(id)});
                ContentValues filtered=new ContentValues();filtered.put("enrichment_state",state);filtered.put("match_state",state);filtered.put("last_error",reason);
                if(typeVerdict==BggProductCompatibility.Verdict.INCOMPATIBLE)filtered.put("lifecycle","AUTO_FILTERED");
                db.update("market_listings",filtered,"game_id=? AND lifecycle='ACTIVE'",new String[]{String.valueOf(id)});
                ContentValues legacy=new ContentValues();legacy.put("verification_state",state);legacy.put("verification_reason",reason);
                db.update("deals",legacy,"bgg_id=? AND lifecycle='ACTIVE'",new String[]{m.bggId});
                ContentValues done=new ContentValues();done.put("state",COMPLETE);done.put("updated_at",System.currentTimeMillis());done.put("next_attempt_at",0);done.put("last_error",reason);done.put("progress",100);
                db.update("processing_jobs",done,"game_id=? AND job_type=?",new String[]{String.valueOf(id),JOB_BGG});
                setDiagnosticState("bgg_product_type",1,"state="+state+";game="+id+";listingType="+listingType+";bggType="+safe(m.itemType));
                db.setTransactionSuccessful();return;
            }
            ContentValues v = new ContentValues();
            if (!TextUtils.isEmpty(m.name)) { v.put("canonical_name", m.name); v.put("normalized_name", normalize(m.name)); }
            put(v, "original_name", emptyToNull(m.originalName)); put(v, "alternate_names", emptyToNull(m.alternateNames));
            put(v, "year", m.year); put(v, "description", emptyToNull(m.description)); put(v, "thumbnail_url", emptyToNull(m.thumbnailUrl)); put(v, "image_url", emptyToNull(m.imageUrl));
            put(v, "min_players", m.minPlayers); put(v, "max_players", m.maxPlayers); put(v, "playtime", m.playtime); put(v, "min_age", m.minAge); put(v, "weight", m.weight);
            put(v, "rating", m.rating); put(v, "voters", m.voters); put(v, "bgg_rank", m.rank);
            put(v, "categories", emptyToNull(m.categories)); put(v, "mechanics", emptyToNull(m.mechanics)); put(v, "designers", emptyToNull(m.designers)); put(v, "artists", emptyToNull(m.artists)); put(v, "publishers", emptyToNull(m.publishers)); put(v, "families", emptyToNull(m.families)); put(v, "expansions", emptyToNull(m.expansions)); put(v, "base_games", emptyToNull(m.baseGames));
            v.put("bgg_url", "https://boardgamegeek.com/boardgame/" + m.bggId); v.put("metadata_updated_at", System.currentTimeMillis()); v.put("match_state", "MATCHED");
            // Product quality gate: raw observations remain durable, but the scouting database
            // excludes known sub-6 games and BGG Children's Game entries before Vinted linking/review.
            boolean childrenGame=DealPolicy.childrenCategory(m.categories);
            boolean ratingRejected=m.rating!=null&&m.rating<DealPolicy.MIN_BGG_RATING;
            boolean qualityRejected=childrenGame||ratingRejected;
            if(childrenGame){
                v.put("database_visible",0);v.put("filter_reason","BGG_CHILDRENS_GAME");
            }else if(m.rating!=null){
                boolean eligible=m.rating>=DealPolicy.MIN_BGG_RATING;
                v.put("database_visible",eligible?1:0);
                if(eligible)v.putNull("filter_reason");else v.put("filter_reason","BGG_RATING_BELOW_6");
            }
            db.update("games", v, "id=?", new String[]{String.valueOf(id)});
            if(qualityRejected){
                String qualityReason=childrenGame?"Escluso: categoria BGG Children's Game":"Escluso: voto BGG sotto 6";
                ContentValues hiddenListing=new ContentValues();hiddenListing.put("lifecycle","AUTO_FILTERED");hiddenListing.put("enrichment_state","AUTO_FILTERED");hiddenListing.put("last_error",qualityReason);hiddenListing.put("manual_review_required",0);hiddenListing.putNull("manual_review_reason");
                db.update("market_listings",hiddenListing,"game_id=? AND lifecycle='ACTIVE'",new String[]{String.valueOf(id)});
                ContentValues hiddenDeal=new ContentValues();hiddenDeal.put("lifecycle","REMOVED");hiddenDeal.put("verification_state",childrenGame?"BGG_CHILDRENS_GAME":"BGG_RATING_BELOW_6");hiddenDeal.put("verification_reason",qualityReason);
                db.update("deals",hiddenDeal,"bgg_id=? AND lifecycle='ACTIVE'",new String[]{m.bggId});
            }
            restoreCategoryConfirmedListings(db,id,m.rating,m.bggId);
            if(typeVerdict==BggProductCompatibility.Verdict.COMPATIBLE && listingType==ListingClassifier.Type.EXPANSION){
                ContentValues verified=new ContentValues();verified.put("verification_state","OK");verified.putNull("verification_reason");
                db.update("deals",verified,"bgg_id=? AND lifecycle='ACTIVE' AND verification_state='EXPANSION_CHECK'",new String[]{m.bggId});
            }
            setDiagnosticState("bgg_product_type",1,"state="+typeVerdict+";game="+id+";listingType="+listingType+";bggType="+safe(m.itemType));
            if(m.marketUsedCount!=null&&m.marketUsedCount>0&&m.marketUsedMedianCents!=null&&m.marketUsedMedianCents>0)saveBggMarketStats(db,m.bggId,m.marketUsedMedianCents,m.marketUsedMinCents,m.marketUsedCount,System.currentTimeMillis());
            if (qualityRejected) {
                String skipReason=childrenGame?"skipped: BGG Children's Game":"skipped: BGG rating below 6";
                ContentValues skipped = new ContentValues(); skipped.put("state", COMPLETE); skipped.put("updated_at", System.currentTimeMillis()); skipped.put("next_attempt_at", 0); skipped.put("last_error", skipReason); skipped.put("progress",100);skipped.put("processing_started_at",0);
                db.update("processing_jobs", skipped, "state IN (?,?,?) AND (game_id=? OR listing_id IN (SELECT id FROM market_listings WHERE game_id=?))", new String[]{PENDING, FAILED_RETRYABLE, PROCESSING, String.valueOf(id), String.valueOf(id)});
                Log.i(TAG, "game=" + id + " bgg=" + m.bggId + " database=HIDDEN reason="+(childrenGame?"children":"rating<6")+" rating=" + m.rating);
            }
            if(!qualityRejected&&m.rating!=null&&m.rating>=DealPolicy.MIN_BGG_RATING){
                // Once BGG opens the quality gate, every valid listing may eventually receive its
                // Vinted identity. Only hot rows enter the fast lane; the rest stay DEFERRED_LINK.
                ContentValues deferred=new ContentValues();deferred.put("enrichment_state","DEFERRED_LINK");deferred.put("deferred_retry_at",0);
                db.update("market_listings",deferred,"game_id=? AND lifecycle='ACTIVE' AND (vinted_url IS NULL OR vinted_url='') AND enrichment_state IN ('LOCAL_ONLY','PENDING_ANALYSIS') AND NOT EXISTS (SELECT 1 FROM deals d WHERE d.signature=COALESCE(NULLIF(market_listings.legacy_signature,''),market_listings.temp_fingerprint) AND (d.tier='filtered' OR d.verification_state='PRICE_FILTERED'))",new String[]{String.valueOf(id)});
                try(Cursor hot=db.rawQuery("SELECT l.id FROM market_listings l JOIN deals d ON d.signature=l.legacy_signature WHERE l.game_id=? AND l.lifecycle='ACTIVE' AND (l.vinted_url IS NULL OR l.vinted_url='') AND d.lifecycle='ACTIVE' AND d.tier='hot'",new String[]{String.valueOf(id)})){
                    while(hot.moveToNext()){long listingId=hot.getLong(0);ContentValues st=new ContentValues();st.put("enrichment_state","PENDING_ENRICHMENT");db.update("market_listings",st,"id=?",new String[]{String.valueOf(listingId)});enqueueListingJob(db,listingId,JOB_VINTED,System.currentTimeMillis(),320,"LIVE_DEAL");}
                }
            }
            if (!TextUtils.isEmpty(m.name)) addAlias(db, id, m.name, "BGG_PRIMARY");
            if (!TextUtils.isEmpty(m.originalName)) addAlias(db, id, m.originalName, "BGG_ORIGINAL");
            if (!TextUtils.isEmpty(m.alternateNames)) for (String a : m.alternateNames.split("\\s*\\|\\s*")) addAlias(db, id, a, "BGG_ALTERNATE");
            ContentValues done = new ContentValues(); done.put("state", COMPLETE); done.put("updated_at", System.currentTimeMillis()); done.put("next_attempt_at", 0); done.put("last_error", ""); done.put("progress",100);
            db.update("processing_jobs", done, "job_key=?", new String[]{"bgg:" + id});
            try(Cursor ready=db.rawQuery("SELECT id FROM market_listings WHERE game_id=? AND lifecycle='ACTIVE' AND vinted_item_id IS NOT NULL AND vinted_item_id<>'' AND vinted_url IS NOT NULL AND vinted_url<>''",new String[]{String.valueOf(id)})){
                while(ready.moveToNext()){trace.phase("HELPER_CALL");try{helper.materializeCanonicalDeal(ready.getLong(0));}finally{trace.phase("TRANSACTION");}}
            }
            db.setTransactionSuccessful();
        } finally { trace.phase("COMMIT");try{db.endTransaction();}finally{trace.phase("POST_TRANSACTION");} }
    
        }catch(RuntimeException|Error failure){trace.failed(failure);throw failure;}finally{trace.close();}
    }

    /** Counts real BGG categories attached to visible, matched games for the Discover category rail. */
    public Map<String,Integer> popularCategories(int limit){
        Map<String,Integer> counts=new HashMap<>();
        String sql="SELECT categories FROM games WHERE database_visible=1 AND bgg_id IS NOT NULL AND bgg_id<>'' AND match_state='MATCHED' AND rating>=? AND categories IS NOT NULL AND categories<>''";
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{String.valueOf(DealPolicy.MIN_BGG_RATING)})){
            while(c.moveToNext()){
                String raw=c.getString(0);
                if(TextUtils.isEmpty(raw))continue;
                Set<String> seen=new LinkedHashSet<>();
                for(String value:raw.split("\\s*·\\s*")){
                    String label=value.trim();
                    if(!label.isEmpty())seen.add(label);
                }
                for(String label:seen)counts.put(label,counts.getOrDefault(label,0)+1);
            }
        }
        List<Map.Entry<String,Integer>> entries=new ArrayList<>(counts.entrySet());
        Collections.sort(entries,(a,b)->{
            int count=Integer.compare(b.getValue(),a.getValue());
            return count!=0?count:a.getKey().compareToIgnoreCase(b.getKey());
        });
        int size=limit<=0?entries.size():Math.min(limit,entries.size());
        Map<String,Integer> result=new LinkedHashMap<>();
        for(int i=0;i<size;i++)result.put(entries.get(i).getKey(),entries.get(i).getValue());
        return result;
    }

    public List<GameRecord> searchGames(String rawQuery, int limit) { return searchGames(rawQuery,limit,true,"all"); }

    public List<GameRecord> searchGames(String rawQuery,int limit,boolean includeUnverified,String filter){
        String q=normalize(rawQuery);String prefix=q.isEmpty()?"%":q+"%",contains=q.isEmpty()?"%":"%"+q+"%";
        StringBuilder where=new StringBuilder("g.database_visible=1");
        if(!includeUnverified)where.append(" AND g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND g.match_state='MATCHED'");
        if("active".equals(filter))where.append(" AND EXISTS(SELECT 1 FROM market_listings lx WHERE lx.game_id=g.id AND lx.lifecycle='ACTIVE')");
        else if("rating7".equals(filter))where.append(" AND g.rating>=7.0");
        else if("rating8".equals(filter))where.append(" AND g.rating>=8.0");
        else if("cheap".equals(filter))where.append(" AND EXISTS(SELECT 1 FROM market_listings lx WHERE lx.game_id=g.id AND lx.lifecycle='ACTIVE' AND lx.current_price_cents<=2000)");
        String sql="SELECT g.id,g.bgg_id,g.provisional_key,g.canonical_name,g.original_name,g.alternate_names,g.year,g.description,g.thumbnail_url,g.image_url,g.min_players,g.max_players,g.playtime,g.min_age,g.weight,g.rating,g.voters,g.bgg_rank,g.categories,g.mechanics,g.designers,g.artists,g.publishers,g.families,g.expansions,g.base_games,g.bgg_url,g.match_state,g.match_confidence,g.first_seen,g.last_seen,g.metadata_updated_at,"+
                "(SELECT COUNT(*) FROM market_listings l WHERE l.game_id=g.id),"+
                "(SELECT COUNT(*) FROM market_listings l WHERE l.game_id=g.id AND l.lifecycle='ACTIVE'),"+
                "(SELECT MIN(l.current_price_cents) FROM market_listings l WHERE l.game_id=g.id AND l.lifecycle='ACTIVE'),"+
                "(SELECT MIN(p.price_cents) FROM price_observations p JOIN market_listings l ON l.id=p.listing_id WHERE l.game_id=g.id),"+
                "(SELECT MAX(p.price_cents) FROM price_observations p JOIN market_listings l ON l.id=p.listing_id WHERE l.game_id=g.id),"+
                "(SELECT AVG(p.price_cents) FROM price_observations p JOIN market_listings l ON l.id=p.listing_id WHERE l.game_id=g.id),"+
                "(SELECT AVG(p.price_cents) FROM price_observations p JOIN market_listings l ON l.id=p.listing_id WHERE l.game_id=g.id AND p.observed_at>=?),"+
                "(SELECT COUNT(*) FROM price_observations p JOIN market_listings l ON l.id=p.listing_id WHERE l.game_id=g.id) "+
                "FROM games g WHERE "+where+" AND (g.normalized_name LIKE ? OR g.normalized_name LIKE ? OR EXISTS(SELECT 1 FROM game_aliases a WHERE a.game_id=g.id AND a.normalized_alias LIKE ?)) "+
                "ORDER BY g.normalized_name COLLATE NOCASE ASC,g.canonical_name COLLATE NOCASE ASC LIMIT ?";
        long recent=System.currentTimeMillis()-30L*24*60*60_000L;List<GameRecord> out=new ArrayList<>();
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{String.valueOf(recent),prefix,contains,contains,String.valueOf(Math.max(1,limit))})){while(c.moveToNext())out.add(readGameWithSummary(c));}
        return out;
    }

    public int countVisibleGames(String rawQuery){return countVisibleGames(rawQuery,true,"all");}
    public int countVisibleGames(String rawQuery,boolean includeUnverified,String filter){
        String q=normalize(rawQuery);String prefix=q.isEmpty()?"%":q+"%",contains=q.isEmpty()?"%":"%"+q+"%";StringBuilder where=new StringBuilder("g.database_visible=1");
        if(!includeUnverified)where.append(" AND g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND g.match_state='MATCHED'");
        if("active".equals(filter))where.append(" AND EXISTS(SELECT 1 FROM market_listings lx WHERE lx.game_id=g.id AND lx.lifecycle='ACTIVE')");
        else if("rating7".equals(filter))where.append(" AND g.rating>=7.0");
        else if("rating8".equals(filter))where.append(" AND g.rating>=8.0");
        else if("cheap".equals(filter))where.append(" AND EXISTS(SELECT 1 FROM market_listings lx WHERE lx.game_id=g.id AND lx.lifecycle='ACTIVE' AND lx.current_price_cents<=2000)");
        String sql="SELECT COUNT(*) FROM games g WHERE "+where+" AND (g.normalized_name LIKE ? OR g.normalized_name LIKE ? OR EXISTS(SELECT 1 FROM game_aliases a WHERE a.game_id=g.id AND a.normalized_alias LIKE ?))";
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{prefix,contains,contains})){return c.moveToFirst()?c.getInt(0):0;}
    }
    public int countReviewGames(){try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM games WHERE database_visible=1 AND (bgg_id IS NULL OR bgg_id='' OR match_state<>'MATCHED')",null)){return c.moveToFirst()?c.getInt(0):0;}}
    public List<GameRecord> searchGamesAdvanced(String rawQuery,int limit,String scope,boolean activeOnly,Double minRating,Integer maxPrice,String sort){return searchGamesAdvanced(rawQuery,limit,scope,activeOnly,minRating,maxPrice,sort,-1);}
    public List<GameRecord> searchGamesAdvanced(String rawQuery,int limit,String scope,boolean activeOnly,Double minRating,Integer maxPrice,String sort,int category){
        String q=normalize(DiscoverCategories.searchText(rawQuery));String prefix=q.isEmpty()?"%":q+"%",contains=q.isEmpty()?"%":"%"+q+"%";StringBuilder where=new StringBuilder("g.database_visible=1");List<String> args=new ArrayList<>();where.append(DiscoverCategories.appendFilter(rawQuery,args));where.append(DiscoverCategories.appendFilter(category,args));
        if("review".equals(scope))where.append(" AND (g.bgg_id IS NULL OR g.bgg_id='' OR g.match_state<>'MATCHED')");else if(!"all".equals(scope))where.append(" AND g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND g.match_state='MATCHED'");
        if(activeOnly)where.append(" AND EXISTS(SELECT 1 FROM market_listings lx WHERE lx.game_id=g.id AND lx.lifecycle='ACTIVE')");
        if(minRating!=null){where.append(" AND g.rating>=?");args.add(String.valueOf(minRating));}
        if(maxPrice!=null){where.append(" AND EXISTS(SELECT 1 FROM market_listings lx WHERE lx.game_id=g.id AND lx.lifecycle='ACTIVE' AND lx.current_price_cents<=?)");args.add(String.valueOf(maxPrice));}
        String order;if("rating".equals(sort))order="CASE WHEN g.rating IS NULL THEN 1 ELSE 0 END,g.rating DESC,g.normalized_name COLLATE NOCASE ASC";else if("price".equals(sort))order="COALESCE((SELECT MIN(lx.current_price_cents) FROM market_listings lx WHERE lx.game_id=g.id AND lx.lifecycle='ACTIVE'),2147483647) ASC,g.normalized_name COLLATE NOCASE ASC";else if("recent".equals(sort))order="g.last_seen DESC,g.normalized_name COLLATE NOCASE ASC";else order="g.normalized_name COLLATE NOCASE ASC,g.canonical_name COLLATE NOCASE ASC";
        String sql="SELECT g.id,g.bgg_id,g.provisional_key,g.canonical_name,g.original_name,g.alternate_names,g.year,g.description,g.thumbnail_url,g.image_url,g.min_players,g.max_players,g.playtime,g.min_age,g.weight,g.rating,g.voters,g.bgg_rank,g.categories,g.mechanics,g.designers,g.artists,g.publishers,g.families,g.expansions,g.base_games,g.bgg_url,g.match_state,g.match_confidence,g.first_seen,g.last_seen,g.metadata_updated_at,"+
                "(SELECT COUNT(*) FROM market_listings l WHERE l.game_id=g.id),"+
                "(SELECT COUNT(*) FROM market_listings l WHERE l.game_id=g.id AND l.lifecycle='ACTIVE'),"+
                "(SELECT MIN(l.current_price_cents) FROM market_listings l WHERE l.game_id=g.id AND l.lifecycle='ACTIVE'),"+
                "(SELECT MIN(p.price_cents) FROM price_observations p JOIN market_listings l ON l.id=p.listing_id WHERE l.game_id=g.id),"+
                "(SELECT MAX(p.price_cents) FROM price_observations p JOIN market_listings l ON l.id=p.listing_id WHERE l.game_id=g.id),"+
                "(SELECT AVG(p.price_cents) FROM price_observations p JOIN market_listings l ON l.id=p.listing_id WHERE l.game_id=g.id),"+
                "(SELECT AVG(p.price_cents) FROM price_observations p JOIN market_listings l ON l.id=p.listing_id WHERE l.game_id=g.id AND p.observed_at>=?),"+
                "(SELECT COUNT(*) FROM price_observations p JOIN market_listings l ON l.id=p.listing_id WHERE l.game_id=g.id) "+
                "FROM games g WHERE "+where+" AND (g.normalized_name LIKE ? OR g.normalized_name LIKE ? OR EXISTS(SELECT 1 FROM game_aliases a WHERE a.game_id=g.id AND a.normalized_alias LIKE ?) OR LOWER(COALESCE(g.categories,'')) LIKE ? OR LOWER(COALESCE(g.mechanics,'')) LIKE ? OR LOWER(COALESCE(g.designers,'')) LIKE ? OR LOWER(COALESCE(g.publishers,'')) LIKE ? OR LOWER(COALESCE(g.families,'')) LIKE ?) ORDER BY "+order+" LIMIT ?";
        List<String> all=new ArrayList<>();all.add(String.valueOf(System.currentTimeMillis()-30L*24*60*60_000L));all.addAll(args);all.add(prefix);all.add(contains);all.add(contains);for(int i=0;i<5;i++)all.add(contains);all.add(String.valueOf(Math.max(1,limit)));List<GameRecord> out=new ArrayList<>();try(Cursor c=helper.getReadableDatabase().rawQuery(sql,all.toArray(new String[0]))){while(c.moveToNext())out.add(readGameWithSummary(c));}return out;
    }

    /** Read-only identity lookup for the already eligible Catalog population; no preview limit. */
    public Set<String> catalogGameMatches(String rawQuery,Set<String> eligibleBggIds){
        Set<String> out=new HashSet<>();if(eligibleBggIds==null||eligibleBggIds.isEmpty())return out;
        String q=normalize(DiscoverCategories.searchText(rawQuery)),contains="%"+q+"%";
        List<String> ids=new ArrayList<>(eligibleBggIds);
        // Keep bound parameters below SQLite's older 999 limit, even for a larger caller.
        for(int start=0;start<ids.size();start+=800){
            List<String> args=new ArrayList<>();StringBuilder where=new StringBuilder("g.database_visible=1 AND g.match_state='MATCHED'");where.append(DiscoverCategories.appendFilter(rawQuery,args));
            where.append(" AND g.bgg_id IN (");int end=Math.min(ids.size(),start+800);for(int i=start;i<end;i++){if(i>start)where.append(',');where.append('?');args.add(ids.get(i));}where.append(')');
            String sql="SELECT g.bgg_id FROM games g WHERE "+where+" AND (g.normalized_name LIKE ? OR EXISTS(SELECT 1 FROM game_aliases a WHERE a.game_id=g.id AND a.normalized_alias LIKE ?) OR LOWER(COALESCE(g.categories,'')) LIKE ? OR LOWER(COALESCE(g.mechanics,'')) LIKE ? OR LOWER(COALESCE(g.designers,'')) LIKE ? OR LOWER(COALESCE(g.publishers,'')) LIKE ? OR LOWER(COALESCE(g.families,'')) LIKE ?)";
            for(int i=0;i<7;i++)args.add(contains);
            try(Cursor c=helper.getReadableDatabase().rawQuery(sql,args.toArray(new String[0]))){while(c.moveToNext())out.add(c.getString(0));}
        }
        return out;
    }

    public int countVisibleGamesAdvanced(String rawQuery,String scope,boolean activeOnly,Double minRating,Integer maxPrice){return countVisibleGamesAdvanced(rawQuery,scope,activeOnly,minRating,maxPrice,-1);}
    public int countVisibleGamesAdvanced(String rawQuery,String scope,boolean activeOnly,Double minRating,Integer maxPrice,int category){
        String q=normalize(DiscoverCategories.searchText(rawQuery));String prefix=q.isEmpty()?"%":q+"%",contains=q.isEmpty()?"%":"%"+q+"%";StringBuilder where=new StringBuilder("g.database_visible=1");List<String> args=new ArrayList<>();where.append(DiscoverCategories.appendFilter(rawQuery,args));where.append(DiscoverCategories.appendFilter(category,args));
        if("review".equals(scope))where.append(" AND (g.bgg_id IS NULL OR g.bgg_id='' OR g.match_state<>'MATCHED')");else if(!"all".equals(scope))where.append(" AND g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND g.match_state='MATCHED'");
        if(activeOnly)where.append(" AND EXISTS(SELECT 1 FROM market_listings lx WHERE lx.game_id=g.id AND lx.lifecycle='ACTIVE')");
        if(minRating!=null){where.append(" AND g.rating>=?");args.add(String.valueOf(minRating));}
        if(maxPrice!=null){where.append(" AND EXISTS(SELECT 1 FROM market_listings lx WHERE lx.game_id=g.id AND lx.lifecycle='ACTIVE' AND lx.current_price_cents<=?)");args.add(String.valueOf(maxPrice));}
        String sql="SELECT COUNT(*) FROM games g WHERE "+where+" AND (g.normalized_name LIKE ? OR g.normalized_name LIKE ? OR EXISTS(SELECT 1 FROM game_aliases a WHERE a.game_id=g.id AND a.normalized_alias LIKE ?) OR LOWER(COALESCE(g.categories,'')) LIKE ? OR LOWER(COALESCE(g.mechanics,'')) LIKE ? OR LOWER(COALESCE(g.designers,'')) LIKE ? OR LOWER(COALESCE(g.publishers,'')) LIKE ? OR LOWER(COALESCE(g.families,'')) LIKE ?)";args.add(prefix);args.add(contains);args.add(contains);for(int i=0;i<5;i++)args.add(contains);try(Cursor c=helper.getReadableDatabase().rawQuery(sql,args.toArray(new String[0]))){return c.moveToFirst()?c.getInt(0):0;}
    }


    public GameRecord game(long gameId) {
        String sql = "SELECT id,bgg_id,provisional_key,canonical_name,original_name,alternate_names,year,description,thumbnail_url,image_url,min_players,max_players,playtime,min_age,weight,rating,voters,bgg_rank,categories,mechanics,designers,artists,publishers,families,expansions,base_games,bgg_url,match_state,match_confidence,first_seen,last_seen,metadata_updated_at FROM games WHERE id=?";
        try (Cursor c = helper.getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(gameId)})) {
            return c.moveToFirst() ? readGameBase(c) : null;
        }
    }

    public GameRecord gameForListing(MarketListingRecord listing) {
        return listing == null || listing.gameId == null ? null : game(listing.gameId);
    }

    public GameRecord gameStats(long gameId) {
        GameRecord g = game(gameId); if (g == null) return null;
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COUNT(*),SUM(CASE WHEN lifecycle='ACTIVE' THEN 1 ELSE 0 END),MIN(CASE WHEN lifecycle='ACTIVE' THEN current_price_cents END),MIN(first_seen),MAX(last_seen) FROM market_listings WHERE game_id=?", new String[]{String.valueOf(gameId)})) {
            if (c.moveToFirst()) { g.listingCount=c.getInt(0); g.activeListingCount=c.isNull(1)?0:c.getInt(1); g.currentMinPriceCents=c.isNull(2)?null:c.getInt(2); if(!c.isNull(3))g.firstSeen=c.getLong(3); if(!c.isNull(4))g.lastSeen=c.getLong(4); }
        }
        try (Cursor c = db.rawQuery("SELECT COUNT(*),MIN(p.price_cents),MAX(p.price_cents),AVG(p.price_cents),AVG(CASE WHEN p.observed_at>=? THEN p.price_cents END) FROM price_observations p JOIN market_listings l ON l.id=p.listing_id WHERE l.game_id=?", new String[]{String.valueOf(System.currentTimeMillis()-30L*24*60*60_000L),String.valueOf(gameId)})) {
            if(c.moveToFirst()){g.observationCount=c.getInt(0);g.historicalMinPriceCents=c.isNull(1)?null:c.getInt(1);g.historicalMaxPriceCents=c.isNull(2)?null:c.getInt(2);g.historicalAveragePriceCents=c.isNull(3)?null:c.getDouble(3);g.recentAveragePriceCents=c.isNull(4)?null:(int)Math.round(c.getDouble(4));}
        }
        g.historicalMedianPriceCents = quantile(gameId, 0.50);
        g.q1PriceCents = quantile(gameId, 0.25);
        g.q3PriceCents = quantile(gameId, 0.75);
        return g;
    }

    public GameRecord gameStatsByBggId(String bggId){if(TextUtils.isEmpty(bggId))return null;try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT id FROM games WHERE bgg_id=? LIMIT 1",new String[]{bggId})){return c.moveToFirst()?gameStats(c.getLong(0)):null;}}

    public List<PricePoint> priceHistory(long gameId,int limit){
        List<PricePoint> out=new ArrayList<>();String sql="SELECT (p.observed_at/86400000)*86400000 AS day,CAST(AVG(p.price_cents) AS INTEGER) FROM price_observations p JOIN market_listings l ON l.id=p.listing_id WHERE l.game_id=? GROUP BY day ORDER BY day DESC LIMIT ?";
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{String.valueOf(gameId),String.valueOf(Math.max(2,limit))})){while(c.moveToNext())out.add(new PricePoint(c.getLong(0),c.getInt(1)));}
        java.util.Collections.reverse(out);return out;
    }

    public List<MarketListingRecord> listingsForGame(long gameId, boolean activeOnly, int limit) {
        String where = activeOnly ? " AND lifecycle='ACTIVE'" : "";
        String sql = "SELECT id,game_id,temp_fingerprint,vinted_item_id,vinted_title,brand,item_condition,current_price_cents,protected_price_cents,favorites,seller_id,seller_name,vinted_url,image_url,listing_photos_csv,published_label,language_code,lifecycle,enrichment_state,match_state,match_confidence,first_seen,last_seen,enriched_at,last_error FROM market_listings WHERE game_id=?"+where+" ORDER BY "+(activeOnly?"current_price_cents ASC,last_seen DESC":"last_seen DESC")+" LIMIT ?";
        List<MarketListingRecord> out = new ArrayList<>();
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{String.valueOf(gameId),String.valueOf(Math.max(1,limit))})){while(c.moveToNext())out.add(readListing(c));}
        return out;
    }

    /** Empirical low-price percentile shrunk toward neutral (50) when history is sparse. */
    public int dealScore(long gameId, int priceCents) {
        String sql="SELECT COUNT(*),SUM(CASE WHEN p.price_cents>? THEN 1 ELSE 0 END),SUM(CASE WHEN p.price_cents=? THEN 1 ELSE 0 END) FROM price_observations p JOIN market_listings l ON l.id=p.listing_id WHERE l.game_id=?";
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{String.valueOf(priceCents),String.valueOf(priceCents),String.valueOf(gameId)})){
            if(!c.moveToFirst()||c.getInt(0)==0)return 50;double n=c.getInt(0),better=c.isNull(1)?0:c.getInt(1),equal=c.isNull(2)?0:c.getInt(2);double raw=100.0*(better+0.5*equal)/n;double confidence=n/(n+10.0);return (int)Math.round(Math.max(0,Math.min(100,50+(raw-50)*confidence)));
        }
    }

    /** Keeps the durable queue live by removing work that can no longer make progress.
     * This is intentionally idempotent and cheap enough to run at service startup/pulses. */
    public boolean hasActiveObservationRun(){return helper.activeObservationSession()!=null;}

    /** Historical audit rows created before 5.12.23 may still carry the old manual-review flag.
     * Clear only that known reason family; trust gating on deals remains MATCH_UNCERTAIN. */
    public int clearHistoricalManualReviewDebt(long now){
        final String marker="historical_review_nonblocking_v1";SQLiteDatabase db=helper.getWritableDatabase();
        try(Cursor x=db.rawQuery("SELECT 1 FROM queue_controls WHERE name=? LIMIT 1",new String[]{marker})){if(x.moveToFirst())return 0;}
        int changed;db.beginTransaction();try{
            ContentValues l=new ContentValues();l.put("manual_review_required",0);l.putNull("manual_review_reason");
            changed=db.update("market_listings",l,"COALESCE(manual_review_required,0)=1 AND manual_review_reason LIKE 'Rivalidazione BGG storica:%'",null);
            ContentValues q=new ContentValues();q.put("name",marker);q.put("value",changed);q.put("updated_at",now);q.put("text_value","build=historical-review-nonblocking-v1;cleared="+changed);db.insertWithOnConflict("queue_controls",null,q,SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        if(changed>0)setDiagnosticState("historical_review_cutover",changed,"build=historical-review-nonblocking-v1;cleared="+changed);
        return changed;
    }

    /** No ordinary background Vinted job should remain materialised when there is no scroll owning
     * it. Return those rows to the deferred/local pool; LIVE/HUNT/MANUAL and historical work are
     * intentionally excluded. */
    public int parkIdleOrdinaryVintedJobs(long now){
        if(helper.activeObservationSession()!=null)return 0;SQLiteDatabase db=helper.getWritableDatabase();int changed=0;
        String automatic="job_type IN (?,?) AND state IN (?,?) AND COALESCE(source,'AUTO') NOT IN ('HUNT_PRIORITY','MANUAL_PRIORITY','LIVE_DEAL','SELLER_BACKFILL',?)";
        db.beginTransaction();try{
            ContentValues done=new ContentValues();done.put("state",COMPLETE);done.put("next_attempt_at",0);done.put("updated_at",now);done.put("progress",100);done.put("processing_started_at",0);done.put("last_error","parked: nessuno scroll attivo");
            changed=db.update("processing_jobs",done,automatic,new String[]{JOB_VINTED,JOB_VINTED_DEEP,PENDING,FAILED_RETRYABLE,HISTORICAL_SOURCE});
            if(changed>0){
                ContentValues deferred=new ContentValues();deferred.put("enrichment_state","DEFERRED_LINK");deferred.put("deferred_retry_at",now+24L*60*60_000L);deferred.put("last_error","In attesa di un nuovo scroll");
                db.update("market_listings",deferred,"lifecycle='ACTIVE' AND (vinted_url IS NULL OR vinted_url='') AND id IN (SELECT listing_id FROM processing_jobs WHERE last_error='parked: nessuno scroll attivo')",null);
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        if(changed>0)setDiagnosticState("vinted_idle_parking",changed,"build=vinted-idle-parking-v1;parked="+changed);
        return changed;
    }

    /** Network-cost guard, not the general pricing model. We skip ordinary Vinted identity work
     * only when the seller ask is so far above an existing used-market reference that edition/noise
     * is unlikely to change the decision. Manual and Hunt intent always override this optimization. */
    public int filterClearlyOverpricedAutomaticListings(long now){
        SQLiteDatabase db=helper.getWritableDatabase();List<Long> ids=new ArrayList<>();List<String> sigs=new ArrayList<>();
        String sql="SELECT l.id,d.signature FROM market_listings l JOIN deals d ON d.signature=COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) "+
                "JOIN games g ON g.id=l.game_id WHERE l.lifecycle='ACTIVE' AND (l.vinted_url IS NULL OR l.vinted_url='') "+
                "AND g.database_visible=1 AND g.match_state='MATCHED' AND g.rating>=? AND d.lifecycle='ACTIVE' "+
                "AND d.benchmark_cents IS NOT NULL AND d.benchmark_cents>0 AND d.item_price_cents>=d.benchmark_cents*2.0 "+
                "AND d.item_price_cents-d.benchmark_cents>=2500 AND NOT EXISTS(SELECT 1 FROM processing_jobs p WHERE p.listing_id=l.id AND p.job_type=? AND p.state=?) "+
                "AND NOT EXISTS(SELECT 1 FROM processing_jobs p WHERE p.listing_id=l.id AND p.state IN (?,?,?) AND p.source IN ('HUNT_PRIORITY','MANUAL_PRIORITY'))";
        try(Cursor cur=db.rawQuery(sql,new String[]{String.valueOf(DealPolicy.MIN_BGG_RATING),JOB_VINTED,PROCESSING,PENDING,PROCESSING,FAILED_RETRYABLE})){while(cur.moveToNext()){ids.add(cur.getLong(0));sigs.add(cur.getString(1));}}
        if(ids.isEmpty())return 0;int changed=0;db.beginTransaction();try{
            for(int i=0;i<ids.size();i++){long id=ids.get(i);String sig=sigs.get(i);
                ContentValues l=new ContentValues();l.put("lifecycle","AUTO_FILTERED");l.put("enrichment_state","AUTO_FILTERED");l.put("last_error","Prezzo chiaramente sopra il riferimento usato; verifica Vinted evitata");changed+=db.update("market_listings",l,"id=?",new String[]{String.valueOf(id)});
                ContentValues d=new ContentValues();d.put("lifecycle","REMOVED");d.put("verification_state","PRICE_FILTERED");d.put("verification_reason","Prezzo almeno 2× e 25 € sopra il riferimento usato; verifica Vinted non necessaria");db.update("deals",d,"signature=?",new String[]{sig});
                ContentValues j=new ContentValues();j.put("state",COMPLETE);j.put("progress",100);j.put("next_attempt_at",0);j.put("updated_at",now);j.put("processing_started_at",0);j.put("last_error","skipped: clearly overpriced before Vinted lookup");
                db.update("processing_jobs",j,"listing_id=? AND job_type=? AND state IN (?,?) AND COALESCE(source,'AUTO') NOT IN ('HUNT_PRIORITY','MANUAL_PRIORITY')",new String[]{String.valueOf(id),JOB_VINTED,PENDING,FAILED_RETRYABLE});
            }db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        if(changed>0){setDiagnosticState("early_price_filter",changed,"build=early-price-gate-v1;filtered="+changed+";rule=ask>=2x_reference_and_25eur;manualHuntExempt=true");helper.invalidateActiveObservationSessionCache();notifyQueueChanged();}
        return changed;
    }

    /**
     * One-time global repair for installs that already contained catalog rows before the rating gate
     * became authoritative on every write path. This is value-based only: titles/names never take
     * part in the decision. Raw observations and listing history are preserved.
     */
    public int enforceGlobalCatalogRatingGate(long now){
        SQLiteDatabase db=helper.getWritableDatabase();
        try(Cursor done=db.rawQuery("SELECT 1 FROM queue_controls WHERE name=? LIMIT 1",new String[]{CATALOG_RATING_SWEEP_V51234})){
            if(done.moveToFirst())return 0;
        }
        int gamesHidden=0,gamesRestored=0,dealsSynced=0,jobsClosed=0;
        db.beginTransaction();try{
            ContentValues hide=new ContentValues();hide.put("database_visible",0);hide.put("filter_reason","BGG_RATING_BELOW_6");
            gamesHidden=db.update("games",hide,"rating IS NOT NULL AND rating<? AND database_visible<>0",new String[]{String.valueOf(DealPolicy.MIN_BGG_RATING)});

            ContentValues restore=new ContentValues();restore.put("database_visible",1);restore.putNull("filter_reason");
            gamesRestored=db.update("games",restore,"rating IS NOT NULL AND rating>=? AND database_visible=0 AND filter_reason='BGG_RATING_BELOW_6'",new String[]{String.valueOf(DealPolicy.MIN_BGG_RATING)});

            // Canonical BGG is the source of truth. Old DealRecord mirrors can otherwise display a
            // stale 5.x/6.x value even though the canonical game has already been refreshed.
            db.execSQL("UPDATE deals SET rating=(SELECT g.rating FROM games g WHERE g.bgg_id=deals.bgg_id),"+
                    "voters=(SELECT g.voters FROM games g WHERE g.bgg_id=deals.bgg_id),"+
                    "bgg_rank=(SELECT g.bgg_rank FROM games g WHERE g.bgg_id=deals.bgg_id) "+
                    "WHERE bgg_id IS NOT NULL AND bgg_id<>'' AND EXISTS(SELECT 1 FROM games g WHERE g.bgg_id=deals.bgg_id AND g.rating IS NOT NULL) "+
                    "AND (rating IS NULL OR ABS(rating-(SELECT g.rating FROM games g WHERE g.bgg_id=deals.bgg_id))>0.0001 "+
                    "OR COALESCE(voters,-1)<>COALESCE((SELECT g.voters FROM games g WHERE g.bgg_id=deals.bgg_id),-1) "+
                    "OR COALESCE(bgg_rank,-1)<>COALESCE((SELECT g.bgg_rank FROM games g WHERE g.bgg_id=deals.bgg_id),-1))");
            try(Cursor ch=db.rawQuery("SELECT changes()",null)){if(ch.moveToFirst())dealsSynced=ch.getInt(0);}

            ContentValues jobDone=new ContentValues();jobDone.put("state",COMPLETE);jobDone.put("progress",100);jobDone.put("next_attempt_at",0);jobDone.put("updated_at",now);jobDone.put("processing_started_at",0);jobDone.put("last_error","skipped: BGG rating below 6");
            jobsClosed=db.update("processing_jobs",jobDone,
                    "state IN (?,?,?) AND (game_id IN (SELECT id FROM games WHERE rating IS NOT NULL AND rating<?) "+
                    "OR listing_id IN (SELECT l.id FROM market_listings l JOIN games g ON g.id=l.game_id WHERE g.rating IS NOT NULL AND g.rating<?))",
                    new String[]{PENDING,PROCESSING,FAILED_RETRYABLE,String.valueOf(DealPolicy.MIN_BGG_RATING),String.valueOf(DealPolicy.MIN_BGG_RATING)});

            ContentValues marker=new ContentValues();marker.put("name",CATALOG_RATING_SWEEP_V51234);marker.put("value",gamesHidden);marker.put("updated_at",now);
            marker.put("text_value","build=catalog-rating-truth-v1;hidden="+gamesHidden+";restored="+gamesRestored+";dealsSynced="+dealsSynced+";jobsClosed="+jobsClosed+";threshold="+DealPolicy.MIN_BGG_RATING);
            db.insertWithOnConflict("queue_controls",null,marker,SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        int changed=gamesHidden+gamesRestored+dealsSynced+jobsClosed;
        setDiagnosticState("catalog_rating_sweep",gamesHidden,"build=catalog-rating-truth-v1;state=DONE;hidden="+gamesHidden+";restored="+gamesRestored+";dealsSynced="+dealsSynced+";jobsClosed="+jobsClosed+";threshold="+DealPolicy.MIN_BGG_RATING);
        if(changed>0){helper.invalidateActiveObservationSessionCache();notifyQueueChanged();}
        return changed;
    }

    public int reconcileQueue() {
        DbContentionTrace.Scope trace=DbContentionTrace.start("MarketStore.reconcileQueue");
        try{
        trace.phase("OPEN_DATABASE");SQLiteDatabase db=helper.getWritableDatabase();trace.phase("PRE_TRANSACTION");
        long now=System.currentTimeMillis();
        int changed=enforceGlobalCatalogRatingGate(now)+clearHistoricalManualReviewDebt(now)+reopenTechnicalBggReviewsForExactIndex(now)+yieldOverBudgetEngineRun(now)+observeEngineTiming(now)+parkIdleOrdinaryVintedJobs(now);
        trace.phase("ACQUIRE_WRITER");db.beginTransaction();trace.phase("TRANSACTION");
        try {
            ContentValues done=new ContentValues();done.put("state",COMPLETE);done.put("next_attempt_at",0);done.put("updated_at",now);
            done.put("last_error","not runnable anymore");
            changed+=db.update("processing_jobs",done,"job_type IN (?,?) AND state IN (?,?,?) AND listing_id IN (SELECT id FROM market_listings WHERE lifecycle<>'ACTIVE')",new String[]{JOB_VINTED,JOB_VINTED_DEEP,PENDING,FAILED_RETRYABLE,PROCESSING});
            changed+=db.update("processing_jobs",done,"job_type=? AND state IN (?,?,?) AND game_id IN (SELECT id FROM games WHERE database_visible=0)",new String[]{JOB_BGG,PENDING,FAILED_RETRYABLE,PROCESSING});
            // A BGG enrichment job without a BGG id can never be claimed. It belongs to the match
            // workflow, not the enrichment lane. Closing it here prevents a phantom "BGG active"
            // count while the lane correctly has zero runnable requests. If the game is matched later,
            // enqueueJob() revives the same durable key.
            ContentValues noBgg=new ContentValues();noBgg.put("state",COMPLETE);noBgg.put("next_attempt_at",0);noBgg.put("updated_at",now);noBgg.put("progress",100);noBgg.put("last_error","BGG match necessario; enrichment rinviato");noBgg.put("processing_started_at",0);
            changed+=db.update("processing_jobs",noBgg,"job_type=? AND state IN (?,?,?) AND (game_id IS NULL OR game_id NOT IN (SELECT id FROM games WHERE bgg_id IS NOT NULL AND bgg_id<>''))",new String[]{JOB_BGG,PENDING,FAILED_RETRYABLE,PROCESSING});

            // Repair cross-table state drift caused by older async analyses. Game identity state is
            // authoritative: review stays review, and automatic quarantine stays filtered.
            ContentValues reviewListing=new ContentValues();reviewListing.put("match_state","BGG_MATCH_REVIEW");
            changed+=db.update("market_listings",reviewListing,
                    "lifecycle='ACTIVE' AND match_state<>'BGG_MATCH_REVIEW' AND game_id IN (SELECT id FROM games WHERE match_state='BGG_MATCH_REVIEW' AND (bgg_id IS NULL OR bgg_id=''))",null);
            ContentValues quarantinedListing=new ContentValues();quarantinedListing.put("lifecycle","AUTO_FILTERED");quarantinedListing.put("enrichment_state","AUTO_FILTERED");quarantinedListing.put("match_state","AUTO_FILTERED_NON_GAME");
            changed+=db.update("market_listings",quarantinedListing,
                    "lifecycle='ACTIVE' AND game_id IN (SELECT id FROM games WHERE match_state='AUTO_QUARANTINED')",null);

            ContentValues archive=new ContentValues();archive.put("lifecycle","UNKNOWN");archive.put("enrichment_state","HISTORICAL_PARTIAL");
            changed+=db.update("market_listings",archive,
                    "lifecycle='ACTIVE' AND id IN (SELECT listing_id FROM processing_jobs WHERE source=? AND job_type=? AND listing_id IS NOT NULL) AND (vinted_item_id IS NULL OR vinted_item_id='') AND (vinted_url IS NULL OR vinted_url='')",
                    new String[]{HISTORICAL_SOURCE,JOB_VINTED});
            ContentValues archivedJob=new ContentValues();archivedJob.put("state",COMPLETE);archivedJob.put("progress",100);archivedJob.put("next_attempt_at",0);archivedJob.put("updated_at",now);archivedJob.put("last_error","historical sighting preserved; live lookup deferred until seen again");
            changed+=db.update("processing_jobs",archivedJob,
                    "job_type=? AND source=? AND state IN (?,?) AND listing_id IN (SELECT id FROM market_listings WHERE lifecycle='UNKNOWN')",
                    new String[]{JOB_VINTED,HISTORICAL_SOURCE,PENDING,FAILED_RETRYABLE});

            String deterministicWhere="job_type=? AND state=? AND ((last_error LIKE ?) OR (attempt>=2 AND (last_error LIKE ? OR last_error LIKE ? OR last_error LIKE ? OR last_error LIKE ? OR last_error LIKE ?)))";
            String[] deterministicArgs=new String[]{JOB_VINTED,FAILED_RETRYABLE,"Pagina Vinted non disponibile (404)%","nessun candidato Vinted abbastanza univoco%","metadati seller/foto non disponibili%","Nessun annuncio compatibile%","Più annunci compatibili%","Ho trovato un annuncio, ma titolo e prezzo non corrispondono abbastanza%"};
            // Only explicit user intent deserves a durable manual question. Automatic misses leave
            // the product quietly and never inflate the review inbox.
            ContentValues blocked=new ContentValues();blocked.put("state",FAILED_PERMANENT);blocked.put("next_attempt_at",0);blocked.put("updated_at",now);
            int explicit=db.update("processing_jobs",blocked,deterministicWhere+" AND source IN ('HUNT_PRIORITY','MANUAL_PRIORITY')",deterministicArgs);
            if(explicit>0){ContentValues review=new ContentValues();review.put("enrichment_state","NEEDS_REVIEW");review.put("manual_review_required",1);review.put("manual_review_reason","Collegamento Vinted ambiguo su richiesta esplicita");db.update("market_listings",review,"id IN (SELECT listing_id FROM processing_jobs WHERE job_type=? AND state=? AND source IN ('HUNT_PRIORITY','MANUAL_PRIORITY') AND listing_id IS NOT NULL) AND enrichment_state<>'COMPLETE'",new String[]{JOB_VINTED,FAILED_PERMANENT});}
            ContentValues autoDone=new ContentValues();autoDone.put("state",COMPLETE);autoDone.put("next_attempt_at",0);autoDone.put("updated_at",now);autoDone.put("progress",100);autoDone.put("processing_started_at",0);autoDone.put("last_error","auto-excluded: collegamento Vinted non abbastanza sicuro");
            int automatic=db.update("processing_jobs",autoDone,deterministicWhere+" AND COALESCE(source,'AUTO') NOT IN ('HUNT_PRIORITY','MANUAL_PRIORITY')",deterministicArgs);
            if(automatic>0){
                ContentValues excluded=new ContentValues();excluded.put("lifecycle","AUTO_FILTERED");excluded.put("enrichment_state","AUTO_EXCLUDED");excluded.put("manual_review_required",0);excluded.putNull("manual_review_reason");excluded.put("last_error","Collegamento Vinted non abbastanza sicuro");
                db.update("market_listings",excluded,"id IN (SELECT listing_id FROM processing_jobs WHERE job_type=? AND state=? AND last_error LIKE 'auto-excluded:%' AND listing_id IS NOT NULL)",new String[]{JOB_VINTED,COMPLETE});
            }
            changed+=explicit+automatic;
            // A batch-linked item may still be waiting for the richer item page to confirm the exact
            // BGG variant. If no deep-metadata job remains, automatic work is over: keep the row in
            // the persistent human-review inbox instead of leaving the run permanently stuck.
            String orphanWhere="lifecycle='ACTIVE' AND match_state='BGG_VARIANT_PENDING' AND vinted_item_id IS NOT NULL AND vinted_item_id<>'' AND NOT EXISTS (SELECT 1 FROM processing_jobs j WHERE j.listing_id=market_listings.id AND j.job_type=? AND j.state IN (?,?,?))";
            ContentValues variantReview=new ContentValues();variantReview.put("match_state","BGG_VARIANT_REVIEW");variantReview.put("manual_review_required",1);variantReview.put("manual_review_reason","Variante BGG non confermata automaticamente");variantReview.put("last_error","Variante BGG non confermata automaticamente");
            int orphanVariants=db.update("market_listings",variantReview,orphanWhere+" AND EXISTS(SELECT 1 FROM processing_jobs p WHERE p.listing_id=market_listings.id AND p.source IN ('HUNT_PRIORITY','MANUAL_PRIORITY'))",new String[]{JOB_VINTED_DEEP,PENDING,PROCESSING,FAILED_RETRYABLE});
            if(orphanVariants>0)db.execSQL("UPDATE deals SET verification_state='BGG_VARIANT_REVIEW',verification_reason='Variante BGG non confermata automaticamente' WHERE signature IN (SELECT legacy_signature FROM market_listings WHERE match_state='BGG_VARIANT_REVIEW' AND legacy_signature IS NOT NULL)");
            ContentValues variantExcluded=new ContentValues();variantExcluded.put("lifecycle","AUTO_FILTERED");variantExcluded.put("enrichment_state","AUTO_EXCLUDED");variantExcluded.put("manual_review_required",0);variantExcluded.putNull("manual_review_reason");variantExcluded.put("last_error","Variante BGG non confermata: esclusa dall'automatico");
            int autoVariants=db.update("market_listings",variantExcluded,orphanWhere+" AND NOT EXISTS(SELECT 1 FROM processing_jobs p WHERE p.listing_id=market_listings.id AND p.source IN ('HUNT_PRIORITY','MANUAL_PRIORITY'))",new String[]{JOB_VINTED_DEEP,PENDING,PROCESSING,FAILED_RETRYABLE});
            changed+=orphanVariants+autoVariants;
            db.setTransactionSuccessful();
        } finally { trace.phase("COMMIT");try{db.endTransaction();}finally{trace.phase("POST_TRANSACTION");} }
        // Catalog health uses the same paced public-page lane, but only when no Motore scroll owns
        // it. One exact item at a time is enough to refresh a small catalog without starving discovery.
        ContentValues unparkSeller=new ContentValues();unparkSeller.put("state",PENDING);unparkSeller.put("next_attempt_at",0);unparkSeller.put("updated_at",now);unparkSeller.put("last_error","");unparkSeller.put("progress",0);
        changed+=db.update("processing_jobs",unparkSeller,"id IN (SELECT p.id FROM processing_jobs p WHERE p.source=? AND p.state=? AND p.attempt=0 AND p.last_error=? AND p.listing_id IN (SELECT id FROM market_listings WHERE lifecycle='ACTIVE' AND (seller_id IS NULL OR seller_id='')) AND NOT EXISTS(SELECT 1 FROM processing_jobs busy WHERE busy.source IN ('SELLER_BACKFILL','CATALOG_HEALTH','CATALOG_RECOVERY') AND busy.state IN ('PENDING','PROCESSING','FAILED_RETRYABLE')) ORDER BY p.updated_at,p.id LIMIT 1)",new String[]{SELLER_BACKFILL_SOURCE,COMPLETE,"parked: nessuno scroll attivo"});
        changed+=enqueueCatalogHealthCheckIfIdle(now);
        if(changed>0)notifyQueueChanged();
        return changed;
    
        }catch(RuntimeException|Error failure){trace.failed(failure);throw failure;}finally{trace.close();}
    }

    private void recordSellerBackfillExclusions(SQLiteDatabase db){
        String sql="SELECT CASE WHEN seller_id IS NOT NULL AND seller_id<>'' THEN 'HAS_SELLER' WHEN COALESCE(vinted_item_id,'')='' OR COALESCE(vinted_url,'')='' THEN 'NO_IDENTITY' WHEN EXISTS(SELECT 1 FROM queue_controls q WHERE q.name='seller_backfill_once:'||l.id) THEN 'ONCE_MARKER' WHEN EXISTS(SELECT 1 FROM processing_jobs j WHERE j.listing_id=l.id AND j.state IN ('PENDING','PROCESSING','FAILED_RETRYABLE')) THEN 'ACTIVE_JOB' ELSE 'ELIGIBLE' END reason,COUNT(*) FROM market_listings l WHERE lifecycle='ACTIVE' GROUP BY reason";
        StringBuilder out=new StringBuilder("reason=NO_CANDIDATE");
        try(Cursor c=db.rawQuery(sql,null)){while(c.moveToNext())out.append(';').append(c.getString(0)).append('=').append(c.getLong(1));}
        setDiagnosticState("seller_backfill_schedule",0,out.toString());
    }

    public int enqueueCatalogHealthCheckIfIdle(long now){
        // Catalog maintenance is deliberately serial and opportunistic. It never competes with a
        // live Motore run, and it materialises at most one Vinted request at a time.
        if(helper.activeObservationSession()!=null){setDiagnosticState("seller_backfill_schedule",0,"reason=ACTIVE_OBSERVATION");return 0;}SQLiteDatabase db=helper.getWritableDatabase();
        try(Cursor active=db.rawQuery("SELECT id,listing_id,source,state,next_attempt_at FROM processing_jobs WHERE source IN (?,?,?) AND state IN (?,?,?) LIMIT 1",new String[]{CATALOG_HEALTH_SOURCE,CATALOG_RECOVERY_SOURCE,SELLER_BACKFILL_SOURCE,PENDING,PROCESSING,FAILED_RETRYABLE})){if(active.moveToFirst()){setDiagnosticState("seller_backfill_schedule",0,"reason=SERIAL_OWNER;job="+active.getLong(0)+";listing="+active.getLong(1)+";source="+active.getString(2)+";state="+active.getString(3)+";due="+active.getLong(4));return 0;}}
        // Phase 1 seller backfill: one exact already-known item at a time, only while Motore is idle.
        // This reuses the existing public-page lane and its 55 s / hourly circuit; it does not raise
        // request frequency. Each listing is attempted once automatically so a missing seller cannot
        // create an endless background loop. Seller identity unlocks deterministic same-seller bundles.
        Long sellerBackfill=scalarLong(db,
                "SELECT l.id FROM market_listings l WHERE l.lifecycle='ACTIVE' "+
                "AND (l.seller_id IS NULL OR l.seller_id='') "+
                "AND l.vinted_item_id IS NOT NULL AND l.vinted_item_id<>'' AND l.vinted_url IS NOT NULL AND l.vinted_url<>'' "+
                // browser_listing records acquisition provenance. It is not evidence that this exact item
                // already has seller identity, so only SELLER_BACKFILL may continue through this marker.
                "AND NOT EXISTS(SELECT 1 FROM queue_controls q WHERE q.name=?||l.id) "+
                "AND NOT EXISTS(SELECT 1 FROM processing_jobs j WHERE j.listing_id=l.id AND j.state IN (?,?,?)) "+
                "ORDER BY l.last_seen DESC,l.id DESC LIMIT 1",
                new String[]{SELLER_BACKFILL_MARKER_PREFIX,PENDING,PROCESSING,FAILED_RETRYABLE});
        if(sellerBackfill==null)recordSellerBackfillExclusions(db);
        if(sellerBackfill!=null){
            enqueueListingJob(db,sellerBackfill,JOB_VINTED_DEEP,now,6,SELLER_BACKFILL_SOURCE);
            boolean queued=false;
            try(Cursor q=db.rawQuery("SELECT 1 FROM processing_jobs WHERE listing_id=? AND source=? AND state IN (?,?,?) LIMIT 1",new String[]{String.valueOf(sellerBackfill),SELLER_BACKFILL_SOURCE,PENDING,PROCESSING,FAILED_RETRYABLE})){queued=q.moveToFirst();}
            if(queued){
                ContentValues marker=new ContentValues();marker.put("name",SELLER_BACKFILL_MARKER_PREFIX+sellerBackfill);marker.put("value",1);marker.put("updated_at",now);marker.put("text_value","state=QUEUED;source="+SELLER_BACKFILL_SOURCE);
                db.insertWithOnConflict("queue_controls",null,marker,SQLiteDatabase.CONFLICT_REPLACE);
                setDiagnosticState("seller_backfill",1,"state=QUEUED;listing="+sellerBackfill+";serial=true;rate=existing-public-lane");
                QueueKeepAliveService.ensureRunning(context);QueueWorkScheduler.schedule(context);return 1;
            }
            setDiagnosticState("seller_backfill",0,"state=ENQUEUE_REJECTED;listing="+sellerBackfill+";marker=false");
            return 0;
        }

        long cutoff=now-CATALOG_HEALTH_MAX_AGE_MS;
        Long listingId=scalarLong(db,
                "SELECT l.id FROM market_listings l JOIN games g ON g.id=l.game_id WHERE l.lifecycle='ACTIVE' "+
                "AND l.vinted_item_id IS NOT NULL AND l.vinted_item_id<>'' AND l.vinted_url IS NOT NULL AND l.vinted_url<>'' "+
                "AND g.database_visible=1 AND g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND g.rating>=? "+
                "AND NOT EXISTS(SELECT 1 FROM queue_controls q WHERE q.name='browser_listing:'||l.id AND q.value=1) AND COALESCE(l.enriched_at,0)<? AND NOT EXISTS(SELECT 1 FROM processing_jobs j WHERE j.listing_id=l.id AND j.state IN (?,?,?)) "+
                "ORDER BY CASE WHEN l.published_label IS NULL OR l.published_label='' OR l.seller_id IS NULL OR l.seller_id='' THEN 0 ELSE 1 END,COALESCE(l.enriched_at,0) ASC LIMIT 1",
                new String[]{String.valueOf(DealPolicy.MIN_BGG_RATING),String.valueOf(cutoff),PENDING,PROCESSING,FAILED_RETRYABLE});
        if(listingId!=null){
            enqueueListingJob(db,listingId,JOB_VINTED_DEEP,now,5,CATALOG_HEALTH_SOURCE);
            setDiagnosticState("catalog_health",1,"build=catalog-health-v2;state=REFRESH_QUEUED;listing="+listingId+";maxAgeMs="+CATALOG_HEALTH_MAX_AGE_MS);
            QueueKeepAliveService.ensureRunning(context);QueueWorkScheduler.schedule(context);return 1;
        }

        // Old catalog cards that never acquired an exact Vinted identity are recovered one by one.
        // This intentionally avoids dumping the historical backlog into the human inbox.
        long recoveryCutoff=now-CATALOG_RECOVERY_MIN_AGE_MS;
        Long recovery=scalarLong(db,
                "SELECT l.id FROM market_listings l JOIN games g ON g.id=l.game_id WHERE l.lifecycle='ACTIVE' "+
                "AND (l.vinted_url IS NULL OR l.vinted_url='') AND COALESCE(l.manual_review_required,0)=0 "+
                "AND g.database_visible=1 AND g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND g.match_state='MATCHED' AND g.rating>=? "+
                "AND l.last_seen<? AND NOT EXISTS(SELECT 1 FROM processing_jobs j WHERE j.listing_id=l.id AND j.state IN (?,?,?)) "+
                "ORDER BY l.last_seen ASC LIMIT 1",
                new String[]{String.valueOf(DealPolicy.MIN_BGG_RATING),String.valueOf(recoveryCutoff),PENDING,PROCESSING,FAILED_RETRYABLE});
        if(recovery==null){setDiagnosticState("catalog_health",0,"build=catalog-health-v2;state=IDLE");return 0;}
        ContentValues st=new ContentValues();st.put("enrichment_state","PENDING_ENRICHMENT");st.put("last_error","");db.update("market_listings",st,"id=?",new String[]{String.valueOf(recovery)});
        enqueueListingJob(db,recovery,JOB_VINTED,now,4,CATALOG_RECOVERY_SOURCE);
        setDiagnosticState("catalog_health",1,"build=catalog-health-v2;state=RECOVERY_QUEUED;listing="+recovery+";serial=true");
        QueueKeepAliveService.ensureRunning(context);QueueWorkScheduler.schedule(context);return 1;
    }

    /** 5.12.22 cut-over: 5.12.21 proved that the old O(31k) exact matcher could hit its
     * CPU budget and incorrectly create human review. Those rows are technical debt, not ambiguity.
     * Reopen only timeout/error reviews once so the compact exact index can evaluate them again. */
    public int reopenTechnicalBggReviewsForExactIndex(long now){
        final String marker="bgg_exact_index_v4_cutover";
        SQLiteDatabase db=helper.getWritableDatabase();
        try(Cursor done=db.rawQuery("SELECT 1 FROM queue_controls WHERE name=? LIMIT 1",new String[]{marker})){if(done.moveToFirst())return 0;}
        String technical="database_visible=1 AND (bgg_id IS NULL OR bgg_id='') AND match_state='BGG_MATCH_REVIEW' AND ("+
                "filter_reason LIKE 'Ricerca BGG %oltre il budget%' OR filter_reason LIKE 'Errore match locale:%' OR filter_reason LIKE 'Errore tecnico match locale:%')";
        int reopened=0;db.beginTransaction();try{
            ContentValues g=new ContentValues();g.put("match_state","BGG_MATCH_REQUIRED");g.put("filter_reason","Riaperto da indice BGG esatto 5.12.22");g.put("match_algorithm_version",BGG_MATCH_ALGORITHM_VERSION);
            reopened=db.update("games",g,technical,null);
            if(reopened>0){
                ContentValues l=new ContentValues();l.put("match_state","BGG_MATCH_REQUIRED");l.put("manual_review_required",0);l.putNull("manual_review_reason");l.put("last_error","");
                db.update("market_listings",l,"game_id IN (SELECT id FROM games WHERE filter_reason='Riaperto da indice BGG esatto 5.12.22') AND lifecycle='ACTIVE'",null);
                db.execSQL("UPDATE market_listings SET enrichment_state=CASE WHEN vinted_url IS NOT NULL AND vinted_url<>'' THEN 'CORE_COMPLETE' ELSE 'LOCAL_ONLY' END WHERE game_id IN (SELECT id FROM games WHERE filter_reason='Riaperto da indice BGG esatto 5.12.22') AND lifecycle='ACTIVE'");
            }
            ContentValues q=new ContentValues();q.put("name",marker);q.put("value",reopened);q.put("updated_at",now);q.put("text_value","build=bgg-exact-index-v4;reopened="+reopened);db.insertWithOnConflict("queue_controls",null,q,SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        if(reopened>0){setDiagnosticState("bgg_exact_index_cutover",reopened,"build=bgg-exact-index-v4;reopened="+reopened);notifyQueueChanged();}
        return reopened;
    }

    private long[] engineRunSlice(SQLiteDatabase db){
        long owner=0L,started=0L;
        try(Cursor c=db.rawQuery("SELECT value,updated_at FROM queue_controls WHERE name=? LIMIT 1",new String[]{ENGINE_RUN_SLICE})){
            if(c.moveToFirst()){owner=Math.max(0L,c.getLong(0));started=Math.max(0L,c.getLong(1));}
        }
        return new long[]{owner,started};
    }

    private void writeEngineRunControl(SQLiteDatabase db,String name,long value,long now,String detail){
        ContentValues v=new ContentValues();v.put("name",name);v.put("value",value);v.put("updated_at",now);v.put("text_value",detail);
        db.insertWithOnConflict("queue_controls",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Fairness rotation only: after one service slice, a large run may hand the ordinary automatic
     * lane to the next unfinished scroll. Nothing is completed, hidden, excluded or reclassified.
     * If no other scroll is waiting, the current run simply keeps going. */
    public int yieldOverBudgetEngineRun(long now){
        DealDatabase.ObservationSession active=helper.activeObservationSession();
        if(active==null){
            setDiagnosticState("engine_fairness",0,"build=engine-fairness-v1;state=IDLE;nonDestructive=true");
            return 0;
        }
        // Do not rotate while the user is still creating the current scroll/session.
        if(now-active.endAt<DealDatabase.ENGINE_SESSION_GAP_MS)return 0;
        SQLiteDatabase db=helper.getWritableDatabase();long[] slice=engineRunSlice(db);
        if(slice[0]!=active.startAt||slice[1]<=0L){
            writeEngineRunControl(db,ENGINE_RUN_SLICE,active.startAt,now,"active="+active.startAt);
            setDiagnosticState("engine_fairness",0,"build=engine-fairness-v1;state=ACTIVE;run="+active.startAt+";servedMs=0;sliceMs="+DealDatabase.ENGINE_RUN_FAIRNESS_SLICE_MS+";nonDestructive=true");
            return 0;
        }
        long served=Math.max(0L,now-slice[1]);
        if(served<DealDatabase.ENGINE_RUN_FAIRNESS_SLICE_MS){
            setDiagnosticState("engine_fairness",0,"build=engine-fairness-v1;state=ACTIVE;run="+active.startAt+";servedMs="+served+";sliceMs="+DealDatabase.ENGINE_RUN_FAIRNESS_SLICE_MS+";nonDestructive=true");
            return 0;
        }
        DealDatabase.ObservationSession next=helper.nextUnfinishedObservationSessionAfter(active.startAt,now);
        if(next==null){
            setDiagnosticState("engine_fairness",0,"build=engine-fairness-v1;state=CONTINUING;run="+active.startAt+";servedMs="+served+";waiting=0;nonDestructive=true");
            return 0;
        }
        db.beginTransaction();try{
            writeEngineRunControl(db,ENGINE_RUN_CURSOR,next.startAt,now,"yieldedFrom="+active.startAt+";to="+next.startAt);
            writeEngineRunControl(db,ENGINE_RUN_SLICE,next.startAt,now,"active="+next.startAt+";resumedFrom="+active.startAt);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        helper.invalidateActiveObservationSessionCache();
        setDiagnosticState("engine_fairness",1,"build=engine-fairness-v1;state=YIELDED;from="+active.startAt+";to="+next.startAt+";servedMs="+served+";remaining="+Math.max(0,active.validListings-active.completeListings-active.reviewListings)+";nonDestructive=true");
        notifyQueueChanged();
        return 1;
    }

    /** Timing telemetry only. A slow run must remain eligible for completion: elapsed time alone
     * is never evidence that a listing is wrong. The adaptive target reflects core remote work,
     * while ETA shrinks with the candidates still waiting for Vinted identity. */
    public int observeEngineTiming(long now){
        DealDatabase.ObservationSession active=helper.activeObservationSession();
        if(active==null){
            setDiagnosticState("engine_sla",0,"build=engine-timing-v2;state=IDLE;nonDestructive=true");
            return 0;
        }
        long target=DealDatabase.engineTargetMs(active);
        long eta=DealDatabase.engineEtaMs(active);
        long sinceEnd=Math.max(0L,now-active.endAt);
        boolean overTarget=sinceEnd>=target&&!DealDatabase.engineContentSettled(active);
        setDiagnosticState("engine_sla",overTarget?1:0,
                "build=engine-timing-v3;state="+(overTarget?"OVER_TARGET":"ACTIVE")+
                        ";targetMs="+target+";etaMs="+eta+";sinceEndMs="+sinceEnd+";valid="+active.validListings+
                        ";coreWork="+active.coreWorkListings+";corePending="+active.corePendingListings+
                        ";ready="+active.completeListings+";review="+active.reviewListings+
                        ";analysisPending="+active.analysisPendingListings+";nonDestructive=true");
        return 0;
    }

    public int vintedActiveCount() {
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM processing_jobs WHERE job_type IN (?,?) AND state IN (?,?,?)",new String[]{JOB_VINTED,JOB_VINTED_DEEP,PENDING,PROCESSING,FAILED_RETRYABLE})){return c.moveToFirst()?c.getInt(0):0;}
    }

    /** Core Vinted identity jobs are what the user is actually waiting for. Deep metadata is
     * opportunistic and intentionally does not inflate the visible backlog. */
    public int coreVintedActiveCount() {
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM processing_jobs WHERE job_type=? AND state IN (?,?,?)",new String[]{JOB_VINTED,PENDING,PROCESSING,FAILED_RETRYABLE})){return c.moveToFirst()?c.getInt(0):0;}
    }

    /** Core identity jobs that are allowed to participate in the current automatic run. Jobs from
     * newer waiting scrolls do not occupy this window and therefore cannot starve the active run. */
    public int activeRunCoreVintedCount(){
        DealDatabase.ObservationSession run=helper.activeObservationSession();SQLiteDatabase db=helper.getReadableDatabase();
        if(run==null){try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM processing_jobs WHERE job_type=? AND state IN (?,?,?)",new String[]{JOB_VINTED,PENDING,PROCESSING,FAILED_RETRYABLE})){return c.moveToFirst()?c.getInt(0):0;}}
        String sql="SELECT COUNT(*) FROM processing_jobs j JOIN market_listings l ON l.id=j.listing_id WHERE j.job_type=? AND j.state IN (?,?,?) AND (j.source IN ('HUNT_PRIORITY','MANUAL_PRIORITY') OR COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) IN (SELECT signature FROM observations WHERE observed_at>=? AND observed_at<=?))";
        try(Cursor c=db.rawQuery(sql,new String[]{JOB_VINTED,PENDING,PROCESSING,FAILED_RETRYABLE,String.valueOf(run.startAt),String.valueOf(run.endAt)})){return c.moveToFirst()?c.getInt(0):0;}
    }

    /** Observations kept locally on purpose: useful for price/history, but they do not consume the remote Vinted lane. */
    public int localOnlyListingCount() {
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM market_listings WHERE lifecycle='ACTIVE' AND enrichment_state='LOCAL_ONLY'",null)){return c.moveToFirst()?c.getInt(0):0;}
    }

    /** Valid >=6 listings that still need their Vinted identity, but are not currently occupying
     * the network queue. This is eventual work, not a permanent local-only bucket. */
    public int deferredVintedCount(){
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM market_listings l JOIN games g ON g.id=l.game_id WHERE l.lifecycle='ACTIVE' AND l.enrichment_state='DEFERRED_LINK' AND (l.vinted_url IS NULL OR l.vinted_url='') AND g.database_visible=1 AND g.rating>=?",new String[]{String.valueOf(DealPolicy.MIN_BGG_RATING)})){return c.moveToFirst()?c.getInt(0):0;}
    }

    /** Deferred Vinted identities that belong to the scroll currently owning Motore. These rows
     * have no processing job until materialised, so queue liveness checks must count them explicitly. */
    public int activeRunDeferredVintedCount(){
        DealDatabase.ObservationSession run=helper.activeObservationSession();if(run==null)return 0;
        String sql="SELECT COUNT(*) FROM market_listings l JOIN games g ON g.id=l.game_id "+
                "LEFT JOIN deals d ON d.signature=COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) "+
                "WHERE l.lifecycle='ACTIVE' AND l.enrichment_state='DEFERRED_LINK' "+
                "AND (l.vinted_url IS NULL OR l.vinted_url='') AND g.database_visible=1 AND g.rating>=? "+
                "AND g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND g.match_state='MATCHED' "+
                "AND COALESCE(l.manual_review_required,0)=0 AND l.match_state<>'BGG_VARIANT_REVIEW' "+
                "AND COALESCE(d.verification_state,'') NOT IN ('BGG_VARIANT_REVIEW','MATCH_UNCERTAIN','PRICE_ANOMALY','EXPANSION_CHECK') "+
                "AND COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) IN (SELECT signature FROM observations WHERE observed_at>=? AND observed_at<=?)";
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{String.valueOf(DealPolicy.MIN_BGG_RATING),String.valueOf(run.startAt),String.valueOf(run.endAt)})){return c.moveToFirst()?c.getInt(0):0;}
    }

    public boolean listingBelongsToActiveRun(long listingId){
        if(listingId<=0)return false;DealDatabase.ObservationSession run=helper.activeObservationSession();if(run==null)return false;
        String sql="SELECT 1 FROM market_listings l WHERE l.id=? AND COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) IN (SELECT signature FROM observations WHERE observed_at>=? AND observed_at<=?) LIMIT 1";
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{String.valueOf(listingId),String.valueOf(run.startAt),String.valueOf(run.endAt)})){return c.moveToFirst();}
    }

    public int deferredVintedReadyCount(long now){
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM market_listings l JOIN games g ON g.id=l.game_id WHERE l.lifecycle='ACTIVE' AND l.enrichment_state='DEFERRED_LINK' AND l.deferred_retry_at<=? AND (l.vinted_url IS NULL OR l.vinted_url='') AND g.database_visible=1 AND g.rating>=?",new String[]{String.valueOf(now),String.valueOf(DealPolicy.MIN_BGG_RATING)})){return c.moveToFirst()?c.getInt(0):0;}
    }

    public long nextDeferredVintedDueAt(){
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT MIN(l.deferred_retry_at) FROM market_listings l JOIN games g ON g.id=l.game_id WHERE l.lifecycle='ACTIVE' AND l.enrichment_state='DEFERRED_LINK' AND (l.vinted_url IS NULL OR l.vinted_url='') AND g.database_visible=1 AND g.rating>=?",new String[]{String.valueOf(DealPolicy.MIN_BGG_RATING)})){if(c.moveToFirst()&&!c.isNull(0))return c.getLong(0);}return 0L;
    }

    /** Materialise only one small same-game batch. A canonical game query can then be shared by
     * VintedPublicSession's cache across several listings, while LIVE/HUNT/MANUAL priorities still preempt. */
    public int promoteDeferredVintedBatch(int limit){
        long now=System.currentTimeMillis();
        // Safe mode intentionally does not run the legacy benchmark-based early price shortcut.
        // Fresh DealEvaluator decisions already use BGG-only evidence; old benchmark rows are being repaired separately.
        int wanted=Math.max(1,Math.min(8,limit));SQLiteDatabase db=helper.getWritableDatabase();int queued=0;db.beginTransaction();
        try{
            if(activeRunCoreVintedCount()>=8){db.setTransactionSuccessful();return 0;}
            DealDatabase.ObservationSession activeRun=helper.activeObservationSession();
            if(activeRun==null){db.setTransactionSuccessful();return 0;}
            // deferred_retry_at is a background-parking throttle only. Once this scroll owns the
            // Motore lane again, its unresolved listings must be rematerialised now rather than wait
            // hours for a retry timestamp that was assigned while another run was active.
            Long gameId=scalarLong(db,"SELECT l.game_id FROM market_listings l JOIN games g ON g.id=l.game_id LEFT JOIN deals d ON d.signature=COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) WHERE l.lifecycle='ACTIVE' AND l.enrichment_state='DEFERRED_LINK' AND (l.vinted_url IS NULL OR l.vinted_url='') AND g.database_visible=1 AND g.rating>=? AND g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND g.match_state='MATCHED' AND COALESCE(l.manual_review_required,0)=0 AND l.match_state<>'BGG_VARIANT_REVIEW' AND COALESCE(d.verification_state,'') NOT IN ('BGG_VARIANT_REVIEW','MATCH_UNCERTAIN','PRICE_ANOMALY','EXPANSION_CHECK') AND COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) IN (SELECT signature FROM observations WHERE observed_at>=? AND observed_at<=?) GROUP BY l.game_id ORDER BY MIN(l.first_seen) ASC LIMIT 1",new String[]{String.valueOf(DealPolicy.MIN_BGG_RATING),String.valueOf(activeRun.startAt),String.valueOf(activeRun.endAt)});
            if(gameId==null){db.setTransactionSuccessful();return 0;}
            try(Cursor c=db.rawQuery("SELECT l.id FROM market_listings l JOIN games g ON g.id=l.game_id LEFT JOIN deals d ON d.signature=COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) WHERE l.game_id=? AND l.lifecycle='ACTIVE' AND l.enrichment_state='DEFERRED_LINK' AND (l.vinted_url IS NULL OR l.vinted_url='') AND g.database_visible=1 AND g.rating>=? AND g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND g.match_state='MATCHED' AND COALESCE(l.manual_review_required,0)=0 AND l.match_state<>'BGG_VARIANT_REVIEW' AND COALESCE(d.verification_state,'') NOT IN ('BGG_VARIANT_REVIEW','MATCH_UNCERTAIN','PRICE_ANOMALY','EXPANSION_CHECK') AND COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) IN (SELECT signature FROM observations WHERE observed_at>=? AND observed_at<=?) ORDER BY l.first_seen ASC LIMIT ?",new String[]{String.valueOf(gameId),String.valueOf(DealPolicy.MIN_BGG_RATING),String.valueOf(activeRun.startAt),String.valueOf(activeRun.endAt),String.valueOf(wanted)})){
                while(c.moveToNext()){long id=c.getLong(0);ContentValues st=new ContentValues();st.put("enrichment_state","PENDING_ENRICHMENT");db.update("market_listings",st,"id=?",new String[]{String.valueOf(id)});enqueueListingJob(db,id,JOB_VINTED,now,80,"DEFERRED_LINK");queued++;}
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        if(queued>0)notifyQueueChanged();return queued;
    }

    /** Failed background linking is never converted into hundreds of human fact-checks. It returns
     * to the deferred pool with a long retry time; urgent/manual work keeps the old review behavior. */
    public void deferBackgroundLink(Job job,String reason,long retryAt){
        if(job==null)return;SQLiteDatabase db=helper.getWritableDatabase();long now=System.currentTimeMillis();db.beginTransaction();try{
            ContentValues j=new ContentValues();j.put("state",COMPLETE);j.put("next_attempt_at",0);j.put("updated_at",now);j.put("progress",100);j.put("processing_started_at",0);j.put("last_error","deferred: "+safe(reason));db.update("processing_jobs",j,"id=?",new String[]{String.valueOf(job.id)});
            ContentValues l=new ContentValues();l.put("enrichment_state","DEFERRED_LINK");l.put("deferred_retry_at",Math.max(now+60_000L,retryAt));l.put("last_error",safe(reason));db.update("market_listings",l,"id=?",new String[]{String.valueOf(job.listingId)});db.setTransactionSuccessful();
        }finally{db.endTransaction();}notifyQueueChanged();
    }

    /** Zero-network maintenance keeps useful work moving even while Vinted is in cooldown. */
    public int inferDeferredLanguages(int limit){
        int changed=0;SQLiteDatabase db=helper.getWritableDatabase();List<long[]> ids=new ArrayList<>();List<String[]> vals=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT id,COALESCE(vinted_title,''),COALESCE(observed_text,''),COALESCE(brand,''),COALESCE(language_code,'') FROM market_listings WHERE lifecycle='ACTIVE' AND (language_code IS NULL OR language_code='' OR language_code LIKE '?%') ORDER BY last_seen DESC LIMIT ?",new String[]{String.valueOf(Math.max(1,limit))})){
            while(c.moveToNext()){String detected=ListingLanguageDetector.detect(c.getString(1),c.getString(2),c.getString(3));if(detected.isEmpty())continue;String merged=ListingLanguageDetector.mergeWithDependency(detected,c.getString(4));vals.add(new String[]{String.valueOf(c.getLong(0)),merged});}
        }
        for(String[] x:vals){ContentValues v=new ContentValues();v.put("language_code",x[1]);changed+=db.update("market_listings",v,"id=?",new String[]{x[0]});}
        if(changed>0){try{db.execSQL("UPDATE deals SET language_code=(SELECT l.language_code FROM market_listings l WHERE l.legacy_signature=deals.signature ORDER BY l.last_seen DESC LIMIT 1) WHERE signature IN (SELECT legacy_signature FROM market_listings WHERE legacy_signature IS NOT NULL AND language_code IS NOT NULL)");}catch(Throwable ignored){}}
        return changed;
    }

    public static final class LocalBatchSummary {
        public final int before,after,nonGamesHidden,languagesInferred,remoteJobsCompacted;
        LocalBatchSummary(int before,int after,int hidden,int languages,int compacted){this.before=before;this.after=after;this.nonGamesHidden=hidden;this.languagesInferred=languages;this.remoteJobsCompacted=compacted;}
    }

    /** v5.11.29: explicit zero-network pass over the local backlog. This deliberately performs no
     * Vinted HTTP work: it removes obvious non-games, infers language from already captured text,
     * compacts accidental automatic network jobs and normalises eligible observations into the
     * deferred-link pool. The expensive remote identity step remains bounded by the normal queue. */
    public LocalBatchSummary optimizeLocalBacklog(){
        int before=deferredVintedCount()+localOnlyListingCount()+pendingAnalysisCount();
        int hidden=autoHideStrongNonGameListings();int languages=0;
        // Work in large local chunks. Repeating stops naturally once no new language can be inferred.
        for(int i=0;i<12;i++){int n=inferDeferredLanguages(750);languages+=n;if(n==0)break;}
        int compacted=compactVintedBacklogToLiveLane();
        // Re-apply the cheap eligibility projection in one SQL statement in case older rows were
        // still LOCAL_ONLY even though BGG identity/rating are already known.
        SQLiteDatabase db=helper.getWritableDatabase();ContentValues d=new ContentValues();d.put("enrichment_state","DEFERRED_LINK");d.put("deferred_retry_at",0);
        db.update("market_listings",d,"lifecycle='ACTIVE' AND enrichment_state='LOCAL_ONLY' AND (vinted_url IS NULL OR vinted_url='') AND game_id IN (SELECT id FROM games WHERE database_visible=1 AND bgg_id IS NOT NULL AND bgg_id<>'' AND rating>=?)",new String[]{String.valueOf(DealPolicy.MIN_BGG_RATING)});
        int after=deferredVintedCount()+localOnlyListingCount()+pendingAnalysisCount();notifyQueueChanged();return new LocalBatchSummary(before,after,hidden,languages,compacted);
    }

    public int deepMetadataActiveCount() {
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM processing_jobs WHERE job_type=? AND state IN (?,?,?)",new String[]{JOB_VINTED_DEEP,PENDING,PROCESSING,FAILED_RETRYABLE})){return c.moveToFirst()?c.getInt(0):0;}
    }

    public int bggActiveCount() {
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM processing_jobs WHERE job_type=? AND state IN (?,?,?)",new String[]{JOB_BGG,PENDING,PROCESSING,FAILED_RETRYABLE})){return c.moveToFirst()?c.getInt(0):0;}
    }

    public int bggUnrunnableCount() {
        String sql="SELECT COUNT(*) FROM processing_jobs j LEFT JOIN games g ON g.id=j.game_id WHERE j.job_type=? AND j.state IN (?,?,?) AND (g.id IS NULL OR g.bgg_id IS NULL OR g.bgg_id='' OR g.database_visible=0)";
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{JOB_BGG,PENDING,PROCESSING,FAILED_RETRYABLE})){return c.moveToFirst()?c.getInt(0):0;}
    }

    /** Visible games that cannot enter the BGG enrichment lane until their identity is confirmed. */
    public int bggMatchRequiredCount() {
        DealDatabase.ObservationSession run=helper.activeObservationSession();String runExtra=run==null?"":" AND EXISTS (SELECT 1 FROM market_listings l WHERE l.game_id=games.id AND COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) IN (SELECT signature FROM observations WHERE observed_at>=? AND observed_at<=?))";
        String sql="SELECT COUNT(*) FROM games WHERE database_visible=1 AND (bgg_id IS NULL OR bgg_id='') AND (match_state='BGG_MATCH_REQUIRED' OR (match_state='BGG_MATCH_REVIEW' AND COALESCE(match_algorithm_version,0)<CAST(? AS INTEGER)))"+runExtra;
        java.util.ArrayList<String> a=new java.util.ArrayList<>();a.add(String.valueOf(BGG_MATCH_ALGORITHM_VERSION));if(run!=null){a.add(String.valueOf(run.startAt));a.add(String.valueOf(run.endAt));}
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,a.toArray(new String[0]))){return c.moveToFirst()?c.getInt(0):0;}
    }

    public int userVisibleActiveCount() {
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM processing_jobs WHERE job_type<>? AND state IN (?,?,?)",new String[]{JOB_VINTED_DEEP,PENDING,PROCESSING,FAILED_RETRYABLE})){return c.moveToFirst()?c.getInt(0):0;}
    }

    public int userVisibleProcessingCount() {
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM processing_jobs WHERE job_type<>? AND state=?",new String[]{JOB_VINTED_DEEP,PROCESSING})){return c.moveToFirst()?c.getInt(0):0;}
    }

    public int userVisibleCompletedSince(long since) {
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM processing_jobs WHERE job_type<>? AND state=? AND updated_at>=? AND (last_error IS NULL OR last_error='' OR (last_error NOT LIKE 'historical sighting preserved%' AND last_error NOT LIKE 'not runnable anymore%' AND last_error NOT LIKE 'BGG match necessario%'))",new String[]{JOB_VINTED_DEEP,COMPLETE,String.valueOf(Math.max(0L,since))})){return c.moveToFirst()?c.getInt(0):0;}
    }

    /** Compact, privacy-safe view of active queue leases for diagnostics. */
    public String processingLeaseSummary(long now) {
        StringBuilder out=new StringBuilder();
        int total=0;
        try(Cursor c=helper.getReadableDatabase().rawQuery(
                "SELECT job_type,COUNT(*),MIN(CASE WHEN COALESCE(processing_started_at,0)>0 THEN processing_started_at END),SUM(CASE WHEN COALESCE(processing_started_at,0)<=0 THEN 1 ELSE 0 END) FROM processing_jobs WHERE state=? GROUP BY job_type ORDER BY job_type",
                new String[]{PROCESSING})) {
            while(c.moveToNext()) {
                if(out.length()>0)out.append(';');
                String type=c.getString(0);
                int count=c.getInt(1);
                long oldestStarted=c.isNull(2)?0L:c.getLong(2);
                int missingStart=c.getInt(3);
                total+=count;
                out.append(type).append("={count=").append(count)
                        .append(";oldestAgeMs=").append(oldestStarted>0?Math.max(0L,now-oldestStarted):-1L)
                        .append(";withoutStartAt=").append(missingStart).append('}');
            }
        }
        return "total="+total+";byType="+(out.length()==0?"none":out.toString());
    }

    public int processingVintedCount() {
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM processing_jobs WHERE job_type IN (?,?) AND state=?",new String[]{JOB_VINTED,JOB_VINTED_DEEP,PROCESSING})){return c.moveToFirst()?c.getInt(0):0;}
    }

    public int processingCount(String jobType) {
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM processing_jobs WHERE job_type=? AND state=?",new String[]{jobType,PROCESSING})){return c.moveToFirst()?c.getInt(0):0;}
    }

    /** Count only work that a lane can actually claim. The previous generic count included rows
     * that the claim query itself filtered out, which made Activity say “ready” while no job was
     * runnable. */
    public int runnableVintedDueCount(long now) {
        if(isVintedPaused())return 0;
        SQLiteDatabase db=helper.getReadableDatabase();boolean allowHistory=vintedHistoryAllowed(db,now);DealDatabase.ObservationSession run=helper.activeObservationSession();
        String historyExtra=allowHistory?"":" AND j.source<>?";
        String runExtra=run==null?" AND j.source IN ('HUNT_PRIORITY','MANUAL_PRIORITY','LIVE_DEAL','CATALOG_HEALTH','SELLER_BACKFILL')":" AND j.source<>'SELLER_BACKFILL' AND (j.source IN ('HUNT_PRIORITY','MANUAL_PRIORITY') OR COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) IN (SELECT signature FROM observations WHERE observed_at>=? AND observed_at<=?))";
        String sql="SELECT COUNT(*) FROM processing_jobs j JOIN market_listings l ON l.id=j.listing_id " +
                "WHERE j.job_type IN (?,?) AND j.state IN (?,?) AND j.next_attempt_at<=? AND l.lifecycle='ACTIVE'"+historyExtra+runExtra;
        java.util.ArrayList<String> a=new java.util.ArrayList<>();a.add(JOB_VINTED);a.add(JOB_VINTED_DEEP);a.add(PENDING);a.add(FAILED_RETRYABLE);a.add(String.valueOf(now));if(!allowHistory)a.add(HISTORICAL_SOURCE);if(run!=null){a.add(String.valueOf(run.startAt));a.add(String.valueOf(run.endAt));}
        try(Cursor c=db.rawQuery(sql,a.toArray(new String[0]))){return c.moveToFirst()?c.getInt(0):0;}
    }


    /** Age of the oldest due, active Vinted job using the same eligibility filters as the claimable count. */
    public long oldestRunnableVintedAgeMs(long now) {
        if(isVintedPaused())return -1L;
        SQLiteDatabase db=helper.getReadableDatabase();boolean allowHistory=vintedHistoryAllowed(db,now);DealDatabase.ObservationSession run=helper.activeObservationSession();
        String historyExtra=allowHistory?"":" AND j.source<>?";
        String runExtra=run==null?" AND j.source IN ('HUNT_PRIORITY','MANUAL_PRIORITY','LIVE_DEAL','CATALOG_HEALTH','SELLER_BACKFILL')":" AND j.source<>'SELLER_BACKFILL' AND (j.source IN ('HUNT_PRIORITY','MANUAL_PRIORITY') OR COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) IN (SELECT signature FROM observations WHERE observed_at>=? AND observed_at<=?))";
        String sql="SELECT MIN(j.created_at) FROM processing_jobs j JOIN market_listings l ON l.id=j.listing_id "+
                "WHERE j.job_type IN (?,?) AND j.state IN (?,?) AND j.next_attempt_at<=? AND l.lifecycle='ACTIVE'"+historyExtra+runExtra;
        java.util.ArrayList<String> a=new java.util.ArrayList<>();a.add(JOB_VINTED);a.add(JOB_VINTED_DEEP);a.add(PENDING);a.add(FAILED_RETRYABLE);a.add(String.valueOf(now));if(!allowHistory)a.add(HISTORICAL_SOURCE);if(run!=null){a.add(String.valueOf(run.startAt));a.add(String.valueOf(run.endAt));}
        try(Cursor c=db.rawQuery(sql,a.toArray(new String[0]))){
            if(!c.moveToFirst()||c.isNull(0))return -1L;
            return Math.max(0L,now-c.getLong(0));
        }catch(Throwable ignored){return -1L;}
    }


    public int runnableBggDueCount(long now) {
        if(isBggPaused())return 0;
        SQLiteDatabase db=helper.getReadableDatabase();boolean allowHistory=historyAllowed(db,now,JOB_BGG);DealDatabase.ObservationSession run=helper.activeObservationSession();
        String historyExtra=allowHistory?"":" AND j.source<>?";
        String runExtra=run==null?"":" AND (j.source IN ('HUNT_PRIORITY','MANUAL_PRIORITY') OR EXISTS (SELECT 1 FROM market_listings l WHERE l.game_id=j.game_id AND COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) IN (SELECT signature FROM observations WHERE observed_at>=? AND observed_at<=?)))";
        String sql="SELECT COUNT(*) FROM processing_jobs j JOIN games g ON g.id=j.game_id " +
                "WHERE j.job_type=? AND j.state IN (?,?) AND j.next_attempt_at<=? AND g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND g.database_visible=1"+historyExtra+runExtra;
        java.util.ArrayList<String> a=new java.util.ArrayList<>();a.add(JOB_BGG);a.add(PENDING);a.add(FAILED_RETRYABLE);a.add(String.valueOf(now));if(!allowHistory)a.add(HISTORICAL_SOURCE);if(run!=null){a.add(String.valueOf(run.startAt));a.add(String.valueOf(run.endAt));}
        try(Cursor c=db.rawQuery(sql,a.toArray(new String[0]))){return c.moveToFirst()?c.getInt(0):0;}
    }

    public int dueNowCount(long now) { return runnableVintedDueCount(now)+runnableBggDueCount(now); }

    public long nextRunnableVintedDueAt() {
        if(isVintedPaused())return 0L;
        long now=System.currentTimeMillis();SQLiteDatabase db=helper.getReadableDatabase();boolean allowHistory=vintedHistoryAllowed(db,now);DealDatabase.ObservationSession run=helper.activeObservationSession();
        String historyExtra=allowHistory?"":" AND j.source<>?";
        String runExtra=run==null?" AND j.source IN ('HUNT_PRIORITY','MANUAL_PRIORITY','LIVE_DEAL','CATALOG_HEALTH','SELLER_BACKFILL')":" AND j.source<>'SELLER_BACKFILL' AND (j.source IN ('HUNT_PRIORITY','MANUAL_PRIORITY') OR COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) IN (SELECT signature FROM observations WHERE observed_at>=? AND observed_at<=?))";
        String sql="SELECT MIN(j.next_attempt_at) FROM processing_jobs j JOIN market_listings l ON l.id=j.listing_id " +
                "WHERE j.job_type IN (?,?) AND j.state IN (?,?) AND l.lifecycle='ACTIVE'"+historyExtra+runExtra;
        java.util.ArrayList<String> a=new java.util.ArrayList<>();a.add(JOB_VINTED);a.add(JOB_VINTED_DEEP);a.add(PENDING);a.add(FAILED_RETRYABLE);if(!allowHistory)a.add(HISTORICAL_SOURCE);if(run!=null){a.add(String.valueOf(run.startAt));a.add(String.valueOf(run.endAt));}
        try(Cursor c=db.rawQuery(sql,a.toArray(new String[0]))){if(c.moveToFirst()&&!c.isNull(0))return c.getLong(0);}return 0L;
    }


    public long nextRunnableBggDueAt() {
        if(isBggPaused())return 0L;
        long now=System.currentTimeMillis();SQLiteDatabase db=helper.getReadableDatabase();boolean allowHistory=historyAllowed(db,now,JOB_BGG);DealDatabase.ObservationSession run=helper.activeObservationSession();
        String historyExtra=allowHistory?"":" AND j.source<>?";
        String runExtra=run==null?"":" AND (j.source IN ('HUNT_PRIORITY','MANUAL_PRIORITY') OR EXISTS (SELECT 1 FROM market_listings l WHERE l.game_id=j.game_id AND COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) IN (SELECT signature FROM observations WHERE observed_at>=? AND observed_at<=?)))";
        String sql="SELECT MIN(j.next_attempt_at) FROM processing_jobs j JOIN games g ON g.id=j.game_id " +
                "WHERE j.job_type=? AND j.state IN (?,?) AND g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND g.database_visible=1"+historyExtra+runExtra;
        java.util.ArrayList<String> a=new java.util.ArrayList<>();a.add(JOB_BGG);a.add(PENDING);a.add(FAILED_RETRYABLE);if(!allowHistory)a.add(HISTORICAL_SOURCE);if(run!=null){a.add(String.valueOf(run.startAt));a.add(String.valueOf(run.endAt));}
        try(Cursor c=db.rawQuery(sql,a.toArray(new String[0]))){if(c.moveToFirst()&&!c.isNull(0))return c.getLong(0);}return 0L;
    }

    public int completedSince(long since) {
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM processing_jobs WHERE state=? AND updated_at>=? AND (last_error IS NULL OR last_error='' OR (last_error NOT LIKE 'historical sighting preserved%' AND last_error NOT LIKE 'not runnable anymore%'))",new String[]{COMPLETE,String.valueOf(Math.max(0L,since))})){return c.moveToFirst()?c.getInt(0):0;}
    }

    public JobSummary jobSummary() {
        JobSummary s=new JobSummary();
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT state,COUNT(*) FROM processing_jobs GROUP BY state",null)){while(c.moveToNext()){String state=c.getString(0);int n=c.getInt(1);if(PENDING.equals(state))s.pending=n;else if(PROCESSING.equals(state))s.processing=n;else if(FAILED_RETRYABLE.equals(state))s.retryable=n;else if(FAILED_PERMANENT.equals(state))s.permanent=n;else if(COMPLETE.equals(state))s.complete=n;}}
        return s;
    }

    public int incompleteListingCount() {
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM market_listings WHERE lifecycle='ACTIVE' AND (enrichment_state IN ('PENDING_ANALYSIS','PENDING_ENRICHMENT','FAILED_RETRYABLE') OR ((vinted_url IS NOT NULL AND vinted_url<>'') AND ((published_label IS NULL OR published_label='') OR (seller_id IS NULL OR seller_id=''))))",null)){return c.moveToFirst()?c.getInt(0):0;}
    }

    /** On-demand, single-pass funnel for the exact catalog eligibility gates. These are
     * state counts, not publication promises: multiple blockers can overlap. */
    public String catalogPipelineFunnel() {
        // 24h buckets use first_seen, so repeated observations of one fingerprint do not
        // inflate intake. These stage counts intentionally overlap where a listing can be
        // qualified but still await an exact Vinted identity or user review.
        String trusted="g.id IS NOT NULL AND g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND COALESCE(g.match_state,'')='MATCHED'";
        String qualified=trusted+" AND g.rating>=6.0 AND g.database_visible=1";
        String exact="l.vinted_item_id IS NOT NULL AND l.vinted_item_id<>'' AND l.vinted_url IS NOT NULL AND l.vinted_url<>''";
        String core=qualified+" AND "+exact+" AND l.enrichment_state IN ('COMPLETE','CORE_COMPLETE') AND COALESCE(l.manual_review_required,0)=0";
        String fresh="l.first_seen>="+(System.currentTimeMillis()-24L*60L*60_000L);
        String sql="SELECT COUNT(*),"+
            "SUM(CASE WHEN l.enrichment_state='PENDING_ANALYSIS' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN "+qualified+" THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN l.enrichment_state='LOCAL_ONLY' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN l.enrichment_state='DEFERRED_LINK' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN "+exact+" THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN "+core+" THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN "+fresh+" THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN "+fresh+" AND l.enrichment_state='PENDING_ANALYSIS' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN "+fresh+" AND l.enrichment_state='BLOCKED_CLASSIFIER' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN "+fresh+" AND COALESCE(g.match_state,l.match_state,'')='TYPE_UNVERIFIED' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN "+fresh+" AND COALESCE(g.match_state,l.match_state,'')='BGG_MATCH_REVIEW' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN "+fresh+" AND COALESCE(l.enrichment_state,'') NOT IN ('PENDING_ANALYSIS','BLOCKED_CLASSIFIER') AND COALESCE(g.match_state,l.match_state,'') NOT IN ('TYPE_UNVERIFIED','BGG_MATCH_REVIEW') AND NOT("+trusted+") THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN "+fresh+" AND "+trusted+" AND g.rating IS NULL THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN "+fresh+" AND "+trusted+" AND g.rating<6.0 THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN "+fresh+" AND "+trusted+" AND g.rating>=6.0 AND g.database_visible=0 THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN "+fresh+" AND "+qualified+" THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN "+fresh+" AND "+qualified+" AND NOT("+exact+") THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN "+fresh+" AND "+exact+" THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN "+fresh+" AND COALESCE(l.manual_review_required,0)=1 THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN "+fresh+" AND l.enrichment_state='LOCAL_ONLY' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN "+fresh+" AND l.enrichment_state='DEFERRED_LINK' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN "+fresh+" AND "+core+" THEN 1 ELSE 0 END)"+
            " FROM market_listings l LEFT JOIN games g ON g.id=l.game_id WHERE l.lifecycle='ACTIVE'";
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,null)){
            if(!c.moveToFirst())return "state=EMPTY";
            return "active="+c.getInt(0)+";pendingAnalysis="+c.getInt(1)+";bggQualified="+c.getInt(2)+
                ";localOnly="+c.getInt(3)+";deferredLink="+c.getInt(4)+";exactVintedLink="+c.getInt(5)+
                ";coreQualified="+c.getInt(6)+";firstSeenListings24h="+c.getInt(7)+
                ";firstSeenPendingAnalysis24h="+c.getInt(8)+";firstSeenClassifierBlocked24h="+c.getInt(9)+
                ";firstSeenProductTypeUnverified24h="+c.getInt(10)+";firstSeenBggMatchReview24h="+c.getInt(11)+
                ";firstSeenBggUnmatched24h="+c.getInt(12)+";firstSeenBggRatingPending24h="+c.getInt(13)+
                ";firstSeenBggBelow6_24h="+c.getInt(14)+";firstSeenBggHidden24h="+c.getInt(15)+
                ";firstSeenBggQualified24h="+c.getInt(16)+";firstSeenVintedLinkPending24h="+c.getInt(17)+
                ";firstSeenExactVintedLink24h="+c.getInt(18)+";firstSeenManualReview24h="+c.getInt(19)+
                ";firstSeenLocalOnly24h="+c.getInt(20)+";firstSeenDeferredLink24h="+c.getInt(21)+
                ";firstSeenCoreQualified24h="+c.getInt(22);
        }catch(Throwable t){return "state=ERROR;type="+t.getClass().getSimpleName();}
    }

    public int pendingAnalysisCount() {
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM market_listings WHERE lifecycle='ACTIVE' AND enrichment_state='PENDING_ANALYSIS'",null)){return c.moveToFirst()?c.getInt(0):0;}
    }

    public List<Job> recentJobs(int limit) {
        List<Job> out=new ArrayList<>();
        String sql="SELECT j.id,j.job_key,j.job_type,j.listing_id,j.game_id,j.state,j.attempt,j.next_attempt_at,j.last_error,j.priority,j.source,"+
                "COALESCE(l.vinted_title,g.canonical_name,'') AS label FROM processing_jobs j LEFT JOIN market_listings l ON l.id=j.listing_id LEFT JOIN games g ON g.id=j.game_id "+
                "ORDER BY CASE j.state WHEN 'PROCESSING' THEN 0 WHEN 'FAILED_RETRYABLE' THEN 1 WHEN 'PENDING' THEN 2 WHEN 'FAILED_PERMANENT' THEN 3 ELSE 4 END,j.updated_at DESC LIMIT ?";
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{String.valueOf(Math.max(1,limit))})){while(c.moveToNext())out.add(readJob(c));}
        return out;
    }

    /** Persist the strongest Vinted candidates so ambiguous jobs can be fixed by a human without
     * spending another public request. queue_controls is intentionally used as a small durable KV store. */
    public void saveVintedCandidates(long listingId,List<VintedLinkResolver.CandidateOption> candidates){
        if(listingId<=0)return;JSONArray a=new JSONArray();if(candidates!=null)for(VintedLinkResolver.CandidateOption c:candidates){if(c==null||TextUtils.isEmpty(c.id))continue;try{JSONObject o=new JSONObject();o.put("id",c.id);o.put("title",c.title==null?"":c.title);o.put("url",c.url==null?"":c.url);o.put("image",c.imageUrl==null?"":c.imageUrl);o.put("sellerId",c.sellerId==null?"":c.sellerId);o.put("seller",c.sellerName==null?"":c.sellerName);o.put("score",c.score);if(!Double.isNaN(c.price))o.put("price",c.price);if(!Double.isNaN(c.photoSimilarity))o.put("photoSimilarity",c.photoSimilarity);o.put("sold",c.sold);a.put(o);}catch(Exception ignored){}}
        ContentValues v=new ContentValues();v.put("name","vinted_candidates:"+listingId);v.put("value",a.length());v.put("updated_at",System.currentTimeMillis());v.put("text_value",a.toString());helper.getWritableDatabase().insertWithOnConflict("queue_controls",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }

    public List<VintedLinkResolver.CandidateOption> vintedCandidates(long listingId){
        List<VintedLinkResolver.CandidateOption> out=new ArrayList<>();if(listingId<=0)return out;try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT text_value FROM queue_controls WHERE name=?",new String[]{"vinted_candidates:"+listingId})){if(!c.moveToFirst()||TextUtils.isEmpty(c.getString(0)))return out;JSONArray a=new JSONArray(c.getString(0));for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;VintedLinkResolver.CandidateOption x=new VintedLinkResolver.CandidateOption();x.id=o.optString("id","");x.title=o.optString("title","");x.url=o.optString("url","");x.imageUrl=o.optString("image","");x.sellerId=o.optString("sellerId","");x.sellerName=o.optString("seller","");x.score=o.optInt("score",0);x.price=o.has("price")?o.optDouble("price",Double.NaN):Double.NaN;x.photoSimilarity=o.has("photoSimilarity")?o.optDouble("photoSimilarity",Double.NaN):Double.NaN;x.sold=o.optBoolean("sold",false);if(!TextUtils.isEmpty(x.id))out.add(x);}}catch(Exception ignored){}return out;
    }

    public void clearVintedCandidates(long listingId){if(listingId>0)helper.getWritableDatabase().delete("queue_controls","name=?",new String[]{"vinted_candidates:"+listingId});}

    /** If Vinted returned multiple listings that are functionally equivalent for this observation
     * (same normalized title + exact observed price), manual identity selection adds little value.
     * Prefer an item id not already represented locally; if all are already known, merge into the
     * best-ranked candidate rather than creating another fact-check. */
    public VintedLinkResolver.CandidateOption equivalentVintedCandidate(long listingId){
        MarketListingRecord l=listing(listingId);if(l==null||l.currentPriceCents<=0)return null;List<VintedLinkResolver.CandidateOption> all=vintedCandidates(listingId);if(all.size()<2)return null;String observed=normalize(l.title);if(TextUtils.isEmpty(observed))return null;List<VintedLinkResolver.CandidateOption> group=new ArrayList<>();String canonical=null;
        for(VintedLinkResolver.CandidateOption c:all){if(c==null||TextUtils.isEmpty(c.id)||Double.isNaN(c.price)||c.score<90)continue;int cents=(int)Math.round(c.price*100.0);if(Math.abs(cents-l.currentPriceCents)>1)continue;String n=normalize(c.title);if(TextUtils.isEmpty(n))continue;if(canonical==null){if(!n.equals(observed))continue;canonical=n;}if(canonical.equals(n))group.add(c);}
        if(group.size()<2)return null;SQLiteDatabase db=helper.getReadableDatabase();for(VintedLinkResolver.CandidateOption c:group){try(Cursor cur=db.rawQuery("SELECT COUNT(*) FROM market_listings WHERE vinted_item_id=? AND id<>?",new String[]{c.id,String.valueOf(listingId)})){if(cur.moveToFirst()&&cur.getInt(0)==0)return c;}}return group.get(0);
    }

    /** User-supplied seller name becomes a matching hint on the next retry. */
    public String setSellerHint(long listingId,String sellerName){if(listingId<=0)return null;String clean=sellerName==null?"":sellerName.trim();ContentValues v=new ContentValues();if(clean.isEmpty())v.putNull("seller_name");else v.put("seller_name",clean);helper.getWritableDatabase().update("market_listings",v,"id=?",new String[]{String.valueOf(listingId)});try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT COALESCE(legacy_signature,temp_fingerprint) FROM market_listings WHERE id=?",new String[]{String.valueOf(listingId)})){return c.moveToFirst()?c.getString(0):null;}}

    /** Apply an already-trusted Vinted identity (human-confirmed or conservative local batch).
     * It closes the current core job; callers may separately schedule low-priority completeness work.
     * Duplicate Vinted ids are canonicalised through the same merge path. */
    public long applyTrustedVintedLink(long jobId,long listingId,String url,String itemId,String sellerName,String imageUrl){
        if(listingId<=0||TextUtils.isEmpty(itemId)||TextUtils.isEmpty(url))return -1L;SQLiteDatabase db=helper.getWritableDatabase();long now=System.currentTimeMillis();long canonicalId=listingId;db.beginTransaction();try{
            Long target=scalarLong(db,"SELECT id FROM market_listings WHERE vinted_item_id=? AND id<>?",new String[]{itemId,String.valueOf(listingId)});if(target!=null){mergeListings(db,listingId,target);canonicalId=target;}
            ContentValues l=new ContentValues();l.put("vinted_item_id",itemId);l.put("vinted_url",url);if(!TextUtils.isEmpty(sellerName))l.put("seller_name",sellerName.trim());if(!TextUtils.isEmpty(imageUrl))l.put("image_url",imageUrl);
            // A human link resolves identity, but the card deliberately returns to Motore for one
            // exact-page metadata pass before it is considered fully recovered.
            l.put("enrichment_state","CORE_COMPLETE");l.put("enriched_at",now);l.put("last_error","");l.put("manual_review_required",0);l.putNull("manual_review_reason");
            db.update("market_listings",l,"id=?",new String[]{String.valueOf(canonicalId)});
            ContentValues j=new ContentValues();j.put("state",COMPLETE);j.put("next_attempt_at",0);j.put("updated_at",now);j.put("last_error","");j.put("progress",100);j.put("processing_started_at",0);
            if(jobId>0)db.update("processing_jobs",j,"id=?",new String[]{String.valueOf(jobId)});
            db.update("processing_jobs",j,"listing_id=? AND job_type=? AND state IN (?,?,?,?)",new String[]{String.valueOf(canonicalId),JOB_VINTED,PENDING,PROCESSING,FAILED_RETRYABLE,FAILED_PERMANENT});
            db.delete("queue_controls","name=?",new String[]{"vinted_candidates:"+listingId});db.delete("queue_controls","name=?",new String[]{"vinted_candidates:"+canonicalId});db.delete("queue_controls","name=? AND value IN (?,?)",new String[]{MANUAL_VINTED_RECOVERY,String.valueOf(listingId),String.valueOf(canonicalId)});
            // Re-open only the final exact-page pass. It is high priority because the user just
            // supplied the missing identity, but it still respects the same paced public Vinted lane.
            enqueueListingJob(db,canonicalId,JOB_VINTED_DEEP,now,260,MANUAL_RECOVERY_SOURCE);
            db.execSQL("UPDATE processing_jobs SET attempt=0 WHERE job_key=?",new Object[]{"vinted-deep:"+canonicalId});
            Long gameId=scalarLong(db,"SELECT game_id FROM market_listings WHERE id=?",new String[]{String.valueOf(canonicalId)});
            if(gameId!=null&&bggRefreshDue(db,gameId,now))enqueueJob(db,"bgg:"+gameId,JOB_BGG,null,gameId,now,250,MANUAL_RECOVERY_SOURCE);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        helper.invalidateActiveObservationSessionCache();QueueKeepAliveService.ensureRunning(context);QueueWorkScheduler.schedule(context);notifyQueueChanged();return canonicalId;
    }

    /** Human confirmation wrapper retained for existing manual flows. */
    public long applyManualVintedLink(long jobId,long listingId,String url,String itemId,String sellerName,String imageUrl){
        return applyTrustedVintedLink(jobId,listingId,url,itemId,sellerName,imageUrl);
    }

    public void saveBggMarketStats(String bggId,Integer typicalCents,Integer minCents,int count,long updatedAt){if(TextUtils.isEmpty(bggId)||count<=0||typicalCents==null||typicalCents<=0)return;SQLiteDatabase db=helper.getWritableDatabase();saveBggMarketStats(db,bggId,typicalCents,minCents,count,updatedAt);}
    private static void saveBggMarketStats(SQLiteDatabase db,String bggId,Integer typicalCents,Integer minCents,int count,long updatedAt){try{JSONObject o=new JSONObject();o.put("count",count);o.put("typical",typicalCents==null?JSONObject.NULL:typicalCents);o.put("min",minCents==null?JSONObject.NULL:minCents);ContentValues v=new ContentValues();v.put("name","bgg_market:"+bggId);v.put("value",count);v.put("updated_at",updatedAt);v.put("text_value",o.toString());db.insertWithOnConflict("queue_controls",null,v,SQLiteDatabase.CONFLICT_REPLACE);}catch(Exception ignored){}}
    public BggMarketStats bggMarketStats(String bggId){if(TextUtils.isEmpty(bggId))return new BggMarketStats(0,null,null,0);try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT value,updated_at,text_value FROM queue_controls WHERE name=?",new String[]{"bgg_market:"+bggId})){if(!c.moveToFirst())return new BggMarketStats(0,null,null,0);int count=c.getInt(0);long at=c.getLong(1);String raw=c.getString(2);JSONObject o=TextUtils.isEmpty(raw)?new JSONObject():new JSONObject(raw);Integer typical=o.isNull("typical")?null:o.optInt("typical",0);Integer min=o.isNull("min")?null:o.optInt("min",0);return new BggMarketStats(count,typical!=null&&typical>0?typical:null,min!=null&&min>0?min:null,at); }catch(Exception ignored){return new BggMarketStats(0,null,null,0);}}

    /** Comparable Vinted asking-price evidence. The median is "typical"; Q25/Q75 describe the
     * market band. Small local samples inform the benchmark but do not replace BGG outright. */
    public MarketReferenceStats localVintedReferenceStats(String bggId,String excludeSignature,String languageCode){
        if(TextUtils.isEmpty(bggId))return new MarketReferenceStats(0,null,null,null,null);String lc=languageCode==null?"":languageCode.trim().toUpperCase(Locale.ROOT);
        StringBuilder where=new StringBuilder(" FROM market_listings l JOIN games g ON g.id=l.game_id WHERE g.bgg_id=? AND l.lifecycle='ACTIVE' AND l.match_state='MATCHED' AND l.current_price_cents>0");
        ArrayList<String> args=new ArrayList<>();args.add(bggId);
        if(!TextUtils.isEmpty(excludeSignature)){where.append(" AND COALESCE(l.legacy_signature,l.temp_fingerprint)<>?");args.add(excludeSignature);}
        if(lc.contains("DEP")){where.append(" AND UPPER(COALESCE(l.language_code,'')) LIKE 'IT%'");}
        else if(!lc.contains("IND")){String code=lc.contains("|")?lc.substring(0,lc.indexOf('|')):lc;if(code.matches("IT|EN|FR|DE|ES|NL|PT")){where.append(" AND UPPER(COALESCE(l.language_code,'')) LIKE ?");args.add(code+"%");}}
        SQLiteDatabase db=helper.getReadableDatabase();int n=0,min=0;try(Cursor cursor=db.rawQuery("SELECT COUNT(*),MIN(l.current_price_cents)"+where,args.toArray(new String[0]))){if(cursor.moveToFirst()){n=cursor.getInt(0);if(!cursor.isNull(1))min=cursor.getInt(1);}}
        if(n==0)return new MarketReferenceStats(0,null,null,null,null);
        int offset=(n-1)/2;Integer median=null;try(Cursor cursor=db.rawQuery("SELECT l.current_price_cents"+where+" ORDER BY l.current_price_cents LIMIT "+(n%2==0?"2":"1")+" OFFSET "+offset,args.toArray(new String[0]))){if(cursor.moveToFirst()){int a=cursor.getInt(0);if(n%2==0&&cursor.moveToNext())median=(a+cursor.getInt(0))/2;else median=a;}}
        int q25Offset=(int)Math.floor((n-1)*.25),q75Offset=(int)Math.ceil((n-1)*.75);Integer q25=null,q75=null;
        try(Cursor cursor=db.rawQuery("SELECT l.current_price_cents"+where+" ORDER BY l.current_price_cents LIMIT 1 OFFSET "+q25Offset,args.toArray(new String[0]))){if(cursor.moveToFirst())q25=cursor.getInt(0);}
        try(Cursor cursor=db.rawQuery("SELECT l.current_price_cents"+where+" ORDER BY l.current_price_cents LIMIT 1 OFFSET "+q75Offset,args.toArray(new String[0]))){if(cursor.moveToFirst())q75=cursor.getInt(0);}
        return new MarketReferenceStats(n,median,q25,q75,min>0?min:null);
    }

    /** Safe mode: local Vinted asks are retained as market history only. Until identity cleanup is
     * trustworthy, they must never replace or blend the BGG used-price reference used for decisions. */
    static Integer resolveVintedReference(MarketReferenceStats s,Integer priorCents){
        return priorCents!=null&&priorCents>0?priorCents:null;
    }

    /** Compatibility accessors deliberately return no autonomous local benchmark in safe mode. */
    public Integer localVintedReferenceCents(String bggId,String excludeSignature,String languageCode){return null;}
    public Integer localVintedReferenceCents(String bggId,String excludeSignature,String languageCode,Integer priorCents){return priorCents!=null&&priorCents>0?priorCents:null;}

    public Integer localVintedMinCents(String bggId,String excludeSignature,String languageCode){MarketReferenceStats s=localVintedReferenceStats(bggId,excludeSignature,languageCode);return s.minCents;}

    /** Game-level comparison policy for the Italian scouting workflow. Dependency is a property of
     * the game, while the actual language remains listing-specific. Unknown DEP rows are therefore
     * excluded from the Italian benchmark until a local language hint is available. */
    public String marketLanguagePolicyForGame(long gameId){
        if(gameId<=0)return "";SQLiteDatabase db=helper.getReadableDatabase();
        try(Cursor c=db.rawQuery("SELECT language_code FROM market_listings WHERE game_id=? AND lifecycle='ACTIVE' AND language_code LIKE '%|DEP' LIMIT 1",new String[]{String.valueOf(gameId)})){if(c.moveToFirst())return "IT|DEP";}
        try(Cursor c=db.rawQuery("SELECT language_code FROM market_listings WHERE game_id=? AND lifecycle='ACTIVE' AND language_code LIKE '%|IND' LIMIT 1",new String[]{String.valueOf(gameId)})){if(c.moveToFirst())return "?|IND";}
        return "";
    }

    /** Cheap targeted refresh used while scrolling. Once a game has enough comparable Vinted
     * observations, immediately propagate the median to its existing legacy cards instead of
     * waiting for the six-hour global maintenance pass. */
    public int refreshLocalVintedBenchmarksForBgg(String bggId){
        // Safe mode: keep local observations for charts/audit, never mutate deal decisions from them.
        return 0;
    }

    /** User exclusions must also disappear from local market-price evidence. Restore only revives
     * rows that were hidden by the user; SOLD/REMOVED listings stay historical. */
    public void setLegacyListingUserHidden(String signature,boolean hidden){if(TextUtils.isEmpty(signature))return;SQLiteDatabase db=helper.getWritableDatabase();String title=scalarString(db,"SELECT vinted_title FROM market_listings WHERE temp_fingerprint=? OR legacy_signature=? LIMIT 1",new String[]{signature,signature});ContentValues v=new ContentValues();v.put("lifecycle",hidden?"USER_HIDDEN":"ACTIVE");String where=hidden?"(temp_fingerprint=? OR legacy_signature=?) AND lifecycle='ACTIVE'":"(temp_fingerprint=? OR legacy_signature=?) AND lifecycle='USER_HIDDEN'";db.update("market_listings",v,where,new String[]{signature,signature});if(!TextUtils.isEmpty(title))synchronized(collisionRiskMemo){collisionRiskMemo.remove(normalize(title));}}

    /** User-confirmed non-game: remove the provisional game and all its listings from active product
     * surfaces while preserving local history for diagnostics. */
    public void hideGameAsNonGame(long gameId){if(gameId<=0)return;SQLiteDatabase db=helper.getWritableDatabase();long now=System.currentTimeMillis();db.beginTransaction();try{ContentValues g=new ContentValues();g.put("database_visible",0);g.put("filter_reason","USER_NOT_A_BOARD_GAME");g.put("match_state","NOT_A_GAME");db.update("games",g,"id=?",new String[]{String.valueOf(gameId)});ContentValues l=new ContentValues();l.put("lifecycle","USER_HIDDEN");db.update("market_listings",l,"game_id=? AND lifecycle='ACTIVE'",new String[]{String.valueOf(gameId)});ContentValues j=new ContentValues();j.put("state",COMPLETE);j.put("next_attempt_at",0);j.put("updated_at",now);j.put("progress",100);j.put("processing_started_at",0);j.put("last_error","chiuso dall'utente: non è un gioco da tavolo");db.update("processing_jobs",j,"game_id=? OR listing_id IN (SELECT id FROM market_listings WHERE game_id=?)",new String[]{String.valueOf(gameId),String.valueOf(gameId)});db.setTransactionSuccessful();}finally{db.endTransaction();}synchronized(collisionRiskMemo){collisionRiskMemo.clear();}notifyQueueChanged();}

    public void updateLegacyListingLanguage(String signature,String languageCode){if(TextUtils.isEmpty(signature))return;ContentValues v=new ContentValues();if(TextUtils.isEmpty(languageCode))v.putNull("language_code");else v.put("language_code",languageCode.trim().toUpperCase(Locale.ROOT));helper.getWritableDatabase().update("market_listings",v,"temp_fingerprint=? OR legacy_signature=?",new String[]{signature,signature});}

    /** Rebuild current deal decisions from the freshest used-market evidence. Mature Vinted
     * observations win; small local samples are blended with the BGG/previous prior. Retail/new
     * prices remain context only and never create a deal. */
    public int refreshLegacyDealBenchmarks(BggSearchClient bgg){
        if(bgg==null)return 0;
        List<DealRecord> deals=helper.getDeals("all_with_review",5000);List<String>signatures=new ArrayList<>();List<ContentValues>updates=new ArrayList<>();
        for(DealRecord d:deals){
            if(d==null||TextUtils.isEmpty(d.signature)||TextUtils.isEmpty(d.bggId))continue;
            boolean languageDependent=!TextUtils.isEmpty(d.languageCode)&&d.languageCode.toUpperCase(Locale.ROOT).contains("DEP");
            BggMarketStats live=bggMarketStats(d.bggId);
            Integer prior=(!languageDependent&&live.count>=2&&live.fresh(System.currentTimeMillis())&&live.typicalCents!=null&&live.typicalCents>0)?live.typicalCents:null;
            if(prior==null||prior<=0)prior=bgg.localMarketReferenceCents(d.bggId);
            Integer ref=prior;
            Integer total=effectiveLegacyTotal(d),shipping=d.shippingVerifiedCents!=null?d.shippingVerifiedCents:d.shippingCents;
            Integer newOffer=null;Double newDiscount=null;String newTier=d.tier,newTierLabel=d.tierLabel;boolean filter=false;
            if(ref==null||ref<=0){if(!"verify".equals(d.tier)){newTier="insufficient";newTierLabel="Pochi dati";}}
            else if(total!=null){
                newDiscount=(ref-total)*100.0/ref;
                boolean allowGreat=ref!=null&&ref>0;
                DealEvaluator.Evaluation evaluation=DealEvaluator.evaluate(d.itemPriceCents,total,ref,null,allowGreat,null,null,shipping);
                newOffer=evaluation.suggestedOfferCents;
                if(!"verify".equals(d.tier)){if(evaluation.visible()){newTier=evaluation.storageTier();newTierLabel=evaluation.label;}else{newTier="filtered";newTierLabel="";filter=true;}}
            }
            if(sameInt(d.benchmarkCents,ref)&&sameInt(d.offerCents,newOffer)&&sameDouble(d.discount,newDiscount)&&Objects.equals(d.tier,newTier)&&Objects.equals(d.tierLabel,newTierLabel)&&!filter)continue;
            ContentValues v=new ContentValues();if(ref==null||ref<=0)v.putNull("benchmark_cents");else v.put("benchmark_cents",ref);if(newOffer==null)v.putNull("offer_cents");else v.put("offer_cents",newOffer);if(newDiscount==null)v.putNull("discount");else v.put("discount",newDiscount);if(!Objects.equals(d.tier,newTier))v.put("tier",newTier);if(!Objects.equals(d.tierLabel,newTierLabel))v.put("tier_label",newTierLabel);
            if(filter){v.put("lifecycle","REMOVED");v.put("verification_state","PRICE_FILTERED");v.put("verification_reason","Prezzo automaticamente escluso dopo ricalcolo del mercato");}
            signatures.add(d.signature);updates.add(v);
        }
        SQLiteDatabase db=helper.getWritableDatabase();int changed=0;
        if(!updates.isEmpty()){db.beginTransaction();try{for(int i=0;i<updates.size();i++)changed+=db.update("deals",updates.get(i),"signature=?",new String[]{signatures.get(i)});db.setTransactionSuccessful();}finally{db.endTransaction();}}
        changed+=restorePriceFilteredFromBgg(bgg);
        reclassifyAutomaticVintedJobs();return changed;
    }

    /** Re-evaluate rows previously removed by a possibly contaminated local benchmark. Only
     * authoritative/fixed BGG used-price references may restore them in safe mode. */
    private int restorePriceFilteredFromBgg(BggSearchClient bgg){
        if(bgg==null)return 0;SQLiteDatabase read=helper.getReadableDatabase();
        ArrayList<String> sigs=new ArrayList<>();ArrayList<ContentValues> vals=new ArrayList<>();
        String sql="SELECT d.signature,d.bgg_id,d.item_price_cents,d.protected_price_cents,d.total_cents,d.shipping_verified_cents,d.shipping_cents,d.language_code,"+
                "COALESCE(g.rating,d.rating),COALESCE(g.categories,d.bgg_categories) FROM deals d LEFT JOIN games g ON g.bgg_id=d.bgg_id "+
                "WHERE d.lifecycle='REMOVED' AND d.verification_state='PRICE_FILTERED' AND d.bgg_id IS NOT NULL AND d.bgg_id<>'' LIMIT 2000";
        try(Cursor c=read.rawQuery(sql,null)){
            while(c.moveToNext()){
                String sig=c.getString(0),bggId=c.getString(1),lang=c.getString(7);
                Double rating=c.isNull(8)?null:c.getDouble(8);String categories=c.isNull(9)?"":c.getString(9);
                if(!DealPolicy.scoutEligible(rating,categories))continue;
                boolean languageDependent=!TextUtils.isEmpty(lang)&&lang.toUpperCase(Locale.ROOT).contains("DEP");
                BggMarketStats live=bggMarketStats(bggId);Integer ref=(!languageDependent&&live.count>=2&&live.fresh(System.currentTimeMillis())&&live.typicalCents!=null&&live.typicalCents>0)?live.typicalCents:null;
                if(ref==null||ref<=0)ref=bgg.localMarketReferenceCents(bggId);if(ref==null||ref<=0)continue;
                int item=c.getInt(2);Integer protectedC=c.isNull(3)?null:c.getInt(3),totalC=c.isNull(4)?null:c.getInt(4),shippingVerified=c.isNull(5)?null:c.getInt(5),shippingEstimated=c.isNull(6)?null:c.getInt(6);
                int effective;if(shippingVerified!=null){int base=protectedC!=null?protectedC:item+PurchaseMath.vintedFee(item);effective=base+shippingVerified;}else if(totalC!=null&&totalC>0)effective=totalC;else if(protectedC!=null&&protectedC>0)effective=protectedC;else effective=item;
                Integer shipping=shippingVerified!=null?shippingVerified:shippingEstimated;
                DealEvaluator.Evaluation e=DealEvaluator.evaluate(item,effective,ref,null,true,null,null,shipping);
                if(!e.visible())continue;
                ContentValues v=new ContentValues();v.put("lifecycle","ACTIVE");v.put("tier",e.storageTier());v.put("tier_label",e.label);v.put("benchmark_cents",ref);v.put("discount",(ref-effective)*100.0/ref);
                if(e.suggestedOfferCents==null)v.putNull("offer_cents");else v.put("offer_cents",e.suggestedOfferCents);
                v.put("verification_state","OK");v.put("verification_reason","Ricalcolato in safe mode con riferimento BGG");
                sigs.add(sig);vals.add(v);
            }
        }
        if(vals.isEmpty())return 0;SQLiteDatabase db=helper.getWritableDatabase();int changed=0;db.beginTransaction();try{for(int i=0;i<vals.size();i++)changed+=db.update("deals",vals.get(i),"signature=? AND lifecycle='REMOVED' AND verification_state='PRICE_FILTERED'",new String[]{sigs.get(i)});db.setTransactionSuccessful();}finally{db.endTransaction();}return changed;
    }

    /** If price evidence changes, stale LIVE_DEAL jobs must not keep consuming the scarce network
     * lane. They fall back to deferred linking; truly hot rows stay urgent. */
    public int reclassifyAutomaticVintedJobs(){
        SQLiteDatabase db=helper.getWritableDatabase();long now=System.currentTimeMillis();int changed=0;db.beginTransaction();try{
            ContentValues done=new ContentValues();done.put("state",COMPLETE);done.put("progress",100);done.put("next_attempt_at",0);done.put("updated_at",now);done.put("processing_started_at",0);done.put("last_error","offerta non più urgente dopo ricalcolo mercato");
            changed=db.update("processing_jobs",done,"job_type=? AND source='LIVE_DEAL' AND state IN (?,?,?) AND listing_id IN (SELECT l.id FROM market_listings l LEFT JOIN deals d ON d.signature=l.legacy_signature WHERE d.signature IS NULL OR d.lifecycle<>'ACTIVE' OR d.tier<>'hot' OR d.rating IS NULL OR d.rating<?)",new String[]{JOB_VINTED,PENDING,PROCESSING,FAILED_RETRYABLE,String.valueOf(DealPolicy.MIN_BGG_RATING)});
            ContentValues def=new ContentValues();def.put("enrichment_state","DEFERRED_LINK");def.put("deferred_retry_at",0);
            db.update("market_listings",def,"lifecycle='ACTIVE' AND (vinted_url IS NULL OR vinted_url='') AND game_id IN (SELECT id FROM games WHERE database_visible=1 AND rating>=?) AND NOT EXISTS (SELECT 1 FROM processing_jobs j WHERE j.listing_id=market_listings.id AND j.job_type=? AND j.state IN (?,?,?))",new String[]{String.valueOf(DealPolicy.MIN_BGG_RATING),JOB_VINTED,PENDING,PROCESSING,FAILED_RETRYABLE});
            // Filtered price rows remain useful market observations but never deserve scarce Vinted
            // identity work. Keep the old extreme pre-network gate as a conservative fallback.
            ContentValues local=new ContentValues();local.put("enrichment_state","LOCAL_ONLY");local.put("deferred_retry_at",0);db.update("market_listings",local,"lifecycle='ACTIVE' AND enrichment_state='DEFERRED_LINK' AND (vinted_url IS NULL OR vinted_url='') AND legacy_signature IN (SELECT signature FROM deals WHERE tier='filtered' OR (lifecycle='ACTIVE' AND tier='normal' AND benchmark_cents IS NOT NULL AND benchmark_cents>0 AND item_price_cents>=benchmark_cents*2.0 AND item_price_cents-benchmark_cents>=2500))",null);
            db.update("processing_jobs",done,"job_type=? AND state IN (?,?,?) AND source NOT IN (?,?) AND listing_id IN (SELECT id FROM market_listings WHERE enrichment_state='LOCAL_ONLY')",new String[]{JOB_VINTED,PENDING,PROCESSING,FAILED_RETRYABLE,"MANUAL_PRIORITY","HUNT_PRIORITY"});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}if(changed>0)notifyQueueChanged();return changed;
    }

    private static boolean sameInt(Integer a,Integer b){return Objects.equals(a,b);}
    private static boolean sameDouble(Double a,Double b){if(a==null||b==null)return a==b;return Math.abs(a-b)<0.001;}


    private static Integer effectiveLegacyTotal(DealRecord d){if(d==null)return null;if(d.shippingVerifiedCents!=null){int base=d.protectedPriceCents!=null?d.protectedPriceCents:d.itemPriceCents+PurchaseMath.vintedFee(d.itemPriceCents);return base+d.shippingVerifiedCents;}if(d.totalCents!=null&&d.totalCents>0)return d.totalCents;if(d.protectedPriceCents!=null&&d.protectedPriceCents>0)return d.protectedPriceCents;return d.itemPriceCents>0?d.itemPriceCents:null;}

    /** Small batches of provisional games for local, zero-network BGG identity matching. Old review
     * rows are retried once whenever the matcher algorithm version advances. */
    public List<GameRecord> provisionalGamesForMatching(int limit){List<GameRecord> out=new ArrayList<>();DealDatabase.ObservationSession run=helper.activeObservationSession();String runExtra=run==null?"":" AND EXISTS (SELECT 1 FROM market_listings l WHERE l.game_id=games.id AND COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) IN (SELECT signature FROM observations WHERE observed_at>=? AND observed_at<=?))";String sql="SELECT id,bgg_id,provisional_key,canonical_name,original_name,alternate_names,year,description,thumbnail_url,image_url,min_players,max_players,playtime,min_age,weight,rating,voters,bgg_rank,categories,mechanics,designers,artists,publishers,families,expansions,base_games,bgg_url,match_state,match_confidence,first_seen,last_seen,metadata_updated_at FROM games WHERE database_visible=1 AND (bgg_id IS NULL OR bgg_id='') AND (match_state='BGG_MATCH_REQUIRED' OR (match_state='BGG_MATCH_REVIEW' AND COALESCE(match_algorithm_version,0)<CAST(? AS INTEGER)))"+runExtra+" ORDER BY CASE WHEN match_state='BGG_MATCH_REQUIRED' THEN 0 ELSE 1 END,last_seen DESC LIMIT ?";java.util.ArrayList<String>a=new java.util.ArrayList<>();a.add(String.valueOf(BGG_MATCH_ALGORITHM_VERSION));if(run!=null){a.add(String.valueOf(run.startAt));a.add(String.valueOf(run.endAt));}a.add(String.valueOf(Math.max(1,limit)));try(Cursor c=helper.getReadableDatabase().rawQuery(sql,a.toArray(new String[0]))){while(c.moveToNext())out.add(readGameBase(c));}return out;}

    /** A title already confirmed by the user/app becomes local knowledge. Only a unique BGG id is
     * returned; collisions remain reviewable rather than being guessed. */
    /** Returns old automatic matches that have not yet passed the v1 historical audit.
     * Explicit MANUAL_BGG / USER_CONFIRMED identities are never touched by automatic revalidation. */
    public List<HistoricalBggCandidate> historicalBggRevalidationCandidates(int limit){
        List<HistoricalBggCandidate> out=new ArrayList<>();SQLiteDatabase db=helper.getReadableDatabase();
        String sql="SELECT g.id,g.bgg_id,g.canonical_name FROM games g WHERE g.database_visible=1 AND g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND g.match_state='MATCHED' AND COALESCE(g.match_algorithm_version,0)<CAST(? AS INTEGER) "+
                "AND NOT EXISTS(SELECT 1 FROM game_aliases a WHERE a.game_id=g.id AND a.source='MANUAL_BGG') "+
                "AND NOT EXISTS(SELECT 1 FROM market_listings l LEFT JOIN deals d ON d.signature=COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) WHERE l.game_id=g.id AND d.verification_state='USER_CONFIRMED') "+
                "AND EXISTS(SELECT 1 FROM market_listings l WHERE l.game_id=g.id AND l.lifecycle='ACTIVE') "+
                "AND NOT EXISTS(SELECT 1 FROM queue_controls q WHERE q.name='"+BGG_REVALIDATION_PREFIX+"'||g.id) ORDER BY g.last_seen DESC LIMIT ?";
        try(Cursor c=db.rawQuery(sql,new String[]{String.valueOf(BGG_MATCH_ALGORITHM_VERSION),String.valueOf(Math.max(1,limit))})){
            while(c.moveToNext()){
                HistoricalBggCandidate g=new HistoricalBggCandidate();g.gameId=c.getLong(0);g.bggId=safe(c.getString(1));g.canonicalName=safe(c.getString(2));
                try(Cursor l=db.rawQuery("SELECT id,COALESCE(vinted_title,''),COALESCE(brand,''),COALESCE(item_condition,''),current_price_cents,COALESCE(observed_text,''),COALESCE(NULLIF(legacy_signature,''),temp_fingerprint) FROM market_listings WHERE game_id=? AND lifecycle='ACTIVE' ORDER BY last_seen DESC",new String[]{String.valueOf(g.gameId)})){
                    while(l.moveToNext()){HistoricalBggListing x=new HistoricalBggListing();x.id=l.getLong(0);x.title=safe(l.getString(1));x.brand=safe(l.getString(2));x.condition=safe(l.getString(3));x.priceCents=l.getInt(4);x.observedText=safe(l.getString(5));x.signature=safe(l.getString(6));g.listings.add(x);}
                }
                if(!g.listings.isEmpty())out.add(g);
            }
        }
        return out;
    }

    /** Historical audit is deliberately non-destructive: the old BGG id stays visible for review,
     * while the listing is removed from the ready/deal path until a human or later authoritative
     * resolver confirms it. */
    public void flagHistoricalBggReview(long listingId,String reason){
        if(listingId<=0)return;SQLiteDatabase db=helper.getWritableDatabase();String why=safe(reason);
        // Historical identity debt is useful for trust gating, but it is not a current user task.
        // Keep it out of the Motore review inbox while preserving the uncertainty on the deal.
        ContentValues l=new ContentValues();l.put("manual_review_required",0);l.putNull("manual_review_reason");l.put("last_error",why);db.update("market_listings",l,"id=?",new String[]{String.valueOf(listingId)});
        String sig=scalarString(db,"SELECT COALESCE(NULLIF(legacy_signature,''),temp_fingerprint) FROM market_listings WHERE id=?",new String[]{String.valueOf(listingId)});
        if(!TextUtils.isEmpty(sig)){ContentValues d=new ContentValues();d.put("verification_state","MATCH_UNCERTAIN");d.put("verification_reason",why);db.update("deals",d,"signature=?",new String[]{sig});}
    }

    public void completeHistoricalBggRevalidation(long gameId,String state,String detail,boolean independentlyVerified){
        completeHistoricalBggRevalidation(gameId,state,detail,independentlyVerified,true);
    }

    /** Bulk historical audit can suppress per-game broadcasts and emit one coalesced update after
     * the slice. This avoids turning a faster local cleanup into an OperationCenter/UI rebuild storm. */
    public void completeHistoricalBggRevalidation(long gameId,String state,String detail,boolean independentlyVerified,boolean notify){
        if(gameId<=0)return;SQLiteDatabase db=helper.getWritableDatabase();long now=System.currentTimeMillis();
        db.beginTransaction();try{
            ContentValues q=new ContentValues();q.put("name",BGG_REVALIDATION_PREFIX+gameId);q.put("value","VERIFIED".equals(state)?1:2);q.put("updated_at",now);q.put("text_value",safe(state)+"|"+safe(detail));db.insertWithOnConflict("queue_controls",null,q,SQLiteDatabase.CONFLICT_REPLACE);
            if(independentlyVerified){ContentValues g=new ContentValues();g.put("match_algorithm_version",BGG_MATCH_ALGORITHM_VERSION);db.update("games",g,"id=?",new String[]{String.valueOf(gameId)});}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        if(notify)notifyQueueChanged();
    }

    public void notifyHistoricalBggRevalidationChanged(){notifyQueueChanged();}

    public int historicalBggRevalidationPendingCount(){
        SQLiteDatabase db=helper.getReadableDatabase();
        String sql="SELECT COUNT(*) FROM games g WHERE g.database_visible=1 AND g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND g.match_state='MATCHED' AND COALESCE(g.match_algorithm_version,0)<CAST(? AS INTEGER) "+
                "AND NOT EXISTS(SELECT 1 FROM game_aliases a WHERE a.game_id=g.id AND a.source='MANUAL_BGG') "+
                "AND NOT EXISTS(SELECT 1 FROM market_listings l LEFT JOIN deals d ON d.signature=COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) WHERE l.game_id=g.id AND d.verification_state='USER_CONFIRMED') "+
                "AND EXISTS(SELECT 1 FROM market_listings l WHERE l.game_id=g.id AND l.lifecycle='ACTIVE') "+
                "AND NOT EXISTS(SELECT 1 FROM queue_controls q WHERE q.name='"+BGG_REVALIDATION_PREFIX+"'||g.id)";
        try(Cursor c=db.rawQuery(sql,new String[]{String.valueOf(BGG_MATCH_ALGORITHM_VERSION)})){return c.moveToFirst()?c.getInt(0):0;}catch(Throwable ignored){return 0;}
    }

    public String historicalBggRevalidationSummary(){
        SQLiteDatabase db=helper.getReadableDatabase();long verified=0,review=0,processed=0;int pending=historicalBggRevalidationPendingCount();
        try(Cursor c=db.rawQuery("SELECT COUNT(*),SUM(CASE WHEN value=1 THEN 1 ELSE 0 END),SUM(CASE WHEN value=2 THEN 1 ELSE 0 END) FROM queue_controls WHERE name LIKE '"+BGG_REVALIDATION_PREFIX+"%'",null)){if(c.moveToFirst()){processed=c.getLong(0);verified=c.isNull(1)?0:c.getLong(1);review=c.isNull(2)?0:c.getLong(2);}}catch(Throwable ignored){}
        return "build=bgg-historical-revalidation-v3; processed="+processed+"; verifiedGames="+verified+"; heldGames="+review+"; pending="+pending;
    }

    /** Reuses only authoritative identity evidence. Seller-authored Vinted titles remain useful
     * for search/display, but they can never bootstrap another automatic BGG match by themselves. */
    public String learnedBggIdForTitle(String raw){
        String key=normalize(raw);if(TextUtils.isEmpty(key))return null;
        String sql="SELECT DISTINCT g.bgg_id FROM game_aliases a JOIN games g ON g.id=a.game_id WHERE a.normalized_alias=? AND a.source IN ('BGG_PRIMARY','BGG_ORIGINAL','BGG_ALTERNATE','BGG_ALIAS','AUTO_LOCAL_BGG','MANUAL_BGG') AND g.database_visible=1 AND g.bgg_id IS NOT NULL AND g.bgg_id<>'' UNION SELECT DISTINCT bgg_id FROM games WHERE normalized_name=? AND database_visible=1 AND bgg_id IS NOT NULL AND bgg_id<>'' LIMIT 2";
        String found=null;try(Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{key,key})){while(c.moveToNext()){String id=c.getString(0);if(TextUtils.isEmpty(id))continue;if(found!=null&&!found.equals(id))return null;found=id;}}return found;
    }

    /** Read-only contamination audit. A v3-and-older matched game is intentionally not mutated here:
     * the next task can revalidate it with stronger evidence instead of destructively resetting data. */
    public String bggIdentityTrustSummary(){
        SQLiteDatabase db=helper.getReadableDatabase();long sellerAliases=0,sellerOnlyAliases=0,matchedToRevalidate=0;
        try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM game_aliases a JOIN games g ON g.id=a.game_id WHERE a.source IN ('VINTED','VINTED_VARIANT') AND g.bgg_id IS NOT NULL AND g.bgg_id<>''",null)){if(c.moveToFirst())sellerAliases=c.getLong(0);}catch(Throwable ignored){}
        try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM game_aliases a JOIN games g ON g.id=a.game_id WHERE a.source IN ('VINTED','VINTED_VARIANT') AND g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND a.normalized_alias<>g.normalized_name AND NOT EXISTS(SELECT 1 FROM game_aliases t WHERE t.game_id=a.game_id AND t.normalized_alias=a.normalized_alias AND t.source IN ('BGG_PRIMARY','BGG_ORIGINAL','BGG_ALTERNATE','BGG_ALIAS','AUTO_LOCAL_BGG','MANUAL_BGG'))",null)){if(c.moveToFirst())sellerOnlyAliases=c.getLong(0);}catch(Throwable ignored){}
        matchedToRevalidate=historicalBggRevalidationPendingCount();
        return "build=bgg-provenance-v1; algorithm="+BGG_MATCH_ALGORITHM_VERSION+"; sellerAliases="+sellerAliases+"; sellerOnlyAliases="+sellerOnlyAliases+"; matchedToRevalidate="+matchedToRevalidate;
    }


    /** Product-type evidence from Qwen is allowed to reopen the BGG pipeline, never to set BGG identity. */
    public boolean hasAiCategoryRecoveryEvidence(VintedCard card){
        if(card==null)return false;SQLiteDatabase db=helper.getReadableDatabase();
        Long listingId=listingIdForCard(db,card);if(listingId==null)return false;
        return AiCategoryEvidence.has(db,listingId,card);
    }

    public boolean isStaleAiCategoryAnalysis(VintedCard card){
        if(card==null)return false;SQLiteDatabase db=helper.getReadableDatabase();
        Long listingId=listingIdForCard(db,card);
        return listingId!=null&&AiCategoryEvidence.has(db,listingId)&&!AiCategoryEvidence.has(db,listingId,card);
    }

    /** Hide one unresolved observation before it can become a human BGG review. The raw row is
     * retained for diagnostics, but it no longer participates in prices, queues or the visible DB. */
    public boolean quarantineUnresolvedObservation(VintedCard card,String reason,long now){
        if(card==null)return false;SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();try{
            Long listingId=listingIdForCard(db,card);if(listingId==null){db.setTransactionSuccessful();return false;}
            Long gameId=scalarLong(db,"SELECT game_id FROM market_listings WHERE id=?",new String[]{String.valueOf(listingId)});
            if(gameId!=null){String confirmed=scalarString(db,"SELECT bgg_id FROM games WHERE id=?",new String[]{String.valueOf(gameId)});if(!TextUtils.isEmpty(confirmed)){db.setTransactionSuccessful();return false;}}
            ContentValues l=new ContentValues();l.put("lifecycle","AUTO_FILTERED");l.put("enrichment_state","AUTO_FILTERED");l.put("match_state","AUTO_FILTERED_NON_GAME");l.put("last_error",safe(reason));l.put("last_seen",now);db.update("market_listings",l,"id=?",new String[]{String.valueOf(listingId)});
            ContentValues j=new ContentValues();j.put("state",COMPLETE);j.put("next_attempt_at",0);j.put("updated_at",now);j.put("progress",100);j.put("processing_started_at",0);j.put("last_error","scarto automatico: "+safe(reason));db.update("processing_jobs",j,"listing_id=?",new String[]{String.valueOf(listingId)});
            if(gameId!=null)deleteOrphanProvisional(db,gameId);db.setTransactionSuccessful();return true;
        }finally{db.endTransaction();}
    }

    /** Quarantine an unresolved provisional game. Nothing is physically deleted: this is a reversible
     * safety bucket that removes noise from fact-check counts and price references. */
    /** A title becomes collision-prone after repeated explicit user exclusions. Seeded collisions
     * (currently Watergate) are handled from day one; learned collisions make the guard improve with
     * the user's own corrections without maintaining a giant blacklist. */
    public boolean isCollisionRiskTitle(String title){
        if(BoardGameIntakeGate.seededCollisionTitle(title))return true;
        String n=normalize(title);if(TextUtils.isEmpty(n))return false;
        synchronized(collisionRiskMemo){Boolean known=collisionRiskMemo.get(n);if(known!=null)return known;}
        String sql="SELECT COUNT(*) FROM listing_overrides o JOIN deals d ON d.signature=o.signature " +
                "WHERE o.excluded=1 AND LOWER(TRIM(d.vinted_title))=LOWER(TRIM(?)) AND " +
                "(LOWER(COALESCE(o.reason,'')) LIKE '%non%gioco%' OR LOWER(COALESCE(o.reason,'')) LIKE '%non ludic%')";
        boolean risky=false;try(Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{title==null?"":title})){risky=c.moveToFirst()&&c.getInt(0)>=2;}catch(Throwable ignored){}
        synchronized(collisionRiskMemo){collisionRiskMemo.put(n,risky);}return risky;
    }

    /** One-time cleanup for collision titles already known to have polluted the database. Only
     * automatically-associated rows are quarantined; an explicit user BGG correction is preserved. */
    public int quarantineKnownCollisionListings(){
        SQLiteDatabase db=helper.getWritableDatabase();long now=System.currentTimeMillis();List<String> signatures=new ArrayList<>();
        String preserve="NOT EXISTS (SELECT 1 FROM listing_overrides o WHERE o.signature=COALESCE(NULLIF(market_listings.legacy_signature,''),market_listings.temp_fingerprint) AND o.payload IS NOT NULL AND o.excluded=0)";
        String where="l.lifecycle='ACTIVE' AND LOWER(TRIM(l.vinted_title))='watergate' AND NOT EXISTS (" +
                "SELECT 1 FROM listing_overrides o WHERE o.signature=COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) AND o.payload IS NOT NULL AND o.excluded=0)";
        try(Cursor c=db.rawQuery("SELECT COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) FROM market_listings l WHERE "+where,null)){while(c.moveToNext())if(!TextUtils.isEmpty(c.getString(0)))signatures.add(c.getString(0));}
        if(signatures.isEmpty())return 0;int changed;
        db.beginTransaction();try{
            ContentValues l=new ContentValues();l.put("lifecycle","AUTO_FILTERED");l.put("enrichment_state","AUTO_FILTERED");l.put("match_state","AUTO_FILTERED_COLLISION");l.put("last_error","Scarto automatico: titolo Watergate condiviso con libri/media senza conferma utente");
            changed=db.update("market_listings",l,"lifecycle='ACTIVE' AND LOWER(TRIM(vinted_title))='watergate' AND "+preserve,null);
            ContentValues j=new ContentValues();j.put("state",COMPLETE);j.put("next_attempt_at",0);j.put("updated_at",now);j.put("progress",100);j.put("processing_started_at",0);j.put("last_error","scarto automatico: collisione titolo Watergate");
            db.update("processing_jobs",j,"listing_id IN (SELECT id FROM market_listings WHERE match_state='AUTO_FILTERED_COLLISION')",null);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        for(String sig:signatures){DealRecord d=helper.findBySignature(sig);if(d!=null)helper.exclude(d,"Non è un gioco da tavolo · collisione titolo Watergate");}
        if(changed>0)notifyQueueChanged();return changed;
    }

    /** v5.11.26 repair for the overly broad v5.11.25 Watergate cleanup. Only observations whose
     * saved Vinted thumbnail visually matches the cached BGG cover (or have an explicit board-game
     * cue) are restored automatically; uncertain books/media remain quarantined and do not poison prices. */
    public int repairV51125CollisionCleanup(){
        SQLiteDatabase db=helper.getWritableDatabase();List<Long> restoreIds=new ArrayList<>();List<String> restoreSigs=new ArrayList<>();
        String sql="SELECT l.id,COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint),g.bgg_id,COALESCE(l.observed_text,'') FROM market_listings l JOIN games g ON g.id=l.game_id WHERE l.match_state='AUTO_FILTERED_COLLISION' AND LOWER(TRIM(l.vinted_title))='watergate'";
        try(Cursor c=db.rawQuery(sql,null)){while(c.moveToNext()){long id=c.getLong(0);String sig=c.getString(1),bgg=c.getString(2),raw=c.getString(3);boolean good=BoardGameIntakeGate.hasStrongBoardGameCue(raw);if(!good&&!TextUtils.isEmpty(sig)&&!TextUtils.isEmpty(bgg)){File a=ThumbnailStore.fileFor(context,sig),b=ArtworkStore.bggFile(context,bgg);if(a.exists()&&b.exists()){Bitmap x=BitmapFactory.decodeFile(a.getAbsolutePath()),y=BitmapFactory.decodeFile(b.getAbsolutePath());try{good=x!=null&&y!=null&&VisualCoverMatcher.similarity(x,y)>=0.72;}finally{if(x!=null)x.recycle();if(y!=null)y.recycle();}}}if(good){restoreIds.add(id);restoreSigs.add(sig);}}}
        if(restoreIds.isEmpty())return 0;int changed=0;db.beginTransaction();try{for(int i=0;i<restoreIds.size();i++){long id=restoreIds.get(i);String sig=restoreSigs.get(i);ContentValues l=new ContentValues();l.put("lifecycle","ACTIVE");l.put("match_state","MATCHED");l.put("enrichment_state","DEFERRED_LINK");l.put("deferred_retry_at",0);l.put("last_error","");changed+=db.update("market_listings",l,"id=?",new String[]{String.valueOf(id)});ContentValues o=new ContentValues();o.put("excluded",0);db.update("listing_overrides",o,"signature=? AND reason LIKE '%collisione titolo Watergate%'",new String[]{sig});ContentValues d=new ContentValues();d.put("lifecycle","ACTIVE");db.update("deals",d,"signature=?",new String[]{sig});}db.setTransactionSuccessful();}finally{db.endTransaction();}if(changed>0)notifyQueueChanged();return changed;
    }

    /** Listing-level collision quarantine used after an otherwise valid BGG exact match. The
     * canonical game remains available; only this marketplace observation is removed. */
    public boolean quarantineCollisionObservation(VintedCard card,String reason,long now){
        if(card==null)return false;SQLiteDatabase db=helper.getWritableDatabase();Long listingId=listingIdForCard(db,card);if(listingId==null)return false;
        // A correction explicitly confirmed by the user always outranks the automatic collision gate.
        String sig=scalarString(db,"SELECT COALESCE(NULLIF(legacy_signature,''),temp_fingerprint) FROM market_listings WHERE id=?",new String[]{String.valueOf(listingId)});
        if(!TextUtils.isEmpty(sig)){try(Cursor c=db.rawQuery("SELECT 1 FROM listing_overrides WHERE signature=? AND payload IS NOT NULL AND excluded=0 LIMIT 1",new String[]{sig})){if(c.moveToFirst())return false;}}
        ContentValues l=new ContentValues();l.put("lifecycle","AUTO_FILTERED");l.put("enrichment_state","AUTO_FILTERED");l.put("match_state","AUTO_FILTERED_COLLISION");l.put("last_error",safe(reason));l.put("last_seen",now);
        int changed=db.update("market_listings",l,"id=?",new String[]{String.valueOf(listingId)});
        if(changed>0){ContentValues j=new ContentValues();j.put("state",COMPLETE);j.put("next_attempt_at",0);j.put("updated_at",now);j.put("progress",100);j.put("processing_started_at",0);j.put("last_error","scarto automatico collisione titolo: "+safe(reason));db.update("processing_jobs",j,"listing_id=?",new String[]{String.valueOf(listingId)});notifyQueueChanged();}
        return changed>0;
    }

    public boolean autoQuarantineGame(long gameId,String reason){
        if(gameId<=0)return false;SQLiteDatabase db=helper.getWritableDatabase();long now=System.currentTimeMillis();
        List<String> signatures=new ArrayList<>();List<Long> keepActive=new ArrayList<>(),protectedIds=new ArrayList<>();
        db.beginTransaction();try{
            String confirmed=scalarString(db,"SELECT bgg_id FROM games WHERE id=?",new String[]{String.valueOf(gameId)});
            if(!TextUtils.isEmpty(confirmed)){db.setTransactionSuccessful();return false;}
            String selection="SELECT l.id,COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint),COALESCE(l.manual_review_required,0),"+
                    "CASE WHEN EXISTS(SELECT 1 FROM listing_overrides u WHERE u.signature=COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) OR (u.item_id IS NOT NULL AND u.item_id=l.vinted_item_id)) "+
                    "OR EXISTS(SELECT 1 FROM deals d WHERE d.signature=COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) AND (COALESCE(d.confirmed,0)<>0 OR d.verification_state='USER_CONFIRMED')) THEN 1 ELSE 0 END "+
                    "FROM market_listings l WHERE l.game_id=? AND l.lifecycle='ACTIVE'";
            try(Cursor c=db.rawQuery(selection,new String[]{String.valueOf(gameId)})){
                while(c.moveToNext()){
                    long id=c.getLong(0);
                    if(c.getInt(2)!=0||c.getInt(3)!=0)protectedIds.add(id);
                    else if(AiCategoryEvidence.has(db,id)){keepActive.add(id);protectedIds.add(id);}
                    else if(!TextUtils.isEmpty(c.getString(1)))signatures.add(c.getString(1));
                }
            }
            ContentValues g=new ContentValues();g.put("database_visible",protectedIds.isEmpty()?0:1);
            g.put("filter_reason",protectedIds.isEmpty()?"AUTO_QUARANTINE: "+safe(reason):"Identità BGG da chiarire: "+safe(reason));
            g.put("match_state",protectedIds.isEmpty()?"AUTO_QUARANTINED":"BGG_MATCH_REVIEW");g.put("match_algorithm_version",BGG_MATCH_ALGORITHM_VERSION);
            db.update("games",g,"id=?",new String[]{String.valueOf(gameId)});
            String retained="";for(Long id:protectedIds)retained+=(retained.isEmpty()?"":",")+id;
            ContentValues l=new ContentValues();l.put("lifecycle","AUTO_FILTERED");l.put("enrichment_state","AUTO_FILTERED");l.put("match_state","AUTO_FILTERED_NON_GAME");l.put("last_error",safe(reason));
            db.update("market_listings",l,"game_id=? AND lifecycle='ACTIVE'"+(retained.isEmpty()?"":" AND id NOT IN ("+retained+")"),new String[]{String.valueOf(gameId)});
            for(Long id:keepActive){
                ContentValues pending=new ContentValues();pending.put("match_state","BGG_MATCH_REVIEW");pending.put("enrichment_state","NEEDS_REVIEW");
                pending.put("last_error","Prodotto confermato da AI; identità BGG da chiarire: "+safe(reason));
                db.update("market_listings",pending,"id=? AND lifecycle='ACTIVE'",new String[]{String.valueOf(id)});
            }
            ContentValues j=new ContentValues();j.put("state",COMPLETE);j.put("next_attempt_at",0);j.put("updated_at",now);j.put("progress",100);j.put("processing_started_at",0);
            j.put("last_error",protectedIds.isEmpty()?"scarto automatico: "+safe(reason):"identità BGG da chiarire: "+safe(reason));
            db.update("processing_jobs",j,"game_id=? OR listing_id IN (SELECT id FROM market_listings WHERE game_id=?)",new String[]{String.valueOf(gameId),String.valueOf(gameId)});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        for(String sig:signatures){DealRecord d=helper.findBySignature(sig);if(d!=null)helper.exclude(d,"Scarto automatico: "+safe(reason));}
        notifyQueueChanged();return protectedIds.isEmpty();
    }

    /** v5.11.24 one-time reset requested by the product owner: the old BGG review queue had become
     * a catch-all for non-games. Move those legacy reviews out of the human queue, without deleting.
     *
     * This deliberately runs as one SQLite transaction rather than calling autoQuarantineGame()
     * hundreds/thousands of times on startup. Any row carrying an explicit user BGG override is
     * excluded from the reset, even if the canonical games row is somehow still provisional. */
    public int quarantineLegacyBggReviewBacklog(){
        SQLiteDatabase db=helper.getWritableDatabase();long now=System.currentTimeMillis();
        final String eligible="g.database_visible=1 AND (g.bgg_id IS NULL OR g.bgg_id='') AND g.match_state='BGG_MATCH_REVIEW' " +
                "AND NOT EXISTS (SELECT 1 FROM market_listings mu JOIN listing_overrides ou ON ou.signature=COALESCE(NULLIF(mu.legacy_signature,''),mu.temp_fingerprint) " +
                "WHERE mu.game_id=g.id AND ou.payload IS NOT NULL AND ou.excluded=0)";
        int count=0;try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM games g WHERE "+eligible,null)){if(c.moveToFirst())count=c.getInt(0);}
        if(count<=0)return 0;
        final String reason="Scarto automatico: reset review legacy 5.11.24; nessuna identità BGG confermata";
        final String gameIds="SELECT g.id FROM games g WHERE "+eligible;
        final String listingSigs="SELECT COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) FROM market_listings l WHERE l.game_id IN ("+gameIds+")";
        db.beginTransaction();try{
            // Preserve the old rows but make the exclusion explicit in the legacy override layer too.
            db.execSQL("INSERT OR IGNORE INTO listing_overrides(signature,item_id,payload,excluded,reason) " +
                    "SELECT COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint),l.vinted_item_id,NULL,1,? FROM market_listings l WHERE l.game_id IN ("+gameIds+")",new Object[]{reason});
            db.execSQL("UPDATE listing_overrides SET excluded=1,reason=? WHERE payload IS NULL AND signature IN ("+listingSigs+")",new Object[]{reason});
            db.execSQL("UPDATE deals SET lifecycle='USER_HIDDEN' WHERE signature IN ("+listingSigs+")");

            ContentValues jobs=new ContentValues();jobs.put("state",COMPLETE);jobs.put("next_attempt_at",0);jobs.put("updated_at",now);jobs.put("progress",100);jobs.put("processing_started_at",0);jobs.put("last_error",reason);
            db.update("processing_jobs",jobs,"game_id IN ("+gameIds+") OR listing_id IN (SELECT l.id FROM market_listings l WHERE l.game_id IN ("+gameIds+"))",null);

            ContentValues listings=new ContentValues();listings.put("lifecycle","AUTO_FILTERED");listings.put("enrichment_state","AUTO_FILTERED");listings.put("match_state","AUTO_FILTERED_NON_GAME");listings.put("last_error",reason);
            db.update("market_listings",listings,"game_id IN ("+gameIds+") AND lifecycle='ACTIVE'",null);

            ContentValues games=new ContentValues();games.put("database_visible",0);games.put("filter_reason","AUTO_QUARANTINE: reset review legacy 5.11.24");games.put("match_state","AUTO_QUARANTINED");games.put("match_algorithm_version",BGG_MATCH_ALGORITHM_VERSION);
            db.update("games",games,"id IN ("+gameIds+")",null);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        notifyQueueChanged();return count;
    }

    /** Atomically moves one still-provisional game into persistent review.
     * Returns the number of canonical game rows changed (0/1) so callers can distinguish a review
     * decision from a write that lost a race to a concurrent match/delete. */
    public int markBggMatchReview(long gameId,String reason){
        if(gameId<=0)return 0;SQLiteDatabase db=helper.getWritableDatabase();int changed=0,listingChanged=0,visible=-1;String before="",after="",bgg="";
        db.beginTransaction();try{
            try(Cursor c=db.rawQuery("SELECT COALESCE(match_state,''),COALESCE(bgg_id,''),database_visible FROM games WHERE id=? LIMIT 1",new String[]{String.valueOf(gameId)})){
                if(c.moveToFirst()){before=safe(c.getString(0));bgg=safe(c.getString(1));visible=c.getInt(2);}
            }
            if(!TextUtils.isEmpty(before)&&TextUtils.isEmpty(bgg)){
                ContentValues v=new ContentValues();v.put("match_state","BGG_MATCH_REVIEW");v.put("filter_reason",safe(reason));v.put("match_algorithm_version",BGG_MATCH_ALGORITHM_VERSION);
                changed=db.update("games",v,"id=?",new String[]{String.valueOf(gameId)});
                if(changed>0){
                    ContentValues l=new ContentValues();l.put("match_state","BGG_MATCH_REVIEW");l.put("enrichment_state","NEEDS_REVIEW");l.put("last_error",safe(reason));
                    listingChanged=db.update("market_listings",l,"game_id=? AND lifecycle='ACTIVE'",new String[]{String.valueOf(gameId)});
                }
            }
            after=scalarString(db,"SELECT COALESCE(match_state,'') FROM games WHERE id=?",new String[]{String.valueOf(gameId)});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        setDiagnosticState("bgg_match_review_write",changed,
                "build=bgg-review-write-v1;game="+gameId+";before="+safe(before)+";after="+safe(after)+";bgg="+safe(bgg)+
                        ";visible="+visible+";gameChanged="+changed+";listingChanged="+listingChanged+";reason="+safe(reason));
        if(changed>0)notifyQueueChanged();return changed;
    }
    public int bggMatchReviewCount(){long epoch=engineEpochStart();try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM games WHERE database_visible=1 AND (bgg_id IS NULL OR bgg_id='') AND match_state='BGG_MATCH_REVIEW' AND last_seen>=?",new String[]{String.valueOf(epoch)})){return c.moveToFirst()?c.getInt(0):0;}}
    public String bggMatchRequiredBreakdown(){
        DealDatabase.ObservationSession run=helper.activeObservationSession();String runExtra=run==null?"":" AND EXISTS (SELECT 1 FROM market_listings l WHERE l.game_id=games.id AND COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) IN (SELECT signature FROM observations WHERE observed_at>=? AND observed_at<=?))";
        String sql="SELECT SUM(CASE WHEN match_state='BGG_MATCH_REQUIRED' THEN 1 ELSE 0 END),SUM(CASE WHEN match_state='BGG_MATCH_REVIEW' AND COALESCE(match_algorithm_version,0)<CAST(? AS INTEGER) THEN 1 ELSE 0 END),SUM(CASE WHEN match_state='BGG_MATCH_REVIEW' AND COALESCE(match_algorithm_version,0)>=CAST(? AS INTEGER) THEN 1 ELSE 0 END) FROM games WHERE database_visible=1 AND (bgg_id IS NULL OR bgg_id='')"+runExtra;
        java.util.ArrayList<String>a=new java.util.ArrayList<>();a.add(String.valueOf(BGG_MATCH_ALGORITHM_VERSION));a.add(String.valueOf(BGG_MATCH_ALGORITHM_VERSION));if(run!=null){a.add(String.valueOf(run.startAt));a.add(String.valueOf(run.endAt));}
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,a.toArray(new String[0]))){if(c.moveToFirst())return "build=bgg-match-breakdown-v1;requiredPure="+(c.isNull(0)?0:c.getInt(0))+";reviewLegacy="+(c.isNull(1)?0:c.getInt(1))+";reviewCurrent="+(c.isNull(2)?0:c.getInt(2))+";algorithm="+BGG_MATCH_ALGORITHM_VERSION;}catch(Throwable ignored){}
        return "build=bgg-match-breakdown-v1;requiredPure=-1;reviewLegacy=-1;reviewCurrent=-1;algorithm="+BGG_MATCH_ALGORITHM_VERSION;
    }

    /** Strong, deterministic cleanup for old provisional rows created before the capture filters were
     * tightened. It is intentionally limited to unconfirmed BGG rows: confirmed board games are never
     * deleted by this migration. */
    public int autoHideStrongNonGameReviews(){
        List<Long> ids=new ArrayList<>();String sql="SELECT id FROM games WHERE database_visible=1 AND (bgg_id IS NULL OR bgg_id='') AND match_state IN ('BGG_MATCH_REQUIRED','BGG_MATCH_REVIEW') AND ((' '||LOWER(COALESCE(normalized_name,''))||' ') LIKE '% warhammer %' OR (' '||LOWER(COALESCE(normalized_name,''))||' ') LIKE '% cd %' OR LOWER(COALESCE(canonical_name,'')) LIKE '%protezione acquisti%' OR LOWER(COALESCE(canonical_name,'')) LIKE '%include la protezione%')";
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,null)){while(c.moveToNext())ids.add(c.getLong(0));}
        int hidden=0;
        for(Long id:ids){if(id==null||id<=0)continue;List<String> signatures=new ArrayList<>();try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT COALESCE(NULLIF(legacy_signature,''),temp_fingerprint) FROM market_listings WHERE game_id=?",new String[]{String.valueOf(id)})){while(c.moveToNext())if(!TextUtils.isEmpty(c.getString(0)))signatures.add(c.getString(0));}
            for(String sig:signatures){DealRecord d=helper.findBySignature(sig);if(d!=null)helper.exclude(d,"Filtro automatico: articolo non pertinente (CD/Warhammer/testo UI Vinted)");}
            hideGameAsNonGame(id);hidden++;
        }
        return hidden;
    }

    /** v5.11.27: clean already-matched cross-category pollution such as books or music gear
     * whose title happens to contain a BGG game name. History is preserved but the row leaves all
     * active price/network surfaces. */
    public int autoHideStrongNonGameListings(){SQLiteDatabase db=helper.getWritableDatabase();List<Long> ids=new ArrayList<>();List<String> sigs=new ArrayList<>();try(Cursor c=db.rawQuery("SELECT id,COALESCE(NULLIF(legacy_signature,''),temp_fingerprint),vinted_title,COALESCE(observed_text,''),COALESCE(brand,''),COALESCE(item_condition,''),current_price_cents,protected_price_cents,favorites FROM market_listings WHERE lifecycle='ACTIVE'",null)){while(c.moveToNext()){String title=c.getString(2),raw=c.getString(3);double price=c.getInt(6)/100.0;Double protectedPrice=c.isNull(7)?null:c.getInt(7)/100.0;Integer fav=c.isNull(8)?null:c.getInt(8);VintedCard card=new VintedCard(title,c.getString(4),c.getString(5),price,protectedPrice,fav,new Rect(0,0,1,1),raw);ListingClassifier.Result classified=ListingClassifier.classify(card);boolean noise=classified.type==ListingClassifier.Type.NON_GAME||classified.type==ListingClassifier.Type.ACCESSORY||classified.type==ListingClassifier.Type.COMPONENTS||classified.type==ListingClassifier.Type.EMPTY_BOX;if(noise){ids.add(c.getLong(0));sigs.add(c.getString(1));}}}if(ids.isEmpty())return 0;long now=System.currentTimeMillis();db.beginTransaction();try{for(int i=0;i<ids.size();i++){long id=ids.get(i);String sig=sigs.get(i);ContentValues l=new ContentValues();l.put("lifecycle","AUTO_FILTERED");l.put("enrichment_state","AUTO_FILTERED");l.put("match_state","AUTO_FILTERED_NON_GAME");l.put("last_error","Filtro prodotto: non gioco/accessorio/componente/scatola vuota");db.update("market_listings",l,"id=?",new String[]{String.valueOf(id)});if(!TextUtils.isEmpty(sig)){ContentValues d=new ContentValues();d.put("lifecycle","USER_HIDDEN");db.update("deals",d,"signature=?",new String[]{sig});}ContentValues j=new ContentValues();j.put("state",COMPLETE);j.put("progress",100);j.put("next_attempt_at",0);j.put("updated_at",now);j.put("processing_started_at",0);j.put("last_error","auto-filtered product noise");db.update("processing_jobs",j,"listing_id=?",new String[]{String.valueOf(id)});}db.setTransactionSuccessful();}finally{db.endTransaction();}notifyQueueChanged();return ids.size();}

    /** v5.11.25: the Vinted queue is a scarce live-action lane, not a mirror of the database.
     * Existing automatic backlog is compacted in one transaction: only qualified hot deals remain
     * remote work. Manual jobs are preserved. Nothing is deleted; ordinary observations become
     * LOCAL_ONLY and still contribute to local history/market references. */
    public int compactVintedBacklogToLiveLane(){
        SQLiteDatabase db=helper.getWritableDatabase();long now=System.currentTimeMillis();int compacted=0;db.beginTransaction();try{
            ContentValues done=new ContentValues();done.put("state",COMPLETE);done.put("progress",100);done.put("next_attempt_at",0);done.put("updated_at",now);done.put("processing_started_at",0);done.put("last_error","local-only: remote Vinted non necessario");
            compacted=db.update("processing_jobs",done,"job_type IN (?,?) AND state IN (?,?,?,?) AND source NOT IN (?,?) AND NOT EXISTS (SELECT 1 FROM market_listings l LEFT JOIN deals d ON d.signature=l.legacy_signature WHERE l.id=processing_jobs.listing_id AND d.lifecycle='ACTIVE' AND d.tier='hot' AND d.rating IS NOT NULL AND d.rating>=?)",new String[]{JOB_VINTED,JOB_VINTED_DEEP,PENDING,FAILED_RETRYABLE,PROCESSING,FAILED_PERMANENT,"MANUAL_PRIORITY","HUNT_PRIORITY",String.valueOf(DealPolicy.MIN_BGG_RATING)});
            // Deep metadata never belongs to the automatic live lane.
            compacted+=db.update("processing_jobs",done,"job_type=? AND state IN (?,?,?,?) AND source NOT IN (?,?)",new String[]{JOB_VINTED_DEEP,PENDING,FAILED_RETRYABLE,PROCESSING,FAILED_PERMANENT,"MANUAL_PRIORITY","HUNT_PRIORITY"});
            ContentValues live=new ContentValues();live.put("priority",320);live.put("source","LIVE_DEAL");live.put("next_attempt_at",0);live.put("updated_at",now);
            db.update("processing_jobs",live,"job_type=? AND state IN (?,?) AND listing_id IN (SELECT l.id FROM market_listings l JOIN deals d ON d.signature=l.legacy_signature WHERE d.lifecycle='ACTIVE' AND d.tier='hot' AND d.rating>=?)",new String[]{JOB_VINTED,PENDING,FAILED_RETRYABLE,String.valueOf(DealPolicy.MIN_BGG_RATING)});
            ContentValues deferred=new ContentValues();deferred.put("enrichment_state","DEFERRED_LINK");deferred.put("deferred_retry_at",0);
            db.update("market_listings",deferred,"lifecycle='ACTIVE' AND (vinted_url IS NULL OR vinted_url='') AND game_id IN (SELECT id FROM games WHERE database_visible=1 AND rating>=?) AND NOT EXISTS (SELECT 1 FROM processing_jobs j WHERE j.listing_id=market_listings.id AND j.job_type=? AND j.state IN (?,?,?))",new String[]{String.valueOf(DealPolicy.MIN_BGG_RATING),JOB_VINTED,PENDING,PROCESSING,FAILED_RETRYABLE});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        if(compacted>0)notifyQueueChanged();return compacted;
    }

    /** Explicit Hunts bypass LOCAL_ONLY and enter the live lane at the highest automatic priority. */
    public boolean promoteLegacyListingForHunt(String signature){
        if(TextUtils.isEmpty(signature))return false;SQLiteDatabase db=helper.getWritableDatabase();long now=System.currentTimeMillis();Long id=scalarLong(db,"SELECT id FROM market_listings WHERE legacy_signature=? OR temp_fingerprint=? ORDER BY last_seen DESC LIMIT 1",new String[]{signature,signature});if(id==null)return false;String url=scalarString(db,"SELECT vinted_url FROM market_listings WHERE id=?",new String[]{String.valueOf(id)});if(!TextUtils.isEmpty(url))return true;ContentValues st=new ContentValues();st.put("enrichment_state","PENDING_ENRICHMENT");db.update("market_listings",st,"id=?",new String[]{String.valueOf(id)});enqueueListingJob(db,id,JOB_VINTED,now,340,"HUNT_PRIORITY");notifyQueueChanged();return true;
    }

    /** One-time product turnaround cut-over: automatic review debt created by older builds is
     * archived, not deleted. Explicit Hunt/manual requests are preserved. Fresh sightings can
     * reactivate the same listing under the new stricter three-way pipeline. */
    public int archiveAutomaticReviewDebtBefore(long cutoff){
        SQLiteDatabase db=helper.getWritableDatabase();long now=System.currentTimeMillis();int archived=0;
        db.beginTransaction();try{
            List<Long> ids=new ArrayList<>();
            try(Cursor c=db.rawQuery("SELECT l.id FROM market_listings l WHERE l.lifecycle='ACTIVE' AND COALESCE(l.manual_review_required,0)=1 AND l.last_seen<? AND NOT EXISTS(SELECT 1 FROM processing_jobs j WHERE j.listing_id=l.id AND j.source IN ('HUNT_PRIORITY','MANUAL_PRIORITY'))",new String[]{String.valueOf(cutoff)})){while(c.moveToNext())ids.add(c.getLong(0));}
            for(Long id:ids){
                String sig=scalarString(db,"SELECT legacy_signature FROM market_listings WHERE id=?",new String[]{String.valueOf(id)});
                ContentValues l=new ContentValues();l.put("lifecycle","AUTO_FILTERED");l.put("enrichment_state","AUTO_EXCLUDED");l.put("manual_review_required",0);l.putNull("manual_review_reason");l.put("last_error","Review automatica precedente archiviata dal turnaround UX");archived+=db.update("market_listings",l,"id=?",new String[]{String.valueOf(id)});
                ContentValues j=new ContentValues();j.put("state",COMPLETE);j.put("progress",100);j.put("next_attempt_at",0);j.put("updated_at",now);j.put("processing_started_at",0);j.put("last_error","review automatica archiviata");db.update("processing_jobs",j,"listing_id=? AND COALESCE(source,'AUTO') NOT IN ('HUNT_PRIORITY','MANUAL_PRIORITY')",new String[]{String.valueOf(id)});
                if(!TextUtils.isEmpty(sig)){ContentValues d=new ContentValues();d.put("lifecycle","USER_HIDDEN");d.put("verification_state","EPOCH_ARCHIVED_REVIEW");d.put("verification_reason","Review automatica precedente archiviata");db.update("deals",d,"signature=?",new String[]{sig});}
            }
            ContentValues g=new ContentValues();g.put("match_state","EPOCH_ARCHIVED_REVIEW");g.put("database_visible",0);g.put("filter_reason","Review BGG automatica precedente archiviata dal turnaround UX");
            archived+=db.update("games",g,"database_visible=1 AND (bgg_id IS NULL OR bgg_id='') AND match_state='BGG_MATCH_REVIEW' AND last_seen<?",new String[]{String.valueOf(cutoff)});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        if(archived>0){setDiagnosticState("review_turnaround",archived,"build=review-turnaround-v1;archived="+archived+";cutoff="+cutoff);notifyQueueChanged();}
        return archived;
    }

    public int vintedReviewCount(){long epoch=engineEpochStart();try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM market_listings WHERE lifecycle='ACTIVE' AND COALESCE(manual_review_required,0)=1 AND last_seen>=?",new String[]{String.valueOf(epoch)})){return c.moveToFirst()?c.getInt(0):0;}}
    public String currentReviewBreakdown(){
        long epoch=engineEpochStart();SQLiteDatabase db=helper.getReadableDatabase();
        int vinted=0,explicit=0,variant=0,historicalInbox=0,other=0,bgg=0,bggTechnical=0,bggOther=0,historicalHeld=0;
        String vsql="SELECT COUNT(*),SUM(CASE WHEN EXISTS(SELECT 1 FROM processing_jobs j WHERE j.listing_id=l.id AND j.source IN ('HUNT_PRIORITY','MANUAL_PRIORITY')) THEN 1 ELSE 0 END),SUM(CASE WHEN l.match_state='BGG_VARIANT_REVIEW' THEN 1 ELSE 0 END),SUM(CASE WHEN l.manual_review_reason LIKE 'Rivalidazione BGG storica:%' THEN 1 ELSE 0 END) FROM market_listings l WHERE l.lifecycle='ACTIVE' AND COALESCE(l.manual_review_required,0)=1 AND l.last_seen>=?";
        try(Cursor c=db.rawQuery(vsql,new String[]{String.valueOf(epoch)})){if(c.moveToFirst()){vinted=c.getInt(0);explicit=c.isNull(1)?0:c.getInt(1);variant=c.isNull(2)?0:c.getInt(2);historicalInbox=c.isNull(3)?0:c.getInt(3);other=Math.max(0,vinted-Math.min(vinted,explicit+variant+historicalInbox));}}
        try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM market_listings l LEFT JOIN deals d ON d.signature=COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) WHERE l.lifecycle='ACTIVE' AND COALESCE(l.manual_review_required,0)=0 AND d.verification_state='MATCH_UNCERTAIN' AND d.verification_reason LIKE 'Rivalidazione BGG storica:%' AND l.last_seen>=?",new String[]{String.valueOf(epoch)})){if(c.moveToFirst())historicalHeld=c.getInt(0);}
        String bsql="SELECT COUNT(*),SUM(CASE WHEN filter_reason LIKE 'Ricerca BGG %oltre il budget%' OR filter_reason LIKE 'Errore%match locale:%' THEN 1 ELSE 0 END) FROM games WHERE database_visible=1 AND (bgg_id IS NULL OR bgg_id='') AND match_state='BGG_MATCH_REVIEW' AND last_seen>=?";
        try(Cursor c=db.rawQuery(bsql,new String[]{String.valueOf(epoch)})){if(c.moveToFirst()){bgg=c.getInt(0);bggTechnical=c.isNull(1)?0:c.getInt(1);bggOther=Math.max(0,bgg-bggTechnical);}}
        return "build=review-breakdown-v2;vinted="+vinted+";explicit="+explicit+";variant="+variant+";historicalInbox="+historicalInbox+";historicalHeld="+historicalHeld+";other="+other+";bgg="+bgg+";bggTechnical="+bggTechnical+";bggOther="+bggOther;
    }
    public List<GameRecord> bggMatchReviewGames(int limit){List<GameRecord> out=new ArrayList<>();long epoch=engineEpochStart();String sql="SELECT id,bgg_id,provisional_key,canonical_name,original_name,alternate_names,year,description,thumbnail_url,image_url,min_players,max_players,playtime,min_age,weight,rating,voters,bgg_rank,categories,mechanics,designers,artists,publishers,families,expansions,base_games,bgg_url,match_state,match_confidence,first_seen,last_seen,metadata_updated_at FROM games WHERE database_visible=1 AND (bgg_id IS NULL OR bgg_id='') AND match_state='BGG_MATCH_REVIEW' AND last_seen>=? ORDER BY last_seen DESC LIMIT ?";try(Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{String.valueOf(epoch),String.valueOf(Math.max(1,limit))})){while(c.moveToNext())out.add(readGameBase(c));}return out;}

    /** Durable human-review inbox. A row remains here even if the user opens it or an automatic
     * retry changes the underlying job state. It disappears only after an explicit link/archive. */
    public List<Job> reviewJobs(int limit) {
        List<Job> out=new ArrayList<>();
        String sql="SELECT COALESCE(j.id,0),COALESCE(j.job_key,'review:'||l.id),COALESCE(j.job_type,'VINTED_ENRICHMENT'),l.id,l.game_id,"+
                "COALESCE(j.state,'FAILED_PERMANENT'),COALESCE(j.attempt,0),COALESCE(j.next_attempt_at,0),"+
                "COALESCE(NULLIF(l.manual_review_reason,''),NULLIF(j.last_error,''),l.last_error,'Da verificare'),COALESCE(j.priority,0) AS priority,COALESCE(j.source,'MANUAL_REVIEW') AS source,COALESCE(j.progress,0) AS stored_progress,"+
                "COALESCE(l.vinted_title,g.canonical_name,'') AS label,COALESCE(l.game_id,0) AS display_game_id,g.bgg_id,g.thumbnail_url,g.image_url,COALESCE(j.processing_started_at,0) AS processing_started_at,COALESCE(j.progress,0) AS progress "+
                "FROM market_listings l LEFT JOIN games g ON g.id=l.game_id LEFT JOIN processing_jobs j ON j.id=(SELECT jj.id FROM processing_jobs jj WHERE jj.listing_id=l.id AND jj.job_type=? ORDER BY jj.updated_at DESC,jj.id DESC LIMIT 1) "+
                "WHERE l.lifecycle='ACTIVE' AND COALESCE(l.manual_review_required,0)=1 AND l.last_seen>=? ORDER BY l.last_seen DESC LIMIT ?";
        long epoch=engineEpochStart();try(Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{JOB_VINTED,String.valueOf(epoch),String.valueOf(Math.max(1,limit))})){while(c.moveToNext())out.add(readJob(c));}
        return out;
    }

    /** Reopen a failed/retryable job immediately at explicit user priority. */
    public boolean retryNow(long jobId) {
        long now=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("state",PENDING);v.put("next_attempt_at",0);v.put("priority",280);v.put("source","MANUAL_PRIORITY");v.put("updated_at",now);v.put("last_error","");v.put("progress",6);v.put("processing_started_at",0);
        int changed=helper.getWritableDatabase().update("processing_jobs",v,"id=? AND state IN (?,?)",new String[]{String.valueOf(jobId),FAILED_RETRYABLE,FAILED_PERMANENT});
        if(changed>0){QueueKeepAliveService.ensureRunning(context);QueueWorkScheduler.schedule(context);notifyQueueChanged();}
        return changed>0;
    }

    /** Hide an unresolved listing from the active catalog without deleting its observations/history. */
    public String archiveUnresolvedListing(long listingId,String reason) {
        if(listingId<=0)return null;SQLiteDatabase db=helper.getWritableDatabase();long now=System.currentTimeMillis();String signature=null;db.beginTransaction();
        try{try(Cursor c=db.rawQuery("SELECT legacy_signature,temp_fingerprint FROM market_listings WHERE id=?",new String[]{String.valueOf(listingId)})){if(c.moveToFirst())signature=!TextUtils.isEmpty(c.getString(0))?c.getString(0):c.getString(1);}
            ContentValues l=new ContentValues();l.put("lifecycle","REMOVED");l.put("enrichment_state","REMOVED");l.put("last_error",safe(reason));l.put("last_seen",now);l.put("manual_review_required",0);l.putNull("manual_review_reason");db.update("market_listings",l,"id=?",new String[]{String.valueOf(listingId)});
            ContentValues j=new ContentValues();j.put("state",COMPLETE);j.put("next_attempt_at",0);j.put("updated_at",now);j.put("progress",100);j.put("processing_started_at",0);j.put("last_error","archiviato dall'utente: "+safe(reason));db.update("processing_jobs",j,"listing_id=? AND job_type IN (?,?) AND state IN (?,?,?,?)",new String[]{String.valueOf(listingId),JOB_VINTED,JOB_VINTED_DEEP,PENDING,PROCESSING,FAILED_RETRYABLE,FAILED_PERMANENT});db.delete("queue_controls","name=?",new String[]{"vinted_candidates:"+listingId});db.delete("queue_controls","name=? AND value=?",new String[]{MANUAL_VINTED_RECOVERY,String.valueOf(listingId)});db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        notifyQueueChanged();return signature;
    }

    /** Human-readable identities for the few cards still blocking the current run. */
    public String engineCoreRemainingSummary(){
        DealDatabase.ObservationSession run=helper.activeObservationSession();if(run==null)return"state=NONE";SQLiteDatabase db=helper.getReadableDatabase();StringBuilder out=new StringBuilder();int n=0;
        String sql="SELECT l.id,COALESCE(l.vinted_title,g.canonical_name,''),COALESCE(l.enrichment_state,''),COALESCE(j.attempt,0),COALESCE(j.state,''),COALESCE(j.last_error,''),COALESCE(j.job_type,''),COALESCE(j.source,''),COALESCE(j.next_attempt_at,0) "+
                "FROM market_listings l JOIN games g ON g.id=l.game_id LEFT JOIN deals d ON d.signature=COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) LEFT JOIN processing_jobs j ON j.id=(SELECT jj.id FROM processing_jobs jj WHERE jj.listing_id=l.id AND jj.job_type=? ORDER BY jj.updated_at DESC,jj.id DESC LIMIT 1) "+
                "WHERE l.lifecycle='ACTIVE' AND g.database_visible=1 AND g.rating>=? AND g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND g.match_state='MATCHED' "+
                "AND COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) IN (SELECT signature FROM observations WHERE observed_at>=? AND observed_at<=?) "+
                "AND COALESCE(l.manual_review_required,0)=0 AND l.enrichment_state NOT IN ('NEEDS_REVIEW','LOCAL_ONLY','AUTO_EXCLUDED','AUTO_FILTERED') AND l.match_state<>'BGG_VARIANT_REVIEW' "+
                "AND COALESCE(d.verification_state,'') NOT IN ('BGG_VARIANT_REVIEW','MATCH_UNCERTAIN','PRICE_ANOMALY','EXPANSION_CHECK') "+
                "AND (l.vinted_item_id IS NULL OR l.vinted_item_id='' OR l.vinted_url IS NULL OR l.vinted_url='') ORDER BY l.last_seen ASC LIMIT 8";
        try(Cursor x=db.rawQuery(sql,new String[]{JOB_VINTED,String.valueOf(DealPolicy.MIN_BGG_RATING),String.valueOf(run.startAt),String.valueOf(run.endAt)})){
            while(x.moveToNext()){if(n++>0)out.append(" | ");out.append("#").append(x.getLong(0)).append(" ").append(safe(x.getString(1))).append(" [").append(x.getString(2)).append(";attempt=").append(x.getInt(3)).append(";job=").append(x.getString(4)).append(";type=").append(safe(x.getString(6))).append(";source=").append(safe(x.getString(7))).append(";nextAttemptInMs=").append(Math.max(0L,x.getLong(8)-System.currentTimeMillis()));String err=x.getString(5);if(!TextUtils.isEmpty(err))out.append(";why=").append(safe(err));out.append("]");}
        }
        return "scope=activeRun;unit=listings;limit=8;count="+n+"; "+(out.length()==0?"none":out.toString());
    }

    /** Historical SQLite evidence only; absence is not proof that a reset never ran. */
    public String engineResetEvidence(){
        SQLiteDatabase db=helper.getReadableDatabase();long now=System.currentTimeMillis();StringBuilder out=new StringBuilder("build=reset-evidence-v1;source=sqlite;scope=allStored;atomic=false");
        try(Cursor c=db.rawQuery(RESET_MARKER_EVIDENCE_SQL,null)){
            if(c.moveToFirst())out.append(";resetRecord=PRESENT;recordedAt=").append(c.getLong(1)).append(";mixedOperationCount=").append(c.getLong(0)).append(";summary=").append(safe(c.getString(2)));
            else out.append(";resetRecord=ABSENT;absenceDoesNotExcludeReset=true");
        }catch(Exception e){out.append(";resetRecord=READ_ERROR;markerError=").append(e.getClass().getSimpleName());}
        out.append(";listings={");int groups=0;
        try(Cursor c=db.rawQuery(RESET_LISTING_EVIDENCE_SQL,null)){
            while(c.moveToNext()){if(groups++>0)out.append("|");out.append(safe(c.getString(0))).append(":total=").append(c.getLong(1)).append(",missingUrl=").append(c.getLong(2));}
            if(groups==0)out.append("empty");
        }catch(Exception e){out.append("READ_ERROR:").append(e.getClass().getSimpleName());}
        out.append("};observations={since24h=").append(now-24L*60L*60_000L).append(";until=").append(now);
        try(Cursor c=db.rawQuery(RESET_OBSERVATION_EVIDENCE_SQL,new String[]{String.valueOf(now-24L*60L*60_000L),String.valueOf(now)})){
            if(c.moveToFirst())out.append(";total=").append(c.getLong(0)).append(";oldestAt=").append(c.isNull(1)?"none":String.valueOf(c.getLong(1))).append(";newestAt=").append(c.isNull(2)?"none":String.valueOf(c.getLong(2))).append(";last24h=").append(c.getLong(3));
        }catch(Exception e){out.append(";state=READ_ERROR;error=").append(e.getClass().getSimpleName());}
        return out.append("}").toString();
    }

    /** Current-run local holds are observations, not missing remote jobs. No cause is inferred. */
    public String engineLocalOnlySummary(){
        DealDatabase.ObservationSession run=helper.activeObservationSession();if(run==null)return "state=NONE";
        String sql="SELECT COUNT(*) FROM market_listings l JOIN games g ON g.id=l.game_id WHERE l.lifecycle='ACTIVE' AND l.enrichment_state='LOCAL_ONLY' AND g.database_visible=1 AND g.rating>=? AND g.bgg_id IS NOT NULL AND g.bgg_id<>'' AND g.match_state='MATCHED' AND (l.vinted_item_id IS NULL OR l.vinted_item_id='' OR l.vinted_url IS NULL OR l.vinted_url='') AND COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) IN (SELECT signature FROM observations WHERE observed_at>=? AND observed_at<=?)";
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{String.valueOf(DealPolicy.MIN_BGG_RATING),String.valueOf(run.startAt),String.valueOf(run.endAt)})){return "scope=activeRun;unit=listings;count="+(c.moveToFirst()?c.getInt(0):0);}
    }

    public int missingVintedCoreCount(){try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM market_listings WHERE lifecycle='ACTIVE' AND (vinted_url IS NULL OR vinted_url='')",null)){return c.moveToFirst()?c.getInt(0):0;}}
    public String vintedMissingBreakdown(){
        try(Cursor c=helper.getReadableDatabase().rawQuery(VINTED_MISSING_BREAKDOWN_SQL,null)){
            if(!c.moveToFirst())return"total=0";
            return "total="+c.getInt(0)+"; notBggQualified="+c.getInt(1)+"; eligible="+c.getInt(2)+"; queued="+c.getInt(3)+"; awaitingAttempt="+c.getInt(4)+"; noCandidate="+c.getInt(5)+"; ambiguous="+c.getInt(6)+"; weakMatch="+c.getInt(7)+"; unavailable="+c.getInt(8)+"; throttled="+c.getInt(9)+"; verificationFailed="+c.getInt(10)+"; other="+c.getInt(11)+"; localOnly="+c.getInt(12)+"; scope=globalActiveMissingUrl;unit=listings";
        }
    }
    public String catalogVisibilityBreakdown(){
        try(Cursor c=helper.getReadableDatabase().rawQuery(CATALOG_VISIBILITY_BREAKDOWN_SQL,null)){
            if(!c.moveToFirst())return"state=EMPTY";
            return "marketCoreListings="+c.getInt(0)+"; coreBridgedDeals="+c.getInt(1)+"; coreInCatalog="+c.getInt(2)+
                    "; coreNotCatalog="+c.getInt(3)+"; catalogOutsideCore="+c.getInt(4)+"; catalogBase="+c.getInt(5)+
                    "; dealIdentity="+c.getInt(6)+"; reviewClear="+c.getInt(7)+"; listingMatched="+c.getInt(8)+
                    "; bggAgreement="+c.getInt(9)+"; noBlockingJobs="+c.getInt(10)+"; catalogEligible="+c.getInt(11)+
                    "; "+helper.catalogBridgeBreakdown();
        }catch(Throwable t){return "state=ERROR;type="+t.getClass().getSimpleName();}
    }
    public int materializeCanonicalCatalogBatch(int limit){
        int bounded=Math.max(1,Math.min(24,limit));long now=System.currentTimeMillis();List<Long> ids=new ArrayList<>();
        try(Cursor c=helper.getReadableDatabase().rawQuery(CATALOG_BRIDGE_BACKFILL_SQL,new String[]{String.valueOf(now),String.valueOf(bounded)})){while(c.moveToNext())ids.add(c.getLong(0));}
        int inserted=0;for(Long id:ids)if("MATERIALIZED".equals(helper.materializeCanonicalDeal(id)))inserted++;return inserted;
    }
    /** Cross-process maintenance lease. SQLite is authoritative; SharedPreferences are process-local. */
    public boolean claimCatalogBridgeSweep(long now,long intervalMs){
        SQLiteDatabase db=helper.getWritableDatabase();boolean claimed=false;long next=now+Math.max(10_000L,intervalMs);
        db.beginTransaction();try{
            long current=0L;try(Cursor c=db.rawQuery("SELECT value FROM queue_controls WHERE name='catalog_bridge_sweep_lease_v1'",null)){if(c.moveToFirst())current=c.getLong(0);}
            if(current<=now){ContentValues v=new ContentValues();v.put("name","catalog_bridge_sweep_lease_v1");v.put("value",next);v.put("updated_at",now);v.put("text_value","claimed");db.insertWithOnConflict("queue_controls",null,v,SQLiteDatabase.CONFLICT_REPLACE);claimed=true;}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        return claimed;
    }
    public int partialVintedMetadataCount(){try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM market_listings WHERE lifecycle='ACTIVE' AND vinted_url IS NOT NULL AND vinted_url<>'' AND ((seller_id IS NULL OR seller_id='') OR (published_label IS NULL OR published_label=''))",null)){return c.moveToFirst()?c.getInt(0):0;}}

    public List<Job> activeBggJobs(int limit){List<Job> out=new ArrayList<>();String sql="SELECT j.id,j.job_key,j.job_type,j.listing_id,j.game_id,j.state,j.attempt,j.next_attempt_at,j.last_error,j.priority,j.source,j.progress AS stored_progress,COALESCE(g.canonical_name,'') AS label,COALESCE(j.game_id,0) AS display_game_id,g.bgg_id,g.thumbnail_url,g.image_url,j.processing_started_at,j.progress AS progress FROM processing_jobs j JOIN games g ON g.id=j.game_id WHERE j.job_type=? AND j.state IN (?,?,?) ORDER BY CASE j.state WHEN 'PROCESSING' THEN 0 WHEN 'FAILED_RETRYABLE' THEN 1 ELSE 2 END,j.priority DESC,j.next_attempt_at ASC,j.created_at ASC LIMIT ?";try(Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{JOB_BGG,PENDING,PROCESSING,FAILED_RETRYABLE,String.valueOf(Math.max(1,limit))})){while(c.moveToNext())out.add(readJob(c));}return out;}

    public List<Job> processingJobsOfType(String type,int limit){
        List<Job> out=new ArrayList<>();String sql="SELECT j.id,j.job_key,j.job_type,j.listing_id,j.game_id,j.state,j.attempt,j.next_attempt_at,j.last_error,j.priority,j.source,j.progress AS stored_progress,COALESCE(l.vinted_title,g.canonical_name,'') AS label,COALESCE(j.game_id,l.game_id,0) AS display_game_id,g.bgg_id,g.thumbnail_url,g.image_url,j.processing_started_at,j.progress AS progress FROM processing_jobs j LEFT JOIN market_listings l ON l.id=j.listing_id LEFT JOIN games g ON g.id=COALESCE(j.game_id,l.game_id) WHERE j.job_type=? AND j.state=? ORDER BY j.priority DESC,j.updated_at DESC LIMIT ?";
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{type,PROCESSING,String.valueOf(Math.max(1,limit))})){while(c.moveToNext())out.add(readJob(c));}return out;
    }

    public List<Job> recentCompletedJobs(int limit) {
        List<Job> out=new ArrayList<>();
        String sql="SELECT j.id,j.job_key,j.job_type,j.listing_id,j.game_id,j.state,j.attempt,j.next_attempt_at,j.last_error,j.priority,j.source,j.progress AS stored_progress,"+
                "COALESCE(l.vinted_title,g.canonical_name,'') AS label,COALESCE(j.game_id,l.game_id,0) AS display_game_id,g.bgg_id,g.thumbnail_url,g.image_url,100 AS progress "+
                "FROM processing_jobs j LEFT JOIN market_listings l ON l.id=j.listing_id LEFT JOIN games g ON g.id=COALESCE(j.game_id,l.game_id) "+
                "WHERE j.state=? AND j.job_type<>? AND j.updated_at>=? AND (j.last_error IS NULL OR j.last_error='' OR j.last_error='skipped: BGG rating below 6') "+
                "ORDER BY j.updated_at DESC LIMIT ?";
        long since=System.currentTimeMillis()-24L*60*60_000L;
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{COMPLETE,JOB_VINTED_DEEP,String.valueOf(since),String.valueOf(Math.max(1,limit))})){
            while(c.moveToNext())out.add(readJob(c));
        }
        return out;
    }

    /** User-facing priority queue: fresh sightings and explicit checks only, never the historical backlog. */
    public List<Job> activePriorityJobs(int limit) {
        List<Job> out=new ArrayList<>();
        String sql="SELECT j.id,j.job_key,j.job_type,j.listing_id,j.game_id,j.state,j.attempt,j.next_attempt_at,j.last_error,j.priority,j.source,j.progress AS stored_progress,"+
                "COALESCE(l.vinted_title,g.canonical_name,'') AS label,COALESCE(j.game_id,l.game_id,0) AS display_game_id,"+
                "g.bgg_id,g.thumbnail_url,g.image_url,j.processing_started_at,"+
                "CASE WHEN j.job_type='BGG_ENRICHMENT' THEN MIN(95,20+CASE WHEN g.image_url IS NOT NULL AND g.image_url<>'' THEN 15 ELSE 0 END+CASE WHEN g.rating IS NOT NULL THEN 15 ELSE 0 END+CASE WHEN g.min_players IS NOT NULL OR g.max_players IS NOT NULL THEN 10 ELSE 0 END+CASE WHEN g.weight IS NOT NULL THEN 10 ELSE 0 END+CASE WHEN g.categories IS NOT NULL AND g.categories<>'' THEN 10 ELSE 0 END+CASE WHEN g.metadata_updated_at>0 THEN 15 ELSE 0 END) "+
                "ELSE MIN(95,10+CASE WHEN l.vinted_item_id IS NOT NULL AND l.vinted_item_id<>'' THEN 18 ELSE 0 END+CASE WHEN l.vinted_url IS NOT NULL AND l.vinted_url<>'' THEN 22 ELSE 0 END+CASE WHEN l.seller_id IS NOT NULL AND l.seller_id<>'' THEN 15 ELSE 0 END+CASE WHEN l.image_url IS NOT NULL AND l.image_url<>'' THEN 15 ELSE 0 END+CASE WHEN l.published_label IS NOT NULL AND l.published_label<>'' THEN 15 ELSE 0 END+CASE WHEN l.listing_photos_csv IS NOT NULL AND l.listing_photos_csv<>'' THEN 5 ELSE 0 END) END AS progress "+
                "FROM processing_jobs j LEFT JOIN market_listings l ON l.id=j.listing_id LEFT JOIN games g ON g.id=COALESCE(j.game_id,l.game_id) "+
                "WHERE j.source<>? AND j.job_type<>? AND j.state IN (?,?,?) ORDER BY CASE j.state WHEN 'PROCESSING' THEN 0 ELSE 1 END,j.priority DESC,j.next_attempt_at,j.created_at LIMIT ?";
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{HISTORICAL_SOURCE,JOB_VINTED_DEEP,PROCESSING,FAILED_RETRYABLE,PENDING,String.valueOf(Math.max(1,limit))})){while(c.moveToNext())out.add(readJob(c));}
        return out;
    }

    /** A bounded window into the historical queue. */
    public List<Job> historicalJobs(int offset,int limit) {
        List<Job> out=new ArrayList<>();
        String sql="SELECT j.id,j.job_key,j.job_type,j.listing_id,j.game_id,j.state,j.attempt,j.next_attempt_at,j.last_error,j.priority,j.source,j.progress AS stored_progress,"+
                "COALESCE(l.vinted_title,g.canonical_name,'') AS label,COALESCE(j.game_id,l.game_id,0) AS display_game_id,"+
                "g.bgg_id,g.thumbnail_url,g.image_url,j.processing_started_at,"+
                "CASE WHEN j.job_type='BGG_ENRICHMENT' THEN MIN(95,20+CASE WHEN g.image_url IS NOT NULL AND g.image_url<>'' THEN 15 ELSE 0 END+CASE WHEN g.rating IS NOT NULL THEN 15 ELSE 0 END+CASE WHEN g.min_players IS NOT NULL OR g.max_players IS NOT NULL THEN 10 ELSE 0 END+CASE WHEN g.weight IS NOT NULL THEN 10 ELSE 0 END+CASE WHEN g.categories IS NOT NULL AND g.categories<>'' THEN 10 ELSE 0 END+CASE WHEN g.metadata_updated_at>0 THEN 15 ELSE 0 END) "+
                "ELSE MIN(95,10+CASE WHEN l.vinted_item_id IS NOT NULL AND l.vinted_item_id<>'' THEN 18 ELSE 0 END+CASE WHEN l.vinted_url IS NOT NULL AND l.vinted_url<>'' THEN 22 ELSE 0 END+CASE WHEN l.seller_id IS NOT NULL AND l.seller_id<>'' THEN 15 ELSE 0 END+CASE WHEN l.image_url IS NOT NULL AND l.image_url<>'' THEN 15 ELSE 0 END+CASE WHEN l.published_label IS NOT NULL AND l.published_label<>'' THEN 15 ELSE 0 END+CASE WHEN l.listing_photos_csv IS NOT NULL AND l.listing_photos_csv<>'' THEN 5 ELSE 0 END) END AS progress "+
                "FROM processing_jobs j LEFT JOIN market_listings l ON l.id=j.listing_id LEFT JOIN games g ON g.id=COALESCE(j.game_id,l.game_id) "+
                "WHERE j.source=? AND j.state IN (?,?,?) ORDER BY CASE j.state WHEN 'PROCESSING' THEN 0 WHEN 'FAILED_RETRYABLE' THEN 1 ELSE 2 END,j.next_attempt_at,j.created_at LIMIT ? OFFSET ?";
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{HISTORICAL_SOURCE,PROCESSING,FAILED_RETRYABLE,PENDING,String.valueOf(Math.max(1,limit)),String.valueOf(Math.max(0,offset))})){while(c.moveToNext())out.add(readJob(c));}
        return out;
    }

    public int historicalActiveCount() {
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM processing_jobs WHERE source=? AND state IN (?,?,?)",new String[]{HISTORICAL_SOURCE,PENDING,PROCESSING,FAILED_RETRYABLE})){return c.moveToFirst()?c.getInt(0):0;}
    }

    public int historicalCompletedSince(long since) {
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM processing_jobs WHERE source=? AND state=? AND updated_at>=?",new String[]{HISTORICAL_SOURCE,COMPLETE,String.valueOf(Math.max(0L,since))})){return c.moveToFirst()?c.getInt(0):0;}
    }

    private static final long URGENT_VINTED_RESERVE_MS=65_000L;

    /** Fresh/explicit Vinted work that must preempt backlog batching. Deep metadata is intentionally excluded.
     * Count all active urgent rows, even when their retry time is still in the future. A future retry
     * no longer freezes ordinary work for minutes: urgentReservationUntil() reserves only the next
     * public-page slot when the urgent row is close enough to become runnable. */
    public int urgentVintedWorkCount(long now) {
        DealDatabase.ObservationSession run=helper.activeObservationSession();SQLiteDatabase db=helper.getReadableDatabase();
        if(run==null){String sql="SELECT COUNT(*) FROM processing_jobs WHERE job_type=? AND source IN (?,?,?) AND state IN (?,?,?)";try(Cursor c=db.rawQuery(sql,new String[]{JOB_VINTED,"LIVE_DEAL","HUNT_PRIORITY","MANUAL_PRIORITY",PENDING,PROCESSING,FAILED_RETRYABLE})){return c.moveToFirst()?c.getInt(0):0;}}
        String sql="SELECT COUNT(*) FROM processing_jobs j LEFT JOIN market_listings l ON l.id=j.listing_id WHERE j.job_type=? AND j.state IN (?,?,?) AND (j.source IN (?,?) OR (j.source='LIVE_DEAL' AND COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) IN (SELECT signature FROM observations WHERE observed_at>=? AND observed_at<=?)))";
        try(Cursor c=db.rawQuery(sql,new String[]{JOB_VINTED,PENDING,PROCESSING,FAILED_RETRYABLE,"HUNT_PRIORITY","MANUAL_PRIORITY",String.valueOf(run.startAt),String.valueOf(run.endAt)})){return c.moveToFirst()?c.getInt(0):0;}
    }

    /** Urgent rows that may be claimed right now. Newer-scroll Live rows are intentionally not due
     * while an older run owns the automatic pipeline. */
    public int urgentVintedDueCount(long now) {
        DealDatabase.ObservationSession run=helper.activeObservationSession();SQLiteDatabase db=helper.getReadableDatabase();
        if(run==null){String sql="SELECT COUNT(*) FROM processing_jobs WHERE job_type=? AND source IN (?,?,?) AND state IN (?,?) AND next_attempt_at<=?";try(Cursor c=db.rawQuery(sql,new String[]{JOB_VINTED,"LIVE_DEAL","HUNT_PRIORITY","MANUAL_PRIORITY",PENDING,FAILED_RETRYABLE,String.valueOf(now)})){return c.moveToFirst()?c.getInt(0):0;}}
        String sql="SELECT COUNT(*) FROM processing_jobs j LEFT JOIN market_listings l ON l.id=j.listing_id WHERE j.job_type=? AND j.state IN (?,?) AND j.next_attempt_at<=? AND (j.source IN (?,?) OR (j.source='LIVE_DEAL' AND COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) IN (SELECT signature FROM observations WHERE observed_at>=? AND observed_at<=?)))";
        try(Cursor c=db.rawQuery(sql,new String[]{JOB_VINTED,PENDING,FAILED_RETRYABLE,String.valueOf(now),"HUNT_PRIORITY","MANUAL_PRIORITY",String.valueOf(run.startAt),String.valueOf(run.endAt)})){return c.moveToFirst()?c.getInt(0):0;}
    }

    public long nextUrgentVintedDueAt(long now) {
        DealDatabase.ObservationSession run=helper.activeObservationSession();SQLiteDatabase db=helper.getReadableDatabase();
        if(run==null){
            String sql="SELECT MIN(next_attempt_at) FROM processing_jobs WHERE job_type=? AND source IN (?,?,?) AND state IN (?,?)";
            try(Cursor c=db.rawQuery(sql,new String[]{JOB_VINTED,"LIVE_DEAL","HUNT_PRIORITY","MANUAL_PRIORITY",PENDING,FAILED_RETRYABLE})){return c.moveToFirst()&&!c.isNull(0)?c.getLong(0):0L;}
        }
        String sql="SELECT MIN(j.next_attempt_at) FROM processing_jobs j LEFT JOIN market_listings l ON l.id=j.listing_id WHERE j.job_type=? AND j.state IN (?,?) AND (j.source IN (?,?) OR (j.source='LIVE_DEAL' AND COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) IN (SELECT signature FROM observations WHERE observed_at>=? AND observed_at<=?)))";
        try(Cursor c=db.rawQuery(sql,new String[]{JOB_VINTED,PENDING,FAILED_RETRYABLE,"HUNT_PRIORITY","MANUAL_PRIORITY",String.valueOf(run.startAt),String.valueOf(run.endAt)})){return c.moveToFirst()&&!c.isNull(0)?c.getLong(0):0L;}
    }

    /** Return a future timestamp only when it is worth holding one public-page slot for urgent work.
     * A retry several minutes away must never starve an already-runnable Motore run. */
    public long urgentVintedReservationUntil(long now) {
        if(urgentVintedWorkCount(now)<=0||urgentVintedDueCount(now)>0)return 0L;
        long next=nextUrgentVintedDueAt(now);
        if(next<=now||next-now>URGENT_VINTED_RESERVE_MS)return 0L;
        return next;
    }

    /** A strong Vinted batch link proves the marketplace item but not necessarily the exact BGG variant. */
    public void markBggVariantPending(long listingId,String reason){
        if(listingId<=0)return;ContentValues v=new ContentValues();v.put("match_state","BGG_VARIANT_PENDING");v.put("last_error","");
        helper.getWritableDatabase().update("market_listings",v,"id=?",new String[]{String.valueOf(listingId)});
        if(!TextUtils.isEmpty(reason))setDiagnosticState("bgg_variant_guard",1,"state=PENDING;listing="+listingId+";reason="+safe(reason));
    }

    public void confirmBggVariant(long listingId,String reason){
        if(listingId<=0)return;ContentValues v=new ContentValues();v.put("match_state","MATCHED");v.put("last_error","");v.put("manual_review_required",0);v.putNull("manual_review_reason");
        helper.getWritableDatabase().update("market_listings",v,"id=? AND match_state IN ('BGG_VARIANT_PENDING','BGG_VARIANT_REVIEW')",new String[]{String.valueOf(listingId)});
        if(!TextUtils.isEmpty(reason))setDiagnosticState("bgg_variant_guard",1,"state=CONFIRMED;listing="+listingId+";reason="+safe(reason));
    }

    public void flagBggVariantReview(long listingId,String reason){
        if(listingId<=0)return;ContentValues v=new ContentValues();v.put("match_state","BGG_VARIANT_REVIEW");v.put("last_error",safe(reason));v.put("manual_review_required",1);v.put("manual_review_reason",safe(reason));
        helper.getWritableDatabase().update("market_listings",v,"id=?",new String[]{String.valueOf(listingId)});
        setDiagnosticState("bgg_variant_guard",2,"state=REVIEW;listing="+listingId+";reason="+safe(reason));
    }
    public boolean isBggVariantPending(long listingId){
        if(listingId<=0)return false;
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT 1 FROM market_listings WHERE id=? AND match_state='BGG_VARIANT_PENDING' LIMIT 1",new String[]{String.valueOf(listingId)})){return c.moveToFirst();}
    }

    public boolean flagPendingBggVariantReview(long listingId,String reason){
        if(listingId<=0)return false;ContentValues v=new ContentValues();v.put("match_state","BGG_VARIANT_REVIEW");v.put("last_error",safe(reason));v.put("manual_review_required",1);v.put("manual_review_reason",safe(reason));
        int changed=helper.getWritableDatabase().update("market_listings",v,"id=? AND match_state='BGG_VARIANT_PENDING'",new String[]{String.valueOf(listingId)});
        if(changed>0)setDiagnosticState("bgg_variant_guard",2,"state=REVIEW;listing="+listingId+";reason="+safe(reason));return changed>0;
    }


    /** Reassign exactly one listing to a more specific BGG identity. Unlike assignAutoBggMatch(),
     * this never moves the other observations currently grouped under the base game. */
    public long reassignListingToBggVariant(long listingId,BggSearchClient.Game selected,double confidence,String reason){
        if(listingId<=0||selected==null||TextUtils.isEmpty(selected.id))return -1L;
        SQLiteDatabase db=helper.getWritableDatabase();long now=System.currentTimeMillis();long targetId=-1L;
        db.beginTransaction();
        try{
            Long target=scalarLong(db,"SELECT id FROM games WHERE bgg_id=?",new String[]{selected.id});
            if(target!=null){
                targetId=target;
                ContentValues existing=new ContentValues();existing.put("last_seen",now);
                if(selected.rating!=null){existing.put("rating",selected.rating);if(selected.rating<DealPolicy.MIN_BGG_RATING){existing.put("database_visible",0);existing.put("filter_reason","BGG_RATING_BELOW_6");}}
                db.update("games",existing,"id=?",new String[]{String.valueOf(targetId)});
            }else{
                ContentValues g=new ContentValues();g.put("bgg_id",selected.id);g.put("canonical_name",safe(TextUtils.isEmpty(selected.name)?selected.id:selected.name));g.put("normalized_name",normalize(selected.name));put(g,"year",selected.year);put(g,"rating",selected.rating);put(g,"voters",selected.voters);put(g,"bgg_rank",selected.rank);put(g,"weight",selected.weight);put(g,"min_players",selected.minPlayers);put(g,"max_players",selected.maxPlayers);put(g,"playtime",selected.playtime);put(g,"image_url",selected.imageUrl);put(g,"categories",selected.categories);g.put("bgg_url","https://boardgamegeek.com/boardgame/"+selected.id);g.put("match_state","MATCHED");g.put("match_confidence",confidence);g.put("match_algorithm_version",BGG_MATCH_ALGORITHM_VERSION);g.put("first_seen",now);g.put("last_seen",now);if(selected.rating!=null&&selected.rating<DealPolicy.MIN_BGG_RATING){g.put("database_visible",0);g.put("filter_reason","BGG_RATING_BELOW_6");}targetId=db.insertOrThrow("games",null,g);
            }
            ContentValues l=new ContentValues();l.put("game_id",targetId);l.put("match_state","MATCHED");l.put("match_confidence",confidence);l.put("last_error","");l.put("manual_review_required",0);l.putNull("manual_review_reason");db.update("market_listings",l,"id=?",new String[]{String.valueOf(listingId)});
            String title=scalarString(db,"SELECT vinted_title FROM market_listings WHERE id=?",new String[]{String.valueOf(listingId)});if(!TextUtils.isEmpty(title))addAlias(db,targetId,title,"VINTED_VARIANT");
            enqueueJob(db,"bgg:"+targetId,JOB_BGG,null,targetId,now,170,"VARIANT_VERIFY");
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        if(targetId>0)setDiagnosticState("bgg_variant_guard",1,"state=REASSIGNED;listing="+listingId+";bgg="+selected.id+";name="+safe(selected.name)+";reason="+safe(reason));
        return targetId;
    }

    public int priorityActiveCount() {
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM processing_jobs WHERE source<>? AND job_type<>? AND state IN (?,?,?)",new String[]{HISTORICAL_SOURCE,JOB_VINTED_DEEP,PENDING,PROCESSING,FAILED_RETRYABLE})){return c.moveToFirst()?c.getInt(0):0;}
    }

    private boolean control(String name, boolean fallback) {
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT value FROM queue_controls WHERE name=? LIMIT 1",new String[]{name})){
            if(c.moveToFirst())return c.getInt(0)!=0;
        }catch(Throwable ignored){}
        // One-time compatibility fallback for installs upgrading from v12.
        return context.getSharedPreferences(QUEUE_PREFS,Context.MODE_PRIVATE).getBoolean(name,fallback);
    }

    private void setControl(String name, boolean value) {
        ContentValues v=new ContentValues();v.put("name",name);v.put("value",value?1:0);v.put("updated_at",System.currentTimeMillis());
        helper.getWritableDatabase().insertWithOnConflict("queue_controls",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Cross-process liveness heartbeat for the queue owner. Stored in SQLite so :ui can trust it. */
    public void touchProcessorHeartbeat() {
        ContentValues v=new ContentValues();v.put("name","processor_heartbeat");v.put("value",1);v.put("updated_at",System.currentTimeMillis());
        helper.getWritableDatabase().insertWithOnConflict("queue_controls",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }

    public long processorHeartbeatAt() {
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT updated_at FROM queue_controls WHERE name='processor_heartbeat' LIMIT 1",null)){
            return c.moveToFirst()?c.getLong(0):0L;
        }catch(Throwable ignored){return 0L;}
    }

    /** Updated after a supervisor pass completes, so it does not make a stalled control thread look alive. */
    public void touchSupervisorHeartbeat() {
        ContentValues v=new ContentValues();v.put("name","supervisor_heartbeat");v.put("value",1);v.put("updated_at",System.currentTimeMillis());
        helper.getWritableDatabase().insertWithOnConflict("queue_controls",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }

    public long supervisorHeartbeatAt() {
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT updated_at FROM queue_controls WHERE name='supervisor_heartbeat' LIMIT 1",null)){
            return c.moveToFirst()?c.getLong(0):0L;
        }catch(Throwable ignored){return 0L;}
    }

    private static String laneKey(String lane,String suffix){return "lane_"+("bgg".equals(lane)?"bgg":"vinted")+"_"+suffix;}

    /** Heartbeats are lane-specific: the notification thread must not make a dead network lane
     * look healthy. */
    public void touchLaneHeartbeat(String lane) {
        ContentValues v=new ContentValues();v.put("name",laneKey(lane,"heartbeat"));v.put("value",1);v.put("updated_at",System.currentTimeMillis());v.putNull("text_value");
        helper.getWritableDatabase().insertWithOnConflict("queue_controls",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }

    public long laneHeartbeatAt(String lane) {
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT updated_at FROM queue_controls WHERE name=? LIMIT 1",new String[]{laneKey(lane,"heartbeat")})){return c.moveToFirst()?c.getLong(0):0L;}catch(Throwable ignored){return 0L;}
    }

    public void setLaneStatus(String lane,String state,String detail,long value) {
        ContentValues v=new ContentValues();v.put("name",laneKey(lane,"status"));v.put("value",value);v.put("updated_at",System.currentTimeMillis());v.put("text_value",(state==null?"":state)+"\n"+(detail==null?"":detail));
        helper.getWritableDatabase().insertWithOnConflict("queue_controls",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }

    public RuntimeStatus laneStatus(String lane) {
        RuntimeStatus r=new RuntimeStatus();
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT value,updated_at,text_value FROM queue_controls WHERE name=? LIMIT 1",new String[]{laneKey(lane,"status")})){
            if(c.moveToFirst()){r.value=c.getLong(0);r.updatedAt=c.getLong(1);String x=c.isNull(2)?"":c.getString(2);int cut=x.indexOf('\n');if(cut>=0){r.state=x.substring(0,cut);r.detail=x.substring(cut+1);}else r.state=x;}
        }catch(Throwable ignored){}return r;
    }

    /** Small SQLite-backed diagnostics channel for values written in :radar and read in :ui.
     * SharedPreferences are process-local caches and are not reliable for cross-process telemetry. */
    public void setDiagnosticState(String name,long value,String text) {
        if(TextUtils.isEmpty(name))return;
        ContentValues v=new ContentValues();v.put("name","diag:"+name);v.put("value",value);v.put("updated_at",System.currentTimeMillis());
        if(text==null)v.putNull("text_value");else v.put("text_value",text);
        helper.getWritableDatabase().insertWithOnConflict("queue_controls",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }

    public RuntimeStatus diagnosticState(String name) {
        RuntimeStatus r=new RuntimeStatus();if(TextUtils.isEmpty(name))return r;
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT value,updated_at,text_value FROM queue_controls WHERE name=? LIMIT 1",new String[]{"diag:"+name})){
            if(c.moveToFirst()){r.value=c.getLong(0);r.updatedAt=c.getLong(1);r.detail=c.isNull(2)?"":c.getString(2);}
        }catch(Throwable ignored){}return r;
    }

    private static final long TEST2B_LOCK_TTL_MS=4*60_000L;

    /** TEST 2b coordination lock. It only prevents other Ludo consumers from claiming a new
     * Vinted job while the one-shot measurement owns the lane; it never affects Vinted's gate. */
    public boolean isTest2bExclusiveActive() {
        RuntimeStatus r=diagnosticState("t2b_exclusive");
        return r.value==1L && r.updatedAt>0L && System.currentTimeMillis()-r.updatedAt<TEST2B_LOCK_TTL_MS;
    }

    public void setTest2bExclusive(boolean active,String detail) {
        setDiagnosticState("t2b_exclusive",active?1L:0L,detail);
    }

    public boolean isHistoricalPaused() { return control(KEY_HISTORY_PAUSED,false); }

    public void setHistoricalPaused(boolean paused) {
        setControl(KEY_HISTORY_PAUSED,paused);
        Log.i(TAG,"historical queue userPaused="+paused);
        if(!paused){ QueueKeepAliveService.ensureRunning(context); QueueWorkScheduler.schedule(context); }
        context.sendBroadcast(new android.content.Intent(OperationCenter.CHANGED).setPackage(context.getPackageName()));
    }

    public boolean isVintedPaused() { return control(KEY_VINTED_PAUSED,false); }

    public void setVintedPaused(boolean paused) {
        setControl(KEY_VINTED_PAUSED,paused);
        Log.i(TAG,"vinted lane userPaused="+paused);
        if(!paused){ QueueKeepAliveService.ensureRunning(context); QueueWorkScheduler.schedule(context); }
        context.sendBroadcast(new android.content.Intent(OperationCenter.CHANGED).setPackage(context.getPackageName()));
    }

    public boolean isBggPaused() { return control(KEY_BGG_PAUSED,false); }

    public void setBggPaused(boolean paused) {
        setControl(KEY_BGG_PAUSED,paused);
        Log.i(TAG,"bgg lane userPaused="+paused);
        if(!paused){ QueueKeepAliveService.ensureRunning(context); QueueWorkScheduler.schedule(context); }
        context.sendBroadcast(new android.content.Intent(OperationCenter.CHANGED).setPackage(context.getPackageName()));
    }

    public int historicalTotalCount() {
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM processing_jobs WHERE source=?",new String[]{HISTORICAL_SOURCE})){return c.moveToFirst()?c.getInt(0):0;}
    }

    public int historicalCompletedCount() {
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM processing_jobs WHERE source=? AND state=?",new String[]{HISTORICAL_SOURCE,COMPLETE})){return c.moveToFirst()?c.getInt(0):0;}
    }

    /** Typical Vinted attempt duration from recent successful jobs. A high-confidence lookup often
     * needs two public pages and the shared pacing gap, so the fallback is deliberately conservative. */
    public long typicalVintedDurationMs() {
        ArrayList<Long> samples=new ArrayList<>();
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT updated_at-processing_started_at FROM processing_jobs WHERE job_type IN (?,?) AND state=? AND processing_started_at>0 AND updated_at>processing_started_at ORDER BY updated_at DESC LIMIT 20",new String[]{JOB_VINTED,JOB_VINTED_DEEP,COMPLETE})){while(c.moveToNext()){long d=c.getLong(0);if(d>=5_000L&&d<=4*60_000L)samples.add(d);}}catch(Throwable ignored){}
        if(samples.isEmpty())return 125_000L;
        java.util.Collections.sort(samples);long median=samples.get(samples.size()/2);return Math.max(70_000L,Math.min(180_000L,median));
    }

    /** Time-based interpolation is only a visual estimate. Real milestones always win and time
     * alone never reaches completion. */
    public int visualProgress(Job job,long now) {
        if(job==null)return 0;int real=Math.max(0,Math.min(100,job.progress));
        if(!PROCESSING.equals(job.state)||job.processingStartedAt<=0||JOB_BGG.equals(job.type))return real;
        long expected=typicalVintedDurationMs();long elapsed=Math.max(0L,now-job.processingStartedAt);
        int estimated=18+(int)Math.round(70.0*Math.min(1.0,elapsed/(double)expected));
        return Math.max(real,Math.min(88,estimated));
    }

    public long visualRemainingMs(Job job,long now) {
        if(job==null||!PROCESSING.equals(job.state)||job.processingStartedAt<=0||JOB_BGG.equals(job.type))return 0L;
        long expected=typicalVintedDurationMs();return Math.max(1_500L,Math.min(180_000L,expected-Math.max(0L,now-job.processingStartedAt)));
    }

    public String currentProcessingLabel() {
        String sql="SELECT COALESCE(l.vinted_title,g.canonical_name,'') FROM processing_jobs j LEFT JOIN market_listings l ON l.id=j.listing_id LEFT JOIN games g ON g.id=COALESCE(j.game_id,l.game_id) WHERE j.state=? ORDER BY j.priority DESC,j.updated_at DESC LIMIT 1";
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{PROCESSING})){return c.moveToFirst()?c.getString(0):"";}
    }

    public int currentProcessingProgress() {
        String sql="SELECT job_type,progress,processing_started_at FROM processing_jobs WHERE state=? ORDER BY priority DESC,updated_at DESC LIMIT 1";
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{PROCESSING})){
            if(!c.moveToFirst())return 0;int real=Math.max(0,Math.min(100,c.getInt(1)));String type=c.getString(0);long started=c.isNull(2)?0L:c.getLong(2);
            if(isVintedJobType(type)&&started>0L){long expected=typicalVintedDurationMs();long elapsed=Math.max(0L,System.currentTimeMillis()-started);int estimate=18+(int)Math.round(70.0*Math.min(1.0,elapsed/(double)expected));return Math.max(real,Math.min(88,estimate));}
            return real;
        }
    }

    public String currentUserVisibleProcessingLabel() {
        String sql="SELECT COALESCE(l.vinted_title,g.canonical_name,'') FROM processing_jobs j LEFT JOIN market_listings l ON l.id=j.listing_id LEFT JOIN games g ON g.id=COALESCE(j.game_id,l.game_id) WHERE j.state=? AND j.job_type<>? ORDER BY j.priority DESC,j.updated_at DESC LIMIT 1";
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{PROCESSING,JOB_VINTED_DEEP})){return c.moveToFirst()?c.getString(0):"";}
    }

    public int currentUserVisibleProcessingProgress() {
        String sql="SELECT job_type,progress,processing_started_at FROM processing_jobs WHERE state=? AND job_type<>? ORDER BY priority DESC,updated_at DESC LIMIT 1";
        try(Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{PROCESSING,JOB_VINTED_DEEP})){
            if(!c.moveToFirst())return 0;int real=Math.max(0,Math.min(100,c.getInt(1)));String type=c.getString(0);long started=c.isNull(2)?0L:c.getLong(2);
            if(JOB_VINTED.equals(type)&&started>0L){long expected=typicalVintedDurationMs();long elapsed=Math.max(0L,System.currentTimeMillis()-started);int estimate=18+(int)Math.round(70.0*Math.min(1.0,elapsed/(double)expected));return Math.max(real,Math.min(88,estimate));}
            return real;
        }
    }

    /** Earliest actually-claimable due time. */
    public long nextDueAt() {
        long now=System.currentTimeMillis();
        long v=nextRunnableVintedDueAt(), b=nextRunnableBggDueAt(), out=0L;
        if(v>0L)out=v;if(b>0L&&(out==0L||b<out))out=b;
        return out==0L?0L:Math.max(now,out);
    }

    private boolean vintedHistoryAllowed(SQLiteDatabase db,long now) {
        if(isHistoricalPaused())return false;
        String sql="SELECT 1 FROM processing_jobs WHERE job_type IN (?,?) AND source<>? AND (state=? OR (state IN (?,?) AND next_attempt_at<=?)) LIMIT 1";
        try(Cursor c=db.rawQuery(sql,new String[]{JOB_VINTED,JOB_VINTED_DEEP,HISTORICAL_SOURCE,PROCESSING,PENDING,FAILED_RETRYABLE,String.valueOf(now)})){return !c.moveToFirst();}
    }

    private boolean historyAllowed(SQLiteDatabase db,long now,String jobType) {
        if (isHistoricalPaused()) return false;
        // Preemption is lane-aware: fresh Vinted work preempts historical Vinted work, while BGG
        // can still use its independent lane (and vice versa). This keeps foreground work fast
        // without wasting safe parallel capacity.
        String sql="SELECT 1 FROM processing_jobs WHERE job_type=? AND source<>? AND (state=? OR (state IN (?,?) AND next_attempt_at<=?)) LIMIT 1";
        try(Cursor c=db.rawQuery(sql,new String[]{jobType,HISTORICAL_SOURCE,PROCESSING,PENDING,FAILED_RETRYABLE,String.valueOf(now)})){return !c.moveToFirst();}
    }


    /** Strict local-catalog auto match. Only the coordinator calls this after a high score + wide
     * margin; ambiguous cases remain for human review. */
    public long assignAutoBggMatch(long sourceGameId,BggSearchClient.Game selected,double confidence){
        if(sourceGameId<=0||selected==null||TextUtils.isEmpty(selected.id))return sourceGameId;SQLiteDatabase db=helper.getWritableDatabase();long now=System.currentTimeMillis();db.beginTransaction();try{Long target=scalarLong(db,"SELECT id FROM games WHERE bgg_id=?",new String[]{selected.id});long targetId;if(target!=null&&target!=sourceGameId){targetId=target;db.execSQL("UPDATE market_listings SET game_id=?,match_state='MATCHED',match_confidence=? WHERE game_id=?",new Object[]{targetId,confidence,sourceGameId});db.execSQL("INSERT OR IGNORE INTO game_aliases(game_id,alias,normalized_alias,source) SELECT ?,alias,normalized_alias,source FROM game_aliases WHERE game_id=?",new Object[]{targetId,sourceGameId});db.delete("game_aliases","game_id=?",new String[]{String.valueOf(sourceGameId)});db.delete("games","id=? AND (bgg_id IS NULL OR bgg_id='')",new String[]{String.valueOf(sourceGameId)});}else{targetId=sourceGameId;ContentValues v=new ContentValues();v.put("bgg_id",selected.id);v.putNull("provisional_key");if(!TextUtils.isEmpty(selected.name)){v.put("canonical_name",selected.name);v.put("normalized_name",normalize(selected.name));}put(v,"year",selected.year);put(v,"rating",selected.rating);put(v,"voters",selected.voters);put(v,"bgg_rank",selected.rank);put(v,"weight",selected.weight);put(v,"min_players",selected.minPlayers);put(v,"max_players",selected.maxPlayers);put(v,"playtime",selected.playtime);put(v,"image_url",selected.imageUrl);put(v,"categories",selected.categories);v.put("bgg_url","https://boardgamegeek.com/boardgame/"+selected.id);v.put("match_state","MATCHED");v.put("match_confidence",confidence);v.put("match_algorithm_version",BGG_MATCH_ALGORITHM_VERSION);v.put("last_seen",now);db.update("games",v,"id=?",new String[]{String.valueOf(sourceGameId)});db.execSQL("UPDATE market_listings SET match_state='MATCHED',match_confidence=? WHERE game_id=?",new Object[]{confidence,sourceGameId});}addAlias(db,targetId,selected.name,"AUTO_LOCAL_BGG");enqueueGameJob(db,targetId,JOB_BGG,now);db.setTransactionSuccessful();return targetId;}finally{db.endTransaction();}
    }

    /** Explicit user confirmation for ambiguous/provisional BGG matches. */
    public long assignManualBggMatch(long sourceGameId,BggSearchClient.Game selected){
        if(sourceGameId<=0||selected==null||TextUtils.isEmpty(selected.id))return sourceGameId;SQLiteDatabase db=helper.getWritableDatabase();long now=System.currentTimeMillis();long targetId=sourceGameId;db.beginTransaction();try{
            Long target=scalarLong(db,"SELECT id FROM games WHERE bgg_id=?",new String[]{selected.id});
            if(target!=null&&target!=sourceGameId){
                targetId=target;db.execSQL("UPDATE market_listings SET game_id=?,match_state='MATCHED',match_confidence=100,manual_review_required=0,manual_review_reason=NULL WHERE game_id=?",new Object[]{targetId,sourceGameId});
                db.execSQL("INSERT OR IGNORE INTO game_aliases(game_id,alias,normalized_alias,source) SELECT ?,alias,normalized_alias,source FROM game_aliases WHERE game_id=?",new Object[]{targetId,sourceGameId});
                db.delete("game_aliases","game_id=?",new String[]{String.valueOf(sourceGameId)});db.delete("games","id=? AND (bgg_id IS NULL OR bgg_id='')",new String[]{String.valueOf(sourceGameId)});
            }else{
                targetId=sourceGameId;ContentValues v=new ContentValues();v.put("bgg_id",selected.id);v.putNull("provisional_key");if(!TextUtils.isEmpty(selected.name)){v.put("canonical_name",selected.name);v.put("normalized_name",normalize(selected.name));}put(v,"year",selected.year);put(v,"rating",selected.rating);put(v,"voters",selected.voters);put(v,"bgg_rank",selected.rank);put(v,"weight",selected.weight);put(v,"min_players",selected.minPlayers);put(v,"max_players",selected.maxPlayers);put(v,"playtime",selected.playtime);put(v,"image_url",selected.imageUrl);put(v,"categories",selected.categories);v.put("bgg_url","https://boardgamegeek.com/boardgame/"+selected.id);v.put("match_state","MATCHED");v.put("match_confidence",100);v.put("match_algorithm_version",BGG_MATCH_ALGORITHM_VERSION);v.put("last_seen",now);db.update("games",v,"id=?",new String[]{String.valueOf(sourceGameId)});
                db.execSQL("UPDATE market_listings SET match_state='MATCHED',match_confidence=100,manual_review_required=0,manual_review_reason=NULL WHERE game_id=?",new Object[]{sourceGameId});
            }
            addAlias(db,targetId,selected.name,"MANUAL_BGG");enqueueJob(db,"bgg:"+targetId,JOB_BGG,null,targetId,now,250,MANUAL_RECOVERY_SOURCE);db.execSQL("UPDATE processing_jobs SET attempt=0 WHERE job_key=?",new Object[]{"bgg:"+targetId});
            // The manual BGG decision is not the end of the card: every linked listing returns to
            // Motore so the remaining Vinted identity/metadata can complete automatically.
            try(Cursor rows=db.rawQuery("SELECT id,vinted_url,published_label,seller_id FROM market_listings WHERE game_id=? AND lifecycle='ACTIVE'",new String[]{String.valueOf(targetId)})){
                while(rows.moveToNext()){
                    long listingId=rows.getLong(0);String url=rows.getString(1),published=rows.getString(2),seller=rows.getString(3);
                    if(TextUtils.isEmpty(url)){
                        ContentValues st=new ContentValues();st.put("enrichment_state","PENDING_ENRICHMENT");st.put("last_error","");db.update("market_listings",st,"id=?",new String[]{String.valueOf(listingId)});
                        enqueueListingJob(db,listingId,JOB_VINTED,now,260,MANUAL_RECOVERY_SOURCE);db.execSQL("UPDATE processing_jobs SET attempt=0 WHERE job_key=?",new Object[]{"vinted:"+listingId});
                    }else if(TextUtils.isEmpty(published)||TextUtils.isEmpty(seller)){
                        ContentValues st=new ContentValues();st.put("enrichment_state","CORE_COMPLETE");st.put("last_error","");db.update("market_listings",st,"id=?",new String[]{String.valueOf(listingId)});
                        enqueueListingJob(db,listingId,JOB_VINTED_DEEP,now,250,MANUAL_RECOVERY_SOURCE);db.execSQL("UPDATE processing_jobs SET attempt=0 WHERE job_key=?",new Object[]{"vinted-deep:"+listingId});
                    }
                }
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        helper.invalidateActiveObservationSessionCache();QueueKeepAliveService.ensureRunning(context);QueueWorkScheduler.schedule(context);notifyQueueChanged();return targetId;
    }
    public void syncLegacyCorrection(DealRecord d) {
        if(d==null||TextUtils.isEmpty(d.signature)||TextUtils.isEmpty(d.bggId))return;
        SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();try{
            long gameId=ensureGameFromDeal(db,d,System.currentTimeMillis());
            ContentValues v=new ContentValues();v.put("game_id",gameId);v.put("match_state","MATCHED");v.put("match_confidence",100);db.update("market_listings",v,"temp_fingerprint=? OR legacy_signature=?",new String[]{d.signature,d.signature});
            addAlias(db,gameId,d.vintedTitle,"VINTED");enqueueGameJob(db,gameId,JOB_BGG,System.currentTimeMillis());db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    private long ensureListingFromLegacyCursor(SQLiteDatabase db, Cursor c, long now) {
        String sig=c.getString(0);Long existing=scalarLong(db,"SELECT id FROM market_listings WHERE temp_fingerprint=? OR legacy_signature=? LIMIT 1",new String[]{sig,sig});
        if(existing!=null){ContentValues u=new ContentValues();put(u,"vinted_item_id",c.getString(1));put(u,"seller_id",c.getString(9));put(u,"seller_name",c.getString(10));put(u,"vinted_url",c.getString(11));put(u,"image_url",c.getString(12));put(u,"listing_photos_csv",c.getString(13));put(u,"published_label",c.getString(14));put(u,"language_code",c.getString(15));db.update("market_listings",u,"id=?",new String[]{String.valueOf(existing)});return existing;}
        ContentValues v=new ContentValues();v.put("temp_fingerprint",sig);v.put("legacy_signature",sig);put(v,"vinted_item_id",c.getString(1));v.put("vinted_title",safe(c.getString(2)));put(v,"brand",c.getString(3));put(v,"item_condition",c.getString(4));v.put("current_price_cents",c.getInt(5));put(v,"protected_price_cents",c.isNull(6)?null:c.getInt(6));put(v,"favorites",c.isNull(7)?null:c.getInt(7));put(v,"seller_id",c.getString(9));put(v,"seller_name",c.getString(10));put(v,"vinted_url",c.getString(11));put(v,"image_url",c.getString(12));put(v,"listing_photos_csv",c.getString(13));put(v,"published_label",c.getString(14));put(v,"language_code",c.getString(15));v.put("lifecycle",TextUtils.isEmpty(c.getString(18))?"ACTIVE":c.getString(18));v.put("first_seen",c.getLong(16));v.put("last_seen",c.getLong(17));v.put("enrichment_state","PENDING_ENRICHMENT");v.put("match_state",TextUtils.isEmpty(c.getString(8))?"BGG_MATCH_REQUIRED":"MATCHED");long id=db.insertOrThrow("market_listings",null,v);
        ContentValues p=new ContentValues();p.put("listing_id",id);p.put("observed_at",c.getLong(16));p.put("price_cents",c.getInt(5));put(p,"protected_price_cents",c.isNull(6)?null:c.getInt(6));p.put("source","legacy-check");db.insert("price_observations",null,p);return id;
    }

    private long ensureListingFromDeal(SQLiteDatabase db,DealRecord d,long now){Long id=scalarLong(db,"SELECT id FROM market_listings WHERE temp_fingerprint=? OR legacy_signature=? LIMIT 1",new String[]{d.signature,d.signature});if(id!=null)return id;ContentValues v=new ContentValues();v.put("temp_fingerprint",d.signature);v.put("legacy_signature",d.signature);put(v,"vinted_item_id",d.vintedItemId);v.put("vinted_title",safe(d.vintedTitle));put(v,"brand",d.brand);put(v,"item_condition",d.condition);v.put("current_price_cents",d.itemPriceCents);put(v,"protected_price_cents",d.protectedPriceCents);put(v,"favorites",d.favorites);put(v,"seller_id",d.sellerId);put(v,"seller_name",d.sellerName);put(v,"vinted_url",d.vintedUrl);put(v,"image_url",d.imageUrl);put(v,"listing_photos_csv",d.listingPhotosCsv);put(v,"published_label",d.publishedLabel);put(v,"language_code",d.languageCode);v.put("lifecycle",TextUtils.isEmpty(d.lifecycle)?"ACTIVE":d.lifecycle);v.put("enrichment_state","PENDING_ENRICHMENT");v.put("match_state",TextUtils.isEmpty(d.bggId)?"BGG_MATCH_REQUIRED":"MATCHED");v.put("first_seen",d.firstSeen>0?d.firstSeen:now);v.put("last_seen",d.lastSeen>0?d.lastSeen:now);id=db.insertOrThrow("market_listings",null,v);ContentValues p=new ContentValues();p.put("listing_id",id);p.put("observed_at",d.firstSeen>0?d.firstSeen:now);p.put("price_cents",d.itemPriceCents);put(p,"protected_price_cents",d.protectedPriceCents);p.put("source","legacy");db.insert("price_observations",null,p);return id;}

    private long ensureGameFromDeal(SQLiteDatabase db,DealRecord d,long now){Long id=scalarLong(db,"SELECT id FROM games WHERE bgg_id=?",new String[]{d.bggId});if(id!=null)return id;ContentValues v=new ContentValues();v.put("bgg_id",d.bggId);String name=!TextUtils.isEmpty(d.gameName)?d.gameName:(!TextUtils.isEmpty(d.displayName)?d.displayName:d.vintedTitle);v.put("canonical_name",safe(name));v.put("normalized_name",normalize(name));put(v,"image_url",d.bggImageUrl);put(v,"min_players",d.minPlayers);put(v,"max_players",d.maxPlayers);put(v,"playtime",d.playtime);put(v,"weight",d.weight);put(v,"rating",d.rating);put(v,"voters",d.voters);put(v,"bgg_rank",d.rank);put(v,"categories",d.bggCategories);v.put("bgg_url","https://boardgamegeek.com/boardgame/"+d.bggId);v.put("match_state","MATCHED");v.put("match_confidence",100);v.put("first_seen",d.firstSeen>0?d.firstSeen:now);v.put("last_seen",d.lastSeen>0?d.lastSeen:now);return db.insertOrThrow("games",null,v);}

    private long upsertMatchedGame(SQLiteDatabase db, GameAnalysis a, String observedTitle, long now) {
        Long id=scalarLong(db,"SELECT id FROM games WHERE bgg_id=?",new String[]{a.bggId});
        boolean knownLow=a.averageRating!=null&&a.averageRating<DealPolicy.MIN_BGG_RATING;
        if(id==null){
            ContentValues v=new ContentValues();v.put("bgg_id",a.bggId);String name=!TextUtils.isEmpty(a.gameName)?a.gameName:observedTitle;v.put("canonical_name",safe(name));v.put("normalized_name",normalize(name));put(v,"rating",a.averageRating);put(v,"voters",a.voters);put(v,"bgg_rank",a.rank);v.put("bgg_url","https://boardgamegeek.com/boardgame/"+a.bggId);v.put("match_state","MATCHED");put(v,"match_confidence",a.matchConfidence);v.put("first_seen",now);v.put("last_seen",now);
            if(a.averageRating!=null){v.put("database_visible",knownLow?0:1);if(knownLow)v.put("filter_reason","BGG_RATING_BELOW_6");else v.putNull("filter_reason");}
            id=db.insertOrThrow("games",null,v);
        }else{
            ContentValues v=new ContentValues();if(!TextUtils.isEmpty(a.gameName)){v.put("canonical_name",a.gameName);v.put("normalized_name",normalize(a.gameName));}put(v,"rating",a.averageRating);put(v,"voters",a.voters);put(v,"bgg_rank",a.rank);put(v,"match_confidence",a.matchConfidence);v.put("match_state","MATCHED");v.put("last_seen",now);
            if(a.averageRating!=null){v.put("database_visible",knownLow?0:1);if(knownLow)v.put("filter_reason","BGG_RATING_BELOW_6");else v.putNull("filter_reason");}
            db.update("games",v,"id=?",new String[]{String.valueOf(id)});
        }
        return id;
    }

    private long upsertProvisionalGame(SQLiteDatabase db,String title,String state,Double confidence,long now){
        String key=normalize(title);if(key.isEmpty())key="unknown-"+now;Long id=null;String currentState="";
        try(Cursor c=db.rawQuery("SELECT id,COALESCE(match_state,'') FROM games WHERE provisional_key=? LIMIT 1",new String[]{key})){if(c.moveToFirst()){id=c.getLong(0);currentState=c.getString(1);}}
        if(id==null){
            ContentValues v=new ContentValues();v.put("provisional_key",key);v.put("canonical_name",safe(title));v.put("normalized_name",key);v.put("match_state",state);put(v,"match_confidence",confidence);v.put("first_seen",now);v.put("last_seen",now);id=db.insertOrThrow("games",null,v);
        }else{
            ContentValues v=new ContentValues();v.put("last_seen",now);put(v,"match_confidence",confidence);
            // Only unresolved states may be refreshed by another analysis pass. REVIEW and
            // AUTO_QUARANTINED are decisions made by a later/stronger stage and must be monotonic.
            if(TextUtils.isEmpty(currentState)||"PENDING_ANALYSIS".equals(currentState)||"BGG_MATCH_REQUIRED".equals(currentState)||"EPOCH_ARCHIVED_REVIEW".equals(currentState)){
                v.put("match_state",state);v.put("database_visible",1);v.putNull("filter_reason");
            }
            db.update("games",v,"id=?",new String[]{String.valueOf(id)});
        }
        return id;
    }

    private void deleteOrphanProvisional(SQLiteDatabase db,long gameId){try(Cursor c=db.rawQuery("SELECT bgg_id,provisional_key,(SELECT COUNT(*) FROM market_listings WHERE game_id=games.id) FROM games WHERE id=?",new String[]{String.valueOf(gameId)})){if(c.moveToFirst()&&TextUtils.isEmpty(c.getString(0))&&!TextUtils.isEmpty(c.getString(1))&&c.getInt(2)==0){db.delete("game_aliases","game_id=?",new String[]{String.valueOf(gameId)});db.delete("games","id=?",new String[]{String.valueOf(gameId)});}}}

    private void addAlias(SQLiteDatabase db,long gameId,String alias,String source){if(TextUtils.isEmpty(alias))return;ContentValues v=new ContentValues();v.put("game_id",gameId);v.put("alias",alias.trim());v.put("normalized_alias",normalize(alias));v.put("source",source);db.insertWithOnConflict("game_aliases",null,v,SQLiteDatabase.CONFLICT_IGNORE);}

    private void enqueueListingJob(SQLiteDatabase db,long listingId,String type,long now){enqueueListingJob(db,listingId,type,now,200,"AUTO");}
    private boolean hasActiveWork(SQLiteDatabase db,long listingId){
        try(Cursor c=db.rawQuery("SELECT 1 FROM processing_jobs j WHERE j.state IN (?,?,?) AND (j.listing_id=? OR j.game_id IN (SELECT game_id FROM market_listings WHERE id=? AND game_id IS NOT NULL)) LIMIT 1",
                new String[]{PENDING,PROCESSING,FAILED_RETRYABLE,String.valueOf(listingId),String.valueOf(listingId)})){return c.moveToFirst();}
    }

    private void enqueueListingJob(SQLiteDatabase db,long listingId,String type,long now,int priority,String source){
        String key=(JOB_VINTED_DEEP.equals(type)?"vinted-deep:":"vinted:")+listingId;
        enqueueJob(db,key,type,listingId,null,now,priority,source);
    }
    private void enqueueGameJob(SQLiteDatabase db,long gameId,String type,long now){enqueueJob(db,"bgg:"+gameId,type,null,gameId,now,190,"AUTO");}
    private void enqueueJob(SQLiteDatabase db,String key,String type,Long listingId,Long gameId,long now,int priority,String source){
        if(isVintedJobType(type)&&listingId!=null&&browserOwned(db,listingId)&&!BrowserCapturePolicy.explicitRequest(source))return;
        QueueWorkScheduler.schedule(context);
        try(Cursor c=db.rawQuery("SELECT state,next_attempt_at,priority FROM processing_jobs WHERE job_key=?",new String[]{key})){
            if(c.moveToFirst()){
                String state=c.getString(0); int oldPriority=c.getInt(2);
                ContentValues v=new ContentValues();
                if(PROCESSING.equals(state)){if(priority>oldPriority){v.put("priority",priority);v.put("source",safe(source));v.put("updated_at",now);db.update("processing_jobs",v,"job_key=?",new String[]{key});}return;}
                if(PENDING.equals(state)){if(priority>oldPriority){v.put("priority",priority);v.put("source",safe(source));v.put("next_attempt_at",0);v.put("updated_at",now);db.update("processing_jobs",v,"job_key=?",new String[]{key});}return;}
                v.put("state",PENDING);v.put("priority",Math.max(priority,oldPriority));v.put("source",safe(source));v.put("next_attempt_at",0);v.put("updated_at",now);v.put("last_error","");v.put("progress",0);db.update("processing_jobs",v,"job_key=?",new String[]{key});return;
            }
        }
        ContentValues v=new ContentValues();v.put("job_key",key);v.put("job_type",type);put(v,"listing_id",listingId);put(v,"game_id",gameId);v.put("state",PENDING);v.put("attempt",0);v.put("next_attempt_at",0);v.put("created_at",now);v.put("updated_at",now);v.put("last_error","");v.put("priority",priority);v.put("source",safe(source));v.put("progress",0);db.insertWithOnConflict("processing_jobs",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        QueueWorkScheduler.schedule(context);QueueKeepAliveService.ensureRunning(context);
    }

    private void markJobState(String key,String state,long nextAt,String error,boolean incrementAttempt){SQLiteDatabase db=helper.getWritableDatabase();ContentValues v=new ContentValues();v.put("state",state);v.put("next_attempt_at",nextAt);v.put("updated_at",System.currentTimeMillis());v.put("last_error",error==null?"":safe(error));if(incrementAttempt)db.execSQL("UPDATE processing_jobs SET attempt=attempt+1 WHERE job_key=?",new Object[]{key});db.update("processing_jobs",v,"job_key=?",new String[]{key});Log.i(TAG,"jobKey="+key+" state="+state+(nextAt>0?" next="+nextAt:"")+(TextUtils.isEmpty(error)?"":" error="+safe(error)));notifyQueueChanged();}
    private void notifyQueueChanged(){try{context.sendBroadcast(new android.content.Intent(OperationCenter.CHANGED).setPackage(context.getPackageName()));}catch(Throwable ignored){}}
    private Long gameIdForBgg(String bggId){if(TextUtils.isEmpty(bggId))return null;return scalarLong(helper.getReadableDatabase(),"SELECT id FROM games WHERE bgg_id=?",new String[]{bggId});}
    private void updateListingState(long listingId,String state,String error){if(listingId<=0)return;ContentValues v=new ContentValues();v.put("enrichment_state",state);v.put("last_error",error==null?"":safe(error));helper.getWritableDatabase().update("market_listings",v,"id=?",new String[]{String.valueOf(listingId)});}

    private void mergeListings(SQLiteDatabase db,long sourceId,long targetId){
        // Keep every price transition, then discard the temporary duplicate identity.
        db.execSQL("INSERT INTO price_observations(listing_id,observed_at,price_cents,protected_price_cents,source) SELECT ?,observed_at,price_cents,protected_price_cents,'canonical-merge' FROM price_observations WHERE listing_id=?",new Object[]{targetId,sourceId});
        db.execSQL("UPDATE market_listings SET game_id=COALESCE(game_id,(SELECT game_id FROM market_listings WHERE id=?)),last_seen=MAX(last_seen,(SELECT last_seen FROM market_listings WHERE id=?)),first_seen=MIN(first_seen,(SELECT first_seen FROM market_listings WHERE id=?)),seen_count=seen_count+(SELECT seen_count FROM market_listings WHERE id=?) WHERE id=?",new Object[]{sourceId,sourceId,sourceId,sourceId,targetId});
        db.delete("processing_jobs","listing_id=?",new String[]{String.valueOf(sourceId)});
        db.delete("price_observations","listing_id=?",new String[]{String.valueOf(sourceId)});
        db.delete("market_listings","id=?",new String[]{String.valueOf(sourceId)});
    }

    private Double quantile(long gameId,double q){SQLiteDatabase db=helper.getReadableDatabase();int n;try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM price_observations p JOIN market_listings l ON l.id=p.listing_id WHERE l.game_id=?",new String[]{String.valueOf(gameId)})){if(!c.moveToFirst()||(n=c.getInt(0))==0)return null;}double pos=(n-1)*q;int lo=(int)Math.floor(pos),hi=(int)Math.ceil(pos);int a=priceAtOffset(db,gameId,lo),b=priceAtOffset(db,gameId,hi);return a+(b-a)*(pos-lo);}
    private int priceAtOffset(SQLiteDatabase db,long gameId,int offset){try(Cursor c=db.rawQuery("SELECT p.price_cents FROM price_observations p JOIN market_listings l ON l.id=p.listing_id WHERE l.game_id=? ORDER BY p.price_cents LIMIT 1 OFFSET ?",new String[]{String.valueOf(gameId),String.valueOf(Math.max(0,offset))})){return c.moveToFirst()?c.getInt(0):0;}}

    private static Job readJob(Cursor c){Job j=new Job();j.id=c.getLong(0);j.key=c.getString(1);j.type=c.getString(2);j.listingId=c.isNull(3)?0:c.getLong(3);j.gameId=c.isNull(4)?0:c.getLong(4);j.state=c.getString(5);j.attempt=c.getInt(6);j.nextAttemptAt=c.getLong(7);j.lastError=c.getString(8);int p=c.getColumnIndex("priority");if(p>=0&&!c.isNull(p))j.priority=c.getInt(p);int s=c.getColumnIndex("source");if(s>=0)j.source=c.getString(s);int l=c.getColumnIndex("label");if(l>=0)j.label=c.getString(l);int dg=c.getColumnIndex("display_game_id");if(dg>=0&&!c.isNull(dg))j.displayGameId=c.getLong(dg);int bi=c.getColumnIndex("bgg_id");if(bi>=0)j.displayBggId=c.getString(bi);int th=c.getColumnIndex("thumbnail_url");if(th>=0)j.displayThumbnailUrl=c.getString(th);int im=c.getColumnIndex("image_url");if(im>=0)j.displayImageUrl=c.getString(im);int pr=c.getColumnIndex("progress");if(pr>=0&&!c.isNull(pr))j.progress=Math.max(0,Math.min(100,c.getInt(pr)));int sp=c.getColumnIndex("stored_progress");if(sp>=0&&!c.isNull(sp))j.progress=Math.max(j.progress,Math.max(0,Math.min(100,c.getInt(sp))));int ps=c.getColumnIndex("processing_started_at");if(ps>=0&&!c.isNull(ps))j.processingStartedAt=c.getLong(ps);return j;}
    private static MarketListingRecord readListing(Cursor c){MarketListingRecord r=new MarketListingRecord();int i=0;r.id=c.getLong(i++);r.gameId=c.isNull(i)?null:c.getLong(i);i++;r.tempFingerprint=c.getString(i++);r.vintedItemId=c.getString(i++);r.title=c.getString(i++);r.brand=c.getString(i++);r.condition=c.getString(i++);r.currentPriceCents=c.getInt(i++);r.protectedPriceCents=c.isNull(i)?null:c.getInt(i);i++;r.favorites=c.isNull(i)?null:c.getInt(i);i++;r.sellerId=c.getString(i++);r.sellerName=c.getString(i++);r.url=c.getString(i++);r.imageUrl=c.getString(i++);r.photosCsv=c.getString(i++);r.publishedLabel=c.getString(i++);r.languageCode=c.getString(i++);r.lifecycle=c.getString(i++);r.enrichmentState=c.getString(i++);r.matchState=c.getString(i++);r.matchConfidence=c.isNull(i)?null:c.getDouble(i);i++;r.firstSeen=c.getLong(i++);r.lastSeen=c.getLong(i++);r.enrichedAt=c.getLong(i++);r.lastError=c.getString(i);return r;}

    private static GameRecord readGameBase(Cursor c){GameRecord g=new GameRecord();int i=0;g.id=c.getLong(i++);g.bggId=c.getString(i++);g.provisionalKey=c.getString(i++);g.name=c.getString(i++);g.originalName=c.getString(i++);g.alternateNames=c.getString(i++);g.year=c.isNull(i)?null:c.getInt(i);i++;g.description=c.getString(i++);g.thumbnailUrl=c.getString(i++);g.imageUrl=c.getString(i++);g.minPlayers=c.isNull(i)?null:c.getInt(i);i++;g.maxPlayers=c.isNull(i)?null:c.getInt(i);i++;g.playtime=c.isNull(i)?null:c.getInt(i);i++;g.minAge=c.isNull(i)?null:c.getInt(i);i++;g.weight=c.isNull(i)?null:c.getDouble(i);i++;g.rating=c.isNull(i)?null:c.getDouble(i);i++;g.voters=c.isNull(i)?null:c.getInt(i);i++;g.rank=c.isNull(i)?null:c.getInt(i);i++;g.categories=c.getString(i++);g.mechanics=c.getString(i++);g.designers=c.getString(i++);g.artists=c.getString(i++);g.publishers=c.getString(i++);g.families=c.getString(i++);g.expansions=c.getString(i++);g.baseGames=c.getString(i++);g.bggUrl=c.getString(i++);g.matchState=c.getString(i++);g.matchConfidence=c.isNull(i)?null:c.getDouble(i);i++;g.firstSeen=c.getLong(i++);g.lastSeen=c.getLong(i++);g.metadataUpdatedAt=c.getLong(i);return g;}
    private static GameRecord readGameWithSummary(Cursor c){GameRecord g=readGameBase(c);int i=32;g.listingCount=c.getInt(i++);g.activeListingCount=c.getInt(i++);g.currentMinPriceCents=c.isNull(i)?null:c.getInt(i);i++;g.historicalMinPriceCents=c.isNull(i)?null:c.getInt(i);i++;g.historicalMaxPriceCents=c.isNull(i)?null:c.getInt(i);i++;g.historicalAveragePriceCents=c.isNull(i)?null:c.getDouble(i);i++;g.recentAveragePriceCents=c.isNull(i)?null:(int)Math.round(c.getDouble(i));i++;g.observationCount=c.getInt(i);return g;}

    private static String listingVintedJobType(SQLiteDatabase db,long listingId){
        if(browserOwned(db,listingId))return null;
        try(Cursor c=db.rawQuery("SELECT vinted_url,seller_id,published_label,image_url,enrichment_state FROM market_listings WHERE id=?",new String[]{String.valueOf(listingId)})){
            if(!c.moveToFirst())return JOB_VINTED;
            if("LOCAL_ONLY".equals(c.getString(4))||"DEFERRED_LINK".equals(c.getString(4)))return null;
            if(TextUtils.isEmpty(c.getString(0)))return JOB_VINTED;
            if(TextUtils.isEmpty(c.getString(1))||TextUtils.isEmpty(c.getString(2)))return JOB_VINTED_DEEP;
            return null;
        }
    }
    private static boolean bggRefreshDue(SQLiteDatabase db,long gameId,long now){try(Cursor c=db.rawQuery("SELECT metadata_updated_at,image_url,rating FROM games WHERE id=?",new String[]{String.valueOf(gameId)})){if(!c.moveToFirst())return true;long updated=c.getLong(0);return updated<=0||TextUtils.isEmpty(c.getString(1))||c.isNull(2)||now-updated>=7L*24*60*60_000L;}}
    private static Long listingIdForCard(SQLiteDatabase db,VintedCard card){if(card==null)return null;if(!card.capturedSignature.isEmpty())return scalarLong(db,"SELECT id FROM market_listings WHERE (legacy_signature=? OR temp_fingerprint=?) AND vinted_title=? AND current_price_cents=? AND COALESCE(observed_text,'')=? AND COALESCE(brand,'')=? AND COALESCE(item_condition,'')=? AND COALESCE(protected_price_cents,-1)=CAST(? AS INTEGER) LIMIT 1",new String[]{card.capturedSignature,card.capturedSignature,card.title,String.valueOf(cents(card.itemPrice)),card.rawDescription==null?"":card.rawDescription,card.brand==null?"":card.brand,card.condition==null?"":card.condition,String.valueOf(card.protectedPrice==null?-1:cents(card.protectedPrice))});String fp=fingerprint(card),legacy=DealDatabase.signature(card);return scalarLong(db,"SELECT id FROM market_listings WHERE temp_fingerprint=? OR (legacy_signature=? AND temp_fingerprint=legacy_signature) ORDER BY CASE WHEN temp_fingerprint=? THEN 0 ELSE 1 END LIMIT 1",new String[]{fp,legacy,fp});}
    private static Long scalarLong(SQLiteDatabase db,String sql,String[] args){try(Cursor c=db.rawQuery(sql,args)){return c.moveToFirst()&&!c.isNull(0)?c.getLong(0):null;}}
    private static String scalarString(SQLiteDatabase db,String sql,String[] args){try(Cursor c=db.rawQuery(sql,args)){return c.moveToFirst()&&!c.isNull(0)?c.getString(0):null;}}
    private static boolean same(Integer a,Integer b){return a==null?b==null:a.equals(b);}
    private static String emptyToNull(String s){return TextUtils.isEmpty(s)?null:s;}
    private static String resolvedLanguage(GameAnalysis a){if(a==null||TextUtils.isEmpty(a.languageCode))return null;String s=a.languageCode.toUpperCase(Locale.ROOT);if(a.languageBlocked&&!s.contains("DEP")&&!s.contains("IND"))s+="|DEP";return s;}
    public static String fingerprint(VintedCard c){if(c!=null&&!c.capturedSignature.isEmpty())return c.capturedSignature;return normalize(c==null?null:c.title)+"|"+normalize(c==null?null:c.brand)+"|"+normalize(c==null?null:c.condition)+"|"+(c==null?0:cents(c.itemPrice));}
    private static boolean isUsableResolvedTitle(String value){if(TextUtils.isEmpty(value))return false;String x=value.trim();if(x.length()<2||x.length()>180)return false;String n=x.toLowerCase(Locale.ROOT);return !n.contains("protezione acquisti")&&!n.contains("include la protezione")&&!n.matches("^[\\d\\s.,€]+$");}
    private static String normalize(String v){if(v==null)return"";String n=Normalizer.normalize(v,Normalizer.Form.NFD).replaceAll("\\p{M}+","");return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();}
    private static int cents(double v){return(int)Math.round(v*100.0);}
    private static String safe(String s){return s==null?"":(s.length()>600?s.substring(0,600):s);}
    private static void put(ContentValues v,String k,Object o){if(o==null)v.putNull(k);else if(o instanceof String)v.put(k,(String)o);else if(o instanceof Integer)v.put(k,(Integer)o);else if(o instanceof Long)v.put(k,(Long)o);else if(o instanceof Double)v.put(k,(Double)o);else v.put(k,String.valueOf(o));}
}
