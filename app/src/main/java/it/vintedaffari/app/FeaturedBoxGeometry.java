package it.vintedaffari.app;
/** Reusable artwork geometry. No knowledge of the hero or its data selection. */
final class FeaturedBoxGeometry {
 final float[] front=new float[8],top=new float[8],side=new float[8];
 float width,height,depth,left,bottom,pedestalLeft,pedestalTop,pedestalWidth,pedestalHeight,pedestalSurfaceY;
 boolean update(int viewW,int viewH,int coverW,int coverH,float density){
  if(viewW<2||viewH<2||coverW<=0||coverH<=0||!Float.isFinite(density)||density<=0)return false;
  float ratio=coverW/(float)coverH;if(ratio<.15f||ratio>6)return false;
  float pad=Math.min(12*density,Math.min(viewW,viewH)*.12f),usableW=viewW-2*pad,usableH=(viewH-2*pad)/1.2f;
  if(usableW<=0||usableH<=0)return false;
  width=Math.min(usableW/1.26f,usableH*ratio);height=width/ratio;depth=width*.085f;
  left=(viewW-width-depth)/2;float y=(viewH-height)/2;bottom=y+height;
  float right=left+width,rise=depth*.38f,tilt=height*.012f;
  set(front,left,y+tilt,right,y,right,bottom,left,bottom-tilt);
  set(top,left,y+tilt,left+depth,y+tilt-rise,right+depth,y-rise,right,y);
  set(side,right,y,right+depth,y-rise,right+depth,bottom-rise,right,bottom);
  return true;
 }
 /** Pedestal-aware fitting: one uniform PNG scale and a shared surface anchor. */
 boolean update(int viewW,int viewH,int coverW,int coverH,float density,float pedestalRatio){
  if(!Float.isFinite(pedestalRatio)||pedestalRatio<=0||viewW<2||viewH<2||coverW<=0||coverH<=0||!Float.isFinite(density)||density<=0)return false;
  float ratio=coverW/(float)coverH;if(ratio<.15f||ratio>6)return false;
  float pad=Math.min(6*density,Math.min(viewW,viewH)*.06f),usableW=viewW-2*pad,usableH=viewH-2*pad;
  float baseScale=1.32f,baseHeightPerWidth=baseScale/pedestalRatio;
  width=Math.min(usableW/baseScale,usableH/(1/ratio+baseHeightPerWidth*.60f+.085f*.38f));height=width/ratio;depth=width*.085f;
  pedestalWidth=width*baseScale;pedestalHeight=pedestalWidth/pedestalRatio;
  float rise=depth*.38f,totalHeight=height+pedestalHeight*.60f+rise,y=(viewH-totalHeight)/2+rise;
  left=(viewW-width)/2;bottom=y+height;pedestalLeft=(viewW-pedestalWidth)/2;pedestalTop=bottom-pedestalHeight*.40f;
  float right=left+width,tilt=height*.012f;
  set(front,left,y+tilt,right,y,right,bottom,left,bottom-tilt);
  set(top,left,y+tilt,left+depth,y+tilt-rise,right+depth,y-rise,right,y);
  set(side,right,y,right+depth,y-rise,right+depth,bottom-rise,right,bottom);
  return true;
 }
 /** Smooth platform scene: complete box centered, camera shows the left spine. */
 boolean updateMetal(int viewW,int viewH,int coverW,int coverH,float density,boolean base){
  if(viewW<2||viewH<2||coverW<=0||coverH<=0||!Float.isFinite(density)||density<=0)return false;
  float ratio=coverW/(float)coverH;if(ratio<.15f||ratio>6)return false;
  float pad=Math.min(6*density,Math.min(viewW,viewH)*.06f),usableW=viewW-2*pad,usableH=viewH-2*pad;
  float risePerWidth=.14f*.20f,tailPerWidth=base?1.24f*.155f:.045f;
  width=Math.min(usableW/(base?1.24f:1.20f),usableH/(1/ratio+risePerWidth+tailPerWidth));height=width/ratio;depth=width*.14f;
  float rise=depth*.20f,total=height+rise+width*tailPerWidth,y=base?viewH-pad-total:(viewH-total)/2;
  left=(viewW-width-depth)/2+depth;bottom=y+rise+height;pedestalSurfaceY=bottom;
  float right=left+width,tilt=height*.005f;
  set(front,left,y+rise,right,y+rise+tilt,right,bottom-tilt,left,bottom);
  set(top,left,y+rise,left-depth,y,right-depth,y+tilt,right,y+rise+tilt);
  set(side,left-depth,y,left,y+rise,left,bottom,left-depth,bottom-rise);
  pedestalWidth=base?width*1.24f:0;pedestalHeight=pedestalWidth*.21f;
  pedestalLeft=(viewW-pedestalWidth)/2;pedestalTop=bottom-pedestalWidth*.055f;
  return true;
 }
 private void set(float[] a,float x1,float y1,float x2,float y2,float x3,float y3,float x4,float y4){a[0]=x1;a[1]=y1;a[2]=x2;a[3]=y2;a[4]=x3;a[5]=y3;a[6]=x4;a[7]=y4;}
}
