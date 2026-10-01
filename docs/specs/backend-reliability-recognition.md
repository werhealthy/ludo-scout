# Ludo Scout — Backend reliability, acquisition and recognition

## Scopo
Questo è il backlog persistente del workstream `backend`. L'obiettivo è aumentare affidabilità e throughput senza perdere correttezza: meno crash/ANR e lock, meno lavoro remoto inutile, più annunci utili trasformati in giochi corretti, meno review manuale e metriche leggibili.

Le modifiche vanno mantenute piccole, misurabili e reversibili. Nessun miglioramento viene accettato perché “sembra più veloce”: serve una baseline e un confronto dopo la modifica.

## Priorità operative

### B1 — Stabilità e performance: crash, ANR, SQLite, memoria e code
Primo obiettivo attivo. Ricostruire dove nasce la contesa tra processi UI/radar/coda e quali operazioni tengono il database occupato più a lungo.

Misurare almeno: crash/ANR per processo e versione; attese SQLite e durata delle transazioni lente; query lente; heartbeat/lease e job bloccati; tempo delle snapshot Motore; memoria/PSS-RSS quando disponibile; backlog runnable/processing/retry. Distinguere sempre sintomo, causa probabile e causa dimostrata.

Audit iniziale 2026-10-01: `DealDatabase` usa WAL e `PRAGMA busy_timeout=8000` perché più processi condividono il database. `QueueKeepAliveService` ha già heartbeat, stale-lease recovery e backpressure su database occupato. Il caricamento overview del Motore è già su executor background. Quindi non aggiungere retry o timeout alla cieca: prima attribuire lock/transazioni lente e ridurre la sezione critica responsabile.

Criterio di successo: nessun peggioramento di correttezza, riduzione misurabile di crash/ANR/lock e dei tempi di attesa, con recovery delle code verificata.

### B2 — Efficienza Vinted senza aggirare protezioni
Massimizzare annunci utili per unità di lavoro e minimizzare richieste remote. Preferire acquisizione user-driven/accessibility, deduplica, cache, local-first, batching, priorità, backoff e circuit breaker.

Misurare: richieste fisiche/cache hit; richieste per nuovo link risolto; 403/429/challenge; tempo in pacing/budget/remote limit; duplicati evitati; percentuale di annunci risolti senza rete; richieste per job/scroll.

Non implementare evasione di CAPTCHA, fingerprint spoofing, rotazione identità/proxy per eludere blocchi, furto/riuso di sessioni o bypass dei rate limit. Se Vinted segnala robot/challenge, ridurre pressione e capire quale flusso genera traffico evitabile.

### B3 — Riconoscimento e riduzione review manuale
Misurare il funnel reale: annunci acquisiti → classificati gioco/non gioco → identità BGG candidata → gioco base/espansione/accessorio/versione → match accettato → review/retry/scarto → catalogo.

Separare errori di titolo, varianti/edizioni, espansioni, accessori, bundle, immagini ambigue e record BGG differenti. Le correzioni manuali diventano dataset di valutazione: ogni miglioramento deve essere provato su casi storici prima di allargare l'auto-accept. Ridurre review senza aumentare falsi positivi.

### B4 — Throughput e osservabilità prodotto
Aggiungere metriche comprensibili e verificabili, prima in diagnostica e poi nella UI quando i denominatori sono chiari:
- nuovi annunci acquisiti per giorno e ultimi 7 giorni;
- nuovi giochi canonici entrati nel catalogo per giorno e ultimi 7 giorni;
- annunci analizzati per job/scroll e media per job;
- giochi distinti riconosciuti per job/scroll;
- percentuale acquisito → catalogo;
- review manuale, retry e scarti con motivi principali;
- richieste Vinted fisiche per nuovo annuncio/link utile.

Prima di mostrare numeri in UI, definire per ogni metrica evento, timestamp autorevole, unità e deduplica. “600 annunci scrollati → 1 gioco” deve poter essere scomposto in un funnel con motivi, non interpretato a intuito.

### B5 — Ciclo di miglioramento continuo
Non è training automatico in produzione. È un loop controllato: baseline → ipotesi → modifica piccola/shadow o fixture → confronto → keep/revert → nuovo benchmark.

Mettere periodicamente in discussione anche una soluzione considerata buona, ma cambiarla solo quando emerge evidenza migliore. Conservare casi regressivi e correzioni manuali come test. Una nuova soluzione deve battere la precedente almeno sulla metrica obiettivo senza peggiorare falsi positivi, stabilità o costi operativi rilevanti.

## Primo milestone
**Attribuzione contesa SQLite e stabilità delle code.**

1. Mappare i writer principali e le transazioni critiche nei tre processi.
2. Identificare operazioni che possono trattenere lock mentre fanno I/O, parsing o loop pesanti.
3. Rendere osservabili attese e transazioni lente senza aggiungere ulteriore contesa significativa.
4. Riprodurre con fixture/regressioni i casi di lease/heartbeat e database busy già noti.
5. Solo dopo l'evidenza, applicare il fix minimo e confrontare prima/dopo.

Nessuna modifica a soglie di riconoscimento, ranking, pricing o schema in questo milestone senza approvazione separata.
