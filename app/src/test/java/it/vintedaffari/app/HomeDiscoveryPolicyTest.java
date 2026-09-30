package it.vintedaffari.app;
import java.util.*;import org.junit.Test;import static org.junit.Assert.*;
public class HomeDiscoveryPolicyTest {
 @Test public void ownsAndDislikesExcludeEveryListingOfSameGame(){Set<String> owned=new HashSet<>(Arrays.asList("10"));assertFalse(HomeDiscoveryPolicy.eligible("10","a","IT",owned,0));assertFalse(HomeDiscoveryPolicy.eligible("10","b","EN",owned,1));assertFalse(HomeDiscoveryPolicy.eligible("11","a","IT",owned,-1));assertEquals(HomeDiscoveryPolicy.key("11","a"),HomeDiscoveryPolicy.key("11","b"));assertNotEquals(HomeDiscoveryPolicy.key(null,"a"),HomeDiscoveryPolicy.key(null,"b"));}
 @Test public void onlyConfirmedForeignDependenceIsExcluded(){Set<String> owned=Collections.emptySet();assertFalse(HomeDiscoveryPolicy.eligible("1","a","FR|DEP",owned,0));assertFalse(HomeDiscoveryPolicy.eligible("1","a","NL|DEP",owned,0));for(String value:new String[]{"IT|DEP","EN|DEP","FR|IND","FR","FR|DEP|IND",null})assertTrue(value,HomeDiscoveryPolicy.eligible("1","a",value,owned,0));}
 @Test public void discountBoundariesAreExact(){int[] values={0,9,10,29,30,79,80,100};int[] bands={0,0,1,1,2,2,3,3};for(int i=0;i<values.length;i++)assertEquals(bands[i],HomeDiscoveryPolicy.discountBand(values[i]));}
 @Test public void railShowsTwoCompleteCardsAndHalfThird(){for(int available:new int[]{280,320,380,720}){int width=HomeDiscoveryPolicy.railWidth(available,8);int peek=available-2*width-16;assertEquals(width*.5,peek,2);}}
}
