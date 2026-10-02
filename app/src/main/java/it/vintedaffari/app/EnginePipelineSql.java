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
        String priced="COALESCE(d.benchmark_cents,0)>0 AND COALESCE(d.total_cents,0)>0 AND d.discount IS NOT NULL";
        String ready=published+" AND "+priced+" AND "+bgg+" AND "+linked+" AND l.enrichment_state IN ('COMPLETE','CORE_COMPLETE') AND l.match_state='MATCHED' AND NOT "+pending;
        String stage="CASE WHEN l.id IS NULL OR g.id IS NULL OR l.enrichment_state='PENDING_ANALYSIS' THEN 0 WHEN "+ready+" THEN 4 WHEN "+bgg+" AND ("+linked+" OR EXISTS(SELECT 1 FROM processing_jobs j WHERE j.listing_id=l.id AND j.job_type='VINTED_ENRICHMENT') OR l.enrichment_state='DEFERRED_LINK') THEN 3 WHEN "+bgg+" THEN 2 WHEN g.id IS NOT NULL AND COALESCE(l.enrichment_state,'')<>'PENDING_ANALYSIS' THEN 1 ELSE 0 END";
        String identity="CASE WHEN g.match_state='MATCHED' AND COALESCE(g.bgg_id,'')<>'' THEN 'bgg:'||g.bgg_id WHEN g.id IS NOT NULL THEN 'game:'||g.id ELSE 'observed:'||o.signature END";
        String rawPending="o.analysis_status='pending' AND o.verification_state='PENDING_ANALYSIS' AND NOT EXISTS(SELECT 1 FROM observations newer WHERE newer.signature=o.signature AND (newer.observed_at>o.observed_at OR (newer.observed_at=o.observed_at AND newer.id>o.id)))";
        String queued="l.enrichment_state='PENDING_ANALYSIS' OR (g.match_state='BGG_MATCH_REQUIRED' AND COALESCE(g.bgg_id,'')='') OR EXISTS(SELECT 1 FROM processing_jobs j WHERE (j.listing_id=l.id OR j.game_id=g.id) AND j.state IN ('PENDING','FAILED_RETRYABLE') AND (j.job_type<>'VINTED_DEEP_ENRICHMENT' OR j.source='MANUAL_RECOVERY'))";
        String candidates="SELECT "+identity+" AS identity,o.signature AS signature,MAX(COALESCE(NULLIF(o.display_name,''),NULLIF(o.game_name,''),NULLIF(o.vinted_title,''),'Annuncio osservato')) AS title,COALESCE(g.id,0) AS game_id,MAX(CASE WHEN EXISTS(SELECT 1 FROM processing_jobs j WHERE (j.listing_id=l.id OR j.game_id=g.id) AND j.state='PROCESSING' AND (j.job_type<>'VINTED_DEEP_ENRICHMENT' OR j.source='MANUAL_RECOVERY')) THEN 1 ELSE 0 END) AS busy,MAX("+stage+") AS phase,MAX(CASE WHEN "+queued+" THEN 1 ELSE 0 END) AS queued FROM observations o "+
            "LEFT JOIN market_listings l ON (l.legacy_signature=o.signature AND l.legacy_signature<>'') OR ((l.legacy_signature IS NULL OR l.legacy_signature='') AND l.temp_fingerprint=o.signature) "+
            "LEFT JOIN games g ON g.id=l.game_id LEFT JOIN deals d ON d.signature=o.signature "+
            "WHERE o.observed_at>=? AND o.observed_at<=? AND o.signature IS NOT NULL "+
            "AND (l.id IS NULL OR (l.lifecycle='ACTIVE' AND COALESCE(l.enrichment_state,'') NOT IN ('AUTO_EXCLUDED','AUTO_FILTERED','BLOCKED_CLASSIFIER','NEEDS_REVIEW','LOCAL_ONLY') AND COALESCE(l.manual_review_required,0)=0 AND COALESCE(l.match_state,'')<>'BGG_VARIANT_REVIEW')) "+
            "AND (l.id IS NOT NULL OR ("+rawPending+")) "+
            "AND (g.id IS NULL OR (g.database_visible=1 AND (g.rating IS NULL OR g.rating>=6.0) AND COALESCE(g.match_state,'')<>'BGG_MATCH_REVIEW')) "+
            "AND (d.signature IS NULL OR d.lifecycle='ACTIVE') AND COALESCE(d.verification_state,'') NOT IN ('BGG_VARIANT_REVIEW','MATCH_UNCERTAIN','PRICE_ANOMALY','EXPANSION_CHECK') "+
            "GROUP BY identity,o.signature,game_id";
        return "WITH candidates AS ("+candidates+") SELECT c.identity,c.signature,c.title,c.game_id,(SELECT MAX(b.busy) FROM candidates b WHERE b.identity=c.identity) AS busy,c.phase,(SELECT MAX(q.queued) FROM candidates q WHERE q.identity=c.identity) AS queued FROM candidates c WHERE NOT EXISTS(SELECT 1 FROM candidates better WHERE better.identity=c.identity AND (better.phase>c.phase OR (better.phase=c.phase AND (better.signature<c.signature OR (better.signature=c.signature AND better.game_id<c.game_id)))))";
    }
    static String query(){return "SELECT phase,COUNT(*) FROM ("+grouped()+") GROUP BY phase";}
    static String items(){return "SELECT identity,signature,game_id,phase,busy,title,queued FROM ("+grouped()+") WHERE (CAST(? AS INTEGER)<0 AND phase<4) OR phase=CAST(? AS INTEGER) ORDER BY title COLLATE NOCASE,identity";}
    static String active(){return "SELECT DISTINCT phase FROM ("+grouped()+") WHERE busy=1";}
    static String activeCounts(){return "SELECT phase,COUNT(*) FROM ("+grouped()+") WHERE busy=1 GROUP BY phase";}
    static String queuedCounts(){return "SELECT phase,COUNT(*) FROM ("+grouped()+") WHERE phase<4 AND queued=1 AND busy=0 GROUP BY phase";}
}
