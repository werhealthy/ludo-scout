package it.vintedaffari.app;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Bounded process-local DB timing. The exporter never uses SQLite or an application monitor.
 * HELPER_CALL plus BLOCKED is evidence of a Java monitor wait; it is not an exact lock timer.
 * ACQUIRE_WRITER includes Android connection-pool/SQLite begin time, not only OS lock time. */
public final class DbContentionTrace {
    private static final int LIMIT=32, MAX_TEXT=32768;
    private static final Object REGISTRY=new Object();
    private static final Map<Scope,Boolean> ACTIVE=new LinkedHashMap<>();
    private static final Map<String,Stats> STATS=new LinkedHashMap<>();
    private static volatile String process="unconfigured",version="unknown",exportError="";
    private static volatile int pid;
    private static boolean configured;
    private DbContentionTrace(){}

    public static synchronized void configure(File files,String processName,String build,int processId){
        if(configured)return;
        process=clean(processName);version=clean(build);pid=processId;
        File directory=new File(files,"db_contention");
        String slot=process.endsWith(":radar")?"radar":process.endsWith(":ui")?"ui":"queue";
        configured=true;
        Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"ludo-db-trace-export");t.setDaemon(true);return t;})
                .scheduleWithFixedDelay(new Runnable(){
                    private boolean first=true;
                    public void run(){
                        if(first){first=false;preservePrevious(directory,slot);}
                        export(directory,slot);
                    }
                },0,5,TimeUnit.SECONDS);
    }

    public static Scope start(String operation){
        Scope scope=new Scope(clean(operation));
        synchronized(REGISTRY){if(ACTIVE.size()<LIMIT){ACTIVE.put(scope,Boolean.TRUE);scope.tracked=true;}}
        return scope;
    }

    public static final class Scope implements AutoCloseable {
        final String operation;final Thread thread=Thread.currentThread();
        final long started=System.nanoTime();
        private String phase="SETUP",error="";
        private long phaseAt=started,openNs,waitNs,bodyNs,commitNs,helperNs;
        private boolean closed,tracked;
        private Scope(String operation){this.operation=operation;}
        public synchronized void phase(String next){
            if(closed)return;account(System.nanoTime());phase=next;
        }
        private void account(long now){
            long duration=Math.max(0,now-phaseAt);
            switch(phase){
                case "OPEN_DATABASE":openNs+=duration;break;
                case "ACQUIRE_WRITER":waitNs+=duration;break;
                case "TRANSACTION":bodyNs+=duration;break;
                case "COMMIT":commitNs+=duration;break;
                case "HELPER_CALL":helperNs+=duration;break;
                default:break;
            }
            phaseAt=now;
        }
        public synchronized void failed(Throwable failure){error=failure==null?"unknown":failure.getClass().getSimpleName();}
        private synchronized String activeLine(){
            String frames="";
            if(System.nanoTime()-started>=250_000_000L){
                StackTraceElement[] stack=thread.getStackTrace();
                for(int i=0;i<Math.min(5,stack.length);i++)frames+=(i==0?"":"|")+clean(stack[i].getClassName()+"."+stack[i].getMethodName());
            }
            return "active={op="+operation+";thread="+clean(thread.getName())+";threadId="+thread.getId()+
                    ";state="+thread.getState()+";phase="+phase+";phaseAgeMs="+ms(System.nanoTime()-phaseAt)+
                    ";ageMs="+ms(System.nanoTime()-started)+";frames="+frames+"}";
        }
        @Override public void close(){
            long total;String failure;
            synchronized(this){if(closed)return;account(System.nanoTime());closed=true;total=System.nanoTime()-started;failure=error;}
            synchronized(REGISTRY){
                if(tracked)ACTIVE.remove(this);
                Stats stats=STATS.get(operation);
                if(stats==null&&STATS.size()<LIMIT){stats=new Stats();STATS.put(operation,stats);}
                if(stats!=null){stats.count++;if(!failure.isEmpty())stats.failures++;
                    stats.totalMax=Math.max(stats.totalMax,total);stats.openMax=Math.max(stats.openMax,openNs);
                    stats.waitMax=Math.max(stats.waitMax,waitNs);stats.bodyMax=Math.max(stats.bodyMax,bodyNs);
                    stats.commitMax=Math.max(stats.commitMax,commitNs);stats.helperMax=Math.max(stats.helperMax,helperNs);
                    if(!failure.isEmpty())stats.lastError=failure;
                }
            }
        }
    }
    private static final class Stats {
        long count,failures,totalMax,openMax,waitMax,bodyMax,commitMax,helperMax;String lastError="";
        String line(String operation){return "completed={op="+operation+";count="+count+";failures="+failures+
                ";maxTotalMs="+ms(totalMax)+";maxOpenMs="+ms(openMax)+";maxBeginMs="+ms(waitMax)+
                ";maxBodyMs="+ms(bodyMax)+";maxCommitMs="+ms(commitMax)+";maxHelperCallMs="+ms(helperMax)+
                ";lastError="+lastError+"}";}
    }
    public static String snapshot(){
        StringBuilder out=new StringBuilder("build=db-contention-v1;process="+process+";pid="+pid+";version="+version+
                ";at="+System.currentTimeMillis()+";exportError="+exportError+";scope=process-lifetime;limits=32\n");
        ArrayList<Scope> active;
        synchronized(REGISTRY){active=new ArrayList<>(ACTIVE.keySet());for(Map.Entry<String,Stats> e:STATS.entrySet())out.append(e.getValue().line(e.getKey())).append('\n');}
        for(Scope scope:active)out.append(scope.activeLine()).append('\n');
        return out.substring(0,Math.min(MAX_TEXT,out.length()));
    }
    private static void preservePrevious(File directory,String slot){
        try{
            File latest=new File(directory,slot+".txt");
            if(latest.isFile()&&latest.length()<=MAX_TEXT)Files.copy(latest.toPath(),new File(directory,slot+".previous.txt").toPath(),StandardCopyOption.REPLACE_EXISTING);
        }catch(Exception failure){exportError=failure.getClass().getSimpleName();}
    }
    private static void export(File directory,String slot){
        try{
            Files.createDirectories(directory.toPath());
            File temp=new File(directory,slot+".tmp"),target=new File(directory,slot+".txt");
            Files.write(temp.toPath(),snapshot().getBytes(StandardCharsets.UTF_8));
            Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            exportError="";
        }catch(Exception failure){exportError=failure.getClass().getSimpleName();}
    }
    /** Called only by the existing background diagnostics exporter. Never probes the database. */
    public static String readSummaries(File files){
        StringBuilder out=new StringBuilder();
        for(String slot:new String[]{"queue","radar","ui","queue.previous","radar.previous","ui.previous"}){
            File file=new File(new File(files,"db_contention"),slot+".txt");
            try{
                if(!file.isFile()){if(!slot.endsWith(".previous"))out.append(slot).append("={unavailable}\n");continue;}
                if(file.length()>MAX_TEXT){out.append(slot).append("={invalid-size}\n");continue;}
                String text=new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);
                out.append("file={slot=").append(slot).append(";ageMs=").append(Math.max(0,System.currentTimeMillis()-file.lastModified())).append("}\n").append(text);
            }catch(Exception failure){out.append(slot).append("={readError=").append(failure.getClass().getSimpleName()).append("}\n");}
        }
        return out.toString();
    }
    private static long ms(long nanos){return Math.max(0,nanos)/1_000_000L;}
    private static String clean(String value){
        if(value==null)return "unknown";String s=value.replaceAll("[^a-zA-Z0-9_.:-]","_");return s.substring(0,Math.min(100,s.length()));
    }
}
