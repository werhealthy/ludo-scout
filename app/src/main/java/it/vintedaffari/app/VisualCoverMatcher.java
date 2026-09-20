package it.vintedaffari.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Lightweight on-device visual matching against BGG covers already cached by Ludo Scout.
 * It intentionally avoids OCR-only matching: several center crops of the photo are compared
 * with perceptual difference hashes of cached covers. OCR remains an independent text signal.
 */
public final class VisualCoverMatcher {
    public static final class Candidate {
        public final String bggId;
        public final int distance;
        public final double similarity;
        Candidate(String id,int d){bggId=id;distance=d;similarity=Math.max(0d,1d-d/64d);}
    }
    private VisualCoverMatcher(){}

    public static List<Candidate> match(Context context,Bitmap photo,int limit){
        if(context==null||photo==null||photo.getWidth()<24||photo.getHeight()<24)return Collections.emptyList();
        File dir=new File(context.getFilesDir(),"bgg_art");File[] files=dir.listFiles((d,n)->n.endsWith(".jpg")||n.endsWith(".jpeg")||n.endsWith(".png"));
        if(files==null||files.length==0)return Collections.emptyList();
        long[] query=queryHashes(photo);List<Candidate> out=new ArrayList<>();
        for(File file:files){Bitmap cover=BitmapFactory.decodeFile(file.getAbsolutePath());if(cover==null)continue;long h=dHash(cover);cover.recycle();int best=64;for(long q:query)best=Math.min(best,Long.bitCount(q^h));String name=file.getName();int dot=name.lastIndexOf('.');if(dot>0)name=name.substring(0,dot);out.add(new Candidate(name,best));}
        out.sort(Comparator.comparingInt(c->c.distance));if(out.size()>Math.max(1,limit))return new ArrayList<>(out.subList(0,Math.max(1,limit)));return out;
    }

    /** Compare one photographed box against one known cover after the text shortlist has reduced the search space. */
    public static double similarity(Bitmap photo,Bitmap cover){
        if(photo==null||cover==null)return 0d;long[] query=queryHashes(photo);long h=dHash(cover);int best=64;for(long q:query)best=Math.min(best,Long.bitCount(q^h));return Math.max(0d,1d-best/64d);
    }

    /** Public hash helpers used by the Vinted shadow cache. They perform no network work. */
    public static long[] queryHashes64(Bitmap source){return source==null?new long[0]:queryHashes(source);}
    public static long dHash64(Bitmap source){return source==null?0L:dHash(source);}
    public static double similarity(long[] queryHashes,long candidateHash){if(queryHashes==null||queryHashes.length==0)return 0d;int best=64;for(long q:queryHashes)best=Math.min(best,Long.bitCount(q^candidateHash));return Math.max(0d,1d-best/64d);}

    /** Full frame + progressively tighter center crops approximate a lightweight cover detector. */
    private static long[] queryHashes(Bitmap source){List<Long> hashes=new ArrayList<>();hashes.add(dHash(source));for(float keep:new float[]{.92f,.82f,.72f}){Bitmap crop=centerCrop(source,keep);hashes.add(dHash(crop));if(crop!=source)crop.recycle();}long[] out=new long[hashes.size()];for(int i=0;i<out.length;i++)out[i]=hashes.get(i);return out;}
    private static Bitmap centerCrop(Bitmap b,float keep){int w=b.getWidth(),h=b.getHeight();int nw=Math.max(16,(int)(w*keep)),nh=Math.max(16,(int)(h*keep));int x=Math.max(0,(w-nw)/2),y=Math.max(0,(h-nh)/2);try{return Bitmap.createBitmap(b,x,y,Math.min(nw,w-x),Math.min(nh,h-y));}catch(Exception e){return b;}}
    private static long dHash(Bitmap source){Bitmap small=Bitmap.createScaledBitmap(source,9,8,true);long hash=0L;for(int y=0;y<8;y++)for(int x=0;x<8;x++){int a=gray(small.getPixel(x,y)),b=gray(small.getPixel(x+1,y));hash=(hash<<1)|(a>b?1L:0L);}if(small!=source)small.recycle();return hash;}
    private static int gray(int c){return (android.graphics.Color.red(c)*30+android.graphics.Color.green(c)*59+android.graphics.Color.blue(c)*11)/100;}
}
