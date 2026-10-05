package it.vintedaffari.app;

import org.json.JSONArray;
import org.json.JSONObject;

/** Run the production recovery policy against the states reported by the phone. */
public final class AiRecoveryLifecycleProbe {
 private static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
 static JSONObject row(String type,String match,String reason){
  return new JSONObject().put("lifecycle","AUTO_FILTERED").put("engine_enrichment","AUTO_FILTERED")
   .put("listing_match_state",match).put("engine_last_error",reason).put("local_type",type)
   .put("bgg_id","").put("engine_has_observation",true).put("photos",new JSONArray().put("https://images1.vinted.net/t/fixture.webp"));
 }
 public static void main(String[] args)throws Exception {
  String weak="Nessun candidato BGG sufficientemente forte: scarto automatico, non fact-check";
  String missing="Nessuna prova positiva di prodotto gioco da tavolo";
  String collision="Il titolo coincide con BGG ma manca una prova indipendente che l'oggetto sia un gioco da tavolo";
  JSONObject answer=new JSONObject().put("proposed_type","BASE_GAME").put("product_title","Gioco identificabile")
   .put("evidence","Confezione del gioco visibile; Componenti del gioco visibili");
  for(String type:new String[]{"BASE_GAME","UNCERTAIN"})for(String[] gap:new String[][]{{"AUTO_FILTERED_NON_GAME",weak},{"AUTO_FILTERED_NON_GAME",missing},{"AUTO_FILTERED_COLLISION",collision}}){
   JSONObject r=row(type,gap[0],gap[1]);
   check(AiEnginePolicy.recover(r,answer),"visual BASE_GAME cannot recover "+type+" / "+gap[1]);
  }
  for(String type:new String[]{"NON_GAME","ACCESSORY","COMPONENTS","EMPTY_BOX","BUNDLE","EXPANSION"})
   check(!AiEnginePolicy.recover(row(type,"AUTO_FILTERED_NON_GAME",weak),answer),"negative product policy was bypassed: "+type);
  for(String field:new String[]{"engine_manual_review","engine_confirmed"})
   check(!AiEnginePolicy.recover(row("UNCERTAIN","AUTO_FILTERED_NON_GAME",missing).put(field,1),answer),"human decision bypassed");
  check(!AiEnginePolicy.recover(row("UNCERTAIN","AUTO_FILTERED_NON_GAME",missing).put("bgg_id","123"),answer),"AI replaced existing identity");
  check(!AiEnginePolicy.recover(row("UNCERTAIN","AUTO_FILTERED_NON_GAME",missing).put("engine_category","books"),answer),"incompatible Vinted category ignored");
  check(!AiEnginePolicy.recover(row("UNCERTAIN","AUTO_FILTERED_NON_GAME",missing).put("photos",new JSONArray()),answer),"recovery without visual source");
  check(!AiEnginePolicy.recover(row("UNCERTAIN","AUTO_FILTERED_NON_GAME",missing).put("lifecycle","USER_HIDDEN"),answer),"hidden item restored");
  check(!AiEnginePolicy.recover(row("UNCERTAIN","AUTO_FILTERED_NON_GAME","Prezzo chiaramente sopra il riferimento usato"),answer),"price filter restored");
  check(!AiEnginePolicy.recover(row("UNCERTAIN","AUTO_FILTERED_COLLISION","Segnali forti di categoria non gioco da tavolo"),answer),"real collision restored");
  check(!AiEnginePolicy.recover(row("UNCERTAIN","AUTO_FILTERED_NON_GAME",missing),new JSONObject().put("proposed_type","UNKNOWN")),"abstention invented evidence");
  System.out.println("PASS AI recovery policy: initial evidence gaps and human/category/price protections");
  java.lang.reflect.Method unresolved;
  try{unresolved=BoardGameIntakeGate.class.getDeclaredMethod("afterAnalysis",VintedCard.class,ListingClassifier.Result.class,GameAnalysis.class,boolean.class);}
  catch(NoSuchMethodException missingGate){throw new AssertionError("visual product evidence is not an input to the unresolved BGG gate");}
  for(String title:new String[]{"Indovina Chi? - gioco da tavolo","Dobbel kaart spelletje","Cluedo Junior","Gioco da tavolo quiz"}){
   VintedCard card=new VintedCard(title);ListingClassifier.Result local=ListingClassifier.classify(card);GameAnalysis analysis=new GameAnalysis();
   BoardGameIntakeGate.Decision positive=(BoardGameIntakeGate.Decision)unresolved.invoke(null,card,local,analysis,true);
   check(positive.action==BoardGameIntakeGate.Action.ACCEPT,"BGG absence erased visual product evidence: "+title);
   check(BoardGameIntakeGate.afterAnalysis(card,local,analysis).action==BoardGameIntakeGate.Action.QUARANTINE,"unproved product gate changed");
   analysis.status="excluded";
   check(((BoardGameIntakeGate.Decision)unresolved.invoke(null,card,local,analysis,true)).action==BoardGameIntakeGate.Action.QUARANTINE,"excluded accessory/bundle was revived");
  }
  for(String title:new String[]{"Libro Watergate","Catan solo scatola vuota","Catan espansione","Catan organizer","Lotto di giochi"}){
   VintedCard card=new VintedCard(title);
   check(((BoardGameIntakeGate.Decision)unresolved.invoke(null,card,ListingClassifier.classify(card),new GameAnalysis(),true)).action!=BoardGameIntakeGate.Action.ACCEPT,"strong negative evidence bypassed: "+title);
  }
  System.out.println("PASS product/BGG gate: visual proof survives no candidate, negative protections retained");
 }
}
