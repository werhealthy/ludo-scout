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

## Verifiche mancanti
Compilazione Android e lint con SDK/dependenze Firebase, test unitari/strumentati
Android con database realistico, test security rules, login Firebase reale,
trasferimento su Firestore, test su Pixel, test Hermes. Non dichiarare
alcuno di questi PASS fino alla loro esecuzione.
