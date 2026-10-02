package it.vintedaffari.app;
import java.util.*;
/** Authoritative cross-process snapshot, separate from legacy Accessibility run telemetry. */
public final class BrowserCaptureDiagnostics {
 private BrowserCaptureDiagnostics(){}
 public static String summary(DealDatabase db,MarketStore market){
  try{BrowserCaptureStore store=new BrowserCaptureStore(db);List<BrowserCaptureStore.Header> headers=store.headers(1);MarketStore.RuntimeStatus lane=market.laneStatus("browser");long now=System.currentTimeMillis();String base="build=browser-engine-sqlite-v1;source=sqlite;globalLocalJobs="+store.activeJobs()+";lane="+lane.state+";laneAgeMs="+(lane.updatedAt<=0?-1:Math.max(0,now-lane.updatedAt));if(headers.isEmpty())return base+";capture=NONE";BrowserCapturePresentation groups=BrowserCapturePresentation.groups(store.snapshot(headers.get(0).id));StringBuilder out=new StringBuilder(base).append(";captureId=").append(groups.snapshot.captureId).append(";captureState=").append(groups.snapshot.state).append(";committedUnique=").append(groups.total());for(String state:BrowserCapturePresentation.STATES)out.append(';').append(state).append('=').append(groups.rows(state).size());return out.append(";otherJobs=").append(market.jobsOutsideBrowserCount(groups.snapshot.captureId)).toString();}catch(RuntimeException failure){return "build=browser-engine-sqlite-v1;source=sqlite;state=READ_ERROR;error="+failure.getClass().getSimpleName();}
 }
}
