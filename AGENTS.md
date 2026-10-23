# Ludo Scout — istruzioni per gli agenti

## Regole operative correnti — checkpoint 2026-10-23

Questa sezione recepisce le istruzioni esplicite più recenti dell'utente e prevale sulle indicazioni operative storiche incompatibili.
- Ogni risposta sul progetto inizia con: **Fase N/M — nome del risultato · avanzamento X% (base di calcolo) · chiusura prevista HH:MM–HH:MM Europe/Rome (confidenza)**. Se denominatore o tempi non sono stimabili, scrivere “non stimabile: manca …”, senza inventare percentuali o orari. Separare la scadenza desiderata dall'ETA previsto; chiedere l'ora attuale una sola volta se il runtime non la fornisce.
- Percentuali basate su criteri di accettazione o casi contati, non su numero di build/test o tempo trascorso. Definire risultati e criteri di chiusura prima del lavoro; non riutilizzare le vecchie stime 70–80% come misure.
- Risposte brevi e trasparenti: risultato concreto, prova e limite, blocco reale, azione successiva e responsabile. Distinguere sorgente, commit/push, APK, distribuzione, installazione, runtime e correttezza delle proposte AI. Non chiamare “live” una fixture. Non dichiarare lavoro in background senza processo/job identificabile.
- Portare avanti sviluppo isolato, diagnosi e test già autorizzati senza richieste ripetute di “continua”. Per gli ostacoli: massimo tre tentativi con nuove evidenze; poi spiegare causa e prossimo percorso supportato. Non ampliare il task dopo verifiche verdi senza necessità.
- Telefono non collegato: zero ADB non è un blocco del progetto. Percorso ordinario phone-first: App Tester, verifica/raccolta locale in app, un report condivisibile. Niente USB/debug USB o login Google nel browser automatizzato salvo nuova richiesta esplicita dell'utente. Riutilizzare Firebase CLI/API e autenticazione supportata già funzionante; non esporre token/URL firmati. Una scadenza di approvazione va gestita secondo il gate effettivo, senza elusione né divieti permanenti inventati.
- Un ingresso “Diagnostica e report”, sezioni interne e report completo per il perimetro. Non aggiungere test di menu per singolo bug. Preservare journal/rollback e rimuovere strumenti ridondanti solo dopo verifica di copertura. Automatizzare il triage, poi chiedere solo le decisioni umane residue; al massimo una raccolta aggiuntiva necessaria per consegna.
- Conservare risultati, deduplica, identità esterna e hash degli input; non ripetere l'intero catalogo AI né la ricerca candidati già conclusa. Analisi storiche riusabili soltanto con input pertinente riconciliato. Categoria, score BGG e hash non attestano da soli identità o correttezza.
- Pilota seller fermo alla challenge persistita: niente reset, nuovi tentativi o percorsi di aggiramento. Gli altri lavori proseguono indipendentemente. Non forzare associazioni, non promuovere UNKNOWN, proteggere SOLD, decisioni umane, preferiti e dati esistenti. Nuove scritture operative richiedono il perimetro approvato.
- Prima di concludere una sessione significativa aggiornare STATE con commit, risultati, prove, limiti e prossimo risultato. Il checkpoint deve consentire il recupero senza leggere la trascrizione; non presumere che il gateway apra automaticamente una nuova sessione.

## Ripresa del lavoro
Il repository di lavoro è `werhealthy/ludo-scout`; il ramo operativo attuale è `beta`.
Prima di qualsiasi lavoro significativo, a ogni nuova chat o ripresa:
1. Leggi questo `AGENTS.md`.
2. Leggi `STATE.md` per stato reale, ultima consegna, verifiche e lavoro aperto.
3. Leggi `docs/specs/2026-09-30-ui-refinement.md`: è la scaletta unica delle attività e delle priorità. La sezione operativa iniziale prevale sulla cronologia successiva.
4. Per il backend leggi `docs/specs/backend-reliability-recognition.md` e, per il Motore, `docs/research/ludo-scout-engine-report-2026-10-02.md`. La ricerca è storica: riconferma le ipotesi sul codice attuale e segui la priorità registrata in STATE, senza adottare automaticamente il suo ordine A–E.
5. Ispeziona il codice pertinente prima di intervenire.

