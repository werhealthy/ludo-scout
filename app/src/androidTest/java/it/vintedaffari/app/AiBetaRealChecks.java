package it.vintedaffari.app;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
/** Executed inside the platform instrumentation: real SQLite, JSON and local classifier. */
final class AiBetaRealChecks {
 static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 static JSONObject answer(long id,String type)throws Exception{return new JSONObject().put("listing_id",id).put("proposed_type",type).put("confidence",JSONObject.NULL).put("evidence","Title evidence").put("needs_review",true).put("apply_authorized",false).put("bgg_verified",false).put("language","UNKNOWN");}
 static void rejected(JSONArray rows,JSONObject response)throws Exception {boolean rejected=false;try{AiBetaListings.display(rows,response);}catch(Exception expected){rejected=true;}check(rejected,"unsafe response accepted");}
 static void run()throws Exception {
  try(SQLiteDatabase db=SQLiteDatabase.create(null)){
   db.execSQL("CREATE TABLE market_listings(id INTEGER PRIMARY KEY,vinted_title TEXT,brand TEXT,item_condition TEXT,current_price_cents INTEGER,observed_text TEXT,game_id INTEGER,last_seen INTEGER,lifecycle TEXT,match_state TEXT)");db.execSQL("CREATE TABLE games(id INTEGER PRIMARY KEY,bgg_id TEXT,canonical_name TEXT,match_state TEXT)");
   check(AiBetaListings.read(db).length()==0,"empty DB");
   db.execSQL("INSERT INTO games VALUES(1,'123','Test game','BGG_MATCH_REVIEW')");
   db.execSQL("INSERT INTO market_listings VALUES(1,'Organizer base + expansions','',NULL,0,'',1,100,'AUTO_FILTERED','BLOCKED_CLASSIFIER')");
   db.execSQL("INSERT INTO market_listings VALUES(2,'Catan gioco da tavolo','Kosmos',NULL,0,'',NULL,101,'ACTIVE','PENDING_ANALYSIS')");
   JSONArray rows=AiBetaListings.read(db);check(rows.length()==2,"filtered or partial row missing");check(rows.getJSONObject(1).getString("local_type").equals("ACCESSORY"),"local subtype changed");
   JSONArray payload=AiBetaListings.payload(rows);check(payload.getJSONObject(0).getLong("listing_id")==1&&payload.getJSONObject(0).length()==3,"input not minimal/canonical");check(!payload.toString().contains("123")&&!payload.toString().contains("PENDING_ANALYSIS"),"private context transmitted");
   String key=AiBetaListings.key(rows);JSONArray reversed=new JSONArray().put(rows.getJSONObject(1)).put(rows.getJSONObject(0));check(key.equals(AiBetaListings.key(reversed)),"reordering consumed another ID");
   JSONObject response=new JSONObject().put("status","PROPOSAL").put("records",new JSONArray().put(answer(1,"ACCESSORY_COMPONENT")).put(answer(2,"BUNDLE"))).put("budget",new JSONObject().put("calls_reserved",9));
   String display=AiBetaListings.display(rows,response);check(display.contains("Accessorio")&&display.contains("categoria compatibile")&&display.contains("divergente")&&display.contains("da revisionare")&&display.contains("9 / 100"),"comparison lost context");
   JSONObject bad=new JSONObject(response.toString());bad.getJSONArray("records").getJSONObject(0).put("apply_authorized",true);rejected(rows,bad);
   bad=new JSONObject(response.toString());bad.getJSONArray("records").getJSONObject(0).put("bgg_verified",true);rejected(rows,bad);
   bad=new JSONObject(response.toString());bad.getJSONArray("records").getJSONObject(0).put("language","IT");rejected(rows,bad);
   bad=new JSONObject(response.toString());bad.getJSONArray("records").getJSONObject(0).put("needs_review",false);rejected(rows,bad);
   bad=new JSONObject(response.toString());bad.getJSONArray("records").getJSONObject(0).put("listing_id",2);rejected(rows,bad);
   bad=new JSONObject(response.toString());bad.getJSONArray("records").getJSONObject(0).put("listing_id",99);rejected(rows,bad);
   bad=new JSONObject(response.toString());bad.getJSONArray("records").getJSONObject(0).put("proposed_type","OTHER");rejected(rows,bad);
   for(String localOnly:new String[]{"ACCESSORY","COMPONENTS","EMPTY_BOX","UNCERTAIN"}){bad=new JSONObject(response.toString());bad.getJSONArray("records").getJSONObject(0).put("proposed_type",localOnly);rejected(rows,bad);}
   bad=new JSONObject(response.toString());bad.getJSONArray("records").getJSONObject(0).put("evidence","");rejected(rows,bad);
   check(AiBetaListings.read(db).toString().equals(rows.toString()),"comparison wrote catalog");
   db.execSQL("UPDATE market_listings SET brand='changed' WHERE id=1");check(!key.equals(AiBetaListings.key(AiBetaListings.read(db))),"stale input reusable");
   rows.getJSONObject(0).put("title",new String(new char[4096]).replace('\0','界'));boolean bounded=false;try{AiBetaListings.payload(rows);}catch(Exception expected){bounded=true;}check(bounded,"UTF8 bound missing");
  }
 }
}

