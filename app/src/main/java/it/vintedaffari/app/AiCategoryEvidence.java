package it.vintedaffari.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Product evidence is independent of mutable analysis/verification reasons.
 * Existing queue_controls stores a fingerprint, not identity or publication trust.
 * A changed title, brand, full source text or photo list requires fresh visual evidence.
 */
final class AiCategoryEvidence {
 private AiCategoryEvidence(){}
 static final String STORE_SQL="INSERT OR REPLACE INTO queue_controls(name,value,updated_at,text_value) VALUES('ai_category_evidence:'||?,1,?,?)";
 static final String READ_SQL="SELECT COALESCE(l.vinted_title,''),COALESCE(l.brand,''),COALESCE(l.observed_text,''),"
  +"COALESCE(NULLIF(l.listing_photos_csv,''),NULLIF(l.image_url,''),''),q.text_value,COALESCE(l.category_normalized,'') "
  +"FROM market_listings l JOIN queue_controls q ON q.name='ai_category_evidence:'||l.id "
  +"LEFT JOIN deals d ON d.signature=COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) "
  +"WHERE l.id=? AND l.lifecycle='ACTIVE' AND COALESCE(l.manual_review_required,0)=0 "
  +"AND COALESCE(d.confirmed,0)=0 AND COALESCE(d.verification_state,'')<>'USER_CONFIRMED' "
  +"AND NOT EXISTS(SELECT 1 FROM listing_overrides u WHERE u.signature=COALESCE(NULLIF(l.legacy_signature,''),l.temp_fingerprint) OR (u.item_id IS NOT NULL AND u.item_id=l.vinted_item_id)) "
  +"AND COALESCE(l.match_state,'')<>'CATEGORY_INCOMPATIBLE'";

 static void remember(SQLiteDatabase db,long listingId,String inputKey,long now){
  db.execSQL(STORE_SQL,new Object[]{listingId,now,inputKey});
 }
 static boolean has(SQLiteDatabase db,long listingId){
  return has(db,listingId,null);
 }
 static boolean has(SQLiteDatabase db,long listingId,VintedCard card){
  try(Cursor c=db.rawQuery(READ_SQL,new String[]{String.valueOf(listingId)})){
   return c.moveToFirst()&&!ListingClassifier.isExplicitNonGameCategory(c.getString(5))
    &&!BoardGameIntakeGate.isStrongNonGameText(c.getString(0),c.getString(2))
    &&(card==null||(java.util.Objects.equals(c.getString(0),card.title==null?"":card.title)
      &&java.util.Objects.equals(c.getString(1),card.brand==null?"":card.brand)
      &&java.util.Objects.equals(c.getString(2),card.rawDescription==null?"":card.rawDescription)))
    &&c.getString(4)!=null&&c.getString(4).equals(
    AiBetaListings.inputKey(c.getString(0),c.getString(1),c.getString(2),c.getString(3)));
  }
 }
}
