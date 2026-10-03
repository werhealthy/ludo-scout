package it.vintedaffari.app;
import java.util.LinkedHashMap;
/** Bounded read snapshots. A data change rejects workers started before invalidation. */
final class UiSnapshotCache<K,V> {
 private static final class Entry<V>{final V value;final long at;Entry(V value,long at){this.value=value;this.at=at;}}
 private final LinkedHashMap<K,Entry<V>> entries=new LinkedHashMap<>(8,.75f,true);
 private final int capacity;private final long lifetime;private long generation;
 UiSnapshotCache(int capacity,long lifetime){this.capacity=capacity;this.lifetime=lifetime;}
 synchronized long generation(){return generation;}
 synchronized V get(K key,long now){Entry<V> e=entries.get(key);if(e==null)return null;if(now<e.at||now-e.at>=lifetime){entries.remove(key);return null;}return e.value;}
 synchronized boolean put(K key,V value,long now,long token){if(token!=generation||value==null)return false;entries.put(key,new Entry<>(value,now));while(entries.size()>capacity)entries.remove(entries.keySet().iterator().next());return true;}
 synchronized void invalidate(){generation++;entries.clear();}
}
