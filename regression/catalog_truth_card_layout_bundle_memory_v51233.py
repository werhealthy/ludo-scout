#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
ui=(ROOT/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
db=(ROOT/"app/src/main/java/it/vintedaffari/app/DealDatabase.java").read_text(encoding="utf-8")
market=(ROOT/"app/src/main/java/it/vintedaffari/app/MarketStore.java").read_text(encoding="utf-8")
relative=(ROOT/"app/src/main/java/it/vintedaffari/app/RelativeTime.java").read_text(encoding="utf-8")
explore=(ROOT/"app/src/main/java/it/vintedaffari/app/BundleExploration.java").read_text(encoding="utf-8")

trusted=db[db.index("public synchronized List<DealRecord> getDeals"):db.index("public synchronized List<DealRecord> getDealsByBggId")]
matched=market[market.index("private long upsertMatchedGame"):market.index("private long upsertProvisionalGame")]
hero=ui[ui.index("private View heroDealV51"):ui.index("private View heroBundleV51")]
hero_bundle=ui[ui.index("private View heroBundleV51"):ui.index("private void addDealRail")]
current=explore[explore.index("public static State current"):explore.index("public static boolean wasExplored")]

checks=[
    ("catalog/card/bundle release lineage keeps app identity",
     "applicationId 'it.vintedaffari.app'" in build and "1000000 + ciVersionCode.toInteger()" in build),
    ("public catalog trusts canonical BGG rating, not stale legacy rating",
     "g.database_visible=1" in trusted and "g.rating IS NOT NULL" in trusted and "g.rating>=6.0" in trusted),
    ("all matched-game paths hide known BGG ratings below six",
     "knownLow=a.averageRating!=null&&a.averageRating<DealPolicy.MIN_BGG_RATING" in matched and
     'v.put("database_visible",knownLow?0:1)' in matched and
     '"BGG_RATING_BELOW_6"' in matched),
    ("Vinted publication parser accepts natural Italian relative labels",
     "pubblicato oggi" in relative and "pubblicato ieri" in relative and
     "settimana|settimane|mese|mesi|anno|anni" in relative and
     'n==1?" settimana fa":" settimane fa"' in relative),
    ("short new Vinted labels remain source truth instead of becoming n/d",
     'return s.length()<=32?s:""' in relative),
    ("featured deal title is one line with ellipsis",
     "game.setSingleLine(true)" in hero and "game.setEllipsize(TextUtils.TruncateAt.END)" in hero),
    ("featured cards have enough fixed height for CTA",
     "new LinearLayout.LayoutParams(dp(364),dp(350))" in hero and
     "new LinearLayout.LayoutParams(dp(364),dp(350))" in hero_bundle),
    ("bundle exploration expiry preserves durable explored-seller memory",
     "clear(context);return null;" in current and "p.edit().clear()" not in current),
    ("bundle durable clear removes only transient capture fields",
     '.remove("source_signature").remove("item_id").remove("seller_id")' in explore and
     "EXPLORED_PREFIX" in explore),
]

for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("5.12.33 catalog/card/bundle regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} 5.12.33 catalog/card/bundle guards")
