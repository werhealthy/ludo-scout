package it.vintedaffari.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Opt-in immutable snapshot publisher; SQLite is read-only and remains authoritative. */
public final class FirestoreSnapshotWorker extends Worker {
    private static final int MAX_PER_PASS = 80;
    private static final int MAX_PER_DAY = 2400;
    static final String QUERY = "SELECT l.id,l.language_code,l.vinted_title,l.brand,l.category_normalized,"
            + "l.category_source,g.bgg_id,l.lifecycle,l.enrichment_state,l.match_state "
            + "FROM market_listings l LEFT JOIN games g ON g.id=l.game_id ORDER BY l.id";

    public FirestoreSnapshotWorker(@NonNull Context context,@NonNull WorkerParameters parameters){super(context,parameters);}

    @NonNull @Override public Result doWork(){
        Context context=getApplicationContext();
        if (!FirestoreSync.enabled(context)) return Result.success();
        File catalog=context.getDatabasePath("vinted_affari.db");if(!catalog.isFile())return Result.success();
        FirestoreSnapshotStore store=null;FileChannel leaseChannel=null;FileLock lease=null;
        try{
            File lockFile=new File(context.getNoBackupFilesDir(),"firestore-snapshot-worker.lock");
            leaseChannel=FileChannel.open(lockFile.toPath(),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
            lease=leaseChannel.tryLock();if(lease==null)return Result.retry();
            SharedPreferences prefs=FirestoreSync.progress(context);String device=FirestoreSync.deviceId(context);
            if(!device.equals(prefs.getString("device_id",""))){
                prefs.edit().clear().putString("device_id",device).commit();
                return Result.success();
            }
            int day=(int)(System.currentTimeMillis()/86_400_000L);int spent=prefs.getInt("budget_day",-1)==day?prefs.getInt("budget_writes",0):0;
            if(spent>=MAX_PER_DAY)return Result.success();FirebaseFirestore cloud=FirestoreSync.store(context);
            if(cloud==null||!FirestoreSync.enabled(context))return Result.success();Tasks.await(cloud.waitForPendingWrites(),20,TimeUnit.SECONDS);
            store=FirestoreSnapshotStore.open(context);FirestoreSnapshotStore.Snapshot state=store.snapshot();
            if(state!=null&&state.protocolVersion!=3){
                store.resetIdle();state=null;
            }
            String owner=BuildConfig.LUDO_SYNC_OWNER_UID;DocumentReference current=currentRef(cloud,owner,device);
            if(state!=null&&"CLEAN_OLD".equals(state.state))return cleanupOld(cloud,store,state,owner,device,prefs,day,spent);
            if(state!=null&&("ABORTING".equals(state.state)||"ABORTING_SEALED".equals(state.state)))return cleanupAborted(cloud,store,state,owner,device,prefs,day,spent);
            if(state==null||"PUBLISHED".equals(state.state)){
                String hash=sourceHash(catalog);if(state!=null&&hash.equals(state.contentHash))return Result.success();
                store.begin(FirestoreSnapshotStore.newId(),readCurrentId(current));materialize(catalog,store);state=store.snapshot();
            }else if("MATERIALIZING".equals(state.state)){
                String id=state.id,previous=state.previousId;store.begin(id,previous);materialize(catalog,store);state=store.snapshot();
            }
            if("STAGE_MANIFEST".equals(state.state)){
                if(!state.contentHash.equals(sourceHash(catalog))){store.markAborted();return Result.success();}
                DocumentReference manifestRef=snapshot(cloud,owner,device,state.id);
                DocumentSnapshot existing=Tasks.await(manifestRef.get(),20,TimeUnit.SECONDS);
                if(existing.exists()){
                    if(matchesManifest(existing,state.id,state.count,state.contentHash,false)){
                        store.state("UPLOADING");return Result.success();
                    }
                    store.markAborted();return Result.success();
                }
                if(!reserve(prefs,day,spent,1))return Result.success();
                Map<String,Object> manifest=new LinkedHashMap<>();manifest.put("schema_version",3);
                manifest.put("author_coverage_available",false);
                manifest.put("snapshot_id",state.id);manifest.put("document_count",state.count);
                manifest.put("content_hash",state.contentHash);manifest.put("complete",false);
                manifest.put("created_at",FieldValue.serverTimestamp());
                if(!FirestoreSync.enabled(context)||isStopped())return Result.success();
                Tasks.await(manifestRef.set(manifest),35,TimeUnit.SECONDS);store.state("UPLOADING");return Result.success();
            }
            if("UPLOADING".equals(state.state)){
                if(state.uploaded>=state.count){if(!state.contentHash.equals(sourceHash(catalog))){store.markAborted();return Result.success();}store.state("MANIFEST");return Result.success();}
                int cap=Math.min(MAX_PER_PASS,MAX_PER_DAY-spent);if(cap<=0)return Result.success();
                List<Long> ids=new ArrayList<>();WriteBatch batch=cloud.batch();
                try(Cursor rows=store.nextRows(cap)){while(rows.moveToNext()){
                    long id=rows.getLong(0);ids.add(id);Map<String,Object> fields=decode(rows.getString(1),state.id);
                    batch.set(listings(cloud,owner,device,state.id).document(Long.toString(id)),fields);
                }}
                if(ids.isEmpty()){store.markAborted();return Result.success();}
                long[] done=new long[ids.size()];for(int i=0;i<ids.size();i++)done[i]=ids.get(i);
                store.markUploadAttempted(done);
                if(!reserve(prefs,day,spent,ids.size()))return Result.success();
                if(!FirestoreSync.enabled(context)||isStopped())return Result.success();
                Tasks.await(batch.commit(),35,TimeUnit.SECONDS);store.markUploaded(done);return Result.success();
            }
            if("MANIFEST".equals(state.state)){
                if(!state.contentHash.equals(sourceHash(catalog))){store.markAborted();return Result.success();}
                DocumentReference manifestRef=snapshot(cloud,owner,device,state.id);
                DocumentSnapshot existing=Tasks.await(manifestRef.get(),20,TimeUnit.SECONDS);
                if(!existing.exists()||!matchesManifest(existing,state.id,state.count,state.contentHash,
                        Boolean.TRUE.equals(existing.getBoolean("complete")))){store.markAborted();return Result.success();}
                if(Boolean.TRUE.equals(existing.getBoolean("complete"))){store.state("PUBLISH");return Result.success();}
                if(!reserve(prefs,day,spent,1))return Result.success();
                if(!FirestoreSync.enabled(context)||isStopped())return Result.success();
                Tasks.await(manifestRef.update("complete",true),35,TimeUnit.SECONDS);store.state("PUBLISH");return Result.success();
            }
            if("PUBLISH".equals(state.state)){
                String active=readCurrentId(current);if(state.id.equals(active)){store.markCleanupPending(state.previousId);return Result.success();}
                if(!state.contentHash.equals(sourceHash(catalog))||!same(state.previousId,active)){store.markAborted();return Result.success();}
                if(!reserve(prefs,day,spent,1))return Result.success();Map<String,Object> pointer=new LinkedHashMap<>();
                pointer.put("schema_version",3);pointer.put("snapshot_id",state.id);pointer.put("updated_at",FieldValue.serverTimestamp());
                if(!FirestoreSync.enabled(context)||isStopped())return Result.success();
                Tasks.await(current.set(pointer),35,TimeUnit.SECONDS);store.markCleanupPending(state.previousId);return Result.success();
            }
            return Result.retry();
        }catch(SQLiteException full){
            // Keep the durable generation state: a remote batch may have committed
            // immediately before its local acknowledgement hit SQLite's size cap.
            FirestoreSync.progress(context).edit().putString("snapshot_error","local_snapshot_limit").commit();return Result.success();}
        catch(IllegalStateException bounded){
            // Retain resumable state; MATERIALIZING restarts locally, while an
            // UPLOADING generation can safely replay idempotent document sets.
            FirestoreSync.progress(context).edit().putString("snapshot_error",bounded.getMessage()==null?"snapshot_invalid":bounded.getMessage()).commit();return Result.success();}
        catch(Exception failure){return Result.retry();}finally{
            if(store!=null)store.close();
            try{if(lease!=null)lease.release();}catch(Exception ignored){}
            try{if(leaseChannel!=null)leaseChannel.close();}catch(Exception ignored){}
        }
    }

    private Result cleanupAborted(FirebaseFirestore db,FirestoreSnapshotStore store,FirestoreSnapshotStore.Snapshot s,
            String owner,String device,SharedPreferences prefs,int day,int spent)throws Exception{
        int writesSpent=spent;
        int remaining=MAX_PER_DAY-writesSpent;
        if("ABORTING".equals(s.state)){
            if(remaining<=0)return Result.success();
            if(!reserve(prefs,day,writesSpent,1))return Result.success();
            writesSpent++;
            remaining--;
            if(!FirestoreSync.enabled(getApplicationContext())||isStopped())return Result.success();
            // Remove the incomplete manifest first; Firestore rules then reject
            // any delayed listing upload for this abandoned generation.
            Tasks.await(snapshot(db,owner,device,s.id).delete(),35,TimeUnit.SECONDS);
            // If SQLite cannot persist the sealed state, continue from Firestore's
            // listing query anyway. The next pass can repeat the idempotent delete.
            try{store.state("ABORTING_SEALED");}catch(SQLiteException localStateFailure){
                FirestoreSync.progress(getApplicationContext()).edit()
                        .putString("snapshot_error","aborted_snapshot_state_write_failed").commit();
            }
        }
        int cap=Math.min(MAX_PER_PASS,remaining);if(cap<=0)return Result.success();
        // Enumerate remote staging rows directly. Local upload/cleanup acknowledgements
        // can fail under SQLite pressure, so they must not be the only delete manifest.
        QuerySnapshot page=Tasks.await(listings(db,owner,device,s.id).orderBy(FieldPath.documentId()).limit(cap).get(),20,TimeUnit.SECONDS);
        if(!page.isEmpty()){if(!reserve(prefs,day,writesSpent,page.size()))return Result.success();
            if(!FirestoreSync.enabled(getApplicationContext())||isStopped())return Result.success();
            WriteBatch batch=db.batch();for(QueryDocumentSnapshot document:page)batch.delete(document.getReference());
            Tasks.await(batch.commit(),35,TimeUnit.SECONDS);return Result.success();}
        store.resetIdle();return Result.success();
    }
    private Result cleanupOld(FirebaseFirestore db,FirestoreSnapshotStore store,FirestoreSnapshotStore.Snapshot s,
            String owner,String device,SharedPreferences prefs,int day,int spent)throws Exception{
        if(s.previousId.isEmpty()){store.clearAfterPublish(s.id,s.contentHash);return Result.success();}
        int cap=Math.min(MAX_PER_PASS,MAX_PER_DAY-spent);if(cap<=0)return Result.success();
        // Deleted rows disappear from the query, so always resume from the first
        // remaining ID. A persisted cursor could skip a row recreated behind it.
        com.google.firebase.firestore.Query q=listings(db,owner,device,s.previousId).orderBy(FieldPath.documentId()).limit(cap);
        QuerySnapshot page=Tasks.await(q.get(),20,TimeUnit.SECONDS);
        if(!page.isEmpty()){
            WriteBatch batch=db.batch();for(QueryDocumentSnapshot d:page)batch.delete(d.getReference());
            if(!reserve(prefs,day,spent,page.size()))return Result.success();if(!FirestoreSync.enabled(getApplicationContext())||isStopped())return Result.success();
            Tasks.await(batch.commit(),35,TimeUnit.SECONDS);
            return Result.success();
        }
        // Recheck before deleting the parent: the rules only permit listing writes
        // while this generation's staging manifest is incomplete.
        QuerySnapshot beforeDelete=Tasks.await(listings(db,owner,device,s.previousId).limit(1).get(),20,TimeUnit.SECONDS);
        if(!beforeDelete.isEmpty())return Result.success();
        if(!reserve(prefs,day,spent,1))return Result.success();if(!FirestoreSync.enabled(getApplicationContext())||isStopped())return Result.success();
        Tasks.await(snapshot(db,owner,device,s.previousId).delete(),35,TimeUnit.SECONDS);
        // Completed manifests reject later listing writes; this final read keeps
        // cleanup resumable if a previously queued write raced the first query.
        QuerySnapshot finalCheck=Tasks.await(listings(db,owner,device,s.previousId).limit(1).get(),20,TimeUnit.SECONDS);
        if(!finalCheck.isEmpty())return Result.success();
        store.clearAfterPublish(s.id,s.contentHash);return Result.success();
    }

    private static void materialize(File file,FirestoreSnapshotStore store)throws Exception{
        MessageDigest root=FirestoreSnapshotStore.sha256();long count=0;
        try(SQLiteDatabase source=SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)){
            // Deferred BEGIN is read-only on this OPEN_READONLY handle and holds one DB snapshot.
            source.execSQL("BEGIN");
            try(Cursor c=source.rawQuery(QUERY,null)){while(c.moveToNext()){
                if(count>=FirestoreSnapshotStore.MAX_ROWS)throw new IllegalStateException("snapshot_row_limit");Row row=project(c);
                String payload=new JSONObject(row.fields).toString();if(payload.getBytes(StandardCharsets.UTF_8).length>12_000)throw new IllegalStateException("snapshot_document_limit");
                store.insertRow(row.id,payload,row.hash);FirestoreSnapshotStore.updateRoot(root,row.id,row.hash);count++;}
                store.finishMaterialization(count,FirestoreSnapshotStore.hex(root.digest()));
            }finally{source.execSQL("ROLLBACK");}
        }
    }
    private static String sourceHash(File file)throws Exception{
        MessageDigest root=FirestoreSnapshotStore.sha256();try(SQLiteDatabase source=SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)){
            source.execSQL("BEGIN");
            try(Cursor c=source.rawQuery(QUERY,null)){long count=0;while(c.moveToNext()){
                Row r=project(c);FirestoreSnapshotStore.updateRoot(root,r.id,r.hash);if(++count>FirestoreSnapshotStore.MAX_ROWS)throw new IllegalStateException("snapshot_row_limit");}
            }finally{source.execSQL("ROLLBACK");}
        }
        return FirestoreSnapshotStore.hex(root.digest());
    }
    private static Row project(Cursor c)throws Exception{
        long id=c.getLong(0);if(id<=0)throw new IllegalArgumentException("invalid local ID");Map<String,Object> v=new LinkedHashMap<>();
        String title=str(c,2,400),brand=str(c,3,120),category=str(c,4,80),categorySource=str(c,5,80);
        v.put("schema_version",3);v.put("source_id",id);v.put("title",title);v.put("title_source","VINTED_LISTING");
        v.put("brand",brand);v.put("brand_source","VINTED_LISTING");v.put("category",category);
        v.put("category_source",categorySource);v.put("category_provenance_available",!categorySource.isEmpty());
        v.put("language",normalizedLanguage(str(c,1,40)));v.put("bgg_id",str(c,6,80));
        v.put("lifecycle",str(c,7,40));v.put("enrichment_state",str(c,8,60));v.put("match_state",str(c,9,60));
        String hash=FirestoreSnapshotStore.hex(FirestoreSnapshotStore.sha256().digest(canonicalJson(v).getBytes(StandardCharsets.UTF_8)));
        v.put("source_hash",hash);return new Row(id,hash,v);
    }
    static String normalizedLanguage(String value){
        String code=value==null?"":value.trim().toUpperCase(java.util.Locale.ROOT);
        return java.util.Arrays.asList("IT","EN","DE","FR","ES","PT","NL","MULTI","OTHER").contains(code)?code:"UNKNOWN";
    }
    static String canonicalJson(Map<String,Object> values){
        List<String> keys=new ArrayList<>(values.keySet());java.util.Collections.sort(keys);StringBuilder out=new StringBuilder("{");
        for(int i=0;i<keys.size();i++){if(i>0)out.append(',');String key=keys.get(i);Object value=values.get(key);
            out.append(quoteJson(key)).append(':');
            if(value==null)out.append("null");else if(value instanceof String)out.append(quoteJson((String)value));
            else if(value instanceof List){out.append('[');List<?> items=(List<?>)value;for(int j=0;j<items.size();j++){if(j>0)out.append(',');out.append(quoteJson(String.valueOf(items.get(j))));}out.append(']');}
            else out.append(value.toString());
        }
        return out.append('}').toString();
    }
    private static String quoteJson(String value){
        StringBuilder out=new StringBuilder("\"");final char[] hex="0123456789abcdef".toCharArray();
        for(int i=0;i<value.length();i++){char ch=value.charAt(i);switch(ch){
            case '"':out.append("\\\"");break;case '\\':out.append("\\\\");break;case '\b':out.append("\\b");break;
            case '\f':out.append("\\f");break;case '\n':out.append("\\n");break;case '\r':out.append("\\r");break;case '\t':out.append("\\t");break;
            default:if(ch<0x20){out.append("\\u00").append(hex[(ch>>4)&15]).append(hex[ch&15]);}else out.append(ch);
        }}return out.append('"').toString();
    }
    private static Map<String,Object> decode(String text,String snapshotId)throws Exception{
        JSONObject j=new JSONObject(text);Map<String,Object> out=new LinkedHashMap<>();java.util.Iterator<String> it=j.keys();
        while(it.hasNext()){String k=it.next();Object v=j.get(k);out.put(k,v==JSONObject.NULL?null:v);}out.put("snapshot_id",snapshotId);return out;
    }
    private static boolean reserve(SharedPreferences p,int day,int spent,int count){if(count<1||count>MAX_PER_PASS||spent+count>MAX_PER_DAY)return false;
        return p.edit().putInt("budget_day",day).putInt("budget_writes",spent+count).commit();}
    private static DocumentReference currentRef(FirebaseFirestore d,String o,String dev){return d.collection("sync_v3").document(o).collection("devices").document(dev).collection("meta").document("current");}
    private static DocumentReference snapshot(FirebaseFirestore d,String o,String dev,String id){return d.collection("sync_v3").document(o).collection("devices").document(dev).collection("snapshots").document(id);}
    private static com.google.firebase.firestore.CollectionReference listings(FirebaseFirestore d,String o,String dev,String id){return snapshot(d,o,dev,id).collection("listings");}
    private static String readCurrentId(DocumentReference r)throws Exception{com.google.firebase.firestore.DocumentSnapshot d=Tasks.await(r.get(),20,TimeUnit.SECONDS);String id=d.getString("snapshot_id");return id==null?"":id;}
    private static boolean matchesManifest(DocumentSnapshot document,String id,long count,String hash,boolean complete){
        Long schema=document.getLong("schema_version"),rows=document.getLong("document_count");
        return document.exists()&&Long.valueOf(3L).equals(schema)&&Long.valueOf(count).equals(rows)
                &&id.equals(document.getString("snapshot_id"))&&hash.equals(document.getString("content_hash"))
                &&Boolean.valueOf(complete).equals(document.getBoolean("complete"));
    }
    private static boolean same(String a,String b){return(a==null?"":a).equals(b==null?"":b);}
    private static final class Row{final long id;final String hash;final Map<String,Object> fields;Row(long i,String h,Map<String,Object> f){id=i;hash=h;fields=f;}}
    private static Long nonnegative(Cursor c,int i){return c.isNull(i)||c.getLong(i)<0?null:c.getLong(i);}
    private static String str(Cursor c,int i,int cap){if(c.isNull(i))return"";String s=c.getString(i);if(s==null)return"";s=s.trim();return s.length()<=cap?s:s.substring(0,cap);}
    private static List<String> photos(String csv,String primary){List<String> out=new ArrayList<>();if(!primary.isEmpty()&&primary.startsWith("https://"))out.add(primary);
        for(String x:csv.split(",")){String u=x.trim();if(u.startsWith("https://")&&u.length()<=1200&&!out.contains(u))out.add(u);if(out.size()==3)break;}return out;}
}
