# Test 5 — Candidate Snapshot + Price Hints (shadow)

Obiettivo: verificare se una risposta catalogo gia scaricata dal resolver puo essere riutilizzata offline da piu osservazioni mantenendo il segnale prezzo che il fallback HTML storico usa per validare i candidati.

Vincoli:
- zero richieste HTTP aggiuntive;
- nessuna modifica ai link reali;
- nessun bypass di pacing/cooldown;
- la snapshot salva solo ID, slug/titolo e un piccolo set di prezzi decimali trovati nello stesso intorno HTML gia usato dal resolver (-1800/+3000 caratteri), non l'intera pagina.

Metriche diagnostiche:
- `snapshotsWithPriceHints`
- `candidatesWithPriceHints`
- `priceHintValues`
- `strictUnique`
- `ambiguous`
- `titleOnlyNoPrice`
- `noMatch`

Criterio di successo del test strumentale: almeno una nuova snapshot con `snapshotsWithPriceHints > 0` e `candidatesWithPriceHints > 0`.
Criterio di utilita per il futuro batch linker: `strictUnique > 0` su osservazioni coperte, senza introdurre richieste extra.
