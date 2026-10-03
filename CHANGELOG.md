# Ludo Scout — Changelog

## 5.12.153 — Ludo: esplorazione e bilancio

- Ludo integra ricerca Vinted, cerchi giornalieri con controlli completati e delta dalla baseline browser, dettagli dei risultati e lavoro corrente.
- Preset per rilevanza/novità/prezzo, query casuali dal catalogo locale, intervallo personalizzato, pagine1..10 manuali; pausa/play e cattura secondarie a icone.
- Preservati frontend151, dati, cattura/sicurezza, pricing/filtri; nessun reset/schema/dipendenza/rete automatica. Verifiche/consegna in STATE, prova telefono ancora necessaria.


## Frontend151 — offerte mensili e collezione a coppie, distribuita

Feedback23:52: composizione Ludo148 accettata provvisoriamente; richiesti padding più curati, dato utile al posto di BGG>6 e due giochi grandi su scaffali profondi. Distribuita **5.12.151-ludo-offers-shelves (1000174)**, localCode200. Integrato e preservato backend150; Motore non modificato dal frontend.

Esplorazione conserva i giochi distinti raccolti nel mese e mostra quanti hanno un’Offertona ancora attiva. Candidati mensili in sola lettura con gli stessi gate delle card fidate, prezzo rivalutato dal DealEvaluator esistente (GREAT_BUY), identità BGG deduplicate. Una sola istruzione SQLite mantiene coerenti i due numeri mentre il Motore aggiorna il database; LEFT JOIN conserva lo zero reale se non ci sono offerte. Mese Europe/Rome, dati mancanti/caricamento/errore distinti da zero. Nessuna nuova soglia, filtro, schema, richiesta di rete o dipendenza.

Collezione: massimo due scatole grandi per ripiano verticale, una se viewport/font non consentono due leggibili. Media224dp, proporzioni reali, piano trapezoidale profondo, pareti laterali, bordo/ombra e nomi sotto il piano. Preferiti, rating, acquisti, vendite, ricerca, ordinamento e archivio preservati. Padding stanza/selettore/ricerca e CTA adattivi; metriche impilate con font grande. Nessun asset nuovo.

PR199 HEAD1326837e7c0a16238896909a7cd4e3e9005cd807; merge8ce1a3967922c475f889e1009b95ac0dd5dfd30c. Riallineamento beta backend150 senza conflitti nel MainActivity tramite merge a tre vie; identità release avanzata da150 a151. CI finale37071427103/job111051526801 success con suite regressioni, fixture SQLite mensile/fiducia/snapshot, JVM DealEvaluator/capacità, unit Android/pricing, compile Java e APK. Primo test locale rosso sulla metrica ridondante, poi verde; run37070265158 rosso sulla vecchia aspettativa3scatole, aggiornato a2 mantenendo partizione/ordine/all-games. Review indipendente: race fra le due letture individuata e corretta; nuova review045bfa8 senza problemi residui. Java/Android locali indisponibili; non confondere adapter JVM con un dispositivo Android reale.

Android beta174/run37071877920/job111052968765 success. Certificato atteso C7DF7C31D0FE0D059307F4DE7B67BE5992DC87EC73E623CC9E8B4E87C63D8710 verificato2026-10-02T22:24:14.0722914Z; upload Firebase151/1000174 confermato22:24:56.8509210Z, distribuzione ai tester/gruppi confermata separatamente22:24:57.8198063Z.

Telefono ancora da verificare: conteggio Offertone rispetto alle card reali, padding e font200%, coppie/scaffali profondi con molte copertine/forme diverse, swipe/scroll/ritorno e azioni collezione. Nessuna accettazione pixel/TalkBack o prestazione dichiarata dalla sola CI. Installare senza cancellare dati. Frontend7/backend5 aperti; il feedback generale148 non chiude le verifiche151. Unico prossimo passo: verificare visivamente151 sul telefono; poi riprendere Catalogo unificato + Bundle/Esplora già approvati.

## 5.12.150 — Contatori Motore coerenti (2026-10-02)

- Gli annunci bloccati dal classificatore non restano più tra quelli da riconoscere.
- La coda indica analisi locali e attività persistenti pendenti, separatamente dagli elementi con dati incompleti.
- Diagnostica delle stesse fasi mostrate sullo schermo; pronti per gioco distinti dai singoli annunci completi.
- Nessun reset, migrazione, modifica ai filtri, alle soglie prezzo o alle richieste Vinted.


## 5.12.83 — Salute della misura Motore (2026-09-30)

- La diagnostica distingue i tentativi di campionamento riusciti da quelli falliti e mostra solo la classe dell’errore, senza messaggi o dati degli annunci.
- Serve a capire perché il supervisore risulta attivo mentre i tempi misurati restano vecchi.
- Solo diagnostica: nessuna modifica alla coda, alle richieste, alle soglie o alla pubblicazione.
- Regressione: `regression/engine_performance_metrics_v51279.py` e `regression/EnginePerformanceMetricsRegression.java`.


## 5.12.82 — Esclusione delle sessioni storiche dai tempi (2026-09-30)

- Il misuratore non usa più sessioni di osservazione già presenti nel database all’avvio come nuovi campioni; evita così tempi del primo risultato di giorni, calcolati da un’ultima osservazione storica.
- Mantiene le misure dei run ancorati all’avvio del misuratore o entro un battito del supervisore, e continua a separare tempo classificato, intervalli non osservati, primo risultato e completamento.
- Solo diagnostica: nessun cambio al flusso del Motore, alle soglie, ai limiti o alla pubblicazione.
- Regressione: `regression/engine_performance_metrics_v51279.py` e `regression/EnginePerformanceMetricsRegression.java`.


## 5.12.81 — Primo risultato senza attesa di completamento (2026-09-30)

- La mediana e il tempo peggiore del primo risultato includono anche i run ancora aperti; il numero `firstResultN` mostra quanti casi hanno già prodotto un risultato.
- Il completamento resta separato e conta solo i run terminati. Il contatore `unobservedMs` continua a mostrare gli intervalli tra campioni oltre la finestra misurabile.
- Solo diagnostica: non cambia priorità, pubblicazione, soglie o richieste di rete.
- Regressione: `regression/engine_performance_metrics_v51279.py` e `regression/EnginePerformanceMetricsRegression.java`.


## 5.12.80 — Copertura dei campioni (2026-09-30)

- La diagnostica mostra in `unobservedMs` il tempo tra campioni che supera i 12 secondi misurabili, invece di nasconderlo nei tempi attribuiti alla coda.
- Questo intervallo può includere servizio inattivo, app sospesa o campioni in ritardo; non viene presentato come tempo di elaborazione né come errore della coda.
- La versione dei dati diagnostici avanza e i vecchi totali non confrontabili vengono azzerati. Nessun cambio a elaborazione, pubblicazione o limiti di rete.
- Regressione: `regression/engine_performance_metrics_v51279.py` e `regression/EnginePerformanceMetricsRegression.java`.


## 5.12.79 — Tempi reali del Motore (2026-09-30)

- La diagnostica misura il tempo Vinted in attesa di pacing/limiti, il tempo con lavoro rivendicabile, il tempo con lease attive e gli altri intervalli osservati.
- Registra il tempo dal termine osservato dello scroll al primo esito e al completamento; mostra mediana, peggiore e numero di run, sugli ultimi 20 completati.
- I dati contengono solo tempi e contatori aggregati; nessun titolo o annuncio. Nessuna modifica al motore di pubblicazione, alle soglie o ai limiti di richiesta.
- Regressioni: `regression/engine_performance_metrics_v51279.py` e `regression/EnginePerformanceMetricsRegression.java`.

## 5.12.78 — Coda dei collegamenti differiti (2026-09-30)

