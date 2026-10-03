package it.vintedaffari.app;
import org.junit.Test;
import static org.junit.Assert.*;
public final class VintedSessionRefreshTest {
 private boolean call(String method,String value)throws Exception{
  try{return (Boolean)VintedBrowserPolicy.class.getMethod(method,String.class).invoke(null,value);}
  catch(NoSuchMethodException absent){throw new AssertionError("Missing session transition policy: "+method,absent);}
 }
 @Test public void exactSessionRefreshCanNavigateButCannotCapture()throws Exception{
  for(String value:new String[]{"https://www.vinted.it/session-refresh","https://vinted.it/session-refresh?redirect=%2Fcatalog%2F4881-board-games"}){
   assertTrue(value,call("allowedNavigation",value));assertTrue(value,call("sessionTransition",value));assertFalse(value,VintedBrowserPolicy.allowedPage(value));
  }
  assertTrue(call("allowedNavigation","https://www.vinted.it/catalog/4881-board-games"));
  assertFalse(call("sessionTransition","https://www.vinted.it/catalog/4881-board-games"));
 }
 @Test public void sessionPermissionCannotExpandOriginsOrPaths()throws Exception{
  for(String value:new String[]{"https://evil.test/session-refresh","http://www.vinted.it/session-refresh","https://www.vinted.it:444/session-refresh","https://user@www.vinted.it/session-refresh","https://www.vinted.it/session-refresh/other","https://www.vinted.it/session-refresh/","https://www.vinted.it/%73ession-refresh","https://www.vinted.it/login","https://www.vinted.it/inbox","intent://session-refresh",null}){
   assertFalse(String.valueOf(value),call("allowedNavigation",value));assertFalse(String.valueOf(value),call("sessionTransition",value));
  }
 }
}
