package it.vintedaffari.app;
import org.junit.Test;import static org.junit.Assert.*;
public class FeaturedBoxGeometryTest {
 @Test public void boxFitsAndKeepsCoverRatio(){for(int[] cover:new int[][]{{400,400},{240,600},{700,300}}){FeaturedBoxGeometry g=new FeaturedBoxGeometry();assertTrue(g.update(180,210,cover[0],cover[1],1));assertEquals(cover[0]/(float)cover[1],g.width/g.height,.001);for(int i=0;i<8;i+=2){assertTrue(g.front[i]>=0&&g.front[i]<=180);assertTrue(g.front[i+1]>=0&&g.front[i+1]<=210);}assertTrue(g.top[5]<g.front[3]);assertTrue(g.depth<g.width*.15);}}
 @Test public void invalidInputsUseFallback(){FeaturedBoxGeometry g=new FeaturedBoxGeometry();assertFalse(g.update(0,100,400,400,1));assertFalse(g.update(100,100,0,400,1));assertFalse(g.update(100,100,400,400,Float.NaN));}
}
