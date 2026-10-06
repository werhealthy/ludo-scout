package it.vintedaffari.app;

/** Schedules the next AI pass at its persisted deadline instead of adding a second cooldown. */
final class AiEngineBackoff {
 private AiEngineBackoff(){}

 static long nextAttemptAt(String state,long now,long persistedNextAt,boolean more,long normalBackoffMs){
  if("WAIT".equals(state))return Math.max(now+10_000L,persistedNextAt);
  return now+(more?10_000L:normalBackoffMs);
 }
}
