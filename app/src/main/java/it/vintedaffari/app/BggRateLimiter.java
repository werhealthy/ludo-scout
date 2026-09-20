package it.vintedaffari.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Cross-process BGG XML API pacing gate.
 *
 * UI search lives in :ui while enrichment lives in the queue process. A static timer only
 * coordinates threads inside one process, so v5.11.14 reserves the next network slot in SQLite.
 * This keeps the ~5.2 s request-start spacing authoritative without preventing BGG from running
 * in parallel with the completely separate Vinted lane.
 */
public final class BggRateLimiter {
    private static final long GAP_MS = 5_200L;
    private static final String KEY="bgg_rate_next_at";
    private static long fallbackNextAt = 0L;
    private BggRateLimiter() {}

    public static void acquire(Context context) throws InterruptedException {
        if(context==null){acquire();return;}
        DealDatabase helper=new DealDatabase(context.getApplicationContext());
        long reservedAt;
        SQLiteDatabase db=helper.getWritableDatabase();
        db.beginTransaction();
        try{
            long now=System.currentTimeMillis(),next=0L;
            try(Cursor c=db.rawQuery("SELECT value FROM queue_controls WHERE name=? LIMIT 1",new String[]{KEY})){
                if(c.moveToFirst())next=c.getLong(0);
            }
            reservedAt=Math.max(now,next);
            ContentValues v=new ContentValues();v.put("name",KEY);v.put("value",reservedAt+GAP_MS);v.put("updated_at",now);v.put("text_value","BGG");
            db.insertWithOnConflict("queue_controls",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();try{helper.close();}catch(Throwable ignored){}}
        long wait=reservedAt-System.currentTimeMillis();
        if(wait>0L)Thread.sleep(wait);
    }

    /** Compatibility fallback for non-Android/unit callers. */
    public static void acquire() throws InterruptedException {
        long wait;
        synchronized (BggRateLimiter.class) {
            long now = System.currentTimeMillis();
            wait = Math.max(0L, fallbackNextAt - now);
            fallbackNextAt = Math.max(fallbackNextAt, now) + GAP_MS;
        }
        if (wait > 0L) Thread.sleep(wait);
    }
}
