# Test 1d — Accessibility probe cross-process

Obiettivo: rendere affidabile la diagnostica del probe Accessibility tra i processi Android `:radar` e `:ui`.

## Perché
`MainActivity` gira in `:ui`, mentre `VintedAccessibilityService` gira in `:radar`. I vecchi contatori erano letti da SharedPreferences e potevano risultare congelati nella UI anche quando il servizio stava lavorando.

## Modifica
Il probe continua a scrivere i contatori locali, ma salva anche una snapshot in `queue_controls` (SQLite), già usata dal progetto per coordinamento cross-process.

La diagnostica espone ora:

`a11yProbeCrossProcess={authoritative=true, ageMs=..., writes=..., payload={...}}`

Questa è la riga da usare per valutare il test. `a11yIdentityProbe=...probe=1d-local-cache` resta solo come confronto con la vecchia cache locale e può essere obsoleto.

## Check manuale
1. Installare la build.
2. Disattivare/riattivare una volta Accessibility Ludo Scout.
3. Aprire Vinted e scrollare 15–20 card.
4. Copiare la diagnostica.
5. Il test di telemetria è riuscito se `a11yProbeCrossProcess` ha `ageMs` recente e `writes > 0`.
6. Per valutare l'identità Accessibility usare esclusivamente il JSON `payload` della stessa riga.