Non chiedere all'utente di ricopiare la scaletta o la conversazione quando il repository è accessibile. Se questi file non sono accessibili, segnala il limite senza inventare lo stato.

## Workstream paralleli: frontend / backend
Le keyword `frontend` e `backend` selezionano il workstream della chat. Entrambi gli agenti restano sviluppatori full-stack: la keyword definisce responsabilità primaria e confini del task, non capacità diverse.

- `frontend`: UI/UX, rendering, navigazione, accessibilità, interazioni e polish. Fonte operativa principale: `docs/specs/2026-09-30-ui-refinement.md`.
- `backend`: stabilità, performance, SQLite/concorrenza, code/lease, acquisizione Vinted conforme, riconoscimento BGG, riduzione review manuale, metriche e osservabilità. Fonte operativa principale: `docs/specs/backend-reliability-recognition.md`.
- Ogni workstream parte dall'ultimo SHA di `beta` e lavora su branch dedicato `frontend/<task>` o `backend/<task>`. Non sviluppare in parallelo direttamente su `beta`.
- Prima del merge, riallineare il branch a `beta` e rieseguire le verifiche pertinenti. Una PR per task sostanziale; piccoli commit logici.
- Evitare modifiche fuori workstream. File condivisi ad alto conflitto (per esempio `MainActivity.java`, `DealDatabase.java`, `MarketStore.java`, `STATE.md`) si toccano solo quando necessari al task e vanno dichiarati nella PR.
- `STATE.md` descrive lo stato integrato del progetto: aggiornarlo al checkpoint/merge, non come diario di ogni branch. Le specifiche di workstream mantengono il backlog separato.
- Se una chat riceve esplicitamente `frontend` o `backend`, non deve chiedere quale ruolo assumere. Se manca la keyword, usa il workstream già attivo nel contesto; chiedi solo se la scelta cambierebbe davvero il lavoro.
- Nessun workstream può cambiare filtri, soglie, schema, dipendenze, sicurezza o comportamento di produzione senza la normale approvazione prevista sopra.

Per Vinted, ottimizzare velocità e affidabilità riducendo richieste inutili, duplicati e lavoro remoto. Non tentare di aggirare CAPTCHA, blocchi anti-bot, rate limit o altri controlli della piattaforma; HTTP 403/429 e challenge sono segnali di backoff e riduzione della pressione.

## Gestione della scaletta
- In ogni risposta indica quanti gruppi tematici aperti mancano alla fine della scaletta. Conta gruppi sostanziosi, non micro-fix; aggiorna il totale quando nuovi feedback cambiano il backlog. Specifica il conteggio frontend separatamente dal backlog backend.
- Raggruppa ogni consegna per tematica, con più correzioni coerenti.
- Aggiorna il file esistente con i nuovi feedback, consolidando i duplicati in gruppi sostanziosi.
- Mantieni distinti lavori da fare, implementati, verificati automaticamente e accettati sul telefono.
- Non riaprire come nuovi job le attività già consegnate; conserva le verifiche pendenti.
- Il feedback esplicito più recente prevale; le ipotesi non sono decisioni approvate.
- A un checkpoint aggiorna `STATE.md` e, se cambia il comportamento atteso, la scaletta. Indica un solo prossimo gruppo raccomandato.
- Non creare scalette parallele o lunghi prompt di passaggio: il contesto deve essere recuperabile da questi file.

## Implementazione e verifica
Preserva dati reali, preferiti per identità BGG, memoria Ludo, rating personali, navigazione e pricing verificato. Non cambiare filtri, soglie, schema, dipendenze o servizi senza una decisione specifica autorizzata.
Per modifiche al codice esegui i controlli pertinenti e il workflow di regressioni/build esistente; registra prove e limiti. La distribuzione beta verificata già autorizzata richiede build firmata, certificato atteso, conferma distinta di upload Firebase e distribuzione ai tester.
Non dichiarare approvazione visiva o prestazioni sul telefono sulla base della sola CI. Modifiche esclusivamente documentali non richiedono una nuova APK.

## Comunicazione del workstream backend
A ogni consegna spiegare sinteticamente cosa è stato fatto, cosa l’utente deve verificare, come eseguire la prova e cosa deve restituire (per esempio due diagnostiche complete con tempi/versione). Se non serve una prova sul telefono, dirlo. Non richiedere test di una funzione diagnostica prima che una build che la contiene sia verificata e distribuita.
