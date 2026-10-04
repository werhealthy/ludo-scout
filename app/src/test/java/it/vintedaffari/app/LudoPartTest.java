package it.vintedaffari.app;
import java.util.*;import org.junit.Test;import static org.junit.Assert.*;
public class LudoPartTest {
 private LudoPart part(String id,String parent,int order){return new LudoPart(id,parent,order,0,0,20,20,10,10);}
 private void rejects(List<LudoPart> parts){try{LudoPart.validateAndOrder(parts);fail("invalid rig accepted");}catch(IllegalArgumentException expected){}}
 @Test public void rejectsInvalidDimensions(){try{new LudoPart("body",null,0,0,0,0,20,0,0);fail();}catch(IllegalArgumentException expected){}try{new LudoPart("body",null,0,Float.NaN,0,20,20,0,0);fail();}catch(IllegalArgumentException expected){}}
 @Test public void rejectsDuplicateIds(){rejects(Arrays.asList(part("body",null,0),part("body",null,1)));}
 @Test public void rejectsMissingParent(){rejects(Collections.singletonList(part("head","missing",0)));}
 @Test public void rejectsCycles(){rejects(Arrays.asList(part("head","hat",0),part("hat","head",1)));rejects(Collections.singletonList(part("body","body",0)));}
 @Test public void stableDrawOrderAndInputCopy(){List<LudoPart> input=new ArrayList<>(Arrays.asList(part("head","body",2),part("body",null,0),part("hat","head",2)));List<LudoPart> ordered=LudoPart.validateAndOrder(input);assertEquals("body",ordered.get(0).id);assertEquals("head",ordered.get(1).id);assertEquals("hat",ordered.get(2).id);input.clear();assertEquals(3,ordered.size());}
}
