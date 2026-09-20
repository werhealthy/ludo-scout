package it.vintedaffari.app;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One bounded unit of durable queue work. Shared by the foreground processor and WorkManager.
 * Claiming remains transactional in MarketStore, so multiple wake-up mechanisms cannot process
 * the same row at the same time.
 */
public final class QueueJobRunner {
    private static final String TAG = "LudoBackground";
    private QueueJobRunner() {}

    /** Zero-network BGG identity stage. It first reuses aliases Ludo Scout has already learned,
     * then tries conservative title cleanup, exact BGG aliases and finally high-confidence fuzzy
     * ranking. Reviews from older matcher versions are eligible once; true ambiguities do not loop. */
    public static int matchBggIdentities(Context context,MarketStore market,BggSearchClient matcher,int limit){
        if(market==null||matcher==null||market.isBggPaused())return 0;
        List<GameRecord> pending=market.provisionalGamesForMatching(Math.max(1,Math.min(40,limit)));
        int handled=0;
        for(GameRecord g:pending){
            if(g==null)continue;
            try{
                market.setLaneStatus("bgg","MATCHING",g.name,0L);
                BggSearchClient.Game chosen=null; double confidence=0; String reviewReason=null; List<BggSearchClient.Game> fuzzyCandidates=java.util.Collections.emptyList();
                List<String> variants=BggTitleNormalizer.variants(g.name);
                if(variants.isEmpty())variants=java.util.Collections.singletonList(g.name);

                // 1) Local memory: a unique alias previously confirmed by the user/app is strongest.
                for(String q:variants){
                    String learned=market.learnedBggIdForTitle(q);
                    if(TextUtils.isEmpty(learned))continue;
                    BggSearchClient.Game remembered=matcher.localById(learned);
                    if(remembered!=null){chosen=remembered;confidence=99.5;break;}
                }

                // 2) Exact primary-name/alias match after conservative marketplace cleanup.
                boolean sawAmbiguousExact=false;
                if(chosen==null){
                    for(String q:variants){
                        List<BggSearchClient.Game> exact=matcher.localExactCandidates(q);
                        if(exact.size()==1){chosen=exact.get(0);confidence=q.equals(BggTitleNormalizer.clean(g.name))?99:98;break;}
                        if(exact.size()>1)sawAmbiguousExact=true;
                    }
                }

                // 3) Fuzzy only when there is a clear winner. Cleanup raises recall without
                // accepting same-title collisions; expansions/sequel words were never stripped.
                if(chosen==null&&!sawAmbiguousExact){
                    String q=variants.get(variants.size()-1);
                    fuzzyCandidates=matcher.localCandidates(q);
                    if(!fuzzyCandidates.isEmpty()){
                        BggSearchClient.Game best=fuzzyCandidates.get(0);
                        int second=fuzzyCandidates.size()>1?fuzzyCandidates.get(1).searchScore:0;
                        int gap=best.searchScore-second;
                        if((best.searchScore>=940&&gap>=100)||(best.searchScore>=920&&gap>=160)){
                            chosen=best; confidence=Math.min(98,92+(best.searchScore-920)/20.0);
                        }else reviewReason=fuzzyCandidates.size()>1?"Più match BGG plausibili":"Match BGG non abbastanza sicuro";
                    }else reviewReason="Nessun candidato BGG locale";
                }else if(chosen==null){
                    reviewReason="Più giochi BGG hanno lo stesso titolo";
                }

                if(chosen!=null)market.assignAutoBggMatch(g.id,chosen,confidence);
                else {
                    BoardGameIntakeGate.Decision gate=BoardGameIntakeGate.unresolvedTitle(g.name,fuzzyCandidates,sawAmbiguousExact);
                    if(gate.action==BoardGameIntakeGate.Action.REVIEW)market.markBggMatchReview(g.id,TextUtils.isEmpty(reviewReason)?gate.reason:reviewReason);
                    else market.autoQuarantineGame(g.id,gate.reason);
                }
                handled++;market.touchLaneHeartbeat("bgg");
            }catch(Throwable t){market.markBggMatchReview(g.id,"Errore match locale: "+safe(t));handled++;}
        }
        return handled;
    }

