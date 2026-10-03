package it.vintedaffari.app;

/** Same stage order as the five journey nodes; idle has no target. */
final class LudoJourneyFocus {
    static int stage(int activeMask) {
        for (int i = 0; i < 5; i++) if ((activeMask & (1 << i)) != 0) return i;
        return -1;
    }
    static float x(int stage) {
        return stage < 0 ? 0 : (float)Math.cos(Math.toRadians(-90 + stage * 72));
    }
    static float y(int stage) {
        return stage < 0 ? 0 : (float)Math.sin(Math.toRadians(-90 + stage * 72));
    }
}
