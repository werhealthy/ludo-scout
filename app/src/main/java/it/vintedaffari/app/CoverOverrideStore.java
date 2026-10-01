package it.vintedaffari.app;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** User artwork only. The canonical BGG cache and listing photographs are never overwritten. */
final class CoverOverrideStore {
 static SharedPreferences prefs(Context c){return c.getSharedPreferences("ludo_cover_override_v1",Context.MODE_PRIVATE);}
 static String key(String id){String normalized=GamePreferenceState.gameId(id);return normalized==null?null:"bgg:"+normalized;}
 static File directory(Context c)throws IOException{File dir=new File(c.getFilesDir(),"cover_overrides");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Impossibile salvare la copertina");return dir;}
 static File current(Context c,String id){String key=key(id);if(key==null)return null;String name=prefs(c).getString(key,"");if(!name.matches("cover-[a-zA-Z0-9_-]+\\.png"))return null;File f=new File(new File(c.getFilesDir(),"cover_overrides"),name);return f.isFile()&&f.length()>0?f:null;}
 static synchronized boolean save(Context c,String id,File candidate,String source){String key=key(id);if(key==null||candidate==null||!candidate.isFile())return false;File previous=current(c,id);SharedPreferences p=prefs(c);String old=p.getString(key,null),oldSource=p.getString("source:"+key,null);boolean ok=p.edit().putString(key,candidate.getName()).putString("source:"+key,source).commit();if(!ok){rollback(p,key,old,oldSource);return false;}if(previous!=null&&!previous.equals(candidate))previous.delete();return true;}
 static synchronized boolean restore(Context c,String id){String key=key(id);if(key==null)return false;File previous=current(c,id);SharedPreferences p=prefs(c);String old=p.getString(key,null),oldSource=p.getString("source:"+key,null);boolean ok=p.edit().remove(key).remove("source:"+key).commit();if(!ok){rollback(p,key,old,oldSource);return false;}if(previous!=null)previous.delete();return true;}
 static void rollback(SharedPreferences p,String key,String old,String source){SharedPreferences.Editor edit=p.edit();if(old==null)edit.remove(key);else edit.putString(key,old);if(source==null)edit.remove("source:"+key);else edit.putString("source:"+key,source);edit.apply();}
 static String apiImage(JSONObject object){JSONObject images=object.optJSONObject("images");if(images!=null)for(String size:new String[]{"original","large","medium"}){JSONObject image=images.optJSONObject(size);String url=image==null?"":image.optString("url","");if(CoverSource.bggImage(url))return url;}String url=object.optString("imageurl","");return CoverSource.bggImage(url)?url:null;}
 static String resolve(String input,CoverImportRequest request)throws Exception {
  String url=CoverSource.https(input),id=CoverSource.pageId(url);if(id==null){String host=new URI(url).getHost();if(host.equalsIgnoreCase("boardgamegeek.com")||host.equalsIgnoreCase("www.boardgamegeek.com"))throw new IOException("Usa il link di una pagina immagine BGG");return url;}
  String image=null;try{image=CoverSource.metaImage(new String(fetch(url,2*1024*1024,request),StandardCharsets.UTF_8));}catch(IOException ignored){request.check();}
  if(image==null)try{image=apiImage(new JSONObject(new String(fetch("https://api.geekdo.com/api/images/"+id,2*1024*1024,request),StandardCharsets.UTF_8)));}catch(Exception ignored){request.check();}
  if(image==null)throw new IOException("BGG non rende disponibile l'immagine. Prova il link diretto della foto.");return image;
 }
 static byte[] fetch(String url,int maximum,CoverImportRequest request)throws IOException {
  for(int hop=0;hop<5;hop++){
   request.check();url=CoverSource.https(url);URL target=new URL(url);for(InetAddress address:InetAddress.getAllByName(target.getHost()))if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()||address.isMulticastAddress())throw new IOException("Usa un link pubblico");request.check();
   HttpURLConnection connection=(HttpURLConnection)target.openConnection();connection.setConnectTimeout(8000);connection.setReadTimeout(12000);connection.setInstanceFollowRedirects(false);connection.setRequestProperty("User-Agent","Mozilla/5.0 LudoScout Android");
   try{request.register(connection);int code=connection.getResponseCode();if(code>=300&&code<400){String location=connection.getHeaderField("Location");if(location==null)throw new IOException("Redirect non valido");url=new URL(target,location).toString();continue;}if(code!=200)throw new IOException("Immagine non disponibile (HTTP "+code+")");if(connection.getContentLengthLong()>maximum)throw new IOException("Immagine troppo grande");
    try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[16384];int n,total=0;while((n=in.read(buffer))!=-1){request.check();total+=n;if(total>maximum)throw new IOException("Immagine troppo grande");out.write(buffer,0,n);}request.check();return out.toByteArray();}
   }finally{request.release(connection);connection.disconnect();}
  }throw new IOException("Troppi redirect");
 }
 static File prepare(Context c,String input,CoverImportRequest request)throws Exception {
  request.check();byte[] bytes=fetch(resolve(input,request),8*1024*1024,request);request.check();BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);if(bounds.outWidth<=0||bounds.outHeight<=0||(long)bounds.outWidth*bounds.outHeight>100000000L)throw new IOException("Il link non contiene una copertina valida");
  BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>1200)options.inSampleSize*=2;Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(bitmap==null)throw new IOException("Immagine non leggibile");
  File candidate=File.createTempFile("cover-",".png",directory(c));boolean written=false;try{try(FileOutputStream out=new FileOutputStream(candidate)){if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Impossibile salvare l'immagine");}request.check();written=true;return candidate;}finally{bitmap.recycle();if(!written)candidate.delete();}
 }
}
