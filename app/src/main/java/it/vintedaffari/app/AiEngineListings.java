package it.vintedaffari.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;

/** Automatic AI policy over canonical listings.
 * Negative disagreement can withdraw automatic trust. Positive visual evidence may only reopen a
 * narrow AUTO_EXCLUDED + local-UNCERTAIN case for the existing local/BGG pipeline to verify again.
 */
final class AiEngineListings implements AiEngineSession.Source {
 private final SQLiteDatabase db;
 private int recovered,refreshed;
 private static final String SELECT=AiBetaListings.SELECTION_SQL.substring(0,AiBetaListings.SELECTION_SQL.indexOf(" FROM "))
   +",COALESCE(d.verification_state,''),COALESCE(d.confirmed,0),COALESCE(l.manual_review_required,0),"
   +"COALESCE(l.enrichment_state,''),COALESCE(l.category_normalized,''),COALESCE(l.last_error,''),"
   +"CASE WHEN EXISTS(SELECT 1 FROM observations o WHERE o.signature=COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint)) THEN 1 ELSE 0 END,"
   +"CASE WHEN EXISTS(SELECT 1 FROM observations o WHERE o.signature=COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) AND COALESCE(o.verification_reason,'') LIKE 'AI category recovery:%') THEN 1 ELSE 0 END,"
   +"CASE WHEN EXISTS(SELECT 1 FROM observations o WHERE o.id=(SELECT x.id FROM observations x WHERE x.signature=COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) ORDER BY x.observed_at DESC,x.id DESC LIMIT 1) AND COALESCE(o.verification_reason,'')='AI category recovery: base game visually recognized; BGG pending') THEN 1 ELSE 0 END "
   +"FROM market_listings l LEFT JOIN games g ON g.id=l.game_id "
   +"LEFT JOIN deals d ON d.signature=COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) ";
 private static final String PROTECTED="NOT EXISTS(SELECT 1 FROM listing_overrides u WHERE u.signature=COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) OR (u.item_id IS NOT NULL AND u.item_id=l.vinted_item_id))";
 private static final String COMMON="l.id>0 AND TRIM(COALESCE(l.vinted_title,''))<>'' AND COALESCE(l.manual_review_required,0)=0 AND COALESCE(d.confirmed,0)=0 AND COALESCE(d.verification_state,'')<>'USER_CONFIRMED' AND "+PROTECTED;
 private static final String RECOVERABLE=AiEnginePolicy.RECOVERABLE_SQL+" AND COALESCE(g.bgg_id,'')='' AND EXISTS(SELECT 1 FROM observations o WHERE o.signature=COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint))";
 private static final String ELIGIBLE=COMMON+" AND (l.lifecycle='ACTIVE' OR ("+RECOVERABLE+"))";

 AiEngineListings(SQLiteDatabase db){this.db=db;}
 int recoveredCount(){return recovered;}\n int refreshedCount(){return refreshed;}

 /** Called by legacy upsert inside its writer transaction; user overrides are applied afterward. */
 static void preserveHold(SQLiteDatabase db,String signature,ContentValues incoming){
  if(!"OK".equals(incoming.getAsString("verification_state")))return;
  try(Cursor c=db.rawQuery("SELECT vinted_title,brand,bgg_id,verification_reason FROM deals WHERE signature=? AND lifecycle='ACTIVE' AND verification_state='MATCH_UNCERTAIN' AND verification_reason LIKE 'AI_CATEGORY_REVIEW:%' AND COALESCE(confirmed,0)=0",new String[]{signature})){
   if(c.moveToFirst()&&java.util.Objects.equals(c.getString(0),incoming.getAsString("vinted_title"))
      &&java.util.Objects.equals(c.getString(1),incoming.getAsString("brand"))&&java.util.Objects.equals(c.getString(2),incoming.getAsString("bgg_id"))){
    incoming.put("verification_state","MATCH_UNCERTAIN");incoming.put("verification_reason",c.getString(3));
   }
  }
 }

