# Ludo Scout — Backend reliability, acquisition and recognition

## Scopo
Questo è il backlog persistente del workstream `backend`. L'obiettivo è aumentare affidabilità e throughput senza perdere correttezza: meno crash/ANR e lock, meno lavoro remoto inutile, più annunci utili trasformati in giochi corretti, meno review manuale e metriche leggibili.

Le modifiche vanno mantenute piccole, misurabili e reversibili. Nessun miglioramento viene accettato perché “sembra più veloce”: serve una baseline e un confronto dopo la modifica.

## Priorità operative

## Backend — evidenza reset 5.12.129, 2026-10-02

Utente autorizza «vai avanti» dopo audit del reset automatico e conferma di non aver eseguito reset manuali. PR148 backend/engine-reset-evidence aggiunge engineResetEvidence al report: legge il registro SQLite diag:fresh_start_reset con recordedAt originale, mixedOperationCount (non numero di annunci), riepilogo originale; conteggi market_listings per lifecycle e URL mancante, incluse RESET_LEGACY; totale/min/max observations e conteggio nella finestra inclusiva now−24h..now. Source sqlite, scope allStored, atomic=false espliciti. Record assente non prova mai che non ci sia stato reset; errori lettura distinti. Il record rappresenta solo l'evidenza attualmente conservata, non un journal completo di ogni reset; recordedAt è il timestamp scritto dal metodo, non un cutoff ricostruito.

Nessuna modifica al reset legacy, ai writer/schema, alla coda, ai filtri/soglie o alla rete; nessun ripristino/riattivazione dati. Query nuove soltanto nell'export diagnostico, non nella snapshot periodica UI. File condivisi MarketStore.java/VintedAccessibilityService.java toccati per SELECT/wiring; frontend127 e backend128 preservati. Costo reale export telefono ancora non misurato.

Fixture production-SQL RED prima delle query, GREEN dopo: lifecycle attivo/archiviato/null, missingURL, observations duplicate/bounds/databasevuoto e assenza/presenza registro con timestamp/riepilogo preservati, nessuna scrittura da SELECT. Review indipendente senza Critical/Important; minor date future riprodotto RED (count4 invece3) e corretto con limite superiore now, GREEN. Review finale senza blocchi. Fixture verifica SQL, non tutte le branch Java di errore/formatting sul dispositivo.
Head finale1c027bfb100ef6549fc849351339976846eb3138: CI387/run37006045746/job110834458407 success con regressioni complete/JVM/SQLite/Androidunit/compile/APK. Mergea7db47fde5aa30f87995372d7e71ccb7b94a78a3. Beta147/run37006470062/job110835813435 success; certificato C7DF7C31D0FE0D059307F4DE7B67BE5992DC87EC73E623CC9E8B4E87C63D8710 verificato. Firebase upload2026-10-02T12:28:14.6426913Z, distribuzione distinta tester/gruppi12:28:15.5957018Z (14:28Europe/Rome), release5.12.129-engine-reset-evidence (1000147).

Non attribuire ancora il passaggio missingLink482→0 al reset: esecuzione/epoca sul telefono resta da verificare. La129 aggiunge letture e lascia i percorsi precedenti invariati. Prova richiesta: installare senza cancellare dati, Motore→menu→Impostazioni e diagnostica, inviare report completo129 con engineResetEvidence; non fare nuove scansioni né reset o ripristini manuali prima della lettura. Nessuna nuova prova Vinted/rete richiesta. Unico prossimo passo backend: interpretare recordedAt/summary/RESET_LEGACY e bounds cronologia insieme al report128 per attribuire la sparizione; poi proporre soltanto la correzione dimostrata. Backend5/frontend7 aperti, nessun gruppo chiuso.

## Audit backend — assenza scroll e reset automatico, 2026-10-02 14:05 Europe/Rome

L'utente conferma «Non ho fatto nulla»: nessun reset manuale riferito. Diagnostica128 ricevuta: engineRun IDLE/waitingRuns0, latestCapture/coreRemaining/localOnly NONE; observationsLast24h0; ACTIVE144 tutte con link esatto; missingLink0 versus482 nella precedente124. Questo non prova elaborazione dei482. Scan opt-in OFF, nessun PROCESSING,1job deep;13richieste nel ledger tutte bundle,0link; no403/429 in questo ledger. Installazione128 da circa20s nel report: zero crash/ANR post-installazione non chiudeB1. Galleria404 resta aperta. Screenshot con trattini coerente con scope assente, non approvazione dell'intera UX.