    public static boolean processOneBgg(Context context, MarketStore market, BggEnricher bgg) {
        return processBggBatch(context,market,bgg,20)>0;
    }

    /** One BGG HTTP request can settle up to 20 durable jobs. */
    public static int processBggBatch(Context context,MarketStore market,BggEnricher bgg,int limit) {
        List<MarketStore.Job> jobs=market.claimBggBatch(System.currentTimeMillis(),Math.max(1,Math.min(20,limit)));
        if(jobs.isEmpty())return 0;
        ArrayList<String> ids=new ArrayList<>();Map<String,MarketStore.Job> byId=new LinkedHashMap<>();
        for(MarketStore.Job job:jobs){
            String id=market.bggIdForGame(job.gameId);
            if(TextUtils.isEmpty(id)){market.failPermanent(job,"BGG id mancante");continue;}
            ids.add(id);byId.put(id,job);
        }
        market.setJobsProgress(jobs,24);
        if(ids.isEmpty())return jobs.size();
        try{
            BggEnricher.BatchOutcome out=bgg.enrichBatchBlocking(ids,p->market.setJobsProgress(jobs,p));
            long retryAt=System.currentTimeMillis()+10*60_000L;
            for(Map.Entry<String,MarketStore.Job> e:byId.entrySet()){
                String id=e.getKey();MarketStore.Job job=e.getValue();
                if(out.completed.contains(id)){
                    // applyBggMetadata atomically marks the durable bgg:<game> job complete.
                    // If a future parser path stops doing so, this call safely finalises the lease.
                    if(market.isLeaseActive(job))market.completeJob(job);
                }else if(market.isLeaseActive(job))market.retryJob(job,out.failed.getOrDefault(id,"BGG senza risultato"),retryAt);
            }
        }catch(Throwable t){
            long retryAt=System.currentTimeMillis()+10*60_000L;
            for(MarketStore.Job job:jobs)if(market.isLeaseActive(job))market.retryJob(job,safe(t),retryAt);
        }
        return jobs.size();
    }


    public static boolean processOneVinted(Context context, DealDatabase db, MarketStore market,
                                            AutoLinkResolver resolver) {
        if(market.isTest2bExclusiveActive())return false;
        return processOneVintedInternal(context,db,market,resolver,false);
    }

    /** TEST 2b only. Executes one normal durable Vinted job while owning the diagnostic lock.
     * It uses the exact same resolver and gate as production; only job claiming is exclusive. */
    public static boolean processOneVintedForTest2b(Context context, DealDatabase db, MarketStore market,
                                                     AutoLinkResolver resolver) {
        return processOneVintedInternal(context,db,market,resolver,true);
    }

    private static boolean processOneVintedInternal(Context context, DealDatabase db, MarketStore market,
                                                     AutoLinkResolver resolver, boolean test2bOwner) {
        long now = System.currentTimeMillis();
        if (market.isVintedPaused()) return false;
        // Production batch pass: consume fresh catalogue snapshots locally before spending the next
        // Vinted permit. Test 2b intentionally bypasses this so its old request-ledger measurement
        // remains comparable. LIVE/HUNT/MANUAL jobs preempt inside VintedBatchEngine.
        if(!test2bOwner){
            int local=VintedBatchEngine.applyCached(context,db,market,6,false);
            if(local>0)return true;
        }
        if (VintedPublicSession.nextAllowedAt(context) > now) return false;
        // If a live/hunt/manual identity is waiting for its own retry time, intentionally leave the
        // public-page lane idle rather than spend the next permit on backlog work.
        if(!test2bOwner && market.urgentVintedWorkCount(now)>0 && market.urgentVintedDueCount(now)==0) return false;
        // Keep only a tiny network window. Deferred listings live outside processing_jobs until the
        // lane is actually available, so thousands of eventual links never block a fresh deal.
        if(market.coreVintedActiveCount()<6&&market.deferredVintedCount()>0)market.promoteDeferredVintedBatch(6-market.coreVintedActiveCount());
        MarketStore.Job job = test2bOwner?market.claimNextVintedJobForTest2b(now):market.claimNextVintedJob(now);
        if (job == null) return false;
        processVinted(context, db, market, resolver, job);
        // A catalogue request may just have refreshed a shared snapshot. Consume it immediately
        // before the next durable job can spend another Vinted request on the same game family.
        // Test 2b stays isolated so historical request-ledger measurements remain comparable.
        if(!test2bOwner) VintedBatchEngine.applyCached(context,db,market,6,true);
        return true;
    }

