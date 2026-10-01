# Game scene assets — 5.12.110
Generated specifically for the app with imagegen on 2026-10-01, exported as WebP for Android. Full original illustrations are retained in the generation output; these production exports preserve the artwork. No new renderer/dependency or per-frame image processing.

- `drawable-nodpi/game_scene_floor.webp` (768 square): dark charcoal/plum physical studio wall and slate floor, directional overhead light, visible horizon, no products, UI, glow or pedestal. Reused behind Home featured/preview artwork.
- `drawable-nodpi/game_scene_plinth.webp` (1086x362, real alpha): matte dark violet stone cylindrical plinth and soft black contact shadow, transparent surroundings, no neon/glow. Home featured and Ludo use this base; existing detail PNG remains available.
- `drawable-nodpi/ludo_room.webp` (768x1152): illustrated plum board-game nook with shelves at edges, arch, warm lamp and visible floor; no pet, text or UI. Pet/product/UI remain live independent views. Center-cropped in the scene.

Decode is off the main thread, once per Activity per asset. Missing/failed assets leave the renderer fallback usable; no scene bitmap is allocated while animating. Covers remain actual BGG game artwork, never generated boxes.

Favorites are positive normalized BGG game IDs in dedicated local durable preferences. They do not reuse interest/dismissal preferences, alter recommendations, remove listings or infer ownership. All listing/game heart views subscribe while attached and unregister on detach. Ludo separately remembers its last game; an unavailable recommendation becomes a canonical game view with no stale listing price or offer link.
