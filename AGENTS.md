# Working rules for Ludo Scout

Before changing code, read `PROJECT.md`, `STATE.md`, and the relevant sections of `AI_HANDOFF.md`. Then inspect the files and tests that own the behavior. Treat GitHub as the source of truth.

## Git workflow
- `main` is the user-verified stable baseline; `beta` is the integration branch used for test builds on the Pixel.
- Start substantial work from the latest `beta` in a new `work/<task-name>` branch. Never reuse or overwrite another active work branch. Keep this bootstrap or documentation work on its dedicated branch until reviewed.
- Use a pull request to integrate meaningful changes. Do not commit directly to `main` or `beta`.
- Keep each change focused on the requested issue. Update `STATE.md` when the active task or verification changes; update `AI_HANDOFF.md` and `CHANGELOG.md` for significant product or technical changes.

## Product and technical constraints
- Keep the core flow: the user scrolls Vinted normally; Ludo Scout observes listings and turns verified board-game opportunities into useful output.
- Preserve exact listing and game identity checks, conservative pricing/trust gates, and the current public-network pacing. Never add anti-bot or CAPTCHA bypass, cookie theft, private abusive API use, or artificial request-rate increases.
- Prefer local filtering, caching, batching and snapshot reuse before adding network work.
- Do not change product scope, major UX direction, architecture, authentication, database schema, signing, package ID, CI versioning, production behavior, or add significant dependencies without Francesco's approval.
- Keep UX changes aligned with `UX_SYSTEM_V3_INVISIBLE_INTERACTION.md` and earlier UX documents only where they do not conflict with current approved behavior.
- Avoid unrelated cleanup and broad rewrites. Follow the existing Java/Android patterns unless evidence shows they are the cause of the issue.

## Verification
- Reproduce the reported behavior where possible, identify its owning path, and make the smallest supported fix.
- Run the focused regression(s) and relevant JUnit tests. For Android changes, run the available Gradle checks; CI also runs the regression suite and Java compilation described in `.github/workflows/android-pr.yml`.
- Report exactly which checks ran, passed, failed, or could not run. For UI changes, include device or emulator checks when available.
- Never treat elapsed time by itself as proof of correctness or completion.
