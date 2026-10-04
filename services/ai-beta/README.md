# Private AI beta

Default OFF. Worker + one SQLite Durable Object hold all devices' reservations.
No API key, device secret or admin endpoint is built into Android. The app's
settings entry submits only the eight fixed title/brand records, then displays
proposals without modifying the catalog. This is not archive classification.

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
   Create the token with at least32random bytes; deliver privately to the owner.
   Revoke a device by removing its digest and redeploying with `ENABLED=false`
   during changes. A stolen valid token can consume remaining quota; this beta
   does not claim app attestation or public-user authentication.
4. Deploy with ENABLED=false. Namespace is SQLite-backed for the Free plan.
   Confirm `/v1/status` with device auth reports the imported count.
   Expiring `FREE_TIER_VALID_UNTIL` is epoch milliseconds; initial live trial
   attestation lasts at most24hours. Set ENABLED=true only after account checks.
5. Enter the HTTPS workers.dev endpoint and personal token in Android
   Settings → Test AI · beta → Configura prova. Enable the manual test.
   Tap Test AI su8annunci once. Repeat uses the same requestID/content cache,
   not a new provider attempt. No cron, queue or automatic background AI.
6. Disable after the trial; record exact service counter and phone result.

## Limits and failure behavior

100 attempts/month total; EUR1 conservative reservation, EUR0.01 per attempt,
not actual invoice. Existing reservations seed the global object. Client
cannot set budget/object/model/contract. Input<=4096UTF8bytes,1..8records;
output<=2048tokens/body<=64KiB,timeout30s. A failed/unknown attempt retains its
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

Known deployment prerequisites: authenticated Cloudflare account, confirmed
Free plan, exact Gemini key/project association and private device enrollment.
No deployment or real provider call is implied by a green local test.
