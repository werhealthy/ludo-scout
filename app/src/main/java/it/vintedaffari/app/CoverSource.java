package it.vintedaffari.app;
import java.net.URI;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/** Pure URL and page-metadata parsing, separate from network and image decoding. */
final class CoverSource {
 static String https(String input)throws IOException {
  try{if(input==null||input.length()>4096)throw new IOException("Link troppo lungo");URI uri=new URI(input.trim());String host=uri.getHost();
   if(!"https".equalsIgnoreCase(uri.getScheme())||host==null||uri.getUserInfo()!=null||uri.getPort()!=-1&&uri.getPort()!=443)throw new IOException("Usa un link HTTPS pubblico");
   host=host.toLowerCase(Locale.ROOT);if(host.equals("localhost")||host.endsWith(".localhost")||host.endsWith(".local")||host.indexOf('.')<0||host.matches("[0-9.]+")||host.contains(":"))throw new IOException("Usa un link HTTPS pubblico");
   return uri.toString();
  }catch(Exception e){throw new IOException("Link HTTPS non valido",e);}
 }
 static String pageId(String input){try{URI uri=new URI(input);String host=uri.getHost();if(!"boardgamegeek.com".equalsIgnoreCase(host)&&!"www.boardgamegeek.com".equalsIgnoreCase(host))return null;Matcher m=Pattern.compile("^/image/([1-9][0-9]*)(?:/.*)?$").matcher(uri.getPath());return m.matches()?m.group(1):null;}catch(Exception e){return null;}}
 static String metaImage(String html){Matcher tags=Pattern.compile("<meta\\b[^>]*>",Pattern.CASE_INSENSITIVE).matcher(html);while(tags.find()){String tag=tags.group();Matcher prop=Pattern.compile("(?:property|name)\\s*=\\s*(['\"])(og:image(?::secure_url)?)\\1",Pattern.CASE_INSENSITIVE).matcher(tag);Matcher value=Pattern.compile("content\\s*=\\s*(['\"])(.*?)\\1",Pattern.CASE_INSENSITIVE).matcher(tag);if(prop.find()&&value.find()){String url=value.group(2).replace("&amp;","&").replace("&#38;","&").replace("&quot;","\"");if(bggImage(url))return url;}}return null;}
 static boolean bggImage(String url){try{URI u=new URI(url);return "https".equalsIgnoreCase(u.getScheme())&&"cf.geekdo-images.com".equalsIgnoreCase(u.getHost());}catch(Exception e){return false;}}
}
