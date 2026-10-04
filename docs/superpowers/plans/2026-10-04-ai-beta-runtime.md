# Protected AI beta — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Execution recommendation: native, within this session; no parallel agents required.

**Goal:** Deliver a manual eight-listing AI test in Android, with a server-held Gemini key and one durable budget shared by all devices.
**Architecture:** Cloudflare Workers Free routes authenticated requests to one SQLite Durable Object. The object reserves budget before Gemini transport, records terminal outcomes, and serves private cached proposals. Android displays proposals without applying them.
**Tech Stack:** JavaScript ES modules, Wrangler/local Cloudflare runtime as development tooling; existing Java17 Android stack, platform HTTPS and Android Keystore.
**Spec:** docs/specs/backend-reliability-recognition.md — servizio AI beta protetto. User approved free service and 100-call test on 2026-10-04.

## Global Constraints

- Gemini Free Tier and Cloudflare Workers Free only. No card, billing activation, paid fallback, cron or automatic retry.
- 100 provider attempts/month and EUR1 conservative reservations; EUR0.01 reserved per attempt. Import October's existing 8 attempts/EUR0.08 before activation.
- 1..8 title/brand/ID records per manual request; request JSON <=4096 UTF8 bytes, output <=2048 tokens, fixed Gemini3.1FlashLite, timeout30seconds.
- Provider/app default OFF. One stable global budget object; client cannot choose an object or update budget.
- All responses remain proposals; local types, BGG, language, pricing, overrides and favorites unchanged.
- Old Actions benchmark must be disabled before new provider activation. Future benchmark uses the shared service or stays disabled.
- Scope excludes archive-wide classification, public account onboarding, image/OCR and paid operation.

## Review Focus

- Simultaneous requests contending for the last slot: only one provider attempt.
- App timeout followed by a repeated request: no second attempt for the same request ID.
- Worker restart after reservation: reservation and idempotency survive.
- Copied/revoked device credential: revoked credential fails; shared global cap limits unrevoked abuse.
- Edited title/brand or changed contract: old cached answer cannot be applied/reused.

### Task1: Real-runtime service and reservation contract

Files: create services/ai-beta/src/worker.js, services/ai-beta/src/budget-object.js, services/ai-beta/src/gemini.js, services/ai-beta/wrangler.jsonc, services/ai-beta/package.json, services/ai-beta/test/service.test.js, .github/workflows/ai-beta-service.yml.

Interfaces: POST /v1/classify accepts {request_id,records:[{listing_id,title,brand}]}; returns {status,request_id,model,contract,records,budget:{month,calls_reserved,reserved_eur}}. GET /v1/status requires device auth and returns enabled/budget only. Every successful proposal has apply_authorized=false, language=UNKNOWN, bgg_verified=false.
- [ ] Write real local-runtime tests with intercepted Gemini HTTP: unauthenticated/revoked ->0attempts; 9records/oversized/invalid IDs ->0attempts; seeded99 attempts plus2concurrent requests ->1attempt,100reserved; same requestID/different payload ->409; duplicate inflight ->0additional attempts; cache repeat ->0additional attempts.
- [ ] Run test command npm test in services/ai-beta and observe the missing behavior fail.
- [ ] Implement Worker validation/device digest auth and a single object named global-ai-beta-v1. In a synchronous storage transaction reserve and persist REQUEST_UNKNOWN before network. Use integer euro micro-units, server UTC month, no floating monetary arithmetic. Never fetch inside transaction. Failed persistence blocks transport.
- [ ] Implement fixed Gemini payload/validation, no tools/search, abort30s; failed/invalid/timeout keeps reservation. 429/503 circuit opens until admin intervention. Completed valid answer is private to the requesting device; content key binds model/contract/content. Cache retention7days; request/budget history retained current+previousmonth.
- [ ] Test restart after reservation, malformed/corrupt storage, disabled/missing free-tier admin attestation, failure/error bodies/log secret leakage; assert zero automatic retries and no apply permission. Run local suite and CI, review diff, commit.

### Task2: Safe bootstrap, device enrollment and counter transfer

Files: create services/ai-beta/scripts/bootstrap.mjs, services/ai-beta/README.md; modify .github/workflows/classification-ai-benchmark.yml only to enforce old-route disablement before live cutover.

Interfaces: bootstrap consumes a locally fetched existing ledger and emits a reviewed seed manifest; privileged initialization via Cloudflare admin tooling, never client endpoint. It may only seed an empty service ledger or recognize the exact already-imported digest.
- [ ] Write tests: source8calls/.08 ->seed8/.08; second identical import ->noincrement; changed/reduced seed, unknown ledger shape or existing conflicting state ->reject. Missing seeded marker ->0Gemini attempts.
- [ ] Run RED, implement import with source digest and month retained; no decrement/reset command. Generate per-device cryptographically random token, store only digest server-side, deliver token outside chat/repo/build logs. Record revocation procedure.
- [ ] Verify service deployment targets WorkersFree with SQLite namespace and enabled=false; check Cloudflare/Gemini actual account configuration. Disable old Actions benchmark before live route enabled. No credentials entered or transmitted without the correct secure channel.
- [ ] Run tests/CI and commit. If account access is unavailable, preserve implementation and report the exact deployment blocker; do not claim activation.

### Task3: Android manual test consumer

Files: create app/src/main/java/it/vintedaffari/app/AiBetaClient.java, AiBetaSettings.java, AiBetaTestDialog.java and relevant tests; modify MainActivity.java only for an existing-settings entry. Re-read latest beta/settings patterns before edit because frontend shares MainActivity.
Interfaces: AiBetaClient submits one persisted requestID and captures typed service statuses on a background executor; AiBetaSettings holds HTTPS endpoint and per-device token encrypted with Keystore, excluded from backup/export; AiBetaTestDialog displays proposals and source title/brand.
- [ ] Tests: request repeated after timeout keepsID; changed inputs use newID; HTTPS-only endpoint; disabled/authfailed/budgetblocked displayed without provider retry; cancelled/lifecycle change never updates detached UI.
- [ ] Run RED, implement defaultOFF settings and manual button Test AI su8annunci. Use exactly8record controlled fixture from repository for first phone test; actual catalog selection is subsequent scope. No production classifier/store writes. Show category/evidence and explicit proposal status; never reinterpret ACCESSORY_COMPONENT as a local subtype.
- [ ] Verify token absent from logs/export/APK constants, settings/proposals survive navigation, repeated request safe, title/brand edits cannot reuse old answer. Run Android regressions/unit/compile and real Activity render at font100/200%; commit.

### Task4: Verified delivery and first provider trial

Files: update STATE.md and existing backend spec at checkpoint; version/release files only following current Android release workflow.
- [ ] Rebase task branch on current beta; complete service+Android CI and diff/security review before merge.
- [ ] Merge verified branch; build/sign and confirm both Firebase upload and tester distribution. Do not claim phone acceptance from CI.
- [ ] Verify FreeTier account/project and device enrollment; service remainsOFF until those checks pass. Enable one manual trial of8records, no broader batch. Confirm one durable reservation, one transport, proposal display, and repeated request using cache/idempotency.
- [ ] If original8reservations unchanged, successful trial records9total/.09reserved. If ledger has changed, import exact fresh totals and recompute headroom instead of assuming92.
- [ ] Record run/build/release, real result, limit/account caveats and phone checks. No claim of verified identity/language or real invoice measurement.
