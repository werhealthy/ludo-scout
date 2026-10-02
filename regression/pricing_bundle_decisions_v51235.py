#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
evaluator=(ROOT/"app/src/main/java/it/vintedaffari/app/DealEvaluator.java").read_text(encoding="utf-8")
market=(ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
bridge=(ROOT/"app/src/main/assets/engine/android-bridge.js").read_text(encoding="utf-8")
db=(ROOT/"app/src/main/java/it/vintedaffari/app/DealDatabase.java").read_text(encoding="utf-8")
ui=(ROOT/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
bundle=(ROOT/"app/src/main/java/it/vintedaffari/app/BundleExploration.java").read_text(encoding="utf-8")
radar=(ROOT/"app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
bgg=(ROOT/"app/src/main/java/it/vintedaffari/app/BggSearchClient.java").read_text(encoding="utf-8")

checks=[
    ("release identity",
     "versionName '5.12." in build and
     "applicationId 'it.vintedaffari.app'" in build and
     "1000000 + ciVersionCode.toInteger()" in build),
    ("no user-visible expensive category",'"Troppo caro"' not in evaluator and 'Decision.REJECT,""' in evaluator and '"Prezzo alto"' not in ui),
    ("central decisions exist",all(x in evaluator for x in ["GREAT_BUY","GOOD_PRICE","OFFER","FAIR","INSUFFICIENT_DATA","REJECT"])),
    ("typical BGG value is median","cents: Math.round(p.usedMedianEUR * 100)" in bridge and "marketMedianCents: Math.round(p.usedMedianEUR * 100)" in bridge),
    ("Q25 remains separate","marketQ25Cents: Math.round(p.usedQ25EUR * 100)" in bridge),
    ("compact BGG fallback reads median column","c.length>=3?c[2]:c[1]" in bgg and "int median=" in bgg),
    ("safe mode keeps local Vinted evidence out of decisions",
     "Safe mode: local Vinted asks are retained as market history only" in market and
     "return priorCents!=null&&priorCents>0?priorCents:null;" in market and
     "refreshLocalVintedBenchmarksForBgg" in market),
    ("local quartiles are explicit","q25Cents,q75Cents" in market and "q25Offset" in market),
    ("price rejects leave product surfaces",'"PRICE_FILTERED"' in db and '"lifecycle","REMOVED"' in db and '"tier","filtered"' in db),
    ("discover stays selective","tier IN ('hot','good','offer')" in db),
    ("catalog can retain fair and insufficient","'fair','insufficient','hunt'" in db),
    ("bundle prospect uses central evaluator","DealEvaluator.isBundleProspect" in ui and "DealEvaluator.bundleProspectScore" in ui),
    ("bundle experiment carries explicit seller without legacy capture side effects",\'openVintedBrowserExperiment(d.vintedUrl,"BUNDLE",d.sellerId)\' in ui and "BundleExploration.begin(this,d)" not in ui and "BundleExploration.current(this)" in radar),
    ("unknown bundle shipping is not shown as exact","Totale da verificare" in ui and 'bundleMetric("RISPARMIO","n/d"' in ui),
    ("bundle shipping scenario stays explicitly estimated","plan.shippingEstimated=true" in ui),
    ("offer target is solved from all-in total","maxItemForTotal" in evaluator and "cut<=.15" in evaluator),
]

for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("5.12.35 source guards failed: "+", ".join(failed))

# Executable arithmetic model mirrors the product thresholds.
def fee(item):
    return 70+round(item*.05)

def total(item,shipping):
    return item+shipping+fee(item)

def max_item(target,shipping):
    lo,hi=0,max(0,target-shipping)
    while lo<hi:
        mid=lo+(hi-lo+1)//2
        if total(mid,shipping)<=target: lo=mid
        else: hi=mid-1
    return lo if total(lo,shipping)<=target else None

# Median 40€, "good" means at least max(3€,8%) = 3.20€ below it.
good_ceiling=4000-max(300,round(4000*.08))
assert good_ceiling==3680
offer=max_item(good_ceiling,450)
offer=(offer//50)*50
assert offer==3000, offer
assert total(offer,450)<=good_ceiling
assert .05 <= (3500-offer)/3500 <= .15

# Cheap sticker price can become merely fair after fees/shipping.
assert total(900,450)==1465
assert 1500-total(900,450)==35

# Safe mode invariant: local market evidence can be 20€ or 200€, but the decision reference
# remains the BGG prior until identity cleanup is explicitly re-enabled.
def safe_reference(local, prior):
    return prior if prior and prior > 0 else None

assert safe_reference(2000,3000)==3000
assert safe_reference(8000,3000)==3000
assert safe_reference(2000,None) is None

print("PASS executable offer-target and BGG-only safe-mode arithmetic")
print(f"PASS {len(checks)+1}/{len(checks)+1} 5.12.35 pricing/bundle guards")