- Conteggio e promozione dei collegamenti differiti limitati agli annunci BGG confermati e senza revisione manuale, variante o verifica incerta già aperta.
- Annunci in revisione e trattenuti restano invariati e non vengono promossi automaticamente nella finestra di lavoro core.
- Nessuna modifica a soglie, rate limit, schema, dati storici o pubblicazione.
- Regressione: `regression/regression_deferred_run_truth_v51278.py`, eseguita in CI PR e beta.

## 5.12.77 — Diagnostica liveness coda (2026-09-29)

- Il testo diagnostico copiabile riporta l’ultimo controllo del supervisore, l’ultimo tentativo WorkManager con esito e motivo, e l’età del lavoro Vinted in attesa più vecchio.
- Solo diagnostica: soglie di recupero, priorità, pubblicazione e dati storici non cambiano.
- Regressione: `regression/queue_liveness_diagnostics_v51277.py`.

## 5.12.76 — Scopri rifinito (2026-09-29)

- Sfondo crema con sfumatura lavanda e illustrazioni originali in PNG; l’occasione in evidenza usa una scatola prospettica composta dalla copertina BGG reale, prezzo, voto e chip dello sconto.
- Le categorie mostrano icone illustrate e un accesso “Vedi tutte”, senza contatori. Gli annunci recenti usano foto a tutta larghezza con data e prezzo sotto; i più votati BGG diventano una classifica verticale.
- Navigazione inferiore più leggibile con icone e testo arrotondato e stato selezionato in evidenza.
- Aggiunta regressione visiva alle verifiche PR e beta. Prezzi, dati attendibili e cronologia annunci restano invariati.

## 5.12.75 — Scopri editoriale (2026-09-29)

- Scopri adotta una superficie crema più vicina alla reference: titolo compatto, tile categorie BGG e sezioni orizzontali con gerarchie diverse.
- La corsia categorie conta le categorie reali dei giochi BGG visibili e confermati nel catalogo locale; toccarne una apre Database già filtrato.
- Aggiunte corsie per annunci più recenti (con data di pubblicazione), voto BGG e rapporto qualità-prezzo; tutte usano la superficie trusted già esistente.
- Il faux 3D è limitato all’occasione in evidenza. Le cover delle altre corsie restano immagini piatte.
- Non cambiano filtri di fiducia, valutazione dei prezzi, acquisizione Vinted o dati storici.

## 5.12.74 — Notifica chiara sulla coda (2026-09-29)

- La notifica distingue le attività realmente in elaborazione da quelle soltanto in coda o in attesa del prossimo permesso Vinted.
- La barra di avanzamento compare solo mentre un’attività è davvero in elaborazione; quando Ludo aspetta, la notifica mostra il motivo e non simula un avanzamento.
- Nessuna modifica a matching, soglie, pacing, dati storici o risultati.


## 5.12.69 — Manutenzione Motore con un solo responsabile (2026-09-28)

- Le pulizie e i passaggi di avvio del Motore non partono più dalla schermata principale.
- Il servizio della coda esegue i passaggi una sola volta, in background, mantenendo le protezioni di ripartenza già esistenti.
- Il controllo automatico PR e beta verifica che le scritture di avvio restino fuori dall’apertura della schermata.
- Nessuna modifica a matching, soglie di pubblicazione, ritmo delle richieste Vinted o dati storici.

## 5.12.68 — Safe mode prezzi e scansione Vinted (2026-09-28)

- Il prezzo medio costruito dagli annunci Vinted interni non decide più convenienza o esclusione: resta storico osservato. Per ora DealEvaluator usa soltanto il riferimento usato BGG disponibile.
- Le card già finite in PRICE_FILTERED vengono riesaminate con il solo benchmark BGG e possono tornare attive se erano state escluse da un riferimento locale contaminato.
- I candidati BGG con voto noto sotto 6 vengono eliminati prima di match/review. Quando arrivano i metadati BGG autorevoli, anche la categoria Children's Game viene esclusa da Database, review, linking Vinted e superfici prodotto senza cancellare lo storico.
- Nessun editore, incluso Asmodee, viene escluso come scorciatoia: publisher e qualità restano segnali distinti.
- Aprire Vinted non avvia più automaticamente la raccolta. L'AccessibilityService mostra un piccolo overlay Ludo · OFF; solo il tap su SCANSIONE ON abilita nuove osservazioni. Uscendo da Vinted lo stato torna OFF.
- L'overlay usa TYPE_ACCESSIBILITY_OVERLAY e non richiede il permesso Android “mostra sopra altre app”.
- La diagnostica espone scanOptIn e pricingSafeMode=BGG_ONLY; la scheda gioco presenta i valori locali come Storico Vinted / Min osservato.
- Questa release non modifica pacing/budget Vinted, signing, applicationId o schema DB e non viene considerata una soluzione al separato problema delle corsie Motore stale osservato sulla 5.12.67.

## 5.12.67 — Recupero degli scroll bloccati (2026-09-28)

- Il debug reale mostrava uno scroll attivo da quasi 3 giorni con una sola verifica Vinted residua, nessun job Vinted rivendicabile e heartbeat delle corsie vecchi di circa 7 ore: il Motore era fermo, non semplicemente lento.
- WorkManager usciva subito se il servizio risultava `RUNNING`, quindi non poteva recuperare il caso servizio vivo ma executor/corsia bloccata.
- Il worker di recovery ora si ferma solo durante l'avvio del servizio; dopo l'avvio verifica heartbeat SQLite, lavoro differito dello scroll attivo e stato reale delle corsie prima di lasciare il controllo al foreground service.
- Se la corsia è realmente stantia, il worker può riprendere il drain e rimaterializza i `DEFERRED_LINK` dello scroll attivo prima di provare a rivendicare un job.
- Anche il supervisore del servizio considera i `DEFERRED_LINK` dello scroll corrente come lavoro che richiede una corsia Vinted viva.
- Nessuna modifica a pacing Vinted, budget orario, matching, soglie di pubblicazione, fairness, database/schema, signing o applicationId.

## 5.12.66 — Redesign completo Motore (2026-09-25)

- La overview del Motore non è più una dashboard della pipeline: mette in ordine stato del lavoro automatico, risultati dello scroll corrente, eventuali scelte umane, altri scroll non conclusi e attività recente.
- Rimossa la progress bar: il Motore usa stati concreti come raccolta dello scroll, riconoscimento giochi, collegamento annunci, attesa del prossimo controllo Vinted, preparazione risultati e completamento.
- I risultati pronti sono sempre dichiarati come appartenenti **a questo scroll**, eliminando l'ambiguità tra conteggio del run corrente e Catalogo globale.
- “Serve il tuo aiuto” compare solo quando esistono casi realmente azionabili; gli esiti trattenuti automaticamente restano secondari e sono descritti come “non pubblicati automaticamente”.
- Gli altri scroll non conclusi sono mostrati come “Riprenderà” o “In attesa”, senza esporre round-robin, corsie, retry o scheduler.
- Il dettaglio “Lavoro automatico” spiega Riconoscimento giochi → Collegamento annunci → Preparazione risultati con quantità concrete e senza percentuali.
- Il dettaglio dello scroll usa filtri per esito (Tutti, Pronti, In lavorazione) invece dei vecchi filtri tecnici BGG/Vinted; le singole card usano stati leggibili e coerenti.
- Il rendering continua a consumare snapshot caricati su `uiDataIo`; nessuna nuova lettura SQLite viene eseguita sul main thread.
- Nessuna modifica a motore, matching, soglie di pubblicazione, pacing Vinted, fairness, schema database, signing o applicationId.

## 5.12.65 — Collegamenti manuali senza bloccare la UI (2026-09-25)

