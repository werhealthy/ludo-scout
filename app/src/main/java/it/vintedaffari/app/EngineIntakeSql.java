package it.vintedaffari.app;
/** Current global intake, in announcement units. Analysis completion supersedes historical sightings. */
final class EngineIntakeSql {
 private EngineIntakeSql(){}
 static String rows(){return "SELECT 'listing:'||l.id AS identity,COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) AS signature,0 AS game_id,0 AS phase,0 AS busy,l.vinted_title AS title FROM market_listings l WHERE l.lifecycle='ACTIVE' AND l.enrichment_state='PENDING_ANALYSIS' UNION ALL SELECT 'observed:'||o.signature,o.signature,0,0,0,MAX(o.vinted_title) FROM observations o WHERE o.signature IS NOT NULL AND o.signature<>'' AND o.analysis_status='pending' AND o.verification_state='PENDING_ANALYSIS' AND NOT EXISTS(SELECT 1 FROM market_listings l WHERE (l.legacy_signature=o.signature AND l.legacy_signature<>'') OR ((l.legacy_signature IS NULL OR l.legacy_signature='') AND l.temp_fingerprint=o.signature)) AND NOT EXISTS(SELECT 1 FROM observations newer WHERE newer.signature=o.signature AND (newer.observed_at>o.observed_at OR (newer.observed_at=o.observed_at AND newer.id>o.id))) GROUP BY o.signature";}
 static String count(){return "SELECT COUNT(*) FROM ("+rows()+")";}
}
