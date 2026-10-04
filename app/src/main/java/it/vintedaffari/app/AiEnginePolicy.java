package it.vintedaffari.app;

/** AI can withdraw automatic trust; it cannot establish identity or restore an excluded item. */
final class AiEnginePolicy {
 static final long TTL=7L*86400000, BACKOFF=15L*60000;
 private AiEnginePolicy(){}
 static boolean hold(String local,String proposed){
  return ("BASE_GAME".equals(local)||"UNCERTAIN".equals(local))
    && java.util.Arrays.asList("NON_GAME","EXPANSION","BUNDLE","ACCESSORY_COMPONENT").contains(proposed);
 }
 static boolean fresh(long at,long now){return at>0&&at<=now&&now-at<TTL;}
}
