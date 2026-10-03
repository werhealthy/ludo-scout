#!/usr/bin/env python3
"""Production room assets plus measured orbit geometry (JVM available in CI)."""
from pathlib import Path
import shutil, subprocess, tempfile
root=Path(__file__).resolve().parents[1]
res=root/'app/src/main/res/drawable-nodpi'
names=['room_engine_background','engine_character','engine_secondary_props','room_hunts_background','hunts_character','hunts_prop','room_library_background','library_character']
for i,name in enumerate(names):
 p=res/('ludo_'+name+'.webp')
 assert p.exists(),f'Missing separate room asset: {name}'
 b=p.read_bytes();assert b[:4]==b'RIFF' and b[8:12]==b'WEBP',name
 if i in (1,2,4,5,7):
  assert b[12:16]==b'VP8X' and b[20]&16,f'{name} requires real alpha'
geometry=root/'app/src/main/java/it/vintedaffari/app/LudoOrbitGeometry.java'
assert geometry.exists(),'Measured native orbit geometry missing'
javac=shutil.which('javac')
if javac:
 with tempfile.TemporaryDirectory() as directory:
  d=Path(directory);test=d/'OrbitTest.java'
  test.write_text('''package it.vintedaffari.app;
public class OrbitTest {
 public static void main(String[] args) {
  for(int w:new int[]{280,284,320,324,340,357,390,420})for(int h:new int[]{84,100,130,180}) {
   int[][] boxes=LudoOrbitGeometry.bounds(w,h);int total=LudoOrbitGeometry.height(h);
   for(int[] b:boxes)if(b[0]<0||b[1]<0||b[0]+b[2]>w||b[1]+b[3]>total)throw new AssertionError("bounds");
   for(int i=0;i<boxes.length;i++)for(int j=0;j<i;j++){int[]a=boxes[i],b=boxes[j];if(a[0]<b[0]+b[2]&&b[0]<a[0]+a[2]&&a[1]<b[1]+b[3]&&b[1]<a[1]+a[3])throw new AssertionError("overlap");}
  }
  if(LudoOrbitGeometry.useRows(280,1)||!LudoOrbitGeometry.useRows(279,1)||!LudoOrbitGeometry.useRows(393,2))throw new AssertionError("accessible fallback");
  System.out.println("orbit production geometry: 32 measured sizes, fallback passed");
 }
}''')
  subprocess.run([javac,'-d',str(d),str(geometry),str(test)],check=True)
  subprocess.run(['java','-cp',str(d),'it.vintedaffari.app.OrbitTest'],check=True)
else:print('JVM orbit geometry not run locally: javac unavailable; required by CI')
print('Separate rooms and transparent sprites verified')
