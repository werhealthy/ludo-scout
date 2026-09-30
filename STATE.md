# Ludo Scout — Current UI work

Updated 2026-09-30. GitHub `beta` remains the source of truth.

## Active goal
Apply the user-approved official dark Home reference, CSS and eight supplied category images.

## Work prepared
- Baseline: `beta` commit `329e66635033aef3bac71a947a16df188f04a502` (5.12.83).
- Proposed source build: 5.12.84-official-dark-home, on `ui/official-dark-home-20260930`.
- Navy radial Home; purple live-data hero with CTA/settings; eight mapped categories; offers/latest rails; vertical BGG ranking; Home/Catalogo/Ludo/Libreria navigation.
- Category SQL is read-only and retains existing verified/visible gates. No migration or matcher/queue/pricing/publication change.
- `UX_SYSTEM_V1.md` and `AI_HANDOFF.md` record the updated UI contract.

## Verification
- Six current local UX regression scripts passed (76 guards total).
- Added JUnit category tests and executable SQLite fixtures using production-generated predicates; Android PR CI will run these and the complete existing regression/unit/Java compile suite.
- Local Android compilation/JUnit execution unavailable: this workspace has no working JDK/Android SDK. GitHub CI is authoritative for compilation.
- No emulator/Pixel visual validation performed. Check narrow width and large fonts, missing covers, long game names, all category filters, section links and settings.
- Historical `ux_phase1_v5120.py` was also attempted but fails obsolete version/navigation expectations on the current baseline; it is not in current CI. `ux_phase2_v5121.py` could not run in the partial local checkout and is not in current CI.

## One next step
Once PR checks pass, integrate this UI change into beta and verify the resulting APK on Pixel against the approved reference.