- Il debug 5.12.64 ha mostrato 6 ANR della UI dopo l’installazione, con input dispatch oltre 5 secondi, mentre gli snapshot SQLite arrivavano a oltre 7 secondi e il ledger Vinted riportava `SQLiteDatabaseLockedException`.
- Il flusso Vinted → Condividi → Ludo e la conferma manuale dei candidati eseguivano ancora letture/scritture SQLite direttamente sul main thread; con `busy_timeout=8000` una collisione poteva bloccare la UI abbastanza da generare ANR.
- Lookup e salvataggi dei collegamenti manuali passano ora su una corsia I/O dedicata; la UI mostra subito feedback e riceve solo il risultato finale.
- Il riuso degli snapshot Vinted della 5.12.64 è ora realmente read-only: non apre più inutilmente il writer SQLite per una semplice lookup.
- Il ledger richieste usa una lettura read-mostly e acquisisce il writer soltanto se deve inizializzare una nuova epoca diagnostica.
- Nessuna modifica a database/schema, matching, rate limit o priorità del Motore.

## 5.12.64 — Efficienza collegamenti Vinted (2026-09-25)

- Il resolver prova prima a riusare una pagina Catalogo Vinted già scaricata e salvata nello snapshot persistente, purché sia recente e non precedente all’osservazione dell’annuncio oltre la tolleranza prevista.
- Il riuso può evitare la richiesta di ricerca Catalogo, ma non abbassa la sicurezza: i candidati provenienti dallo snapshot non usano il fast path strutturato e devono ancora superare la verifica della pagina pubblica dell’articolo esatto.
- Snapshot deboli, ambigui o mancanti ricadono sul percorso di rete precedente senza cambiare soglie, ranking o limiti.
- Il vecchio rapporto richieste/link era distorto dalle rimozioni di annunci venduti e da più build accumulate. Il ledger v3 riparte da zero e divide le richieste fisiche per eventi reali di nuova identità Vinted risolta, includendo anche i link ottenuti a rete zero dal batch locale.
- Invariati pacing di 55 secondi, budget massimo di 60 richieste/ora e policy di identità/pubblicazione.

## 5.12.63 — Verifica immediata dell’annuncio appena aperto (2026-09-25)

- Il debug 5.12.62 mostra che il ritorno da un annuncio Vinted noto accoda correttamente una verifica esatta (`openedVintedVerify=QUEUED`), ma la coda ordinaria del Motore può impedirne la rivendicazione quando quell’annuncio non appartiene allo scroll attivo.
- `OPENED_VERIFY` è ora una sorgente interattiva ammessa anche durante un run Motore diverso e viene scelta prima del lavoro automatico ordinario.
- La verifica continua a usare la pagina pubblica Vinted già prevista e il pacing esistente: non aumenta il rate limit e non allenta identità, matching o criteri di pubblicazione.
- Se la pagina esatta risulta venduta/non disponibile, il listing canonico viene marcato terminale e rimosso dalle superfici attive, mentre osservazioni e storico prezzi restano conservati.
- Aggiunta regressione SQLite/source-level dedicata e collegata alla CI PR/beta.

## 5.12.62 — Ponte canonico verso il Catalogo (2026-09-25)

- Un annuncio completato in modo asincrono viene materializzato nel Catalogo solo quando il grafo canonico conferma identità BGG, voto almeno 6, ID e URL Vinted esatti, assenza di review e assenza di job bloccanti.
- Tipo prodotto e prova di prezzo provengono dall'osservazione originale: espansioni non verificate, prezzi respinti e classificazioni incerte restano esclusi.
- La verifica del tipo BGG può usare l'osservazione canonica quando la riga legacy non esiste ancora, eliminando la dipendenza circolare tra verifica e Catalogo.
- La sincronizzazione è idempotente per firma e ID Vinted; non elimina né riclassifica osservazioni storiche. Un recupero locale e senza rete riesamina fino a 24 record completi al minuto, così anche il backlog già presente può arrivare al Catalogo. Il debug riporta materializzazioni, duplicati già presenti ed esclusioni.

## 5.12.60 — Cause dei collegamenti Vinted mancanti (2026-09-25)

- Il debug separa tutti gli annunci attivi senza link Vinted in gruppi mutuamente esclusivi: non idonei BGG, in coda, ancora da tentare, nessun candidato, ambigui, match debole, pagina non disponibile, attesa Vinted, verifica fallita e altro.
- Il totale idoneo si riconcilia con la somma degli esiti, distinguendo i record che non devono ancora arrivare a Vinted dai veri fallimenti del linker.
- La misura legge lo stato SQLite condiviso e non aggiunge richieste di rete né modifica soglie, ranking o pubblicazione.

## 5.12.59 — Ripresa immediata dopo aggiornamento (2026-09-24)

- Una nuova istanza del servizio libera subito i job rimasti `PROCESSING` dal processo precedente e li rende nuovamente eseguibili.
- Tentativi, priorità, sorgente, annunci e osservazioni vengono conservati; non viene eliminato alcun backlog.
- Il watchdog dei job bloccati durante lo stesso avvio resta attivo.

## 5.12.58 — Titoli alternativi Vinted con prova fotografica (2026-09-24)

- Il collegamento Vinted può riconoscere il nome BGG esatto anche quando il titolo osservato usa una traduzione o un nome alternativo.
- Questo percorso automatico richiede prezzo identico e similarità fotografica almeno dell’84%, quindi verifica comunque la pagina pubblica Vinted.
- In assenza di uno dei tre segnali il risultato resta incerto: non vengono ridotte le soglie generali e non viene pubblicato un match dubbio.

## 5.12.57 — Motore leggibile per scroll (2026-09-24)

- La schermata Motore mostra il percorso dello scroll con cinque conteggi espliciti: card Vinted uniche, giochi idonei, BGG confermati con voto almeno 6, annunci Vinted collegati e risultati entrati nel Catalogo.
- Lo stato principale usa parole descrittive al posto della frazione ambigua “elaborati”. Il colore azzurro indica lavoro automatico in corso; arancione resta riservato ai casi che richiedono una scelta.
- I casi manuali sono dichiarati separati dal lavoro automatico: non fanno apparire l’intero Motore bloccato.
- La UI riusa lo snapshot già caricato in background e non aggiunge letture SQLite sul thread principale.

## 5.12.55 — Funnel diagnostico per nuove inserzioni (2026-09-24)

- Il report separa le uscite Android per memoria bassa da quelle per uso eccessivo di risorse CPU. I vecchi contatori memoria includevano entrambe le cause e non vanno confrontati direttamente con i nuovi.
- Il funnel Motore aggiunge il percorso delle inserzioni attive viste per la prima volta nelle ultime 24 ore: analisi in attesa, blocchi del classificatore, tipo prodotto incerto, match BGG ambiguo o assente, rating mancante o sotto 6, voce BGG nascosta, collegamento Vinted esatto e qualificazione finale.
- Le metriche recenti contano inserzioni/fingerprint, non giochi distinti; restano conteggi di stato e possono sovrapporsi dopo la qualificazione.
- Modifica solo diagnostica: non cambia selezione, rating minimo, collegamenti, pubblicazione né database.

## 5.12.52 — Recupero da categoria Vinted esplicita (2026-09-23)

- Un match BGG poteva nascondere un gioco come `TYPE_UNVERIFIED` quando la card Accessibility non conteneva una prova positiva del prodotto. L'apertura successiva della pagina Vinted salvava la categoria strutturata, ma non riapriva il caso.
- «Giochi da tavolo» (e categorie equivalenti riconosciute in italiano, inglese, spagnolo e francese) riattiva soltanto quell'annuncio. I breadcrumb che terminano in accessori, espansioni o componenti restano esclusi.
- La pubblicazione resta subordinata al tipo BGG e al rating ≥ 6: rating assente in attesa di arricchimento; rating sotto 6 locale e nascosto.
- La verifica BGG usa prima le prove `BASE_GAME`, così una card incerta più recente non nasconde tutte le inserzioni dello stesso gioco.
- Le verifiche riuscite riaprono solo gli annunci che hanno una categoria da gioco da tavolo acquisita dalla pagina prodotto. Dati storici e osservazioni non vengono cancellati.
- Questo intervento ripara il percorso di recupero della classificazione; non aumenta il tetto di richieste Vinted. Il debug più recente non era incluso nel prompt, quindi volume e causa runtime vanno ancora confrontati sul dispositivo.

