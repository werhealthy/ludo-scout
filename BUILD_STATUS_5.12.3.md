# Ludo Scout 5.12.3 — Engine correctness build status

Questa build corregge il modello operativo del Motore prima del redesign visuale.

## Correzioni principali

- Il dettaglio di un job Motore non apre più il Catalogo: usa una vista dedicata costruita direttamente da observations -> market_listings -> games.
- La vista del run mostra anche le card non ancora complete e ne espone lo stadio: BGG, collegamento Vinted, conferma variante, metadata, revisione o pronta.
- Le righe BGG / Vinted / Card pronte del Motore aprono la stessa vista del run con il filtro coerente.
- Il batch Vinted da snapshot è `batch-engine-v2-run-first`: è zero-network, lavora sul job attivo e può avanzare anche mentre la corsia HTTP Vinted è in pacing/cooldown.
- I job Vinted e BGG ordinari dei nuovi scroll restano fuori dal job attivo finché il run precedente non ha concluso il lavoro automatico. Manuale e Caccia restano eccezioni prioritarie.
- La capacità del linker è calcolata sul job attivo, così i job dei run in attesa non bloccano la promozione del run corrente.
- Le notifiche di affare e Caccia richiedono ora identità Vinted esatta, confidence >= 90, market listing MATCHED, game MATCHED e nessuna revisione. Accessori/componenti/non-game/bundle e varianti BGG provvisorie non notificano.
- BGG_VARIANT_PENDING senza più una verifica automatica possibile viene promosso a BGG_VARIANT_REVIEW persistente invece di restare sospeso per sempre.
- Protezioni memoria: screenshot thumbnail convertiti a RGB_565, cattura saltata con heap sotto pressione, cache immagini UI ridotta.
- Diagnostica aggiunta: `engineRun={...}` per separare job attivo, progressi reali e numero di run in attesa.

## Verifiche statiche eseguite

- Versione: `5.12.3-engine-correctness`, versionCode 118.
- Bilanciamento parentesi graffe verificato sui file Java modificati: 0 sbilanciamenti e nessuna chiusura anticipata.
- `javac -proc:none` non segnala errori sintattici prima degli attesi errori di dipendenze Android mancanti nell'ambiente.
- Ricerca riferimenti conferma i nuovi gate notification, batch locale prima del gate HTTP e active-run gating BGG/Vinted.

## Limite dell'ambiente

La build Android completa non è eseguibile qui: il Gradle wrapper richiede Gradle 8.9 da `services.gradle.org`, non raggiungibile dall'ambiente. Android Studio rimane il controllo finale di compilazione.
