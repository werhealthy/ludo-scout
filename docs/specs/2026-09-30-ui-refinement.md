# Ludo Scout — UI refinement, five reviewable steps

## Scaletta operativa unica — aggiornata 2026-10-01, 23:11 Europe/Rome

Questo file è la fonte unica per le cose da fare. Leggerlo all'inizio di ogni sessione insieme a `AGENTS.md` e `STATE.md`. Le priorità qui sotto prevalgono sulle vecchie indicazioni di “prossimo step” nella cronologia. Aggiornare questi gruppi senza duplicare i lavori già consegnati.

**Gruppo1 consegnato in5.12.112; prossimo passo: verifica Home sul telefono.** Card BGG più grandi, scatole libere senza riquadro interno anche nelle anteprime offerte/recenti. Build e distribuzione App Tester verificate; accettazione visiva/prestazioni aperte. Dopo questo controllo, il prossimo gruppo funzionale è2 (dettagli/interesse contestuale), con durata/reset/annullamento ancora da definire. Nessuna estensione del renderer a Catalogo/Libreria prima della prova di fluidità.

1. **Home e card: immagini, proporzioni e gerarchia.** Ingrandire le card della classifica BGG e soprattutto le immagini dei giochi. Provare le scatole libere sullo sfondo della card, eliminando il riquadro interno che le contiene; valutare lo stesso trattamento per le altre card, mantenendo il contenitore complessivo, i target touch e la leggibilità. È una direzione da applicare con giudizio, non un obbligo di eliminare ogni bordo dell'app. Unire alla prima priorità già aperta: proporzioni/centratura, sfondo profondo viola metallizzato, base sottile liscia integrata, ombre e riflesso discreto come Ubongo. Evitare pietra, pavimenti fotografici e piedistalli decorativi. Mantenere l'equilibrio del Catalogo senza stravolgere le card; gerarchie specifiche: BGG → voto prominente ma equilibrato; recenti → pubblicazione reale quando nota; offerte → risparmio verificato e qualità. Preservare tag percentuali e icone già corretti. Controllare titoli lunghi, cover quadrate/alte/larghe/mancanti, testo grande e fluidità prima di estendere il renderer a Catalogo/Libreria.
2. **Dettagli e interesse contestuale.** Ridurre prosa e lunghezza, raggruppare i fatti e usare icone leggibili. Spostare Mi interessa/Non mi interessa nei tre puntini, raggiungibili anche nell'evidenza. Non mi interessa nell'evidenza esclude quel candidato solo da quella posizione nel contesto corrente: non cancella il gioco o l'annuncio dal Catalogo e non diventa automaticamente un gusto negativo globale. Definire durata, reset, annullamento e semantica di Mi interessa prima di cambiare la preferenza persistente. Preservare BGG/Vinted, lingua/dipendenza e gesto intenzionale già consegnati.
3. **Motore: numeri, periodo e movimento.** Spiegare il cerchio Giochi a zero con query e dati reali. Separare carico attuale da attività del periodo e annunci da giochi distinti. Valutare giorno/ultime24ore senza inventare contatori o cancellare lo storico; periodo/reset restano da scegliere. Liste coerenti con ogni numero, delta rosso/verde con baseline comprensibile e animazioni più visibili solo durante attività reale, distinguendo attesa/pausa.
4. **Motore: riconoscimento, filtri e stabilità.** Misurare perché entrano solo pochi giochi per run: acquisizione → riconoscimento → identità BGG → idoneità → prezzo/link → Catalogo. Separare duplicati/giochi già noti, scarti, review, retry e blocchi. Valutare recuperi solo dopo evidenze, senza abbassare automaticamente soglie. Indagare anche SQLite lock/crash/ANR, tempi UI, lease/heartbeat e run troppo lunghi.
5. **Verifica trasversale Ludo, Libreria e interazioni.** Ludo: pet/ambiente, spiegazione e pulsanti visibili, interazione, ultimo gioco mantenuto, pausa corretta delle animazioni. Preferiti: salvataggio per gioco BGG sincronizzato su tutti gli annunci, distinto da interesse/possesso. Libreria: rating0–5 senza perdere dati storici, zero/nessun voto, prezzi di vendita reali/vuoti, ripristino e bottom sheet con tastiera/insets. Controlli comuni di contrasto, touch, Back/posizione, testi grandi e animazioni disattivate.

**Già consegnato, da preservare:** navigazione Catalogo Annunci/Giochi e Bundle; tag risparmio/BGG; dettagli/gesto intenzionale; Libreria e vendita; Ludo interattivo, Preferiti e memoria; correzione premium5.12.111. Le verifiche sul telefono rimaste aperte non equivalgono a nuovi job di reimplementazione. I requisiti dettagliati dei cinque gruppi rimangono nelle sezioni consolidate sotto.


## Gruppo 1 — Home e card BGG 5.12.112
PR121: scatole libere sullo sfondo complessivo delle card Home, senza pannello interno. Anteprime BGG112×128dp invece di68×68dp; medaglia nell'area immagine, maggiore respiro verticale e card interamente verticale su schermi<360dp o fontScale>1.3. Offerte/recenti condividono lo stage trasparente. Conservati cover reali/aspect ratio, geometria centrata/fianco sinistro, hero/base/riflesso5.12.111, voto/risparmio/pubblicazione/favoriti/routes. Catalogo/Libreria non estesi prima della prova di fluidità. Nessuna modifica a dati, schema, filtri o dipendenze.
Verifiche:38guard locali esistenti passati; head8edf59de3be565200d48d41389b0cdcd851c88c8 ha superato CI324(36927729015), tutte le regressioni/SQLite, geometria premium/preview, Android unit test, compile Java e APK di revisione. Diff letto:2file di prodotto, nessuna modifica fuori scope. Merge beta807472a1939aaa08ec88beb1b0ee3744880d22fa. Nessuna verifica visiva o misura di frame sul dispositivo.
Build firmata beta129(36928353294), job110591159310: regressioni/test/APK/certificato atteso superati; upload Firebase esplicitamente riuscito2026-10-01 21:27:37UTC e distribuzione tester/gruppi riuscita21:27:38UTC(23:27Europe/Rome), versione5.12.112-free-box-cards(1000129).
Verifica telefono e fluidità pending; preservare hero5.12.111 senza riaprire il redesign. Non considerare gruppo2 approvato nella semantica ancora non definita.

