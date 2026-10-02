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
 public BrowserCandidate(String id,String url,String title,Integer price,Integer protectedPrice,Map<String,String> metadata,Map<String,String> provenance,long at){
  if(!VintedBrowserPolicy.matchesItem(url,id))throw new IllegalArgumentException("ID/URL Vinted non coerenti");
  this.itemId=id;this.url="https://www.vinted.it/items/"+id;this.title=title==null?"":title.trim();
  if(this.title.length()>600)throw new IllegalArgumentException("Titolo troppo lungo");
  this.priceCents=validPrice(price);this.protectedPriceCents=validPrice(protectedPrice);
  this.metadata=Collections.unmodifiableMap(new TreeMap<>(metadata==null?Collections.emptyMap():metadata));
  this.provenance=Collections.unmodifiableMap(new TreeMap<>(provenance==null?Collections.emptyMap():provenance));this.observedAt=Math.max(0,at);
 }
 private static Integer validPrice(Integer p){return p!=null&&p>0&&p<=1000000000?p:null;}
 public boolean elaborable(){return !title.isEmpty()&&priceCents!=null;}
 public String revisionKey(){
  try{MessageDigest d=MessageDigest.getInstance("SHA-256");List<String> values=new ArrayList<>(Arrays.asList(itemId,title,String.valueOf(priceCents),String.valueOf(protectedPriceCents)));for(Map.Entry<String,String> e:metadata.entrySet()){values.add(e.getKey());values.add(e.getValue());}for(String value:values){byte[] b=(value==null?"":value).getBytes(StandardCharsets.UTF_8);d.update(java.nio.ByteBuffer.allocate(4).putInt(b.length).array());d.update(b);}StringBuilder out=new StringBuilder();for(byte b:d.digest())out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 private static int quality(String source){if(source==null)return 0;return source.contains("detail")?3:source.contains("dom")?1:2;}
 public BrowserCandidate merge(BrowserCandidate next){
  if(!itemId.equals(next.itemId))throw new IllegalArgumentException("Annunci distinti");
  Map<String,String> data=new TreeMap<>(metadata),sources=new TreeMap<>(provenance);
  for(Map.Entry<String,String> e:next.metadata.entrySet())if(e.getValue()!=null&&!e.getValue().isEmpty()&&(next.observedAt>=observedAt||!data.containsKey(e.getKey()))&&quality(next.provenance.get(e.getKey()))>=quality(sources.get(e.getKey()))){data.put(e.getKey(),e.getValue());sources.put(e.getKey(),next.provenance.get(e.getKey()));}
  boolean later=next.observedAt>=observedAt;
  String mergedTitle=title;if(!next.title.isEmpty()&&(title.isEmpty()||later&&quality(next.provenance.get("title"))>=quality(provenance.get("title")))){mergedTitle=next.title;sources.put("title",next.provenance.get("title"));}
  Integer price=priceCents,protect=protectedPriceCents;
  if(next.priceCents!=null&&(price==null||later&&quality(next.provenance.get("priceCents"))>=quality(provenance.get("priceCents")))){price=next.priceCents;sources.put("priceCents",next.provenance.get("priceCents"));}
  if(next.protectedPriceCents!=null&&(protect==null||later)){protect=next.protectedPriceCents;sources.put("protectedPriceCents",next.provenance.get("protectedPriceCents"));}
  return new BrowserCandidate(itemId,url,mergedTitle,price,protect,data,sources,Math.max(observedAt,next.observedAt));
 }
}
