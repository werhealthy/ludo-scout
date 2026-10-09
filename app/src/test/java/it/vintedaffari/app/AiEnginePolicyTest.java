package it.vintedaffari.app;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Exercises the real Android policy class with Gradle's Android test classpath. */
public final class AiEnginePolicyTest {
    @Test public void automaticAiDisagreementHoldsTrust() {
        assertTrue(AiEnginePolicy.hold("BASE_GAME", "NON_GAME"));
        assertTrue(AiEnginePolicy.hold("UNCERTAIN", "NON_GAME"));
        assertTrue(AiEnginePolicy.hold("BASE_GAME", "BUNDLE"));
        assertFalse(AiEnginePolicy.hold("BASE_GAME", "BASE_GAME"));
        assertFalse(AiEnginePolicy.hold("BASE_GAME", "UNKNOWN"));
        assertFalse(AiEnginePolicy.hold("ACCESSORY", "BASE_GAME"));
        assertFalse(AiEnginePolicy.hold("COMPONENTS", "ACCESSORY_COMPONENT"));
    }

    @Test public void evidenceTtlIsStrictAndRejectsFuture() {
        assertTrue(AiEnginePolicy.fresh(1000L, 1001L));
        assertFalse(AiEnginePolicy.fresh(1000L, 1000L + 7L * 86400000L));
        assertFalse(AiEnginePolicy.fresh(1001L, 1000L));
    }
}
