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

## B1 — note di audit iniziali (ipotesi, non root cause)
- Database condiviso multi-processo con WAL e `busy_timeout=8000`: una collisione di scrittura può bloccare un chiamante fino a 8 s prima dell'errore.
- La coda ha già backpressure e recovery: Vinted tratta il database occupato con retry breve, il supervisor mantiene heartbeat e recupera lease stale. Aggiungere altri retry non è la prima leva.
- L'overview Motore viene già caricata su `engineUiIo`: il vecchio snapshot lento non è semplicemente un caso di query pesante lasciata sul main thread.
- Hotspot da misurare: `MarketStore.claimNextVintedJobInternal` e `claimNextBggJob` aprono transazioni per query + claim; sono ad alta frequenza e competono con radar/UI.
- `DealDatabase.applyBggGame` aggiorna i deal e poi scansiona/parsa tutti i `listing_overrides` dentro la stessa transazione; è un candidato a sezione critica troppo ampia se gli override crescono.
- `DealDatabase.inferMissingLanguages` esegue potenzialmente molti update riga-per-riga mentre mantiene aperto il cursore di scansione: possibile writer churn anche senza una singola transazione lunga.
- `VintedPublicSession` scrive il ledger SQLite anche per cache hit e usa transazioni per gate/contatori: utile per verità cross-processo, ma da misurare come possibile pressione di scrittura diagnostica.

Primo intervento ammesso: osservabilità a basso impatto che distingua **tempo di attesa per acquisire il writer lock** e **tempo di lock mantenuto**, etichettati per processo/operazione e persistiti fuori dal database per non peggiorare la contesa. Nessun cambio di timeout o retry finché questi dati non indicano il colpevole.


## B1 — mappa writer e sezioni critiche, 2026-10-01

Audit del codice su `beta` commit `d05f40ae779938d4cdd71cd281671a840f45c494`. Questo passo completa la mappatura iniziale; non implementa ancora strumenti di misura o una correzione. I riferimenti sono nomi di metodi del codice analizzato.

### Processi e writer

| Processo | Origine | Operazioni da misurare |
| --- | --- | --- |
| `:radar` | `scanVisibleVintedCards` e callback analisi | `DealDatabase.recordSighting/record`, `MarketStore.recordSighting/applyAnalysis`: scritture legacy e canoniche distinte per la stessa acquisizione. |
| default, coda Vinted | `QueueKeepAliveService.vintedLoop`, `QueueJobRunner.processOneVinted` | claim, progress, link verificato, prezzi/lifecycle legacy, materializzazione canonica. |
| default, coda BGG | `bggLoop`, `BggEnricher` | claim singolo/batch, `applyBggEnrichment`, `applyBggMetadata`, alias, promozione annunci e materializzazione. |
| default, controllo/manutenzione | `initializeBackground`, control executor, startup sweep | cutover una tantum, recovery lease, `reconcileQueue`, filtri/stati globali e sweep. La singola proprietà del controllo non serializza tutti i writer delle altre lane. |
| `:ui` | azioni manuali e manutenzione background | correzioni/esclusioni, priorità/verifica annunci, aggiornamenti legacy/canonici. Overview Motore su `engineUiIo`; non attribuire la vecchia lentezza a query sul main thread senza trace. |
| processi che usano il client pubblico | `VintedPublicSession` | ledger anche per cache hit, permit/circuit breaker: transazioni non esclusive sullo stesso DB. |
| radar/coda/UI | diagnostica e heartbeat | `setDiagnosticState`, `touchLaneHeartbeat`, `setLaneStatus`: anche la telemetria corrente è un writer SQLite. |

Il manifest assegna esplicitamente `:ui` e `:radar`; servizio coda e receiver usano il processo default. I monitor Java `synchronized` sono locali all'istanza/processo, quindi non serializzano i writer dei tre processi.

### Sezioni critiche confermate nel codice

