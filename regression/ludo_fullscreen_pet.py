"""Room assets and containment contract, complemented by actual Android view tests."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
main=(root/'app/src/main/java/it/vintedaffari/app/MainActivity.java').read_text()
frame=(root/'app/src/main/java/it/vintedaffari/app/LudoRoomFrame.java').read_text()
backdrop=(root/'app/src/main/java/it/vintedaffari/app/LudoRoomBackdropView.java').read_text()
assert 'mainScrollStage.getHeight()-ludoRoomDots.getHeight()-dp(152)' not in main, 'Old scene still reserves a dashboard below it'
assert 'setControlsInset' in frame and 'ludoRoomPanelOpen' in main, 'Room controls/panel missing'
assert 'void setBitmap(Bitmap ignored)' not in backdrop, 'Approved background is ignored'
for room,width in [('engine',895),('hunts',916),('library',857)]:
 p=root/f'app/src/main/res/drawable-nodpi/ludo_room_{room}_background.webp'
 data=p.read_bytes()
 assert data[:4]==b'RIFF' and data[8:12]==b'WEBP', 'WebP room required'
 if data[12:16]==b'VP8X':
  w=1+int.from_bytes(data[24:27],'little');h=1+int.from_bytes(data[27:30],'little')
 else:
  assert data[12:16]==b'VP8L' and data[20]==0x2f, 'Lossless room required'
  bits=int.from_bytes(data[21:25],'little');w=(bits&0x3fff)+1;h=((bits>>14)&0x3fff)+1
 assert (w,h)==(width,1536), 'Original approved portrait dimensions must remain intact'
print('PASS fullscreen room integration contract; actual pixels/geometry/lifecycle covered by Android instrumentation')
