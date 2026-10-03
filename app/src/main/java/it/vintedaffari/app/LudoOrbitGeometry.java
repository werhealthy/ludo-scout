package it.vintedaffari.app;
/** Bounds use measured text height; no fixed screen-wide absolute node positions. */
final class LudoOrbitGeometry {
 private LudoOrbitGeometry(){}
 static boolean useRows(int widthDp,float fontScale){return widthDp<280||fontScale>1.3f;}
 static int height(int nodeHeight){return Math.max(1,nodeHeight)*4;}
 static int[][] bounds(int width,int nodeHeight){
  int w=Math.max(0,width),h=Math.max(1,nodeHeight),col=w/3,right=w-col;
  return new int[][]{{col,0,w-2*col,h},{0,h,col,h},{right,h,col,h},{0,2*h,col,h},{right,2*h,col,h},{col,3*h,w-2*col,h},{col,h,w-2*col,2*h}};
 }
}
