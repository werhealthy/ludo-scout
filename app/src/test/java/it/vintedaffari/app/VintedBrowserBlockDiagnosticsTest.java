package it.vintedaffari.app;
import org.junit.Test;
import static org.junit.Assert.*;
/** Diagnostic output must explain a block without leaking URL credentials. */
public final class VintedBrowserBlockDiagnosticsTest {
 private String call(String method,String value)throws Exception{
  try{return (String)VintedBrowserPolicy.class.getMethod(method,String.class).invoke(null,value);}
  catch(NoSuchMethodException missing){throw new AssertionError("Missing navigation diagnostic: "+method,missing);}
 }
 @Test public void blockedRoutesHaveDistinctReasons()throws Exception{
  assertEquals("PATH_NOT_ALLOWED",call("blockReason","https://www.vinted.it/login"));
  assertEquals("ORIGIN_NOT_ALLOWED",call("blockReason","https://other.example/catalog"));
  assertEquals("INVALID_URL",call("blockReason","not a url"));
  assertEquals("ENCODED_PATH",call("blockReason","https://www.vinted.it/%6cogin"));
  assertEquals("",call("blockReason","https://www.vinted.it/catalog?page=1"));
 }
 @Test public void diagnosticUrlOmitsCredentialsQueryAndFragment()throws Exception{
  assertEquals("https://www.vinted.it/login",call("diagnosticPage","https://user:secret@www.vinted.it/login?token=secret#secret"));
  assertEquals("INVALID_URL",call("diagnosticPage","not a url"));
  assertFalse(call("diagnosticPage","https://www.vinted.it/"+ "a".repeat(400)).length()>240);
 }
}
