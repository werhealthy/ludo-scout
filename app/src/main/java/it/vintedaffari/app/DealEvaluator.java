package it.vintedaffari.app;

/**
 * Single product decision for price/value.
 *
 * <p>Overpriced listings are deliberately internal-only: REJECT rows are removed from product surfaces.
 * Fair seller asks stay available because shipping can make them useful bundle leads.</p>
 */
public final class DealEvaluator {
    public enum Decision { GREAT_BUY, GOOD_PRICE, OFFER, FAIR, INSUFFICIENT_DATA, REJECT }

    public static final class Evaluation {
        public final Decision decision;
        public final String label;
        public final String reason;
        public final Integer suggestedOfferCents;
        public final Integer currentTotalCents;
        public final Integer benchmarkCents;

        Evaluation(Decision decision,String label,String reason,Integer suggestedOfferCents,
                   Integer currentTotalCents,Integer benchmarkCents){
            this.decision=decision;this.label=label;this.reason=reason;this.suggestedOfferCents=suggestedOfferCents;
            this.currentTotalCents=currentTotalCents;this.benchmarkCents=benchmarkCents;
        }
        public boolean visible(){return decision!=Decision.REJECT;}
        public boolean discoverable(){return decision==Decision.GREAT_BUY||decision==Decision.GOOD_PRICE||decision==Decision.OFFER;}
        public String storageTier(){
            switch(decision){
                case GREAT_BUY:return "hot";
                case GOOD_PRICE:return "good";
                case OFFER:return "offer";
                case FAIR:return "fair";
                case INSUFFICIENT_DATA:return "insufficient";
                default:return "filtered";
            }
        }
    }

    private DealEvaluator(){}

    public static Evaluation evaluate(VintedCard card,GameAnalysis analysis){
        if(card==null||analysis==null)return insufficient(null,null);
        int item=Math.max(0,(int)Math.round(card.itemPrice*100.0));
        return evaluate(item,analysis.totalCents,analysis.benchmarkCents,analysis.marketQ25Cents,
                analysis.marketAllowHot,analysis.offerCents,analysis.afterOfferCents,analysis.shippingCents);
    }

    public static Evaluation evaluate(DealRecord deal){
        if(deal==null)return insufficient(null,null);
        Integer shipping=deal.shippingVerifiedCents!=null?deal.shippingVerifiedCents:deal.shippingCents;
        Integer total=effectiveTotal(deal);
        Integer offerTotal=offerTotal(deal.offerCents,shipping);
        // Deal rows do not persist quartiles yet. A previously validated hot row may retain the
        // strongest category; all recalculation paths with current market stats pass Q25 explicitly.
        return evaluate(Math.max(0,deal.itemPriceCents),total,deal.benchmarkCents,null,
                "hot".equals(deal.tier),deal.offerCents,offerTotal,shipping);
    }

    public static Evaluation evaluate(int itemCents,Integer currentTotalCents,Integer benchmarkCents,
                                      Integer offerCents,Integer afterOfferCents,Integer shippingCents){
        return evaluate(itemCents,currentTotalCents,benchmarkCents,null,false,offerCents,afterOfferCents,shippingCents);
    }

