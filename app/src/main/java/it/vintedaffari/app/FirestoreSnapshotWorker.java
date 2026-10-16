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
import java.util.function.Supplier;
import org.json.JSONObject;

/** Opt-in immutable snapshot publisher; SQLite is read-only and remains authoritative. */
public final class FirestoreSnapshotWorker extends Worker {
    private static final int MAX_PER_PASS = 80;
    private static final int MAX_PER_DAY = 2400;
    static final String QUERY = "SELECT l.id,l.language_code,l.vinted_title,l.brand,l.category_normalized,"
            + "l.category_source,g.bgg_id,l.lifecycle,l.enrichment_state,l.match_state,"
            + "l.observed_text,l.listing_photos_csv,l.image_url "
            + "FROM market_listings l LEFT JOIN games g ON g.id=l.game_id ORDER BY l.id";
    static final int MAX_EVIDENCE_TEXT = 2_000;
    static final int MAX_PHOTO_URLS = 4;
    static final int MAX_DOCUMENT_BYTES = 12_000;
    private static final int SNAPSHOT_PROTOCOL_VERSION = 6;
    // Retain the currently published generation plus one rollback generation.
    private static final int RETAINED_GENERATIONS = 2;

    public FirestoreSnapshotWorker(@NonNull Context context,@NonNull WorkerParameters parameters){super(context,parameters);}

