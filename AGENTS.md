# Ludo Scout — istruzioni per gli agenti

## Ripresa del lavoro
Il repository di lavoro è `werhealthy/ludo-scout`; il ramo operativo attuale è `beta`.
Prima di qualsiasi lavoro significativo, a ogni nuova chat o ripresa:
1. Leggi questo `AGENTS.md`.
2. Leggi `STATE.md` per stato reale, ultima consegna, verifiche e lavoro aperto.
3. Leggi `docs/specs/2026-09-30-ui-refinement.md`: è la scaletta unica delle attività e delle priorità. La sezione operativa iniziale prevale sulla cronologia successiva.
4. Ispeziona il codice pertinente prima di intervenire.

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
