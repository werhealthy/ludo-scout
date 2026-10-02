package it.vintedaffari.app;
/** Claims intentional horizontal input; vertical, diagonal and short gestures stay harmless. */
final class LudoRoomSwipe {
 private float x,y;private boolean active,owned,rejected;
 void down(float startX,float startY){x=startX;y=startY;active=true;owned=false;rejected=false;}
 boolean move(float nextX,float nextY,float slop){
  if(!active||rejected)return false;if(owned)return true;
  float dx=Math.abs(nextX-x),dy=Math.abs(nextY-y);
  if(dy>slop&&dy>=dx){rejected=true;return false;}
  if(dx>slop&&dx>dy*1.6f)owned=true;return owned;
 }
 boolean owns(){return owned;}
 int release(float endX,float endY,float threshold){float dx=endX-x,dy=Math.abs(endY-y);boolean valid=active&&owned&&!rejected&&Math.abs(dx)>=threshold&&Math.abs(dx)>dy*1.6f;cancel();return valid?(dx<0?1:-1):0;}
 void cancel(){active=false;owned=false;rejected=true;}
}