    @NonNull @Override public Result doWork(){
        Context context=getApplicationContext();
        FirestoreWorkerDiagnostics.Run diagnostics=FirestoreWorkerDiagnostics.begin(context);
        diagnostics.progress("CHECKS");
        if (!FirestoreSync.enabled(context)) {diagnostics.progress("NOT_ACTIVE");return diagnostics.success();}
        boolean evidenceEnabled=FirestoreSync.evidenceUploadEnabled(context);
        File catalog=context.getDatabasePath("vinted_affari.db");if(!catalog.isFile()){diagnostics.progress("CATALOG_MISSING");return diagnostics.success();}
        FirestoreSnapshotStore store=null;FileChannel leaseChannel=null;FileLock lease=null;
        try{
            File lockFile=new File(context.getNoBackupFilesDir(),"firestore-snapshot-worker.lock");
            leaseChannel=FileChannel.open(lockFile.toPath(),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
            lease=leaseChannel.tryLock();if(lease==null){diagnostics.progress("LOCK_BUSY");return diagnostics.retry();}
            SharedPreferences prefs=FirestoreSync.progress(context);String device=FirestoreSync.deviceId(context);
            boolean identityChanged=!device.equals(prefs.getString("device_id",""));
            if(identityChanged&&!prefs.edit().putString("device_id",device).putInt("budget_day",-1)
                    .putInt("budget_writes",0).remove("snapshot_error").commit()){diagnostics.progress("LOCAL_ERROR");return diagnostics.retry();}
            int day=(int)(System.currentTimeMillis()/86_400_000L);int spent=prefs.getInt("budget_day",-1)==day?prefs.getInt("budget_writes",0):0;
            if(spent>=MAX_PER_DAY){diagnostics.progress("BUDGET_LIMIT");return diagnostics.success();}FirebaseFirestore cloud=FirestoreSync.store(context);
            if(cloud==null||!FirestoreSync.enabled(context)){diagnostics.progress("NOT_ACTIVE");return diagnostics.success();}Tasks.await(cloud.waitForPendingWrites(),20,TimeUnit.SECONDS);
            store=FirestoreSnapshotStore.open(context);if(identityChanged)store.resetIdle();
            FirestoreSnapshotStore.Snapshot state=store.snapshot();diagnostics.snapshot("LOAD_SNAPSHOT",state);
            String owner=BuildConfig.LUDO_SYNC_OWNER_UID;DocumentReference current=currentRef(cloud,owner,device);
            if(state!=null&&state.protocolVersion!=SNAPSHOT_PROTOCOL_VERSION){
                if("CLEAN_OLD".equals(state.state)||"ABORTING".equals(state.state)||"ABORTING_SEALED".equals(state.state)){
                    // Finish already-authorized cleanup before migrating the local sidecar format.
                }else if("PUBLISH".equals(state.state)){
                    // A completed legacy generation may still need its pointer acknowledgement.
                }else if("PUBLISHED".equals(state.state)){
                    store.resetIdle();state=null;
                }else{
                    // Never complete a pre-evidence partial generation under the new schema.
                    store.markAborted();state=store.snapshot();
                    return cleanupAborted(cloud,store,state,owner,device,prefs,day,spent,diagnostics);
                }
            }
            if(state!=null&&"CLEAN_OLD".equals(state.state))return cleanupOld(cloud,store,state,owner,device,prefs,day,spent,diagnostics);
            if(state!=null&&("ABORTING".equals(state.state)||"ABORTING_SEALED".equals(state.state)))return cleanupAborted(cloud,store,state,owner,device,prefs,day,spent,diagnostics);
            if(state!=null&&"PUBLISH".equals(state.state)&&state.includesEvidence!=evidenceEnabled){
                // Completed but not current: do not publish after consent is revoked.
                store.markAborted();state=store.snapshot();
                return cleanupAborted(cloud,store,state,owner,device,prefs,day,spent,diagnostics);
            }
            if(!FirestoreSync.enabled(context)||isStopped()){diagnostics.progress("NOT_ACTIVE");return diagnostics.success();}
            if(state!=null&&state.protocolVersion==SNAPSHOT_PROTOCOL_VERSION
                    && state.includesEvidence!=evidenceEnabled
                    && ("STAGE_MANIFEST".equals(state.state)||"UPLOADING".equals(state.state)||"MANIFEST".equals(state.state))){
                store.markAborted();state=store.snapshot();
                return cleanupAborted(cloud,store,state,owner,device,prefs,day,spent,diagnostics);
            }
            if(state==null||"PUBLISHED".equals(state.state)){
                String hash=sourceHash(catalog,evidenceEnabled);if(state!=null&&hash.equals(state.contentHash)){diagnostics.progress("NO_CHANGE");return diagnostics.success();}
                diagnostics.progress("MATERIALIZE");String active=readCurrentId(current);String expired=active.isEmpty()?"":readPreviousGenerationId(current);store.begin(FirestoreSnapshotStore.newId(),active,evidenceEnabled,expired);materialize(catalog,store,evidenceEnabled);state=store.snapshot();diagnostics.snapshot("SNAPSHOT_PREPARED",state);
            }else if("MATERIALIZING".equals(state.state)){
                String id=state.id,previous=state.previousId;diagnostics.progress("MATERIALIZE");store.begin(id,previous,evidenceEnabled);materialize(catalog,store,evidenceEnabled);state=store.snapshot();diagnostics.snapshot("SNAPSHOT_PREPARED",state);
            }
            if("STAGE_MANIFEST".equals(state.state)){
                diagnostics.snapshot("STAGE_MANIFEST",state);
                if(!state.contentHash.equals(sourceHash(catalog,evidenceEnabled))){store.markAborted();diagnostics.snapshot("ABORTED",store.snapshot());return diagnostics.success();}
                DocumentReference manifestRef=snapshot(cloud,owner,device,state.id);
                DocumentSnapshot existing=Tasks.await(manifestRef.get(),20,TimeUnit.SECONDS);
                if(existing.exists()){
                    if(matchesManifest(existing,state.id,state.count,state.contentHash,false)){
                        store.state("UPLOADING");diagnostics.snapshot("MANIFEST_STAGED",store.snapshot());return diagnostics.success();
                    }
                    store.markAborted();diagnostics.snapshot("ABORTED",store.snapshot());return diagnostics.success();
                }
                if(!reserve(prefs,day,spent,1)){diagnostics.progress("BUDGET_LIMIT");return diagnostics.success();}
                Map<String,Object> manifest=new LinkedHashMap<>();manifest.put("schema_version",3);
                manifest.put("author_coverage_available",false);
                manifest.put("snapshot_id",state.id);manifest.put("document_count",state.count);
                manifest.put("content_hash",state.contentHash);manifest.put("complete",false);
                manifest.put("created_at",FieldValue.serverTimestamp());
                if(!FirestoreSync.enabled(context)||isStopped()){diagnostics.progress("NOT_ACTIVE");return diagnostics.success();}
                com.google.android.gms.tasks.Task<Void> stageTask = commitIfEvidenceModeMatches(
                        context, state, () -> manifestRef.set(manifest));
                if(stageTask==null){store.markAborted();return cleanupAborted(cloud,store,store.snapshot(),owner,device,prefs,day,spent,diagnostics);}
                Tasks.await(stageTask,35,TimeUnit.SECONDS);store.state("UPLOADING");diagnostics.snapshot("MANIFEST_STAGED",store.snapshot());return diagnostics.success();
            }
            if("UPLOADING".equals(state.state)){
                diagnostics.snapshot("UPLOAD_BATCH",state);
                if(state.uploaded>=state.count){if(!state.contentHash.equals(sourceHash(catalog,evidenceEnabled))){store.markAborted();diagnostics.snapshot("ABORTED",store.snapshot());return diagnostics.success();}store.state("MANIFEST");diagnostics.snapshot("FINALIZE_MANIFEST",store.snapshot());return diagnostics.success();}
                int cap=Math.min(MAX_PER_PASS,MAX_PER_DAY-spent);if(cap<=0){diagnostics.progress("BUDGET_LIMIT");return diagnostics.success();}
                List<Long> ids=new ArrayList<>();WriteBatch batch=cloud.batch();
                try(Cursor rows=store.nextRows(cap)){while(rows.moveToNext()){
                    long id=rows.getLong(0);ids.add(id);Map<String,Object> fields=decode(rows.getString(1),state.id);
                    batch.set(listings(cloud,owner,device,state.id).document(Long.toString(id)),fields);
                }}
                if(ids.isEmpty()){store.markAborted();diagnostics.snapshot("ABORTED",store.snapshot());return diagnostics.success();}
                long[] done=new long[ids.size()];for(int i=0;i<ids.size();i++)done[i]=ids.get(i);
                store.markUploadAttempted(done);
                if(!reserve(prefs,day,spent,ids.size())){diagnostics.progress("BUDGET_LIMIT");return diagnostics.success();}
                if(!FirestoreSync.enabled(context)||isStopped()){diagnostics.progress("NOT_ACTIVE");return diagnostics.success();}
                com.google.android.gms.tasks.Task<Void> uploadTask = commitIfEvidenceModeMatches(
                        context, state, batch::commit);
                if(uploadTask==null){store.markAborted();return cleanupAborted(cloud,store,store.snapshot(),owner,device,prefs,day,spent,diagnostics);}
                Tasks.await(uploadTask,35,TimeUnit.SECONDS);store.markUploaded(done);diagnostics.snapshot("BATCH_SENT",store.snapshot());return diagnostics.success();
            }
            if("MANIFEST".equals(state.state)){
                diagnostics.snapshot("FINALIZE_MANIFEST",state);
                if(!state.contentHash.equals(sourceHash(catalog,evidenceEnabled))){store.markAborted();diagnostics.snapshot("ABORTED",store.snapshot());return diagnostics.success();}
                DocumentReference manifestRef=snapshot(cloud,owner,device,state.id);
                DocumentSnapshot existing=Tasks.await(manifestRef.get(),20,TimeUnit.SECONDS);
                if(!existing.exists()||!matchesManifest(existing,state.id,state.count,state.contentHash,
                        Boolean.TRUE.equals(existing.getBoolean("complete")))){store.markAborted();diagnostics.snapshot("ABORTED",store.snapshot());return diagnostics.success();}
                if(Boolean.TRUE.equals(existing.getBoolean("complete"))){store.state("PUBLISH");diagnostics.snapshot("PUBLISH_CURRENT",store.snapshot());return diagnostics.success();}
                if(!reserve(prefs,day,spent,1)){diagnostics.progress("BUDGET_LIMIT");return diagnostics.success();}
                if(!FirestoreSync.enabled(context)||isStopped()){diagnostics.progress("NOT_ACTIVE");return diagnostics.success();}
                com.google.android.gms.tasks.Task<Void> completeTask = commitIfEvidenceModeMatches(
                        context, state, () -> manifestRef.update("complete",true));
                if(completeTask==null){store.markAborted();return cleanupAborted(cloud,store,store.snapshot(),owner,device,prefs,day,spent,diagnostics);}
                Tasks.await(completeTask,35,TimeUnit.SECONDS);store.state("PUBLISH");diagnostics.snapshot("MANIFEST_COMPLETE",store.snapshot());return diagnostics.success();
            }
            if("PUBLISH".equals(state.state)){
                diagnostics.snapshot("PUBLISH_CURRENT",state);
                String active=readCurrentId(current);
                if(state.id.equals(active)){store.markCleanupPending(state.previousId);diagnostics.published("POINTER_PUBLISHED",state);return diagnostics.success();}
                if(!state.contentHash.equals(sourceHash(catalog,evidenceEnabled))||!same(state.previousId,active)){store.markAborted();diagnostics.snapshot("ABORTED",store.snapshot());return diagnostics.success();}
                if(!reserve(prefs,day,spent,1)){diagnostics.progress("BUDGET_LIMIT");return diagnostics.success();}Map<String,Object> pointer=new LinkedHashMap<>();
                pointer.put("schema_version",3);pointer.put("snapshot_id",state.id);pointer.put("rollback_snapshot_id",active);pointer.put("updated_at",FieldValue.serverTimestamp());
                if(!FirestoreSync.enabled(context)||isStopped()){diagnostics.progress("NOT_ACTIVE");return diagnostics.success();}
                com.google.android.gms.tasks.Task<Void> pointerTask = commitIfEvidenceModeMatches(
                        context, state, () -> current.set(pointer));
                if(pointerTask==null){store.markAborted();return cleanupAborted(cloud,store,store.snapshot(),owner,device,prefs,day,spent,diagnostics);}
                Tasks.await(pointerTask,35,TimeUnit.SECONDS);store.markCleanupPending(state.previousId);diagnostics.published("POINTER_PUBLISHED",state);return diagnostics.success();
            }
            diagnostics.progress("RETRYABLE_ERROR");
            return diagnostics.retry();
        }catch(SQLiteException full){
            // Keep the durable generation state: a remote batch may have committed
            // immediately before its local acknowledgement hit SQLite's size cap.
            diagnostics.error("LOCAL_DATABASE_ERROR");diagnostics.progress("LOCAL_ERROR");FirestoreSync.progress(context).edit().putString("snapshot_error","local_snapshot_limit").commit();return diagnostics.success();}
        catch(IllegalStateException bounded){
            // Retain resumable state; MATERIALIZING restarts locally, while an
            // UPLOADING generation can safely replay idempotent document sets.
            diagnostics.error("SNAPSHOT_VALIDATION_ERROR");diagnostics.progress("LOCAL_ERROR");FirestoreSync.progress(context).edit().putString("snapshot_error",bounded.getMessage()==null?"snapshot_invalid":bounded.getMessage()).commit();return diagnostics.success();}
        catch(Exception failure){diagnostics.error(FirestoreWorkerDiagnostics.safeFailureCode(failure));diagnostics.progress("RETRYABLE_ERROR");return diagnostics.retry();}finally{
            if(store!=null)store.close();
            try{if(lease!=null)lease.release();}catch(Exception ignored){}
            try{if(leaseChannel!=null)leaseChannel.close();}catch(Exception ignored){}
        }
    }

    private Result cleanupAborted(FirebaseFirestore db,FirestoreSnapshotStore store,FirestoreSnapshotStore.Snapshot s,
            String owner,String device,SharedPreferences prefs,int day,int spent,FirestoreWorkerDiagnostics.Run diagnostics)throws Exception{
        diagnostics.snapshot("CLEANUP",s);
        int writesSpent=spent;
        int remaining=MAX_PER_DAY-writesSpent;
        if("ABORTING".equals(s.state)){
            if(remaining<=0){diagnostics.progress("BUDGET_LIMIT");return diagnostics.success();}
            if(!reserve(prefs,day,writesSpent,1)){diagnostics.progress("BUDGET_LIMIT");return diagnostics.success();}
            writesSpent++;
            remaining--;
            if(!FirestoreSync.enabled(getApplicationContext())||isStopped()){diagnostics.progress("NOT_ACTIVE");return diagnostics.success();}
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
        int cap=Math.min(MAX_PER_PASS,remaining);if(cap<=0)return diagnostics.success();
        // Enumerate remote staging rows directly. Local upload/cleanup acknowledgements
        // can fail under SQLite pressure, so they must not be the only delete manifest.
        QuerySnapshot page=Tasks.await(listings(db,owner,device,s.id).orderBy(FieldPath.documentId()).limit(cap).get(),20,TimeUnit.SECONDS);
        if(!page.isEmpty()){if(!reserve(prefs,day,writesSpent,page.size())){diagnostics.progress("BUDGET_LIMIT");return diagnostics.success();}
            if(!FirestoreSync.enabled(getApplicationContext())||isStopped()){diagnostics.progress("NOT_ACTIVE");return diagnostics.success();}
            WriteBatch batch=db.batch();for(QueryDocumentSnapshot document:page)batch.delete(document.getReference());
            Tasks.await(batch.commit(),35,TimeUnit.SECONDS);return diagnostics.success();}
        store.resetIdle();return diagnostics.success();
    }
    private Result cleanupOld(FirebaseFirestore db,FirestoreSnapshotStore store,FirestoreSnapshotStore.Snapshot s,
            String owner,String device,SharedPreferences prefs,int day,int spent,FirestoreWorkerDiagnostics.Run diagnostics)throws Exception{
        diagnostics.progress("CLEANUP");
        String expiredId=expiredGenerationId(s);
        if(expiredId.isEmpty()){store.clearAfterPublish(s.id,s.contentHash,s.includesEvidence);return diagnostics.success();}
        int cap=Math.min(MAX_PER_PASS,MAX_PER_DAY-spent);if(cap<=0){diagnostics.progress("BUDGET_LIMIT");return diagnostics.success();}
        // Deleted rows disappear from the query, so always resume from the first
        // remaining ID. A persisted cursor could skip a row recreated behind it.
        com.google.firebase.firestore.Query q=listings(db,owner,device,expiredId).orderBy(FieldPath.documentId()).limit(cap);
        QuerySnapshot page=Tasks.await(q.get(),20,TimeUnit.SECONDS);
        if(!page.isEmpty()){
            WriteBatch batch=db.batch();for(QueryDocumentSnapshot d:page)batch.delete(d.getReference());
            if(!reserve(prefs,day,spent,page.size())){diagnostics.progress("BUDGET_LIMIT");return diagnostics.success();}if(!FirestoreSync.enabled(getApplicationContext())||isStopped()){diagnostics.progress("NOT_ACTIVE");return diagnostics.success();}
            Tasks.await(batch.commit(),35,TimeUnit.SECONDS);
            return diagnostics.success();
        }
        // Recheck before deleting the parent: the rules only permit listing writes
        // while this generation's staging manifest is incomplete.
        QuerySnapshot beforeDelete=Tasks.await(listings(db,owner,device,expiredId).limit(1).get(),20,TimeUnit.SECONDS);
        if(!beforeDelete.isEmpty())return diagnostics.success();
        if(!reserve(prefs,day,spent,1)){diagnostics.progress("BUDGET_LIMIT");return diagnostics.success();}if(!FirestoreSync.enabled(getApplicationContext())||isStopped()){diagnostics.progress("NOT_ACTIVE");return diagnostics.success();}
        Tasks.await(snapshot(db,owner,device,expiredId).delete(),35,TimeUnit.SECONDS);
        // Completed manifests reject later listing writes; this final read keeps
        // cleanup resumable if a previously queued write raced the first query.
        QuerySnapshot finalCheck=Tasks.await(listings(db,owner,device,expiredId).limit(1).get(),20,TimeUnit.SECONDS);
        if(!finalCheck.isEmpty())return diagnostics.success();
        store.clearAfterPublish(s.id,s.contentHash,s.includesEvidence);return diagnostics.success();
    }

    private static void materialize(File file,FirestoreSnapshotStore store,boolean includeEvidence)throws Exception{
        MessageDigest root=FirestoreSnapshotStore.sha256();long count=0;
        try(SQLiteDatabase source=SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)){
            // Deferred BEGIN is read-only on this OPEN_READONLY handle and holds one DB snapshot.
            source.execSQL("BEGIN");
            try(Cursor c=source.rawQuery(QUERY,null)){while(c.moveToNext()){
                if(count>=FirestoreSnapshotStore.MAX_ROWS)throw new IllegalStateException("snapshot_row_limit");Row row=project(c,includeEvidence);
                String payload=new JSONObject(row.fields).toString();if(payload.getBytes(StandardCharsets.UTF_8).length>MAX_DOCUMENT_BYTES)throw new IllegalStateException("snapshot_document_limit");
                store.insertRow(row.id,payload,row.hash);FirestoreSnapshotStore.updateRoot(root,row.id,row.hash);count++;}
                store.finishMaterialization(count,FirestoreSnapshotStore.hex(root.digest()));
            }finally{source.execSQL("ROLLBACK");}
        }
    }
    private static String sourceHash(File file,boolean includeEvidence)throws Exception{
        MessageDigest root=FirestoreSnapshotStore.sha256();try(SQLiteDatabase source=SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)){
            source.execSQL("BEGIN");
            try(Cursor c=source.rawQuery(QUERY,null)){long count=0;while(c.moveToNext()){
                Row r=project(c,includeEvidence);FirestoreSnapshotStore.updateRoot(root,r.id,r.hash);if(++count>FirestoreSnapshotStore.MAX_ROWS)throw new IllegalStateException("snapshot_row_limit");}
            }finally{source.execSQL("ROLLBACK");}
        }
        return FirestoreSnapshotStore.hex(root.digest());
    }
    private static Row project(Cursor c,boolean includeEvidence)throws Exception{
        long id=c.getLong(0);if(id<=0)throw new IllegalArgumentException("invalid local ID");Map<String,Object> v=new LinkedHashMap<>();
        String title=str(c,2,400),brand=str(c,3,120),category=str(c,4,80),categorySource=str(c,5,80);
        v.put("schema_version",3);v.put("source_id",id);v.put("title",title);v.put("title_source","VINTED_LISTING");
        v.put("brand",brand);v.put("brand_source","VINTED_LISTING");v.put("category",category);
        v.put("category_source",categorySource);v.put("category_provenance_available",!categorySource.isEmpty());
        v.put("language",normalizedLanguage(str(c,1,40)));v.put("bgg_id",str(c,6,80));
        v.put("lifecycle",str(c,7,40));v.put("enrichment_state",str(c,8,60));v.put("match_state",str(c,9,60));
        if(includeEvidence){
            v.put("observed_text",boundedCodePoints(c.isNull(10)?"":c.getString(10),MAX_EVIDENCE_TEXT));
            v.put("photo_urls",photos(c.isNull(11)?"":c.getString(11),c.isNull(12)?"":c.getString(12)));
        }
        String hash=FirestoreSnapshotStore.hex(FirestoreSnapshotStore.sha256().digest(canonicalJson(v).getBytes(StandardCharsets.UTF_8)));
        v.put("source_hash",hash);return new Row(id,hash,v);
    }
    static String boundedCodePoints(String value,int max){
        if(value==null)return "";String trimmed=value.trim();int count=trimmed.codePointCount(0,trimmed.length());
        return count<=max?trimmed:trimmed.substring(0,trimmed.offsetByCodePoints(0,max));
    }
    static List<String> photos(String csv,String primary){
        List<String> out=new ArrayList<>();List<String> candidates=new ArrayList<>();
        if(primary!=null&&!primary.trim().isEmpty())candidates.add(primary.trim());
        if(csv!=null)for(String item:csv.split(","))if(!item.trim().isEmpty())candidates.add(item.trim());
        for(String url:candidates){
            try{java.net.URI u=new java.net.URI(url);String host=u.getHost();
                if(!"https".equalsIgnoreCase(u.getScheme())||host==null||u.getUserInfo()!=null||u.getPort()!=-1
                        ||!(host.equalsIgnoreCase("vinted.net")||host.toLowerCase(java.util.Locale.ROOT).endsWith(".vinted.net")
                        ||host.equalsIgnoreCase("vinted.com")||host.toLowerCase(java.util.Locale.ROOT).endsWith(".vinted.com"))
                        ||url.length()>1200||out.contains(url))continue;
                out.add(url);if(out.size()==MAX_PHOTO_URLS)break;
            }catch(Exception ignored){}
        }
        return out;
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
    private static String readPreviousGenerationId(DocumentReference pointer)throws Exception{com.google.firebase.firestore.DocumentSnapshot d=Tasks.await(pointer.get(),20,TimeUnit.SECONDS);String id=d.getString("rollback_snapshot_id");return id==null?"":id;}
    static String expiredGenerationId(FirestoreSnapshotStore.Snapshot state){return state==null||state.previousCursor==null?"":state.previousCursor;}
    private static boolean matchesManifest(DocumentSnapshot document,String id,long count,String hash,boolean complete){
        Long schema=document.getLong("schema_version"),rows=document.getLong("document_count");
        return document.exists()&&Long.valueOf(3L).equals(schema)&&Long.valueOf(count).equals(rows)
                &&id.equals(document.getString("snapshot_id"))&&hash.equals(document.getString("content_hash"))
                &&Boolean.valueOf(complete).equals(document.getBoolean("complete"));
    }
    private static boolean evidenceModeMatches(Context context,FirestoreSnapshotStore.Snapshot state){
        return evidenceModeMatches(FirestoreSync.enabled(context),FirestoreSync.evidenceEnabled(context),state);
    }
    static boolean evidenceModeMatches(boolean metadataEnabled,boolean evidenceEnabled,FirestoreSnapshotStore.Snapshot state){
        return state!=null&&state.protocolVersion==SNAPSHOT_PROTOCOL_VERSION
                && state.includesEvidence==evidenceEnabled && metadataEnabled;
    }
    static <T> T commitIfEvidenceModeMatches(boolean metadataEnabled,boolean evidenceEnabled,
            FirestoreSnapshotStore.Snapshot state,Supplier<T> submit){
        if(!evidenceModeMatches(metadataEnabled,evidenceEnabled,state))return null;
        return submit.get();
    }
    static <T> T commitIfEvidenceModeMatches(Context context,FirestoreSnapshotStore.Snapshot state,
            Supplier<T> submit){
        synchronized(FirestoreSync.class){
            return commitIfEvidenceModeMatches(FirestoreSync.enabled(context),
                    FirestoreSync.evidenceEnabled(context),state,submit);
        }
    }
    private static boolean same(String a,String b){return(a==null?"":a).equals(b==null?"":b);}
    private static final class Row{final long id;final String hash;final Map<String,Object> fields;Row(long i,String h,Map<String,Object> f){id=i;hash=h;fields=f;}}
    private static Long nonnegative(Cursor c,int i){return c.isNull(i)||c.getLong(i)<0?null:c.getLong(i);}
    private static String str(Cursor c,int i,int cap){if(c.isNull(i))return"";String s=c.getString(i);if(s==null)return"";s=s.trim();return s.length()<=cap?s:s.substring(0,cap);}
}
