# Ludo Scout — istruzioni per gli agenti

## Ripresa del lavoro
Il repository di lavoro è `werhealthy/ludo-scout`; il ramo operativo attuale è `beta`.
Prima di qualsiasi lavoro significativo, a ogni nuova chat o ripresa:
1. Leggi questo `AGENTS.md`.
2. Leggi `STATE.md` per stato reale, ultima consegna, verifiche e lavoro aperto.
3. Leggi `docs/specs/2026-09-30-ui-refinement.md`: è la scaletta unica delle attività e delle priorità. La sezione operativa iniziale prevale sulla cronologia successiva.
4. Ispeziona il codice pertinente prima di intervenire.

Non chiedere all'utente di ricopiare la scaletta o la conversazione quando il repository è accessibile. Se questi file non sono accessibili, segnala il limite senza inventare lo stato.

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
