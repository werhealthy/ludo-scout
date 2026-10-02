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


## B1 — baseline telefono 5.12.115, 2026-10-02
Due diagnostiche ricevute, intervallo circa 706 s; stessi PID queue/radar/ui, export recenti. Delta cardsParsedTotal +229 (letture di card, non necessariamente uniche); osservazioni +219 e run observations=219/unique=219 (firme locali, non ID Vinted certi). market active +21, firstSeen24h +21; non equivalgono a tutti gli annunci della sessione perché possono essere già noti. Run games=13/bgg=13/vinted=0/ready=0/held=4; games conta annunci qualificati con rating>=6 e visibilità/filtri, non giochi distinti né tutti i giochi riconosciuti. I 4 held sono parte dei 13, non una fase additiva. coreRemaining=9 nel riepilogo; lista diagnostica mostra 8 elementi: snapshot/metodi separati, coerenza da verificare.
Delta classifierBlocked +174 indica osservazioni senza allowPriceModel, non 174 non-giochi unici. NON sottrarre 174 da229 per derivare giochi. NON_GAME viene escluso prima delle osservazioni; manca funnel completo per run con motivi mutuamente esclusivi. analysesStored +216 è incremento della dimensione batch, anche con continue/quarantene prima del salvataggio: non prova 216 record salvati o match BGG. Globali/catalogo e sessione hanno denominatori diversi.
Una sola nuova richiesta fisica Vinted, http403 +1; zero nuovi link. VintedPublicSession trasforma403/429 in REMOTE_LIMIT; gate finale attende circa39.6min. Un403 può avere cause diverse, non prova da solo rate limit remoto. Batch locale: snapshots=1/stale=1/candidateEdges=0/applied=0, nessun candidato riutilizzabile in quel batch.
Misure mirate: radar recordSighting219 max55ms/maxBegin2ms; applyAnalysis24 max55ms; queue applyBggMetadata13 max51ms; reconcile max1198ms. Nessun failure misurato o scope attivo bloccato nel dump; crash/ANR afterInstall=0 e vecchio crash journal invariato. Non esclude lock in writer non coperti o episodi fra snapshot. Matching locale ultimo batch3: matched0/quarantined3/timedOut1/elapsed30184ms; caricamento indice27.8s e token26.7s, hotspot separato da indagare. activityIndicator.elapsedMs190115 è anomalo; snapshot112ms, non attribuire190s a render/query senza trace.
Prossimo passo backend: ricostruire funnel per run (unità/firma/ID/deduplica e motivi) e percorso risoluzione link con403/candidati cache. Nessun cambio di soglie/retry/timeout o dichiarazione B1 risolto. Nessun nuovo APK in questo checkpoint. Per prova successiva basta una diagnostica dopo la scadenza del gate, senza forzare richieste, con eventuali blocchi/crash.


## B1 — follow-up telefono 5.12.116, 2026-10-02 01:53 Europe/Rome
Pausa precedente terminata01:45:45; gate finale PACING, circa40s al timestamp export, budget10/60. Rispetto a Fine115: ledger physical1643→1652 (+9), item1046→1055 (+9), http2001614→1621 (+7), http40316 invariato, linkedNow388→390 (+2). Non prova che i due link appartengano alla sessione219: engineRun ora seleziona un run storico di circa31.3h, observations101/games20/vinted6/ready2/waitingRuns4. Queue viva, claim8 nel nuovo processo, lease deep14.5s; pending51 e deep22 non sono nuove osservazioni.
Nuova evidenza ANR :radar alle01:16:32 (1790896592920), precedente install11601:30:58. Dopo install116 crash/ANR=0, ma NON dichiarare stabilità risolta. Radar previous è PID26559 senza scope; il PID20161 che acquisiva la sessione non è preservato qui. Nessuna stack ANR in questo testo, nessuna rootcause dimostrata; trace attuale/previous non esclude attese al main thread prima dello scope.
Counter intake regrediti cards14248→14019, scans9056→9010, analyses11982→11766; osservazioni SQLite24h497 invariato. Non prova perdita DB; indagare snapshot SharedPreferences multi-processo/source fallback e riavvio radar. Non calcolare delta acquisizione usando questi contatori attraverso update/restart. Global catalogEligible68→66, noBlockingJobs79→66 indica13 bloccati a quella fase; confronti non sono un funnel della sessione.
Prossimo passo investigativo prioritario: ANR radar e affidabilità contatori; funnel/link restano aperti. Nessun nuovo codice/APK in questo checkpoint. Utente non deve aspettare un'altra pausa40min; richiedere contesto dell'ANR01:16, nessuna diagnostica ripetitiva finché non cambia condizione o strumentazione.


