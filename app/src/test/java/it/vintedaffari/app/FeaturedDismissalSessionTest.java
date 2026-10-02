package it.vintedaffari.app;
import org.junit.Test;
import static org.junit.Assert.*;

public class FeaturedDismissalSessionTest {
    @Test public void priceOrTitleChangeDoesNotRestoreTheSameListing() {
        FeaturedDismissalSession session=new FeaturedDismissalSession();
        session.dismiss("keltis|kosmos|595","12345","Keltis");
        assertTrue(session.excludes("keltis kartenspiel|kosmos|500","12345"));
        assertFalse(session.excludes("keltis|kosmos|595","67890"));
    }
    @Test public void signatureFallbackSurvivesEnrichmentWithoutHidingAnotherListing() {
        FeaturedDismissalSession session=new FeaturedDismissalSession();
        session.dismiss("legacy",null,"Keltis");
        assertTrue(session.excludes("legacy","12345"));
        assertTrue(session.excludes("vinted:12345","12345"));
        assertFalse(session.excludes("other","67890"));
    }
    @Test public void undoAndClearRestoreListingsAndPreserveEarlierDismissals() {
        FeaturedDismissalSession session=new FeaturedDismissalSession();
        session.dismiss("a","1","First");
        session.dismiss("b","2","Second");
        assertEquals("Second",session.lastTitle());
        session.undo();
        assertTrue(session.excludes("a changed","1"));
        assertFalse(session.excludes("b","2"));
        assertEquals("First",session.lastTitle());
        session.clear();
        assertFalse(session.canUndo());
        assertFalse(session.excludes("a","1"));
    }
    @Test public void redismissingAnAliasHasOneUndoEntry() {
        FeaturedDismissalSession session=new FeaturedDismissalSession();
        session.dismiss("old","1","First");
        session.dismiss("new","1","Updated");
        session.undo();
        assertFalse(session.canUndo());
        assertFalse(session.excludes("old","1"));
    }
}
