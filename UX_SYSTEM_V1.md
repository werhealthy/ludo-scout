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

Discover implementation contract (5.12.84, approved 2026-09-30 reference):
- Navy-to-black radial background with white Helvetica-compatible typography; only Home uses this chrome.
- Greeting and settings, purple featured offer, categories, best offers, BGG top three, latest listings in that order.
- Hero uses the actual game name, BGG rating/description/cover and eligible listing price/discount, with a white “Scopri di più” action. Never copy the mismatched placeholder names/covers from the mockup.
- Eight fixed categories: Strategia, Famiglia, Party, Fantasy, Carte, Sci-Fi, Mistero, Guerra. Supplied transparent artwork is decoded at a bounded sample size and reused. No category counts.
- The shared DiscoverCategories mapping filters whole BGG category labels with OR semantics. Only verified, visible games are returned by Home category navigation; all existing eligibility rules remain in force, including the Children's Game exclusion.
- Best offers/latest are horizontal rails with BGG cover, rating, real price and purple discount; latest additionally shows publication age. BGG is a vertical ranked list over all unique games in the current trusted Home snapshot, labelled with the overall BGG rank.
- Bottom navigation is Home / Catalogo / Ludo / Libreria. “Vedi tutte” opens the existing relevant destination.
- Device validation is required for long titles, missing covers, large fonts and narrow screens.

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



### Home/Catalog refinement 5.12.85
Vertical cards share larger cover-first layout, explicit edition and text-dependence information. Category borders follow their individual tonal color. Featured card uses actual loaded cover ratio: wide cover above copy, square or portrait beside start-aligned copy, discount inside artwork and compact button within 48dp touch area. Catalog uses two columns; pipeline eligibility/filter/sort/pagination remain unchanged. Engine and other pages await subsequent milestones.

## 5.12.86 — Unified UI and five-phase Motore (2026-09-30)
- Home/Catalog cards reserve exactly two title lines with ellipsis, taller covers and a consistent price slot; catalog status reserves two lines too.
- Catalog uses pill search plus circular filters, sort at result count, no floating engine overlay. Filter pages share navy surfaces/Remus typography; edition and text dependence are distinct, include all known edition codes, preserve unknown, reset/apply transactionally, persist category/dependence/discount through recreation.
- Global palette, typography, navigation, shared cards, buttons, sheets and full-screen panels now follow the approved dark system. Ludo has a compact heading and explicit Motore entry; Library and market pages use the same shell. Existing detail/actions remain available.
- Motore presents Osservati → Giochi → Idonei → Verifiche Vinted → Pronti in the reference circle. Large fonts/narrow phones use an accessible ordered list. Phase explanations and ready-announcement drilldown remain accessible.
- Counts are exclusive read-only current-state counts for the active scroll (latest scroll when idle), not cumulative funnel counts or global Catalog totals. Known BGG/game identities group listings at their furthest usable phase; unresolved signatures remain distinct. Center excludes Pronti. Review/holds/archived entries are excluded. Ready requires the same legacy/canonical trust, identity, type, rating, lifecycle and pending-job gates as Catalog; no queue/publication logic changed.
- Attention is the existing actionable inbox (global); recent section shows actual recent scrolls, not fabricated per-game events. Reads run on uiDataIo, snapshot clock starts after all reads, stale data remains visible.
- No DB migration/dependency/signing/pacing changes. Added production-generated SQLite fixture (five phases, duplicate sightings/identities, advanced listing selection, manual/price/identity holds, sold/untrusted/type/identity gates, deep/manual jobs and time scope), wired in PR/beta CI; expanded language filter JUnit cases.
- Verification: 61 local source regressions plus SQLite fixture (--source-eval) passed; Java/Android checks delegated to PR CI due missing local JDK. Independent review addressed filter transaction/reset/restore and ready gate/drilldown mismatches. Visual validation on Android remains manual: 320–393dp, large fonts, long names, catalogue filter cancel/apply/reset/sort/pagination, Ludo→Motore and phase counts.
- User preference persists: automatically merge verified updates to beta and distribute on Firebase App Tester; confirm upload and tester distribution success before availability claims.

## Home component contract — 5.12.115 (2026-10-02)
The approved flat-list direction supersedes earlier 3D Home preview guidance. Home offers/latest/BGG and Catalog use real uncropped front covers; 3D remains in the featured hero and product detail. Existing cover override precedence and favorites listeners remain shared.
- Home list surfaces: navy, 16dp corner, one quiet outline; 12dp internal padding/gutters. Purple studio lighting belongs to the featured scene.
- Offers/latest share media148dp, title16sp/two lines, score14sp plus edition/dependence, price20sp and compact discount12sp on one row. Larger fonts stack metadata/price; price can fit to14sp without truncating currency. Latest adds publication12sp regular/muted, never a button or a bold status banner.
- Ranked BGG list: cover96×112dp, title16sp, score18sp, price16sp, overall BGG rank12sp and the same compact discount. Narrow widths/large fonts retain vertical fallback.
- Discount is a content-width badge with the existing verified band colors, 7dp corners and 8×4dp padding; never a full-width progress bar. Data semantics are unchanged.
- Featured hero: actual48dp menu header; ratio-fitted art capped196dp inline/220dp stacked, centered against copy. Title24sp, BGG/edition, price27sp and favorite, full-width48dp primary action. No blanket48dp top spacer, no art height coupled to the copy height, no discount floating far above the box.
Phone acceptance remains required for card alignment, long titles, unknown data, square/tall/wide covers, large fonts, missing artwork and scroll performance. Product detail redesign is the next thematic group.

## Product hierarchy and BGG podium —5.12.116 (2026-10-02)
Latest approved feedback overrides flat covers for the BGG ranking only. It uses the existing native3D preview renderer; first image116×152dp, second104×128dp, third96×112dp. First place has a stronger tonal scene. Typography, price, favorite48dp and rank semantics stay legible; narrow/large-font layouts stack. Offers/latest/Catalog remain flat.
Featured CTA and listing CTA share primary pill shape/type, ripple, at least48dp target and a Font Awesome18dp icon centered with its label. No independently floating trailing arrow or white rectangular custom button.
Listing hierarchy: toolbar → compact144–208dp3D cover → title26sp → quiet deal/publication signal → price32sp plus content-width discount12sp → primary Vinted action → identified real listing photos → compact BGG/Ludo scores → edition/dependence and correction → icon/value facts → taxonomy/description disclosures and game link. Original offer price, verified shipping, edition, data refresh, bundles, accessories and intentional game pull remain available. Missing facts are explicit, never fabricated.
Game detail reuses compact media, scores and icon/value facts. Taxonomy, creators and description use progressive disclosure; real market prices/history/listings remain intact.
BGG enrichment rebuilds visible product sections, preserves caching/data writes, and checks dialog lifecycle before updating UI. Errors keep prior data and expose quiet status. Cover overrides/favorites retain their existing identity/attach lifecycle.
Device acceptance required: hierarchy/first viewport, long labels and large type, glyph rendering/touch/TalkBack,3D podium proportions, real photo opening, link/game/pull flow and refresh during detail.
