# Project state

Updated: 2026-09-23

## Current baseline
- Repository: `werhealthy/ludo-scout` (private); GitHub is canonical.
- Integration branch `beta`: 5.12.51, commit `45f6bf2d398868179ef4f7386b6c4b7f81b25a15` (2026-09-23).
- Stable branch `main`: 5.12.41, commit `94f8407af4418ec244175297a17e52e037e49fdc` (2026-09-21).
- The beta handoff says the 5.12.51 fairness and catalog-funnel changes still need fresh Pixel validation after installing the build and scrolling Vinted. Older root summaries such as `README.md` and `BUILD_STATUS.md` contain stale 5.12.3/5.11.29 labels; use the branch's `AI_HANDOFF.md`, `CHANGELOG.md`, and `app/build.gradle` for current details.

## Active task
Bootstrap the repository's project context for Vibe Coding before starting a specific issue. No app code has been changed as part of this task. Await Francesco's concrete problem report, then reproduce and trace it before proposing a fix.

## Git
- This documentation bootstrap is being prepared on `codex/vibe-coding-os-bootstrap-20260923`, based on `beta`.
- Do not reuse existing active work branches. Start the next substantial code task from the latest `beta` in its own `work/<task-name>` branch.

## Verification
- Repository metadata, branch heads, project docs, Gradle configuration, manifest, source tree, regressions, and CI workflow definitions were inspected through GitHub.
- No local Android build, regression script, or Pixel/device validation was run in this documentation-only bootstrap.

## Next step
Wait for Francesco to describe the specific issue and its observed behavior.
