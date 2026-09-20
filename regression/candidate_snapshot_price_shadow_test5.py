from pathlib import Path
import re

src=Path('app/src/main/java/it/vintedaffari/app/VintedCandidateSnapshotShadow.java').read_text()
resolver=Path('app/src/main/java/it/vintedaffari/app/VintedLinkResolver.java').read_text()

def ok(name, cond):
    if not cond:
        raise SystemExit('FAIL '+name)
    print('PASS '+name)

ok('build_v2', 'candidate-snapshot-price-shadow-v2' in src)
ok('zero_network_class', 'getPublic(' not in src and 'HttpURLConnection' not in src and 'openConnection(' not in src)
ok('captures_only_existing_response', 'VintedCandidateSnapshotShadow.capture(context,query,shadowScan,body)' in resolver)
ok('same_candidate_window', 'm.start()-1800' in src and 'm.end()+3000' in src)
ok('price_hint_extraction', 'PRICE_DECIMAL' in src and 'collectPriceHints(near,c.priceHints)' in src)
ok('compact_payload', 'o.put("ph",ph)' in src and 'payload' in src)
ok('offline_price_replay', 'bestPriceDiff(c,observedPrice)' in src and 'priceCompatible(x,price)' in src)
ok('diagnostic_hint_counts', 'snapshotsWithPriceHints' in src and 'candidatesWithPriceHints' in src and 'priceHintValues' in src)
ok('no_real_link_mutation', 'UPDATE market_listings' not in src and 'INSERT INTO market_listings' not in src and 'applyResolvedLink' not in src)
# Mirror the intended decimal clue behavior in a tiny fixture.
pat=re.compile(r'(?<![0-9])([0-9]{1,5})[.,]([0-9]{2})(?![0-9])')
vals={(int(a)*100+int(b)) for a,b in pat.findall('x 12.00 y 15,50 z id10012002716')}
ok('price_fixture', vals=={1200,1550})
