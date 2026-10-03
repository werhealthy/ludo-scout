package it.vintedaffari.app;

import java.net.URLEncoder;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Explicit user-driven public searches; no automatic page traversal or capture requests. */
final class ExplorationPlan {
    private ExplorationPlan(){}
    static String url(String query,String order,int minimumCents,int maximumCents){
        if(query==null||query.length()>100||minimumCents<0||maximumCents<0||minimumCents>9999900||maximumCents>9999900||(maximumCents>0&&maximumCents<minimumCents))return "";
        if(!java.util.Arrays.asList("relevance","newest_first","price_low_to_high","price_high_to_low").contains(order))return "";
        String out="https://www.vinted.it/catalog/4881-board-games?page=1&order="+order;
        if(!query.trim().isEmpty())out+="&search_text="+encode(query.trim()).replace("+","%20");
        if(minimumCents>0)out+="&price_from="+money(minimumCents);
        if(maximumCents>0)out+="&price_to="+money(maximumCents);
        return out;
    }
    private static String encode(String query){try{return URLEncoder.encode(query,"UTF-8");}catch(java.io.UnsupportedEncodingException impossible){throw new IllegalStateException(impossible);}}
    private static String money(int cents){return java.math.BigDecimal.valueOf(cents,2).stripTrailingZeros().toPlainString();}
    static String delta(int now,int before){int n=now-before;return n>0?"↑ +"+n:n<0?"↓ −"+(-n):"= 0";}
    static long[] day(long now){ZoneId zone=ZoneId.of("Europe/Rome");LocalDate date=Instant.ofEpochMilli(now).atZone(zone).toLocalDate();return new long[]{date.atStartOfDay(zone).toInstant().toEpochMilli(),date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()};}
    static String baselineKey(long day){return "before_"+day;}
    static String label(int stage){return new String[]{"Acquisiti","Dati BGG","Collegati","Verificati","Pronti"}[stage];}
    static boolean baselineApplies(long day,long savedDay,long baselineAt,long runStart,long runEnd,long baselineEnd){return day==savedDay&&baselineAt>0&&runEnd>=baselineAt&&(baselineEnd==0||runStart<=baselineEnd);}
}
