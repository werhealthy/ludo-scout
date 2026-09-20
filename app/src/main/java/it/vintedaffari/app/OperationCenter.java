package it.vintedaffari.app;

import android.content.*;
import org.json.*;
import java.util.*;

/** Persistent activity/queue ledger shared by the observer and UI. */
public final class OperationCenter {
    public static final String PREFS="ludo_operations_v53";
    public static final String CHANGED="it.vintedaffari.app.OPERATIONS_CHANGED";
    public static final String RETRY="it.vintedaffari.app.RETRY_OPERATION";
    public static final String REFRESH_MISSING="it.vintedaffari.app.REFRESH_MISSING_DATA";
    public static final String QUEUED="in coda", RUNNING="in corso", PAUSED="in pausa", DONE="completato", ERROR="errore";
    public static final String LINK="link", SELLER="seller", SNAPSHOT="snapshot", DEEP_SCAN="deep_scan", CATALOG="catalog", MATCH="match", COVER="cover", BUNDLE="bundle", MAINTENANCE="maintenance";

    public static final class Task {
        public String id,type,label,detail,state,error; public long createdAt,updatedAt,retryAt; public int progress;
        Task(JSONObject o){id=o.optString("id");type=o.optString("type");label=o.optString("label");detail=o.optString("detail");state=o.optString("state");error=o.optString("error");updatedAt=o.optLong("updatedAt");createdAt=o.optLong("createdAt",updatedAt);progress=Math.max(0,Math.min(100,o.optInt("progress",0)));retryAt=o.optLong("retryAt",0L);}
    }
    public static final class Summary {public int queued,running,paused,done,error,total;public int active(){return queued+running+paused;}public int completed(){return done;}public int settled(){return done+error;}}
    private OperationCenter(){}

