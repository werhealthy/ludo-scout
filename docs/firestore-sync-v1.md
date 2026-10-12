# Firestore: prima sincronizzazione degli annunci (v1)

## Stato e confini
Proposta di implementazione sul branch backend/firestore-sync-v1. Non è in produzione.
L'archivio SQLite `vinted_affari.db` resta l'unica fonte autorevole: il worker
apre direttamente il file in **sola lettura**, senza upgrade/migrazioni né ADB.
Le regole Firestore attualmente pubblicate nel progetto sono **deny all**;
questo file `firestore.rules` è una proposta da verificare, non è stato pubblicato.

Sono trasferite solo schede annuncio da `market_listings` con alcune informazioni
di `games`: identità articolo e BGG, titolo, marca, condizione, prezzo,
link e massimo tre URL foto, testo osservato (massimo 2000 caratteri),
categoria, stato, lingua, ultimo avvistamento, review.
**Non** vengono trasferiti seller ID/nome, preferiti, libreria, override,
osservazioni storiche, token AI, diagnostica, credenziali o l'intero database.

Il testo osservato proviene dagli annunci e **può contenere dati personali**:
non abilitare la sincronizzazione senza questa consapevolezza. Il catalogo remoto
contiene dati e quindi il database Firestore NON è un archivio pubblicabile.

## Configurazione Android
La configurazione **ufficiale del client Android** fornita dal proprietario è
stata verificata (2026-10-10): progetto Firebase `ludo-scout`,
applicationId Android `it.vintedaffari.app` e ID app Firebase congruente
con il project number. Il file fornito NON è stato committato su GitHub.

Per le build Hermes/locali, salvare l'originale scaricato dalla console come
`app/google-services.json` (esatto percorso locale in working repo).
Il file è ignorato da `.gitignore` e va conservato solo sulla macchina
di build autorizzata. Il Gradle Android legge automaticamente il file,
seleziona il client per `applicationId`, e blocca una build con progetto,
package, Firebase App ID o API key mancanti o incongruenti.
**Non occorre** aggiungere il plugin Gradle google-services.
`FirebaseOptions` inizializza una FirebaseApp nominata senza cambiare
l'eventuale app Firebase predefinita.

Come fallback per release autonome senza JSON locale, Gradle accetta
`LUDO_FIREBASE_APP_ID`, `LUDO_FIREBASE_API_KEY`,
`LUDO_FIREBASE_PROJECT_ID` da proprietà Gradle o variabili d'ambiente.
`LUDO_SYNC_OWNER_UID` mantiene l'UID già autorizzato del progetto come
valore predefinito (sovrascrivibile per test controllati). Il suo valore
non è una password e viene anche vincolato nelle regole remote.

### Installazione locale dal codice GitHub (senza ZIP in repository)

La repository GitHub `werhealthy/ludo-scout` è **pubblica**. Per questo
`google-services.json` originale NON va aggiunto ai commit né agli
allegati di PR: il client Firebase Android può essere incorporato nell'APK,
ma il file non serve al lettore Hermes e non è necessario pubblicarlo.

I soli installer sono versionati su questo branch:
`tools/install_local_firebase_config.cmd` e
`tools/install_local_firebase_config.ps1`. Dopo aver scaricato
`google-services.json` sul PC Windows nella cartella *Download*,
eseguire con doppio clic il file `.cmd` dalla checkout locale del
branch contenente la PR; in alternativa lo script apre un selettore
file. L'installer:
- verifica remote Git `werhealthy/ludo-scout`, project ID, project number,
  Android package, Firebase Android App ID e presenza di una API key;
- si rifiuta di sovrascrivere un JSON locale diverso o un file tracciato;
- richiede che `app/google-services.json` sia ignorato da Git e ne
  controlla hash e stato Git dopo la copia;
- non compila, non cambia branch, non invia dati, non fa login Firebase,
  non attiva Firestore e non modifica Hermes.

**Nota operativa:** mettere l'installer su GitHub non trasferisce da solo
il JSON su Windows e non abilita automaticamente il trasferimento
Android→Firestore→Hermes. Se il proprietario vuole evitare anche l'unica
installazione locale, serve una capacità Hermes autenticata già
autorizzata oppure un'ulteriore decisione esplicita sulla gestione della
configurazione: nessun accesso remoto viene inventato o abilitato
implicitamente.

**Se mancano app ID, API key o project ID, la sincronizzazione è OFF.**
Identificativi e API key del client Android sono incorporati nell'APK
Firebase, ma **non autenticano l'utente**: l'accesso al database richiede
Firebase Authentication + security rules. Non committare JSON originale,
password utente, refresh token, credenziali Admin SDK o chiavi private.
Non condividere la password dell'account con Hermes.

In Impostazioni > Avanzate > Sincronizzazione archivio, l'utente effettua
**una sola volta** l'accesso Email/Password; la sessione è gestita da
Firebase Auth. Se l'UID non corrisponde a quello configurato, accesso
rifiutato e sessione disconnessa. Disconnetti cancella la sessione attiva
e blocca il lavoro futuro. Nessuna schermata di login nella navigazione ordinaria.

## Contratto remoto e frequenza
`sync_v1/{uid}/devices/{deviceId}/listings/{localId}`, con `deviceId`
generato localmente e conservato fuori backup. Il worker usa WorkManager con
rete connessa e periodicità minima Android (15 minuti, non esatta).
Massimo 80 documenti a esecuzione e 2400 scritture al giorno UTC;
fingerprint locale per non inviare annunci invariati. Un batch è segnato
completato solo dopo acknowledgment Firebase, altrimenti viene ritentato.
Tutti i documenti includono `source_hash` e `synced_at`.

