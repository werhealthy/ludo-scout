# Test 4 — Candidate Snapshot Shadow

## Scopo
Dimostrare che una pagina catalogo Vinted già scaricata dal resolver può essere trasformata in un candidate set persistente e riutilizzata offline per altre osservazioni dello stesso gioco.

## Sicurezza della prova
- Zero richieste HTTP aggiuntive: il test si aggancia solo a `link_catalog` che Ludo avrebbe già effettuato.
- Non modifica coda, rate limit, pacing o cooldown.
- Non collega alcun annuncio in base al risultato shadow.
- Non usa API private, cookie, CAPTCHA bypass o altre tecniche invasive.

## Diagnostica
`vintedCandidateSnapshotShadow={...}`

Valori chiave:
- `snapshots`: famiglie/query per cui è stata catturata almeno una risposta catalogo.
- `coveredObservations`: osservazioni unresolved che possono essere analizzate con snapshot già acquisita.
- `reusableWithoutRefetch`: osservazioni oltre la prima della famiglia che possono essere confrontate offline senza un altro catalog GET.
- `strictUnique`: osservazioni per cui il replay locale trova un match univoco con gli stessi criteri titolo+prezzo conservativi.
- `ambiguous`: candidate presenti ma non sufficientemente univoci.
- `titleOnlyNoPrice`: la snapshot contiene il titolo ma non abbastanza informazione prezzo per un match conservativo.

## Criterio PASS
Almeno una snapshot con `coveredObservations >= 2` e `reusableWithoutRefetch > 0` dimostra il riuso pratico. `strictUnique > 0` dimostra inoltre che la stessa risposta può produrre decisioni di matching locali per più osservazioni; nessuna di queste decisioni viene applicata in Test 4.
