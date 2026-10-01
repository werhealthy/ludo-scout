package it.vintedaffari.app;
import java.util.ArrayList;
import java.util.List;
/** Only IDs from the current eligible recommendation snapshot can be opened. */
final class LudoPetState {
 private final List<String> ids=new ArrayList<>(); private String selected;
 void refresh(String[] values){ids.clear();for(String id:values)if(id!=null&&!id.isEmpty()&&!ids.contains(id))ids.add(id);if(!ids.contains(selected))selected=null;}
 void select(String id){if(ids.contains(id))selected=id;}
 String selected(){return selected;}
 String suggest(){if(ids.isEmpty()){selected=null;return null;}int index=ids.indexOf(selected);selected=ids.get((index+1)%ids.size());return selected;}
 static boolean animate(boolean enabled,boolean resumed,boolean attached){return enabled&&resumed&&attached;}
}