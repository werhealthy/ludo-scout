package it.vintedaffari.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real platform JSON/SQLite with fake transport. No credentials or provider access. */
final class AiEngineChecks {
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 private static JSONObject config()throws Exception{return new JSONObject().put("enabled",true).put("endpoint","https://ludo-test.workers.dev").put("token","test-token-only-not-real");}
 private static final class Memory implements AiEngineSession.Journal {
  JSONObject j=new JSONObject();public JSONObject load()throws Exception{return new JSONObject(j.toString());}
  public void save(JSONObject value)throws Exception{j=new JSONObject(value.toString());}
 }
 private static final class Network implements AiEngineSession.Transport {
  int calls,statusCalls;boolean enabled=true,fail,invalid;int budget=10;String id,reply="PROPOSAL";JSONArray lastPayload;Runnable during;
  public JSONObject status()throws Exception {statusCalls++;return new JSONObject().put("enabled",enabled).put("budget",new JSONObject().put("calls_reserved",budget).put("reserved_micro",budget*10000));}
  public JSONObject submit(String request,JSONArray payload)throws Exception {
   calls++;if(id!=null)check(id.equals(request),"recovery allocated another reservation ID");id=request;
   lastPayload=new JSONArray(payload.toString());
   check(payload.length()<=8,"unbounded batch");for(int i=0;i<payload.length();i++){JSONObject row=payload.getJSONObject(i);check(row.length()==5&&row.has("source_text")&&row.has("photos"),"rich evidence contract missing");check(!row.has("local_type")&&!row.has("bgg_id")&&!row.has("game_title"),"private identity fields transmitted");}
   if(during!=null)during.run();if(fail)throw new Exception("simulated lost response");
   JSONArray answers=new JSONArray();for(int i=0;i<payload.length();i++)answers.put(AiBetaRealChecks.answer(payload.getJSONObject(i).getLong("listing_id"),"NON_GAME"));
   if(invalid)answers.getJSONObject(0).put("bgg_verified",true);
   return new JSONObject().put("status",reply).put("request_id",request).put("model",AiBetaProtocol.MODEL).put("contract",AiBetaProtocol.CONTRACT).put("records",answers);
  }
 }
 private static SQLiteDatabase fixture(){
  SQLiteDatabase db=SQLiteDatabase.create(null);
  db.execSQL("CREATE TABLE market_listings(id INTEGER PRIMARY KEY,vinted_title TEXT,brand TEXT,item_condition TEXT,current_price_cents INTEGER,observed_text TEXT,game_id INTEGER,last_seen INTEGER,lifecycle TEXT,match_state TEXT,legacy_signature TEXT,temp_fingerprint TEXT,vinted_item_id TEXT,manual_review_required INTEGER,last_error TEXT,listing_photos_csv TEXT)");
  db.execSQL("CREATE TABLE games(id INTEGER PRIMARY KEY,bgg_id TEXT,canonical_name TEXT,match_state TEXT)");
  db.execSQL("CREATE TABLE deals(signature TEXT PRIMARY KEY,verification_state TEXT,confirmed INTEGER,lifecycle TEXT,bgg_id TEXT,vinted_title TEXT,brand TEXT,verification_reason TEXT,total_cents INTEGER)");
  db.execSQL("CREATE TABLE listing_overrides(signature TEXT,item_id TEXT,payload TEXT)");
  db.execSQL("INSERT INTO games VALUES(1,'123','Catan','MATCHED')");
  db.execSQL("INSERT INTO market_listings VALUES(1,'Catan gioco da tavolo','Kosmos','buono',1000,'Testo completo',1,100,'ACTIVE','MATCHED','sig1','temp1','999',0,NULL,'https://images1.vinted.net/t/catan.webp')");
  db.execSQL("INSERT INTO deals VALUES('sig1','OK',0,'ACTIVE','123','Catan gioco da tavolo','Kosmos',NULL,2000)");
  return db;
 }
 private static String state(SQLiteDatabase db){try(Cursor c=db.rawQuery("SELECT verification_state FROM deals WHERE signature='sig1'",null)){c.moveToFirst();return c.getString(0);}}
 static void run(android.content.Context context)throws Exception {
  long now=1000000;
  try(SQLiteDatabase db=fixture()){
   AiEngineListings source=new AiEngineListings(db);Memory m=new Memory();Network net=new Network();net.enabled=false;
   check(AiEngineSession.run(config(),m,source,net,now).state.equals("SERVICE_OFF")&&net.calls==0&&state(db).equals("OK"),"OFF consumed provider or changed trust");
   m=new Memory();net=new Network();net.budget=100;
   check(AiEngineSession.run(config(),m,source,net,now).state.equals("BUDGET_BLOCKED")&&net.calls==0,"budget exceeded");
   m=new Memory();net=new Network();net.invalid=true;
   check(AiEngineSession.run(config(),m,source,net,now).state.equals("INVALID_RESPONSE")&&state(db).equals("OK"),"invalid identity claim accepted");
   m=new Memory();net=new Network();net.fail=true;
   check(AiEngineSession.run(config(),m,source,net,now).state.equals("PENDING_RECOVERY"),"ambiguous attempt discarded");
   String request=m.j.getJSONObject("pending").getString("id");
   net.fail=false;AiEngineSession.Result r=AiEngineSession.run(config(),m,source,net,now+AiEnginePolicy.BACKOFF);
   check(r.held==1&&state(db).equals("MATCH_UNCERTAIN")&&request.equals(net.id),"recovery did not hold safely");
   try(Cursor c=db.rawQuery("SELECT bgg_id,total_cents FROM deals WHERE signature='sig1'",null)){c.moveToFirst();check(c.getString(0).equals("123")&&c.getInt(1)==2000,"AI changed identity or pricing");}
   int calls=net.calls;
   AiEngineSession.run(config(),m,source,net,now+AiEnginePolicy.BACKOFF+10000);
   check(net.calls==calls,"same title/brand recategorized remotely");
  }
  // Freshness and human decisions are repeated inside the actual writer transaction.
  for(String change:new String[]{"UPDATE market_listings SET brand='Changed'","UPDATE market_listings SET observed_text='Changed tail'","UPDATE market_listings SET lifecycle='USER_HIDDEN'","UPDATE deals SET verification_state='USER_CONFIRMED'","INSERT INTO listing_overrides VALUES('sig1',NULL,'{}')"}){
   try(SQLiteDatabase db=fixture()){
    Memory m=new Memory();Network net=new Network();net.during=()->db.execSQL(change);
    AiEngineSession.run(config(),m,new AiEngineListings(db),net,now);
    check(!state(db).equals("MATCH_UNCERTAIN"),"stale/protected snapshot changed trust: "+change);
   }
  }
  try(SQLiteDatabase db=fixture()){
   db.execSQL("UPDATE market_listings SET lifecycle='AUTO_FILTERED'");
   Memory m=new Memory();Network net=new Network();AiEngineSession.run(config(),m,new AiEngineListings(db),net,now);
   check(net.calls==0&&net.statusCalls==0&&state(db).equals("OK"),"historical/filtered catalog was restored");
  }
  try(SQLiteDatabase db=fixture()){
   AiEngineListings source=new AiEngineListings(db);JSONObject j=new JSONObject().put("seen",new JSONObject());
   JSONArray rows=source.select(j,now);
   JSONObject answer=new JSONObject().put("status","PROPOSAL").put("records",new JSONArray().put(AiBetaRealChecks.answer(1,"BASE_GAME")));
   check(source.apply(rows,answer)==0&&state(db).equals("OK"),"agreement bypassed local identity gates");
   answer.getJSONArray("records").getJSONObject(0).put("proposed_type","UNKNOWN");
   check(source.apply(rows,answer)==0&&state(db).equals("OK"),"abstention invented negative evidence");
  }
  android.util.Log.i("LudoAI","AI engine OFF/budget/cache/recovery/freshness/human protections verified");
  try(SQLiteDatabase db=SQLiteDatabase.create(null);DealDatabase schema=new DealDatabase(context)){
   schema.onCreate(db);
   db.execSQL("INSERT INTO deals(signature,first_seen,last_seen,vinted_title,brand,bgg_id,verification_state,verification_reason) VALUES('ai-held',1,1,'Catan gioco da tavolo','Kosmos','123','MATCH_UNCERTAIN','AI_CATEGORY_REVIEW: retained')");
   VintedCard card=new VintedCard("Catan gioco da tavolo","Kosmos","buono",10.0,null,null,new android.graphics.Rect(0,0,1,1),"Testo completo");
   GameAnalysis analysis=GameAnalysis.fromJson(new JSONObject().put("status","matched").put("game",new JSONObject().put("name","Catan").put("bggId","123").put("averageRating",7.0)));
   java.lang.reflect.Method upsert=DealDatabase.class.getDeclaredMethod("upsertDeal",SQLiteDatabase.class,String.class,VintedCard.class,GameAnalysis.class,ListingClassifier.Result.class,String.class,String.class,long.class,String.class);
   upsert.setAccessible(true);upsert.invoke(null,db,"ai-held",card,analysis,ListingClassifier.classify(card),"OK",null,100L,"fair");
   try(Cursor c=db.rawQuery("SELECT verification_state FROM deals WHERE signature='ai-held'",null)){c.moveToFirst();check(c.getString(0).equals("MATCH_UNCERTAIN"),"automatic reanalysis erased the AI hold");}
  }
  reviewChecks(now);
  try(SQLiteDatabase db=fixture()){
   Memory m=new Memory();Network net=new Network();
   AiEngineSession.run(config(),m,new AiEngineListings(db),net,now);
   db.execSQL("UPDATE market_listings SET observed_text='Changed local context'");net.id=null;
   AiEngineSession.run(config(),m,new AiEngineListings(db),net,now+6L*86400000);
   check(net.calls==1,"fresh cache was not reused");
   AiEngineSession.run(config(),m,new AiEngineListings(db),net,now+7L*86400000+1);
   check(net.calls==2,"local recheck extended remote proposal expiry");
  }
 }
 private static void addSecond(SQLiteDatabase db){
  db.execSQL("INSERT INTO market_listings VALUES(2,'Azul gioco da tavolo','Next Move','buono',1000,'Testo completo',1,100,'ACTIVE','MATCHED','sig2','temp2','1000',0,NULL,'')");
  db.execSQL("INSERT INTO deals VALUES('sig2','OK',0,'ACTIVE','123','Azul gioco da tavolo','Next Move',NULL,2000)");
 }
 private static void reviewChecks(long now)throws Exception {
  java.util.ArrayList<String> failures=new java.util.ArrayList<>();
  try(SQLiteDatabase db=fixture()){
   Memory m=new Memory();Network net=new Network();net.fail=true;
   AiEngineSession.run(config(),m,new AiEngineListings(db),net,now);
   net.fail=false;net.budget=100;
   check(AiEngineSession.run(config(),m,new AiEngineListings(db),net,now+AiEnginePolicy.BACKOFF).held==1,"reserved request cannot recover at budget100");
  }catch(AssertionError failure){failures.add(failure.getMessage());}
  try(SQLiteDatabase db=fixture()){
   Memory m=new Memory();Network net=new Network();
   AiEngineSession.run(config(),m,new AiEngineListings(db),net,now);
   db.execSQL("UPDATE market_listings SET observed_text='New local evidence' WHERE id=1");addSecond(db);net.id=null;
   AiEngineSession.run(config(),m,new AiEngineListings(db),net,now+10000);
   check(net.lastPayload.length()==1&&net.lastPayload.getJSONObject(0).getLong("listing_id")==2,"cached row consumed a new remote batch");
  }catch(AssertionError failure){failures.add(failure.getMessage());}
  try(SQLiteDatabase db=fixture()){
   Memory m=new Memory();Network net=new Network();net.reply="FAILED";
   check(AiEngineSession.run(config(),m,new AiEngineListings(db),net,now).state.equals("TERMINAL_FAILED"),"terminal failure blocks unrelated work");
   addSecond(db);net.id=null;net.reply="PROPOSAL";
   AiEngineSession.run(config(),m,new AiEngineListings(db),net,now+10000);
   check(net.lastPayload.length()==1&&net.lastPayload.getJSONObject(0).getLong("listing_id")==2,"failed batch monopolized the lane");
  }catch(AssertionError failure){failures.add(failure.getMessage());}
  if(!failures.isEmpty())throw new AssertionError(String.join("; ",failures));
  android.util.Log.i("LudoAI","AI engine budget100 recovery, partial cache and terminal failure verified");
 }
}
