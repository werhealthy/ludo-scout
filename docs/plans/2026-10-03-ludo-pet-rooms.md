# Ludo pet rooms integration

Goal: implement the user's supplied three-room design in the existing native Android area, from beta 03ea4e48, on frontend/ludo-pet-rooms.

Architecture: retain LudoRoomState/PullRefreshScrollView, library renderer and preference ownership. A layered room View renders cached environment art under a separate LudoPetView; native controls remain outside bitmap assets. Native measured orbit nodes preserve the daily journey and delta destinations; intake and current work remain distinct scopes. No dependencies, schema, filters or services change.

Spec: latest explicit user brief in this conversation; preserve docs/specs/2026-10-03-ludo-exploration.md data semantics. Existing monthly metrics remain monthly, never relabel as weekly without actual weekly data.

- [x] Add failing resource/geometry checks for room alpha and measured orbit bounds, including 320–393dp and font200% fallback.
- [x] Export 8 WebP resources from supplied generated PNG assets, retaining alpha; add room background rendering with cache/executor guards and lifecycle-controlled actor.
- [x] MainActivity: remove top tabs/search, reuse swipe and persistent positions, add accessible page indicators and lateral arrows; native orbit then 2 monthly metrics and Vinted CTA; favourites remain existing cards and Library retains search/add/menu/shelves.
- [x] Execute relevant regression suite, CI compile/unit/build, independent code review, align latest beta, merge verified PR and confirm signed Firebase upload and tester distribution.

Review focus: small width/large type labels; stale async art after navigation; favourites updates while restoring scroll; UI actions and scopes; animation pause/reduced motion. Phone pixel/gesture/visual acceptance remains separate from CI.