## Correzione prioritaria — riferimento Ubongo, 2026-10-01
Il feedback più recente rifiuta il pavimento fotografico e il piedistallo in pietra di5.12.110. Richiesta: profondità discreta, card viola metallizzata, base liscia integrata, fianco della scatola a sinistra e riflesso leggero come Ubongo. Implementazione5.12.111: gradienti/shader nativi condivisi tra evidenza e anteprime, geometria centrata, riflesso della copertina reale e layout affiancato anche con copertine quadrate su telefoni360dp. Fallback verticale per larghezze ridotte/testo grande. Nessun nuovo fondale illustrato; Ludo/Preferiti/dati e backlog funzionale restano invariati. Verifica visiva sul telefono ancora necessaria.

## Priorità aggiornate — feedback 2026-10-01, 20:43 Europe/Rome
Il gruppo richiesto è stato implementato in5.12.110: nuovi asset/scena Home, ambiente e redesign Ludo, consiglio più leggibile/interattivo, ultimo gioco mantenuto e Preferiti per identità BGG con cuore condiviso tra annunci. Vedere milestone e asset per i limiti verificati. La vecchia restrizione “nessun nuovo redesign Ludo” è superata dalla richiesta esplicita.
- Primo controllo sul telefono: profondità e contatto della base, proporzioni scena/copertine, testo grande, ritorno all'ultimo gioco e cuori su più annunci dello stesso gioco.
- I Preferiti sono un salvataggio del gioco. Non equivalgono a Mi interessa/Non mi interessa e non nascondono giochi o annunci. Le azioni d'interesse contestuali restano il prossimo gruppo funzionale del backlog.
- Motore: numeri del periodo/fasi, resa del riconoscimento e stabilità rimangono gruppi3–4 da affrontare con misure reali; nessuna soglia o dato di mercato modificato in questo aggiornamento.

## Backlog prioritario consolidato — feedback 2026-10-01, 20:09 Europe/Rome

Questo elenco è operativo e sostituisce le precedenti indicazioni di “prossimo step”. Unisce i punti già aperti senza duplicarli. Il feedback di oggi ha precedenza. È un aggiornamento di requisiti, non una dichiarazione di implementazione.

### 1. Home: scena in evidenza e gerarchia delle anteprime
- Rivedere insieme centratura, profondità dello sfondo e integrazione scatola/piedistallo. Il prodotto è ancora poco immerso; controllare il possibile decentramento con screenshot reali.
- Scure ombreggiature sulle facce laterali/superiori; meno luminosità artificiale. Migliorare contatto scatola/base e ombra sotto il piedistallo, evitando effetto sospeso.
- Esplorare una base che emerge dal basso e un passaggio nero/grigio nella card: ipotesi visive da confrontare, non soluzioni già approvate. Conservare Home scura e cover reale.
- Correggere la freccia del CTA usando il pack di icone esistente.
- Usare le card Catalogo come riferimento di equilibrio, con un linguaggio comune e gerarchie diverse per funzione: classifica BGG → voto; arrivi recenti → data/tempo di pubblicazione dell'annuncio; migliori offerte → convenienza verificata e qualità del gioco.
- Verificare che “recente” si riferisca alla pubblicazione quando nota; non sostituirla silenziosamente con data di acquisizione da Ludo. Dati sconosciuti restano espliciti.
- Tag percentuali e voto più equilibrato in classifica BGG sono già consegnati: preservare e rifinire nello stesso sistema, non riaprire un job identico.
- La combinazione qualità/convenienza riguarda presentazione e comprensione; eventuali nuove formule/criteri di ordinamento vanno proposti separatamente prima di cambiare la selezione.

### 2. Dettagli prodotto e azioni di interesse contestuali
- Annuncio/scheda gioco troppo didascalici e lunghi: ridurre prosa, raggruppare fatti, usare icone con significato leggibile e distribuire informazioni nello spazio. Approfondimenti su richiesta, senza nascondere dati essenziali o unknown.
- Spostare Mi interessa / Non mi interessa nel menu dei tre puntini; renderlo raggiungibile anche dall'annuncio in evidenza.
- Nuova regola esplicita: Non mi interessa sull'evidenza rimuove quel candidato da quella posizione nel contesto corrente; NON cancella l'annuncio, NON nasconde il gioco dal Catalogo e NON diventa automaticamente una preferenza negativa globale.
- Distinguere l'esclusione contestuale dall'eventuale gusto persistente; chiarire anche l'effetto di Mi interessa prima di riutilizzare la preferenza globale attuale.
- Durata dell'esclusione (“adesso”), reset e possibilità di annullamento ancora da definire. Nessuna durata arbitraria approvata.
- Preservare accesso BGG/Vinted, lingue/dipendenza dal testo, navigazione e gesto controllato già consegnati; ripresentarli con meno rumore. Accessibilità: non sostituire tutto con icone prive di etichetta/descrizione.

