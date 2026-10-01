package it.vintedaffari.app;
import java.util.LinkedHashMap;
/** Activity recreation keeps this process session; finishing the app clears it. */
final class FeaturedDismissalSession {
 private final LinkedHashMap<String,String> dismissed=new LinkedHashMap<>();
 synchronized void dismiss(String signature,String title){if(signature==null||signature.trim().isEmpty())return;dismissed.remove(signature);dismissed.put(signature,title);}
 synchronized boolean excludes(String signature){return signature!=null&&!signature.isEmpty()&&dismissed.containsKey(signature);}
 synchronized String lastTitle(){String title="";for(String t:dismissed.values())title=t;return title==null?"":title;}
 synchronized boolean canUndo(){return !dismissed.isEmpty();}
 synchronized void undo(){String last=null;for(String key:dismissed.keySet())last=key;if(last!=null)dismissed.remove(last);}
 synchronized void clear(){dismissed.clear();}
}
