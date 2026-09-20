from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=(root/'app/src/main/java/it/vintedaffari/app/VintedBatchResolverShadow.java').read_text()
assert 'zeroNetwork=true, zeroWrites=true' in p
assert 'HttpURLConnection' not in p and 'new URL(' not in p and 'getPublic(' not in p
assert 'gtPrecisionWhenCommitted' in p
assert 'assignedIds' in p and 'topCandidateCollisionClaims' in p
svc=(root/'app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java').read_text()
assert 'vintedBatchResolverShadow={' in svc
print('PASS test9 batch resolver shadow guards')
