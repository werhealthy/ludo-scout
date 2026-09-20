package it.vintedaffari.app;
import java.util.*;
/** Extract balanced JSON structures and JSON string values without evaluating JavaScript. */
public final class EmbeddedJson {
 public static List<String> structures(String text){List<String> out=new ArrayList<>();if(text==null)return out;int start=-1,depth=0;boolean string=false,escape=false;for(int i=0;i<text.length();i++){char c=text.charAt(i);if(string){if(escape){escape=false;continue;}if(c=='\\'){escape=true;continue;}if(c=='"')string=false;continue;}if(c=='"'){string=true;continue;}if(c=='{'||c=='['){if(depth++==0)start=i;}else if((c=='}'||c==']')&&depth>0&&--depth==0){out.add(text.substring(start,i+1));if(out.size()>=10000)break;}}return out;}
 public static List<String> strings(String text){List<String> out=new ArrayList<>();boolean in=false,escape=false;int start=0;for(int i=0;i<text.length();i++){char c=text.charAt(i);if(!in){if(c=='"'){in=true;start=i;}}else if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='"'){out.add(text.substring(start,i+1));in=false;}}return out;}
}