In v1 NON viene effettuata la rimozione automatica di documenti remoti
quando una riga locale viene cancellata; Hermes dovrà leggere anche
`lifecycle`, `last_seen`, `synced_at` e trattare gli snapshot obsoleti
come non attendibili. Prima di usare questo come catalogo live va
implementata la riconciliazione/tombstone.

## Regole e Hermes
Il file `firestore.rules` limita le scritture alle sole schede previste,
all'UID owner specifico e allo schema consentito. **Non pubblicare fino
a una review e verifica su emulator o progetto di test.** Nessuna regola è
stata cambiata da questa PR nel database Firebase reale.

Hermes sarà un lettore **separato** dall'account owner, con un'identità
di sola lettura ancora da definire. Non usare una credenziale Admin SDK:
l'Admin SDK bypassa le Security Rules. Non condividere la password
dell'account Ludo con Hermes. Il collegamento Hermes e i test end-to-end
restano fuori dal perimetro verificato della prima PR.

## Verifica permessi Firebase senza accessi al cloud

`regression/firestore-emulator/rules.test.cjs` è una suite isolata nel
progetto fittizio `demo-ludo-firestore-rules`, lanciata nel workflow
Android tramite Firebase Local Emulator Suite. Non richiede
`google-services.json`, password o account reali; non contatta la
istanza di produzione. Controlla creazione, lettura e update validi da
UID owner, blocco di utenti non autenticati/altri UID, blocco di
altre collection, cancellazioni, documenti con path invalidi e
schema non consentito. L'esecuzione sul commit di introduzione della
suite è **PASS** (run GitHub Actions 38097481496).

Questa prova valida il file **proposto** `firestore.rules`, non le
regole effettivamente pubblicate nella console Firebase. Non abilita
trasferimenti reali né l'accesso read-only Hermes. Prima della
pubblicazione servono un confronto con le regole console correnti,
verifica login Firebase reale/consenso e conferma dei costi Spark.

## Snapshot versionato v2 — preparazione locale

Il worktree isolato introduce un protocollo v2 separato da `sync_v1`:

- Il catalogo autorevole `vinted_affari.db` è aperto `OPEN_READONLY` dentro una transazione SQLite differita `BEGIN`/`ROLLBACK`; query e materializzazione condividono così la stessa read view. La copia vive solo nella sidecar app-private `noBackupFilesDir/ludo_firestore_snapshot.db`, mai in tabelle del catalogo.
- La sidecar conserva al massimo uno snapshot di staging, con proiezione minima, ID ordinato, hash per riga, progresso di upload e stato riprendibile. La dimensione del file è limitata a 32 MiB e le righe a 100.000; il superamento interrompe la preparazione senza pubblicare un puntatore.
- Ogni snapshot usa un ID UUID e percorsi `sync_v2/{uid}/devices/{device}/snapshots/{snapshotId}`. Il worker continua a limitare ogni passaggio a 80 operazioni e 2.400 scritture giornaliere, includendo manifest, puntatore e pulizia. Il budget è prenotato localmente prima della richiesta per non superarlo dopo crash/timeout.
- Il manifest completo e poi il puntatore `meta/current` vengono scritti solo dopo l'upload di tutti i documenti e una verifica che la proiezione corrente SQLite abbia ancora lo stesso hash. Se il catalogo cambia durante l'upload, la generazione viene abortita e pulita in batch; non diventa corrente. Dopo lo switch, la versione precedente è rimossa a pagine, mantenendo al massimo corrente + staging/vecchia generazione.
- Le regole v2 in questo repository sono **solo proposta locale**: lettura owner-scoped, staging/versioni, puntatore legato a manifest completo e delete consentito solo a versioni non correnti. Nessuna regola live è stata letta o pubblicata.
- Hermes ha un core separato in `firestore_reader_v2.py` che legge solo il puntatore corrente e verifica schema, scope, hash riga, conteggio e hash snapshot. Il core è offline/iniettato: non include adapter Firebase, credenziali, registrazione `ludo_dev` o Telegram runtime.

Le verifiche automatiche locali coprono il modello di cambio catalogo/interruzione/non-pubblicazione, i controlli adversariali reader e le regole nell'emulatore demo. Non dimostrano il comportamento end-to-end dell'Android Worker su Firestore live né il recapito Telegram. Una modifica SQLite concorrente dopo l'ultima verifica e prima dell'update del puntatore può lasciare corrente una versione coerente ma momentaneamente non più latest; il passaggio periodico successivo rileva il nuovo hash e avvia la generazione seguente. Non è introdotto un contatore di mutazione nello schema autorevole.

## Verifiche ancora mancanti
Compilazione Java/unit Gradle locali PASS; modello snapshot 4/4 e contratto sync 6/6 PASS; reader v2 + suite Stack 193/193 PASS; Firebase Emulator Rules v2 7/7 PASS nel progetto demo locale. Queste prove verificano il codice proposto, non il Worker su device/emulatore Android, Firestore live, regole pubblicate, Telegram/Hermes runtime o catalogo reale. Review indipendente in corso prima del push. Non dichiarare end-to-end o accettazione telefono completati.
