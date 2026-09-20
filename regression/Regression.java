import it.vintedaffari.app.*;import com.google.gson.Gson;
public class Regression {
 static int cases;static void check(boolean ok,String description){cases++;if(!ok)throw new AssertionError(description);}
 static String script(String body){return "<script>"+body+"</script>";}
 public static void main(String[] args)throws Exception{
 check(PurchaseMath.vintedFee(800)==110,"Fee 8 EUR");check(PurchaseMath.vintedFee(150)==78,"Fee rounding half cent");check(PurchaseMath.vintedFee(0)==70,"Fixed fee");check(PurchaseMath.total(800,300,110)==1210,"No double counting");check(PurchaseMath.total(800,null,110)==null,"Unknown shipping remains unknown");boolean invalid=false;try{PurchaseMath.vintedFee(-1);}catch(IllegalArgumentException e){invalid=true;}check(invalid,"Reject negative prices");
 check(GalleryPosition.afterDrag(0,90,400,4)==1,"Slow drag advances");check(GalleryPosition.afterDrag(2,-90,400,4)==1,"Drag back");check(GalleryPosition.afterDrag(0,-90,400,4)==0,"First boundary");check(GalleryPosition.afterDrag(3,90,400,4)==3,"Last boundary");check(GalleryPosition.afterDrag(1,8,400,4)==1,"Tap does not change page");check(GalleryPosition.clamp(9,1)==0,"Single image");
 String own="{\"id\":101,\"title\":\"Game with } and \\\"quotes\\\"\",\"price\":{\"amount\":\"8.00\"},\"user_id\":7}";
 String foreign="{\"id\":202,\"title\":\"Another game\",\"price\":\"19.00\",\"user_id\":8}";
 String data="{\"items\":["+own+","+foreign+"]}";
 check(VintedStructuredData.scan(script(data),"7").items.size()==1,"Only requested seller");
 check(VintedStructuredData.scan(script(data),"9").items.isEmpty(),"Reject other seller");
 String embedded="self.__next_f.push([1,"+new Gson().toJson("1:"+data+"\n")+"]);";
 check(VintedStructuredData.scan(script(embedded),"7").items.size()==1,"Escaped React Flight JSON");
 String flight="1:"+data+"\n";int split=flight.length()/2;
 String fragments=script("self.__next_f.push([1,"+new Gson().toJson(flight.substring(0,split))+"])")+script("self.__next_f.push([1,"+new Gson().toJson(flight.substring(split))+"])");
 check(VintedStructuredData.scan(fragments,"7").items.size()==1,"Reassemble split Flight chunks");
 String noOwner="{\"id\":303,\"title\":\"Base\",\"price\":\"3.00\"}";
 check(VintedStructuredData.scan(script("{\"user\":{\"id\":7},\"items\":["+noOwner+"]}"),"7").items.size()==1,"Catalog ownership scope");
 check(VintedStructuredData.scan(script("{\"user\":{\"id\":7},\"recommendations\":["+noOwner+"]}"),"7").items.isEmpty(),"Recommendations are not the seller catalog");
 check(VintedStructuredData.scan(script("{\"items\":["+noOwner+"]}"),"7").items.isEmpty(),"Unknown ownership rejected");
 check(VintedStructuredData.scan(script(data)+script(data),"7").items.size()==1,"Deduplicate item IDs");
 check(VintedStructuredData.item(script(data),"202").optString("title").equals("Another game"),"Exact item metadata");
 check(VintedStructuredData.item(script(data),"999")==null,"Never substitute another item");
 check(VintedStructuredData.scan("<a href='/items/123'>Game</a><span>9.00</span>","7").items.isEmpty(),"No price proximity inference");
 check(EmbeddedJson.structures("1:"+data).size()==1,"Quotes and braces balanced");
 check(QualityComposite.score(1,8.4,9.0,200000)==100,"Quality upper anchor");check(QualityComposite.score(null,8.4,9.0,200000)==69,"No rank cap");
 if(args.length>0)for(String line:java.nio.file.Files.readAllLines(java.nio.file.Path.of(args[0]))){String[] v=line.split("\t");Integer rank="null".equals(v[0])?null:Integer.valueOf(v[0]);check(QualityComposite.score(rank,Double.valueOf(v[1]),Double.valueOf(v[2]),Integer.valueOf(v[3]))==Integer.parseInt(v[4]),"Composite parity with original JS: "+line);}
 check(VintedStructuredData.scan(script("{\"userId\":7,\"catalogItems\":["+noOwner+"]}"),"7").items.size()==1,"CamelCase seller scope");
 check(VintedStructuredData.scan(script("{\"userId\":7,\"items\":[{\"id\":500,\"title\":\"Other seller\",\"price\":\"5\",\"sellerId\":8}]}"),"7").items.isEmpty(),"Explicit other seller overrides parent");
 System.out.println("PASS: "+cases+" parser, paging, fee and quality regression checks. Test JSON adapter uses Gson; Android UI/network are not executed.");
 }
}
