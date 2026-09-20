package it.vintedaffari.app;
import java.util.*;
/** Real same-seller pairs. Unknown bundle shipping means no claimed total or discount. */
public final class BundlePlanner {
 public static List<BundleSuggestion> forSource(DealRecord source,List<DealRecord> records){List<BundleSuggestion> out=new ArrayList<>();if(!usable(source))return out;
  for(DealRecord d:records){if(!usable(d)||source.signature.equals(d.signature)||source.vintedItemId.equals(d.vintedItemId)||!source.sellerId.equals(d.sellerId))continue;
   BundleSuggestion b=new BundleSuggestion();b.sourceSignature=source.signature;b.sellerId=source.sellerId;b.itemId=d.vintedItemId;b.itemUrl=d.vintedUrl;b.title=d.vintedTitle;b.bggId=d.bggId;b.gameName=d.displayName==null?d.gameName:d.displayName;b.rating=d.rating;b.qualityScore=d.qualityScore;b.itemPriceCents=d.itemPriceCents;b.benchmarkCents=d.benchmarkCents;b.languageCode=d.languageCode;b.imageUrl=d.bggImageUrl==null?d.imageUrl:d.bggImageUrl;b.scannedAt=System.currentTimeMillis();
   // Per-item shipping does not establish shipping for a larger parcel.
   b.estimatedBundleTotalCents=null;b.savingPct=null;out.add(b);
  }out.sort((a,b)->Integer.compare(b.qualityScore==null?0:b.qualityScore,a.qualityScore==null?0:a.qualityScore));return out;
 }
 private static boolean usable(DealRecord d){return d!=null&&DealPolicy.ratingEligible(d)&&"ACTIVE".equals(d.lifecycle)&&!"verify".equals(d.tier)&&d.itemPriceCents>0&&d.sellerId!=null&&d.vintedItemId!=null&&d.vintedUrl!=null&&d.bggId!=null;}
}