## B1 — fix radar reliability 5.12.118 verificata e distribuita
Richiesta utente: analisi più ampia e consegna fix verificata. Correzione circoscritta: selezione batch SQLite e salvataggio risultati WebView su corsia FIFO RadarPersistence; WebView e mappe hint rimangono sul main. Un solo batch/selection in volo, timer di ripresa anche su errore, cleanup drena lavoro accettato e contatori prima di chiudere DB fuori main. Altri accessi DB nella scansione/startup legacy restano: non dichiarare tutti gli ANR risolti.
Contatori RadarIntakeCounters: file owner-only fuori SQLite, inizializzazione async da massimo file/snapshot SQLite/legacy con delta arrivati durante bootstrap; prefs sono mirror di compatibilità. Snapshot v2 indica app/PID/epoch/error e finestra2s; persistenza coalesced e flush finale ordinato. Morte improvvisa può perdere la coda di incremento non ancora salvata; nessuna garanzia fsync/zero-loss. analysesStored resta tentativi legacy; nuovi analysisCommitted/analysisQuarantined contano esiti del nuovo percorso, non annunci unici né gioco distinto.
Diagnostica engineLatestCapture distinta dal cursore engineRun. engineCaptureFunnel partiziona firme osservate per stato corrente (no canonical, inactive, auto-filtered, classifier blocked, pending analysis, missing game, unresolved BGG, rating pending/below6, hidden, qualified). La somma dei bucket corrisponde alle firme osservate della finestra, non a ID Vinted certi. Usa bridge canonico deterministico MIN(id) se duplicato storico; esclude i NON_GAME scartati prima di observations. Non è ancora funnel cronologico completo di tutte le card viste; riletture raw e rifiuti pre-observation sono cumulativi separati. Nessun cambiamento a classificatore, soglie, schema, pacing/rate limit o servizi remoti.
Test-first guard riproduceva salvataggio sul callback; fixture reali SQLite per duplicati, finestre, firma fallback, null match/rating/visibility, bucket; JVM sul recorder reale (seed/restart stale/corrupt/pending init/flush finale) e executor reale (FIFO/nonblocking/drain/late rejection); harness esegue metodo reale service di selezione con boundary Android stub (singleflight, worker/main, destroy, error/wait timer). Review indipendente ha individuato flush finale mancante, corretto. PR128 integrata in beta (merge `6ce17e120af78bd9bc4529c556360cbe3b32ceb1`). Head finale `ec852871d643afba7b25f7564eb4475f0141f1ca` ha superato CI345 (36946980942): regressioni, JVM real counter/executor/service-boundary, fixture SQLite, Android unit test, compilazione Java e APK. Review indipendente: flush finale e overlap fra istanze del servizio corretti; nessuna criticità Critical/Important residua. Build firmata beta136 (36947271102), job110652128114 completata con successo; signer SHA256 atteso `C7DF7C31D0FE0D059307F4DE7B67BE5992DC87EC73E623CC9E8B4E87C63D8710` confermato nel log. Upload Firebase riuscito2026-10-02 00:46:17.886UTC e distribuzione tester/gruppi riuscita00:46:18.304UTC (02:46Europe/Rome), versione `5.12.118-radar-reliability (1000136)`. Nessuna prova su telefono della118 ancora ricevuta; causa ANR115 e guadagni di performance non dimostrati.
Prova telefono dopo distribuzione: diagnostica iniziale; scan2–3min; lavoro5min e diagnostica finale; riavvio telefono senza reinstallare/cancellare dati, attendere readiness e terza diagnostica. Confrontare epoch e contatori v2 non decrescenti (salvo flush window), stessa latestCapture/funnel e somma bucket; segnalare nuovi crash/ANR/eventuali blocchi. User dice nessun blocco notato alle01:16 del precedente ANR115.


### Verifica telefono118 aggiornata
Prova telefono: A diagnostica iniziale; scansione ON2–3min, OFF, attesa lavoro5min e B diagnostica; Vinted con scansione OFF per1min e C diagnostica; riavvio telefono senza reinstallare/cancellare dati, attendere motore pronto e D diagnostica. Verificare acquisizione ON, assenza acquisizione OFF, continuità contatori nuovi fra B/C/D, breakdown ultimo scroll distinto run storico, elaborazione/pausa Vinted e nessun nuovo blocco/crash/ANR. Non richiedere interpretazione tecnica all'utente: restituisce4testi completi con eventi/tempi. Counters118 non ricostruiscono incrementi già persi nelle vecchie versioni. Finestra coalescing2s e morte improvvisa possono perdere coda non salvata: no garanzia zero-loss.
Ulteriore review: overlap di ricreazione servizio corretto con owner static per processo e diskLock serializzato attorno a initialize/persist. JVM testa anche due writer concorrenti, seed ripetuto ignorato e reload finale. User conferma nessun blocco/messaggio notato alle01:16, ma ANR Android115 rimane evidenza senza stack/rootcause.