Audit read-only su beta f44f3dc3bc502caaf66c3f4d165233641f692d38: EngineStartupMaintenance.run è chiamato automaticamente dal servizio coda e invoca applyFreshStart. Se SharedPreferences va_v3_diag.v5121FreshStartApplied manca/false, chiama freshStartLegacyBacklog(now): elimina tutti i processing_jobs, elimina observations anteriori al cutoff, archivia market_listings/deals incompleti come RESET_LEGACY e nasconde giochi senza annunci ACTIVE. Conserva annunci completi e gate di rete. Scrive poi diag:fresh_start_reset nel DB e flag/summary nelle preferenze. Il metodo è ancora nel codice attuale, non introdotto128. Preferences va_v3_diag sono scritte da processi diversi; perdita/staleness del flag è un'ipotesi, non un evento osservato. Nessuna chiamata automatica chiara a clearAll trovata: quella UI ha conferma esplicita.

Questo percorso è compatibile con perdita di cronologia e scomparsa del backlog, ma il report attuale NON espone registro fresh_start_reset, cutoff e conteggi RESET_LEGACY: esecuzione/epoca/causalità sul telefono ancora da dimostrare. Non dichiarare cancellazione fisica degli annunci (archiviazione) né attribuire la transizione alla128. Nessuna correzione/runtime/reset/ripristino effettuati. Unico prossimo passo: esporre in diagnostica read-only il registro SQLite fresh_start_reset e distribuzione lifecycle/osservazioni conservate, poi confrontare il telefono; non avviare nuove scansioni prima di preservare questa evidenza. Eventuale disattivazione di migrazione obsoleta/ripristino dati richiede decisione distinta, senza riattivare in massa job o aggirare gate. Backend5/frontend7 ancora aperti.

## Backend — diagnostica LOCAL_ONLY 5.12.128, 2026-10-02

Fix autorizzata «vai», PR145, branch backend/engine-diagnostic-scope. Audit del percorso applyAnalysis → LOCAL_ONLY/DEFERRED_LINK → promoteDeferredVintedBatch/enqueueJob: BGG qualificato non equivale a lavoro remoto automaticamente idoneo. LOCAL_ONLY può derivare da decisione prezzo/lingua/metadati; nessun bug di scheduling dei cinque annunci reali dimostrato senza dati delle loro righe. Il promoter crea piccoli batch del run proprietario solo per DEFERRED_LINK, preservando gate review/identità e precedenza urgente; pause/budget restano invariati.

Discrepanza dimostrata: engineRangeCounts esclude LOCAL_ONLY come trust hold, engineCoreRemainingSummary prima lo includeva. La lista diagnostica ora esclude anche LOCAL_ONLY/AUTO_EXCLUDED/AUTO_FILTERED e rispetta gli stati NULL come il conteggio del run. Riporta scope=activeRun, unit=listings, limit=8: count è il numero di righe elencate, non il totale quando superiore a8. engineLocalOnly è un conteggio separato degli annunci BGG qualificati ACTIVE del run con identità Vinted incompleta; nessuna causa prezzo è inventata e non rappresenta tutti i trust hold.
vintedMissingBreakdown mantiene scope globale ACTIVE senza URL, separa localOnly qualificati da eligible. eligible qui significa qualificazione BGG esclusi LOCAL_ONLY, non l'intero predicato del promoter né soltanto il run corrente. total=notBggQualified+eligible+localOnly, eligible somma i nove esiti; le diverse letture diagnostiche non sono snapshot atomica e possono divergere durante aggiornamenti concorrenti.

Solo query/copy diagnostici, wiring nel report e test/workflow/versione. Nessun writer coda, schema, algoritmo/soglie, filtro prodotto o rete/pacing modificato. MarketStore.java e VintedAccessibilityService.java condivisi toccati soltanto per diagnostica; frontend127 preservato.

