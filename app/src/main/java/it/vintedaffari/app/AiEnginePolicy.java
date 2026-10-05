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
  boolean bounced="AUTO_FILTERED".equals(enrichment)
    && "AUTO_FILTERED_NON_GAME".equals(row.optString("listing_match_state"))
    && "Nessuna prova positiva di prodotto gioco da tavolo".equals(row.optString("engine_last_error"));
  if(!original&&!bounced)return false;
  if(!"UNCERTAIN".equals(row.optString("local_type")))return false;
  if(!row.optString("bgg_id").isEmpty())return false;
  if(row.optInt("engine_manual_review",0)!=0||row.optInt("engine_confirmed",0)!=0)return false;
  if(!row.optBoolean("engine_has_observation",false))return false;
  String category=row.optString("engine_category","");
  if(!category.isEmpty()&&ListingClassifier.isExplicitNonGameCategory(category))return false;
  JSONArray photos=row.optJSONArray("photos");
  return photos!=null&&photos.length()>0;
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
