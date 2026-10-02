package it.vintedaffari.app;
import java.net.URI;import java.util.*;import java.util.regex.*;
/** Identifies one Vinted photo across thumbnail sizes, crops and signed URLs. */
public final class PhotoIdentity {
 public static String key(String value){try{URI u=new URI(value);String host=u.getHost();if(host==null)return value;String path=u.getPath();if(host.endsWith("vinted.net")||host.endsWith("vinted.com")){Matcher id=Pattern.compile("(?:^|[/_])([0-9a-fA-F-]{32,36})(?:[/.]|$)").matcher(path);if(id.find())return "vinted:"+id.group(1).toLowerCase(Locale.ROOT);path=path.replaceAll("/(?:f[0-9]+|thumb|small|medium|large|original|[0-9]+x[0-9]+)/", "/");return "vinted:"+path;}return host+path;}catch(Exception e){return value;}}
 public static List<String> unique(List<String> urls){LinkedHashMap<String,String> found=new LinkedHashMap<>();for(String u:urls){String k=key(u);String previous=found.get(k);if(previous==null||quality(u)>quality(previous))found.put(k,u);}return new ArrayList<>(found.values());}
 /** Use only observed URLs, never manufacture a size or a replacement signature. */
 public static List<String> sources(String preferred,List<String> observed){
  LinkedHashSet<String> found=new LinkedHashSet<>();String first=remote(preferred);
  if(first==null)return new ArrayList<>();found.add(first);String identity=key(first);
  if(observed!=null)for(String value:observed){String url=remote(value);if(url!=null&&identity.equals(key(url)))found.add(url);if(found.size()>=4)break;}
  return new ArrayList<>(found);
 }
 private static String remote(String value){
  if(value==null)return null;String url=value.trim().replace("&amp;","&").replace("\\/","/").replace("\\u002F","/");
  while(url.endsWith("\\"))url=url.substring(0,url.length()-1);
  try{URI u=new URI(url);return ("https".equals(u.getScheme())||"http".equals(u.getScheme()))&&u.getHost()!=null?url:null;}catch(Exception error){return null;}
 }
 private static int quality(String s){Matcher m=Pattern.compile("/f([0-9]+)/").matcher(s);if(m.find())return Integer.parseInt(m.group(1));return s.contains("original")?10000:0;}
}
