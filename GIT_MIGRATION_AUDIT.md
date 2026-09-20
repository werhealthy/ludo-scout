# Git migration audit — baseline 5.12.3

## Baseline inspected
- `applicationId`: `it.vintedaffari.app`
- `versionCode`: 118
- `versionName`: `5.12.3-engine-correctness`
- Gradle wrapper: 8.9
- Android Gradle Plugin: 8.7.3
- Java: 17

## Secret audit
The uploaded `secrets.properties` contained only the placeholder `PASTE_YOUR_BGG_TOKEN_HERE`, not a real BGG token.
The Git-ready copy removes `secrets.properties` entirely and keeps only `secrets.properties.example`.

`app/build.gradle` now resolves BGG token in this order:
1. Gradle project/user property `BGG_TOKEN` (recommended for local Windows builds),
2. environment variable `BGG_TOKEN` (intended for CI),
3. legacy untracked `secrets.properties` fallback.

## Signing audit
There is no custom Android `signingConfig` in the baseline project. Normal Android Studio debug runs therefore use the developer machine's debug keystore, normally:
`%USERPROFILE%\\.android\\debug.keystore`

Before GitHub-generated APKs are installed over the existing phone app, this certificate must be preserved and reused by CI. Do not generate a replacement signing key yet.

## Repository safety
`.gitignore` excludes:
- `secrets.properties`
- `local.properties`
- Android Studio/Gradle local state
- build output/APKs/AABs
- common signing key formats (`*.jks`, `*.keystore`, etc.)

The Gradle wrapper JAR is intentionally kept in Git.

## Regression-note
Historical regression scripts in this repository intentionally target older version labels/build implementations and some fail against 5.12.3 due to stale assertions. This migration did not rewrite those historical tests because the purpose of this copy is workflow migration, not engine behavior modification. `BUILD_STATUS_5.12.3.md` remains the baseline correctness record.
