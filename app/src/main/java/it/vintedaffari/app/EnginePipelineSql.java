package it.vintedaffari.app;

/** Read-only, mutually exclusive current phases for one observation scroll.
 * Recognized identities merge their listings; unrecognized observations remain distinct.
 * A game takes its furthest verified listing phase. Review/holds never enter the ready total. */
final class EnginePipelineSql {
    private EnginePipelineSql() {}
    static String grouped(){
        String bgg="g.rating>=6.0 AND COALESCE(g.bgg_id,'')<>'' AND g.match_state='MATCHED'";
        String linked="COALESCE(l.vinted_item_id,'')<>'' AND COALESCE(l.vinted_url,'')<>''";
        String pending="EXISTS(SELECT 1 FROM processing_jobs j WHERE j.listing_id=l.id AND (j.job_type<>'VINTED_DEEP_ENRICHMENT' OR j.source='MANUAL_RECOVERY') AND j.state IN ('PENDING','PROCESSING','FAILED_RETRYABLE'))";
        String published="d.lifecycle='ACTIVE' AND d.verification_state IN ('OK','USER_CONFIRMED') AND d.listing_type IN ('BASE_GAME','EXPANSION','GAME') AND d.tier IN ('hot','good','offer','fair','insufficient','hunt') AND d.rating>=6.0 AND d.bgg_id=g.bgg_id AND COALESCE(d.vinted_item_id,'')<>'' AND COALESCE(d.vinted_url,'')<>''";
        String ready=published+" AND "+bgg+" AND "+linked+" AND l.enrichment_state IN ('COMPLETE','CORE_COMPLETE') AND l.match_state='MATCHED' AND NOT "+pending;
        String stage="CASE WHEN l.id IS NULL OR g.id IS NULL OR l.enrichment_state='PENDING_ANALYSIS' THEN 0 WHEN "+ready+" THEN 4 WHEN "+bgg+" AND ("+linked+" OR EXISTS(SELECT 1 FROM processing_jobs j WHERE j.listing_id=l.id AND j.job_type='VINTED_ENRICHMENT') OR l.enrichment_state='DEFERRED_LINK') THEN 3 WHEN "+bgg+" THEN 2 WHEN g.id IS NOT NULL AND COALESCE(l.enrichment_state,'')<>'PENDING_ANALYSIS' THEN 1 ELSE 0 END";
        String identity="CASE WHEN g.match_state='MATCHED' AND COALESCE(g.bgg_id,'')<>'' THEN 'bgg:'||g.bgg_id WHEN g.id IS NOT NULL THEN 'game:'||g.id ELSE 'observed:'||o.signature END";
        return "SELECT "+identity+" AS identity,MIN(o.signature) AS signature,MAX(COALESCE(NULLIF(o.display_name,''),NULLIF(o.game_name,''),NULLIF(o.vinted_title,''),'Annuncio osservato')) AS title,MIN(COALESCE(g.id,0)) AS game_id,MAX(CASE WHEN EXISTS(SELECT 1 FROM processing_jobs j WHERE (j.listing_id=l.id OR j.game_id=g.id) AND j.state='PROCESSING' AND (j.job_type<>'VINTED_DEEP_ENRICHMENT' OR j.source='MANUAL_RECOVERY')) THEN 1 ELSE 0 END) AS busy,MAX("+stage+") AS phase FROM observations o "+
            "LEFT JOIN market_listings l ON COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint)=o.signature "+
            "LEFT JOIN games g ON g.id=l.game_id LEFT JOIN deals d ON d.signature=o.signature "+
            "WHERE o.observed_at>=? AND o.observed_at<=? AND o.signature IS NOT NULL "+
            "AND (l.id IS NULL OR (l.lifecycle='ACTIVE' AND COALESCE(l.enrichment_state,'') NOT IN ('AUTO_EXCLUDED','AUTO_FILTERED','NEEDS_REVIEW','LOCAL_ONLY') AND COALESCE(l.manual_review_required,0)=0 AND COALESCE(l.match_state,'')<>'BGG_VARIANT_REVIEW')) "+
            "AND (g.id IS NULL OR (g.database_visible=1 AND (g.rating IS NULL OR g.rating>=6.0) AND COALESCE(g.match_state,'')<>'BGG_MATCH_REVIEW')) "+
            "AND (d.signature IS NULL OR d.lifecycle='ACTIVE') AND COALESCE(d.verification_state,'') NOT IN ('BGG_VARIANT_REVIEW','MATCH_UNCERTAIN','PRICE_ANOMALY','EXPANSION_CHECK') "+
            "GROUP BY identity";
    }
    static String query(){return "SELECT phase,COUNT(*) FROM ("+grouped()+") GROUP BY phase";}
    static String items(){return "SELECT identity,signature,game_id,phase,busy,title FROM ("+grouped()+") WHERE (CAST(? AS INTEGER)<0 AND phase<4) OR phase=CAST(? AS INTEGER) ORDER BY title COLLATE NOCASE,identity";}
    static String active(){return "SELECT DISTINCT phase FROM ("+grouped()+") WHERE busy=1";}
}
