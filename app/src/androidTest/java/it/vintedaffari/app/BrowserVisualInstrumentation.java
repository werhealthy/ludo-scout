package it.vintedaffari.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Test APK only: renders the actual Activity with deterministic local page content. */
public final class BrowserVisualInstrumentation extends Instrumentation {
 @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
 private Object get(Activity a,String name)throws Exception{Field f=a.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(a);}
 private void set(Activity a,String name,Object value)throws Exception{Field f=a.getClass().getDeclaredField(name);f.setAccessible(true);f.set(a,value);}
 private void invoke(Activity a,String name)throws Exception{Method m=a.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(a);}
 @SuppressWarnings({"unchecked","rawtypes"}) private void state(Activity a,String name)throws Exception{Field f=a.getClass().getDeclaredField("uiState");f.setAccessible(true);f.set(a,Enum.valueOf((Class)f.getType(),name));set(a,"enabled",!name.equals("manualMatch"));invoke(a,"refreshStatus");}
 private void capture(Activity a,String name)throws Exception{
  waitForIdleSync();getUiAutomation().waitForIdle(200,5000);Bitmap original=getUiAutomation().takeScreenshot();if(original==null)throw new AssertionError("no screenshot");
  Bitmap reduced=Bitmap.createScaledBitmap(original,432,Math.round(original.getHeight()*432f/original.getWidth()),true);
  java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();reduced.compress(Bitmap.CompressFormat.JPEG,88,bytes);
  String encoded=android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);
  // Logcat truncates long lines; encode in numbered chunks for exact reconstruction.
  for(int offset=0,index=0;offset<encoded.length();offset+=3000,index++)android.util.Log.i("LudoVisual","VISUAL "+(a.getResources().getConfiguration().fontScale>1.3f?"large_":"normal_")+name+" "+index+" "+encoded.substring(offset,Math.min(offset+3000,encoded.length())));
  original.recycle();reduced.recycle();
 }
 private android.webkit.WebResourceRequest pageRequest(String url){
  return new android.webkit.WebResourceRequest(){
   public android.net.Uri getUrl(){return android.net.Uri.parse(url);}
   public boolean isForMainFrame(){return true;}public boolean isRedirect(){return true;}public boolean hasGesture(){return false;}
   public String getMethod(){return "GET";}public java.util.Map<String,String> getRequestHeaders(){return java.util.Collections.emptyMap();}
  };
 }
 private void checkRealNavigationClient(Activity screen)throws Exception{
  WebView web=(WebView)get(screen,"web");web.stopLoading();WebViewClient client=web.getWebViewClient();
  android.webkit.WebResourceRequest request=pageRequest("https://www.vinted.it/login?token=secret#secret");
  if(!client.shouldOverrideUrlLoading(web,request))throw new AssertionError("blocked route escaped policy");
  if(!"PAGE_BLOCKED".equals(get(screen,"state")))throw new AssertionError("real override did not record PAGE_BLOCKED");
  if((Boolean)get(screen,"loading")||(Boolean)get(screen,"accepting")||(Boolean)get(screen,"pageDrained"))throw new AssertionError("blocked page remained active");
  if(!"PATH_NOT_ALLOWED".equals(get(screen,"blockedReason")))throw new AssertionError("missing block reason");
  Method report=screen.getClass().getDeclaredMethod("reportLocked");report.setAccessible(true);
  String text=(String)report.invoke(screen);
  if(!text.contains("blockedPage=https://www.vinted.it/login")||text.contains("token=secret"))throw new AssertionError("unsafe/incomplete block report");
  client.onReceivedError(web,request,null);
  android.webkit.WebResourceResponse failure=new android.webkit.WebResourceResponse("text/html","UTF-8",null);failure.setStatusCodeAndReasonPhrase(403,"Forbidden");
  client.onReceivedHttpError(web,request,failure);
  if(!"PAGE_BLOCKED".equals(get(screen,"state")))throw new AssertionError("late error discarded block");
  String liveUrl=web.getUrl();set(screen,"navigationUrl","https://www.vinted.it/catalog/other");
  Method receive=screen.getClass().getDeclaredMethod("receive",String.class,String.class,boolean.class,androidx.webkit.JavaScriptReplyProxy.class);receive.setAccessible(true);
  receive.invoke(screen,"{\"kind\":\"location\",\"url\":"+org.json.JSONObject.quote(liveUrl)+"}","https://www.vinted.it",true,null);
  if(!"PAGE_BLOCKED".equals(get(screen,"state"))||!(Boolean)get(screen,"pageFailed")||(Boolean)get(screen,"accepting"))throw new AssertionError("location discarded block");
  client.onPageStarted(web,"https://www.vinted.it/inbox",null);
  if(!"PAGE_BLOCKED".equals(get(screen,"state"))||(Boolean)get(screen,"loading"))throw new AssertionError("started block remained loading");
  client.onPageFinished(web,"https://www.vinted.it/inbox");
  if(!"PAGE_BLOCKED".equals(get(screen,"state")))throw new AssertionError("finish cleared block");
  client.onPageFinished(web,"https://www.vinted.it/catalog/4881-board-games");
  if(!"PAGE_BLOCKED".equals(get(screen,"state"))||(Boolean)get(screen,"accepting"))throw new AssertionError("stale finish resumed capture");
  ((View)get(screen,"status")).performClick();
  if(!"LOADING".equals(get(screen,"state"))||!(Boolean)get(screen,"loading")||(Boolean)get(screen,"pageFailed"))throw new AssertionError("explicit retry did not start allowed search");
  client.onPageFinished(web,"https://www.vinted.it/catalog/old");
  if(!"LOADING".equals(get(screen,"state"))||!(Boolean)get(screen,"loading")||(Boolean)get(screen,"accepting"))throw new AssertionError("stale finish resumed capture during retry");
  client.onPageFinished(web,"https://www.vinted.it/login");
  if(!"LOADING".equals(get(screen,"state")))throw new AssertionError("stale blocked finish cancelled retry");
  web.stopLoading();client.onPageStarted(web,"https://www.vinted.it/catalog/4881-board-games?page=1&order=relevance&price_to=25",null);
  client.onPageFinished(web,"https://www.vinted.it/catalog/old");client.onPageFinished(web,"https://www.vinted.it/login");
  if(!"LOADING".equals(get(screen,"state"))||(Boolean)get(screen,"accepting"))throw new AssertionError("stale finish crossed started retry");
  if(!"LOADING".equals(get(screen,"state")))throw new AssertionError("new allowed navigation retained blocking state");
  client.shouldOverrideUrlLoading(web,request);((View)get(screen,"status")).performClick();
  String redirected="https://vinted.it/catalog/4881-board-games?page=1";
  if(client.shouldOverrideUrlLoading(web,pageRequest(redirected)))throw new AssertionError("permitted redirect was blocked");
  client.onPageStarted(web,redirected,null);client.onPageFinished(web,redirected);
  if((Boolean)get(screen,"retryPending")||(Boolean)get(screen,"loading")||(Boolean)get(screen,"pageFailed"))throw new AssertionError("allowed redirect stalled retry");
  web.stopLoading();
 }
 
 private void checkSessionRefresh(Activity screen)throws Exception{
  WebView web=(WebView)get(screen,"web");web.stopLoading();WebViewClient client=web.getWebViewClient();
  String session="https://www.vinted.it/session-refresh?redirect=%2Fcatalog%2F4881-board-games";
  if(client.shouldOverrideUrlLoading(web,pageRequest(session)))throw new AssertionError("session refresh still blocked");
  if((Boolean)get(screen,"accepting"))throw new AssertionError("capture remained active at session transition");
  client.onPageFinished(web,"https://www.vinted.it/catalog/4881-board-games?page=1");
  client.onPageFinished(web,(String)get(screen,"navigationUrl"));
  invoke(screen,"onResume");
  Method pendingCapture=screen.getClass().getDeclaredMethod("setCapture",boolean.class);pendingCapture.setAccessible(true);pendingCapture.invoke(screen,true);invoke(screen,"captureNow");
  if((Boolean)get(screen,"accepting")||!"SESSION_REFRESH".equals(get(screen,"state"))||!(Boolean)get(screen,"sessionPending"))throw new AssertionError("stale completion/lifecycle escaped pending session pause");
  client.onPageStarted(web,session,null);client.onPageFinished(web,session);
  if(!"SESSION_REFRESH".equals(get(screen,"state"))||(Boolean)get(screen,"accepting")||(Boolean)get(screen,"pageDrained"))throw new AssertionError("session page captured or marked drained");
  invoke(screen,"onResume");
  Method capture=screen.getClass().getDeclaredMethod("setCapture",boolean.class);capture.setAccessible(true);capture.invoke(screen,true);invoke(screen,"captureNow");
  if((Boolean)get(screen,"accepting")||!"SESSION_REFRESH".equals(get(screen,"state")))throw new AssertionError("session capture escaped through lifecycle/control");
  String catalog="https://www.vinted.it/catalog/4881-board-games";
  if(client.shouldOverrideUrlLoading(web,pageRequest(catalog)))throw new AssertionError("return to catalog blocked");
  client.onPageStarted(web,catalog,null);client.onPageFinished(web,catalog);
  if(!"CAPTURING".equals(get(screen,"state"))||!(Boolean)get(screen,"accepting"))throw new AssertionError("capture did not resume on catalog");
  web.stopLoading();
 }


 private void checkPartialIntakeAndControls(Activity screen)throws Exception{
  String page="https://www.vinted.it/catalog/4881-board-games?page=2";
  runOnMainSync(()->{try{((WebView)get(screen,"web")).stopLoading();Method reset=screen.getClass().getDeclaredMethod("resetPage",String.class);reset.setAccessible(true);reset.invoke(screen,page);set(screen,"resumed",false);set(screen,"enabled",true);set(screen,"supported",true);set(screen,"loading",false);set(screen,"pageFailed",false);set(screen,"state","CAPTURING");}catch(Exception e){throw new RuntimeException(e);}});
  long token=(Long)get(screen,"pageGeneration"),generation=(Long)get(screen,"captureGeneration");
  Method parse=screen.getClass().getDeclaredMethod("parse",String.class,long.class,String.class,long.class);parse.setAccessible(true);
  org.json.JSONObject item=new org.json.JSONObject().put("id","9900000162").put("url","https://www.vinted.it/items/9900000162").put("title","Partial browser fixture").put("source","dom");
  org.json.JSONObject packet=new org.json.JSONObject().put("schema",1).put("pageToken",token).put("page",new org.json.JSONObject().put("url",page).put("observed",1).put("withPrice",0)).put("items",new org.json.JSONArray().put(item)).put("settled",false);
  if(!(Boolean)parse.invoke(screen,packet.toString(),generation,page,token))throw new AssertionError("partial snapshot persistence failed");
  waitForIdleSync();
  runOnMainSync(()->{try{if(((View)get(screen,"next")).isEnabled())throw new AssertionError("Next opened before settled checkpoint");}catch(Exception e){throw new RuntimeException(e);}});
  packet.put("items",new org.json.JSONArray()).put("settled",true);
  parse.invoke(screen,packet.toString(),generation,page,token);waitForIdleSync();
  StringBuilder defects=new StringBuilder();
  try(android.database.Cursor c=((DealDatabase)get(screen,"captureDatabase")).getReadableDatabase().rawQuery("SELECT text_value FROM queue_controls WHERE name=?",new String[]{"browser_snapshot:9900000162"})){if(!c.moveToFirst()||!c.getString(0).contains("Partial browser fixture"))throw new AssertionError("partial data not durable");}
  runOnMainSync(()->{try{
   if(!((View)get(screen,"previous")).isEnabled()||!((View)get(screen,"next")).isEnabled())defects.append("durably saved partial card keeps arrows blocked; ");
   if(!((java.util.Set)get(screen,"persistedPageIds")).isEmpty())throw new AssertionError("partial price invented a canonical listing");
   try{View toggle=(View)get(screen,"expansionToggle");if(toggle.getVisibility()!=View.VISIBLE)throw new AssertionError("toggle hidden");toggle.performClick();if(!"exploreCollapsed".equals(get(screen,"uiState").toString()))throw new AssertionError("collapse button failed");toggle.performClick();if(!"exploreExpanded".equals(get(screen,"uiState").toString()))throw new AssertionError("expand button failed");if(!page.equals(get(screen,"navigationUrl"))||token!=(Long)get(screen,"pageGeneration")||!((View)get(screen,"next")).isEnabled())throw new AssertionError("layout toggle reset capture");}catch(NoSuchFieldException missing){defects.append("visible expand/collapse button missing; ");}
   set(screen,"pageDrained",false);invoke(screen,"refreshStatus");if(((View)get(screen,"next")).isEnabled())throw new AssertionError("unsettled capture bypassed");
  }catch(Exception e){throw new RuntimeException(e);}});
  if(defects.length()>0)throw new AssertionError(defects.toString());
  runOnMainSync(()->{try{set(screen,"pageDrained",true);invoke(screen,"refreshStatus");}catch(Exception e){throw new RuntimeException(e);}});
 }

 @Override public void onStart(){Bundle result=new Bundle();Activity a=null;try{
  Intent intent=new Intent().setClassName(getTargetContext(),"it.vintedaffari.app.VintedBrowserActivity").putExtra("mode","SEARCH").putExtra("url","https://www.vinted.it/catalog/4881-board-games?page=1&order=relevance&price_to=25").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  a=startActivitySync(intent);final Activity screen=a;final java.util.concurrent.CountDownLatch pageReady=new java.util.concurrent.CountDownLatch(1);
  runOnMainSync(()->{try{checkRealNavigationClient(screen);checkSessionRefresh(screen);WebView web=(WebView)get(screen,"web");web.stopLoading();web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView view,String url){view.postVisualStateCallback(1,new WebView.VisualStateCallback(){@Override public void onComplete(long requestId){view.postDelayed(pageReady::countDown,500);}});}});web.loadDataWithBaseURL("https://www.vinted.it/catalog/4881-board-games","<html><meta name='viewport' content='width=device-width'><body style='margin:0;background:#fff;font-family:sans-serif'><div style='padding:18px;color:#087f89;font-size:28px;font-weight:bold'>Vinted</div><div style='margin:12px;padding:14px;background:#edf1f2;color:#667'>Cerca in Giochi da tavolo</div><div style='padding:20px;font-size:26px;font-weight:bold'>Giochi da tavolo</div><div style='padding:20px;color:#667'>Contenuto locale per verifica visiva</div></body></html>","text/html","UTF-8",null);invoke(screen,"cancelCount");set(screen,"resumed",false);set(screen,"supported",true);set(screen,"loading",false);set(screen,"pageFailed",false);set(screen,"navigationUrl",intent.getStringExtra("url"));set(screen,"lastSearchUrl",intent.getStringExtra("url"));java.util.Set ids=(java.util.Set)get(screen,"persistedPageIds");for(int i=1;i<=96;i++)ids.add(String.valueOf(i));java.util.Map records=(java.util.Map)get(screen,"items");for(int i=1;i<=96;i++)records.put(String.valueOf(i),new org.json.JSONObject());set(screen,"pageObserved",96);set(screen,"pageDrained",true);state(screen,"exploreExpanded");}catch(Exception e){throw new RuntimeException(e);}});
  checkPartialIntakeAndControls(screen);
  if(!pageReady.await(15,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("local page did not render");waitForIdleSync();capture(a,"exploreExpanded");
  runOnMainSync(()->{try{((View)get(screen,"expansionToggle")).performClick();if(!"exploreCollapsed".equals(get(screen,"uiState").toString()))throw new AssertionError("visible collapse failed");}catch(Exception e){throw new RuntimeException(e);}});capture(a,"exploreCollapsed");
  runOnMainSync(()->{try{if(((View)get(screen,"controlsHost")).getVisibility()!=View.GONE)throw new AssertionError("expanded controls visible in collapsed");state(screen,"manualMatch");}catch(Exception e){throw new RuntimeException(e);}});capture(a,"manualMatch");
  runOnMainSync(()->{try{if(((View)get(screen,"counterRow")).getVisibility()!=View.GONE)throw new AssertionError("manual counter visible");if(((View)get(screen,"manualCard")).getVisibility()!=View.VISIBLE)throw new AssertionError("manual card hidden");View web=(View)get(screen,"web");if(web.getHeight()<screen.getResources().getDisplayMetrics().heightPixels*.25)throw new AssertionError("browser viewport consumed");}catch(Exception e){throw new RuntimeException(e);}});
  result.putString("result","Three actual browser states rendered; deterministic local WebView, no live Vinted visual claim");finish(Activity.RESULT_OK,result);
 }catch(Throwable failure){android.util.Log.e("LudoVisual","FAIL",failure);result.putString("error",failure.toString());finish(Activity.RESULT_CANCELED,result);}finally{if(a!=null){Activity done=a;runOnMainSync(done::finish);}}}
}
