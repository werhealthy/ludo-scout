package it.vintedaffari.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class AiBetaProtocolTest {
 @Test public void modelContractAndInputChangesInvalidateRequestAndSavedDisplay(){
  String key=AiBetaProtocol.fingerprint("rows","model","contract");
  assertEquals(key,AiBetaProtocol.fingerprint("rows","model","contract"));
  for(String changed:new String[]{AiBetaProtocol.fingerprint("edited","model","contract"),AiBetaProtocol.fingerprint("rows","new-model","contract"),AiBetaProtocol.fingerprint("rows","model","new-contract")}){
   assertNotEquals(key,changed);assertNotEquals("saved",AiBetaProtocol.requestId(changed,key,"saved"));assertFalse(AiBetaProtocol.reusableDisplay(changed,key,1000,2000));
  }
  assertTrue(AiBetaProtocol.reusableDisplay(key,key,1000,2000));
  assertFalse(AiBetaProtocol.reusableDisplay(key,key,1000,1000+7L*86400000));
  assertFalse(AiBetaProtocol.reusableDisplay(key,key,3000,2000));
 }
 @Test public void timeoutRetainsIdAndChangedContentUsesNewId(){
  assertEquals("saved",AiBetaProtocol.requestId("body","body","saved"));
  assertNotEquals("saved",AiBetaProtocol.requestId("edited","body","saved"));
 }
 @Test public void credentialsNeverFollowUntrustedRedirectEndpoint(){
  assertTrue(AiBetaProtocol.validEndpoint("https://ludo.example.workers.dev"));
  for(String s:new String[]{"http://ludo.example.workers.dev","https://evil.test","https://evil.test@ludo.example.workers.dev","https://ludo.example.workers.dev/?key=x","https://ludo.example.workers.dev:8443","https://ludo.example.workers.dev/path"})assertFalse(s,AiBetaProtocol.validEndpoint(s));
 }
}
