package it.vintedaffari.app;
/** Pure paging decision, shared by touch handling and regression tests. */
public final class GalleryPosition {
 public static int clamp(int index,int count){return Math.max(0,Math.min(index,Math.max(0,count-1)));}
 public static int afterDrag(int start,float distance,int width,int count){if(width<=0)return clamp(start,count);float threshold=Math.min(width*.18f,72f);return clamp(start+(distance>threshold?1:distance< -threshold?-1:0),count);}
}
