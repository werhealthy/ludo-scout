package it.vintedaffari.app;
import java.util.*;
/** Immutable part geometry in shared source-artboard coordinates; pivots are local. */
final class LudoPart {
 final String id,parentId;
 final int drawOrder;
 final float x,y,width,height,pivotX,pivotY;
 LudoPart(String id,String parentId,int drawOrder,float x,float y,float width,float height,float pivotX,float pivotY){
  if(id==null||id.trim().isEmpty()||parentId!=null&&parentId.trim().isEmpty()||!finite(x,y,width,height,pivotX,pivotY)||width<=0||height<=0||pivotX<0||pivotY<0||pivotX>width||pivotY>height)throw new IllegalArgumentException("Invalid Ludo part");
  this.id=id;this.parentId=parentId;this.drawOrder=drawOrder;this.x=x;this.y=y;this.width=width;this.height=height;this.pivotX=pivotX;this.pivotY=pivotY;
 }
 private static boolean finite(float... values){for(float value:values)if(!Float.isFinite(value))return false;return true;}
 static List<LudoPart> validateAndOrder(List<LudoPart> parts){
  if(parts==null)throw new IllegalArgumentException("Missing parts");
  List<LudoPart> copy=new ArrayList<>(parts);Map<String,LudoPart> byId=new HashMap<>();
  for(LudoPart part:copy)if(part==null||byId.put(part.id,part)!=null)throw new IllegalArgumentException("Duplicate/missing part");
  for(LudoPart part:copy){Set<String> visited=new HashSet<>();LudoPart cursor=part;
   while(cursor!=null){if(!visited.add(cursor.id))throw new IllegalArgumentException("Cyclic rig");
    if(cursor.parentId==null)break;cursor=byId.get(cursor.parentId);if(cursor==null)throw new IllegalArgumentException("Missing parent");
   }
  }
  copy.sort(Comparator.comparingInt(p->p.drawOrder));return Collections.unmodifiableList(copy);
 }
}
