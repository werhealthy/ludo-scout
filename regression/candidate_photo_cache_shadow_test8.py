from pathlib import Path
root=Path(__file__).resolve().parents[1]
shadow=(root/'app/src/main/java/it/vintedaffari/app/VintedCandidateSnapshotShadow.java').read_text()
cache=(root/'app/src/main/java/it/vintedaffari/app/VintedPhotoHashCache.java').read_text()
matcher=(root/'app/src/main/java/it/vintedaffari/app/VintedPhotoMatcher.java').read_text()
visual=(root/'app/src/main/java/it/vintedaffari/app/VisualCoverMatcher.java').read_text()
assert 'candidate-photo-cache-shadow-v5' in shadow
assert 'photoDecisiveObs' in shadow and 'photoFullCoverageObs' in shadow
assert 'candidatesWithImage' in shadow and 'cachedPhotoHashes' in shadow
for forbidden in ['HttpURLConnection','new URL(','getPublic(']:
    assert forbidden not in shadow, f'shadow must be zero network: {forbidden}'
    assert forbidden not in cache, f'hash cache must be zero network: {forbidden}'
assert 'VintedPhotoHashCache.record(context,candidateImageUrl,b)' in matcher
assert 'queryHashes64' in visual and 'dHash64' in visual
print('PASS test8 photo cache shadow')