### 3. Motore: numeri comprensibili, andamento e movimento
- Riattivare l'analisi del Motore per questi requisiti; precedente standby non impedisce l'audit richiesto. Il cerchio Giochi percepito sempre a zero va spiegato attraverso query, ambito e liste reali, non riempito artificialmente.
- Distinguere “presenti ora in una fase” da “passati/elaborati nel periodo”: il primo è carico corrente, il secondo è attività storica. Non sommare annunci, giochi distinti e scroll come se fossero la stessa unità.
- Confrontare una vista di attività giornaliera/ultime24ore e una vista del carico attuale. Proposta raccomandata da validare: attività del periodo in primo piano, fasi correnti separate come dettaglio operativo. Giorno civile, finestra mobile e reset NON ancora scelti; lo storico non va cancellato.
- Ogni numero deve avere unità, periodo e lista coerente; mantenere drilldown esatto e contatori veritieri anche per dati incompleti.
- Riprendere il requisito già aperto dei delta: frecce rosso/verde visibili, baseline e durata comprensibili. L'assenza percepita va verificata; confronto “dall'ultima visita” rimane una proposta, non una decisione.
- Rendere più evidente l'animazione dei cerchi durante attività reale, distinguendo lavoro attivo, attesa e pausa; movimento rispettoso delle animazioni disattivate. Includere loading, padding e raggruppamento già aperti, senza duplicare il lavoro consegnato.

### 4. Motore: resa del riconoscimento, filtri e stabilità
- Il basso numero percepito di nuovi giochi per run è un problema da misurare, non prova che tutti i filtri siano troppo severi.
- Ricostruire il percorso: annunci acquisiti → riconosciuti come giochi → identità BGG → qualità/idoneità → prezzo/link verificati → Catalogo. Separare giochi già noti e annunci duplicati dai nuovi ingressi.
- Quantificare scarti, review, dati mancanti, attese/retry e blocchi, con motivi e campioni reali. Distinguere basso ingresso da perdita nel riconoscimento, filtro intenzionale o coda non completata.
- Valutare recuperi/filtri troppo restrittivi solo dopo l'audit; nessun abbassamento automatico di soglie o ammissione di falsi positivi.
- Unire all'indagine i punti di stabilità tuttora aperti: SQLite lock/crash/ANR, tempi UI, lease/heartbeat vecchi, run attivi troppo a lungo e recupero dei job. Le vecchie diagnostiche non descrivono automaticamente lo stato odierno.
- Miglioramenti al layout, join indicizzati e altre correzioni consegnate non sono prova che tutte queste cause siano risolte. Pricing e trust/pubblicazione restano invariati finché non viene approvata una modifica specifica.

### 5. Verifica trasversale di Ludo, Libreria e interazioni
- Ludo5.12.110 aggiorna la scena5.12.108 su richiesta esplicita: ambiente, spiegazione in primo piano, interazioni e Preferiti. Verificare sul telefono proporzioni, controlli/animazioni, persistenza del gioco e cuori sincronizzati. La direzione del faccione/scroll resta superata.
- Libreria5.12.107 già consegnata: controllare voti storici/zero/nessun voto, prezzi di vendita reali/vuoti, backfill e ripristino, keyboard/insets dei bottom sheet, testi grandi e annullamento.
- Preservare rating personali, prezzo vendita e navigazione consegnati; una presentazione “scaffale” più completa resta esplorazione secondaria, non priorità e non requisito di rifare la Libreria.
- Verifica comune di icone, target touch, titoli lunghi, cover mancanti, contrasto, ritorno/posizione e animazioni disattivate durante i gruppi precedenti.

### Già consegnato: non duplicare come nuovo lavoro
- Card Catalogo, palette/tag del risparmio, classifica BGG, preview Bundle.
- Catalogo Annunci/Giochi con accesso Bundle separato e navigazione collegata.
- Gerarchia BGG/Ludo, lingua/dipendenza, gesto annuncio→gioco e personalizzazione: questa ultima richiede ora la revisione semantica del gruppo2.
- Libreria con voto0–5 preservato, vendita/backfill e finestre dal basso.
- Scena pet Ludo e azioni visibili. Tutti richiedono ancora accettazione visiva/di interazione sul telefono.

Prossimo gruppo raccomandato: verificare Home5.12.109 sul telefono, quindi dettagli prodotto e interesse contestuale (gruppo2). Implementazione iniziale del gruppo1 consegnata; accettazione visiva/prestazioni e qualsiasi estensione2,5D a Catalogo/Libreria restano pendenti. Prima di toccare i filtri Motore o l'interesse persistente, presentare il comportamento concreto da approvare.


---
Le sezioni seguenti conservano la cronologia. Per priorità e requisiti attuali vale il backlog consolidato sopra.

User request: 2026-09-30, installed 5.12.87. Validate each delivered step on Firebase App Tester before moving to the next. Automatic verified merge/distribution remains authorized.

## 1. Home — current implementation
One gear opens Motore. Engine ellipsis leads to existing settings/diagnostics. Featured CTA visibly52dp with ripple and14sp text. Flags + edition names and readable independence labels; unknown facts remain explicit, never inferred. All Home covers crop to their frame, including featured and BGG ranking. Section headings use FontAwesome. Remove category Vedi tutte (interpreting the transcription as “eviterei”; all categories remain horizontally accessible). Real same-seller bundle spotlight with2–3covers and direct detail; no invented combined price or explanatory filler. Bundle discovery bounded and cached off main thread.

Also repair the reproduced phase-list bug immediately: Android binds rawQuery selectionArgs as strings; the computed numeric phase has no column affinity and phase=? never matches. Explicit parameter CAST plus fixtures using Android-equivalent string bindings. Do not claim this fixes queue stalls/SQLite locks.

## 2. Motore and stability — next recommended step
Diagnostic reports five post-install crashes and one ANR, SQLiteDatabaseLockedException in UI, snapshot load5751ms, busy_timeout8000ms, a Vinted PROCESSING lease5670595ms, queue heartbeat5732255ms, eight runnable Vinted jobs, and active scroll over five hours. These are stale/blocked activity evidence, not proof of a specific queue-owner root cause. Audit UI synchronous reads, cross-process SQLite and lease/watchdog recovery before adding broad motion.

