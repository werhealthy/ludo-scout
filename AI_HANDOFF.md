# Ludo Scout — AI handoff

## Source of truth
The private GitHub repository `werhealthy/ludo-scout` is now the source of truth.
Do not reconstruct the project from an older ZIP when the repository is available.

## Current baseline
- App: Ludo Scout Android
- Package / applicationId: `it.vintedaffari.app`
- Baseline version: `5.12.5-bgg-identity-provenance`
- versionCode: `119`
- compileSdk / targetSdk: 35
- minSdk: 28
- Java: 17
- Gradle wrapper: 8.9
- Android Gradle Plugin: 8.7.3
- Current development stage: correctness turnaround first; BGG identity provenance containment is active, historical revalidation and identity-model hardening follow before throughput/UX redesign.

## Git workflow
- Repository: `werhealthy/ludo-scout` (private)
- `main`: user-verified stable baseline.
- `beta`: integration branch used for test builds delivered to the Pixel.
- Significant work should start from `beta` on `work/<task-name>`.
- Do not overwrite another chat's active work branch.
- Every significant change must update this file and `CHANGELOG.md`.
- CI workflow: `.github/workflows/android-beta.yml`.
- `Android beta` runs on pushes to `beta` and can also be triggered manually (`workflow_dispatch`). It builds the signed debug APK, verifies the preserved signing certificate, uploads the APK artifact and distributes it through Firebase App Distribution.
- CI versionCode remains `1,000,000 + github.run_number`; do not change the signing key, package/applicationId or versionCode strategy without explicit user approval.

## Current engine invariants
- Vinted Accessibility observation remains the core UX: user scrolls Vinted normally.
- No anti-bot bypass, CAPTCHA bypass, cookie stealing, private abusive API use or artificial request-rate increase.
- Prefer local filtering, caching, batching and snapshot reuse before public-page requests.
- BGG rating below 6 is filtered before ordinary Vinted linking where possible.
- Exact Vinted identity and exact BGG identity are separate facts.
- One ordinary scroll/run owns the automatic Vinted/BGG backlog lane at a time.
- Newer ordinary scrolls are captured but wait while the older run is active.
- LIVE_DEAL, HUNT_PRIORITY and explicit manual work can preempt ordinary backlog work.
- Manual review is persistent: opening/exploring a case must not dismiss it. It disappears only after explicit resolution/archive/removal or a successful authoritative automatic resolution.
- A ready card requires confirmed BGG, exact Vinted item/url, complete normal enrichment, no open review and no open automatic job.
- Publication date, language and price should be retained when technically available; do not silently drop required data just to make a card look complete.

## 5.12.3 correctness changes
- Motore run detail uses a dedicated observations -> market_listings -> games view rather than Catalogo.
- Run inspector includes incomplete cards and shows their actual stage.
- Zero-network snapshot batch is scoped to the active run and may operate during HTTP pacing/cooldown.
- Ordinary BGG/Vinted work from later scrolls waits for the active run; manual/Hunt remain priority exceptions.
- Deal/Hunt notifications require exact/strong identity and reject accessories, components, bundles, non-games and provisional BGG variants.
- Unresolvable `BGG_VARIANT_PENDING` is promoted to persistent review.
- Thumbnail capture/cache memory pressure protections are enabled.
- Diagnostics include `engineRun={...}`.

## 5.12.4 performance/stability changes
- Motore run inspector is paginated at 24 rows per page instead of inflating up to 220 thumbnail-heavy cards at once.
- Run-list rendering no longer performs per-row Deal/MarketListing lookups just to obtain a thumbnail.
- Motore run subpages are not force-rebuilt every few seconds; queue-driven refresh is coalesced and slower while a run inspector is open.
- OperationCenter/deal reconciliation is single-flight + coalesced instead of allowing repeated 1,200-row scans to queue.
- The old Test-1 deep Accessibility identity diagnostic probe is retired from production observation. Production keeps only a small explicit `/items/<digits>` opportunistic check and never requests Compose extra-data for this purpose.
- Accessibility event diagnostics are batched rather than writing SharedPreferences on every event.
- Screenshot capture is single-flight; remote thumbnail decoding uses RGB_565 and skips under low heap headroom.
- Active-run lookup has a short cache and waiting-run counting avoids recomputing full BGG/Vinted status for every later session.
- UI bitmap cache reduced to 4 MiB.
- Static regression guard: `regression/performance_stability_v5124.py`; Android beta CI runs it before the Android build.
- No database/schema migration in 5.12.4.
- Remaining open performance question: current request efficiency is much better than the historical resolver (~6 physical link requests per newly linked row in the latest field diagnostic), but active-run completion time still needs fresh Pixel measurement after these CPU/memory fixes. Do not increase Vinted request rate; future throughput work must reduce requests via shared family discovery/snapshot reuse/fewer fallbacks.

## 5.12.5 BGG identity provenance firewall
- Automatic zero-network BGG memory reuse now trusts only authoritative BGG metadata/aliases, the canonical game name, an auto-selected canonical BGG name, or an explicit manual BGG choice.
- Seller-authored Vinted titles (`VINTED`, `VINTED_VARIANT`) remain stored as useful observational/search evidence but can no longer, by themselves, bootstrap a future automatic BGG identity at 99.5 confidence.
- BGG match algorithm version is `4`, so older unresolved review cases may be retried under the stricter provenance rule.
- Existing `MATCHED` identities are intentionally not rewritten or deleted by this task. Diagnostics now expose `bggIdentityTrust={... matchedToRevalidate=...}` so the next correctness task can target historical revalidation without an indiscriminate reset.
- Static regression guard: `regression/bgg_identity_provenance_v5125.py`; Android beta CI runs it before the Android build.
- No database/schema migration, no signing/applicationId/versionCode-strategy change, and no request-rate change.

## Known UX direction
The next major phase is a full Motore redesign. Avoid treating all information as equal cards.
The intended hierarchy is run-oriented:
1. current/active scroll job;
2. explicit pipeline stages and current stage;
3. ready results / unresolved review;
4. newer runs waiting;
5. history grouped by day, with individual scrolls only inside a day.
Use concrete language (`card Vinted osservate`, `giochi trovati`, `BGG riconosciuto`, `Vinted collegato`, `card pronta`) rather than ambiguous labels such as `gioco valido`.

## Secrets
Never commit secrets, signing keys or local SDK paths.
- `secrets.properties` is ignored and should not normally be needed.
- Local BGG token should live in `%USERPROFILE%\\.gradle\\gradle.properties` as `BGG_TOKEN=...`.
- CI expects GitHub Actions secret `BGG_TOKEN`.
- CI expects GitHub Actions secret `ANDROID_DEBUG_KEYSTORE_BASE64`, containing the existing developer-machine `%USERPROFILE%\\.android\\debug.keystore` encoded as Base64.
- Signing material must stay outside Git.

## Android signing warning
The baseline has no custom `signingConfig`; Android Studio debug builds therefore normally use the developer machine's debug keystore (`%USERPROFILE%\\.android\\debug.keystore`).
The GitHub workflow restores that exact keystore to `$HOME/.android/debug.keystore` before `assembleDebug`.
Do not replace it with a newly generated key: the first cloud APK must update the existing Pixel installation without uninstalling or losing local app data.

## Required completion note after every task
State explicitly:
- files changed;
- behavior changed;
- database/schema migration, if any;
- automated checks run and results;
- one simple manual test for the user, including exact pass/fail criteria;
- remaining risks/open questions.
