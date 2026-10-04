package it.vintedaffari.app;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.concurrent.atomic.AtomicReference;
/** Runs only in the test APK, with AI disabled and no provider credentials. */
public final class AiBetaInstrumentation extends Instrumentation {
 @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
 private View find(View v,String text){if(v instanceof TextView&&text.equals(((TextView)v).getText().toString()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View found=find(g.getChildAt(i),text);if(found!=null)return found;}}return null;}
 @Override public void onStart(){Bundle result=new Bundle();Activity a=null;try{
  String fixture;try(java.io.InputStream in=getTargetContext().getAssets().open("ai-beta/sample8.json");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] buf=new byte[2048];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);fixture=new org.json.JSONArray(out.toString("UTF-8")).toString();}AiBetaSettings settings=new AiBetaSettings(getTargetContext());org.json.JSONObject saved=new org.json.JSONObject().put("enabled",false).put("last_display","Prova disattivata · nessuna chiamata AI").put("last_display_key",AiBetaProtocol.fingerprint(fixture,AiBetaProtocol.MODEL,AiBetaProtocol.CONTRACT)).put("last_display_at",System.currentTimeMillis());settings.save(saved);if(settings.load().optBoolean("enabled",true))throw new AssertionError("disabled setting lost");
  if(!new java.io.File(getTargetContext().getNoBackupFilesDir(),"ai-beta.private").isFile())throw new AssertionError("settings not excluded from backup");
  a=startActivitySync(new Intent().setClassName(getTargetContext(),"it.vintedaffari.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final Activity screen=a;AtomicReference<AlertDialog> opened=new AtomicReference<>();runOnMainSync(()->opened.set(AiBetaTestDialog.show(screen)));AlertDialog dialog=opened.get();
  boolean loaded=false;for(int attempt=0;attempt<50;attempt++){waitForIdleSync();AtomicReference<Boolean> seen=new AtomicReference<>(false);runOnMainSync(()->seen.set(find(dialog.getWindow().getDecorView(),"Prova disattivata · nessuna chiamata AI")!=null));if(seen.get()){loaded=true;break;}Thread.sleep(100);}if(!loaded)throw new AssertionError("saved result never loaded");
  runOnMainSync(()->{View root=dialog.getWindow().getDecorView();View run=find(root,"Test AI su 8 annunci");View configure=find(root,"Configura prova");if(run==null||run.isEnabled()||configure==null||!configure.isEnabled())throw new AssertionError("manual default OFF failed");if(run.getHeight()==0||configure.getHeight()==0)throw new AssertionError("controls not laid out");});
  getUiAutomation().waitForIdle(200,5000);Bitmap original=getUiAutomation().takeScreenshot();if(original==null)throw new AssertionError("no screenshot");Bitmap reduced=Bitmap.createScaledBitmap(original,432,Math.round(original.getHeight()*432f/original.getWidth()),true);java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();reduced.compress(Bitmap.CompressFormat.JPEG,88,bytes);String encoded=android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);String size=a.getResources().getConfiguration().fontScale>1.3f?"large_ai":"normal_ai";for(int offset=0,index=0;offset<encoded.length();offset+=3000,index++)android.util.Log.i("LudoVisual","VISUAL "+size+" "+index+" "+encoded.substring(offset,Math.min(offset+3000,encoded.length())));original.recycle();reduced.recycle();
  runOnMainSync(dialog::dismiss);java.lang.reflect.Method visible=AiBetaTestDialog.class.getDeclaredMethod("visible",Activity.class,AlertDialog.class);visible.setAccessible(true);if((Boolean)visible.invoke(null,a,dialog))throw new AssertionError("detached dialog accepted update");
  verifyRealSelection();
  renderRealComparison(screen,settings);
  result.putString("result","AI manual dialog rendered; disabled settings persisted; detached UI rejected; no provider call");finish(Activity.RESULT_OK,result);
 }catch(Throwable failure){android.util.Log.e("LudoVisual","FAIL",failure);result.putString("error",failure.toString());finish(Activity.RESULT_CANCELED,result);}finally{if(a!=null){Activity done=a;runOnMainSync(done::finish);}}}

 private static void insert(android.database.sqlite.SQLiteDatabase db,long id,String title,String description,String lifecycle,long seen) {
  android.content.ContentValues row=new android.content.ContentValues();row.put("id",id);row.put("temp_fingerprint","ai-instrumentation-"+id);row.put("vinted_title",title);row.put("brand","Kosmos");row.put("observed_text",description);row.put("current_price_cents",100);row.put("lifecycle",lifecycle);row.put("first_seen",seen);row.put("last_seen",seen);db.insertOrThrow("market_listings",null,row);
 }
 private static org.json.JSONObject response(AiBetaRealListings.Snapshot snapshot)throws Exception {
  org.json.JSONArray proposals=new org.json.JSONArray();
  for(int i=snapshot.rows.length()-1;i>=0;i--)proposals.put(new org.json.JSONObject().put("listing_id",snapshot.rows.getJSONObject(i).getLong("listing_id")).put("proposed_type","ACCESSORY_COMPONENT").put("confidence",org.json.JSONObject.NULL).put("evidence","Organizer dichiarato nel titolo.").put("needs_review",true).put("apply_authorized",false).put("language","UNKNOWN").put("bgg_verified",false));
  return new org.json.JSONObject().put("status","PROPOSAL").put("request_id","instrumentation-only").put("model",AiBetaProtocol.MODEL).put("contract",AiBetaProtocol.CONTRACT).put("records",proposals);
 }
 private void verifyRealSelection()throws Exception {
  try(android.database.sqlite.SQLiteDatabase db=android.database.sqlite.SQLiteDatabase.create(null)) {
   MarketStore.createSchema(db);
   insert(db,1,"Organizer Catan","Contesto locale, venditore e prezzo mai inviati","AUTO_FILTERED",20);
   insert(db,2,"Solo carte Catan","","ACTIVE",20);
   insert(db,3,"Annuncio nascosto","","USER_HIDDEN",99);
   insert(db,4,new String(new char[6000]).replace('\0','X'),"","ACTIVE",100);
   long before=android.database.DatabaseUtils.longForQuery(db,"SELECT total_changes()",null);
   AiBetaRealListings.Snapshot snapshot=AiBetaRealListings.select(db);
   if(snapshot.rows.length()!=2||snapshot.rows.getJSONObject(0).getLong("listing_id")!=2)throw new AssertionError("selection bounds/lifecycle/tie failed");
   if(!"COMPONENTS".equals(snapshot.local.getJSONObject(0).getString("local_type")))throw new AssertionError("local subtype lost");
   for(int i=0;i<snapshot.rows.length();i++)if(snapshot.rows.getJSONObject(i).length()!=3||snapshot.rows.getJSONObject(i).has("observed_text")||snapshot.rows.getJSONObject(i).has("local_type"))throw new AssertionError("remote payload leaked local fields");
   if(new org.json.JSONObject().put("request_id","00000000-0000-0000-0000-000000000000").put("records",snapshot.rows).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>4096)throw new AssertionError("payload oversized");
   if(!AiBetaRealListings.current(db,snapshot)||before!=android.database.DatabaseUtils.longForQuery(db,"SELECT total_changes()",null))throw new AssertionError("read-only selection changed archive");
   org.json.JSONObject answer=response(snapshot);String display=AiBetaRealDialog.comparison(answer,snapshot,false);
   if(!display.contains("Componenti separati")||!display.contains("sottotipo locale conservato"))throw new AssertionError("grouped proposal overwrote local subtype");
   for(String field:new String[]{"apply_authorized","bgg_verified"}) {
    org.json.JSONObject invalid=new org.json.JSONObject(answer.toString());invalid.getJSONArray("records").getJSONObject(0).put(field,true);boolean rejected=false;
    try{AiBetaRealDialog.comparison(invalid,snapshot,false);}catch(Exception expected){rejected=true;}
    if(!rejected)throw new AssertionError("accepted forbidden proposal "+field);
   }
   org.json.JSONObject invalid=new org.json.JSONObject(answer.toString());invalid.getJSONArray("records").getJSONObject(0).put("listing_id",999);boolean rejected=false;
   try{AiBetaRealDialog.comparison(invalid,snapshot,false);}catch(Exception expected){rejected=true;}if(!rejected)throw new AssertionError("foreign proposal accepted");
   db.execSQL("UPDATE market_listings SET observed_text='Solo scatola vuota' WHERE id=2");
   if(AiBetaRealListings.current(db,snapshot))throw new AssertionError("description edit retained stale comparison");
   AiBetaRealListings.Snapshot edited=AiBetaRealListings.select(db);
   if(!snapshot.key.equals(edited.key)||snapshot.localKey().equals(edited.localKey()))throw new AssertionError("remote reuse/local revision scope failed");
   db.execSQL("DELETE FROM market_listings WHERE id=1");if(AiBetaRealListings.current(db,edited))throw new AssertionError("deleted listing retained stale proposal");
  }
 }
 private void renderRealComparison(Activity a,AiBetaSettings settings)throws Exception {
  DealDatabase helper=new DealDatabase(getTargetContext());android.database.sqlite.SQLiteDatabase db=helper.getWritableDatabase();
  db.delete("market_listings","temp_fingerprint LIKE ?",new String[]{"ai-instrumentation-%"});
  insert(db,900000001,"Organizer Catan","Solo organizer; descrizione locale","AUTO_FILTERED",System.currentTimeMillis()+1000);
  insert(db,900000002,"Solo carte Catan","Componenti separati","AUTO_FILTERED",System.currentTimeMillis()+1000);
  AiBetaRealListings.Snapshot snapshot=AiBetaRealListings.prepare(getTargetContext());
  if(snapshot.rows.length()!=2)throw new AssertionError("isolated emulator selection unexpected");
  org.json.JSONObject saved=settings.load();saved.put("real_snapshot",snapshot.local).put("real_response",response(snapshot)).put("real_response_key",snapshot.key).put("real_response_at",System.currentTimeMillis()).put("enabled",false);settings.save(saved);
  AtomicReference<AlertDialog> opened=new AtomicReference<>();runOnMainSync(()->opened.set(AiBetaRealDialog.show(a)));AlertDialog real=opened.get();
  boolean loaded=false;for(int i=0;i<50;i++){waitForIdleSync();AtomicReference<Boolean> seen=new AtomicReference<>(false);runOnMainSync(()->{View root=real.getWindow().getDecorView();View button=find(root,"Segna confronto come revisionato");seen.set(button!=null&&button.isEnabled());});if(seen.get()){loaded=true;break;}Thread.sleep(100);}if(!loaded)throw new AssertionError("real cached comparison not rendered");
  runOnMainSync(()->{View root=real.getWindow().getDecorView();View analyze=find(root,"Chiedi proposte AI");if(analyze==null||analyze.isEnabled())throw new AssertionError("real analysis allowed while OFF");});
  getUiAutomation().waitForIdle(200,5000);Bitmap screenshot=getUiAutomation().takeScreenshot();if(screenshot==null)throw new AssertionError("real comparison screenshot absent");java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();Bitmap reduced=Bitmap.createScaledBitmap(screenshot,432,Math.round(screenshot.getHeight()*432f/screenshot.getWidth()),true);reduced.compress(Bitmap.CompressFormat.JPEG,88,bytes);String encoded=android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);String name=a.getResources().getConfiguration().fontScale>1.3f?"large_real_ai":"normal_real_ai";for(int offset=0,index=0;offset<encoded.length();offset+=3000,index++)android.util.Log.i("LudoVisual","VISUAL "+name+" "+index+" "+encoded.substring(offset,Math.min(offset+3000,encoded.length())));screenshot.recycle();reduced.recycle();
  runOnMainSync(()->find(real.getWindow().getDecorView(),"Segna confronto come revisionato").performClick());
  boolean reviewed=false;for(int i=0;i<50;i++){if(snapshot.localKey().equals(settings.load().optString("real_review_key"))){reviewed=true;break;}Thread.sleep(100);}if(!reviewed)throw new AssertionError("explicit review not persisted");
  if(!AiBetaRealListings.current(getTargetContext(),snapshot))throw new AssertionError("review modified catalog rows");
  if(android.database.DatabaseUtils.longForQuery(db,"SELECT COUNT(*) FROM processing_jobs",null)!=0)throw new AssertionError("AI comparison scheduled background jobs");
  runOnMainSync(real::dismiss);
  saved=settings.load();saved.put("enabled",true).put("real_pending_key",snapshot.key).put("real_pending_id","stable-existing-request");settings.save(saved);
  runOnMainSync(()->opened.set(AiBetaRealDialog.show(a)));AlertDialog cached=opened.get();
  boolean ready=false;for(int i=0;i<50;i++){waitForIdleSync();AtomicReference<Boolean> seen=new AtomicReference<>(false);runOnMainSync(()->{View button=find(cached.getWindow().getDecorView(),"Chiedi proposte AI");seen.set(button!=null&&button.isEnabled());});if(seen.get()){ready=true;break;}Thread.sleep(100);}if(!ready)throw new AssertionError("manual cached analysis unavailable");
  runOnMainSync(()->find(cached.getWindow().getDecorView(),"Chiedi proposte AI").performClick());
  AiBetaTestDialog.IO.submit(()->{}).get(10,java.util.concurrent.TimeUnit.SECONDS);waitForIdleSync();
  runOnMainSync(()->{View root=cached.getWindow().getDecorView();if(!find(root,"Segna confronto come revisionato").isEnabled())throw new AssertionError("cache miss attempted invalid transport");});
  if(!"stable-existing-request".equals(settings.load().optString("real_pending_id")))throw new AssertionError("cache reuse changed pending request");
  db.execSQL("UPDATE market_listings SET vinted_title='Titolo modificato' WHERE id=900000001");
  runOnMainSync(()->find(cached.getWindow().getDecorView(),"Chiedi proposte AI").performClick());
  AiBetaTestDialog.IO.submit(()->{}).get(10,java.util.concurrent.TimeUnit.SECONDS);waitForIdleSync();
  runOnMainSync(()->{View root=cached.getWindow().getDecorView();if(find(root,"Segna confronto come revisionato").isEnabled()||find(root,"Chiedi proposte AI").isEnabled())throw new AssertionError("stale comparison remained actionable");});
  if(!"stable-existing-request".equals(settings.load().optString("real_pending_id")))throw new AssertionError("stale input created new request");
  runOnMainSync(cached::dismiss);saved=settings.load();saved.put("enabled",false);settings.save(saved);helper.close();
 }
}