## 5.12.51 — Remote-run fairness and catalog conversion funnel (2026-09-23)

- Historical run backlog still monopolized public Vinted lane despite local intake separation: 14 waiting scrolls, 24 remote link tasks in an approximately 44-hour-old active run.
- Rotate remote run ownership after 90 seconds of service time whenever another unfinished session waits (formerly ten minutes), preserving durable jobs, serial public access and per-hour request budget. This is fair rotation, not deletion or forced completion.
- Add on-demand catalog conversion funnel in debug: active persisted listings, pending local analysis, BGG-qualified listings, LOCAL_ONLY, DEFERRED_LINK, exact Vinted identity and core-qualified listings.
- The debug supplied right after 5.12.50 install was before any new Vinted Accessibility event, so it cannot validate post-install local intake; verify only after fresh scrolling.
- Exact Vinted identity remains an explicit requirement for publishable listings; no fake URLs or reduced trust gates.


## 5.12.50 — Local intake and Activity daily summary (2026-09-23)

- 5.12.49 field debug: 2 new post-install UI ANRs, 1 low-memory exit and excessive :ui CPU despite a 371-ms asynchronous badge. Snapshot history load still took 5.9 seconds.
- Replace day-summary N+1 session detail loads (up to 100 × expensive joins per day) with a simple ordered observation burst count. Daily outcome totals remain calculated separately.
- Remove the active remote-run restriction from purely local analysis. Freshly acquired Vinted observations from newer scrolls are analyzed newest-first in batches of 8 while older runs retain remote Vinted ownership and pacing.
- Continue local batches every 1.5 seconds rather than immediately saturating :radar; capture last completed batch age/size in diagnostics.
- Add publisher evidence for 999 Games, Z-Man Games, Keymaster Games and Just Games; no title-specific bypass or relaxation of BGG/product proof.


## 5.12.49 — UI indicator ANR guard (2026-09-23)

- Field evidence from 5.12.48 showed the Motore cursor advancing correctly after the LOCAL_ONLY fix, but three UI ANRs immediately after install.
- The Activity status badge no longer runs queue/review SQLite queries synchronously from the Android main thread during app startup or navigation.
- Queue/review badge state is loaded through the existing UI-data executor and rendered from a cached snapshot; stale/empty cache never blocks touch dispatch.
- Diagnostics now expose `activityIndicator={...}` with background-load elapsed time/error.
- No change to Vinted pacing, matching, publication rules or queue ownership.


## 5.12.48 — Local-only run unblock (2026-09-23)

- Fixes the Motore ownership deadlock observed with four Marracash `LOCAL_ONLY` listings: those listings are deliberately local and non-published, so they count as settled holds instead of indefinitely missing Vinted remote work.
- Old sessions remain in history and no listings are deleted, promoted or published merely due to age. Ownership naturally proceeds to the next unfinished scroll when all actionable rows are complete/review/held.
- Diagnostics now expose `lastVintedEventAgeMs` to distinguish a new Vinted capture from historical `cardsParsedTotal` and `lastClassifierBlock` values.
- No Vinted pacing, signing, package or CI versionCode changes.

## 5.12.47 — Stability + intake recovery (2026-09-23)

- Riduce i fault della corsia Vinted su contention SQLite multiprocesso: ogni connessione usa un `busy_timeout` di 8 s e `SQLITE_BUSY` viene trattato come backpressure con retry breve, non come lane fault.
- Gli annunci con publisher/brand chiaramente ludico (ad esempio Kosmos) ottengono finalmente evidenza marketplace positiva e possono proseguire verso identità BGG + verifica Vinted, senza pubblicazione basata sul solo brand.
- Espansioni esplicite restano osservabili ma non entrano più nel pricing/catalogo automatico né nella coda di revisione utente.
- Mantiene invariati rate limit Vinted, signing, applicationId, strategia CI e dati esistenti.
- Estesa la regressione pipeline con guard per brand ludici, esclusione espansioni e timeout SQLite.


## 5.12.46 — Activity ready-state recovery (2026-09-22)

- Corregge il ciclo di caricamento infinito introdotto dalla 5.12.45: la freschezza dello snapshot parte dalla fine delle query SQLite, non dal loro inizio.
- Uno snapshot scaduto resta visibile durante l'aggiornamento asincrono invece di essere sostituito dal placeholder.
- L'overview materializza solo i tre giorni di cronologia effettivamente mostrati, riducendo query e contention.
- Il percorso Motore non calcola più il badge nascosto tramite query SQLite sul main thread; anche i broadcast di coda saltano quel lavoro quando Motore è aperto.
- Gli errori di snapshot hanno backoff di 5 secondi e stato diagnostico esplicito, evitando retry serrati su SQLITE_BUSY.
- La regressione riproduce una lettura più lenta del TTL e protegge freshness, stale-while-revalidate, ownership transitive e osservabilità.



## 5.12.45 — Activity snapshot recovery (2026-09-22)

- La pagina Attività non legge più sessioni, review e cronologia SQLite sul main thread.
- Un executor UI dedicato carica uno snapshot coerente; il main thread costruisce solo le view da dati già materializzati.
- Lo snapshot usa single-flight e placeholder immediato, evitando blocchi input durante contention con Radar/Queue.
- Regressione dedicata impedisce il ritorno di query DB nei metodi di render overview/hero.


## 5.12.44 — Queue ownership stability (2026-09-22)

- Il processo UI non esegue più reconciliation, cleanup e conteggi della coda SQLite: avvia soltanto il servizio queue, proprietario della manutenzione seriale.
- La pagina Attività non forza più un rebuild completo ogni sei secondi; gli aggiornamenti arrivano dagli eventi semantici della coda.
- Aggiunta regressione per ownership SQLite e cadence UI.


## 5.12.43 — Runtime catalog unblock (2026-09-22)

- Corregge la regressione 5.12.42: un candidato BGG con titolo esattamente uguale dopo normalizzazione può raggiungere la lane Vinted a basso ritmo per acquisire la categoria strutturata, ma resta UNCERTAIN e non catalogabile finché categoria Vinted e compatibilità tipo BGG non sono entrambe verificate.
- Un titolo soltanto simile (ad esempio Marrakech Music → Marrakech) resta quarantinato; titoli collision-prone continuano a richiedere evidenza ludica indipendente.
- Rimuove le catture screenshot full-frame nel feed Accessibility: erano un tie-break opzionale ma aumentavano la pressione heap durante scroll lunghi. Restano thumbnail da pagina prodotto/remota dopo prova di idoneità.
- Estende la regressione di integrità con la route esatta non-pubblicante e l'harness da 200 card senza screenshot feed.

## 5.12.42 — Pipeline integrity recovery

