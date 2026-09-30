package it.vintedaffari.app;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small diagnostic-only accumulator. It stores timings and run timestamps, never listing data. */
public final class EnginePerformanceMetrics {
    private static final int VERSION = 5;
    private static final int MAX_HISTORY = 20;
    private static final int MAX_ACTIVE_RUNS = 80;
    private static final long MAX_SAMPLE_GAP_MS = 12_000L;

    private static final class Run {
        long startAt;
        long scrollEndAt;
        long firstResultAt;
        long lastSampleAt;
        long progressMarker;
        long noProgressMs;

        Run(long start, long end, long progress) { startAt = start; scrollEndAt = end; progressMarker = progress; }
    }

    private static final class FinishedRun {
        long startAt;
        long firstResultMs;
        long completionMs;
        long noProgressMs;
        FinishedRun(long start, long first, long completion, long noProgress) { startAt = start; firstResultMs = first; completionMs = completion; noProgressMs = noProgress; }
    }

    private final LinkedHashMap<Long, Run> activeRuns = new LinkedHashMap<>();
    private final ArrayDeque<FinishedRun> history = new ArrayDeque<>();
    private long trackingStartedAt;
    private long lastSampleAt;
    private long lastAttemptAt;
    private long lastFailureAt;
    private long attemptCount;
    private long successfulSampleCount;
    private long sampleFailureCount;
    private String lastFailureClass="";
    private long pacingMs;
    private long gateMs;
    private long sqliteBusyMs;
    private long runnableMs;
    private long processingMs;
    private long otherMs;
    private long unobservedMs;

    public synchronized void recordAttempt(long now) {
        attemptCount++;
        lastAttemptAt=Math.max(0L,now);
    }

    /** Stores only the exception class, never its message or any listing data. */
    public synchronized void recordFailure(long now, Throwable failure) {
        sampleFailureCount++;
        lastFailureAt=Math.max(0L,now);
        lastFailureClass=failure==null?"Unknown":failure.getClass().getSimpleName();
    }

    /** Returns true when a first-result or completion event needs an immediate persistence flush. */
    public synchronized boolean sample(long runStartAt, long scrollEndAt, long now, long progressMarker,
            boolean hasResult, boolean complete, int due, int processing,
            String laneState, String laneDetail) {
        successfulSampleCount++;
        if (trackingStartedAt <= 0L) trackingStartedAt = Math.max(1L, now);
        long elapsed = lastSampleAt <= 0L ? 0L : Math.max(0L, now - lastSampleAt);
        long delta = Math.min(MAX_SAMPLE_GAP_MS, elapsed);
        // Do not mistake delayed pulses or app downtime for engine work; report the gap explicitly.
        unobservedMs += Math.max(0L, elapsed - delta);
        String state = laneState == null ? "" : laneState;
        String detail = laneDetail == null ? "" : laneDetail;
        String reason = detail.toUpperCase(java.util.Locale.ROOT);
        boolean databaseWait = due > 0 && "WAITING".equals(state) &&
                (reason.contains("DATABASE OCCUPATO") || reason.contains("SQLITE_BUSY") || reason.contains("COORDINATOR_BUSY"));
        boolean gateWait = due > 0 && "WAITING".equals(state) &&
                (reason.contains("PAC") || reason.contains("LIMIT") || reason.contains("BUDGET") || reason.contains("COORDINATOR_BUSY"));
        if (processing > 0) processingMs += delta;
        else if (databaseWait) sqliteBusyMs += delta;
        else if (gateWait && reason.contains("PAC")) pacingMs += delta;
        else if (gateWait) gateMs += delta;
        else if (due > 0) runnableMs += delta;
        else otherMs += delta;
        lastSampleAt = Math.max(0L, now);

        boolean changed = false;
        // Existing observation sessions can be reloaded from SQLite after install/startup.
        // Their endAt may be days old, so only time sessions anchored at or just before
        // the start of this measurement epoch (one pulse of grace) are eligible.
        boolean freshAnchor = scrollEndAt > 0L && scrollEndAt >= trackingStartedAt - MAX_SAMPLE_GAP_MS;
        if (runStartAt > 0L && freshAnchor) {
            if (wasCompleted(runStartAt)) return false;
            Run run = activeRuns.get(runStartAt);
            if (run == null) {
                run = new Run(runStartAt, scrollEndAt, progressMarker);
                activeRuns.put(runStartAt, run);
                trimActiveRuns();
            } else if (scrollEndAt > 0L) run.scrollEndAt = scrollEndAt;
            long runDelta=run.lastSampleAt<=0L?0L:Math.min(MAX_SAMPLE_GAP_MS,Math.max(0L,now-run.lastSampleAt));
            if(run.progressMarker!=progressMarker){run.progressMarker=progressMarker;run.noProgressMs=0L;}
            else run.noProgressMs+=runDelta;
            run.lastSampleAt=Math.max(0L,now);
            if (hasResult && run.firstResultAt <= 0L) { run.firstResultAt = Math.max(now, run.scrollEndAt); changed = true; }
            if (complete) {
                long first = run.firstResultAt > 0L ? Math.max(0L, run.firstResultAt - run.scrollEndAt) : -1L;
                long completion = run.scrollEndAt > 0L ? Math.max(0L, now - run.scrollEndAt) : -1L;
                history.addLast(new FinishedRun(runStartAt, first, completion, run.noProgressMs));
                while (history.size() > MAX_HISTORY) history.removeFirst();
                activeRuns.remove(runStartAt);
                changed = true;
            }
        }
        return changed;
    }

