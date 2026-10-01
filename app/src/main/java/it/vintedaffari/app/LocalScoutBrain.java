package it.vintedaffari.app;

import android.text.TextUtils;
import java.util.*;

/**
 * Deterministic on-device reasoning for Ludo.
 *
 * BGG quality and deal quality remain dominant. Library signals only add a modest
 * personalization bonus and generate explanations/insights; they never replace BGG.
 */
public final class LocalScoutBrain {
    public static final class Snapshot {
        public int totalDeals, hotDeals, freshDeals, bundleSources, italianDeals, independentDeals, libraryGames, ratedLibrary;
        public int medianPriceCents;
        public double averageScoutScore;
        public String headline="";
        public final List<DealRecord> picks=new ArrayList<>();
        public final List<DealRecord> fresh=new ArrayList<>();
        public final List<DealRecord> topRated=new ArrayList<>();
        public final List<DealRecord> cheapest=new ArrayList<>();
        public final List<DealRecord> bundlePicks=new ArrayList<>();
        public final List<String> libraryInsights=new ArrayList<>();
        public final Map<String,String> pickReasons=new HashMap<>();
        public final Map<String,String> pickLabels=new HashMap<>();
    }

    private LocalScoutBrain() {}

    public static Snapshot analyze(List<DealRecord> input, BundleDatabase bundles, List<LibraryGame> library) {
        Snapshot s=new Snapshot();
        List<DealRecord> deals=new ArrayList<>();
        if(input!=null)for(DealRecord d:input)if(d!=null&&!"verify".equals(d.tier)&&DealEvaluator.evaluate(d).discoverable())deals.add(d);
        List<LibraryGame> owned=new ArrayList<>();
        if(library!=null)for(LibraryGame g:library)if(g!=null&&!"sold".equals(g.collectionState))owned.add(g);
        s.totalDeals=deals.size();s.libraryGames=owned.size();
        for(LibraryGame g:owned)if(g.personalRating!=null)s.ratedLibrary++;

        long now=System.currentTimeMillis();List<Integer> prices=new ArrayList<>();double scoreSum=0;
        Set<String> ownedNames=new HashSet<>();Set<String> ownedBgg=new HashSet<>();
        for(LibraryGame g:owned){if(!TextUtils.isEmpty(g.name))ownedNames.add(norm(g.name));if(!TextUtils.isEmpty(g.bggId))ownedBgg.add(g.bggId);}
        PreferenceProfile profile=profile(library);

        for(DealRecord d:deals){
            if("hot".equals(d.tier))s.hotDeals++;
            if(now-d.firstSeen<=60*60_000L)s.freshDeals++;
            if(bundles!=null&&bundles.countForSource(d.signature)>0){s.bundleSources++;s.bundlePicks.add(d);}
            if(d.languageCode!=null&&d.languageCode.startsWith("IT"))s.italianDeals++;
            if(d.languageCode!=null&&d.languageCode.contains("IND"))s.independentDeals++;
            Integer p=effectiveTotal(d);if(p!=null&&p>0)prices.add(p);
            scoreSum+=scoutScore(d);
        }
        s.averageScoutScore=deals.isEmpty()?0:scoreSum/deals.size();
        if(!prices.isEmpty()){Collections.sort(prices);s.medianPriceCents=prices.get(prices.size()/2);}

        deals.sort((a,b)->Double.compare(modelScore(b,bundles,ownedNames,ownedBgg,profile),modelScore(a,bundles,ownedNames,ownedBgg,profile)));
        for(DealRecord d:deals){
            if(s.picks.size()>=5)break;
            if(isOwned(d,ownedNames,ownedBgg))continue;
            if(scoutScore(d)<6.0)continue;
            s.picks.add(d);
            s.pickLabels.put(d.signature,pickLabel(d,profile));
            s.pickReasons.put(d.signature,pickReason(d,profile,library));
        }
        s.fresh.addAll(deals);s.fresh.sort((a,b)->Long.compare(b.firstSeen,a.firstSeen));trim(s.fresh,5);
        s.topRated.addAll(deals);s.topRated.sort((a,b)->{int c=Double.compare(b.rating==null?0:b.rating,a.rating==null?0:a.rating);if(c!=0)return c;return Integer.compare(b.voters==null?0:b.voters,a.voters==null?0:a.voters);});trim(s.topRated,5);
        s.cheapest.addAll(deals);s.cheapest.sort(Comparator.comparingInt(d->{Integer p=effectiveTotal(d);return p==null?Integer.MAX_VALUE:p;}));trim(s.cheapest,5);
        s.bundlePicks.sort((a,b)->Double.compare(modelScore(b,bundles,ownedNames,ownedBgg,profile),modelScore(a,bundles,ownedNames,ownedBgg,profile)));trim(s.bundlePicks,4);
        buildInsights(s,owned,profile);
        s.headline=headline(s);
        return s;
    }

