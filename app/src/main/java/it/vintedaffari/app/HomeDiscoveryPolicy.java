package it.vintedaffari.app;
import java.util.Set;
/** Home-only preferences. Catalog eligibility and stored game facts remain independent. */
final class HomeDiscoveryPolicy {
 private HomeDiscoveryPolicy(){}
 static String listingKey(String signature){return "ad:"+(signature==null?"":signature);}
 static int interest(int listing,int game){return listing<0||game<0?-1:game;}
 static String key(String bggId,String signature){return bggId!=null&&!bggId.trim().isEmpty()?"bgg:"+bggId.trim():"listing:"+(signature==null?"":signature);}
 static boolean eligible(String bggId,String signature,String language,Set<String> owned,int interest){
  if(interest<0||bggId!=null&&owned.contains(bggId.trim()))return false;
  String edition=HomePresentation.languageLabel(language).split(" · ")[0];
  return !HomePresentation.matchesDependence(language,"DEP")||"IT".equals(edition)||"EN".equals(edition)||"?".equals(edition);
 }
 static int railWidth(int usable,int gap){return Math.max(1,Math.round((usable-1.25f*gap)/2.15f));}
 static int discountBand(int percent){return percent<10?0:percent<30?1:percent<80?2:3;}
}