    private boolean wasCompleted(long startAt) {
        for (FinishedRun run : history) if (run.startAt == startAt) return true;
        return false;
    }

    private void trimActiveRuns() {
        while (activeRuns.size() > MAX_ACTIVE_RUNS) {
            Long first = activeRuns.keySet().iterator().next();
            activeRuns.remove(first);
        }
    }

    public synchronized int sampleCount() { return history.size(); }

    public synchronized String summary(long now) {
        List<Long> first = new ArrayList<>();
        List<Long> completion = new ArrayList<>();
        long noProgressWorst=0L;
        for (FinishedRun run : history) {
            if (run.firstResultMs >= 0L) first.add(run.firstResultMs);
            if (run.completionMs >= 0L) completion.add(run.completionMs);
            noProgressWorst=Math.max(noProgressWorst,run.noProgressMs);
        }
        for(Run run:activeRuns.values()) {
            noProgressWorst=Math.max(noProgressWorst,run.noProgressMs);
            if(run.firstResultAt>0L && run.scrollEndAt>0L) first.add(Math.max(0L,run.firstResultAt-run.scrollEndAt));
        }
        Collections.sort(first);
        Collections.sort(completion);
        return "build=engine-performance-v5;sampleCount=" + history.size() +
                ";samplingAttempts="+attemptCount+";successfulSamples="+successfulSampleCount+";sampleFailures="+sampleFailureCount+
                ";lastAttemptAgeMs="+(lastAttemptAt<=0L?-1L:Math.max(0L,now-lastAttemptAt))+";lastFailureClass="+lastFailureClass+
                ";lastFailureAgeMs="+(lastFailureAt<=0L?-1L:Math.max(0L,now-lastFailureAt))+
                ";firstResultN=" + first.size() + ";firstResultMedianMs=" + median(first) + ";firstResultWorstMs=" + worst(first) +
                ";completionN=" + completion.size() + ";completionMedianMs=" + median(completion) + ";completionWorstMs=" + worst(completion) +
                ";noProgressWorstMs="+noProgressWorst+
                ";scrollEndAnchor=last_observation_at;sampleWindow=last20;pacingMs=" + pacingMs + ";gateMs=" + gateMs + ";sqliteBusyMs=" + sqliteBusyMs + ";runnableMs=" + runnableMs + ";processingMs=" + processingMs + ";otherMs=" + otherMs + ";unobservedMs=" + unobservedMs +
                ";activeRuns=" + activeRuns.size() + ";lastSampleAgeMs=" + (lastSampleAt <= 0L ? -1L : Math.max(0L, now - lastSampleAt));
    }

    private static long median(List<Long> values) {
        if (values.isEmpty()) return -1L;
        int mid = values.size() / 2;
        return values.size() % 2 == 1 ? values.get(mid) : (values.get(mid - 1) + values.get(mid)) / 2L;
    }

    private static long worst(List<Long> values) { return values.isEmpty() ? -1L : values.get(values.size() - 1); }

