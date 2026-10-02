package it.vintedaffari.app;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.math.BigDecimal;
import java.io.UnsupportedEncodingException;
import java.util.*;

/** Builds a navigation requested by the user; never loads or fetches a page. */
public final class BrowserSearchNavigation {
 private BrowserSearchNavigation() {}
 private static String decode(String s) { try { return URLDecoder.decode(s, "UTF-8"); } catch(UnsupportedEncodingException e) { throw new AssertionError(e); } }
 private static String encode(String s) { try { return URLEncoder.encode(s, "UTF-8"); } catch(UnsupportedEncodingException e) { throw new AssertionError(e); } }
 private static String euros(Integer cents) { return cents==null?null:BigDecimal.valueOf(cents,2).stripTrailingZeros().toPlainString(); }
 public static String build(String currentUrl,String query,String order,Integer min,Integer max,int page) {
  if(!VintedBrowserPolicy.allowedPage(currentUrl)||page<1||page>10||min!=null&&min<0||max!=null&&max<=0||min!=null&&max!=null&&min>max)throw new IllegalArgumentException("Filtri non validi");
  URI current=URI.create(currentUrl);
  List<String[]> params=new ArrayList<>();
  if(current.getRawQuery()!=null)for(String part:current.getRawQuery().split("&")){String[] pair=part.split("=",2);params.add(new String[]{decode(pair[0]),pair.length>1?decode(pair[1]):""});}
  Map<String,String> changes=new LinkedHashMap<>();changes.put("search_text",query==null?"":query.trim());changes.put("order",order==null?"relevance":order);changes.put("price_from",euros(min));changes.put("price_to",euros(max));
  if(!Arrays.asList("relevance","newest_first","price_low_to_high","price_high_to_low").contains(changes.get("order")))throw new IllegalArgumentException("Ordinamento non valido");
  boolean changed=false;
  for(Map.Entry<String,String> e:changes.entrySet()){String old=null;for(String[] p:params)if(p[0].equals(e.getKey()))old=p[1];String value=e.getValue();if("order".equals(e.getKey())&&old==null)old="relevance";if("search_text".equals(e.getKey())&&old==null)old="";if(!Objects.equals(old,value))changed=true;}
  params.removeIf(p->changes.containsKey(p[0])||"page".equals(p[0]));
  for(Map.Entry<String,String> e:changes.entrySet())if(e.getValue()!=null&&!e.getValue().isEmpty())params.add(new String[]{e.getKey(),e.getValue()});
  params.add(new String[]{"page",String.valueOf(changed?1:page)});
  StringBuilder result=new StringBuilder("https://www.vinted.it");String path=current.getRawPath();result.append(path.startsWith("/catalog")?path:"/catalog");result.append('?');
  for(String[] p:params){if(result.charAt(result.length()-1)!='?')result.append('&');result.append(encode(p[0])).append('=').append(encode(p[1]));}
  return result.toString();
 }
}
