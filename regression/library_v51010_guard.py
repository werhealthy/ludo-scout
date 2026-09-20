from pathlib import Path
import json,re
root=Path(__file__).resolve().parents[1]
main=(root/'app/src/main/java/it/vintedaffari/app/MainActivity.java').read_text()
bgg=(root/'app/src/main/java/it/vintedaffari/app/BggSearchClient.java').read_text()
db=(root/'app/src/main/java/it/vintedaffari/app/LibraryDatabase.java').read_text()
brain=(root/'app/src/main/java/it/vintedaffari/app/LocalScoutBrain.java').read_text()
checks={
 'library backfill runs from library screen':'backfillLibraryBgg(allGames)' in main,
 'BGG backfill batches up to 20':'detailsMany(batch' in main and 'count>=20' in bgg,
 'market reference comes from local catalog':'localMarketReferenceCents' in bgg and 'selectedPriceReference' in bgg,
 'direct BGG link is first class':'Link BGG oppure ID BGG' in main and 'Collega da BGG' in main,
 'bundle wizard adds another product':'＋ Aggiungi un altro gioco' in main and 'addWizardBundleGame' in main,
 'bundle price split uses market weights':'allocateLibraryBundleAmount' in main and 'marketReferenceForBgg' in main,
 'bundle title can be thematic':'Bundle "+bundleThemeLabel' in main,
 'bundle title fallback uses lead game':' + altri ' in main,
 'technical same-seller copy removed':'giochi dello stesso venditore' not in main.lower(),
 'gift suppresses economic judgment':'"Regalo".equalsIgnoreCase(g.source)' in main,
 'sold is kept as history':'markSold' in db and 'soldReason' in (root/'app/src/main/java/it/vintedaffari/app/LibraryGame.java').read_text(),
 'sold no longer counts as owned':'!"sold".equals(g.collectionState)' in brain and '!"sold".equals(game.collectionState)' in main,
 'permanent delete exists':'deleteGame' in db and 'Elimina definitivamente' in main,
 'library db migration is additive':'VER=7' in db and 'bundle_group_id' in db and 'collection_state' in db,
}
for name,ok in checks.items():
 if not ok: raise SystemExit('FAIL: '+name)
# Verify at least one known catalog market reference remains readable from the bundled asset.
s=(root/'app/src/main/assets/engine/catalog-data.js').read_text()
o=json.loads(s[s.index('{'):s.rindex('}')+1])
refs=[g for g in o['games'] if g.get('selectedPriceReference',{} ) and g['selectedPriceReference'].get('valueEUR')]
assert refs and refs[0]['selectedPriceReference']['valueEUR']>0
print(f'PASS: {len(checks)} v5.10.10 library/bundle guards; catalog has {len(refs)} priced BGG records')