- `claimNextVintedJobInternal`, `claimNextBggJob`, `claimBggBatch`: writer acquisito prima di interrogare lo scroll attivo con `helper.activeObservationSession()`, poi selezione e claim condizionale. Misurare anche il costo della risoluzione dello scroll, non solo l'UPDATE.
- `applyBggMetadata`: una transazione include aggiornamenti su giochi/annunci/deal/job, alias multipli, loop sugli annunci hot e loop sui pronti con `helper.materializeCanonicalDeal`. Quest'ultimo riusa la transazione già aperta. Il fan-out dipende dal numero di annunci/alias per gioco; non c'è un limite di righe nel loop ready.
- `DealDatabase.applyBggGame`: parsing JSON di tutti gli override non nulli durante la transazione, anche quando riguardano altri BGG ID. Hotspot candidato, durata non misurata.
- `reconcileQueue`: invoca altre manutenzioni prima della propria transazione; il tempo totale del metodo non equivale al tempo di un singolo lock. Separare misure per fase e transazione.
- `recordSighting/applyAnalysis`: diverse query e aggiornamenti atomici per annuncio; chiamate legacy/canoniche separate dal radar. Non unirle senza verificare semantica e correttezza.
- `refreshLegacyDealBenchmarks/restorePriceFilteredFromBgg`: preparazione fuori dalla transazione, poi batch di UPDATE. Il recupero legge fino a 2000 righe; misurare dimensione batch e commit.
- `repairV51125CollisionCleanup`: decode bitmap e confronto avvengono PRIMA di `beginTransaction`; non attribuire al lock writer questo I/O sulla base della sola presenza nel metodo. Resta possibile costo di cursore/memoria.
- `inferMissingLanguages`: scansione con update riga per riga senza unica transazione esplicita; misurare writer churn separatamente.

### Rischio aggiuntivo: ordine monitor Java / writer SQLite

Due ordini diversi sono presenti nel codice:
1. metodi `DealDatabase` sincronizzati (per esempio `applyResolvedLink`, `updateVerifiedCurrentPrice`, `record`) prendono il monitor dell'helper e poi scrivono sul DB;
2. `MarketStore.applyBggMetadata` prende il writer SQLite e poi chiama `helper.materializeCanonicalDeal`, che è sincronizzato. Anche i claim chiamano metodi sincronizzati dello stesso helper dopo l'inizio della transazione.

Le lane Vinted/BGG del servizio condividono `db` e `market`; `BggEnricher` conserva l'helper ricevuto dal servizio. Quindi l'inversione di ordine è un rischio nello stesso processo, oltre alla contesa tra processi. Possibile sequenza: un thread mantiene il monitor e attende il writer; un altro mantiene il writer e attende il monitor. La presenza dei due ordini è dimostrata dal codice; l'occorrenza sul telefono e il suo contributo agli stalli NON sono dimostrati. Non estendere il monitor Java al radar come se fosse condiviso tra processi.

### Verifiche eseguite

Su snapshot esatto sopra: cinque script esistenti superati, 38 guard complessive:
- `startup_processing_lease_recovery_v51259.py`: 4/4, inclusa fixture SQLite di riavvio che preserva metadati retry;
- `queue_stall_recovery_v51267.py`: 11/11;
- `queue_liveness_diagnostics_v51277.py`: 7/7;
- `single_owner_startup_maintenance_v51269.py`: 8/8;
- `queue_single_owner_engine_order_v51219.py`: 8/8, inclusa fixture SQLite di ordinamento.

Sono guard del sorgente e modelli/fixture, non una prova di contesa reale Android né di assenza di deadlock. Nessuna build APK, misura sul dispositivo, riproduzione di crash/ANR o variazione di performance eseguita; nessun codice applicativo cambiato.

### Unico prossimo passo B1

Introdurre osservabilità limitata per claim, acquisizione radar, applicazione metadati/materializzazione e manutenzione. Separare attesa per apertura DB, ingresso nel monitor Java, acquisizione writer, corpo transazione e commit; registrare fallimenti anche prima dell'acquisizione. Correlare processo/PID/thread/operazione e timestamp con heartbeat/lease e trace thread. Persistenza asincrona, limitata e fuori SQLite; nessun I/O di misura mentre è detenuto il writer. Una misura solo dopo `beginTransaction` perderebbe i tentativi falliti e potrebbe non rilevare il blocco al monitor. Timeout, retry, filtri, soglie, pricing e schema restano invariati.

## Primo milestone
**Attribuzione contesa SQLite e stabilità delle code.**

1. Mappare i writer principali e le transazioni critiche nei tre processi.
2. Identificare operazioni che possono trattenere lock mentre fanno I/O, parsing o loop pesanti.
3. Rendere osservabili attese e transazioni lente senza aggiungere ulteriore contesa significativa.
4. Riprodurre con fixture/regressioni i casi di lease/heartbeat e database busy già noti.
5. Solo dopo l'evidenza, applicare il fix minimo e confrontare prima/dopo.