 private static JSONObject row(Cursor c)throws Exception {
  return AiBetaListings.row(c)
    .put("engine_verification",c.getString(12))
    .put("engine_confirmed",c.getInt(13))
    .put("engine_manual_review",c.getInt(14))
    .put("engine_enrichment",c.getString(15))
    .put("engine_category",c.getString(16))
    .put("engine_last_error",c.getString(17))
    .put("engine_has_observation",c.getInt(18)!=0)
    .put("engine_ai_recovered",c.getInt(19)!=0)
    .put("engine_latest_ai_recovered",c.getInt(20)!=0);
 }

 @Override public JSONArray select(JSONObject j,long now)throws Exception {
  JSONArray selected=new JSONArray();JSONObject seen=j.getJSONObject("seen");
  long after=j.optLong("cursor");int scanned=0;j.put("scan_more",false);
  try(Cursor c=db.rawQuery(SELECT+"WHERE "+ELIGIBLE+" AND l.id>? ORDER BY l.id LIMIT 128",new String[]{String.valueOf(after)})){
   while(c.moveToNext()){
    scanned++;JSONObject r=row(c);j.put("cursor",r.getLong("listing_id"));
    JSONObject done=seen.optJSONObject(AiEngineSession.localKey(r));
    if(done!=null&&AiEnginePolicy.fresh(done.optLong("at"),now))continue;
    // Filtered history gets remote work only when the cheap local prerequisites already satisfy
    // the conservative recovery policy. Everything else is remembered without a Qwen call.
    if("AUTO_FILTERED".equals(r.optString("lifecycle"))&&!AiEnginePolicy.recoveryCandidate(r)){
     seen.put(AiEngineSession.localKey(r),new JSONObject().put("at",now));continue;
    }
    JSONArray trial=new JSONArray(selected.toString()).put(r);
    try{AiBetaListings.payload(trial);}catch(Exception oversized){
     if(selected.length()>0){j.put("cursor",r.getLong("listing_id")-1);j.put("scan_more",true);return selected;}
     seen.put(AiEngineSession.localKey(r),new JSONObject().put("at",now));j.put("oversized",j.optInt("oversized")+1);continue;
    }
    selected=trial;
    if(selected.length()==8){j.put("scan_more",true);return selected;}
   }
  }
  if(scanned==128)j.put("scan_more",true);else j.put("cursor",0);
  return selected;
 }

 @Override public boolean current(JSONArray snapshot)throws Exception {
  if(snapshot.length()<1||snapshot.length()>8)return false;
  for(int i=0;i<snapshot.length();i++){
   JSONObject before=snapshot.getJSONObject(i);
   try(Cursor c=db.rawQuery(SELECT+"WHERE "+ELIGIBLE+" AND l.id=? LIMIT 1",new String[]{String.valueOf(before.getLong("listing_id"))})){
    if(!c.moveToFirst()||!before.toString().equals(row(c).toString()))return false;
   }
  }
  return true;
 }

