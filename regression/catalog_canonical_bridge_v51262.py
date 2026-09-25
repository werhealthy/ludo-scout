#!/usr/bin/env python3
import re
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/it/vintedaffari/app"
POLICY = SRC / "CatalogBridgePolicy.java"

if not POLICY.exists():
    raise AssertionError("CatalogBridgePolicy is missing: canonical records still cannot be safely materialized")

harness = r'''
package it.vintedaffari.app;

public final class CatalogBridgePolicyHarness {
    private static DealRecord deal(int item, Integer total, Integer benchmark, String type, String verification) {
        DealRecord d = new DealRecord();
        d.signature = "sig";
        d.vintedItemId = "991";
        d.vintedUrl = "https://www.vinted.it/items/991";
        d.bggId = "42";
        d.rating = 7.1;
        d.itemPriceCents = item;
        d.totalCents = total;
        d.benchmarkCents = benchmark;
        d.listingType = type;
        d.verificationState = verification;
        d.lifecycle = "ACTIVE";
        return d;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        CatalogBridgePolicy.Result valid = CatalogBridgePolicy.decide(
                deal(1500, 1700, 3000, "BASE_GAME", "MATCH_UNCERTAIN"), true, false, false);
        check(valid.publish, "a canonically verified base game should be materialized");
        check("good".equals(valid.tier), "the existing price policy must classify the deal as good");

        CatalogBridgePolicy.Result overpriced = CatalogBridgePolicy.decide(
                deal(4000, 4500, 3000, "BASE_GAME", "MATCH_UNCERTAIN"), true, false, false);
        check(!overpriced.publish && "PRICE_REJECTED".equals(overpriced.reason),
                "an overpriced listing must stay out of Catalog");

        CatalogBridgePolicy.Result ambiguous = CatalogBridgePolicy.decide(
                deal(1500, 1700, 3000, "BASE_GAME", "MATCH_UNCERTAIN"), false, false, false);
        check(!ambiguous.publish, "an uncertain canonical identity must stay out of Catalog");

        CatalogBridgePolicy.Result review = CatalogBridgePolicy.decide(
                deal(1500, 1700, 3000, "BASE_GAME", "MATCH_UNCERTAIN"), true, true, false);
        check(!review.publish, "a manual-review listing must stay out of Catalog");

        CatalogBridgePolicy.Result expansion = CatalogBridgePolicy.decide(
                deal(1500, 1700, 3000, "EXPANSION", "MATCH_UNCERTAIN"), true, false, false);
        check(!expansion.publish, "an unverified expansion must stay out of Catalog");

        CatalogBridgePolicy.Result pendingPrice = CatalogBridgePolicy.decide(
                deal(1500, null, null, "BASE_GAME", "MATCH_UNCERTAIN"), true, false, false);
        check(pendingPrice.publish && "insufficient".equals(pendingPrice.tier),
                "missing market evidence is pending, not a fabricated price rejection");

        DealRecord low = deal(1500, 1700, 3000, "BASE_GAME", "MATCH_UNCERTAIN");
        low.rating = 5.9;
        check(!CatalogBridgePolicy.decide(low, true, false, false).publish,
                "BGG ratings below six must stay out of Catalog");

        DealRecord wrongId = deal(1500, 1700, 3000, "BASE_GAME", "MATCH_UNCERTAIN");
        wrongId.vintedUrl = "https://www.vinted.it/items/992-same-title";
        check(!CatalogBridgePolicy.decide(wrongId, true, false, false).publish,
                "the Vinted URL item id must equal the canonical item id");

        DealRecord foreignHost = deal(1500, 1700, 3000, "BASE_GAME", "MATCH_UNCERTAIN");
        foreignHost.vintedUrl = "https://example.com/items/991";
        check(!CatalogBridgePolicy.decide(foreignHost, true, false, false).publish,
                "a non-Vinted URL must never satisfy exact identity");
    }
}
'''

with tempfile.TemporaryDirectory() as td:
    td = Path(td)
    harness_path = td / "CatalogBridgePolicyHarness.java"
    sources = [
        SRC / "DealRecord.java",
        SRC / "PurchaseMath.java",
        SRC / "DealEvaluator.java",
        SRC / "DealPolicy.java",
        POLICY,
    ]
    # The workspace ships a Java 17 runtime but not the standalone javac executable. Java's
    # source-file launcher still uses the real compiler. Combine the small pure-Java production
    # units into one source file; only top-level public modifiers change for source-file legality.
    production = []
    for source in sources:
        text = source.read_text(encoding="utf-8")
        text = re.sub(r"^package it\.vintedaffari\.app;\s*", "", text)
        text = re.sub(r"public final class (DealRecord|PurchaseMath|DealEvaluator|DealPolicy|CatalogBridgePolicy)", r"final class \1", text)
        production.append(text)
    compile_stubs = """
final class VintedCard { double itemPrice; }
final class GameAnalysis {
    Integer totalCents, benchmarkCents, marketQ25Cents, offerCents, afterOfferCents, shippingCents;
    boolean marketAllowHot;
}
"""
    harness_body = re.sub(r"^\s*package it\.vintedaffari\.app;\s*", "", harness)
    combined = "package it.vintedaffari.app;\n" + harness_body + "\n" + compile_stubs + "\n" + "\n".join(production)
    harness_path.write_text(combined, encoding="utf-8")
    subprocess.run(["java", str(harness_path)], check=True)

database = (SRC / "DealDatabase.java").read_text(encoding="utf-8")
market = (SRC / "MarketStore.java").read_text(encoding="utf-8")

assert "materializeCanonicalDeal" in database, "DealDatabase does not materialize canonical listings"
assert "findByVintedItemId" in database, "materialization must deduplicate by exact Vinted identity"
assert market.count("materializeCanonicalDeal") >= 2, "both BGG-first and Vinted-first completion must bridge Catalog"

print("PASS canonical Catalog bridge policy, safety gates, price handling, and async wiring")
