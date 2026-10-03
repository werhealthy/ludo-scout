# Ludo 158 — room layers

Implements the user's virtual-pet room direction, replacing framed environment/actor cards in the Ludo area. Source PNGs generated in the current design conversation; no raster UI or text is included in resources. Assets exported to WebP for native Android; backgrounds768px wide, characters640px, props320px. Alpha retained for all three characters and both props; cached ARGB_8888 decoding uses the existing gallery worker.

| Resource | Role |
|---|---|
| ludo_room_engine_background | Small workshop, no cat |
| ludo_engine_character | Thinking/sitting cat |
| ludo_engine_secondary_props | Sorting tray, decorative |
| ludo_room_hunts_background | Keepsake room, no cat |
| ludo_hunts_character | Cat holding letter |
| ludo_hunts_prop | Open keepsake drawer |
| ludo_room_library_background | Domestic wooden shelves, no cat |
| ludo_library_character | Cat resting on shelf |

LudoRoomFrame measures full-width room, separate actor, optional prop, native title/menu and lateral controls. LudoRoomBackdropView fades the scene into BG without a hero frame. Native page indicators are above bottom navigation. Existing swipe/room persistence and Library renderer remain in use. No extension assets09–10 needed: rooms switch as complete panels, not a seamless panorama.

LudoOrbitView measures all native child controls before arranging six peripheral nodes and center. Global intake is labelled coda globale; daily acquired/BGG/linked/verified/eligible counters and signed deltas retain their SQL and direct routes. Current work is the sum of disjoint unfinished scoped phases; when no scope exists, it explicitly counts motor activities instead. Big type/less than280dp available uses natural-height rows. No fake weekly statistics: two existing monthly metrics retain their month scope.

Existing illustrations/icons outside Ludo are preserved. Geometry/asset checks run in regression/ludo_pet_rooms.py and both CI workflows. Phone appearance, swipe, TalkBack and font200% acceptance remain pending; CI is not visual approval.
