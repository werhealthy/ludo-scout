# Complete catalog audit and AI measurement

The phone remains the source of its current catalog. GitHub fixtures and historical
exports are not a live copy. Use the existing classification audit export from
the phone; no new APK, reset or database migration is required.

## Inspect every saved canonical listing

```bash
python3 tools/catalog_ai_audit.py --archive ludo-audit.zip --calls-reserved 10 --output catalog-audit.json
```

`10` is the last owner-confirmed response count for October 2026. Supply a fresh
authenticated status count before executing any plan. This offline command does
not read service status, activate AI or execute its batches.

The report verifies the export format and section counts, records its SHA256 and
inspects every canonical listing across all lifecycle states. It flags missing
data, orphaned saved games, duplicate exact Vinted IDs, contradictory saved BGG
associations and existing review. Flags identify audit candidates, not proven
classification errors. No fuzzy identity matching or Java classifier recreation.

AI planning includes ACTIVE and AUTO_FILTERED rows with titles. Sold, hidden and
archived rows remain counted but are excluded from new AI requests. Exact Vinted
IDs are deduplicated; the newest eligible title/brand represents each item, while
priority includes risk from older eligible duplicates. Distinct IDs with similar
titles remain distinct. Each batch holds at most eight records and 4096 UTF8
bytes including the worst-case request ID. Oversized rows are reported, never
silently truncated. Only listing ID/title/brand enter planned payloads.

At 10 declared reservations, no more than 90 additional requests are planned.
720 is the maximum number of records, not a guaranteed coverage count. Long
titles and small batches can lower coverage. Unplanned and oversized records
are explicit. Existing failed reservations, runtime circuit and actual server
budget can further reduce capacity. No budget reset, paid upgrade or retry.

Output contains private listing text. Keep it with the source archive; do not
commit real catalog reports or phone exports into GitHub.

## Measure improvement without using AI as its own reference

The existing `classification_benchmark.py` now emits paired `error_reduction`:
both prior and proposed categories are compared against the same independently
supplied known reference labels. Unknown reference labels and unknown prior
categories cannot establish a before/after error denominator. Unknown model
answers remain unresolved errors, not corrected records. Accuracy likewise
excludes unknown references and reports their count explicitly.

Relative reduction is `(baseline_errors - model_errors_or_abstentions) /
baseline_errors`. With zero baseline errors, missing references or no comparable
rows, reduction is null. Negative values mean deterioration. Supplier-declared
reference independence is not independently attested by the tool. Measurements
apply only to labelled rows, never automatically to the entire catalog, physical
contents, BGG identity or product language.

## Verification and limits

Run the existing offline classification suite:

```bash
python3 regression/classification_benchmark_test.py
python3 regression/catalog_ai_audit_test.py
python3 regression/classification_runner_test.py
python3 regression/classification_resume_test.py
python3 regression/classification_resume_execute_test.py
python3 regression/classification_proposals_test.py
```

These tools perform no provider calls or catalog writes. They do not integrate
automatic AI into the Android engine or correct the archive. A current export
and independently checked product evidence are still required to report actual
catalog error rates or a 99% reduction. Export sections are separate snapshots;
cross-section discrepancies can also reflect collection during export.
