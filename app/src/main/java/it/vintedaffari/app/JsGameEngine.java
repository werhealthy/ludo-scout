package it.vintedaffari.app;

import android.content.Context;
import android.graphics.PixelFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.content.SharedPreferences;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.List;

public final class JsGameEngine {

    public interface ReadyListener {
        void onReady(int gameCount);
        void onError(String message);
    }

    public interface BatchListener {
        void onResult(List<GameAnalysis> analyses);
        void onError(String message);
    }

    private static final String TAG = "VintedAffariEngine";
    private final Context context;
    private final WindowManager windowManager;
    private WebView webView;
    private boolean ready = false;
    private boolean attached = false;
    private ReadyListener readyListener;
    private boolean verifyInFlight = false;
    private int verifyAttempts = 0;
    private static final int MAX_VERIFY_ATTEMPTS = 45;
    private static final long VERIFY_RETRY_MS = 2_000L;

    public JsGameEngine(Context context, WindowManager windowManager) {
        this.context = context;
        this.windowManager = windowManager;
    }

    public boolean isReady() {
        return ready;
    }

    public void start(ReadyListener listener) {
        this.readyListener = listener;
        if (webView != null) return;

        webView = new WebView(context);
        webView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        webView.setBackgroundColor(0x00000000);
        webView.setAlpha(0.01f);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setDomStorageEnabled(false);
        settings.setDatabaseEnabled(false);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                verifyEngine();
            }
        });

        attachHiddenWebView();
        webView.loadUrl("file:///android_asset/engine/engine.html");
        // A cold WebView parsing the bundled catalog can take materially longer than page creation,
        // especially immediately after an APK update. Do not rely on one onPageFinished callback:
        // keep a bounded readiness watchdog until the bridge is actually usable.
        webView.postDelayed(this::verifyEngine, VERIFY_RETRY_MS);
    }

    private void attachHiddenWebView() {
        if (attached || webView == null) return;
        if(context instanceof android.app.Activity){((android.app.Activity)context).addContentView(webView,new android.view.ViewGroup.LayoutParams(1,1));attached=true;return;}
        if(windowManager==null)return;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                1,
                1,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = -10;
        params.y = -10;
        try {
            windowManager.addView(webView, params);
            attached = true;
        } catch (Exception e) {
            Log.e(TAG, "Impossibile montare il runtime JS", e);
            if (readyListener != null) readyListener.onError(e.getMessage());
        }
    }

    private void verifyEngine() {
        if (webView == null || ready || verifyInFlight) return;
        verifyInFlight=true;
        final WebView current=webView;
        final int attempt=++verifyAttempts;
        String js = "(() => JSON.stringify({ready:!!globalThis.VintedAffariAndroidBridge?.ready,gameCount:globalThis.VintedAffariAndroidBridge?.gameCount||0}))()";
        current.evaluateJavascript(js, value -> {
            verifyInFlight=false;
            if(current!=webView||ready)return;
            try {
                String decoded = decodeJavascriptString(value);
                JSONObject state = new JSONObject(decoded);
                ready = state.optBoolean("ready", false);
                int gameCount = state.optInt("gameCount", 0);
                if (ready) {
                    Log.d(TAG, "Motore JS pronto: " + gameCount + " giochi · tentativo " + attempt);
                    if (readyListener != null) readyListener.onReady(gameCount);
                    return;
                }
            } catch (Exception e) {
                Log.w(TAG, "Runtime JS non ancora verificabile al tentativo " + attempt, e);
            }
            if(attempt<MAX_VERIFY_ATTEMPTS&&webView!=null){
                current.postDelayed(this::verifyEngine,VERIFY_RETRY_MS);
            }else{
                String message = "Il runtime JS non risulta pronto dopo "+attempt+" tentativi.";
                Log.e(TAG, message);
                if (readyListener != null) readyListener.onError(message);
            }
        });
    }

    public void analyze(List<VintedCard> cards, BatchListener listener) {
        if (!ready || webView == null) {
            listener.onError("Motore BGG non ancora pronto");
            return;
        }

        try {
            JSONArray input = new JSONArray();
            SharedPreferences prefs = context.getSharedPreferences("va_settings", Context.MODE_PRIVATE);
            int shippingCents = prefs.getInt("shipping_cents", 450);
            for (VintedCard card : cards) {
                JSONObject item = new JSONObject();
                item.put("title", card.title);
                item.put("brand", card.brand);
                item.put("itemPrice", card.itemPrice);
                if (card.protectedPrice != null) item.put("protectedPrice", card.protectedPrice);
                item.put("shippingCents", shippingCents);
                input.put(item);
            }

            String js = "(() => JSON.stringify(globalThis.VintedAffariAndroidBridge.analyzeBatch(" + input + ")))()";
            webView.evaluateJavascript(js, value -> {
                try {
                    String decoded = decodeJavascriptString(value);
                    JSONArray array = new JSONArray(decoded);
                    List<GameAnalysis> analyses = new ArrayList<>();
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject row = array.optJSONObject(i);
                        analyses.add(row == null
                                ? GameAnalysis.fromJson(new JSONObject().put("status", "error").put("reason", "Risposta JS non valida"))
                                : GameAnalysis.fromJson(row));
                    }
                    listener.onResult(analyses);
                } catch (Exception e) {
                    Log.e(TAG, "Errore parsing risposta JS", e);
                    listener.onError(e.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Errore richiesta JS", e);
            listener.onError(e.getMessage());
        }
    }

    private static String decodeJavascriptString(String value) throws Exception {
        if (value == null || "null".equals(value)) return "{}";
        Object decoded = new JSONTokener(value).nextValue();
        if (decoded instanceof String) return (String) decoded;
        return String.valueOf(decoded);
    }

    public void destroy() {
        ready = false;
        verifyInFlight = false;
        verifyAttempts = MAX_VERIFY_ATTEMPTS;
        if (webView == null) return;
        try {
            if(context instanceof android.app.Activity && webView.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)webView.getParent()).removeView(webView);else if (attached && windowManager != null) windowManager.removeViewImmediate(webView);
        } catch (Exception ignored) {
        }
        attached = false;
        try {
            webView.stopLoading();
            webView.destroy();
        } catch (Exception ignored) {
        }
        webView = null;
    }
}
