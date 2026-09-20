# Test 3 — Batch-Reuse Shadow Mode

This build makes **no Vinted requests for the test** and does not alter resolver decisions.
It inspects the durable SQLite queue plus eligible DEFERRED_LINK listings and groups them by the
same canonical game that `VintedLinkResolver` uses for its first catalogue query.

Diagnostic line:

`vintedBatchReuseShadow={...}`

Definitions:
- `queued`: core Vinted jobs already materialised in `processing_jobs`.
- `readyPool`: queued + deferred listings whose retry time is already due and whose BGG game passes
  the same visibility/rating gates used by `promoteDeferredVintedBatch()`.
- `primaryBefore`: one canonical first search per listing (current resolver model conceptually).
- `primaryAfter`: one canonical first search per query family (batch model).
- `potentialSaved`: `primaryBefore - primaryAfter`, counting only groups with >=2 listings.
- `reusePct`: structural share of canonical first-search attempts that could be eliminated.
- `topReady`: largest same-query groups, e.g. `Istanbul:8->1`.
- `fallbacks-not-counted`: fallback title queries after a true miss are intentionally excluded, so
  this does not pretend every remote request can be shared.
