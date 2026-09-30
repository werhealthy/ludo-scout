package it.vintedaffari.app;
import org.junit.Test;
import static org.junit.Assert.*;

public class HomePresentationTest {
    @Test public void coverUsesDistinctLandscapeSquarePortraitCompositions(){
        assertEquals(HomePresentation.WIDE,HomePresentation.coverMode(1600,900));
        assertEquals(HomePresentation.SQUARE,HomePresentation.coverMode(1000,1000));
        assertEquals(HomePresentation.TALL,HomePresentation.coverMode(700,1100));
        assertEquals(HomePresentation.SQUARE,HomePresentation.coverMode(0,0));
    }
    @Test public void languageAndTextDependenceAreIndependentFacts(){
        assertEquals("IT · indipendente",HomePresentation.languageLabel("IT|IND"));
        assertEquals("EN · testo",HomePresentation.languageLabel("EN|DEP"));
        assertEquals("FR · testo n/d",HomePresentation.languageLabel("FR"));
        assertEquals("? · indipendente",HomePresentation.languageLabel("IND"));
        assertEquals("? · testo n/d",HomePresentation.languageLabel(null));
        assertEquals("? · testo n/d",HomePresentation.languageLabel("INDONESIAN"));
        assertEquals("IT · testo n/d",HomePresentation.languageLabel("IT|IND|DEP"));
    }
    @org.junit.Test public void catalogCategoriesUseExactBggTokens(){
        org.junit.Assert.assertTrue(DiscoverCategories.matches(3,"Fantasy · Exploration"));
        org.junit.Assert.assertFalse(DiscoverCategories.matches(3,"Space Exploration"));
        org.junit.Assert.assertTrue(DiscoverCategories.matches(5,"Space Exploration"));
        org.junit.Assert.assertFalse(DiscoverCategories.matches(5,null));
    }
}
