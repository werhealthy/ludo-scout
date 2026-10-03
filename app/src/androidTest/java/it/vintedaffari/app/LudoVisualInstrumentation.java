package it.vintedaffari.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Actual native views and navigation; no external listings or production test hooks. */
public final class LudoVisualInstrumentation extends Instrumentation {
 @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
 private Object get(Activity a,String name)throws Exception{Field f=a.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(a);}
 private void set(Activity a,String name,Object value)throws Exception{Field f=a.getClass().getDeclaredField(name);f.setAccessible(true);f.set(a,value);}
 private void invoke(Activity a,String name)throws Exception{Method m=a.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(a);}
 private TextView text(View root,String label){
  if(root instanceof TextView && (label.contentEquals(((TextView)root).getText()) || label.equals(root.getContentDescription()==null?null:root.getContentDescription().toString())))return (TextView)root;
  if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){TextView result=text(((ViewGroup)root).getChildAt(i),label);if(result!=null)return result;}
  return null;
 }
 private void capture(Activity a,String name)throws Exception{
  waitForIdleSync();Bitmap original=getUiAutomation().takeScreenshot();if(original==null)throw new AssertionError("no screenshot");
  Bitmap reduced=Bitmap.createScaledBitmap(original,432,Math.round(original.getHeight()*432f/original.getWidth()),true);
  java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();reduced.compress(Bitmap.CompressFormat.JPEG,88,bytes);
  String encoded=android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);
  for(int offset=0,index=0;offset<encoded.length();offset+=3000,index++)android.util.Log.i("LudoVisual","VISUAL "+(a.getResources().getConfiguration().fontScale>1.3f?"large_":"normal_")+name+" "+index+" "+encoded.substring(offset,Math.min(offset+3000,encoded.length())));
  original.recycle();reduced.recycle();
 }
 @Override public void onStart(){
  Bundle result=new Bundle();
  try{
   Intent intent=new Intent(getTargetContext(),MainActivity.class);intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
   Activity a=startActivitySync(intent);waitForIdleSync();
   for(String room:new String[]{LudoRoomState.EXPLORE,LudoRoomState.HUNTS,LudoRoomState.HOME}){
    runOnMainSync(()->{try{set(a,"tab","companion");set(a,"ludoRooms",new LudoRoomState(room,0,0,0));set(a,"renderedLudoRoom","");invoke(a,"render");}catch(Exception e){throw new RuntimeException(e);}});
    waitForIdleSync();getUiAutomation().waitForIdle(200,5000);
    runOnMainSync(()->{try{
     View footer=(View)get(a,"ludoRoomDots");View refresh=(View)get(a,"refreshHost");View nav=(View)get(a,"nav");
     if(footer==null||footer.getHeight()<=0)throw new AssertionError("footer not measured");
     if(((FrameLayout.LayoutParams)refresh.getLayoutParams()).bottomMargin!=footer.getHeight())throw new AssertionError("footer obscures scroll viewport");
     for(String label:new String[]{"Esplora","Preferiti","Libreria","Trova nuovi giochi"})if(text(footer,label)==null)throw new AssertionError("missing destination/action "+label);
     int[] pos=new int[2],navPos=new int[2];footer.getLocationInWindow(pos);nav.getLocationInWindow(navPos);
     if(pos[1]+footer.getHeight()>navPos[1])throw new AssertionError("footer overlaps main navigation");
     ((android.widget.ScrollView)get(a,"scroll")).scrollTo(0,99999);
     int[] after=new int[2];footer.getLocationInWindow(after);if(after[1]!=pos[1])throw new AssertionError("search scrolls out of view");
     if(text(nav,"Bundle")!=null||((ViewGroup)nav).getChildCount()!=3)throw new AssertionError("Bundle still in main navigation");
    }catch(Exception e){throw new RuntimeException(e);}});
    capture(a,room);
   }
   runOnMainSync(()->{try{set(a,"tab","catalog");invoke(a,"render");if(get(a,"ludoRoomDots")!=null)throw new AssertionError("Ludo footer leaked to Catalog");if(((FrameLayout.LayoutParams)((View)get(a,"refreshHost")).getLayoutParams()).bottomMargin!=0)throw new AssertionError("Catalog viewport still reserved");if(text((View)get(a,"body"),"Bundle")==null)throw new AssertionError("Bundle inaccessible from Catalog");}catch(Exception e){throw new RuntimeException(e);}});
   capture(a,"catalog");
   runOnMainSync(a::finish);
   result.putString("stream","Ludo actual rooms, fixed footer and Catalog Bundle verified");finish(Activity.RESULT_OK,result);
  }catch(Throwable failure){result.putString("stream",android.util.Log.getStackTraceString(failure));finish(Activity.RESULT_CANCELED,result);}
 }
}
