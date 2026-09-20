package it.vintedaffari.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ArtworkStore {
    private static final String TAG="ArtworkStore";
    private static final ExecutorService IO=Executors.newFixedThreadPool(2);
    private static final Set<String> IN_FLIGHT=ConcurrentHashMap.newKeySet();
    private ArtworkStore(){}
    public static File bggFile(Context c,String bggId){File dir=new File(c.getFilesDir(),"bgg_art");if(!dir.exists())dir.mkdirs();return new File(dir,safe(bggId)+".jpg");}
    public static void downloadBgg(Context c,String bggId,String url){if(c==null||bggId==null||bggId.isEmpty()||url==null||url.isEmpty())return;File f=bggFile(c,bggId);if(f.exists()&&f.length()>2048)return;if(!IN_FLIGHT.add(bggId))return;OperationCenter.queued(c,"cover:"+bggId,OperationCenter.COVER,bggId);IO.execute(()->{OperationCenter.running(c,"cover:"+bggId,OperationCenter.COVER,bggId);HttpURLConnection conn=null;InputStream in=null;try{conn=(HttpURLConnection)new URL(url).openConnection();conn.setConnectTimeout(8000);conn.setReadTimeout(12000);conn.setInstanceFollowRedirects(true);conn.setRequestProperty("User-Agent","LudoScout/5.3 Android");int code=conn.getResponseCode();c.getSharedPreferences("va_v3_diag",Context.MODE_PRIVATE).edit().putInt("bggImageLastCode",code).apply();if(code<200||code>=400)throw new IOException("HTTP "+code);in=conn.getInputStream();Bitmap bmp=BitmapFactory.decodeStream(in);if(bmp==null)throw new IOException("immagine non valida");int max=900;int w=bmp.getWidth(),h=bmp.getHeight();if(Math.max(w,h)>max){float sc=max/(float)Math.max(w,h);Bitmap scaled=Bitmap.createScaledBitmap(bmp,Math.max(1,(int)(w*sc)),Math.max(1,(int)(h*sc)),true);if(scaled!=bmp)bmp.recycle();bmp=scaled;}try(FileOutputStream out=new FileOutputStream(f)){bmp.compress(Bitmap.CompressFormat.JPEG,88,out);}bmp.recycle();c.getSharedPreferences("va_v3_diag",Context.MODE_PRIVATE).edit().putLong("bggImageBytes",f.length()).putString("bggImageLastError","").apply();OperationCenter.done(c,"cover:"+bggId,OperationCenter.COVER,bggId);}catch(Throwable t){String e=t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage());c.getSharedPreferences("va_v3_diag",Context.MODE_PRIVATE).edit().putString("bggImageLastError",e).apply();OperationCenter.error(c,"cover:"+bggId,OperationCenter.COVER,bggId,e);Log.w(TAG,"bgg artwork failed",t);}finally{try{if(in!=null)in.close();}catch(Exception ignored){}if(conn!=null)conn.disconnect();IN_FLIGHT.remove(bggId);}});}
    private static String safe(String s){return s.replaceAll("[^a-zA-Z0-9_-]","_");}
}
