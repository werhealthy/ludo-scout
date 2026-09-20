package it.vintedaffari.app;

import android.graphics.Rect;
import android.text.TextUtils;

import java.util.List;

/**
 * One-shot, zero-network audit for BGG identities accepted before provenance algorithm v4.
 *
 * It never deletes or silently reassigns an identity. A listing is considered independently
 * verified only when its seller title, after conservative marketplace cleanup, resolves exactly
 * and uniquely to the already stored BGG id in the bundled BGG index. Everything weaker becomes
 * persistent review instead of being guessed.
 */
public final class BggHistoricalRevalidator {
    public static final String BUILD="bgg-historical-revalidation-v2";

    private BggHistoricalRevalidator(){}

    public static int runSlice(MarketStore market,BggSearchClient matcher,int limit){
        if(market==null||matcher==null)return 0;
        List<MarketStore.HistoricalBggCandidate> games=market.historicalBggRevalidationCandidates(Math.max(1,Math.min(32,limit)));
        if(games.isEmpty()){
            market.setDiagnosticState("bgg_historical_revalidation",1,"state=DONE;"+market.historicalBggRevalidationSummary());
            return 0;
        }

        int processed=0,verifiedGames=0,reviewGames=0,reviewListings=0;
        for(MarketStore.HistoricalBggCandidate candidate:games){
            if(candidate==null||candidate.gameId<=0||TextUtils.isEmpty(candidate.bggId))continue;
            int supported=0,review=0;
            BggSearchClient.Game expected=matcher.localById(candidate.bggId);

            if(expected==null){
                for(MarketStore.HistoricalBggListing listing:candidate.listings){
                    market.flagHistoricalBggReview(listing.id,"Rivalidazione BGG storica: ID "+candidate.bggId+" non presente nell'indice locale corrente.");
                    review++;reviewListings++;
                }
                market.completeHistoricalBggRevalidation(candidate.gameId,"REVIEW_NO_LOCAL_BGG","listings="+candidate.listings.size(),false,false);
                reviewGames++;processed++;continue;
            }

            for(MarketStore.HistoricalBggListing listing:candidate.listings){
                Evaluation e=evaluate(listing,expected,matcher);
                if(e.verified)supported++;
                else{
                    market.flagHistoricalBggReview(listing.id,e.reason);
                    review++;reviewListings++;
                }
            }

            boolean independentlyVerified=supported>0;
            String state=review==0?"VERIFIED":"REVIEW";
            market.completeHistoricalBggRevalidation(candidate.gameId,state,
                    "bgg="+candidate.bggId+"; supported="+supported+"; review="+review+"; listings="+candidate.listings.size(),
                    independentlyVerified,false);
            if(review>0)reviewGames++;else verifiedGames++;
            processed++;
        }

        if(processed>0)market.notifyHistoricalBggRevalidationChanged();
        market.setDiagnosticState("bgg_historical_revalidation",1,
                "state=RUNNING;build="+BUILD+";slice="+processed+";verifiedGames="+verifiedGames+
                        ";reviewGames="+reviewGames+";reviewListings="+reviewListings+";"+market.historicalBggRevalidationSummary());
        return processed;
    }

    private static Evaluation evaluate(MarketStore.HistoricalBggListing listing,BggSearchClient.Game expected,BggSearchClient matcher){
        if(listing==null)return new Evaluation(false,"Rivalidazione BGG storica: listing non leggibile.");
        VintedCard card=new VintedCard(listing.title,listing.brand,listing.condition,
                listing.priceCents/100.0,null,null,new Rect(),listing.observedText);
        ListingClassifier.Result classified=ListingClassifier.classify(card);
        if(classified.type==ListingClassifier.Type.NON_GAME||
                classified.type==ListingClassifier.Type.ACCESSORY||
                classified.type==ListingClassifier.Type.COMPONENTS||
                classified.type==ListingClassifier.Type.EMPTY_BOX||
                classified.type==ListingClassifier.Type.BUNDLE){
            return new Evaluation(false,"Rivalidazione BGG storica: "+classified.type+" · "+classified.reason);
        }

        boolean sawExact=false,sawCurrent=false,sawDifferent=false,sawAmbiguous=false;
        String differentName="";
        List<String> variants=BggTitleNormalizer.variants(listing.title);
        for(String q:variants){
            List<BggSearchClient.Game> exact=matcher.localExactCandidates(q);
            if(exact.isEmpty())continue;
            sawExact=true;boolean current=false,different=false;
            for(BggSearchClient.Game g:exact){
                if(g==null||TextUtils.isEmpty(g.id))continue;
                if(expected.id.equals(g.id))current=true;
                else{different=true;if(TextUtils.isEmpty(differentName))differentName=g.name;}
            }
            if(current&&exact.size()==1)return new Evaluation(true,"Titolo seller risolto esattamente e univocamente al BGG corrente.");
            if(current)sawCurrent=true;
            if(different)sawDifferent=true;
            if(current&&different)sawAmbiguous=true;
        }

        if(sawExact&&!sawCurrent&&sawDifferent)
            return new Evaluation(false,"Rivalidazione BGG storica: il titolo seller risolve esattamente a un altro BGG"+(TextUtils.isEmpty(differentName)?"":" ("+differentName+")")+".");
        if(sawAmbiguous)
            return new Evaluation(false,"Rivalidazione BGG storica: lo stesso titolo/alias appartiene a più identità BGG.");
        if(sawExact&&sawCurrent)
            return new Evaluation(false,"Rivalidazione BGG storica: match esatto non univoco, serve verifica.");

        // No fuzzy score is promoted to truth during historical cleanup. A plausible overlap is
        // useful context for the reviewer, but not enough to preserve an old automatic MATCHED.
        boolean overlap=BoardGameIntakeGate.plausibleOverlap(listing.title,expected.name);
        if(!overlap)for(String alias:expected.aliases)if(BoardGameIntakeGate.plausibleOverlap(listing.title,alias)){overlap=true;break;}
        return new Evaluation(false,overlap
                ?"Rivalidazione BGG storica: titolo plausibile ma non esatto/univoco; serve verifica."
                :"Rivalidazione BGG storica: il titolo seller non conferma indipendentemente il BGG corrente.");
    }

    private static final class Evaluation{
        final boolean verified;final String reason;
        Evaluation(boolean verified,String reason){this.verified=verified;this.reason=reason;}
    }
}
