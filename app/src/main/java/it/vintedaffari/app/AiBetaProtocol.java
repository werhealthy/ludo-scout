package it.vintedaffari.app;
import java.net.URI;
import java.util.UUID;
/** Pure client routing contract; no credentials, provider calls or catalog writes. */
public final class AiBetaProtocol {
 public static final String MODEL="gemini-3.1-flash-lite",CONTRACT="title-brand-beta-v1";
 private AiBetaProtocol(){}
 public static String fingerprint(String body,String model,String contract){try{java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");StringBuilder out=new StringBuilder();for(String part:new String[]{model,contract,body}){byte[] bytes=part.getBytes(java.nio.charset.StandardCharsets.UTF_8);digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);}for(byte b:digest.digest())out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
 public static boolean reusableDisplay(String current,String saved,long createdAt,long now){return current!=null&&current.equals(saved)&&createdAt>0&&createdAt<=now&&now-createdAt<7L*86400000;}
 public static String requestId(String body,String savedBody,String savedId){return body.equals(savedBody)&&savedId!=null&&!savedId.isEmpty()?savedId:UUID.randomUUID().toString();}
 public static boolean validEndpoint(String value){try{URI u=new URI(value);String host=u.getHost();return "https".equals(u.getScheme())&&host!=null&&host.endsWith(".workers.dev")&&u.getUserInfo()==null&&u.getPort()==-1&&u.getQuery()==null&&u.getFragment()==null&&(u.getPath()==null||u.getPath().isEmpty()||u.getPath().equals("/"));}catch(Exception e){return false;}}
}