- Replaced the implicit BASE_GAME fallback with an UNCERTAIN state. A BGG-like title now needs independent marketplace evidence before it can become a price/publication candidate; unknown observations remain reversible rather than becoming title-collision cards.
- Added product/BGG-type compatibility validation: BASE_GAME accepts only BGG boardgame, EXPANSION only boardgameexpansion. Authoritative XML type is persisted through enrichment; mismatches become retained TYPE_MISMATCH rows rather than silently publishing as the wrong product type.
- Product-page Vinted category/catalog semantics are now durable, sourced evidence (raw, normalized, source, confidence, timestamp). A labelled non-game category filters the exact canonical listing; missing feed category remains explicitly unknown.
- Defined Catalog population truth: storedAll, eligible, and visible are separate diagnostics. The Catalog count and empty search state now refer to the same eligible query as the rendered list, preventing “124 saved / 7 visible” from being presented as one population.
- Serialized global queue reconciliation through the control lane; Activity wake and consumer lanes no longer compete with independent reconcile transactions. Bound screenshot capture to eight viable cards and moved crop/file work off accessibility callbacks.
- Added additive schema migration 21 and pipeline-integrity regressions, including base/expansion fixtures, collision guard, catalog contract guards, category provenance and a deterministic 200-card thumbnail-cap stress harness.
- No data reset, signing/applicationId/CI-version strategy or Vinted request budget increase.


## 5.12.35 — Pricing decisions + bundle exploration
- Added a single pricing decision layer: **Offertona**, **Buon prezzo**, **Prova un'offerta**, **Prezzo giusto** and **Pochi dati**. Overpriced rows use an internal reject state and leave product surfaces automatically; raw market history is preserved.
- Used-market semantics are now explicit: the BGG median is the typical used value, while Q25 remains a lower market band used only for stronger deal evidence. The compact offline BGG index now reads its median column instead of the lower-band column.
- Local Vinted asking-price evidence no longer takes over from only three observations. Samples of 3–7 comparables are blended with the BGG/prior reference; from 8 comparable listings onward the local median may stand alone.
- Offer suggestions are solved backwards from the all-in purchase target and are shown only for plausible 5–15% reductions.
- Discover remains selective to Offertona / Buon prezzo / Prova un'offerta. Catalog can retain fair/insufficient-data rows; price-rejected rows remain market evidence but do not consume ordinary Vinted identity work.
- Bundle suggestions now include promising sellers surfaced from a strong game as well as economic leads. Opening Vinted from Bundle keeps the existing short-lived seller-exploration intent used by Accessibility.
- Unknown bundle shipping is no longer presented as a precise all-in total or saving. The UI shows the suggested bundle offer and asks to verify the final shipping on Vinted.
- Recommendation ranking and high-signal notifications consume the same central pricing decision instead of separate raw-discount thresholds. Notifications keep the stricter 30% all-in gate.
- Added JUnit pricing cases plus `regression/pricing_bundle_decisions_v51235.py`; PR/beta CI run both before compilation/build.
- No database schema migration, request-rate increase, signing/applicationId change or CI versionCode-strategy change.

## Git workflow / CI bootstrap
- Published the Git-ready 5.12.3 baseline to private GitHub repository `werhealthy/ludo-scout`.
- Created `beta` from `main` for test-build integration.
- `Android beta` now runs automatically on pushes to `beta` and remains manually triggerable.
- The workflow builds a signed debug APK with the preserved developer signing identity, verifies the certificate fingerprint, uploads the artifact and distributes it through Firebase App Distribution.
- CI requires the documented BGG, signing and Firebase GitHub Secrets; none are stored in the repository.

## 5.12.29 — Motore network priority
- Closed a remaining ETA/throughput gap visible in the 5.12.27 field debug: Bundle deep work could still run with `vintedActive=20` while Motore had unfinished scrolls because the old guard only deferred Bundle above 20 active Vinted jobs.
- An unfinished Motore observation run now owns the public Vinted lane ahead of opportunistic background work, even when its next Vinted identity is parked/deferred and the durable queue is momentarily empty.
- Automatic legacy metadata maintenance no longer starts while Motore owns a run.
- Bundle backlog discovery, public snapshots, queued deep scans and ownership verification all re-check Motore ownership before starting network work.
- Zero-network local seller-graph rebuilding is still allowed, so stale Bundle labels can be corrected without delaying Motore.
- Added `bundleDeferredForMotore` diagnostics and `regression/motore_network_priority_v51229.py`.
- No schema migration, request-rate increase, signing, applicationId, Firebase, secrets or CI versionCode-strategy change.

## 5.12.28 — Catalog freshness, bundle health and ETA truth
- Fixed Motore ETA for parked current-run work. Exact Vinted identities still missing now remain in the estimate even when no durable Vinted job is currently materialised.
- When a deferred scroll becomes the Motore owner again, its parked unresolved Vinted links can resume immediately instead of inheriting a long background retry timestamp.
- Motore now labels remaining time as `~N min di corsia` and indicates when fairness may alternate service with other scrolls.
- Added low-priority idle Catalog health: when no Motore scroll is active, one already-linked catalog item at a time can be rechecked through the existing 55-second-paced public-page lane, prioritizing missing publication/seller metadata and then stale items older than 24 hours.
- Exact health checks remove sold/404 listings from the active catalog without creating user review and refresh publication/seller/current-price metadata when available.
- Opening a known Vinted item from Ludo now carries short-lived exact provenance into Accessibility. A sold product page updates the exact Ludo card rather than relying on seller title/price rediscovery.
- Bundle state is now current-inventory-derived: sold/hidden/corrected items invalidate their seller graph and caches; sellers falling below two active eligible games lose stale bundle rows.
- Catalog Bundle chips/presets require an actually reconstructable two-game bundle, preventing a card from showing Bundle while its detail/page has no real combination.
- Manual `Ricontrolla dati` now includes missing Vinted publication date and seller metadata.
- Added diagnostics for catalog health, exact-open reconciliation and `coreRemaining` plus regression `catalog_freshness_bundle_health_eta_v51228.py`.
- No schema migration, request-rate increase, signing, applicationId, Firebase, secrets or CI versionCode-strategy change.

## 5.12.27 — Queue liveness and Catalog truth
- Fixed a Vinted-lane starvation bug exposed by the 5.12.26 Pixel debug: runnable Motore jobs could coexist with an idle lane because any future LIVE/HUNT/MANUAL retry blocked ordinary work until its retry time.
- Urgent Vinted work now reserves at most one public-page slot when it is within 65 seconds of becoming runnable. Far-future urgent retries no longer freeze current Motore progress.
- Existing priority ordering is preserved: once urgent work is due it still preempts ordinary work.
- The queue lane now reports near-due urgent reservation as an explicit WAITING state instead of “nessuna attività rivendicabile”.
- Diagnostics add `vintedUrgent={active,due,nextDueAt,reserveUntil}`.
- Fixed Catalog `Vinted da completare` count/filter mismatch. The badge and filter now agree on missing URL, publication label, or seller id.
- The red Vinted core warning remains specific to a missing exact page/link; metadata-only incompleteness remains a softer state.
- Added `regression/queue_liveness_catalog_truth_v51227.py` to PR and beta CI.
- No schema migration, request-rate increase, signing, applicationId, Firebase, secrets or CI versionCode-strategy change.

## 5.12.26 — Target-only recovery, truthful review and early price gate
- Manual “Cerca questo annuncio su Vinted” is now a target-only recovery mode. Search-result cards seen during that flow no longer become ordinary Motore observations/jobs; explicit market-price scans remain unchanged.
- Recovery copy now explains that one visible result is not enough for an exact automatic link when Vinted does not expose the item URL/id; the exact item must be opened/shared or its link pasted.
- Split user-actionable review from non-actionable trust/history holds. Historical `MATCH_UNCERTAIN` rows remain excluded from trusted surfaces but no longer inflate the “da controllare” count or lead to an empty inbox.
- Held rows are considered settled automatic work, so they do not keep a run alive forever; Motore labels them as non-published with no action required.
- Added a conservative pre-network price gate: ordinary unlinked listings are removed from automatic Vinted work only when seller ask is at least 2× and €25 above an existing used-market reference. Hunt/manual work is exempt and raw/game history is preserved.
- Applied the price gate immediately before deferred Vinted promotion so it can actually save the scarce public-page request.
- Moved queue reconciliation and repeated noise maintenance off Activity startup’s UI thread to reduce the observed SQLite contention/input-ANR path.
- Diagnostics add manual recovery suppression/state, early price filtering and Motore held counts.
- Added `regression/recovery_review_price_gates_v51226.py` to PR and beta CI.
- No schema migration, request-rate increase, signing, applicationId, Firebase, secrets or CI versionCode-strategy change.

