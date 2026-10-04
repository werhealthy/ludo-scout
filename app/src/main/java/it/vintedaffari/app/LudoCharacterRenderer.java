package it.vintedaffari.app;
import android.graphics.*;
import java.util.*;
/** Raster cut-out renderer. Images and all scratch geometry are reused between frames. */
final class LudoCharacterRenderer {
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
 private final Rect source=new Rect();
 private final RectF destination=new RectF();
 private Bitmap fallback;
 private Node[] drawNodes=new Node[0],transformNodes=drawNodes;
 private float sourceWidth,sourceHeight;
 private static final class Node {
  final LudoPart part;final Bitmap image,alternative;final Matrix transform=new Matrix(),local=new Matrix();final RectF bounds;
  Node parent;int depth;
  Node(LudoPart part,Bitmap image,Bitmap alternative){this.part=part;this.image=image;this.alternative=alternative;bounds=new RectF(part.x,part.y,part.x+part.width,part.y+part.height);}
 }
 void setFallback(Bitmap bitmap){fallback=bitmap;}
 void setParts(List<LudoPart> parts,Map<String,Bitmap> images,float width,float height){
  if(!Float.isFinite(width)||!Float.isFinite(height)||width<=0||height<=0||images==null)throw new IllegalArgumentException("Invalid source artboard");
  List<LudoPart> ordered=LudoPart.validateAndOrder(parts);Map<String,Node> nodes=new HashMap<>();
  Node[] draw=new Node[ordered.size()];
  for(int i=0;i<draw.length;i++){LudoPart part=ordered.get(i);draw[i]=new Node(part,images.get(part.id),images.get(part.id+"_surprised"));nodes.put(part.id,draw[i]);}
  for(Node node:draw){node.parent=nodes.get(node.part.parentId);for(Node p=node.parent;p!=null;p=nodes.get(p.part.parentId))node.depth++;}
  Node[] transform=draw.clone();Arrays.sort(transform,Comparator.comparingInt(n->n.depth));
  drawNodes=draw;transformNodes=transform;sourceWidth=width;sourceHeight=height;
 }
 void draw(Canvas canvas,RectF bounds,LudoPose pose,boolean portrait){
  if(bounds.width()<=0||bounds.height()<=0)return;
  if(drawNodes.length==0){drawFallback(canvas,bounds,pose,portrait);return;}
  float height=portrait?sourceHeight*.67f:sourceHeight;
  float scale=Math.min(bounds.width()/sourceWidth,bounds.height()/height);
  float left=bounds.centerX()-sourceWidth*scale/2,bottom=bounds.centerY()+height*scale/2;
  int saved=canvas.save();
  canvas.clipRect(bounds);canvas.translate(left,bottom);canvas.scale(scale,scale);
  canvas.translate(pose.gazeX*sourceWidth*.008f,-height-pose.reactionLift*height);
  canvas.scale(1,pose.bodyScaleY,sourceWidth/2,height);
  // Every part uses absolute source coordinates. Parent matrices carry only animation deltas.
  for(Node node:transformNodes){node.local.reset();float px=node.part.x+node.part.pivotX,py=node.part.y+node.part.pivotY;
   if("head".equals(node.part.id))node.local.setRotate(pose.headRotationDeg,px,py);
   else if("arm_left".equals(node.part.id))node.local.setRotate(pose.armRotationDeg,px,py);
   else if("arm_right".equals(node.part.id))node.local.setRotate(-pose.armRotationDeg,px,py);
   else if(node.part.id.startsWith("eye_"))node.local.setScale(1,Math.max(.03f,pose.eyeOpen),node.bounds.centerX(),node.bounds.centerY());
   else if(node.part.id.startsWith("spiral_"))node.local.setTranslate(pose.gazeX*7,pose.gazeY*5);
   if(node.parent==null)node.transform.set(node.local);else{node.transform.set(node.parent.transform);node.transform.preConcat(node.local);}
  }
  for(Node node:drawNodes){Bitmap image=pose.reactionLift>.008f&&node.alternative!=null?node.alternative:node.image;
   if(image==null||image.isRecycled())continue;
   int layer=canvas.save();
   if(node.part.id.startsWith("spiral_")&&node.parent!=null){canvas.concat(node.parent.transform);canvas.clipRect(node.parent.bounds);canvas.concat(node.local);}else canvas.concat(node.transform);
   canvas.drawBitmap(image,null,node.bounds,paint);canvas.restoreToCount(layer);
  }
  canvas.restoreToCount(saved);
 }
 private void drawFallback(Canvas canvas,RectF bounds,LudoPose pose,boolean portrait){
  if(fallback==null||fallback.isRecycled())return;
  int imageHeight=portrait?Math.max(1,Math.round(fallback.getHeight()*.67f)):fallback.getHeight();
  source.set(0,0,fallback.getWidth(),imageHeight);
  float scale=Math.min(bounds.width()/fallback.getWidth(),bounds.height()/imageHeight);
  float width=fallback.getWidth()*scale,height=imageHeight*scale;
  destination.set(bounds.centerX()-width/2,bounds.centerY()-height/2,bounds.centerX()+width/2,bounds.centerY()+height/2);
  int saved=canvas.save();canvas.translate(pose.gazeX*bounds.width()*.008f,portrait?pose.gazeY*bounds.height()*.004f:-pose.reactionLift*height);
  canvas.scale(1,pose.bodyScaleY,destination.centerX(),destination.bottom);
  if(portrait)canvas.rotate(pose.headRotationDeg+pose.gazeX*2,destination.centerX(),destination.top+height*.6f);
  canvas.drawBitmap(fallback,source,destination,paint);canvas.restoreToCount(saved);
 }
}
