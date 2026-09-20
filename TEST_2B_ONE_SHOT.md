# Test 2b — one-shot Vinted request measurement

Purpose: obtain one deterministic baseline from the existing durable Vinted queue without increasing request frequency or bypassing Vinted pacing/cooldown/budget.

## User flow
1. Open Ludo Scout settings.
2. Tap `Test 2b · misura 1 attività Vinted` once.
3. Wait for the in-app toast `Test 2b completato · ora copia la diagnostica` (or a blocked/error message).
4. Tap `Copia diagnostica` and share the text.

## Safety / behavior
- Uses a short SQLite-backed exclusive diagnostic lock so normal queue consumers cannot claim a second Vinted job while the one-shot is running.
- Does not bypass the Vinted pause state, pacing, hourly budget or remote-limit circuit.
- A normal pacing wait of <=70 seconds is honored; longer remote/budget blocks are reported instead of bypassed.
- Processes an already-existing durable Vinted job through the production resolver.

## Diagnostic line
`vintedRequestOneShot={ageMs=..., state=..., didWork=..., physicalDelta=..., cacheDelta=..., linkPhysicalDelta=..., linkCacheDelta=..., catalogDelta=..., itemDelta=..., linkedDelta=..., elapsedMs=...}`

Interpretation:
- `state=DONE`: the single queue job produced at least one VintedPublicSession event.
- `state=NO_HTTP`: a job was processed/requeued but did not need a public-page/cache access.
- `state=NO_CLAIM`: no runnable job was claimable.
- `state=BLOCKED`: normal app/Vinted safety state prevented the test.
- `state=ERROR`: copy diagnostics; do not retry blindly.
