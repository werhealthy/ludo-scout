# Ludo Scout Android

Integration and Pixel test builds use `beta`; see `STATE.md` for the current branch and build baseline.

Ludo Scout observes Vinted UI through Android Accessibility while the user scrolls normally, performs local/BGG classification and conservatively resolves exact Vinted listing identity and metadata. The project intentionally avoids anti-bot bypasses and prioritizes local filtering, batching, caching and request reduction.

## For AI-assisted development
GitHub is the source of truth. Before changing code, read `AGENTS.md`, `PROJECT.md`, `STATE.md`, and the relevant sections of `AI_HANDOFF.md`.

## Local secrets
Do not commit `secrets.properties` or signing keys. Preferred local configuration:

`%USERPROFILE%\\.gradle\\gradle.properties`

with:

`BGG_TOKEN=...`

See `GITHUB_SETUP.md` for the one-time migration workflow.

## Current technical notes
See `AI_HANDOFF.md`, `CHANGELOG.md`, and `app/build.gradle`. Historical `BUILD_STATUS_*` files describe earlier snapshots.
