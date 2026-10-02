package it.vintedaffari.app;
import java.util.Set;
/** Home-only preferences. Catalog eligibility and stored game facts remain independent. */
final class HomeDiscoveryPolicy {
 private HomeDiscoveryPolicy(){}
 static String listingKey(String signature){return "ad:"+(signature==null?"":signature);}
 static int interest(int listing,int game){return listing<0||game<0?-1:game;}
 static String key(String bggId,String signature){return bggId!=null&&!bggId.trim().isEmpty()?"bgg:"+bggId.trim():"listing:"+(signature==null?"":signature);}
 static boolean eligible(String bggId,String signature,String language,Set<String> owned,int interest){
  String id=GamePreferenceState.gameId(bggId);
  return interest>=0&&(id==null||!owned.contains(id));
 }
 static boolean offerEligible(String language){
  String edition=HomePresentation.languageLabel(language).split(" · ")[0];
  return !HomePresentation.matchesDependence(language,"DEP")||"IT".equals(edition)||"EN".equals(edition)||"?".equals(edition);
 }
 static int languagePriority(String language){return offerEligible(language)?0:1;}
 static int railWidth(int usable,int gap){return Math.max(1,Math.round((usable-1.25f*gap)/2.15f));}
 static int discountBand(int percent){return percent<20?0:percent<35?1:percent<50?2:3;}
}
