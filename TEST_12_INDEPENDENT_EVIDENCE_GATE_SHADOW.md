# Test 12 — Independent Evidence Gate Shadow

Final conservative safety hardening for the Vinted batch resolver.

The previous shadow gate allowed a long candidate title to pass when the observed listing title was lexically similar. That is not an independent signal because both can repeat the same game-name tokens.

This test removes that bypass: if a multiword canonical game title is fully present but the Vinted candidate adds two or more unexplained distinctive tokens, the batch auto-link path now requires either an explicit board-game cue in the candidate title or a matching brand. Otherwise the candidate remains unresolved and can fall back to the legacy resolver.

Zero network. Zero writes.

Expected diagnostic build: `batch-resolver-independent-evidence-shadow-v4`.
