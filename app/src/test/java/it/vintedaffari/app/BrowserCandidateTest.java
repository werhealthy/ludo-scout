package it.vintedaffari.app;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class BrowserCandidateTest {
 private BrowserCandidate card(String id,String title,Integer price,long at){return new BrowserCandidate(id,"https://www.vinted.it/items/"+id,title,price,null,Collections.emptyMap(),Collections.emptyMap(),at);}
 @Test public void missingPriceIsIncomplete(){assertFalse(card("101","Azul",null,10).elaborable());}
 @Test public void observationTimeDoesNotCreateRevision(){assertEquals(card("101","Azul",1000,10).revisionKey(),card("101","Azul",1000,20).revisionKey());}
 @Test public void changingPriceCreatesRevision(){assertNotEquals(card("101","Azul",1000,10).revisionKey(),card("101","Azul",900,20).revisionKey());}
 @Test public void absentPriceCannotEraseObservedPrice(){assertEquals(Integer.valueOf(1000),card("101","Azul",1000,10).merge(card("101","Azul",null,20)).priceCents);}
 @Test public void sameTextWithDistinctIdsRemainsDistinct(){assertNotEquals(card("101","Azul",1000,10).itemId,card("102","Azul",1000,10).itemId);}
 @Test(expected=IllegalArgumentException.class) public void identityMismatchRejected(){new BrowserCandidate("101","https://www.vinted.it/items/102","Azul",1000,null,Collections.emptyMap(),Collections.emptyMap(),10);}
 @Test(expected=IllegalArgumentException.class) public void otherIdCannotMerge(){card("101","Azul",1000,10).merge(card("102","Azul",1000,20));}
 @Test public void fieldsKeepIndependentObservationClocks(){
  BrowserCandidate first=card("101","Azul",1000,10);
  BrowserCandidate merged=first.merge(card("101","",null,100));
  assertEquals(Long.valueOf(10),merged.fieldObservedAt.get("title"));
  BrowserCandidate corrected=merged.merge(card("101","Azul Mini",null,50));
  assertEquals("Azul Mini",corrected.title);
  assertEquals(Long.valueOf(50),corrected.fieldObservedAt.get("title"));
  assertEquals(Long.valueOf(10),corrected.fieldObservedAt.get("priceCents"));
 }
}
