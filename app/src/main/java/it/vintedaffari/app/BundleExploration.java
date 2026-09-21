package it.vintedaffari.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

/** Short-lived intent created when the user deliberately opens Vinted from the Bundle area. */
public final class BundleExploration {
    private static final String PREFS="bundle_exploration_v1";
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
        if(context==null||deal==null)return;long now=System.currentTimeMillis();
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
                .putString("source_signature",safe(deal.signature)).putString("item_id",safe(deal.vintedItemId)).putString("seller_id",safe(deal.sellerId))
                .putLong("started_at",now).putLong("expires_at",now+TTL_MS).apply();
    }

    public static State current(Context context){
        if(context==null)return null;SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);long expires=p.getLong("expires_at",0L);
        if(expires<=System.currentTimeMillis()){p.edit().clear().apply();return null;}
        return new State(p.getString("source_signature",""),p.getString("item_id",""),p.getString("seller_id",""),p.getLong("started_at",0L),expires);
    }

    public static void clear(Context context){if(context!=null)context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().clear().apply();}
    private static String safe(String value){return value==null?"":value;}
}
