package it.vintedaffari.app;
import java.text.*;import java.util.*;import java.util.regex.*;
/** Publication age that keeps advancing after Vinted metadata has been resolved once. */
public final class RelativeTime{
 private RelativeTime(){}
 public static String compact(long when,long now){long m=Math.max(0,now-when)/60000L;if(m<1)return"ora";if(m<60)return m+" min";long h=m/60;if(h<24)return h+" h";long d=h/24;if(d<7)return d+" g";return new SimpleDateFormat("dd/MM",Locale.ITALY).format(new Date(when));}
 /**
  * label may be a Vinted relative label ("2 ore fa") or an absolute date. referenceAt is the
  * time at which Ludo observed/resolved that label. Relative labels are anchored to referenceAt,
  * so "2 ore fa" naturally becomes "4 ore fa" two hours later without another network request.
  */
 public static String fromLabel(String label,long referenceAt,long now){
  if(label==null||label.trim().isEmpty())return"";
  String s=label.trim().toLowerCase(Locale.ITALY).replace('\u00a0',' ').replaceAll("\\s+"," ");long anchor=referenceAt>0?referenceAt:now;
  if(s.equals("oggi")||s.equals("pubblicato oggi"))return compact(anchor,now);
  if(s.equals("ieri")||s.equals("pubblicato ieri"))return compact(Math.max(0,anchor-24L*60L*60_000L),now);
  Matcher m=Pattern.compile("(?:(\\d+)|(un|una))\\s*(min|minuto|minuti|minute|h|ora|ore|g|giorno|giorni|settimana|settimane|mese|mesi|anno|anni)").matcher(s);
  if(m.find()){
   long n=m.group(1)!=null?Long.parseLong(m.group(1)):1L;String u=m.group(3);
   if(u.startsWith("settim"))return n+(n==1?" settimana fa":" settimane fa");
   if(u.startsWith("mes"))return n+(n==1?" mese fa":" mesi fa");
   if(u.startsWith("ann"))return n+(n==1?" anno fa":" anni fa");
   long delta;if(u.startsWith("min"))delta=n*60_000L;else if(u.equals("h")||u.startsWith("or"))delta=n*60L*60_000L;else delta=n*24L*60L*60_000L;
   return compact(Math.max(0,anchor-delta),now);
  }
  for(String p:new String[]{"dd/MM/yyyy HH:mm","dd/MM/yyyy","dd/MM/yy","yyyy-MM-dd'T'HH:mm:ss","yyyy-MM-dd"})try{SimpleDateFormat f=new SimpleDateFormat(p,Locale.ITALY);f.setLenient(false);Date d=f.parse(label.replace("Pubblicato il ","").replace("pubblicato il ","").trim());if(d!=null)return compact(d.getTime(),now);}catch(Exception ignored){}
  // Vinted may introduce new human-readable labels. Preserve a short source label rather than
  // turning valid metadata into a false "unknown" state.
  return s.length()<=32?s:"";
 }
}