Rebalance overview to available screen height. Full loading state with animation, no top-line debug text. Keep exact direct phase drilldown. Establish durable delta semantics before implementation: proposal is comparison with last seen counts of the same observation scroll, labeled “Dall’ultima visita”; show green/red deltas throughout the visit rather than disappearing after three seconds; update baseline on leaving, animate once on reentry, and explicitly reset when the scroll changes. This proposal requires product review, not an assumed final decision. Separate actual PROCESSING activity from waiting. Include reproducible real string-bind fixtures and device debug evidence.

## 3. Catalog and details — pending design review
Clickable taxonomy chips in game detail/preview where useful. Prominent meaningful deal label and raw BGG rating; reduce the chip wall. Smaller provider identity, Material-style bottom actions BGG/Vinted. Rethink Annunci/Bundle/Giochi navigation: relationship between catalog game and market listings differs from same-seller bundles. User explicitly has not settled a new navigation model; present concrete alternatives before replacing the selector.

Controlled announcement→game gesture: only at content end, continued intentional finger movement fills a visible circular indicator and a label describing the transition; release before threshold cancels, completion enters preloaded game view without an intermediate loading screen. Keep explicit accessible game navigation, reduced-motion behavior and no accidental transition from normal scrolling. Bundle catalog cards and filler descriptions also reviewed here.

## 4. Ludo — pending
Purple bold minimal pet inspired by the user’s attached reference (purple cloud-like body, expressive eyes). Opening view gives pet most of the screen; scrolling collapses it progressively and reveals useful advice. It is the app mascot, not a ChatGPT Work pet. No analytics/stat clutter. Decide desired visual/motion prototype before replacing all mascot assets.

## 5. Collection and shared interactions — pending
Explore a subtle bookshelf presentation of covers. Personal liking is the leading preview fact (simple0–5stars/hearts); preserve existing stored ratings and clarify conversion from0–10 rather than overwrite data. Remove played/unplayed and aggregate payment/count clutter from primary hierarchy. Sold games remain in Sold with actual sale price; ask sale price on sale and permit adding missing historical price, never invent it from purchase price.

Add-game and other task modals should slide upward from bottom with coherent background scrim; blur only where platform supports it. Review keyboard/insets, dismissal, accessibility and small purposeful animations. Loading states show actual progress only when measurable; otherwise honest indeterminate animation.

## Delivery checks
Every implemented step: current regression workflow + Android/JUnit compilation; code review; signed beta APK; Firebase upload and tester distribution confirmation. Local environment has no working JDK or Android emulator, so device visual/gesture validation comes from App Tester and user feedback.

## 2026-10-01 — First Motore layout milestone delivered
5.12.98-engine-layout: header/page spacing, bars/cutout helper, one grouped surface for current scroll/state, independent waiting-scroll/global-intake actions and unchanged five-phase graph. Progressive phase cards use actual available BGG and legacy Vinted fields, with background enrichment/artwork decode. Raw rows remain visible; exact phase membership/publication gates and schema unchanged. Shared game/announcement detail padding is consistent. CI/regression/SQLite/JUnit/compile/APK/signing and Firebase upload/distribution confirmed; device visual approval pending.

This delivery does not close the stability or motion requirements in section2. Recorded PROCESSING activity is not proof of a healthy lease. Current deltas compare successive fresh snapshots of the same scroll and fade after~3seconds; the older “Dall’ultima visita” proposal still requires a dedicated decision. User chose compact waiting-scroll actions, superseding the extra “Da analizzare” circle. No navigation selector, mascot, Library or queue rewrite was included.


## 2026-10-01 — Product box stage delivered (latest decision)
User put Motore on standby and requested foreground product artwork throughout product details.5.12.99-product-box-stage (1000116), PR108 merge1c3152ecce6e3d94728be055700cb8b89f10c584, is uploaded and distributed through Firebase App Tester at09:38 Europe/Rome. PR279 and signed beta116 passed checks.
Visible cover front shares pedestal center; side depth13%, colors derived from real cover with preserved hue/saturation, stronger contact shadow. Existing PNG preserved. Home square stage now82% available width capped240dp, two-line description. Catalog game, game overlay, listing and Library details use the same transparent stage and continuous page background. Real listing photos remain available in separate existing gallery; unknown-BGG listings retain photos. No changes to navigation, dependency, schema, publication gates or queue ownership. Decode and metadata fallback remain off UI thread.
Automated verification includes the centering test failing before correction then passing, existing regressions/SQLite fixtures, Android JUnit, Java compilation and signed APK. No device/emulator available. Pending visual review: square/tall/wide cover integrity, bright-front/pedestal alignment, contact shadow and depth/colors, compact Home balance, and listing photo access. Next milestone is phone visual approval/refinement of this shared stage; Motore stability/motion and other historical tasks remain pending.


## 2026-10-01 — Home light scene and product refinement (latest phone feedback)
5.12.100-home-light (1000117), PR109 merge3e828d3792bfd0a2978665238f9303b4d61968fa, uploaded/distributed to Firebase testers at10:36 Europe/Rome. PR282 and signed beta117 checks succeeded.
Phone feedback supersedes the13% box depth: use8.5% width with darker top/side tones derived from the actual cover. Preserve front/pedestal centering, complete cover and existing PNG/contact shadows. Listing photos are clickable64dp thumbnails below artwork, excluding BGG cover and keeping real gallery access; no text photo button. BGG pill logo is20dp bounded.
Home now uses a pale continuous full-width greeting/product scene, no featured card boundary, dark readable copy and CTA. A144dp content-relative angular purple/blue light transition blends into dark category/offer sections. Cached shaders/paths avoid draw allocations; enlarged copy moves the transition with actual content. Home status-bar appearance is light scoped to Home. Category order, preferences, publication gates and navigation remain. No assets/dependencies/schema/engine changes; Motore remains on standby.
Automated checks passed and336geometry cases stayed within bounds. Visual review remains pending on device: light-to-dark composition, subtler darker side, thumbnail/full-gallery behavior, BGG icon and enlarged text. No emulator/device available here. The next single step is phone visual approval/refinement of this direction.


