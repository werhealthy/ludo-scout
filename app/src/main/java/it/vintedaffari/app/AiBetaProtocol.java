package it.vintedaffari.app;
import java.net.URI;
import java.util.UUID;
/** Pure client routing contract; no credentials, provider calls or catalog writes. */
public final class AiBetaProtocol {
 private AiBetaProtocol(){}
 public static String requestId(String body,String savedBody,String savedId){return body.equals(savedBody)&&savedId!=null&&!savedId.isEmpty()?savedId:UUID.randomUUID().toString();}
 public static boolean validEndpoint(String value){try{URI u=new URI(value);String host=u.getHost();return "https".equals(u.getScheme())&&host!=null&&host.endsWith(".workers.dev")&&u.getUserInfo()==null&&u.getPort()==-1&&u.getQuery()==null&&u.getFragment()==null&&(u.getPath()==null||u.getPath().isEmpty()||u.getPath().equals("/"));}catch(Exception e){return false;}}
}
