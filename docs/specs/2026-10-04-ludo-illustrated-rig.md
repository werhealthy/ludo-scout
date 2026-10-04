# Ludo — raster rig integration

User approved cut-out atlas creation and explicitly requested implementation on2026-10-04. Continue native Java/Canvas pipeline from PR238; no dependencies/services/room changes.

Plan:1) Export exact isolated atlas pixels as transparent WebP and coordinate rig, inspect recomposition; add failing pose tests for blink/tap-arm.2) Load complete cached rig off UI thread for persistent room mascot only, retain canonical fallback and inline faces; animate parent head, eyes/spirals and arms from same time-based pose; lifecycle/reduced-motion gates unchanged.3) Run full CI and real Android pixels/layout100/200%/motion, inspect assembled screenshots, fresh final review, merge and signed Firebase delivery under existing authorization. Update STATE and existing UI backlog; frontend7/backend6 unchanged.

Atlas is derived illustration, not original layered PSD. Designer source consists of exported raster pieces with exact source crop and artboard positions recorded in rig JSON; no mesh/IK/3D. Pupils stay clipped inside eye bounds; blink squeezes eyes about their own centers. One cached rig perActivity, no per-frame decode or object creation. Only room actor uses layered art; existing inline portrait remains canonical. No icon/cameo package rollout or new destination in this task.
