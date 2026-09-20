package it.vintedaffari.app;

import android.content.Context;
import android.graphics.PixelFormat;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
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
    private static final long READY_TIMEOUT_MS = 30_000L;
    private static final long READY_RETRY_MS = 500L;
    private final Context context;
    private final WindowManager windowManager;
    private final Handler main = new Handler(Looper.getMainLooper());
    private WebView webView;
    private boolean ready = false;
    private boolean attached = false;
    private boolean verifyInFlight = false;
    private boolean terminalReadyCallback = false;
    private long verifyStartedAt = 0L;
    private ReadyListener readyListener;

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
        ready=false;verifyInFlight=false;terminalReadyCallback=false;verifyStartedAt=System.currentTimeMillis();

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
        // onPageFinished may arrive before engine.html has completed its asynchronous bridge setup,
        // especially after a WebView/process restart. Probe independently and retry for a bounded time.
        main.postDelayed(this::verifyEngine, READY_RETRY_MS);
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
        if (webView == null || ready || terminalReadyCallback || verifyInFlight) return;
        verifyInFlight=true;
        String js = "(() => JSON.stringify({ready:!!globalThis.VintedAffariAndroidBridge?.ready,gameCount:globalThis.VintedAffariAndroidBridge?.gameCount||0}))()";
        try {
            webView.evaluateJavascript(js, value -> {
                verifyInFlight=false;
                try {
                    String decoded = decodeJavascriptString(value);
                    JSONObject state = new JSONObject(decoded);
                    ready = state.optBoolean("ready", false);
                    int gameCount = state.optInt("gameCount", 0);
                    if (ready) {
                        terminalReadyCallback=true;
                        Log.d(TAG, "Motore JS pronto: " + gameCount + " giochi");
                        if (readyListener != null) readyListener.onReady(gameCount);
                        return;
                    }
                    retryVerifyOrFail("Il runtime JS non risulta pronto.");
                } catch (Exception e) {
                    Log.w(TAG, "Verifica runtime JS non ancora riuscita", e);
                    retryVerifyOrFail(e.getMessage());
                }
            });
        } catch (Throwable t) {
            verifyInFlight=false;
            retryVerifyOrFail(t.getMessage());
        }
    }

    private void retryVerifyOrFail(String message) {
        if (webView == null || ready || terminalReadyCallback) return;
        long elapsed=System.currentTimeMillis()-verifyStartedAt;
        if (elapsed < READY_TIMEOUT_MS) {
            main.postDelayed(this::verifyEngine, READY_RETRY_MS);
            return;
        }
        terminalReadyCallback=true;
        String safe=(message==null||message.trim().isEmpty())
                ?"Il runtime JS non è diventato pronto entro 30 secondi.":message;
        Log.e(TAG, safe);
        if (readyListener != null) readyListener.onError(safe);
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
        ready = false;terminalReadyCallback=true;verifyInFlight=false;main.removeCallbacksAndMessages(null);
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