## 2026-10-01 — Light Home rejected; product page approved (latest decision)
User explicitly requested return to black/dark Home and liked the product page. This supersedes the preceding light scene/transition direction.5.12.101-home-dark (1000118), PR110 mergeb4bfef8cc956cdb2883d7b6effaf4b56214cedbe, uploaded/distributed to Firebase App Tester at10:59 Europe/Rome. PR283 and signed beta118 passed all existing checks.
Restore5.12.99 dark Home composition/header/featured card/status-bar appearance. Preserve5.12.100 shared product stage (8.5% depth, darker sides, contact shadow), listing photo thumbnails/gallery and bounded20dp BGG logo; direct source comparison confirms preservation. Product-page visual direction approved by user. No device/emulator available here; verify restored Home on phone. Motore stays on standby. Next milestone: Catalog preview hierarchy/layout with existing navigation and filters preserved.


## 2026-10-01 — Catalog cards delivered (latest)
5.12.102-catalog-cards (1000119), PR111 mergeb757202670b76da576850e9838a1a1e65881ddc5, uploaded/distributed through Firebase App Tester at11:22 Europe/Rome. PR284 and signed beta119 passed checks.
Catalog cards use square BGG cover, publication date, max-two-line title, BGG rating, compact edition, real price and saving only when verified; their action is a48dp target. Search/filters/category active state/order/pagination/detail routes unchanged. No changes to schema, dependencies, queue or navigation structure. Device review pending: density/height with normal and large text; historical date, unknown rating/language, saving/no-saving examples. The Annunci/Bundle/Giochi selector is still unresolved and should not be replaced without a product decision.


## 2026-10-01 — Catalog phone review additions
For the next Catalog refinement, preserve the current card structure and make three targeted changes: (1) top-right ellipsis must remain a48dp accessible target but have a smaller, less prominent visible circle; (2) unknown publication date is displayed as “?” rather than a full “Data pubblicazione n/d” label; (3) discounts need an explicit visual scale. Proposed product rule: 0–19% neutral slate, 20–34% blue, 35–49% teal, 50%+ green as “Offertona”. Only verified saving data receives a color/badge; no inferred original prices.

## 2026-10-01 — Grouped Phase5: market previews and navigation proposal
User requests fewer, broader updates combining related improvements. Group card hierarchy, Bundle previews and Catalog navigation review in one milestone; preserve separate approval for material navigation changes. Future delivery groups: (A) Catalog/market previews and navigation; (B) product details, language/personalization and controlled transitions; (C) Library/shared sheets and accessibility; (D) Ludo mascot once reference is available; (E) Motore stability and motion after resuming it.

Implemented preview scope:
- Home BGG ranking reuses discoverDiscountBadge, hence the same real-saving data and0–19/20–34/35–49/50+ palette. Rating22sp with16sp star, title16sp capped2lines, square68dp artwork and compact rank medal26dp. Price and rating occupy distinct areas; BGG rank stays secondary and voter evidence remains accessible. Narrow/large-font layouts stack values and facts; no fixed text heights.
- Catalog confirmed Bundle cards emphasize actual seller, game count, up to3existing cover fronts and2-line group title, opening existing Bundle detail. Replace the inherited tall Home tile only in the Bundle Catalog list. Do not promote estimated offer totals as an actual combined purchase price. Existing game eligibility, grouping, order, prospect section and detail calculations/routes preserved.

Navigation proposal — NOT APPROVED / NOT IMPLEMENTED:
- Keep Annunci as default Catalog entry for shopping; two equal tabs: Annunci and Giochi.
- Move Bundle from the three-way selector to a distinct compact header action. Bundle is a seller-based shopping group, not a third form of game identity.
- A game detail lists its actual linked offers; an offer has an explicit game link only when linked and can expose same-seller Bundle only when a confirmed group exists. Existing unknown/unlinked offers remain visible and actionable.
- Bundle detail opens each member offer and the seller. Preserve Back position and separate filter/search state for game/listing views.
- Do not redesign bottom navigation, introduce migrations or change eligibility/pricing to implement this relationship.
- Product approval of the proposed two-tab-plus-Bundle-entry navigation is required before replacement of the existing selector.

Delivery: full existing PR regressions/SQLite/Android JUnit/Java/APK, source diff review, signed beta build, separate Firebase upload/distribution confirmation. Visual acceptance on phone remains required for long titles, absent saving/rating, large text,2/3/many-game bundles.

### Verified delivery
5.12.104-market-previews (1000121), PR113 merged3be0adae846f98ed76ccbc5015b61777411738ea. PR286 (36863477090) and signed beta121 (36863954605) passed regressions/SQLite/JUnit/Java/APK. Firebase separately confirmed upload and tester distribution at2026-10-01 12:49:50UTC (14:49 Europe/Rome). No device/emulator visual acceptance claimed. Navigation remains a proposal.

Clarification superseding the earlier saving-color wording:50%+ means a green saving badge, not automatic Offertona classification. Existing total-price/used-benchmark evaluation remains separate and unchanged.


## 2026-10-01 — Navigation proposal approved and delivered
5.12.105-catalog-navigation (1000122) uploaded to Firebase at 2026-10-01 14:58:10 UTC and separately distributed to testers/groups at 14:58:10 UTC (16:58 Europe/Rome), confirmed in signed job110429267970 logs. PR114 merged atad475ad702e8618dada5a10e6eb730c2552bfad8. PR validation run293 (36879669999), head26b2053aa413d3b0b9348e7bdebf749b34d3a552, passed full regressions/SQLite, 11 navigation behavior cases, Android JUnit, Java compilation and review APK. Signed beta run122 (36880064442) passed tests/build/signature/upload/distribution.

