package it.vintedaffari.app;

import java.util.LinkedHashMap;

/** Activity recreation keeps this process session; finishing the app clears it. */
final class FeaturedDismissalSession {
    private static final class Dismissal {
        final String signature;
        String itemId;
        final String title;
        Dismissal(String signature,String itemId,String title){this.signature=signature;this.itemId=itemId;this.title=title;}
        boolean matches(String signature,String itemId){
            // Verified listing identity wins: identical titles/prices can belong to other sellers.
            if(!this.itemId.isEmpty()&&!itemId.isEmpty())return this.itemId.equals(itemId);
            return !signature.isEmpty()&&this.signature.equals(signature);
        }
    }
    private final LinkedHashMap<Long,Dismissal> dismissed=new LinkedHashMap<>();
    private long sequence;
    private static String clean(String value){return value==null?"":value.trim();}
    synchronized void dismiss(String signature,String title){dismiss(signature,null,title);}
    synchronized void dismiss(String signature,String itemId,String title){
        signature=clean(signature);itemId=clean(itemId);
        if(signature.isEmpty()&&itemId.isEmpty())return;
        Long existing=null;
        for(java.util.Map.Entry<Long,Dismissal> entry:dismissed.entrySet())
            if(entry.getValue().matches(signature,itemId)){existing=entry.getKey();if(itemId.isEmpty())itemId=entry.getValue().itemId;break;}
        if(existing!=null)dismissed.remove(existing);
        dismissed.put(++sequence,new Dismissal(signature,itemId,title));
    }
    synchronized boolean excludes(String signature){return excludes(signature,null);}
    synchronized boolean excludes(String signature,String itemId){
        signature=clean(signature);itemId=clean(itemId);
        for(Dismissal entry:dismissed.values())if(entry.matches(signature,itemId)){
            // Enrichment may resolve an ID after dismissal; retain it across later signature changes.
            if(entry.itemId.isEmpty()&&!itemId.isEmpty())entry.itemId=itemId;
            return true;
        }
        return false;
    }
    synchronized String lastTitle(){String title="";for(Dismissal entry:dismissed.values())title=entry.title;return title==null?"":title;}
    synchronized boolean canUndo(){return !dismissed.isEmpty();}
    synchronized void undo(){Long last=null;for(Long key:dismissed.keySet())last=key;if(last!=null)dismissed.remove(last);}
    synchronized void clear(){dismissed.clear();}
}
