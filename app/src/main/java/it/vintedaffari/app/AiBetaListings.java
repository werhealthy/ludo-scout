package it.vintedaffari.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Rect;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;

/** Bounded snapshot of saved announcements. Never opens a helper, upgrades or writes the catalog. */
public final class AiBetaListings {
 static final String SELECTION_SQL = "SELECT l.id,COALESCE(l.vinted_title,''),COALESCE(l.brand,''),COALESCE(l.item_condition,''),COALESCE(l.current_price_cents,0),COALESCE(l.observed_text,''),COALESCE(l.lifecycle,''),COALESCE(l.match_state,''),COALESCE(g.bgg_id,''),COALESCE(g.canonical_name,''),COALESCE(g.match_state,'') FROM market_listings l LEFT JOIN games g ON g.id=l.game_id WHERE l.id>0 AND TRIM(COALESCE(l.vinted_title,''))<>'' ORDER BY l.last_seen DESC,l.id DESC LIMIT 8";
 private static final String ONE_SQL=SELECTION_SQL.substring(0,SELECTION_SQL.indexOf(" WHERE "))+ " WHERE l.id=? LIMIT 1";
 private AiBetaListings(){}
 public static JSONArray read(Context context)throws Exception {
  File file=context.getDatabasePath("vinted_affari.db");if(!file.isFile())return new JSONArray();
  try(SQLiteDatabase db=SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)){return read(db);}
 }
 static JSONArray read(SQLiteDatabase db)throws Exception {
  JSONArray rows=new JSONArray();
  try(Cursor c=db.rawQuery(SELECTION_SQL,null)){while(c.moveToNext()){
   rows.put(row(c));
  }}return rows;
 }
 static JSONObject row(Cursor c)throws Exception {
   VintedCard card=new VintedCard(c.getString(1),c.getString(2),c.getString(3),c.getInt(4)/100.0,null,null,new Rect(0,0,1,1),c.getString(5));
   ListingClassifier.Result local=ListingClassifier.classify(card);
   String source=card.rawDescription==null?"":card.rawDescription;
   int count=source.codePointCount(0,source.length());
   String excerpt=count>2000?source.substring(0,source.offsetByCodePoints(0,2000)):source;
   return new JSONObject().put("listing_id",c.getLong(0)).put("title",card.title).put("brand",card.brand)
    .put("source_text",excerpt).put("source_truncated",count>2000)
    .put("local_type",local.type.name()).put("local_reason",local.reason).put("lifecycle",c.getString(6))
    .put("listing_match_state",c.getString(7)).put("bgg_id",c.getString(8)).put("game_title",c.getString(9)).put("game_match_state",c.getString(10))
    .put("input_key",AiBetaProtocol.fingerprint(new JSONArray().put(card.title).put(card.brand).put(card.rawDescription).toString(),"local","v1"));
 }
 static boolean current(SQLiteDatabase db,JSONArray snapshot)throws Exception {
  if(snapshot.length()<1||snapshot.length()>8)return false;
  for(int i=0;i<snapshot.length();i++){
   JSONObject before=snapshot.getJSONObject(i);
   try(Cursor cursor=db.rawQuery(ONE_SQL,new String[]{String.valueOf(before.getLong("listing_id"))})){
    if(!cursor.moveToFirst()||!before.toString().equals(row(cursor).toString()))return false;
   }
  }
  return true;
 }
 /** Only these three fields may leave the phone; local comparison and identities stay local. */
 static JSONArray payload(JSONArray snapshot)throws Exception {
  java.util.ArrayList<JSONObject> sorted=new java.util.ArrayList<>();for(int i=0;i<snapshot.length();i++)sorted.add(snapshot.getJSONObject(i));sorted.sort((a,b)->Long.compare(a.optLong("listing_id"),b.optLong("listing_id")));
  JSONArray rows=new JSONArray();for(JSONObject r:sorted){rows.put(new JSONObject().put("listing_id",r.getLong("listing_id")).put("title",r.getString("title")).put("brand",r.getString("brand")));}
  if(rows.length()>0&&new JSONObject().put("request_id","xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx").put("records",rows).toString().getBytes(StandardCharsets.UTF_8).length>4096)throw new Exception("input too large");
  return rows;
 }
 static String key(JSONArray snapshot)throws Exception{return AiBetaProtocol.fingerprint(payload(snapshot).toString(),AiBetaProtocol.MODEL,AiBetaProtocol.CONTRACT);}
 static String verdictLabel(String verdict){switch(verdict){case "AGREEMENT":return "concorde";case "COMPATIBLE_GROUP":return "categoria compatibile; sottotipo locale conservato";case "CONFLICT":return "divergente";default:return "informazioni insufficienti · da chiarire";}}
 static String comparable(String type){switch(type){case "ACCESSORY":case "COMPONENTS":case "EMPTY_BOX":return "ACCESSORY_COMPONENT";case "UNCERTAIN":return "UNKNOWN";default:return type;}}
 static String lifecycle(String state){switch(state){case "ACTIVE":return "attivo";case "AUTO_FILTERED":return "filtrato";case "USER_HIDDEN":return "nascosto";case "SOLD":return "venduto";case "ARCHIVED":return "archiviato";default:return "stato non disponibile";}}
 static String category(String type)throws Exception {switch(type){case "BASE_GAME":return "Gioco base";case "EXPANSION":return "Espansione";case "BUNDLE":return "Bundle";case "ACCESSORY":return "Accessorio";case "COMPONENTS":return "Componenti";case "EMPTY_BOX":return "Scatola vuota";case "ACCESSORY_COMPONENT":return "Accessorio o componente";case "NON_GAME":return "Non gioco";case "UNCERTAIN":case "UNKNOWN":return "Da chiarire";default:throw new Exception("invalid category");}}
 static String evidence(JSONObject row)throws Exception {
  String brand=row.getString("brand"),source=row.getString("source_text"),bgg=row.getString("bgg_id");
  return "Dati del gruppo preparato · solo sul telefono\nCatalogo invariato. BGG e lingua non verificati dall’AI.\n\n"
   +row.getString("title")+"\nMarca dichiarata: "+(brand.trim().isEmpty()?"non disponibile":brand)
   +"\n\nTesto acquisito: "+(source.trim().isEmpty()?"non disponibile":source)
   +(row.getBoolean("source_truncated")?"\n[Mostrati i primi 2.000 caratteri del testo acquisito]":"")
   +"\n\nLocale (ricalcolata): "+category(row.getString("local_type"))+"\n"+row.getString("local_reason")
   +"\n"+(bgg.isEmpty()?"BGG: nessun collegamento salvato":"BGG salvato: "+row.getString("game_title")+" (#"+bgg+") · da verificare")
   +"\nStato annuncio: "+lifecycle(row.getString("lifecycle"));
 }
 static String display(JSONArray snapshot,JSONObject response)throws Exception {
  if(response!=null&&!"PROPOSAL".equals(response.getString("status")))throw new Exception("invalid proposal status");
  JSONArray answers=response==null?null:response.getJSONArray("records");
  if(answers!=null){if(answers.length()!=snapshot.length())throw new Exception("invalid proposals");java.util.HashSet<Long> ids=new java.util.HashSet<>();for(int i=0;i<answers.length();i++){JSONObject r=answers.getJSONObject(i);long id=r.getLong("listing_id");boolean known=false;for(int j=0;j<snapshot.length();j++)if(snapshot.getJSONObject(j).getLong("listing_id")==id)known=true;
   if(!known||!ids.add(id)||!Boolean.FALSE.equals(r.get("apply_authorized"))||!Boolean.FALSE.equals(r.get("bgg_verified"))||!Boolean.TRUE.equals(r.get("needs_review"))||!"UNKNOWN".equals(r.get("language")))throw new Exception("invalid proposals");String proposed=r.getString("proposed_type");if(!java.util.Arrays.asList("BASE_GAME","EXPANSION","BUNDLE","ACCESSORY_COMPONENT","NON_GAME","UNKNOWN").contains(proposed))throw new Exception("invalid proposed type");Object confidence=r.get("confidence");if(confidence!=JSONObject.NULL&&(!(confidence instanceof Number)||!Double.isFinite(((Number)confidence).doubleValue())||((Number)confidence).doubleValue()<0||((Number)confidence).doubleValue()>100))throw new Exception("invalid confidence");String evidence=r.getString("evidence");if(evidence.trim().isEmpty()||evidence.length()>500)throw new Exception("invalid evidence");
  }}
  StringBuilder out=new StringBuilder(answers==null?"Ultimi annunci salvati · massimo 8\nInvio manuale di solo titolo e marca. Catalogo invariato.\n":"Confronto AI · catalogo invariato\nProposte da revisionare. BGG e lingua non verificati dall’AI.\n");
  if(snapshot.length()==0)return "Nessun annuncio salvato disponibile. Acquisisci prima gli annunci dall’app.";
  for(int i=0;i<snapshot.length();i++){JSONObject r=snapshot.getJSONObject(i);out.append("\n").append(r.getString("title")).append("\nLocale (ricalcolata): ").append(category(r.getString("local_type"))).append("\n").append(r.getString("local_reason")).append("\n");
   if(answers!=null){JSONObject answer=null;for(int j=0;j<answers.length();j++)if(answers.getJSONObject(j).getLong("listing_id")==r.getLong("listing_id"))answer=answers.getJSONObject(j);String proposed=answer.getString("proposed_type");out.append("AI: ").append(category(proposed)).append(" · ").append(verdictLabel(AiBetaComparison.verdict(r.getString("local_type"),proposed))).append(" · da verificare\n").append(answer.getString("evidence")).append("\n");}
   String bgg=r.getString("bgg_id"),gameState=r.getString("game_match_state");String identityState="MATCHED".equals(gameState)?"associazione salvata":gameState.contains("REVIEW")?"da revisionare":"da verificare";out.append(bgg.isEmpty()?"BGG: nessun collegamento salvato":"BGG salvato: "+r.getString("game_title")+" (#"+bgg+") · "+identityState).append("\nStato annuncio: ").append(lifecycle(r.getString("lifecycle"))).append("\n");
  }
  if(response!=null&&response.optJSONObject("budget")!=null)out.append("\nChiamate al momento della risposta: ").append(response.getJSONObject("budget").optInt("calls_reserved")).append(" / 100");return out.toString();
 }
}


