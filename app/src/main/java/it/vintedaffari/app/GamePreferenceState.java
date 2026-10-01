package it.vintedaffari.app;
import java.util.HashSet;
import java.util.Set;
/** Durable game identity; favorites never use listing IDs or influence offer eligibility. */
final class GamePreferenceState {
 interface Store {Set<String> favorites();void favorites(Set<String> value);String lastGame();void lastGame(String value);}
 private final Store store;
 GamePreferenceState(Store store){this.store=store;}
 static String gameId(String value){
  if(value==null)return null;String id=value.trim();
  if(!id.matches("[0-9]+"))return null;
  try{long number=Long.parseLong(id);return number>0?Long.toString(number):null;}catch(NumberFormatException error){return null;}
 }
 synchronized Set<String> favorites(){Set<String> result=new HashSet<>();Set<String> stored=store.favorites();if(stored!=null)for(String value:stored){String id=gameId(value);if(id!=null)result.add(id);}return result;}
 synchronized boolean saved(String value){String id=gameId(value);return id!=null&&favorites().contains(id);}
 synchronized boolean toggle(String value){String id=gameId(value);if(id==null)return false;Set<String> values=favorites();boolean saved=values.add(id);if(!saved)values.remove(id);store.favorites(new HashSet<>(values));return saved;}
 synchronized String lastGame(){return gameId(store.lastGame());}
 synchronized void remember(String value){String id=gameId(value);if(id!=null)store.lastGame(id);}
}
