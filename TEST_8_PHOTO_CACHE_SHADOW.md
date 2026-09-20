# Test 8 — Photo cache shadow

Scopo: verificare se la thumbnail catturata durante lo scroll può disambiguare, senza nuove richieste Vinted, più candidati che hanno già superato i filtri semantici + prezzo.

## Vincoli
- VintedCandidateSnapshotShadow non effettua HTTP.
- VintedPhotoHashCache non effettua HTTP.
- L'unico hash remoto viene registrato dopo che VintedPhotoMatcher ha già scaricato una thumbnail per il resolver normale.
- Nessun link reale viene scritto dal test shadow.

## Metriche principali
- candidatesWithImage: candidati snapshot per cui il catalogo espone una thumbnail.
- cachedPhotoHashes: thumbnail candidate uniche già scaricate dal resolver normale e trasformate in dHash.
- photoHashHarvests: numero totale di download normali da cui è stato ricavato un hash; se supera cachedPhotoHashes c'è riuso/dedup potenziale.
- semanticPriceAmbiguousObs: osservazioni che hanno >1 candidato plausibile per titolo+prezzo.
- photoFullCoverageObs: ambiguità per cui tutti i candidati plausibili hanno già un hash in cache.
- photoDecisiveObs: subset full-coverage con similarity >= 0.76 e margine >= 0.10 sul secondo.
- photoVeryStrongObs: full-coverage con best similarity >= 0.84.

Il test non abilita auto-link. Le soglie servono soltanto a misurare il potere discriminante della foto.
