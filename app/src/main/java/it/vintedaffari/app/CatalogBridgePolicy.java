package it.vintedaffari.app;

/** Safety gate used only when a canonical Market listing has no legacy Catalog row yet. */
public final class CatalogBridgePolicy {
    public static final class Result {
        public final boolean publish;
        public final String tier, label, reason;
        Result(boolean publish,String tier,String label,String reason){
            this.publish=publish;this.tier=tier;this.label=label;this.reason=reason;
        }
    }

    private CatalogBridgePolicy() {}

    public static Result decide(DealRecord draft,boolean canonicalQualified,
                                boolean manualReviewRequired,boolean blockingJob){
        if(draft==null||!canonicalQualified)return reject("CANONICAL_NOT_QUALIFIED");
        if(manualReviewRequired)return reject("MANUAL_REVIEW");
        if(blockingJob)return reject("BLOCKING_JOB");
        if(empty(draft.bggId)||empty(draft.vintedItemId)||empty(draft.vintedUrl))return reject("IDENTITY_INCOMPLETE");
        if(!exactVintedIdentity(draft.vintedItemId,draft.vintedUrl))return reject("VINTED_IDENTITY_MISMATCH");
        if(draft.rating==null||draft.rating<DealPolicy.MIN_BGG_RATING)return reject("BGG_RATING");

        String type=safe(draft.listingType);
        String verification=safe(draft.verificationState);
        boolean verified="OK".equals(verification)||"USER_CONFIRMED".equals(verification);
        boolean asyncIdentity="MATCH_UNCERTAIN".equals(verification);
        if(!("BASE_GAME".equals(type)||"GAME".equals(type)||"EXPANSION".equals(type)))return reject("PRODUCT_TYPE");
        // An expansion needs explicit compatibility evidence. A later BGG identity alone cannot
        // prove that the Vinted box is the matching expansion rather than the base game.
        if("EXPANSION".equals(type)&&!verified)return reject("EXPANSION_UNVERIFIED");
        if(!verified&&!asyncIdentity)return reject("VERIFICATION_STATE");

        DealEvaluator.Evaluation price=DealEvaluator.evaluate(draft);
        if(!price.visible())return reject("PRICE_REJECTED");
        return new Result(true,price.storageTier(),price.label,null);
    }

    private static Result reject(String reason){return new Result(false,null,null,reason);}
    static boolean exactVintedIdentity(String itemId,String url){
        String expected=safe(itemId);
        if(expected.isEmpty()||!expected.matches("[0-9]+"))return false;
        try{
            java.net.URI parsed=new java.net.URI(safe(url));String host=safe(parsed.getHost()).toLowerCase(java.util.Locale.ROOT);
            if(!"https".equalsIgnoreCase(parsed.getScheme()))return false;
            if(!("vinted.it".equals(host)||host.endsWith(".vinted.it")))return false;
            String path=safe(parsed.getPath()),prefix="/items/"+expected;
            if(!path.startsWith(prefix))return false;
            return path.length()==prefix.length()||path.charAt(prefix.length())=='-'||path.charAt(prefix.length())=='/';
        }catch(Exception ignored){return false;}
    }
    private static String safe(String value){return value==null?"":value.trim();}
    private static boolean empty(String value){return safe(value).isEmpty();}
}
