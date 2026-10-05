package it.vintedaffari.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

/** Manual comparisons and explicit review acknowledgements in encrypted private storage.
 * Review records inspection only; no catalog, BGG, pricing or language writes.
 */
public final class AiBetaRealDialog {
 private final Activity activity;
 private final AiBetaSettings settings;
 private final TextView status;
 private final Button prepare, analyze, review, inspect;
 private final AlertDialog dialog;
 private AiBetaRealListings.Snapshot snapshot;
 private boolean enabled, validProposal;

 private AiBetaRealDialog(Activity a) {
  activity=a; settings=new AiBetaSettings(a);
  LinearLayout box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);
  int pad=Math.round(20*a.getResources().getDisplayMetrics().density);box.setPadding(pad,pad,pad,pad);box.setBackgroundColor(Color.rgb(27,24,39));
  prepare=new Button(a);prepare.setText("Prepara fino a 8 annunci reali");box.addView(prepare);
  inspect=new Button(a);inspect.setText("Esamina dati locali");inspect.setEnabled(false);box.addView(inspect);
  analyze=new Button(a);analyze.setText("Chiedi proposte AI");analyze.setEnabled(false);box.addView(analyze);
  review=new Button(a);review.setText("Segna confronto come revisionato");review.setEnabled(false);box.addView(review);
  status=new TextView(a);status.setTextColor(Color.rgb(225,223,236));status.setTextSize(15);status.setPadding(0,pad,0,pad);status.setText("Caricamento…");box.addView(status);
  ScrollView scroll=new ScrollView(a);scroll.addView(box);
  dialog=new AlertDialog.Builder(a).setTitle("Annunci reali · confronto AI").setView(scroll).setNegativeButton("Chiudi",null).create();dialog.show();
  prepare.setOnClickListener(v->prepare());inspect.setOnClickListener(v->inspect());analyze.setOnClickListener(v->analyze());review.setOnClickListener(v->review());
  prepare.setEnabled(false);
  AiBetaTestDialog.IO.execute(()->{
   try {
    JSONObject saved=settings.load();enabled=saved.optBoolean("enabled",false);
    JSONArray local=saved.optJSONArray("real_snapshot");
    if(local!=null) {
     AiBetaRealListings.Snapshot old=AiBetaRealListings.restore(local);
     if(AiBetaRealListings.current(a,old)) { show(old,saved);return; }
    }
    update(null,"Prepara un piccolo gruppo dagli annunci già acquisiti. L’AI privata riceve titolo, brand, testo acquisito e fino a 4 foto Vinted; BGG e pricing restano locali. Nessuna modifica al catalogo.\n\n"+(enabled?"Analisi locale-first con fallback configurato dal servizio.":"Prova AI disattivata: puoi preparare gli annunci. Per analizzarli, usa Configura prova nel pannello precedente."),false);
   }catch(Exception e){update(null,"Archivio o configurazione non disponibili. Nessuna analisi avviata.",false);}
  });
 }
 public static AlertDialog show(Activity a){return new AiBetaRealDialog(a).dialog;}
 private boolean visible(){return !activity.isFinishing()&&!activity.isDestroyed()&&dialog.isShowing();}
 private void update(AiBetaRealListings.Snapshot next,String message,boolean proposed) {
  activity.runOnUiThread(()->{if(!visible())return;snapshot=next;validProposal=proposed;status.setText(message);boolean idle=!AiBetaTestDialog.BUSY.get();prepare.setEnabled(idle);inspect.setEnabled(idle&&next!=null&&next.rows.length()>0);analyze.setEnabled(idle&&enabled&&next!=null&&next.rows.length()>0);review.setEnabled(idle&&proposed);});
 }
 private boolean begin(String message) {
  if(!AiBetaTestDialog.BUSY.compareAndSet(false,true))return false;
  prepare.setEnabled(false);inspect.setEnabled(false);analyze.setEnabled(false);review.setEnabled(false);status.setText(message);return true;
 }
 private void inspect() {
  final AiBetaRealListings.Snapshot chosen=snapshot;
  if(chosen==null||!begin("Controllo dei dati locali…"))return;
  AiBetaTestDialog.IO.execute(()->{
   JSONObject saved=null;String error=null;final String[] titles=new String[chosen.local.length()],details=new String[chosen.local.length()];
   try {
    saved=settings.load();
    if(!AiBetaRealListings.current(activity,chosen))error="Gli annunci sono cambiati. Prepara nuovamente il confronto prima di esaminarli.";
    else for(int i=0;i<chosen.local.length();i++){JSONObject row=chosen.local.getJSONObject(i);titles[i]=row.getString("title");details[i]=AiBetaListings.evidence(row);}
   }catch(Exception e){error="Dati locali non disponibili. Nessuna analisi avviata.";}finally{AiBetaTestDialog.BUSY.set(false);}
   if(error!=null){update(null,error,false);return;}
   try {
    show(chosen,saved);
    activity.runOnUiThread(()->{
     if(!visible())return;
     new AlertDialog.Builder(activity).setTitle("Scegli l’annuncio da esaminare").setItems(titles,(d,which)->{
      if(!visible())return;
      TextView text=new TextView(activity);text.setText(details[which]);text.setTextSize(16);text.setTextColor(Color.rgb(225,223,236));text.setTextIsSelectable(true);
      int pad=Math.round(20*activity.getResources().getDisplayMetrics().density);text.setPadding(pad,pad,pad,pad);text.setBackgroundColor(Color.rgb(27,24,39));
      ScrollView scroll=new ScrollView(activity);scroll.addView(text);
      new AlertDialog.Builder(activity).setTitle("Evidenze locali").setView(scroll).setNegativeButton("Chiudi",null).show();
     }).setNegativeButton("Chiudi",null).show();
    });
   }catch(Exception e){update(chosen,"Confronto non disponibile. Catalogo invariato.",false);}
  });
 }
 private void prepare() {
  if(!begin("Preparazione degli annunci locali…"))return;
  AiBetaTestDialog.IO.execute(()->{
   AiBetaRealListings.Snapshot next=null;JSONObject saved=null;String error=null;
   try{saved=settings.load();enabled=saved.optBoolean("enabled",false);next=AiBetaRealListings.prepare(activity);saved.put("real_snapshot",next.local);settings.save(saved);}catch(Exception e){error="Archivio non disponibile. Nessuna analisi avviata.";}finally{AiBetaTestDialog.BUSY.set(false);}
   if(error!=null)update(null,error,false);else try{show(next,saved);}catch(Exception e){update(next,"Proposta salvata non valida. Nessuna modifica al catalogo.",false);}
  });
 }
 private void analyze() {
  final AiBetaRealListings.Snapshot chosen=snapshot;
  if(chosen==null||!begin("Analisi manuale degli annunci preparati…"))return;
  AiBetaTestDialog.IO.execute(()->{
   JSONObject saved=null;String message=null;
   try {
    saved=settings.load();enabled=saved.optBoolean("enabled",false);
    if(!enabled)message="Prova AI disattivata. Nessuna analisi avviata.";
    else if(!AiBetaRealListings.current(activity,chosen))message="Gli annunci sono cambiati. Prepara nuovamente il confronto; nessuna richiesta inviata.";
    else if(cached(saved,chosen)!=null) { /* Reuse valid proposals without transport. */ }
    else {
     String id=AiBetaProtocol.requestId(chosen.key,saved.optString("real_pending_key"),saved.optString("real_pending_id"));
     saved.put("real_pending_key",chosen.key).put("real_pending_id",id);settings.save(saved);
     JSONObject response=AiBetaClient.submit(saved.optString("endpoint"),saved.optString("token"),id,chosen.rows);
     if("PROPOSAL".equals(response.optString("status"))) {
      comparison(response,chosen,false);
      saved.put("real_response",response).put("real_display_key",chosen.key).put("real_display_at",System.currentTimeMillis());
      saved.remove("real_review_key");settings.save(saved);
     }else message=AiBetaTestDialog.display(response,chosen.rows);
    }
    if(!AiBetaRealListings.current(activity,chosen))message="Gli annunci sono cambiati durante il confronto. Prepara nuovamente il gruppo; il catalogo è invariato.";
   }catch(Exception e){message="Risposta non ricevuta o non valida. Il catalogo è invariato. Ripetere questo gruppo conserva l’identificativo; nessun reinvio automatico.";}finally{AiBetaTestDialog.BUSY.set(false);}
   if(message!=null)update(null,message,false);else try{show(chosen,saved);}catch(Exception e){update(chosen,"Proposta non valida. Nessuna modifica al catalogo.",false);}
  });
 }
 private void review() {
  final AiBetaRealListings.Snapshot chosen=snapshot;
  if(!validProposal||chosen==null||!begin("Registrazione della revisione…"))return;
  AiBetaTestDialog.IO.execute(()->{
   JSONObject saved=null;String error=null;
   try {
    saved=settings.load();
    if(!AiBetaRealListings.current(activity,chosen)||cached(saved,chosen)==null)error="Confronto cambiato o scaduto. Prepara nuovamente gli annunci prima di revisionarli.";
    else{saved.put("real_review_key",chosen.localKey()).put("real_review_at",System.currentTimeMillis());settings.save(saved);}
   }catch(Exception e){error="Revisione non salvata. Catalogo invariato.";}finally{AiBetaTestDialog.BUSY.set(false);}
   if(error!=null)update(null,error,false);else try{show(chosen,saved);}catch(Exception e){update(null,"Confronto non disponibile.",false);}
  });
 }
 private static JSONObject cached(JSONObject saved,AiBetaRealListings.Snapshot chosen)throws Exception {
  if(!AiBetaProtocol.reusableDisplay(chosen.key,saved.optString("real_display_key"),saved.optLong("real_display_at"),System.currentTimeMillis()))return null;
  JSONObject response=saved.optJSONObject("real_response");
  if(response==null)return null;
  if(!AiBetaProtocol.MODEL.equals(response.optString("model"))||!AiBetaProtocol.CONTRACT.equals(response.optString("contract")))throw new Exception("stale contract");
  comparison(response,chosen,false);return response;
 }
 private void show(AiBetaRealListings.Snapshot chosen,JSONObject saved)throws Exception {
  JSONObject response=cached(saved,chosen);
  if(response!=null){update(chosen,comparison(response,chosen,chosen.localKey().equals(saved.optString("real_review_key"))),true);return;}
  StringBuilder text=new StringBuilder("Annunci preparati: ").append(chosen.local.length()).append(" / 8\n\n").append(AiBetaListings.display(chosen.local,null));
  if(!enabled)text.append("\nProva AI disattivata.");
  update(chosen,text.toString(),false);
 }
 static String comparison(JSONObject response,AiBetaRealListings.Snapshot chosen,boolean reviewed)throws Exception {
  String body=AiBetaListings.display(chosen.local,response);
  return (reviewed?"Revisionato: registrata soltanto la lettura del confronto. Nessuna correzione applicata.\n\n":"")+body;
 }
}
