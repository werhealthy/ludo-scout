package it.vintedaffari.app;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Rect;
import android.os.Bundle;
import java.io.File;
import org.json.JSONArray;
import org.json.JSONObject;

/** Offline native replay. Every database/file path is isolated from the installed catalog. */
public final class AiRecoveryInstrumentation extends Instrumentation {
 private Bundle arguments;
 @Override public void onCreate(Bundle arguments){super.onCreate(arguments);this.arguments=arguments;start();}
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 private static String value(SQLiteDatabase db,String sql){try(Cursor c=db.rawQuery(sql,null)){return c.moveToFirst()?c.getString(0):null;}}
 private static final class IsolatedContext extends ContextWrapper {
  final File root;
  IsolatedContext(Context base){super(base);root=new File(base.getCacheDir(),"ai-recovery-test-"+java.util.UUID.randomUUID());check(root.mkdirs(),"cannot create isolated test directory");}
  @Override public Context getApplicationContext(){return this;}
  @Override public File getDatabasePath(String name){return new File(root,name);}
  @Override public File getFilesDir(){return root;}
  @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory){return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name),factory);}
  @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,DatabaseErrorHandler handler){return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).getPath(),factory,handler);}
 }
 private static void fixture(SQLiteDatabase db,String title){
  String reason=title.contains("tavolo")?"Nessun candidato BGG sufficientemente forte: scarto automatico, non fact-check":"Nessuna prova positiva di prodotto gioco da tavolo";
  db.execSQL("INSERT INTO market_listings(id,temp_fingerprint,legacy_signature,vinted_item_id,vinted_title,brand,current_price_cents,observed_text,listing_photos_csv,vinted_url,lifecycle,enrichment_state,match_state,last_error,first_seen,last_seen) VALUES(1,'fixture','fixture','101',?,'Fixture publisher',1000,?,'https://images1.vinted.net/t/fixture.webp','https://www.vinted.it/items/101-fixture','AUTO_FILTERED','AUTO_FILTERED','AUTO_FILTERED_NON_GAME',?,100,100)",new Object[]{title,title,reason});
  db.execSQL("INSERT INTO observations(signature,observed_at,vinted_title,brand,analysis_status,verification_state,verification_reason) VALUES('fixture',100,?,'Fixture publisher','pending','BLOCKED_CLASSIFIER','original classifier')",new Object[]{title});
  db.execSQL(BrowserIntakeSql.PROVENANCE,new Object[]{"browser_listing:1",1,100,"browser-public-capture-v1"});
 }
 private static JSONObject response(JSONArray snapshot)throws Exception {
  JSONArray answers=new JSONArray();
  for(int i=0;i<snapshot.length();i++)answers.put(new JSONObject().put("listing_id",snapshot.getJSONObject(i).getLong("listing_id"))
   .put("proposed_type","BASE_GAME").put("product_title","Gioco identificabile").put("confidence",90)
   .put("evidence","Confezione del gioco visibile; Componenti del gioco visibili")
   .put("language","UNKNOWN").put("needs_review",true).put("apply_authorized",false).put("bgg_verified",false));
  return new JSONObject().put("status","PROPOSAL").put("records",answers);
 }
 private static void replay(Context base,String title,boolean matched,boolean oldQuarantine)throws Exception {
  IsolatedContext context=new IsolatedContext(base);
  check(!context.getDatabasePath("vinted_affari.db").equals(base.getDatabasePath("vinted_affari.db")),"test database is not isolated");
  try(DealDatabase helper=new DealDatabase(context)){
   SQLiteDatabase db=helper.getWritableDatabase();fixture(db,title);MarketStore market=new MarketStore(context,helper);
   if("Gioco senza marca".equals(title)){db.execSQL("UPDATE market_listings SET brand=NULL,observed_text=NULL");db.execSQL("UPDATE observations SET brand=NULL");}
   AiEngineListings source=new AiEngineListings(db);JSONArray rows=source.select(new JSONObject().put("seen",new JSONObject()),1000);
   check(rows.length()==1,"real filtered state not selectable");source.apply(rows,response(rows));
   check(source.recoveredCount()==1,"visual recovery not committed");
   check("ACTIVE".equals(value(db,"SELECT lifecycle FROM market_listings WHERE id=1")),"recovery remained filtered");
   check(value(db,"SELECT game_id FROM market_listings WHERE id=1")==null,"AI assigned a game");
   check("100".equals(value(db,"SELECT observed_at FROM observations WHERE signature='fixture'")),"recovery changed observation time");
   check("0".equals(value(db,"SELECT COUNT(*) FROM deals")),"AI created deal trust");
   if(oldQuarantine)db.execSQL("INSERT INTO games(provisional_key,canonical_name,normalized_name,match_state,database_visible,first_seen,last_seen) VALUES(?, ?, ?,'AUTO_QUARANTINED',0,100,100)",new Object[]{title.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim(),title,title.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim()});
   VintedCard card=market.pendingAnalysisCards(8).get(0);
   check(market.hasAiCategoryRecoveryEvidence(card),"recovery proof missing before analysis");
   ListingClassifier.Result product=ListingClassifier.classify(card);
   JSONObject result=new JSONObject().put("status",matched?"matched":"unmatched");
   if(matched)result.put("game",new JSONObject().put("name","Fixture game").put("bggId","123").put("averageRating",8.0));
   GameAnalysis analysis=GameAnalysis.fromJson(result);
   if(!matched)check(BoardGameIntakeGate.afterAnalysis(card,product,analysis,market.hasAiCategoryRecoveryEvidence(card)).action==BoardGameIntakeGate.Action.ACCEPT,"BGG absence erased product proof");
   check(market.commitAiRecoveredAnalysis(card,analysis,product,200,false),"browser commit refused recovered ACTIVE row");
   check(market.hasAiCategoryRecoveryEvidence(card),"analysis erased or consumed product proof");
   check(market.commitAiRecoveredAnalysis(card,analysis,product,300,false),"unchanged repeated commit refused");
   check(market.hasAiCategoryRecoveryEvidence(card),"repeated analysis erased proof");
   helper.recordSighting(card,product,700000);
   check(market.hasAiCategoryRecoveryEvidence(card),"unchanged rediscovery erased proof");
   check("ACTIVE".equals(value(db,"SELECT lifecycle FROM market_listings WHERE id=1")),"local analysis returned to filtered");
   check("1000".equals(value(db,"SELECT current_price_cents FROM market_listings WHERE id=1")),"recovery changed price");
   if(matched){
    check("123".equals(value(db,"SELECT bgg_id FROM games WHERE id=(SELECT game_id FROM market_listings WHERE id=1)")),"local BGG match not retained");
    long gameId=Long.parseLong(value(db,"SELECT game_id FROM market_listings WHERE id=1"));
    check(!market.autoQuarantineGame(gameId,"Nessun candidato BGG locale"),"saved BGG identity quarantined");
    check("MATCHED".equals(value(db,"SELECT match_state FROM market_listings WHERE id=1")),"saved BGG identity downgraded by product marker");
   }
   else{
    check(value(db,"SELECT bgg_id FROM games WHERE id=(SELECT game_id FROM market_listings WHERE id=1)")==null,"AI supplied BGG identity");
    String state=value(db,"SELECT match_state FROM market_listings WHERE id=1");
    check("BGG_MATCH_REQUIRED".equals(state)||"BGG_MATCH_REVIEW".equals(state),"unresolved identity has no BGG state");
    long gameId=Long.parseLong(value(db,"SELECT game_id FROM market_listings WHERE id=1"));
    check(!market.autoQuarantineGame(gameId,"Nessun candidato BGG locale"),"background identity failure filtered visual-positive product");
    check("ACTIVE".equals(value(db,"SELECT lifecycle FROM market_listings WHERE id=1")),"background matcher returned to filtered");
    check("BGG_MATCH_REVIEW".equals(value(db,"SELECT match_state FROM market_listings WHERE id=1")),"missing identity not retained in review");
    check("NEEDS_REVIEW".equals(value(db,"SELECT enrichment_state FROM market_listings WHERE id=1")),"review enrichment state inconsistent");
   }
   for(String sql:new String[]{"UPDATE market_listings SET observed_text='Changed source' WHERE id=1","UPDATE market_listings SET listing_photos_csv='https://images1.vinted.net/t/changed.webp' WHERE id=1","UPDATE market_listings SET category_normalized='books' WHERE id=1","UPDATE market_listings SET manual_review_required=1 WHERE id=1","INSERT INTO listing_overrides(signature,excluded) VALUES('fixture',1)"}){
    db.execSQL("SAVEPOINT protection");db.execSQL(sql);
    check(!market.hasAiCategoryRecoveryEvidence(card),"stale/protected visual evidence accepted: "+sql);
    db.execSQL("ROLLBACK TO protection");db.execSQL("RELEASE protection");
   }
  }
 }
 /** Inspect the real catalog read-only. No helper, schema upgrade, settings write or network call. */
 private JSONObject auditLive()throws Exception {
  String ids=arguments.getString("ids","");check(ids.matches("[0-9]+(,[0-9]+)*"),"explicit listing IDs required");
  JSONArray rows=new JSONArray();int positive=0,filtered=0,ready=0;
  java.lang.reflect.Method evidence=MarketStore.class.getDeclaredMethod("hasAiCategoryRecoveryEvidence",SQLiteDatabase.class,long.class,VintedCard.class);evidence.setAccessible(true);
  MarketStore market=new MarketStore(getTargetContext(),null);
  File file=getTargetContext().getDatabasePath("vinted_affari.db");check(file.isFile(),"installed catalog absent");
  JSONObject report=new JSONObject();
  try(SQLiteDatabase db=SQLiteDatabase.openDatabase(file.getPath(),null,SQLiteDatabase.OPEN_READONLY)){
   for(String id:ids.split(",")){
    String select=AiBetaListings.SELECTION_SQL.substring(0,AiBetaListings.SELECTION_SQL.indexOf(" WHERE "))+" WHERE l.id=?";
    JSONObject item;
    try(Cursor c=db.rawQuery(select,new String[]{id})){if(!c.moveToFirst())continue;item=AiBetaListings.row(c);}
    boolean currentMarker;
    try(Cursor c=db.rawQuery("SELECT 1 FROM observations o JOIN market_listings l ON o.signature=COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) WHERE l.id=? AND o.verification_reason LIKE ? LIMIT 1",new String[]{id,"AI category recovery:"+item.getString("input_key")+":%"})){currentMarker=c.moveToFirst();}
    boolean valid=(Boolean)evidence.invoke(market,db,Long.parseLong(id),null);
    JSONObject row=new JSONObject().put("id",Long.parseLong(id)).put("title",item.getString("title"))
      .put("lifecycle",item.getString("lifecycle")).put("match_state",item.getString("listing_match_state"))
      .put("bgg_id",item.getString("bgg_id")).put("game_match_state",item.getString("game_match_state"))
      .put("current_visual_marker",currentMarker).put("valid_active_product_evidence",valid);
    try(Cursor c=db.rawQuery("SELECT enrichment_state,last_error,current_price_cents,manual_review_required FROM market_listings WHERE id=?",new String[]{id})){
     c.moveToFirst();row.put("enrichment_state",c.getString(0)).put("last_error",c.getString(1)).put("price_cents",c.getInt(2)).put("manual_review_required",c.getInt(3));
    }
    if(currentMarker){positive++;if("AUTO_FILTERED".equals(item.getString("lifecycle")))filtered++;}
    String state=item.getString("listing_match_state");
    if(valid&&("BGG_MATCH_REQUIRED".equals(state)||"BGG_MATCH_REVIEW".equals(state)||("MATCHED".equals(state)&&!item.getString("bgg_id").isEmpty())))ready++;
    rows.put(row);
   }
   try(Cursor c=db.rawQuery("SELECT text_value FROM queue_controls WHERE name='diag:ai_engine'",null)){if(c.moveToFirst())report.put("ai_engine",c.getString(0));}
  }
  return report.put("listings",rows).put("found",rows.length()).put("ai_positive",positive).put("ai_positive_filtered",filtered).put("ready_for_bgg",ready)
   .put("runtime_acceptance",positive>0&&filtered==0&&ready==positive);
 }
 @Override public void onStart(){
  Bundle result=new Bundle();
  try{
   if(arguments!=null&&"audit".equals(arguments.getString("mode"))){result.putString("report",auditLive().toString());finish(-1,result);return;}
   replay(getTargetContext(),"Indovina Chi? - gioco da tavolo",false,false);
   replay(getTargetContext(),"Cluedo Junior",false,false);
   replay(getTargetContext(),"Gioco da tavolo quiz",true,false);
   replay(getTargetContext(),"Dobbel kaart spelletje",false,true);
   replay(getTargetContext(),"Gioco senza marca",false,false);
   result.putString("stream","PASS isolated native AI recovery -> local analysis -> BGG states; stale/manual/category protections\n");finish(-1,result);
  }catch(Throwable error){result.putString("stream",android.util.Log.getStackTraceString(error));finish(0,result);}
 }
}
