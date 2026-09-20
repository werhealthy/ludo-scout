# Build status — v5.11.29

- PASS: regression mirata `regression/vinted_local_batch_v51129.py`.
- La suite legacy `regression/run.sh` non può partire in questo ambiente perché richiede un JAR locale di Gson 2.10.1 (`GSON_JAR`).
- La build Android completa non può essere eseguita qui: il wrapper Gradle 8.9 non è presente in cache e l'ambiente non può raggiungere `services.gradle.org`.
- Lo ZIP viene verificato con `unzip -t` dopo il packaging.

Controllo finale consigliato: aprire il progetto in Android Studio, eseguire Gradle sync/build e provare la nuova azione **Ottimizza** nella schermata Attività.

## Fix 1
- Corretto errore di compilazione in `MainActivity.java`: metodo `compactCount(int)` definito due volte.
- Dopo la correzione rimane una sola implementazione condivisa per badge e contatori.

## Test 1d — cross-process Accessibility diagnostics
- Aggiunta snapshot del probe in SQLite `queue_controls` per evitare SharedPreferences stale tra `:radar` e `:ui`.
- PASS: `regression/a11y_identity_probe_1d.py`.
- La build Android completa resta da verificare in Android Studio per indisponibilità locale del wrapper/SDK.