## 5.12.25 — Adaptive Motore fairness and real-work ETA
- Closed the remaining head-of-line gap left after 5.12.24: the oldest unfinished scroll can no longer monopolize the ordinary automatic lane indefinitely.
- Motore timing now models core Vinted identity work rather than raw valid-card count. The target is a 10-minute floor, otherwise roughly 1 minute of local/setup allowance plus 55 seconds per core remote candidate.
- Added a live ETA based on core Vinted candidates still pending; it shrinks as online identity work completes and does not include optional deep metadata.
- Added non-destructive round-robin continuation: when another unfinished scroll exists, an ordinary run yields after a 10-minute service slice. Its listings/jobs remain untouched and resume on a later turn.
- HUNT_PRIORITY and MANUAL_PRIORITY keep their existing preemption; trusted-only Home/Scopri rules are unchanged.
- Motore UI now reports remaining online verifications, uses `In pausa · riprenderà` for yielded work, and explains that acquired scrolls rotate without discarding cards.
- Diagnostics add `engineFairness`, `etaMs`, `coreWork` and `corePending`.
- Added `regression/engine_adaptive_fairness_v51225.py` and updated the 5.12.24 timing regression to the real-work model.
- No schema migration, request-rate increase, signing, applicationId, Firebase, secrets or CI versionCode-strategy change.

## 5.12.24 — Adaptive, non-destructive Motore timing
- Replaced the universal 10-minute Motore correctness cutoff with workload-aware timing. Ten minutes remains the product target for a small/ordinary scroll.
- The timing estimate is based on actual eligible listings and the deliberately conservative Vinted public-page pace: minimum 10 minutes, otherwise roughly 5 minutes base plus 55 seconds per eligible listing.
- This was the first non-destructive timing model; 5.12.25 supersedes its raw eligible-listing estimate with measured core Vinted work and adds fair run rotation.
- Elapsed time alone no longer auto-excludes listings, hides deals or completes a run. A run finishes only when its automatic content is actually settled.
- Timing overrun is now diagnostics-only via `engine-timing-v2`; existing job watchdogs/retries and deterministic classification/matching outcomes remain responsible for real failure handling.
- Motore UI now labels remaining time as an estimate instead of promising a hard deadline; waiting-scroll copy no longer claims a fixed ten-minute release.
- Updated the 5.12.21 product regression to the superseding timing contract and added `regression/engine_adaptive_timing_v51224.py`.
- No schema migration, request-rate increase, signing, applicationId or CI versionCode-strategy change.

## 5.12.23 — Review truth, bounded fuzzy BGG and idle queue cleanup
- Pixel validation of 5.12.22 confirmed the exact-title BGG path is fixed: exact lookup produced zero timeouts, BGG review was zero and the current install had zero crash/ANR/memory exits.
- Replaced full-catalog fuzzy rescoring with a compact token-postings index and a bounded candidate pool selected from rare query tokens.
- Historical BGG revalidation no longer creates Motore human-review work. Existing historical review flags are cleared once while the corresponding legacy deals remain `MATCH_UNCERTAIN` and excluded from trusted surfaces.
- Review diagnostics now distinguish explicit/manual, BGG variant, historical inbox debt, historical held debt, other Vinted review, BGG technical and BGG genuine review.
- Fixed idle Vinted queue truth: runnable counts and next-due timing now use the same source gate as the actual job claimer.
- Ordinary Vinted jobs left materialized after Motore becomes idle are parked back into deferred state, and the maintenance sweep no longer recreates ordinary network jobs while no scroll owns them.
- Added diagnostics for historical-review cleanup, idle-job parking, token-index load time and fuzzy candidate counts.
- Added `regression/review_fuzzy_queue_truth_v51223.py` and wired it into PR/beta CI.
- No schema migration, request-rate increase, signing, applicationId or CI versionCode-strategy change.

## 5.12.22 — Compact BGG exact index and no technical timeout review
- First field diagnostic after 5.12.21 confirmed Motore now closes cleanly at the product SLA: no active/waiting run remained and five unresolved ordinary rows were expired at the 10-minute ceiling, with no current-install crash/ANR/memory exit.
- The same run exposed 29 exact BGG scans and 29 exact-scan timeouts; those technical timeouts were being written as BGG review.
- Replaced repeated 31k-game exact scans with a compact primitive hash index built once with the queue-process catalog; candidate hash collisions are verified against original names/aliases before acceptance.
- Excluded one-time catalog/index bootstrap time from the per-query fuzzy safety timer.
- BGG matcher timeouts and technical exceptions no longer create human review; they are automatically quarantined from trusted surfaces and retained in diagnostics.
- Added a one-time recovery for 5.12.21 timeout/error review rows so they are reopened under the new exact-index path instead of remaining permanent review debt.
- Added `reviewBreakdown` diagnostics and timestamp/build metadata for `lastClassifierBlock`, preventing stale pre-update classifier evidence from being mistaken for current behavior.
- Added `regression/bgg_exact_index_no_timeout_review_v51222.py` and wired it into PR/beta CI.
- No schema migration, request-rate increase, signing, applicationId or CI versionCode-strategy change.

## 5.12.21 — Product UX turnaround: 10-minute Motore SLA and trusted results
- Added a hard 10-minute ownership ceiling for ordinary Motore runs. Incomplete automatic rows are parked reversibly so one difficult listing cannot hold later scrolls for hours.
- Automatic Vinted misses and unresolved automatic BGG variants no longer become routine manual-review work. Explicit Hunt/manual requests keep the recovery path; ordinary ambiguity is auto-excluded.
- Optional deep Vinted metadata no longer blocks a card whose BGG and exact Vinted identity are already complete.
- Added a one-time non-destructive cleanup for automatic review debt created by older builds while preserving explicit Hunt/manual intent.
- Tightened the BGG review band and added conservative seller-title suffix normalization for obvious descriptive titles.
- Added explicit videogame/platform exclusions and narrowed accessory/component detection so words such as “tessere” or “dadi” in a full-game description do not cause false component filtering.
- Scopri and companion recommendations now use trusted-only results: fully matched identity, no review and no open core job. Hunts use the same trust checks without requiring resale-tier pricing.
- Motore cards now open the standard product detail instead of a special two-button modal. The product detail exposes the unified BGG/Vinted/correction actions, including “Gioco sbagliato”.
- Hunts can promote exact watched games even when the price is merely average; an explicit max price is still respected.
- Diagnostics add current-run age, SLA time remaining, review percentage and SLA-expiry telemetry.
- Added `regression/product_turnaround_sla_trust_v51221.py` and wired it into PR/beta CI.
- No schema migration, request-rate, signing, applicationId or CI versionCode-strategy changes.

## 5.12.20 — Engine runtime cross-process telemetry
- Pixel validation of 5.12.19 showed no current-install crash/ANR/memory exits and confirmed the stale Motore analysis-pending count is fixed.
- Replaced process-local engine/service readiness diagnostics with authoritative SQLite-backed `queue_controls` state so `:ui` cannot report a stale `:radar` SharedPreferences cache.
- Top-level diagnostics now derive `serviceConnected`, `engineReady` and `engineGames` from cross-process runtime state when available.
- Added bounded JsGameEngine bootstrap tracing: WebView creation/attach/page completion, sampled verify snapshots, ready/timeout state and JavaScript console errors.
- Verify snapshots report document readiness plus bridge/catalog presence and catalog game count, making the next Pixel result sufficient to distinguish a real engine failure from stale telemetry.
- Added `regression/engine_runtime_cross_process_v51220.py` and wired it into PR/beta CI.
- No queue semantics, database schema, signing, applicationId, request-rate or CI versionCode-strategy changes.

