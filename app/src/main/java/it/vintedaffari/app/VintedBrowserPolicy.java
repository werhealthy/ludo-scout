package it.vintedaffari.app;
import java.net.URI;
/** Fail-closed trust boundary for the experimental public browser. No Android dependency. */
public final class VintedBrowserPolicy {
 private VintedBrowserPolicy(){}
 private static URI trusted(String value){try{URI u=new URI(value);return "https".equals(u.getScheme())&&("www.vinted.it".equals(u.getHost())||"vinted.it".equals(u.getHost()))&&u.getRawUserInfo()==null&&(u.getPort()==-1||u.getPort()==443)&&u.getRawPath().indexOf('%')<0?u:null;}catch(Exception e){return null;}}
 public static boolean allowedPage(String value){URI u=trusted(value);if(u==null)return false;String p=u.getPath();return p.isEmpty()||"/".equals(p)||p.matches("/catalog(?:/.*)?")||p.matches("/items/[1-9][0-9]{0,18}(?:-[^/]*|/)?")||p.matches("/members?/[1-9][0-9]{0,18}(?:-[^/]*|/)?");}
 /** Session transition is navigable, never a capturable content page. */
 public static boolean sessionTransition(String value){URI u=trusted(value);return u!=null&&"/session-refresh".equals(u.getPath());}
 public static boolean allowedNavigation(String value){return allowedPage(value)||sessionTransition(value);}
 /** Explain the existing policy; does not expand its allowlist. */
 public static String blockReason(String value){
  if(allowedNavigation(value))return "";
  try{URI u=new URI(value);if(u.getScheme()==null||u.getHost()==null)return "INVALID_URL";
   if(!"https".equals(u.getScheme()))return "SCHEME_NOT_ALLOWED";
   if(!("www.vinted.it".equals(u.getHost())||"vinted.it".equals(u.getHost()))||u.getRawUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443))return "ORIGIN_NOT_ALLOWED";
   if(u.getRawPath().indexOf('%')>=0)return "ENCODED_PATH";
   return "PATH_NOT_ALLOWED";
  }catch(Exception invalid){return "INVALID_URL";}
 }
 /** Deliberately omit credentials, query and fragment from shared diagnostics. */
 public static String diagnosticPage(String value){
  try{URI u=new URI(value);String scheme=u.getScheme(),host=u.getHost();if(host==null||!("https".equals(scheme)||"http".equals(scheme)))return "INVALID_URL";
   String safe=scheme+"://"+host+(u.getPort()==-1?"":":"+u.getPort())+u.getRawPath();
   safe=safe.replace(';','_').replace('{','_').replace('}','_');
   return safe.length()>240?safe.substring(0,240):safe;
  }catch(Exception invalid){return "INVALID_URL";}
 }
 public static boolean allowedMessage(String origin,boolean mainFrame,int bytes){URI u=trusted(origin);return mainFrame&&bytes>0&&bytes<=128*1024&&u!=null&&u.getRawPath().isEmpty()&&u.getRawQuery()==null&&u.getRawFragment()==null;}
 public static boolean matchesItem(String value,String id){if(id==null||!id.matches("[1-9][0-9]{0,18}")||!allowedPage(value))return false;try{return new URI(value).getPath().matches("/items/"+id+"(?:-[^/]*|/)?");}catch(Exception e){return false;}}
 public static boolean startsEnabled(String mode){return "SEARCH".equals(mode)||"BUNDLE".equals(mode);}
}
