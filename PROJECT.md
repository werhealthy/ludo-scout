# Ludo Scout — Project

## Prodotto
Ludo Scout è un'app Android che osserva annunci di giochi da tavolo, li collega a identità BGG, costruisce un catalogo locale e aiuta a distinguere offerte utili, giochi, bundle, preferiti e libreria personale.

## Obiettivo
Trasformare un flusso ampio e rumoroso di annunci in informazioni affidabili con il minor lavoro manuale possibile, mantenendo l'app stabile, veloce e comprensibile.

## Principi durevoli
- Dati reali e provenienza esplicita: non inventare prezzi, identità, lingue o disponibilità.
- Correttezza prima del throughput: aumentare velocità senza aprire la porta a falsi positivi.
- Local-first e lavoro remoto minimo quando possibile.
- UI leggibile e progressiva: mostrare ciò che serve senza esporre complessità interna non necessaria.
- Misurare prima/dopo i cambiamenti sostanziali; non scambiare una build riuscita per prova di prestazioni o UX.
- Conservare preferiti per identità BGG, memoria Ludo, rating personali, navigazione e dati storici.

## Percorso Ludo
Ludo/Esplorazione è il punto comune per cercare annunci su Vinted e controllare i risultati quotidiani. Le metriche dei controlli completati restano leggibili anche quando l'elaborazione è rapida; coda corrente, scarti e interventi manuali sono distinti. Cacce, Libreria e memoria Ludo restano nello stesso contesto.

## Workstream
- `frontend`: esperienza, rendering, navigazione, accessibilità e interazioni.
- `backend`: stabilità, performance, SQLite/code, acquisizione Vinted conforme, matching BGG, riduzione review e osservabilità.

Entrambi lavorano come sviluppatori full-stack su branch separati e convergono tramite PR su `beta`. Vedi `AGENTS.md` per le regole operative.

## Vincoli e rischio
- Vincolo economico esplicito (2026-10-02): non introdurre tecnologie, API o servizi a pagamento. Preferire elaborazione sul telefono e componenti gratuiti; nuove dipendenze e architetture restano soggette ad approvazione.
- Coordinamento workstation AI (2026-10-06): i workload locali pesanti di Ludo non devono assumere accesso esclusivo a RAM/VRAM né avviare in modo incontrollato runtime concorrenti. La direzione approvata è farli progressivamente passare attraverso il control plane Hermes + supervisor indipendente di `werhealthy/personal-ai-stack`. Finché il supervisor non è validato a runtime, evitare di sovrapporre workload locali AI pesanti di Ludo con altri modelli locali.
L'app usa dati locali persistenti, più processi Android e integrazioni esterne. Cambiamenti a schema, filtri/soglie, sicurezza, autenticazione, servizi, costi o comportamento di produzione richiedono approvazione esplicita. Le protezioni anti-abuso delle piattaforme esterne non vanno aggirate.
