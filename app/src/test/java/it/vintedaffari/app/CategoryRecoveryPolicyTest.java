package it.vintedaffari.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class CategoryRecoveryPolicyTest {
    @Test public void recognizesStructuredBoardGameCategoriesButNotLookalikes() {
        assertTrue(ListingClassifier.isExplicitBoardGameCategory("Giochi da tavolo > Strategia"));
        assertTrue(ListingClassifier.isExplicitBoardGameCategory("Jeux de société"));
        assertFalse(ListingClassifier.isExplicitBoardGameCategory("Giochi da tavolo > Accessori"));
        assertFalse(ListingClassifier.isExplicitBoardGameCategory("Board games > Expansions"));
        assertFalse(ListingClassifier.isExplicitBoardGameCategory("Juegos de mesa > Accesorios"));
        assertFalse(ListingClassifier.isExplicitBoardGameCategory("Juegos de mesa > Expansiones"));
        assertFalse(ListingClassifier.isExplicitBoardGameCategory("Juegos de mesa > Repuestos"));
        assertFalse(ListingClassifier.isExplicitBoardGameCategory("Jeux de société > Accessoires"));
        assertFalse(ListingClassifier.isExplicitBoardGameCategory("Jeux de société > Extensions"));
        assertFalse(ListingClassifier.isExplicitBoardGameCategory("Jeux de société > Pièces de rechange"));
        assertFalse(ListingClassifier.isExplicitBoardGameCategory("Giochi da tavolo > Ricambio"));
        assertFalse(ListingClassifier.isExplicitBoardGameCategory("Musica"));
        assertFalse(ListingClassifier.isExplicitBoardGameCategory("Categoria non disponibile"));
    }

    @Test public void missingRatingWaitsAndDoesNotClearTheTypeHold() {
        assertEquals("PENDING_ANALYSIS", CategoryRecoveryPolicy.enrichmentState(true, null, false));
        assertFalse(CategoryRecoveryPolicy.mayClearTypeHold(true, null));
        assertFalse(CategoryRecoveryPolicy.mayClearTypeHold(false, 8.2));
    }

    @Test public void ratingBelowSixStaysOutOfTheCatalog() {
        assertEquals("LOCAL_ONLY", CategoryRecoveryPolicy.enrichmentState(true, 5.99, true));
        assertFalse(CategoryRecoveryPolicy.mayClearTypeHold(true, 5.99));
    }

    @Test public void qualifiedListingUsesExactIdentityBeforeChoosingLinkState() {
        assertEquals("CORE_COMPLETE", CategoryRecoveryPolicy.enrichmentState(true, 6.0, true));
        assertEquals("DEFERRED_LINK", CategoryRecoveryPolicy.enrichmentState(true, 8.2, false));
        assertTrue(CategoryRecoveryPolicy.mayClearTypeHold(true, 6.0));
    }

    @Test public void unconfirmedCategoryCannotRestoreListingOrDeal() {
        assertNull(CategoryRecoveryPolicy.enrichmentState(false, 9.0, true));
        assertFalse(CategoryRecoveryPolicy.mayClearTypeHold(false, 9.0));
    }
}