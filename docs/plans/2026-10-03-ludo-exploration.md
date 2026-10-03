# Ludo Exploration Implementation Plan

## Ludo153 — esplorazione e bilancio giornaliero, distribuita

Richiesta01:40Europe/Rome: integrare ricerca e Motore in Ludo, conservare nei cerchi i risultati della giornata e mostrare il contributo dello scroll con frecce, esplorazioni Vinted centrali. Distribuita **5.12.153-ludo-exploration (1000176)**, localCode202. Preservati Ludo151 e Catalogo/Bundle152.

Stanza Esplorazione: ricerca, cinque controlli giornalieri e risultati consultabili. Unità annunci osservati oggi (Europe/Rome), canonical ID o firma; BGG/link già disponibili contribuiscono ai controlli completati. I cerchi si sovrappongono e non si sommano. Frecce con segno/testo/colore: delta dei soli annunci dello scroll dalla baseline per annuncio dell'esplorazione; completamenti di altri scroll esclusi, fallback firma evita doppio ingresso raw→canonical. Totali riflettono controlli attuali, rettifiche possibili: nessun registro di throughput inventato, nessun ritardo artificiale. Lavoro live distinto; sospesi/motivi e dettagli apribili, ritorno da browser/dettagli in Ludo. Baseline non pertinente/assente: variazione indisponibile.

Browser: Nuova esplorazione primaria, preset rilevanza/novità/prezzo, ricerca manuale e Sorprendimi da titoli reali del catalogo locale. Frecce1–10 e filtri preservati, una pagina su gesto; nessuna scansione automatica di tutte le pagine. Pausa/play e cattura secondarie con icone, capsule coerenti e target48dp. Nessun reset/schema/dipendenza/rete di cattura aggiuntiva; gate fiducia/pricing conservati.

PR203 HEAD7799ca03a6750d3d04840e65513c077b155acbb7; mergeb5890e1ddc419267c0a5c5d86c4781032f747cbd, beta dietro0. Review indipendente:3Important corretti (destinazioni dettagli da Ludo, baseline esplorazioni ravvicinate, sospesi dopo BGG); RED/GREEN. Suite ha individuato adapter navigazione e freschezza snapshot, corretti senza indebolire i controlli. Fixture esegue SQL reale; JVM policy URL/giornata DST/delta membership e metodo reale Activity; browser40/40 locale. Java locale assente, verificato in CI.

CI finale37081692544/job111083394319 SUCCESS: regressioni complete, SQL/JVM, unit Android/pricing, compile e review APK. Android beta176/run37081966417/job111084239869 SUCCESS: suite/unit/build firmata; certificato atteso C7DF7C31D0FE0D059307F4DE7B67BE5992DC87EC73E623CC9E8B4E87C63D8710 verificato2026-10-03T00:28:48.1182543Z. Upload Firebase153/1000176 confermato00:29:43.5444577Z; distribuzione tester/gruppi separata00:29:43.9781299Z; release248ghq0d7d468.

Prova telefono: aggiornare senza cancellare dati; Ludo→ricerca→ritorno, due esplorazioni ravvicinate, totali/frecce e dettagli/sospesi; Sorprendimi/preset/prezzi/pagine1–10; pausa/cattura; font200%/TalkBack. CI non prova pixel, fluidità o tempi del motore sul telefono. Restano frontend7/backend5: feedback consolidato nei gruppi Ludo/Motore/browser/osservabilità già aperti, nessun job duplicato e nessun gruppo chiuso dalla sola consegna. Prossimo passo di questo workstream: screenshot e diagnostica153 della prova, poi correggere eventuali scostamenti; scaletta Catalogo/Bundle152 preservata.


> For agentic workers: Use superpowers:executing-plans inline, with one final independent review.

**Goal:** Ludo integra ricerca, risultati quotidiani e controllo del lavoro.
**Architecture:** SQL read-only per annunci/stadi, snapshot asincrono e baseline per annuncio/controllo in prefs nel processo UI; policy pura per URL esplorazione; renderer Ludo riusa wheel/layout e dettagli esistenti.
**Tech Stack:** Android Java17/SQLite/WebView, Python regressioni; nessuna dipendenza.
**Spec:** docs/specs/2026-10-03-ludo-exploration.md

## Global Constraints
Preservare dati, filtri, pricing, sicurezza/capture, frontend151 e Catalogo/Bundle152. Nessun reset/schema/rete background. Unità annunci; giornata Europe/Rome; delta baseline esplicito. Target48dp e font adattivo.

## Review Focus
- Cambio giorno/DST: finestra calendario e baseline incompatibile non produce delta.
- Raw/canonical e firme legacy: listing deterministicamente deduplicato, stesso annuncio non duplicato.
- Match/review/pricing assente: nessun pronto inventato; dati mancanti distinti.
- Browser query error/provider non supportato: navigazione utente disponibile, capture rispettata.
- Ritorno browser/process restart: stanza Esplorazione e snapshot corrente; dettagli non alterano conteggi.

### Task1: Bilancio annunci
Files EngineJourneySql.java, DealDatabase.java; test regression/ludo_exploration.py.
Interface engineJourneyItems(start,end)->List<JourneyItem>; counts(items)->int[5].
- [x] SQL fixture RED per mancanza query; canonical+raw, duplicati, BGG/link già disponibili, ready/held/prezzo0/negativo, day boundaries.
- [x] Implementare query read-only, snapshot e baseline.
- [x] GREEN Python SQLite e JVM presentation/policy; commit.

### Task2: Esplorazioni e Ludo
Files ExplorationPlan.java, MainActivity.java, VintedBrowserActivity.java; same regression plus browser existing tests.
Interface ExplorationPlan.url(query,order,min,max) returns validated HTTPS URL; baseline/delta uses Task1 snapshot.
- [x] RED JVM URL/preset/delta/day, source integration checks missing Ludo journey.
- [x] Nuova esplorazione/preset/random/filter, icone pause/capture, wheel totals/day/delta, liste reali e ritorno in Ludo.
- [x] GREEN suite/Java/Android build, regressioni legacy aggiornate solo per requisiti superseduti.
- [x] Review indipendente, fix RED→GREEN, riallineare beta e distribuire build firmata verificata. STATE/backlog al checkpoint.
