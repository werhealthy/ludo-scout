package it.vintedaffari.app;
import java.util.*;
/** Counts and drilldowns share this single disjoint projection of persisted candidate rows. */
public final class BrowserCapturePresentation {
 public static final List<String> STATES=Collections.unmodifiableList(Arrays.asList("INCOMPLETE","QUEUED","ANALYZING","FILTERED","REVIEW","READY","TECHNICAL_ERROR"));
 private final Map<String,List<BrowserCaptureStore.Row>> groups=new LinkedHashMap<>();
 public final BrowserCaptureStore.Snapshot snapshot;
 private BrowserCapturePresentation(BrowserCaptureStore.Snapshot snapshot){this.snapshot=snapshot;for(String state:STATES)groups.put(state,new ArrayList<>());for(BrowserCaptureStore.Row row:snapshot.rows){String state=row==null?"TECHNICAL_ERROR":row.state;if("BGG_PENDING".equals(state))state="QUEUED";if("TYPE_UNVERIFIED".equals(state))state="INCOMPLETE";if(!groups.containsKey(state))state="TECHNICAL_ERROR";groups.get(state).add(row);}}
 public static BrowserCapturePresentation groups(BrowserCaptureStore.Snapshot snapshot){if(snapshot==null)throw new IllegalArgumentException("Snapshot assente");return new BrowserCapturePresentation(snapshot);}
 public List<BrowserCaptureStore.Row> rows(String state){List<BrowserCaptureStore.Row> rows=groups.get(state);return rows==null?Collections.emptyList():Collections.unmodifiableList(rows);}
 public int total(){return snapshot.rows.size();}
 public boolean empty(){return snapshot.captureId<=0&&snapshot.rows.isEmpty();}
 public int localWork(){return rows("QUEUED").size()+rows("ANALYZING").size();}
 public static String label(String state){switch(state){case "INCOMPLETE":return "Dati da completare";case "QUEUED":return "In coda";case "ANALYZING":return "In analisi";case "FILTERED":return "Esclusi dai risultati";case "REVIEW":return "Da verificare";case "READY":return "Pronti nel Catalogo";default:return "Errore di elaborazione";}}
}
