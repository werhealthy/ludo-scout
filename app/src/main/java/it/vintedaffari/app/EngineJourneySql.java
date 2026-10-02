package it.vintedaffari.app;

/** Daily observed cohort, one stable listing identity and independent completed gates.
 * Read-only current results, not historical job events or mutually exclusive queues. */
final class EngineJourneySql {
    private EngineJourneySql(){}
    static String rows(){return """
        WITH cohort AS (
          SELECT o.signature,MIN(o.observed_at) AS first_at,MAX(o.vinted_title) AS title
          FROM observations o WHERE o.observed_at>=? AND o.observed_at<? GROUP BY o.signature
        ), candidates AS (
          SELECT CASE WHEN l.id IS NULL THEN 'observed:'||o.signature ELSE 'listing:'||l.id END AS identity,
          o.signature,COALESCE(NULLIF(o.title,''),'Annuncio osservato') AS title,
          CASE WHEN l.lifecycle='ACTIVE' AND g.database_visible=1 AND g.match_state='MATCHED'
            AND COALESCE(g.bgg_id,'')<>'' AND g.rating>=6
            AND COALESCE(l.enrichment_state,'') NOT IN ('AUTO_FILTERED','AUTO_EXCLUDED','BLOCKED_CLASSIFIER','LOCAL_ONLY') THEN 1 ELSE 0 END AS bgg,
          CASE WHEN COALESCE(l.vinted_item_id,'')<>'' AND COALESCE(l.vinted_url,'')<>'' THEN 1 ELSE 0 END AS linked,
          CASE WHEN l.enrichment_state IN ('COMPLETE','CORE_COMPLETE') AND l.match_state='MATCHED'
            AND COALESCE(l.manual_review_required,0)=0
            AND NOT EXISTS(SELECT 1 FROM processing_jobs j WHERE (j.listing_id=l.id OR j.game_id=g.id)
              AND j.state IN ('PENDING','PROCESSING','FAILED_RETRYABLE')
              AND (j.job_type<>'VINTED_DEEP_ENRICHMENT' OR j.source='MANUAL_RECOVERY')) THEN 1 ELSE 0 END AS verified,
          CASE WHEN d.lifecycle='ACTIVE' AND d.verification_state IN ('OK','USER_CONFIRMED')
            AND d.listing_type IN ('BASE_GAME','EXPANSION','GAME')
            AND d.tier IN ('hot','good','offer','fair','insufficient','hunt')
            AND d.rating>=6 AND d.bgg_id=g.bgg_id
            AND COALESCE(d.vinted_item_id,'')<>'' AND COALESCE(d.vinted_url,'')<>''
            AND COALESCE(d.benchmark_cents,0)>0 AND COALESCE(d.total_cents,0)>0
            AND d.discount IS NOT NULL THEN 1 ELSE 0 END AS priced,
          CASE WHEN l.lifecycle='AUTO_FILTERED' OR l.enrichment_state='AUTO_FILTERED' THEN COALESCE(l.match_state,'AUTO_FILTERED')
            WHEN l.lifecycle IN ('SOLD','REMOVED','USER_HIDDEN','UNKNOWN','RESET_LEGACY') THEN l.lifecycle
            WHEN g.match_state='BGG_MATCH_REVIEW' THEN 'BGG_MATCH_REVIEW'
            WHEN g.rating<6 THEN 'BELOW_RATING'
            WHEN g.rating IS NULL AND g.bgg_id IS NOT NULL THEN 'RATING_PENDING'
            ELSE COALESCE(l.enrichment_state,'PENDING_ANALYSIS') END AS reason,
          COALESCE(l.manual_review_required,0) AS manual_review,o.first_at
          FROM cohort o LEFT JOIN market_listings l ON l.id=(SELECT MIN(m.id) FROM market_listings m
            WHERE (m.legacy_signature=o.signature AND m.legacy_signature<>'')
              OR ((m.legacy_signature IS NULL OR m.legacy_signature='') AND m.temp_fingerprint=o.signature))
          LEFT JOIN games g ON g.id=l.game_id LEFT JOIN deals d ON d.signature=o.signature
        ), listing AS (
          SELECT identity,MIN(signature) AS signature,MAX(title) AS title,
          MAX(bgg) AS bgg,MAX(bgg*linked) AS linked,
          MAX(bgg*linked*verified) AS verified,MAX(bgg*linked*verified*priced) AS ready,
          MAX(reason) AS reason,MAX(manual_review) AS manual_review,MIN(first_at) AS first_at
          FROM candidates GROUP BY identity
        )
        SELECT identity,signature,title,1,bgg,linked,verified,ready,reason,manual_review,first_at
          FROM listing ORDER BY title COLLATE NOCASE,identity
        """;}
}
