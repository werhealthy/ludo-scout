#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ui=(ROOT/"app/src/main/java/it/vintedaffari/app/MainActivity.java").read_text(encoding="utf-8")
explore=(ROOT/"app/src/main/java/it/vintedaffari/app/BundleExploration.java").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

review=ui[ui.index("private void renderEngineReview"):ui.index("private void renderEngineHistory")]
review_card=ui[ui.index("private View reviewJobCard"):ui.index("private View bggMatchReviewCard")]
bgg_card=ui[ui.index("private View bggMatchReviewCard"):ui.index("private void openVintedRecoveryForDeal")]
resolution=ui[ui.index("private void openVintedResolution"):ui.index("private View vintedCandidateRow")]
bundles=ui[ui.index("private void renderBundles()"):ui.index("private void sortBundleSources")]

checks=[
    ("review/bundle release lineage keeps app identity",
     "applicationId 'it.vintedaffari.app'" in build and "1000000 + ciVersionCode.toInteger()" in build),
    ("review inbox separates Vinted, BGG identity and BGG variant",
     '"Collegamento Vinted"' in review and
     '"Gioco BGG"' in review and
     '"Variante / abbinamento BGG"' in review and
     '"BGG_VARIANT_REVIEW".equals(l.matchState)' in review),
    ("linked Vinted review offers confirm or replace",
     '"È quello giusto"' in review_card and
     '"Collega un altro annuncio"' in review_card and
     "confirmCurrentVintedLink(job,listing)" in review_card and
     "openVintedResolution(job)" in review_card),
    ("missing Vinted identity opens real search flow",
     '"Cerca / collega Vinted"' in review_card and
     "resolve.setOnClickListener(v->openVintedResolution(job))" in review_card),
    ("variant review does not masquerade as Vinted uncertainty",
     '"BGG è giusto"' in review_card and
     '"Cambia BGG"' in review_card and
     '"L’annuncio Vinted è sbagliato · collegane un altro"' in review_card),
    ("BGG identity review can also correct Vinted",
     '"Cambia Vinted":"Collega Vinted"' in bgg_card and
     "openVintedRecoveryForDeal(observed,null)" in bgg_card),
    ("resolution sheet labels existing link as proposal rather than truth",
     '"PROPOSTA NON ANCORA CONFERMATA"' in resolution and
     '"Cerca un altro annuncio su Vinted"' in resolution and
     '"Sostituisci con questo link"' in resolution),
    ("bundle prospects become one-shot seller suggestions",
     'EXPLORED_PREFIX="explored_seller:"' in explore and
     "putLong(EXPLORED_PREFIX+deal.sellerId,now)" in explore and
     "BundleExploration.wasExplored(this,d.sellerId)" in bundles),
    ("clearing active bundle capture preserves explored history",
     '.remove("source_signature").remove("item_id").remove("seller_id")' in explore and
     ".clear().apply()" not in explore[explore.index("public static void clear"):]),
    ("confirmed bundles are visually primary",
     '"Bundle confermati · "+sources.size()' in bundles and
     '"Questi sono bundle reali' in bundles and
     ('"Da controllare · "+prospects.size()' in bundles or '"Da esplorare · "+prospects.size()' in bundles) and
     bundles.index('"Bundle confermati · "+sources.size()') <
        (bundles.index('"Da controllare · "+prospects.size()') if '"Da controllare · "+prospects.size()' in bundles else bundles.index('"Da esplorare · "+prospects.size()'))),
    ("bundle sort controls only appear with confirmed bundles",
     "if(!sources.isEmpty())" in bundles and
     bundles.index("addBundleSortChip") > bundles.index("if(!sources.isEmpty())")),
]

for name,ok in checks:
    print(("PASS " if ok else "FAIL ")+name)
failed=[name for name,ok in checks if not ok]
if failed:
    raise SystemExit("5.12.31 review/bundle regression failed: "+", ".join(failed))
print(f"PASS {len(checks)}/{len(checks)} 5.12.31 review/bundle guards")
