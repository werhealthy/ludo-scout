"""Decoded alpha, monochrome navigation and persistent-scene boundaries."""
from pathlib import Path
from PIL import Image
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
asset=root/'app/src/main/res/drawable-nodpi/ludo_scribble_character.webp'
assert asset.exists(), 'Approved transparent mascot missing'
im=Image.open(asset).convert('RGBA')
assert im.getextrema()[3]==(0,255), 'Mascot needs actual alpha'
assert all(im.getpixel(p)[3]==0 for p in [(0,0),(im.width-1,0),(0,im.height-1),(im.width-1,im.height-1)]), 'Opaque rectangle around mascot'
for name in ['idle','hello','deal_search','treasure_reward','no_results','sleeping','hunt_explorer','engine_character','hunts_character','library_character']:
 p=root/f'app/src/main/res/drawable/ludo_{name}.xml'
 assert p.exists(), f'Old paper art is still used: {name}'
 assert '@drawable/ludo_scribble_character' in p.read_text(), name
mark=ET.parse(root/'app/src/main/res/drawable/ludo_mark.xml').getroot()
ns='{http://schemas.android.com/apk/res/android}'
assert mark.tag=='vector' and all(x.get(ns+'fillColor') in (None,'#FFFFFFFF','#00000000') for x in mark), 'Navigation mark is not monochrome'
print('PASS real alpha, transparent corners, paper-state replacement, monochrome mascot mark')
