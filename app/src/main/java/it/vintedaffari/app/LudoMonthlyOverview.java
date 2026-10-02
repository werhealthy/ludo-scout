package it.vintedaffari.app;
import java.util.Calendar;
import java.util.TimeZone;
/** Read-only UI summary of locally observed listings, never the imported BGG corpus. */
final class LudoMonthlyOverview {
 static final String STATIC_QUERY="SELECT COUNT(*) AS game_count FROM (SELECT g.bgg_id,MAX(g.rating) AS rating FROM market_listings l JOIN games g ON g.id=l.game_id WHERE l.first_seen>=? AND l.first_seen<=? AND g.database_visible=1 AND g.match_state='MATCHED' AND TRIM(COALESCE(g.bgg_id,''))<>'' AND l.lifecycle IN ('ACTIVE','ARCHIVED','REMOVED') GROUP BY g.bgg_id)";
 // Stream trusted current candidates; the shared DealEvaluator decides price quality.
 static final String GREAT_BUY_QUERY="SELECT g.bgg_id,d.item_price_cents,d.total_cents,d.benchmark_cents,d.protected_price_cents,d.shipping_cents,d.shipping_verified_cents,d.offer_cents,d.tier FROM market_listings l JOIN games g ON g.id=l.game_id JOIN deals d ON (l.legacy_signature=d.signature OR (d.vinted_item_id IS NOT NULL AND l.vinted_item_id=d.vinted_item_id)) AND d.bgg_id=g.bgg_id WHERE l.first_seen>=? AND l.first_seen<=? AND l.lifecycle='ACTIVE' AND l.enrichment_state IN ('COMPLETE','CORE_COMPLETE') AND l.match_state='MATCHED' AND COALESCE(l.manual_review_required,0)=0 AND g.database_visible=1 AND g.match_state='MATCHED' AND g.rating>=6.0 AND TRIM(COALESCE(g.bgg_id,''))<>'' AND d.lifecycle='ACTIVE' AND d.tier='hot' AND d.rating>=6.0 AND TRIM(COALESCE(d.vinted_item_id,''))<>'' AND TRIM(COALESCE(d.vinted_url,''))<>'' AND COALESCE(d.verification_state,'') IN ('OK','USER_CONFIRMED') AND COALESCE(d.listing_type,'') IN ('BASE_GAME','EXPANSION','GAME') AND NOT EXISTS(SELECT 1 FROM processing_jobs j WHERE j.listing_id=l.id AND (j.job_type<>'VINTED_DEEP_ENRICHMENT' OR j.source='MANUAL_RECOVERY') AND j.state IN ('PENDING','PROCESSING','FAILED_RETRYABLE'))";
 // One SQLite statement keeps both counters consistent while the engine writes.
 static final String SNAPSHOT_QUERY="SELECT summary.game_count,candidates.* FROM ("+STATIC_QUERY+") summary LEFT JOIN ("+GREAT_BUY_QUERY+") candidates ON 1=1";
 static long monthStart(long now){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("Europe/Rome"));c.setTimeInMillis(now);c.set(Calendar.DAY_OF_MONTH,1);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
}
