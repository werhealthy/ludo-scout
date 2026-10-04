package it.vintedaffari.app;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.concurrent.atomic.AtomicReference;
/** Runs only in the test APK, with AI disabled and no provider credentials. */
public final class AiBetaInstrumentation extends Instrumentation {
 @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
 private View find(View v,String text){if(v instanceof TextView&&text.equals(((TextView)v).getText().toString()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View found=find(g.getChildAt(i),text);if(found!=null)return found;}}return null;}
 @Override public void onStart(){Bundle result=new Bundle();Activity a=null;try{
  AiBetaSettings settings=new AiBetaSettings(getTargetContext());org.json.JSONObject saved=new org.json.JSONObject().put("enabled",false).put("last_display","Prova disattivata · nessuna chiamata AI");settings.save(saved);if(settings.load().optBoolean("enabled",true))throw new AssertionError("disabled setting lost");
  if(!new java.io.File(getTargetContext().getNoBackupFilesDir(),"ai-beta.private").isFile())throw new AssertionError("settings not excluded from backup");
  a=startActivitySync(new Intent().setClassName(getTargetContext(),"it.vintedaffari.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final Activity screen=a;AtomicReference<AlertDialog> opened=new AtomicReference<>();runOnMainSync(()->opened.set(AiBetaTestDialog.show(screen)));AlertDialog dialog=opened.get();
  boolean loaded=false;for(int attempt=0;attempt<50;attempt++){waitForIdleSync();AtomicReference<Boolean> seen=new AtomicReference<>(false);runOnMainSync(()->seen.set(find(dialog.getWindow().getDecorView(),"Prova disattivata · nessuna chiamata AI")!=null));if(seen.get()){loaded=true;break;}Thread.sleep(100);}if(!loaded)throw new AssertionError("saved result never loaded");
  runOnMainSync(()->{View root=dialog.getWindow().getDecorView();View run=find(root,"Test AI su 8 annunci");View configure=find(root,"Configura prova");if(run==null||run.isEnabled()||configure==null||!configure.isEnabled())throw new AssertionError("manual default OFF failed");if(run.getHeight()==0||configure.getHeight()==0)throw new AssertionError("controls not laid out");});
  getUiAutomation().waitForIdle(200,5000);Bitmap original=getUiAutomation().takeScreenshot();if(original==null)throw new AssertionError("no screenshot");Bitmap reduced=Bitmap.createScaledBitmap(original,432,Math.round(original.getHeight()*432f/original.getWidth()),true);java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();reduced.compress(Bitmap.CompressFormat.JPEG,88,bytes);String encoded=android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);String size=a.getResources().getConfiguration().fontScale>1.3f?"large_ai":"normal_ai";for(int offset=0,index=0;offset<encoded.length();offset+=3000,index++)android.util.Log.i("LudoVisual","VISUAL "+size+" "+index+" "+encoded.substring(offset,Math.min(offset+3000,encoded.length())));original.recycle();reduced.recycle();
  runOnMainSync(dialog::dismiss);java.lang.reflect.Method visible=AiBetaTestDialog.class.getDeclaredMethod("visible",Activity.class,AlertDialog.class);visible.setAccessible(true);if((Boolean)visible.invoke(null,a,dialog))throw new AssertionError("detached dialog accepted update");
  result.putString("result","AI manual dialog rendered; disabled settings persisted; detached UI rejected; no provider call");finish(Activity.RESULT_OK,result);
 }catch(Throwable failure){android.util.Log.e("LudoVisual","FAIL",failure);result.putString("error",failure.toString());finish(Activity.RESULT_CANCELED,result);}finally{if(a!=null){Activity done=a;runOnMainSync(done::finish);}}}
}
