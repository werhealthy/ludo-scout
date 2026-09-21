package it.vintedaffari.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public final class DealEvaluatorTest {
    @Test public void shippingCanEraseCheapAskWithoutHidingTheGame() {
        DealEvaluator.Evaluation e=DealEvaluator.evaluate(900,1465,1500,1200,true,null,null,450);
        assertEquals(DealEvaluator.Decision.FAIR,e.decision);
    }

    @Test public void confidentLowBandDealBecomesGreatBuy() {
        DealEvaluator.Evaluation e=DealEvaluator.evaluate(4800,5560,7000,5800,true,null,null,450);
        assertEquals(DealEvaluator.Decision.GREAT_BUY,e.decision);
    }

    @Test public void weakEvidenceCannotCreateGreatBuy() {
        DealEvaluator.Evaluation e=DealEvaluator.evaluate(4800,5560,7000,5800,false,null,null,450);
        assertEquals(DealEvaluator.Decision.GOOD_PRICE,e.decision);
    }

    @Test public void normalUsedPriceStaysFairWhenShippingErasesSaving() {
        DealEvaluator.Evaluation e=DealEvaluator.evaluate(2500,3145,3000,2400,true,null,null,450);
        assertEquals(DealEvaluator.Decision.FAIR,e.decision);
    }

    @Test public void offerIsSolvedFromGoodAllInTargetAndRounded() {
        DealEvaluator.Evaluation e=DealEvaluator.evaluate(3500,4195,4000,3200,true,null,null,450);
        assertEquals(DealEvaluator.Decision.OFFER,e.decision);
        assertEquals(Integer.valueOf(3000),e.suggestedOfferCents);
    }

    @Test public void materiallyOverpricedListingIsInternalReject() {
        DealEvaluator.Evaluation e=DealEvaluator.evaluate(5000,5800,4000,3200,true,null,null,450);
        assertEquals(DealEvaluator.Decision.REJECT,e.decision);
        assertFalse(e.visible());
    }

    @Test public void missingBenchmarkIsInsufficientNotExpensive() {
        DealEvaluator.Evaluation e=DealEvaluator.evaluate(2500,3145,null,null,false,null,null,450);
        assertEquals(DealEvaluator.Decision.INSUFFICIENT_DATA,e.decision);
        assertNull(e.suggestedOfferCents);
    }
}
