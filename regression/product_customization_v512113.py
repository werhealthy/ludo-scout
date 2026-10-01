from pathlib import Path
import subprocess, tempfile
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/it/vintedaffari/app'
for name in ['FeaturedDismissalSession','CoverSource']:
    assert (src/(name+'.java')).exists(), 'Missing production behavior: '+name
java=r'''package it.vintedaffari.app;
public class ProductCustomizationRegression {
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception {
  FeaturedDismissalSession s=new FeaturedDismissalSession();
  check(!s.excludes("a"),"fresh session excluded an offer");s.dismiss("a","Game");
  check(s.excludes("a")&&!s.excludes("b"),"dismissal must match listing identity only");
  s.dismiss("b","Other");s.undo();check(s.excludes("a")&&!s.excludes("b"),"undo must restore latest offer only");
  s.clear();check(!s.excludes("a"),"app close must reset exclusions");
  s.dismiss("", "Missing");check(!s.excludes(""),"unknown identity cannot exclude unrelated offers");
  check("6526676".equals(CoverSource.pageId("https://boardgamegeek.com/image/6526676/aye-dark-overlord-the-green-box")),"user page URL did not resolve its image ID");
  check(CoverSource.pageId("https://boardgamegeek.com.evil.test/image/6526676/x")==null,"lookalike host treated as BGG");
  check(CoverSource.pageId("https://boardgamegeek.com/boardgame/1/x")==null,"game page treated as image page");
  String want="https://cf.geekdo-images.com/front.jpg?a=1&b=2";
  check(want.equals(CoverSource.metaImage("<meta content='https://cf.geekdo-images.com/front.jpg?a=1&amp;b=2' property='og:image'>")),"metadata order/entities lost");
  check(CoverSource.metaImage("<meta property='og:image' content='https://boardgamegeek.com/logo.png'>")==null,"BGG logo accepted as product cover");
  for(String bad:new String[]{"http://example.com/x","https://localhost/x","https://127.0.0.1/x","https://user:pass@example.com/x","file:///tmp/x"}){
   boolean rejected=false;try{CoverSource.https(bad);}catch(Exception expected){rejected=true;}check(rejected,"unsafe or non-HTTPS input accepted: "+bad);
  }
  check("https://example.com/front.png".equals(CoverSource.https("https://example.com/front.png")),"direct HTTPS image URL rejected");
  System.out.println("PASS contextual dismiss/undo/reset and BGG-page/metadata/direct-URL cases");
 }
}'''
with tempfile.TemporaryDirectory() as temp:
 p=Path(temp)/'ProductCustomizationRegression.java';p.write_text(java)
 subprocess.run(['javac','-d',temp,str(src/'FeaturedDismissalSession.java'),str(src/'CoverSource.java'),str(p)],check=True)
 subprocess.run(['java','-cp',temp,'it.vintedaffari.app.ProductCustomizationRegression'],check=True)
