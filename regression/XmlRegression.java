import it.vintedaffari.app.SafeXml;import java.io.*;import java.nio.charset.StandardCharsets;
public class XmlRegression {
 static ByteArrayInputStream in(String s){return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));}
 public static void main(String[] args)throws Exception{
 String xml="<?xml version='1.0' encoding='UTF-8'?><items><item id='217372'><name value='The Quest for El Dorado'/><image>https://example.test/cover.jpg</image></item></items>";
 if(SafeXml.parse(in(xml)).getElementsByTagName("item").getLength()!=1)throw new AssertionError("BGG normal response");
 for(String bad:new String[]{"<!DOCTYPE items SYSTEM 'https://example.test/evil'><items/>","<!DOCTYPE items [<!ENTITY e 'x'>]><items>&e;</items>","<items>\u0000</items>"}){boolean rejected=false;try{SafeXml.parse(in(bad));}catch(Exception e){rejected=true;}if(!rejected)throw new AssertionError("Unsafe XML accepted");}
 if(SafeXml.parse(in("\ufeff"+xml)).getElementsByTagName("image").getLength()!=1)throw new AssertionError("BOM");
 System.out.println("PASS: 5 portable XML checks; no implementation-specific feature flags.");
 }
}
