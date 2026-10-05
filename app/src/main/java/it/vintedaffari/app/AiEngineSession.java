package it.vintedaffari.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

/** One bounded pass. Transport retries recover the same server reservation, never a new ID. */
final class AiEngineSession {
 interface Journal {JSONObject load()throws Exception;void save(JSONObject value)throws Exception;}
 interface Source {
  JSONArray select(JSONObject journal,long now)throws Exception;
  boolean current(JSONArray snapshot)throws Exception;
  int apply(JSONArray snapshot,JSONObject response)throws Exception;
 }
 interface Transport {
  JSONObject status()throws Exception;
  JSONObject submit(String id,JSONArray rows)throws Exception;
 }
 static final class Result {
  final String state;final int checked,held;final boolean more;
  Result(String state,int checked,int held,boolean more){this.state=state;this.checked=checked;this.held=held;this.more=more;}
 }
 private AiEngineSession(){}
 static String localKey(JSONObject row){return AiBetaProtocol.fingerprint(row.toString(),"ai-engine-local","v1");}
 static String remoteKey(JSONObject row)throws Exception {
  return AiBetaListings.key(new JSONArray().put(row));
 }
 static void prune(JSONObject j,long now)throws Exception {
  for(String field:new String[]{"seen","cache"}){
   JSONObject entries=j.optJSONObject(field);if(entries==null){j.put(field,new JSONObject());continue;}
   java.util.ArrayList<String> keys=new java.util.ArrayList<>();java.util.Iterator<String> it=entries.keys();while(it.hasNext())keys.add(it.next());
   for(String key:keys)if(!AiEnginePolicy.fresh(entries.optJSONObject(key)==null?0:entries.getJSONObject(key).optLong("at"),now))entries.remove(key);
   while(entries.length()>800)removeOldest(entries);
  }
  while(j.toString().getBytes(StandardCharsets.UTF_8).length>850000){
   JSONObject cache=j.getJSONObject("cache"),seen=j.getJSONObject("seen");
   if(cache.length()>0)removeOldest(cache);else if(seen.length()>0)removeOldest(seen);else throw new Exception("journal too large");
  }
 }
 private static void removeOldest(JSONObject entries){
  String oldest=null;long at=Long.MAX_VALUE;java.util.Iterator<String> it=entries.keys();
  while(it.hasNext()){String k=it.next();long t=entries.optJSONObject(k)==null?0:entries.optJSONObject(k).optLong("at");if(oldest==null||t<at){oldest=k;at=t;}}
  if(oldest!=null)entries.remove(oldest);
 }
 private static Result finish(Journal store,JSONObject j,long now,String state,int checked,int held,boolean more)throws Exception {
  j.put("state",state).put("checked",checked).put("held",held).put("updated_at",now);
  j.put("checked_total",j.optLong("checked_total")+checked).put("held_total",j.optLong("held_total")+held);
  j.put("next_at",more?now+10000:now+AiEnginePolicy.BACKOFF);
  prune(j,now);store.save(j);return new Result(state,checked,held,more);
 }
 static Result run(JSONObject config,Journal store,Source source,Transport transport,long now)throws Exception {
  if(!config.optBoolean("enabled")||!AiBetaProtocol.validEndpoint(config.optString("endpoint"))||!AiBetaProtocol.validToken(config.optString("endpoint"),config.optString("token")))
   return new Result("LOCAL_DISABLED",0,0,false);
  String configKey=AiBetaProtocol.fingerprint(config.optString("endpoint")+"\n"+config.optString("token")+"\n"+AiBetaProtocol.MODEL+"\n"+AiBetaProtocol.CONTRACT,"ai-engine-config","v2");
  JSONObject j=store.load();
  if(!configKey.equals(j.optString("config_key")))j=new JSONObject().put("config_key",configKey);
  prune(j,now);
  if(j.optLong("next_at")>now)return new Result("WAIT",0,0,false);
  JSONObject pending=j.optJSONObject("pending");JSONArray rows;
  if(pending!=null){
   rows=pending.getJSONArray("snapshot");
   if(!AiEnginePolicy.fresh(pending.optLong("at"),now)||!source.current(rows)){
    j.remove("pending");return finish(store,j,now,"STALE_PENDING",0,0,true);
   }
  }else{
   rows=source.select(j,now);
   if(rows.length()==0)return finish(store,j,now,"NO_WORK",0,0,j.optBoolean("scan_more"));
  }
  // Reuse each validated listing-evidence proposal locally, even when batch grouping or identity changes.
  JSONArray cached=new JSONArray(),cachedRows=new JSONArray(),remoteRows=new JSONArray();JSONObject cache=j.getJSONObject("cache");
  int checkedCached=0,heldCached=0;
  if(pending!=null)remoteRows=rows;
  else for(int i=0;i<rows.length();i++){
   JSONObject row=rows.getJSONObject(i),c=cache.optJSONObject(remoteKey(row));
   if(c!=null&&AiEnginePolicy.fresh(c.optLong("at"),now)){cached.put(c.getJSONObject("answer"));cachedRows.put(row);}else remoteRows.put(row);
  }
  if(cachedRows.length()>0){
   JSONObject cachedResponse=new JSONObject().put("status","PROPOSAL").put("records",cached);
   try{AiBetaListings.display(cachedRows,cachedResponse);}catch(Exception invalid){return finish(store,j,now,"INVALID_CACHE",0,0,false);}
   heldCached=source.apply(cachedRows,cachedResponse);checkedCached=cachedRows.length();
   for(int i=0;i<cachedRows.length();i++){
    JSONObject row=cachedRows.getJSONObject(i);
    j.getJSONObject("seen").put(localKey(row),new JSONObject().put("at",cache.getJSONObject(remoteKey(row)).getLong("at")));
   }
   // Preserve the original remote cache timestamp. Local rechecks cannot extend the seven-day TTL.
   if(remoteRows.length()==0)return finish(store,j,now,"CHECKED_CACHE",checkedCached,heldCached,true);
  }
  rows=remoteRows;
  JSONObject response;
  {
   JSONObject status;
   try{status=transport.status();}catch(Exception unavailable){return finish(store,j,now,"STATUS_UNAVAILABLE",checkedCached,heldCached,false);}
   JSONObject budget=status.optJSONObject("budget");
   Object calls=budget==null?null:budget.opt("calls_reserved"),micro=budget==null?null:budget.opt("reserved_micro");
   if(!Boolean.TRUE.equals(status.opt("enabled")))return finish(store,j,now,"SERVICE_OFF",checkedCached,heldCached,false);
   if(!(calls instanceof Number)||!(micro instanceof Number)||((Number)calls).doubleValue()!=((Number)calls).longValue()
      ||((Number)micro).doubleValue()!=((Number)micro).longValue()||((Number)calls).longValue()<0||((Number)calls).longValue()>100
      ||((Number)micro).longValue()<((Number)calls).longValue()*10000||((Number)micro).longValue()>1000000)
    return finish(store,j,now,"INVALID_BUDGET",checkedCached,heldCached,false);
   boolean localOnline=Boolean.TRUE.equals(status.opt("local_online"));
   if(pending==null&&!localOnline&&(((Number)calls).longValue()>=100||((Number)micro).longValue()+10000>1000000))
    return finish(store,j,now,"BUDGET_BLOCKED",checkedCached,heldCached,false);
   if(!source.current(rows))return finish(store,j,now,"STALE_INPUT",checkedCached,heldCached,true);
   if(pending==null){
    pending=new JSONObject().put("id",java.util.UUID.randomUUID().toString()).put("snapshot",rows).put("at",now);
    j.put("pending",pending);prune(j,now);store.save(j); // Persist BEFORE the physical attempt.
   }
   String id=pending.getString("id");
   try{response=transport.submit(id,AiBetaListings.payload(remoteRows));}
   catch(Exception ambiguous){return finish(store,j,now,"PENDING_RECOVERY",checkedCached,heldCached,false);}
   if("FAILED".equals(response.optString("status"))&&id.equals(response.optString("request_id"))&&AiBetaProtocol.MODEL.equals(response.optString("model"))&&AiBetaProtocol.CONTRACT.equals(response.optString("contract"))){
    // A terminal provider failure keeps its reservation, but cannot monopolize unrelated work.
    for(int i=0;i<rows.length();i++)j.getJSONObject("seen").put(localKey(rows.getJSONObject(i)),new JSONObject().put("at",now));
    j.remove("pending");j.put("failed_batches",j.optInt("failed_batches")+1);
    return finish(store,j,now,"TERMINAL_FAILED",checkedCached,heldCached,true);
   }
   if(!"PROPOSAL".equals(response.optString("status"))){String remoteState=response.optString("status","UNAVAILABLE");boolean localPending="LOCAL_PENDING".equals(remoteState);return finish(store,j,now,"PENDING_"+remoteState,checkedCached,heldCached,localPending);}
   if(!id.equals(response.optString("request_id"))||!AiBetaProtocol.MODEL.equals(response.optString("model"))||!AiBetaProtocol.CONTRACT.equals(response.optString("contract")))
    return finish(store,j,now,"INVALID_RESPONSE",checkedCached,heldCached,false);
   try{AiBetaListings.display(remoteRows,response);}catch(Exception invalid){return finish(store,j,now,"INVALID_RESPONSE",checkedCached,heldCached,false);}
  }
  // The existing strict proposal validator checks IDs, enums, flags, confidence and language.
  try{AiBetaListings.display(rows,response);}catch(Exception invalid){return finish(store,j,now,"INVALID_RESPONSE",0,0,false);}
  int held=source.apply(rows,response); // Adapter repeats freshness and protections in its transaction.
  for(int i=0;i<rows.length();i++){
   JSONObject row=rows.getJSONObject(i),answer=null;JSONArray answers=response.getJSONArray("records");
   for(int k=0;k<answers.length();k++)if(answers.getJSONObject(k).getLong("listing_id")==row.getLong("listing_id"))answer=answers.getJSONObject(k);
   j.getJSONObject("seen").put(localKey(row),new JSONObject().put("at",now));
   cache.put(remoteKey(row),new JSONObject().put("at",now).put("answer",answer));
  }
  j.remove("pending");
  return finish(store,j,now,"CHECKED",checkedCached+rows.length(),heldCached+held,true);
 }
}
