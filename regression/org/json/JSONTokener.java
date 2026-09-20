package org.json;
import com.google.gson.*;
public class JSONTokener {private final String text;public JSONTokener(String s){text=s;}public Object nextValue(){return JSONObject.wrap(JsonParser.parseString(text));}}
