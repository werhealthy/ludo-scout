# Ludo visual universe — 5.12.157

Source: user supplied ludo_asset_pack_v1.zip, received 2026-10-03. All art is RGB on paper, not transparent sprites. WebP exports retain the source art at a bounded decode size. No generated or invented replacements.

## Audit and migration

The beta source has 38 legacy Ludo illustration files, including green goblin context art, pink/purple vector blob fallback, old room and launcher variants. MainActivity, PullRefreshScrollView and launcher/search-animation XML reference those illustration resources. The vector fallback in LudoPetView is removed as well. Animation XML with two old frames is removed. All obsolete illustration files and the generic old ic_launcher_foreground.png are deleted after reference migration. Existing anim/ludo_sheet_* resources are transitions, not mascot art, and are retained.

New resources: idle/hello/search/no_results/sleeping/reward/explorer plus room/tavern and app_icon. hunt_map is byte-identical to room and is intentionally deduplicated. Room/tavern are separate scenic panels in Libreria/Cacce; controls are outside the art. Paper character plates use neutral parchment surfaces. Empty states, Hunt setup and welcome/loading use contextual characters. Game covers keep real BGG art; unavailable covers use a neutral book glyph. Dense bundle cards lose decorative scenic backgrounds.

Launcher: legacy and adaptive round/square wrappers all resolve the new ludo_app_icon. Old source/icon crops are removed.

## Motore cause and change

Journey and standalone pipeline both used fixed 100dp circular children positioned using display width inside a padded container. Their fixed height cannot grow with wrapped phase names, counts and delta labels. The central portrait also drew the old vector blob instead of loading supplied art. The new composition has a paper actor above five natural-height phase rows, circular/pill count badges and separate real deltas. At fontScale >1.3 labels and values stack. No absolute x/y wheel placement, screen-derived height or nested scrolling. Existing root insets/scroll, data snapshot, counts, phase destinations and history remain.

## Pet

LudoPetMood and LudoArt centralize six states. Existing lifecycle/focus/overlay/reduced-motion gating is retained; actor bitmaps use the existing Activity scene cache and gallery executor. No bitmap decode/allocation in drawing. Tap shows greeting temporarily; breath and small directional tilt use the current animator. Search/finished/paused reflect real journey status. Confusion is mapped and available for later interactive flows. Dedicated eyelid blink frames are not present in this pack; no artificial painted eyes are added.

## Acceptance

Automated resource audit, existing frontend/JVM/SQL regressions and Android compile run before delivery. Phone checks still required: 320–393dp, font 200%, paper crops/launcher mask, Home→Ludo room retention, counts/delta drill-down, paused/finished states, tap and animation disabled. CI is not visual approval. Backend5/frontend7 groups remain open pending phone acceptance.
