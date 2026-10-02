"""Run the real summary method against truncated Android history after reboot.
The platform boundary is replaced; installation classification remains production code.
"""
from pathlib import Path
import subprocess, tempfile
root=Path(__file__).resolve().parents[1]
src=(root/'app/src/main/java/it/vintedaffari/app/ProcessCrashJournal.java').read_text()
start=src.index('    public static String systemExitSummary(Context context,long since)')
brace=src.index('{',start);depth=0
for i in range(brace,len(src)):
 if src[i]=='{':depth+=1
 elif src[i]=='}':
  depth-=1
  if depth==0:method=src[start:i+1];break
harness=r'''
import java.util.*;
public class ExitBoundaryTest {
 static class Build {static class VERSION {static int SDK_INT=35;}}
 static class BuildConfig {static String VERSION_NAME="test-build";}
 static class TextUtils {static boolean isEmpty(String s){return s==null||s.isEmpty();}}
 static class PackageInfo {long lastUpdateTime;}
 static class PackageManager {long updated;boolean fail;PackageInfo getPackageInfo(String p,int flags){if(fail)throw new IllegalStateException();PackageInfo i=new PackageInfo();i.lastUpdateTime=updated;return i;}}
 static class Context {static String ACTIVITY_SERVICE="activity";PackageManager pm=new PackageManager();ActivityManager am=new ActivityManager();String getPackageName(){return "app";}Object getSystemService(String s){return am;}PackageManager getPackageManager(){return pm;}}
 static class ActivityManager {List<ApplicationExitInfo> exits=new ArrayList<>();List<ApplicationExitInfo> getHistoricalProcessExitReasons(String p,int pid,int limit){return exits;}}
 static class ApplicationExitInfo {
  static final int REASON_PACKAGE_UPDATED=1,REASON_CRASH=2,REASON_CRASH_NATIVE=3,REASON_ANR=4,REASON_LOW_MEMORY=5,REASON_EXCESSIVE_RESOURCE_USAGE=6;
  long at;int why;String process;ApplicationExitInfo(long a,int w,String p){at=a;why=w;process=p;}
  String getProcessName(){return process;}int getReason(){return why;}long getTimestamp(){return at;}String getDescription(){return "";}long getPss(){return 0;}long getRss(){return 0;}int getImportance(){return 0;}int getStatus(){return 0;}
 }
 static String clean(String s){return s;}static String cleanLong(String s,int max){return s;}static String reasonName(int r){return String.valueOf(r);}
 static void check(boolean ok,String m){if(!ok)throw new AssertionError(m);}
 public static void main(String[] args){
  Context c=new Context();c.pm.updated=2000;
  c.am.exits.add(new ApplicationExitInfo(1000,1,"app"));c.am.exits.add(new ApplicationExitInfo(1500,5,"app:ui"));c.am.exits.add(new ApplicationExitInfo(2500,4,"app:radar"));
  c.am.exits.add(new ApplicationExitInfo(3000,2,"webview"));
  String s=systemExitSummary(c,500);
  check(s.contains("installBoundary=2000;")&&s.contains("memoryAfterInstall=0;")&&s.contains("anrAfterInstall=1;"),"truncated exit history misclassified old memory exit: "+s);
  check(s.contains("boundarySource=package-last-update;")&&s.contains("crashAfterInstall=0;"),"provenance/process filtering lost: "+s);
  c.pm.fail=true;s=systemExitSummary(c,500);
  check(s.contains("boundarySource=exit-history-fallback;")&&s.contains("installBoundary=1000;"),"fallback not explicit: "+s);
  c.am.exits.clear();s=systemExitSummary(c,500);
  check(s.contains("boundarySource=engine-epoch-fallback;")&&s.contains("installBoundary=500;"),"missing boundary not explicit: "+s);
  System.out.println("PASS real exit summary truncated-history, install boundary, provenance and process scope");
 }
 __PRODUCTION__
}
'''.replace('__PRODUCTION__',method)
with tempfile.TemporaryDirectory() as out:
 p=Path(out)/'ExitBoundaryTest.java';p.write_text(harness)
 subprocess.run(['javac','-d',out,str(p)],check=True)
 subprocess.run(['java','-cp',out,'ExitBoundaryTest'],check=True)