## 5.12.19 — Queue single owner and Motore analysis ordering
- ACTION_NOW now starts only the foreground queue owner and no longer also enqueues one-shot WorkManager work.
- QueueDrainWorker stands down before opening SQLite whenever the foreground queue is starting or running; WorkManager is recovery-only.
- Pending classifier batches are scoped to the oldest active Motore observation run, preventing a newer waiting scroll from consuming JS analysis ahead of current work.
- RAM Accessibility hints no longer bypass persisted current-run ordering; waiting scrolls are rechecked every 5 seconds and can advance without another Vinted visit.
- JsGameEngine readiness retries in a bounded, single-chain loop every 500 ms for up to 30 seconds instead of failing after one cold-WebView check.
- Added `regression/queue_single_owner_engine_order_v51219.py` and wired it into PR/beta CI.
- No schema migration, signing, applicationId, request-rate or CI versionCode-strategy changes.

## 5.12.18 — WorkManager/main-thread stability
- Pixel validation confirmed unique-card acquisition counts, but Android recorded a fresh default-process ANR 47.8 seconds after the 5.12.17 package update: `No response to onStartJob ... SystemJobService`.
- Moved queue-service database/reconcile/sweep/supervisor work off Android Service callbacks onto a serialized control executor; `startForeground()` remains immediate and `onStartCommand()` now returns without synchronous SQLite work.
- Moved default-process WorkManager enqueue operations out of `BroadcastReceiver.onReceive()` via `goAsync()`, with an additional main-looper dispatch guard in `QueueWorkScheduler`.
- WorkManager recovery now stands down while the foreground queue owner is still cold-starting, reducing SQLite startup contention.
- Motore analysis progress now uses canonical market-listing state rather than stale raw duplicate observation rows, preventing already-analysed cards from keeping an older job alive.
- System-exit diagnostics v3 report crash/ANR/memory counts since the current APK install boundary, while retaining epoch and 24-hour history.
- Added `engineWaiting` diagnostics for the first queued scroll behind the active job.
- Added `regression/workmanager_mainthread_stability_v51218.py` with an executable SQLite stale-pending fixture and main-thread ownership guards; wired it into PR and beta CI.
- No database/schema migration, signing, applicationId, request-rate or CI versionCode-strategy changes.

## 5.12.17 — Acquisition dedupe and crash stability
- Fixed Motore card-count inflation caused by repeated Accessibility renders of the same visible Vinted cards. Same title/brand/price sightings and re-analysis are suppressed for 10 minutes, with the observation dedupe persisted in SQLite so it survives `:radar` restarts.
- Motore now reports unique Vinted cards in the hero, job detail header and daily chronology; raw observation events remain available only in diagnostics.
- Hardened crash journaling so the minimal crash header is flushed before stack formatting, improving evidence retention when the fatal condition is memory pressure.
- System exit-history diagnostics now ignore WebView sandbox processes, scope fresh crash/ANR/memory counts to the current engine epoch, and expose Ludo-process PSS/RSS plus a compact recent-exit history.
- Added a hard 2.5 s budget to queue-process local BGG exact/fuzzy scans. An incomplete timed-out scan cannot produce an automatic match; it becomes optional review instead.
- Added `regression/engine_acquisition_crash_stability_v51217.py` and wired it into PR and beta CI.
- No schema migration, request-rate, signing, applicationId or CI versionCode-strategy changes.

## 5.12.16 — Motore epoch, progress semantics, queue startup stability
- Added a non-destructive Motore operational epoch so old timeline/review debt no longer appears as current work; observations, prices and authoritative matched data remain stored.
- Old automatic queue jobs are completed at cut-over while explicit Hunt work is preserved. Legacy review flags and unresolved provisional BGG review rows are archived from the active workflow.
- Fresh sightings can revive an archived provisional BGG title and re-evaluate it with current logic.
- Fixed stuck jobs caused by counting BLOCKED_CLASSIFIER observations as analysis still pending.
- Reworked Motore around job progress instead of games found; ambiguous cases are optional and non-blocking.
- Hardened QueueKeepAliveService startup against crash loops and extended crash diagnostics with startup phase plus root cause.
- Added engineEpoch diagnostics and regression/engine_epoch_progress_stability_v51216.py.
- No raw observation deletion, network-rate change, signing/applicationId change, or CI versionCode-strategy change.

## 5.12.15 — Crash diagnostics + executable SQLite integration
- Added application-wide crash journaling so UI, radar and queue/default processes are all covered instead of only MainActivity.
- Added Android system exit-history diagnostics on API 30+ to identify actual process deaths as CRASH, ANR, memory-related or other exit reasons.
- Added `processCrashJournal` and `systemExitHistory` to the technical diagnostic output.
- Added an executable sqlite3 BGG state-machine integration test that reproduces the previously missed affinity bug and verifies current/historical work drains after transitions.
- Added a dedicated regression for multi-process crash diagnostics and wired both new tests into PR and beta CI.
- Shifted Pixel usage to final real-device validation rather than repeated debugging of deterministic SQLite behavior.
- No schema/data reset, request-rate, signing, applicationId or CI versionCode-strategy changes.

## 5.12.14 — BGG algorithm-version numeric affinity
- Fixed a SQLite comparison bug that could keep review rows permanently eligible for re-matching even after `match_algorithm_version` was updated to the current algorithm.
- All version predicates now cast the bound parameter to INTEGER explicitly, including current matcher selection/counting and historical revalidation selection/counting.
- Added `bggMatchBreakdown` diagnostics separating pure required identities, legacy review rows and current review rows.
- Added `regression/bgg_version_affinity_v51214.py` and wired it into PR/beta CI.
- No schema/data reset, request-rate, signing, applicationId or CI versionCode-strategy changes.

## 5.12.13 — BGG cold-index + single-flight
- Removed the queue-process global all-alias exact-name map that could dominate BGG matcher cold start after an APK/process restart.
- The local catalog is now parsed once into shared game objects plus a direct BGG-id map; exact title/alias lookups are lazy in-memory scans cached in a bounded 64-query LRU.
- Preserved queue fuzzy matching on the same shared catalog and the disk-streaming manual/UI path.
- Added single-flight protection so QueueKeepAliveService and WorkManager cannot execute duplicate local BGG identity batches concurrently.
- `bggLocalMatch` v3 now reports index load time and exact-cache telemetry for Pixel validation.
- Added `regression/bgg_cold_index_singleflight_v51213.py` and updated older index regressions for the new architecture.
- No schema/data reset, request-rate, signing, applicationId or CI versionCode-strategy changes.

## 5.12.12 — BGG review-write accountability
- Added transactional, directly observable accounting for the three provisional BGG rows that still appeared stuck after the state-monotonicity fix.
- `markBggMatchReview()` now validates the current row inside its write transaction, protects a concurrent authoritative match, returns the actual write count, and keeps linked listing review state in the same transaction.
- `bggLocalMatch` now distinguishes review decisions from successful writes and reports remaining required identities after each batch.
- Added `bggReviewWrite` diagnostics with before/after state, BGG id, visibility and changed-row counts.
- Added `regression/bgg_review_write_accountability_v51212.py` and wired it into PR and beta CI.
- No schema/data reset, request-rate, signing, applicationId or CI versionCode-strategy changes.

