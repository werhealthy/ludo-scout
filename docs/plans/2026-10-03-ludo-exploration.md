# Ludo Exploration Implementation Plan

> For agentic workers: Use superpowers:executing-plans inline, with one final independent review.

**Goal:** Ludo integra ricerca, risultati quotidiani e controllo del lavoro.
**Architecture:** SQL read-only per annunci/stadi, snapshot asincrono e baseline prefs nel processo UI; policy pura per URL esplorazione; renderer Ludo riusa wheel/layout e dettagli esistenti.
**Tech Stack:** Android Java17/SQLite/WebView, Python regressioni; nessuna dipendenza.
**Spec:** docs/specs/2026-10-03-ludo-exploration.md

## Global Constraints
Preservare dati, filtri, pricing, sicurezza/capture, frontend151. Nessun reset/schema/rete background. Unità annunci; giornata Europe/Rome; delta baseline esplicito. Target48dp e font adattivo.

## Review Focus
- Cambio giorno/DST: finestra calendario e baseline incompatibile non produce delta.
- Raw/canonical e firme legacy: listing deterministicamente deduplicato, stesso annuncio non duplicato.
- Match/review/pricing assente: nessun pronto inventato; dati mancanti distinti.
- Browser query error/provider non supportato: navigazione utente disponibile, capture rispettata.
- Ritorno browser/process restart: stanza Esplorazione e snapshot corrente; dettagli non alterano conteggi.

### Task1: Bilancio annunci
Files EngineJourneySql.java, DealDatabase.java; test regression/ludo_exploration.py.
Interface engineJourneyItems(start,end)->List<JourneyItem>; counts(items)->int[5].
- [ ] SQL fixture RED per mancanza query; canonical+raw, duplicati, BGG/link già disponibili, ready/held/prezzo0/negativo, day boundaries.
- [ ] Implementare query read-only, snapshot e baseline.
- [ ] GREEN Python SQLite e JVM presentation/policy; commit.

### Task2: Esplorazioni e Ludo
Files ExplorationPlan.java, MainActivity.java, VintedBrowserActivity.java; same regression plus browser existing tests.
Interface ExplorationPlan.url(query,order,min,max) returns validated HTTPS URL; baseline/delta uses Task1 snapshot.
- [ ] RED JVM URL/preset/delta/day, source integration checks missing Ludo journey.
- [ ] Nuova esplorazione/preset/random/filter, icone pause/capture, wheel totals/day/delta, liste reali e ritorno in Ludo.
- [ ] GREEN suite/Java/Android build, regressioni legacy aggiornate solo per requisiti superseduti.
- [ ] Review indipendente, fix RED→GREEN, riallineare beta e distribuire build firmata verificata. STATE/backlog al checkpoint.
