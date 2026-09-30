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
    @Test public void editionAndDependenceFiltersNeverConflateUnknownWithDependent(){
        assertTrue(HomePresentation.matchesLanguage("IT|DEP","IT"));
        assertFalse(HomePresentation.matchesLanguage("IT|DEP","unknown"));
        assertTrue(HomePresentation.matchesLanguage("IND","unknown"));
        assertTrue(HomePresentation.matchesDependence("IT|DEP","DEP"));
        assertFalse(HomePresentation.matchesDependence("IT|DEP","unknown"));
        assertTrue(HomePresentation.matchesDependence("FR","unknown"));
        assertTrue(HomePresentation.matchesDependence("IT|IND|DEP","unknown"));
    }
    @Test public void flagsNeverInventEditionFromTextIndependence(){
        assertEquals("🇮🇹",HomePresentation.editionFlag("IT|IND"));assertEquals("Italiano",HomePresentation.editionName("IT|IND"));
        assertEquals("",HomePresentation.editionFlag("IND"));assertEquals("Edizione da verificare",HomePresentation.editionName("IND"));
        assertEquals("Non serve la lingua",HomePresentation.dependenceLabel("IND"));assertEquals("Testo da verificare",HomePresentation.dependenceLabel("IT|IND|DEP"));assertEquals("Serve la lingua",HomePresentation.dependenceLabel("FR|DEP"));
    }
}
