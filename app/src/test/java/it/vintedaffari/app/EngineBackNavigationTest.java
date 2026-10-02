package it.vintedaffari.app;
import org.junit.Test;
import static org.junit.Assert.*;

public class EngineBackNavigationTest {
    @Test public void historyDrilldownReturnsOneLevelAtATime(){
        assertEquals("day",EngineOverviewPresentation.backSection("run",true));
        assertEquals("history",EngineOverviewPresentation.backSection("day",true));
        assertEquals("overview",EngineOverviewPresentation.backSection("history",false));
    }
    @Test public void directRunAndWorkListsReturnToOverview(){
        assertEquals("overview",EngineOverviewPresentation.backSection("run",false));
        for(String section:new String[]{"phase","review","waiting"})
            assertEquals("overview",EngineOverviewPresentation.backSection(section,false));
    }
}
