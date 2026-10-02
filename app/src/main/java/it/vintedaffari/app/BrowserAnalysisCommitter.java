package it.vintedaffari.app;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Rect;
/** A stale JS result can neither materialize an identity nor finish another revision's lease. */
public final class BrowserAnalysisCommitter {
 private final DealDatabase helper;private final MarketStore market;private final BrowserCaptureStore captures;
 public BrowserAnalysisCommitter(DealDatabase helper,MarketStore market,BrowserCaptureStore captures){this.helper=helper;this.market=market;this.captures=captures;}
 static VintedCard toCard(BrowserCandidate c){return new VintedCard(c.title,c.metadata.getOrDefault("brand",""),c.metadata.getOrDefault("condition",""),c.priceCents/100.0,c.protectedPriceCents==null?null:c.protectedPriceCents/100.0,null,new Rect(),c.metadata.getOrDefault("description",""),c.metadata.getOrDefault("sellerName",""),c.itemId,c.url,"BROWSER_CAPTURE");}
 public boolean commit(BrowserCaptureStore.Claim claim,GameAnalysis analysis,long now){
  if(analysis==null||"error".equals(analysis.status))throw new IllegalArgumentException("Risposta di analisi non valida");
  VintedCard card=toCard(claim.candidate);ListingClassifier.Result listing=ListingClassifier.classify(card);
  boolean matched="matched".equals(analysis.status)&&analysis.bggId!=null&&!analysis.bggId.isEmpty();
  BoardGameIntakeGate.Decision gate=matched?BoardGameIntakeGate.matchedAnalysis(card,listing,analysis,market.isCollisionRiskTitle(card.title)):BoardGameIntakeGate.afterAnalysis(card,listing,analysis);
  SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();
  try{
   if(!captures.isCurrent(db,claim))return false;
   long listingId=market.recordBrowserSighting(db,card,listing,claim.candidate,now);String signature=market.browserSignature(db,listingId);
   ContentValues link=new ContentValues();link.put("listing_id",listingId);db.update("browser_candidates",link,"item_id=?",new String[]{claim.itemId});
   String state,reason="";
   if(market.browserProtected(db,listingId)){state="FILTERED";reason="Annuncio archiviato o escluso: osservazione conservata";}
   else if(matched&&heldGame(db,analysis.bggId)){state="FILTERED";reason="Gioco escluso o già in verifica: dati osservati conservati";helper.recordBrowserFiltered(db,card,listing,claim.candidate.observedAt,signature,reason);market.browserFiltered(db,listingId,reason,now);}
   else if(listing.type==ListingClassifier.Type.NON_GAME||gate.action==BoardGameIntakeGate.Action.QUARANTINE){state="FILTERED";reason=gate.reason;helper.recordBrowserFiltered(db,card,listing,claim.candidate.observedAt,signature,reason);market.browserFiltered(db,listingId,reason,now);}
   else{
    helper.recordAnalysis(db,card,analysis,listing,claim.candidate.observedAt,signature);
    market.applyAnalysisInTransaction(db,card,analysis,listing,now);
    // Optional metadata is kept in the canonical deal when present, never synthesized.
    db.execSQL("UPDATE deals SET seller_id=COALESCE((SELECT seller_id FROM market_listings WHERE id=?),seller_id),seller_name=COALESCE((SELECT seller_name FROM market_listings WHERE id=?),seller_name),image_url=COALESCE((SELECT image_url FROM market_listings WHERE id=?),image_url),listing_photos_csv=COALESCE((SELECT listing_photos_csv FROM market_listings WHERE id=?),listing_photos_csv),published_label=COALESCE((SELECT published_label FROM market_listings WHERE id=?),published_label) WHERE signature=?",new Object[]{listingId,listingId,listingId,listingId,listingId,signature});
    if(gate.action==BoardGameIntakeGate.Action.REVIEW||ListingClassifier.isExtremePriceAnomaly(analysis)||listing.type==ListingClassifier.Type.EXPANSION){state="REVIEW";reason=gate.reason;}
    else if(!listing.hasPositiveBoardGameEvidence()){state="TYPE_UNVERIFIED";reason="Tipo di articolo non verificato nei dati osservati";}
    else if(!matched||analysis.averageRating==null){state="BGG_PENDING";reason="Identità o dati BGG da completare";}
    else if(!DealPolicy.ratingEligible(analysis.averageRating)||analysis.languageBlocked||!DealEvaluator.evaluate(card,analysis).visible()){state="FILTERED";reason="Annuncio escluso dalle regole del Catalogo";}
    else if(DealDatabase.browserCatalogReady(db,listingId)){state="READY";}
    else{state="BGG_PENDING";reason="Verifiche del Catalogo da completare";}
   }
   boolean done=captures.complete(db,claim,state,reason,now);if(!done)throw new IllegalStateException("Lease cambiata durante il commit");db.setTransactionSuccessful();return true;
  }finally{db.endTransaction();}
 }
 private static boolean heldGame(SQLiteDatabase db,String bggId){try(Cursor c=db.rawQuery("SELECT 1 FROM games WHERE bgg_id=? AND (database_visible=0 OR match_state IN ('BGG_MATCH_REVIEW','AUTO_QUARANTINED','EPOCH_ARCHIVED_REVIEW')) LIMIT 1",new String[]{bggId})){return c.moveToFirst();}}
}
