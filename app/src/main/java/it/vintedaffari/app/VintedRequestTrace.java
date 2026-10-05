package it.vintedaffari.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Bounded cross-process evidence in existing queue_controls. Never stores page or seller values. */
final class VintedRequestTrace {
    private static final ThreadLocal<String> OWNER=new ThreadLocal<>();
    static void owner(String owner){if(owner==null)OWNER.remove();else OWNER.set(owner);}
    static void record(Context context,String stage,String detail){
        DealDatabase helper=null;SQLiteDatabase db=null;
        try{
            helper=new DealDatabase(context.getApplicationContext());db=helper.getWritableDatabase();
            db.beginTransactionNonExclusive();long sequence=0;
            try(Cursor c=db.rawQuery("SELECT value FROM queue_controls WHERE name='diag:vinted_trace_sequence'",null)){if(c.moveToFirst())sequence=c.getLong(0);}
            sequence++;long now=System.currentTimeMillis();
            String component="VintedPublicSession";
            for(StackTraceElement e:Thread.currentThread().getStackTrace()){
                String n=e.getClassName();if(n.startsWith("it.vintedaffari.app.")&&!n.endsWith("VintedRequestTrace")&&!n.endsWith("VintedPublicSession")){component=n.substring(n.lastIndexOf('.')+1);break;}
            }
            String event="seq="+sequence+";at="+now+";pid="+android.os.Process.myPid()+";process="+android.app.Application.getProcessName()+";component="+component+";"+(OWNER.get()==null?"job=none":OWNER.get())+";stage="+stage+";"+detail;
            put(db,"diag:vinted_trace_sequence",sequence,now,null);
            put(db,"diag:vinted_trace:"+(sequence%64),sequence,now,event);
            db.setTransactionSuccessful();
        }catch(Throwable failure){android.util.Log.w("VintedTrace","diagnostic write failed: "+failure.getClass().getSimpleName());}
        finally{try{if(db!=null&&db.inTransaction())db.endTransaction();}catch(Throwable ignored){}try{if(helper!=null)helper.close();}catch(Throwable ignored){}}
    }
    private static void put(SQLiteDatabase db,String name,long value,long now,String text){
        ContentValues v=new ContentValues();v.put("name",name);v.put("value",value);v.put("updated_at",now);v.put("text_value",text);
        db.insertWithOnConflict("queue_controls",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
}
