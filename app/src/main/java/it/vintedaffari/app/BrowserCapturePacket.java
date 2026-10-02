package it.vintedaffari.app;
import org.json.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Sanitizes one admitted public main-frame packet; no network, UI or database access. */
public final class BrowserCapturePacket {
 public final List<BrowserCandidate> candidates=new ArrayList<>();
 public int rejectedItems,rejectedPhotos,truncatedMetadata,observed,withPrice,dropped,unknownShapes,readErrors,oversized;
 public boolean complete;public long captureToken;
 private BrowserCapturePacket(){}
 public static BrowserCapturePacket parse(String data,String expectedUrl,long pageToken,long observedAt){
  if(data==null||data.getBytes(StandardCharsets.UTF_8).length>128*1024||!VintedBrowserPolicy.allowedPage(expectedUrl))throw new IllegalArgumentException("Pacchetto non valido");
  try{
   JSONObject packet=new JSONObject(data);JSONArray items=packet.optJSONArray("items");JSONObject page=packet.optJSONObject("page");
   if(packet.optInt("schema")!=1||items==null||items.length()>32||page==null||!VintedBrowserPolicy.allowedPage(page.optString("url"))||!samePage(page.optString("url"),expectedUrl)||packet.optLong("pageToken",-1)!=pageToken)throw new IllegalArgumentException("Pacchetto di una pagina non ammessa");
   BrowserCapturePacket out=new BrowserCapturePacket();out.observed=bound(page.optInt("observed"));out.withPrice=bound(page.optInt("withPrice"));out.complete=packet.optBoolean("complete");out.captureToken=packet.optLong("captureToken",-1);
   JSONObject stats=packet.optJSONObject("stats");if(stats!=null){out.dropped=bound(stats.optInt("dropped"));out.unknownShapes=bound(stats.optInt("unknownShapes"));out.readErrors=bound(stats.optInt("readErrors"));out.oversized=bound(stats.optInt("oversized"));}
   for(int i=0;i<items.length();i++){
    JSONObject item=items.optJSONObject(i);if(item==null||!VintedBrowserPolicy.matchesItem(item.optString("url"),item.optString("id"))||item.optString("title").length()>600){out.rejectedItems++;continue;}
    Map<String,String> metadata=new TreeMap<>(),sources=new TreeMap<>();String source=bounded(item.optString("source","dom"),80,out);
    for(String field:new String[]{"brand","condition","currency","language"})put(metadata,sources,field,bounded(item.optString(field),field.equals("brand")||field.equals("condition")?600:40,out),source);
    put(metadata,sources,"description",bounded(item.optString("description"),2000,out),source);put(metadata,sources,"sellerName",bounded(item.optString("sellerName"),100,out),source);
    String seller=item.optString("sellerId");if(seller.matches("[1-9][0-9]{0,18}"))put(metadata,sources,"sellerId",seller,source);
    JSONObject publication=item.optJSONObject("publication");if(publication!=null)put(metadata,sources,"publicationRaw",bounded(publication.optString("raw"),100,out),bounded(publication.optString("source"),80,out));
    JSONArray photos=item.optJSONArray("photos"),clean=new JSONArray();if(photos!=null)for(int n=0;n<photos.length();n++){String photo=photos.optString(n);if(!publicImage(photo)){out.rejectedPhotos++;continue;}if(clean.length()>=12){out.truncatedMetadata++;continue;}clean.put(photo);}
    if(clean.length()>0)put(metadata,sources,"photos",clean.toString(),source);
    Integer price=cents(item.opt("priceCents")),protectedPrice=cents(item.opt("protectedPriceCents"));sources.put("title",source);if(price!=null)sources.put("priceCents",source);if(protectedPrice!=null)sources.put("protectedPriceCents",source);
    out.candidates.add(new BrowserCandidate(item.getString("id"),item.getString("url"),item.optString("title"),price,protectedPrice,metadata,sources,observedAt));
   }
   return out;
  }catch(JSONException e){throw new IllegalArgumentException("JSON non valido",e);}
 }
 private static void put(Map<String,String> fields,Map<String,String> sources,String field,String value,String source){if(value==null||value.isEmpty())return;fields.put(field,value);sources.put(field,source);}
 private static String bounded(String text,int maximum,BrowserCapturePacket out){if(text==null)return "";text=text.trim();if(text.length()>maximum){out.truncatedMetadata++;return text.substring(0,maximum);}return text;}
 private static Integer cents(Object value){if(!(value instanceof Number))return null;double number=((Number)value).doubleValue();return Double.isFinite(number)&&number==Math.rint(number)&&number>0&&number<=1_000_000_000?(int)number:null;}
 private static boolean publicImage(String value){if(value==null||value.length()>2048)return false;try{URI uri=new URI(value);String host=uri.getHost();return "https".equals(uri.getScheme())&&uri.getUserInfo()==null&&(uri.getPort()==-1||uri.getPort()==443)&&host!=null&&host.matches("images[0-9]*\\.vinted\\.net");}catch(Exception e){return false;}}
 private static boolean samePage(String a,String b){return withoutFragment(a).equals(withoutFragment(b));}
 private static String withoutFragment(String value){int index=value.indexOf('#');return index<0?value:value.substring(0,index);}
 private static int bound(int value){return Math.max(0,Math.min(1_000_000,value));}
}
