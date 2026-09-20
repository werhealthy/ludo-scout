# Test 2 — Request ledger baseline

Scopo: misurare il traffico Vinted che Ludo genera gia oggi senza cambiare resolver, scheduling o pacing.

La diagnostica `vintedRequestLedger` e salvata in SQLite ed e quindi autorevole tra i processi Android.
Classifica richieste fisiche/cache, linking/bundle e catalog/item.

Criterio minimo di test: lasciare funzionare Ludo finche `linkPhysical >= 5`, poi usare Copia diagnostica.
Nessun click o operazione speciale su Vinted e necessario.