    private static final class PreferenceProfile{
        final Map<String,Double> positive=new HashMap<>();
        final Map<String,Double> negative=new HashMap<>();
        int rated, positiveRatings, knownPlaytimes;
        double playtimeSum;
    }

    private static PreferenceProfile profile(List<LibraryGame> library){
        PreferenceProfile p=new PreferenceProfile();if(library==null)return p;
        for(LibraryGame g:library){if(g==null)continue;boolean sold="sold".equals(g.collectionState);double w=0;
            if(g.personalRating!=null){p.rated++;if(g.personalRating>=8){w=(g.personalRating-7)*1.5;p.positiveRatings++;}else if(g.personalRating<=5)w=-(6-g.personalRating)*1.2;}
            if(sold&&"Non mi piaceva".equalsIgnoreCase(g.soldReason))w-=2.0;
            for(String c:categories(g.categories)){if(w>0)p.positive.put(c,p.positive.getOrDefault(c,0.0)+w);else if(w<0)p.negative.put(c,p.negative.getOrDefault(c,0.0)+(-w));}
            if(!sold&&g.playtime!=null&&g.playtime>0){p.knownPlaytimes++;p.playtimeSum+=g.playtime;}
        }return p;
    }

    private static void buildInsights(Snapshot s,List<LibraryGame> owned,PreferenceProfile p){
        if(owned.isEmpty()){s.libraryInsights.add("La Libreria è ancora vuota: per ora Ludo resta quasi interamente guidato da BGG e qualità dell'offerta.");return;}
        List<Map.Entry<String,Double>> top=new ArrayList<>(p.positive.entrySet());top.sort((a,b)->Double.compare(b.getValue(),a.getValue()));
        if(!top.isEmpty()){
            String a=top.get(0).getKey(),b=top.size()>1?top.get(1).getKey():null;
            s.libraryInsights.add("Segnale di gusto: nei giochi che hai valutato meglio ricorre soprattutto "+a+(b==null?"":" insieme a "+b)+". Lo uso come bonus leggero, non come filtro.");
        }else s.libraryInsights.add("Hai "+owned.size()+" giochi in Libreria, ma pochi voti personali: Ludo continua quindi a dare priorità quasi totale ai dati BGG.");
        if(p.knownPlaytimes>=3){int avg=(int)Math.round(p.playtimeSum/p.knownPlaytimes);String band=avg<=45?"brevi":avg<=100?"di durata media":"lunghi";s.libraryInsights.add("La tua collezione attuale tende verso giochi "+band+" (circa "+avg+" min in media). I consigli non vengono però limitati a questa durata.");}
        if(p.rated>0)s.libraryInsights.add("Profilo personale: "+p.rated+" giochi valutati. Il tuo voto influenza solo la compatibilità; il punteggio generale resta ancorato a BGG.");
    }

    private static String pickLabel(DealRecord d,PreferenceProfile p){
        double affinity=affinity(d,p);if(affinity>=2.5)return"NELLE TUE CORDE";if(affinity<=0.2&&scoutScore(d)>=7.5)return"SCOPERTA";return"DA TENERE D'OCCHIO";
    }

    private static String pickReason(DealRecord d,PreferenceProfile p,List<LibraryGame> library){
        List<String> cats=categories(d.bggCategories);LibraryGame closest=closestLiked(cats,library);StringBuilder out=new StringBuilder();
        if(closest!=null&&closest.personalRating!=null){String common=firstCommon(cats,categories(closest.categories));out.append("Condivide ").append(common==null?"alcuni tratti":common).append(" con ").append(closest.name).append(", a cui hai dato ").append(String.format(java.util.Locale.ITALY,"%.1f",closest.personalRating/2.0)).append("/5. ");}
        else if(affinity(d,p)<=0.2&&scoutScore(d)>=7.5)out.append("È fuori dai segnali più forti della tua Libreria, ma la qualità BGG è abbastanza alta da meritare spazio come scoperta. ");
        else if(!cats.isEmpty())out.append("Il profilo BGG lo colloca soprattutto in ").append(cats.get(0)).append(cats.size()>1?" e "+cats.get(1):"").append(". ");
        DealEvaluator.Evaluation evaluation=DealEvaluator.evaluate(d);if(!TextUtils.isEmpty(evaluation.reason))out.append(evaluation.reason).append(". ");
        if(d.rating!=null){out.append("BGG ").append(String.format(Locale.ITALY,"%.1f",d.rating));if(d.rank!=null)out.append(" · #").append(d.rank);out.append(".");}
        return out.toString().trim();
    }

