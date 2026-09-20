package it.vintedaffari.app;

import android.app.*;
import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import android.os.Build;

import java.util.*;

/** Local BGG watch list. No remote account and no LLM/API dependency. */
public final class HuntDatabase extends SQLiteOpenHelper {
    private static final String NAME="ludo_hunts.db"; private static final int VERSION=1;
    public static final String CHANNEL="ludo_hunts";

    public static final class Hunt {
        public long id; public String bggId,name,imageUrl; public Double rating; public Integer targetCents; public long createdAt,lastNotifiedAt;
    }

    public HuntDatabase(Context c){super(c,NAME,null,VERSION);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE hunts(id INTEGER PRIMARY KEY AUTOINCREMENT,bgg_id TEXT NOT NULL UNIQUE,name TEXT NOT NULL,image_url TEXT,rating REAL,target_cents INTEGER,created_at INTEGER NOT NULL,last_notified_at INTEGER NOT NULL DEFAULT 0)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldV,int newV){}

    public synchronized void add(BggSearchClient.Game g,Integer target){
        if(g==null||g.id==null||g.id.isEmpty())return;ContentValues v=new ContentValues();v.put("bgg_id",g.id);v.put("name",g.name);v.put("image_url",g.imageUrl);if(g.rating!=null)v.put("rating",g.rating);if(target!=null)v.put("target_cents",target);v.put("created_at",System.currentTimeMillis());getWritableDatabase().insertWithOnConflict("hunts",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public synchronized void remove(long id){getWritableDatabase().delete("hunts","id=?",new String[]{String.valueOf(id)});}
    public synchronized List<Hunt> all(){
        List<Hunt> out=new ArrayList<>();Cursor c=getReadableDatabase().rawQuery("SELECT id,bgg_id,name,image_url,rating,target_cents,created_at,last_notified_at FROM hunts ORDER BY created_at DESC",null);while(c.moveToNext()){Hunt h=new Hunt();h.id=c.getLong(0);h.bggId=c.getString(1);h.name=c.getString(2);h.imageUrl=c.getString(3);h.rating=c.isNull(4)?null:c.getDouble(4);h.targetCents=c.isNull(5)?null:c.getInt(5);h.createdAt=c.getLong(6);h.lastNotifiedAt=c.getLong(7);out.add(h);}c.close();return out;
    }
    public synchronized List<Hunt> matches(String bggId,int totalCents){
        List<Hunt> out=new ArrayList<>();if(bggId==null||bggId.isEmpty())return out;Cursor c=getReadableDatabase().rawQuery("SELECT id,bgg_id,name,image_url,rating,target_cents,created_at,last_notified_at FROM hunts WHERE bgg_id=? AND (target_cents IS NULL OR ?<=target_cents)",new String[]{bggId,String.valueOf(totalCents)});while(c.moveToNext()){Hunt h=new Hunt();h.id=c.getLong(0);h.bggId=c.getString(1);h.name=c.getString(2);h.imageUrl=c.getString(3);h.rating=c.isNull(4)?null:c.getDouble(4);h.targetCents=c.isNull(5)?null:c.getInt(5);h.createdAt=c.getLong(6);h.lastNotifiedAt=c.getLong(7);out.add(h);}c.close();return out;
    }
    public synchronized void markNotified(long id,long now){ContentValues v=new ContentValues();v.put("last_notified_at",now);getWritableDatabase().update("hunts",v,"id=?",new String[]{String.valueOf(id)});}

    public static boolean evaluateAndNotify(Context context,GameAnalysis a,DealRecord d){if(a==null||a.bggId==null)return false;return evaluateAndNotifyLinked(context,d,a.bggId);}
    public static boolean evaluateAndNotifyLinked(Context context,DealRecord d){return evaluateAndNotifyLinked(context,d,d==null?null:d.bggId);}
    private static boolean evaluateAndNotifyLinked(Context context,DealRecord d,String bggId){
        if(context==null||d==null||bggId==null||bggId.isEmpty())return false;
        // A hunt alert must open a real, already identified Vinted item. Provisional observations
        // are kept in the engine but never wake the user.
        if(!DealAlertNotifier.exactIdentityConfirmed(context,d))return false;
        String verify=d.verificationState==null?"":d.verificationState.toUpperCase(Locale.ROOT);if(verify.contains("REVIEW")||verify.contains("UNCERTAIN")||verify.contains("ANOMALY")||verify.contains("EXPANSION_CHECK"))return false;
        String type=d.listingType==null?"":d.listingType.toUpperCase(Locale.ROOT);if(type.contains("ACCESSORY")||type.contains("COMPONENT")||type.contains("EMPTY_BOX")||type.contains("NON_GAME")||type.contains("BUNDLE"))return false;
        if(BoardGameIntakeGate.isStrongNonGameText(d.vintedTitle,d.vintedTitle))return false;
        Integer total=d.totalCents!=null?d.totalCents:d.protectedPriceCents!=null?d.protectedPriceCents:d.itemPriceCents;HuntDatabase db=new HuntDatabase(context);long now=System.currentTimeMillis();List<Hunt> matches=db.matches(bggId,total);for(Hunt h:matches){if(now-h.lastNotifiedAt<12*60*60_000L)continue;notify(context,h,d,total);db.markNotified(h.id,now);}db.close();return !matches.isEmpty();
    }
    private static void notify(Context c,Hunt h,DealRecord d,int total){
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(nm==null)return;if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Cacce di Ludo",NotificationManager.IMPORTANCE_DEFAULT));Intent open=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("open_hunts",true);PendingIntent pi=PendingIntent.getActivity(c,(int)h.id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);b.setSmallIcon(R.drawable.ludo_app_icon).setContentTitle("Ludo ha trovato "+h.name).setContentText("Annuncio a "+java.text.NumberFormat.getCurrencyInstance(Locale.ITALY).format(total/100.0)).setAutoCancel(true).setContentIntent(pi);nm.notify((int)(10_000+h.id),b.build());
    }
}
