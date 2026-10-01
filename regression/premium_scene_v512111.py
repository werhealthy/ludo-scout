#!/usr/bin/env python3
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1]
java='''package it.vintedaffari.app;
import java.lang.reflect.Method;
public class PremiumSceneRegression {
 static void expect(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
 public static void main(String[] args)throws Exception {
  Method update;
  try{update=FeaturedBoxGeometry.class.getDeclaredMethod("updateMetal",int.class,int.class,int.class,int.class,float.class,boolean.class);}
  catch(NoSuchMethodException missing){throw new AssertionError("Premium scene needs centered left-face geometry and an anchored thin surface");}
  int cases=0;
  for(int w:new int[]{68,120,180,300})for(int h:new int[]{68,160,240})for(int cw:new int[]{15,50,100,300,600})for(boolean base:new boolean[]{false,true}){
   FeaturedBoxGeometry g=new FeaturedBoxGeometry();expect((boolean)update.invoke(g,w,h,cw,100,1f,base),"valid cover geometry rejected");
   float left=Float.MAX_VALUE,right=-Float.MAX_VALUE,frontLeft=Float.MAX_VALUE,sideLeft=Float.MAX_VALUE;
   for(int i=0;i<8;i+=2){frontLeft=Math.min(frontLeft,g.front[i]);sideLeft=Math.min(sideLeft,g.side[i]);}
   expect(sideLeft<frontLeft,"reference requires visible left spine");
   for(float[] p:new float[][]{g.front,g.top,g.side})for(int i=0;i<8;i+=2){expect(p[i]>=0&&p[i]<=w&&p[i+1]>=0&&p[i+1]<=h,"box clipped");left=Math.min(left,p[i]);right=Math.max(right,p[i]);}
   expect(Math.abs((left+right)/2-w/2f)<.01f,"complete box must be centered");
   expect(Math.abs(g.width/g.height-cw/100f)<.001f,"real cover aspect must survive fitting");
   if(base){float surface=FeaturedBoxGeometry.class.getDeclaredField("pedestalSurfaceY").getFloat(g);
    expect(Math.abs(g.bottom-surface)<.01f,"box must touch the surface");
    expect(g.pedestalLeft>=0&&g.pedestalLeft+g.pedestalWidth<=w,"base clipped horizontally");
    expect(g.pedestalTop>=0&&g.pedestalTop+g.pedestalHeight<=h,"base clipped vertically");
    expect(g.pedestalHeight>0&&g.pedestalHeight<g.pedestalWidth*.30f,"base must be a thin cylinder");
    expect(Math.abs(g.pedestalLeft+g.pedestalWidth/2-w/2f)<.01f,"base and complete box centers diverge");
   }cases++;
  }
  expect(!(boolean)update.invoke(new FeaturedBoxGeometry(),0,100,100,100,1f,true),"invalid bounds accepted");
  Method inline;
  try{inline=HomePresentation.class.getDeclaredMethod("heroInline",float.class,float.class);}
  catch(NoSuchMethodException missing){throw new AssertionError("Square covers should use the compact two-column hero on a normal phone");}
  expect((boolean)inline.invoke(null,288f,1f),"360dp phone after actual 40dp+32dp padding must have horizontal hero");
  expect((boolean)inline.invoke(null,300f,1f),"normal phone hero should be horizontal");
  expect((boolean)inline.invoke(null,340f,1.15f),"moderate type scaling must keep the layout usable");
  expect(!(boolean)inline.invoke(null,250f,1f),"narrow cards need vertical fallback");
  expect(!(boolean)inline.invoke(null,340f,1.5f),"large type needs measured vertical fallback");
  System.out.println("PASS "+cases+" left-face/contact/centering/aspect scene cases and accessible compact hero");
 }
}''';
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp)/"PremiumSceneRegression.java";p.write_text(java)
 src=root/"app/src/main/java/it/vintedaffari/app"
 subprocess.run(["javac","-d",tmp,str(src/"FeaturedBoxGeometry.java"),str(src/"HomePresentation.java"),str(p)],check=True)
 subprocess.run(["java","-cp",tmp,"it.vintedaffari.app.PremiumSceneRegression"],check=True)