    public static void sweepMissing(Context context, MarketStore market) {
        try {
            android.content.SharedPreferences auto=context.getSharedPreferences("ludo_queue_maintenance",Context.MODE_PRIVATE);
            long last=auto.getLong("last_missing_sweep",0L),now=System.currentTimeMillis();
            // Local inference is intentionally independent from the Vinted HTTP gate.
            market.inferDeferredLanguages(120);
            if(now-last>=30*60_000L){
                int canonical=market.enqueueIncompleteListingsBackground(120);
                int legacy=market.enqueueMissingLegacyDeals();
                auto.edit().putLong("last_missing_sweep",now).putInt("last_missing_scheduled",canonical+legacy).apply();
            }
        } catch(Throwable t){ Log.d(TAG,"automatic missing-data sweep skipped",t); }
    }

    private static void processVinted(Context context, DealDatabase db, MarketStore market,
                                      AutoLinkResolver resolver, MarketStore.Job job) {
        MarketListingRecord listing = market.listing(job.listingId);
        if (listing == null || TextUtils.isEmpty(listing.title)) {
            market.failPermanent(job, "listing mancante o senza titolo");
            return;
        }
        GameRecord game = market.gameForListing(listing);
        DealRecord candidate = listing.asDealRecord(game);
        long now = System.currentTimeMillis();
        long allowed = Math.max(VintedPublicSession.nextAllowedAt(context), resolver.nextAllowedAt(candidate));
        if (allowed > now) {
            market.retryJob(job, "attesa richiesta Vinted", allowed);
            return;
        }

        market.setJobProgress(job, 28); // request prepared
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<VintedLinkResolver.Result> resolved = new AtomicReference<>();
        AtomicReference<String> failure = new AtomicReference<>();
        AtomicBoolean accepting = new AtomicBoolean(true);
        resolver.resolve(candidate, new AutoLinkResolver.Callback() {
            @Override public void onResolved(VintedLinkResolver.Result r) { if(accepting.compareAndSet(true,false)){resolved.set(r);latch.countDown();} }
            @Override public void onUnresolved(String signature, String reason) { if(accepting.compareAndSet(true,false)){failure.set(reason);latch.countDown();} }
            @Override public void onProgress(String signature,int progress,String stage) { if(accepting.get())market.setJobProgress(job,progress); }
            @Override public void onCandidates(String signature,java.util.List<VintedLinkResolver.CandidateOption> candidates) { if(accepting.get())market.saveVintedCandidates(job.listingId,candidates); }
        });

        try {
            long deadline=System.currentTimeMillis()+145_000L;
            boolean finished=false;
            while(System.currentTimeMillis()<deadline){
                // A manual skip/watchdog releases the durable lease. Polling it makes the serial
                // lane move to the next card within ~1 second instead of waiting for the old HTTP callback.
                if(!market.isLeaseActive(job)){accepting.set(false);return;}
                long left=deadline-System.currentTimeMillis();
                if(latch.await(Math.max(1L,Math.min(1_000L,left)),TimeUnit.MILLISECONDS)){finished=true;break;}
            }
            if(!finished){
                accepting.set(false);
                market.retryJob(job, "timeout recupero Vinted", System.currentTimeMillis() + 2 * 60_000L);
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            market.retryJob(job, "elaborazione interrotta", System.currentTimeMillis() + 60_000L);
            return;
        }

        if(!market.isLeaseActive(job)){accepting.set(false);return;}
        market.setJobProgress(job, 62); // resolver returned a response, successful or not
        VintedLinkResolver.Result r = resolved.get();
        if(r==null){VintedLinkResolver.CandidateOption eq=market.equivalentVintedCandidate(job.listingId);if(eq!=null){r=new VintedLinkResolver.Result();r.signature=candidate.signature;r.itemId=eq.id;r.url=!TextUtils.isEmpty(eq.url)?eq.url:"https://www.vinted.it/items/"+eq.id;r.imageUrl=eq.imageUrl;r.sellerId=eq.sellerId;r.sellerName=eq.sellerName;r.matchedTitle=eq.title;r.sold=eq.sold;r.confidence=93;r.needsDeepMetadata=true;r.reason="Auto-match: candidati Vinted equivalenti per titolo e prezzo";android.content.SharedPreferences p=context.getSharedPreferences("va_v3_diag",Context.MODE_PRIVATE);p.edit().putLong("vintedEquivalentAutoMatches",p.getLong("vintedEquivalentAutoMatches",0L)+1L).apply();}}
        if (r != null) {
            market.setJobProgress(job, 78);
            long canonical = market.applyResolvedLink(job, r);
            DealRecord legacy = db.findBySignature(r.signature);
            if (r.sold) {
                market.markSold(canonical);
                if (legacy != null) db.markSold(legacy.signature);
            } else if (legacy != null) {
                db.applyResolvedLink(legacy.signature, r.itemId, r.url, r.imageUrl, r.confidence, r.reason,
                        r.sellerId, r.sellerName, r.photosCsv, System.currentTimeMillis());
                if(!TextUtils.isEmpty(r.matchedTitle))db.updateVintedTitle(legacy.signature,r.matchedTitle);
                if (!TextUtils.isEmpty(r.publishedLabel)) db.updatePublishedLabel(legacy.signature, r.publishedLabel);
            }
            // An exact public item page is authoritative for the current asking price. Preserve the
            // original observations as history, but refresh the current Market/legacy card so a
            // Vinted price drop (e.g. 10 € -> 6 €) is not hidden behind the old scroll-time price.
            if(canonical>0 && r.exactPagePrice && r.verifiedCurrentPriceCents!=null){
                boolean marketPriceChanged=market.updateVerifiedCurrentPrice(canonical,r.verifiedCurrentPriceCents,r.verifiedProtectedPriceCents);
                boolean legacyPriceChanged=db.updateVerifiedCurrentPrice(r.signature,r.verifiedCurrentPriceCents,r.verifiedProtectedPriceCents,System.currentTimeMillis());
                if(marketPriceChanged||legacyPriceChanged){android.content.SharedPreferences p=context.getSharedPreferences("va_v3_diag",Context.MODE_PRIVATE);p.edit().putLong("vintedVerifiedPriceUpdates",p.getLong("vintedVerifiedPriceUpdates",0L)+1L).putInt("vintedVerifiedCurrentPriceCents",r.verifiedCurrentPriceCents).apply();market.setDiagnosticState("verified_price_refresh",1,"build=verified-item-price-v1;listing="+canonical+";current="+r.verifiedCurrentPriceCents+";protected="+(r.verifiedProtectedPriceCents==null?"n/a":r.verifiedProtectedPriceCents));}
            }
            market.setJobProgress(job, 90); // canonical + legacy DB updated
            // If the public item page exposed richer text, use it to distinguish a base game from a
            // more specific BGG variant (e.g. Tokaido vs Tokaido Duo) without another network call.
            if(canonical>0 && (!TextUtils.isEmpty(r.detailsText)||!TextUtils.isEmpty(r.matchedTitle))){
                BggVariantReconciler.reconcile(context,db,market,canonical,r.signature,r.matchedTitle,r.detailsText);
            }
            // Deal/Hunt notifications are evaluated only after the exact Vinted identity exists and
            // the variant guard has had a chance to reject a wrong game/accessory association.
            DealRecord alertReady=db.findBySignature(r.signature);if(alertReady!=null){DealAlertNotifier.evaluateAndNotify(context,alertReady);HuntDatabase.evaluateAndNotifyLinked(context,alertReady);}
            if (!TextUtils.isEmpty(r.imageUrl)) ThumbnailStore.downloadRemote(context, r.signature, r.imageUrl);
            market.setJobProgress(job, 96); // optional thumbnail scheduled
            market.clearVintedCandidates(job.listingId);
            market.completeJob(job);
            if(canonical>0 && TextUtils.isEmpty(r.publishedLabel)){
                // Publication time is core information for this product. It is very low priority and
                // can never jump ahead of fresh identities; seller/photo are no longer requirements.
                market.enqueueDeepMetadata(canonical);
            }
            return;
        }

        String reason = TextUtils.isEmpty(failure.get()) ? "nessun risultato" : failure.get();

        // Deep metadata is optional: once the core id/url is known it must never clog the user-visible
        // queue. Two deterministic misses are enough; keep the core listing and stop retrying.
        if(MarketStore.JOB_VINTED_DEEP.equals(job.type)&&isDeterministicMiss(reason)&&job.attempt>=2){
            String variantReason="Pagina Vinted non ha fornito abbastanza testo per confermare la variante BGG";
            if(market.flagPendingBggVariantReview(job.listingId,variantReason)&&candidate!=null&&!TextUtils.isEmpty(candidate.signature))db.flagBggVariantReview(candidate.signature,variantReason);
            market.completeJob(job);return;
        }

        // Eventual background linking must never create a giant human review queue. A real remote
        // limit remains on the current retryable job and resumes automatically; deterministic misses
        // go back to the deferred pool for a later fresh attempt.
        if("DEFERRED_LINK".equals(job.source) && isDeterministicMiss(reason)){
            if(market.listingBelongsToActiveRun(job.listingId)){
                if(job.attempt>=2){market.needsReview(job,reason);return;}
                market.retryJob(job,reason,System.currentTimeMillis()+5L*60_000L);return;
            }
            market.deferBackgroundLink(job,reason,System.currentTimeMillis()+24L*60*60_000L);return;
        }

        // A 404 on the actual public item page is actionable information, not a network retry loop.
        // Surface it immediately so the user can archive the stale listing or explicitly retry it.
        if(MarketStore.JOB_VINTED.equals(job.type)&&isGoneVintedPage(reason)){
            market.needsReview(job,reason);return;
        }

        // A core search that repeatedly returns equally plausible candidates is not a network outage.
        // Give fresh data one second chance, then surface it as a manual-review case.
        if (isDeterministicMiss(reason) && job.attempt >= 2) {
            market.needsReview(job, reason);
            return;
        }

        long next;
        if (isTransientVintedWait(reason)) {
            next = Math.max(VintedPublicSession.nextAllowedAt(context), resolver.nextAllowedAt(candidate));
            if (next <= System.currentTimeMillis()) next = System.currentTimeMillis() + 60_000L;
        } else if (isDeterministicMiss(reason)) {
            next = System.currentTimeMillis() + 5 * 60_000L;
        } else if (job.attempt >= 6) {
            // Six unsuccessful full searches is enough evidence that automatic matching is not
            // making progress. Keep the listing/history, stop burning network, let a later fresh
            // sighting reopen the deduplicated job automatically.
            market.needsReview(job, reason);
            return;
        } else {
            long base = Math.min(6 * 60 * 60_000L, 10 * 60_000L * (1L << Math.min(5, Math.max(0, job.attempt - 1))));
            next = System.currentTimeMillis() + base;
        }
        market.retryJob(job, reason, next);
    }

    private static boolean isGoneVintedPage(String reason){return reason!=null&&reason.toLowerCase(java.util.Locale.ROOT).contains("pagina vinted non disponibile (404)");}

    private static boolean isDeterministicMiss(String reason) {
        if (reason == null) return false;
        String s = reason.toLowerCase(java.util.Locale.ROOT);
        return s.contains("nessun candidato vinted abbastanza univoco")
                || s.contains("nessun annuncio compatibile")
                || s.contains("più annunci compatibili")
                || s.contains("non corrispondono abbastanza")
                || s.contains("non supera la verifica")
                || s.contains("pagina vinted non disponibile (404)")
                || s.contains("metadati dell’annuncio non sono leggibili")
                || s.contains("metadati seller/foto non disponibili")
                || s.contains("candidati equivalenti")
                || s.contains("ambigu");
    }

    private static boolean isTransientVintedWait(String reason) {
        if (reason == null) return false;
        String s = reason.toLowerCase(java.util.Locale.ROOT);
        return s.contains("cooldown") || s.contains("rallent") || s.contains("richieste")
                || s.contains("429") || s.contains("403") || s.contains("timeout")
                || s.contains("tempor") || s.contains("rete") || s.contains("network");
    }

    private static String safe(Throwable t) {
        String s = t == null ? "errore" : t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
        return s.length() > 220 ? s.substring(0, 220) : s;
    }
}
