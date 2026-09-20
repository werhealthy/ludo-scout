package it.vintedaffari.app;

import android.app.ActivityManager;
import android.app.Application;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** Process-wide crash/exit diagnostics. Installed from Application so :ui, :radar and the
 * default queue process are all covered. The file journal is deliberately separate per process
 * to avoid multi-process SharedPreferences cache ambiguity during a crash. */
public final class ProcessCrashJournal {
    private static volatile boolean installed=false;
    private ProcessCrashJournal(){}

    public static synchronized void install(Context context){
        if(installed||context==null)return;installed=true;
        final Context app=context.getApplicationContext();
        final String process=safeProcess(Application.getProcessName());
        final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            try{record(app,process,thread,error,"UNCAUGHT");}catch(Throwable ignored){}
            if(previous!=null)previous.uncaughtException(thread,error);
        });
    }

    public static void recordHandled(Context context,String phase,Throwable error){
        if(context==null||error==null)return;
        try{record(context.getApplicationContext(),safeProcess(Application.getProcessName()),Thread.currentThread(),error,TextUtils.isEmpty(phase)?"HANDLED":phase);}catch(Throwable ignored){}
    }

    private static void record(Context context,String process,Thread thread,Throwable error,String phase)throws Exception{
        File dir=new File(context.getFilesDir(),"process_crash_journal");if(!dir.exists())dir.mkdirs();
        boolean uncaught="UNCAUGHT".equals(phase);
        File out=uncaught?new File(dir,"crash-"+process+".txt"):new File(dir,"handled-"+process+".txt");
        Throwable root=error;while(root!=null&&root.getCause()!=null&&root.getCause()!=root)root=root.getCause();
        // Persist a small header before formatting the stack. If the fatal error is OOM/heap pressure,
        // StringWriter/stack formatting may itself fail; the timestamp/process/root still survives.
        String header="at="+System.currentTimeMillis()+"\nprocess="+safe(Application.getProcessName())+"\nthread="+safe(thread==null?"":thread.getName())+
                "\nphase="+safe(phase)+"\nerror="+safe(error==null?"":error.getClass().getName()+": "+String.valueOf(error.getMessage()))+
                "\nroot="+safe(root==null?"":root.getClass().getName()+": "+String.valueOf(root.getMessage()));
        try(FileOutputStream fos=new FileOutputStream(out,false)){
            fos.write(header.getBytes(StandardCharsets.UTF_8));fos.flush();try{fos.getFD().sync();}catch(Throwable ignored){}
            try{
                StringWriter sw=new StringWriter();if(error!=null)error.printStackTrace(new PrintWriter(sw));
                String stack=sw.toString();if(stack.length()>7000)stack=stack.substring(0,7000);
                fos.write(("\nstack="+stack).getBytes(StandardCharsets.UTF_8));fos.flush();try{fos.getFD().sync();}catch(Throwable ignored){}
            }catch(Throwable ignored){}
        }
    }

    public static String fileSummary(Context context){
        try{
            File dir=new File(context.getFilesDir(),"process_crash_journal");File[] files=dir.listFiles((d,n)->(n.startsWith("crash-")||n.startsWith("handled-"))&&n.endsWith(".txt"));
            if(files==null||files.length==0)return "build=process-crash-v2;count=0";
            long latestAt=0;String latestProcess="",latestError="",latestRoot="",latestPhase="",latestKind="";int count=0;
            for(File f:files){
                long at=0;String process="",error="",root="",phase="";
                try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8))){
                    String line;while((line=r.readLine())!=null){
                        if(line.startsWith("at="))try{at=Long.parseLong(line.substring(3));}catch(Throwable ignored){}
                        else if(line.startsWith("process="))process=line.substring(8);
                        else if(line.startsWith("phase="))phase=line.substring(6);
                        else if(line.startsWith("error="))error=line.substring(6);
                        else if(line.startsWith("root="))root=line.substring(5);
                        if(at>0&&!TextUtils.isEmpty(process)&&!TextUtils.isEmpty(error)&&!TextUtils.isEmpty(root))break;
                    }
                }catch(Throwable ignored){}
                if(at>0){count++;if(at>latestAt){latestAt=at;latestProcess=process;latestError=error;latestRoot=root;latestPhase=phase;latestKind=f.getName().startsWith("crash-")?"UNCAUGHT":"HANDLED";}}
            }
            return "build=process-crash-v2;count="+count+";latestAt="+latestAt+";latestProcess="+clean(latestProcess)+";kind="+latestKind+";phase="+clean(latestPhase)+";latest="+clean(latestError)+";root="+clean(latestRoot);
        }catch(Throwable t){return "build=process-crash-v2;error="+clean(String.valueOf(t));}
    }

    public static String systemExitSummary(Context context){return systemExitSummary(context,0L);}

    /** Android exit history is UID-wide and can include WebView sandbox processes. Product stability
     * only cares about Ludo's default/:ui/:radar processes, and the epoch boundary lets diagnostics
     * distinguish fresh failures from historical noise. */
    public static String systemExitSummary(Context context,long since){
        if(Build.VERSION.SDK_INT<30)return "build=system-exit-v3;unsupportedApi="+Build.VERSION.SDK_INT;
        try{
            ActivityManager am=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ApplicationExitInfo> exits=am==null?null:am.getHistoricalProcessExitReasons(null,0,32);
            long now=System.currentTimeMillis(),day=24L*60L*60_000L,latestAt=0;int crash24=0,anr24=0,lowMem24=0,other24=0,crashSince=0,anrSince=0,lowMemSince=0,otherSince=0;
            long latestPss=0,latestRss=0;int latestImportance=0,latestStatus=0;String process="",reason="",description="";ApplicationExitInfo latestExit=null;StringBuilder recent=new StringBuilder();int recentCount=0;
            String pkg=context.getPackageName();
            if(exits!=null)for(ApplicationExitInfo e:exits){
                String pn=e.getProcessName();if(TextUtils.isEmpty(pn)||!(pn.equals(pkg)||pn.startsWith(pkg+":")))continue;
                long at=e.getTimestamp();int why=e.getReason();
                if(now-at<=day){
                    if(why==ApplicationExitInfo.REASON_CRASH||why==ApplicationExitInfo.REASON_CRASH_NATIVE)crash24++;
                    else if(why==ApplicationExitInfo.REASON_ANR)anr24++;
                    else if(why==ApplicationExitInfo.REASON_LOW_MEMORY||why==ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE)lowMem24++;
                    else other24++;
                }
                if(since<=0||at>=since){
                    if(why==ApplicationExitInfo.REASON_CRASH||why==ApplicationExitInfo.REASON_CRASH_NATIVE)crashSince++;
                    else if(why==ApplicationExitInfo.REASON_ANR)anrSince++;
                    else if(why==ApplicationExitInfo.REASON_LOW_MEMORY||why==ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE)lowMemSince++;
                    else otherSince++;
                }
                if(at>latestAt){latestAt=at;process=pn;reason=reasonName(why);description=e.getDescription();latestPss=e.getPss();latestRss=e.getRss();latestImportance=e.getImportance();latestStatus=e.getStatus();latestExit=e;}
                if(recentCount<6){if(recent.length()>0)recent.append("|");recent.append(at).append(",").append(clean(pn)).append(",").append(reasonName(why)).append(",pss=").append(e.getPss()).append(",rss=").append(e.getRss());recentCount++;}
            }
            String trace=latestExit==null?"":mainTraceSnippet(latestExit);
            return "build=system-exit-v3;since="+Math.max(0L,since)+";latestAt="+latestAt+";latestProcess="+clean(process)+";latestReason="+reason+";latestDescription="+clean(description)+
                    ";latestPssKb="+latestPss+";latestRssKb="+latestRss+";latestImportance="+latestImportance+";latestStatus="+latestStatus+
                    ";crashSince="+crashSince+";anrSince="+anrSince+";memorySince="+lowMemSince+";otherSince="+otherSince+
                    ";crash24h="+crash24+";anr24h="+anr24+";memory24h="+lowMem24+";other24h="+other24+";recent="+cleanLong(recent.toString(),900)+";latestMainTrace="+cleanLong(trace,1400);
        }catch(Throwable t){return "build=system-exit-v3;error="+clean(String.valueOf(t));}
    }

    private static String mainTraceSnippet(ApplicationExitInfo exit){
        if(exit==null||Build.VERSION.SDK_INT<30)return "";
        try(java.io.InputStream in=exit.getTraceInputStream()){
            if(in==null)return "";
            BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder();String line;boolean capture=false;int captured=0,scanned=0;
            while((line=r.readLine())!=null&&scanned++<1200){
                if(!capture&&(line.startsWith("\"main\"")||line.contains(" \"main\" "))){capture=true;}
                if(capture){if(out.length()>0)out.append(" | ");out.append(line.trim());if(++captured>=16)break;}
            }
            return out.toString();
        }catch(Throwable ignored){return "";}
    }

    private static String reasonName(int r){
        if(Build.VERSION.SDK_INT<30)return String.valueOf(r);
        switch(r){
            case ApplicationExitInfo.REASON_CRASH:return"CRASH";
            case ApplicationExitInfo.REASON_CRASH_NATIVE:return"CRASH_NATIVE";
            case ApplicationExitInfo.REASON_ANR:return"ANR";
            case ApplicationExitInfo.REASON_LOW_MEMORY:return"LOW_MEMORY";
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE:return"EXCESSIVE_RESOURCE";
            case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE:return"INITIALIZATION_FAILURE";
            case ApplicationExitInfo.REASON_DEPENDENCY_DIED:return"DEPENDENCY_DIED";
            case ApplicationExitInfo.REASON_SIGNALED:return"SIGNALED";
            case ApplicationExitInfo.REASON_USER_REQUESTED:return"USER_REQUESTED";
            case ApplicationExitInfo.REASON_USER_STOPPED:return"USER_STOPPED";
            case ApplicationExitInfo.REASON_PACKAGE_UPDATED:return"PACKAGE_UPDATED";
            case ApplicationExitInfo.REASON_EXIT_SELF:return"EXIT_SELF";
            default:return"OTHER_"+r;
        }
    }
    private static String safeProcess(String s){String x=TextUtils.isEmpty(s)?"unknown":s.replaceAll("[^A-Za-z0-9._-]","_");return x.length()>80?x.substring(0,80):x;}
    private static String safe(String s){return s==null?"":s.replace('\n',' ').replace('\r',' ');}
    private static String clean(String s){String x=safe(s);return x.length()>180?x.substring(0,180):x;}
    private static String cleanLong(String s,int max){String x=safe(s);return x.length()>max?x.substring(0,max):x;}
}
