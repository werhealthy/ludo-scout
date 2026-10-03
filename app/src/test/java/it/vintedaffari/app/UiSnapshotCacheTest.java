package it.vintedaffari.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class UiSnapshotCacheTest {
 @Test public void returningScreenReusesSnapshotUntilExpired(){UiSnapshotCache<String,String> c=new UiSnapshotCache<>(2,100);long generation=c.generation();assertTrue(c.put("Carcassonne","results",10,generation));assertEquals("results",c.get("Carcassonne",109));assertNull(c.get("Carcassonne",110));}
 @Test public void updatedDataRejectsOldWorkerAndClearsWarmScreens(){UiSnapshotCache<Long,String> c=new UiSnapshotCache<>(2,100);long generation=c.generation();c.put(1L,"old",10,generation);c.invalidate();assertNull(c.get(1L,11));assertFalse(c.put(1L,"late old worker",12,generation));assertNull(c.get(1L,12));assertTrue(c.put(1L,"fresh",13,c.generation()));assertEquals("fresh",c.get(1L,14));}
 @Test public void evictionIsBoundedAndUsesLastAccess(){UiSnapshotCache<String,String> c=new UiSnapshotCache<>(2,100);long g=c.generation();c.put("A","a",0,g);c.put("B","b",0,g);c.get("A",1);c.put("C","c",2,g);assertNull(c.get("B",3));assertEquals("a",c.get("A",3));assertEquals("c",c.get("C",3));}
}
