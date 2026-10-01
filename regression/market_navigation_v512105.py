#!/usr/bin/env python3
"""Execute MainActivity navigation methods with deterministic screen/handler boundaries."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
ui = (root / "app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text()

def method(signature):
    start = ui.index(signature)
    brace = ui.index("{", start)
    depth = 0
    for i in range(brace, len(ui)):
        if ui[i] == "{":
            depth += 1
        elif ui[i] == "}":
            depth -= 1
            if depth == 0:
                return ui[start:i+1]
    raise ValueError("Unclosed production method: " + signature)

methods = "\n".join(method(s) for s in [
    "private void openMarketTab(", "private void navigate(",
    "private void closeDatabaseGame(", "@Override public void onBackPressed("
])
harness = r"""
import java.util.*;
class ScreenBase { public void onBackPressed() {} }
class TextUtils { static boolean isEmpty(String s) { return s==null||s.isEmpty(); } }
public class MarketNavigationRegression extends ScreenBase {
    String tab="catalog", databaseDetailReturnTab="", engineSection="overview", returnTab="";
    String query="catan", databaseQuery="azul", languageFilter="IT";
    int catalogCategory=2, databaseCategory=4, catalogVisible=48;
    long selectedGameId=0, engineDayStart=0, engineEnteredAt=0;
    boolean openingPreset=false;
    Map<String,Integer> tabScrollPositions=new HashMap<>();
    Deque<String> tabHistory=new ArrayDeque<>();
    FakeScroll scroll=new FakeScroll();
    FakeHandler uiUpdates=new FakeHandler();
    static class FakeScroll {
        int y;
        int getScrollY(){return y;}
        void scrollTo(int x,int value){y=value;}
        void smoothScrollTo(int x,int value){y=value;}
    }
    static class FakeHandler {
        Deque<Runnable> delayed=new ArrayDeque<>();
        void postDelayed(Runnable action,long delay){delayed.add(action);}
        void flush(){while(!delayed.isEmpty())delayed.remove().run();}
    }
    void persistTransientUiSession(){}
    void renderNav(){}
    void updateActivityIndicator(){}
    void requestEngineOverviewSnapshot(){}
    void recordAction(String action){}
    // Rendering replaces the content. Restore must occur after this boundary.
    void scheduleRender(long delay){scroll.scrollTo(0,0);}
    static void equal(Object expected,Object actual,String label){
        if(!Objects.equals(expected,actual))throw new AssertionError(label+": expected "+expected+", got "+actual);
    }
    static void retainsSiblingPositions(){
        MarketNavigationRegression n=new MarketNavigationRegression();
        n.scroll.y=480;n.tabScrollPositions.put("database",260);
        n.openMarketTab("database");n.uiUpdates.flush();
        equal("database",n.tab,"destination");equal(260,n.scroll.y,"Games saved position");
        equal(480,n.tabScrollPositions.get("catalog"),"Listings saved position");
        n.openMarketTab("catalog");n.uiUpdates.flush();
        equal(480,n.scroll.y,"Listings return position");equal(0,n.tabHistory.size(),"Sibling tabs do not stack");
    }
    static void retainsIndependentSearchAndFilters(){
        MarketNavigationRegression n=new MarketNavigationRegression();
        n.openMarketTab("database");n.uiUpdates.flush();n.openMarketTab("catalog");n.uiUpdates.flush();
        equal("catan",n.query,"Listing search");equal("azul",n.databaseQuery,"Game search");
        equal(2,n.catalogCategory,"Listing category");equal(4,n.databaseCategory,"Game category");
        equal("IT",n.languageFilter,"Listing edition");equal(48,n.catalogVisible,"Listing pagination");
    }
    static void returnsFromBundleToSource(){
        MarketNavigationRegression n=new MarketNavigationRegression();
        n.tab="database";n.scroll.y=640;n.tabScrollPositions.put("bundles",180);
        n.navigate("bundles");n.uiUpdates.flush();equal(180,n.scroll.y,"Bundle saved position");
        n.scroll.y=420;n.onBackPressed();n.uiUpdates.flush();
        equal("database",n.tab,"Bundle Back destination");equal(640,n.scroll.y,"Game list Back position");
        equal(420,n.tabScrollPositions.get("bundles"),"Bundle Back saved position");
    }
    static void staleTabRestoreDoesNotMoveAnotherView(){
        MarketNavigationRegression n=new MarketNavigationRegression();
        n.tabScrollPositions.put("database",260);n.openMarketTab("database");
        Runnable stale=n.uiUpdates.delayed.remove();n.tab="catalog";n.scroll.y=555;
        stale.run();equal(555,n.scroll.y,"Stale sibling callback");
    }
    static void staleBundleRestoreDoesNotMoveAnotherView(){
        MarketNavigationRegression n=new MarketNavigationRegression();
        n.navigate("bundles");Runnable stale=n.uiUpdates.delayed.remove();
        n.tab="catalog";n.scroll.y=555;stale.run();equal(555,n.scroll.y,"Stale Bundle callback");
    }
    static void returnsFromGameToCatalogPosition(){
        MarketNavigationRegression n=new MarketNavigationRegression();
        n.tab="database";n.selectedGameId=42;n.databaseDetailReturnTab="catalog";
        n.tabScrollPositions.put("catalog",720);n.scroll.y=100;
        n.onBackPressed();n.uiUpdates.flush();
        equal("catalog",n.tab,"Game Back destination");equal(720,n.scroll.y,"Game Back position");
        equal(0L,n.selectedGameId,"Selected detail cleared");
    }
    public static void main(String[] args){
        int failed=0;
        Runnable[] tests={MarketNavigationRegression::retainsSiblingPositions,
            MarketNavigationRegression::retainsIndependentSearchAndFilters,
            MarketNavigationRegression::returnsFromBundleToSource,
            MarketNavigationRegression::staleTabRestoreDoesNotMoveAnotherView,
            MarketNavigationRegression::staleBundleRestoreDoesNotMoveAnotherView,
            MarketNavigationRegression::returnsFromGameToCatalogPosition};
        for(Runnable test:tests){try{test.run();System.out.println("PASS navigation scenario");}
            catch(AssertionError e){failed++;System.out.println("FAIL "+e.getMessage());}}
        if(failed>0)throw new AssertionError(failed+" navigation scenarios failed");
    }
    __PRODUCTION_METHODS__
}
""".replace("__PRODUCTION_METHODS__", methods)
with tempfile.TemporaryDirectory() as temp:
    source = Path(temp) / "MarketNavigationRegression.java"
    source.write_text(harness)
    subprocess.run(["javac", "-d", temp, str(source)], check=True)
    subprocess.run(["java", "-cp", temp, "MarketNavigationRegression"], check=True)
