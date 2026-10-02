package it.vintedaffari.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Measurement-only browser: no catalog writers, endpoint replay or automatic navigation. */
public final class VintedBrowserActivity extends Activity {
 private static final int BG=Color.rgb(10,11,20),TEXT=Color.WHITE,MUTED=Color.rgb(170,170,195);
 private final ThreadPoolExecutor ingest=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(4));
 private final LinkedHashMap<String,JSONObject> items=new LinkedHashMap<>();
 private final LinkedHashSet<String> routes=new LinkedHashSet<>();
 private final AtomicInteger rejected=new AtomicInteger();
 private volatile boolean accepting,closed,resumed;
 private long captureGeneration;
 private boolean enabled,supported,rendererGone,pageFailed;
 private String mode,state="STARTING",expectedSeller="",lastReport="",navigationUrl="";
 private int updates,jsonItems,domItems,dropped,unknownShapes,readErrors,oversized;
 private WebView web;private TextView status;private Button toggle,capture;
 @Override public void onCreate(Bundle saved){super.onCreate(saved);mode=getIntent().getStringExtra("mode");if(!"SEARCH".equals(mode)&&!"BUNDLE".equals(mode))mode="VIEW";enabled=VintedBrowserPolicy.startsEnabled(mode);expectedSeller=getIntent().getStringExtra("expected_seller_id");if(expectedSeller==null||!expectedSeller.matches("[1-9][0-9]{0,18}"))expectedSeller="";
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setPadding(0,statusBarHeight(),0,0);setContentView(root);
  LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.addView(action("Chiudi",this::finish),new LinearLayout.LayoutParams(0,-2,1));toggle=action(enabled?"Pausa":"Attiva",()->setCapture(!enabled));bar.addView(toggle,new LinearLayout.LayoutParams(0,-2,1));capture=action("Cattura",this::captureNow);bar.addView(capture,new LinearLayout.LayoutParams(0,-2,1));bar.addView(action("Dati",this::showReport),new LinearLayout.LayoutParams(0,-2,1));root.addView(bar);
  status=new TextView(this);status.setTextColor(MUTED);status.setTextSize(13);status.setPadding(dp(12),dp(4),dp(12),dp(6));root.addView(status);web=new WebView(this);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
  WebView.setWebContentsDebuggingEnabled(false);WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setSafeBrowsingEnabled(true);settings.setSupportMultipleWindows(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
  web.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){if(!request.isForMainFrame())return false;boolean allowed=VintedBrowserPolicy.allowedPage(request.getUrl().toString());if(!allowed)Toast.makeText(VintedBrowserActivity.this,"Questa pagina non fa parte del test pubblico",Toast.LENGTH_SHORT).show();return !allowed;}
   @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){observeRoute(request.getUrl());return null;}
   @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){navigationUrl=url;pageFailed=false;accepting=supported&&enabled&&resumed&&VintedBrowserPolicy.allowedPage(url);if(!VintedBrowserPolicy.allowedPage(url)){view.stopLoading();state="PAGE_BLOCKED";refreshStatus();}}
   @Override public void onPageFinished(WebView view,String url){if(closed||rendererGone)return;if(pageFailed){accepting=false;}else if(!VintedBrowserPolicy.allowedPage(url)){state="PAGE_BLOCKED";accepting=false;}else{state=supported?(enabled?"CAPTURING":"PAUSED"):"UNSUPPORTED_PROVIDER";accepting=supported&&enabled&&resumed;if(supported)view.evaluateJavascript("window.LudoCaptureControl&&window.LudoCaptureControl.setEnabled("+(enabled&&resumed)+")",null);}refreshStatus();}
   @Override public void onReceivedError(WebView view,WebResourceRequest request,android.webkit.WebResourceError error){if(request.isForMainFrame()){pageFailed=true;accepting=false;state="PAGE_ERROR";refreshStatus();}}
   @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){if(request.isForMainFrame()&&response.getStatusCode()>=400){pageFailed=true;accepting=false;state="PAGE_ERROR";refreshStatus();}}
   @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){accepting=false;rendererGone=true;state="RENDERER_GONE";((LinearLayout)view.getParent()).removeView(view);view.destroy();web=null;refreshStatus();Toast.makeText(VintedBrowserActivity.this,"Browser interrotto. Chiudi e riapri per riprovare.",Toast.LENGTH_LONG).show();return true;}
  });
  supported=WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)&&WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER);
  try{if(supported){WebViewCompat.addWebMessageListener(web,"LudoCapture",new LinkedHashSet<>(java.util.Arrays.asList("https://www.vinted.it","https://vinted.it")),(view,message,origin,mainFrame,reply)->receive(message.getData(),origin.toString(),mainFrame,reply));String script;try(java.io.InputStream input=getAssets().open("browser/vinted-capture.js")){java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;while((count=input.read(buffer))!=-1)output.write(buffer,0,count);script=new String(output.toByteArray(),StandardCharsets.UTF_8);}JSONObject initial=new JSONObject();initial.put("enabled",enabled);initial.put("expectedSeller",expectedSeller);WebViewCompat.addDocumentStartJavaScript(web,"window.__LudoCaptureInitial="+initial+";\n"+script,new LinkedHashSet<>(java.util.Arrays.asList("https://www.vinted.it","https://vinted.it")));}}
  catch(Exception error){supported=false;accepting=false;state="CAPTURE_INIT_ERROR";}
  toggle.setEnabled(supported);capture.setEnabled(supported);refreshStatus();String url=getIntent().getStringExtra("url");if(!VintedBrowserPolicy.allowedPage(url))url="https://www.vinted.it/catalog?search_text=catan";navigationUrl=url;accepting=supported&&enabled&&resumed;web.loadUrl(url);
 }
 private Button action(String name,Runnable run){Button b=new Button(this);b.setText(name);b.setTextColor(TEXT);b.setTextSize(12);b.setMinHeight(dp(48));b.setContentDescription(name);b.setOnClickListener(v->run.run());return b;}
 private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
 private int statusBarHeight(){int id=getResources().getIdentifier("status_bar_height","dimen","android");return id==0?0:getResources().getDimensionPixelSize(id);}
 private void setCapture(boolean value){if(!supported||web==null)return;captureGeneration++;enabled=value;accepting=value&&resumed&&!pageFailed&&VintedBrowserPolicy.allowedPage(navigationUrl);state=value?"CAPTURING":"PAUSED";web.evaluateJavascript("window.LudoCaptureControl&&window.LudoCaptureControl.setEnabled("+value+")",null);refreshStatus();}
 private void captureNow(){if(!supported||web==null||!resumed||pageFailed||!VintedBrowserPolicy.allowedPage(web.getUrl()))return;captureGeneration++;accepting=true;web.evaluateJavascript("window.LudoCaptureControl&&window.LudoCaptureControl.captureNow()",null);}
 private static String messageSequence(String data){if(data==null)return "0";java.util.regex.Matcher m=java.util.regex.Pattern.compile("\"seq\":([0-9]+)").matcher(data.substring(0,Math.min(100,data.length())));return m.find()?m.group(1):"0";}
 private void receive(String data,String origin,boolean mainFrame,androidx.webkit.JavaScriptReplyProxy reply){if(closed||!accepting||data==null||data.length()>128*1024||web==null||!VintedBrowserPolicy.allowedPage(navigationUrl)){rejected.incrementAndGet();reply.postMessage("paused:"+messageSequence(data));return;}int bytes=data.getBytes(StandardCharsets.UTF_8).length;if(!VintedBrowserPolicy.allowedMessage(origin,mainFrame,bytes)){rejected.incrementAndGet();return;}final long generation=captureGeneration;try{ingest.execute(()->{parse(data,generation);runOnUiThread(()->{if(!closed)reply.postMessage("ready:"+messageSequence(data));});});}catch(java.util.concurrent.RejectedExecutionException error){rejected.incrementAndGet();reply.postMessage("retry:"+messageSequence(data));}}
 private void parse(String data,long generation){if(closed)return;try{JSONObject packet=new JSONObject(data);JSONArray incoming=packet.optJSONArray("items");if(packet.optInt("schema")!=1||incoming==null||incoming.length()>32){rejected.incrementAndGet();return;}
  synchronized(items){for(int i=0;i<incoming.length();i++){JSONObject item=incoming.optJSONObject(i);if(item==null||!VintedBrowserPolicy.matchesItem(item.optString("url"),item.optString("id"))||item.optString("title").length()>600){rejected.incrementAndGet();continue;}String id=item.getString("id");if(!items.containsKey(id)&&items.size()>=500){dropped++;continue;}JSONObject clean=new JSONObject();for(String key:new String[]{"id","url","title","priceCents","currency","sellerId","language","publication","source"}){Object value=item.opt(key);if(value==null||value==JSONObject.NULL)continue;if(value instanceof String&&((String)value).length()<=600)clean.put(key,value);else if("priceCents".equals(key)&&value instanceof Number&&((Number)value).longValue()>0&&((Number)value).longValue()<=1000000000L)clean.put(key,value);else if("publication".equals(key)&&value instanceof JSONObject){JSONObject p=(JSONObject)value;if(p.optString("raw").length()<=100&&p.optString("source").length()<=50)clean.put(key,new JSONObject().put("raw",p.optString("raw")).put("source",p.optString("source")));}}
   JSONObject previous=items.get(id);if(previous!=null&&!previous.toString().equals(clean.toString()))updates++;items.put(id,clean);if("dom".equals(clean.optString("source")))domItems++;else jsonItems++;}
   JSONObject stats=packet.optJSONObject("stats");if(stats!=null){dropped=Math.max(dropped,bound(stats.optInt("dropped")));unknownShapes=Math.max(unknownShapes,bound(stats.optInt("unknownShapes")));readErrors=Math.max(readErrors,bound(stats.optInt("readErrors")));oversized=Math.max(oversized,bound(stats.optInt("oversized")));}lastReport=reportLocked();}
  boolean complete=packet.optBoolean("complete");saveReport();runOnUiThread(()->{if(!closed){if(complete&&!enabled&&generation==captureGeneration)accepting=false;refreshStatus();}});
 }catch(Exception error){rejected.incrementAndGet();}}
 private static int bound(int n){return Math.max(0,Math.min(1000000,n));}
 private void observeRoute(Uri uri){if(uri==null||!"https".equals(uri.getScheme())||!("www.vinted.it".equals(uri.getHost())||"vinted.it".equals(uri.getHost())))return;String path=uri.getPath();if(path==null||!path.startsWith("/api/"))return;path=path.replaceAll("[0-9]+","{id}").replaceAll("[^a-zA-Z0-9/{}_\\-]","_");if(path.length()>160)path=path.substring(0,160);synchronized(routes){if(routes.size()<20)routes.add(path);}}
 private String reportLocked(){int price=0,seller=0,publication=0,language=0;for(JSONObject item:items.values()){if(item.has("priceCents"))price++;if(item.has("sellerId"))seller++;if(item.has("publication"))publication++;if(item.has("language"))language++;}String routeSummary;synchronized(routes){routeSummary=routes.toString();}return "build=browser-capture-experiment-v1;app="+BuildConfig.VERSION_NAME+";state="+state+";mode="+mode+";features="+supported+";capture="+enabled+";acquiredUnique="+items.size()+";updated="+updates+";price="+price+";seller="+seller+";publication="+publication+";language="+language+";jsonOrInitial="+jsonItems+";dom="+domItems+";dropped="+dropped+";unknownShapes="+unknownShapes+";readErrors="+readErrors+";oversized="+oversized+";rejected="+rejected.get()+";extraRequestsByCapture=0;networkScope=capturer-only;catalogWrites=0;sellerCoverage=partial;routes="+routeSummary;}
 private void saveReport(){String report; synchronized(items){report=reportLocked();lastReport=report;}getSharedPreferences("vinted_browser_experiment",MODE_PRIVATE).edit().putString("report",report).putLong("at",System.currentTimeMillis()).apply();}
 private void refreshStatus(){int size;synchronized(items){size=items.size();}if(status==null)return;toggle.setText(enabled?"Pausa":"Attiva");status.setText(!supported?"Cattura non disponibile su questa WebView":rendererGone?"Browser interrotto":state.equals("PAGE_BLOCKED")||state.equals("PAGE_ERROR")?"Pagina non disponibile per la cattura":"Test · "+(enabled?"cattura attiva":"consultazione")+" · "+size+" annunci acquisiti");saveReport();}
 private void showReport(){String report;StringBuilder samples=new StringBuilder();synchronized(items){report=reportLocked();int n=0;for(JSONObject item:items.values()){if(n++>=8)break;samples.append("\n\n#").append(item.optString("id")).append(" · ").append(item.optString("title")).append("\n").append(item.optString("source")).append(" · ").append(item.opt("publication"));}}final String publicReport=report;TextView text=new TextView(this);text.setTextColor(TEXT);text.setTextSize(13);text.setPadding(dp(16),dp(16),dp(16),dp(16));text.setText("Test sperimentale: nessuna scrittura nel Catalogo. Il sito può caricare soltanto una parte degli annunci del venditore.\n\n"+report+samples);text.setTextIsSelectable(true);ScrollView scroll=new ScrollView(this);scroll.addView(text);new AlertDialog.Builder(this).setTitle("Cattura browser · diagnostica").setView(scroll).setPositiveButton("Torna a Vinted",null).setNeutralButton("Copia report",(d,w)->{ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(ClipData.newPlainText("Diagnostica browser",publicReport));}).show();}
 @Override public void onBackPressed(){if(web!=null&&web.canGoBack())web.goBack();else finish();}
 @Override protected void onPause(){super.onPause();resumed=false;captureGeneration++;accepting=false;if(web!=null)web.evaluateJavascript("window.LudoCaptureControl&&window.LudoCaptureControl.setEnabled(false)",null);saveReport();}
 @Override protected void onResume(){super.onResume();resumed=true;if(web!=null&&supported){accepting=enabled&&resumed&&!pageFailed&&VintedBrowserPolicy.allowedPage(navigationUrl);web.evaluateJavascript("window.LudoCaptureControl&&window.LudoCaptureControl.setEnabled("+(enabled&&resumed)+")",null);}}
 @Override protected void onDestroy(){closed=true;accepting=false;ingest.shutdownNow();if(web!=null){web.stopLoading();web.destroy();web=null;}synchronized(items){items.clear();}super.onDestroy();}
}
