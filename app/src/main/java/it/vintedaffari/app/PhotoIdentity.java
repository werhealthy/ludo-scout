package it.vintedaffari.app;
import java.net.URI;import java.util.*;import java.util.regex.*;
/** Identifies one Vinted photo across thumbnail sizes, crops and signed URLs. */
public final class PhotoIdentity {
 public static String key(String value){try{URI u=new URI(value);String host=u.getHost();if(host==null)return value;String path=u.getPath();if(host.endsWith("vinted.net")||host.endsWith("vinted.com")){Matcher id=Pattern.compile("(?:^|[/_])([0-9a-fA-F-]{32,36})(?:[/.]|$)").matcher(path);if(id.find())return "vinted:"+id.group(1).toLowerCase(Locale.ROOT);path=path.replaceAll("/(?:f[0-9]+|thumb|small|medium|large|original|[0-9]+x[0-9]+)/", "/");return "vinted:"+path;}return host+path;}catch(Exception e){return value;}}
 public static List<String> unique(List<String> urls){LinkedHashMap<String,String> found=new LinkedHashMap<>();for(String u:urls){String k=key(u);String previous=found.get(k);if(previous==null||quality(u)>quality(previous))found.put(k,u);}return new ArrayList<>(found.values());}
 private static int quality(String s){Matcher m=Pattern.compile("/f([0-9]+)/").matcher(s);if(m.find())return Integer.parseInt(m.group(1));return s.contains("original")?10000:0;}
}
