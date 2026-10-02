# Vinted browser sperimentale — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for the recommended native execution, or superpowers:subagent-driven-development only if the user selects delegation. Steps use checkbox syntax for tracking.

**Goal:** Distribuire una beta sperimentale che misuri dati realmente acquisibili dallo scroll Vinted e azioni di cattura, senza richieste aggiuntive del capturer né scritture nei database di catalogo.
**Architecture:** Activity WebView non esportata nel processo :ui, script document-start e bridge a messaggi con origini/frame verificati. Raccolta diagnostica limitata con normalizzazione JS e contatori Java; nessun riuso del bridge locale JsGameEngine. Il browser sperimentale non promette schede pronte: integrazione intake e correzione suggerimenti sono consegne successive separate.
**Tech Stack:** Java17, Android minSdk28/compileSdk35 correnti, JavaScript, WebView provider del telefono, dipendenza gratuita proposta androidx.webkit:webkit:1.12.1 (feature support verificato runtime e compatibilità Gradle verificata in CI; nessun upgrade SDK automatico).
**Spec:** docs/specs/backend-reliability-recognition.md, sezione Proposta B2 browser Vinted passivo e feedback14:57.
**Execution:** proposta diretta in questa sessione, con review finale indipendente; piano non ancora approvato, codice non avviato.

## Global Constraints
- Nessun nuovo servizio/API/proxy/LLM a pagamento; nessuna nuova migrazione o eliminazione di dati.
- Solo HTTPS vinted.it/www.vinted.it; bridge soltanto frame principale delle origini esatte consentite, nessun wildcard. Navigazioni altre origini non ricevono bridge; nel test restano esterne o bloccate con feedback esplicito.
- No login nel test, estrazione cookie/token/header, replay endpoint, scroll o apertura profili automatici, user-agent spoofing, bypass challenge.
- Nessun writer MarketStore/DealDatabase/BundleDatabase, nessun job resolver/deep/bundle avviato dal capturer.
- Pubblicazione distinta da timestamp foto e first_seen; dato assente resta sconosciuto. Profilo parziale non significa catalogo completo.
- Entry SEARCH cattura ON, VIEW cattura OFF, BUNDLE cattura ON con sellerID atteso. Pausa blocca invio, Cattura acquisisce snapshot dei dati già disponibili senza navigare.
- Batch max32item, messaggio max128KiB UTF-8, registry max500ID/sessione; overflow contato ed esposto, mai falso totale completo.
- Dati persistiti per diagnosi limitati a contatori, presenza campi e provenienza; nessun body completo o cookie. Campioni pubblici whitelisted possono restare in memoria e sono mostrati nello schermo diagnostico solo su richiesta.
- Non dichiarare sostituito il percorso nativo, risolto il reset legacy, filtri acquisto o l'intero motore con questa consegna.

## Review Focus
1. Prima pagina caricata prima di onPageFinished: hook anticipato e fallback snapshot, con origine distinta.
2. Stesso ID riappare con prezzo diverso: aggiornamento, non doppio item né perdita del nuovo prezzo.
3. Catalogo raccomandazioni con più seller: proprietà solo sellerID esplicito, non URL pagina.
4. Sito restituisce HTML/challenge o provider non supporta feature: cattura non disponibile, nessun retry autonomo.
5. Foto con timestamp ma item senza pubblicazione: publication resta assente, nessuna falsa freschezza.

### Task1: Normalizzazione passiva e contratto diagnostico
**Files:** create app/src/main/assets/browser/vinted-capture.js; create regression/vinted_browser_capture.test.js.
**Interfaces:** window.LudoCaptureControl.setEnabled(boolean); window.LudoCaptureControl.captureNow(); script invia LudoCapture.postMessage(JSON.stringify({schema:1,items,stats})) solo con cattura ON o one-shot richiesto. items contiene ID/URL/titolo, prezzo+cambio valuta se presente, sellerID esplicito, URL foto, descrizione, publication raw+source quando semanticamente documentato; assenti null, nessun prezzo inventato.
- [ ] Scrivere fixture Node su prime card JSON, DOM con href item esatto, stessoID cambiato, prezzo non parsabile, multi-seller, timestamp foto, payload nonJSON/challenge e cattura OFF; assert ID distinti/aggiornamenti/publication null e nessun fetch/XHR aggiunto.
- [ ] Eseguire node --test regression/vinted_browser_capture.test.js: RED prima dello script.
- [ ] Implementare hook preservando argomenti, this, promise/errors della pagina e risposta originale; leggere clone JSON limitato a risposte catalogo/item note dopo la classificazione della URL, DOM solo card/item, niente pagine chat/account/checkout. Adapter iniziali fixture sono ipotesi e unknown shape incrementa contatore, non successo fittizio. Limitare letture/cloni a un numero finito, payload e item al contratto.
- [ ] Testare repeated injection senza doppi hook, OFF/one-shot senza replay, MutationObserver e overflow500ID; eseguire fixture GREEN, registrare limiti workers/serviceworkers.
- [ ] Commit feat: passive browser capture experiment.

