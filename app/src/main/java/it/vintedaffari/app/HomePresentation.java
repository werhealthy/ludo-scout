package it.vintedaffari.app;
import java.util.Locale;

/** Pure presentation decisions; missing language evidence remains explicitly unknown. */
final class HomePresentation {
    static final int SQUARE=0,WIDE=1,TALL=2;
 private HomePresentation(){}
 static boolean matchesLanguage(String value,String filter){
  if("all".equals(filter))return true;
  if("IND".equals(filter))return matchesDependence(value,filter);
  String edition=languageLabel(value).split(" · ")[0];
  return "unknown".equals(filter)?"?".equals(edition):edition.equals(filter);
 }
 static boolean matchesDependence(String value,String filter){
  if("all".equals(filter))return true;
  String label=languageLabel(value);
  return "unknown".equals(filter)?label.endsWith("testo n/d"):
    "IND".equals(filter)?label.endsWith("indipendente"):
    "DEP".equals(filter)&&label.endsWith(" · testo");
 }
    static String editionName(String value){String edition=languageLabel(value).split(" · ")[0];switch(edition){case "IT":return "Italiano";case "EN":return "Inglese";case "FR":return "Francese";case "DE":return "Tedesco";case "ES":return "Spagnolo";case "NL":return "Olandese";case "PT":return "Portoghese";default:return "Edizione da verificare";}}
    static String editionFlag(String value){String edition=languageLabel(value).split(" · ")[0];switch(edition){case "IT":return "🇮🇹";case "EN":return "🇬🇧";case "FR":return "🇫🇷";case "DE":return "🇩🇪";case "ES":return "🇪🇸";case "NL":return "🇳🇱";case "PT":return "🇵🇹";default:return "";}}
    static String dependenceLabel(String value){String label=languageLabel(value);return label.endsWith("indipendente")?"Non serve la lingua":label.endsWith(" · testo")?"Serve la lingua":"Testo da verificare";}
    static boolean heroInline(float availableDp,float fontScale){return Float.isFinite(availableDp)&&availableDp>=280&&Float.isFinite(fontScale)&&fontScale>0&&fontScale<=1.15f;}
    static int coverMode(int width,int height){
        if(width<=0||height<=0)return SQUARE;
        float ratio=width/(float)height;
        return ratio>1.22f?WIDE:ratio<.82f?TALL:SQUARE;
    }
    static String languageLabel(String value){
        String edition="?";boolean independent=false,dependent=false;
        if(value!=null)for(String part:value.toUpperCase(Locale.ROOT).split("\\|")){
            String token=part.trim();
            if(token.matches("IT|EN|FR|DE|ES|NL|PT"))edition=token;
            if("IND".equals(token))independent=true;
            if("DEP".equals(token))dependent=true;
        }
        return edition+" · "+(independent==dependent?"testo n/d":independent?"indipendente":"testo");
    }
}
