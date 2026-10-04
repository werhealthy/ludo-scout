package it.vintedaffari.app;
import org.json.JSONArray;
import org.json.JSONObject;
import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
/** One physical HTTPS attempt per call. Redirects are forbidden; IDs bind reservation recovery. */
public final class AiBetaClient {
 private AiBetaClient(){}
 public static JSONObject status(String endpoint,String token)throws Exception {
  if(!AiBetaProtocol.validEndpoint(endpoint)||token==null||token.length()<16||token.length()>500||token.contains("\n")||token.contains("\r"))throw new Exception("configuration invalid");
  HttpsURLConnection c=(HttpsURLConnection)new URL(endpoint.replaceAll("/$","")+"/v1/status").openConnection();
  try{
   c.setRequestMethod("GET");c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(35000);c.setRequestProperty("Authorization","Bearer "+token);
   int code=c.getResponseCode();if(code!=200)throw new Exception("status unavailable");
   try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
    byte[] b=new byte[2048];int n;while((n=in.read(b))!=-1){if(out.size()+n>65536)throw new Exception("response too large");out.write(b,0,n);}
    return new JSONObject(out.toString("UTF-8"));
   }
  }finally{c.disconnect();}
 }
 public static JSONObject submit(String endpoint,String token,String id,JSONArray rows)throws Exception {
  if(!AiBetaProtocol.validEndpoint(endpoint)||token==null||token.length()<16||token.length()>500||token.contains("\n")||token.contains("\r"))throw new Exception("configuration invalid");
  JSONObject payload=new JSONObject().put("request_id",id).put("records",rows);byte[] data=payload.toString().getBytes(StandardCharsets.UTF_8);if(data.length>4096||rows.length()<1||rows.length()>8)throw new Exception("input invalid");
  HttpsURLConnection c=(HttpsURLConnection)new URL(endpoint.replaceAll("/$","")+"/v1/classify").openConnection();
  try{c.setRequestMethod("POST");c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(35000);c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Authorization","Bearer "+token);c.setFixedLengthStreamingMode(data.length);try(java.io.OutputStream out=c.getOutputStream()){out.write(data);}int status=c.getResponseCode();if(status>=300&&status<400)throw new Exception("redirect refused");InputStream input=status<400?c.getInputStream():c.getErrorStream();if(input==null)return new JSONObject().put("status","UNAVAILABLE");try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[2048];int n;while((n=in.read(buf))!=-1){if(out.size()+n>65536)throw new Exception("response too large");out.write(buf,0,n);}JSONObject response=new JSONObject(out.toString("UTF-8"));if("PROPOSAL".equals(response.optString("status"))&&(!id.equals(response.optString("request_id"))||!AiBetaProtocol.MODEL.equals(response.optString("model"))||!AiBetaProtocol.CONTRACT.equals(response.optString("contract"))))throw new Exception("response contract invalid");return response;}}
  finally{c.disconnect();}
 }
}