    private static LibraryGame closestLiked(List<String> cats,List<LibraryGame> library){if(library==null||cats.isEmpty())return null;LibraryGame best=null;int bestCommon=0,bestRating=0;for(LibraryGame g:library){if(g==null||"sold".equals(g.collectionState)||g.personalRating==null||g.personalRating<8)continue;int common=0;List<String> gc=categories(g.categories);for(String c:cats)if(gc.contains(c))common++;if(common>bestCommon||(common==bestCommon&&common>0&&g.personalRating>bestRating)){best=g;bestCommon=common;bestRating=g.personalRating;}}return bestCommon>0?best:null;}
    private static String firstCommon(List<String>a,List<String>b){for(String x:a)if(b.contains(x))return x;return null;}
    private static double affinity(DealRecord d,PreferenceProfile p){double v=0;for(String c:categories(d.bggCategories)){v+=Math.min(4,p.positive.getOrDefault(c,0.0))*.55;v-=Math.min(4,p.negative.getOrDefault(c,0.0))*.45;}return Math.max(-5,Math.min(7,v));}
    private static List<String> categories(String raw){List<String>o=new ArrayList<>();if(TextUtils.isEmpty(raw))return o;String clean=raw.replace("[","").replace("]","").replace("\"","").replace("|"," · ").replace(","," · ");for(String x:clean.split(" · ")){x=x.trim();if(!x.isEmpty()&&!o.contains(x))o.add(x);}return o;}
    private static boolean isOwned(DealRecord d,Set<String>names,Set<String>bgg){return (!TextUtils.isEmpty(d.bggId)&&bgg.contains(d.bggId))||names.contains(norm(displayName(d)));}

    private static void trim(List<?> l,int n){while(l.size()>n)l.remove(l.size()-1);}
    private static String headline(Snapshot s){
        if(s.libraryGames==0)return "Parto da BGG e dal prezzo. Appena riempi la Libreria posso aggiungere contesto personale.";
        if(s.ratedLibrary>=5)return "Ho confrontato Libreria, BGG e catalogo: ti segnalo affinità, scoperte e occasioni senza chiuderti in una bolla.";
        return "Conosco già la tua Libreria. Qualche voto in più mi aiuterà a spiegarti meglio perché un gioco può avere senso per te.";
    }

    private static double modelScore(DealRecord d,BundleDatabase bundles,Set<String> ownedNames,Set<String> ownedBgg,PreferenceProfile profile){
        double q=scoutScore(d)*10.0;double rating=d.rating==null?6.0:d.rating;double decision=decisionScore(d);double ageH=Math.max(0,(System.currentTimeMillis()-d.firstSeen)/3_600_000.0);double fresh=Math.max(0,18-ageH);String lc=d.languageCode==null?"":d.languageCode.toUpperCase(Locale.ROOT);double friction=((lc.startsWith("IT")||lc.contains("IND"))?5:0)+(d.shippingVerifiedCents!=null&&d.shippingVerifiedCents<=300?5:0);boolean foreign=lc.startsWith("FR")||lc.startsWith("DE")||lc.startsWith("ES")||lc.startsWith("NL")||lc.startsWith("PT");double languagePenalty=foreign&&lc.contains("DEP")?170:foreign&&!lc.contains("IND")?35:0;double own=isOwned(d,ownedNames,ownedBgg)?-120:0;double personal=affinity(d,profile);return decision+q*.35+rating*2.5+fresh*.30+friction+personal+own-languagePenalty;
    }
    private static double decisionScore(DealRecord d){switch(DealEvaluator.evaluate(d).decision){case GREAT_BUY:return 115;case GOOD_PRICE:return 85;case OFFER:return 62;case FAIR:return 32;default:return 0;}}

    private static double scoutScore(DealRecord d){return d.qualityScore!=null?d.qualityScore/10.0:(d.rating!=null?d.rating:0);}
    private static Integer effectiveTotal(DealRecord d){if(d.shippingVerifiedCents!=null){int base=d.protectedPriceCents!=null?d.protectedPriceCents:d.itemPriceCents+PurchaseMath.vintedFee(d.itemPriceCents);return base+d.shippingVerifiedCents;}return d.totalCents!=null?d.totalCents:d.protectedPriceCents;}
    private static Integer saving(DealRecord d){Integer t=effectiveTotal(d);if(t==null||d.benchmarkCents==null||d.benchmarkCents<=0)return null;return(int)Math.round(Math.max(-999,Math.min(99,(d.benchmarkCents-t)*100.0/d.benchmarkCents)));}
    private static String displayName(DealRecord d){return !TextUtils.isEmpty(d.displayName)?d.displayName:!TextUtils.isEmpty(d.gameName)?d.gameName:d.vintedTitle;}
    private static String norm(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();}
}
