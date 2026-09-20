# Test 10 — Batch Safety Gate Shadow

## Scopo
Verificare che il batch resolver non consideri sufficiente la sola presenza del nome canonico del gioco in titoli appartenenti ad altre categorie (es. `Tokaido` come marchio karate).

## Vincoli
- zero HTTP aggiuntivo;
- zero scritture di link reali;
- riusa solo snapshot e dati SQLite già presenti;
- i candidati sospetti vengono esclusi solo nello shadow matcher.

## Safety gate
Prima dello scoring batch un candidato viene escluso se:
1. contiene segnali forti di prodotto non ludico già riconosciuti da `BoardGameIntakeGate`;
2. contiene segnali marketplace addizionali molto espliciti (es. guanti/karate/abbigliamento/accessori);
3. il gioco ha un nome canonico molto corto/ambiguo e manca un secondo segnale indipendente: titolo osservato simile, dicitura board-game multilingue, oppure titolo candidato molto compatto.

## Metriche
La diagnostica `vintedBatchResolverShadow` espone:
- `candidateEdgesBeforeSafety`
- `candidateEdgesAfterSafety`
- `strongNonGameRejected`
- `collisionRiskRejected`
- `safetyBlockedPct`
- `blockedSamples`
- `safeAssignments`
- `ambiguousAfterBatch`
- `noCandidate`

## Criterio di successo
- il falso positivo noto tipo `Tokaido -> guanti ... karate ... Tokaido` deve comparire tra i `blockedSamples` oppure non comparire più tra `samples`;
- nessun nuovo esempio chiaramente non ludico deve comparire fra i `samples` accettati;
- il safety gate non deve azzerare sistematicamente tutti i match forti.

Il test non autorizza da solo il linking automatico: serve a definire la subset conservativa che potrà entrare nel test finale controllato.