Nessuna modifica a soglie di riconoscimento, ranking, pricing o schema in questo milestone senza approvazione separata.

## B1 — diagnostica implementata, 2026-10-02

Richiesta utente «Vai»: misure circoscritte, nessun fix del comportamento. `DbContentionTrace` registra fino a 32 operazioni concorrenti e 32 aggregati per processo, con tempi monotoni, massimi/count/failure e operazioni in corso. Export daemon ogni 5 secondi fuori SQLite, file atomici limitati a 32 KiB per processo; conservata anche l'ultima sessione precedente. PID, versione e timestamp distinguono dati correnti e storici. Nessun titolo, URL o messaggio d'errore: solo classe errore e nomi tecnici.

Strumentati record legacy, applyResolvedLink/updateVerifiedCurrentPrice legacy, recordSighting/applyAnalysis canonici, claim Vinted/BGG singolo/batch, applyBggGame/applyBggMetadata e reconcileQueue. Fasi: SETUP, OPEN_DATABASE, ACQUIRE_WRITER, TRANSACTION, HELPER_CALL, IMPLICIT_WRITE, COMMIT, POST_TRANSACTION. PRE_TRANSACTION separa le attività preparatorie dopo l’apertura. maxImplicitWriteMs include il tratto autocommit (anche lookup/helper nel tail), non è tempo puro del lock. `maxBeginMs` comprende pool connessioni e SQLite begin, non misura esclusivamente il lock OS. `maxHelperCallMs` comprende attesa monitor e lavoro del metodo; thread BLOCKED e primi frame sulle operazioni >=250ms aiutano l'attribuzione senza modificare synchronized. Il monitor all'ingresso dei metodi sincronizzati DealDatabase non viene misurato direttamente. `maxBodyMs` esclude i tratti helper marcati. Massimi di fasi diverse possono appartenere a chiamate diverse: non sommarli. Copertura mirata, non di tutti i writer/query. `reconcileQueue` include sotto-manutenzioni nel totale; la fase iniziale non equivale a una singola apertura DB.

La diagnostica esistente esporta `dbContention`; letture solo di file su percorso background già presente. Files assenti/stale e errori export sono espliciti. Nessuna prova di riduzione crash/lock o overhead sul dispositivo finché mancano dati reali. La diagnostica generale usa ancora SQLite: se il suo export rimane bloccato, serve un ulteriore percorso file-only; non dichiarare che l'export risolve tutti i deadlock.

## Comunicazione e prova sul telefono

A ogni step backend indicare: cosa è stato fatto; cosa controllare; come eseguire la prova; cosa restituire. Distinguere test automatici da prova reale. Dopo build firmata e distribuita: aggiornare senza cancellare dati, copiare una diagnostica iniziale, attivare acquisizione e scorrere normalmente annunci di giochi su Vinted per 2–3 minuti, tornare in Ludo Scout e lasciare lavorare 5 minuti; copiare diagnostica finale completa e segnalare blocchi/crash e circa quando sono accaduti. Niente riavvio intenzionale tra le due diagnostiche. Se l'export non termina, riportare questo fatto senza insistere con molti tentativi. Controllare presenza di `dbContention`, versione/PID/at/freshness per processo. La prova prepara la baseline per attribuire attese, non deve dimostrare un miglioramento già promesso.

PR123 integrata in beta, merge `b7747372c656c79fed4c624091db9e2d30395490`. Head finale `80d6f345ae82cd5aab7f3a9eefb739cad8d3b300` verificato da CI330 (36936402812): regressioni, harness JVM, fixture SQLite, Android unit test, compilazione e APK di revisione superati. Review indipendente: corretti separazione PRE_TRANSACTION e copertura holder Vinted; nessuna criticità importante residua. Build firmata beta132 (36936664563), job110618411054: test/build/certificato atteso superati. Firebase upload riuscito 2026-10-01 22:46:41.218 UTC e distribuzione tester/gruppi riuscita 22:46:41.899 UTC, versione `5.12.114-db-contention-trace (1000132)`. Nessuna misura sul telefono o riduzione di crash/stalli ancora dimostrata.

Unico prossimo passo dopo consegna: confrontare le due diagnostiche del telefono; B1 rimane aperto.