    public static Evaluation evaluate(int itemCents,Integer currentTotalCents,Integer benchmarkCents,
                                      Integer lowerQuartileCents,boolean allowGreat,
                                      Integer offerCents,Integer afterOfferCents,Integer shippingCents){
        if(benchmarkCents==null||benchmarkCents<=0||itemCents<=0)return insufficient(currentTotalCents,benchmarkCents);

        int benchmark=benchmarkCents;
        int smallMargin=Math.max(300,(int)Math.round(benchmark*.08));
        int strongMargin=Math.max(500,(int)Math.round(benchmark*.15));
        int goodCeiling=Math.max(1,benchmark-smallMargin);
        int greatCeiling=Math.max(1,benchmark-strongMargin);

        if(currentTotalCents!=null&&currentTotalCents>0){
            int greatItemCeiling=lowerQuartileCents!=null&&lowerQuartileCents>0
                    ?Math.min(lowerQuartileCents,goodCeiling):goodCeiling;
            if(allowGreat&&currentTotalCents<=greatCeiling&&itemCents<=greatItemCeiling)
                return new Evaluation(Decision.GREAT_BUY,"Offertona",
                        money(benchmark-currentTotalCents)+" sotto l'usato tipico",null,currentTotalCents,benchmark);
            if(currentTotalCents<=goodCeiling)
                return new Evaluation(Decision.GOOD_PRICE,"Buon prezzo",
                        money(benchmark-currentTotalCents)+" sotto l'usato tipico",null,currentTotalCents,benchmark);
        }

        Integer suggested=shippingCents!=null&&shippingCents>=0
                ?PurchaseMath.maxItemForTotal(goodCeiling,shippingCents):offerCents;
        if(suggested!=null&&shippingCents!=null){
            int step=itemCents<1000?10:50;
            suggested=Math.max(step,(suggested/step)*step);
        }
        Integer plausible=offerTotal(suggested,shippingCents);
        if((plausible==null||plausible<=0)&&suggested!=null&&suggested.equals(offerCents))plausible=afterOfferCents;
        if(suggested!=null&&suggested>0&&suggested<itemCents){
            double cut=(itemCents-suggested)/(double)itemCents;
            if(cut>=.05&&cut<=.15&&plausible!=null&&plausible<=goodCeiling)
                return new Evaluation(Decision.OFFER,"Prova un'offerta",
                        "A "+money(suggested)+" diventerebbe un buon prezzo",suggested,currentTotalCents,benchmark);
        }

        int fairItemCeiling=benchmark+smallMargin;
        if(itemCents<=fairItemCeiling){
            String reason=currentTotalCents!=null&&currentTotalCents>benchmark+smallMargin
                    ?"Il prezzo del gioco è sensato; la spedizione pesa sul singolo acquisto"
                    :"In linea con l'usato tipico";
            return new Evaluation(Decision.FAIR,"Prezzo giusto",reason,null,currentTotalCents,benchmark);
        }

        return new Evaluation(Decision.REJECT,"","Prezzo sopra il mercato usato",null,currentTotalCents,benchmark);
    }

    public static boolean isBundleProspect(DealRecord deal){
        if(deal==null||!"ACTIVE".equals(deal.lifecycle)||"verify".equals(deal.tier))return false;
        if(!DealPolicy.ratingEligible(deal)||empty(deal.sellerId)||empty(deal.vintedUrl)||empty(deal.bggId))return false;
        Evaluation e=evaluate(deal);if(!e.visible())return false;
        boolean strongGame=(deal.qualityScore!=null&&deal.qualityScore>=74)||(deal.rating!=null&&deal.rating>=7.4);
        return strongGame||e.discoverable();
    }

    public static double bundleProspectScore(DealRecord deal){
        if(!isBundleProspect(deal))return -1;
        Evaluation e=evaluate(deal);
        double score=deal.qualityScore==null?(deal.rating==null?55:deal.rating*10):deal.qualityScore;
        switch(e.decision){
            case GREAT_BUY:score+=28;break;
            case GOOD_PRICE:score+=18;break;
            case OFFER:score+=12;break;
            case FAIR:score+=4;break;
            default:break;
        }
        Integer total=effectiveTotal(deal);
        if(deal.benchmarkCents!=null&&total!=null&&total>deal.benchmarkCents&&deal.itemPriceCents<=deal.benchmarkCents)score+=8;
        return score;
    }

    private static Evaluation insufficient(Integer total,Integer benchmark){
        return new Evaluation(Decision.INSUFFICIENT_DATA,"Pochi dati",
                "Riferimento usato non ancora abbastanza solido",null,total,benchmark);
    }
    private static Integer effectiveTotal(DealRecord d){
        if(d.shippingVerifiedCents!=null){
            int base=d.protectedPriceCents!=null?d.protectedPriceCents:d.itemPriceCents+PurchaseMath.vintedFee(d.itemPriceCents);
            return base+d.shippingVerifiedCents;
        }
        if(d.totalCents!=null&&d.totalCents>0)return d.totalCents;
        if(d.protectedPriceCents!=null&&d.protectedPriceCents>0)return d.protectedPriceCents;
        return null;
    }
    private static Integer offerTotal(Integer offer,Integer shipping){
        if(offer==null||offer<=0||shipping==null||shipping<0)return null;
        return PurchaseMath.estimatedTotal(offer,shipping);
    }
    private static String money(int cents){
        int abs=Math.abs(cents),euros=abs/100,rem=abs%100;
        String value=rem==0?euros+" €":String.format(java.util.Locale.ITALY,"%.2f €",abs/100.0);
        return cents<0?"-"+value:value;
    }
    private static boolean empty(String s){return s==null||s.trim().isEmpty();}
}
