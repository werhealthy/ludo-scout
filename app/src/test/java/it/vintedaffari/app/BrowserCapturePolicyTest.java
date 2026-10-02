package it.vintedaffari.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class BrowserCapturePolicyTest {
 @Test public void onlyExplicitIntegralEuroPricesCanEnterAnalysis(){
  assertEquals(Integer.valueOf(950),BrowserCapturePolicy.priceCents(950,"EUR"));
  for(Object n:new Object[]{null,"950",0,-1,950.5,Double.NaN,Double.POSITIVE_INFINITY,1000000001L})assertNull(BrowserCapturePolicy.priceCents(n,"EUR"));
  assertNull(BrowserCapturePolicy.priceCents(950,"USD"));assertNull(BrowserCapturePolicy.priceCents(950,""));
 }
 @Test public void capturedPhotosStayWithinTheActualVintedImageHost(){
  assertEquals("https://images1.vinted.net/p.jpg",BrowserCapturePolicy.photo("https://images1.vinted.net/p.jpg"));
  for(String url:new String[]{null,"https://vinted.net.evil.org/p","https://images1.vinted.net@evil.org/p","http://images1.vinted.net/p","file:///p"})assertEquals("",BrowserCapturePolicy.photo(url));
 }
 @Test public void missingBrowserMetadataNeverAuthorizesAutomaticRemoteWork(){
  for(String s:new String[]{"AUTO","DEEP_METADATA","CATALOG_HEALTH","CATALOG_RECOVERY","LIVE_DEAL",null})assertFalse(BrowserCapturePolicy.explicitRequest(s));
  for(String s:new String[]{"MANUAL_PRIORITY","MANUAL_RECOVERY","OPENED_VERIFY","HUNT_PRIORITY"})assertTrue(BrowserCapturePolicy.explicitRequest(s));
 }
}
