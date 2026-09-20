# Ludo Scout — Changelog

## 5.12.3 — Engine correctness
- Dedicated Motore run inspector instead of redirecting run details to Catalogo.
- Active-run scoped Vinted/BGG ordinary processing; newer runs wait.
- Zero-network snapshot batch may continue during HTTP pacing/cooldown.
- Stronger ready/deal/Hunt identity gates.
- Persistent BGG/Vinted review behavior.
- Variant-pending cases that cannot progress automatically become review.
- Thumbnail/UI bitmap memory-pressure safeguards.
- `engineRun` diagnostics.

## Git workflow migration preparation
- Removed tracked/local `secrets.properties` from the Git-ready copy.
- Added `secrets.properties.example` only as documentation.
- `BGG_TOKEN` can now come from Gradle user properties, CI environment, or legacy local file, in that order.
- Expanded `.gitignore` for secrets, signing material, build products and local IDE state.
- Added `AI_HANDOFF.md` as cross-chat technical source of context.
