package it.vintedaffari.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.TextView;

/** One icon language for Ludo Scout, backed by Font Awesome Free 6. */
final class LudoIcons {
    private LudoIcons(){}

    static final String HOUSE="\uf015";
    static final String SEARCH="\uf002";
    static final String BOOK_OPEN="\uf518";
    static final String USER="\uf007";
    static final String HEART="\uf004";
    static final String INFO="\uf05a";
    static final String ELLIPSIS_VERTICAL="\uf142";
    static final String ARROW_LEFT="\uf060";
    static final String CHEVRON_LEFT="\uf053";
    static final String CHEVRON_RIGHT="\uf054";
    static final String FILTER="\uf0b0";
    static final String SLIDERS="\uf1de";
    static final String CAMERA="\uf030";
    static final String IMAGE="\uf03e";
    static final String TRASH="\uf1f8";
    static final String GIFT="\uf06b";
    static final String CHECK="\uf00c";
    static final String XMARK="\uf00d";
    static final String PLUS="\uf067";
    static final String PEN="\uf304";
    static final String BOLT="\uf0e7";
    static final String CLOCK="\uf017";
    static final String HISTORY="\uf1da";
    static final String STAR="\uf005";
    static final String TAG="\uf02b";
    static final String SORT="\uf0dc";
    static final String DATABASE="\uf1c0";
    static final String LIST="\uf03a";
    static final String GEAR="\uf013";
    static final String COMPASS="\uf14e";
    static final String ARROW_DOWN="\uf063";
    static final String TRIANGLE_EXCLAMATION="\uf071";
    static final String CIRCLE_CHECK="\uf058";
    static final String LINK="\uf0c1";
    static final String SHARE="\uf1e0";
    static final String BOOKMARK="\uf02e";
    static final String EYE_SLASH="\uf070";
    static final String GAMEPAD="\uf11b";
    static final String STORE="\uf54e";

    private static Typeface solid;
    private static Typeface regular;

    static Typeface solid(Context context){
        if(solid==null)solid=Typeface.createFromAsset(context.getApplicationContext().getAssets(),"fonts/fa-solid-900.ttf");
        return solid;
    }

    static Typeface regular(Context context){
        if(regular==null)regular=Typeface.createFromAsset(context.getApplicationContext().getAssets(),"fonts/fa-regular-400.ttf");
        return regular;
    }

    static TextView view(Context context,String glyph,float sp,int color){
        TextView v=new TextView(context);
        apply(v,glyph,sp,color,false);
        return v;
    }

    static TextView regularView(Context context,String glyph,float sp,int color){
        TextView v=new TextView(context);
        apply(v,glyph,sp,color,true);
        return v;
    }

    static void apply(TextView view,String glyph,float sp,int color,boolean useRegular){
        view.setText(glyph);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(useRegular?regular(view.getContext()):solid(view.getContext()));
        view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
    }

    static Drawable drawable(Context context,String glyph,int color,int sizePx){
        return new FontDrawable(context,glyph,color,sizePx,false);
    }

    static Drawable regularDrawable(Context context,String glyph,int color,int sizePx){
        return new FontDrawable(context,glyph,color,sizePx,true);
    }

    private static final class FontDrawable extends Drawable{
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
        private final String glyph;
        private final int size;
        FontDrawable(Context context,String g,int color,int px,boolean useRegular){
            glyph=g;size=Math.max(1,px);paint.setColor(color);paint.setTextSize(size*.78f);
            paint.setTypeface(useRegular?regular(context):solid(context));paint.setTextAlign(Paint.Align.CENTER);
        }
        @Override public void draw(Canvas canvas){
            android.graphics.Rect b=getBounds();Paint.FontMetrics fm=paint.getFontMetrics();
            float y=b.exactCenterY()-(fm.ascent+fm.descent)/2f;
            canvas.drawText(glyph,b.exactCenterX(),y,paint);
        }
        @Override public int getIntrinsicWidth(){return size;}
        @Override public int getIntrinsicHeight(){return size;}
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
