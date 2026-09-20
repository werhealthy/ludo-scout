package it.vintedaffari.app;

import java.text.Normalizer;
import java.util.Locale;

/** Lightweight, local-only language hints from the Vinted card text. Never forces a language
 * when evidence is weak: unknown stays unknown and is excluded from language-dependent prices. */
public final class ListingLanguageDetector {
    private ListingLanguageDetector(){}

    public static String detect(String... parts){
        StringBuilder b=new StringBuilder();
        if(parts!=null)for(String p:parts)if(p!=null&&!p.trim().isEmpty())b.append(' ').append(p);
        String s=norm(b.toString());
        if(any(s,"italiano","italiana","italiane","italiani","lingua italiana","edizione italiana","versione italiana","ita","italian edition"))return "IT";
        if(any(s,"inglese","lingua inglese","edizione inglese","versione inglese","english","english edition","eng"))return "EN";
        if(any(s,"tedesco","tedesca","lingua tedesca","edizione tedesca","versione tedesca","deutsch","german edition","deu"))return "DE";
        if(any(s,"francese","lingua francese","edizione francese","versione francese","francais","francaise","french edition"))return "FR";
        if(any(s,"spagnolo","spagnola","lingua spagnola","edizione spagnola","espanol","spanish edition"))return "ES";
        if(any(s,"olandese","nederlands","dutch edition"))return "NL";
        if(any(s,"portoghese","portugues","portuguese edition"))return "PT";
        return "";
    }

    public static String mergeWithDependency(String detected,String existing){
        String old=existing==null?"":existing.trim().toUpperCase(Locale.ROOT);
        String dep="";
        if(old.contains("DEP"))dep="|DEP"; else if(old.contains("IND"))dep="|IND";
        String code=detected==null?"":detected.trim().toUpperCase(Locale.ROOT);
        if(code.isEmpty()){
            if(old.matches("^(IT|EN|DE|FR|ES|NL|PT)(\\|(?:DEP|IND))?$"))return old;
            return dep.isEmpty()?old:("?"+dep);
        }
        return code+dep;
    }

    private static boolean any(String s,String... terms){
        if(s==null)return false;
        for(String t:terms){String n=norm(t);if(!n.isEmpty()&&(" "+s+" ").contains(" "+n+" "))return true;}
        return false;
    }
    private static String norm(String v){
        if(v==null)return "";
        String n=Normalizer.normalize(v,Normalizer.Form.NFD).replaceAll("\\p{M}+","");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();
    }
}
