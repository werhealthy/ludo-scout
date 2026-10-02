package it.vintedaffari.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Radar-owned cumulative counters. No SharedPreferences cache can overwrite this file.
 * Disk work is called only by the diagnostic executor; main-thread increments use memory only. */
final class RadarIntakeCounters {
    static final String[] KEYS={"vintedEvents","scans","cardsParsedTotal","analysisBatches","analysesStored","classifierBlocked","nonGameRejected","analysisCommitted","analysisQuarantined"};
    private final Map<String,Long> values=new LinkedHashMap<>();
    private boolean ready;private long epoch;private String error="";
    RadarIntakeCounters(){for(String key:KEYS)values.put(key,0L);}
    synchronized long add(String key,long delta){
        if(!values.containsKey(key)||delta<0)throw new IllegalArgumentException(key);
        long n=values.get(key);n=delta>Long.MAX_VALUE-n?Long.MAX_VALUE:n+delta;values.put(key,n);return n;
    }
    synchronized long get(String key){return values.getOrDefault(key,0L);}
    synchronized boolean ready(){return ready;}
    synchronized String metadata(){return "source=radar-owner-file;counterEpoch="+epoch+";counterReady="+ready+";counterError="+error+";flushWindowMs=2000";}
    void initialize(File file,Map<String,Long> seed,long now){
        Properties disk=new Properties();String failure="";
        try{if(file.isFile()){if(file.length()>16384)throw new IOException("oversize");try(FileInputStream in=new FileInputStream(file)){disk.load(in);}}}
        catch(IOException|IllegalArgumentException e){failure=e.getClass().getSimpleName();}
        synchronized(this){
            if(ready)return;
            epoch=number(disk.getProperty("epoch"),now);if(epoch<=0)epoch=now;
            for(String key:KEYS){long baseline=Math.max(number(disk.getProperty(key),0L),Math.max(0L,seed.getOrDefault(key,0L)));long delta=values.get(key);values.put(key,delta>Long.MAX_VALUE-baseline?Long.MAX_VALUE:baseline+delta);}
            error=failure;ready=true;
        }
    }
    void persist(File file){
        Properties copy=new Properties();
        synchronized(this){if(!ready)return;copy.setProperty("epoch",String.valueOf(epoch));for(String key:KEYS)copy.setProperty(key,String.valueOf(values.get(key)));}
        try{
            File parent=file.getParentFile();if(parent!=null&&!parent.isDirectory()&&!parent.mkdirs())throw new IOException("mkdir");
            File tmp=new File(file.getPath()+".tmp");
            try(FileOutputStream out=new FileOutputStream(tmp)){copy.store(out,"radar-intake-v2");out.flush();}
            if(!tmp.renameTo(file))throw new IOException("rename");
            synchronized(this){error="";}
        }catch(IOException e){synchronized(this){error=e.getClass().getSimpleName();}}
    }
    private static long number(String s,long fallback){try{return Math.max(0L,Long.parseLong(s));}catch(Exception e){return fallback;}}
}
