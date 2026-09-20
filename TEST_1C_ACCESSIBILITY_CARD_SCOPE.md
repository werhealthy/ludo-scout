# Test 1c — Accessibility card scope + Compose extra data

Obiettivo: verificare se la card Vinted espone un vero `/items/<digits>` non sul nodo foglia già analizzato, ma su wrapper/antenati Accessibility o nei valori delle extra data Compose (`semantics.id`, `semantics.testTag`).

## Sicurezza del test
- Nessun numero generico viene interpretato come item ID.
- Un'identità è considerata esplicita solo se contiene letteralmente `/items/<digits>`.
- Gli eventuali hit trovati sugli antenati sono solo diagnostici e NON vengono usati per auto-collegare annunci.
- Vengono richiesti solo pochi extra data dichiarati dal nodo, e solo su un campione iniziale di card uniche.

## Check utente
1. Installa la build.
2. Disattiva e riattiva il servizio Accessibility di Ludo Scout.
3. Apri Vinted su una lista di annunci e scorri 30–50 card differenti.
4. In Ludo Scout usa `Copia diagnostica` e invia tutto il testo.

## Campi da leggere
- `probe=1c` conferma che la build corretta è attiva.
- `uniqueCards >= 30` indica un campione minimo utile.
- `ancestorNodes > 0` conferma che sono stati ispezionati i wrapper.
- `extraRefresh=X/Y` indica quanti valori Compose sono stati effettivamente restituiti rispetto alle richieste.
- `ancestorExplicitHits > 0` è un segnale positivo da validare prima di abilitarne l'uso.
- `a11yProbeLastRequestedExtra` mostra l'ultimo valore Compose recuperato.
- `a11yProbeLastAncestorExplicit` mostra l'ultimo vero `/items/<id>` visto su un antenato, se presente.

## Criterio esito
- POSITIVO: compare un vero `/items/<digits>` in un campo della card o negli extra; oppure `ancestorExplicitHits > 0` da validare.
- NEGATIVO: almeno 50 card uniche, `ancestorNodes > 0`, extra data effettivamente richieste/lette, ma nessun `/items/<digits>`.
- INCONCLUSIVO: campione <30, probe non attivo, o extra data mai realmente restituite quando avrebbero dovuto esserlo.