### Task2: Browser sicuro e modalità cattura
**Files:** create app/src/main/java/it/vintedaffari/app/VintedBrowserPolicy.java; create app/src/main/java/it/vintedaffari/app/VintedBrowserActivity.java; create app/src/test/java/it/vintedaffari/app/VintedBrowserPolicyTest.java; modify app/build.gradle; modify app/src/main/AndroidManifest.xml.
**Interfaces:** VintedBrowserPolicy.allowedPage(String url): boolean; VintedBrowserPolicy.allowedMessage(String origin,boolean mainFrame,int bytes): boolean; Activity extras url/mode/expected_seller_id, modi SEARCH/VIEW/BUNDLE, nessuna riga DB modificata.
- [ ] Test RED per http/file/content/javascript/intent/host-suffix/userinfo/porta diversa443, origine non consentita/subframe e payload>128KiB; accettare soltanto HTTPS origini esatte e URLitem conID numerico valido.
- [ ] Implementare policy pura Java e Activity non esportata :ui; nessun addJavascriptInterface. Controllare DOCUMENT_START_SCRIPT e WEB_MESSAGE_LISTENER prima di loadUrl; se indisponibili navigazione senza acquisizione e stato esplicito. Aggiungere WebKit1.12.1 senza altre variazioni dipendenze/SDK; se incompatibile CI fermare, non cambiare stack.
- [ ] Configurare JS/DOM storage del sito, first-party cookies gestiti da WebView senza accesso native al contenuto, third-party cookies OFF, accesso file/content OFF, mixed content OFF, SafeBrowsing ON; debug WebView OFF. Renderer crash gestito con stato errore e ricarica soltanto richiesta dall'utente.
- [ ] Barra accessibile pausa/riprendi/Cattura/risultati diagnostici/chiudi; SEARCH ON, VIEW OFF, BUNDLE ON; validare JSON/schema/limiti e mantenere counters su executor, pubblicare snapshot UI senza lavoro pesante nel callback. Campioni ignoti non serializzati su disco. Back usa history poi termina; terminazione distrugge WebView e registry.
- [ ] Eseguire unit policy e CI Android compile/APK: GREEN; review wiring bridge/no writer e sottoprocessi WebView.
- [ ] Commit feat: secure Vinted browser experiment.

### Task3: Entrate UX e diagnostica verificabile
**Files:** modify app/src/main/java/it/vintedaffari/app/MainActivity.java; modify app/src/main/java/it/vintedaffari/app/VintedAccessibilityService.java; modify regression/run.sh; modify docs/specs/backend-reliability-recognition.md; modify STATE.md.
**Interfaces:** helper MainActivity.openVintedBrowserExperiment(String url,String mode,String sellerId). Nessun cambio generico openVinted fino alla prova sito; azioni browser etichettate sperimentali. Diagnostica browserCapture contiene build, feature/state/mode, acquiredUnique, updated, dropped, presence counts ID/URL/price/seller/publication/language, source JSON/DOM, extraRequestsByCapture; contatore dichiarato capturer-only, non tutta navigazione WebView.
- [ ] Aggiungere “Avvia nuove ricerche” in Ludo con browser SEARCH sperimentale, azione esplicita browser VIEW nel dettaglio, e card Esplora bundle direttamente BUNDLE (nessuna scheda prodotto intermedia); preservare alternativa app nativa.
- [ ] Integrare fixture Node nel workflow regressioni esistente (controllare Node disponibile in CI); diagnostics sanitizzati accessibili anche quando nessun item acquisito. La UI non presenta risultati pronti o bundle confermati da soli counters.
- [ ] Verifica regressioni/Android unit/build PR; riallineare all'ultimo beta e review indipendente finale di origine/frame/integrazione/no side-effects. Nessun test snapshot che duplichi soltanto layout.
- [ ] Dopo merge autorizzato: build beta firmata/certificato atteso e conferme distinte Firebase upload/distribuzione; registrare versione e prove effettive in STATE.
- [ ] Prova telefono: SEARCHprima pagina+2scroll, VIEW senza cattura e one-shot, BUNDLEitem+profilo+scroll, pausa/Back/ritorno, feature/challenge eventuale. Restituire report e campioni sanitizzati pubblicazione per ogni origine. Confrontare traffico cattura OFF/ON sul dispositivo, separando richieste sito e altri job già attivi.
- [ ] Risultato sperimentale: verificati campi/coverage/network effettivi oppure limiti documentati. Solo dopo prova progettare intake perID, lingua/pubblicazione e spegnimento jobs ripetitivi; niente affermazione “tutto il catalogo venditore” senza dati.

## Follow-up separato: suggerimenti personali
Audit già dimostra controlli non uniformi. Una correzione circoscritta successiva deve filtrare owned per ID BGG normalizzato in tutte le liste LocalScoutBrain/Bundle e dare bucket compatibile/da-verificare/non-compatibile senza modificare rating/benchmark; usare GamePreferenceState/HomePresentation e fonte dipendenza verificata. Testare posseduto vs venduto, ID con zeri/spazi, FR|DEP/IT|DEP/EN|DEP/IND/?DEP/conflitto IND+DEP e lingue fuori dai5prefissi, bundle con membro non idoneo e almeno2idonei. Il feedback è autorizzato, la fix non è ancora implementata. Non bloccare il test isolato browser su una migrazione produzione e non dichiarare risolto Acropolis senza dati reali.
