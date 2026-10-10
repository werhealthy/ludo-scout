#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
app = ROOT / "app/src/main"
ui = (app / "java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
icons = (app / "java/it/vintedaffari/app/LudoIcons.java").read_text(encoding="utf-8")
build = (ROOT / "app/build.gradle").read_text(encoding="utf-8")

def between(source, start, end):
    return source[source.index(start):source.index(end, source.index(start))]

chrome = between(ui, "private void applyDiscoverChrome()", "private void renderDiscover()")
home = between(ui, "private void renderDiscover()", "private List<DealRecord> limitDeals")
header = between(ui, "private View discoverHeader()", "private String discoverGreetingName")
categories = between(ui, "private void addDiscoverCategories()", "private GameRecord discoverGame")
product = between(ui, "private View discoverProductCard", "private void addDiscoverTopRatedRail")
score = between(ui, "private LinearLayout homeScore", "private View homeMetadata")
price = between(ui, "private View homePrice", "private View discoverProductCard")
metadata = between(ui, "private View homeMetadata", "private View homePrice")
top_rail = between(ui, "private void addDiscoverTopRatedRail", "private View discoverTopRatedCard")
top_card = between(ui, "private View discoverTopRatedCard", "private View heroOpportunityCard")
hero = between(ui, "private View heroOpportunityCard", "private View discoverFlatArtwork")
nav = between(ui, "private void renderNav()", "private void addMarketHeader")

checks = [
    ("build identity is current beta line",
     "versionName '5.12." in build or
     ("versionName releaseVersionName ?: '5.12." in build and
      "LUDO_VERSION_NAME" in build)),
    ("Home gradient scales with its content bounds",
     "RadialGradient" in chrome and "b.height()/(float)b.width()" in chrome),
    ("Home uses a Helvetica-compatible bundled typeface",
     '"fonts/remus-variable.ttf"' in ui and
     "Typeface.create(discoverTypefaceBase,w,false)" in ui),
    ("Home greeting matches the Figma hierarchy",
     '"Bentornato,"' in header and "discoverGreetingName()" in header and
     "discoverTextWeight(discoverGreetingName(),37" in header),
    ("Hero keeps real game identity and offer information",
     all(x in hero for x in ["discoverGameDescription(d)", "FeaturedBoxView box=new FeaturedBoxView(true,false)", "setFeaturedArtwork(image,placeholder,d", "name(d)", "total(d)", "discoverDiscountBadge(d", "LudoIcons.STAR", "openDetail(d)"])),
    ("Hero featured artwork uses adaptive 2.5D geometry",
     all(x in ui for x in ["setPolyToPoly", "path(topPath,geometry.top)", "path(sidePath,geometry.side)", "sampleEdge(bitmap)"])),
    ("category navigation uses the shared BGG clusters",
     "DiscoverCategories.labels()" in ui and "DiscoverCategories.query(index)" in categories and "openDiscoverCategory" in categories),
    ("category bitmaps are sampled and cached",
     "opts.inSampleSize=8" in ui and "discoverCategoryIcons.put(index,bitmap)" in ui),
    ("offers and latest reuse one canonical product card",
     "discoverProductCard(d,false)" in ui and
     "discoverProductCard(d,true)" in ui),
    ("Home product cards use BGG-only artwork",
     "homePreviewBox(d)" in product and
     "setProductArtwork(source,placeholder,d.bggId,d.bggImageUrl" in ui and
     "firstListingPhoto" not in product and
     "setDealArtwork" not in product),
    ("product price and discount share a data-backed row",
     "homePrice(d)" in product and "discoverDiscountBadge(d,12)" in price and "total(d)" in price),
    ("fresh variant promotes publication time",
     "publicationDisplay(d)" in product and "if(fresh)" in product),
    ("offer variant promotes BGG rating with Font Awesome star",
     "homeMetadata(d)" in product and "homeScore(d,14)" in metadata and "LudoIcons.STAR" in score and "d.rating" in score),
    ("BGG list labels its overall BGG rank",
     '" BGG"' in top_card and "d.rank" in top_card and "d.voters" in top_card),
    ("BGG rating remains visible beside price",
     "homeScore(d,18)" in top_card and "LudoIcons.STAR" in score and "d.rating" in score),
    ("reference section order is hero, categories, offers, BGG, latest",
     home.index("heroOpportunityCard") < home.index("addDiscoverCategories") <
     home.index("addDiscoverValueRail") < home.index("addDiscoverTopRatedRail") <
     home.index("addDiscoverFreshRail")),
    ("bottom navigation uses shared icons for Home Catalogo and integrated Ludo",
     'LudoIcons.BOOK_OPEN,"Libreria","library"' not in nav and all(x in nav for x in [
         'LudoIcons.HOUSE,"Home","discover"',
         'LudoIcons.SEARCH,"Catalogo","catalog"',
         'navItem("","Ludo","companion")', 'R.drawable.ludo_icon_ludo'
     ])),
    ("Font Awesome semantic map includes core app actions",
     all(x in icons for x in ["HOUSE=", "SEARCH=", "HEART=", "TRASH=", "CAMERA=", "GEAR=", "CHECK=", "STAR="])),
    ("product-detail listing photos crop to fill while BGG stays fit",
     "isBggPhoto(d,item)?ImageView.ScaleType.FIT_CENTER:ImageView.ScaleType.CENTER_CROP" in ui),
]

for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
failed = [name for name, ok in checks if not ok]
if failed:
    raise SystemExit("Discover visual polish regression failed: " + ", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} Discover visual polish guards")

