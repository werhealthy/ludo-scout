package it.vintedaffari.app;
import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import org.json.JSONObject;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;
/** Encrypted settings and pending request, explicitly excluded from Android backup. */
public final class AiBetaSettings {
 private static final String ALIAS="ludo_ai_beta_private_v1";
 private final AtomicFile file;
 private final int limit;
 public AiBetaSettings(Context context){this(context,"ai-beta.private",200000);}
 AiBetaSettings(Context context,String name,int limit){this.limit=limit;file=new AtomicFile(new File(context.getNoBackupFilesDir(),name));}
 private SecretKey key() throws Exception {KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return g.generateKey();}
 public synchronized JSONObject load() throws Exception {if(!file.getBaseFile().exists())return new JSONObject();byte[] bytes=file.readFully();if(bytes.length<29||bytes.length>limit)throw new Exception("private settings unreadable");int n=bytes[0]&255;if(n!=12)throw new Exception("private settings unreadable");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Arrays.copyOfRange(bytes,1,n+1)));return new JSONObject(new String(c.doFinal(Arrays.copyOfRange(bytes,n+1,bytes.length)),StandardCharsets.UTF_8));}
 public synchronized void save(JSONObject value) throws Exception {byte[] clear=value.toString().getBytes(StandardCharsets.UTF_8);if(clear.length+29>limit)throw new Exception("private settings too large");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());byte[] iv=c.getIV(),encrypted=c.doFinal(clear);FileOutputStream out=null;try{out=file.startWrite();out.write(iv.length);out.write(iv);out.write(encrypted);file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}}
}
