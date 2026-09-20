package org.json;
import com.google.gson.*;import java.util.*;
/** Test-only adapter: production uses Android org.json. No adapter is shipped in the APK. */
public class JSONObject {
 final JsonObject value;JSONObject(JsonObject v){value=v;}
 public JSONObject(String json){this(JsonParser.parseString(json).getAsJsonObject());}
 static Object wrap(JsonElement v){if(v==null||v.isJsonNull())return null;if(v.isJsonObject())return new JSONObject(v.getAsJsonObject());if(v.isJsonArray())return new JSONArray(v.getAsJsonArray());JsonPrimitive p=v.getAsJsonPrimitive();if(p.isString())return p.getAsString();if(p.isBoolean())return p.getAsBoolean();return p.getAsNumber();}
 public Object opt(String key){return wrap(value.get(key));}public boolean has(String key){return value.has(key);}
 public String optString(String key){return optString(key,"");}public String optString(String key,String fallback){Object v=opt(key);return v==null?fallback:v.toString();}
 public JSONObject optJSONObject(String key){Object v=opt(key);return v instanceof JSONObject?(JSONObject)v:null;}
 public Iterator<String> keys(){return value.keySet().iterator();}
}
