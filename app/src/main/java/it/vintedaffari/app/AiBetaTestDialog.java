package it.vintedaffari.app;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
/** Controlled-fixture test and entry point to the read-only real announcement comparison. */
public final class AiBetaTestDialog {
 static final ExecutorService IO=Executors.newSingleThreadExecutor();
 static final AtomicBoolean BUSY=new AtomicBoolean();
 private AiBetaTestDialog(){}
 private static boolean visible(Activity a,AlertDialog d){return !a.isFinishing()&&!a.isDestroyed()&&d.isShowing();}
 private static TextView label(Activity a,String s){TextView t=new TextView(a);t.setText(s);t.setTextColor(Color.rgb(225,223,236));t.setTextSize(15);t.setPadding(0,12,0,12);return t;}
 public static AlertDialog show(Activity a){
  int pad=Math.round(20*a.getResources().getDisplayMetrics().density);LinearLayout box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(pad,pad,pad,pad);box.setBackgroundColor(Color.rgb(27,24,39));
  ScrollView scroll=new ScrollView(a);scroll.addView(box);TextView status=label(a,"Caricamento della prova…");box.addView(status);
  Button configure=new Button(a);configure.setText("Configura prova");box.addView(configure);Button run=new Button(a);run.setText("Test AI su 8 annunci");run.setEnabled(false);box.addView(run);
  AlertDialog dialog=new AlertDialog.Builder(a).setTitle("Prova AI · beta").setView(scroll).setNegativeButton("Chiudi",null).create();dialog.show();
  Button real=new Button(a);real.setText("Confronta annunci reali");box.addView(real);
  real.setOnClickListener(v->{dialog.dismiss();AiBetaRealDialog.show(a);});
  AiBetaSettings settings=new AiBetaSettings(a);
  IO.execute(()->{try{JSONObject saved=settings.load();String key=AiBetaProtocol.fingerprint(fixture(a).toString(),AiBetaProtocol.MODEL,AiBetaProtocol.CONTRACT);String previous=AiBetaProtocol.reusableDisplay(key,saved.optString("last_display_key"),saved.optLong("last_display_at"),System.currentTimeMillis())?saved.optString("last_display"):"La prova usa 8 annunci controllati. Le proposte non modificano il catalogo.";a.runOnUiThread(()->{if(!visible(a,dialog))return;status.setText(previous);run.setEnabled(saved.optBoolean("enabled",false)&&!BUSY.get());});}catch(Exception e){a.runOnUiThread(()->{if(visible(a,dialog))status.setText("Configurazione non leggibile. Configura nuovamente la prova.");});}});
  configure.setOnClickListener(v->IO.execute(()->{try{JSONObject saved=settings.load();a.runOnUiThread(()->{if(visible(a,dialog))configure(a,settings,saved,run,status);});}catch(Exception e){a.runOnUiThread(()->{if(visible(a,dialog))status.setText("Configurazione non leggibile.");});}}));
  run.setOnClickListener(v->{if(!BUSY.compareAndSet(false,true))return;run.setEnabled(false);configure.setEnabled(false);status.setText("Analisi degli 8 annunci di prova…");
   IO.execute(()->{String display;try{JSONObject saved=settings.load();if(!saved.optBoolean("enabled",false))throw new Exception("disabled");JSONArray rows=fixture(a);String key=AiBetaProtocol.fingerprint(rows.toString(),AiBetaProtocol.MODEL,AiBetaProtocol.CONTRACT);String id=AiBetaProtocol.requestId(key,saved.optString("pending_key"),saved.optString("pending_id"));saved.remove("pending_body");saved.put("pending_key",key).put("pending_id",id);settings.save(saved);
    JSONObject response=AiBetaClient.submit(saved.optString("endpoint"),saved.optString("token"),id,rows);display=display(response,rows);saved.put("last_display",display).put("last_display_key",key).put("last_display_at",System.currentTimeMillis());settings.save(saved);
   }catch(Exception e){display="Risposta non ricevuta o configurazione non valida. Ripetere la prova usa lo stesso identificativo, senza reinvio automatico.";}finally{BUSY.set(false);}
   final String shown=display;a.runOnUiThread(()->{if(visible(a,dialog)){status.setText(shown);run.setEnabled(true);configure.setEnabled(true);}});
  });});
  return dialog;
 }
 private static JSONArray fixture(Activity a)throws Exception{try(InputStream in=a.getAssets().open("ai-beta/sample8.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[2048];int n;while((n=in.read(b))!=-1){if(out.size()+n>4096)throw new Exception("fixture too large");out.write(b,0,n);}return new JSONArray(out.toString("UTF-8"));}}
 static String display(JSONObject response,JSONArray source)throws Exception{
  String state=response.optString("status");if(!"PROPOSAL".equals(state)){
   switch(state){case "BUDGET_BLOCKED":return "Limite del test raggiunto. Nessuna nuova analisi avviata.";case "DISABLED":return "Il servizio di prova è spento.";case "UNAUTHORIZED":return "Accesso non autorizzato. Controlla la configurazione.";case "IN_FLIGHT":return "La richiesta è già registrata. Nessuna nuova chiamata avviata.";case "FAILED":return "Il servizio AI non ha restituito una proposta valida. Il tentativo resta conteggiato.";default:return "Prova non disponibile. Nessuna modifica al catalogo.";}
  }
  JSONArray results=response.getJSONArray("records");if(results.length()!=source.length())throw new Exception("invalid proposals");StringBuilder text=new StringBuilder("Proposte AI · catalogo invariato\n");java.util.HashSet<Long> seen=new java.util.HashSet<>();
  for(int i=0;i<results.length();i++){JSONObject r=results.getJSONObject(i);if(!r.has("apply_authorized")||r.getBoolean("apply_authorized")||!r.has("bgg_verified")||r.getBoolean("bgg_verified")||!"UNKNOWN".equals(r.getString("language")))throw new Exception("invalid proposals");long id=r.getLong("listing_id");if(!seen.add(id))throw new Exception("duplicate proposal");String title=null;for(int j=0;j<source.length();j++)if(source.getJSONObject(j).getLong("listing_id")==id)title=source.getJSONObject(j).getString("title");if(title==null)throw new Exception("unknown proposal");text.append("\n").append(title).append("\n").append(category(r.getString("proposed_type"))).append(" · da verificare\n").append(r.getString("evidence")).append("\n");}
  JSONObject budget=response.optJSONObject("budget");if(budget!=null)text.append("\nChiamate del mese: ").append(budget.optInt("calls_reserved")).append(" / 100");return text.toString();
 }
 static String category(String type)throws Exception{switch(type){case "BASE_GAME":return "Gioco base";case "EXPANSION":return "Espansione";case "BUNDLE":return "Bundle";case "ACCESSORY_COMPONENT":return "Accessorio o componente";case "NON_GAME":return "Non gioco";case "UNKNOWN":return "Da chiarire";default:throw new Exception("invalid category");}}
 private static void configure(Activity a,AiBetaSettings settings,JSONObject saved,Button run,TextView status){
  LinearLayout box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);int p=Math.round(20*a.getResources().getDisplayMetrics().density);box.setPadding(p,p,p,p);EditText endpoint=new EditText(a);endpoint.setSingleLine(true);endpoint.setHint("Indirizzo del servizio di prova");endpoint.setText(saved.optString("endpoint"));box.addView(endpoint);EditText token=new EditText(a);token.setSingleLine(true);token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);token.setHint(saved.has("token")?"Token salvato · vuoto per conservarlo":"Token personale della prova");box.addView(token);CheckBox enabled=new CheckBox(a);enabled.setText("Abilita prova AI manuale");enabled.setChecked(saved.optBoolean("enabled",false));box.addView(enabled);
  ScrollView configScroll=new ScrollView(a);configScroll.addView(box);AlertDialog config=new AlertDialog.Builder(a).setTitle("Configura prova AI").setView(configScroll).setNegativeButton("Annulla",null).setPositiveButton("Salva",null).create();config.show();config.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String url=endpoint.getText().toString().trim(),secret=token.getText().toString().trim();if(!AiBetaProtocol.validEndpoint(url)||(!url.equals(saved.optString("endpoint"))&&secret.isEmpty())){endpoint.setError("Usa l’indirizzo HTTPS del servizio e il relativo token.");return;}if(secret.isEmpty())secret=saved.optString("token");if(secret.length()<16||secret.length()>500){token.setError("Inserisci il token personale della prova.");return;}final String value=secret;final boolean on=enabled.isChecked();token.setText("");config.dismiss();IO.execute(()->{try{JSONObject current=settings.load();if(!url.equals(current.optString("endpoint"))||!value.equals(current.optString("token"))){for(String k:new String[]{"pending_body","pending_key","pending_id","last_display","last_display_key","last_display_at","real_snapshot","real_pending_key","real_pending_id","real_response","real_response_key","real_response_at","real_review_key","real_review_at"})current.remove(k);}current.put("endpoint",url).put("token",value).put("enabled",on);settings.save(current);a.runOnUiThread(()->{if(!a.isDestroyed()&&status.isAttachedToWindow()){status.setText(on?"Configurazione salvata. Puoi avviare la prova manuale.":"Prova AI disattivata.");run.setEnabled(on&&!BUSY.get());}});}catch(Exception e){a.runOnUiThread(()->{if(!a.isDestroyed()&&status.isAttachedToWindow())status.setText("Configurazione non salvata.");});}});});
 }
}