Verifiche: nuova fixture sulle query reali SQLite RED prima del fix, GREEN dopo; fixture missing-link esistente aggiornata per colonna aggiunta e superata. Test locale supplementare su8casi review manuale/variante/verification hold/NULL superato. Revisione indipendente senza Critical/Important; minor copertura supplementare locale non integrata nella fixture CI. CI383/run37000110023/job110815617607 e CI384/run37000467757/job110816760224 success: regressioni complete/JVM/SQLite/unit Android/Java compile/APK. Riallineamento headacb59cc1428e2343dc12d7a02de89f6b8b7f5349 conserva soltanto aggiornamenti documentali frontend rispetto al primo headtestato d6c34a77b81715a05c37a4f5e2f3f26eb0d77aa7. Merge dac4da1cf82352e8fbeb2e49d3e470e7093e0eee.
Beta146/run37000757503/job110817671805 success: test/build/certificato C7DF7C31D0FE0D059307F4DE7B67BE5992DC87EC73E623CC9E8B4E87C63D8710 verificati. Firebase upload 2026-10-02T11:28:05.5857629Z e distribuzione tester/gruppi distinta11:28:06.1511281Z (13:28Europe/Rome), versione5.12.128-engine-diagnostic-scope (1000146).

Nessun telefono/emulatore, nessuna riduzione403 o miglioramento prestazioni dimostrato. Prova: installare128 senza cancellare dati, Motore→menu→Impostazioni e diagnostica, inviare report completo; non servono nuove scansioni né richieste Vinted forzate. Verificare engineCoreRemaining, engineLocalOnly e vintedMissingBreakdown insieme a versione/run/gate. Unico prossimo passo backend: leggere la diagnostica128 per distinguere lavoro remoto effettivo, attesa gate e LOCAL_ONLY, attribuendo le cause prima di qualunque correzione scheduling. Backend5/frontend7 aperti, nessun gruppo chiuso.

## Checkpoint backend — ricerca Motore, 2026-10-02 12:51 Europe/Rome

Richiesta utente: conservare la ricerca come know-how e rimandare ogni nuova indagine/implementazione alla prossima chat. Report originale: [docs/research/ludo-scout-engine-report-2026-10-02.md](docs/research/ludo-scout-engine-report-2026-10-02.md). È un audit storico riferito principalmente a 5.12.41: ipotesi, percorsi e raccomandazioni vanno riconfermati sull'ultimo beta. PROJECT/AGENTS/STATE già esistono; non ricrearli. Citazioni filecite/search del report appartengono alla sessione originale e non sono prove navigabili nel repository.

Diagnostica ricevuta: 5.12.124-motore-overview; non equivale alla versione più recente sul branch. Run ACTIVE da circa27h, corePending2, waitingRuns5; REMOTE_LIMIT con49/60 richieste locali. missingLink482, eligible233, queued19, awaitingAttempt102, other107. engineCoreRemaining elenca7 righe,5 LOCAL_ONLY senza job e2 PENDING_ENRICHMENT: non confrontare direttamente questo ambito con coreRemaining2 del run attivo. Anche i diversi contatori possono avere ambiti/epoche differenti. Zero crash/ANR dopo installazione124 nel log; vecchio SQLiteBusy storico, B1 non chiuso. Galleria3/5 HTTP404. photoMatcher tutti zero non prova mancato funzionamento né miglioramento.

Unico prossimo passo: nella nuova chat backend, audit read-only del percorso annuncio BGG qualificato → stato LOCAL_ONLY/DEFERRED_LINK → creazione/riprogrammazione job, ricostruendo ambiti, criteri e motivi per cui annunci idonei restano senza job. LOCAL_ONLY può essere intenzionale: non dichiarare bug prima della verifica. Poi proporre una correzione piccola solo se la causa è dimostrata. Non aumentare budget/frequenza né ignorare backoff.

Know-how da rivalutare dopo questa check: matcher Java BGG già indicizzato; possibile costo JS nel fallback refusi byWord; descrizione solo come evidenza conservativa di variante/conflitto; riuso snapshot/cache prima di rete; corpus casi reali e metriche per stadio senza duplicare telemetria esistente. Nessuna di queste proposte è approvazione di cambio soglie/schema/architettura.

