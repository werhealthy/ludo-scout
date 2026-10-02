package it.vintedaffari.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.text.TextUtils;

/**
 * Tiny persistent cache of perceptual hashes for Vinted thumbnails that the existing resolver has
 * already downloaded. It never performs HTTP. The cache lets shadow/batch matching reuse one image
 * download across many observations and across process restarts.
 */
public final class VintedPhotoHashCache {
    private VintedPhotoHashCache() {}
    public static final String TABLE="vinted_photo_hash_cache_v1";

    public static void ensure(SQLiteDatabase db){
        if(db==null)return;
        db.execSQL("CREATE TABLE IF NOT EXISTS "+TABLE+"(photo_key TEXT PRIMARY KEY,image_url TEXT,hash64 INTEGER NOT NULL,first_at INTEGER NOT NULL,last_at INTEGER NOT NULL,capture_count INTEGER NOT NULL DEFAULT 1)");
    }

    public static void record(Context context,String imageUrl,Bitmap image){
        if(context==null||image==null||TextUtils.isEmpty(imageUrl))return;
        String key=PhotoIdentity.key(imageUrl);if(TextUtils.isEmpty(key))return;
        long hash=VisualCoverMatcher.dHash64(image),now=System.currentTimeMillis(),first=now;int count=0;
        DealDatabase helper=new DealDatabase(context.getApplicationContext());
        try{
            SQLiteDatabase db=helper.getWritableDatabase();ensure(db);
            try(Cursor c=db.rawQuery("SELECT first_at,capture_count FROM "+TABLE+" WHERE photo_key=? LIMIT 1",new String[]{key})){
                if(c.moveToFirst()){first=c.getLong(0);count=c.getInt(1);}
            }
            ContentValues v=new ContentValues();v.put("photo_key",key);v.put("image_url",imageUrl);v.put("hash64",hash);v.put("first_at",first);v.put("last_at",now);v.put("capture_count",count+1);
            db.insertWithOnConflict(TABLE,null,v,SQLiteDatabase.CONFLICT_REPLACE);
        }catch(Throwable ignored){}finally{try{helper.close();}catch(Throwable ignored){}}
    }

    /** Exact URL, recent integer hash. Missing/corrupt tables are misses; no DDL here. */
    public static Long lookupExact(Context context,String imageUrl){
        if(context==null||TextUtils.isEmpty(imageUrl))return null;
        String source=imageUrl.replace("&amp;","&"),key=PhotoIdentity.key(source);
        if(TextUtils.isEmpty(key))return null;
        DealDatabase helper=new DealDatabase(context.getApplicationContext());
        long now=System.currentTimeMillis();
        try{
            SQLiteDatabase db=helper.getReadableDatabase();
            try(Cursor cur=db.rawQuery("SELECT hash64 FROM "+TABLE+" WHERE photo_key=? AND REPLACE(image_url,'&amp;','&')=? AND typeof(hash64)='integer' AND last_at>=? AND last_at<=? LIMIT 1",
                    new String[]{key,source,String.valueOf(now-24L*60L*60_000L),String.valueOf(now)})){
                return cur.moveToFirst()?cur.getLong(0):null;
            }
        }catch(Throwable ignored){return null;}
        finally{try{helper.close();}catch(Throwable ignored){}}
    }

    public static Long lookup(SQLiteDatabase db,String imageUrl){
        if(db==null||TextUtils.isEmpty(imageUrl))return null;ensure(db);String key=PhotoIdentity.key(imageUrl);if(TextUtils.isEmpty(key))return null;
        try(Cursor c=db.rawQuery("SELECT hash64 FROM "+TABLE+" WHERE photo_key=? LIMIT 1",new String[]{key})){return c.moveToFirst()?c.getLong(0):null;}catch(Throwable ignored){return null;}
    }

    public static int uniqueCount(SQLiteDatabase db){ensure(db);try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM "+TABLE,null)){return c.moveToFirst()?c.getInt(0):0;}catch(Throwable ignored){return 0;}}
    public static int captureCount(SQLiteDatabase db){ensure(db);try(Cursor c=db.rawQuery("SELECT COALESCE(SUM(capture_count),0) FROM "+TABLE,null)){return c.moveToFirst()?c.getInt(0):0;}catch(Throwable ignored){return 0;}}
}
