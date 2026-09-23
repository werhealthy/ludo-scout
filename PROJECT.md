# Ludo Scout

## What it is
Ludo Scout is an Android app for finding useful used board-game opportunities on Vinted. It observes the Vinted interface through Android Accessibility while the user browses normally, then locally analyzes listings, enriches game identity and market context with BoardGameGeek (BGG), and presents verified results.

## Product intent
Turn an ordinary Vinted scroll into trustworthy, understandable board-game discovery. Identity confidence and useful output matter more than publishing quickly or maximizing listing volume.

## People and platforms
- Primary user: a board-game shopper browsing Vinted on Android.
- App package: `it.vintedaffari.app`.
- Main user interface runs in process `:ui`; Vinted Accessibility observation runs in `:radar`.
- Current external sources: Vinted's user-visible pages and BGG. See `AI_HANDOFF.md` for approved access, pacing, and product invariants.

## Current technical shape
- Native Android app, single Gradle module (`:app`), Java 17.
- Android Gradle Plugin 8.7.3; Gradle wrapper 8.9; compile/target SDK 35, min SDK 28.
- UI and product logic are primarily in `app/src/main/java/it/vintedaffari/app`; regression scripts live in `regression/`.
- CI validation and beta distribution are defined in `.github/workflows/android-pr.yml` and `.github/workflows/android-beta.yml`.
- The branch used for day-to-day integration and Pixel test builds is `beta`; `main` is the user-verified stable baseline.

## Preserve
- Normal user-driven Vinted browsing; no bypasses or increased request rates.
- Exact Vinted listing identity and distinct, verified BGG identity.
- Conservative deal, publication, and notification gates.
- Durable queue work and truthful progress; time alone cannot classify, hide, discard, or complete a listing.
- Existing package/application ID, signing key, CI version-code strategy, and local secret handling.

The detailed, versioned engine and product rules in `AI_HANDOFF.md` are authoritative when they are more specific than this summary.

## UX direction
The interface should explain itself through familiar shapes, placement, and behavior. Keep listing details focused on the listing; use direct transitions to game detail; keep filters in focused drill-down screens. See `UX_SYSTEM_V3_INVISIBLE_INTERACTION.md`.

## Decision status
The repository records the product and engine constraints above. New product scope, major UX direction, architectural changes, schema changes, sensitive access changes, important dependencies, and production behavior remain proposals until Francesco approves them.
