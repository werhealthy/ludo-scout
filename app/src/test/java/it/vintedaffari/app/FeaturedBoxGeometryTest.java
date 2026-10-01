package it.vintedaffari.app;
import org.junit.Test;import static org.junit.Assert.*;
public class FeaturedBoxGeometryTest {
 @Test public void boxFitsAndKeepsCoverRatio(){for(int[] cover:new int[][]{{400,400},{240,600},{700,300}}){FeaturedBoxGeometry g=new FeaturedBoxGeometry();assertTrue(g.update(180,210,cover[0],cover[1],1));assertEquals(cover[0]/(float)cover[1],g.width/g.height,.001);for(int i=0;i<8;i+=2){assertTrue(g.front[i]>=0&&g.front[i]<=180);assertTrue(g.front[i+1]>=0&&g.front[i+1]<=210);}assertTrue(g.top[5]<g.front[3]);assertTrue(g.depth<g.width*.15);}}
 @Test public void invalidInputsUseFallback(){FeaturedBoxGeometry g=new FeaturedBoxGeometry();assertFalse(g.update(0,100,400,400,1));assertFalse(g.update(100,100,0,400,1));assertFalse(g.update(100,100,400,400,Float.NaN));}
 @Test public void pedestalFitsAllCoverModesWithoutStretching(){for(int[] cover:new int[][]{{400,400},{240,600},{700,300}}){FeaturedBoxGeometry g=new FeaturedBoxGeometry();assertTrue(g.update(170,260,cover[0],cover[1],1,3));assertEquals(3,g.pedestalWidth/g.pedestalHeight,.001);assertTrue(g.pedestalWidth>g.width+g.depth);assertTrue(g.pedestalLeft>=0);assertTrue(g.pedestalLeft+g.pedestalWidth<=170);assertTrue(g.pedestalTop+g.pedestalHeight<=260);assertEquals(g.bottom,g.pedestalTop+g.pedestalHeight*.40f,.001);}}
 @Test public void invalidPedestalUsesFlatFallback(){FeaturedBoxGeometry g=new FeaturedBoxGeometry();assertFalse(g.update(170,260,400,400,1,0));assertFalse(g.update(170,260,400,400,1,Float.NaN));}
 @Test public void squareArtworkOccupiesForeground(){FeaturedBoxGeometry g=new FeaturedBoxGeometry();assertTrue(g.update(320,333,400,400,1,3));assertTrue("Square cover should occupy at least 79 percent of artwork width",g.width>=320*.79f);assertTrue(g.pedestalWidth>g.width+g.depth);assertTrue(g.top[5]>=0);assertTrue(g.pedestalTop+g.pedestalHeight<=333);}
}