Checkpoint solo documentale: nessun codice/runtime, test Android, build, APK o prova telefono nuova. Base Git letta: beta d0f05487a613e42cfc5f9318cf1e9f12e5c027e6. Stato e verifiche frontend/backend precedenti preservati; frontend7 aree/backend5 gruppi, nessuno chiuso.


## Backend — B2 riuso foto 5.12.123 consegnata, 2026-10-02
Fix autorizzata «Vai», branch backend/photo-cache-reuse, PR134. Matcher normale legge la firma dHash già salvata prima di HTTP: solo URL esatta (normalizzazione &amp;), hash SQLite integer e last_at entro24h/non futuro. La chiave PhotoIdentity da sola non basta: crop/taglie/URLfirmate diverse possono produrre hash diversi. Miss/errore/tabellaassente/scaduto/corrotto mantengono il percorso precedente; lettura senza ensure/DDL. Algoritmo VisualCoverMatcher e soglie invariati, nessuna nuova rete o cambio schema/budget/pausa/retry. Una sola riga perphoto_key resta: crop alternati possono produrre miss sicuri; nessuna cache completa o singleflight aggiunta.
Ledger SQLite esistente espone photoMatcher separato dai contatori pagine: cacheHits/downloadAttempts/httpResponses/http403/http429/errors; scope matcher-only. Non include tutte le immagini UI né tutte le richieste fisiche dei redirect; errors conta eccezioni del percorso, non tutti i possibili decode null. Misure best-effort come ledger esistente; transazioni brevi sul worker, overhead sul telefono non misurato. Epoch inizializzata dentro transazione prima del primo evento, altrimenti la lettura diagnostica cancellava i primi contatori.
RED CI36336987336608/job110775240966 riproduce downloadripetuto. CI36736988497710/job110778932709 riproduce perdita primo evento foto/epoch. Harness esegue matcher/algoritmo reali con adattatori Android/HTTP controllato;100casi paritàbitmap/hash, scoreliteral/dHashzero, URLcropdiversa, errorcache,403/I/O/noobs. SQLite esegue query reale conURLesatta,ampersand,hashcorrotto,scaduto/futuro. Telemetry harness esegue metodi reali record/epoch/helpers con boundarySQLite; preserva contatori e non modifica pagine. Localmente fixtureSQLite e syntax passati; javac/SDK assenti. Revisioneindipendente finale su59353bfb senza Critical/Important dopofixepoch.
Riallineato a beta7950191807874296cf376a920a8220b872245060 (frontend122); headfinale59353bfb92d18363d591d33f9353ee8507cdcd62 supera CI36836988621031/job110779327243 (regressioni complete, Androidunit, Javac, APKreview). PR134 integrata merge592c492e3b25ee00e998b551a38cacd246438d5e. Beta14136989029274/job110780639505 completata success: test/build/certificato attesoC7DF7C31D0FE0D059307F4DE7B67BE5992DC87EC73E623CC9E8B4E87C63D8710 verificati. Firebase upload2026-10-02 09:23:15.574UTC e distribuzione tester/gruppi09:23:16.067UTC (11:23Europe/Rome), versione5.12.123-photo-cache-reuse(1000141), includefrontend122.
B–C120 restano sospese, A120 ricevuta; non dichiarare diagnostica120/reboot validati né B1 chiuso. Nessuna prova telefono123 o riduzione403 dimostrata. Prova breve: installare senza cancellaredati, diagnostica iniziale,2–3min uso/scansione normale e diagnostica finale; se comparepausa interrompere e inviare finale senza aspettare/scavalcare/forzareaggiornamenti. Matcher può non essere invocato in quella sessione: zero nuovi contatori non è fallimento. Unico prossimo passoB2: confrontare foto/pageledger nelle due diagnostiche, quindi audit richieste pagine e cachecross-processo. Backend5/frontend7, nessungruppo chiuso da questo sottopasso.


## Priorità attiva — B2 Vinted, audit 2026-10-02
Richiesta utente: sospendere la prova B–C120 e dare priorità assoluta ai blocchi Vinted/riduzione chiamate. B1 resta aperto; backend5/frontend3 all'ultimo conteggio verificato. Nessuna nuova prova telefono richiesta prima di un intervento misurabile.

