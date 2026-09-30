#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
app = ROOT / "app/src/main"
ui = (app / "java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
build = (ROOT / "app/build.gradle").read_text(encoding="utf-8")

def between(source, start, end):
    return source[source.index(start):source.index(end, source.index(start))]

chrome = between(ui, "private void applyDiscoverChrome()", "private void renderDiscover()")
home = between(ui, "private void renderDiscover()", "private List<DealRecord> limitDeals")
header = between(ui, "private View discoverHeader()", "private String discoverGreetingName")
categories = between(ui, "private void addDiscoverCategories", "private void showDiscoverCategoryDirectory")
category_tile = between(ui, "private View discoverCategoryTile", "private void addDiscoverFreshRail")
fresh = between(ui, "private View discoverFreshCard", "private void addDiscoverTopRatedRail")
top_rail = between(ui, "private void addDiscoverTopRatedRail", "private View discoverTopRatedCard")
top_card = between(ui, "private View discoverTopRatedCard", "private void addDiscoverValueRail")
offers = between(ui, "private void addDiscoverValueRail", "private View heroOpportunityCard")
hero = between(ui, "private View heroOpportunityCard", "private View discoverBoxArtwork")
nav = between(ui, "private void renderNav()", "private void addMarketHeader")

checks = [
    ("build identity advances beyond v5.12.76",
     "versionName '5.12.76-discover-visual-polish'" not in build),
    ("home uses the new multi-stop pastel background",
     "GradientDrawable.Orientation.TOP_BOTTOM" in chrome and
     "Color.rgb(228,249,246)" in chrome and "Color.rgb(255,239,247)" in chrome),
    ("home greeting matches the new reference hierarchy",
     '"Bentornato,"' in header and "discoverGreetingName()" in header and
     "37,Color.BLACK,Typeface.BOLD" in header),
    ("hero uses the blue wave pattern, real game artwork, BGG rating, price and discount",
     "discoverHeroPattern()" in hero and "discoverFlatArtwork(d" in hero and
     "name(d)" in hero and "total(d)" in hero and "★" in hero and "saved" in hero),
    ("category tiles use pastel shapes, symbols and no counts",
     "discoverCategoryShape" in category_tile and "discoverCategorySymbol" in category_tile and
     '" giochi"' not in category_tile and '"Vedi tutto"' in categories),
    ("best offers are a compact horizontal card rail",
     '"Le migliori offerte"' in offers and "HorizontalScrollView" in offers and
     "discoverListingArtwork" in offers and "saving(d)" in offers),
    ("BGG section is a three-row ranked list",
     '"I migliori su BGG"' in top_rail and "Math.min(3,deals.size())" in top_rail and
     "HorizontalScrollView" not in top_rail and "d.rank" in top_card and "d.rating" in top_card),
    ("latest listings use listing photos, publication time, discount and score",
     '"Appena pubblicati"' in ui and "ImageView.ScaleType.CENTER_CROP" in fresh and
     "publicationDisplay(d)" in fresh and "saving(d)" in fresh and "d.rating" in fresh),
    ("reference section order is hero, categories, offers, BGG, latest",
     home.index("heroOpportunityCard") < home.index("addDiscoverCategories") <
     home.index("addDiscoverValueRail") < home.index("addDiscoverTopRatedRail") <
     home.index("addDiscoverFreshRail")),
    ("reference bottom navigation labels are present without a selected pill",
     all(x in nav for x in ['"Home","discover"','"Catalogo","catalog"','"Libreria","library"','"Profilo","companion"']) and
     "round(Color.rgb(239,230,244)" not in nav),
]

for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
failed = [name for name, ok in checks if not ok]
if failed:
    raise SystemExit("Discover visual polish regression failed: " + ", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} Discover visual polish guards")
