package it.vintedaffari.app;

import org.json.JSONArray;
import org.json.JSONObject;

/** AI may withdraw automatic trust or reopen a narrowly-scoped automatic exclusion.
 * It never establishes BGG identity, language truth, pricing truth or a human decision.
 */
final class AiEnginePolicy {
 static final long TTL=7L*86400000, BACKOFF=15L*60000;
 private AiEnginePolicy(){}

 static boolean hold(String local,String proposed){
  return ("BASE_GAME".equals(local)||"UNCERTAIN".equals(local))
    && java.util.Arrays.asList("NON_GAME","EXPANSION","BUNDLE","ACCESSORY_COMPONENT").contains(proposed);
 }

 static boolean recoveryCandidate(JSONObject row){
  if(row==null)return false;
  if(!"AUTO_FILTERED".equals(row.optString("lifecycle")))return false;
  String enrichment=row.optString("engine_enrichment");
  boolean original="AUTO_EXCLUDED".equals(enrichment);
  boolean gap="AUTO_FILTERED".equals(enrichment)&&productEvidenceGap(row.optString("listing_match_state"),row.optString("engine_last_error"));
  if(!original&&!gap)return false;
  String type=row.optString("local_type");
  if(!"UNCERTAIN".equals(type)&&!(gap&&"BASE_GAME".equals(type)))return false;
  if(!row.optString("bgg_id").isEmpty())return false;
  if(row.optInt("engine_manual_review",0)!=0||row.optInt("engine_confirmed",0)!=0)return false;
  if(!row.optBoolean("engine_has_observation",false))return false;
  String category=row.optString("engine_category","");
  if(!category.isEmpty()&&ListingClassifier.isExplicitNonGameCategory(category))return false;
  JSONArray photos=row.optJSONArray("photos");
  return photos!=null&&photos.length()>0;
 }

 // Only the demonstrated absence-of-evidence branches are recoverable, never price/type exclusions.
 static final String EVIDENCE_GAP_SQL="((l.match_state='AUTO_FILTERED_NON_GAME' AND l.last_error IN ('Nessuna prova positiva di prodotto gioco da tavolo','Nessun candidato BGG sufficientemente forte: scarto automatico, non fact-check')) OR (l.match_state='AUTO_FILTERED_COLLISION' AND l.last_error='Il titolo coincide con BGG ma manca una prova indipendente che l''oggetto sia un gioco da tavolo'))";
 static boolean productEvidenceGap(String match,String reason){
  return ("AUTO_FILTERED_NON_GAME".equals(match)&&("Nessuna prova positiva di prodotto gioco da tavolo".equals(reason)||"Nessun candidato BGG sufficientemente forte: scarto automatico, non fact-check".equals(reason)))
   ||("AUTO_FILTERED_COLLISION".equals(match)&&"Il titolo coincide con BGG ma manca una prova indipendente che l'oggetto sia un gioco da tavolo".equals(reason));
 }

 static boolean recover(JSONObject row,JSONObject answer){
  if(!recoveryCandidate(row)||answer==null)return false;
  if(!"BASE_GAME".equals(answer.optString("proposed_type")))return false;
  String title=answer.optString("product_title","").trim();
  if(title.length()<3)return false;
  String evidence=answer.optString("evidence","").trim();
  if(evidence.isEmpty()||evidence.startsWith("Proposta basata sui dati disponibili:"))return false;
  int strong=0;
  for(String part:evidence.split("[;\\n]"))if(part.trim().length()>=8)strong++;
  return strong>=2;
 }

 static boolean fresh(long at,long now){return at>0&&at<=now&&now-at<TTL;}
}
