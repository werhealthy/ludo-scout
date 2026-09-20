# Ludo Scout Android

Current baseline: **5.12.3-engine-correctness** (`versionCode 118`).

Ludo Scout observes Vinted UI through Android Accessibility while the user scrolls normally, performs local/BGG classification and conservatively resolves exact Vinted listing identity and metadata. The project intentionally avoids anti-bot bypasses and prioritizes local filtering, batching, caching and request reduction.

## For AI-assisted development
Read `AI_HANDOFF.md` before changing code. GitHub becomes the source of truth once the repository is published.

## Local secrets
Do not commit `secrets.properties` or signing keys. Preferred local configuration:

`%USERPROFILE%\\.gradle\\gradle.properties`

with:

`BGG_TOKEN=...`

See `GITHUB_SETUP.md` for the one-time migration workflow.

## Current technical notes
See `BUILD_STATUS_5.12.3.md` and `V5.12.3-ENGINE-CORRECTNESS.md`.
