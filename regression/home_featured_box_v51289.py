#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ui=(ROOT/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

hero=ui[ui.index("private View heroOpportunityCard"):ui.index("private View discoverFlatArtwork")]

checks=[
    ("release name", "5.12.89-featured-box-poc" in build),
    ("featured hero uses dedicated artwork renderer", "FeaturedBoxArtwork featured=featuredBoxArtwork(d)" in hero),
    ("featured hero no longer center-crops the BGG cover", "ImageView.ScaleType.CENTER_CROP" not in hero),
    ("renderer maps cover into a perspective quad", "setPolyToPoly" in ui and "drawBitmap(cover,coverMatrix,bitmapPaint)" in ui),
    ("renderer has separate top and side planes", "topFace=quad(topPts)" in ui and "sideFace=quad(sidePts)" in ui),
    ("renderer derives side tone from the real cover", "sampleEdge(bitmap)" in ui),
    ("renderer includes ambient and contact shadows", "Broad ambient shadow plus tighter contact shadow" in ui),
    ("renderer remains adaptive to wide tall square covers", "HomePresentation.coverMode(cover.getWidth(),cover.getHeight())" in ui),
]

for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("5.12.89 featured-box regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} 5.12.89 featured-box guards")