Audit read-only dei sorgenti beta correnti: VintedPublicSession, VintedLinkResolver, VintedCandidateSnapshotStore, AutoLinkResolver, QueueJobRunner, SellerBundleScanner, VintedPhotoMatcher, VintedPhotoHashCache e VisualCoverMatcher.
- 403/429 inducono pausa LOCALE fissa45min; non è una durata letta da Vinted. 403 non dimostra da solo rate limit. A120: gateREMOTE_LIMIT30/60, ledgerultimo link_catalog/catalog/403; nessuna causa remota dimostrata.
- Gate delle pagine condiviso SQLite; cache risposte solo per processo,16URL/10min. Quindi coordinamento richieste non equivale a cache condivisa.
- Resolver cerca fino2query e verifica la pagina articolo; catalogStructured=0 nella baseline, quindi fast path strutturato non utilizzabile. Snapshot/batch persistenti già esistono, non vanno duplicati.
- Confronto foto: applyPhotoEvidence seleziona fino6candidati; VintedPhotoMatcher usa direttamente HttpURLConnection, fuori dal gate/ledger delle pagine. Salva dHash ma non legge cache prima di HTTP. Quindi30/60 e physical non rappresentano tutto il traffico immagini. Nessuna attribuzione causale dei403 a questo traffico.
- VisualCoverMatcher ha già overload similarity(queryHashes,candidateHash) con stessa distanza/crop del confronto bitmap; possibile riuso senza cambiare soglie. Verificare identità foto, equivalenza risultati, cache assente/corrotta, errori DB e zero richieste su hit prima di attivarlo; evitare ensure/DDL in lookup read-only.
- Intake A120 mostra0ID su14219card; numeratore legacy esplicito, non prova possibilità di recupero automatico link nello scroll. Nessuna nuova rete di probe avviata.

Unico prossimo intervento raccomandato: riusare le firme delle foto già scaricate nel confronto normale, conservando algoritmo e soglie, e misurare separatamente hit/download/esiti foto. Non modificare pausa, budget, schema o regole di matching. Test automatici e confronto stesso punteggio precedono build; successivo audit cache pagine cross-processo resta distinto. Questa consegna è solo audit/documentazione, nessunaAPK né riduzione403 verificata.


### B1 — Stabilità e performance: crash, ANR, SQLite, memoria e code
Resta aperto; B2 ha priorità per richiesta utente. Ricostruire dove nasce la contesa tra processi UI/radar/coda e quali operazioni tengono il database occupato più a lungo.

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


## Verifica telefono118 A/B/C/D — 2026-10-02 09:39 Europe/Rome
A→B: cardsParsedTotal14019→14169 (+150 letture), osservazioni/firme ultima sessione116;15batch persistiti senza failure,31 chiamate salvataggio completate e60 quarantene, tentativi91. Funnel116=65INACTIVE+7AUTO_FILTERED+6CLASSIFIER_BLOCKED+3BGG_UNRESOLVED+5BELOW_RATING+30BGG_QUALIFIED; latest30BGG/3Vinted/2ready/16held (subset sovrapposti). INACTIVE aggrega cause diverse, non chiamare65 non-giochi. B→C OFF: scans9057/cards14169/analyses11857/committed31/quarantined60 invariati; eventi crescono, nessuna nuova osservazione (391 su24h invariato). C→D dopo riavvio: PID3343→4489, stesso counterEpoch1790925030456, totals conservati, eventi454508→454548; motoreREADY31181 e radarCONNECTED. Verificati acquisizione ON, OFF, salvataggio batch e continuità counters nel test, non B1 interamente risolto.
Ledger A→D: physical1769→1783 (+14), http2001732→1746 (+14),40322 invariato; linkedNow395→400, non tutti link attribuibili alla sessione nuova. Coda riparte anche dopo reboot. Nessun nuovo crash/ANR visibile nei testi; D installBoundary regredisce1790925029460→1790900195183 e memoryAfterInstall1 si riferisce a vecchio LOW_MEMORY1790903025646 (prima install118), non nuovo episodio. Non usare boundaryD per giudicare nuovi eventi senza timestamp/versione.
Difetti residui: D eventAt/localAnalysisLastBatchAt/lastBatchSize riprendono vecchi valori prefs pur con totals corretti; denominator vintedIdsCaptured/cardsParsed resta14019 stale. Nuovo reconcileQueue startup totale4922ms, body1323ms, begin0: non è prova lockwriter5s; indagare preparazione/manutenzione. Prossimo passo: separare cause INACTIVE del funnel e correggere freshness/provenienza dei campi diagnostici; no nuove prove richieste ora. B/C/D troncati in coda, sezioni centrali disponibili; non dichiarare verifica dei campi mancanti.


