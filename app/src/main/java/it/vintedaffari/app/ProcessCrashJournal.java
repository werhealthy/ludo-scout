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
            try{record(app,process,thread,error);}catch(Throwable ignored){}
            if(previous!=null)previous.uncaughtException(thread,error);
        });
    }

    private static void record(Context context,String process,Thread thread,Throwable error)throws Exception{
        File dir=new File(context.getFilesDir(),"process_crash_journal");if(!dir.exists())dir.mkdirs();
        File out=new File(dir,"crash-"+process+".txt");
        StringWriter sw=new StringWriter();if(error!=null)error.printStackTrace(new PrintWriter(sw));
        String stack=sw.toString();if(stack.length()>7000)stack=stack.substring(0,7000);
        String body="at="+System.currentTimeMillis()+"\nprocess="+safe(Application.getProcessName())+"\nthread="+safe(thread==null?"":thread.getName())+
                "\nerror="+safe(error==null?"":error.getClass().getName()+": "+String.valueOf(error.getMessage()))+"\nstack="+stack;
        try(FileOutputStream fos=new FileOutputStream(out,false)){fos.write(body.getBytes(StandardCharsets.UTF_8));fos.flush();try{fos.getFD().sync();}catch(Throwable ignored){}}
    }

    public static String fileSummary(Context context){
        try{
            File dir=new File(context.getFilesDir(),"process_crash_journal");File[] files=dir.listFiles((d,n)->n.startsWith("crash-")&&n.endsWith(".txt"));
            if(files==null||files.length==0)return "build=process-crash-v1;count=0";
            long latestAt=0;String latestProcess="",latestError="";int count=0;
            for(File f:files){
                long at=0;String process="",error="";
                try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8))){
                    String line;while((line=r.readLine())!=null){
                        if(line.startsWith("at="))try{at=Long.parseLong(line.substring(3));}catch(Throwable ignored){}
                        else if(line.startsWith("process="))process=line.substring(8);
                        else if(line.startsWith("error="))error=line.substring(6);
                        if(at>0&&!TextUtils.isEmpty(process)&&!TextUtils.isEmpty(error))break;
                    }
                }catch(Throwable ignored){}
                if(at>0){count++;if(at>latestAt){latestAt=at;latestProcess=process;latestError=error;}}
            }
            return "build=process-crash-v1;count="+count+";latestAt="+latestAt+";latestProcess="+clean(latestProcess)+";latest="+clean(latestError);
        }catch(Throwable t){return "build=process-crash-v1;error="+clean(String.valueOf(t));}
    }

    public static String systemExitSummary(Context context){
        if(Build.VERSION.SDK_INT<30)return "build=system-exit-v1;unsupportedApi="+Build.VERSION.SDK_INT;
        try{
            ActivityManager am=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ApplicationExitInfo> exits=am==null?null:am.getHistoricalProcessExitReasons(null,0,16);
            long now=System.currentTimeMillis(),day=24L*60L*60_000L,latestAt=0;int crash24=0,anr24=0,lowMem24=0,other24=0;String process="",reason="",description="";
            if(exits!=null)for(ApplicationExitInfo e:exits){
                long at=e.getTimestamp();int why=e.getReason();
                if(now-at<=day){
                    if(why==ApplicationExitInfo.REASON_CRASH||why==ApplicationExitInfo.REASON_CRASH_NATIVE)crash24++;
                    else if(why==ApplicationExitInfo.REASON_ANR)anr24++;
                    else if(why==ApplicationExitInfo.REASON_LOW_MEMORY||why==ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE||why==ApplicationExitInfo.REASON_MEMORY_LIMITER)lowMem24++;
                    else other24++;
                }
                if(at>latestAt){latestAt=at;process=e.getProcessName();reason=reasonName(why);description=e.getDescription();}
            }
            return "build=system-exit-v1;latestAt="+latestAt+";latestProcess="+clean(process)+";latestReason="+reason+";latestDescription="+clean(description)+
                    ";crash24h="+crash24+";anr24h="+anr24+";memory24h="+lowMem24+";other24h="+other24;
        }catch(Throwable t){return "build=system-exit-v1;error="+clean(String.valueOf(t));}
    }

    private static String reasonName(int r){
        if(Build.VERSION.SDK_INT<30)return String.valueOf(r);
        switch(r){
            case ApplicationExitInfo.REASON_CRASH:return"CRASH";
            case ApplicationExitInfo.REASON_CRASH_NATIVE:return"CRASH_NATIVE";
            case ApplicationExitInfo.REASON_ANR:return"ANR";
            case ApplicationExitInfo.REASON_LOW_MEMORY:return"LOW_MEMORY";
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE:return"EXCESSIVE_RESOURCE";
            case ApplicationExitInfo.REASON_MEMORY_LIMITER:return"MEMORY_LIMITER";
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
}
