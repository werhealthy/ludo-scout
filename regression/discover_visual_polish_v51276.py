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
    ("Home gradient scales with its content bounds",
     "RadialGradient" in chrome and "b.height()/(float)b.width()" in chrome),
    ("Home uses a Helvetica-compatible bundled typeface",
     '"fonts/remus-variable.ttf"' in ui and
     "Typeface.create(discoverTypefaceBase,w,false)" in ui),
    ("Home greeting matches the Figma hierarchy",
     '"Bentornato,"' in header and "discoverGreetingName()" in header and
     "discoverTextWeight(discoverGreetingName(),37" in header),
    ("Hero keeps real game identity and offer information",
     all(x in hero for x in ["discoverGameDescription(d)", "FeaturedBoxArtwork featured=featuredBoxArtwork(d)", "setDiscoverBggArtwork(featured.source,featured.placeholder,d", "name(d)", "total(d)", "discoverDiscountBadge(d", "LudoIcons.STAR", "openDetail(d)"])),
    ("Hero featured artwork uses adaptive 2.5D geometry",
     all(x in ui for x in ["setPolyToPoly", "topFace=quad(topPts)", "sideFace=quad(sidePts)", "sampleEdge(bitmap)"])),
    ("category navigation uses the shared BGG clusters",
     "DiscoverCategories.labels()" in ui and "DiscoverCategories.query(index)" in categories and "openDiscoverCategory" in categories),
    ("category bitmaps are sampled and cached",
     "opts.inSampleSize=8" in ui and "discoverCategoryIcons.put(index,bitmap)" in ui),
    ("offers and latest reuse one canonical product card",
     "discoverProductCard(d,false)" in ui and
     "discoverProductCard(d,true)" in ui),
    ("Home product cards use BGG-only artwork",
     "discoverBggCover(d" in product and
     "firstListingPhoto" not in product and
     "setDealArtwork" not in product),
    ("product discount follows the real price row",
     "discoverDiscountBadge(d,11)" in product and "bottom.addView(badge)" in product),
    ("fresh variant promotes publication time",
     "publicationDisplay(d)" in product and "if(fresh)" in product),
    ("offer variant promotes BGG rating with Font Awesome star",
     "LudoIcons.STAR" in product and "d.rating" in product),
    ("BGG list labels its overall BGG rank",
     '" BGG"' in top_card and "d.rank" in top_card and "d.voters" in top_card),
    ("BGG rating remains visible beside price",
     "LudoIcons.STAR" in top_card and "d.rating" in top_card),
    ("reference section order is hero, categories, offers, BGG, latest",
     home.index("heroOpportunityCard") < home.index("addDiscoverCategories") <
     home.index("addDiscoverValueRail") < home.index("addDiscoverTopRatedRail") <
     home.index("addDiscoverFreshRail")),
    ("bottom navigation uses shared Font Awesome icons",
     all(x in nav for x in [
         'LudoIcons.HOUSE,"Home","discover"',
         'LudoIcons.SEARCH,"Catalogo","catalog"',
         'LudoIcons.BOOK_OPEN,"Libreria","library"',
         'LudoIcons.STAR,"Ludo","companion"'
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

