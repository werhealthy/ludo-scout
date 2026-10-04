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
  awaitActivityFrame(a);
  Bitmap original=null;
  for(int attempt=0;attempt<3;attempt++){
   awaitActivityFrame(a);original=getUiAutomation().takeScreenshot();
   if(original!=null&&hasSceneContent(original))break;
   if(original!=null){original.recycle();original=null;}
  }
  if(original==null)throw new AssertionError("blank or obscured Activity screenshot: "+name);
  Bitmap reduced=Bitmap.createScaledBitmap(original,432,Math.round(original.getHeight()*432f/original.getWidth()),true);
  java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();reduced.compress(Bitmap.CompressFormat.JPEG,88,bytes);
  String encoded=android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);
  for(int offset=0,index=0;offset<encoded.length();offset+=3000,index++)android.util.Log.i("LudoVisual","VISUAL "+(a.getResources().getConfiguration().fontScale>1.3f?"large_":"normal_")+name+" "+index+" "+encoded.substring(offset,Math.min(offset+3000,encoded.length())));
  original.recycle();reduced.recycle();
 }

 private boolean hasSceneContent(Bitmap image){
  // Sample the central app viewport: system bars alone must never satisfy the capture.
  int first=0,distinct=0;boolean initialized=false;
  for(int y=image.getHeight()/5;y<image.getHeight()*4/5;y+=Math.max(1,image.getHeight()/40))
   for(int x=image.getWidth()/10;x<image.getWidth()*9/10;x+=Math.max(1,image.getWidth()/40)){
    int color=image.getPixel(x,y)&0x00ffffff;
    if(!initialized){first=color;initialized=true;}
    int delta=Math.abs((color>>16)-(first>>16))+Math.abs(((color>>8)&255)-((first>>8)&255))+Math.abs((color&255)-(first&255));
    if(delta>45)distinct++;
   }
  return distinct>20;
 }
 private void awaitActivityFrame(Activity a)throws Exception{
  java.util.concurrent.CountDownLatch drawn=new java.util.concurrent.CountDownLatch(1);
  runOnMainSync(()->{
   View decor=a.getWindow().getDecorView();
   android.view.ViewTreeObserver.OnDrawListener listener=new android.view.ViewTreeObserver.OnDrawListener(){
    @Override public void onDraw(){
     if(decor.hasWindowFocus()&&decor.isShown()){
      decor.post(()->{if(decor.getViewTreeObserver().isAlive())decor.getViewTreeObserver().removeOnDrawListener(this);drawn.countDown();});
     }
    }
   };
   decor.getViewTreeObserver().addOnDrawListener(listener);decor.invalidate();
  });
  if(!drawn.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Activity did not regain focus and draw");
  waitForIdleSync();getUiAutomation().waitForIdle(200,5000);
 }
 private void overlayContracts(Activity a)throws Exception{
  final android.app.Dialog[] overlay={null};
  runOnMainSync(()->{try{
   overlay[0]=new android.app.Dialog(a);TextView label=new TextView(a);label.setText("Ludo overlay fixture");overlay[0].setContentView(label);
   set(a,"activeGameOverlay",overlay[0]);overlay[0].show();invoke(a,"syncPetVisibility");
  }catch(Exception e){throw new RuntimeException(e);}});
  waitForIdleSync();getUiAutomation().waitForIdle(200,5000);
  runOnMainSync(()->{try{
   if(!overlay[0].isShowing()||!overlay[0].getWindow().getDecorView().hasWindowFocus())throw new AssertionError("overlay did not acquire real focus");
   LudoPetView actor=(LudoPetView)get(a,"petView");
   if(petField(actor,"idle")!=null)throw new AssertionError("overlay leaves actor animating");
   overlay[0].dismiss();set(a,"activeGameOverlay",null);invoke(a,"syncPetVisibility");
  }catch(Exception e){throw new RuntimeException(e);}});
  awaitActivityFrame(a);
  runOnMainSync(()->{try{
   LudoPetView actor=(LudoPetView)get(a,"petView");
   if(overlay[0].isShowing()||!actor.hasWindowFocus())throw new AssertionError("Activity focus not restored after overlay");
   if(android.animation.ValueAnimator.areAnimatorsEnabled()&&petField(actor,"idle")==null)throw new AssertionError("actor did not resume after overlay");
  }catch(Exception e){throw new RuntimeException(e);}});
 }

 private void rendererContracts(){
  Bitmap blank=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);blank.eraseColor(android.graphics.Color.GRAY);
  if(hasSceneContent(blank))throw new AssertionError("blank screenshot accepted");
  new android.graphics.Canvas(blank).drawRect(20,20,80,80,new android.graphics.Paint(){{setColor(android.graphics.Color.GREEN);}});
  if(!hasSceneContent(blank))throw new AssertionError("visible content rejected");blank.recycle();
  Bitmap target=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888),red=Bitmap.createBitmap(10,10,Bitmap.Config.ARGB_8888),blue=Bitmap.createBitmap(10,10,Bitmap.Config.ARGB_8888);
  red.eraseColor(android.graphics.Color.RED);blue.eraseColor(android.graphics.Color.BLUE);
  android.graphics.Canvas canvas=new android.graphics.Canvas(target);android.graphics.RectF bounds=new android.graphics.RectF(0,0,100,100);LudoPose pose=new LudoPose();LudoPose.sample(0,-1,false,0,0,pose);
  LudoCharacterRenderer renderer=new LudoCharacterRenderer();renderer.setFallback(red);renderer.draw(canvas,bounds,pose,false);
  if(target.getPixel(50,99)!=android.graphics.Color.RED)throw new AssertionError("fallback feet do not reach bottom anchor");
  target.eraseColor(0);pose.bodyScaleY=.996f;renderer.draw(canvas,bounds,pose,false);
  if(target.getPixel(50,99)!=android.graphics.Color.RED)throw new AssertionError("breathing moves foot anchor");
  java.util.List<LudoPart> parts=java.util.Arrays.asList(new LudoPart("head","body",1,40,20,20,20,10,10),new LudoPart("body",null,0,0,0,100,100,50,100));
  java.util.Map<String,Bitmap> images=new java.util.HashMap<>();images.put("head",blue);images.put("body",red);renderer.setParts(parts,images,100,100);pose.bodyScaleY=1;target.eraseColor(0);renderer.draw(canvas,bounds,pose,false);
  if(target.getPixel(50,30)!=android.graphics.Color.BLUE||target.getPixel(10,30)!=android.graphics.Color.RED)throw new AssertionError("draw order/coordinates");
  // Root translation must move the child as well without changing its source coordinates.
  pose.gazeX=1;target.eraseColor(0);renderer.draw(canvas,bounds,pose,false);
  if(target.getPixel(40,30)==android.graphics.Color.BLUE||target.getPixel(41,30)!=android.graphics.Color.BLUE)throw new AssertionError("parent transform not inherited");
  renderer.setParts(java.util.Collections.emptyList(),java.util.Collections.emptyMap(),100,100);renderer.setFallback(null);target.eraseColor(0);renderer.draw(canvas,bounds,pose,false);if(target.getPixel(50,50)!=0)throw new AssertionError("missing asset fabricated");
  renderer.setFallback(blue);blue.recycle();renderer.draw(canvas,bounds,pose,false);if(target.getPixel(50,50)!=0)throw new AssertionError("recycled asset rendered");
  Bitmap green=Bitmap.createBitmap(10,10,Bitmap.Config.ARGB_8888);green.eraseColor(android.graphics.Color.GREEN);
  renderer.setParts(java.util.Arrays.asList(new LudoPart("body",null,0,0,0,100,100,50,100),new LudoPart("head","body",1,40,20,20,20,10,10),new LudoPart("hat","head",2,60,20,10,10,5,5)),new java.util.HashMap<String,Bitmap>(){{put("body",red);put("hat",green);}},100,100);
  pose.gazeX=0;pose.headRotationDeg=90;target.eraseColor(0);renderer.draw(canvas,bounds,pose,false);
  if(target.getPixel(55,45)!=android.graphics.Color.GREEN||target.getPixel(65,25)==android.graphics.Color.GREEN)throw new AssertionError("child did not rotate around parent pivot");
  green.recycle();red.recycle();target.recycle();
 }
 private Object petField(LudoPetView actor,String name)throws Exception{Field f=LudoPetView.class.getDeclaredField(name);f.setAccessible(true);return f.get(actor);}
 private void motionContracts(Activity a)throws Exception{
  LudoPetView actor=(LudoPetView)get(a,"petView");boolean enabled=android.animation.ValueAnimator.areAnimatorsEnabled();
  actor.setResumed(true);Object animator=petField(actor,"idle");
  if(enabled&&animator==null)throw new AssertionError("visible actor does not animate");
  LudoRoomBackdropView backdrop=(LudoRoomBackdropView)get(a,"ludoBackdrop");
  Field particleField=LudoRoomBackdropView.class.getDeclaredField("particles");particleField.setAccessible(true);
  if(enabled&&particleField.get(backdrop)==null)throw new AssertionError("visible background not animating");
  set(a,"petResumed",false);invoke(a,"syncPetVisibility");if(particleField.get(backdrop)!=null)throw new AssertionError("paused scene leaves background animating");
  set(a,"petResumed",true);invoke(a,"syncPetVisibility");actor.setResumed(true);animator=petField(actor,"idle");
  actor.react();actor.react();if(petField(actor,"idle")!=animator)throw new AssertionError("tap creates extra animator");
  if(!enabled&&petField(actor,"idle")!=null)throw new AssertionError("reduced motion still runs");
  if(enabled){
   Field started=LudoPetView.class.getDeclaredField("reactionStarted");started.setAccessible(true);started.setLong(actor,android.os.SystemClock.uptimeMillis()-800);
   Bitmap sample=Bitmap.createBitmap(Math.max(1,actor.getWidth()),Math.max(1,actor.getHeight()),Bitmap.Config.ARGB_8888);android.graphics.Canvas canvas=new android.graphics.Canvas(sample);actor.draw(canvas);
   float before=((LudoPose)petField(actor,"pose")).reactionLift;actor.react();actor.draw(canvas);float after=((LudoPose)petField(actor,"pose")).reactionLift;sample.recycle();
   if(before<.014f||Math.abs(before-after)>.001f)throw new AssertionError("repeated tap jumps to resting pose");
  }
  actor.setResumed(false);if(petField(actor,"idle")!=null||((Long)petField(actor,"reactionStarted"))!=-1L)throw new AssertionError("pause leaves reaction/animator alive");
  actor.react();if(((Long)petField(actor,"reactionStarted"))!=-1L)throw new AssertionError("paused tap starts movement");
  actor.setResumed(true);
  ViewGroup parent=(ViewGroup)actor.getParent();int index=parent.indexOfChild(actor);android.view.ViewGroup.LayoutParams layout=actor.getLayoutParams();parent.removeView(actor);
  if(petField(actor,"idle")!=null||((Long)petField(actor,"reactionStarted"))!=-1L)throw new AssertionError("detach leaves motion alive");
  parent.addView(actor,index,layout);actor.setResumed(true);

 }

 @Override public void onStart(){
  Bundle result=new Bundle();
  try{
   Intent intent=new Intent(getTargetContext(),MainActivity.class);intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
   Activity a=startActivitySync(intent);waitForIdleSync();
   runOnMainSync(this::rendererContracts);
   final Object[] actor={null};
   for(String room:new String[]{LudoRoomState.EXPLORE,LudoRoomState.HUNTS,LudoRoomState.HOME}){
    runOnMainSync(()->{try{set(a,"tab","companion");set(a,"ludoRooms",new LudoRoomState(room,0,0,0));set(a,"renderedLudoRoom","");invoke(a,"render");}catch(Exception e){throw new RuntimeException(e);}});
    waitForIdleSync();getUiAutomation().waitForIdle(200,5000);
    runOnMainSync(()->{try{
     if(!room.equals(((LudoRoomState)get(a,"ludoRooms")).room()))throw new AssertionError("wrong room rendered");
     View footer=(View)get(a,"ludoRoomDots");View refresh=(View)get(a,"refreshHost");View nav=(View)get(a,"nav");
     if(footer==null||footer.getHeight()<=0)throw new AssertionError("footer not measured");
     if(((FrameLayout.LayoutParams)refresh.getLayoutParams()).bottomMargin!=footer.getHeight())throw new AssertionError("footer obscures scroll viewport");
     for(String label:new String[]{"Trova nuovi giochi"})if(text(footer,label)==null)throw new AssertionError("missing destination/action "+label);
     View scene=(View)get(a,"ludoStage");View actorNow=(View)get(a,"petView");
     if(actor[0]!=null&&actor[0]!=actorNow)throw new AssertionError("mascot recreated between rooms");actor[0]=actorNow;
     if(((FrameLayout.LayoutParams)refresh.getLayoutParams()).topMargin!=(scene.getParent()==get(a,"mainScrollStage")?scene.getHeight():0))throw new AssertionError("scene obscures content");
     for(String label:new String[]{"Esplora","Preferiti","Libreria"})if(text(scene,label)==null)throw new AssertionError("top section missing "+label);
     if(refresh.getHeight()<100)throw new AssertionError("content viewport collapsed");
     int[] pos=new int[2],navPos=new int[2];footer.getLocationInWindow(pos);nav.getLocationInWindow(navPos);
     if(pos[1]+footer.getHeight()>navPos[1])throw new AssertionError("footer overlaps main navigation");
     ((android.widget.ScrollView)get(a,"scroll")).scrollTo(0,99999);
     int[] after=new int[2];footer.getLocationInWindow(after);if(after[1]!=pos[1])throw new AssertionError("search scrolls out of view");
     if(text(nav,"Bundle")!=null||((ViewGroup)nav).getChildCount()!=3)throw new AssertionError("Bundle still in main navigation");
    }catch(Exception e){throw new RuntimeException(e);}});
    if(LudoRoomState.EXPLORE.equals(room))runOnMainSync(()->{try{motionContracts(a);}catch(Exception e){throw new RuntimeException(e);}});
    if(LudoRoomState.EXPLORE.equals(room))overlayContracts(a);
    capture(a,room);
   }
   runOnMainSync(()->{try{set(a,"tab","catalog");invoke(a,"render");if(get(a,"ludoRoomDots")!=null||get(a,"ludoStage")!=null)throw new AssertionError("Ludo footer leaked to Catalog");if(((FrameLayout.LayoutParams)((View)get(a,"refreshHost")).getLayoutParams()).bottomMargin!=0)throw new AssertionError("Catalog viewport still reserved");if(text((View)get(a,"body"),"Bundle")==null)throw new AssertionError("Bundle inaccessible from Catalog");}catch(Exception e){throw new RuntimeException(e);}});
   capture(a,"catalog");
   runOnMainSync(()->{try{
    set(a,"tab","activity");set(a,"engineSection","phase");set(a,"engineDetailReturnToLudo",true);
    Bundle saved=new Bundle();java.lang.reflect.Method save=MainActivity.class.getDeclaredMethod("saveUiState",Bundle.class);save.setAccessible(true);save.invoke(a,saved);
    set(a,"engineDetailReturnToLudo",false);java.lang.reflect.Method restore=MainActivity.class.getDeclaredMethod("restoreUiState",Bundle.class);restore.setAccessible(true);restore.invoke(a,saved);
    if(!Boolean.TRUE.equals(get(a,"engineDetailReturnToLudo"))||!"overview".equals(get(a,"engineSection")))throw new AssertionError("engine recreation lost Ludo origin or restored an unloaded phase");
    invoke(a,"onBackPressed");if(!"companion".equals(get(a,"tab")))throw new AssertionError("engine recreation Back did not return to Ludo");
   }catch(Exception e){throw new RuntimeException(e);}});
   runOnMainSync(a::finish);
   result.putString("stream","Ludo actual rooms, fixed footer and Catalog Bundle verified; persistent actor and top sections checked");finish(Activity.RESULT_OK,result);
  }catch(Throwable failure){result.putString("stream",android.util.Log.getStackTraceString(failure));finish(Activity.RESULT_CANCELED,result);}
 }
}
