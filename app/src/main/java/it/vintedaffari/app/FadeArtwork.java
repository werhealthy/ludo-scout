package it.vintedaffari.app;
import android.content.Context;import android.graphics.*;
/** Alpha mask: artwork becomes transparent at its edges, without darkening its center. */
public final class FadeArtwork extends androidx.appcompat.widget.AppCompatImageView {public FadeArtwork(Context c){super(c);setLayerType(LAYER_TYPE_SOFTWARE,null);}@Override protected void onDraw(Canvas c){int layer=c.saveLayer(0,0,getWidth(),getHeight(),null);super.onDraw(c);Paint p=new Paint();p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));p.setShader(new LinearGradient(0,0,0,getHeight(),new int[]{0x00ffffff,0xffffffff,0xffffffff,0x00ffffff},new float[]{0,.25f,.78f,1},Shader.TileMode.CLAMP));c.drawRect(0,0,getWidth(),getHeight(),p);c.restoreToCount(layer);}}

