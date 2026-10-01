from pathlib import Path
root=Path(__file__).resolve().parents[1]
s=(root/'app/src/main/java/it/vintedaffari/app/MainActivity.java').read_text()
h=s.split('private View heroOpportunityCard')[1].split('private View discoverFlatArtwork')[0]
assert 'STRIKE_THRU_TEXT_FLAG' not in h, 'Hero price must have no struck comparison'
assert 'featured_game_pedestal' in s, 'Renderer must support the supplied pedestal'
assert 'setFeaturedArtwork' in h, 'Hero bitmap decode must be asynchronous'
r=s.split('private final class FeaturedBoxView')[1].split('private View discoverBoxArtwork')[0]
assert 'Bitmap.Config.ARGB_8888' in s, 'Transparent pedestal must preserve alpha'
assert 'pedestalBounds' in r and 'canvas.drawBitmap(pedestal' in r, 'Pedestal lives only in artwork'
draw=r.split('protected void onDraw')[1].split('private int sampleEdge')[0]
assert all(x not in draw for x in ['new Paint','new Matrix','new Path','new Bitmap','new LinearGradient']), 'No frame allocations'
print('PASS featured pedestal renderer guards')

import struct,zlib
png=(root/'app/src/main/res/drawable-nodpi/featured_game_pedestal.png').read_bytes()
assert png[:8]==b'\x89PNG\r\n\x1a\n', 'Pedestal must be a real PNG'
w,h,depth,color,_,_,interlace=struct.unpack('>IIBBBBB',png[16:29])
assert depth==8 and color==6 and interlace==0, 'Pedestal must preserve RGBA transparency'
pos=8;compressed=b''
while pos<len(png):
 n=struct.unpack('>I',png[pos:pos+4])[0];kind=png[pos+4:pos+8]
 if kind==b'IDAT':compressed+=png[pos+8:pos+8+n]
 pos+=12+n
row=zlib.decompress(compressed)
assert row[0] in (0,1,2,3,4) and row[4]==0, 'Pedestal top-left pixel must be transparent'
assert 2<w/h<4, 'One wide pedestal asset serves all cover modes'
print('PASS real transparent pedestal PNG')
