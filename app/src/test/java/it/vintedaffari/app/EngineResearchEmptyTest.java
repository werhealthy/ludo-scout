package it.vintedaffari.app;
import org.junit.Test;
import static org.junit.Assert.*;

public class EngineResearchEmptyTest {
    @Test public void emptyResearchDoesNotPretendToHavePipelineNumbers(){
        assertTrue(EngineOverviewPresentation.showResearchEmpty(false,0,0));
        assertFalse(EngineOverviewPresentation.showResearchEmpty(true,0,0));
    }
    @Test public void acquiredRawWorkIsNotAnEmptyResearch(){
        assertFalse(EngineOverviewPresentation.showResearchEmpty(false,1,0));
        assertFalse(EngineOverviewPresentation.showResearchEmpty(false,0,1));
        assertFalse(EngineOverviewPresentation.showResearchEmpty(true,1,1));
    }
}
