package it.vintedaffari.app;

import java.text.Normalizer;
import java.util.*;

/**
 * Human-facing BGG search ranking. Deliberately separate from SearchRanking, which is also used
 * by automatic identity matching and therefore has much stricter semantics/thresholds.
 */
public final class BggManualSearchRanking {
    public static final class Query {
        final String core, normalized;
        Query(String c,String n){core=c;normalized=n;}
    }

    private static final Set<String> FILLER=Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "gioco","giochi","tavolo","board","game","games","edizione","edition","versione","version",
            "italiano","italiana","italian","completo","completa","nuovo","nuova","vendo","vintage",
            "originale","original")));

    private BggManualSearchRanking(){}

    public static Query prepare(String raw){return new Query(core(raw),normalize(raw));}

    public static int score(Query query,String rawTitle,boolean alias){
        if(query==null)return 0;
        String q=query.core,t=core(rawTitle);
        if(q.isEmpty()||t.isEmpty())return 0;
        if(q.equals(t))return 1800+(alias?30:80);
        String t0=normalize(rawTitle);
        if(query.normalized.equals(t0))return 1750+(alias?20:70);
        String[] qw=q.split(" +"),tw=t.split(" +");
        double matched=0;
        for(String a:qw){double best=0;for(String b:tw)best=Math.max(best,tokenSimilarity(a,b));matched+=best;}
        double coverage=matched/Math.max(1,qw.length);
        double reverse=0;
        for(String b:tw){double best=0;for(String a:qw)best=Math.max(best,tokenSimilarity(a,b));reverse+=best;}
        reverse/=Math.max(1,tw.length);
        double seq=coverage>=.35?stringSimilarity(q,t):0;
        int containment=(t.contains(q)||q.contains(t))?220:0;
        int base=(int)Math.round(760*coverage+260*reverse+260*seq)+containment-Math.abs(qw.length-tw.length)*28;
        if(alias)base+=35;
        return Math.max(0,base);
    }

    public static int popularityBoost(Integer rank,Integer voters){
        int boost=0;
        if(rank!=null&&rank>0){if(rank<=100)boost+=110;else if(rank<=500)boost+=80;else if(rank<=2000)boost+=45;else if(rank<=10000)boost+=18;}
        if(voters!=null){if(voters>=20000)boost+=55;else if(voters>=5000)boost+=35;else if(voters>=1000)boost+=18;}
        return boost;
    }

    static String normalize(String s){
        return Normalizer.normalize(s==null?"":s,Normalizer.Form.NFD).replaceAll("\\p{M}","")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim().replaceAll("\\s+"," ");
    }

    private static String core(String raw){
        String s=normalize(raw);if(s.isEmpty())return s;
        List<String> keep=new ArrayList<>();for(String w:s.split(" +"))if(!FILLER.contains(w))keep.add(w);
        return keep.isEmpty()?s:String.join(" ",keep);
    }

    private static double tokenSimilarity(String a,String b){
        if(a.equals(b))return 1.0;
        if(a.length()>=3&&(a.startsWith(b)||b.startsWith(a)))return .88;
        int max=Math.max(a.length(),b.length());
        if(max==0||Math.abs(a.length()-b.length())>Math.max(3,max/2))return 0;
        int d=damerau(a,b);double sim=1.0-d/(double)max;
        if(max<=3&&d>1)return 0;
        return sim>=.55?sim:0;
    }

    private static double stringSimilarity(String a,String b){int max=Math.max(a.length(),b.length());return max==0?1:Math.max(0,1.0-damerau(a,b)/(double)max);}

    private static int damerau(String a,String b){
        int[][] d=new int[a.length()+1][b.length()+1];
        for(int i=0;i<=a.length();i++)d[i][0]=i;for(int j=0;j<=b.length();j++)d[0][j]=j;
        for(int i=1;i<=a.length();i++)for(int j=1;j<=b.length();j++){
            int cost=a.charAt(i-1)==b.charAt(j-1)?0:1;
            int v=Math.min(Math.min(d[i-1][j]+1,d[i][j-1]+1),d[i-1][j-1]+cost);
            if(i>1&&j>1&&a.charAt(i-1)==b.charAt(j-2)&&a.charAt(i-2)==b.charAt(j-1))v=Math.min(v,d[i-2][j-2]+1);
            d[i][j]=v;
        }
        return d[a.length()][b.length()];
    }
}