## 5.12.11 — BGG state monotonicity
- Fixed a state-race exposed by Pixel Test 5: repeated/stale analysis could reset a game already moved to BGG review back to `BGG_MATCH_REQUIRED`, which permanently blocked historical revalidation behind the same three current titles.
- Provisional re-analysis may now refresh only unresolved states. `BGG_MATCH_REVIEW` and `AUTO_QUARANTINED` are monotonic and cannot be silently reopened, even by a late `status=matched` analysis result.
- `markBggMatchReview()` now updates canonical game state and active linked listing state in one SQLite transaction.
- Stale analysis results no longer mutate inactive listings; a listing tied to an already quarantined game is refiltered rather than resurrected.
- `reconcileQueue()` repairs legacy game/listing drift for review and auto-quarantine states without deleting records.
- Added `regression/bgg_state_monotonicity_v51211.py` and wired it into PR validation and Android beta CI.
- No schema/data reset, no Vinted/BGG rate change, and no signing/applicationId/CI versionCode-strategy change.

## 5.12.10 — BGG fuzzy index performance
- Fixed the remaining local BGG matcher full-scan path: queue fuzzy matching no longer reopens/decompresses/scans the ~31k-game gzip catalog once per query.
- Added a queue-only in-memory fuzzy scorer over the catalog objects already retained by exact/id indexes, with a bounded top-16 heap and 32-query LRU cache.
- Preserved the existing disk-streaming fuzzy path for short-lived/manual clients to avoid reintroducing a large retained catalog into UI processes.
- Added `bggLocalMatch` diagnostics with batch counts and elapsed time so local matcher stalls can be measured on Pixel.
- Added `regression/bgg_fuzzy_index_performance_v51210.py` and wired it into PR and Android beta CI.
- No schema/data reset and no signing, applicationId, CI versionCode strategy, Vinted request rate or BGG network-rate changes.

## 5.12.9 — Historical BGG drain scheduling
- Fixed the second bottleneck revealed by Pixel testing after the local-index optimization: historical revalidation was artificially limited to tiny slices and could run before current BGG work.
- Current BGG identity and enrichment now always precede historical cleanup; historical revalidation runs only when the current BGG lane has no runnable work.
- Historical cleanup now drains bounded 24-game bursts with a short yield, while the revalidator hard-caps callers at 32 games.
- Added historical pending work to foreground-service liveness, BGG lane supervision and WorkManager recovery/rescheduling so the one-shot audit cannot silently stop while rows remain.
- Canonical pending accounting now requires an active listing, matching the actual candidate query.
- Coalesced historical queue notifications to one broadcast per slice instead of one per game, avoiding UI/update storms while increasing local throughput.
- Added `regression/historical_bgg_drain_scheduling_v5129.py` and updated the historical safety regression to verify priority semantics rather than obsolete batch constants.
- No schema/data reset and no signing, applicationId, CI versionCode strategy, Vinted request rate or BGG network-rate changes.

## 5.12.8 — BGG local-index performance
- Fixed the root cause of historical BGG revalidation starvation: `localById()` no longer reopens/decompresses/scans the entire ~31k-game local catalog once per game.
- The local BGG catalog is now indexed once per queue-process `BggSearchClient`, producing both exact title/alias lookup and direct BGG-id lookup from the same parsed `Game` objects.
- Subsequent historical BGG id resolution is O(1), while audit batch sizes and network pacing remain unchanged.
- Added `regression/bgg_local_index_performance_v5128.py` and wired it into PR validation and Android beta CI.
- No schema/data reset and no signing, applicationId, CI versionCode strategy, Vinted request rate or BGG network-rate changes.

## 5.12.7 — Revalidation accounting fix
- Aligned historical revalidation diagnostics with execution: `USER_CONFIRMED` identities are excluded from `pending`/`matchedToRevalidate`, so completion can truthfully reach zero.
- Mixed games with at least one historical listing requiring review are persistently counted as review even if another listing independently confirms the canonical BGG identity.
- No data, identity, queue, network, signing or schema behavior changed beyond these accounting semantics.

## 5.12.6 — Historical BGG revalidation
- Added a restart-safe, zero-network one-shot audit for pre-v4 automatic BGG identities after Pixel diagnostics reported 143 historical `MATCHED` games still needing revalidation.
- Manual/user-confirmed identities are excluded from automatic audit.
- Historical seller titles must resolve exactly and uniquely to the stored BGG id to preserve automatic trust; weaker, conflicting, ambiguous, accessory, bundle and non-game evidence is moved to persistent review instead of being guessed.
- Revalidation never deletes or silently reassigns historical BGG ids. It flags individual listings, preserving recovery and allowing later manual/authoritative correction.
- Legacy deal rows associated with flagged listings become `MATCH_UNCERTAIN`, keeping suspect historical identities out of the ready/deal path.
- Added durable per-game `bgg_revalidation_v1:<gameId>` markers so the audit is one-shot even across process restarts.
- Added `bggHistoricalRevalidation` diagnostics and `regression/historical_bgg_revalidation_v5126.py`; both PR validation and Android beta CI run the guard.
- No schema migration/reset and no Vinted request-rate, signing, applicationId or CI versionCode strategy changes.

## 5.12.5 — BGG identity provenance firewall
- Stopped seller-authored Vinted aliases from acting as authoritative learned BGG identities in the zero-network matcher.
- Kept Vinted titles as non-authoritative evidence while limiting identity shortcuts to BGG primary/original/alternate aliases, curated BGG aliases, canonical auto-match names and explicit manual BGG choices.
- Bumped the BGG match algorithm version to 4 so older unresolved review cases can be reconsidered under the stricter trust rule.
- Added a read-only contamination audit (`bggIdentityTrust`) including the count of seller aliases and already-matched games that remain candidates for a later controlled revalidation pass.
- Added `regression/bgg_identity_provenance_v5125.py` and wired it into Android beta CI.
- Added non-distributive `Android PR validation` for pull requests targeting `beta`: regressions plus Java compile before merge, without Firebase or signing-secret use.
- Existing matched rows are preserved in this step: no destructive reset and no schema migration. Signing, applicationId, CI versionCode strategy and Vinted request pacing are unchanged.

## 5.12.4 — Performance stability
- Android beta CI now runs on pushes to `beta` as well as manual dispatch, so a merged beta commit is built, signature-checked and distributed automatically.
- Paginated Motore run inspector to 24 rows per page and removed per-row Deal/MarketListing lookups used only for thumbnails.
- Coalesced Motore refreshes and OperationCenter reconciliation to stop repeated full view-tree rebuilds and queued 1,200-row scans.
- Retired the old deep Test-1 Accessibility identity probe from production; kept only a bounded explicit Vinted URL/ID check.
- Batched Accessibility event diagnostic writes instead of persisting SharedPreferences on every Vinted event.
- Serialized screenshot capture, added low-heap guards and RGB_565 remote thumbnail decoding, and reduced the UI bitmap cache.
- Added short active-run caching and lightweight waiting-run counting to reduce repeated SQLite work in queue loops.
- Added `regression/performance_stability_v5124.py` and wired it into the Android beta workflow before the Android build.
- No database/schema migration. Resolver safety, BGG/Vinted identity separation, active-run scoping, BGG <6 filtering and persistent review semantics are unchanged.

## 5.12.3 — Engine correctness
- Dedicated Motore run inspector instead of redirecting run details to Catalogo.
- Active-run scoped Vinted/BGG ordinary processing; newer runs wait.
- Zero-network snapshot batch may continue during HTTP pacing/cooldown.
- Stronger ready/deal/Hunt identity gates.
- Persistent BGG/Vinted review behavior.
- Variant-pending cases that cannot progress automatically become review.
- Thumbnail/UI bitmap memory-pressure safeguards.
- `engineRun` diagnostics.

## Git workflow migration preparation
- Removed tracked/local `secrets.properties` from the Git-ready copy.
- Added `secrets.properties.example` only as documentation.
- `BGG_TOKEN` can now come from Gradle user properties, CI environment, or legacy local file, in that order.
- Expanded `.gitignore` for secrets, signing material, build products and local IDE state.
- Added `AI_HANDOFF.md` as cross-chat technical source of context.
