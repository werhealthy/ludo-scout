package it.vintedaffari.app;
import android.graphics.*;import android.graphics.drawable.Drawable;
/** Book/text and crossed-text symbols, with accessible descriptions supplied by the view. */
public final class LanguageBadge extends Drawable {
 private final String code;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final int color;
 public LanguageBadge(String c,int color){code=c==null?"":c;this.color=color;}
 public static String shortLabel(String c){if(c!=null&&c.contains("DEP"))return "T";if(c==null||c.isEmpty()||c.contains("?"))return "?";if(c.contains("IND"))return "";return c.replaceAll("[^A-Z]","");}
 public void draw(Canvas canvas){Rect b=getBounds();float scale=b.width()/24f;canvas.save();canvas.translate(b.left,b.top);canvas.scale(scale,scale);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);canvas.drawRoundRect(4,3,20,21,2,2,p);canvas.drawLine(8,8,16,8,p);canvas.drawLine(8,12,16,12,p);canvas.drawLine(8,16,13,16,p);if(code.contains("IND")){p.setStrokeWidth(2.5f);canvas.drawLine(2,22,22,2,p);}canvas.restore();}
 public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