User's “next” approves the preceding two-tab/Bundle-entry proposal. Implemented as a grouped navigation and connected-detail update.
- Annunci / Giochi are sibling tabs; Bundle is a distinct header entry with Back. Independent search/filter/pagination state and scroll positions retained; delayed restores are scoped to their destination.
- Actual linked game offers open internal announcement detail. A labeled 48dp “Scheda gioco” action opens the canonical linked game; Vinted remains the provider action inside detail.
- Same-seller partner and Bundle member details preserve their source for Back. Category navigation explicitly closes the nested listing/Bundle stack so it cannot cover the Games destination.
- Added 11 JVM behavior scenarios executing production routing/lifecycle methods. Existing glyph-only source guards now recognize the labeled accessible game action. Independent code review caught the nested category issue; correction re-reviewed and approved.
- Approved Home/product visuals, canonical data mapping, eligibility/prices, schema, dependencies and Motore remain unchanged. No device/emulator validation; phone checks: tab search/filter/position, Bundle Back, game→offer→Bundle→member→Back, and member category destination.
Next grouped block: product detail hierarchy, language/personalization and the intentional announcement→game gesture, preserving the approved product artwork. Motore remains on standby.


This approval supersedes earlier statements that the two-tab proposal is pending. Historical pricing and Motore stability requirements remain open.


## 2026-10-01 — Grouped product interaction design approved and delivered
5.12.106-product-interaction (1000123) uploaded to Firebase at2026-10-01 15:30:50 UTC and separately distributed to testers/groups at15:30:51 UTC (17:30 Europe/Rome), explicitly confirmed in signed job110443809254 logs. PR115 merged6abe6c411812f050251cb0641425a9cf5695aacb. PR validation run300 (36883748763), head44bb6c8b76fb5d0e602947351f026510301369c8, passed9gesture scenarios, all existing regressions/SQLite fixtures, Android JUnit, Java compile and review APK. Signed beta run123 (36884375420) passed tests/build/signature/Firebase upload and tester distribution.

User approved the concrete grouped design with “vai” on2026-10-01: raw BGG rating prominent, Ludo secondary; explicit edition/text dependence; existing interest preferences; intentional end-of-content pull and prepared game destination.
- Listing and game details share24sp raw BGG rating with inspectable secondary Ludo Score. Listing price and real saving badge remain distinct; provider identity28dp. Approved dark product artwork/photos preserved.
- Edition name and text dependence are separate readable facts using HomePresentation; correction/help have48dp minimum targets and larger-font stacking. Unknown language is not inferred.
- Mi interessa / Non mi interessa uses the existing game-key Home preferences, toggles back to neutral, and synchronizes attached preserved views. Catalog eligibility and owned/library ratings unchanged.
- Gesture begins counting only at actual content end, requires72dp further movement and full circular progress followed by release. Retreat, final-UP retreat, Android CANCEL, multiple fingers, source closure and unavailable preparation cancel. Explicit Scheda gioco action remains available.
- Source-bound prepared game dialog uses factual background snapshot of rating/similar games/prices/history/active offers and captured deal scores. UI construction stays on main thread; local image decoding on image executor. Pull opens already prepared content without a full-screen loading intermediate, retaining the listing and its scroll for Back. Images may still use their existing asynchronous placeholders; no instant-image guarantee.
- Independent read-only review identified source reprepare-after-close and final-release-coordinate defects; corrected and re-reviewed. Attached-only preference listeners address stale selection on preload/Back. No blockers remained.
- New executable production touch-listener harness covers9scenarios; test-only run294 reproduced6old failures and run297 reproducedfinal-UPretreat before correction. Full existing regression/SQLite/JUnit/compile/APK checks verified below. A compiler API error (LinearLayout minimum height) was corrected before delivery; no failed final checks hidden.
- No device/emulator available. Harness verifies app listener/state effects, not real Android dispatch/interception or visual/gesture smoothness. Phone checks: BGG/language with enlarged text; interest synchronization/neutral; intentional pull, cancellation/retreat and Back/category from prepared game.
Next grouped block: Library previews/personal ratings and sale-price entry, with shared bottom sheets and accessibility. Motore remains on standby.

The concrete in-chat design was presented and explicitly approved before implementation. Intentional pull requirements in section3 are implemented for prepared linked-game destinations; device gesture/visual acceptance remains pending. Earlier Home/Motore/Library historical requirements are not automatically closed.


## 2026-10-01 — Grouped Library design and schema approved; delivered
5.12.107-library-interactions (1000124) uploaded to Firebase at2026-10-01 16:19:41 UTC and separately distributed to testers/groups at16:19:42 UTC (18:19 Europe/Rome), explicitly confirmed in signed job110465000317 logs. PR116 merged39913c18530f96759e38038c3acb983a02146233. Final head3942962c5a2ed1c57370ca6f25d26bbc8ca19ed5 passed PR run305 (36890098114): all regressions/SQLite fixtures, Android JUnit, Java compilation and review APK. Signed beta run124 (36890615976) passed regressions/tests, APK build, expected-certificate verification and Firebase upload/tester distribution.

