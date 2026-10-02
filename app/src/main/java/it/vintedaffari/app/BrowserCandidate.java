package it.vintedaffari.app;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
/** Validated immutable candidate. Unknown fields stay absent; timestamps are not product data. */
public final class BrowserCandidate {
 public final String itemId,url,title;
 public final Integer priceCents,protectedPriceCents;
 public final Map<String,String> metadata,provenance;
 public final long observedAt;
 public final Map<String,Long> fieldObservedAt;
 public BrowserCandidate(String id,String url,String title,Integer price,Integer protectedPrice,Map<String,String> metadata,Map<String,String> provenance,long at){
  this(id,url,title,price,protectedPrice,metadata,provenance,at,null);
 }
 public BrowserCandidate(String id,String url,String title,Integer price,Integer protectedPrice,Map<String,String> metadata,Map<String,String> provenance,long at,Map<String,Long> clocks){
  if(!VintedBrowserPolicy.matchesItem(url,id))throw new IllegalArgumentException("ID/URL Vinted non coerenti");
  this.itemId=id;this.url="https://www.vinted.it/items/"+id;this.title=title==null?"":title.trim();
  if(this.title.length()>600)throw new IllegalArgumentException("Titolo troppo lungo");
  this.priceCents=validPrice(price);this.protectedPriceCents=validPrice(protectedPrice);
  this.metadata=Collections.unmodifiableMap(new TreeMap<>(metadata==null?Collections.emptyMap():metadata));
  this.provenance=Collections.unmodifiableMap(new TreeMap<>(provenance==null?Collections.emptyMap():provenance));this.observedAt=Math.max(0,at);
  Map<String,Long> times=new TreeMap<>();if(!this.title.isEmpty())times.put("title",this.observedAt);if(this.priceCents!=null)times.put("priceCents",this.observedAt);if(this.protectedPriceCents!=null)times.put("protectedPriceCents",this.observedAt);for(String key:this.metadata.keySet())times.put(key,this.observedAt);if(clocks!=null)for(String key:times.keySet())if(clocks.containsKey(key))times.put(key,Math.max(0,clocks.get(key)));fieldObservedAt=Collections.unmodifiableMap(times);
 }
 private static Integer validPrice(Integer p){return p!=null&&p>0&&p<=1000000000?p:null;}
 public boolean elaborable(){return !title.isEmpty()&&priceCents!=null;}
 public String revisionKey(){
  try{MessageDigest d=MessageDigest.getInstance("SHA-256");List<String> values=new ArrayList<>(Arrays.asList(itemId,title,String.valueOf(priceCents),String.valueOf(protectedPriceCents)));for(Map.Entry<String,String> e:metadata.entrySet()){values.add(e.getKey());values.add(e.getValue());}for(String value:values){byte[] b=(value==null?"":value).getBytes(StandardCharsets.UTF_8);d.update(java.nio.ByteBuffer.allocate(4).putInt(b.length).array());d.update(b);}StringBuilder out=new StringBuilder();for(byte b:d.digest())out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 private static int quality(String source){if(source==null||source.isEmpty())return 0;return source.contains("detail")?3:source.contains("dom")?1:2;}
 private boolean replace(String field,BrowserCandidate next,boolean missing){
  return missing||next.fieldObservedAt.getOrDefault(field,next.observedAt)>=fieldObservedAt.getOrDefault(field,0L)&&quality(next.provenance.get(field))>=quality(provenance.get(field));
 }
 private static void take(String field,BrowserCandidate next,Map<String,String> sources,Map<String,Long> clocks){String source=next.provenance.get(field);if(source!=null)sources.put(field,source);else sources.remove(field);clocks.put(field,next.fieldObservedAt.getOrDefault(field,next.observedAt));}
 public BrowserCandidate merge(BrowserCandidate next){
  if(!itemId.equals(next.itemId))throw new IllegalArgumentException("Annunci distinti");
  Map<String,String> data=new TreeMap<>(metadata),sources=new TreeMap<>(provenance);Map<String,Long> clocks=new TreeMap<>(fieldObservedAt);
  for(Map.Entry<String,String> e:next.metadata.entrySet())if(e.getValue()!=null&&!e.getValue().isEmpty()&&replace(e.getKey(),next,!data.containsKey(e.getKey()))){data.put(e.getKey(),e.getValue());take(e.getKey(),next,sources,clocks);}
  String mergedTitle=title;if(!next.title.isEmpty()&&replace("title",next,title.isEmpty())){mergedTitle=next.title;take("title",next,sources,clocks);}
  Integer price=priceCents,protect=protectedPriceCents;
  if(next.priceCents!=null&&replace("priceCents",next,price==null)){price=next.priceCents;take("priceCents",next,sources,clocks);}
  if(next.protectedPriceCents!=null&&replace("protectedPriceCents",next,protect==null)){protect=next.protectedPriceCents;take("protectedPriceCents",next,sources,clocks);}
  return new BrowserCandidate(itemId,url,mergedTitle,price,protect,data,sources,Math.max(observedAt,next.observedAt),clocks);
 }
}
