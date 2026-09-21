package it.vintedaffari.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

/** Short-lived intent created when the user deliberately opens Vinted from the Bundle area. */
public final class BundleExploration {
    private static final String PREFS="bundle_exploration_v1";
    private static final String EXPLORED_PREFIX="explored_seller:";
    private static final long TTL_MS=10L*60_000L;

    public static final class State {
        public final String sourceSignature,itemId,sellerId;
        public final long startedAt,expiresAt;
        State(String sourceSignature,String itemId,String sellerId,long startedAt,long expiresAt){
            this.sourceSignature=sourceSignature;this.itemId=itemId;this.sellerId=sellerId;this.startedAt=startedAt;this.expiresAt=expiresAt;
        }
        public boolean canTagVisibleSellerCards(){return !TextUtils.isEmpty(sellerId)&&System.currentTimeMillis()-startedAt<=3L*60_000L;}
        public boolean matches(DealRecord deal){if(deal==null)return false;if(!TextUtils.isEmpty(itemId)&&itemId.equals(deal.vintedItemId))return true;return !TextUtils.isEmpty(sourceSignature)&&sourceSignature.equals(deal.signature);}
    }

    private BundleExploration(){}

    public static void begin(Context context,DealRecord deal){
        if(context==null||deal==null)return;long now=System.currentTimeMillis();SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        SharedPreferences.Editor e=p.edit()
                .putString("source_signature",safe(deal.signature)).putString("item_id",safe(deal.vintedItemId)).putString("seller_id",safe(deal.sellerId))
                .putLong("started_at",now).putLong("expires_at",now+TTL_MS);
        // A prospect is a one-shot suggestion. As soon as the user deliberately opens that seller,
        // it leaves "Da esplorare". If Ludo later proves a real two-game bundle, the seller appears
        // independently in the confirmed-bundle section.
        if(!TextUtils.isEmpty(deal.sellerId))e.putLong(EXPLORED_PREFIX+deal.sellerId,now);
        e.apply();
    }

    public static State current(Context context){
        if(context==null)return null;SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);long expires=p.getLong("expires_at",0L);
        if(expires<=System.currentTimeMillis()){p.edit().clear().apply();return null;}
        return new State(p.getString("source_signature",""),p.getString("item_id",""),p.getString("seller_id",""),p.getLong("started_at",0L),expires);
    }

    public static boolean wasExplored(Context context,String sellerId){
        if(context==null||TextUtils.isEmpty(sellerId))return false;
        return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getLong(EXPLORED_PREFIX+sellerId,0L)>0L;
    }

    public static long exploredAt(Context context,String sellerId){
        if(context==null||TextUtils.isEmpty(sellerId))return 0L;
        return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getLong(EXPLORED_PREFIX+sellerId,0L);
    }

    /** Clear only the active short-lived capture intent; explored seller history is product memory. */
    public static void clear(Context context){
        if(context==null)return;
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
                .remove("source_signature").remove("item_id").remove("seller_id").remove("started_at").remove("expires_at").apply();
    }
    private static String safe(String value){return value==null?"":value;}
}