 @Override public int apply(JSONArray snapshot,JSONObject response)throws Exception {
  AiBetaListings.display(snapshot,response);
  int held=0;db.beginTransaction();
  try{
   if(!current(snapshot)){db.setTransactionSuccessful();return 0;}
   JSONArray answers=response.getJSONArray("records");
   for(int i=0;i<snapshot.length();i++){
    JSONObject r=snapshot.getJSONObject(i),answer=null;
    for(int k=0;k<answers.length();k++)if(answers.getJSONObject(k).getLong("listing_id")==r.getLong("listing_id"))answer=answers.getJSONObject(k);
    if(answer==null)continue;
    String id=String.valueOf(r.getLong("listing_id"));

    if(AiEnginePolicy.recover(r,answer)){
     String signature="";
     try(Cursor c=db.rawQuery("SELECT COALESCE(NULLIF(legacy_signature,''),temp_fingerprint) FROM market_listings WHERE id=?",new String[]{id})){if(c.moveToFirst())signature=c.getString(0);}
     ContentValues listing=new ContentValues();
     listing.put("lifecycle","ACTIVE");
     listing.put("enrichment_state","PENDING_ANALYSIS");
     listing.put("match_state","PENDING_ANALYSIS");
     listing.putNull("game_id");listing.putNull("match_confidence");
     listing.put("deferred_retry_at",0);listing.put("manual_review_required",0);listing.putNull("manual_review_reason");
     listing.put("last_error","AI_CATEGORY_RECOVERED: gioco base riconosciuto da evidenza visiva; identità BGG ancora da verificare");
     // current(snapshot) was checked inside this writer transaction, including overrides/BGG.
     int changed=db.update("market_listings",listing,"id=? AND lifecycle='AUTO_FILTERED' AND COALESCE(manual_review_required,0)=0",new String[]{id});
     if(changed>0&&!signature.isEmpty()){
      AiCategoryEvidence.remember(db,r.getLong("listing_id"),r.getString("input_key"),System.currentTimeMillis());
      // Re-open the existing sighting instead of manufacturing a new observation timestamp.
      ContentValues observation=new ContentValues();observation.put("analysis_status","pending");
      // Qwen owns product category only. Persist BASE_GAME as category evidence while identity
      // remains explicitly uncertain until the independent BGG matcher succeeds.
      observation.put("listing_type","BASE_GAME");observation.put("verification_state","MATCH_UNCERTAIN");
      observation.put("verification_reason","AI category recovery: base game visually recognized; BGG pending");
      db.update("observations",observation,"id=(SELECT id FROM observations WHERE signature=? ORDER BY observed_at DESC,id DESC LIMIT 1)",new String[]{signature});
      recovered+=changed;
     }
     continue;
    }

    if(AiEnginePolicy.refreshRecoveredProductEvidence(r,answer)){
     String signature="";
     try(Cursor c=db.rawQuery("SELECT COALESCE(NULLIF(legacy_signature,''),temp_fingerprint) FROM market_listings WHERE id=?",new String[]{id})){if(c.moveToFirst())signature=c.getString(0);}
     if(!signature.isEmpty()){
      AiCategoryEvidence.remember(db,r.getLong("listing_id"),r.getString("input_key"),System.currentTimeMillis());
      ContentValues observation=new ContentValues();observation.put("analysis_status","pending");
      observation.put("listing_type","BASE_GAME");observation.put("verification_state","MATCH_UNCERTAIN");
      observation.put("verification_reason","AI category recovery: base game visually recognized; BGG pending");
      refreshed+=db.update("observations",observation,
       "id=(SELECT id FROM observations WHERE signature=? ORDER BY observed_at DESC,id DESC LIMIT 1)",
       new String[]{signature});
     }
     continue;
    }

    if(!AiEnginePolicy.hold(r.getString("local_type"),answer.getString("proposed_type")))continue;
    ContentValues hold=new ContentValues();
    hold.put("verification_state","MATCH_UNCERTAIN");
    hold.put("verification_reason","AI_CATEGORY_REVIEW: tipo locale e proposta AI da chiarire; identità BGG non verificata");
    // Only existing automatic OK trust may be withdrawn. Human decisions and stronger holds survive.
    int changed=db.update("deals",hold,"lifecycle='ACTIVE' AND verification_state='OK' AND COALESCE(confirmed,0)=0 AND signature=(SELECT COALESCE(NULLIF(legacy_signature,''),temp_fingerprint) FROM market_listings WHERE id=?)",new String[]{id});
    if(changed>0){
     ContentValues listing=new ContentValues();listing.put("last_error",hold.getAsString("verification_reason"));
     db.update("market_listings",listing,"id=? AND lifecycle='ACTIVE'",new String[]{id});held+=changed;
    }
   }
   db.setTransactionSuccessful();
  }finally{db.endTransaction();}
  return held;
 }
}
