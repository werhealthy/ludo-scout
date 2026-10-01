package it.vintedaffari.app;
import java.util.ArrayList;
import java.util.List;
/** Only IDs from the current eligible recommendation snapshot can be opened. */
final class LudoPetState {
 private final List<String> ids=new ArrayList<>(); private String selected;
 void refresh(String[] values){ids.clear();for(String id:values)if(id!=null&&!id.isEmpty()&&!ids.contains(id))ids.add(id);if(!ids.contains(selected))selected=null;}
 void select(String id){if(ids.contains(id))selected=id;}
 void refreshGames(String[] signatures,String[] gameIds){
  java.util.Set<String> games=new java.util.HashSet<>();java.util.List<String> unique=new ArrayList<>();
  if(signatures!=null&&gameIds!=null)for(int i=0;i<Math.min(signatures.length,gameIds.length);i++){String game=GamePreferenceState.gameId(gameIds[i]);String signature=signatures[i];if(signature!=null&&!signature.isEmpty()&&game!=null&&games.add(game))unique.add(signature);}
  refresh(unique.toArray(new String[0]));
 }
 boolean eligible(String signature){return ids.contains(signature);}
 String selected(){return selected;}
 String suggest(){if(ids.isEmpty()){selected=null;return null;}int index=ids.indexOf(selected);selected=ids.get((index+1)%ids.size());return selected;}
 static boolean animate(boolean enabled,boolean resumed,boolean attached){return enabled&&resumed&&attached;}
}