#!/usr/bin/env python3
import re
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
resolver = (ROOT / "app/src/main/java/it/vintedaffari/app/VintedLinkResolver.java").read_text(encoding="utf-8")


def norm(value):
    value = unicodedata.normalize("NFD", value)
    value = "".join(ch for ch in value if unicodedata.category(ch) != "Mn")
    return re.sub(r"[^a-z0-9]+", " ", value.lower()).strip()


def jaccard(left, right):
    a, b = set(norm(left).split()), set(norm(right).split())
    return len(a & b) / len(a | b) if a and b else 0.0


# Real recovery shape observed on the Pixel: the listing uses an alternate-language title,
# while the Vinted candidate uses the canonical BGG name plus the known publisher/brand.
observed = "la Cucaracha loop ravensburger + batterijen"
canonical = "Bugs in the Kitchen"
brand = "Ravensburger"
candidate = "ravensburger bugs in the kitchen"
candidate_without_brand = norm(candidate).replace(norm(brand), "").strip()

assert jaccard(observed, candidate) < 0.68, "fixture must reproduce the old title-score miss"
assert candidate_without_brand == norm(canonical), "fixture must carry an exact canonical title after known-brand removal"


def alternate_title_policy(canonical_exact, exact_price, photo_similarity):
    return canonical_exact and exact_price and photo_similarity >= 0.84


assert alternate_title_policy(True, True, 0.91)
assert not alternate_title_policy(True, False, 0.91)
assert not alternate_title_policy(True, True, 0.83)
assert not alternate_title_policy(False, True, 0.91)

photo_start = resolver.index("private void applyPhotoEvidence")
photo_end = resolver.index("private static String extractNearbyImage", photo_start)
photo_block = resolver[photo_start:photo_end]

checks = [
    ("known brand can be removed before exact canonical-title comparison",
     "canonicalTitleExact" in resolver and "titleWithoutKnownBrand" in resolver),
    ("candidate records observed and canonical title provenance",
     "observedTitleMatch" in resolver and "canonicalTitleMatch" in resolver),
    ("one strong candidate still receives photo evidence",
     "ranked.size()<2" not in photo_block and "ranked.isEmpty()" in photo_block),
    ("canonical-only identity requires exact price and strong photo proof",
     "canonicalOnly" in resolver and "exactObservedPrice" in resolver and "photoSimilarity>=.84" in resolver),
    ("canonical-only identity cannot use the unverified catalogue fast path",
     "!canonicalOnly&&best.catalogStructured" in resolver),
]

for name, ok in checks:
    print(("PASS " if ok else "FAIL ") + name)

failed = [name for name, ok in checks if not ok]
if failed:
    raise SystemExit("Vinted alternate-title photo-proof regression failed: " + ", ".join(failed))

print(f"PASS {len(checks)}/{len(checks)} Vinted alternate-title photo-proof guards")