User explicitly approved the presented grouped Library design and nullable actual-sale-price schema with “vai” on2026-10-01.
- Library previews lead with actual covers and personal taste; aggregate payment summary and filler leave the primary hierarchy. Search, owned/Sold scopes and existing product artwork remain.
- Personal rating display uses0–5stars. Stored0–10values remain exact without bulk rewrite; legacy7 displays3.5/5, zero is meaningful and distinct from missing. Rating removal storesnull. Whole-star choices write corresponding even legacy values.
- Approved SQLite version8 adds nullable sale_price_cents. Sale entry permits unknownblank or actualzero, validates exact decimal cents/negative/precision/overflow. Historical backfill changes only actual proceeds on Sold, keeping original sale date/reason and purchase/rating. Restore-owned explicitly confirms clearing sale fields. Sold purchase editing is omitted to avoid the existing acquisition path silently resetting sale history.
- Library mutations run off UI thread; failures keep inputs for retry. Sheet-scoped single-flight guard rejects rapid second taps and dismissed sheets; successful writes return to the active originating detail and scroll. Leaving during a write does not reopen it.
- Shared bottom sheets use bounded wrap-content height, upward220ms entrance/160ms exit, safe insets/keyboard resizing and disabled-animation fallback. No new dependencies. No emulator/device visual or keyboard validation performed.
- Executable production-Java/SQLite tests cover migration fixtures1–7, nullable fresh schema, preserved ratings7/10/null/zero, actual sale/backfill/restore/owned guard, five-star display and12euro-input boundaries. Async production-method harness covers double submission, failure/retry and leaving during save.
- Test-only run301 reproduced15pre-implementation data/display failures; run304 reproduced2writes from duplicate taps before the single-flight fix. Legacy UX phase2 guard was intentionally updated for approved rows/search/history without the primary payment summary. Final independent review of3942962 found no Critical/Important issues.
Phone checks pending: long titles/enlarged text, legacy3.5stars/zero/no vote, actual/blank/zero sale and historical price, restore confirmation, keyboard/navigation bars, cancellation and disabled animations.
Next grouped block: Ludo app mascot presence/collapse/motion. Present concrete visual prototype before replacing mascot assets; use the user's supplied reference when available in this conversation. Motore stability/motion remains on standby.

This supersedes the pending implementation status of section5 for Library covers/taste, sale price and shared sheets. No full bookshelf redesign, keyboard/device visual acceptance or Ludo mascot replacement is claimed.


## 2026-10-01 — Interactive Ludo pet scene approved and delivered
5.12.108-ludo-pet (1000125) uploaded to Firebase at2026-10-01 18:05:40 UTC and separately distributed to testers/groups at18:05:41 UTC (20:05 Europe/Rome), explicitly confirmed in signed job110508767016 logs. PR117 merged527b71b46802477c8e648297838b3fad38e79926. Final head2cfc41ef024a8b5de29ec67d619ed1d4c76edd7a passed PR run310 (36903297123), all existing regressions/SQLite fixtures, new executable pet state harness, Android JUnit, Java compilation and review APK. Signed beta run125 (36903679457) passed tests/build/expected-certificate verification and Firebase upload/tester distribution.

User approved the final scene proposal with “Bello, creiamolo ma i bottoni devono essere più visibili” on2026-10-01. This supersedes giant opening face, scroll-collapse mascot, stacked action cards and double horizontal dock proposals.
- Compact pet scene has flat purple vector cloud, neutral existing app typography, breathing/blinking/tap reaction and gaze toward actual game. Pet keeps the same allocated size before/after suggestion; no huge-face initial mode.
- Cacce/Gusti are filled contrasting icon+label buttons, min56dp target, with automatically measured control area independent from artwork. One primary action Consigliami becomes Apri il gioco; optional Un altro consiglio remains accessible. Existing bottom navigation preserved.
- Existing intelligence/eligible recommendations are loaded off the main thread; only current real eligible IDs map to canonical game overlay. Empty/removed suggestions clear actionable selection; missing/failed data is explicit, retry throttled and does not invent an offer.
- Actual product artwork/pedestal reused next to pet. Recommendation reason remains readable. Scenes contain no fabricated price/rating/reward/progress data or new dependencies/schema.
- Existing Cacce/Gusti render in dedicated full-screen panels. Mutations refresh the active panel host; underlying pet remains paused. Last successful profile snapshot is retained on refresh failure to prevent null-profile crash, while actionable suggestions are cleared.
- Motion is lifecycle-bound to resumed/attached/shown window, canceled on detach/pause/hidden window; disabled-animation fallback including tap reaction. No device/emulator validation of motion or Android lifecycle.
- New executable real Java state harness checks12 suggestion/lifecycle cases: intentional selection, preservation, current eligibility/removal, empty/invalid/duplicate inputs, bounded cycling and disabled/paused/detached animation gate. Test-only PR run306 reproduced absent behavior before implementation.
- Independent review caught stable-size, stale Cacce mutation and null-profile failure defects; corrections re-reviewed on exact final2cfc41e with no remaining blockers. Harness verifies state policy, not actual Android drawing/dispatch/animator mechanics.
Phone checks: full pet and box/covers; visible controls with large fonts/small screen; tap/blink/gaze, disabled animations/backgrounding; real suggestion/empty/error states and game Back; Cacce add/delete and Gusti panels. Static image mockups are design references, not exact on-device verification.
Next recommended step: phone acceptance/refinement of5.12.108 pet scene and visible actions. Motore stability/motion remains on standby until user resumes it.

This supersedes section4's opening-most-of-screen/collapse-on-scroll proposal. Final approved design uses stable pet size in a compact scene, visible Cacce/Gusti controls, useful real suggestions and dedicated panels. Phone acceptance remains pending; no exact reproduction of generated concept images is claimed.


## 2026-10-01 — Group1 Home implementation delivered, phone acceptance pending
5.12.109-home-box-cards (1000126) uploaded to Firebase at2026-10-01 18:35:45 UTC and separately distributed to testers/groups at18:35:46 UTC (20:35 Europe/Rome), confirmed in signed job110521729455 logs. PR118 merged95a77b251ace8b28a1ca56fcd5c4162bb3cc6188. Final heada20409231c38770d75cb96ccf3ca792fa40a64e8 passed PR run314 (36907046099): all regressions/SQLite fixtures,60 new geometry cases, Android JUnit, Java compile and review APK. Signed beta126 (36907538365) passed tests/build/certificate/Firebase upload/tester distribution.

