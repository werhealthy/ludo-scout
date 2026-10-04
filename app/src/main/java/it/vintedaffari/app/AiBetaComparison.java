package it.vintedaffari.app;

/** Comparison only. A verdict never authorizes publication or catalog mutation. */
public final class AiBetaComparison {
 private AiBetaComparison() {}
 public static String verdict(String local, String proposed) {
  if ("UNCERTAIN".equals(local) || "UNKNOWN".equals(proposed)) return "UNCERTAIN";
  if ("ACCESSORY_COMPONENT".equals(proposed) &&
      ("ACCESSORY".equals(local) || "COMPONENTS".equals(local) || "EMPTY_BOX".equals(local)))
   return "COMPATIBLE_GROUP";
  return local.equals(proposed) ? "AGREEMENT" : "CONFLICT";
 }
}
