"""Run the production startup entry point with controlled Android/SQLite boundaries.

Regression caught: a missing/false/stale legacy flag must never invoke the
destructive reset. This is a JVM boundary test, not a real SQLite/device test.
"""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/java/it/vintedaffari/app/EngineStartupMaintenance.java'
STUBS = {
    'android/content/SharedPreferences.java': '''package android.content;
public interface SharedPreferences {
 boolean getBoolean(String key, boolean fallback);
 Editor edit();
 interface Editor {
  Editor putBoolean(String key, boolean value);
  Editor putLong(String key, long value);
  Editor putInt(String key, int value);
  Editor putString(String key, String value);
  Editor remove(String key);
  void apply();
 }
}''',
    'android/content/Context.java': '''package android.content;
public abstract class Context {
 public static final int MODE_PRIVATE=0;
 public abstract SharedPreferences getSharedPreferences(String name,int mode);
 public String getPackageName(){return "it.vintedaffari.app";}
 public void sendBroadcast(Intent intent){}
}''',
    'android/content/Intent.java': '''package android.content;
public class Intent {
 public Intent(String action){}
 public Intent setPackage(String name){return this;}
}''',
    'it/vintedaffari/app/OperationCenter.java': '''package it.vintedaffari.app;
final class OperationCenter {
 static final String PREFS="operations",CHANGED="changed";
}''',
    'it/vintedaffari/app/MarketStore.java': '''package it.vintedaffari.app;
final class MarketStore {
 int resetCalls,epochCalls;
 static final class FreshStartSummary {
  int jobsRemoved,listingsArchived,dealsArchived,gamesHidden;
 }
 static final class OperationalEpochSummary {long epochAt=1789911424073L;}
 FreshStartSummary freshStartLegacyBacklog(long cutoff){
  resetCalls++;
  throw new AssertionError("Destructive reset reached from startup");
 }
 OperationalEpochSummary startOperationalEpochIfMissing(){
  epochCalls++;return new OperationalEpochSummary();
 }
 int archiveAutomaticReviewDebtBefore(long cutoff){return 0;}
 int autoHideStrongNonGameListings(){return 0;}
 int quarantineLegacyBggReviewBacklog(){return 0;}
 int repairV51125CollisionCleanup(){return 0;}
 int compactVintedBacklogToLiveLane(){return 0;}
}''',
    'it/vintedaffari/app/StartupResetRegression.java': '''package it.vintedaffari.app;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;

public final class StartupResetRegression {
 static final class Preferences implements SharedPreferences,SharedPreferences.Editor {
  final Map<String,Object> values=new HashMap<>();
  public boolean getBoolean(String key,boolean fallback){
   Object value=values.get(key);return value instanceof Boolean?(Boolean)value:fallback;
  }
  public SharedPreferences.Editor edit(){return this;}
  public SharedPreferences.Editor putBoolean(String key,boolean value){values.put(key,value);return this;}
  public SharedPreferences.Editor putLong(String key,long value){values.put(key,value);return this;}
  public SharedPreferences.Editor putInt(String key,int value){values.put(key,value);return this;}
  public SharedPreferences.Editor putString(String key,String value){values.put(key,value);return this;}
  public SharedPreferences.Editor remove(String key){values.remove(key);return this;}
  public void apply(){}
 }
 static final class TestContext extends Context {
  final Map<String,Preferences> files=new HashMap<>();
  public Preferences getSharedPreferences(String name,int mode){
   return files.computeIfAbsent(name,key->new Preferences());
  }
 }
 static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static void scenario(Boolean legacyFlag) {
  TestContext context=new TestContext();
  Preferences prefs=context.getSharedPreferences("va_v3_diag",0);
  // Unrelated existing cutovers have already run on the installed beta.
  for(String key:new String[]{"v51216OperationalEpochApplied","v51221ReviewTurnaroundApplied",
    "v51221ProductNoiseSweepApplied","v51124ReviewBacklogReset","v51125CollisionCleanup",
    "v51126CollisionRepair","v51126MarketMedian","v51268SafeModePricing","v51125VintedLiveLane"})
   prefs.putBoolean(key,true);
  if(legacyFlag!=null)prefs.putBoolean("v5121FreshStartApplied",legacyFlag);
  prefs.putLong("v5121FreshStartAt",1790938327666L);
  prefs.putString("v5121FreshSummary","jobs=1488, observations=3269");
  Preferences operations=context.getSharedPreferences(OperationCenter.PREFS,0);
  operations.putString("tasks","existing user tasks");
  Map<String,Object> before=new HashMap<>(prefs.values);
  MarketStore market=new MarketStore();
  EngineStartupMaintenance.run(context,market);
  EngineStartupMaintenance.run(context,market);
  require(market.resetCalls==0,"legacy flag="+legacyFlag+": reset reached "+market.resetCalls+" times");
  require(market.epochCalls==2,"existing operational epoch maintenance must continue");
  require(prefs.values.equals(before),"startup changed historical reset evidence/preferences");
  require("existing user tasks".equals(operations.values.get("tasks")),"startup erased tasks");
  // Simulate only the old cross-process flag becoming stale after a successful start.
  prefs.putBoolean("v5121FreshStartApplied",false);
  EngineStartupMaintenance.run(context,market);
  require(market.resetCalls==0,"stale legacy flag reactivated destructive reset");
  require(market.epochCalls==3,"repeat startup skipped existing epoch maintenance");
  require(Long.valueOf(1790938327666L).equals(prefs.values.get("v5121FreshStartAt")),"reset timestamp changed");
  require("jobs=1488, observations=3269".equals(prefs.values.get("v5121FreshSummary")),"reset summary changed");
  require("existing user tasks".equals(operations.values.get("tasks")),"stale flag erased tasks");
  System.out.println("PASS startup preserves state with legacy flag="+legacyFlag+" and repeated/stale startup");
 }
 public static void main(String[] args){scenario(null);scenario(false);scenario(true);}
}''',
}

with tempfile.TemporaryDirectory() as directory:
    output = Path(directory)
    sources = [str(SOURCE)]
    for relative, content in STUBS.items():
        path = output / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding='utf-8')
        sources.append(str(path))
    subprocess.run(['javac', '-d', str(output), *sources], check=True)
    subprocess.run(['java', '-cp', str(output),
                    'it.vintedaffari.app.StartupResetRegression'], check=True)
