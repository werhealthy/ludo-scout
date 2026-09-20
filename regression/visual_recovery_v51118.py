from pathlib import Path
root=Path(__file__).resolve().parents[1]
java=root/'app/src/main/java/it/vintedaffari/app'
main=(java/'MainActivity.java').read_text()
market=(java/'MarketStore.java').read_text()
resolver=(java/'VintedLinkResolver.java').read_text()
service=(java/'VintedAccessibilityService.java').read_text()
thumb=(java/'ThumbnailStore.java').read_text()
photo=(java/'VintedPhotoMatcher.java').read_text()
card=(java/'VintedCard.java').read_text()
parser=(java/'VintedCardParser.java').read_text()
product=(java/'ProductPageParser.java').read_text()
gradle=(root/'app/build.gradle').read_text()
checks={
    'version': "versionName '5.11.18-visual-recovery'" in gradle,
    'capture at observation': 'ThumbnailStore.captureMissing(this, discovered)' in service,
    'visual matcher exists': 'class VintedPhotoMatcher' in photo and 'VisualCoverMatcher.similarity' in photo,
    'photo tie breaker': 'applyPhotoEvidence(d,ranked)' in resolver and 'photoSimilarity' in resolver,
    'broad search local price guard': '/catalog?search_text=' in resolver and 'if(!containsPrice(near,euros))continue' in resolver,
    'seller card hint': 'sellerName' in card and 'SELLER_PATTERN' in parser,
    'seller detail hint': 'p.sellerName' in product and 'seller_name' in market,
    'candidate photo persisted': 'photoSimilarity' in market and 'foto "+Math.round(c.photoSimilarity*100)' in main,
    'manual Vinted always reachable': 'openVintedRecoveryForDeal' in main and 'latestJobForLegacySignature' in market,
    'manual BGG observed photo': 'Foto osservata su Vinted' in main and 'Link BGG, ID o titolo' in main,
    'manual BGG direct repair': 'assignManualBggMatch' in main and 'db.correctMatch(legacy,candidate)' in main,
    'legacy correction syncs canonical': 'marketStore.syncLegacyCorrection(fresh)' in main,
    'review button direct': 'fix.setOnClickListener(v->chooseManualBggMatch(game))' in main,
    'provider logo clipped': 'wrap.setClipToOutline(true)' in main and 'ImageView.ScaleType.CENTER_CROP' in main,
    'product detail missing providers actionable': 'hasVinted?"Apri annuncio":"Collega annuncio"' in main and 'hasBgg?"Scheda gioco":"Collega gioco"' in main,
    'manual reviews separate from required queue': "match_state='BGG_MATCH_REVIEW'" in market and "match_state='BGG_MATCH_REQUIRED'" in market,
    'no schema bump': 'DB_VERSION=17' in (java/'DealDatabase.java').read_text().replace(' ',''),
    'secrets exact name': (root/'secrets.properties').exists() and not (root/'secrets.properties.example').exists(),
}
failed=[k for k,v in checks.items() if not v]
if failed:
    print('FAIL:', ', '.join(failed)); raise SystemExit(1)
print('PASS: observed-photo Vinted recovery, seller hints, manual BGG repair, provider clipping, and cross-store correction are wired.')