    public static synchronized void set(Context c,String id,String type,String detail,String state,String error){
        if(c==null||id==null)return;SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);JSONArray a=read(p);JSONArray out=new JSONArray();boolean found=false,changed=false;long created=System.currentTimeMillis();int progress=DONE.equals(state)?100:0;long retryAt=0L;String safeDetail=detail==null?"":detail,safeError=error==null?"":error;
        for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;if(id.equals(o.optString("id"))){found=true;created=o.optLong("createdAt",o.optLong("updatedAt",created));progress=DONE.equals(state)?100:o.optInt("progress",0);retryAt=(DONE.equals(state)||ERROR.equals(state))?0L:o.optLong("retryAt",0L);boolean same=Objects.equals(type,o.optString("type"))&&Objects.equals(safeDetail,o.optString("detail"))&&Objects.equals(state,o.optString("state"))&&Objects.equals(safeError,o.optString("error"))&&progress==o.optInt("progress",0)&&retryAt==o.optLong("retryAt",0L);if(same)out.put(o);else{out.put(make(id,type,safeDetail,state,safeError,created,progress,retryAt));changed=true;}}else out.put(o);}
        if(!found){out.put(make(id,type,safeDetail,state,safeError,created,progress,retryAt));changed=true;}
        while(out.length()>120){int remove=-1;for(int i=0;i<out.length();i++){JSONObject task=out.optJSONObject(i);String stateValue=task==null?"":task.optString("state");if(!QUEUED.equals(stateValue)&&!RUNNING.equals(stateValue)&&!PAUSED.equals(stateValue)){remove=i;break;}}if(remove<0)break;JSONArray n=new JSONArray();for(int i=0;i<out.length();i++)if(i!=remove)n.put(out.opt(i));out=n;changed=true;}
        if(changed){p.edit().putString("tasks",out.toString()).apply();broadcast(c);}
    }
    public static void queued(Context c,String id,String type,String detail){set(c,id,type,detail,QUEUED,"");}
    public static void running(Context c,String id,String type,String detail){set(c,id,type,detail,RUNNING,"");}
    public static void paused(Context c,String id,String type,String detail){set(c,id,type,detail,PAUSED,"");}
    public static void done(Context c,String id,String type,String detail){set(c,id,type,detail,DONE,"");}
    public static void error(Context c,String id,String type,String detail,String error){set(c,id,type,detail,ERROR,error);}

    /** Progress is factual completion of known steps, never elapsed-time animation. */
    public static synchronized void progress(Context c,String id,int progress,long retryAt){
        if(c==null||id==null)return;SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);JSONArray a=read(p),out=new JSONArray();boolean changed=false;int clamped=Math.max(0,Math.min(100,progress));long retry=Math.max(0L,retryAt);
        for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;if(id.equals(o.optString("id"))){if(o.optInt("progress",0)!=clamped||o.optLong("retryAt",0L)!=retry){try{o.put("progress",clamped);o.put("retryAt",retry);o.put("updatedAt",System.currentTimeMillis());}catch(Exception ignored){}changed=true;}}out.put(o);}if(changed){p.edit().putString("tasks",out.toString()).apply();broadcast(c);}
    }
    public static synchronized void reset(Context c,String id,String type,String detail){remove(c,id);queued(c,id,type,detail);progress(c,id,0,0L);}
    /** Closes a queued/running task without resurrecting an already settled row. */
    public static synchronized void doneIfActive(Context c,String id,String type,String detail){
        if(c==null||id==null)return;
        for(Task t:tasks(c))if(id.equals(t.id)&&(QUEUED.equals(t.state)||RUNNING.equals(t.state)||PAUSED.equals(t.state))){done(c,id,type,detail);return;}
    }
    public static List<Task> tasks(Context c){JSONArray a=read(c.getSharedPreferences(PREFS,Context.MODE_PRIVATE));List<Task> r=new ArrayList<>();for(int i=a.length()-1;i>=0;i--){JSONObject o=a.optJSONObject(i);if(o!=null)r.add(new Task(o));}return r;}
    /** Bundle/link plumbing stays in diagnostics. User-facing refreshes are represented by one card per product. */
    public static boolean isUserFacing(Task t){if(t==null)return false;if(t.id!=null&&t.id.startsWith("bgg-enrich:"))return false;return !(SNAPSHOT.equals(t.type)||DEEP_SCAN.equals(t.type)||BUNDLE.equals(t.type)||CATALOG.equals(t.type)||LINK.equals(t.type)||SELLER.equals(t.type)||COVER.equals(t.type));}
    public static List<Task> userTasks(Context c){List<Task> r=new ArrayList<>();for(Task t:tasks(c))if(isUserFacing(t))r.add(t);return r;}
    public static Summary userSummary(Context c){Summary s=new Summary();for(Task t:userTasks(c))add(s,t);return s;}
    public static Summary userCurrentSummary(Context c){List<Task> all=userTasks(c);boolean anyActive=false;long newestActive=0;for(Task t:all)if(QUEUED.equals(t.state)||RUNNING.equals(t.state)||PAUSED.equals(t.state)){anyActive=true;newestActive=Math.max(newestActive,t.createdAt);}Summary s=new Summary();if(!anyActive)return s;long cutoff=Math.min(newestActive,System.currentTimeMillis())-2*60*60_000L;for(Task t:all)if(t.createdAt>=cutoff)add(s,t);return s;}
    public static Task find(Context c,String id){if(c==null||id==null)return null;for(Task t:tasks(c))if(id.equals(t.id))return t;return null;}
    public static boolean active(Context c,String id){Task t=find(c,id);return t!=null&&(QUEUED.equals(t.state)||RUNNING.equals(t.state)||PAUSED.equals(t.state));}
    public static int userActive(Context c){return userSummary(c).active();}
    public static Summary summary(Context c){Summary s=new Summary();for(Task t:tasks(c))add(s,t);return s;}
    public static Summary currentSummary(Context c){List<Task> all=tasks(c);boolean anyActive=false;long newestActive=0;for(Task t:all)if(QUEUED.equals(t.state)||RUNNING.equals(t.state)||PAUSED.equals(t.state)){anyActive=true;newestActive=Math.max(newestActive,t.createdAt);}Summary s=new Summary();if(!anyActive)return s;long cutoff=Math.min(newestActive,System.currentTimeMillis())-2*60*60_000L;for(Task t:all)if(t.createdAt>=cutoff)add(s,t);return s;}
    private static void add(Summary s,Task t){s.total++;if(QUEUED.equals(t.state))s.queued++;else if(RUNNING.equals(t.state))s.running++;else if(PAUSED.equals(t.state))s.paused++;else if(DONE.equals(t.state))s.done++;else if(ERROR.equals(t.state))s.error++;}
    public static synchronized void clearFailures(Context c,String type){SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);JSONArray a=read(p),out=new JSONArray();boolean changed=false;for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;if(type.equals(o.optString("type"))&&ERROR.equals(o.optString("state"))){changed=true;continue;}out.put(o);}if(changed){p.edit().putString("tasks",out.toString()).apply();broadcast(c);}}
    /** Clear stale error cards as soon as the underlying product has actually been fixed. */
    public static synchronized void resolveForSignature(Context c,String signature,String detail){if(c==null||signature==null||signature.isEmpty())return;SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);JSONArray a=read(p),out=new JSONArray();boolean changed=false;for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;String id=o.optString("id");String state=o.optString("state");if(id.endsWith(signature)&&(ERROR.equals(state)||PAUSED.equals(state)||QUEUED.equals(state)||RUNNING.equals(state))){out.put(make(id,o.optString("type"),detail==null?"Risolto":detail,DONE,"",o.optLong("createdAt",System.currentTimeMillis()),100,0L));changed=true;}else out.put(o);}if(changed){p.edit().putString("tasks",out.toString()).apply();broadcast(c);}}
    /** Reconciles stale user-facing errors after a product was corrected and is now complete. */
    public static synchronized void resolveForDeal(Context c,String signature,String... labels){if(c==null)return;SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);JSONArray a=read(p),out=new JSONArray();boolean changed=false;for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;String id=o.optString("id"),state=o.optString("state"),taskDetail=o.optString("detail");boolean hit=signature!=null&&!signature.isEmpty()&&id.endsWith(signature);if(!hit&&labels!=null)for(String label:labels)if(label!=null&&!label.isEmpty()&&taskDetail!=null&&taskDetail.toLowerCase(Locale.ROOT).contains(label.toLowerCase(Locale.ROOT))){hit=true;break;}if(hit&&(ERROR.equals(state)||PAUSED.equals(state)||QUEUED.equals(state)||RUNNING.equals(state))){out.put(make(id,o.optString("type"),"Risolto",DONE,"",o.optLong("createdAt",System.currentTimeMillis()),100,0L));changed=true;}else out.put(o);}if(changed){p.edit().putString("tasks",out.toString()).apply();broadcast(c);}}

    public static synchronized void remove(Context c,String id){if(c==null||id==null)return;SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);JSONArray a=read(p),out=new JSONArray();boolean changed=false;for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&!id.equals(o.optString("id")))out.put(o);else if(o!=null)changed=true;}if(changed){p.edit().putString("tasks",out.toString()).apply();broadcast(c);}}
    public static void retry(Context c,Task t){done(c,t.id,t.type,(t.detail==null?"":t.detail)+" · nuovo tentativo richiesto");c.sendBroadcast(new Intent(RETRY).setPackage(c.getPackageName()).putExtra("type",t.type).putExtra("detail",t.detail));}
    public static synchronized void settleStaleActive(Context c,long maxAgeMs){if(c==null)return;SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);JSONArray a=read(p),out=new JSONArray();long now=System.currentTimeMillis();boolean changed=false;for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;String state=o.optString("state");long updated=o.optLong("updatedAt",o.optLong("createdAt",0));if((QUEUED.equals(state)||RUNNING.equals(state)||PAUSED.equals(state))&&updated>0&&now-updated>maxAgeMs){out.put(make(o.optString("id"),o.optString("type"),o.optString("detail")+" · sessione precedente chiusa",DONE,"",o.optLong("createdAt",updated),100,0L));changed=true;}else out.put(o);}if(changed){p.edit().putString("tasks",out.toString()).apply();broadcast(c);}}
    private static JSONObject make(String id,String type,String detail,String state,String error,long created,int progress,long retryAt){JSONObject o=new JSONObject();try{o.put("id",id);o.put("type",type);o.put("label",label(type));o.put("detail",detail==null?"":detail);o.put("state",state);o.put("error",error==null?"":error);o.put("createdAt",created);o.put("updatedAt",System.currentTimeMillis());o.put("progress",Math.max(0,Math.min(100,progress)));o.put("retryAt",Math.max(0L,retryAt));}catch(Exception ignored){}return o;}
    private static JSONArray read(SharedPreferences p){try{return new JSONArray(p.getString("tasks","[]"));}catch(Exception e){return new JSONArray();}}
    private static void broadcast(Context c){c.sendBroadcast(new Intent(CHANGED).setPackage(c.getPackageName()));}
    private static String label(String t){if(LINK.equals(t))return"Risoluzione link";if(SELLER.equals(t))return"Dati venditore";if(SNAPSHOT.equals(t))return"Controllo venditore";if(DEEP_SCAN.equals(t))return"Ricerca bundle";if(CATALOG.equals(t))return"Catalogo venditore";if(MATCH.equals(t))return"Ricerca gioco";if(COVER.equals(t))return"Immagine gioco";if(MAINTENANCE.equals(t))return"Aggiornamento dati";return"Bundle";}
}
