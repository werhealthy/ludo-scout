package it.vintedaffari.app;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorSpace;
import android.hardware.HardwareBuffer;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public final class ThumbnailStore {
    private static final String TAG = "VintedThumbs";
    private static long lastCaptureAt = 0L;
    private static final ExecutorService DOWNLOADS = Executors.newSingleThreadExecutor();
    private ThumbnailStore() { }

    public static File fileFor(android.content.Context context, String signature) {
        File dir = new File(context.getFilesDir(), "thumbs_v2");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, sha1(signature) + ".jpg");
    }

    public static void captureMissing(AccessibilityService service, List<VintedCard> cards) {
        if (Build.VERSION.SDK_INT < 30 || service == null || cards == null || cards.isEmpty()) return;
        if(!enoughHeapForScreenshot())return;
        long now = System.currentTimeMillis();
        if (now - lastCaptureAt < 1200L) return;
        List<VintedCard> missing = new ArrayList<>();
        for (VintedCard c : cards) if (!fileFor(service, DealDatabase.signature(c)).exists()) missing.add(c);
        if (missing.isEmpty()) return;
        lastCaptureAt = now;
        Executor direct = Runnable::run;
        try {
            service.takeScreenshot(android.view.Display.DEFAULT_DISPLAY, direct,
                    new AccessibilityService.TakeScreenshotCallback() {
                        @Override public void onSuccess(AccessibilityService.ScreenshotResult result) {
                            HardwareBuffer hb = result.getHardwareBuffer();
                            ColorSpace cs = result.getColorSpace();
                            Bitmap hw = Bitmap.wrapHardwareBuffer(hb, cs);
                            if (hw == null) { hb.close(); return; }
                            Bitmap full = hw.copy(Bitmap.Config.RGB_565, false);
                            hb.close();
                            if (full == null) return;
                            for (VintedCard c : missing) saveCrop(service, full, c);
                            full.recycle();
                        }
                        @Override public void onFailure(int errorCode) {
                            Log.w(TAG, "takeScreenshot failure=" + errorCode);
                        }
                    });
        } catch (Throwable t) { Log.w(TAG, "Screenshot unavailable", t); }
    }

    private static void saveCrop(android.content.Context context, Bitmap full, VintedCard card) {
        Rect r = card.bounds;
        if (r == null) return;
        int l = Math.max(0, Math.min(full.getWidth()-1, r.left));
        int t = Math.max(0, Math.min(full.getHeight()-1, r.top));
        int rr = Math.max(l+1, Math.min(full.getWidth(), r.right));
        int bb = Math.max(t+1, Math.min(full.getHeight(), r.bottom));
        // Keep mostly the image portion; Vinted cards often include text in the lower ~25%.
        int imageBottom = Math.max(t + 1, t + (int)((bb - t) * 0.74f));
        try {
            Bitmap crop = Bitmap.createBitmap(full, l, t, rr-l, imageBottom-t);
            int targetW = 420;
            int targetH = Math.max(220, Math.round(crop.getHeight() * (targetW / (float) crop.getWidth())));
            Bitmap scaled = Bitmap.createScaledBitmap(crop, targetW, targetH, true);
            File out = fileFor(context, DealDatabase.signature(card));
            try (FileOutputStream fos = new FileOutputStream(out)) { scaled.compress(Bitmap.CompressFormat.JPEG, 82, fos); }
            android.content.SharedPreferences prefs=context.getSharedPreferences("va_v3_diag",android.content.Context.MODE_PRIVATE);prefs.edit().putLong("vintedThumbCaptured",prefs.getLong("vintedThumbCaptured",0L)+1L).apply();
            if (scaled != crop) scaled.recycle();
            crop.recycle();
        } catch (Throwable t1) { Log.w(TAG, "crop failed", t1); }
    }

    public static void captureProduct(AccessibilityService service, ProductPage page, String signature) {
        if (Build.VERSION.SDK_INT < 30 || service == null || page == null || page.imageBounds == null || page.imageBounds.isEmpty() || signature == null || signature.isEmpty()) return;
        if(!enoughHeapForScreenshot())return;
        Executor direct = Runnable::run;
        try {
            service.takeScreenshot(android.view.Display.DEFAULT_DISPLAY, direct, new AccessibilityService.TakeScreenshotCallback() {
                @Override public void onSuccess(AccessibilityService.ScreenshotResult result) {
                    HardwareBuffer hb = result.getHardwareBuffer();
                    Bitmap hw = Bitmap.wrapHardwareBuffer(hb, result.getColorSpace());
                    if (hw == null) { hb.close(); return; }
                    Bitmap full = hw.copy(Bitmap.Config.RGB_565, false); hb.close();
                    if (full == null) return;
                    saveRect(service, full, page.imageBounds, signature); full.recycle();
                }
                @Override public void onFailure(int errorCode) { Log.w(TAG, "product screenshot failure=" + errorCode); }
            });
        } catch (Throwable t) { Log.w(TAG, "product screenshot unavailable", t); }
    }

    private static void saveRect(android.content.Context context, Bitmap full, Rect r, String signature) {
        int l=Math.max(0,Math.min(full.getWidth()-1,r.left));
        int t=Math.max(0,Math.min(full.getHeight()-1,r.top));
        int rr=Math.max(l+1,Math.min(full.getWidth(),r.right));
        int bb=Math.max(t+1,Math.min(full.getHeight(),r.bottom));
        try {
            Bitmap crop=Bitmap.createBitmap(full,l,t,rr-l,bb-t);
            int targetW=640; int targetH=Math.max(320,Math.round(crop.getHeight()*(targetW/(float)crop.getWidth())));
            Bitmap scaled=Bitmap.createScaledBitmap(crop,targetW,targetH,true);
            File out=fileFor(context,signature);
            try(FileOutputStream fos=new FileOutputStream(out)){scaled.compress(Bitmap.CompressFormat.JPEG,88,fos);}
            if(scaled!=crop)scaled.recycle(); crop.recycle();
        } catch(Throwable e){Log.w(TAG,"product crop failed",e);}
    }

    public static void downloadRemote(android.content.Context context, String signature, String imageUrl) {
        if (context == null || signature == null || signature.isEmpty() || imageUrl == null || imageUrl.isEmpty()) return;
        File out = fileFor(context, signature);
        if (out.exists() && out.length() > 2048) return;
        DOWNLOADS.execute(() -> {
            HttpURLConnection c = null; InputStream in = null;
            try {
                c=(HttpURLConnection)new URL(imageUrl).openConnection(); c.setConnectTimeout(8000); c.setReadTimeout(10000); c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 16; Pixel 8) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36");
                if(c.getResponseCode()!=200) return; in=c.getInputStream(); Bitmap b=BitmapFactory.decodeStream(in); if(b==null)return;
                int targetW=520; int targetH=Math.max(280,Math.round(b.getHeight()*(targetW/(float)b.getWidth()))); Bitmap scaled=Bitmap.createScaledBitmap(b,targetW,targetH,true);
                try(FileOutputStream fos=new FileOutputStream(out)){scaled.compress(Bitmap.CompressFormat.JPEG,88,fos);}
                if(scaled!=b)scaled.recycle();b.recycle();
            } catch(Throwable t){Log.w(TAG,"remote thumbnail failed",t);} finally {try{if(in!=null)in.close();}catch(Exception ignored){} if(c!=null)c.disconnect();}
        });
    }

    private static boolean enoughHeapForScreenshot(){
        try{Runtime r=Runtime.getRuntime();long headroom=r.maxMemory()-(r.totalMemory()-r.freeMemory());return headroom>=24L*1024L*1024L;}catch(Throwable ignored){return true;}
    }

    private static String sha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest((input == null ? "" : input).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder(); for (byte x : d) b.append(String.format("%02x", x)); return b.toString();
        } catch (Exception e) { return Integer.toHexString((input == null ? "" : input).hashCode()); }
    }
}
