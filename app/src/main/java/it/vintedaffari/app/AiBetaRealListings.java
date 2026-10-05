package it.vintedaffari.app;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import java.io.File;

/** Stable manual snapshot using the existing frontend read-only announcement source.
 * No helper, upgrades or catalog writes. BGG/local classification stay local; bounded listing text/photos may be routed to private AI.
 */
public final class AiBetaRealListings {
 private AiBetaRealListings() {}
 public static final class Snapshot {
  public final JSONArray local, rows;
  public final String key;
  Snapshot(JSONArray local) throws Exception {
   this.local=local; rows=AiBetaListings.payload(local); key=AiBetaListings.key(local);
  }
  public String localKey() {
   return AiBetaProtocol.fingerprint(local.toString(),AiBetaProtocol.MODEL,AiBetaProtocol.CONTRACT);
  }
 }
 static Snapshot select(SQLiteDatabase db) throws Exception {return new Snapshot(AiBetaListings.read(db));}
 static boolean current(SQLiteDatabase db,Snapshot snapshot) throws Exception {
  return snapshot!=null && AiBetaListings.current(db,snapshot.local);
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

