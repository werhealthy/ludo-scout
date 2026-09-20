package org.json;
import com.google.gson.*;
public class JSONArray {final JsonArray value;JSONArray(JsonArray v){value=v;}public int length(){return value.size();}public Object opt(int i){return i<0||i>=value.size()?null:JSONObject.wrap(value.get(i));}public String optString(int i){Object v=opt(i);return v==null?"":v.toString();}}
