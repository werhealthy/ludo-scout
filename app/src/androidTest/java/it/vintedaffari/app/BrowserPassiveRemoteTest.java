package it.vintedaffari.app;
import android.test.AndroidTestCase;
import java.util.*;
/** Tests the actual legacy Radar priority boundary, not merely enqueue flags. */
public class BrowserPassiveRemoteTest extends AndroidTestCase {
 public void testBrowserHighPriorityDoesNotTriggerAutomaticRemoteMetadata()throws Exception{
  android.content.Context context=new BrowserTestContext(getContext(),getClass().getSimpleName());context.deleteDatabase("vinted_affari.db");DealDatabase db=new DealDatabase(context);
  try{BrowserCaptureStore store=new BrowserCaptureStore(db);long capture=store.beginCapture("https://www.vinted.it/catalog",10);store.commit(capture,1,Collections.singletonList(new BrowserCandidate("101","https://www.vinted.it/items/101","Azul",1000,null,Collections.emptyMap(),Collections.emptyMap(),10)),10);VintedAccessibilityService radar=new VintedAccessibilityService();java.lang.reflect.Field market=VintedAccessibilityService.class.getDeclaredField("marketStore");market.setAccessible(true);market.set(radar,new MarketStore(context,db));java.lang.reflect.Method priority=VintedAccessibilityService.class.getDeclaredMethod("networkPriority",DealRecord.class);priority.setAccessible(true);DealRecord captured=new DealRecord();captured.vintedItemId="101";captured.signature="vinted:101";captured.tier="hot";captured.qualityScore=90;assertEquals(Boolean.FALSE,priority.invoke(radar,captured));DealRecord legacy=new DealRecord();legacy.vintedItemId="102";legacy.tier="hot";assertEquals(Boolean.TRUE,priority.invoke(radar,legacy));}
  finally{db.close();context.deleteDatabase("vinted_affari.db");}
 }
}
