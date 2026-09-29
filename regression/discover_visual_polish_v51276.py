#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
app = ROOT / "app/src/main"
ui = (app / "java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
build = (ROOT / "app/build.gradle").read_text(encoding="utf-8")

def between(source, start, end):
    return source[source.index(start):source.index(end, source.index(start))]

chrome = between(ui, "private void applyDiscoverChrome()", "private void renderDiscover()")
categories = between(ui, "private void addDiscoverCategories", "private void addDiscoverFreshRail")
category_tile = between(ui, "private View discoverCategoryTile", "private void addDiscoverFreshRail")
fresh = between(ui, "private View discoverFreshCard", "private void addDiscoverTopRatedRail")
top_rail = between(ui, "private void addDiscoverTopRatedRail", "private View discoverTopRatedCard")
top_card = between(ui, "private View discoverTopRatedCard", "private void addDiscoverValueRail")
hero = between(ui, "private View heroOpportunityCard", "private View discoverBoxArtwork")
box = between(ui, "private View discoverBoxArtwork", "private View discoverFlatArtwork")
nav_item = between(ui, "private View navItem", "private void addMarketHeader")

checks = [
    ("visual polish build identity advances to 5.12.76",
     "versionName '5.12.76-discover-visual-polish'" in build),
    ("lavender-to-cream background gradient",
     "GradientDrawable.Orientation.TOP_BOTTOM" in chrome and "DISCOVER_LAVENDER" in chrome and "DISCOVER_BG" in chrome),
    ("generated hero and category PNG assets are packaged",
     (app / "res/drawable-nodpi/discover_hero_background.png").exists() and
     (app / "res/drawable-nodpi/discover_category_icons_sheet.png").exists() and
     "R.drawable.discover_hero_background" in hero and "R.drawable.discover_category_icons_sheet" in category_tile),
    ("featured offer hierarchy shows title, BGG stars, price, and discount",
     "name(d)" in hero and "total(d)" in hero and "★" in hero and "saved" in hero and "OCCASIONE" in hero),
    ("only the featured offer uses a perspective game box made from its real cover",
     "discoverBoxArtwork(d" in hero and "discoverBoxArtwork" not in fresh + top_card and
     "setDealArtwork(cover,placeholder,d)" in box and "setRotationY(" in box),
    ("category tiles use illustrated symbols without counts and have a directory",
     "discoverCategoryIcon" in category_tile and '" giochi"' not in category_tile and
     '"Vedi tutte"' in categories and "showDiscoverCategoryDirectory" in categories),
    ("latest listings use full-bleed listing photos with age and price",
     "ImageView.ScaleType.CENTER_CROP" in fresh and "firstListingPhoto(d)" in fresh and
     "publicationDisplay(d)" in fresh and "total(d)" in fresh),
    ("BGG favorites use a vertical ranked list with scores",
     "HorizontalScrollView" not in top_rail and "LinearLayout.VERTICAL" in top_rail and
     "discoverTopRatedCard(d,i+1)" in top_rail and "rank" in top_card.lower() and "d.rating" in top_card),
    ("Discover navigation uses rounded bold labels and a selected pill",
     "discoverText(label,12" in nav_item and "Typeface.BOLD" in nav_item and
     "round(Color.rgb(239,230,244),18" in nav_item),
]

for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
failed = [name for name, ok in checks if not ok]
if failed:
    raise SystemExit("Discover visual polish regression failed: " + ", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} Discover visual polish guards")
