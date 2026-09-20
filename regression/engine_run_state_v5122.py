from pathlib import Path
root=Path(__file__).resolve().parents[1]
app=root/'app/src/main/java/it/vintedaffari/app'
main=(app/'MainActivity.java').read_text()
db=(app/'DealDatabase.java').read_text()
store=(app/'MarketStore.java').read_text()
batch=(app/'VintedBatchEngine.java').read_text()
runner=(app/'QueueJobRunner.java').read_text()
svc=(app/'QueueKeepAliveService.java').read_text()
gradle=(root/'app/build.gradle').read_text()
checks={
 'version_5_12_2': "versionCode 117" in gradle and "5.12.2-engine-run-state" in gradle,
 'db_v20': 'DB_VERSION=20' in db and 'upgradeV19ToV20' in db,
 'review_columns': 'manual_review_required INTEGER NOT NULL DEFAULT 0' in store and 'manual_review_reason TEXT' in store,
 'review_set_on_failure': 'markManualReviewOpen(job.listingId,error)' in store,
 'review_query_is_sticky': "COALESCE(l.manual_review_required,0)=1" in store and 'Durable human-review inbox' in store,
 'review_clear_on_explicit_link': 'l.put("manual_review_required",0)' in store and 'applyTrustedVintedLink' in store,
 'review_clear_on_archive': 'l.put("lifecycle","REMOVED")' in store and 'l.put("manual_review_required",0)' in store,
 'bgg_review_already_sticky': "match_state='BGG_MATCH_REVIEW'" in store and 'markBggMatchReview' in store,
 'session_gap_shared': 'ENGINE_SESSION_GAP_MS=3L*60_000L' in db,
 'oldest_unfinished_active': 'for(int i=sessions.size()-1;i>=0;i--)' in db and '!engineAutomaticDone(s,now)' in db,
 'waiting_sessions': 'waitingObservationSessionCount' in db and 'scroll successiv' in main,
 'ui_uses_active_not_latest': 'DealDatabase.ObservationSession run=db.activeObservationSession()' in main,
 'batch_scoped_to_active_run': 'activeRun=helper.activeObservationSession()' in batch and 'observed_at>=? AND observed_at<=?' in batch,
 'promotion_scoped_to_active_run': 'activeRun=helper.activeObservationSession()' in store and 'MIN(l.first_seen) ASC' in store,
 'claim_scoped_but_urgent_preempts': "j.source IN ('LIVE_DEAL','HUNT_PRIORITY','MANUAL_PRIORITY')" in store and 'runGate' in store,
 'active_deferred_miss_becomes_review': 'listingBelongsToActiveRun' in runner and 'job.attempt>=2' in runner and 'market.needsReview(job,reason)' in runner,
 'ready_means_deep_complete': "l.enrichment_state='COMPLETE'" in db,
 'review_counts_as_auto_settled': 's.completeListings+s.reviewListings>=s.validListings' in db,
 'notifications_ordered': 'maybeNotifyNextRunComplete' in svc and 'if(!DealDatabase.engineAutomaticDone(run,now))return' in svc,
}
failed=[k for k,v in checks.items() if not v]
for k,v in checks.items(): print(('PASS' if v else 'FAIL'),k)
raise SystemExit(1 if failed else 0)
