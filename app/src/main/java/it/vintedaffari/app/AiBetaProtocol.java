package it.vintedaffari.app;
import java.net.URI;
import java.util.UUID;
/** Pure client routing contract; no provider calls or catalog writes. */
public final class AiBetaProtocol {
 public static final String MODEL="ludo-hybrid-v1",CONTRACT="listing-evidence-v2";
 public static final String LOCAL_USB_ENDPOINT="http://127.0.0.1:8765";
 // Not a secret: USB debug transport is physically scoped by adb reverse + PC loopback.
 public static final String LOCAL_USB_TOKEN="ludo-local-usb-debug";
 private AiBetaProtocol(){}
 public static String fingerprint(String body,String model,String contract){try{java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");StringBuilder out=new StringBuilder();for(String part:new String[]{model,contract,body}){byte[] bytes=part.getBytes(java.nio.charset.StandardCharsets.UTF_8);digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);}for(byte b:digest.digest())out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
 public static boolean reusableDisplay(String current,String saved,long createdAt,long now){return current!=null&&current.equals(saved)&&createdAt>0&&createdAt<=now&&now-createdAt<7L*86400000;}
 public static String requestId(String body,String savedBody,String savedId){return body.equals(savedBody)&&savedId!=null&&!savedId.isEmpty()?savedId:UUID.randomUUID().toString();}
 public static boolean isLocalUsbEndpoint(String value){
  if(!BuildConfig.DEBUG||value==null)return false;
  try{URI u=new URI(value);return "http".equals(u.getScheme())&&"127.0.0.1".equals(u.getHost())&&u.getPort()==8765&&u.getUserInfo()==null&&u.getQuery()==null&&u.getFragment()==null&&(u.getPath()==null||u.getPath().isEmpty()||u.getPath().equals("/"));}catch(Exception e){return false;}
 }
 public static boolean validEndpoint(String value){
  if(isLocalUsbEndpoint(value))return true;
  try{URI u=new URI(value);String host=u.getHost();return "https".equals(u.getScheme())&&host!=null&&host.endsWith(".workers.dev")&&u.getUserInfo()==null&&u.getPort()==-1&&u.getQuery()==null&&u.getFragment()==null&&(u.getPath()==null||u.getPath().isEmpty()||u.getPath().equals("/"));}catch(Exception e){return false;}
 }
 public static boolean validToken(String endpoint,String token){
  if(isLocalUsbEndpoint(endpoint))return LOCAL_USB_TOKEN.equals(token);
  return token!=null&&token.length()>=16&&token.length()<=500&&!token.contains("\n")&&!token.contains("\r");
 }
}
