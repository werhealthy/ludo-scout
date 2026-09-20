# Regressioni mirate

`GSON_JAR=/percorso/gson-2.10.1.jar JAVA_HOME=/percorso/jdk17 ./regression/run.sh`

Gson è usato solo per adattare org.json nei test JVM; non è dipendenza dell'app. Non sostituisce un test Android. Le fixture sono sintetiche, non una cattura della pagina Vinted dell'utente. `node regression/quality-fixtures.js` rigenera i 120 confronti dal motore JavaScript originale. Il controllo SQLite usa il SQL di migrazione del progetto su dati preesistenti.
