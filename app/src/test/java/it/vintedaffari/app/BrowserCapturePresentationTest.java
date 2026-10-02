package it.vintedaffari.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class BrowserCapturePresentationTest {
 @Test public void everyIdBelongsToOneGroup(){BrowserCaptureStore.Snapshot snapshot=new BrowserCaptureStore.Snapshot();snapshot.captureId=1;snapshot.state="CAPTURING";for(int i=0;i<156;i++){BrowserCaptureStore.Row row=new BrowserCaptureStore.Row();row.state=i<96?"QUEUED":"INCOMPLETE";snapshot.rows.add(row);}BrowserCapturePresentation p=BrowserCapturePresentation.groups(snapshot);assertEquals(156,p.total());assertEquals(96,p.rows("QUEUED").size());assertEquals(60,p.rows("INCOMPLETE").size());assertFalse(p.empty());}
 @Test public void startedCaptureIsNotFalseEmpty(){BrowserCaptureStore.Snapshot s=new BrowserCaptureStore.Snapshot();s.captureId=2;s.state="CAPTURING";assertFalse(BrowserCapturePresentation.groups(s).empty());}
 @Test public void noCaptureIsEmpty(){assertTrue(BrowserCapturePresentation.groups(new BrowserCaptureStore.Snapshot()).empty());}
 @Test public void pendingBggIsNotReady(){BrowserCaptureStore.Snapshot s=new BrowserCaptureStore.Snapshot();s.captureId=2;BrowserCaptureStore.Row row=new BrowserCaptureStore.Row();row.state="BGG_PENDING";s.rows.add(row);assertEquals(1,BrowserCapturePresentation.groups(s).rows("QUEUED").size());assertEquals(0,BrowserCapturePresentation.groups(s).rows("READY").size());}
 @Test public void unknownStateIsVisibleError(){BrowserCaptureStore.Snapshot s=new BrowserCaptureStore.Snapshot();s.captureId=2;BrowserCaptureStore.Row row=new BrowserCaptureStore.Row();row.state="CORRUPT";s.rows.add(row);assertEquals(1,BrowserCapturePresentation.groups(s).rows("TECHNICAL_ERROR").size());}
}
