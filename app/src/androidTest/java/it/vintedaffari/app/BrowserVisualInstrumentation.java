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
 @Override public void onStart(){Bundle result=new Bundle();Activity a=null;try{
  Intent intent=new Intent().setClassName(getTargetContext(),"it.vintedaffari.app.VintedBrowserActivity").putExtra("mode","SEARCH").putExtra("url","https://www.vinted.it/catalog/4881-board-games?page=1&order=relevance&price_to=25").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  a=startActivitySync(intent);final Activity screen=a;final java.util.concurrent.CountDownLatch pageReady=new java.util.concurrent.CountDownLatch(1);
  runOnMainSync(()->{try{WebView web=(WebView)get(screen,"web");web.stopLoading();web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView view,String url){view.postVisualStateCallback(1,new WebView.VisualStateCallback(){@Override public void onComplete(long requestId){pageReady.countDown();}});}});web.loadDataWithBaseURL("https://www.vinted.it/catalog/4881-board-games","<html><meta name='viewport' content='width=device-width'><body style='margin:0;background:#fff;font-family:sans-serif'><div style='padding:18px;color:#087f89;font-size:28px;font-weight:bold'>Vinted</div><div style='margin:12px;padding:14px;background:#edf1f2;color:#667'>Cerca in Giochi da tavolo</div><div style='padding:20px;font-size:26px;font-weight:bold'>Giochi da tavolo</div><div style='padding:20px;color:#667'>Contenuto locale per verifica visiva</div></body></html>","text/html","UTF-8",null);invoke(screen,"cancelCount");set(screen,"resumed",false);set(screen,"supported",true);set(screen,"loading",false);set(screen,"pageFailed",false);set(screen,"navigationUrl",intent.getStringExtra("url"));set(screen,"lastSearchUrl",intent.getStringExtra("url"));java.util.Set ids=(java.util.Set)get(screen,"persistedPageIds");for(int i=1;i<=96;i++)ids.add(String.valueOf(i));java.util.Map records=(java.util.Map)get(screen,"items");for(int i=1;i<=96;i++)records.put(String.valueOf(i),new org.json.JSONObject());set(screen,"pageObserved",96);set(screen,"pageDrained",true);state(screen,"exploreExpanded");}catch(Exception e){throw new RuntimeException(e);}});
  if(!pageReady.await(15,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("local page did not render");waitForIdleSync();capture(a,"exploreExpanded");
  runOnMainSync(()->{try{state(screen,"exploreCollapsed");}catch(Exception e){throw new RuntimeException(e);}});capture(a,"exploreCollapsed");
  runOnMainSync(()->{try{if(((View)get(screen,"controlsHost")).getVisibility()!=View.GONE)throw new AssertionError("expanded controls visible in collapsed");state(screen,"manualMatch");}catch(Exception e){throw new RuntimeException(e);}});capture(a,"manualMatch");
  runOnMainSync(()->{try{if(((View)get(screen,"counterRow")).getVisibility()!=View.GONE)throw new AssertionError("manual counter visible");if(((View)get(screen,"manualCard")).getVisibility()!=View.VISIBLE)throw new AssertionError("manual card hidden");View web=(View)get(screen,"web");if(web.getHeight()<screen.getResources().getDisplayMetrics().heightPixels*.25)throw new AssertionError("browser viewport consumed");}catch(Exception e){throw new RuntimeException(e);}});
  result.putString("result","Three actual browser states rendered; deterministic local WebView, no live Vinted visual claim");finish(Activity.RESULT_OK,result);
 }catch(Throwable failure){android.util.Log.e("LudoVisual","FAIL",failure);result.putString("error",failure.toString());finish(Activity.RESULT_CANCELED,result);}finally{if(a!=null){Activity done=a;runOnMainSync(done::finish);}}}
}
