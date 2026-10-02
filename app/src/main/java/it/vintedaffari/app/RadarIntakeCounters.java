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
    static final String[][] LAST_GROUPS={{"eventAt","eventType"},{"scanAt","lastCardsParsed"},{"localAnalysisLastBatchAt","localAnalysisLastBatchSize"}};
    private final Map<String,Long> values=new LinkedHashMap<>();
    private final Map<String,String> sources=new LinkedHashMap<>();
    private final Object diskLock=new Object();
    private boolean ready;private long epoch;private String error="";
    RadarIntakeCounters(){for(String key:KEYS)values.put(key,0L);for(String[] group:LAST_GROUPS){for(String key:group)values.put(key,0L);sources.put(group[0],"unknown");}}
    synchronized long add(String key,long delta){
        if(!java.util.Arrays.asList(KEYS).contains(key)||delta<0)throw new IllegalArgumentException(key);
        long n=values.get(key);n=delta>Long.MAX_VALUE-n?Long.MAX_VALUE:n+delta;values.put(key,n);return n;
    }
    synchronized long get(String key){return values.getOrDefault(key,0L);}
    synchronized boolean ready(){return ready;}
    synchronized String metadata(){return "source=radar-owner-file;counterEpoch="+epoch+";counterReady="+ready+";counterError="+error+";flushWindowMs=2000;freshnessSchema=1;eventSource="+sources.get("eventAt")+";scanSource="+sources.get("scanAt")+";analysisSource="+sources.get("localAnalysisLastBatchAt");}
    synchronized void recordEvent(long at,int type){recordLast(LAST_GROUPS[0],at,type);}
    synchronized void recordScan(long at,int count){recordLast(LAST_GROUPS[1],at,count);}
    synchronized void recordAnalysis(long at,int count){recordLast(LAST_GROUPS[2],at,count);}
    private void recordLast(String[] group,long at,long value){
        if(at>0&&value>=0&&at>=values.get(group[0])){values.put(group[0],at);values.put(group[1],value);sources.put(group[0],"live-owner");}
    }
    synchronized String diagnosticPayload(){
        StringBuilder out=new StringBuilder(metadata());for(Map.Entry<String,Long> e:values.entrySet())out.append(';').append(e.getKey()).append('=').append(e.getValue());return out.toString();
    }
    void initialize(File file,Map<String,Long> seed,long now){
        synchronized(diskLock){
        synchronized(this){if(ready)return;}
        Properties disk=new Properties();String failure="";
        try{if(file.isFile()){if(file.length()>16384)throw new IOException("oversize");try(FileInputStream in=new FileInputStream(file)){disk.load(in);}}}
        catch(IOException|IllegalArgumentException e){failure=e.getClass().getSimpleName();}
        synchronized(this){
            if(ready)return;
            epoch=number(disk.getProperty("epoch"),now);if(epoch<=0)epoch=now;
            for(String key:KEYS){long baseline=Math.max(number(disk.getProperty(key),0L),Math.max(0L,seed.getOrDefault(key,0L)));long delta=values.get(key);values.put(key,delta>Long.MAX_VALUE-baseline?Long.MAX_VALUE:baseline+delta);}
            for(String[] group:LAST_GROUPS){
                long diskAt=number(disk.getProperty(group[0]),0L),seedAt=Math.max(0L,seed.getOrDefault(group[0],0L));
                long chosen=Math.max(diskAt,seedAt);
                // A new callback can arrive while initialization is doing disk/SQLite reads.
                if(chosen>values.get(group[0])){
                    boolean fromDisk=diskAt>=seedAt;values.put(group[0],chosen);
                    values.put(group[1],fromDisk?number(disk.getProperty(group[1]),0L):Math.max(0L,seed.getOrDefault(group[1],0L)));
                    sources.put(group[0],fromDisk?safeSource(disk.getProperty(group[0]+"Source")):"sqlite-migration-unverified");
                }
            }
            error=failure;ready=true;
        }
        }
    }
    void persist(File file){
        synchronized(diskLock){
        Properties copy=new Properties();
        synchronized(this){if(!ready)return;copy.setProperty("epoch",String.valueOf(epoch));for(Map.Entry<String,Long> e:values.entrySet())copy.setProperty(e.getKey(),String.valueOf(e.getValue()));for(Map.Entry<String,String> e:sources.entrySet())copy.setProperty(e.getKey()+"Source",e.getValue());}
        try{
            File parent=file.getParentFile();if(parent!=null&&!parent.isDirectory()&&!parent.mkdirs())throw new IOException("mkdir");
            File tmp=new File(file.getPath()+".tmp");
            try(FileOutputStream out=new FileOutputStream(tmp)){copy.store(out,"radar-intake-v2");out.flush();}
            if(!tmp.renameTo(file))throw new IOException("rename");
            synchronized(this){error="";}
        }catch(IOException e){synchronized(this){error=e.getClass().getSimpleName();}}
        }
    }
    private static long number(String s,long fallback){try{return Math.max(0L,Long.parseLong(s));}catch(Exception e){return fallback;}}
    private static String safeSource(String s){return "live-owner".equals(s)||"sqlite-migration-unverified".equals(s)?s:"unknown";}
}
