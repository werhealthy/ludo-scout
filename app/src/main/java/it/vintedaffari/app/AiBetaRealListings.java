package it.vintedaffari.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Rect;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;

/** Read-only, bounded snapshot of announcements already acquired by the app.
 * Uses the same direct archive path as the existing audit, with no helper upgrades.
 * Description stays local; the remote payload contains only id/title/brand.
 */
public final class AiBetaRealListings {
 static final String SELECT_RECENT = "SELECT id,vinted_title,COALESCE(brand,''),COALESCE(observed_text,'') FROM market_listings WHERE lifecycle IN ('ACTIVE','AUTO_FILTERED') AND trim(vinted_title)<>'' ORDER BY last_seen DESC,id DESC LIMIT 32";
 private static final String SELECT_ONE = "SELECT id,vinted_title,COALESCE(brand,''),COALESCE(observed_text,'') FROM market_listings WHERE id=? AND lifecycle IN ('ACTIVE','AUTO_FILTERED') AND trim(vinted_title)<>'' LIMIT 1";
 private AiBetaRealListings() {}

 public static final class Snapshot {
  public final JSONArray local, rows;
  public final String key;
  Snapshot(JSONArray local) throws Exception {
   this.local=local; rows=payload(local);
   key=AiBetaProtocol.fingerprint(rows.toString(),AiBetaProtocol.MODEL,AiBetaProtocol.CONTRACT);
  }
  public String localKey() {
   return AiBetaProtocol.fingerprint(local.toString(),AiBetaProtocol.MODEL,AiBetaProtocol.CONTRACT);
  }
 }

 private static JSONObject read(Cursor c) throws Exception {
  VintedCard card=new VintedCard(c.getString(1),c.getString(2),"",0,null,null,new Rect(0,0,1,1),c.getString(3));
  ListingClassifier.Result classification=ListingClassifier.classify(card);
  return new JSONObject().put("listing_id",c.getLong(0)).put("title",card.title).put("brand",card.brand)
      .put("local_type",classification.type.name()).put("local_reason",classification.reason)
      .put("input_key",AiBetaProtocol.fingerprint(new JSONArray().put(card.title).put(card.brand).put(card.rawDescription).toString(),"local","v1"));
 }

 static JSONArray payload(JSONArray local) throws Exception {
  JSONArray rows=new JSONArray();
  for(int i=0;i<local.length();i++) {
   JSONObject r=local.getJSONObject(i);
   rows.put(new JSONObject().put("listing_id",r.getLong("listing_id")).put("title",r.getString("title")).put("brand",r.getString("brand")));
  }
  return rows;
 }

 static Snapshot select(SQLiteDatabase db) throws Exception {
  JSONArray selected=new JSONArray();
  try(Cursor c=db.rawQuery(SELECT_RECENT,null)) {
   while(c.moveToNext() && selected.length()<8) {
    if(c.getLong(0)<=0 || c.getLong(0)>9007199254740991L)continue;
    JSONObject row=read(c); JSONArray candidate=new JSONArray(selected.toString()).put(row);
    int bytes=new JSONObject().put("request_id","00000000-0000-0000-0000-000000000000").put("records",payload(candidate)).toString().getBytes(StandardCharsets.UTF_8).length;
    if(bytes<=4096)selected.put(row);
   }
  }
  return new Snapshot(selected);
 }

 static boolean current(SQLiteDatabase db, Snapshot snapshot) throws Exception {
  if(snapshot==null || snapshot.local.length()<1 || snapshot.local.length()>8)return false;
  for(int i=0;i<snapshot.local.length();i++) {
   JSONObject before=snapshot.local.getJSONObject(i);
   try(Cursor c=db.rawQuery(SELECT_ONE,new String[]{String.valueOf(before.getLong("listing_id"))})) {
    if(!c.moveToFirst())return false;
    JSONObject now=read(c);
    if(!before.getString("input_key").equals(now.getString("input_key")) ||
       !before.getString("local_type").equals(now.getString("local_type")) ||
       !before.getString("local_reason").equals(now.getString("local_reason")))return false;
   }
  }
  return true;
 }

 private static SQLiteDatabase open(Context context) throws Exception {
  File file=context.getDatabasePath("vinted_affari.db");
  if(!file.isFile())throw new Exception("archive unavailable");
  return SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);
 }
 public static Snapshot prepare(Context context) throws Exception {
  try(SQLiteDatabase db=open(context)){return select(db);}
 }
 public static boolean current(Context context,Snapshot snapshot) throws Exception {
  try(SQLiteDatabase db=open(context)){return current(db,snapshot);}
 }
 public static Snapshot restore(JSONArray local) throws Exception {return new Snapshot(local);}
}
