"""Room assets and containment contract, complemented by actual Android view tests."""
from pathlib import Path
from PIL import Image
root=Path(__file__).resolve().parents[1]
main=(root/'app/src/main/java/it/vintedaffari/app/MainActivity.java').read_text()
frame=(root/'app/src/main/java/it/vintedaffari/app/LudoRoomFrame.java').read_text()
backdrop=(root/'app/src/main/java/it/vintedaffari/app/LudoRoomBackdropView.java').read_text()
assert 'mainScrollStage.getHeight()-ludoRoomDots.getHeight()-dp(152)' not in main, 'Old scene still reserves a dashboard below it'
assert 'setControlsInset' in frame and 'ludoRoomPanelOpen' in main, 'Room controls/panel missing'
assert 'void setBitmap(Bitmap ignored)' not in backdrop, 'Approved background is ignored'
for room in ['engine','hunts','library']:
 p=root/f'app/src/main/res/drawable-nodpi/ludo_room_{room}_background.webp'
 im=Image.open(p)
 assert im.height>im.width and im.height/im.width>1.5, 'Full portrait room required'
 assert im.getpixel((im.width//2,int(im.height*.85)))[:3] != (8,10,20), 'Old dark panel background retained'
print('PASS fullscreen room integration contract; actual geometry/lifecycle covered by Android instrumentation')
