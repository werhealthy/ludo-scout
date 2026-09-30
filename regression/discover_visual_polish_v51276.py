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
top_rail = between(ui, "private void addDiscoverTopRatedRail", "private View discoverTopRatedCard")
top_card = between(ui, "private View discoverTopRatedCard", "private View heroOpportunityCard")
hero = between(ui, "private View heroOpportunityCard", "private View discoverFlatArtwork")
nav = between(ui, "private void renderNav()", "private void addMarketHeader")

checks = [
    ("build identity is current beta line",
     "versionName '5.12." in build),
    ("Home preserves the exported Figma background aspect ratio",
     "R.drawable.discover_home_shader" in chrome and
     "scale=b.width()/(float)bitmap.getWidth()" in chrome and
     "BitmapDrawable" not in chrome),
    ("Home uses a Helvetica-compatible bundled typeface",
     '"fonts/remus-variable.ttf"' in ui and
     "Typeface.create(discoverTypefaceBase,w,false)" in ui),
    ("Home greeting matches the Figma hierarchy",
     '"Bentornato,"' in header and "discoverGreetingName()" in header and
     "discoverTextWeight(discoverGreetingName(),37" in header),
    ("Hero is the Figma composition rather than procedural waves",
     "R.drawable.discover_hero_background" in hero and
     "discoverGameDescription(d)" in hero and
     "discoverBggCover(d" in hero and
     "rfp.leftMargin=dp(-29)" in hero and
     "LudoIcons.STAR" in hero),
    ("Home exposes exactly five semantic category clusters",
     'new String[]{"Strategia","Cooperativi","Fantasy","Filler","Eurogame"}' in ui and
     "for(int i=0;i<labels.length;i++)tiles.addView" in categories and
     "preferredDiscoverCategories" not in ui),
    ("category tiles keep translucent glass surfaces",
     "Color.argb(51" in categories and
     "discoverCategoryShape(index)" in categories and
     "LinearGradient" in ui),
    ("offers and latest reuse one canonical product card",
     "discoverProductCard(d,false)" in ui and
     "discoverProductCard(d,true)" in ui),
    ("Home product cards use BGG-only artwork",
     "discoverBggCover(d" in product and
     "firstListingPhoto" not in product and
     "setDealArtwork" not in product),
    ("discount overlays the image in the shared product card",
     "imageWrap.addView(badge,bp)" in product and
     "Gravity.TOP|Gravity.RIGHT" in product),
    ("fresh variant promotes publication time",
     "publicationDisplay(d)" in product and "if(fresh)" in product),
    ("offer variant promotes BGG rating with Font Awesome star",
     "LudoIcons.STAR" in product and "d.rating" in product),
    ("BGG list uses category-aware rank metadata",
     "discoverRankCategory(d)" in top_card and '" BGG"' not in top_card),
    ("BGG heart has dedicated unclipped space",
     "setClipChildren(false)" in top_card and "LudoIcons.HEART" in top_card),
    ("reference section order is hero, categories, offers, BGG, latest",
     home.index("heroOpportunityCard") < home.index("addDiscoverCategories") <
     home.index("addDiscoverValueRail") < home.index("addDiscoverTopRatedRail") <
     home.index("addDiscoverFreshRail")),
    ("bottom navigation uses shared Font Awesome icons",
     all(x in nav for x in [
         'LudoIcons.HOUSE,"Home","discover"',
         'LudoIcons.SEARCH,"Catalogo","catalog"',
         'LudoIcons.BOOK_OPEN,"Libreria","library"',
         'LudoIcons.USER,"Profilo","companion"'
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
