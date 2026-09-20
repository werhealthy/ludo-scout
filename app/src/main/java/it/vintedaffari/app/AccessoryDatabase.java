package it.vintedaffari.app;
import android.content.*;import android.database.*;import android.database.sqlite.*;import java.util.*;
public final class AccessoryDatabase extends SQLiteOpenHelper{
 public static final class Item{public long id;public String parentBggId,title,url,imageUrl;public int priceCents;}
 public AccessoryDatabase(Context c){super(c,"vinted_affari_accessories.db",null,1);}public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE accessories(id INTEGER PRIMARY KEY AUTOINCREMENT,parent_bgg_id TEXT,title TEXT,url TEXT,image_url TEXT,price_cents INTEGER,added_at INTEGER,UNIQUE(parent_bgg_id,url))");}public void onUpgrade(SQLiteDatabase db,int o,int n){}
 public synchronized void save(String bgg,String title,String url,String image,int price){if(bgg==null||bgg.isEmpty())return;ContentValues v=new ContentValues();v.put("parent_bgg_id",bgg);v.put("title",title);v.put("url",url);v.put("image_url",image);v.put("price_cents",price);v.put("added_at",System.currentTimeMillis());getWritableDatabase().insertWithOnConflict("accessories",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
 public synchronized List<Item> forGame(String bgg){List<Item> out=new ArrayList<>();Cursor c=getReadableDatabase().rawQuery("SELECT id,parent_bgg_id,title,url,image_url,price_cents FROM accessories WHERE parent_bgg_id=? ORDER BY added_at DESC",new String[]{bgg});while(c.moveToNext()){Item i=new Item();i.id=c.getLong(0);i.parentBggId=c.getString(1);i.title=c.getString(2);i.url=c.getString(3);i.imageUrl=c.getString(4);i.priceCents=c.getInt(5);out.add(i);}c.close();return out;}
}
