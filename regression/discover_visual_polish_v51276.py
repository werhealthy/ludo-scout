#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ui = (ROOT / "app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
build = (ROOT / "app/build.gradle").read_text(encoding="utf-8")

def between(source, start, end):
    return source[source.index(start):source.index(end, source.index(start))]

chrome = between(ui, "private void applyDiscoverChrome()", "private void renderDiscover()")
home = between(ui, "private void renderDiscover()", "private void addBundleEmptyState")
categories = between(ui, "private void addDiscoverCategories", "private void addDiscoverFreshRail")
category_tile = between(ui, "private View discoverCategoryTile", "private String discoverCategoryGlyph")
fresh = between(ui, "private View discoverFreshCard", "private void addDiscoverTopRatedRail")
top_rail = between(ui, "private void addDiscoverTopRatedRail", "private View discoverTopRatedCard")
top_card = between(ui, "private View discoverTopRatedCard", "private void addDiscoverValueRail")
hero = between(ui, "private View heroOpportunityCard", "private View discoverBoxArtwork")
box = between(ui, "private View discoverBoxArtwork", "private View discoverFlatArtwork")
nav = between(ui, "private void renderNav()", "private void addMarketHeader")
nav_item = between(ui, "private View navItem", "private void addMarketHeader")

checks = [
    ("visual polish build identity advances to 5.12.76",
     "versionName '5.12.76-discover-visual-polish'" in build),
    ("Discover background fades from lavender into warm cream",
     "GradientDrawable.Orientation.TOP_BOTTOM" in chrome and
     "DISCOVER_LAVENDER" in chrome and
     "DISCOVER_BG" in chrome),
    ("featured opportunity uses generated organic artwork and keeps live content",
     "R.drawable.discover_hero_background" in hero and
     "name(d)" in hero and "total(d)" in hero),
    ("featured opportunity gives BGG stars and discount a clear visual role",
     "★" in hero and "DISCOVER_ORANGE" in hero and
     ("saved" in hero or "saving(d)" in hero)),
    ("only the featured opportunity uses a perspective game box",
     "discoverBoxArtwork(d" in hero and
     "discoverBoxArtwork" not in fresh + top_card and
     "ImageView.ScaleType.CENTER_CROP" in box and
     "setRotationY(" in box),
    ("categories use illustrated icons without game counts",
     "R.drawable.discover_category_icons_sheet" in categories + category_tile and
     "discoverCategoryIcon" in categories + category_tile and
     '" giochi"' not in category_tile and
     '"Vedi tutte"' in categories),
    ("category directory opens from Vedi tutte",
     "showDiscoverCategoryDirectory" in categories),
    ("new listings show a full-bleed cover with publication time and price below",
     "ImageView.ScaleType.CENTER_CROP" in fresh and
     "publicationDisplay(d)" in fresh and
     "discoverFullBleedArtwork" in fresh and
     "total(d)" in fresh),
    ("BGG top games are a vertical ranked list",
     "HorizontalScrollView" not in top_rail and
     "LinearLayout.VERTICAL" in top_rail and
     "discoverTopRatedCard(d,i+1)" in top_rail and
     "rank" in top_card.lower()),
    ("Discover navigation uses rounded typography and a clearer selected item",
     "sans-serif-rounded" in nav + nav_item and
     "Typeface.BOLD" in nav_item and
     "dp(28)" in nav_item and
     "setBackground(round(" in nav_item),
    ("latest, ratings and value still use the established trusted data rails",
     "addDiscoverFreshRail(limitDeals(newest,12))" in home and
     "addDiscoverTopRatedRail(uniqueDiscoverGames(topRated,12))" in home and
     "addDiscoverValueRail(limitDeals(value,12))" in home and
     'db.getDeals("trusted",320)' in home),
]

for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)
failed = [name for name, ok in checks if not ok]
if failed:
    raise SystemExit("Discover visual polish regression failed: " + ", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} Discover visual polish guards")
