# Test 6 — Local price context shadow

Obiettivo: verificare se i price hints del Test 5 erano contaminati dai prezzi delle card adiacenti.

- Zero HTTP aggiuntivo.
- Nessun link reale modificato.
- Usa una tabella shadow v3 nuova, quindi misura solo snapshot catturate dopo l'installazione.
- Isola il contesto del singolo item usando i link vicini a item differenti e un cap locale.
- Misura sia la qualità dei price hints sia la cardinalità del matching titolo+prezzo.

Metriche chiave:
- `avgHintsPerPricedCandidate`: deve scendere molto rispetto a ~23.8 del Test 5.
- `candidatesLe3Hints`: quanti candidati hanno 1–3 prezzi plausibili.
- `pairUnique`: osservazioni con esattamente un candidato compatibile titolo+prezzo.
- `strictUnique`: subset abbastanza forte da superare anche la soglia di confidenza conservativa.
