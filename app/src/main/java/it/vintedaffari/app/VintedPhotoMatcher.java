package it.vintedaffari.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Lightweight visual identity check between the screenshot captured when a Vinted card was first
 * observed and a candidate thumbnail exposed by Vinted's public catalogue HTML.
 *
 * This is deliberately a secondary signal: title + price remain mandatory. The photo is only used
 * to break ties between otherwise plausible candidates.
 */
public final class VintedPhotoMatcher {
    private VintedPhotoMatcher() {}

    public static double similarity(Context context,String signature,String candidateImageUrl){
        if(context==null||TextUtils.isEmpty(signature)||TextUtils.isEmpty(candidateImageUrl))return Double.NaN;
        File observed=ThumbnailStore.fileFor(context,signature);
        if(!observed.exists()||observed.length()<2048)return Double.NaN;
        Bitmap a=decodeFile(observed,420,520);if(a==null)return Double.NaN;
        // Exact source only: different crops/sizes may have different hashes.
        String source=candidateImageUrl.replace("&amp;","&");
        Long cached=null;
        try{cached=VintedPhotoHashCache.lookupExact(context,source);}catch(Throwable ignored){}
        if(cached!=null){
            try{
                double sim=VisualCoverMatcher.similarity(VisualCoverMatcher.queryHashes64(a),cached);
                VintedPublicSession.recordPhotoMatcherEvent(context,"cache",0);
                return sim;
            }finally{if(!a.isRecycled())a.recycle();}
        }
        Bitmap b=null;HttpURLConnection c=null;InputStream in=null;
        try{
            c=(HttpURLConnection)new URL(source).openConnection();
            c.setConnectTimeout(4500);c.setReadTimeout(6500);c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent","Mozilla/5.0 Android LudoScout/5.11");
            VintedPublicSession.recordPhotoMatcherEvent(context,"download",0);
            int code=c.getResponseCode();VintedPublicSession.recordPhotoMatcherEvent(context,"http",code);if(code<200||code>=400)return Double.NaN;
            in=c.getInputStream();b=decodeStream(in,420,520);if(b==null)return Double.NaN;
            double sim=VisualCoverMatcher.similarity(a,b);
            // Test 8: this image was already downloaded by the normal resolver. Persist only its
            // perceptual hash so other observations can reuse the visual evidence with zero extra HTTP.
            try{VintedPhotoHashCache.record(context,source,b);}catch(Throwable ignored){}
            return sim;
        }catch(Throwable ignored){VintedPublicSession.recordPhotoMatcherEvent(context,"error",0);return Double.NaN;}
        finally{try{if(in!=null)in.close();}catch(Exception ignored){}if(c!=null)c.disconnect();if(a!=null&&!a.isRecycled())a.recycle();if(b!=null&&!b.isRecycled())b.recycle();}
    }

    private static Bitmap decodeFile(File f,int maxW,int maxH){
        try{BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getAbsolutePath(),bounds);BitmapFactory.Options o=options(bounds.outWidth,bounds.outHeight,maxW,maxH);return BitmapFactory.decodeFile(f.getAbsolutePath(),o);}catch(Throwable e){return null;}
    }
    private static Bitmap decodeStream(InputStream in,int maxW,int maxH){
        try{
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream(128*1024);byte[] buf=new byte[16*1024];int n,total=0;
            while((n=in.read(buf))!=-1){total+=n;if(total>4*1024*1024)return null;out.write(buf,0,n);}byte[] data=out.toByteArray();
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,bounds);BitmapFactory.Options o=options(bounds.outWidth,bounds.outHeight,maxW,maxH);return BitmapFactory.decodeByteArray(data,0,data.length,o);
        }catch(Throwable e){return null;}
    }
    private static BitmapFactory.Options options(int w,int h,int maxW,int maxH){BitmapFactory.Options o=new BitmapFactory.Options();int sample=1;while(w/sample>maxW*2||h/sample>maxH*2)sample*=2;o.inSampleSize=Math.max(1,sample);o.inPreferredConfig=Bitmap.Config.RGB_565;return o;}
}
