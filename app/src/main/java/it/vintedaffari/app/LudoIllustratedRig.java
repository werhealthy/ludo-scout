package it.vintedaffari.app;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.util.*;
/** One immutable, complete raster rig per Activity, decoded only on its artwork executor. */
final class LudoIllustratedRig {
 final List<LudoPart> parts;final Map<String,Bitmap> images;
 private LudoIllustratedRig(List<LudoPart> parts,Map<String,Bitmap> images){this.parts=Collections.unmodifiableList(parts);this.images=Collections.unmodifiableMap(images);}
 static LudoIllustratedRig load(Resources resources){
  List<LudoPart> parts=new ArrayList<>();Map<String,Bitmap> images=new HashMap<>();
  BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
  try {
   parts.add(new LudoPart("cape","body",0,255f,650f,530f,350f,265f,35f));
   images.put("cape",BitmapFactory.decodeResource(resources,R.drawable.ludo_part_cape,options));
   parts.add(new LudoPart("body",null,1,290f,690f,420f,330f,210f,330f));
   images.put("body",BitmapFactory.decodeResource(resources,R.drawable.ludo_part_body,options));
   parts.add(new LudoPart("hat","head",2,160f,0f,670f,579f,350f,500f));
   images.put("hat",BitmapFactory.decodeResource(resources,R.drawable.ludo_part_hat,options));
   parts.add(new LudoPart("ear_left","head",3,105f,490f,120f,103f,110f,55f));
   images.put("ear_left",BitmapFactory.decodeResource(resources,R.drawable.ludo_part_ear_left,options));
   parts.add(new LudoPart("ear_right","head",3,785f,510f,130f,96f,10f,40f));
   images.put("ear_right",BitmapFactory.decodeResource(resources,R.drawable.ludo_part_ear_right,options));
   parts.add(new LudoPart("head","body",4,185f,365f,630f,426f,315f,365f));
   images.put("head",BitmapFactory.decodeResource(resources,R.drawable.ludo_part_head,options));
   parts.add(new LudoPart("eye_left","head",5,258f,425f,190f,208f,95f,104f));
   images.put("eye_left",BitmapFactory.decodeResource(resources,R.drawable.ludo_part_eye_left,options));
   parts.add(new LudoPart("eye_right","head",5,514f,447f,190f,215f,95f,107f));
   images.put("eye_right",BitmapFactory.decodeResource(resources,R.drawable.ludo_part_eye_right,options));
   parts.add(new LudoPart("spiral_left","eye_left",6,297f,471f,112f,120f,56f,60f));
   images.put("spiral_left",BitmapFactory.decodeResource(resources,R.drawable.ludo_part_spiral_left,options));
   parts.add(new LudoPart("spiral_right","eye_right",6,553f,494f,112f,116f,56f,58f));
   images.put("spiral_right",BitmapFactory.decodeResource(resources,R.drawable.ludo_part_spiral_right,options));
   parts.add(new LudoPart("mouth","head",7,416f,674f,158f,66f,79f,33f));
   images.put("mouth",BitmapFactory.decodeResource(resources,R.drawable.ludo_part_mouth,options));
   parts.add(new LudoPart("arm_left","body",8,260f,744f,205f,162f,185f,100f));
   images.put("arm_left",BitmapFactory.decodeResource(resources,R.drawable.ludo_part_arm_left,options));
   parts.add(new LudoPart("arm_right","body",8,555f,744f,205f,160f,20f,100f));
   images.put("arm_right",BitmapFactory.decodeResource(resources,R.drawable.ludo_part_arm_right,options));
   parts.add(new LudoPart("dice","body",9,450f,835f,100f,99f,50f,50f));
   images.put("dice",BitmapFactory.decodeResource(resources,R.drawable.ludo_part_dice,options));
   images.put("mouth_surprised",BitmapFactory.decodeResource(resources,R.drawable.ludo_part_mouth_surprised,options));
   for(Bitmap bitmap:images.values())if(bitmap==null)throw new IllegalStateException("Missing rig image");
   LudoPart.validateAndOrder(parts);return new LudoIllustratedRig(parts,images);
  }catch(RuntimeException|OutOfMemoryError failure){for(Bitmap bitmap:images.values())if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();return null;}
 }
}