User approved direct implementation of the grouped Home refinement on2026-10-01, asking to preserve current cards while making them closer to Catalog and varying hierarchy by section. First 2.5D rollout is Home only; Catalog/Library extension awaits phone performance/visual acceptance.
- Home offer/recent cards retain existing horizontal rails, title/price/data/routes and square artwork area, with12dp padding,18dp corners and16sp two-line title. Publication is17sp prominent in fresh with16sp price; offers emphasize20sp raw BGG quality and14sp real-saving badge with18sp price. Ranking retains22sp rating and real-saving palette. Large-font score/language stacks; language min48dp target.
- Home preview/ranking artwork uses the existing cached Canvas box renderer without pedestal, complete real cover aspect, subtle dark depth and ellipse shadow; product artwork lookup/decode off UI thread, missing-cover placeholder and flat-cover renderer fallback retained. No physical 3D model, new asset/dependency or animation loop/per-frame bitmap allocations.
- Complete no-pedestal box centering is corrected in the5arg geometry; separate pedestal-aware6arg geometry/details remain unchanged. New executable production-Java fixture sweeps60combinations of size/aspect, independent complete-box center/bounds/aspect expectations plus invalid bounds. Test-only PR311 reproduced the off-center failure.
- Hero uses Home-scoped darker top/side factors, centered charcoal radial backdrop with restrained violet, ground shadow beneath existing pedestal and pack FontAwesome right arrow with relative aligned drawable. Shared default detail/product artwork remains unchanged; existing transparent pedestal PNG reused.
- Old visual source guards updated only for approved preview helper, section savings placement and explicit Home-scoped renderer; BGG-only source and real discount/price checks remain. Compile constant missing during first code pass was corrected before final validation.
- Independent read-only review of finala204092 found no critical/important blockers. No changes to selection/order, pricing/eligibility, interest semantics, schema, dependencies, Catalog/Library previews, pet or Motore.
- Phone verification still required for Home scroll fluidity with multiple boxes, square/tall/wide covers, missing cover, large text, actual hero centering/pedestal grounding, sides darkness and arrow alignment. CI/geometry do not prove device visual acceptance or frame timing.
Next grouped block: product detail clarity and contextual interest actions (backlog group2), defining temporary featured dismissal separately from persistent taste before changing behavior. First accept/refine Home on phone; only extend box previews elsewhere once fluidity is confirmed.


## Delivered milestone — Grounded scenes, redesigned Ludo and game favorites 5.12.110
5.12.110-scene-favorites (1000127) uploaded to Firebase at2026-10-01 19:15:58 UTC and separately distributed to testers/groups at19:15:59 UTC (21:15 Europe/Rome), explicitly confirmed in signed job110538363316 logs. PR119 mergeda87d6d442a53144a55536857e0b5ba211b4cc04e. Final heada3127101338faa69226dc79a52158a88789ec063 passed PR run319 (36912124710): all regressions/SQLite fixtures, new executable game preference + real Activity-method fixtures, Android JUnit, compile and review APK. Signed beta127 (36912505410) passed tests/build/expected-certificate verification and Firebase upload/tester distribution.

User requested immediate grouped corrections on2026-10-01 20:43 Europe/Rome: create assets for deeper Home/cards, replace floating pedestal, redesign the sparse Ludo page with prominent explanation and interactions, preserve its last game, replace Cacce with Preferiti, and save games rather than individual listings. This supersedes the previous pet verification-only limitation and preserves the outstanding contextual-interest/Motore backlog.
- Three actual generated WebP assets shipped under app/src/main/res/drawable-nodpi: game_scene_floor (physical charcoal/plum wall and floor), game_scene_plinth (matte dark stone, true alpha and contact shadow), ludo_room (illustrated game nook). Home previews and featured stage use the floor; featured/Ludo use the matte base. Existing detail pedestal remains supported. BGG covers stay real. Export/alpha checks passed; prompts/use documented in docs/assets/game-scenes-512110.md.
- Asset decode is once per Activity on the gallery executor; cached renderer geometry/shaders remain outside drawing. No 3D dependency/model, schema migration, generated product cover or per-frame bitmap processing. Physical floor and contact shading improve anchoring; actual on-phone appearance and memory/frame timing remain acceptance checks.
- Ludo has an environment, large24sp game title,18sp explanation, touch dialogue/reaction, contrasting Preferiti/Gusti actions and clear primary CTA. Speech and actors measure separately for large fonts. Cacce remains in the Ludo overflow menu.
- Dedicated local durable preferences contain canonical normalized positive BGG game favorites and a separate last-Ludo-game ID. Hearts subscribe while attached across Home/card/listing/canonical-game/details/similar-game/Ludo/Favorites surfaces and unregister on detach. Favorite list resolves real canonical records off UI thread; missing games are explicitly unavailable/removable. Saves never use listing identity, hide offers, imply ownership or reuse interest/eligibility preferences.
- Ludo reopening restores the last game. Loss of an eligible offer becomes a game-only view with no stale listing price or Vinted link. Intentional selection caches canonical metadata immediately; refresh results cannot replace a newly chosen identity. Advice deduplicates eligible listings by normalized BGG ID, so another advice advances across distinct games.
- Pet motion now gates on resumed Activity, window focus, companion tab and absence of covering pet/game/listing panels. Opening game detail pauses the underlying pet; closing overlays resumes only when eligible.
- Test-only PR315 reproduced missing durable preferences, and PR318 reproduced missing distinct-game cycle APIs. Final executable Java regression verifies save/remove/reload/identity, canonical cycling and extracted real Activity methods for selection during refresh, removed-offer fallback, recreation and overlay/focus/lifecycle gates. Platform adapters are stubbed; this is not an Android device restart/instrumentation test. Existing pure pet lifecycle harness retains its12 cases.
- Old UX guard corrected to explicitly inspect Home physical renderer instead of a coincidentally nearby default detail constructor; BGG content/price/route guards retained. Independent re-review of finala3127101338faa69226dc79a52158a88789ec063 found no remaining critical/important issues after duplicate-cycle, refresh-race and overlay-motion corrections.
Phone acceptance: floor/plinth contact and depth at cover aspect ratios, backdrop crop, full pet/box and readable explanation at large fonts/small screens, Favorites save/remove reflected on multiple listings, return/restart retains last game, unavailable offer fallback, pet touch and pause/resume/disabled animation, scrolling/memory. CI does not prove visual acceptance, actual SharedPreferences process durability or frame time.

