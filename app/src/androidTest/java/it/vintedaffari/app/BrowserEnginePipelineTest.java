package it.vintedaffari.app;
import android.test.AndroidTestCase;
import java.util.*;
/** Durable membership drives Motore; a stored terminal label alone cannot mean Catalog-ready. */
public class BrowserEnginePipelineTest extends AndroidTestCase {
 private DealDatabase db;private BrowserCaptureStore store;
 protected void setUp()throws Exception{super.setUp();setContext(new BrowserTestContext(getContext(),getClass().getSimpleName()));getContext().deleteDatabase("vinted_affari.db");db=new DealDatabase(getContext());store=new BrowserCaptureStore(db);}
 protected void tearDown()throws Exception{db.close();getContext().deleteDatabase("vinted_affari.db");super.tearDown();}
 private BrowserCandidate card(int id,Integer price){return new BrowserCandidate(String.valueOf(id),"https://www.vinted.it/items/"+id,"Azul gioco da tavolo",price,null,Collections.emptyMap(),Collections.emptyMap(),10);}
 public void test156Ids96PricesBeforeDrainAccountedExactlyOnce(){long capture=store.beginCapture("https://www.vinted.it/catalog",10);for(int first=0;first<156;first+=32){List<BrowserCandidate> packet=new ArrayList<>();for(int i=first;i<Math.min(first+32,156);i++)packet.add(card(101+i,i<96?1000:null));store.commit(capture,1,packet,10);}BrowserCapturePresentation groups=BrowserCapturePresentation.groups(store.snapshot(capture));assertEquals(156,groups.total());assertEquals(96,groups.rows("QUEUED").size());assertEquals(60,groups.rows("INCOMPLETE").size());assertEquals(96,store.activeJobs());}
 public void testSameIdInTwoSearchesKeepsOneCanonicalJob(){long first=store.beginCapture("https://www.vinted.it/catalog",10),second=store.beginCapture("https://www.vinted.it/catalog?search_text=Azul",11);store.commit(first,1,Collections.singletonList(card(101,1000)),10);store.commit(second,1,Collections.singletonList(card(101,1000)),11);assertEquals(1,store.snapshot(first).rows.size());assertEquals(1,store.snapshot(second).rows.size());assertEquals(1,store.activeJobs());}
 public void testReadyLabelCannotReplaceCurrentCatalogEligibility(){long capture=store.beginCapture("https://www.vinted.it/catalog",10);store.commit(capture,1,Collections.singletonList(card(101,1000)),10);assertTrue(store.complete(store.claim(8,20).get(0),"READY","",30));assertEquals(0,BrowserCapturePresentation.groups(store.snapshot(capture)).rows("READY").size());}
}
