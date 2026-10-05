# Private AI beta

Default OFF. Worker + one SQLite Durable Object hold all devices' reservations.
No API key, device secret or admin endpoint is built into Android. The existing
owner configuration now enables automatic checks of current ACTIVE announcements
in foreground/recovery queue owners, alongside the manual smoke and comparison
dialogs. The switch is explicitly labelled "Abilita AI nel Motore e prove manuali".
Unconfigured or disabled phones send nothing. The private router may receive listing ID/title/brand, up to 2,000 characters of acquired listing text and up to four Vinted photo URLs. BGG identity, local classifier state, pricing, preferences and catalog writes remain local.

Automatic checks use a separate encrypted no-backup journal and a cross-process
file lease, bounded8 records/32768UTF8bytes. Validated per-record proposals are cached
for7days (up to800 entries with a850KB serialization bound); local description or
identity changes recheck current SQLite evidence without refreshing remote cache
expiry. Mixed batches send only uncached records. GET/status verifies enabled state and valid100attempt/EUR1reservation accounting before a Gemini reservation. An authenticated Windows Ollama worker gets first refusal when its heartbeat is fresh; local completions do not reserve or spend Gemini quota. OFF,
unavailable or inconsistent state backs off15minutes. Pending transport recovery
keeps the original requestID; the server resolves prior IDs before the new budget
cutoff. Terminal provider FAILED retains its charge and advances unrelated work.
No client reset, refund, service activation or paid upgrade.

AI agreement never establishes BGG identity, restores filtered history or promotes an item. Local vision may propose an edition language, but it remains review-only and cannot certify or write edition state by itself. A negative proposal conflicting with local BASE_GAME/UNCERTAIN
can change an existing automatic ACTIVE deal fromOK toMATCH_UNCERTAIN, with full
source freshness checked again inside the writer transaction. Human confirmations,
overrides and stronger holds remain protected. Subsequent automatic legacy upsert
preserves that hold for unchanged title/brand/BGG; human overrides retain precedence.
This is an asynchronous additional check, not a claim that all announcements were AI-verified before their first display. BGG context, local classification, prices and preferences stay local and unchanged; the bounded acquired listing text and Vinted photo URLs are the only added evidence routed for private AI analysis. Diagnostics record counts/states, no titles
or credentials. Manual comparison alone still never writes the catalog.

## Verify locally

`npm ci --ignore-scripts && npm test`

Tests exercise the real local Workers/SQLite runtime with only provider HTTP
replaced. No Gemini credentials or external provider traffic in tests/CI.
`npx wrangler deploy --dry-run` validates the deploy bundle, not the account plan.

## Cutover checklist (owner/admin only)

1. Verify Cloudflare Workers Free and Gemini project Free Tier/no billing.
   No automatic paid upgrade. Free-tier attestation is an admin declaration,
   not an API proof; before enabling it verify the actual key's project.
2. Ensure the old GitHub benchmark is disabled and no provider job is active.
   Fetch a fresh `ai-benchmark-ledger/ledger.json`. Run
   `node scripts/bootstrap.mjs LEDGER SEED.json --benchmark-disabled`.
   Inspect the output: currently October has 8 calls/EUR0.08 reserved.
   This script never resets/refunds historical calls; repeated import into
   the service must be byte-identical. Invalid/inconsistent state blocks AI.
3. Use authenticated Cloudflare admin tooling to set secrets `GEMINI_API_KEY`,
   `SEED_MANIFEST` (the generated JSON), and `DEVICE_DIGESTS` (JSON mapping device
   IDs to SHA256 of their separate randomly generated tokens). Never place
   raw secrets in wrangler.jsonc, shell arguments, CI output or chat.
   Run node scripts/enroll.mjs DEVICE_ID PRIVATE_OUTPUT.json to create
   a32-byte random token and digest in an exclusive mode0600 file, without printing
   the secret. Deliver privately to the owner.
   Revoke a device by removing its digest and redeploying with `ENABLED=false`
   during changes. A stolen valid token can consume remaining quota; this beta
   does not claim app attestation or public-user authentication.
4. Deploy with ENABLED=false for provisioning. Namespace is SQLite-backed for the Free plan.
   Confirm `/v1/status` with device auth reports the imported count.
   Expiring `FREE_TIER_VALID_UNTIL` is epoch milliseconds; initial live trial
   attestation lasts at most24hours. Set ENABLED=true only after account checks.
5. For local-first mode, set `LOCAL_WORKER_DIGEST` to the SHA256 of a separate Windows-worker token and run `tools/ai_local_worker.py` with that raw token kept only on the PC. Then enter the HTTPS workers.dev endpoint and the phone's separate personal token in Android
   Settings → Test AI · beta → Configura prova. Enable AI in the engine and manual tests.
   Tap Test AI su8annunci once. Repeat uses the same requestID/content cache,
   not a new provider attempt. Automatic engine checks reuse this configuration and budget; the smoke test stays manual.
6. Disable after the trial; record exact service counter and phone result.

## Limits and failure behavior

100 Gemini attempts/month total; EUR1 conservative reservation, EUR0.01 per Gemini attempt, not actual invoice. Local Ollama jobs do not consume this counter. Existing reservations seed the global object. Client cannot set budget/object/model/contract. Input<=32768UTF8bytes,1..8records;
Gemini fallback output<=2048tokens/body<=64KiB,timeout30s. A failed/unknown attempt retains its
reservation.429/503 open a persistent circuit; redeploy alone never resets it.
Recovery requires a reviewed admin migration, not client retry. No refunds.
Kill configuration stops new attempts; an already sent request can finish.
Config updates may require redeploy/propagation; not an instantaneous provider
cancel. Model prices must be revalidated before any paid decision.

Results are private by device. Content hash is not anonymization of common
titles. Responses expire after7days and are deleted/redacted on the next service request;
this beta does not guarantee physical deletion exactly at the expiry instant; idempotency tombstones
remain so old requestIDs cannot silently generate another attempt. Monthly
counters/import record retained for audit. No title/body/credential logs.

Known deployment prerequisites: authenticated Cloudflare account, confirmed Free plan, private phone enrollment and private Windows-worker enrollment. Gemini fallback additionally requires the exact Gemini key/project association and a valid `FREE_TIER_VALID_UNTIL` window; with that window closed, local AI can still run while Gemini remains unavailable.
No deployment or real provider call is implied by a green local test.
