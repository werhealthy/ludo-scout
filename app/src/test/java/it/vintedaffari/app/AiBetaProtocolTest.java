package it.vintedaffari.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class AiBetaProtocolTest {
 @Test public void timeoutRetainsIdAndChangedContentUsesNewId(){
  assertEquals("saved",AiBetaProtocol.requestId("body","body","saved"));
  assertNotEquals("saved",AiBetaProtocol.requestId("edited","body","saved"));
 }
 @Test public void credentialsNeverFollowUntrustedRedirectEndpoint(){
  assertTrue(AiBetaProtocol.validEndpoint("https://ludo.example.workers.dev"));
  for(String s:new String[]{"http://ludo.example.workers.dev","https://evil.test","https://evil.test@ludo.example.workers.dev","https://ludo.example.workers.dev/?key=x","https://ludo.example.workers.dev:8443","https://ludo.example.workers.dev/path"})assertFalse(s,AiBetaProtocol.validEndpoint(s));
 }
}
