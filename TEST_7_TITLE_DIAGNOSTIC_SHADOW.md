# Test 7 — Title Diagnostic Shadow

Scopo: capire perché lo shadow batch del Test 6 non trova nessuna corrispondenza di titolo, prima di introdurre image matching.

Il test non esegue rete aggiuntiva e non modifica link reali. Riusa le snapshot già persistite dal Test 6 e confronta ogni osservazione coperta con i candidati usando:

- Jaccard sul titolo osservato;
- Jaccard sul nome canonico BGG;
- containment coefficient sul titolo osservato;
- containment coefficient sul nome canonico BGG;
- compatibilità prezzo già disponibile nella snapshot.

La diagnostica include fino a 5 coppie `titolo osservato -> miglior candidato` con le quattro similarità, per capire visivamente la causa del mancato match.

## Check utente

Dopo l'installazione non è necessario aspettare nuove richieste: aprire Ludo Scout e usare `Copia diagnostica`.

Cercare `vintedCandidateSnapshotShadow={build=candidate-title-diagnostic-shadow-v4,...}`.

Interpretazione:
- `semanticMatchedObs > 0`: il titolo contiene segnale utile che il vecchio Jaccard perdeva.
- `semanticPriceMatchedObs > 0`: titolo/gioco + prezzo possono già restringere candidati offline.
- tutti i valori a 0 e sample chiaramente non correlati: la snapshot sta estraendo link non appartenenti ai risultati utili e il prossimo test deve identificare meglio la card HTML, non introdurre subito immagini.
