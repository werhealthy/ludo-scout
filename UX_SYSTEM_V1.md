# Ludo Scout UX System v1

This document is the visual and interaction contract for the Android UI. New UI work should extend these rules instead of inventing new local patterns.

## Product character
Ludo Scout should feel **quiet, premium, data-smart**: calm enough to scan quickly, dense only where the user expects utility, and explicit about why something matters.

## Information hierarchy
Each screen has one job:
- **Scopri** = editorial attention. Surface what deserves attention now.
- **Mercato** = utility and comparison. Dense, sortable, filterable.
- **Dettaglio annuncio** = inspect one listing and take one primary action.
- **Libreria** = personal collection and ownership history.
- **Ludo** = personalized interpretation and intent.

Do not reuse the same card grammar across all of these screens.

## Visual hierarchy
Use hierarchy before decoration:
1. Screen / section title
2. Primary content
3. Primary action
4. Secondary metadata
5. Contextual / corrective actions

Avoid giving metadata the same visual weight as price, title, or primary CTA.

## Color semantics
- **Lime**: primary CTA, strong positive commercial signal.
- **Cyan**: links, navigation, neutral system affordances.
- **Orange**: attention / warning only.
- **Red**: destructive or removal action only.
- **Pink / Purple**: rare product-specific accent, not generic chrome.
- **Muted gray**: metadata.

A normal card should not require more than one accent color.

## Component semantics
### Primary button
Exactly one per major content block when possible. Filled, obvious, action-oriented.

### Secondary action
Quiet surface or text action. Never visually compete with primary CTA.

### Filter chip
Represents active state or a removable filter. It is not a generic badge.

### Status
Plain text or subtle metadata unless urgent. Never styled like a button.

### Context / corrective actions
Use a trailing ellipsis or menu entry. Examples:
- Mark listing sold
- Correct linked game
- Hide listing
- Refresh missing data

These actions must not occupy permanent space in the main content hierarchy.

## Screen patterns

### Scopri
Use mixed module types so the page has rhythm:
- one hero
- compact urgent list
- visual opportunity rail
- ranked list
- discount mini-cards
- optional bundle spotlight
- recent timeline

Do not render every section as the same horizontal carousel.

### Mercato
Utility-first:
- search
- Filter + Sort controls
- active filter chips only
- compact list rows with dividers
- no large decision badges on artwork

The list row contains only:
- cover
- title
- BGG / language / age
- price + discount
- one concise deal signal
- contextual actions

### Listing detail
Toolbar:
- back
- title
- ellipsis menu

Content:
- gallery
- concise deal signal
- title
- metadata
- price
- exactly one primary CTA: open/link Vinted
- secondary BoardGameGeek / Library actions
- optional quiet data-quality banner
- progressive disclosure for details

Corrective actions live in the ellipsis menu, including “mark as sold”.

## Typography
Prefer a short scale:
- 29–32sp: page / detail title
- 20–22sp: section title
- 16–18sp: card title
- 13–14sp: body / action
- 11–12sp: metadata

Use bold sparingly. Size and spacing should create hierarchy before font weight.

## Spacing
Base spacing scale:
4 / 8 / 12 / 16 / 24 / 32dp

Default horizontal screen margin: 16–20dp.
Section spacing should be larger than spacing inside a component.

## Review checklist
Before merging UI changes:
- Does this screen still have one obvious primary task?
- Is a status being mistaken for an action?
- Is an action being mistaken for a chip?
- Are more than two accents competing in one component?
- Is the same card type being reused only because it already exists?
- Can any metadata move behind progressive disclosure?
- Is a corrective/manual action cluttering the main hierarchy?
