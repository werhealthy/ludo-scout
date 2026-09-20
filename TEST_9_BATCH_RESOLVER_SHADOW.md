# Test 9 — Batch Resolver Shadow

Scopo: simulare offline il nuovo resolver batch usando le candidate snapshot già presenti.

- Zero HTTP aggiuntivo.
- Zero scritture di link reali.
- Un item ID può essere assegnato al massimo a una observation.
- Le collisioni/tie non vengono risolte arbitrariamente.
- Dove esiste un link già noto e il relativo item è ancora nella snapshot, viene usato come ground truth retrospettivo.

## Check
Aprire Ludo Scout e copiare subito la diagnostica. Cercare `vintedBatchResolverShadow`.

Metriche principali:
- `projectedPrimarySaved`: prime ricerche canoniche che il batching eviterebbe nel pool coperto.
- `safeAssignments`: observation che il simulatore collegherebbe conservativamente.
- `ambiguousAfterBatch`: casi lasciati intenzionalmente irrisolti.
- `topCandidateCollisionClaims`: quante collisioni one-to-one il batch deve gestire.
- `gtCorrect`, `gtWrong`, `gtPrecisionWhenCommitted`: controllo retrospettivo sui link già noti, solo quando il vero item è presente nella snapshot.