## Backend — capture diagnostics 5.12.120 consegnata, 2026-10-02
Task autorizzato dopo A/B/C/D118: diagnosticare cause INACTIVE e freshness/provenienza dopo riavvio. Branch backend/capture-diagnostic-truth, PR130, base frontend119 integrata. Funnel v2 read-only e stessa deduplica per firma: distingue SOLD/REMOVED/USER_HIDDEN/UNKNOWN/OTHER da AUTO_FILTERED/AUTO_EXCLUDED; marker persistiti separano non-game, collisione e tipo BGG incompatibile. Nessuna inferenza da testo errore, nessun cambio filtri/soglie/schema/queue/network; filtri generici senza motivo strutturato restano AUTO_FILTERED.
Owner file esistente v2 esteso con coppie timestamp/valore evento, scansione e batch; snapshot v3 coerente sotto monitor. Proprietà valide del file autorevole prevalgono su seed più alti; migrazione solo per proprietà mancanti/invalide. Timestamp importati da vecchia snapshot SQLite marcati sqlite-migration-unverified; non ricostruiscono dati storici persi. Eventi incrementano subito memoria; publish coalescer ogni callback programma anche la coda quieta. Handoff AtomicReference.getAndSet evita perdita di nuova snapshot. I/O su executor, drenaggio finale preservato; resta possibile perdita di delta in caso kill prima flush, no garanzia zero-loss. Denominatore cardsParsed nella diagnostica ID usa intake autorevole, numeratore legacy esplicitamente indicato.
SystemExitHistory usa PackageInfo.lastUpdateTime per installBoundary; espone app/boundarySource/exitHistoryBoundary e fallback espliciti se PackageInfo indisponibile, evitando di attribuire vecchi eventi a nuova build per storico troncato al reboot. Non risolve cause crash/ANR.
RED eseguiti: SQL INACTIVE_SOLD e marker cause; CI35136981010186 funnelRED, CI35236981112905 timestamp/typeRED; CI35336981508778 freshness/service passati ed exit-historyRED; CI35536981907925 quiet-tailRED. Test JVM reload/corruzione/seed/initialization/executor più fixture SQLite e real summary con boundary Android stub. Review indipendente finale: nessun Important/Critical dopo quiet-tail, seed authority, handoff atomic. Locale fixture/source/syntax passati; javac/Android SDK assenti, test ProcessCrashJournal completo locale indisponibile per manifest assente. Head finale6385af2ec4424339dac878141f150e3de526bdef allineato a beta b9cf6cd: CI35736982371329 job110759533441 completata con successo (regressioni complete, JVM/SQLite, Android unit test, Java compile e review APK). PR130 integrata con merge948da221bf380f347a388dde4b0766b16aa9df97. Build firmata beta13836982752743 job110760772310 completata con successo, certificato atteso C7DF7C31D0FE0D059307F4DE7B67BE5992DC87EC73E623CC9E8B4E87C63D8710 verificato nel log. Firebase upload riuscito2026-10-02 08:16:41.247UTC e distribuzione tester/gruppi riuscita08:16:42.191UTC (10:16Europe/Rome), versione5.12.120-capture-diagnostics(1000138). Verifica telefono120 ancora aperta; no claim stabilità/performance risolta.
Prova prevista dopo distribuzione verificata: A iniziale; scansione ON2min/OFF, attesa3min e B; riavvio senza reinstallare/cancellare, attendere motore pronto e C. Restituire3diagnostiche complete e orari, eventuali blocchi; nessuna attesa40min richiesta per questo task diagnostico. B1 e altri gruppi non chiusi: backend5, frontend3. Unico prossimo passo: ricevere A/B/C120 e verificare freshness/contatori dopo reboot, quindi usare cause funnel per indagare poche conversioni e run storici.

