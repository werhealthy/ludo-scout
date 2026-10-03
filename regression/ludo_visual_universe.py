from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
allowed={'ludo_idle','ludo_hello','ludo_deal_search','ludo_treasure_reward','ludo_no_results','ludo_sleeping','ludo_room','ludo_tavern_hub','ludo_hunt_explorer','ludo_app_icon'}
allowed.update({'ludo_room_engine_background','ludo_engine_character','ludo_engine_secondary_props','ludo_room_hunts_background','ludo_hunts_character','ludo_hunts_prop','ludo_room_library_background','ludo_library_character'})
res=root/'app/src/main/res'
actual={p.stem for p in res.rglob('*') if p.is_file() and p.stem.startswith('ludo_') and p.suffix in {'.png','.webp'}}
assert actual==allowed, f'Legacy or missing Ludo art: {actual ^ allowed}'
for p in (root/'app/src/main').rglob('*'):
 if p.suffix not in {'.java','.xml'}: continue
 refs=set(re.findall(r'(?:R\.drawable\.|@drawable/)(ludo_\w+)',p.read_text()))
 assert refs<=allowed, f'{p}: stale references {refs-allowed}'
pet=(root/'app/src/main/java/it/vintedaffari/app/LudoPetView.java').read_text()
assert 'drawCircle' not in pet and 'drawPath' not in pet, 'Legacy blob still drawn'
main=(root/'app/src/main/java/it/vintedaffari/app/MainActivity.java').read_text()
journey=main.split('private void renderLudoJourney(){')[1].split('private void')[0]
assert 'setX' not in journey and 'dp(100),dp(100)' not in journey, 'Fixed wheel still overflows container'
print('PASS visual universe: resource references, launcher chain, legacy vector, adaptive journey')
