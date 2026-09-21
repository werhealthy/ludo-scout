package it.vintedaffari.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * High-signal deal alerts only. A notification requires the central Offertona decision plus the
 * stricter 30% all-in saving gate, exact identities, eligible language and freshness.
 */
public final class DealAlertNotifier {
    public static final String CHANNEL="ludo_super_deals";
    private static final String PREFS="ludo_super_deal_alerts_v1";
    private static final int MIN_DISCOUNT_PCT=30;
    private static final long REPEAT_GAP_MS=7L*24*60*60_000L;
    private static final long MAX_DISCOVERY_AGE_MS=24L*60*60_000L;

    private DealAlertNotifier() {}

    public static void evaluateAndNotify(Context context, DealRecord d){
        if(context==null||d==null||TextUtils.isEmpty(d.signature)||TextUtils.isEmpty(d.bggId))return;
        if(!"ACTIVE".equals(d.lifecycle))return;
        // Alerts are a product promise, not a provisional classifier result. Never notify until the
        // exact Vinted identity is usable; this also gives us a stable item id for de-duplication.
        if(!exactIdentityConfirmed(context,d))return;
        if(isUnverified(d))return;
        if(isAccessoryLike(d))return;
        if(BoardGameIntakeGate.isStrongNonGameText(d.vintedTitle,d.vintedTitle))return;
        long now=System.currentTimeMillis();if(d.firstSeen>0&&now-d.firstSeen>MAX_DISCOVERY_AGE_MS)return;
        if(d.rating==null||!DealPolicy.ratingEligible(d.rating))return;
        if(!languageEligible(d.languageCode))return;
        Integer total=effectiveTotal(d);
        if(total==null||total<=0||d.benchmarkCents==null||d.benchmarkCents<=0)return;
        if(DealEvaluator.evaluate(d).decision!=DealEvaluator.Decision.GREAT_BUY)return;
        int pct=(int)Math.round((d.benchmarkCents-total)*100.0/d.benchmarkCents);
        if(pct<MIN_DISCOUNT_PCT)return;
        if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;

        SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        String key=safeKey(!TextUtils.isEmpty(d.vintedItemId)?d.vintedItemId:d.signature);long previousAt=p.getLong(key+":at",0L);int previousPct=p.getInt(key+":pct",Integer.MIN_VALUE);int previousPrice=p.getInt(key+":price",Integer.MAX_VALUE);
        boolean meaningfullyBetter=pct>=previousPct+5||total<=previousPrice-300;
        if(previousAt>0&&now-previousAt<REPEAT_GAP_MS&&!meaningfullyBetter)return;

        NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);if(nm==null)return;
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel ch=new NotificationChannel(CHANNEL,"Affari importanti",NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Solo occasioni forti che superano i filtri di Ludo Scout");
            nm.createNotificationChannel(ch);
        }
        Intent open=new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("open_deal_signature",d.signature);
        PendingIntent pi=PendingIntent.getActivity(context,(int)(Math.abs(d.signature.hashCode())%200000+30000),open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String name=!TextUtils.isEmpty(d.displayName)?d.displayName:!TextUtils.isEmpty(d.gameName)?d.gameName:d.vintedTitle;
        String euro=NumberFormat.getCurrencyInstance(Locale.ITALY).format(total/100.0);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(context,CHANNEL):new Notification.Builder(context);
        b.setSmallIcon(R.drawable.ic_deal_bolt).setContentTitle("Super affare · "+name).setContentText(euro+" · −"+pct+"% rispetto all'usato di riferimento").setAutoCancel(true).setContentIntent(pi);
        if(Build.VERSION.SDK_INT<26)b.setPriority(Notification.PRIORITY_DEFAULT);
        try{nm.notify((int)(200000+Math.abs(d.signature.hashCode()%700000)),b.build());p.edit().putLong(key+":at",now).putInt(key+":pct",pct).putInt(key+":price",total).apply();}catch(SecurityException ignored){}
    }

    private static boolean isUnverified(DealRecord d){
        String v=d==null||d.verificationState==null?"":d.verificationState.toUpperCase(Locale.ROOT);
        return v.contains("REVIEW")||v.contains("UNCERTAIN")||v.contains("ANOMALY")||v.contains("EXPANSION_CHECK")||v.contains("BGG_VARIANT_PENDING");
    }

    private static boolean isAccessoryLike(DealRecord d){
        String t=d==null||d.listingType==null?"":d.listingType.toUpperCase(Locale.ROOT);
        return t.contains("ACCESSORY")||t.contains("COMPONENT")||t.contains("EMPTY_BOX")||t.contains("NON_GAME")||t.contains("BUNDLE");
    }

    /** Alerts are allowed only after the canonical listing agrees that both the Vinted identity and
     * the BGG identity/variant are confirmed. A legacy DealRecord alone is not sufficient evidence. */
    public static boolean exactIdentityConfirmed(Context context,DealRecord d){
        if(context==null||d==null||TextUtils.isEmpty(d.vintedItemId)||TextUtils.isEmpty(d.vintedUrl))return false;
        if(d.linkConfidence==null||d.linkConfidence<90)return false;
        DealDatabase helper=null;try{helper=new DealDatabase(context.getApplicationContext());String sql="SELECT COALESCE(l.match_state,''),COALESCE(l.manual_review_required,0),COALESCE(g.match_state,'') FROM market_listings l LEFT JOIN games g ON g.id=l.game_id WHERE (l.vinted_item_id=? OR l.legacy_signature=?) AND l.lifecycle='ACTIVE' ORDER BY CASE WHEN l.vinted_item_id=? THEN 0 ELSE 1 END,l.last_seen DESC LIMIT 1";try(android.database.Cursor c=helper.getReadableDatabase().rawQuery(sql,new String[]{d.vintedItemId,d.signature==null?"":d.signature,d.vintedItemId})){if(!c.moveToFirst())return false;return "MATCHED".equals(c.getString(0))&&c.getInt(1)==0&&"MATCHED".equals(c.getString(2));}}catch(Throwable ignored){return false;}finally{if(helper!=null)try{helper.close();}catch(Throwable ignored){}}
    }

    private static Integer effectiveTotal(DealRecord d){
        if(d.shippingVerifiedCents!=null){int base=d.protectedPriceCents!=null?d.protectedPriceCents:d.itemPriceCents+PurchaseMath.vintedFee(d.itemPriceCents);return base+d.shippingVerifiedCents;}
        if(d.totalCents!=null)return d.totalCents;
        return d.protectedPriceCents!=null?d.protectedPriceCents:d.itemPriceCents;
    }

    private static boolean languageEligible(String raw){
        if(TextUtils.isEmpty(raw))return false;String lc=raw.toUpperCase(Locale.ROOT);
        if(lc.contains("IND"))return true;
        return lc.startsWith("IT")||lc.startsWith("EN");
    }

    private static String safeKey(String signature){return Integer.toHexString(signature.hashCode());}
}
