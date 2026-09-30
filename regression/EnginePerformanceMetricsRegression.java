import it.vintedaffari.app.EnginePerformanceMetrics;

public final class EnginePerformanceMetricsRegression {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        EnginePerformanceMetrics metrics = new EnginePerformanceMetrics();
        metrics.sample(10L, 500L, 1_000L, 0L, false, false, 2, 0, "WAITING", "PACING");
        metrics.sample(10L, 500L, 9_000L, 0L, false, false, 2, 0, "WAITING", "PACING");
        metrics.sample(10L, 500L, 17_000L, 1L, true, false, 2, 1, "CLAIMING", "2 ready");
        metrics.sample(10L, 500L, 25_000L, 1L, true, true, 0, 1, "ACTIVE", "completed");
        metrics.sample(10L, 500L, 33_000L, 1L, true, true, 0, 0, "IDLE", "settled");
        metrics.sample(0L, 0L, 41_000L, 0L, false, false, 1, 0, "WAITING", "REMOTE_LIMIT");
        metrics.sample(0L, 0L, 49_000L, 0L, false, false, 1, 0, "WAITING", "database occupato · riprovo");

        String summary = metrics.summary(25_000L);
        check(summary.contains("sampleCount=1"), "one completed run must be counted");
        check(summary.contains("firstResultN=1;firstResultMedianMs=16500;firstResultWorstMs=16500"),
                "first result latency must be scroll-end to first result");
        check(summary.contains("completionN=1;completionMedianMs=24500;completionWorstMs=24500"),
                "completion latency must be scroll-end to settled run");
        check(summary.contains("pacingMs=8000"), "PACING time must be separate");
        check(summary.contains("gateMs=8000"), "non-pacing rate-limit gates must be separate");
        check(summary.contains("sqliteBusyMs=8000"), "SQLite busy wait must be separate");
        check(summary.contains("processingMs=16000"), "lease processing time must be separate");
        check(summary.contains("noProgressWorstMs=8000"), "unchanged run progress must be measurable");

        EnginePerformanceMetrics restored = EnginePerformanceMetrics.restore(metrics.serialize());
        check(restored.summary(25_000L).equals(summary), "persisted diagnostic state must round-trip");

        EnginePerformanceMetrics capped = new EnginePerformanceMetrics();
        capped.sample(0L, 0L, 1_000L, 0L, false, false, 1, 0, "WAITING", "PACING");
        capped.sample(0L, 0L, 61_000L, 0L, false, false, 1, 0, "WAITING", "PACING");
        check(capped.summary(61_000L).contains("pacingMs=12000"),
                "a missed pulse must not count the whole service/process downtime as queue wait");
        check(capped.summary(61_000L).contains("unobservedMs=48000"),
                "time outside the capped measurement interval must remain visible as unobserved");
        check(EnginePerformanceMetrics.restore(capped.serialize()).summary(61_000L).equals(capped.summary(61_000L)),
                "unobserved duration must survive persistence");

        EnginePerformanceMetrics activeFirst = new EnginePerformanceMetrics();
        activeFirst.sample(100L, 500L, 1_000L, 0L, false, false, 1, 0, "WAITING", "PACING");
        activeFirst.sample(100L, 500L, 9_000L, 1L, true, false, 1, 0, "CLAIMING", "result ready");
        check(activeFirst.summary(9_000L).contains("firstResultN=1;firstResultMedianMs=8500;firstResultWorstMs=8500"),
                "first-result latency must be visible before the run completes");
        check(activeFirst.summary(9_000L).contains("completionN=0;completionMedianMs=-1;completionWorstMs=-1"),
                "an active run must not be counted as completed");
        System.out.println("PASS engine timing separates pacing, processing, first result, and completion");
        System.out.println("PASS persisted timing survives restart and caps missed-pulse gaps");
    }
}
