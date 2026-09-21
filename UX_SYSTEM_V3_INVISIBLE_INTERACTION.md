# Ludo Scout UX System v3 — Invisible Interaction

UX v3 keeps the connected product graph from v2, but removes explanatory UI that should be learned through familiar interaction patterns.

## Principle
The interface should explain itself by shape, placement and behavior before it explains itself with copy.

Do not add instructional labels for established components. Examples:
- tags look and behave like tags; do not add "Esplora per tag"
- a chevron row means drill-down; do not show all filter controls on the parent screen
- a provider row carries provider identity; avoid duplicating it as a separate CTA and label
- a contextual icon can expose a secondary destination without repeating the entity name

## Listing detail
The listing is a compact commercial view, not a second game encyclopedia.

It contains:
- gallery
- deal signal and recency
- title
- Ludo Score, language and BGG as separate compact elements
- one horizontal strip of lightweight tags
- price / discount / offer
- one quiet Vinted provider row
- only listing-specific facts and seller/bundle context

It must NOT contain:
- a duplicated "Scheda gioco + game name" card
- market history or market-comparison blocks
- "other listings of this game"
- a permanent Library CTA
- an explanatory "Explore tags" section label

## Game transition
Game detail is a direct content destination.

Entry points:
- compact game icon in listing title area
- pull-beyond-end gesture from listing detail

The transition must not route through the Games catalog. Show the destination shell immediately and load game data on a dedicated UI-data executor.

## Tags
Tags are compact, horizontally scrollable, and visually token-like.
Use lightweight symbols only when they improve scanning.
Tapping a semantic tag opens the Games database filtered by that concept.

## Filters
Follow the marketplace drill-down pattern:
- parent screen = rows
- each row = label + current value + chevron
- tapping a row opens a focused child screen
- only the child screen contains choices
- top-level screen has Clear / Show results
- advanced toggles live behind "Other filters"

Never place all chips, checkboxes and inputs on the same filter screen.

## Insets and spacing
Full-screen utility views must respect system bars at the component level.
Do not solve status-bar collisions with per-screen magic margins.

## Performance as UX
Navigation latency is a design failure when it exposes intermediate screens.
UI-triggered database reads should not wait behind maintenance queues.
