## Delivery preference — 2026-09-30

User explicitly authorizes publishing each Ludo Scout update immediately to Firebase App Tester after automated checks pass, including the necessary beta PR merge. Do not wait for another merge/distribution request. Confirm actual Firebase distribution success before claiming availability. Manual visual feedback follows installation.

PR93 merged to beta at 3f8370154fe82be50df80dfb6536cbf06b70aec7; source and final PR-head Android CI passed. Signed beta build and Firebase delivery are underway.

## UI milestone 5.12.85 — 2026-09-30

Home cards/categories enlarged; tonal category outlines; hero adapts to loaded BGG cover aspect ratio, discount over artwork, compact CTA. Explicit edition/text-dependence labels preserve unknown data. Catalog uses two-column shared product cards and pagination. No database migration.

Base: PR92 merged to beta at 9f9e38739b65c2a5fe1e6344977ec04db717263f. Android/Java checks passed in PR CI; manual Android layout checks required (square/wide/tall covers, large font, narrow phone, pagination). Engine and remaining pages are outstanding: current engine counters overlap and count listings; do not sum them or label them unique games. Define exclusive phase membership before implementing reference.

# Ludo Scout — Current UI work

Updated 2026-09-30. GitHub `beta` remains the source of truth.

## Active goal
Apply the user-approved official dark Home reference, CSS and eight supplied category images.

## Work prepared
- Baseline: `beta` commit `329e66635033aef3bac71a947a16df188f04a502` (5.12.83).
- PR: https://github.com/werhealthy/ludo-scout/pull/92 (open, not merged).
- Verified code commit: `d603bb1476f4a184e02ab46595b668f2896ac999`.
- Proposed source build: 5.12.84-official-dark-home, on `ui/official-dark-home-20260930`.
- Navy radial Home; purple live-data hero with CTA/settings; eight mapped categories; offers/latest rails; vertical BGG ranking; Home/Catalogo/Ludo/Libreria navigation.
- Category SQL is read-only and retains existing verified/visible gates. No migration or matcher/queue/pricing/publication change.
- `UX_SYSTEM_V1.md` and `AI_HANDOFF.md` record the updated UI contract.

## Verification
- GitHub Android PR validation run 248 / 36734569866 passed on code commit `d603bb1476f4a184e02ab46595b668f2896ac999`: complete current regressions, 8 production SQLite category fixtures, `:app:testDebugUnitTest` (including new category tests), and `:app:compileDebugJavaWithJavac`. Logs show both Gradle tasks BUILD SUCCESSFUL.
- 61 current Python regressions also passed locally. Two JVM-dependent fixtures were verified in CI because the local JDK is incomplete. Local Android build unavailable; CI compiled the complete repository.
- The first CI attempt stopped on a hard-coded old version name in the liveness guard. Updated that test to permit subsequent 5.12 beta releases while retaining the CI versionCode strategy check. No queue code changed.
- Read-only code review completed; fixed candidate truncation before BGG ordering and enlarged settings/hero CTA touch targets.
- No emulator/Pixel visual validation performed. Check narrow width and large fonts, missing covers, long game names, all category filters, section links and settings.
- Historical `ux_phase1_v5120.py` was attempted but fails obsolete version/navigation expectations on the current baseline; it is not in current CI. `ux_phase2_v5121.py` could not run in the initial partial checkout and is not in current CI.

## One next step
Once PR checks pass, integrate this UI change into beta and verify the resulting APK on Pixel against the approved reference.
