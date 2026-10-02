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
    "private void dismissMarketDetailStack(", "private void openGameTag(", "private void closeDatabaseGame(", "@Override public void onBackPressed("
])
if "private void finishListingDetail(" in ui:
    finish = method("private void finishListingDetail(")
else:
    original = method("dialog.setOnDismissListener(x->")
    finish = "private void finishListingDetail(Dialog dialog,Dialog parent,String parentSignature)" + original[original.index("{"):]
harness = r"""
import java.util.*;
class ScreenBase { public void onBackPressed() {} }
class TextUtils { static boolean isEmpty(String s) { return s==null||s.isEmpty(); } }
public class MarketNavigationRegression extends ScreenBase {
    String tab="catalog", databaseDetailReturnTab="", engineSection="overview", returnTab="";
    String query="catan", databaseQuery="azul", languageFilter="IT";
    int catalogCategory=2, databaseCategory=4, catalogVisible=48;
    long selectedGameId=0, engineDayStart=0, engineEnteredAt=0;
    boolean openingPreset=false;int databaseVisible=24;String databaseScope="verified";Dialog activeGameOverlay;ArrayList<Dialog> marketDetailDialogs=new ArrayList<>();
    Dialog activeDetailDialog,activeResolutionDialog;String activeDealSignature="";boolean suppressDetailDismissState=false;
    static class Dialog { boolean showing=true; boolean isShowing(){return showing;} Runnable onDismiss;void dismiss(){showing=false;if(onDismiss!=null)onDismiss.run();} }
    Map<Dialog,Object> preparedGameOverlays=new HashMap<>();
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
 void openLudoHome(){tab="companion";}
 int roomPositionRecords,roomEntries;
    void recordLudoRoomPosition(){if("companion".equals(tab))roomPositionRecords++;}
    void prepareLudoNavigation(String value){if("companion".equals(value)&&!"companion".equals(tab))roomEntries++;}
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
    static void relatedListingBackRestoresParent(){
        MarketNavigationRegression n=new MarketNavigationRegression();
        Dialog parent=new Dialog(),child=new Dialog();n.activeDetailDialog=child;n.activeDealSignature="child";
        n.finishListingDetail(child,parent,"source");
        equal(parent,n.activeDetailDialog,"Related listing Back parent");equal("source",n.activeDealSignature,"Related listing Back signature");
    }
    static void unrelatedDismissDoesNotClearCurrentListing(){
        MarketNavigationRegression n=new MarketNavigationRegression();
        Dialog old=new Dialog(),current=new Dialog();n.activeDetailDialog=current;n.activeDealSignature="current";
        n.finishListingDetail(old,null,"");
        equal(current,n.activeDetailDialog,"Unrelated dismiss owner");equal("current",n.activeDealSignature,"Unrelated dismiss signature");
    }
    static void closedParentIsNotRestored(){
        MarketNavigationRegression n=new MarketNavigationRegression();
        Dialog parent=new Dialog(),child=new Dialog();parent.showing=false;n.activeDetailDialog=child;n.activeDealSignature="child";
        n.finishListingDetail(child,parent,"source");
        equal(null,n.activeDetailDialog,"Closed parent");equal("",n.activeDealSignature,"Closed parent signature");
    }
    static void replacementPreservesNewSignature(){
        MarketNavigationRegression n=new MarketNavigationRegression();
        Dialog old=new Dialog(),parent=new Dialog();n.activeDetailDialog=old;n.activeDealSignature="replacement";n.suppressDetailDismissState=true;
        n.finishListingDetail(old,parent,"source");
        equal(null,n.activeDetailDialog,"Replacement clears old owner");equal("replacement",n.activeDealSignature,"Replacement signature");
    }
    static void categoryFromNestedBundleClosesAncestors(){
        MarketNavigationRegression n=new MarketNavigationRegression();
        Dialog source=new Dialog(),bundle=new Dialog(),member=new Dialog();
        n.marketDetailDialogs.addAll(Arrays.asList(source,bundle,member));n.activeDetailDialog=member;n.activeDealSignature="member";
        member.onDismiss=()->n.finishListingDetail(member,source,"source");
        source.onDismiss=()->n.finishListingDetail(source,null,"");
        n.openGameTag("Economic");n.uiUpdates.flush();
        equal(false,source.isShowing(),"Source closed for category");equal(false,bundle.isShowing(),"Bundle closed for category");equal(false,member.isShowing(),"Member closed for category");
        equal("database",n.tab,"Category destination");equal("Economic",n.databaseQuery,"Category query");equal(null,n.activeDetailDialog,"No detail owner");equal("",n.activeDealSignature,"No hidden signature");
    }
    static void motorBackFollowsHistoryHierarchy(){
        MarketNavigationRegression n=new MarketNavigationRegression();
        n.tab="activity";n.engineSection="run";n.engineDayStart=1;
        n.onBackPressed();equal("day",n.engineSection,"Motor run Back");
        n.onBackPressed();equal("history",n.engineSection,"Motor day Back");
        n.onBackPressed();equal("overview",n.engineSection,"Motor history Back");
        n.engineSection="run";n.engineDayStart=0;
        n.onBackPressed();equal("overview",n.engineSection,"Direct run Back");
        for(String section:new String[]{"phase","review","waiting"}){
            n.engineSection=section;n.onBackPressed();equal("overview",n.engineSection,"Work list Back");
        }
    }
    static void roomBackUsesNavigationHooks(){MarketNavigationRegression n=new MarketNavigationRegression();n.tab="companion";n.scroll.y=640;n.tabHistory.push("discover");n.onBackPressed();n.uiUpdates.flush();equal(1,n.roomPositionRecords,"Back records room before leaving");equal(640,n.tabScrollPositions.get("companion"),"Back stores room scroll");n.tab="catalog";n.tabHistory.push("companion");n.onBackPressed();n.uiUpdates.flush();equal(1,n.roomEntries,"Back prepares room before entering");equal(640,n.scroll.y,"Back restores room scroll");}
    public static void main(String[] args){
        int failed=0;
        Runnable[] tests={MarketNavigationRegression::roomBackUsesNavigationHooks,MarketNavigationRegression::retainsSiblingPositions,MarketNavigationRegression::motorBackFollowsHistoryHierarchy,
            MarketNavigationRegression::retainsIndependentSearchAndFilters,
            MarketNavigationRegression::returnsFromBundleToSource,
            MarketNavigationRegression::staleTabRestoreDoesNotMoveAnotherView,
            MarketNavigationRegression::staleBundleRestoreDoesNotMoveAnotherView,
            MarketNavigationRegression::returnsFromGameToCatalogPosition,
            MarketNavigationRegression::relatedListingBackRestoresParent,
            MarketNavigationRegression::unrelatedDismissDoesNotClearCurrentListing,
            MarketNavigationRegression::closedParentIsNotRestored,
            MarketNavigationRegression::replacementPreservesNewSignature,MarketNavigationRegression::categoryFromNestedBundleClosesAncestors};
        for(Runnable test:tests){try{test.run();System.out.println("PASS navigation scenario");}
            catch(AssertionError e){failed++;System.out.println("FAIL "+e.getMessage());}}
        if(failed>0)throw new AssertionError(failed+" navigation scenarios failed");
    }
    __PRODUCTION_METHODS__
    __FINISH_DETAIL__
}
""".replace("__PRODUCTION_METHODS__", methods).replace("__FINISH_DETAIL__", finish)
with tempfile.TemporaryDirectory() as temp:
    source = Path(temp) / "MarketNavigationRegression.java"
    # Compile the real presentation dependency used by extracted MainActivity methods.
    presentation=(root / "app/src/main/java/it/vintedaffari/app/EngineOverviewPresentation.java").read_text()
    source.write_text(harness + "\n" + presentation.replace("package it.vintedaffari.app;", ""))
    subprocess.run(["javac", "-d", temp, str(source)], check=True)
    subprocess.run(["java", "-cp", temp, "MarketNavigationRegression"], check=True)
