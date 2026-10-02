"""Execute shared price decisions and the actual Library capacity at phone/font sizes."""
from pathlib import Path
import subprocess, tempfile
root=Path(__file__).resolve().parents[1]
src=root/"app/src/main/java/it/vintedaffari/app"
ui=(src/"MainActivity.java").read_text()
start=ui.index("private static int libraryShelfCapacity(")
brace=ui.index("{",start);depth=0
for i in range(brace,len(ui)):
    if ui[i]=="{":depth+=1
    elif ui[i]=="}":
        depth-=1
        if depth==0:capacity=ui[start:i+1];break
java=r'''package it.vintedaffari.app;
public class LudoOffersShelvesRegression {
 __CAPACITY__
 static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
 static DealRecord deal(){DealRecord d=new DealRecord();d.itemPriceCents=1000;d.totalCents=1400;d.benchmarkCents=3000;d.shippingCents=280;d.protectedPriceCents=1120;d.tier="hot";return d;}
 static boolean great(DealRecord d){return DealEvaluator.evaluate(d).decision==DealEvaluator.Decision.GREAT_BUY;}
 public static void main(String[] args){
  check(great(deal()),"known Offertona");
  DealRecord d=deal();d.benchmarkCents=null;check(!great(d),"missing benchmark");
  d=deal();d.totalCents=null;d.protectedPriceCents=null;check(!great(d),"missing total");
  d=deal();d.totalCents=3000;check(!great(d),"no discount");
  d=deal();d.totalCents=4000;check(!great(d),"negative discount");
  d=deal();d.shippingVerifiedCents=2300;check(!great(d),"verified shipping supersedes stale total");
  d=deal();d.tier="good";check(!great(d),"good price is not hot");
  for(int width:new int[]{324,354,394,560,800})check(libraryShelfCapacity(width,1f)==2,"two large boxes at "+width);
  check(libraryShelfCapacity(280,1f)==1,"narrow width");
  check(libraryShelfCapacity(324,1.5f)==1,"large text");
  check(libraryShelfCapacity(800,2f)==2,"wide large text capped at two");
  for(int width=0;width<1200;width+=7)for(float font:new float[]{1f,1.4f,2f}){int n=libraryShelfCapacity(width,font);check(n>=1&&n<=2,"capacity bounds");}
  System.out.println("PASS real price evaluator: missing/zero/negative/shipping/tier; actual shelf capacity at phone, narrow, large-text and tablet widths");
 }
}'''.replace("__CAPACITY__",capacity)
with tempfile.TemporaryDirectory() as tmp:
    p=Path(tmp)
    (p/"LudoOffersShelvesRegression.java").write_text(java)
    (p/"VintedCard.java").write_text("package it.vintedaffari.app;class VintedCard {double itemPrice;}")
    (p/"GameAnalysis.java").write_text("package it.vintedaffari.app;class GameAnalysis {Integer totalCents,benchmarkCents,marketQ25Cents,offerCents,afterOfferCents,shippingCents;boolean marketAllowHot;}")
    subprocess.run(["javac","-d",tmp,*[str(src/n) for n in ["DealEvaluator.java","DealRecord.java","DealPolicy.java","PurchaseMath.java"]],*[str(x) for x in p.glob("*.java")]],check=True)
    subprocess.run(["java","-cp",tmp,"it.vintedaffari.app.LudoOffersShelvesRegression"],check=True)