    /** Compact key/value persistence in the existing cross-process diagnostics row; no schema change. */
    public synchronized String serialize() {
        StringBuilder out = new StringBuilder("v=").append(VERSION).append(";epoch=").append(trackingStartedAt).append(";last=").append(lastSampleAt)
                .append(";attemptAt=").append(lastAttemptAt).append(";failureAt=").append(lastFailureAt)
                .append(";attempts=").append(attemptCount).append(";successfulSamples=").append(successfulSampleCount)
                .append(";sampleFailures=").append(sampleFailureCount).append(";failureClass=").append(lastFailureClass)
                .append(";pacing=").append(pacingMs).append(";gate=").append(gateMs).append(";sqliteBusy=").append(sqliteBusyMs).append(";runnable=").append(runnableMs)
                .append(";processing=").append(processingMs).append(";other=").append(otherMs).append(";unobserved=").append(unobservedMs).append(";active=");
        boolean comma = false;
        for (Map.Entry<Long, Run> entry : activeRuns.entrySet()) {
            if (comma) out.append(',');
            Run run = entry.getValue();
            out.append(run.startAt).append(':').append(run.scrollEndAt).append(':').append(run.firstResultAt).append(':').append(run.lastSampleAt).append(':').append(run.progressMarker).append(':').append(run.noProgressMs);
            comma = true;
        }
        out.append(";history=");
        comma = false;
        for (FinishedRun run : history) {
            if (comma) out.append(',');
            out.append(run.startAt).append(':').append(run.firstResultMs).append(':').append(run.completionMs).append(':').append(run.noProgressMs);
            comma = true;
        }
        return out.toString();
    }

    public static EnginePerformanceMetrics restore(String serialized) {
        EnginePerformanceMetrics out = new EnginePerformanceMetrics();
        if (serialized == null || serialized.isEmpty()) return out;
        Map<String, String> fields = new LinkedHashMap<>();
        for (String item : serialized.split(";")) {
            int cut = item.indexOf('=');
            if (cut > 0) fields.put(item.substring(0, cut), item.substring(cut + 1));
        }
        if (parse(fields.get("v"), 0L) != VERSION) return out;
        out.trackingStartedAt = parse(fields.get("epoch"), 0L);
        out.lastSampleAt = parse(fields.get("last"), 0L);
        out.lastAttemptAt=parse(fields.get("attemptAt"),0L);
        out.lastFailureAt=parse(fields.get("failureAt"),0L);
        out.attemptCount=parse(fields.get("attempts"),0L);
        out.successfulSampleCount=parse(fields.get("successfulSamples"),0L);
        out.sampleFailureCount=parse(fields.get("sampleFailures"),0L);
        out.lastFailureClass=fields.getOrDefault("failureClass","");
        out.pacingMs = parse(fields.get("pacing"), 0L);
        out.gateMs = parse(fields.get("gate"), 0L);
        out.sqliteBusyMs = parse(fields.get("sqliteBusy"), 0L);
        out.runnableMs = parse(fields.get("runnable"), 0L);
        out.processingMs = parse(fields.get("processing"), 0L);
        out.otherMs = parse(fields.get("other"), 0L);
        out.unobservedMs = parse(fields.get("unobserved"), 0L);
        String active = fields.get("active");
        if (active != null && !active.isEmpty()) for (String row : active.split(",")) {
            String[] parts = row.split(":", -1);
            if (parts.length == 6) {
                long start = parse(parts[0], 0L);
                if (start > 0L) out.activeRuns.put(start, new Run(start, parse(parts[1], 0L), parse(parts[4], 0L)));
                Run run = out.activeRuns.get(start);
                if (run != null) { run.firstResultAt=parse(parts[2],0L);run.lastSampleAt=parse(parts[3],0L);run.noProgressMs=parse(parts[5],0L); }
            }
        }
        String history = fields.get("history");
        if (history != null && !history.isEmpty()) for (String row : history.split(",")) {
            String[] parts = row.split(":", -1);
            if (parts.length == 4) out.history.addLast(new FinishedRun(parse(parts[0], 0L), parse(parts[1], -1L), parse(parts[2], -1L), parse(parts[3], 0L)));
        }
        while (out.history.size() > MAX_HISTORY) out.history.removeFirst();
        out.trimActiveRuns();
        return out;
    }

    private static long parse(String value, long fallback) {
        try { return Long.parseLong(value); } catch (Throwable ignored) { return fallback; }
    }
}
