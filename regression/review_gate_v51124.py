from pathlib import Path
root=Path(__file__).resolve().parents[1]
java=root/'app/src/main/java/it/vintedaffari/app'
main=(java/'MainActivity.java').read_text()
market=(java/'MarketStore.java').read_text()
service=(java/'VintedAccessibilityService.java').read_text()
queue=(java/'QueueJobRunner.java').read_text()
gate=(java/'BoardGameIntakeGate.java').read_text()
resolver=(java/'VintedLinkResolver.java').read_text()
bridge=(root/'app/src/main/assets/engine/android-bridge.js').read_text()
gradle=(root/'app/build.gradle').read_text()
checks={
 'version': "versionName '5.11.24-review-gate'" in gradle and 'versionCode 107' in gradle,
 'matcher v3': 'BGG_MATCH_ALGORITHM_VERSION = 3' in market,
 'legacy reviews quarantined once': 'v51124ReviewBacklogReset' in main and 'quarantineLegacyBggReviewBacklog' in main,
 'bulk reset before row cleanup': main.index('quarantineLegacyBggReviewBacklog') < main.index('autoHideStrongNonGameReviews'),
 'bulk reset protects user overrides': 'INSERT OR IGNORE INTO listing_overrides' in market and 'ou.payload IS NOT NULL AND ou.excluded=0' in market,
 'quarantine is reversible storage': 'AUTO_QUARANTINED' in market and 'AUTO_FILTERED' in market,
 'live unresolved gate': 'BoardGameIntakeGate.afterAnalysis(card,ga)' in service and 'quarantineUnresolvedObservation' in service,
 'background unresolved gate': 'BoardGameIntakeGate.unresolvedTitle' in queue and 'autoQuarantineGame' in queue,
 'human review needs positive evidence': 'plausibleOverlap' in gate and 'hasStrongBoardGameCue' in gate,
 'size/non-game signals': 'taglia:' in gate and 'warhammer' in gate and 'funko' in gate,
 'unresolved JS exposes candidate': 'matchScore:' in bridge and 'candidate:' in bridge,
 'vinted category gate': 'catalog_id' in resolver and 'id==4881||id==4883' in resolver and 'linkCategoryRejected' in resolver,
}
for name,ok in checks.items():
    if not ok: raise AssertionError(name)
print('PASS review gate 5.11.24:',len(checks),'checks')
